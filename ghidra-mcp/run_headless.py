"""Serve one explicitly selected binary using GhidraMCP's headless backend."""
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import signal
import sys
import threading


READY_FILE = Path("/tmp/ghidra-backend-url")


def prepare_target(environment=None, data_root=Path("/data"), project_root=Path("/projects")):
    environment = os.environ if environment is None else environment
    selected = environment.get("GHIDRA_BINARY", "")
    if not selected:
        raise ValueError("Set GHIDRA_BINARY to the container path of a file under /data")
    binary = Path(selected).resolve(strict=True)
    if not binary.is_file() or not binary.is_relative_to(data_root.resolve()):
        raise ValueError("GHIDRA_BINARY must resolve to a regular file under /data")

    with binary.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    stem = re.sub(r"[^A-Za-z0-9_-]", "_", binary.stem).strip("_")[:40] or "binary"
    name = environment.get("GHIDRA_PROJECT_NAME") or f"{stem}_{digest[:12]}"
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,79}", name):
        raise ValueError("GHIDRA_PROJECT_NAME must be a simple name, not a path")

    project_root.mkdir(parents=True, exist_ok=True)
    manifest = project_root / f"{name}.target.json"
    target = {"binary": binary.name, "sha256": digest}
    if manifest.exists():
        if json.loads(manifest.read_text()) != target:
            raise ValueError("This project belongs to another binary; choose a new GHIDRA_PROJECT_NAME")
    elif (project_root / f"{name}.gpr").exists():
        raise ValueError("Existing project has no target manifest; choose a new GHIDRA_PROJECT_NAME")
    else:
        # Exclusive creation avoids overwriting metadata from another session.
        with manifest.open("x") as stream:
            json.dump(target, stream, indent=2)
            stream.write("\n")
    return binary, name


def main():
    try:
        binary, name = prepare_target()
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s %(levelname)s %(name)s: %(message)s",
            handlers=[logging.StreamHandler(), logging.FileHandler("/logs/mcp.log")],
        )
    except (ValueError, OSError) as error:
        print(f"Ghidra configuration error: {error}", file=sys.stderr)
        return 2

    from ghidra_mcp.backend import DeferredBackend
    from ghidra_mcp.headless import HeadlessOptions, HeadlessSession
    from ghidra_mcp.server import create_server

    class ReadySession(HeadlessSession):
        def _serve(self):
            if binary.suffix.lower() == ".xex":
                language = str(self._program.getLanguageID())
                file_format = str(self._program.getExecutableFormat())
                if language != "PowerPC:BE:64:Xenon-VMX128-32addr" or "XEX" not in file_format.upper():
                    raise ValueError("XEX target was not imported with the Xbox 360 loader/language")
            super()._serve()

        def load(self):
            super().load()
            READY_FILE.write_text(self.backend.base_url)
            logging.info("Analysis ready: %s (%s)", binary.name, name)

    READY_FILE.unlink(missing_ok=True)
    backend = DeferredBackend(5, 120, wait_timeout=10)
    session = ReadySession(
        HeadlessOptions(binary=binary, project_dir=Path("/projects"), project_name=name, save=True),
        backend,
    )
    server = create_server(backend, host="0.0.0.0", port=18081, capabilities=set())

    def stop(signum, frame):
        logging.info("Shutdown requested; saving the Ghidra project")
        session.request_stop()

    def serve():
        try:
            server.run(transport="streamable-http")
        except Exception:
            logging.exception("MCP server failed")
        finally:
            session.request_stop()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    exit_code = 0
    try:
        session.start()
        # Starting the JVM installs its own OS signal handlers. Re-register
        # Python's handlers afterwards so SIGTERM saves instead of exiting 143.
        signal.signal(signal.SIGTERM, stop)
        signal.signal(signal.SIGINT, stop)
        threading.Thread(target=serve, name="ghidra-mcp-server", daemon=True).start()
        session.run()
    except Exception:
        logging.exception("Headless session failed")
        exit_code = 1
    finally:
        READY_FILE.unlink(missing_ok=True)
        try:
            session.close()
        except Exception:
            logging.exception("Could not save/close the Ghidra project")
            exit_code = 1
        logging.shutdown()
    # The JVM can leave non-daemon threads alive after Ghidra has closed.
    sys.stdout.flush()
    sys.stderr.flush()
    os._exit(exit_code)


if __name__ == "__main__":
    raise SystemExit(main())

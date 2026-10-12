"""Run inside the MCP image with temporary mounts only; never executes the fixture.

Checks real MCP tool calls, graceful-save/reopen, and the Xenon language module.
The ELF fixture is the open-source sample bundled in the pinned GhidraMCP source.
"""
import asyncio
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import traceback

from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


BINARY = Path("/data/sample_x86_64.elf")
READY = Path("/tmp/ghidra-backend-url")


async def call(session, tool_name, **arguments):
    result = await session.call_tool(tool_name, arguments)
    if result.isError:
        raise AssertionError(result.content)
    return json.loads(result.content[0].text)


async def inspect_program(reopened, kind):
    async with streamablehttp_client("http://127.0.0.1:18081/mcp") as (reader, writer, _):
        async with ClientSession(reader, writer) as session:
            await session.initialize()
            names = {tool.name for tool in (await session.list_tools()).tools}
            assert "decompile_function" in names and "get_current_function" not in names
            info = await call(session, "get_program_info")
            assert info["name"] == BINARY.name
            if kind == "xex":
                assert info["language_id"] == "PowerPC:BE:64:Xenon-VMX128-32addr"
                assert "XEX" in info["executable_format"].upper()
                code = await call(session, "decompile_function_by_address", address="0x82001000")
                assert "return" in code["code"]
                return
            assert info["language_id"] == "x86:LE:64:default"
            functions = (await call(session, "list_functions", limit=2000))["items"]
            addresses = {item["name"]: item["address"] for item in functions}
            code = await call(session, "decompile_function", name="compute")
            assert "add_numbers" in code["code"]
            if reopened:
                assert "smoke_multiply" in addresses
                lines = (await call(session, "disassemble_function", address=addresses["add_numbers"]))["items"]
                assert lines[0]["comment"] == "synthetic smoke-test annotation"
            else:
                await call(session, "rename_function_by_address",
                           function_address=addresses["mul_numbers"], new_name="smoke_multiply")
                await call(session, "set_disassembly_comment",
                           address=addresses["add_numbers"], comment="synthetic smoke-test annotation")


def run_session(reopened, kind="elf"):
    environment = {**os.environ, "GHIDRA_BINARY": str(BINARY), "GHIDRA_PROJECT_NAME": f"{kind}_smoke"}
    log_path = Path(f"/logs/smoke-{kind}-{int(reopened)}.log")
    with log_path.open("w") as log:
        process = subprocess.Popen(
            [sys.executable, "/opt/ghidra-runner/run_headless.py"],
            env=environment, stdout=log, stderr=subprocess.STDOUT,
        )
        try:
            deadline = time.monotonic() + 180
            while not READY.exists():
                detail = log_path.read_text(errors="replace")
                if (process.poll() is not None or time.monotonic() > deadline
                        or "Headless startup failed" in detail or "Headless session failed" in detail):
                    raise AssertionError("Headless startup did not become ready:\n" + detail[-6000:])
                time.sleep(0.2)
            subprocess.run([sys.executable, "/opt/ghidra-runner/healthcheck.py"], check=True)
            asyncio.run(inspect_program(reopened, kind))
        finally:
            process.terminate()
            try:
                process.wait(timeout=120)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                raise AssertionError("Graceful Ghidra shutdown timed out")
        assert process.returncode == 0, (process.returncode, log_path.read_text(errors="replace")[-6000:])
        assert not READY.exists()


def synthetic_xex():
    """Construct an uncompressed, unencrypted toy XEX containing addi/blr.

    Header layout follows the public XEX/PE formats and Xenon360's synthetic
    fixture conventions. No game data, SDK content, keys, or encryption is used.
    """
    image = bytearray(0x10000)
    image[:2] = b"MZ"
    struct.pack_into("<I", image, 0x3c, 0x80)
    image[0x80:0x84] = b"PE\0\0"
    struct.pack_into("<HH", image, 0x84, 0x1f2, 2)  # Xbox 360 machine, two sections
    struct.pack_into("<H", image, 0x94, 224)
    struct.pack_into("<H", image, 0x98, 0x10b)
    for offset, value in [(16, 0x1000), (28, 0x82000000), (56, 0x20000), (60, 0x400)]:
        struct.pack_into("<I", image, 0x98 + offset, value)
    for index, (name, rva, size, flags) in enumerate([
        (b".text", 0x1000, 32, 0x60000020), (b".pdata", 0x2000, 8, 0x40000040),
    ]):
        section = 0x178 + 40 * index
        image[section:section + len(name)] = name
        struct.pack_into("<IIII", image, section + 8, size, rva, size, rva)
        struct.pack_into("<I", image, section + 36, flags)
    struct.pack_into(">II", image, 0x1000, 0x38630001, 0x4e800020)  # addi r3,r3,1; blr
    struct.pack_into(">II", image, 0x2000, 0x82001000, 0x00000102)

    header = bytearray(0x1000)
    header[:4] = b"XEX2"
    for offset, value in [(4, 1), (8, 0x1000), (16, 0x100), (20, 3)]:
        struct.pack_into(">I", header, offset, value)
    for index, (key, value) in enumerate([
        (0x00010100, 0x82001000), (0x00010201, 0x82000000), (0x000003ff, 0x300),
    ]):
        struct.pack_into(">II", header, 24 + 8 * index, key, value)
    for offset, value in [(0x100, 0x19c), (0x104, len(image)), (0x210, 0x82000000),
                          (0x280, 1), (0x284, 0x11), (0x300, 8)]:
        struct.pack_into(">I", header, offset, value)
    return bytes(header + image)


def main():
    global BINARY
    shutil.copyfile("/opt/ghidramcp/tests/fixtures/sample_x86_64.elf", BINARY)
    original = BINARY.read_bytes()
    run_session(False)
    print("PASS: real MCP connection, program identification, listings, decompilation, annotations", flush=True)
    run_session(True)
    assert BINARY.read_bytes() == original
    print("PASS: graceful shutdown saved annotations; reopening preserved them; original unchanged", flush=True)

    BINARY = Path("/data/synthetic.xex")
    BINARY.write_bytes(synthetic_xex())
    original = BINARY.read_bytes()
    run_session(False, kind="xex")
    assert BINARY.read_bytes() == original
    print("PASS: synthetic Xbox 360 XEX import and PowerPC decompilation over MCP", flush=True)

    import pyghidra
    pyghidra.start()
    from ghidra.program.model.lang import LanguageID
    from ghidra.program.util import DefaultLanguageService
    language = DefaultLanguageService.getLanguageService().getLanguage(
        LanguageID("PowerPC:BE:64:Xenon-VMX128-32addr")
    )
    assert str(language.getLanguageID()) == "PowerPC:BE:64:Xenon-VMX128-32addr"
    print("PASS: Xenon360 PowerPC/VMX128 language is installed and loads", flush=True)


if __name__ == "__main__":
    exit_code = 0
    try:
        main()
    except BaseException:
        traceback.print_exc()
        exit_code = 1
    finally:
        sys.stdout.flush()
        sys.stderr.flush()
        # PyGhidra may retain non-daemon JVM threads after the language check.
        os._exit(exit_code)

# Headless Ghidra/MCP

`ghidra-mcp` embeds Ghidra through PyGhidra and serves Streamable HTTP MCP at
`http://127.0.0.1:18081/mcp`. No GUI or separate server on port 18080 is needed.
The image includes matching Ghidra 12.1.4, GhidraMCP, and Xenon360 0.7.0
extensions for Xbox 360 XEX and Xenon PowerPC/VMX128 analysis.

## Mounts and target selection

The shared volume list in `docker-compose.yml` preserves your host paths:

| Container path | Access | Purpose |
| --- | --- | --- |
| `/data` | Read-only | Only the binaries/files you choose |
| `/projects` | Writable | Persistent Ghidra projects and target manifests |
| `/exports` | Writable | Reserved for exported code/reports |
| `/scripts` | Read-only | Optional trusted scripts; not run automatically |
| `/logs` | Writable | Persistent MCP runner logs (`mcp.log`) |

Use separate host directories. Writable directories must permit UID/GID 1000.
The MCP container runs non-root, with a read-only root filesystem and temporary
home/cache directories. Do not mount your home directory or the Docker socket.
These restrictions apply to Ghidra, not an agent running on the host.

Add the **container path**, not the host path, to `ghidra-mcp/.env`, for example:

```bash
GHIDRA_BINARY=/data/fable2/default.xex
GHIDRA_PROJECT_NAME=fable2
```

Only that file is imported; the runner does not scan `/data` recursively.
The file's SHA-256 is recorded. A named project cannot silently be reused for
different bytes; choose another project name for a changed executable.
Without an explicit project name, a hash-based name is generated automatically.

## Start

From the repository root, run `./start-services.sh` and select `re` (or `5`).
The ordinary core/chat presets do not require `GHIDRA_BINARY`.

Standalone from this folder:

```bash
../create-docker-network.sh
set -a
source ./.env
set +a
docker compose up -d --build ghidra-mcp
docker compose logs -f ghidra-mcp
```

Initial import/analysis can take a long time. The MCP listener is available
during analysis; analysis tools return `NOT_READY` until the backend is ready.
Container health checks validate the analyzed program, not just an open port.
OpenCode's project config registers `ghidra`; check it with `opencode mcp list`.
Reconnect the server through `/mcps` if OpenCode was already open before startup.

Stop gracefully with `docker compose stop ghidra-mcp`. Annotations are saved
when the session closes; force-killing it can lose pending changes. The stop
grace period is two minutes. This is not yet an unattended checkpointing agent.

The `ghidra-headless` service remains an optional manual utility container
(`manual` profile). It is not needed by MCP. Never open the same project in two
Ghidra processes simultaneously.

## Verification

The runner and connection should first be tested against a small synthetic
binary. For a real Xbox 360 target, call `get_program_info` and verify that its
loader/architecture is Xenon/XEX, not a raw-binary fallback, before trusting
decompilation results. The runner rejects `.xex` files imported with the wrong
loader/language. Fable II's actual import must be verified separately.

Pure configuration tests (no containers needed):

```bash
python tests/test_ghidra_runner.py
```

After building the image, run this from the repository root for real MCP,
decompilation, persistence, synthetic XEX import, and Xenon language checks.
It uses only synthetic/open-source fixtures, temporary container storage, and no network access or
host port publishing. It does not mount your game files/projects:

```bash
docker run --rm --pull=never --network none --read-only \
  --cap-drop ALL --security-opt no-new-privileges:true --init \
  --tmpfs /data:uid=1000,gid=1000,mode=700 \
  --tmpfs /projects:uid=1000,gid=1000,mode=700 \
  --tmpfs /logs:uid=1000,gid=1000,mode=700 \
  --tmpfs /tmp:mode=1777 \
  --tmpfs /home/ghidra:uid=1000,gid=1000,mode=700 \
  --env _JAVA_OPTIONS=-Xmx512m \
  --mount "type=bind,src=$PWD/tests/ghidra-smoke-test.py,dst=/tests/smoke.py,readonly" \
  --entrypoint python ghidra-mcp:local /tests/smoke.py
```

# PCSX2 PINE MCP

A small, read-only Java MCP bridge to PCSX2's built-in PINE IPC on Linux. It
uses the official MCP Java SDK over stdio and PCSX2's Unix-domain socket. It
requires no PCSX2 patch and works with the Linux AppImage and normal Linux
source builds.

## Tools

- `status`: emulator status.
- `game_info`: title, game serial, CRC, game version, and PCSX2 build.
- `read_memory`: read 1–256 bytes at a guest address.

The MCP bridge is intentionally read-only today, even though PCSX2's PINE
protocol also supports 8/16/32/64-bit guest-memory writes and save/load-state
commands (see `pcsx2/PINE.cpp`, opcodes `0x04`–`0x07` and `0x09`–`0x0a` in the
PCSX2 source tree). These could be added later as separate, explicitly
documented tools with strict validation and safeguards; state loads and memory
writes can alter or disrupt a running game. PINE does not provide CPU register
inspection, breakpoints, or pause/resume commands, so those require a separate
debugger-oriented approach.

## PINE socket

On Linux, PCSX2 uses `$XDG_RUNTIME_DIR/pcsx2.sock` with the default PINE slot
28011, or `/tmp/pcsx2.sock` if `XDG_RUNTIME_DIR` is unset. A non-default slot
adds `.<slot>` to the socket filename. Set `PCSX2_PINE_SOCKET` to override the
path, or `PCSX2_PINE_SLOT` to select another slot.

Enable PINE in PCSX2 under **Settings → Advanced → Enable PINE IPC** (the exact
menu may vary by version). PINE is disabled by default. The MCP starts without
requiring PCSX2 to be running and connects only when a tool is called.

## Build and test

Java 17 or newer is required. The checked-in Maven wrapper downloads Maven if
needed and builds a self-contained JAR:

```sh
./mvnw test package
```

The unit tests use a fake Unix-domain socket and do not launch or modify PCSX2.
The built server JAR is `target/pcsx2-pine-mcp-0.2.0-all.jar`.

OpenCode's project configuration starts the JAR with `java -jar`. The bridge is
read-only and has no external game-data or Docker dependency.

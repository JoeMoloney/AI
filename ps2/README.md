# PS2 recompilation analysis tools: build spec

This directory preserves reusable PS2 tooling written while debugging the
*Sims Bustin' Out* static recompilation (`/mnt/HDD/git_stuff/my_git/TheSimsBustinOut-Recomp`,
2026-11-12), and specifies an MCP server, **`ps2-recomp-analysis`**, that wraps it.
It's written so another agent (e.g. a local model) can build the server without
the original conversation.

Everything here is **asset-free**: public PS2 encodings only. Never commit game
data, save states, dumps or generated game code to this repo.

---

## 1. What already exists (reference implementations)

`reference-tools/` contains working Python 3 tools. They are the **test oracle**:
any new implementation must produce the same output on the same input.

| File | What it does | Usage |
|---|---|---|
| `vu_disasm.py` | Disassembles VU0/VU1 microcode (64-bit pairs: upper FMAC + lower integer/memory/branch). Flags `[I E M D T]`; I-bit pairs show `LOI <float>`. | `python3 -I vu_disasm.py vu1_code.bin --start 0x910 --end 0xc00` |
| `vif_decode.py` | Independently decodes a raw VIF1 command stream. Tracks STCYCL cl/wl, BASE/OFFSET/TOPS double buffering (TOPS swaps on MSCAL/MSCALF/MSCNT), sizes UNPACK/MPG/DIRECT/STROW/STCOL/STMASK. `--watch <qw>` flags UNPACKs that write that VU qword. | `python3 -I vif_decode.py vif1_raw.bin --watch 0x30 --until-mscal 3` |
| `recover_data_referenced_functions.py` | Recovers MIPS functions missing from a Ghidra-MCP function map: code addresses referenced from data tables (vtables/callbacks) or built in code with `lui`+`addiu/ori`, sized by a linear sweep that tracks the furthest forward branch. | `python3 -I recover_data_referenced_functions.py GAME.ELF out.csv map.csv [more_maps.csv]` |

Map CSV format (input and output): header `name,start,end,size`, hex addresses with `0x`, `end` exclusive.

### Input formats

**Raw VIF1 capture (`vif1_raw.bin`)**: repeated records `[u32 little-endian length][length bytes]`, one per VIF1 transfer. Produced by the PS2Recomp runtime switch `SIMS_VIF1_DUMP_DIR=<dir>` (see §4). Concatenate the payloads; the VIF processes one continuous stream.

**VU dumps**: flat little-endian images. VU1 micro memory = 16 KiB, VU1 data = 16 KiB (1024 qwords of 4×u32), VU0 = 4 KiB each. Produced by `SIMS_VU1_TRACE_DIR` (`vu1_code.bin`, `vu1_data.bin`, `vu1_trace.txt`) or extracted from a PCSX2 save state.

**PCSX2 save state (`.p2s`)**: a **zip** archive. Entries seen in PCSX2 v2.8.2 (savestate version 36):

```
PCSX2 Savestate Version.id     PCSX2 Internal Structures.dat
eeMemory.bin   (32 MiB EE RAM)  iopMemory.bin (2 MiB)
eeHwRegs.bin   iopHwRegs.bin    Scratchpad.bin (16 KiB)
vu0Memory.bin (4 KiB)  vu0MicroMem.bin (4 KiB)
vu1Memory.bin (16 KiB) vu1MicroMem.bin (16 KiB)
SPU2.bin  USB.bin  PAD.bin  GS.bin  Screenshot.png
```
Location: `~/.config/PCSX2/sstates/<SERIAL> (<CRC>).<slot>.p2s`.
**Why it matters:** PINE cannot read VU memory. `vtlb_ramRead` returns 0 for
handler-mapped pages, so the `pcsx2-pine` MCP reads zeros at `0x1100xxxx`. A save
state is the ground truth for VU memory, and it solved the black-screen bug.

---

## 2. The MCP server to build: `ps2-recomp-analysis`

Follow the existing `x360/recomp-mcp` and `pcsx2-pine` projects exactly
(Java 17, official MCP Java SDK, Streamable HTTP on `/mcp`, Maven wrapper,
self-contained JAR, per-service Dockerfile, read-only container). Copy their
`pom.xml`, `Dockerfile`, server bootstrap and per-tool stderr logging
("begin/completion/failure" lines).

- Directory: `AI/ps2/recomp-mcp/` (package e.g. `local.opencode.ps2recomp`).
- Port: **18085**. Compose service `ps2-recomp-analysis`, image `ps2-recomp-analysis-mcp:local`.
- Config: `PS2_ANALYSIS_CONFIG=/config/targets.properties` (same idea as x360). Mount
  `${PS2_DATA_ROOT:-/mnt/HDD/git_stuff}` read-only at the same path, and
  `~/.config/PCSX2/sstates` read-only at `/sstates`.
- Register in `opencode.json` / Claude Code as `http://127.0.0.1:18085/mcp`.
- **All tools are read-only.** Bound every output (lengths ≤ 4096 bytes per read, ≤ 400 lines per listing, with `offset`/`limit` paging and `has_more`).

### targets.properties

```properties
target.sims.name=The Sims Bustin' Out (PAL)
target.sims.elf=/mnt/HDD/git_stuff/my_git/TheSimsBustinOut-Recomp/work/sims-bustin-out/disc-files/SLES_520.48
target.sims.maps=/mnt/HDD/git_stuff/my_git/TheSimsBustinOut-Recomp/work/sims-bustin-out/SLES_520.48-recovered-map.csv,/mnt/HDD/git_stuff/my_git/TheSimsBustinOut-Recomp/work/sims-bustin-out/supplemental-map.csv
target.sims.workdir=/mnt/HDD/git_stuff/my_git/TheSimsBustinOut-Recomp/work/sims-bustin-out
target.sims.serial=SLES-52048
target.sims.crc=B89C03BA
```

### Tools

| Tool | Input | Output |
|---|---|---|
| `list_targets` | none | Configured targets (id, name, ELF path + SHA-256, serial/CRC). |
| `list_savestates` | `serial?` | `.p2s` files under `/sstates` with size and mtime. |
| `savestate_read` | `file`, `region` (`ee`,`iop`,`scratchpad`,`vu0_data`,`vu0_micro`,`vu1_data`,`vu1_micro`), `offset`, `length` (≤4096), `format` (`hex`/`qwords`/`floats`) | Bytes from that zip entry. `qwords` prints `qw NNN: w0 w1 w2 w3` like the debugging session did. |
| `savestate_screenshot` | `file` | The embedded `Screenshot.png` as an MCP image content block. |
| `vu_disassemble` | source (`file`+`region` from a save state **or** `path` to a raw dump), `start`, `end` (byte PCs) | `vu_disasm.py` output lines. |
| `vif_decode` | `path` to `vif1_raw.bin`, `watch?`, `until_mscal?`, `offset/limit` | `vif_decode.py` output lines. |
| `compare_vu_memory` | `a` and `b` (each: save state + region, or a raw dump path), `qw_start`, `qw_end` | Only the differing qwords, side by side. **This would have found the black-screen bug in one call.** |
| `recover_functions` | `target`, `max_results?` | Candidate rows `name,start,end,size` not in the target's maps (do not write files). |
| `read_trace` | `path` to `vu1_trace.txt` (or any probe log), `grep?`, `offset/limit` | Bounded lines. |

### Acceptance checks
1. `vu_disassemble` on any `vu1_code.bin` produces byte-identical output to `vu_disasm.py` for the full 0x0–0x4000 range, with zero `upper?`/`lower?` lines on Sims data. Known lines: `0910 … XTOP vi1`, `0b80 … ILW.w vi3, 48(vi0)`, `0b90 … XGKICK vi3`.
2. `vif_decode` matches `vif_decode.py` line-for-line; first lines on the Sims capture are `STCYCL cl=4 wl=4`, `STMASK 0x00000000`, …, `MSCAL pc=0x910 top=0x3e`.
3. `savestate_read(region=vu1_data, offset=0x30, length=16, format=qwords)` on the 2026-11-12 Sims state returns `qw 003: 0000801e 10000000 0000000e 00000000`.
4. `compare_vu_memory` between that state's `vu1_data` and an all-zero 16 KiB file lists qw 0–7 and 0x30.
5. Unit tests use synthetic inputs only (hand-built VU words, a tiny zip with fake entries). No game data in the repo.

### Suggested build order
1. `savestate_read` + `savestate_screenshot` (zip + bounds checks): the highest value.
2. `compare_vu_memory`.
3. Port `vu_disasm` (pure table-driven decoding; use the Python file as the spec).
4. Port `vif_decode`.
5. `recover_functions` (needs ELF section parsing; port the Python sweep).
6. Optional later: `ee_disassemble` (MIPS R5900) and IOP IRX import/export listing (the session also split `MODULEU.DAT` into IRXs at each `\x7fELF` and listed imports via magic `0x41E00000`, exports `0x41C00000`).

> **Rust alternative (owner's learning goal).** The same spec suits a Rust
> crate (`zip`, `goblin`, `serde`, `rmcp`). Keep the Python tools as the oracle and
> compare outputs in tests. Java remains the default for consistency with the
> other services.

---

## 3. Key PS2 facts learned (do not re-derive)

- IRX entry is `int _start(int argc, char *argv[])`, `argv[0]` = module name.
- Negative EE syscalls are the interrupt-context variants: `0xFC SetAlarm, 0xFD ReleaseAlarm, -0xFE iSetAlarm, -0xFF iReleaseAlarm`.
- `sifman` #32 = `sceSifSetDmaIntr(xfer, n, handler, arg)`: transfer, then the handler runs later (interrupt-like).
- VIF V2 unpack writes `v0 v1 v0 v1`; V3 writes W from the next element (PCSX2 `Vif_Unpack.cpp`).
- An XGKICK packet ≥ VU memory size (16 KiB) is dropped and VU1 continues (PCSX2 `Gif_Unit::GetGSPacketSize`).
- EE address windows `0x11000000–0x1100FFFF` are VU0/VU1 micro/data memory. Games write VU1 data directly from the EE (GIF tag templates, buffer pointers).

## 4. Runtime-side debug switches (PS2Recomp patch, not in this repo)

These live in the local PS2Recomp checkout (`TheSimsBustinOut-Recomp/references/PS2Recomp`,
patch snapshots `work/sims-bustin-out/ps2recomp-local-nov12*.patch`). All are opt-in
environment variables and could be upstreamed with a `PS2X_` prefix:
`SIMS_FRAME_DUMP_DIR`, `SIMS_GS_STATS`, `SIMS_GIF_DUMP`, `SIMS_VIF1_TRACE`,
`SIMS_VIF1_DUMP_DIR`, `SIMS_VU1_TRACE_DIR` (+`SIMS_VU1_TRACE_SKIP`),
`SIMS_VU1_RUNLOG_QW`, `SIMS_IOP_FILE_TRACE`, and `[dma:gif-tag]` under `SIMS_DMA_TRACE`.
The MCP's `read_trace`/`vif_decode`/`vu_disassemble` tools consume their outputs.

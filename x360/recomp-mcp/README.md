# Xbox 360 Recomp Analysis MCP

A read-only Java MCP intended to give future models self-describing tools for
Xbox 360 static-recompilation projects. It serves Streamable HTTP at `/mcp`.
Guest PPC addresses are the join key
between generated C++, Xenia traces, native profiles, and Ghidra analysis.

## Current scope

Version 0.1 provides:

- `list_targets`: list configured original XEX, Xenia, generated-source, and
  native-build paths.
- `inspect_target`: hash the configured XEX/native executable, count generated
  guest functions, and inspect trace flags in the configured Xenia source tree.
- `search_functions`: search generated C++ definitions by guest address or
  `sub_XXXXXXXX` name.
- `lookup_function`: retrieve a bounded generated-C++ excerpt for one guest PPC
  address, with source path, line, and file hash.
- `import_xenia_trace`: parse numbered chunks produced by Xenia's
  `trace_function_data_path`, summarize function calls and optional instruction
  coverage, and join trace starts to the generated-function index.

All tools are read-only. They do not launch Xenia or the native game, change
Ghidra's selected executable, build code, or store inferred function meanings.
They report configured paths and observed hashes so a model can detect stale or
mismatched artifacts. Generated function addresses are useful identities, not
semantic names; the tools do not guess what a function does.

## Target registry

Copy [`targets.example.properties`](../targets.example.properties) to
`~/.config/opencode/x360-targets.properties`, then replace the example paths.
The registry is intentionally local and should not be committed. Add other
titles using the same `target.<id>.*` keys and append their IDs to
`target.ids`.

The server uses `X360_ANALYSIS_CONFIG` when set, otherwise it reads the path
above. Paths identify inputs only; the server never modifies them.

## Build and test

Java 17 or newer is required. From this directory, use the checked-in wrapper:

```sh
./mvnw test package
```

The standalone JAR is `target/x360-recomp-mcp-0.1.0-all.jar`.

The root AI repository's Compose stack builds this MCP as
`x360-recomp-analysis` and serves it at `http://127.0.0.1:18084/mcp`. Configure
`X360_ANALYSIS_CONFIG` to the host registry file and `X360_DATA_ROOT` to the
read-only host directory containing every path referenced by that registry.
Paths in the registry must also be valid at the same absolute paths inside the
container. Start it from `start-services.sh` (option 7); view diagnostics with
`docker compose logs -f x360-recomp-analysis` or the `logs` menu option.

## Planned extensions

The next steps should be evidence-driven and separately testable:

1. Validate the trace parser against a real Xenia capture and include capture
   identity metadata when trace production is available.
2. Parse native `perf` captures and preserve/resolve process perf maps so samples
   can be attributed to generated functions where possible.
3. Combine those records with Ghidra evidence in a cross-view lookup, retaining
   provenance and confidence rather than asserting inferred subsystem labels.
4. Add optional memory/PGO diagnostics only after function attribution works.

Use the memory service for concise, confirmed function knowledge and
cross-game discoveries, not as the bulk exact-address index. The latter needs
structured lookup and build-specific identity.

## Xenia trace import

Call `import_xenia_trace` with the configured target and the base filename given
to Xenia's `trace_function_data_path` (for example `/tmp/functions.trace`). The
Windows writer replaces that base extension with numbered chunk names such as
`functions.0`, `functions.1`; the importer reads these files without modifying
them. It validates the 48-byte little-endian function header, caller history,
and any instruction-count payload, and returns chunk SHA-256 hashes, totals,
top call-count records, and exact start-address matches against generated C++.
The trace format does not embed game/build identity, so the tool also reports
the configured original-XEX hash and explicitly warns that the user must verify
the capture belongs to that XEX and Xenia build.

Trace parsing is covered by synthetic fixtures, but no real capture was
available for validation during this implementation. Additionally, the local
Xenia checkout's POSIX `ChunkedMappedMemoryWriter::Open` is currently a stub
that returns null; this importer can read correctly framed trace chunks, but
that checkout cannot currently produce them on Linux without a writer
implementation or a trace from another supported build.

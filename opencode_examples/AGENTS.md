# Durable Memory

Use the `memory` MCP server as durable knowledge across sessions.

- The global OpenCode MCP entry is `memory`, using Streamable HTTP at
  `http://127.0.0.1:18082/mcp`. The local Docker service must be running; its
  host port `18082` maps to container port `8080`. Prefer the registered MCP
  tools over calling the service's HTTP API directly.
- Search memory before substantial investigations, architectural decisions, or work on a familiar issue. Use focused queries and retrieve only what is relevant.
- Start searches with a small result limit (usually 3). Omitting `projectId`
  searches global memories only; supply the relevant project ID for project
  knowledge. `includeGlobal` defaults to true when a project ID is supplied;
  set it false when the task requires project-only results.
- Store confirmed, reusable findings when substantial work is complete: root causes and fixes, decisions, failed approaches, important commands, and useful file or function meanings.
- Available tools: `memory_search` searches; `memory_store` adds a finding after
  duplicate detection; `memory_get` retrieves an ID; `memory_update` edits an
  active finding; `memory_supersede` marks an old finding as replaced. If a
  store reports duplicate candidates, inspect them and update or supersede
  stale knowledge rather than creating a contradictory duplicate.
- Do not store secrets, credentials, transient progress, unsupported speculation as fact, or information already documented in the project.
- Keep findings concise and scoped correctly. Follow more specific project instructions for project IDs, scope, and evidence requirements.

# Ghidra MCP

- The global OpenCode MCP entry is `ghidra`, served locally over Streamable
  HTTP at `http://127.0.0.1:18081/mcp`. It is provided by the `ghidra-mcp`
  Docker service in `~/HDD/git_stuff/my_git/AI`; it embeds headless Ghidra, so
  no Ghidra GUI or separate port-18080 server is required. The service and its
  selected target must be running/ready before analysis calls will work.
- The runner analyzes only the single executable selected by `GHIDRA_BINARY`
  at startup; it does not scan the mounted data directory. Treat the configured
  binary as the target and do not assume it is the game or file from a previous
  session. If analysis reports `NOT_READY`, wait for initialization rather
  than interpreting that as a target result.
- The current MCP tool surface does not switch executables. Target changes are
  made by changing `GHIDRA_BINARY` (and, if needed, `GHIDRA_PROJECT_NAME`) in
  `ghidra-mcp/.env`, then restarting the `ghidra-mcp` container. Do this only
  when the user requests a target switch; after restart, verify the new target
  with `get_program_info` before proceeding.
- The running image includes two console-specific extensions: **XenonVMX128
  (Xenon360 0.7.0)** for Xbox 360 XEX/XEXP loading and Xenon PowerPC/VMX128
  analysis, and **Emotion Engine** for PS2 R5900/MMI/VU analysis. The R5900
  SLEIGH language is compiled in the image. These make the relevant processor
  languages/loaders available, but do not prove a target imported correctly;
  verify its format and selected language in `get_program_info` before relying
  on analysis. Do not add a second XEX loader by default: Xenon360 already
  includes XEX/XEXP loading.
- At the start of every reverse-engineering task/session, and after the user
  switches executables, call `get_program_info`; never infer the active target
  from conversation history. Confirm program format, architecture/language,
  image base, and hashes. If it is not the expected binary, stop and ask the
  user to select/restart the correct target before analysis. For Xbox 360 XEX
  targets, verify Xenon/XEX loading rather than accepting a raw-binary fallback
  before trusting decompilation.
- Prefer address-based inspection for ambiguous names: use focused function,
  disassembly, decompilation, caller/callee, and xref tools. Keep result limits
  small and paginate only when needed to answer the current question.
- Ghidra MCP also exposes state-changing tools for renaming functions/data/
  variables, setting prototypes/types, and adding comments. Treat these as
  persistent project edits: do not call them unless the user explicitly asks
  for Ghidra annotations/changes. Graceful service shutdown saves annotations;
  force-killing the service can lose pending changes.
- Keep reverse-engineering conclusions tied to addresses and tool evidence.
  Separate observations from hypotheses; follow project-specific instructions
  for target hashes, memory scope, work-unit limits, and persistence.

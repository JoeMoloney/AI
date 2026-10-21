# AI Memory Usage

Use the `memory` MCP server as durable project knowledge.

- Search memory when starting substantial work, investigating a familiar
  issue, making an architectural decision, or when the user asks for a recap.
- Store only durable, reusable findings: confirmed root causes and fixes,
  architectural decisions, failed approaches, important commands, and useful
  file or function meanings.
- Do not store secrets, credentials, transient progress, speculation presented
  as fact, or information already present in repository documentation.
- Prefer updating or superseding stale knowledge rather than creating a
  contradictory duplicate.
- Treat duplicate candidates as a prompt to inspect existing knowledge; never
  silently merge or overwrite memories.

## Fable II Reverse Engineering

- The memory-service project `FableII` has **project ID 3**. This is a database
  namespace, independent of the Ghidra project name.
- Before a new Fable II analysis task, and after resuming or compacting a
  session, call `memory_search` with `projectId: 3`, `includeGlobal: false`, and
  a focused query. Start with `limit: 3`; retrieve more only when needed.
- Store game-specific findings with `scope: "PROJECT"` and `projectId: 3`.
  Never store Fable II code, function findings, or game-specific hypotheses as
  `GLOBAL`. For infrastructure tasks, keep using the general rules above.
- Separate observations from hypotheses and set confidence accordingly. Include
  the binary SHA-256, function/reference addresses, and supporting tool evidence
  when available; never invent missing identifiers or evidence.
- Save durable conclusions at task completion. Do not dump full chat histories
  or large decompilations into memory; keep notes focused and retrieve details
  from Ghidra when needed.

### Bounded work units

- Answer conceptual questions directly. Do not turn a file-format question into
  a broad investigation, recursive file scan, or native-code decompilation task.
- For an investigation, state one concrete objective and a completion condition.
  Split it into small items; inspect one function at a time and start reference,
  function, import, and string listings with `limit: 5`. Fetch further pages only
  when needed to resolve that item; do not assume a truncated preview is complete.
- Default each work unit to at most 3 functions and 12 tool calls unless the user
  explicitly sets another budget. Stop on completion, a blocker, or exhaustion;
  summarize evidence, uncertainty, and the next action rather than restarting
  the same investigation repeatedly.
- Reuse notes and already inspected results instead of repeatedly fetching
  unchanged code. Store concise durable conclusions in memory project 3. Track
  pending/completed work separately in a small user-approved task ledger, not
  as transient progress in the knowledge database.
- When verification of a conceptual answer is necessary, start with at most 2
  focused tool calls. Use small search-result limits where supported and read
  only relevant excerpts, rather than entire documents or directory inventories.
- After compaction, resume the same bounded objective from its ledger/notes.
  Do not restart all completed steps or dump the entire investigation history.
- The local Qwen router selects brief/analysis effort; its mode label is not
  evidence of correctness. Keep answers concise in either mode, and do not
  delegate to another model unless explicitly requested.

### Coordinator/worker batches

- Invoking `/re-work`, `/re-resume`, or explicitly selecting re-coordinator for
  this workflow authorizes sequential delegation to re-worker. Other agents
  and unrelated tasks still require explicit delegation permission.
- Keep the coordinator's context small: task status, target hash, memory IDs,
  and short results only. Full code/tool output stays in the worker's context.
- Progress lives in the ignored `.re-work/` ledger; knowledge lives in memory
  project 3. A fresh session must not reset completed work or invent missing IDs.
- The coordinator is planning/handoff work: use Brief effort. Workers may use
  Analysis for code interpretation. Worker completion is not a request to stop
  Ghidra, delete sessions, or execute/rebuild the game.

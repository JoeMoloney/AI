# Local Qwen RE mode router

OpenCode V2 discovers this repo-local plugin automatically. It applies only to
`ollama/qwen3.8:27b` sessions inside the loaded workspace. No global config,
cloud model, other Ollama model, Ghidra process, or game file is changed.

| Request | Thinking | Maximum generated tokens |
| --- | --- | ---: |
| Brief/conceptual | none | 1024 |
| Code analysis | medium | 4096 |
| Compaction | none | 4096 |
| Title/transient generation when hooked | none | 1536 |
| Auxiliary paths bypassing hooks | none | 4096 fallback |

Auto is the default. Classification is deliberately simple: conceptual/file
format questions remain brief; decompilation, addresses, callers, control flow,
and attached code select analysis. Direct Ghidra decompile/disassembly/call-graph
results can escalate the following step. Code Mode wrapper results may obscure
tool names, so the task's original intent is retained as a fallback.

Answers are instructed to show `[Brief]` or `[Analysis]`, but labels are model
output, not an authoritative indicator. Mode commands' last-routed status is
authoritative about the applied controls. Native request logs
show only mode and cap. No prompts, responses, code, or history are logged by
this plugin. Storage holds per-session override/intent enums and the last routed
request's mode/kind/token cap, never prompt or code text. Mode commands report
those numeric diagnostics when available.

## Overrides

- `/re-brief` — force brief until changed
- `/re-analyse` — force analysis until changed
- `/re-auto` — return to automatic selection

These commands do not switch models or launch subagents. Optional task arguments
are submitted normally with composer attachments preserved. Compaction/title
requests stay brief even when analysis is forced.

The native HTTP hook targets the local `/v1/chat/completions` endpoint and writes
Ollama's tested `reasoning_effort` and `max_tokens` fields. The project also aligns
Qwen's advertised output budget to 4096, without increasing its 32768 context.
Model-level request-body defaults (none/4096) protect auxiliary paths even when
the current OpenCode build bypasses session hooks. Primary routing carries its
decision in a request header and overrides those defaults. Transient SDK/API
generation is intentionally brief; use the interactive agent loop for analysis.
Larger tasks can hit the cap: narrow the objective instead of treating a partial
answer as complete. This router is not a permissions sandbox or a task scheduler.

Compaction needs enough space to finish a useful summary. The initial 1536-token
fallback caused real technical-session summaries to fail at the exact cap; it
was raised to 4096 without enabling thinking or changing primary reply budgets.
The summary guidance aims for 500-800 words, leaving room instead of demanding
lossless preservation of whole code dumps.

## Checks

From the repository root (Node 22.18+ or 24+):

```bash
npm --prefix .opencode install --ignore-scripts
npm --prefix .opencode run typecheck
npm --prefix .opencode test
```

Opt-in live checks (Ollama must be running):

```bash
python tests/re-mode-smoke.py
```

This creates a fresh synthetic OpenCode session with all tools denied. It checks
automatic/manual routing and a real compaction without accessing existing
conversations, game files, or game memories. Synthetic sessions remain available
for inspection; none of your existing sessions are cleared or rewritten.

Start a fresh local-model session for the first trial. Existing bloated sessions
are not rewritten or deleted. Configuration/plugin reload is normally automatic;
if commands are absent, reopen OpenCode before considering a service restart.

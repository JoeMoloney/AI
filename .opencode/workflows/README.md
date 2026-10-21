# Bounded local RE batches

This MVP uses a small coordinator and fresh worker sessions instead of one
ever-growing analysis chat. Both profiles specify local Qwen; the entry commands
also explicitly select it, because choosing an agent alone need not replace an
already selected session model. Cloud providers are not globally disabled.

Start a **new OpenCode session in this repository**, then:

```text
/re-work Investigate one small function and its immediate callers, recording evidence and uncertainty.
```

Supply a specific function address/objective when known. The coordinator creates
`.re-work/FableII-ledger.json` from the example and plans at most three tasks.
Workers are sequential, read-only toward Ghidra, and save knowledge to memory
project 3. They return small JSON handoffs with memory IDs, not pseudocode.

To resume in a new session:

```text
/re-resume
```

Or run a fresh command-line batch (do not use --continue):

```bash
opencode run --model ollama/qwen3.8:27b --agent re-coordinator \
  "Resume .re-work/FableII-ledger.json; process the next bounded batch."
```

The ledger is gitignored and holds goal, target hash, pending/completed task
cards, memory IDs, and next actions. Do not delete it to clear context. A fresh
coordinator can recover from it while Ghidra and memory remain running.

Suggested task shape:

```json
{
  "id": "T001",
  "objective": "One small concrete investigation",
  "completion_condition": "A specific evidence-backed answer",
  "status": "pending",
  "memory_ids": [],
  "summary": "",
  "next_action": ""
}
```

The parent patches running/done/partial/blocked state around each worker. A task
still running after interruption requires review; it is not automatically retried.
Child sessions finish and remain in OpenCode for audit. Their completion does
not stop Ghidra or automatically save its unsaved annotations.

## Limits

This is not an indefinitely running scheduler. Three tasks per batch, worker
model-step caps, narrow tool permissions, and short handoffs are the first
guardrails. Instructions about exact tool counts, task completeness, memory
namespace, or handoff length are still best-effort. Memory MCP itself currently
has no per-project authorization. Code Mode is not an OS/network sandbox.

Do not change the coordinator to a cloud model if results must remain local:
local-worker handoffs are returned to the parent. Use /re-work or the explicit
local CLI command, rather than relying on whichever model was last selected.

## Checks

Run the permission-order and pinned-project regression tests without starting
OpenCode or Ghidra:

```bash
python -m unittest discover -s tests -p 'test_re_permissions.py'
python -m unittest discover -s tests -p 'test_ghidra_runner.py'
```

Agent/command metadata and permissions can be inspected without game data. A
live smoke test must use an isolated `.re-work/smoke-*.json` ledger, explicitly
synthetic task cards, and session-wide denies for every memory/Ghidra action.
No real investigation is launched as part of setup verification.

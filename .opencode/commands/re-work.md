---
description: Run one bounded batch of fresh local RE workers using the task ledger
agent: re-coordinator
model: ollama/qwen3.8:27b
subagent: false
---

Coordinate this objective using `.re-work/FableII-ledger.json`:

$ARGUMENTS

Use a fresh re-worker for each small task, sequentially. Default to at most
three tasks this invocation. Save knowledge to memory project 3 and progress to
the ledger. Resume pending work if no new objective is given; do not replace an
existing goal or repeat completed tasks. Do not inspect game code yourself.

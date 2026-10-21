---
description: Resume the next bounded local RE batch from its ledger in this session
agent: re-coordinator
model: ollama/qwen3.8:27b
subagent: false
---

Resume `.re-work/FableII-ledger.json`. Read the ledger, retrieve only necessary
project-3 notes, and run the next pending task(s) with NEW re-worker children,
sequentially, at most three tasks. Do not restart completed tasks or erase the
ledger. Stop on a blocker/partial task and report the next action.

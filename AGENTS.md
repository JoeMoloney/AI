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

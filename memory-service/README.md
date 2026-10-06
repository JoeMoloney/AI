# AI Memory Service

Local durable memory for AI coding tools, backed by PostgreSQL, pgvector, and
Ollama `nomic-embed-text` embeddings.

## Run

Start PostgreSQL from the repository root, then start the service:

```bash
docker compose -f memory-db/docker-compose.yml up -d
cd memory-service
set -a
source ../memory-db/.pkey
set +a
./gradlew bootRun
```

The REST API is available at `http://127.0.0.1:8080/api` and the Streamable
HTTP MCP endpoint is `http://127.0.0.1:8080/mcp`. Both bind to localhost by
default. Set `SERVER_ADDRESS` only when intentionally exposing the service
through an appropriate security boundary.

The root `opencode.json` registers the MCP server with OpenCode. Restart
OpenCode after starting the service, then verify the connection with:

```bash
opencode mcp list
```

## MCP tools

- `memory_search` — hybrid semantic and full-text search
- `memory_store` — store memory with duplicate detection
- `memory_get` — retrieve a memory by ID
- `memory_update` — partially update an active memory
- `memory_supersede` — replace old knowledge with a newer memory

## Search and duplicate behavior

Search fuses pgvector and PostgreSQL full-text ranks using reciprocal-rank
fusion. Only active memories are returned, with project/global filtering and
confidence-aware deterministic ordering.

Before insertion, active memories in the same project and scope are compared
using cosine similarity. Matches at or above `0.95` return HTTP `409` and a
candidate list instead of silently overwriting knowledge. Configuration:

```text
MEMORY_DEDUPLICATION_ENABLED=true
MEMORY_DUPLICATE_THRESHOLD=0.95
MEMORY_DUPLICATE_CANDIDATE_LIMIT=3
```

## Test

```bash
./gradlew test --rerun-tasks
```

# AI Memory Service

Local durable memory for AI coding tools, backed by PostgreSQL, pgvector, and
Ollama `nomic-embed-text` embeddings.

## Documentation

- [Architecture and behavior](docs/ARCHITECTURE.md)
- [REST API reference](docs/REST_API.md)
- Generated Javadocs: run `./gradlew javadoc`, then open
  `build/docs/javadoc/index.html`

## Requirements

- Java 21
- PostgreSQL 17 with pgvector
- Ollama with `nomic-embed-text`

## Run

Start PostgreSQL from the repository root, then start the service:

```bash
./create-docker-network.sh
set -a
source memory-db/.env
source memory-db/.pkey
set +a
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

The root `opencode.json` targets the Docker deployment at port `18082`. If using
`bootRun` instead, set its memory MCP URL to `http://127.0.0.1:8080/mcp` locally.
After starting the service, verify the connection with:

```bash
opencode mcp list
```

## Run (Docker)

`memory-service` is part of the always-on trio at the repository root
(`ollama` + `memory-db` + `memory-service`). Start the whole stack with:

```bash
./start-services.sh   # Enter = start the always-on trio
```

Or start it manually from the repository root. Note: root-level `docker
compose` commands parse the **entire** compose file, so every env file
listed in `start-services.sh`'s `ENV_FILES` must be sourced first (the same
way its `load_env` does), not just `memory-db`'s:

```bash
./create-docker-network.sh
set -a
for f in memory-db/.env memory-db/.pkey open-webui/.env open-webui/.apiKey \
         searxng/.env comfyui/.env sillytavern/.env ghidra-mcp/.env; do
  source "$f"
done
set +a
docker compose up -d --build memory-service
```

Standalone from this directory (still needs `memory-db` and `ollama` on
`selfhost_network`):

```bash
../create-docker-network.sh
set -a
source ../memory-db/.pkey
set +a
docker compose up -d --build
```

From the host, the containerized REST API is at `http://127.0.0.1:18082/api` and the
Streamable HTTP MCP endpoint is `http://127.0.0.1:18082/mcp`, bound to
localhost only. Readiness probe:
`http://127.0.0.1:18082/actuator/health`.

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

Generate the code-level API documentation with:

```bash
./gradlew javadoc
```

# memory-db

Postgres + [pgvector](https://github.com/pgvector/pgvector) container that stores
LLM-backed semantic memory. It is a service on the shared selfhost stack
(always-on alongside `ollama`), not a standalone app.

- Image: `pgvector/pgvector:0.8.6-pg17`
- DB / user: `ai_memory` (set in `.env`)
- Embeddings: produced by Ollama's `nomic-embed-text` (768-dim), stored in the
  `embedding vector(768)` column with an HNSW cosine index.

## Password setup

Create a gitignored `.pkey` beside `.env` containing a shell-quoted value:

```bash
POSTGRES_PASSWORD='your_password_here'
```

Restrict it with `chmod 600 .pkey`. Keep credentials out of tracked files.
`.env` holds only non-secret config (`POSTGRES_DATA`, `POSTGRES_DB`,
`POSTGRES_USER`).

The root launcher exports `.pkey` for both PostgreSQL and the memory service.
The Python scripts read `POSTGRES_PASSWORD` from their environment and stop
with a clear error if it is missing or empty. Do not edit credentials into them.
Changing `.pkey` does not rotate the password in an existing PostgreSQL data
directory; coordinate any database password change separately.

## Other things to set

- `POSTGRES_DATA` in `.env` points at the host directory where the database is
  persisted. Change it to a path that exists on this host (it uses a `~/` path).
- Ollama must be reachable and have the embedding model pulled:
  `ollama pull nomic-embed-text`. The scripts target `http://localhost:11434`.

## Running the scripts

The Python scripts need the `psycopg` driver. From this directory, export the
configuration before running them with the local virtualenv (`.venv`):

```bash
set -a
source ./.env
source ./.pkey
set +a
./.venv/bin/python embed-memories.py
./.venv/bin/python search_memory.py
```

Both scripts also accept `POSTGRES_HOST` and `POSTGRES_PORT` (defaults:
`127.0.0.1` and `5432`). `search_memory.py` currently uses its hardcoded sample
query; command-line query arguments are not implemented.

If you don't have `.venv`, create it and install the driver, e.g.
`python -m venv .venv && ./.venv/bin/pip install "psycopg[binary]"`.

## Note on the embedding prefix

`embed-memories.py` prefixes stored text with
`search_document:` and `search_memory.py` prefixes the query with
`search_query:`. Nomic-embed-text best-practice is `passage:` for stored text
and `search_query:` for the query; it works either way, but re-embedding the
existing memories with `passage:` can tighten retrieval if you ever notice
off results.

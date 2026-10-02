# memory-db

Postgres + [pgvector](https://github.com/pgvector/pgvector) container that stores
LLM-backed semantic memory. It is a service on the shared selfhost stack
(always-on alongside `ollama`), not a standalone app.

- Image: `pgvector/pgvector:0.8.6-pg17`
- DB / user: `ai_memory` (set in `.env`)
- Embeddings: produced by Ollama's `nomic-embed-text` (768-dim), stored in the
  `embedding vector(768)` column with an HNSW cosine index.

## Passwords you must set (do this first)

The password is **not shipped** in this repo. It appears in exactly three
places, and all three **must contain the same value**.

| # | File | What to change | Shipped in repo? |
|---|------|----------------|------------------|
| 1 | `.env` → (next to it) `.pkey` | Create a file named `.pkey` (gitignored) containing `POSTGRES_PASSWORD=<your_password>` | No — **you create it** |
| 2 | `embed-memories.py` | Line `password=YOUR_PASSWORD` (in `DB_DSN`) → your password | Yes — replace the placeholder |
| 3 | `search_memory.py` | Line `password=YOUR_PASSWORD` (in `DB_DSN`) → your password | Yes — replace the placeholder |

`.env` is tracked and holds only the **non-secret** config (`POSTGRES_DATA`,
`POSTGRES_DB`, `POSTGRES_USER`). The password deliberately lives in the separate
`.pkey` file, which `.gitignore` excludes — so it never enters the repo.

**Why all three must match:**

- `docker-compose.yml` injects `POSTGRES_PASSWORD` into the Postgres container
  from the environment. The parent `start-services.sh` loads `.pkey` into the
  environment (`source .pkey`) before calling `docker compose`, so **`.pkey` is
  the source of truth** that provisions the database.
- The two Python scripts do **not** read `.pkey`; they hard-code the credential
  in their `DB_DSN` string. They connect to the running database and will fail
  to authenticate unless that value equals the one from `.pkey`.

So: pick one password, put it in `.pkey`, and replace `YOUR_PASSWORD` in both
`embed-memories.py` and `search_memory.py` with that same value. If you change
it later, update all three to stay in sync.

## Other things to set

- `POSTGRES_DATA` in `.env` points at the host directory where the database is
  persisted. Change it to a path that exists on this host (it uses a `~/` path).
- Ollama must be reachable and have the embedding model pulled:
  `ollama pull nomic-embed-text`. The scripts target `http://localhost:11434`.

## Running the scripts

The Python scripts need the `psycopg` driver. A local virtualenv (`.venv`,
gitignored) is the intended interpreter, so run them as:

```
./.venv/bin/python embed-memories.py
./.venv/bin/python search_memory.py "<query text>"
```

If you don't have `.venv`, create it and install the driver, e.g.
`python -m venv .venv && ./.venv/bin/pip install "psycopg[binary]"`.

## Note on the embedding prefix

`embed-memories.py` prefixes stored text with
`search_document:` and `search_memory.py` prefixes the query with
`search_query:`. Nomic-embed-text best-practice is `passage:` for stored text
and `search_query:` for the query; it works either way, but re-embedding the
existing memories with `passage:` can tighten retrieval if you ever notice
off results.

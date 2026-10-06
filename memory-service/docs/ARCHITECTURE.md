# Memory Service Architecture

## Purpose

The memory service gives local AI tools durable, searchable knowledge without
sending that knowledge to a hosted embedding provider. It stores structured
memories in PostgreSQL, generates embeddings with local Ollama, and exposes the
same behavior through REST and Model Context Protocol (MCP).

The service is deliberately conservative:

- stored knowledge is never silently overwritten;
- only active memories appear in search results;
- lifecycle transitions are explicit;
- likely duplicates produce a conflict for the caller to resolve;
- REST and MCP both reuse the same service-layer business rules.

## System overview

```text
OpenCode or another MCP client ──┐
                                 ├──> service layer ──> JdbcTemplate ──> PostgreSQL/pgvector
REST client ──> controllers ─────┘          │
                                            └──> Ollama /api/embed
```

| Layer | Responsibility |
|---|---|
| `controller` | HTTP routing, request validation, and status codes |
| `mcp` | Small AI-tool interface that delegates to services |
| `service` | Validation, lifecycle rules, embedding semantics, and DTO mapping |
| `repository` | SQL, row mapping, vector queries, and atomic database updates |
| `client` | Ollama HTTP communication and embedding validation |
| `domain` | Internal persistence-oriented records and enums |
| `dto` | REST and MCP request/response contracts |
| Flyway migrations | Authoritative database schema |

The project intentionally uses Spring JDBC rather than JPA. SQL remains
visible because vector distance, full-text ranking, generated `tsvector`
columns, and lifecycle updates are central to the application.

## Core data model

A memory contains:

- optional project and source IDs;
- a scope (`GLOBAL`, `PROJECT`, or `SESSION`);
- a memory type such as `FACT`, `DECISION`, `FAILURE`, or `LESSON`;
- title, content, optional evidence, and confidence;
- lifecycle status and optional replacement memory ID;
- a 768-dimensional embedding;
- creation and update timestamps.

API enums are uppercase. Their database representations are lowercase and are
converted explicitly by each enum's `databaseValue()` and
`fromDatabaseValue()` methods.

### Scope rules

| Scope | Project ID | Search visibility |
|---|---|---|
| `GLOBAL` | Must be absent | Included when `includeGlobal` is true |
| `PROJECT` | Required | Visible to searches for that project |
| `SESSION` | Required | Currently searched with its project |

## Embeddings

`MemoryEmbeddingTextBuilder` creates one canonical document from the title,
content, and optional evidence:

```text
Title: ...

Content:
...

Evidence:
...
```

`EmbeddingService` then applies the prefixes recommended for
`nomic-embed-text`:

- stored memories: `search_document:`
- search queries: `search_query:`

`OllamaClient` calls `POST /api/embed` and rejects responses that do not contain
exactly one 768-dimensional embedding. Embeddings are persistence details and
are never included in API responses.

## Memory creation and deduplication

Creation follows this sequence:

1. Validate scope, project, source, and required text.
2. Normalize title, content, evidence, and default confidence to `MEDIUM`.
3. Build the canonical embedding text and request an Ollama embedding.
4. Compare it with active memories in the same project and exact scope.
5. Return `409 Conflict` when any candidate meets the configured threshold.
6. Otherwise insert the active memory and embedding.

The default duplicate threshold is `0.95`. A conflict includes candidate IDs,
titles, and similarities. The caller decides whether to use, update,
supersede, or intentionally distinguish the existing knowledge.

The similarity check and insert are separate operations, so this feature is a
knowledge-quality guard rather than a strict uniqueness constraint under
concurrent writes.

## Hybrid search

Search embeds the query and sends both its text and vector to PostgreSQL.
`MemoryRepository.hybridSearch` creates two candidate rankings:

1. cosine similarity using pgvector's `<=>` operator;
2. PostgreSQL full-text relevance using the generated `search_document`
   `tsvector` column.

The rankings are combined with reciprocal-rank fusion (RRF):

```text
score = 1 / (60 + vector_rank) + 1 / (60 + text_rank)
```

Missing ranks contribute zero. RRF avoids directly comparing cosine similarity
with `ts_rank_cd`, whose numeric scales have different meanings. Confidence,
project scope, and ID provide understandable deterministic tie-breaking.

Only `ACTIVE` memories are candidates. Invalidated, archived, and superseded
memories therefore remain auditable by ID but disappear from retrieval.

## Lifecycle

```text
                 ┌──> INVALIDATED
ACTIVE ──────────├──> ARCHIVED
                 └──> SUPERSEDED ──> replacement ACTIVE memory
```

Only active memories can be edited or transitioned. There is no transition
back to active.

- **Update:** null fields are unchanged. Blank evidence clears evidence.
  Blank title or content is rejected.
- **Embedding regeneration:** title, content, or evidence changes regenerate
  the vector. Type- or confidence-only changes preserve it.
- **Supersession:** old and replacement memories must differ, be active, and
  have the same project and scope. The repository performs both updates in one
  SQL statement inside a transaction.

## MCP integration

Spring AI exposes Streamable HTTP MCP at `/mcp`. The tool surface is kept small
to reduce model context usage:

| Tool | Delegates to |
|---|---|
| `memory_search` | `MemorySearchService` |
| `memory_store` | `MemoryService` |
| `memory_get` | `MemoryService` |
| `memory_update` | `MemoryLifecycleService` |
| `memory_supersede` | `MemoryLifecycleService` |

The server binds to `127.0.0.1` by default and has no authentication layer.
Do not expose it to another network without adding a security boundary.

## Database migrations

Flyway owns the schema:

| Migration | Purpose |
|---|---|
| `V1__enable_vector.sql` | Enables pgvector |
| `V2__create_memory_schema.sql` | Creates projects, sources, memories, relationships, indexes, and triggers |
| `V3__add_embeddings.sql` | Adds 768-dimensional vectors and the vector index |

Do not duplicate schema creation in Docker initialization scripts. Add future
schema changes as new immutable migrations.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ai_memory` | JDBC URL |
| `DB_USER` | `ai_memory` | Database user |
| `POSTGRES_PASSWORD` | none | Required database password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama endpoint |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model |
| `OLLAMA_TIMEOUT` | `60s` | Connect/read timeout |
| `MEMORY_DEDUPLICATION_ENABLED` | `true` | Enables duplicate checks |
| `MEMORY_DUPLICATE_THRESHOLD` | `0.95` | Minimum cosine similarity |
| `MEMORY_DUPLICATE_CANDIDATE_LIMIT` | `3` | Maximum conflict candidates |
| `SERVER_ADDRESS` | `127.0.0.1` | HTTP bind address |

## Testing strategy

Unit and MVC slice tests cover validation, service rules, endpoint mappings,
embedding semantics, lifecycle behavior, deduplication, and MCP delegation.
The Ollama integration test is opt-in so normal test runs do not require the
model server. Live verification is still valuable for pgvector SQL, ranking,
and MCP protocol behavior.

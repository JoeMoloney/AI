# REST API Reference

Base URL: `http://127.0.0.1:8080/api`

Enum values use uppercase JSON strings. Timestamps use ISO-8601 with an offset.
The examples assume project ID `1` exists.

## Projects

### Create a project

```http
POST /projects
Content-Type: application/json
```

```json
{
  "name": "local-ai-stack",
  "description": "Local AI infrastructure"
}
```

Returns `201 Created`. Duplicate names return `409 Conflict`.

### List projects

```http
GET /projects
```

## Memories

### Store a memory

```http
POST /memories
Content-Type: application/json
```

```json
{
  "projectId": 1,
  "scope": "PROJECT",
  "memoryType": "FAILURE",
  "title": "Large context caused model slowdown",
  "content": "A very large context increased CPU usage during long sessions.",
  "confidence": "HIGH",
  "evidence": "Reducing the context restored responsiveness."
}
```

Returns `201 Created`. `sourceId`, `confidence`, and `evidence` are optional;
confidence defaults to `MEDIUM`. A likely duplicate returns `409 Conflict`:

```json
{
  "title": "Likely duplicate memory",
  "status": 409,
  "candidates": [
    {
      "id": 7,
      "title": "Existing lesson",
      "similarity": 0.98
    }
  ]
}
```

### Retrieve a memory

```http
GET /memories/{id}
```

Retrieval by ID includes memories in any lifecycle state.

### Search memories

```http
POST /memories/search
Content-Type: application/json
```

```json
{
  "query": "Why did long sessions become slow?",
  "projectId": 1,
  "includeGlobal": true,
  "limit": 5
}
```

- `includeGlobal` defaults to `true`.
- `limit` defaults to `5` and must be between 1 and 20.
- Omitting `projectId` performs a global-only search.
- `projectId: null` with `includeGlobal: false` is invalid.
- Results contain a cosine `similarity`; ordering uses the combined hybrid
  rank rather than similarity alone.

### Partially update a memory

```http
PATCH /memories/{id}
Content-Type: application/json
```

```json
{
  "content": "Updated durable knowledge.",
  "confidence": "CONFIRMED"
}
```

Only active memories can be updated. Null or omitted fields are unchanged.
Sending `"evidence": ""` clears evidence. Content, title, or evidence changes
regenerate the embedding.

### Invalidate a memory

```http
POST /memories/{id}/invalidate
```

Use this when the knowledge is known to be incorrect.

### Archive a memory

```http
POST /memories/{id}/archive
```

Use this when the knowledge is no longer relevant but is not necessarily
incorrect.

### Supersede a memory

```http
POST /memories/{id}/supersede/{replacementId}
```

The memories must both be active and share project and scope. The old memory
becomes `SUPERSEDED` and records the replacement ID.

## Common response fields

```json
{
  "id": 7,
  "projectId": 1,
  "sourceId": null,
  "scope": "PROJECT",
  "memoryType": "LESSON",
  "title": "Example",
  "content": "Durable knowledge",
  "confidence": "HIGH",
  "status": "ACTIVE",
  "evidence": null,
  "supersededBy": null,
  "createdAt": "2026-10-06T20:00:00Z",
  "updatedAt": "2026-10-06T20:00:00Z"
}
```

Search results omit lifecycle timestamps and status because only active
memories are searchable, and add `similarity`.

## Errors

Errors use Spring `ProblemDetail` JSON.

| Status | Meaning |
|---|---|
| `400` | Invalid JSON fields, scope combination, lifecycle operation, or limit |
| `404` | Project, source, or memory does not exist |
| `409` | Duplicate project or likely duplicate memory |
| `502` | Ollama is unavailable or returned an invalid response |

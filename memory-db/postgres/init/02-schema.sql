-- ============================================================
-- AI Memory relational schema
-- PostgreSQL 17
-- ============================================================


-- ------------------------------------------------------------
-- Projects
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS projects (
    id              BIGSERIAL PRIMARY KEY,
    name            TEXT NOT NULL UNIQUE,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- ------------------------------------------------------------
-- Sources
--
-- A source tells us where a memory came from.
-- This is intentionally flexible because memories may originate
-- from files, terminals, Ghidra, conversations, documentation,
-- web research, manual notes, etc.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sources (
    id              BIGSERIAL PRIMARY KEY,

    project_id      BIGINT REFERENCES projects(id)
                    ON DELETE SET NULL,

    source_type     TEXT NOT NULL,
    source_ref      TEXT,
    description     TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT sources_source_type_check
        CHECK (
            source_type IN (
                'file',
                'terminal',
                'ghidra',
                'conversation',
                'documentation',
                'web',
                'manual',
                'system',
                'other'
            )
        )
);


-- ------------------------------------------------------------
-- Memories
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS memories (
    id                  BIGSERIAL PRIMARY KEY,

    -- NULL means this can be global rather than belonging
    -- exclusively to a project.
    project_id          BIGINT REFERENCES projects(id)
                        ON DELETE SET NULL,

    source_id           BIGINT REFERENCES sources(id)
                        ON DELETE SET NULL,

    -- global:
    --   reusable across projects
    --
    -- project:
    --   specific to a particular project
    --
    -- session:
    --   potentially temporary/short-lived
    scope               TEXT NOT NULL DEFAULT 'project',

    memory_type         TEXT NOT NULL,

    title               TEXT NOT NULL,
    content             TEXT NOT NULL,

    -- Useful for uncertain reverse-engineering discoveries,
    -- troubleshooting hypotheses, etc.
    confidence          TEXT NOT NULL DEFAULT 'medium',

    -- Controls whether this memory should normally be retrieved.
    status              TEXT NOT NULL DEFAULT 'active',

    -- Optional explanation/evidence supporting the memory.
    evidence            TEXT,

    -- If a newer memory replaces this one, point to it.
    superseded_by       BIGINT REFERENCES memories(id)
                        ON DELETE SET NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT memories_scope_check
        CHECK (
            scope IN (
                'global',
                'project',
                'session'
            )
        ),

    CONSTRAINT memories_type_check
        CHECK (
            memory_type IN (
                'fact',
                'discovery',
                'decision',
                'bug',
                'failure',
                'success',
                'workaround',
                'command',
                'architecture',
                'hypothesis',
                'lesson',
                'file_purpose',
                'function_meaning'
            )
        ),

    CONSTRAINT memories_confidence_check
        CHECK (
            confidence IN (
                'hypothesis',
                'low',
                'medium',
                'high',
                'confirmed'
            )
        ),

    CONSTRAINT memories_status_check
        CHECK (
            status IN (
                'active',
                'superseded',
                'invalidated',
                'archived'
            )
        )
);


-- ------------------------------------------------------------
-- Memory relationships
--
-- This lets us represent:
--
-- memory A CAUSED_BY memory B
-- memory A SOLVED_BY memory C
-- memory X RELATED_TO memory Y
--
-- without needing a graph database.
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS memory_relationships (
    id                  BIGSERIAL PRIMARY KEY,

    from_memory_id      BIGINT NOT NULL
                        REFERENCES memories(id)
                        ON DELETE CASCADE,

    relation_type       TEXT NOT NULL,

    to_memory_id        BIGINT NOT NULL
                        REFERENCES memories(id)
                        ON DELETE CASCADE,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT memory_relationships_relation_type_check
        CHECK (
            relation_type IN (
                'related_to',
                'caused_by',
                'solved_by',
                'depends_on',
                'supports',
                'contradicts',
                'derived_from',
                'supersedes',
                'calls',
                'reads',
                'writes',
                'implements',
                'belongs_to'
            )
        ),

    CONSTRAINT memory_relationships_no_self_reference
        CHECK (from_memory_id <> to_memory_id),

    CONSTRAINT memory_relationships_unique
        UNIQUE (
            from_memory_id,
            relation_type,
            to_memory_id
        )
);


-- ------------------------------------------------------------
-- Useful indexes
-- ------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_projects_name
    ON projects(name);

CREATE INDEX IF NOT EXISTS idx_sources_project_id
    ON sources(project_id);

CREATE INDEX IF NOT EXISTS idx_sources_source_type
    ON sources(source_type);

CREATE INDEX IF NOT EXISTS idx_memories_project_id
    ON memories(project_id);

CREATE INDEX IF NOT EXISTS idx_memories_scope
    ON memories(scope);

CREATE INDEX IF NOT EXISTS idx_memories_memory_type
    ON memories(memory_type);

CREATE INDEX IF NOT EXISTS idx_memories_confidence
    ON memories(confidence);

CREATE INDEX IF NOT EXISTS idx_memories_status
    ON memories(status);

CREATE INDEX IF NOT EXISTS idx_memories_source_id
    ON memories(source_id);

CREATE INDEX IF NOT EXISTS idx_memories_created_at
    ON memories(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_memories_updated_at
    ON memories(updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_relationships_from_memory
    ON memory_relationships(from_memory_id);

CREATE INDEX IF NOT EXISTS idx_relationships_to_memory
    ON memory_relationships(to_memory_id);

CREATE INDEX IF NOT EXISTS idx_relationships_relation_type
    ON memory_relationships(relation_type);


-- ------------------------------------------------------------
-- Full-text search support
--
-- This gives us useful keyword/text search before pgvector
-- enters the picture.
-- ------------------------------------------------------------

ALTER TABLE memories
    ADD COLUMN IF NOT EXISTS search_document TSVECTOR
    GENERATED ALWAYS AS (
        to_tsvector(
            'english',
            COALESCE(title, '') || ' ' ||
            COALESCE(content, '') || ' ' ||
            COALESCE(evidence, '')
        )
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_memories_search_document
    ON memories
    USING GIN (search_document);


-- ------------------------------------------------------------
-- Automatically maintain updated_at
-- ------------------------------------------------------------

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


DROP TRIGGER IF EXISTS trg_projects_updated_at
ON projects;

CREATE TRIGGER trg_projects_updated_at
BEFORE UPDATE ON projects
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();


DROP TRIGGER IF EXISTS trg_memories_updated_at
ON memories;

CREATE TRIGGER trg_memories_updated_at
BEFORE UPDATE ON memories
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
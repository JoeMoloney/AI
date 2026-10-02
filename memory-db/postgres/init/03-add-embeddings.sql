ALTER TABLE memories
    ADD COLUMN IF NOT EXISTS embedding vector(768);

CREATE INDEX IF NOT EXISTS idx_memories_embedding_hnsw
    ON memories
    USING hnsw (embedding vector_cosine_ops);
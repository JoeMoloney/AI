ALTER TABLE memories
    ADD COLUMN embedding vector(768);

CREATE INDEX idx_memories_embedding_hnsw
    ON memories
    USING hnsw (embedding vector_cosine_ops);
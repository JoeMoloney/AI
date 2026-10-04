package dev.joe.aimemoryservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import dev.joe.aimemoryservice.domain.Memory;
import dev.joe.aimemoryservice.domain.ScoredMemory;
import dev.joe.aimemoryservice.domain.enums.Confidence;
import dev.joe.aimemoryservice.domain.enums.MemoryScope;
import dev.joe.aimemoryservice.domain.enums.MemoryStatus;
import dev.joe.aimemoryservice.domain.enums.MemoryType;

@Repository
public class MemoryRepository {
    private static final int EMBEDDING_DIMENSIONS = 768;

    private static final RowMapper<Memory> MEMORY_ROW_MAPPER = (resultSet, rowNumber) -> new Memory(
        resultSet.getLong("id"),
        resultSet.getObject("project_id", Long.class),
        resultSet.getObject("source_id", Long.class),
        MemoryScope.fromDatabaseValue(resultSet.getString("scope")),
        MemoryType.fromDatabaseValue(resultSet.getString("memory_type")),
        resultSet.getString("title"),
        resultSet.getString("content"),
        Confidence.fromDatabaseValue(resultSet.getString("confidence")),
        MemoryStatus.fromDatabaseValue(resultSet.getString("status")),
        resultSet.getString("evidence"),
        resultSet.getObject("superseded_by", Long.class),
        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
    );

    private static final RowMapper<ScoredMemory> SCORED_MEMORY_ROW_MAPPER = (resultSet, rowNumber) -> new ScoredMemory(
        MEMORY_ROW_MAPPER.mapRow(resultSet, rowNumber),
        resultSet.getDouble("similarity")
    );

    private final JdbcTemplate jdbcTemplate;

    public MemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Memory insert(
        Long projectId,
        Long sourceId,
        MemoryScope scope,
        MemoryType memoryType,
        String title,
        String content,
        Confidence confidence,
        MemoryStatus status,
        String evidence,
        float[] embedding
    ) {
        String sql = """
                INSERT INTO memories (
                    project_id,
                    source_id,
                    scope,
                    memory_type,
                    title,
                    content,
                    confidence,
                    status,
                    evidence,
                    embedding
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                RETURNING
                    id,
                    project_id,
                    source_id,
                    scope,
                    memory_type,
                    title,
                    content,
                    confidence,
                    status,
                    evidence,
                    superseded_by,
                    created_at,
                    updated_at
                """;

            return jdbcTemplate.queryForObject(
                sql,
                MEMORY_ROW_MAPPER,
                projectId,
                sourceId,
                scope.databaseValue(),
                memoryType.databaseValue(),
                title,
                content,
                confidence.databaseValue(),
                status.databaseValue(),
                evidence,
                toVectorLiteral(embedding)
            ); 
    }

    public Optional<Memory> findById(long id) {
        String sql = """
                SELECT
                    id,
                    project_id,
                    source_id,
                    scope,
                    memory_type,
                    title,
                    content,
                    confidence,
                    status,
                    evidence,
                    superseded_by,
                    created_at,
                    updated_at
                FROM memories
                WHERE id = ?
                """;

        return jdbcTemplate.query(
            sql,
            MEMORY_ROW_MAPPER,
            id
        ).stream().findFirst();
    }

    public List<ScoredMemory> semanticSearch(float[] queryEmbedding, Long projectId, boolean includeGlobal, int limit) {
        String sql = """
            WITH search_query AS (
                SELECT CAST(? AS vector) AS embedding
            )
            SELECT
                m.id,
                m.project_id,
                m.source_id,
                m.scope,
                m.memory_type,
                m.title,
                m.content,
                m.confidence,
                m.status,
                m.evidence,
                m.superseded_by,
                m.created_at,
                m.updated_at,
                1 - (
                    m.embedding <=> search_query.embedding
                ) AS similarity
            FROM memories m
            CROSS JOIN search_query
            WHERE m.status = 'active'
              AND m.embedding IS NOT NULL
              AND (
                  (
                      m.project_id = ?
                      AND m.scope IN ('project', 'session')
                  )
                  OR (
                      ?
                      AND m.scope = 'global'
                  )
              )
            ORDER BY
                m.embedding <=> search_query.embedding
            LIMIT ?
            """;

        return jdbcTemplate.query(
            sql,
            SCORED_MEMORY_ROW_MAPPER,
            toVectorLiteral(queryEmbedding),
            projectId,
            includeGlobal,
            limit
        );
    }

    private static String toVectorLiteral(float[] embedding) {
        if(embedding == null)
            throw new IllegalArgumentException("Embedding must not be null");

        if(embedding.length != EMBEDDING_DIMENSIONS)
            throw new IllegalArgumentException("Expected an embedding with "+EMBEDDING_DIMENSIONS+" dimensions, but received: "+embedding.length);
        
        StringBuilder vector = new StringBuilder("[");

        for(int i = 0; i < embedding.length; i++) {
            float value = embedding[i];

            if(!Float.isFinite(value))
                throw new IllegalArgumentException("Embedding contains a non-finite value at index: "+i);

            if(i > 0)
                vector.append(",");
            vector.append(value);
        }

        return vector.append("]").toString();
    }
}

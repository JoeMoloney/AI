package dev.joe.aimemoryservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import dev.joe.aimemoryservice.domain.Memory;
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
        resultSet.getObject("create_at", java.time.OffsetDateTime.class),
        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
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

    private static String toVectorLiteral(float[] embedding) {
        if(embedding == null)
            throw new IllegalArgumentException("Embedding must not be null");

        if(embedding.length != EMBEDDING_DIMENSIONS)
            throw new IllegalArgumentException("Expected an embedding with "+EMBEDDING_DIMENSIONS+" dimensions, but recieved: "+embedding.length);
        
        StringBuilder vector = new StringBuilder("[");

        for(int i = 0; i < embedding.length; i++) {
            float value = embedding[i];
            if(!Float.isFinite(value))
                throw new IllegalArgumentException("Embedding contains a non-finite value at index: "+i);

            if(i > 0)
                vector.append(value);
        }

        return vector.append("]").toString();
    }
}

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

    public List<ScoredMemory> hybridSearch(
        String query,
        float[] queryEmbedding,
        Long projectId,
        boolean includeGlobal,
        int limit
    ) {
        int candidateLimit = Math.max(20, limit * 4);
        String sql = """
            WITH params AS (
                SELECT
                    CAST(? AS vector) AS query_embedding,
                    websearch_to_tsquery('english', ?) AS text_query
            ),
            vector_ranked AS (
                SELECT
                    m.id,
                    1 - (m.embedding <=> p.query_embedding) AS similarity,
                    ROW_NUMBER() OVER (
                        ORDER BY m.embedding <=> p.query_embedding
                    ) AS vector_rank
                FROM memories m
                CROSS JOIN params p
                WHERE m.status = 'active'
                  AND m.embedding IS NOT NULL
                  AND (
                      (m.project_id = ? AND m.scope IN ('project', 'session'))
                      OR (? AND m.scope = 'global')
                  )
                ORDER BY m.embedding <=> p.query_embedding
                LIMIT ?
            ),
            text_ranked AS (
                SELECT
                    m.id,
                    ROW_NUMBER() OVER (
                        ORDER BY ts_rank_cd(m.search_document, p.text_query) DESC
                    ) AS text_rank
                FROM memories m
                CROSS JOIN params p
                WHERE m.status = 'active'
                  AND m.search_document @@ p.text_query
                  AND (
                      (m.project_id = ? AND m.scope IN ('project', 'session'))
                      OR (? AND m.scope = 'global')
                  )
                ORDER BY ts_rank_cd(m.search_document, p.text_query) DESC
                LIMIT ?
            ),
            ranked AS (
                SELECT
                    COALESCE(v.id, t.id) AS id,
                    v.similarity,
                    COALESCE(1.0 / (60 + v.vector_rank), 0.0)
                        + COALESCE(1.0 / (60 + t.text_rank), 0.0) AS rrf_score
                FROM vector_ranked v
                FULL OUTER JOIN text_ranked t ON t.id = v.id
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
                COALESCE(
                    r.similarity,
                    1 - (m.embedding <=> p.query_embedding)
                ) AS similarity
            FROM ranked r
            JOIN memories m ON m.id = r.id
            CROSS JOIN params p
            ORDER BY
                r.rrf_score DESC,
                CASE m.confidence
                    WHEN 'confirmed' THEN 5
                    WHEN 'high' THEN 4
                    WHEN 'medium' THEN 3
                    WHEN 'low' THEN 2
                    ELSE 1
                END DESC,
                CASE WHEN m.project_id IS NOT DISTINCT FROM ? THEN 1 ELSE 0 END DESC,
                m.id DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(
            sql,
            SCORED_MEMORY_ROW_MAPPER,
            toVectorLiteral(queryEmbedding),
            query,
            projectId,
            includeGlobal,
            candidateLimit,
            projectId,
            includeGlobal,
            candidateLimit,
            projectId,
            limit
        );
    }

    public List<ScoredMemory> findDuplicateCandidates(
        float[] embedding,
        Long projectId,
        MemoryScope scope,
        double similarityThreshold,
        int limit
    ) {
        String sql = """
            WITH candidate AS (
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
                1 - (m.embedding <=> candidate.embedding) AS similarity
            FROM memories m
            CROSS JOIN candidate
            WHERE m.status = 'active'
              AND m.embedding IS NOT NULL
              AND m.scope = ?
              AND m.project_id IS NOT DISTINCT FROM ?
              AND 1 - (m.embedding <=> candidate.embedding) >= ?
            ORDER BY m.embedding <=> candidate.embedding, m.id DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(
            sql,
            SCORED_MEMORY_ROW_MAPPER,
            toVectorLiteral(embedding),
            scope.databaseValue(),
            projectId,
            similarityThreshold,
            limit
        );
    }

    public Optional<Memory> updateActive(long id, MemoryType memoryType, String title, String content, Confidence confidence, String evidence, float[] replacementEmbedding) {
        String sql = """
            UPDATE memories
            SET
                memory_type = ?,
                title = ?,
                content = ?,
                confidence = ?,
                evidence = ?,
                embedding = COALESCE(
                    CAST(? AS vector),
                    embedding
                )
            WHERE id = ?
              AND status = 'active'
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

        String vector = replacementEmbedding == null ? null : toVectorLiteral(replacementEmbedding);

        return jdbcTemplate.query(
            sql,
            MEMORY_ROW_MAPPER,
            memoryType.databaseValue(),
            title,
            content,
            confidence.databaseValue(),
            evidence,
            vector,
            id
        ).stream().findFirst();
    }

    public Optional<Memory> transitionActiveStatus(long id, MemoryStatus newStatus, Long supersededBy) {
        String sql = """
            UPDATE memories
            SET
                status = ?,
                superseded_by = ?
            WHERE id = ?
              AND status = 'active'
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
        
            return jdbcTemplate.query(
                sql,
                MEMORY_ROW_MAPPER,
                newStatus.databaseValue(),
                supersededBy,
                id
            ).stream().findFirst();
    }

    public List<Memory> supersede(long oldMemoryId, long replacementMemoryId) {
        String sql = """
            UPDATE memories
            SET
                status = CASE
                    WHEN id = ? THEN 'superseded'
                    ELSE 'active'
                END,
                superseded_by = CASE
                    WHEN id = ? THEN ?
                    ELSE NULL
                END
            WHERE id IN (?, ?)
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
        
        return jdbcTemplate.query(
            sql,
            MEMORY_ROW_MAPPER,
            oldMemoryId,
            oldMemoryId,
            replacementMemoryId,
            oldMemoryId,
            replacementMemoryId
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

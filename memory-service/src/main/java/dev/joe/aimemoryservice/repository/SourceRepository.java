package dev.joe.aimemoryservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository 
public class SourceRepository {
    private final JdbcTemplate jdbcTemplate;

    public SourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsById(long id) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM sources
                    WHERE id = ?
                )
                """;
        
        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            id
        );

        return Boolean.TRUE.equals(exists);
    }

    public boolean isCompatibleWithProject(long sourceId, Long projectId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM sources
                    WHERE id = ?
                        AND (
                            project_id IS NULL
                            OR project_id = ?
                        )
                )
                """;
        Boolean compatible = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            sourceId,
            projectId
        );

        return Boolean.TRUE.equals(compatible);
    }
}

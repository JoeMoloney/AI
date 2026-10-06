package dev.joe.aimemoryservice.repository;

import dev.joe.aimemoryservice.domain.Project;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Performs Spring JDBC persistence and lookup operations for projects. */
@Repository
public class ProjectRepository {

    private static final RowMapper<Project> PROJECT_ROW_MAPPER = (resultSet, rowNumber) -> new Project(
        resultSet.getLong("id"),
        resultSet.getString("name"),
        resultSet.getString("description"),
        resultSet.getObject("created_at", java.time.OffsetDateTime.class),
        resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Project create(String name, String description) {
        String sql = """
                INSERT INTO projects (name, description)
                VALUES (?, ?)
                RETURNING id, name, description, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(
            sql,
            PROJECT_ROW_MAPPER,
            name,
            description
        );
    }

    public Optional<Project> findById(long id) {
        String sql = """
                SELECT id, name, description, created_at, updated_at
                FROM projects
                WHERE id = ?
                """;
        return jdbcTemplate.query(
            sql,
            PROJECT_ROW_MAPPER,
            id
        ).stream().findFirst();
    }

    public Optional<Project> findByName(String name) {
        String sql = """
                SELECT id, name, description, created_at, updated_at
                FROM projects
                WHERE name = ?
                """;
        return jdbcTemplate.query(
            sql,
            PROJECT_ROW_MAPPER,
            name
        ).stream().findFirst();
    }

    public List<Project> findAll() {
        String sql = """
                SELECT id, name, description, created_at, updated_at
                FROM projects
                ORDER BY name
                """;
        return jdbcTemplate.query(sql, PROJECT_ROW_MAPPER);
    }

    public boolean existsByName(String name) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM projects
                    WHERE name = ?
                )
                """;
        Boolean exists = jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            name
        );

        return Boolean.TRUE.equals(exists);
    }
}

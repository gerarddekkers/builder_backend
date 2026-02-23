package com.mentesme.builder.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
public class BuilderProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public BuilderProjectRepository(@Qualifier("metroJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ProjectListItem(
            String id, String name, int currentStep,
            String createdBy, String updatedBy,
            Instant createdAt, Instant updatedAt
    ) {}

    public record ProjectRow(
            String id, String name, String projectData, int currentStep,
            String createdBy, String updatedBy,
            Instant createdAt, Instant updatedAt
    ) {}

    public List<ProjectListItem> listAll() {
        return jdbcTemplate.query(
                "SELECT id, name, current_step, created_by, updated_by, created_at, updated_at " +
                        "FROM builder_projects ORDER BY updated_at DESC",
                (rs, rowNum) -> new ProjectListItem(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getInt("current_step"),
                        rs.getString("created_by"),
                        rs.getString("updated_by"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ));
    }

    public Optional<ProjectRow> findById(String id) {
        return jdbcTemplate.query(
                "SELECT id, name, project_data, current_step, created_by, updated_by, created_at, updated_at " +
                        "FROM builder_projects WHERE id = ?",
                (rs, rowNum) -> new ProjectRow(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("project_data"),
                        rs.getInt("current_step"),
                        rs.getString("created_by"),
                        rs.getString("updated_by"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ), id).stream().findFirst();
    }

    public void save(String id, String name, String projectData, int currentStep, String username) {
        jdbcTemplate.update(
                "INSERT INTO builder_projects (id, name, project_data, current_step, created_by, updated_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "name = VALUES(name), project_data = VALUES(project_data), " +
                        "current_step = VALUES(current_step), updated_by = VALUES(updated_by)",
                id, name, projectData, currentStep, username, username);
    }

    public boolean deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM builder_projects WHERE id = ?", id) > 0;
    }
}

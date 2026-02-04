package com.mentesme.builder.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mentesme.builder.model.CompetenceSearchResult;
import com.mentesme.builder.model.CategorySearchResult;

import com.mentesme.builder.model.GroupSearchResult;

import java.util.Optional;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
public class MetroLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetroLookupRepository(@Qualifier("metroJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GroupSearchResult> searchGroups(String query) {
        String like = "%" + query.trim().toLowerCase() + "%";
        String sql = "SELECT id, name FROM groups WHERE LOWER(name) LIKE ? LIMIT 20";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new GroupSearchResult(
            rs.getLong("id"),
            rs.getString("name")
        ), like);
    }

    public Optional<Long> findCompetenceIdByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id FROM competences WHERE LOWER(name) = LOWER(?) LIMIT 1";
        Optional<Long> direct = jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
        if (direct == null) {
            direct = Optional.empty();
        }
        if (direct.isPresent()) {
            return direct;
        }
        String translatedSql = "SELECT competenceId FROM competence_translations WHERE LOWER(name) = LOWER(?) LIMIT 1";
        return jdbcTemplate.query(translatedSql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
    }

    public List<CompetenceSearchResult> searchCompetences(String query) {
        String like = "%" + query.trim().toLowerCase() + "%";
        String sql = "SELECT c.id, " +
                "(SELECT ct.name FROM competence_translations ct WHERE ct.competenceId = c.id AND ct.language = 'nl' LIMIT 1) AS nameNl, " +
                "(SELECT ct.name FROM competence_translations ct WHERE ct.competenceId = c.id AND ct.language = 'en' LIMIT 1) AS nameEn " +
                "FROM competences c " +
                "WHERE LOWER(c.name) LIKE ? " +
                "OR EXISTS (SELECT 1 FROM competence_translations ct WHERE ct.competenceId = c.id AND LOWER(ct.name) LIKE ?) " +
                "LIMIT 20";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompetenceSearchResult(
                rs.getLong("id"),
                rs.getString("nameNl"),
                rs.getString("nameEn")
        ), like, like);
    }

        public List<CategorySearchResult> searchCategories(String query) {
        String like = "%" + query.trim().toLowerCase() + "%";
        String sql = "SELECT c.id, " +
            "(SELECT ct.name FROM category_translations ct WHERE ct.categoryId = c.id AND ct.language = 'nl' LIMIT 1) AS nameNl, " +
            "(SELECT ct.name FROM category_translations ct WHERE ct.categoryId = c.id AND ct.language = 'en' LIMIT 1) AS nameEn " +
            "FROM categories c " +
            "WHERE LOWER(c.name) LIKE ? " +
            "OR EXISTS (SELECT 1 FROM category_translations ct WHERE ct.categoryId = c.id AND LOWER(ct.name) LIKE ?) " +
            "LIMIT 20";

        return jdbcTemplate.query(sql, (rs, rowNum) -> new CategorySearchResult(
            rs.getLong("id"),
            rs.getString("nameNl"),
            rs.getString("nameEn")
        ), like, like);
        }

    public Optional<Long> findCategoryIdByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id FROM categories WHERE LOWER(name) = LOWER(?) LIMIT 1";
        Optional<Long> direct = jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
        if (direct == null) {
            direct = Optional.empty();
        }
        if (direct.isPresent()) {
            return direct;
        }
        String translatedSql = "SELECT categoryId FROM category_translations WHERE LOWER(name) = LOWER(?) LIMIT 1";
        return jdbcTemplate.query(translatedSql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
    }

    public Optional<Long> findGoalIdByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id FROM goals WHERE LOWER(name) = LOWER(?) LIMIT 1";
        Optional<Long> direct = jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
        if (direct == null) {
            direct = Optional.empty();
        }
        if (direct.isPresent()) {
            return direct;
        }
        String translatedSql = "SELECT goalId FROM goal_translations WHERE LOWER(name) = LOWER(?) LIMIT 1";
        return jdbcTemplate.query(translatedSql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
    }

    public long getMaxId(String table) {
        String sql = "SELECT COALESCE(MAX(id), 0) FROM " + table;
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }

    /**
     * Execute a list of SQL statements.
     * Used by the build endpoint to persist assessments to Metro database.
     */
    public void executeSqlStatements(List<String> sqlStatements) {
        for (String sql : sqlStatements) {
            if (sql != null && !sql.isBlank()) {
                jdbcTemplate.execute(sql);
            }
        }
    }

}

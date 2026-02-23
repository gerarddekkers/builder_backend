package com.mentesme.builder.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
public class AssessmentDefinitionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AssessmentDefinitionRepository(@Qualifier("metroJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─────────────────────────────────────────────────────────────
    // Row types
    // ─────────────────────────────────────────────────────────────

    public record QuestionnaireRow(long id, String name) {}

    public record QuestionnaireTranslationRow(
            String language, String name, String questions, String report) {}

    public record ItemDetailRow(
            long itemId, String itemName, int invertOrder, int itemOrder,
            long competenceId, long categoryId) {}

    public record ItemTranslationRow(
            long itemId, String language, String leftText, String rightText) {}

    public record CompetenceTranslationRow(
            long competenceId, String language, String name, String description) {}

    public record CategoryTranslationRow(
            long categoryId, String language, String name) {}

    // ─────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────

    public Optional<QuestionnaireRow> findQuestionnaireById(long questionnaireId) {
        String sql = "SELECT id, name FROM questionnaires WHERE id = ?";
        return jdbcTemplate.query(sql, rs ->
                        rs.next() ? Optional.of(new QuestionnaireRow(rs.getLong("id"), rs.getString("name")))
                                : Optional.empty(),
                questionnaireId);
    }

    public List<QuestionnaireTranslationRow> findQuestionnaireTranslations(long questionnaireId) {
        String sql = "SELECT language, name, questions, report " +
                "FROM questionnaire_translations WHERE questionnaireId = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new QuestionnaireTranslationRow(
                rs.getString("language"),
                rs.getString("name"),
                rs.getString("questions"),
                rs.getString("report")
        ), questionnaireId);
    }

    public List<ItemDetailRow> findQuestionnaireItemsWithDetails(long questionnaireId) {
        String sql = """
                SELECT qi.itemId, i.name AS itemName, i.invertOrder, qi.`order` AS itemOrder,
                       ci.competenceId, cc.categoryId
                FROM questionnaire_items qi
                JOIN items i ON i.id = qi.itemId
                JOIN competence_items ci ON ci.itemId = qi.itemId
                JOIN category_competences cc ON cc.competenceId = ci.competenceId
                WHERE qi.questionnaireId = ?
                ORDER BY qi.`order` ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ItemDetailRow(
                rs.getLong("itemId"),
                rs.getString("itemName"),
                rs.getInt("invertOrder"),
                rs.getInt("itemOrder"),
                rs.getLong("competenceId"),
                rs.getLong("categoryId")
        ), questionnaireId);
    }

    public List<ItemTranslationRow> findItemTranslationsForQuestionnaire(long questionnaireId) {
        String sql = """
                SELECT it.itemId, it.language, it.leftText, it.rightText
                FROM item_translations it
                WHERE it.itemId IN (SELECT qi.itemId FROM questionnaire_items qi WHERE qi.questionnaireId = ?)
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ItemTranslationRow(
                rs.getLong("itemId"),
                rs.getString("language"),
                rs.getString("leftText"),
                rs.getString("rightText")
        ), questionnaireId);
    }

    public List<CompetenceTranslationRow> findCompetenceTranslationsForIds(List<Long> competenceIds) {
        if (competenceIds.isEmpty()) return List.of();
        String placeholders = competenceIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT competenceId, language, name, description " +
                "FROM competence_translations WHERE competenceId IN (" + placeholders + ")";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CompetenceTranslationRow(
                rs.getLong("competenceId"),
                rs.getString("language"),
                rs.getString("name"),
                rs.getString("description")
        ), competenceIds.toArray());
    }

    public List<CategoryTranslationRow> findCategoryTranslationsForIds(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) return List.of();
        String placeholders = categoryIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT categoryId, language, name " +
                "FROM category_translations WHERE categoryId IN (" + placeholders + ")";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CategoryTranslationRow(
                rs.getLong("categoryId"),
                rs.getString("language"),
                rs.getString("name")
        ), categoryIds.toArray());
    }
}

package com.mentesme.builder.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mentesme.builder.model.CompetenceSearchResult;
import com.mentesme.builder.model.CategorySearchResult;

import com.mentesme.builder.model.GroupSearchResult;
import com.mentesme.builder.model.definition.QuestionnaireListItem;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
public class MetroLookupRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetroLookupRepository(@Qualifier("metroJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GroupSearchResult> findGroupById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, name FROM `groups` WHERE id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new GroupSearchResult(
            rs.getLong("id"),
            rs.getString("name")
        ), id).stream().findFirst();
    }

    public List<GroupSearchResult> searchGroups(String query) {
        String like = "%" + query.trim().toLowerCase() + "%";
        String sql = "SELECT id, name FROM `groups` WHERE LOWER(name) LIKE ? LIMIT 20";
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

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "questionnaires", "categories", "competences", "goals", "items");

    public long getMaxId(String table) {
        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("Invalid table name for getMaxId: " + table);
        }
        String sql = "SELECT COALESCE(MAX(id), 0) FROM " + table;
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }

    public long getMaxCqId() {
        String sql = "SELECT COALESCE(MAX(cq_id), 0) FROM competence_questions";
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }

    /**
     * Fetch all max IDs in a single query to minimize round trips.
     * Returns map: table name -> max id.
     */
    public Map<String, Long> getAllMaxIds() {
        String sql = "SELECT " +
                "(SELECT COALESCE(MAX(id), 0) FROM questionnaires), " +
                "(SELECT COALESCE(MAX(id), 0) FROM categories), " +
                "(SELECT COALESCE(MAX(id), 0) FROM competences), " +
                "(SELECT COALESCE(MAX(id), 0) FROM goals), " +
                "(SELECT COALESCE(MAX(id), 0) FROM items), " +
                "(SELECT COALESCE(MAX(cq_id), 0) FROM competence_questions)";
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            Map<String, Long> m = new HashMap<>();
            m.put("questionnaires", rs.getLong(1));
            m.put("categories", rs.getLong(2));
            m.put("competences", rs.getLong(3));
            m.put("goals", rs.getLong(4));
            m.put("items", rs.getLong(5));
            m.put("cq", rs.getLong(6));
            return m;
        });
    }

    /**
     * Validate multiple group IDs exist in a single query.
     * Returns set of IDs that were NOT found.
     */
    public Set<Long> findMissingGroupIds(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) return Set.of();
        String placeholders = String.join(",", groupIds.stream().map(id -> "?").toList());
        String sql = "SELECT id FROM `groups` WHERE id IN (" + placeholders + ")";
        List<Long> found = jdbcTemplate.queryForList(sql, Long.class, groupIds.toArray());
        Set<Long> missing = new HashSet<>(groupIds);
        missing.removeAll(new HashSet<>(found));
        return missing;
    }

    /**
     * Check if a questionnaire with the given name already exists.
     * @return Optional containing the questionnaire ID if found, empty otherwise.
     */
    public Optional<Long> findQuestionnaireIdByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id FROM questionnaires WHERE LOWER(name) = LOWER(?) LIMIT 1";
        Optional<Long> direct = jdbcTemplate.query(sql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
        if (direct == null) {
            direct = Optional.empty();
        }
        if (direct.isPresent()) {
            return direct;
        }
        // Also check questionnaire_translations
        String translatedSql = "SELECT questionnaireId FROM questionnaire_translations WHERE LOWER(name) = LOWER(?) LIMIT 1";
        return jdbcTemplate.query(translatedSql, rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name.trim());
    }

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(MetroLookupRepository.class);

    // Triggers on competence_questions that call calculate_user_competence_scores_for_all_assessments()
    // Each trigger takes ~10s per row. We drop them before bulk inserts and recreate + call once after.
    private static final String[] CQ_TRIGGER_NAMES = {
            "recalculate_user_competence_scores_on_insert_2",
            "recalculate_user_competence_scores_on_update_2",
            "recalculate_user_competence_scores_on_delete_2"
    };
    private static final String[] CQ_TRIGGER_CREATE = {
            "CREATE TRIGGER recalculate_user_competence_scores_on_insert_2 AFTER INSERT ON competence_questions FOR EACH ROW CALL metro.calculate_user_competence_scores_for_all_assessments()",
            "CREATE TRIGGER recalculate_user_competence_scores_on_update_2 AFTER UPDATE ON competence_questions FOR EACH ROW CALL metro.calculate_user_competence_scores_for_all_assessments()",
            "CREATE TRIGGER recalculate_user_competence_scores_on_delete_2 AFTER DELETE ON competence_questions FOR EACH ROW CALL metro.calculate_user_competence_scores_for_all_assessments()"
    };

    /**
     * Execute a list of SQL statements using raw JDBC for minimal overhead.
     * Temporarily disables competence_questions triggers to avoid 10s/row recalculation,
     * then calls the stored procedure once at the end.
     * Returns per-statement timing data for diagnostics.
     */
    public List<Map<String, Object>> executeSqlStatements(List<String> sqlStatements) {
        List<String> validSql = sqlStatements.stream()
                .filter(sql -> sql != null && !sql.isBlank())
                .map(sql -> sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql)
                .toList();
        if (validSql.isEmpty()) return List.of();

        // Check if any statements touch competence_questions
        boolean hasCqInserts = validSql.stream().anyMatch(s ->
                s.toLowerCase().contains("competence_questions"));

        log.info("Executing {} SQL statements via raw JDBC (cq_triggers_bypass={})...",
                validSql.size(), hasCqInserts);

        return jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<List<Map<String, Object>>>) conn -> {
            List<Map<String, Object>> perStmt = new ArrayList<>();
            try (java.sql.Statement stmt = conn.createStatement()) {
                // Drop triggers if needed
                if (hasCqInserts) {
                    for (String triggerName : CQ_TRIGGER_NAMES) {
                        stmt.execute("DROP TRIGGER IF EXISTS " + triggerName);
                    }
                    log.info("Dropped {} competence_questions triggers", CQ_TRIGGER_NAMES.length);
                }

                long totalStart = System.currentTimeMillis();
                for (int i = 0; i < validSql.size(); i++) {
                    String sql = validSql.get(i);
                    long start = System.currentTimeMillis();
                    stmt.execute(sql);
                    long elapsed = System.currentTimeMillis() - start;
                    String sqlPrefix = sql.length() > 80 ? sql.substring(0, 80) + "..." : sql;
                    perStmt.add(Map.of("i", i, "ms", elapsed, "sql", sqlPrefix));
                    if (elapsed > 100) {
                        log.warn("SLOW stmt #{} ({}ms): {}", i, elapsed, sqlPrefix);
                    }
                }
                long totalElapsed = System.currentTimeMillis() - totalStart;
                log.info("Raw JDBC: {} statements executed in {}ms ({}ms/stmt)",
                        validSql.size(), totalElapsed, totalElapsed / validSql.size());

                // Recreate triggers (recalculation not needed for new/updated assessments)
                if (hasCqInserts) {
                    for (String createSql : CQ_TRIGGER_CREATE) {
                        stmt.execute(createSql);
                    }
                    log.info("Recreated {} competence_questions triggers (skipping recalculation — not needed for publish)", CQ_TRIGGER_CREATE.length);
                }
            }
            return perStmt;
        });
    }

    /**
     * List questionnaires with optional search filter.
     */
    public List<QuestionnaireListItem> listQuestionnaires(String query, int limit) {
        String competenceCountSql =
                "(SELECT COUNT(DISTINCT ci.competenceId) FROM questionnaire_items qi2 " +
                "JOIN competence_items ci ON ci.itemId = qi2.itemId " +
                "WHERE qi2.questionnaireId = q.id) AS competenceCount";

        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase() + "%";
            String sql = "SELECT q.id, q.name, " +
                    "(SELECT qt.name FROM questionnaire_translations qt WHERE qt.questionnaireId = q.id AND qt.language = 'nl' LIMIT 1) AS nameNl, " +
                    "(SELECT qt.name FROM questionnaire_translations qt WHERE qt.questionnaireId = q.id AND qt.language = 'en' LIMIT 1) AS nameEn, " +
                    "(SELECT COUNT(*) FROM questionnaire_items qi WHERE qi.questionnaireId = q.id) AS itemCount, " +
                    competenceCountSql + " " +
                    "FROM questionnaires q " +
                    "WHERE LOWER(q.name) LIKE ? " +
                    "OR EXISTS (SELECT 1 FROM questionnaire_translations qt WHERE qt.questionnaireId = q.id AND LOWER(qt.name) LIKE ?) " +
                    "ORDER BY q.id DESC LIMIT ?";
            return jdbcTemplate.query(sql, (rs, rowNum) -> new QuestionnaireListItem(
                    rs.getLong("id"), rs.getString("name"),
                    rs.getString("nameNl"), rs.getString("nameEn"),
                    rs.getInt("itemCount"), rs.getInt("competenceCount")
            ), like, like, limit);
        } else {
            String sql = "SELECT q.id, q.name, " +
                    "(SELECT qt.name FROM questionnaire_translations qt WHERE qt.questionnaireId = q.id AND qt.language = 'nl' LIMIT 1) AS nameNl, " +
                    "(SELECT qt.name FROM questionnaire_translations qt WHERE qt.questionnaireId = q.id AND qt.language = 'en' LIMIT 1) AS nameEn, " +
                    "(SELECT COUNT(*) FROM questionnaire_items qi WHERE qi.questionnaireId = q.id) AS itemCount, " +
                    competenceCountSql + " " +
                    "FROM questionnaires q " +
                    "ORDER BY q.id DESC LIMIT ?";
            return jdbcTemplate.query(sql, (rs, rowNum) -> new QuestionnaireListItem(
                    rs.getLong("id"), rs.getString("name"),
                    rs.getString("nameNl"), rs.getString("nameEn"),
                    rs.getInt("itemCount"), rs.getInt("competenceCount")
            ), limit);
        }
    }

    /**
     * Fetch groups linked to a questionnaire, with names.
     */
    public List<GroupSearchResult> findGroupsForQuestionnaire(long questionnaireId) {
        String sql = "SELECT g.id, g.name FROM `groups` g " +
                "INNER JOIN group_questionnaires gq ON gq.groupId = g.id " +
                "WHERE gq.questionnaireId = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new GroupSearchResult(
                rs.getLong("id"), rs.getString("name")
        ), questionnaireId);
    }

    /**
     * Update questionnaire_translations with S3 URLs for questions and report XML.
     * Uses parameterized query to prevent SQL injection.
     */
    public void updateTranslationUrls(long questionnaireId, String language,
                                       String questionsUrl, String reportUrl) {
        String sql = "UPDATE questionnaire_translations SET questions = ?, report = ? " +
                     "WHERE questionnaireId = ? AND language = ?";
        int updated = jdbcTemplate.update(sql, questionsUrl, reportUrl, questionnaireId, language);
        if (updated == 0) {
            throw new IllegalStateException(
                    "No questionnaire_translation found for questionnaireId=" + questionnaireId
                    + ", language=" + language + ". Cannot set XML URLs.");
        }
    }

}

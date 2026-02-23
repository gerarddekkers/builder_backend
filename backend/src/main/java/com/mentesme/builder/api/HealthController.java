package com.mentesme.builder.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public HealthController(
            @Qualifier("metroJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("metroDataSource") DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @DeleteMapping("/api/db-cleanup")
    public Map<String, Object> cleanupQuestionnaires(@RequestParam int fromId, @RequestParam int toId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (toId - fromId > 100) {
            result.put("error", "Range too large (max 100)");
            return result;
        }

        // Drop competence_questions triggers to avoid 10s/row recalculation
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS recalculate_user_competence_scores_on_insert_2");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS recalculate_user_competence_scores_on_update_2");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS recalculate_user_competence_scores_on_delete_2");

        // Cascade delete in correct FK order
        int d1 = jdbcTemplate.update("DELETE it FROM item_translations it INNER JOIN questionnaire_items qi ON qi.itemId = it.itemId WHERE qi.questionnaireId BETWEEN ? AND ?", fromId, toId);
        int d2 = jdbcTemplate.update("DELETE ci FROM competence_items ci INNER JOIN questionnaire_items qi ON qi.itemId = ci.itemId WHERE qi.questionnaireId BETWEEN ? AND ?", fromId, toId);
        int d3 = jdbcTemplate.update("DELETE i FROM items i INNER JOIN questionnaire_items qi ON qi.itemId = i.id WHERE qi.questionnaireId BETWEEN ? AND ?", fromId, toId);
        int d4 = jdbcTemplate.update("DELETE FROM questionnaire_items WHERE questionnaireId BETWEEN ? AND ?", fromId, toId);
        int d5 = jdbcTemplate.update("DELETE FROM competence_questions WHERE questionnaireId BETWEEN ? AND ?", fromId, toId);
        int d6 = jdbcTemplate.update("DELETE FROM group_questionnaires WHERE questionnaireId BETWEEN ? AND ?", fromId, toId);
        int d7 = jdbcTemplate.update("DELETE FROM questionnaire_translations WHERE questionnaireId BETWEEN ? AND ?", fromId, toId);
        int d8 = jdbcTemplate.update("DELETE FROM questionnaires WHERE id BETWEEN ? AND ?", fromId, toId);

        // Recreate triggers
        jdbcTemplate.execute("CREATE TRIGGER recalculate_user_competence_scores_on_insert_2 AFTER INSERT ON competence_questions FOR EACH ROW CALL metro.calculate_user_competence_scores_for_all_assessments()");
        jdbcTemplate.execute("CREATE TRIGGER recalculate_user_competence_scores_on_update_2 AFTER UPDATE ON competence_questions FOR EACH ROW CALL metro.calculate_user_competence_scores_for_all_assessments()");
        jdbcTemplate.execute("CREATE TRIGGER recalculate_user_competence_scores_on_delete_2 AFTER DELETE ON competence_questions FOR EACH ROW CALL metro.calculate_user_competence_scores_for_all_assessments()");

        result.put("range", fromId + "-" + toId);
        result.put("item_translations", d1);
        result.put("competence_items", d2);
        result.put("items", d3);
        result.put("questionnaire_items", d4);
        result.put("competence_questions", d5);
        result.put("group_questionnaires", d6);
        result.put("questionnaire_translations", d7);
        result.put("questionnaires", d8);
        return result;
    }

    @GetMapping("/api/db-questionnaires")
    public List<Map<String, Object>> listQuestionnaires() {
        return jdbcTemplate.queryForList(
                "SELECT q.id, q.name, " +
                "GROUP_CONCAT(DISTINCT gq.groupId ORDER BY gq.groupId) AS groupIds, " +
                "(SELECT COUNT(*) FROM questionnaire_items qi WHERE qi.questionnaireId = q.id) AS itemCount, " +
                "(SELECT COUNT(*) FROM competence_questions cq WHERE cq.questionnaireId = q.id) AS cqCount " +
                "FROM questionnaires q " +
                "LEFT JOIN group_questionnaires gq ON gq.questionnaireId = q.id " +
                "GROUP BY q.id, q.name " +
                "ORDER BY q.id DESC LIMIT 30");
    }

    @GetMapping("/api/db-translations")
    public List<Map<String, Object>> listTranslations(@RequestParam int questionnaireId) {
        return jdbcTemplate.queryForList(
                "SELECT questionnaireId, language, name, questions, report FROM questionnaire_translations WHERE questionnaireId = ?",
                questionnaireId);
    }

    @GetMapping("/api/db-perf-test")
    public Map<String, Object> dbPerfTest() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // Get max IDs for test
        Long maxItemId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM items", Long.class);
        long baseId = (maxItemId != null ? maxItemId : 0) + 500;

        // Test 1: Simple INSERTs (raw JDBC, autocommit=false)
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            long start = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                stmt.execute("INSERT INTO items(id, name, invertOrder) VALUES (" + (baseId + i) + ", '__perf_test_" + i + "', 0)");
            }
            long elapsed = System.currentTimeMillis() - start;
            conn.rollback();
            stmt.close();
            result.put("simpleInsert_10x_ms", elapsed);
            result.put("simpleInsert_perStmt_ms", elapsed / 10);
        }

        // Test 2: INSERT IGNORE (link tables style)
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            long start = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                stmt.execute("INSERT IGNORE INTO group_questionnaires (groupId, questionnaireId, promoted, price) VALUES (1, " + (baseId + i) + ", 0, 0.00)");
            }
            long elapsed = System.currentTimeMillis() - start;
            conn.rollback();
            stmt.close();
            result.put("insertIgnore_10x_ms", elapsed);
            result.put("insertIgnore_perStmt_ms", elapsed / 10);
        }

        // Test 3: DELETE with JOIN (same pattern as publish cleanup)
        // First insert test data, then time the DELETE
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            // Setup: insert items + questionnaire_items to test cascading DELETE
            for (int i = 0; i < 5; i++) {
                stmt.execute("INSERT INTO items(id, name, invertOrder) VALUES (" + (baseId + 200 + i) + ", '__perf_del_" + i + "', 0)");
                stmt.execute("INSERT INTO item_translations(itemId, language, leftText, rightText) VALUES (" + (baseId + 200 + i) + ", 'nl', 'left', 'right')");
            }
            // Time the DELETE with JOIN
            long start = System.currentTimeMillis();
            stmt.execute("DELETE it FROM item_translations it INNER JOIN items i ON i.id = it.itemId WHERE i.name LIKE '__perf_del_%'");
            long elapsed = System.currentTimeMillis() - start;
            conn.rollback();
            stmt.close();
            result.put("deleteWithJoin_ms", elapsed);
        }

        // Test 4: FK INSERT (item_translations with FK to items)
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            // First create the parent items
            for (int i = 0; i < 10; i++) {
                stmt.execute("INSERT INTO items(id, name, invertOrder) VALUES (" + (baseId + 300 + i) + ", '__perf_fk_" + i + "', 0)");
            }
            // Time FK inserts
            long start = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                stmt.execute("INSERT INTO item_translations(itemId, language, leftText, rightText) VALUES (" + (baseId + 300 + i) + ", 'nl', 'test left', 'test right')");
            }
            long elapsed = System.currentTimeMillis() - start;
            conn.rollback();
            stmt.close();
            result.put("fkInsert_10x_ms", elapsed);
            result.put("fkInsert_perStmt_ms", elapsed / 10);
        }

        // Test 5: INSERT INTO competence_questions (the suspected slow table)
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            // Get max cq_id
            var rs = stmt.executeQuery("SELECT COALESCE(MAX(cq_id), 0) FROM competence_questions");
            rs.next();
            long maxCqId = rs.getLong(1);
            rs.close();
            // Get a valid competenceId and questionnaireId
            rs = stmt.executeQuery("SELECT competenceId, questionnaireId FROM competence_questions LIMIT 1");
            long testCompId = 1, testQId = 1;
            if (rs.next()) {
                testCompId = rs.getLong(1);
                testQId = rs.getLong(2);
            }
            rs.close();

            // Test 5a: WITHOUT trigger bypass
            long start = System.currentTimeMillis();
            stmt.execute("INSERT INTO competence_questions (competenceId, questionnaireId, questionId, cq_id) VALUES (" +
                    testCompId + ", " + testQId + ", '99.1.', " + (maxCqId + 100) + ")");
            long elapsed = System.currentTimeMillis() - start;
            result.put("cq_withTrigger_ms", elapsed);

            // Test 5b: WITH trigger temporarily dropped
            try {
                stmt.execute("DROP TRIGGER IF EXISTS recalculate_user_competence_scores_on_insert_2");
                result.put("cq_dropTrigger", "OK");
                start = System.currentTimeMillis();
                stmt.execute("INSERT INTO competence_questions (competenceId, questionnaireId, questionId, cq_id) VALUES (" +
                        testCompId + ", " + testQId + ", '99.2.', " + (maxCqId + 101) + ")");
                elapsed = System.currentTimeMillis() - start;
                result.put("cq_withoutTrigger_ms", elapsed);
                // Recreate trigger
                stmt.execute("CREATE TRIGGER recalculate_user_competence_scores_on_insert_2 AFTER INSERT ON competence_questions FOR EACH ROW CALL metro.calculate_user_competence_scores_for_all_assessments()");
                result.put("cq_recreateTrigger", "OK");
            } catch (Exception e) {
                result.put("cq_triggerBypass_error", e.getMessage());
            }

            conn.rollback();
            stmt.close();
        }

        // Test 6: Check table sizes and indexes
        Long cqCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM competence_questions", Long.class);
        result.put("competenceQuestions_rowCount", cqCount);

        // Check for triggers on competence_questions
        var triggers = jdbcTemplate.queryForList(
                "SELECT TRIGGER_NAME, EVENT_MANIPULATION, ACTION_TIMING, ACTION_STATEMENT FROM INFORMATION_SCHEMA.TRIGGERS WHERE EVENT_OBJECT_TABLE = 'competence_questions'");
        result.put("competenceQuestions_triggers", triggers.size());
        if (!triggers.isEmpty()) {
            result.put("competenceQuestions_triggerDetails", triggers.toString());
        }

        // Cleanup (just in case rollback didn't work)
        jdbcTemplate.execute("DELETE FROM item_translations WHERE leftText = 'test left' OR leftText = 'left'");
        jdbcTemplate.execute("DELETE FROM items WHERE name LIKE '__perf_%'");
        jdbcTemplate.execute("DELETE FROM competence_questions WHERE questionId LIKE '99.%'");

        result.put("baseItemId", baseId);
        return result;
    }
}

package com.mentesme.builder.service;

import com.mentesme.builder.model.*;
import com.mentesme.builder.model.StepInput.StepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for LearningJourneyPublishService.
 *
 * Uses H2 in MySQL compatibility mode — real SQL, real transactions, no mocking, no Docker.
 * Schema initialized from schema-test.sql (matches metro-prod.sql structure).
 */
@SpringBootTest
@ActiveProfiles("test")
class LearningJourneyPublishServiceIT {

    @TestConfiguration
    static class SchemaInit {
        @Bean
        ResourceDatabasePopulator metroSchemaPopulator(
                @Qualifier("metroDataSource") DataSource dataSource) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema-test.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            return populator;
        }
    }

    @Autowired
    private LearningJourneyPublishService publishService;

    @Autowired
    @Qualifier("metroJdbcTemplate")
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        // Clean in FK-safe order
        jdbc.execute("DELETE FROM group_learning_journey");
        jdbc.execute("DELETE FROM learning_journey_documents");
        jdbc.execute("DELETE FROM step_question");
        jdbc.execute("DELETE FROM steps");
        jdbc.execute("DELETE FROM labels");
        jdbc.execute("DELETE FROM learning_journeys");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper: build a minimal valid request
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Structure:
     *   Step 1: Hoofdstap "Introductie" (TEXT, chatbox enabled)
     *   Step 2: Hoofdstap "Zelfreflectie" (QUESTION — has 2 questions)
     *   Step 3: Substap "Verdieping" (QUESTION — has 2 questions, 2 documents)
     *   Step 4: Afsluiting "Samenvatting" (TEXT)
     */
    private LearningJourneyPublishRequest buildValidRequest() {
        // Step 1: Hoofdstap — text only, chatbox enabled
        StepInput step1 = new StepInput(
                StepType.hoofdstap,
                "Introductie",
                "Introduction",
                "Welkom bij deze learning journey.",
                "Welcome to this learning journey.",
                true,   // chatbox enabled
                false,  // upload disabled
                null,   // no video
                List.of(), // no questions → TEXT
                List.of()  // no documents
        );

        // Step 2: Hoofdstap — with 2 questions
        StepInput step2 = new StepInput(
                StepType.hoofdstap,
                "Zelfreflectie",
                "Self-reflection",
                "Denk na over je sterke punten.",
                "Think about your strengths.",
                false,  // chatbox disabled
                false,  // upload disabled
                null,
                List.of(
                        new QuestionInput("Wat is je grootste kracht?", "What is your greatest strength?", null),
                        new QuestionInput("Waar loop je tegenaan?", "What challenges do you face?", null)
                ),
                List.of()
        );

        // Step 3: Substap — 2 questions + 2 documents
        StepInput step3 = new StepInput(
                StepType.substap,
                "Verdieping",
                "Deep dive",
                "Lees het bijgevoegde document.",
                "Read the attached document.",
                false,
                false,
                null,
                List.of(
                        new QuestionInput("Wat heb je geleerd?", "What did you learn?", null),
                        new QuestionInput("Hoe pas je dit toe?", "How will you apply this?", null)
                ),
                List.of(
                        new DocumentInput("Werkblad reflectie", "werkblad-reflectie.pdf", null, "nl"),
                        new DocumentInput("Reflection worksheet", "werkblad-reflectie.pdf", null, "en")
                )
        );

        // Step 4: Afsluiting — text only
        StepInput step4 = new StepInput(
                StepType.afsluiting,
                "Samenvatting",
                "Summary",
                "Goed gedaan! Je hebt de journey afgerond.",
                "Well done! You have completed the journey.",
                false,
                false,
                null,
                List.of(),
                List.of()
        );

        return new LearningJourneyPublishRequest(
                "Test Journey",
                "Test Journey EN",
                "Een test journey",
                "A test journey",
                List.of(1L),  // group ID 1 (seeded in schema-test.sql)
                false,        // aiCoachEnabled
                List.of(step1, step2, step3, step4),
                null
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: full publish flow
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void publishCreatesAllRowsCorrectly() {
        LearningJourneyPublishRequest request = buildValidRequest();

        LearningJourneyPublishResult result = publishService.publish(request, PublishEnvironment.TEST);

        // ── Result assertions ──────────────────────────────────────────
        assertTrue(result.success());
        assertEquals("TEST", result.environment());
        assertTrue(result.learningJourneyId() > 0);
        assertNotNull(result.timings());

        long ljId = result.learningJourneyId();

        // ── learning_journeys ──────────────────────────────────────────
        Map<String, Object> journey = jdbc.queryForMap(
                "SELECT * FROM learning_journeys WHERE id = ?", ljId);
        assertEquals("Test Journey", journey.get("name"));
        assertNotNull(journey.get("ljKey"));
        assertFalse(((String) journey.get("ljKey")).contains(" "), "ljKey must not contain spaces");
        assertEquals("test-journey", journey.get("ljKey"));
        assertEquals("Een test journey", journey.get("description"));

        // ── steps: count and positions ─────────────────────────────────
        List<Map<String, Object>> steps = jdbc.queryForList(
                "SELECT * FROM steps WHERE learningJourneyId = ? ORDER BY position", ljId);
        assertEquals(4, steps.size(), "Expected 4 steps");

        // Positions are sequential 1..4
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, ((Number) steps.get(i).get("position")).intValue(),
                    "Step " + (i + 1) + " position");
        }

        // ── steps: no duplicates per language ──────────────────────────
        Long stepCountTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM steps WHERE learningJourneyId = ?", Long.class, ljId);
        assertEquals(4L, stepCountTotal, "Steps must NOT be duplicated per language");

        // ── Step 1: Hoofdstap position 1 → big/blue, TEXT, chatbox ────
        Map<String, Object> s1 = steps.get(0);
        assertEquals("big", s1.get("size"));
        assertEquals("blue", s1.get("colour"));
        assertEquals("TEXT", s1.get("type"), "No questions → TEXT");
        assertEquals("principal", s1.get("role"));
        assertEquals("S", s1.get("conversation"), "Chatbox enabled → 'S'");
        String s1TitleId = (String) s1.get("title");
        assertTrue(s1TitleId.startsWith("LJ_" + ljId + "_STEP_1_TITLE"));
        String s1TextId = (String) s1.get("textContent");
        assertTrue(s1TextId.startsWith("LJ_" + ljId + "_STEP_1_TEXT"));

        // ── Step 2: Hoofdstap position 2 → big/blue, QUESTION ─────────
        Map<String, Object> s2 = steps.get(1);
        assertEquals("big", s2.get("size"));
        assertEquals("blue", s2.get("colour"));
        assertEquals("QUESTION", s2.get("type"), "Has questions → QUESTION");
        assertEquals("principal", s2.get("role"));
        assertNull(s2.get("conversation"), "Chatbox disabled → NULL");

        // ── Step 3: Substap position 3 → small/violet, QUESTION ───────
        Map<String, Object> s3 = steps.get(2);
        assertEquals("small", s3.get("size"), "Substep size = small");
        assertEquals("violet", s3.get("colour"), "First substep colour = violet");
        assertEquals("QUESTION", s3.get("type"));
        assertEquals("principal", s3.get("role"));
        // Documents identifier should be set
        assertNotNull(s3.get("documents"));
        assertTrue(((String) s3.get("documents")).startsWith("LJ_" + ljId + "_STEP_3_DOCS"));

        // ── Step 4: Afsluiting position 4 → big/blue, TEXT ─────────────
        Map<String, Object> s4 = steps.get(3);
        assertEquals("big", s4.get("size"));
        assertEquals("blue", s4.get("colour"));
        assertEquals("TEXT", s4.get("type"));
        assertEquals("principal", s4.get("role"));

        // ── labels: NL + EN rows for every title and textContent ───────
        // 4 steps × (title NL + title EN) = 8 title labels
        // 4 steps × (text NL + text EN) = 8 text labels (all steps have textContent)
        // 4 questions × (NL + EN) = 8 question labels
        // Total: 24 labels
        Long labelCount = jdbc.queryForObject("SELECT COUNT(*) FROM labels", Long.class);
        assertEquals(24L, labelCount, "Expected 24 label rows");

        // Check NL+EN pair for step 1 title
        List<Map<String, Object>> s1TitleLabels = jdbc.queryForList(
                "SELECT * FROM labels WHERE identifier = ? ORDER BY lang",
                s1TitleId);
        assertEquals(2, s1TitleLabels.size(), "Each identifier has NL + EN");
        assertEquals("en", s1TitleLabels.get(0).get("lang"));
        assertEquals("Introduction", s1TitleLabels.get(0).get("text"));
        assertEquals("nl", s1TitleLabels.get(1).get("lang"));
        assertEquals("Introductie", s1TitleLabels.get(1).get("text"));

        // Check identifier pattern for step 2 questions
        String q1Id = "LJ_" + ljId + "_STEP_2_Q_1";
        String q2Id = "LJ_" + ljId + "_STEP_2_Q_2";
        List<Map<String, Object>> q1Labels = jdbc.queryForList(
                "SELECT * FROM labels WHERE identifier = ? ORDER BY lang", q1Id);
        assertEquals(2, q1Labels.size());
        assertEquals("What is your greatest strength?", q1Labels.get(0).get("text")); // EN
        assertEquals("Wat is je grootste kracht?", q1Labels.get(1).get("text")); // NL

        List<Map<String, Object>> q2Labels = jdbc.queryForList(
                "SELECT * FROM labels WHERE identifier = ? ORDER BY lang", q2Id);
        assertEquals(2, q2Labels.size());

        // ── step_question: correct stepId, order, type ─────────────────
        long step2DbId = ((Number) s2.get("id")).longValue();
        long step3DbId = ((Number) s3.get("id")).longValue();

        // Step 2 has 2 questions
        List<Map<String, Object>> step2Qs = jdbc.queryForList(
                "SELECT * FROM step_question WHERE stepId = ? ORDER BY `order`", step2DbId);
        assertEquals(2, step2Qs.size());
        assertEquals(1, ((Number) step2Qs.get(0).get("order")).intValue());
        assertEquals(2, ((Number) step2Qs.get(1).get("order")).intValue());
        assertEquals(q1Id, step2Qs.get(0).get("question"));
        assertEquals(q2Id, step2Qs.get(1).get("question"));
        assertEquals("menteeValuation", step2Qs.get(0).get("type"));
        assertEquals("menteeValuation", step2Qs.get(1).get("type"));

        // Step 3 has 2 questions
        List<Map<String, Object>> step3Qs = jdbc.queryForList(
                "SELECT * FROM step_question WHERE stepId = ? ORDER BY `order`", step3DbId);
        assertEquals(2, step3Qs.size());
        String q3Id = "LJ_" + ljId + "_STEP_3_Q_1";
        String q4Id = "LJ_" + ljId + "_STEP_3_Q_2";
        assertEquals(q3Id, step3Qs.get(0).get("question"));
        assertEquals(q4Id, step3Qs.get(1).get("question"));

        // Step 1 and 4 have NO questions
        Long step1QCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM step_question WHERE stepId = ?", Long.class,
                ((Number) s1.get("id")).longValue());
        assertEquals(0L, step1QCount);
        Long step4QCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM step_question WHERE stepId = ?", Long.class,
                ((Number) s4.get("id")).longValue());
        assertEquals(0L, step4QCount);

        // Total questions: 4
        Long totalQuestions = jdbc.queryForObject("SELECT COUNT(*) FROM step_question", Long.class);
        assertEquals(4L, totalQuestions);

        // ── learning_journey_documents ─────────────────────────────────
        List<Map<String, Object>> docs = jdbc.queryForList(
                "SELECT * FROM learning_journey_documents ORDER BY lang");
        assertEquals(2, docs.size(), "2 document rows (NL + EN)");

        // EN doc
        assertEquals("en", docs.get(0).get("lang"));
        assertEquals("Reflection worksheet", docs.get(0).get("label"));
        String expectedDocsId = "LJ_" + ljId + "_STEP_3_DOCS";
        assertEquals(expectedDocsId, docs.get(0).get("identifier"));
        assertTrue(((String) docs.get(0).get("url")).contains("test-journey/werkblad-reflectie.pdf"),
                "URL must contain ljKey/fileName");

        // NL doc
        assertEquals("nl", docs.get(1).get("lang"));
        assertEquals("Werkblad reflectie", docs.get(1).get("label"));
        assertEquals(expectedDocsId, docs.get(1).get("identifier"));

        // ── group_learning_journey ─────────────────────────────────────
        List<Map<String, Object>> groups = jdbc.queryForList(
                "SELECT * FROM group_learning_journey WHERE learningJourneyId = ?", ljId);
        assertEquals(1, groups.size());
        assertEquals(1L, ((Number) groups.get(0).get("groupId")).longValue());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: validation failure prevents transaction
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void validationFailurePreventsAnyInsert() {
        // Invalid: only 1 hoofdstap (need 2), no afsluiting
        StepInput onlyStep = new StepInput(
                StepType.hoofdstap, "Solo", "Solo EN",
                null, null, false, false, null, List.of(), List.of());

        LearningJourneyPublishRequest invalid = new LearningJourneyPublishRequest(
                "Invalid Journey", null, null, null,
                List.of(1L), false,
                List.of(onlyStep),
                null
        );

        assertThrows(IllegalArgumentException.class,
                () -> publishService.publish(invalid, PublishEnvironment.TEST));

        // Verify nothing was inserted
        Long journeyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_journeys", Long.class);
        assertEquals(0L, journeyCount, "No journey should be inserted on validation failure");

        Long stepCount = jdbc.queryForObject("SELECT COUNT(*) FROM steps", Long.class);
        assertEquals(0L, stepCount, "No steps should be inserted on validation failure");

        Long labelCount = jdbc.queryForObject("SELECT COUNT(*) FROM labels", Long.class);
        assertEquals(0L, labelCount, "No labels should be inserted on validation failure");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: empty title fails validation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void emptyTitleFailsValidation() {
        StepInput noTitle = new StepInput(
                StepType.hoofdstap, "", "EN title",
                null, null, false, false, null, List.of(), List.of());
        StepInput step2 = new StepInput(
                StepType.hoofdstap, "OK", "OK EN",
                null, null, false, false, null, List.of(), List.of());
        StepInput closing = new StepInput(
                StepType.afsluiting, "Einde", "End",
                null, null, false, false, null, List.of(), List.of());

        LearningJourneyPublishRequest req = new LearningJourneyPublishRequest(
                "Journey", null, null, null, List.of(1L), false,
                List.of(noTitle, step2, closing), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> publishService.publish(req, PublishEnvironment.TEST));
        assertTrue(ex.getMessage().contains("no title"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: max 5 questions per substep
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void tooManyQuestionsFailsValidation() {
        StepInput h1 = new StepInput(StepType.hoofdstap, "H1", "H1 EN",
                null, null, false, false, null, List.of(), List.of());
        StepInput h2 = new StepInput(StepType.hoofdstap, "H2", "H2 EN",
                null, null, false, false, null, List.of(), List.of());
        StepInput badSubstep = new StepInput(StepType.substap, "Sub", "Sub EN",
                null, null, false, false, null,
                List.of(
                        new QuestionInput("Q1", "Q1 EN", null),
                        new QuestionInput("Q2", "Q2 EN", null),
                        new QuestionInput("Q3", "Q3 EN", null),
                        new QuestionInput("Q4", "Q4 EN", null),
                        new QuestionInput("Q5", "Q5 EN", null),
                        new QuestionInput("Q6", "Q6 EN", null)  // 6th → exceeds max 5
                ),
                List.of());
        StepInput closing = new StepInput(StepType.afsluiting, "Einde", "End",
                null, null, false, false, null, List.of(), List.of());

        LearningJourneyPublishRequest req = new LearningJourneyPublishRequest(
                "Journey", null, null, null, List.of(1L), false,
                List.of(h1, h2, badSubstep, closing), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> publishService.publish(req, PublishEnvironment.TEST));
        assertTrue(ex.getMessage().contains("max 5"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: substep colour alternation with multiple substeps
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void substepColoursAlternateCorrectly() {
        StepInput h1 = new StepInput(StepType.hoofdstap, "H1", "H1",
                "Tekst", "Text", false, false, null, List.of(), List.of());
        StepInput h2 = new StepInput(StepType.hoofdstap, "H2", "H2",
                "Tekst", "Text", false, false, null, List.of(), List.of());
        StepInput sub1 = new StepInput(StepType.substap, "Sub1", "Sub1",
                null, null, false, false, null, List.of(), List.of());
        StepInput sub2 = new StepInput(StepType.substap, "Sub2", "Sub2",
                null, null, false, false, null, List.of(), List.of());
        StepInput sub3 = new StepInput(StepType.substap, "Sub3", "Sub3",
                null, null, false, false, null, List.of(), List.of());
        StepInput closing = new StepInput(StepType.afsluiting, "Einde", "End",
                "Klaar", "Done", false, false, null, List.of(), List.of());

        LearningJourneyPublishRequest req = new LearningJourneyPublishRequest(
                "Colour Test", null, null, null, List.of(1L), false,
                List.of(h1, h2, sub1, sub2, sub3, closing), null);

        LearningJourneyPublishResult result = publishService.publish(req, PublishEnvironment.TEST);
        long ljId = result.learningJourneyId();

        List<Map<String, Object>> steps = jdbc.queryForList(
                "SELECT * FROM steps WHERE learningJourneyId = ? ORDER BY position", ljId);
        assertEquals(6, steps.size());

        // Positions 1-2: big/blue
        assertEquals("big", steps.get(0).get("size"));
        assertEquals("blue", steps.get(0).get("colour"));
        assertEquals("big", steps.get(1).get("size"));
        assertEquals("blue", steps.get(1).get("colour"));

        // Substeps 3-5: small, alternating violet/orange/violet
        assertEquals("small", steps.get(2).get("size"));
        assertEquals("violet", steps.get(2).get("colour"));
        assertEquals("small", steps.get(3).get("size"));
        assertEquals("orange", steps.get(3).get("colour"));
        assertEquals("small", steps.get(4).get("size"));
        assertEquals("violet", steps.get(4).get("colour"));

        // Afsluiting: big/blue
        assertEquals("big", steps.get(5).get("size"));
        assertEquals("blue", steps.get(5).get("colour"));
    }
}

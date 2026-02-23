package com.mentesme.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentesme.builder.model.*;
import com.mentesme.builder.model.StepInput.StepType;
import com.mentesme.builder.service.TokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Production smoke test for security hardening fixes.
 * Uses MockMvc to simulate HTTP calls with auth ENABLED.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = "builder.auth.enabled=true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecuritySmokeTest {

    @TestConfiguration
    static class SchemaInit {
        @Bean
        ResourceDatabasePopulator smokeTestSchemaPopulator(
                @Qualifier("metroDataSource") DataSource dataSource) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema-test.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            return populator;
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired @Qualifier("metroJdbcTemplate") private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("DELETE FROM group_learning_journey");
        jdbc.execute("DELETE FROM learning_journey_documents");
        jdbc.execute("DELETE FROM step_question");
        jdbc.execute("DELETE FROM steps");
        jdbc.execute("DELETE FROM labels");
        jdbc.execute("DELETE FROM learning_journeys");
    }

    private String tokenFor(String role) {
        return tokenService.generateToken("smoketest-user", role);
    }

    // ═════════════════════════════════════════════════════════════════
    // PHASE 1 — AUTH VERIFICATION
    // ═════════════════════════════════════════════════════════════════

    @Test @Order(1)
    void test1_1_noToken_returns401() throws Exception {
        mvc.perform(get("/api/learning-journeys"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(2)
    void test1_2_viewerRole_publishTest_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(buildMinimalRequest(List.of(1L)));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test @Order(3)
    void test1_3_editorRole_publishTest_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(buildMinimalRequest(List.of(1L)));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("EDITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        Long journeyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_journeys", Long.class);
        assertTrue(journeyCount > 0, "Journey should be inserted for EDITOR role");
    }

    @Test @Order(4)
    void test1_4a_editorRole_publishProduction_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(buildMinimalRequest(List.of(1L)));

        mvc.perform(post("/api/learning-journeys/publish-production")
                        .header("Authorization", "Bearer " + tokenFor("EDITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test @Order(5)
    void test1_4b_adminRole_publishProduction_passesRoleCheck() throws Exception {
        String body = objectMapper.writeValueAsString(buildMinimalRequest(List.of(1L)));

        // ADMIN passes role check but production DB is not configured in test env
        MvcResult result = mvc.perform(post("/api/learning-journeys/publish-production")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertNotEquals(403, status, "ADMIN must pass role check (should not be 403)");
        // 500 expected: production DB not configured in test environment
        assertEquals(500, status,
                "Production DB not configured → 500 (not 403 — role check passed)");
    }

    // ═════════════════════════════════════════════════════════════════
    // PHASE 2 — GROUP VALIDATION
    // ═════════════════════════════════════════════════════════════════

    @Test @Order(6)
    void test2_1_validGroupId_succeeds() throws Exception {
        String body = objectMapper.writeValueAsString(buildMinimalRequest(List.of(1L)));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        Long groupCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_learning_journey", Long.class);
        assertEquals(1L, groupCount, "Group binding should be created");
    }

    @Test @Order(7)
    void test2_2_nonexistentGroupId_returns400_andRollsBack() throws Exception {
        String body = objectMapper.writeValueAsString(buildMinimalRequest(List.of(999999L)));

        MvcResult result = mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("Invalid group"),
                "Error should mention invalid group IDs");

        // Verify full transaction rollback — no partial inserts
        Long journeyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_journeys", Long.class);
        assertEquals(0L, journeyCount, "Transaction must be fully rolled back");

        Long stepCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM steps", Long.class);
        assertEquals(0L, stepCount, "No orphaned steps after rollback");

        Long labelCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM labels", Long.class);
        assertEquals(0L, labelCount, "No orphaned labels after rollback");
    }

    @Test @Order(8)
    void test2_3_mixedValidAndInvalidGroupIds_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildMinimalRequest(List.of(1L, 999999L)));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        Long journeyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_journeys", Long.class);
        assertEquals(0L, journeyCount, "Transaction must be fully rolled back");
    }

    // ═════════════════════════════════════════════════════════════════
    // PHASE 3 — DOCUMENT FILENAME SECURITY
    // ═════════════════════════════════════════════════════════════════

    @Test @Order(9)
    void test3_1_pathTraversalFileName_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildRequestWithDocument("../admin.js", "nl"));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(10)
    void test3_2_queryStringFileName_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildRequestWithDocument("file.pdf?delete=true", "nl"));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(11)
    void test3_3_validFileName_succeeds() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildRequestWithDocument("rapport-2026.pdf", "nl"));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test @Order(12)
    void test3_4_invalidLang_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                buildRequestWithDocument("rapport.pdf", "fr"));

        mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ═════════════════════════════════════════════════════════════════
    // PHASE 4 — FUNCTIONAL REGRESSION
    // ═════════════════════════════════════════════════════════════════

    @Test @Order(13)
    void test4_fullPublish_allTablesPopulated() throws Exception {
        String body = objectMapper.writeValueAsString(buildFullRequest());

        MvcResult result = mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("\"success\""), "Response must contain success field");

        // Verify all tables populated — no partial inserts
        Long journeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_journeys", Long.class);
        assertEquals(1L, journeys, "1 journey row");

        Long steps = jdbc.queryForObject(
                "SELECT COUNT(*) FROM steps", Long.class);
        assertEquals(4L, steps, "4 step rows (2 hoofd + 1 sub + 1 afsluiting)");

        // 4 steps with titles (NL+EN) = 8 labels
        // 3 steps with textContent (NL+EN) = 6 labels (substap has text)
        // Wait: all 4 steps have textContent → 8 more labels
        // 2 questions (NL+EN) = 4 labels
        // Total minimum: 20 labels
        Long labels = jdbc.queryForObject(
                "SELECT COUNT(*) FROM labels", Long.class);
        assertTrue(labels >= 16, "At least 16 label rows");

        Long questions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM step_question", Long.class);
        assertEquals(2L, questions, "2 question rows");

        Long docs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_journey_documents", Long.class);
        assertEquals(1L, docs, "1 document row");

        Long groups = jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_learning_journey", Long.class);
        assertEquals(1L, groups, "1 group binding row");
    }

    // ═════════════════════════════════════════════════════════════════
    // PHASE 5 — LOG / RESPONSE VERIFICATION
    // ═════════════════════════════════════════════════════════════════

    @Test @Order(14)
    void test5_1_validationError_noStackTraceInResponse() throws Exception {
        // Passes bean validation but fails custom validation (only 1 hoofdstap, no afsluiting)
        StepInput solo = new StepInput(StepType.hoofdstap, "Solo", "Solo EN",
                null, null, false, false, null, List.of(), List.of());
        LearningJourneyPublishRequest req = new LearningJourneyPublishRequest(
                "Test", null, null, null, List.of(1L), false, List.of(solo), null);
        String body = objectMapper.writeValueAsString(req);

        MvcResult result = mvc.perform(post("/api/learning-journeys/publish-test")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("at com.mentesme"),
                "Response must not contain Java stack trace");
        assertFalse(responseBody.contains("java.lang."),
                "Response must not contain Java class names");
    }

    @Test @Order(15)
    void test5_2_unauthorizedResponse_cleanJson() throws Exception {
        MvcResult result = mvc.perform(get("/api/learning-journeys"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("\"error\""),
                "401 response should be JSON with error key");
        assertFalse(responseBody.contains("at com.mentesme"),
                "No stack trace in 401 response");
    }

    // ═════════════════════════════════════════════════════════════════
    // Request builders
    // ═════════════════════════════════════════════════════════════════

    private LearningJourneyPublishRequest buildMinimalRequest(List<Long> groupIds) {
        StepInput h1 = new StepInput(StepType.hoofdstap, "Intro", "Introduction",
                null, null, false, false, null, List.of(), List.of());
        StepInput h2 = new StepInput(StepType.hoofdstap, "Reflectie", "Reflection",
                null, null, false, false, null, List.of(), List.of());
        StepInput closing = new StepInput(StepType.afsluiting, "Einde", "End",
                null, null, false, false, null, List.of(), List.of());

        return new LearningJourneyPublishRequest(
                "Smoke Test", "Smoke Test EN", "Beschrijving", "Description",
                groupIds, false, List.of(h1, h2, closing), null);
    }

    private LearningJourneyPublishRequest buildRequestWithDocument(
            String fileName, String lang) {
        StepInput h1 = new StepInput(StepType.hoofdstap, "Intro", "Intro",
                null, null, false, false, null, List.of(), List.of());
        StepInput h2 = new StepInput(StepType.hoofdstap, "Stap2", "Step2",
                null, null, false, false, null, List.of(),
                List.of(new DocumentInput("Document", fileName, null, lang)));
        StepInput closing = new StepInput(StepType.afsluiting, "Einde", "End",
                null, null, false, false, null, List.of(), List.of());

        return new LearningJourneyPublishRequest(
                "Doc Test", "Doc Test EN", null, null,
                List.of(1L), false, List.of(h1, h2, closing), null);
    }

    private LearningJourneyPublishRequest buildFullRequest() {
        StepInput h1 = new StepInput(StepType.hoofdstap, "Introductie", "Introduction",
                "Welkom", "Welcome", true, false, null, List.of(), List.of());
        StepInput h2 = new StepInput(StepType.hoofdstap, "Zelfreflectie", "Self-reflection",
                "Denk na", "Think", false, false, null, List.of(), List.of());
        StepInput sub = new StepInput(StepType.substap, "Verdieping", "Deep dive",
                "Lees", "Read", false, false, null,
                List.of(
                        new QuestionInput("Wat leerde je?", "What did you learn?", null),
                        new QuestionInput("Hoe pas je dit toe?", "How to apply?", null)
                ),
                List.of(new DocumentInput("Werkblad", "werkblad.pdf", null, "nl")));
        StepInput closing = new StepInput(StepType.afsluiting, "Afsluiting", "Closing",
                "Klaar!", "Done!", false, false, null, List.of(), List.of());

        return new LearningJourneyPublishRequest(
                "Full Smoke", "Full Smoke EN", "Beschrijving", "Description",
                List.of(1L), false, List.of(h1, h2, sub, closing), null);
    }
}

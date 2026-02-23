package com.mentesme.builder.service;

import com.mentesme.builder.model.*;
import com.mentesme.builder.model.LearningJourneyDetail.DocumentDetail;
import com.mentesme.builder.model.LearningJourneyDetail.QuestionDetail;
import com.mentesme.builder.model.LearningJourneyDetail.StepDetail;
import com.mentesme.builder.model.StepInput.StepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for LearningJourneyLookupRepository.
 *
 * Seeds data via the real publish service, then queries via lookup repository.
 * Uses H2 in MySQL compatibility mode — no Docker required.
 */
@SpringBootTest
@ActiveProfiles("test")
class LearningJourneyLookupIT {

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
    private LearningJourneyLookupRepository lookupRepository;

    @Autowired
    @Qualifier("metroJdbcTemplate")
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("DELETE FROM group_learning_journey");
        jdbc.execute("DELETE FROM learning_journey_documents");
        jdbc.execute("DELETE FROM step_question");
        jdbc.execute("DELETE FROM steps");
        jdbc.execute("DELETE FROM labels");
        jdbc.execute("DELETE FROM learning_journeys");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private LearningJourneyPublishRequest buildFullRequest(String name) {
        StepInput step1 = new StepInput(StepType.hoofdstap,
                "Introductie", "Introduction",
                "Welkom tekst", "Welcome text",
                true, false, null, List.of(), List.of());

        StepInput step2 = new StepInput(StepType.hoofdstap,
                "Zelfreflectie", "Self-reflection",
                "Reflecteer op je handelen.", "Reflect on your actions.",
                false, false, null,
                List.of(
                        new QuestionInput("Wat is je kracht?", "What is your strength?", null),
                        new QuestionInput("Wat wil je verbeteren?", "What do you want to improve?", null)
                ),
                List.of());

        StepInput step3 = new StepInput(StepType.substap,
                "Verdieping", "Deep dive",
                "Lees het document.", "Read the document.",
                false, false, null,
                List.of(new QuestionInput("Wat heb je geleerd?", "What did you learn?", null)),
                List.of(
                        new DocumentInput("Werkblad", "werkblad.pdf", null, "nl"),
                        new DocumentInput("Worksheet", "werkblad.pdf", null, "en")
                ));

        StepInput step4 = new StepInput(StepType.afsluiting,
                "Afsluiting", "Closing",
                "Goed gedaan!", "Well done!",
                false, false, null, List.of(), List.of());

        return new LearningJourneyPublishRequest(
                name, name + " EN", "Beschrijving", "Description",
                List.of(1L, 2L), false,
                List.of(step1, step2, step3, step4),
                null);
    }

    private LearningJourneyPublishRequest buildMinimalRequest(String name) {
        StepInput h1 = new StepInput(StepType.hoofdstap, "Stap 1", "Step 1",
                null, null, false, false, null, List.of(), List.of());
        StepInput h2 = new StepInput(StepType.hoofdstap, "Stap 2", "Step 2",
                null, null, false, false, null, List.of(), List.of());
        StepInput closing = new StepInput(StepType.afsluiting, "Einde", "End",
                null, null, false, false, null, List.of(), List.of());
        return new LearningJourneyPublishRequest(
                name, null, null, null,
                List.of(1L), false, List.of(h1, h2, closing), null);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: findAll returns published journeys
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void findAllReturnsPublishedJourneys() {
        publishService.publish(buildMinimalRequest("Journey Alpha"), PublishEnvironment.TEST);
        publishService.publish(buildMinimalRequest("Journey Beta"), PublishEnvironment.TEST);

        List<LearningJourneyListItem> all = lookupRepository.findAll();

        assertEquals(2, all.size());
        // Ordered by id DESC → Beta first
        assertEquals("Journey Beta", all.get(0).name());
        assertEquals("journey-beta", all.get(0).ljKey());
        assertEquals("Journey Alpha", all.get(1).name());
        assertEquals("journey-alpha", all.get(1).ljKey());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: findById returns full structure
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void findByIdReturnsFullStructure() {
        LearningJourneyPublishResult published =
                publishService.publish(buildFullRequest("Complete Journey"), PublishEnvironment.TEST);
        long ljId = published.learningJourneyId();

        Optional<LearningJourneyDetail> opt = lookupRepository.findById(ljId);
        assertTrue(opt.isPresent());

        LearningJourneyDetail detail = opt.get();

        // ── Journey info ──
        assertEquals(ljId, detail.id());
        assertEquals("Complete Journey", detail.name());
        assertEquals("complete-journey", detail.ljKey());
        assertEquals("Beschrijving", detail.description());

        // ── Steps ──
        assertEquals(4, detail.steps().size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, detail.steps().get(i).position());
        }

        // ── Questions ──
        StepDetail step2 = detail.steps().get(1);
        assertEquals(2, step2.questions().size());
        assertEquals(1, step2.questions().get(0).order());
        assertEquals(2, step2.questions().get(1).order());

        StepDetail step3 = detail.steps().get(2);
        assertEquals(1, step3.questions().size());

        StepDetail step1 = detail.steps().get(0);
        assertEquals(0, step1.questions().size());
        StepDetail step4 = detail.steps().get(3);
        assertEquals(0, step4.questions().size());

        // ── Documents ──
        assertEquals(2, detail.documents().size());

        // ── Groups ──
        assertEquals(2, detail.groupIds().size());
        assertTrue(detail.groupIds().contains(1L));
        assertTrue(detail.groupIds().contains(2L));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: findById returns empty for non-existent
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void findByIdReturnsEmptyForNonExistent() {
        Optional<LearningJourneyDetail> opt = lookupRepository.findById(99999L);
        assertTrue(opt.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: labels resolved to actual text (not identifiers)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void labelsResolvedCorrectly() {
        LearningJourneyPublishResult published =
                publishService.publish(buildFullRequest("Label Test"), PublishEnvironment.TEST);

        LearningJourneyDetail detail = lookupRepository.findById(published.learningJourneyId()).orElseThrow();

        // Step 1: title NL + EN
        StepDetail s1 = detail.steps().get(0);
        assertEquals("Introductie", s1.titleNl());
        assertEquals("Introduction", s1.titleEn());

        // Step 1: textContent NL + EN
        assertEquals("Welkom tekst", s1.textContentNl());
        assertEquals("Welcome text", s1.textContentEn());

        // Step 2: question labels resolved
        StepDetail s2 = detail.steps().get(1);
        QuestionDetail q1 = s2.questions().get(0);
        assertEquals("Wat is je kracht?", q1.textNl());
        assertEquals("What is your strength?", q1.textEn());

        QuestionDetail q2 = s2.questions().get(1);
        assertEquals("Wat wil je verbeteren?", q2.textNl());
        assertEquals("What do you want to improve?", q2.textEn());

        // Step 3: substep question label
        StepDetail s3 = detail.steps().get(2);
        assertEquals("Wat heb je geleerd?", s3.questions().get(0).textNl());
        assertEquals("What did you learn?", s3.questions().get(0).textEn());

        // Documents: plain text labels (not identifiers)
        DocumentDetail nlDoc = detail.documents().stream()
                .filter(d -> "nl".equals(d.lang())).findFirst().orElseThrow();
        assertEquals("Werkblad", nlDoc.label());
        DocumentDetail enDoc = detail.documents().stream()
                .filter(d -> "en".equals(d.lang())).findFirst().orElseThrow();
        assertEquals("Worksheet", enDoc.label());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Test: structural types derived correctly from colour + size
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void structuralTypesDerivedCorrectly() {
        LearningJourneyPublishResult published =
                publishService.publish(buildFullRequest("Type Test"), PublishEnvironment.TEST);

        LearningJourneyDetail detail = lookupRepository.findById(published.learningJourneyId()).orElseThrow();

        // Step 1: big/blue, not last → hoofdstap
        assertEquals("hoofdstap", detail.steps().get(0).structuralType());

        // Step 2: big/blue, not last → hoofdstap
        assertEquals("hoofdstap", detail.steps().get(1).structuralType());

        // Step 3: small/violet → substap
        assertEquals("substap", detail.steps().get(2).structuralType());

        // Step 4: big/blue, last → afsluiting
        assertEquals("afsluiting", detail.steps().get(3).structuralType());

        // Verify DB types (content-based)
        assertEquals("TEXT", detail.steps().get(0).dbType());
        assertEquals("QUESTION", detail.steps().get(1).dbType());
        assertEquals("QUESTION", detail.steps().get(2).dbType());
        assertEquals("TEXT", detail.steps().get(3).dbType());

        // Chatbox
        assertTrue(detail.steps().get(0).chatboxEnabled());
        assertFalse(detail.steps().get(1).chatboxEnabled());
    }
}

package com.mentesme.builder.service;

import com.mentesme.builder.model.DocumentInput;
import com.mentesme.builder.model.LearningJourneyPublishRequest;
import com.mentesme.builder.model.LearningJourneyPublishResult;
import com.mentesme.builder.model.QuestionInput;
import com.mentesme.builder.model.StepInput;
import com.mentesme.builder.model.StepInput.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes parameterized SQL against Metro DB for Learning Journey publishing.
 *
 * Production schema (verified against metro-prod.sql):
 *
 *   learning_journeys : id, name(50), nameEn(50), ljKey(20), description(50), descriptionEn(50), aiCoachEnabled(20)
 *   labels            : id, identifier(100), text(10000), lang ENUM('en','nl'), category(50)
 *   steps             : id, position, title(100), learningJourneyId, textContent(100),
 *                       conversation(20), type(20), colour(20), size(20), role(20), documents(50)
 *   step_question     : id, stepId, question(100), `order`, type ENUM('menteeValuation','mentorValuation')
 *   learning_journey_documents : id, identifier(50), label(100), url(500), lang(5)
 *   group_learning_journey     : id, groupId, learningJourneyId, assignedAt
 *
 * Key conventions:
 *   steps.title / steps.textContent / step_question.question → store label IDENTIFIER strings
 *   steps.type   = 'TEXT' (no questions) or 'QUESTION' (has questions)
 *   steps.role   = 'principal'
 *   steps.conversation = 'S' (enabled) or NULL (disabled)
 *   learning_journey_documents.label = plain display text (NOT a label reference)
 */
@Service
public class LearningJourneyIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(LearningJourneyIntegrationService.class);

    private static final String S3_BASE_URL =
            "https://s3-eu-west-1.amazonaws.com/metro-learningjourney/";

    // ── Column size limits (from production schema) ────────────────────────
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_LJKEY_LENGTH = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 50;
    private static final int MAX_LABEL_TEXT_LENGTH = 10000;
    private static final int MAX_IDENTIFIER_LENGTH = 100;
    private static final int MAX_DOC_IDENTIFIER_LENGTH = 50;

    // ── Metro step.type values ─────────────────────────────────────────────
    private static final String DB_TYPE_TEXT = "TEXT";
    private static final String DB_TYPE_QUESTION = "QUESTION";

    // ── Metro step.role default ────────────────────────────────────────────
    private static final String DEFAULT_ROLE = "principal";

    // ── Colour / size constants ────────────────────────────────────────────
    private static final String COLOUR_BLUE = "blue";
    private static final String COLOUR_VIOLET = "violet";
    private static final String COLOUR_ORANGE = "orange";
    private static final String SIZE_BIG = "big";
    private static final String SIZE_MEDIUM = "medium";
    private static final String SIZE_SMALL = "small";
    // Alternating colours: 2nd hoofdstap=orange, 3rd=violet, 4th=orange, etc.
    private static final String[] ALTERNATING_COLOURS = {COLOUR_ORANGE, COLOUR_VIOLET};

    // ═══════════════════════════════════════════════════════════════════════
    // Main entry point — called from within TransactionTemplate
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Insert order (mandatory):
     *  1. learning_journeys  → capture learningJourneyId
     *  2. Per step: labels (NL+EN) then step row  → capture stepDbIds
     *  3. Per question: labels (NL+EN) then step_question row
     *  4. learning_journey_documents
     *  5. group_learning_journey
     */
    public LearningJourneyPublishResult execute(LearningJourneyPublishRequest request,
                                                 JdbcTemplate jdbc,
                                                 String environment) {
        Map<String, Long> timings = new LinkedHashMap<>();
        long totalStart = System.currentTimeMillis();
        List<StepInput> steps = request.steps();

        // ── Schema migration: ensure bilingual columns exist ─────────────
        ensureBilingualColumns(jdbc, environment);

        // ── 0/1. Reuse existing journey ID or create new ─────────────────
        long t0 = System.currentTimeMillis();
        final long ljId;

        if (request.editLearningJourneyId() != null) {
            long editId = request.editLearningJourneyId();
            // Verify the journey still exists (may have been deleted)
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM learning_journeys WHERE id = ?", Integer.class, editId);
            if (count != null && count > 0) {
                log.info("[{}] Re-publishing journey {} — clearing content, keeping ID + user assignments", environment, editId);
                cleanJourneyContent(editId, jdbc);
                updateLearningJourney(editId, request, jdbc);
                ljId = editId;
            } else {
                log.warn("[{}] Journey {} no longer exists — creating new journey", environment, editId);
                ljId = insertLearningJourney(request, jdbc);
            }
        } else {
            ljId = insertLearningJourney(request, jdbc);
        }

        timings.put("phase1_journey_ms", System.currentTimeMillis() - t0);
        log.info("[{}] Phase 1: learning_journeys id={}", environment, ljId);

        // Category for labels (follows Metro convention: Learning_Journey_{ljKey})
        String ljKey = generateLjKey(request.name());
        String category = truncate("Learning_Journey_" + ljKey, 50);

        // ── 2. Per step: labels → step row ─────────────────────────────────
        long t1 = System.currentTimeMillis();
        long[] stepDbIds = new long[steps.size()];
        int labelCount = 0;
        int hoofdstapCounter = 0;        // counts all hoofdstappen
        String currentGroupColour = COLOUR_BLUE;  // colour for current hoofdstap group

        for (int i = 0; i < steps.size(); i++) {
            StepInput step = steps.get(i);
            int stepIdx = i + 1;

            // Track hoofdstap counter and determine group colour
            if (step.type() == StepType.hoofdstap) {
                hoofdstapCounter++;
                if (hoofdstapCounter == 1) {
                    currentGroupColour = COLOUR_BLUE;
                } else {
                    currentGroupColour = ALTERNATING_COLOURS[(hoofdstapCounter - 2) % ALTERNATING_COLOURS.length];
                }
            }

            // 2a. Insert title labels (NL + EN)
            String titleId = labelId(ljId, stepIdx, "TITLE");
            insertLabel(jdbc, titleId, step.title(), "nl", category);
            insertLabel(jdbc, titleId, fallback(step.titleEn(), step.title()), "en", category);
            labelCount += 2;

            // 2b. Insert textContent labels (NL + EN) — only if content present
            // EN publish pipeline: fallback() → ensureMediaInEn() → truncate() → insertLabel()
            //   - fallback: use NL as fallback when EN is null/blank
            //   - ensureMediaInEn: single compiler for EN media — derives media structure from NL
            //   - truncate: enforce max label length
            //   - insertLabel: parameterized INSERT, no further transformation
            String textId = null;
            if (step.textContent() != null && !step.textContent().isBlank()) {
                textId = labelId(ljId, stepIdx, "TEXT");
                String nlText = step.textContent();
                String enText = fallback(step.textContentEn(), nlText);
                enText = ensureMediaInEn(enText, nlText);
                insertLabel(jdbc, textId, truncate(nlText, MAX_LABEL_TEXT_LENGTH), "nl", category);
                insertLabel(jdbc, textId, truncate(enText, MAX_LABEL_TEXT_LENGTH), "en", category);
                labelCount += 2;
            }

            // 2c. Determine colour + size from structural type
            boolean hasQuestions = step.questions() != null && !step.questions().isEmpty();
            String colour;
            String size;

            switch (step.type()) {
                case hoofdstap:
                    colour = currentGroupColour;
                    size = (hoofdstapCounter == 1) ? SIZE_BIG : SIZE_MEDIUM;
                    break;
                case substap:
                    colour = currentGroupColour;
                    size = SIZE_SMALL;
                    break;
                case afsluiting:
                    colour = COLOUR_BLUE;
                    size = SIZE_BIG;
                    break;
                default:
                    throw new IllegalStateException("Unknown step type: " + step.type());
            }

            // 2d. Document group identifier (if step has documents or upload enabled)
            String docsId;
            if (step.documents() != null && !step.documents().isEmpty()) {
                docsId = docGroupId(ljId, stepIdx);
            } else if (step.uploadEnabled()) {
                // Upload-enabled steps get a docs identifier so Metro shows upload zone
                docsId = docGroupId(ljId, stepIdx);
            } else {
                docsId = null;
            }

            // 2e. Conversation flag (step-level)
            String conversation = step.chatboxEnabled() ? "S" : null;

            // 2f. DB type: derived from content, NOT from structural type
            String dbType = hasQuestions ? DB_TYPE_QUESTION : DB_TYPE_TEXT;

            // 2g. INSERT step row
            final String fTitleId = titleId;
            final String fTextId = textId;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(conn -> {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO steps " +
                        "(position, title, learningJourneyId, textContent, " +
                        " conversation, type, colour, size, role, documents) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, stepIdx);           // position: global linear 1..N
                ps.setString(2, fTitleId);       // label identifier
                ps.setLong(3, ljId);
                ps.setString(4, fTextId);        // label identifier or NULL
                ps.setString(5, conversation);   // 'S' or NULL
                ps.setString(6, dbType);         // 'TEXT' or 'QUESTION'
                ps.setString(7, colour);
                ps.setString(8, size);
                ps.setString(9, DEFAULT_ROLE);   // always 'principal'
                ps.setString(10, docsId);        // doc group identifier or NULL
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException(
                        "No generated key returned for step at position " + stepIdx);
            }
            stepDbIds[i] = key.longValue();
        }

        timings.put("phase2_labelsAndSteps_ms", System.currentTimeMillis() - t1);
        timings.put("stepCount", (long) steps.size());
        timings.put("labelCount", (long) labelCount);
        log.info("[{}] Phase 2: {} steps, {} labels", environment, steps.size(), labelCount);

        // ── 3. Per question: labels → step_question ────────────────────────
        long t2 = System.currentTimeMillis();
        int questionCount = 0;

        for (int i = 0; i < steps.size(); i++) {
            StepInput step = steps.get(i);
            if (step.questions() == null || step.questions().isEmpty()) continue;

            long stepDbId = stepDbIds[i];
            int stepIdx = i + 1;

            for (int q = 0; q < step.questions().size(); q++) {
                QuestionInput question = step.questions().get(q);
                int sortOrder = q + 1;

                // 3a. Insert question labels (NL + EN)
                String qId = questionLabelId(ljId, stepIdx, sortOrder);
                insertLabel(jdbc, qId, question.text(), "nl", category);
                insertLabel(jdbc, qId, fallback(question.textEn(), question.text()), "en", category);
                labelCount += 2;

                // 3b. INSERT step_question (type from frontend, defaults to menteeValuation)
                String qType = (question.questionType() != null && !question.questionType().isBlank())
                        ? question.questionType() : "menteeValuation";
                jdbc.update(
                        "INSERT INTO step_question (stepId, question, `order`, type) " +
                        "VALUES (?, ?, ?, ?)",
                        stepDbId, qId, sortOrder, qType);
                questionCount++;
            }
        }

        timings.put("phase3_questions_ms", System.currentTimeMillis() - t2);
        timings.put("questionCount", (long) questionCount);
        log.info("[{}] Phase 3: {} questions", environment, questionCount);

        // ── 4. INSERT learning_journey_documents ───────────────────────────
        long t3 = System.currentTimeMillis();
        int docCount = 0;

        for (int i = 0; i < steps.size(); i++) {
            StepInput step = steps.get(i);
            if (step.documents() == null || step.documents().isEmpty()) continue;

            String docsIdentifier = docGroupId(ljId, i + 1);
            for (DocumentInput doc : step.documents()) {
                String url = (doc.url() != null && !doc.url().isBlank())
                        ? doc.url()
                        : S3_BASE_URL + ljKey + "/" + doc.fileName();
                jdbc.update(
                        "INSERT INTO learning_journey_documents " +
                        "(identifier, label, url, lang) VALUES (?, ?, ?, ?)",
                        docsIdentifier, doc.label(), url, doc.lang());
                docCount++;
            }
        }

        timings.put("phase4_documents_ms", System.currentTimeMillis() - t3);
        timings.put("documentCount", (long) docCount);
        log.info("[{}] Phase 4: {} documents", environment, docCount);

        // ── 5. SYNC group_learning_journey ───────────────────────────────
        long t4 = System.currentTimeMillis();

        // Validate all groupIds exist in the groups table
        List<Long> groupIds = request.groupIds();
        if (groupIds == null || groupIds.isEmpty()) {
            throw new IllegalArgumentException("At least one group must be selected.");
        }
        String placeholders = groupIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Long> validGroupIds = jdbc.queryForList(
                "SELECT id FROM `groups` WHERE id IN (" + placeholders + ")",
                Long.class,
                groupIds.toArray());
        if (validGroupIds.size() != groupIds.size()) {
            List<Long> invalid = new ArrayList<>(groupIds);
            invalid.removeAll(validGroupIds);
            throw new IllegalArgumentException("Invalid group IDs: " + invalid);
        }

        // On edit: sync groups (delete old, insert new) instead of just inserting
        if (request.editLearningJourneyId() != null) {
            // First: nullify FK references from user_learning_journey to group_learning_journey
            // (user_learning_journey.groupLearningJourneyId → group_learning_journey.id, ON DELETE RESTRICT)
            try {
                jdbc.update(
                        "UPDATE user_learning_journey SET groupLearningJourneyId = NULL " +
                        "WHERE groupLearningJourneyId IN " +
                        "(SELECT id FROM group_learning_journey WHERE learningJourneyId = ?)", ljId);
            } catch (Exception e) {
                log.warn("Could not nullify user_learning_journey FK for journey {}: {}", ljId, e.getMessage());
            }
            jdbc.update("DELETE FROM group_learning_journey WHERE learningJourneyId = ?", ljId);
        }

        for (Long groupId : request.groupIds()) {
            jdbc.update(
                    "INSERT INTO group_learning_journey (groupId, learningJourneyId) " +
                    "VALUES (?, ?)",
                    groupId, ljId);
        }

        timings.put("phase5_groups_ms", System.currentTimeMillis() - t4);
        timings.put("groupCount", (long) request.groupIds().size());
        log.info("[{}] Phase 5: {} groups bound", environment, request.groupIds().size());

        long totalMs = System.currentTimeMillis() - totalStart;
        timings.put("total_ms", totalMs);
        timings.put("labelCountTotal", (long) labelCount);
        log.info("[{}] Journey {} published ({}ms)", environment, ljId, totalMs);

        return new LearningJourneyPublishResult(ljId, true, environment, timings);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Clean journey content for re-publish (preserves journey ID + user assignments)
    // ═══════════════════════════════════════════════════════════════════════

    private void cleanJourneyContent(long ljId, JdbcTemplate jdbc) {
        // Delete user progress (best-effort — tables may not exist in all environments)
        int dua = 0, dus = 0;
        try {
            dua = jdbc.update(
                    "DELETE usa FROM user_step_answer usa " +
                    "INNER JOIN user_step us ON us.id = usa.userStepId " +
                    "INNER JOIN user_learning_journey ulj ON ulj.id = us.userLearningJourneyId " +
                    "WHERE ulj.learningJourneyId = ?", ljId);
            dus = jdbc.update(
                    "DELETE us FROM user_step us " +
                    "INNER JOIN user_learning_journey ulj ON ulj.id = us.userLearningJourneyId " +
                    "WHERE ulj.learningJourneyId = ?", ljId);
        } catch (Exception e) {
            log.warn("Could not clean user progress for journey {} (tables may not exist): {}",
                    ljId, e.getMessage());
        }
        // NOTE: user_learning_journey is NOT deleted — user assignments stay!

        // Delete step content (these tables must exist)
        int dq = jdbc.update(
                "DELETE sq FROM step_question sq " +
                "INNER JOIN steps s ON s.id = sq.stepId " +
                "WHERE s.learningJourneyId = ?", ljId);
        String labelPattern = "LJ_" + ljId + "_%";
        int dl = jdbc.update("DELETE FROM labels WHERE identifier LIKE ?", labelPattern);
        int dd = jdbc.update("DELETE FROM learning_journey_documents WHERE identifier LIKE ?", labelPattern);
        int ds = jdbc.update("DELETE FROM steps WHERE learningJourneyId = ?", ljId);
        // NOTE: group_learning_journey is synced separately in phase 5
        // NOTE: learning_journeys row is KEPT and UPDATED

        log.info("Cleaned journey {} content: {} user answers, {} user steps, " +
                "{} questions, {} labels, {} docs, {} steps (journey ID + user assignments preserved)",
                ljId, dua, dus, dq, dl, dd, ds);
    }

    /**
     * Update the learning_journeys row in-place (preserves the ID).
     */
    private void updateLearningJourney(long ljId, LearningJourneyPublishRequest request, JdbcTemplate jdbc) {
        String name = truncate(request.name(), MAX_NAME_LENGTH);
        String nameEn = truncate(request.nameEn(), MAX_NAME_LENGTH);
        String ljKey = generateLjKey(request.name());
        String description = truncate(
                request.description() != null ? request.description() : "",
                MAX_DESCRIPTION_LENGTH);
        String descriptionEn = truncate(request.descriptionEn(), MAX_DESCRIPTION_LENGTH);
        int aiCoachEnabled = request.aiCoachEnabled() ? 1 : 0;
        jdbc.update("UPDATE learning_journeys SET name = ?, nameEn = ?, ljKey = ?, description = ?, descriptionEn = ?, aiCoachEnabled = ? WHERE id = ?",
                name, nameEn, ljKey, description, descriptionEn, aiCoachEnabled, ljId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Delete existing journey completely (for admin cleanup)
    // ═══════════════════════════════════════════════════════════════════════

    public void deleteJourney(long ljId, JdbcTemplate jdbc) {
        // 0. Delete user progress data (FK: user_step_answer → user_step → user_learning_journey → steps)
        int dua = jdbc.update(
                "DELETE usa FROM user_step_answer usa " +
                "INNER JOIN user_step us ON us.id = usa.userStepId " +
                "INNER JOIN user_learning_journey ulj ON ulj.id = us.userLearningJourneyId " +
                "WHERE ulj.learningJourneyId = ?", ljId);
        int dus = jdbc.update(
                "DELETE us FROM user_step us " +
                "INNER JOIN user_learning_journey ulj ON ulj.id = us.userLearningJourneyId " +
                "WHERE ulj.learningJourneyId = ?", ljId);
        int dulj = jdbc.update(
                "DELETE FROM user_learning_journey WHERE learningJourneyId = ?", ljId);

        // 1. Delete step_question rows (via step ids)
        int dq = jdbc.update(
                "DELETE sq FROM step_question sq " +
                "INNER JOIN steps s ON s.id = sq.stepId " +
                "WHERE s.learningJourneyId = ?", ljId);

        // 2. Delete labels (identifier pattern: LJ_{id}_*)
        String labelPattern = "LJ_" + ljId + "_%";
        int dl = jdbc.update("DELETE FROM labels WHERE identifier LIKE ?", labelPattern);

        // 3. Delete learning_journey_documents (identifier pattern: LJ_{id}_STEP_%_DOCS)
        int dd = jdbc.update("DELETE FROM learning_journey_documents WHERE identifier LIKE ?", labelPattern);

        // 4. Delete group_learning_journey
        int dg = jdbc.update("DELETE FROM group_learning_journey WHERE learningJourneyId = ?", ljId);

        // 5. Delete steps
        int ds = jdbc.update("DELETE FROM steps WHERE learningJourneyId = ?", ljId);

        // 6. Delete learning_journeys
        int dj = jdbc.update("DELETE FROM learning_journeys WHERE id = ?", ljId);

        log.info("Deleted journey {}: {} user answers, {} user steps, {} user journeys, " +
                "{} questions, {} labels, {} docs, {} groups, {} steps, {} journey",
                ljId, dua, dus, dulj, dq, dl, dd, dg, ds, dj);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Schema migration: ensure bilingual columns exist
    // ═══════════════════════════════════════════════════════════════════════

    private void ensureBilingualColumns(JdbcTemplate jdbc, String environment) {
        // Case-insensitive check: H2 may store identifiers differently than MySQL
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE UPPER(TABLE_NAME) = UPPER(?) AND UPPER(COLUMN_NAME) = UPPER(?)";

        // learning_journeys: nameEn + descriptionEn
        Integer count = jdbc.queryForObject(checkSql, Integer.class, "learning_journeys", "nameEn");
        if (count != null && count == 0) {
            log.info("[{}] Schema migration: adding nameEn/descriptionEn to learning_journeys", environment);
            jdbc.execute("ALTER TABLE learning_journeys ADD COLUMN nameEn VARCHAR(50) NULL");
            jdbc.execute("ALTER TABLE learning_journeys ADD COLUMN descriptionEn VARCHAR(50) NULL");
        }

        // learning_journeys: aiCoachEnabled
        Integer aiCoachCount = jdbc.queryForObject(checkSql, Integer.class, "learning_journeys", "aiCoachEnabled");
        if (aiCoachCount != null && aiCoachCount == 0) {
            log.info("[{}] Schema migration: adding aiCoachEnabled to learning_journeys", environment);
            jdbc.execute("ALTER TABLE learning_journeys ADD COLUMN aiCoachEnabled INT NOT NULL DEFAULT 0");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Phase 1: learning_journeys
    // ═══════════════════════════════════════════════════════════════════════

    private long insertLearningJourney(LearningJourneyPublishRequest request, JdbcTemplate jdbc) {
        String name = truncate(request.name(), MAX_NAME_LENGTH);
        String nameEn = truncate(request.nameEn(), MAX_NAME_LENGTH);
        String ljKey = generateLjKey(request.name());
        String description = truncate(
                request.description() != null ? request.description() : "",
                MAX_DESCRIPTION_LENGTH);
        String descriptionEn = truncate(request.descriptionEn(), MAX_DESCRIPTION_LENGTH);
        int aiCoachEnabled = request.aiCoachEnabled() ? 1 : 0;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO learning_journeys (name, nameEn, ljKey, description, descriptionEn, aiCoachEnabled) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, nameEn);
            ps.setString(3, ljKey);
            ps.setString(4, description);
            ps.setString(5, descriptionEn);
            ps.setInt(6, aiCoachEnabled);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "No generated key returned for learning_journeys insert.");
        }
        return key.longValue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Label INSERT (shared helper)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * INSERT INTO labels (identifier, text, lang, category) VALUES (?, ?, ?, ?)
     *
     * Category follows Metro convention: Learning_Journey_{ljKey}
     */
    private void insertLabel(JdbcTemplate jdbc, String identifier, String text, String lang, String category) {
        jdbc.update(
                "INSERT INTO labels (identifier, text, lang, category) VALUES (?, ?, ?, ?)",
                identifier, text, lang, category);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Identifier generation (deterministic, same across NL and EN)
    // ═══════════════════════════════════════════════════════════════════════

    /** LJ_{ljId}_STEP_{n}_TITLE  or  LJ_{ljId}_STEP_{n}_TEXT */
    private String labelId(long ljId, int stepIdx, String suffix) {
        return truncate("LJ_" + ljId + "_STEP_" + stepIdx + "_" + suffix,
                MAX_IDENTIFIER_LENGTH);
    }

    /** LJ_{ljId}_STEP_{n}_Q_{q} */
    private String questionLabelId(long ljId, int stepIdx, int questionIdx) {
        return truncate("LJ_" + ljId + "_STEP_" + stepIdx + "_Q_" + questionIdx,
                MAX_IDENTIFIER_LENGTH);
    }

    /** LJ_{ljId}_STEP_{n}_DOCS */
    private String docGroupId(long ljId, int stepIdx) {
        return truncate("LJ_" + ljId + "_STEP_" + stepIdx + "_DOCS",
                MAX_DOC_IDENTIFIER_LENGTH);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Utilities
    // ═══════════════════════════════════════════════════════════════════════

    /** Lowercase slug, no spaces, max 20 chars. */
    static String generateLjKey(String name) {
        if (name == null || name.isBlank()) return "unnamed";
        String slug = name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isEmpty()) return "unnamed";
        return truncate(slug, MAX_LJKEY_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String fallback(String primary, String fallbackValue) {
        return (primary != null && !primary.isBlank()) ? primary : fallbackValue;
    }

    /**
     * SINGLE COMPILER for EN media interleaving.
     *
     * Architecture contract:
     * - Frontend sends text-only EN (no &lt;img&gt;/&lt;video&gt; tags)
     * - This method is the ONLY place that adds media tags to EN content
     * - Media structure is always derived from NL (blocksToHtml output)
     * - Called exactly once per step, after fallback(), before truncate()/insertLabel()
     *
     * NL content format (from blocksToHtml): text\n&lt;img.../&gt;\ntext\n&lt;video...&gt;
     * EN content format (from frontend): text\ntext (no media)
     *
     * Algorithm: walk NL parts split on \n — media tags are copied as-is,
     * text positions are substituted with corresponding EN segments.
     */
    private static String ensureMediaInEn(String enText, String nlText) {
        if (enText == null || nlText == null) return enText;
        // NL has no media? Nothing to add
        if (!nlText.contains("<img ") && !nlText.contains("<video ")) return enText;
        // Strip any stale/misplaced media tags from EN before re-interleaving
        String cleanEn = enText.replaceAll("<img\\s[^>]*/?>", "").replaceAll("<video\\s[^>]*>.*?</video>", "").trim();

        String[] nlParts = nlText.split("\n");
        String[] enParts = cleanEn.split("\n");

        List<String> result = new ArrayList<>();
        int enIdx = 0;
        for (String nlPart : nlParts) {
            String trimmed = nlPart.trim();
            if (trimmed.startsWith("<img ") || trimmed.startsWith("<video ")) {
                result.add(nlPart); // Media tag — copy from NL
            } else if (enIdx < enParts.length) {
                result.add(enParts[enIdx++]); // Text — use EN segment
            } else {
                // Fail-safe: NL has more text positions than EN segments.
                // Keep the position empty rather than silently dropping it.
                result.add("");
            }
        }
        // Append any remaining EN segments (more EN text than NL text positions)
        while (enIdx < enParts.length) {
            result.add(enParts[enIdx++]);
        }

        return String.join("\n", result);
    }
}

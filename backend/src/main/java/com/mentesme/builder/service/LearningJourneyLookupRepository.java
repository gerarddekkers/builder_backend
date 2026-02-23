package com.mentesme.builder.service;

import com.mentesme.builder.model.LearningJourneyDetail;
import com.mentesme.builder.model.LearningJourneyDetail.DocumentDetail;
import com.mentesme.builder.model.LearningJourneyDetail.QuestionDetail;
import com.mentesme.builder.model.LearningJourneyDetail.StepDetail;
import com.mentesme.builder.model.LearningJourneyListItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only lookups for learning journeys in the Metro database.
 */
@Repository
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
public class LearningJourneyLookupRepository {

    private final JdbcTemplate jdbc;

    public LearningJourneyLookupRepository(
            @Qualifier("metroJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LearningJourneyListItem> findAll() {
        return jdbc.query(
                "SELECT id, name, ljKey FROM learning_journeys ORDER BY id DESC",
                (rs, rowNum) -> new LearningJourneyListItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("ljKey")
                ));
    }

    public Optional<LearningJourneyDetail> findById(long id) {
        boolean hasAiCoach = checkAiCoachEnabledColumn();
        String aiCoachSelect = hasAiCoach ? ", aiCoachEnabled" : "";
        String sql = "SELECT id, name, nameEn, ljKey, description, descriptionEn" + aiCoachSelect +
                     " FROM learning_journeys WHERE id = ?";

        List<LearningJourneyDetail> results = jdbc.query(sql,
                (rs, rowNum) -> {
                    long ljId = rs.getLong("id");
                    boolean aiCoachEnabled = hasAiCoach && rs.getInt("aiCoachEnabled") == 1;
                    return new LearningJourneyDetail(
                            ljId,
                            rs.getString("name"),
                            rs.getString("nameEn"),
                            rs.getString("ljKey"),
                            rs.getString("description"),
                            rs.getString("descriptionEn"),
                            findSteps(ljId),
                            findDocuments(ljId),
                            findGroupIds(ljId),
                            aiCoachEnabled
                    );
                },
                id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private boolean hasAiCoachEnabledCol = false;
    private boolean aiCoachEnabledChecked = false;

    private boolean checkAiCoachEnabledColumn() {
        if (!aiCoachEnabledChecked) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'learning_journeys' AND COLUMN_NAME = 'aiCoachEnabled'",
                    Integer.class);
            hasAiCoachEnabledCol = (count != null && count > 0);
            aiCoachEnabledChecked = true;
        }
        return hasAiCoachEnabledCol;
    }

    private List<StepDetail> findSteps(long ljId) {
        String sql =
                "SELECT s.id, s.position, s.title AS titleIdentifier, " +
                "  s.textContent AS textIdentifier, s.type AS dbType, " +
                "  s.colour, s.size, s.role, s.conversation, s.documents " +
                ", (SELECT l.text FROM labels l WHERE l.identifier = s.title AND l.lang = 'nl' LIMIT 1) AS titleNl, " +
                "  (SELECT l.text FROM labels l WHERE l.identifier = s.title AND l.lang = 'en' LIMIT 1) AS titleEn, " +
                "  (SELECT l.text FROM labels l WHERE l.identifier = s.textContent AND l.lang = 'nl' LIMIT 1) AS textContentNl, " +
                "  (SELECT l.text FROM labels l WHERE l.identifier = s.textContent AND l.lang = 'en' LIMIT 1) AS textContentEn " +
                "FROM steps s WHERE s.learningJourneyId = ? ORDER BY s.position";

        List<StepRow> rows = jdbc.query(sql, (rs, rowNum) -> new StepRow(
                rs.getLong("id"),
                rs.getInt("position"),
                rs.getString("titleNl"),
                rs.getString("titleEn"),
                rs.getString("textContentNl"),
                rs.getString("textContentEn"),
                rs.getString("dbType"),
                rs.getString("colour"),
                rs.getString("size"),
                rs.getString("conversation"),
                rs.getString("documents")
        ), ljId);

        int totalSteps = rows.size();
        return java.util.stream.IntStream.range(0, totalSteps).mapToObj(i -> {
            StepRow row = rows.get(i);
            boolean isLast = (i == totalSteps - 1);
            String structuralType = deriveStructuralType(
                    row.size, row.colour, isLast);
            return new StepDetail(
                    row.id,
                    row.position,
                    structuralType,
                    row.titleNl,
                    row.titleEn,
                    row.textContentNl,
                    row.textContentEn,
                    row.dbType,
                    row.colour,
                    row.size,
                    "S".equals(row.conversation),
                    row.documents,
                    findQuestions(row.id)
            );
        }).toList();
    }

    private List<QuestionDetail> findQuestions(long stepId) {
        return jdbc.query(
                "SELECT sq.id, sq.`order`, sq.type AS questionType, " +
                "  (SELECT l.text FROM labels l WHERE l.identifier = sq.question AND l.lang = 'nl' LIMIT 1) AS textNl, " +
                "  (SELECT l.text FROM labels l WHERE l.identifier = sq.question AND l.lang = 'en' LIMIT 1) AS textEn " +
                "FROM step_question sq WHERE sq.stepId = ? ORDER BY sq.`order`",
                (rs, rowNum) -> new QuestionDetail(
                        rs.getLong("id"),
                        rs.getInt("order"),
                        rs.getString("textNl"),
                        rs.getString("textEn"),
                        rs.getString("questionType")
                ),
                stepId);
    }

    private List<DocumentDetail> findDocuments(long ljId) {
        return jdbc.query(
                "SELECT d.id, d.identifier, d.label, d.url, d.lang " +
                "FROM learning_journey_documents d " +
                "WHERE d.identifier LIKE ? ORDER BY d.identifier, d.lang",
                (rs, rowNum) -> new DocumentDetail(
                        rs.getLong("id"),
                        rs.getString("identifier"),
                        rs.getString("label"),
                        rs.getString("url"),
                        rs.getString("lang")
                ),
                "LJ_" + ljId + "_%");
    }

    private List<Long> findGroupIds(long ljId) {
        return jdbc.queryForList(
                "SELECT groupId FROM group_learning_journey WHERE learningJourneyId = ?",
                Long.class, ljId);
    }

    /**
     * Derive the structural step type from DB colour/size/position.
     *
     * Reverse of the publish encoding:
     *   small            → substap
     *   medium           → hoofdstap
     *   big + last step  → afsluiting
     *   big + not last   → hoofdstap
     */
    static String deriveStructuralType(String size, String colour, boolean isLast) {
        if ("small".equals(size)) return "substap";
        if ("medium".equals(size)) return "hoofdstap";
        // big
        if (isLast) return "afsluiting";
        return "hoofdstap";
    }

    private record StepRow(
            long id, int position,
            String titleNl, String titleEn,
            String textContentNl, String textContentEn,
            String dbType, String colour, String size,
            String conversation, String documents
    ) {}
}

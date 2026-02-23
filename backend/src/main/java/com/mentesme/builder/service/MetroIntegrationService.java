package com.mentesme.builder.service;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.CompetenceInput;
import com.mentesme.builder.model.IntegrationPreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MetroIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(MetroIntegrationService.class);

    /**
     * Generate SQL statements for publishing an assessment to a Metro database.
     * Uses the provided repository for lookups and fresh ID generation.
     * All sequences are local to this call — no shared state between invocations.
     */
    public IntegrationPreviewResponse generatePreview(AssessmentBuildRequest request, MetroLookupRepository repo) {
        // Validate all groups exist in target database (single query)
        var missingGroups = repo.findMissingGroupIds(request.groupIds());
        if (!missingGroups.isEmpty()) {
            throw new IllegalArgumentException(
                    "Group(s) with ID " + missingGroups + " do not exist in the target database.");
        }

        List<String> sql = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Local sequences — all fetched in a single query
        Map<String, Long> maxIds = repo.getAllMaxIds();
        long questionnaireSeq = maxIds.get("questionnaires") + 1;
        long categorySeq = maxIds.get("categories") + 1;
        long competenceSeq = maxIds.get("competences") + 1;
        long goalSeq = maxIds.get("goals") + 1;
        long itemSeq = maxIds.get("items") + 1;
        long cqSeq = maxIds.get("cq") + 1;

        Map<String, Long> categoryIds = new HashMap<>();
        Map<String, Long> goalIds = new HashMap<>();

        String assessmentName = safeTrim(request.assessmentName());
        String truncatedName = truncate(assessmentName, 30, warnings);

        // Determine questionnaire ID:
        // 1. If editQuestionnaireId is set, always use that (even if name changed)
        // 2. Otherwise, look up by name (existing re-publish behavior)
        // 3. Otherwise, create new
        Long existingQuestionnaireId;
        if (request.editQuestionnaireId() != null) {
            existingQuestionnaireId = request.editQuestionnaireId();
        } else {
            existingQuestionnaireId = repo.findQuestionnaireIdByName(truncatedName).orElse(null);
        }

        long questionnaireId;
        if (existingQuestionnaireId != null) {
            questionnaireId = existingQuestionnaireId;

            // Clean up old items linked to this questionnaire (cascade delete)
            sql.add("DELETE it FROM item_translations it " +
                    "INNER JOIN questionnaire_items qi ON qi.itemId = it.itemId " +
                    "WHERE qi.questionnaireId = " + questionnaireId + ";");
            sql.add("DELETE ci FROM competence_items ci " +
                    "INNER JOIN questionnaire_items qi ON qi.itemId = ci.itemId " +
                    "WHERE qi.questionnaireId = " + questionnaireId + ";");
            sql.add("DELETE i FROM items i " +
                    "INNER JOIN questionnaire_items qi ON qi.itemId = i.id " +
                    "WHERE qi.questionnaireId = " + questionnaireId + ";");
            sql.add("DELETE FROM questionnaire_items WHERE questionnaireId = " + questionnaireId + ";");
            sql.add("DELETE FROM competence_questions WHERE questionnaireId = " + questionnaireId + ";");

            // Clean up old group links for this questionnaire (will be re-inserted below)
            sql.add("DELETE FROM group_questionnaires WHERE questionnaireId = " + questionnaireId + ";");

            // Refresh questionnaire translations (clears old XML URLs; S3 upload will re-set them)
            sql.add("DELETE FROM questionnaire_translations WHERE questionnaireId = " + questionnaireId + ";");
            sql.add("INSERT INTO questionnaire_translations(questionnaireId, language, name, questions, report) VALUES (" +
                    questionnaireId + ", 'nl', '" + escape(truncatedName) + "', NULL, NULL);");
            sql.add("INSERT INTO questionnaire_translations(questionnaireId, language, name, questions, report) VALUES (" +
                    questionnaireId + ", 'en', '" + escape(truncatedName) + "', NULL, NULL);");

            // Also update the questionnaire name if it changed
            sql.add("UPDATE questionnaires SET name = '" + escape(truncatedName) + "' WHERE id = " + questionnaireId + ";");

            warnings.add("Questionnaire '" + truncatedName + "' wordt bijgewerkt (ID: " + questionnaireId +
                    "). Oude items verwijderd, nieuwe worden aangemaakt.");
        } else {
            questionnaireId = questionnaireSeq++;
            sql.add("INSERT INTO questionnaires(id, name) VALUES (" + questionnaireId + ", '" + escape(truncatedName) + "');");
            sql.add("INSERT INTO questionnaire_translations(questionnaireId, language, name, questions, report) VALUES (" +
                    questionnaireId + ", 'nl', '" + escape(truncatedName) + "', NULL, NULL);");
            sql.add("INSERT INTO questionnaire_translations(questionnaireId, language, name, questions, report) VALUES (" +
                    questionnaireId + ", 'en', '" + escape(truncatedName) + "', NULL, NULL);");
        }

        long newCompetenceCount = 0;
        long newCategoryCount = 0;
        long newGoalCount = 0;
        long newItemCount = 0;
        int itemOrder = 0;

        // Track section numbers for competence_questions questionId (matches XML section numbering)
        Map<String, Integer> categorySections = new HashMap<>();
        Map<String, Integer> sectionQuestionCounters = new HashMap<>();
        int nextSectionNumber = 1;

        for (CompetenceInput input : request.competences()) {
            String categoryName = safeTrim(input.category());
            String subcategoryName = safeTrim(input.subcategory());

            String categoryKey = categoryName.toLowerCase(Locale.ROOT);
            Long categoryId = categoryIds.get(categoryKey);
            if (categoryId == null) {
                categoryId = repo.findCategoryIdByName(categoryName).orElse(null);
                if (categoryId == null) {
                    categoryId = categorySeq++;
                    sql.add("INSERT INTO categories(id, name) VALUES (" + categoryId + ", '" + escape(categoryName) + "');");
                    sql.add("INSERT INTO category_translations(categoryId, language, name) VALUES (" + categoryId + ", 'nl', '" + escape(categoryName) + "');");
                    sql.add("INSERT INTO category_translations(categoryId, language, name) VALUES (" + categoryId + ", 'en', '" + escape(categoryName) + "');");
                    newCategoryCount++;
                }
                categoryIds.put(categoryKey, categoryId);
            }
            // Track section number for this category (for questionId in competence_questions)
            if (!categorySections.containsKey(categoryKey)) {
                categorySections.put(categoryKey, nextSectionNumber++);
                sectionQuestionCounters.put(categoryKey, 0);
            }

            Long goalId = null;
            if (!subcategoryName.isBlank()) {
                String goalKey = subcategoryName.toLowerCase(Locale.ROOT);
                goalId = goalIds.get(goalKey);
                if (goalId == null) {
                    goalId = repo.findGoalIdByName(subcategoryName).orElse(null);
                    if (goalId == null) {
                        goalId = goalSeq++;
                        sql.add("INSERT INTO goals(id, name) VALUES (" + goalId + ", '" + escape(subcategoryName) + "');");
                        sql.add("INSERT INTO goal_translations(goalId, language, name) VALUES (" + goalId + ", 'nl', '" + escape(subcategoryName) + "');");
                        sql.add("INSERT INTO goal_translations(goalId, language, name) VALUES (" + goalId + ", 'en', '" + escape(subcategoryName) + "');");
                        newGoalCount++;
                    }
                    goalIds.put(goalKey, goalId);
                }
            }

            Long competenceId = input.existingId();
            if (competenceId == null) {
                competenceId = repo.findCompetenceIdByName(input.name()).orElse(null);
            }

            if (competenceId == null && input.isNew()) {
                competenceId = competenceSeq++;
                String competenceName = safeTrim(input.name());
                String description = safeTrim(input.description());
                String nameEn = safeTrim(input.nameEn());
                String descriptionEn = safeTrim(input.descriptionEn());

                sql.add("INSERT INTO competences(id, name, description, defaultMinPassScore, defaultMinMentorScore) VALUES (" +
                        competenceId + ", '" + escape(competenceName) + "', " + nullOrQuoted(description) + ", NULL, NULL);");

                sql.add("INSERT INTO competence_translations(competenceId, language, name, description) VALUES (" +
                        competenceId + ", 'nl', '" + escape(competenceName) + "', " + nullOrQuoted(description) + ");");

                String effectiveEnName = nameEn.isBlank() ? competenceName : nameEn;
                String effectiveEnDescription = descriptionEn.isBlank() ? description : descriptionEn;
                sql.add("INSERT INTO competence_translations(competenceId, language, name, description) VALUES (" +
                        competenceId + ", 'en', '" + escape(effectiveEnName) + "', " + nullOrQuoted(effectiveEnDescription) + ");");
                newCompetenceCount++;
            }

            if (competenceId == null) {
                warnings.add("Competence '" + input.name() + "' is gemarkeerd als bestaand maar heeft geen existingId. Koppelingen zijn overgeslagen.");
                continue;
            }

            sql.add("INSERT IGNORE INTO category_competences (categoryId, competenceId) VALUES (" + categoryId + ", " + competenceId + ");");

            if (goalId != null) {
                sql.add("INSERT IGNORE INTO goal_competences (goalId, competenceId) VALUES (" + goalId + ", " + competenceId + ");");
            }

            // Create item for this competence
            String questionLeft = safeTrim(input.questionLeft());
            String questionRight = safeTrim(input.questionRight());
            String questionLeftEn = safeTrim(input.questionLeftEn());
            String questionRightEn = safeTrim(input.questionRightEn());

            if (!questionLeft.isBlank() || !questionRight.isBlank()) {
                long itemId = itemSeq++;
                String itemName = safeTrim(input.name()) + "_item";

                sql.add("INSERT INTO items(id, name, invertOrder) VALUES (" + itemId + ", '" + escape(itemName) + "', 0);");

                String effectiveLeftNl = questionLeft.isBlank() ? questionRight : questionLeft;
                String effectiveRightNl = questionRight.isBlank() ? questionLeft : questionRight;
                String effectiveLeftEn = questionLeftEn.isBlank() ? effectiveLeftNl : questionLeftEn;
                String effectiveRightEn = questionRightEn.isBlank() ? effectiveRightNl : questionRightEn;

                sql.add("INSERT INTO item_translations(itemId, language, leftText, rightText) VALUES (" +
                        itemId + ", 'nl', '" + escape(effectiveLeftNl) + "', '" + escape(effectiveRightNl) + "');");
                sql.add("INSERT INTO item_translations(itemId, language, leftText, rightText) VALUES (" +
                        itemId + ", 'en', '" + escape(effectiveLeftEn) + "', '" + escape(effectiveRightEn) + "');");

                itemOrder++;
                sql.add("INSERT IGNORE INTO questionnaire_items (questionnaireId, itemId, `order`) VALUES (" +
                        questionnaireId + ", " + itemId + ", " + itemOrder + ");");

                sql.add("INSERT IGNORE INTO competence_items (competenceId, itemId) VALUES (" + competenceId + ", " + itemId + ");");

                // Link competence to question for Metro scoring (competence_questions)
                int sectionNum = categorySections.get(categoryKey);
                int questionInSection = sectionQuestionCounters.get(categoryKey) + 1;
                sectionQuestionCounters.put(categoryKey, questionInSection);
                String questionId = sectionNum + "." + questionInSection + ".";
                sql.add("INSERT INTO competence_questions (competenceId, questionnaireId, questionId, cq_id) VALUES (" +
                        competenceId + ", " + questionnaireId + ", '" + escape(questionId) + "', " + cqSeq++ + ");");

                newItemCount++;
            }
        }

        // Link questionnaire, categories and goals to all selected groups
        for (Long groupId : request.groupIds()) {
            sql.add("INSERT IGNORE INTO group_questionnaires (groupId, questionnaireId, promoted, price) VALUES (" +
                    groupId + ", " + questionnaireId + ", 0, 0.00);");
            for (Long catId : categoryIds.values()) {
                // group_categories has no UNIQUE constraint, so INSERT IGNORE won't prevent duplicates.
                // Use WHERE NOT EXISTS to avoid creating duplicate rows on re-publish.
                sql.add("INSERT INTO group_categories (groupId, categoryId) " +
                        "SELECT " + groupId + ", " + catId + " FROM DUAL " +
                        "WHERE NOT EXISTS (SELECT 1 FROM group_categories WHERE groupId = " + groupId + " AND categoryId = " + catId + ");");
            }
            for (Long gId : goalIds.values()) {
                sql.add("INSERT INTO group_goals (groupId, goalId) " +
                        "SELECT " + groupId + ", " + gId + " FROM DUAL " +
                        "WHERE NOT EXISTS (SELECT 1 FROM group_goals WHERE groupId = " + groupId + " AND goalId = " + gId + ");");
            }
        }

        IntegrationPreviewResponse.Summary summary = new IntegrationPreviewResponse.Summary(
                newCompetenceCount,
                newCategoryCount,
                newGoalCount,
                questionnaireId,
                newItemCount
        );
        return new IntegrationPreviewResponse(sql, warnings, summary);
    }

    // ─────────────────────────────────────────────────────────────
    // Utility methods
    // ─────────────────────────────────────────────────────────────

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int max, List<String> warnings) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        warnings.add("Assessment naam is langer dan " + max + " tekens en is afgekapt voor Metro.");
        return value.substring(0, max);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String nullOrQuoted(String value) {
        if (value == null || value.isBlank()) {
            return "NULL";
        }
        return "'" + escape(value) + "'";
    }
}

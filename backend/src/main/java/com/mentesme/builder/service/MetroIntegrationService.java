package com.mentesme.builder.service;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.CompetenceInput;
import com.mentesme.builder.model.IntegrationPreviewResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetroIntegrationService {

    private final AtomicLong competenceSequence;
    private final AtomicLong categorySequence;
    private final AtomicLong goalSequence;
    private final AtomicLong questionnaireSequence;
    private final AtomicLong itemSequence;
    private final MetroLookupRepository lookupRepository;
    private volatile String lookupError;

    public MetroIntegrationService(
            ObjectProvider<MetroLookupRepository> lookupRepositoryProvider,
            @Value("${builder.integration.competenceStartId:2000}") long competenceStartId,
            @Value("${builder.integration.categoryStartId:300}") long categoryStartId,
            @Value("${builder.integration.goalStartId:300}") long goalStartId,
            @Value("${builder.integration.questionnaireStartId:200}") long questionnaireStartId,
            @Value("${builder.integration.itemStartId:5000}") long itemStartId
    ) {
        this.lookupRepository = lookupRepositoryProvider.getIfAvailable();
        long competenceSeed = resolveStartId(competenceStartId, "competences");
        long categorySeed = resolveStartId(categoryStartId, "categories");
        long goalSeed = resolveStartId(goalStartId, "goals");
        long questionnaireSeed = resolveStartId(questionnaireStartId, "questionnaires");
        long itemSeed = resolveStartId(itemStartId, "items");

        this.competenceSequence = new AtomicLong(competenceSeed);
        this.categorySequence = new AtomicLong(categorySeed);
        this.goalSequence = new AtomicLong(goalSeed);
        this.questionnaireSequence = new AtomicLong(questionnaireSeed);
        this.itemSequence = new AtomicLong(itemSeed);
    }

    public IntegrationPreviewResponse generatePreview(AssessmentBuildRequest request) {
        List<String> sql = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Long> categoryIds = new HashMap<>();
        Map<String, Long> goalIds = new HashMap<>();

        String assessmentName = safeTrim(request.assessmentName());
        String truncatedName = truncate(assessmentName, 30, warnings);

        long questionnaireId = questionnaireSequence.incrementAndGet();
        sql.add("INSERT IGNORE INTO questionnaires (id, name) VALUES (" + questionnaireId + ", '" + escape(truncatedName) + "');");
        sql.add("INSERT IGNORE INTO questionnaire_translations (questionnaireId, language, name, questions, report) VALUES (" + questionnaireId + ", 'nl', '" + escape(truncatedName) + "', NULL, NULL);");
        sql.add("INSERT IGNORE INTO questionnaire_translations (questionnaireId, language, name, questions, report) VALUES (" + questionnaireId + ", 'en', '" + escape(truncatedName) + "', NULL, NULL);");

        long newCompetenceCount = 0;
        long newCategoryCount = 0;
        long newGoalCount = 0;
        long newItemCount = 0;
        int itemOrder = 0;

        for (CompetenceInput input : request.competences()) {
            String categoryName = safeTrim(input.category());
            String subcategoryName = safeTrim(input.subcategory());

            String categoryKey = categoryName.toLowerCase(Locale.ROOT);
            Long categoryId = categoryIds.get(categoryKey);
            if (categoryId == null) {
                categoryId = lookupExistingCategoryId(categoryName);
                if (categoryId == null) {
                    categoryId = categorySequence.incrementAndGet();
                    sql.add("INSERT IGNORE INTO categories (id, name) VALUES (" + categoryId + ", '" + escape(categoryName) + "');");
                    sql.add("INSERT IGNORE INTO category_translations (categoryId, language, name) VALUES (" + categoryId + ", 'nl', '" + escape(categoryName) + "');");
                    sql.add("INSERT IGNORE INTO category_translations (categoryId, language, name) VALUES (" + categoryId + ", 'en', '" + escape(categoryName) + "');");
                    newCategoryCount++;
                }
                categoryIds.put(categoryKey, categoryId);
            }

            Long goalId = null;
            if (!subcategoryName.isBlank()) {
                String goalKey = subcategoryName.toLowerCase(Locale.ROOT);
                goalId = goalIds.get(goalKey);
                if (goalId == null) {
                    goalId = lookupExistingGoalId(subcategoryName);
                    if (goalId == null) {
                        goalId = goalSequence.incrementAndGet();
                        sql.add("INSERT IGNORE INTO goals (id, name) VALUES (" + goalId + ", '" + escape(subcategoryName) + "');");
                        sql.add("INSERT IGNORE INTO goal_translations (goalId, language, name) VALUES (" + goalId + ", 'nl', '" + escape(subcategoryName) + "');");
                        sql.add("INSERT IGNORE INTO goal_translations (goalId, language, name) VALUES (" + goalId + ", 'en', '" + escape(subcategoryName) + "');");
                        newGoalCount++;
                    }
                    goalIds.put(goalKey, goalId);
                }
            }

            Long competenceId = input.existingId();
            if (competenceId == null) {
                competenceId = lookupExistingCompetenceId(input.name());
            }

            if (competenceId == null && input.isNew()) {
                competenceId = competenceSequence.incrementAndGet();
                String competenceName = safeTrim(input.name());
                String description = safeTrim(input.description());
                String nameEn = safeTrim(input.nameEn());
                String descriptionEn = safeTrim(input.descriptionEn());

                sql.add("INSERT IGNORE INTO competences (id, name, description, defaultMinPassScore, defaultMinMentorScore) VALUES (" +
                        competenceId + ", '" + escape(competenceName) + "', " + nullOrQuoted(description) + ", NULL, NULL);");

                sql.add("INSERT IGNORE INTO competence_translations (competenceId, language, name, description) VALUES (" + competenceId + ", 'nl', '" + escape(competenceName) + "', " + nullOrQuoted(description) + ");");

                String effectiveEnName = nameEn.isBlank() ? competenceName : nameEn;
                String effectiveEnDescription = descriptionEn.isBlank() ? description : descriptionEn;
                sql.add("INSERT IGNORE INTO competence_translations (competenceId, language, name, description) VALUES (" + competenceId + ", 'en', '" + escape(effectiveEnName) + "', " + nullOrQuoted(effectiveEnDescription) + ");");
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

            // Create item for this competence (Niet-vraag / Wel-vraag)
            String questionLeft = safeTrim(input.questionLeft());
            String questionRight = safeTrim(input.questionRight());
            String questionLeftEn = safeTrim(input.questionLeftEn());
            String questionRightEn = safeTrim(input.questionRightEn());

            if (!questionLeft.isBlank() || !questionRight.isBlank()) {
                long itemId = itemSequence.incrementAndGet();
                String itemName = safeTrim(input.name()) + "_item";

                // Create item
                sql.add("INSERT IGNORE INTO items (id, name, invertOrder) VALUES (" + itemId + ", '" + escape(itemName) + "', 0);");

                // Create item translations (leftText = Niet-vraag, rightText = Wel-vraag)
                String effectiveLeftNl = questionLeft.isBlank() ? questionRight : questionLeft;
                String effectiveRightNl = questionRight.isBlank() ? questionLeft : questionRight;
                String effectiveLeftEn = questionLeftEn.isBlank() ? effectiveLeftNl : questionLeftEn;
                String effectiveRightEn = questionRightEn.isBlank() ? effectiveRightNl : questionRightEn;

                sql.add("INSERT IGNORE INTO item_translations (itemId, language, leftText, rightText) VALUES (" +
                        itemId + ", 'nl', '" + escape(effectiveLeftNl) + "', '" + escape(effectiveRightNl) + "');");
                sql.add("INSERT IGNORE INTO item_translations (itemId, language, leftText, rightText) VALUES (" +
                        itemId + ", 'en', '" + escape(effectiveLeftEn) + "', '" + escape(effectiveRightEn) + "');");

                // Link item to questionnaire
                itemOrder++;
                sql.add("INSERT IGNORE INTO questionnaire_items (questionnaireId, itemId, `order`) VALUES (" +
                        questionnaireId + ", " + itemId + ", " + itemOrder + ");");

                // Link item to competence
                sql.add("INSERT IGNORE INTO competence_items (competenceId, itemId) VALUES (" + competenceId + ", " + itemId + ");");

                newItemCount++;
            }
        }

        IntegrationPreviewResponse.Summary summary = new IntegrationPreviewResponse.Summary(
                newCompetenceCount,
                newCategoryCount,
                newGoalCount,
                questionnaireId,
                newItemCount
        );
        maybeAddLookupWarning(warnings);
        return new IntegrationPreviewResponse(sql, warnings, summary);
    }

    private long resolveStartId(long fallback, String table) {
        if (lookupRepository == null) {
            return fallback;
        }
        try {
            long maxId = lookupRepository.getMaxId(table);
            return Math.max(fallback, maxId + 1);
        } catch (Exception ex) {
            recordLookupError(ex);
            return fallback;
        }
    }

    private Long lookupExistingCategoryId(String categoryName) {
        if (lookupRepository == null) {
            return null;
        }
        try {
            return lookupRepository.findCategoryIdByName(categoryName).orElse(null);
        } catch (Exception ex) {
            recordLookupError(ex);
            return null;
        }
    }

    private Long lookupExistingGoalId(String goalName) {
        if (lookupRepository == null) {
            return null;
        }
        try {
            return lookupRepository.findGoalIdByName(goalName).orElse(null);
        } catch (Exception ex) {
            recordLookupError(ex);
            return null;
        }
    }

    private Long lookupExistingCompetenceId(String competenceName) {
        if (lookupRepository == null) {
            return null;
        }
        try {
            return lookupRepository.findCompetenceIdByName(competenceName).orElse(null);
        } catch (Exception ex) {
            recordLookupError(ex);
            return null;
        }
    }

    private void recordLookupError(Exception ex) {
        if (lookupError == null) {
            lookupError = "Metro lookup unavailable: " + ex.getMessage();
        }
    }

    private void maybeAddLookupWarning(List<String> warnings) {
        if (lookupError != null) {
            warnings.add(lookupError);
        }
    }

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

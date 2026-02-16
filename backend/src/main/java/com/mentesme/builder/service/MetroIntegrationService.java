package com.mentesme.builder.service;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.CompetenceInput;
import com.mentesme.builder.model.IntegrationPreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MetroIntegrationService.class);

    private final AtomicLong competenceSequence;
    private final AtomicLong categorySequence;
    private final AtomicLong goalSequence;
    private final AtomicLong questionnaireSequence;
    private final AtomicLong itemSequence;
    private final ObjectProvider<MetroLookupRepository> lookupRepositoryProvider;
    private MetroLookupRepository lookupRepository;
    private volatile String lookupError;

    public MetroIntegrationService(
            ObjectProvider<MetroLookupRepository> lookupRepositoryProvider,
            @Value("${builder.integration.competenceStartId:2000}") long competenceStartId,
            @Value("${builder.integration.categoryStartId:300}") long categoryStartId,
            @Value("${builder.integration.goalStartId:300}") long goalStartId,
            @Value("${builder.integration.questionnaireStartId:200}") long questionnaireStartId,
            @Value("${builder.integration.itemStartId:5000}") long itemStartId
    ) {
        this.lookupRepositoryProvider = lookupRepositoryProvider;
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

    /**
     * Lazy-resolve lookupRepository als het bij constructie null was.
     */
    private MetroLookupRepository getLookupRepository() {
        if (lookupRepository == null) {
            lookupRepository = lookupRepositoryProvider.getIfAvailable();
        }
        return lookupRepository;
    }

    /**
     * Herlaad sequence-waarden van de DB zodat we altijd boven MAX(id) zitten.
     * Wordt aangeroepen aan het begin van elke generatePreview().
     */
    private void refreshSequences() {
        MetroLookupRepository repo = getLookupRepository();
        if (repo == null) {
            return;
        }
        try {
            long qMax  = repo.getMaxId("questionnaires");
            long catMax = repo.getMaxId("categories");
            long cMax  = repo.getMaxId("competences");
            long gMax  = repo.getMaxId("goals");
            long iMax  = repo.getMaxId("items");

            questionnaireSequence.set(Math.max(questionnaireSequence.get(), qMax + 1));
            categorySequence.set(Math.max(categorySequence.get(), catMax + 1));
            competenceSequence.set(Math.max(competenceSequence.get(), cMax + 1));
            goalSequence.set(Math.max(goalSequence.get(), gMax + 1));
            itemSequence.set(Math.max(itemSequence.get(), iMax + 1));

        } catch (Exception ex) {
            log.warn("refreshSequences failed: {}", ex.getMessage());
        }
    }

    public IntegrationPreviewResponse generatePreview(AssessmentBuildRequest request) {
        // Herlaad sequences van DB zodat IDs altijd boven huidige MAX zitten
        refreshSequences();

        List<String> sql = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Long> categoryIds = new HashMap<>();
        Map<String, Long> goalIds = new HashMap<>();

        String assessmentName = safeTrim(request.assessmentName());
        String truncatedName = truncate(assessmentName, 30, warnings);

        // Reuse existing questionnaire if name already exists, otherwise create new
        Long existingQuestionnaireId = lookupExistingQuestionnaireId(truncatedName);
        long questionnaireId;
        if (existingQuestionnaireId != null) {
            questionnaireId = existingQuestionnaireId;
            warnings.add("Questionnaire '" + truncatedName + "' bestaat al (ID: " + questionnaireId + "). Competenties en items worden toegevoegd.");
        } else {
            questionnaireId = questionnaireSequence.incrementAndGet();
            sql.add("INSERT INTO questionnaires(id, name) VALUES (" + questionnaireId + ", '" + escape(truncatedName) + "');");
            sql.add("INSERT INTO questionnaire_translations(questionnaireId, language, name, questions, report) VALUES (" + questionnaireId + ", 'nl', '" + escape(truncatedName) + "', NULL, NULL);");
            sql.add("INSERT INTO questionnaire_translations(questionnaireId, language, name, questions, report) VALUES (" + questionnaireId + ", 'en', '" + escape(truncatedName) + "', NULL, NULL);");
        }

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
                    sql.add("INSERT INTO categories(id, name) VALUES (" + categoryId + ", '" + escape(categoryName) + "');");
                    sql.add("INSERT INTO category_translations(categoryId, language, name) VALUES (" + categoryId + ", 'nl', '" + escape(categoryName) + "');");
                    sql.add("INSERT INTO category_translations(categoryId, language, name) VALUES (" + categoryId + ", 'en', '" + escape(categoryName) + "');");
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
                competenceId = lookupExistingCompetenceId(input.name());
            }

            if (competenceId == null && input.isNew()) {
                competenceId = competenceSequence.incrementAndGet();
                String competenceName = safeTrim(input.name());
                String description = safeTrim(input.description());
                String nameEn = safeTrim(input.nameEn());
                String descriptionEn = safeTrim(input.descriptionEn());

                sql.add("INSERT INTO competences(id, name, description, defaultMinPassScore, defaultMinMentorScore) VALUES (" +
                        competenceId + ", '" + escape(competenceName) + "', " + nullOrQuoted(description) + ", NULL, NULL);");

                sql.add("INSERT INTO competence_translations(competenceId, language, name, description) VALUES (" + competenceId + ", 'nl', '" + escape(competenceName) + "', " + nullOrQuoted(description) + ");");

                String effectiveEnName = nameEn.isBlank() ? competenceName : nameEn;
                String effectiveEnDescription = descriptionEn.isBlank() ? description : descriptionEn;
                sql.add("INSERT INTO competence_translations(competenceId, language, name, description) VALUES (" + competenceId + ", 'en', '" + escape(effectiveEnName) + "', " + nullOrQuoted(effectiveEnDescription) + ");");
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
                sql.add("INSERT INTO items(id, name, invertOrder) VALUES (" + itemId + ", '" + escape(itemName) + "', 0);");

                // Create item translations (leftText = Niet-vraag, rightText = Wel-vraag)
                String effectiveLeftNl = questionLeft.isBlank() ? questionRight : questionLeft;
                String effectiveRightNl = questionRight.isBlank() ? questionLeft : questionRight;
                String effectiveLeftEn = questionLeftEn.isBlank() ? effectiveLeftNl : questionLeftEn;
                String effectiveRightEn = questionRightEn.isBlank() ? effectiveRightNl : questionRightEn;

                sql.add("INSERT INTO item_translations(itemId, language, leftText, rightText) VALUES (" +
                        itemId + ", 'nl', '" + escape(effectiveLeftNl) + "', '" + escape(effectiveRightNl) + "');");
                sql.add("INSERT INTO item_translations(itemId, language, leftText, rightText) VALUES (" +
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
        MetroLookupRepository repo = getLookupRepository();
        if (repo == null) {
            return fallback;
        }
        try {
            long maxId = repo.getMaxId(table);
            return Math.max(fallback, maxId + 1);
        } catch (Exception ex) {
            recordLookupError(ex);
            return fallback;
        }
    }

    private Long lookupExistingQuestionnaireId(String questionnaireName) {
        MetroLookupRepository repo = getLookupRepository();
        if (repo == null) {
            return null;
        }
        try {
            return repo.findQuestionnaireIdByName(questionnaireName).orElse(null);
        } catch (Exception ex) {
            recordLookupError(ex);
            return null;
        }
    }

    private Long lookupExistingCategoryId(String categoryName) {
        MetroLookupRepository repo = getLookupRepository();
        if (repo == null) {
            return null;
        }
        try {
            return repo.findCategoryIdByName(categoryName).orElse(null);
        } catch (Exception ex) {
            recordLookupError(ex);
            return null;
        }
    }

    private Long lookupExistingGoalId(String goalName) {
        MetroLookupRepository repo = getLookupRepository();
        if (repo == null) {
            return null;
        }
        try {
            return repo.findGoalIdByName(goalName).orElse(null);
        } catch (Exception ex) {
            recordLookupError(ex);
            return null;
        }
    }

    private Long lookupExistingCompetenceId(String competenceName) {
        MetroLookupRepository repo = getLookupRepository();
        if (repo == null) {
            return null;
        }
        try {
            return repo.findCompetenceIdByName(competenceName).orElse(null);
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

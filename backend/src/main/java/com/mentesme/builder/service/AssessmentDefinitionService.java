package com.mentesme.builder.service;

import com.mentesme.builder.model.definition.AssessmentDefinitionResponse;
import com.mentesme.builder.model.definition.AssessmentDefinitionResponse.*;
import com.mentesme.builder.service.AssessmentDefinitionRepository.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "builder.metro.enabled", havingValue = "true")
public class AssessmentDefinitionService {

    private final AssessmentDefinitionRepository repository;

    public AssessmentDefinitionService(AssessmentDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Combineert meerdere bestaande assessments tot één definitie.
     * Elke bron-assessment wordt 1 categorie/sectie (assessment naam = categorie naam).
     * Bestaande competenties en items worden hergebruikt, geen nieuwe aangemaakt.
     */
    public AssessmentDefinitionResponse composeDefinitions(List<Long> questionnaireIds) {
        List<CategoryDef> composedCategories = new ArrayList<>();
        int sortOrder = 0;

        for (long qId : questionnaireIds) {
            var defOpt = exportDefinition(qId);
            if (defOpt.isEmpty()) continue;

            var def = defOpt.get();

            // Assessment naam wordt de categorie naam
            Map<String, CategoryTexts> categoryTexts = new LinkedHashMap<>();
            for (var entry : def.texts().entrySet()) {
                categoryTexts.put(entry.getKey(), new CategoryTexts(entry.getValue().name()));
            }

            // Alle competenties uit alle categorieën samenvoegen onder deze ene categorie
            List<CompetenceDef> allCompetences = new ArrayList<>();
            for (var cat : def.categories()) {
                allCompetences.addAll(cat.competences());
            }

            composedCategories.add(new CategoryDef(0, sortOrder++, categoryTexts, allCompetences));
        }

        return new AssessmentDefinitionResponse(
                0, "1.0",
                new Metadata("compose", Instant.now()),
                Map.of(),
                new Scale(6, "bipolar"),
                composedCategories
        );
    }

    public Optional<AssessmentDefinitionResponse> exportDefinition(long questionnaireId) {
        var questionnaireOpt = repository.findQuestionnaireById(questionnaireId);
        if (questionnaireOpt.isEmpty()) {
            return Optional.empty();
        }

        // Questionnaire texts (NL/EN)
        var qTranslations = repository.findQuestionnaireTranslations(questionnaireId);
        Map<String, QuestionnaireTexts> questionnaireTexts = new LinkedHashMap<>();
        for (var qt : qTranslations) {
            questionnaireTexts.put(qt.language(), new QuestionnaireTexts(
                    qt.name(),
                    qt.report(),       // DB "report" column → contract "description"
                    qt.questions()     // DB "questions" column → contract "instruction"
            ));
        }

        // Item-competence-category links (the core join)
        var itemDetailRows = repository.findQuestionnaireItemsWithDetails(questionnaireId);
        if (itemDetailRows.isEmpty()) {
            return Optional.of(buildResponse(questionnaireId, questionnaireTexts, List.of()));
        }

        // Collect distinct IDs and first-seen mappings
        Set<Long> competenceIds = new LinkedHashSet<>();
        Set<Long> categoryIds = new LinkedHashSet<>();
        Map<Long, Long> itemToCompetence = new LinkedHashMap<>();
        Map<Long, Long> competenceToCategory = new LinkedHashMap<>();
        Map<Long, Integer> itemToOrder = new LinkedHashMap<>();
        Map<Long, Integer> itemToInvertOrder = new LinkedHashMap<>();

        for (var row : itemDetailRows) {
            competenceIds.add(row.competenceId());
            categoryIds.add(row.categoryId());
            itemToCompetence.putIfAbsent(row.itemId(), row.competenceId());
            competenceToCategory.putIfAbsent(row.competenceId(), row.categoryId());
            itemToOrder.putIfAbsent(row.itemId(), row.itemOrder());
            itemToInvertOrder.putIfAbsent(row.itemId(), row.invertOrder());
        }

        // Bulk-fetch translations
        Map<Long, Map<String, ItemTexts>> itemTextsMap = buildItemTextsMap(
                repository.findItemTranslationsForQuestionnaire(questionnaireId));
        Map<Long, Map<String, CompetenceTexts>> compTextsMap = buildCompetenceTextsMap(
                repository.findCompetenceTranslationsForIds(new ArrayList<>(competenceIds)));
        Map<Long, Map<String, CategoryTexts>> catTextsMap = buildCategoryTextsMap(
                repository.findCategoryTranslationsForIds(new ArrayList<>(categoryIds)));

        // Build items grouped by competence
        Map<Long, List<ItemDef>> itemsByCompetence = new LinkedHashMap<>();
        Set<Long> seenItems = new LinkedHashSet<>();
        for (var row : itemDetailRows) {
            if (!seenItems.add(row.itemId())) continue; // deduplicate
            long compId = itemToCompetence.get(row.itemId());
            ItemDef item = new ItemDef(
                    row.itemId(),
                    row.invertOrder() == 0 ? "positive" : "negative",
                    row.itemOrder(),
                    itemTextsMap.getOrDefault(row.itemId(), Map.of())
            );
            itemsByCompetence.computeIfAbsent(compId, k -> new ArrayList<>()).add(item);
        }
        itemsByCompetence.values().forEach(list -> list.sort(Comparator.comparingInt(ItemDef::sortOrder)));

        // Build competences grouped by category
        Map<Long, List<CompetenceDef>> competencesByCategory = new LinkedHashMap<>();
        for (long compId : competenceIds) {
            long catId = competenceToCategory.get(compId);
            List<ItemDef> items = itemsByCompetence.getOrDefault(compId, List.of());
            int compSortOrder = items.stream().mapToInt(ItemDef::sortOrder).min().orElse(Integer.MAX_VALUE);
            CompetenceDef comp = new CompetenceDef(
                    compId, compSortOrder,
                    compTextsMap.getOrDefault(compId, Map.of()),
                    items
            );
            competencesByCategory.computeIfAbsent(catId, k -> new ArrayList<>()).add(comp);
        }
        competencesByCategory.values().forEach(list ->
                list.sort(Comparator.comparingInt(CompetenceDef::sortOrder)));

        // Build categories
        List<CategoryDef> categories = new ArrayList<>();
        for (long catId : categoryIds) {
            List<CompetenceDef> comps = competencesByCategory.getOrDefault(catId, List.of());
            int catSortOrder = comps.stream().mapToInt(CompetenceDef::sortOrder).min().orElse(Integer.MAX_VALUE);
            categories.add(new CategoryDef(
                    catId, catSortOrder,
                    catTextsMap.getOrDefault(catId, Map.of()),
                    comps
            ));
        }
        categories.sort(Comparator.comparingInt(CategoryDef::sortOrder));

        return Optional.of(buildResponse(questionnaireId, questionnaireTexts, categories));
    }

    private AssessmentDefinitionResponse buildResponse(
            long questionnaireId,
            Map<String, QuestionnaireTexts> texts,
            List<CategoryDef> categories) {
        return new AssessmentDefinitionResponse(
                questionnaireId,
                "1.0",
                new Metadata("metro-sql", Instant.now()),
                texts,
                new Scale(6, "bipolar"),
                categories
        );
    }

    private Map<Long, Map<String, ItemTexts>> buildItemTextsMap(List<ItemTranslationRow> rows) {
        Map<Long, Map<String, ItemTexts>> map = new HashMap<>();
        for (var row : rows) {
            map.computeIfAbsent(row.itemId(), k -> new LinkedHashMap<>())
                    .put(row.language(), new ItemTexts(row.leftText(), row.rightText()));
        }
        return map;
    }

    private Map<Long, Map<String, CompetenceTexts>> buildCompetenceTextsMap(List<CompetenceTranslationRow> rows) {
        Map<Long, Map<String, CompetenceTexts>> map = new HashMap<>();
        for (var row : rows) {
            map.computeIfAbsent(row.competenceId(), k -> new LinkedHashMap<>())
                    .put(row.language(), new CompetenceTexts(row.name(), row.description()));
        }
        return map;
    }

    private Map<Long, Map<String, CategoryTexts>> buildCategoryTextsMap(List<CategoryTranslationRow> rows) {
        Map<Long, Map<String, CategoryTexts>> map = new HashMap<>();
        for (var row : rows) {
            map.computeIfAbsent(row.categoryId(), k -> new LinkedHashMap<>())
                    .put(row.language(), new CategoryTexts(row.name()));
        }
        return map;
    }
}

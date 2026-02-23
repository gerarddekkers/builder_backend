package com.mentesme.builder.model.definition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AssessmentDefinitionResponse(
        long id,
        String version,
        Metadata metadata,
        Map<String, QuestionnaireTexts> texts,
        Scale scale,
        List<CategoryDef> categories
) {

    public record Metadata(
            String createdFrom,
            Instant exportedAt
    ) {}

    public record QuestionnaireTexts(
            String name,
            String description,
            String instruction
    ) {}

    public record Scale(
            int points,
            String type
    ) {}

    public record CategoryDef(
            long id,
            int sortOrder,
            Map<String, CategoryTexts> texts,
            List<CompetenceDef> competences
    ) {}

    public record CategoryTexts(
            String name
    ) {}

    public record CompetenceDef(
            long id,
            int sortOrder,
            Map<String, CompetenceTexts> texts,
            List<ItemDef> items
    ) {}

    public record CompetenceTexts(
            String name,
            String description
    ) {}

    public record ItemDef(
            long id,
            String polarity,
            int sortOrder,
            Map<String, ItemTexts> texts
    ) {}

    public record ItemTexts(
            String leftText,
            String rightText
    ) {}
}

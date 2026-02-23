package com.mentesme.builder.model.definition;

public record QuestionnaireListItem(
    long id,
    String name,
    String nameNl,
    String nameEn,
    int itemCount,
    int competenceCount
) {}

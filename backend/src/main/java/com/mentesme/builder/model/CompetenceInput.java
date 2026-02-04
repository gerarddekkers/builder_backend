package com.mentesme.builder.model;

import jakarta.validation.constraints.NotBlank;

public record CompetenceInput(
        // Category
        @NotBlank String category,
        String categoryEn,
        String categoryDescription,
        String categoryDescriptionEn,

        // Subcategory
        String subcategory,
        String subcategoryEn,
        String subcategoryDescription,
        String subcategoryDescriptionEn,

        // Competence
        @NotBlank String name,
        String nameEn,
        String description,
        String descriptionEn,

        // Question text
        String questionLeft,
        String questionLeftEn,
        String questionRight,
        String questionRightEn,

        // Meta
        boolean isNew,
        Long existingId
) {
}

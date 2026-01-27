package com.mentesme.builder.model;

import jakarta.validation.constraints.NotBlank;

public record CompetenceInput(
        Long existingId,
        @NotBlank String category,
        String subcategory,
        String categoryDescription,
        String categoryDescriptionEn,
        String subcategoryDescription,
        String subcategoryDescriptionEn,
        @NotBlank String name,
        boolean isNew,
        String description,
        String nameEn,
        String descriptionEn,
        String questionLeft,
        String questionRight,
        String questionLeftEn,
        String questionRightEn
) {
}

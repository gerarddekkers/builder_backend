package com.mentesme.builder.model;

import jakarta.validation.constraints.NotBlank;

public record QuestionInput(
        @NotBlank String text,
        String textEn,
        String questionType
) {
}

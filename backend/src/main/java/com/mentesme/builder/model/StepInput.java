package com.mentesme.builder.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StepInput(
        @NotNull StepType type,
        @NotBlank String title,
        String titleEn,
        String textContent,
        String textContentEn,
        boolean chatboxEnabled,
        boolean uploadEnabled,
        String videoUrl,
        @Valid List<QuestionInput> questions,
        @Valid List<DocumentInput> documents
) {
    public enum StepType {
        hoofdstap,
        substap,
        afsluiting
    }
}

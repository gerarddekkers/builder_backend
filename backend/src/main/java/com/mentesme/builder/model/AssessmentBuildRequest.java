package com.mentesme.builder.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AssessmentBuildRequest(
        // Assessment name
        @NotBlank String assessmentName,
        String assessmentNameEn,

        // Assessment description
        String assessmentDescription,
        String assessmentDescriptionEn,

        // Assessment instruction
        String assessmentInstruction,
        String assessmentInstructionEn,

        // Competences
        @NotEmpty @Valid List<CompetenceInput> competences
) {
}

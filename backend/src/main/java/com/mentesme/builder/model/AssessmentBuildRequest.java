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

        // Groups (must exist in target database)
        @NotEmpty List<Long> groupIds,

        // Competences
        @NotEmpty @Valid List<CompetenceInput> competences,

        // If set, overwrite this questionnaire instead of looking up by name
        Long editQuestionnaireId
) {
}

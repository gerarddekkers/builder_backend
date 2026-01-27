package com.mentesme.builder.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

import java.util.List;

public record AssessmentBuildRequest(
        @NotBlank String assessmentName,
        String assessmentDescription,
        String assessmentInstruction,
        String assessmentNameEn,
        String assessmentDescriptionEn,
        String assessmentInstructionEn,
        @NotEmpty @Valid List<CompetenceInput> competences,
        List<Long> groupIds
) {
}

package com.mentesme.builder.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record LearningJourneyPublishRequest(
        @NotBlank String name,
        String nameEn,
        String description,
        String descriptionEn,
        @NotEmpty List<Long> groupIds,
        boolean aiCoachEnabled,
        @NotEmpty @Valid List<StepInput> steps,
        Long editLearningJourneyId
) {
}

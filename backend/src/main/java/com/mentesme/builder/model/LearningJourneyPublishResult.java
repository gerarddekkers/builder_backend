package com.mentesme.builder.model;

import java.util.Map;

public record LearningJourneyPublishResult(
        long learningJourneyId,
        boolean success,
        String environment,
        Map<String, Long> timings
) {
}

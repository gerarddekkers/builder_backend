package com.mentesme.builder.model;

import java.util.List;

public record CompetenceLookupResponse(
        List<CompetenceMatch> existing,
        List<CompetenceInput> newItems
) {
    public static CompetenceLookupResponse fromRequest(
            AssessmentBuildRequest request,
            List<CompetenceSummary> existingItems
    ) {
        List<CompetenceMatch> matches = request.competences().stream()
                .filter(input -> existingItems.stream().anyMatch(existing ->
                        existing.nameNl().equalsIgnoreCase(input.name())
                                || existing.nameEn().equalsIgnoreCase(input.name())
                ))
                .map(input -> new CompetenceMatch(input.name(), true))
                .toList();

        List<CompetenceInput> newItems = request.competences().stream()
                .filter(input -> matches.stream().noneMatch(match -> match.name().equalsIgnoreCase(input.name())))
                .toList();

        return new CompetenceLookupResponse(matches, newItems);
    }

    public record CompetenceMatch(String name, boolean exists) {
    }
}

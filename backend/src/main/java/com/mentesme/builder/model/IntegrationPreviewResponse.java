package com.mentesme.builder.model;

import java.util.List;

public record IntegrationPreviewResponse(
        List<String> sqlStatements,
        List<String> warnings,
        Summary summary
) {
    public record Summary(
            long newCompetences,
            long newCategories,
            long newGoals,
            long questionnaireId,
            long newItems
    ) {
    }
}

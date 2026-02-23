package com.mentesme.builder.model;

import java.util.List;

public record LearningJourneyDetail(
        long id,
        String name,
        String nameEn,
        String ljKey,
        String description,
        String descriptionEn,
        List<StepDetail> steps,
        List<DocumentDetail> documents,
        List<Long> groupIds,
        boolean aiCoachEnabled
) {

    public record StepDetail(
            long id,
            int position,
            String structuralType,
            String titleNl,
            String titleEn,
            String textContentNl,
            String textContentEn,
            String dbType,
            String colour,
            String size,
            boolean chatboxEnabled,
            String documentsIdentifier,
            List<QuestionDetail> questions
    ) {}

    public record QuestionDetail(long id, int order, String textNl, String textEn, String questionType) {}

    public record DocumentDetail(long id, String identifier, String label, String url, String lang) {}
}

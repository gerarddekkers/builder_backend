package com.mentesme.builder.model;

import java.util.Map;

public record PublishResult(long questionnaireId, boolean published, Map<String, Long> timings) {
    public PublishResult(long questionnaireId, boolean published) {
        this(questionnaireId, published, null);
    }
}

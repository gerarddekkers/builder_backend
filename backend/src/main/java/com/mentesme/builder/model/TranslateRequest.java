package com.mentesme.builder.model;

import java.util.List;

public record TranslateRequest(
        String sourceLanguage,
        String targetLanguage,
        List<String> texts
) {
}

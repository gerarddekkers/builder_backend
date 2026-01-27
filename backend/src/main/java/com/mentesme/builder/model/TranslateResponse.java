package com.mentesme.builder.model;

import java.util.List;

public record TranslateResponse(
        List<String> translations,
        String warning
) {
}

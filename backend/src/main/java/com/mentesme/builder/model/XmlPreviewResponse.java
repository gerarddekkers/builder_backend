package com.mentesme.builder.model;

import java.util.List;

public record XmlPreviewResponse(
        String questionnaireXmlNl,
        String questionnaireXmlEn,
        String reportXmlNl,
        String reportXmlEn,
        List<String> warnings
) {
}

package com.mentesme.builder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleTranslationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey;

    public GoogleTranslationService(
            @Value("${builder.google.apiKey:}") String apiKey
    ) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public TranslationResult translate(String sourceLanguage, String targetLanguage, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new TranslationResult(List.of(), null);
        }
        if (!isConfigured()) {
            return new TranslationResult(texts, "Google Translate is niet geconfigureerd. Zet GOOGLE_API_KEY in Elastic Beanstalk.");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("q", texts);
            payload.put("source", sourceLanguage);
            payload.put("target", targetLanguage);
            payload.put("format", "html");

            String body = objectMapper.writeValueAsString(payload);
            String url = "https://translation.googleapis.com/language/translate/v2?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new TranslationResult(texts, "Google Translate faalde: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode translationsNode = root.at("/data/translations");

            if (translationsNode.isMissingNode() || !translationsNode.isArray()) {
                return new TranslationResult(texts, "Google Translate faalde: onverwacht antwoord");
            }

            List<String> translations = new ArrayList<>();
            for (JsonNode node : translationsNode) {
                translations.add(node.get("translatedText").asText());
            }

            if (translations.size() != texts.size()) {
                return new TranslationResult(texts, "Google Translate gaf een onverwachte lengte terug");
            }

            return new TranslationResult(translations, null);
        } catch (Exception ex) {
            return new TranslationResult(texts, "Google Translate faalde: " + ex.getMessage());
        }
    }

    public record TranslationResult(List<String> translations, String warning) {
    }
}

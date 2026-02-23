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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiTranslationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public AiTranslationService(
            @Value("${builder.ai.apiUrl:https://api.openai.com/v1/chat/completions}") String apiUrl,
            @Value("${builder.ai.apiKey:}") String apiKey,
            @Value("${builder.ai.model:gpt-4o-mini}") String model
    ) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public TranslationResult translate(String sourceLanguage, String targetLanguage, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new TranslationResult(List.of(), null);
        }
        if (apiKey == null || apiKey.isBlank()) {
            return new TranslationResult(texts, "AI vertaling is niet geconfigureerd. Zet builder.ai.apiKey om vertaling te activeren.");
        }

        String systemPrompt = "You are a professional translator. Translate from " + sourceLanguage + " to " + targetLanguage +
                ". IMPORTANT: Preserve all HTML tags and structure exactly. Only translate the text content between tags. " +
                "Return ONLY a JSON array of strings in the same order. No extra text.";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", texts)
        ));

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new TranslationResult(texts, "AI vertaling faalde: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.at("/choices/0/message/content");
            if (contentNode.isMissingNode()) {
                return new TranslationResult(texts, "AI vertaling faalde: leeg antwoord");
            }

            List<String> translations = objectMapper.readValue(contentNode.asText(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (translations.size() != texts.size()) {
                return new TranslationResult(texts, "AI vertaling gaf een onverwachte lengte terug");
            }
            return new TranslationResult(translations, null);
        } catch (Exception ex) {
            return new TranslationResult(texts, "AI vertaling faalde: " + ex.getMessage());
        }
    }

    public record TranslationResult(List<String> translations, String warning) {
    }
}

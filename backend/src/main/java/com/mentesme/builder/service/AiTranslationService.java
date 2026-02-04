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
public class AiTranslationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String provider;
    private final String googleApiKey;
    private final String openaiApiUrl;
    private final String openaiApiKey;
    private final String openaiModel;

    public AiTranslationService(
            @Value("${builder.translation.provider:google}") String provider,
            @Value("${builder.translation.google.apiKey:}") String googleApiKey,
            @Value("${builder.translation.openai.apiUrl:https://api.openai.com/v1/chat/completions}") String openaiApiUrl,
            @Value("${builder.translation.openai.apiKey:}") String openaiApiKey,
            @Value("${builder.translation.openai.model:gpt-4o-mini}") String openaiModel
    ) {
        this.provider = provider;
        this.googleApiKey = googleApiKey;
        this.openaiApiUrl = openaiApiUrl;
        this.openaiApiKey = openaiApiKey;
        this.openaiModel = openaiModel;
    }

    public TranslationResult translate(String sourceLanguage, String targetLanguage, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new TranslationResult(List.of(), null);
        }

        if ("google".equalsIgnoreCase(provider)) {
            return translateWithGoogle(sourceLanguage, targetLanguage, texts);
        } else {
            return translateWithOpenAI(sourceLanguage, targetLanguage, texts);
        }
    }

    private TranslationResult translateWithGoogle(String sourceLanguage, String targetLanguage, List<String> texts) {
        if (googleApiKey == null || googleApiKey.isBlank()) {
            return new TranslationResult(texts, "Google Translate is niet geconfigureerd. Zet GOOGLE_TRANSLATE_API_KEY environment variable.");
        }

        try {
            List<String> translations = new ArrayList<>();

            for (String text : texts) {
                if (text == null || text.isBlank()) {
                    translations.add(text);
                    continue;
                }

                String url = "https://translation.googleapis.com/language/translate/v2?key=" + googleApiKey;

                Map<String, Object> payload = new HashMap<>();
                payload.put("q", text);
                payload.put("source", sourceLanguage);
                payload.put("target", targetLanguage);
                payload.put("format", "text");

                String body = objectMapper.writeValueAsString(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return new TranslationResult(texts, "Google Translate faalde: " + response.statusCode() + " - " + response.body());
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode translatedText = root.at("/data/translations/0/translatedText");

                if (translatedText.isMissingNode()) {
                    return new TranslationResult(texts, "Google Translate gaf onverwacht antwoord");
                }

                translations.add(translatedText.asText());
            }

            return new TranslationResult(translations, null);

        } catch (Exception ex) {
            return new TranslationResult(texts, "Google Translate faalde: " + ex.getMessage());
        }
    }

    private TranslationResult translateWithOpenAI(String sourceLanguage, String targetLanguage, List<String> texts) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            return new TranslationResult(texts, "OpenAI vertaling is niet geconfigureerd. Zet OPENAI_API_KEY environment variable.");
        }

        String systemPrompt = "You are a professional translator. Translate from " + sourceLanguage + " to " + targetLanguage +
                ". Return ONLY a JSON array of strings in the same order. No extra text.";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", openaiModel);
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", texts)
        ));

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(openaiApiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new TranslationResult(texts, "OpenAI vertaling faalde: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.at("/choices/0/message/content");
            if (contentNode.isMissingNode()) {
                return new TranslationResult(texts, "OpenAI vertaling faalde: leeg antwoord");
            }

            List<String> translations = objectMapper.readValue(contentNode.asText(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (translations.size() != texts.size()) {
                return new TranslationResult(texts, "OpenAI vertaling gaf een onverwachte lengte terug");
            }
            return new TranslationResult(translations, null);
        } catch (Exception ex) {
            return new TranslationResult(texts, "OpenAI vertaling faalde: " + ex.getMessage());
        }
    }

    public record TranslationResult(List<String> translations, String warning) {
    }
}

package com.mentesme.builder.api;

import com.mentesme.builder.model.*;
import com.mentesme.builder.service.AiTranslationService;
import com.mentesme.builder.service.MetroIntegrationService;
import com.mentesme.builder.service.MetroLookupRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me"})
public class BuilderController {

    private final MetroLookupRepository metroLookup;
    private final MetroIntegrationService integrationService;
    private final AiTranslationService translationService;

    public BuilderController(
            MetroLookupRepository metroLookup,
            MetroIntegrationService integrationService,
            AiTranslationService translationService
    ) {
        this.metroLookup = metroLookup;
        this.integrationService = integrationService;
        this.translationService = translationService;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    // ─────────────────────────────────────────────────────────────
    // Category search (database)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/categories/search")
    public List<CategorySearchResult> searchCategories(@RequestParam(value = "q", defaultValue = "") String query) {
        if (query.isBlank()) {
            return List.of();
        }
        return metroLookup.searchCategories(query);
    }

    // ─────────────────────────────────────────────────────────────
    // Competence search (database)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/competences/search")
    public List<CompetenceSearchResult> searchCompetences(@RequestParam(value = "q", defaultValue = "") String query) {
        if (query.isBlank()) {
            return List.of();
        }
        return metroLookup.searchCompetences(query);
    }

    // ─────────────────────────────────────────────────────────────
    // AI Translation (NL → EN)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/translate")
    public TranslateResponse translate(@RequestBody TranslateRequest request) {
        String source = request.sourceLanguage() != null ? request.sourceLanguage() : "nl";
        String target = request.targetLanguage() != null ? request.targetLanguage() : "en";

        AiTranslationService.TranslationResult result = translationService.translate(source, target, request.texts());
        return new TranslateResponse(result.translations(), result.warning());
    }

    // ─────────────────────────────────────────────────────────────
    // Assessment build
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/assessments/build")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentBuildResponse buildAssessment(@Valid @RequestBody AssessmentBuildRequest request) {
        // Generate SQL statements for the assessment
        IntegrationPreviewResponse preview = integrationService.generatePreview(request);

        // Execute the SQL statements to persist to Metro database
        metroLookup.executeSqlStatements(preview.sqlStatements());

        // Build response message with summary
        String message = String.format(
            "Assessment opgeslagen in Metro database (ID: %d). Nieuwe competenties: %d, nieuwe categorieën: %d.",
            preview.summary().questionnaireId(),
            preview.summary().newCompetences(),
            preview.summary().newCategories()
        );

        if (!preview.warnings().isEmpty()) {
            message += " Waarschuwingen: " + String.join("; ", preview.warnings());
        }

        return new AssessmentBuildResponse(preview.summary().questionnaireId(), message);
    }
}

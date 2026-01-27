package com.mentesme.builder.api;

import com.mentesme.builder.model.*;
import com.mentesme.builder.service.AiTranslationService;
import com.mentesme.builder.service.MetroIntegrationService;
import com.mentesme.builder.service.MetroLookupRepository;
import com.mentesme.builder.service.XmlGenerationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {"http://localhost:5173", "http://localhost:5174"},
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS},
        allowedHeaders = "*"
)
public class BuilderController {

    private final AtomicLong idSequence = new AtomicLong(1000);
    private final MetroIntegrationService integrationService;
    private final MetroLookupRepository lookupRepository;
    private final XmlGenerationService xmlGenerationService;
    private final AiTranslationService translationService;

    private final List<CompetenceSummary> existingCompetences = List.of(
            new CompetenceSummary("Leiderschap", "Leadership", "Management", "People"),
            new CompetenceSummary("Samenwerken", "Collaboration", "Team", "Soft skills"),
            new CompetenceSummary("Analyse", "Analysis", "Data", "Technical")
    );

    public BuilderController(
            MetroIntegrationService integrationService,
            ObjectProvider<MetroLookupRepository> lookupRepositoryProvider,
            XmlGenerationService xmlGenerationService,
            AiTranslationService translationService
    ) {
        this.integrationService = integrationService;
        this.lookupRepository = lookupRepositoryProvider.getIfAvailable();
        this.xmlGenerationService = xmlGenerationService;
        this.translationService = translationService;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    // ---------- COMPETENCES ----------
    @GetMapping("/competences")
    public List<?> findCompetences(@RequestParam(value = "query", required = false) String query) {
        if (query == null || query.isBlank()) {
            return existingCompetences;
        }

        if (lookupRepository != null) {
            try {
                return lookupRepository.searchCompetences(query);
            } catch (Exception ignored) {}
        }

        String lowered = query.toLowerCase(Locale.ROOT);
        return existingCompetences.stream()
                .filter(c ->
                        c.nameNl().toLowerCase().contains(lowered) ||
                        c.nameEn().toLowerCase().contains(lowered))
                .collect(Collectors.toList());
    }

    // ---------- CATEGORIES (FIXED: NO SQL REQUIRED) ----------
    @GetMapping("/categories")
    public List<CategorySearchResult> findCategories(
            @RequestParam(value = "query", required = false) String query
    ) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // TEMP fallback zodat lookup ALTIJD werkt
        return List.of(
                new CategorySearchResult(1L, "Persoonlijk", "Personal"),
                new CategorySearchResult(2L, "Leiderschap", "Leadership"),
                new CategorySearchResult(3L, "Samenwerken", "Collaboration"),
                new CategorySearchResult(4L, "Analyse", "Analysis")
        ).stream()
         .filter(c ->
                 c.nameNl().toLowerCase().contains(query.toLowerCase()) ||
                 c.nameEn().toLowerCase().contains(query.toLowerCase()))
         .toList();
    }

    // ---------- BUILD ----------
    @PostMapping("/assessments/build")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentBuildResponse buildAssessment(@Valid @RequestBody AssessmentBuildRequest request) {
        long id = idSequence.incrementAndGet();
        return new AssessmentBuildResponse(id, "Assessment opgeslagen.");
    }

    // ---------- XML ----------
    @PostMapping("/xml/preview")
    public XmlPreviewResponse previewXml(@Valid @RequestBody AssessmentBuildRequest request) {
        List<String> warnings = new java.util.ArrayList<>();
        return new XmlPreviewResponse(
                xmlGenerationService.generateQuestionnaireXml(request, "nl", warnings),
                xmlGenerationService.generateQuestionnaireXml(request, "en", warnings),
                xmlGenerationService.generateReportXml(request, "nl", warnings),
                xmlGenerationService.generateReportXml(request, "en", warnings),
                warnings
        );
    }

    // ---------- TRANSLATE ----------
    @PostMapping("/translate")
    public TranslateResponse translate(@RequestBody TranslateRequest request) {
        var result = translationService.translate(
                request.sourceLanguage() == null ? "nl" : request.sourceLanguage(),
                request.targetLanguage() == null ? "en" : request.targetLanguage(),
                request.texts()
        );
        return new TranslateResponse(result.translations(), result.warning());
    }
}


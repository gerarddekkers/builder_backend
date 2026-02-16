package com.mentesme.builder.api;

import com.mentesme.builder.model.*;
import com.mentesme.builder.service.GoogleTranslationService;
import com.mentesme.builder.service.MetroLookupRepository;
import com.mentesme.builder.service.QuestionnairePublishService;
import com.mentesme.builder.service.XmlGenerationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me"})
public class BuilderController {

    private static final Logger log = LoggerFactory.getLogger(BuilderController.class);

    private final MetroLookupRepository metroLookup;
    private final QuestionnairePublishService publishService;
    private final GoogleTranslationService translationService;
    private final XmlGenerationService xmlGenerationService;

    public BuilderController(
            MetroLookupRepository metroLookup,
            QuestionnairePublishService publishService,
            GoogleTranslationService translationService,
            XmlGenerationService xmlGenerationService
    ) {
        this.metroLookup = metroLookup;
        this.publishService = publishService;
        this.translationService = translationService;
        this.xmlGenerationService = xmlGenerationService;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    // ─────────────────────────────────────────────────────────────
    // Assessment name check (database)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/questionnaires/check")
    public QuestionnaireCheckResponse checkQuestionnaireName(@RequestParam(value = "name", defaultValue = "") String name) {
        if (name.isBlank()) {
            return new QuestionnaireCheckResponse(false, null);
        }
        return metroLookup.findQuestionnaireIdByName(name)
                .map(id -> new QuestionnaireCheckResponse(true, id))
                .orElse(new QuestionnaireCheckResponse(false, null));
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
    // Google Translation (NL → EN)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/translate")
    public TranslateResponse translate(@RequestBody TranslateRequest request) {
        String source = request.sourceLanguage() != null ? request.sourceLanguage() : "nl";
        String target = request.targetLanguage() != null ? request.targetLanguage() : "en";

        GoogleTranslationService.TranslationResult result = translationService.translate(source, target, request.texts());
        return new TranslateResponse(result.translations(), result.warning());
    }

    // ─────────────────────────────────────────────────────────────
    // XML Preview (questionnaire + report)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/assessments/xml-preview")
    public XmlPreviewResponse xmlPreview(@Valid @RequestBody AssessmentBuildRequest request) {
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();

        String questionnaireNl = xmlGenerationService.generateQuestionnaireXml(request, "nl", warnings);
        String questionnaireEn = xmlGenerationService.generateQuestionnaireXml(request, "en", warnings);
        String reportNl = xmlGenerationService.generateReportXml(request, "nl", warnings);
        String reportEn = xmlGenerationService.generateReportXml(request, "en", warnings);

        return new XmlPreviewResponse(questionnaireNl, questionnaireEn, reportNl, reportEn, warnings);
    }

    // ─────────────────────────────────────────────────────────────
    // Assessment build (DEPRECATED)
    // ─────────────────────────────────────────────────────────────

    // TODO: Remove this endpoint after frontend migration is confirmed stable.
    @Deprecated
    @PostMapping("/assessments/build")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentBuildResponse buildAssessment(@Valid @RequestBody AssessmentBuildRequest request) {
        log.warn("DEPRECATED endpoint /api/assessments/build used. Please migrate to /api/questionnaires/publish.");
        PublishResult result = publishService.publish(request);
        String message = String.format(
            "Assessment opgeslagen in Metro database (ID: %d).",
            result.questionnaireId()
        );
        return new AssessmentBuildResponse(result.questionnaireId(), message);
    }
}

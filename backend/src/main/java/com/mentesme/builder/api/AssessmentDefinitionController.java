package com.mentesme.builder.api;

import com.mentesme.builder.model.ComposeRequest;
import com.mentesme.builder.model.GroupSearchResult;
import com.mentesme.builder.model.definition.AssessmentDefinitionResponse;
import com.mentesme.builder.model.definition.QuestionnaireListItem;
import com.mentesme.builder.service.AssessmentDefinitionService;
import com.mentesme.builder.service.MetroLookupRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me", "https://builder-prod.mentes.me"})
public class AssessmentDefinitionController {

    private final AssessmentDefinitionService definitionService;
    private final MetroLookupRepository lookupRepository;

    public AssessmentDefinitionController(AssessmentDefinitionService definitionService,
                                           MetroLookupRepository lookupRepository) {
        this.definitionService = definitionService;
        this.lookupRepository = lookupRepository;
    }

    @GetMapping("/assessment-definitions")
    public List<QuestionnaireListItem> listQuestionnaires(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return lookupRepository.listQuestionnaires(query, Math.min(limit, 100));
    }

    @GetMapping("/assessment-definitions/{questionnaireId}")
    public ResponseEntity<AssessmentDefinitionResponse> getAssessmentDefinition(
            @PathVariable long questionnaireId) {
        return definitionService.exportDefinition(questionnaireId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assessment-definitions/{questionnaireId}/groups")
    public List<GroupSearchResult> getQuestionnaireGroups(@PathVariable long questionnaireId) {
        return lookupRepository.findGroupsForQuestionnaire(questionnaireId);
    }

    @PostMapping("/assessment-definitions/compose")
    public ResponseEntity<AssessmentDefinitionResponse> composeAssessments(
            @RequestBody ComposeRequest request) {
        if (request.questionnaireIds() == null || request.questionnaireIds().size() < 2) {
            return ResponseEntity.badRequest().build();
        }
        var result = definitionService.composeDefinitions(request.questionnaireIds());
        return ResponseEntity.ok(result);
    }
}

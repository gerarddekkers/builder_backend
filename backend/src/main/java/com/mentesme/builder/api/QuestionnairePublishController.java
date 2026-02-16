package com.mentesme.builder.api;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.PublishResult;
import com.mentesme.builder.service.QuestionnairePublishService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me"})
public class QuestionnairePublishController {

    private final QuestionnairePublishService publishService;

    public QuestionnairePublishController(QuestionnairePublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping("/api/questionnaires/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public PublishResult publish(@Valid @RequestBody AssessmentBuildRequest request) {
        return publishService.publish(request);
    }
}

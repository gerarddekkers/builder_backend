package com.mentesme.builder.api;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.PublishEnvironment;
import com.mentesme.builder.model.PublishResult;
import com.mentesme.builder.service.QuestionnairePublishService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me", "https://builder-prod.mentes.me"})
public class QuestionnairePublishController {

    private static final Logger log = LoggerFactory.getLogger(QuestionnairePublishController.class);

    private final QuestionnairePublishService publishService;

    public QuestionnairePublishController(QuestionnairePublishService publishService) {
        this.publishService = publishService;
    }

    @PostMapping("/api/questionnaires/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public PublishResult publish(@Valid @RequestBody AssessmentBuildRequest request) {
        return publishService.publish(request, PublishEnvironment.TEST);
    }

    @PostMapping("/api/questionnaires/publish-test")
    @ResponseStatus(HttpStatus.CREATED)
    public PublishResult publishTest(@Valid @RequestBody AssessmentBuildRequest request) {
        return publishService.publish(request, PublishEnvironment.TEST);
    }

    @PostMapping("/api/questionnaires/publish-production")
    @ResponseStatus(HttpStatus.CREATED)
    public PublishResult publishProduction(
            @Valid @RequestBody AssessmentBuildRequest request,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("userRole");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required for production publish");
        }
        log.warn("PRODUCTION publish triggered for assessment: {}", request.assessmentName());
        return publishService.publish(request, PublishEnvironment.PRODUCTION);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleBeanValidation(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Publish bean validation failed: {}", detail);
        return ResponseEntity.badRequest().body(Map.of("error", detail));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Publish validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        log.error("Publish failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericError(Exception ex) {
        if (ex instanceof ResponseStatusException rse) {
            log.warn("Publish status error: {}", rse.getReason());
            return ResponseEntity.status(rse.getStatusCode())
                    .body(Map.of("error", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        }
        log.error("Publish unexpected error: {}", ex.getMessage(), ex);
        String detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", detail));
    }
}

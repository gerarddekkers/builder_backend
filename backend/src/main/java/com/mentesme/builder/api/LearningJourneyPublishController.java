package com.mentesme.builder.api;

import com.mentesme.builder.model.LearningJourneyPublishRequest;
import com.mentesme.builder.model.LearningJourneyPublishResult;
import com.mentesme.builder.model.PublishEnvironment;
import com.mentesme.builder.service.LearningJourneyIntegrationService;
import com.mentesme.builder.service.LearningJourneyPublishService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me", "https://builder-prod.mentes.me"})
public class LearningJourneyPublishController {

    private static final Logger log = LoggerFactory.getLogger(LearningJourneyPublishController.class);

    private final LearningJourneyPublishService publishService;
    private final LearningJourneyIntegrationService integrationService;
    private final JdbcTemplate metroJdbcTemplate;

    public LearningJourneyPublishController(
            LearningJourneyPublishService publishService,
            LearningJourneyIntegrationService integrationService,
            @Qualifier("metroJdbcTemplate") JdbcTemplate metroJdbcTemplate) {
        this.publishService = publishService;
        this.integrationService = integrationService;
        this.metroJdbcTemplate = metroJdbcTemplate;
    }

    @PostMapping("/api/learning-journeys/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningJourneyPublishResult publish(
            @Valid @RequestBody LearningJourneyPublishRequest request,
            HttpServletRequest httpRequest) {
        requireEditorOrAdmin(httpRequest);
        return publishService.publish(request, PublishEnvironment.TEST);
    }

    @PostMapping("/api/learning-journeys/publish-test")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningJourneyPublishResult publishTest(
            @Valid @RequestBody LearningJourneyPublishRequest request,
            HttpServletRequest httpRequest) {
        requireEditorOrAdmin(httpRequest);
        return publishService.publish(request, PublishEnvironment.TEST);
    }

    @PostMapping("/api/learning-journeys/publish-production")
    @ResponseStatus(HttpStatus.CREATED)
    public LearningJourneyPublishResult publishProduction(
            @Valid @RequestBody LearningJourneyPublishRequest request,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("userRole");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required for production publish");
        }
        log.warn("PRODUCTION publish triggered for learning journey: {}", request.name());
        return publishService.publish(request, PublishEnvironment.PRODUCTION);
    }

    @DeleteMapping("/api/learning-journeys/{id}")
    public ResponseEntity<Map<String, String>> deleteJourney(
            @PathVariable long id,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("userRole");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required");
        }
        log.warn("Deleting learning journey {}", id);
        integrationService.deleteJourney(id, metroJdbcTemplate);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", String.valueOf(id)));
    }

    private void requireEditorOrAdmin(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("userRole");
        if (!"ADMIN".equals(role) && !"EDITOR".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN or EDITOR role required");
        }
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleBeanValidation(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Learning journey bean validation failed: {}", detail);
        return ResponseEntity.badRequest().body(Map.of("error", detail));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException ex) {
        log.warn("Learning journey publish validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateError(IllegalStateException ex) {
        log.error("Learning journey publish failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericError(Exception ex) {
        // Let ResponseStatusException propagate with its original status code
        if (ex instanceof ResponseStatusException rse) {
            log.warn("Learning journey publish status error: {}", rse.getReason());
            return ResponseEntity.status(rse.getStatusCode())
                    .body(Map.of("error", rse.getReason() != null ? rse.getReason() : rse.getMessage()));
        }
        log.error("Learning journey publish unexpected error: {}", ex.getMessage(), ex);
        String detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", detail));
    }
}

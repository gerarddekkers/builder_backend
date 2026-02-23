package com.mentesme.builder.service;

import com.mentesme.builder.model.DocumentInput;
import com.mentesme.builder.model.LearningJourneyPublishRequest;
import com.mentesme.builder.model.StepInput;
import com.mentesme.builder.model.StepInput.StepType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pre-transaction validation for Learning Journey publish requests.
 * All checks run BEFORE any database transaction is started.
 * On failure: throws IllegalArgumentException with all errors listed.
 */
@Service
public class LearningJourneyValidationService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_QUESTIONS_PER_SUBSTEP = 5;
    private static final Set<String> ALLOWED_LANGS = Set.of("nl", "en");
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");

    public void validate(LearningJourneyPublishRequest request) {
        List<String> errors = new ArrayList<>();

        // Name max 50 chars and not empty
        if (request.name() == null || request.name().isBlank()) {
            errors.add("Learning journey name is required.");
        } else if (request.name().length() > MAX_NAME_LENGTH) {
            errors.add("Learning journey name exceeds " + MAX_NAME_LENGTH + " characters.");
        }

        // ljKey contains no spaces
        if (request.name() != null && request.name().contains(" ") &&
                LearningJourneyIntegrationService.generateLjKey(request.name()).contains(" ")) {
            errors.add("Generated ljKey must not contain spaces.");
        }

        // Groups required
        if (request.groupIds() == null || request.groupIds().isEmpty()) {
            errors.add("At least one group must be selected.");
        }

        // Steps required
        List<StepInput> steps = request.steps();
        if (steps == null || steps.isEmpty()) {
            errors.add("At least one step is required.");
            throwIfErrors(errors);
            return;
        }

        // At least 2 hoofdstappen
        long hoofdstapCount = steps.stream()
                .filter(s -> s.type() == StepType.hoofdstap)
                .count();
        if (hoofdstapCount < 2) {
            errors.add("At least 2 Hoofdstappen required (found " + hoofdstapCount + ").");
        }

        // Must end with afsluiting
        StepInput lastStep = steps.get(steps.size() - 1);
        if (lastStep.type() != StepType.afsluiting) {
            errors.add("Last step must be Afsluiting.");
        }

        // Per-step validation
        for (int i = 0; i < steps.size(); i++) {
            StepInput step = steps.get(i);
            int pos = i + 1;

            // No empty titles
            if (step.title() == null || step.title().isBlank()) {
                errors.add("Step " + pos + " has no title.");
            }

            // Max 5 questions per substep
            if (step.type() == StepType.substap
                    && step.questions() != null
                    && step.questions().size() > MAX_QUESTIONS_PER_SUBSTEP) {
                errors.add("Step " + pos + " has " + step.questions().size()
                        + " questions (max " + MAX_QUESTIONS_PER_SUBSTEP + ").");
            }

            // Document validation: filename safety + language
            if (step.documents() != null) {
                for (int d = 0; d < step.documents().size(); d++) {
                    DocumentInput doc = step.documents().get(d);
                    int docPos = d + 1;
                    if (doc.fileName() != null && !SAFE_FILENAME.matcher(doc.fileName()).matches()) {
                        errors.add("Step " + pos + " document " + docPos
                                + ": invalid filename '" + doc.fileName() + "'.");
                    }
                    if (doc.lang() != null && !ALLOWED_LANGS.contains(doc.lang())) {
                        errors.add("Step " + pos + " document " + docPos
                                + ": unsupported language '" + doc.lang() + "' (allowed: nl, en).");
                    }
                }
            }
        }

        throwIfErrors(errors);
    }

    private void throwIfErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Validation failed:\n- " + String.join("\n- ", errors));
        }
    }
}

package com.mentesme.builder.service;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.IntegrationPreviewResponse;
import com.mentesme.builder.model.PublishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionnairePublishService {

    private static final Logger log = LoggerFactory.getLogger(QuestionnairePublishService.class);

    private final MetroIntegrationService metroIntegrationService;
    private final MetroLookupRepository metroLookupRepository;

    public QuestionnairePublishService(
            MetroIntegrationService metroIntegrationService,
            MetroLookupRepository metroLookupRepository
    ) {
        this.metroIntegrationService = metroIntegrationService;
        this.metroLookupRepository = metroLookupRepository;
    }

    @Transactional(transactionManager = "metroTransactionManager")
    public PublishResult publish(AssessmentBuildRequest request) {
        log.info("Publishing questionnaire: {}", request.assessmentName());

        IntegrationPreviewResponse preview = metroIntegrationService.generatePreview(request);

        log.info("Executing {} SQL statements for questionnaire ID {}",
                preview.sqlStatements().size(), preview.summary().questionnaireId());

        metroLookupRepository.executeSqlStatements(preview.sqlStatements());

        long questionnaireId = preview.summary().questionnaireId();

        log.info("Questionnaire {} published successfully", questionnaireId);
        return new PublishResult(questionnaireId, true);
    }
}

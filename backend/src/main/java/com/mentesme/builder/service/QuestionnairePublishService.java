package com.mentesme.builder.service;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.IntegrationPreviewResponse;
import com.mentesme.builder.model.PublishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionnairePublishService {

    private static final Logger log = LoggerFactory.getLogger(QuestionnairePublishService.class);

    private final MetroIntegrationService metroIntegrationService;
    private final MetroLookupRepository metroLookupRepository;
    private final XmlGenerationService xmlGenerationService;
    private final S3XmlUploadService s3XmlUploadService; // null when S3 is disabled

    public QuestionnairePublishService(
            MetroIntegrationService metroIntegrationService,
            MetroLookupRepository metroLookupRepository,
            XmlGenerationService xmlGenerationService,
            ObjectProvider<S3XmlUploadService> s3XmlUploadServiceProvider
    ) {
        this.metroIntegrationService = metroIntegrationService;
        this.metroLookupRepository = metroLookupRepository;
        this.xmlGenerationService = xmlGenerationService;
        this.s3XmlUploadService = s3XmlUploadServiceProvider.getIfAvailable();
    }

    @Transactional(transactionManager = "metroTransactionManager")
    public PublishResult publish(AssessmentBuildRequest request) {
        log.info("Publishing questionnaire: {}", request.assessmentName());

        // Phase 1: Generate and execute SQL
        IntegrationPreviewResponse preview = metroIntegrationService.generatePreview(request);

        log.info("Executing {} SQL statements for questionnaire ID {}",
                preview.sqlStatements().size(), preview.summary().questionnaireId());

        metroLookupRepository.executeSqlStatements(preview.sqlStatements());

        long questionnaireId = preview.summary().questionnaireId();

        // Phase 2: Generate XML, upload to S3, update translation URLs
        if (s3XmlUploadService != null) {
            uploadXmlAndUpdateUrls(request, questionnaireId);
        } else {
            log.info("S3 upload disabled; skipping XML upload for questionnaire {}", questionnaireId);
        }

        log.info("Questionnaire {} published successfully", questionnaireId);
        return new PublishResult(questionnaireId, true);
    }

    private void uploadXmlAndUpdateUrls(AssessmentBuildRequest request, long questionnaireId) {
        List<String> warnings = new ArrayList<>();

        // Generate XML for both languages
        String questionnaireNl = xmlGenerationService.generateQuestionnaireXml(request, "nl", warnings);
        String reportNl = xmlGenerationService.generateReportXml(request, "nl", warnings);
        String questionnaireEn = xmlGenerationService.generateQuestionnaireXml(request, "en", warnings);
        String reportEn = xmlGenerationService.generateReportXml(request, "en", warnings);

        if (!warnings.isEmpty()) {
            log.warn("XML generation warnings for questionnaire {}: {}", questionnaireId, warnings);
        }

        // Track successfully uploaded keys for rollback
        List<String> uploadedKeys = new ArrayList<>();

        try {
            // Upload NL questionnaire
            String nlQuestionnaireKey = s3XmlUploadService.buildKey("nl", questionnaireId, "questionnaire.xml");
            s3XmlUploadService.uploadXml(nlQuestionnaireKey, questionnaireNl);
            uploadedKeys.add(nlQuestionnaireKey);

            // Upload NL report
            String nlReportKey = s3XmlUploadService.buildKey("nl", questionnaireId, "report.xml");
            s3XmlUploadService.uploadXml(nlReportKey, reportNl);
            uploadedKeys.add(nlReportKey);

            // Upload EN questionnaire
            String enQuestionnaireKey = s3XmlUploadService.buildKey("en", questionnaireId, "questionnaire.xml");
            s3XmlUploadService.uploadXml(enQuestionnaireKey, questionnaireEn);
            uploadedKeys.add(enQuestionnaireKey);

            // Upload EN report
            String enReportKey = s3XmlUploadService.buildKey("en", questionnaireId, "report.xml");
            s3XmlUploadService.uploadXml(enReportKey, reportEn);
            uploadedKeys.add(enReportKey);

            // Update DB with S3 URLs
            metroLookupRepository.updateTranslationUrls(questionnaireId, "nl",
                    s3XmlUploadService.buildUrl(nlQuestionnaireKey),
                    s3XmlUploadService.buildUrl(nlReportKey));
            metroLookupRepository.updateTranslationUrls(questionnaireId, "en",
                    s3XmlUploadService.buildUrl(enQuestionnaireKey),
                    s3XmlUploadService.buildUrl(enReportKey));

            log.info("XML files uploaded to S3 and URLs stored for questionnaire {}", questionnaireId);

        } catch (RuntimeException ex) {
            log.error("S3 upload or URL update failed for questionnaire {}; rolling back {} uploaded objects",
                    questionnaireId, uploadedKeys.size(), ex);
            s3XmlUploadService.deleteObjects(uploadedKeys);
            throw ex;
        }
    }
}

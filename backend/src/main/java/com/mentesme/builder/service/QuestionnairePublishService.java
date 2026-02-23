package com.mentesme.builder.service;

import com.mentesme.builder.model.AssessmentBuildRequest;
import com.mentesme.builder.model.IntegrationPreviewResponse;
import com.mentesme.builder.model.PublishEnvironment;
import com.mentesme.builder.model.PublishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionnairePublishService {

    private static final Logger log = LoggerFactory.getLogger(QuestionnairePublishService.class);

    private final MetroIntegrationService metroIntegrationService;
    private final XmlGenerationService xmlGenerationService;
    private final S3XmlUploadService s3XmlUploadService; // null when S3 is disabled

    // Test environment
    private final TransactionTemplate testTxTemplate;
    private final JdbcTemplate testJdbcTemplate;

    // Production environment (null when not configured)
    private final PlatformTransactionManager prodTxManager;
    private final JdbcTemplate prodJdbcTemplate;

    public QuestionnairePublishService(
            MetroIntegrationService metroIntegrationService,
            XmlGenerationService xmlGenerationService,
            ObjectProvider<S3XmlUploadService> s3XmlUploadServiceProvider,
            @Qualifier("metroJdbcTransactionManager") PlatformTransactionManager testTxManager,
            @Qualifier("metroJdbcTemplate") JdbcTemplate testJdbcTemplate,
            @Qualifier("metroProdTransactionManager") ObjectProvider<PlatformTransactionManager> prodTxManagerProvider,
            @Qualifier("metroProdJdbcTemplate") ObjectProvider<JdbcTemplate> prodJdbcTemplateProvider
    ) {
        this.metroIntegrationService = metroIntegrationService;
        this.xmlGenerationService = xmlGenerationService;
        this.s3XmlUploadService = s3XmlUploadServiceProvider.getIfAvailable();
        this.testTxTemplate = new TransactionTemplate(testTxManager);
        this.testJdbcTemplate = testJdbcTemplate;
        this.prodTxManager = prodTxManagerProvider.getIfAvailable();
        this.prodJdbcTemplate = prodJdbcTemplateProvider.getIfAvailable();
    }

    public PublishResult publish(AssessmentBuildRequest request, PublishEnvironment env) {
        log.info("Publishing questionnaire '{}' to {}", request.assessmentName(), env);

        // Resolve environment-specific resources
        TransactionTemplate txTemplate;
        JdbcTemplate jdbcTemplate;
        String s3Prefix;

        if (env == PublishEnvironment.PRODUCTION) {
            if (prodTxManager == null || prodJdbcTemplate == null) {
                throw new IllegalStateException(
                        "Production database is not configured. Set BUILDER_METRO_PROD_ENABLED=true with valid credentials.");
            }
            txTemplate = new TransactionTemplate(prodTxManager);
            jdbcTemplate = prodJdbcTemplate;
            s3Prefix = "production";
        } else {
            txTemplate = testTxTemplate;
            jdbcTemplate = testJdbcTemplate;
            s3Prefix = "test";
        }

        // Execute within environment-specific transaction
        long totalStart = System.currentTimeMillis();
        return txTemplate.execute(status -> {
            Map<String, Long> timings = new LinkedHashMap<>();

            // Create environment-specific repository
            MetroLookupRepository envRepo = new MetroLookupRepository(jdbcTemplate);

            // Check autocommit for diagnostics
            try {
                boolean autoCommit = jdbcTemplate.execute(
                        (org.springframework.jdbc.core.ConnectionCallback<Boolean>) conn -> conn.getAutoCommit());
                timings.put("autoCommit", autoCommit ? 1L : 0L);
            } catch (Exception e) {
                log.warn("Could not check autoCommit: {}", e.getMessage());
            }

            // Phase 1a: Generate SQL (includes DB lookups)
            long t0 = System.currentTimeMillis();
            IntegrationPreviewResponse preview = metroIntegrationService.generatePreview(request, envRepo);
            long t1 = System.currentTimeMillis();
            timings.put("generatePreview_ms", t1 - t0);
            timings.put("sqlStatementCount", (long) preview.sqlStatements().size());
            log.info("[{}] Phase 1a: generatePreview took {}ms ({} SQL statements, questionnaire ID {})",
                    env, t1 - t0, preview.sqlStatements().size(), preview.summary().questionnaireId());

            // Phase 1b: Execute SQL statements one by one (with per-statement timing)
            var perStmtTimings = envRepo.executeSqlStatements(preview.sqlStatements());
            long t2 = System.currentTimeMillis();
            timings.put("executeSql_ms", t2 - t1);
            // Add top-5 slowest statements to timings
            if (perStmtTimings != null) {
                var sorted = perStmtTimings.stream()
                        .sorted((a, b) -> Long.compare((long) b.get("ms"), (long) a.get("ms")))
                        .limit(5)
                        .toList();
                for (int i = 0; i < sorted.size(); i++) {
                    var entry = sorted.get(i);
                    timings.put("slow" + (i + 1) + "_ms", (long) entry.get("ms"));
                    timings.put("slow" + (i + 1) + "_idx", (long) (int) entry.get("i"));
                }
            }
            log.info("[{}] Phase 1b: executeSqlStatements took {}ms", env, t2 - t1);

            long questionnaireId = preview.summary().questionnaireId();

            // Phase 2: Generate XML, upload to S3, update translation URLs
            if (s3XmlUploadService != null) {
                uploadXmlAndUpdateUrls(request, questionnaireId, envRepo, s3Prefix);
                long t3 = System.currentTimeMillis();
                timings.put("xmlAndS3Upload_ms", t3 - t2);
                log.info("[{}] Phase 2: XML generation + S3 upload took {}ms", env, t3 - t2);
            } else {
                log.info("[{}] S3 upload disabled; skipping XML upload for questionnaire {}",
                        env, questionnaireId);
            }

            long totalMs = System.currentTimeMillis() - totalStart;
            timings.put("total_ms", totalMs);
            timings.put("questionnaireId", questionnaireId);
            timings.put("groupCount", (long) request.groupIds().size());
            log.info("[{}] Questionnaire {} published successfully (total: {}ms)",
                    env, questionnaireId, totalMs);
            return new PublishResult(questionnaireId, true, timings);
        });
    }

    private void uploadXmlAndUpdateUrls(AssessmentBuildRequest request, long questionnaireId,
                                         MetroLookupRepository envRepo, String s3Prefix) {
        List<String> warnings = new ArrayList<>();
        String assessmentName = request.assessmentName();

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
            // Upload NL questionnaire — key: test/nl/questionnaire_{slug}_NL.xml
            String nlQuestionnaireKey = s3XmlUploadService.buildKey(s3Prefix, "nl", assessmentName, "questionnaire");
            s3XmlUploadService.uploadXml(nlQuestionnaireKey, questionnaireNl);
            uploadedKeys.add(nlQuestionnaireKey);

            // Upload NL report
            String nlReportKey = s3XmlUploadService.buildKey(s3Prefix, "nl", assessmentName, "report");
            s3XmlUploadService.uploadXml(nlReportKey, reportNl);
            uploadedKeys.add(nlReportKey);

            // Upload EN questionnaire
            String enQuestionnaireKey = s3XmlUploadService.buildKey(s3Prefix, "en", assessmentName, "questionnaire");
            s3XmlUploadService.uploadXml(enQuestionnaireKey, questionnaireEn);
            uploadedKeys.add(enQuestionnaireKey);

            // Upload EN report
            String enReportKey = s3XmlUploadService.buildKey(s3Prefix, "en", assessmentName, "report");
            s3XmlUploadService.uploadXml(enReportKey, reportEn);
            uploadedKeys.add(enReportKey);

            // Update DB with S3 URLs
            envRepo.updateTranslationUrls(questionnaireId, "nl",
                    s3XmlUploadService.buildUrl(nlQuestionnaireKey),
                    s3XmlUploadService.buildUrl(nlReportKey));
            envRepo.updateTranslationUrls(questionnaireId, "en",
                    s3XmlUploadService.buildUrl(enQuestionnaireKey),
                    s3XmlUploadService.buildUrl(enReportKey));

            log.info("XML files uploaded to S3 ({}) and URLs stored for questionnaire {} [keys: {}, {}]",
                    s3Prefix, questionnaireId, nlQuestionnaireKey, enQuestionnaireKey);

        } catch (RuntimeException ex) {
            log.error("S3 upload or URL update failed for questionnaire {}; rolling back {} uploaded objects",
                    questionnaireId, uploadedKeys.size(), ex);
            s3XmlUploadService.deleteObjects(uploadedKeys);
            throw ex;
        }
    }
}

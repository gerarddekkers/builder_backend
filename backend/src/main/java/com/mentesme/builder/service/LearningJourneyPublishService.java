package com.mentesme.builder.service;

import com.mentesme.builder.model.LearningJourneyPublishRequest;
import com.mentesme.builder.model.LearningJourneyPublishResult;
import com.mentesme.builder.model.PublishEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaction orchestrator for Learning Journey publishing.
 * Follows the exact same pattern as QuestionnairePublishService:
 * - Separate TEST and PRODUCTION environments
 * - Separate JdbcTemplates and TransactionTemplates
 * - Full transaction wrapping
 * - Rollback on any exception
 */
@Service
public class LearningJourneyPublishService {

    private static final Logger log = LoggerFactory.getLogger(LearningJourneyPublishService.class);

    private final LearningJourneyValidationService validationService;
    private final LearningJourneyIntegrationService integrationService;

    // Test environment
    private final TransactionTemplate testTxTemplate;
    private final JdbcTemplate testJdbcTemplate;

    // Production environment (null when not configured)
    private final PlatformTransactionManager prodTxManager;
    private final JdbcTemplate prodJdbcTemplate;

    public LearningJourneyPublishService(
            LearningJourneyValidationService validationService,
            LearningJourneyIntegrationService integrationService,
            @Qualifier("metroJdbcTransactionManager") PlatformTransactionManager testTxManager,
            @Qualifier("metroJdbcTemplate") JdbcTemplate testJdbcTemplate,
            @Qualifier("metroProdTransactionManager") ObjectProvider<PlatformTransactionManager> prodTxManagerProvider,
            @Qualifier("metroProdJdbcTemplate") ObjectProvider<JdbcTemplate> prodJdbcTemplateProvider
    ) {
        this.validationService = validationService;
        this.integrationService = integrationService;
        this.testTxTemplate = new TransactionTemplate(testTxManager);
        this.testJdbcTemplate = testJdbcTemplate;
        this.prodTxManager = prodTxManagerProvider.getIfAvailable();
        this.prodJdbcTemplate = prodJdbcTemplateProvider.getIfAvailable();
    }

    /**
     * Publish a learning journey to the specified environment.
     * Validation runs BEFORE the transaction starts.
     * The entire insert flow runs within a single transaction — any exception triggers full rollback.
     */
    public LearningJourneyPublishResult publish(LearningJourneyPublishRequest request, PublishEnvironment env) {
        log.info("Publishing learning journey '{}' to {}", request.name(), env);

        // ── Phase 0: Pre-validation (NO SQL) ──────────────────────────────
        validationService.validate(request);

        // ── Resolve environment-specific resources ─────────────────────────
        TransactionTemplate txTemplate;
        JdbcTemplate jdbcTemplate;
        String envLabel;

        if (env == PublishEnvironment.PRODUCTION) {
            if (prodTxManager == null || prodJdbcTemplate == null) {
                throw new IllegalStateException(
                        "Production database is not configured. Set BUILDER_METRO_PROD_ENABLED=true with valid credentials.");
            }
            txTemplate = new TransactionTemplate(prodTxManager);
            jdbcTemplate = prodJdbcTemplate;
            envLabel = "PRODUCTION";
        } else {
            txTemplate = testTxTemplate;
            jdbcTemplate = testJdbcTemplate;
            envLabel = "TEST";
        }

        // ── Execute within transaction ─────────────────────────────────────
        LearningJourneyPublishResult result = txTemplate.execute(status -> {
            try {
                return integrationService.execute(request, jdbcTemplate, envLabel);
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });

        if (result == null) {
            throw new IllegalStateException("Transaction returned null result.");
        }

        log.info("[{}] Learning journey '{}' published successfully (id={})",
                envLabel, request.name(), result.learningJourneyId());
        return result;
    }
}

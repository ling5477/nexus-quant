package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationPolicy;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.TrustedRootStrategyArtifactVerifier;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** publishRecordId 到 read-only Release admission preview 的编排回归。 */
class StrategyReleaseAdmissionPreviewServiceTest {

    private static final String PUBLISH_ID = "publish-preview-001";
    private static final String STRATEGY_VERSION_ID = "strategy-version-preview-001";
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String EVALUATION_ID = "evaluation-preview-001";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant WINDOW_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-02T00:00:00Z");

    private StubReleaseProductionService releaseService;
    private StubFactsRepository factsRepository;
    private StrategyReleaseAdmissionPreviewService service;

    @BeforeEach
    void setUp() {
        releaseService = new StubReleaseProductionService();
        factsRepository = new StubFactsRepository(validFacts(policy()));
        StrategyValidationOverviewQueryService validationService = new StrategyValidationOverviewQueryService(
                () -> new com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts(
                        0, 0, 0, 0, 0, 0, Optional.empty()
                )
        );
        service = new StrategyReleaseAdmissionPreviewService(
                releaseService,
                factsRepository,
                validationService,
                new ReleaseToShadowAdmissionService()
        );
    }

    @Test
    void shouldReturnEmptyWhenPublishRecordDoesNotExist() {
        releaseService.result = rejected(FindingCode.PUBLISH_RECORD_NOT_FOUND);

        assertTrue(service.preview(PUBLISH_ID, "trace-not-found").isEmpty());
    }

    @Test
    void shouldBlockLegacyUnboundRelease() {
        assertBlocked(FindingCode.ARTIFACT_LOCATION_UNBOUND, "ARTIFACT_LOCATION_UNBOUND");
    }

    @Test
    void shouldBlockWhenTrustedRootIsNotConfigured() {
        assertBlocked(FindingCode.ARTIFACT_ROOT_NOT_CONFIGURED, "ARTIFACT_ROOT_NOT_CONFIGURED");
    }

    @Test
    void shouldBlockRejectedArtifact() {
        assertBlocked(FindingCode.ARTIFACT_MANIFEST_INVALID, "ARTIFACT_NOT_VERIFIED");
    }

    @Test
    void shouldBlockArtifactReleaseIdentityMismatch() {
        assertBlocked(
                FindingCode.ARTIFACT_RELEASE_IDENTITY_MISMATCH,
                "ARTIFACT_RELEASE_IDENTITY_MISMATCH"
        );
    }

    @Test
    void shouldBlockWhenCanonicalValidationIsNotApproved() {
        releaseService.result = verifiedRelease();
        factsRepository.result = factsWithValidationStatus("PENDING", policy());

        StrategyReleaseAdmissionPreview preview = service.preview(PUBLISH_ID, "trace-validation").orElseThrow();

        assertEquals(ReleaseToShadowAdmissionDecision.Decision.BLOCKED, preview.admissionDecision());
        assertTrue(preview.reasonCodes().contains("VALIDATION_NOT_APPROVED"));
    }

    @Test
    void shouldBlockWhenNoSideEffectPolicyIsIncomplete() {
        releaseService.result = verifiedRelease();
        factsRepository.result = validFacts(
                new ShadowRunCreationPlan.SideEffectPolicy(false, true, true, true, true, true)
        );

        StrategyReleaseAdmissionPreview preview = service.preview(PUBLISH_ID, "trace-policy").orElseThrow();

        assertEquals(ReleaseToShadowAdmissionDecision.Decision.BLOCKED, preview.admissionDecision());
        assertTrue(preview.reasonCodes().contains("NO_ORDER_SUBMISSION_REQUIRED"));
    }

    @Test
    void shouldReturnEligiblePreviewWithoutExecutingCreationPlan() {
        releaseService.result = verifiedRelease();

        StrategyReleaseAdmissionPreview preview = service.preview(PUBLISH_ID, "trace-eligible").orElseThrow();

        assertEquals(ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE, preview.admissionDecision());
        assertEquals(List.of("ELIGIBLE_FOR_CREATION_PLAN_ONLY"), preview.reasonCodes());
        assertEquals("RELEASE_BOUND", preview.bindingMode().name());
        assertEquals(DIGEST, preview.artifactDigest());
        assertEquals(1, releaseService.calls);
        assertEquals(1, factsRepository.calls);
    }

    @Test
    void shouldFailClosedWhenFactLoadThrows() {
        releaseService.result = verifiedRelease();
        factsRepository.failure = new IllegalStateException("synthetic");

        StrategyReleaseAdmissionPreview preview = service.preview(PUBLISH_ID, "trace-facts").orElseThrow();

        assertEquals(ReleaseToShadowAdmissionDecision.Decision.BLOCKED, preview.admissionDecision());
        assertTrue(preview.reasonCodes().contains("VALIDATION_EVIDENCE_MISSING"));
        assertTrue(preview.reasonCodes().contains("SHADOW_WINDOW_MISSING"));
        assertTrue(preview.reasonCodes().contains("SIDE_EFFECT_POLICY_MISSING"));
    }

    @Test
    void shouldHaveNoShadowRuntimeOrTradingWriteDependency() {
        assertTrue(Arrays.stream(StrategyReleaseAdmissionPreviewService.class.getDeclaredFields())
                .noneMatch(field -> {
                    String type = field.getType().getName().toLowerCase();
                    return type.contains("shadowrunrepository")
                            || type.contains("runner")
                            || type.contains("scheduler")
                            || type.contains("ordercommand")
                            || type.contains("ledger")
                            || type.contains("credential")
                            || type.contains("adapter");
                }));
    }

    private void assertBlocked(FindingCode findingCode, String expectedReason) {
        releaseService.result = rejected(findingCode);

        StrategyReleaseAdmissionPreview preview = service.preview(PUBLISH_ID, "trace-blocked").orElseThrow();

        assertEquals(ReleaseToShadowAdmissionDecision.Decision.BLOCKED, preview.admissionDecision());
        assertTrue(preview.reasonCodes().contains(expectedReason), preview.reasonCodes().toString());
        assertFalse(preview.reasonCodes().isEmpty());
    }

    private static StrategyReleaseAdmissionPreviewFacts validFacts(
            ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy
    ) {
        return factsWithValidationStatus("SUCCEEDED", sideEffectPolicy);
    }

    private static StrategyReleaseAdmissionPreviewFacts factsWithValidationStatus(
            String evaluationStatus,
            ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy
    ) {
        return new StrategyReleaseAdmissionPreviewFacts(
                new LatestDecisionFact(
                        STRATEGY_VERSION_ID,
                        DATASET_ID,
                        EVALUATION_ID,
                        PUBLISH_ID,
                        "paper-preview-001",
                        null,
                        "ACTIVE",
                        evaluationStatus,
                        "SUCCEEDED",
                        "STOPPED",
                        "SIM",
                        null,
                        null,
                        WINDOW_END,
                        WINDOW_END
                ),
                WINDOW_START,
                WINDOW_END,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                sideEffectPolicy
        );
    }

    private static StrategyRelease verifiedRelease() {
        return release(
                StrategyReleaseStatus.VERIFIED,
                StrategyArtifactVerificationResult.verified(DIGEST, 1, 64),
                manifest(),
                DIGEST
        );
    }

    private static StrategyRelease rejected(FindingCode findingCode) {
        return release(
                StrategyReleaseStatus.REJECTED,
                StrategyArtifactVerificationResult.rejected(findingCode, "<artifact-binding>"),
                StrategyArtifactManifest.empty(),
                null
        );
    }

    private static StrategyRelease release(
            StrategyReleaseStatus status,
            StrategyArtifactVerificationResult verification,
            StrategyArtifactManifest manifest,
            String digest
    ) {
        return new StrategyRelease(
                PUBLISH_ID,
                PUBLISH_ID,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID,
                manifest,
                digest,
                status,
                verification,
                WINDOW_START,
                WINDOW_START
        );
    }

    private static StrategyArtifactManifest manifest() {
        return new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID,
                List.of(new StrategyArtifactManifest.ArtifactFile(
                        "model",
                        "artifacts/model.bin",
                        "b".repeat(64),
                        64,
                        "application/octet-stream"
                )),
                DIGEST,
                WINDOW_START,
                "test-generator"
        );
    }

    private static ShadowRunCreationPlan.SideEffectPolicy policy() {
        return new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, true);
    }

    private static final class StubFactsRepository implements StrategyReleaseAdmissionPreviewFactsRepository {
        private StrategyReleaseAdmissionPreviewFacts result;
        private RuntimeException failure;
        private int calls;

        private StubFactsRepository(StrategyReleaseAdmissionPreviewFacts result) {
            this.result = result;
        }

        @Override
        public StrategyReleaseAdmissionPreviewFacts loadByPublishRecordId(String publishRecordId) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class StubReleaseProductionService extends StrategyReleaseProductionService {
        private StrategyRelease result = verifiedRelease();
        private int calls;

        private StubReleaseProductionService() {
            super(
                    ignored -> StrategyReleaseProvenanceFacts.missing(PUBLISH_ID),
                    (artifactStorageKey, manifestStorageKey) ->
                            StrategyReleaseArtifactBindingResolver.ArtifactBindingResolution.rejected(
                                    FindingCode.ARTIFACT_LOCATION_UNBOUND,
                                    "<artifact-binding>"
                            ),
                    new TrustedRootStrategyArtifactVerifier(new StrategyArtifactVerificationPolicy(1, 64, 64))
            );
        }

        @Override
        public StrategyRelease verify(String publishRecordId) {
            calls++;
            return result;
        }
    }
}

package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionDecision.Decision;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionDecision.ReasonCode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Release-to-Shadow production admission 的纯决策回归；不使用 repository、文件、网络或 Clock。
 */
class ReleaseToShadowAdmissionServiceTest {

    private static final ReleaseToShadowAdmissionService SERVICE = new ReleaseToShadowAdmissionService();
    private static final String PUBLISH_ID = "publish-release-001";
    private static final String STRATEGY_VERSION_ID = "strategy-version-001";
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EVALUATION_ID = "evaluation-001";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant WINDOW_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void shouldReturnEligibleCreationPlanForVerifiedReleaseBoundFacts() {
        ReleaseToShadowAdmissionDecision result = SERVICE.admit(request());

        assertEquals(Decision.ELIGIBLE, result.decision());
        assertEquals(List.of(ReasonCode.ELIGIBLE_FOR_CREATION_PLAN_ONLY), result.reasonCodes());
        assertEquals(PUBLISH_ID, result.releaseAnchorId());
        assertEquals(DIGEST, result.artifactDigest());
        assertEquals(STRATEGY_VERSION_ID, result.strategyVersionId());
        assertEquals(DATASET_ID, result.datasetId());
        assertEquals(EVALUATION_ID, result.evaluationId());
        assertSafetyBoundary(result);

        ShadowRunCreationPlan plan = result.creationPlan();
        assertEquals(PUBLISH_ID, plan.releaseAnchorId());
        assertEquals(PUBLISH_ID, plan.publishRecordId());
        assertEquals(DIGEST, plan.artifactDigest());
        assertEquals(WINDOW_START, plan.windowStart());
        assertEquals(WINDOW_END, plan.windowEnd());
        assertEquals("dataset:" + DATASET_ID, plan.inputReference());
        assertEquals("publish:" + PUBLISH_ID, plan.provenanceReference());
        assertEquals("trace-001", plan.traceId());
        assertTrue(plan.sideEffectPolicy().allNoSideEffects());
        assertEquals(64, plan.shadowRunIdempotencyKey().length());
    }

    @Test
    void shouldBlockLegacyBindingModesAndInvalidDigest() {
        assertBlocked(withBinding(PUBLISH_ID, null), ReasonCode.RELEASE_BINDING_REQUIRED);
        assertBlocked(withBinding(PUBLISH_ID, null), ReasonCode.ARTIFACT_DIGEST_MISSING);
        assertBlocked(withBinding(null, null), ReasonCode.RELEASE_BINDING_REQUIRED);
        assertBlocked(withBinding(null, null), ReasonCode.PUBLISH_ID_MISSING);
        assertBlocked(withBinding(PUBLISH_ID, "A".repeat(64)), ReasonCode.ARTIFACT_DIGEST_INVALID);
    }

    @Test
    void shouldBlockEveryRequestedProvenanceMismatch() {
        assertBlocked(withIdentity("release-other", PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID),
                ReasonCode.RELEASE_IDENTITY_MISMATCH);
        assertBlocked(withIdentity(PUBLISH_ID, "publish-other", DIGEST, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID),
                ReasonCode.PUBLISH_ID_MISMATCH);
        assertBlocked(withIdentity(PUBLISH_ID, PUBLISH_ID, DIGEST, "strategy-other", DATASET_ID, EVALUATION_ID),
                ReasonCode.STRATEGY_VERSION_MISMATCH);
        assertBlocked(withIdentity(PUBLISH_ID, PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID,
                        UUID.fromString("22222222-2222-2222-2222-222222222222"), EVALUATION_ID),
                ReasonCode.DATASET_MISMATCH);
        assertBlocked(withIdentity(PUBLISH_ID, PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID, "evaluation-other"),
                ReasonCode.EVALUATION_MISMATCH);
        assertBlocked(withIdentity(PUBLISH_ID, PUBLISH_ID, "b".repeat(64), STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID),
                ReasonCode.ARTIFACT_DIGEST_MISMATCH);
    }

    @Test
    void shouldBlockUnverifiedRejectedAndArtifactVerificationFailure() {
        assertBlocked(withRelease(release(StrategyReleaseStatus.UNVERIFIED, verified(), manifest())),
                ReasonCode.RELEASE_UNVERIFIED);
        assertBlocked(withRelease(release(StrategyReleaseStatus.REJECTED, rejectedVerification(), manifest())),
                ReasonCode.RELEASE_REJECTED);
        assertBlocked(withRelease(release(StrategyReleaseStatus.REJECTED, rejectedVerification(), manifest())),
                ReasonCode.ARTIFACT_NOT_VERIFIED);
    }

    @Test
    void shouldBlockManifestProvenanceMismatch() {
        StrategyArtifactManifest mismatched = new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                "evaluation-other",
                manifest().artifactFiles(),
                DIGEST,
                WINDOW_START,
                "test-generator"
        );

        assertBlocked(withRelease(release(StrategyReleaseStatus.VERIFIED, verified(), mismatched)),
                ReasonCode.MANIFEST_PROVENANCE_MISMATCH);
    }

    @Test
    void shouldFailClosedForMissingFactsAndValidationUnknowns() {
        assertBlocked(new ReleaseToShadowAdmissionRequest(
                        null, PUBLISH_ID, PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID,
                        StrategyValidationDecision.APPROVED, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), "trace-001"),
                ReasonCode.PUBLISH_RECORD_MISSING);
        assertBlocked(withValidation(null), ReasonCode.VALIDATION_EVIDENCE_MISSING);
        assertBlocked(withValidation(StrategyValidationDecision.NO_EVIDENCE), ReasonCode.VALIDATION_EVIDENCE_MISSING);
        assertBlocked(withValidation(StrategyValidationDecision.STALE_EVIDENCE), ReasonCode.VALIDATION_EVIDENCE_STALE);
        ReleaseToShadowAdmissionRequest base = request();
        assertBlocked(copy(base, null, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, base.traceId()),
                ReasonCode.SIDE_EFFECT_POLICY_MISSING);
        assertBlocked(withIdentity(PUBLISH_ID, PUBLISH_ID, DIGEST, null, null, null),
                ReasonCode.STRATEGY_VERSION_MISSING);
        assertBlocked(withIdentity(PUBLISH_ID, PUBLISH_ID, DIGEST, null, null, null),
                ReasonCode.DATASET_MISSING);
        assertBlocked(withIdentity(PUBLISH_ID, PUBLISH_ID, DIGEST, null, null, null),
                ReasonCode.EVALUATION_MISSING);
        for (StrategyValidationDecision decision : List.of(
                StrategyValidationDecision.REJECTED,
                StrategyValidationDecision.NEEDS_REVIEW,
                StrategyValidationDecision.BLOCKED
        )) {
            assertBlocked(withValidation(decision), ReasonCode.VALIDATION_NOT_APPROVED);
        }
    }

    @Test
    void shouldBlockInvalidWindowAndAuthorizationBoundary() {
        ReleaseToShadowAdmissionRequest base = request();
        assertBlocked(copy(base, base.sideEffectPolicy(), WINDOW_START, WINDOW_START,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, base.traceId()),
                ReasonCode.SHADOW_WINDOW_INVALID);
        assertBlocked(copy(base, base.sideEffectPolicy(), WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.REPLAY_ONLY, base.traceId()),
                ReasonCode.AUTHORIZATION_BOUNDARY_INVALID);
    }

    @Test
    void shouldBlockEveryNoSideEffectPolicyViolation() {
        List<ShadowRunCreationPlan.SideEffectPolicy> invalidPolicies = List.of(
                new ShadowRunCreationPlan.SideEffectPolicy(false, true, true, true, true, true),
                new ShadowRunCreationPlan.SideEffectPolicy(true, false, true, true, true, true),
                new ShadowRunCreationPlan.SideEffectPolicy(true, true, false, true, true, true),
                new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, false, true, true),
                new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, false, true),
                new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, false)
        );
        List<ReasonCode> reasons = List.of(
                ReasonCode.NO_ORDER_SUBMISSION_REQUIRED,
                ReasonCode.NO_CREDENTIAL_ACCESS_REQUIRED,
                ReasonCode.NO_PRIVATE_ENDPOINT_REQUIRED,
                ReasonCode.NO_LEDGER_MUTATION_REQUIRED,
                ReasonCode.NO_ACCOUNT_MUTATION_REQUIRED,
                ReasonCode.NO_EXTERNAL_PRIVATE_IO_REQUIRED
        );

        for (int index = 0; index < invalidPolicies.size(); index++) {
            ReleaseToShadowAdmissionRequest base = request();
            assertBlocked(copy(base, invalidPolicies.get(index), WINDOW_START, WINDOW_END,
                    ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, base.traceId()), reasons.get(index));
        }
    }

    @Test
    void shouldBeDeterministicAndKeepTraceOutOfIdempotencyMaterial() {
        ReleaseToShadowAdmissionDecision first = SERVICE.admit(request());
        ReleaseToShadowAdmissionDecision same = SERVICE.admit(request());
        ReleaseToShadowAdmissionRequest differentTrace = copy(
                request(), policy(), WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, "trace-002"
        );
        ReleaseToShadowAdmissionDecision replay = SERVICE.admit(differentTrace);

        assertEquals(first, same);
        assertEquals(first.creationPlan().shadowRunIdempotencyKey(), replay.creationPlan().shadowRunIdempotencyKey());
        assertNotEquals(first.creationPlan(), replay.creationPlan());
    }

    @Test
    void shouldProveNoRepositoryTradingCredentialOrRuntimeDependency() {
        assertEquals(0, ReleaseToShadowAdmissionService.class.getDeclaredConstructors()[0].getParameterCount());
        assertTrue(Arrays.stream(ReleaseToShadowAdmissionService.class.getDeclaredFields())
                .noneMatch(field -> {
                    String type = field.getType().getName().toLowerCase();
                    return type.contains("repository")
                            || type.contains("ordercommand")
                            || type.contains("adapter")
                            || type.contains("credential")
                            || type.contains("runner")
                            || type.contains("scheduler");
                }));
        assertSafetyBoundary(SERVICE.admit(request()));
    }

    @Test
    void shouldKeepDecisionAndPlanFreeOfTradingOrSensitiveFields() {
        List<String> decisionFields = recordFieldNames(ReleaseToShadowAdmissionDecision.class);
        List<String> planFields = recordFieldNames(ShadowRunCreationPlan.class);

        List.of("credential", "accountBalance", "privateEndpointPayload", "exchangeOrderId", "liveAuthorization")
                .forEach(forbidden -> {
                    assertFalse(decisionFields.contains(forbidden));
                    assertFalse(planFields.contains(forbidden));
                });
    }

    private static ReleaseToShadowAdmissionRequest request() {
        return new ReleaseToShadowAdmissionRequest(
                release(StrategyReleaseStatus.VERIFIED, verified(), manifest()),
                PUBLISH_ID,
                PUBLISH_ID,
                DIGEST,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID,
                StrategyValidationDecision.APPROVED,
                WINDOW_START,
                WINDOW_END,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                policy(),
                "trace-001"
        );
    }

    private static ReleaseToShadowAdmissionRequest withRelease(StrategyRelease release) {
        ReleaseToShadowAdmissionRequest base = request();
        return new ReleaseToShadowAdmissionRequest(
                release, base.releaseAnchorId(), base.publishRecordId(), base.artifactDigest(), base.strategyVersionId(),
                base.datasetId(), base.evaluationId(), base.validationDecision(), base.windowStart(), base.windowEnd(),
                base.authorizationBoundary(), base.sideEffectPolicy(), base.traceId()
        );
    }

    private static ReleaseToShadowAdmissionRequest withBinding(String publishId, String digest) {
        ReleaseToShadowAdmissionRequest base = request();
        return new ReleaseToShadowAdmissionRequest(
                base.release(), base.releaseAnchorId(), publishId, digest, base.strategyVersionId(), base.datasetId(),
                base.evaluationId(), base.validationDecision(), base.windowStart(), base.windowEnd(),
                base.authorizationBoundary(), base.sideEffectPolicy(), base.traceId()
        );
    }

    private static ReleaseToShadowAdmissionRequest withIdentity(
            String releaseAnchorId,
            String publishId,
            String digest,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId
    ) {
        ReleaseToShadowAdmissionRequest base = request();
        return new ReleaseToShadowAdmissionRequest(
                base.release(), releaseAnchorId, publishId, digest, strategyVersionId, datasetId, evaluationId,
                base.validationDecision(), base.windowStart(), base.windowEnd(), base.authorizationBoundary(),
                base.sideEffectPolicy(), base.traceId()
        );
    }

    private static ReleaseToShadowAdmissionRequest withValidation(StrategyValidationDecision decision) {
        ReleaseToShadowAdmissionRequest base = request();
        return new ReleaseToShadowAdmissionRequest(
                base.release(), base.releaseAnchorId(), base.publishRecordId(), base.artifactDigest(),
                base.strategyVersionId(), base.datasetId(), base.evaluationId(), decision, base.windowStart(),
                base.windowEnd(), base.authorizationBoundary(), base.sideEffectPolicy(), base.traceId()
        );
    }

    private static ReleaseToShadowAdmissionRequest copy(
            ReleaseToShadowAdmissionRequest base,
            ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy,
            Instant windowStart,
            Instant windowEnd,
            ShadowRunAuthorizationBoundary boundary,
            String traceId
    ) {
        return new ReleaseToShadowAdmissionRequest(
                base.release(), base.releaseAnchorId(), base.publishRecordId(), base.artifactDigest(),
                base.strategyVersionId(), base.datasetId(), base.evaluationId(), base.validationDecision(), windowStart,
                windowEnd, boundary, sideEffectPolicy, traceId
        );
    }

    private static StrategyRelease release(
            StrategyReleaseStatus status,
            StrategyArtifactVerificationResult verification,
            StrategyArtifactManifest manifest
    ) {
        return new StrategyRelease(
                PUBLISH_ID,
                PUBLISH_ID,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID,
                manifest,
                DIGEST,
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
                        "strategy-model",
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

    private static StrategyArtifactVerificationResult verified() {
        return StrategyArtifactVerificationResult.verified(DIGEST, 1, 64);
    }

    private static StrategyArtifactVerificationResult rejectedVerification() {
        return StrategyArtifactVerificationResult.rejected(
                StrategyArtifactVerificationResult.FindingCode.DIGEST_MISMATCH,
                "artifacts/model.bin"
        );
    }

    private static ShadowRunCreationPlan.SideEffectPolicy policy() {
        return new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, true);
    }

    private static void assertBlocked(ReleaseToShadowAdmissionRequest request, ReasonCode reason) {
        ReleaseToShadowAdmissionDecision result = SERVICE.admit(request);

        assertEquals(Decision.BLOCKED, result.decision());
        assertTrue(result.reasonCodes().contains(reason), result.reasonCodes().toString());
        assertNull(result.creationPlan());
        assertSafetyBoundary(result);
    }

    private static void assertSafetyBoundary(ReleaseToShadowAdmissionDecision result) {
        assertFalse(result.shadowRunCreated());
        assertFalse(result.shadowRunStarted());
        assertFalse(result.tradingAuthorized());
        assertFalse(result.orderSubmitted());
    }

    private static List<String> recordFieldNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}

package com.guidinglight.nexusquant.strategyrelease.preparation;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Release-to-Shadow 准入的纯领域回归；不使用 repository、Clock、文件或网络。 */
class StrategyReleaseToShadowAdmissionPrototypeTest {

    private static final StrategyReleaseToShadowAdmissionServicePrototype SERVICE =
            new StrategyReleaseToShadowAdmissionServicePrototype();
    private static final String PUBLISH_ID = "publish-release-001";
    private static final String STRATEGY_VERSION_ID = "strategy-version-001";
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EVALUATION_ID = "evaluation-001";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant WINDOW_START = Instant.parse("2026-07-20T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-07-21T00:00:00Z");

    @Test
    void shouldAdmitCompleteReleaseBoundPublishedVerifiedAndApprovedInput() {
        ShadowRunAdmissionPrototype result = admit(
                release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, StrategyReleaseToShadowAdmissionServicePrototype.SUPPORTED_MANIFEST_SCHEMA),
                verified(DIGEST),
                StrategyValidationDecision.APPROVED,
                binding(PUBLISH_ID, DIGEST),
                request("action-001", "trace-001", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID,
                        WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy())
        );

        assertEquals(ShadowRunAdmissionStatus.ADMITTED, result.status());
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.unknowns().isEmpty());
        assertTrue(result.warnings().contains(ShadowRunAdmissionFindingCode.ADMISSION_NOT_TRADING_AUTHORIZATION));
        assertSafetyBoundary(result);
        assertPlanComplete(result.creationPlan(), "trace-001");
    }

    @Test
    void shouldBlockEveryNonPublishedReleaseState() {
        for (StrategyReleaseState state : EnumSet.of(
                StrategyReleaseState.DRAFT,
                StrategyReleaseState.CANDIDATE,
                StrategyReleaseState.VERIFIED,
                StrategyReleaseState.REJECTED,
                StrategyReleaseState.RETIRED
        )) {
            ShadowRunAdmissionPrototype result = admit(release(state, PUBLISH_ID, DIGEST, schema()));

            assertBlocked(result, ShadowRunAdmissionFindingCode.RELEASE_NOT_PUBLISHED);
        }
    }

    @Test
    void shouldBlockBothLegacyBindingModes() {
        ShadowRunAdmissionPrototype unbound = admit(
                release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, schema()),
                verified(DIGEST),
                StrategyValidationDecision.APPROVED,
                ShadowRunReleaseBindingPrototype.legacyUnbound(),
                request()
        );
        ShadowRunAdmissionPrototype publishOnly = admit(
                release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, schema()),
                verified(DIGEST),
                StrategyValidationDecision.APPROVED,
                ShadowRunReleaseBindingPrototype.legacyPublishOnly(PUBLISH_ID),
                request()
        );

        assertBlocked(unbound, ShadowRunAdmissionFindingCode.RELEASE_BINDING_NOT_COMPLETE);
        assertBlocked(publishOnly, ShadowRunAdmissionFindingCode.RELEASE_BINDING_NOT_COMPLETE);
    }

    @Test
    void shouldFailClosedForPublishAnchorAndArtifactVerificationFailures() {
        StrategyReleaseAggregatePrototype aliasMismatchRelease = new StrategyReleaseAggregatePrototype(
                "legacy-release-id-must-not-be-independent",
                PUBLISH_ID,
                STRATEGY_VERSION_ID,
                DATASET_ID.toString(),
                EVALUATION_ID,
                schema(),
                DIGEST,
                StrategyReleaseState.PUBLISHED,
                3,
                WINDOW_START,
                WINDOW_START,
                null,
                null,
                null
        );
        ShadowRunAdmissionPrototype anchorMismatch = admit(
                release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, schema()),
                verified(DIGEST),
                StrategyValidationDecision.APPROVED,
                binding("publish-release-other", DIGEST),
                request()
        );
        ShadowRunAdmissionPrototype unknown = admit(
                release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, schema()),
                verification(TrustedRootArtifactVerifierPrototype.Status.UNKNOWN, null, 0),
                StrategyValidationDecision.APPROVED,
                binding(PUBLISH_ID, DIGEST),
                request()
        );
        ShadowRunAdmissionPrototype rejected = admit(
                release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, schema()),
                verification(TrustedRootArtifactVerifierPrototype.Status.REJECTED, null, 0),
                StrategyValidationDecision.APPROVED,
                binding(PUBLISH_ID, DIGEST),
                request()
        );
        ShadowRunAdmissionPrototype digestMismatch = admit(
                release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, schema()),
                verified("b".repeat(64)),
                StrategyValidationDecision.APPROVED,
                binding(PUBLISH_ID, DIGEST),
                request()
        );

        assertBlocked(admit(aliasMismatchRelease), ShadowRunAdmissionFindingCode.PUBLISH_ANCHOR_MISMATCH);
        assertBlocked(anchorMismatch, ShadowRunAdmissionFindingCode.PUBLISH_ANCHOR_MISMATCH);
        assertUnknown(unknown, ShadowRunAdmissionFindingCode.ARTIFACT_VERIFICATION_UNKNOWN);
        assertBlocked(rejected, ShadowRunAdmissionFindingCode.ARTIFACT_VERIFICATION_REJECTED);
        assertBlocked(digestMismatch, ShadowRunAdmissionFindingCode.ARTIFACT_DIGEST_MISMATCH);
    }

    @Test
    void shouldUseExistingValidationDecisionSemanticsWithoutNewAliases() {
        for (StrategyValidationDecision decision : List.of(
                StrategyValidationDecision.REJECTED,
                StrategyValidationDecision.NEEDS_REVIEW,
                StrategyValidationDecision.BLOCKED
        )) {
            assertBlocked(admit(defaultRelease(), verified(DIGEST), decision, defaultBinding(), request()),
                    ShadowRunAdmissionFindingCode.VALIDATION_NOT_APPROVED);
        }
        assertUnknown(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.NO_EVIDENCE, defaultBinding(), request()),
                ShadowRunAdmissionFindingCode.VALIDATION_EVIDENCE_MISSING);
        assertUnknown(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.STALE_EVIDENCE, defaultBinding(), request()),
                ShadowRunAdmissionFindingCode.VALIDATION_EVIDENCE_STALE);
    }

    @Test
    void shouldBlockEveryMismatchedReleaseAnchor() {
        assertBlocked(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(),
                        request("action", "trace", "publish-other", STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID,
                                WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy())),
                ShadowRunAdmissionFindingCode.PUBLISH_ANCHOR_MISMATCH);
        assertBlocked(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(),
                        request("action", "trace", PUBLISH_ID, "strategy-other", DATASET_ID, EVALUATION_ID,
                                WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy())),
                ShadowRunAdmissionFindingCode.STRATEGY_VERSION_MISMATCH);
        assertBlocked(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(),
                        request("action", "trace", PUBLISH_ID, STRATEGY_VERSION_ID,
                                UUID.fromString("22222222-2222-2222-2222-222222222222"), EVALUATION_ID,
                                WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy())),
                ShadowRunAdmissionFindingCode.DATASET_MISMATCH);
        assertBlocked(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(),
                        request("action", "trace", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID, "evaluation-other",
                                WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy())),
                ShadowRunAdmissionFindingCode.EVALUATION_MISMATCH);
    }

    @Test
    void shouldBlockUnsupportedSchemaWindowBoundaryAndEverySideEffectViolation() {
        assertBlocked(admit(release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, "strategy-release-manifest.v2")),
                ShadowRunAdmissionFindingCode.SCHEMA_VERSION_UNSUPPORTED);
        assertBlocked(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(),
                        request("action", "trace", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID,
                                WINDOW_START, WINDOW_START, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy())),
                ShadowRunAdmissionFindingCode.INVALID_SHADOW_WINDOW);
        assertBlocked(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(),
                        request("action", "trace", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID,
                                WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.REPLAY_ONLY, policy())),
                ShadowRunAdmissionFindingCode.AUTHORIZATION_BOUNDARY_INVALID);

        for (ShadowRunSideEffectPolicyPrototype invalidPolicy : List.of(
                new ShadowRunSideEffectPolicyPrototype(false, true, true, true, true, true),
                new ShadowRunSideEffectPolicyPrototype(true, false, true, true, true, true),
                new ShadowRunSideEffectPolicyPrototype(true, true, false, true, true, true),
                new ShadowRunSideEffectPolicyPrototype(true, true, true, false, true, true),
                new ShadowRunSideEffectPolicyPrototype(true, true, true, true, false, true),
                new ShadowRunSideEffectPolicyPrototype(true, true, true, true, true, false)
        )) {
            assertBlocked(admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(),
                            request("action", "trace", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID,
                                    WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, invalidPolicy)),
                    ShadowRunAdmissionFindingCode.SIDE_EFFECT_POLICY_VIOLATION);
        }
    }

    @Test
    void shouldReturnUnknownForMissingFactsAndNeverPlanForNonAdmittedResults() {
        ShadowRunAdmissionPrototype missingRelease = admit(null, verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(), request());
        ShadowRunAdmissionPrototype missingRequest = admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(), null);

        assertUnknown(missingRelease, ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
        assertUnknown(missingRequest, ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
        assertNull(missingRelease.creationPlan());
        assertNull(missingRequest.creationPlan());
    }

    @Test
    void shouldGenerateStableKeyWithoutActionOrTraceAndMapItToExistingIdempotencyColumn() {
        ShadowRunAdmissionPrototype first = admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED,
                defaultBinding(), request("action-first", "trace-first", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID,
                        EVALUATION_ID, WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy()));
        ShadowRunAdmissionPrototype sameInput = admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED,
                defaultBinding(), request("action-first", "trace-first", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID,
                        EVALUATION_ID, WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy()));
        ShadowRunAdmissionPrototype replay = admit(defaultRelease(), verified(DIGEST), StrategyValidationDecision.APPROVED,
                defaultBinding(), request("action-second", "trace-second", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID,
                        EVALUATION_ID, WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                        new ShadowRunSideEffectPolicyPrototype(true, true, true, true, true, true)));

        assertEquals(first.creationPlan(), sameInput.creationPlan());
        assertEquals(first.creationPlan().shadowRunIdempotencyKey(), replay.creationPlan().shadowRunIdempotencyKey());
        assertNotEquals(first.creationPlan().traceId(), replay.creationPlan().traceId());
        assertEquals(64, first.creationPlan().shadowRunIdempotencyKey().length());
        assertTrue(first.creationPlan().shadowRunIdempotencyKey().length() <= 160,
                "key must fit existing shadow_runs.idempotency_key");
    }

    @Test
    void shouldChangeDeterministicKeyForEveryBusinessAnchor() {
        ShadowRunIdempotencyMaterialPrototype base = material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(),
                EVALUATION_ID, WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema());
        String baseKey = StrategyReleaseToShadowAdmissionServicePrototype.deterministicIdempotencyKey(base);

        List<ShadowRunIdempotencyMaterialPrototype> variations = List.of(
                material("publish-other", DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(), EVALUATION_ID, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema()),
                material(PUBLISH_ID, "b".repeat(64), STRATEGY_VERSION_ID, DATASET_ID.toString(), EVALUATION_ID, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema()),
                material(PUBLISH_ID, DIGEST, "strategy-other", DATASET_ID.toString(), EVALUATION_ID, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema()),
                material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, "22222222-2222-2222-2222-222222222222", EVALUATION_ID, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema()),
                material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(), "evaluation-other", WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema()),
                material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(), EVALUATION_ID, WINDOW_START.plusSeconds(1), WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema()),
                material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(), EVALUATION_ID, WINDOW_START, WINDOW_END.plusSeconds(1),
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), schema()),
                material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(), EVALUATION_ID, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.REVIEW_ONLY, policy(), schema()),
                material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(), EVALUATION_ID, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                        new ShadowRunSideEffectPolicyPrototype(true, true, true, true, true, false), schema()),
                material(PUBLISH_ID, DIGEST, STRATEGY_VERSION_ID, DATASET_ID.toString(), EVALUATION_ID, WINDOW_START, WINDOW_END,
                        ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy(), "strategy-release-manifest.v2")
        );

        for (ShadowRunIdempotencyMaterialPrototype variation : variations) {
            assertNotEquals(baseKey, StrategyReleaseToShadowAdmissionServicePrototype.deterministicIdempotencyKey(variation));
        }
    }

    @Test
    void shouldKeepResultAndPlanFreeOfSensitiveOrIndependentReleaseFields() {
        List<String> resultFields = recordFieldNames(ShadowRunAdmissionPrototype.class);
        List<String> planFields = recordFieldNames(ShadowRunCreationPlanPrototype.class);

        List.of("credential", "account", "balance", "position", "privateEndpoint", "canTrade", "liveReady")
                .forEach(forbidden -> assertFalse(resultFields.contains(forbidden), forbidden));
        List.of("releaseId", "credential", "accountBalance", "position", "realOrderId", "privateEndpoint", "artifactContent", "absolutePath")
                .forEach(forbidden -> assertFalse(planFields.contains(forbidden), forbidden));
        assertTrue(Arrays.stream(StrategyReleaseToShadowAdmissionServicePrototype.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().contains("Repository")));
    }

    private ShadowRunAdmissionPrototype admit(
            StrategyReleaseAggregatePrototype release,
            ArtifactVerificationResultPrototype verification,
            StrategyValidationDecision validationDecision,
            ShadowRunReleaseBindingPrototype binding,
            RequestedShadowBindingPrototype request
    ) {
        return SERVICE.admit(release, verification, validationDecision, binding, request);
    }

    private ShadowRunAdmissionPrototype admit(StrategyReleaseAggregatePrototype release) {
        return admit(release, verified(DIGEST), StrategyValidationDecision.APPROVED, defaultBinding(), request());
    }

    private static StrategyReleaseAggregatePrototype defaultRelease() {
        return release(StrategyReleaseState.PUBLISHED, PUBLISH_ID, DIGEST, schema());
    }

    private static StrategyReleaseAggregatePrototype release(
            StrategyReleaseState state,
            String publishId,
            String digest,
            String manifestSchemaVersion
    ) {
        return new StrategyReleaseAggregatePrototype(
                publishId,
                publishId,
                STRATEGY_VERSION_ID,
                DATASET_ID.toString(),
                EVALUATION_ID,
                manifestSchemaVersion,
                digest,
                state,
                3,
                WINDOW_START,
                WINDOW_START,
                null,
                null,
                null
        );
    }

    private static ShadowRunReleaseBindingPrototype defaultBinding() {
        return binding(PUBLISH_ID, DIGEST);
    }

    private static ShadowRunReleaseBindingPrototype binding(String publishId, String digest) {
        return ShadowRunReleaseBindingPrototype.releaseBound(publishId, digest);
    }

    private static ArtifactVerificationResultPrototype verified(String digest) {
        return verification(TrustedRootArtifactVerifierPrototype.Status.VERIFIED, digest, 64);
    }

    private static ArtifactVerificationResultPrototype verification(
            TrustedRootArtifactVerifierPrototype.Status status,
            String digest,
            long size
    ) {
        return new ArtifactVerificationResultPrototype(status, digest, size, List.of());
    }

    private static RequestedShadowBindingPrototype request() {
        return request("action-001", "trace-001", PUBLISH_ID, STRATEGY_VERSION_ID, DATASET_ID, EVALUATION_ID,
                WINDOW_START, WINDOW_END, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, policy());
    }

    private static RequestedShadowBindingPrototype request(
            String actionId,
            String traceId,
            String publishId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            Instant windowStart,
            Instant windowEnd,
            ShadowRunAuthorizationBoundary authorizationBoundary,
            ShadowRunSideEffectPolicyPrototype sideEffectPolicy
    ) {
        return new RequestedShadowBindingPrototype(
                actionId,
                traceId,
                publishId,
                strategyVersionId,
                datasetId,
                evaluationId,
                windowStart,
                windowEnd,
                authorizationBoundary,
                sideEffectPolicy
        );
    }

    private static ShadowRunSideEffectPolicyPrototype policy() {
        return new ShadowRunSideEffectPolicyPrototype(true, true, true, true, true, true);
    }

    private static ShadowRunIdempotencyMaterialPrototype material(
            String publishId,
            String digest,
            String strategyVersionId,
            String datasetId,
            String evaluationId,
            Instant windowStart,
            Instant windowEnd,
            ShadowRunAuthorizationBoundary boundary,
            ShadowRunSideEffectPolicyPrototype sideEffectPolicy,
            String schema
    ) {
        return new ShadowRunIdempotencyMaterialPrototype(
                publishId,
                digest,
                strategyVersionId,
                datasetId,
                evaluationId,
                windowStart,
                windowEnd,
                boundary,
                sideEffectPolicy,
                schema
        );
    }

    private static String schema() {
        return StrategyReleaseToShadowAdmissionServicePrototype.SUPPORTED_MANIFEST_SCHEMA;
    }

    private static void assertBlocked(ShadowRunAdmissionPrototype result, ShadowRunAdmissionFindingCode finding) {
        assertEquals(ShadowRunAdmissionStatus.BLOCKED, result.status());
        assertTrue(result.blockers().contains(finding), result.blockers().toString());
        assertNull(result.creationPlan());
        assertSafetyBoundary(result);
    }

    private static void assertUnknown(ShadowRunAdmissionPrototype result, ShadowRunAdmissionFindingCode finding) {
        assertEquals(ShadowRunAdmissionStatus.UNKNOWN, result.status());
        assertTrue(result.unknowns().contains(finding), result.unknowns().toString());
        assertNull(result.creationPlan());
        assertSafetyBoundary(result);
    }

    private static void assertSafetyBoundary(ShadowRunAdmissionPrototype result) {
        assertTrue(result.diagnosticOnly());
        assertTrue(result.noSideEffect());
        assertTrue(result.notTradingAuthorization());
        assertTrue(result.liveDisabled());
        assertFalse(result.shadowRunCreated());
        assertFalse(result.shadowRunStarted());
        assertFalse(result.orderSubmitted());
    }

    private static void assertPlanComplete(ShadowRunCreationPlanPrototype plan, String expectedTraceId) {
        assertEquals(PUBLISH_ID, plan.publishRecordId());
        assertEquals(DIGEST, plan.artifactDigest());
        assertEquals(STRATEGY_VERSION_ID, plan.strategyVersionId());
        assertEquals(DATASET_ID, plan.datasetId());
        assertEquals(EVALUATION_ID, plan.evaluationId());
        assertEquals(WINDOW_START, plan.windowStart());
        assertEquals(WINDOW_END, plan.windowEnd());
        assertEquals(ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY, plan.authorizationBoundary());
        assertEquals(expectedTraceId, plan.traceId());
        assertTrue(plan.sideEffectPolicy().allNoSideEffects());
        assertTrue(plan.diagnosticOnly());
        assertTrue(plan.noSideEffect());
        assertTrue(plan.notTradingAuthorization());
        assertTrue(plan.liveDisabled());
    }

    private static List<String> recordFieldNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}

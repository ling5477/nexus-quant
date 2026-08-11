package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** POST materialization 必须重评估 admission、绑定 command identity 并保持 BLOCKED 零写入。 */
class StrategyReleaseShadowRunMaterializationServiceTest {

    private static final String PUBLISH_ID = "publish-gatex5-001";
    private static final String TRACE_ID = "trace-gatex5-001";

    @Test
    void shouldReevaluateAdmissionAndMaterializeBoundPlan() {
        ShadowRunCreationPlan basePlan = plan();
        AtomicReference<ShadowRunCreationPlan> writtenPlan = new AtomicReference<>();
        StrategyReleaseShadowRunMaterializationService service = new StrategyReleaseShadowRunMaterializationService(
                (publishId, traceId) -> Optional.of(eligible(basePlan)),
                (boundPlan, actorId) -> {
                    writtenPlan.set(boundPlan);
                    return result(false);
                }
        );

        ShadowRunMaterializationResult actual = service.materialize(
                PUBLISH_ID,
                "operator-action-001",
                new ShadowRunMaterializationActor(41L, List.of("OPERATOR")),
                TRACE_ID
        ).orElseThrow();

        assertEquals(PUBLISH_ID, writtenPlan.get().publishRecordId());
        assertNotEquals(basePlan.shadowRunIdempotencyKey(), writtenPlan.get().shadowRunIdempotencyKey());
        assertEquals(ShadowRunStatus.CREATED, actual.status());
    }

    @Test
    void shouldKeepRetryStableAndAllowNewCommandIdentity() {
        ShadowRunCreationPlan basePlan = plan();

        String first = basePlan.bindMaterializationCommand("operator-action-001").shadowRunIdempotencyKey();
        String retry = basePlan.bindMaterializationCommand("operator-action-001").shadowRunIdempotencyKey();
        String rerun = basePlan.bindMaterializationCommand("operator-action-002").shadowRunIdempotencyKey();

        assertEquals(first, retry);
        assertNotEquals(first, rerun);
    }

    @Test
    void shouldRejectBlockedAdmissionWithoutWrite() {
        AtomicInteger writes = new AtomicInteger();
        StrategyReleaseShadowRunMaterializationService service = new StrategyReleaseShadowRunMaterializationService(
                (publishId, traceId) -> Optional.of(blocked()),
                (boundPlan, actorId) -> {
                    writes.incrementAndGet();
                    return result(false);
                }
        );

        assertThrows(ShadowRunMaterializationRejectedException.class, () -> service.materialize(
                PUBLISH_ID,
                "operator-action-001",
                new ShadowRunMaterializationActor(41L, List.of("ADMIN")),
                TRACE_ID
        ));
        assertEquals(0, writes.get());
    }

    @Test
    void shouldReturnMissingPublishWithoutWrite() {
        AtomicInteger writes = new AtomicInteger();
        StrategyReleaseShadowRunMaterializationService service = new StrategyReleaseShadowRunMaterializationService(
                (publishId, traceId) -> Optional.empty(),
                (boundPlan, actorId) -> {
                    writes.incrementAndGet();
                    return result(false);
                }
        );

        assertEquals(Optional.empty(), service.materialize(
                PUBLISH_ID,
                "operator-action-001",
                new ShadowRunMaterializationActor(41L, List.of("OPERATOR")),
                TRACE_ID
        ));
        assertEquals(0, writes.get());
    }

    @Test
    void shouldRejectViewerBeforeAdmissionOrWrite() {
        AtomicInteger evaluations = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        StrategyReleaseShadowRunMaterializationService service = new StrategyReleaseShadowRunMaterializationService(
                (publishId, traceId) -> {
                    evaluations.incrementAndGet();
                    return Optional.of(eligible(plan()));
                },
                (boundPlan, actorId) -> {
                    writes.incrementAndGet();
                    return result(false);
                }
        );

        assertThrows(ShadowRunMaterializationAuthorizationException.class, () -> service.materialize(
                PUBLISH_ID,
                "operator-action-001",
                new ShadowRunMaterializationActor(41L, List.of("VIEWER")),
                TRACE_ID
        ));
        assertEquals(0, evaluations.get());
        assertEquals(0, writes.get());
    }

    @Test
    void shouldRejectMissingCommandIdentityBeforeAdmissionOrWrite() {
        AtomicInteger evaluations = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        StrategyReleaseShadowRunMaterializationService service = new StrategyReleaseShadowRunMaterializationService(
                (publishId, traceId) -> {
                    evaluations.incrementAndGet();
                    return Optional.of(eligible(plan()));
                },
                (boundPlan, actorId) -> {
                    writes.incrementAndGet();
                    return result(false);
                }
        );

        assertThrows(IllegalArgumentException.class, () -> service.materialize(
                PUBLISH_ID,
                null,
                new ShadowRunMaterializationActor(41L, List.of("OPERATOR")),
                TRACE_ID
        ));
        assertEquals(0, evaluations.get());
        assertEquals(0, writes.get());
    }

    private static StrategyReleaseAdmissionPreviewService.AdmissionEvaluation eligible(
            ShadowRunCreationPlan plan
    ) {
        ReleaseToShadowAdmissionDecision decision = new ReleaseToShadowAdmissionDecision(
                ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE,
                List.of(ReleaseToShadowAdmissionDecision.ReasonCode.ELIGIBLE_FOR_CREATION_PLAN_ONLY),
                plan.releaseAnchorId(),
                plan.artifactDigest(),
                plan.strategyVersionId(),
                plan.datasetId(),
                plan.evaluationId(),
                plan.sideEffectPolicy(),
                plan,
                false,
                false,
                false,
                false
        );
        return new StrategyReleaseAdmissionPreviewService.AdmissionEvaluation(preview(), decision);
    }

    private static StrategyReleaseAdmissionPreviewService.AdmissionEvaluation blocked() {
        ReleaseToShadowAdmissionDecision decision = new ReleaseToShadowAdmissionDecision(
                ReleaseToShadowAdmissionDecision.Decision.BLOCKED,
                List.of(ReleaseToShadowAdmissionDecision.ReasonCode.VALIDATION_NOT_APPROVED),
                PUBLISH_ID,
                "a".repeat(64),
                "strategy-version-001",
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "evaluation-001",
                null,
                null,
                false,
                false,
                false,
                false
        );
        return new StrategyReleaseAdmissionPreviewService.AdmissionEvaluation(preview(), decision);
    }

    private static StrategyReleaseAdmissionPreview preview() {
        return new StrategyReleaseAdmissionPreview(
                PUBLISH_ID,
                PUBLISH_ID,
                "strategy-version-001",
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "evaluation-001",
                ShadowRunReleaseBindingMode.RELEASE_BOUND,
                com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus.VERIFIED,
                com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.Status.VERIFIED,
                com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision.APPROVED,
                ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE,
                List.of("ELIGIBLE_FOR_CREATION_PLAN_ONLY"),
                "a".repeat(64)
        );
    }

    static ShadowRunCreationPlan plan() {
        return new ShadowRunCreationPlan(
                PUBLISH_ID,
                PUBLISH_ID,
                "a".repeat(64),
                "strategy-version-001",
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "evaluation-001",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                "dataset:11111111-1111-4111-8111-111111111111",
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, true),
                "strategy-release-manifest.v1",
                "publish:" + PUBLISH_ID,
                TRACE_ID,
                "b".repeat(64)
        );
    }

    private static ShadowRunMaterializationResult result(boolean replay) {
        return new ShadowRunMaterializationResult(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                PUBLISH_ID,
                "a".repeat(64),
                ShadowRunReleaseBindingMode.RELEASE_BOUND,
                ShadowRunStatus.CREATED,
                Instant.parse("2026-08-11T00:00:00Z"),
                replay
        );
    }
}

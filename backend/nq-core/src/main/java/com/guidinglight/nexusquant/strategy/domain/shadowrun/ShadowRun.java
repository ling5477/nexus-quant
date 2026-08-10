package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shadow Run 主事实。
 *
 * <p>该 record 只表达一次本地影子运行的可审计事实和无副作用边界。幂等由
 * {@code idempotencyKey} 承担，乐观锁由 {@code version} 承担；所有 JSONB 字段在
 * 构造时做敏感字段禁入检查，避免 credential、private payload、真实账户或订单字段进入本地事实。
 */
public record ShadowRun(
        UUID id,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String artifactDigest,
        String paperRunId,
        ShadowRunStatus status,
        Instant windowStart,
        Instant windowEnd,
        JsonNode sideEffectPolicy,
        boolean noOrderSubmission,
        boolean noCredentialAccess,
        boolean noPrivateEndpoint,
        boolean noLedgerMutation,
        boolean noAccountMutation,
        boolean noExternalPrivateIo,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        String requestId,
        String idempotencyKey,
        String traceId,
        JsonNode blockers,
        JsonNode warnings,
        JsonNode nextSteps,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant stoppedAt,
        Instant completedAt
) {

    public ShadowRun {
        Objects.requireNonNull(id, "id must not be null");
        requireText(strategyVersionId, "strategyVersionId");
        Objects.requireNonNull(datasetId, "datasetId must not be null");
        ShadowRunReleaseBindingMode.derive(publishId, artifactDigest);
        Objects.requireNonNull(status, "status must not be null");
        ShadowRunJsonRules.validateWindow(windowStart, windowEnd);
        ShadowRunSensitiveDataGuard.validateJson("sideEffectPolicy", sideEffectPolicy);
        ShadowRunJsonRules.requireObject(sideEffectPolicy, "sideEffectPolicy");
        requireNoSideEffects(
                noOrderSubmission,
                noCredentialAccess,
                noPrivateEndpoint,
                noLedgerMutation,
                noAccountMutation,
                noExternalPrivateIo
        );
        Objects.requireNonNull(authorizationBoundary, "authorizationBoundary must not be null");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(traceId, "traceId");
        ShadowRunSensitiveDataGuard.validateJson("blockers", blockers);
        ShadowRunSensitiveDataGuard.validateJson("warnings", warnings);
        ShadowRunSensitiveDataGuard.validateJson("nextSteps", nextSteps);
        ShadowRunJsonRules.requireArray(blockers, "blockers");
        ShadowRunJsonRules.requireArray(warnings, "warnings");
        ShadowRunJsonRules.requireArray(nextSteps, "nextSteps");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * 兼容 GateX-2 之前的创建路径；旧路径没有已验证 artifact digest，因此只能创建
     * {@link ShadowRunReleaseBindingMode#LEGACY_UNBOUND} 或
     * {@link ShadowRunReleaseBindingMode#LEGACY_PUBLISH_ONLY} 事实，不能推测为 release-bound。
     */
    public ShadowRun(
            UUID id,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            String publishId,
            String paperRunId,
            ShadowRunStatus status,
            Instant windowStart,
            Instant windowEnd,
            JsonNode sideEffectPolicy,
            boolean noOrderSubmission,
            boolean noCredentialAccess,
            boolean noPrivateEndpoint,
            boolean noLedgerMutation,
            boolean noAccountMutation,
            boolean noExternalPrivateIo,
            ShadowRunAuthorizationBoundary authorizationBoundary,
            String requestId,
            String idempotencyKey,
            String traceId,
            JsonNode blockers,
            JsonNode warnings,
            JsonNode nextSteps,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant stoppedAt,
            Instant completedAt
    ) {
        this(
                id,
                strategyVersionId,
                datasetId,
                evaluationId,
                publishId,
                null,
                paperRunId,
                status,
                windowStart,
                windowEnd,
                sideEffectPolicy,
                noOrderSubmission,
                noCredentialAccess,
                noPrivateEndpoint,
                noLedgerMutation,
                noAccountMutation,
                noExternalPrivateIo,
                authorizationBoundary,
                requestId,
                idempotencyKey,
                traceId,
                blockers,
                warnings,
                nextSteps,
                version,
                createdAt,
                updatedAt,
                startedAt,
                stoppedAt,
                completedAt
        );
    }

    /**
     * 返回由不可变 provenance 字段派生的绑定模式；该值不单独持久化。
     */
    public ShadowRunReleaseBindingMode releaseBindingMode() {
        return ShadowRunReleaseBindingMode.derive(publishId, artifactDigest);
    }

    private static void requireNoSideEffects(
            boolean noOrderSubmission,
            boolean noCredentialAccess,
            boolean noPrivateEndpoint,
            boolean noLedgerMutation,
            boolean noAccountMutation,
            boolean noExternalPrivateIo
    ) {
        if (!noOrderSubmission
                || !noCredentialAccess
                || !noPrivateEndpoint
                || !noLedgerMutation
                || !noAccountMutation
                || !noExternalPrivateIo) {
            throw new IllegalArgumentException("shadow run requires all no-side-effect flags to be true");
        }
    }

    static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}

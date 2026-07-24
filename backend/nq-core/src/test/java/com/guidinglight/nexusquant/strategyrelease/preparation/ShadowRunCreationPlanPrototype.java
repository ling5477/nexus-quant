package com.guidinglight.nexusquant.strategyrelease.preparation;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 未来 GateX 创建 release-bound Shadow Run 所需的纯数据计划。
 *
 * <p>该计划不是 Shadow Run 主事实，不会写数据库、调用 runner 或产生交易授权。它只能在
 * {@link ShadowRunAdmissionStatus#ADMITTED}（已准入）的 test-only admission 结果中出现。
 */
record ShadowRunCreationPlanPrototype(
        String publishRecordId,
        String artifactDigest,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        Instant windowStart,
        Instant windowEnd,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        ShadowRunSideEffectPolicyPrototype sideEffectPolicy,
        String shadowRunIdempotencyKey,
        String traceId
) {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    ShadowRunCreationPlanPrototype {
        publishRecordId = requireText(publishRecordId, "publishRecordId");
        artifactDigest = requireText(artifactDigest, "artifactDigest");
        strategyVersionId = requireText(strategyVersionId, "strategyVersionId");
        Objects.requireNonNull(datasetId, "datasetId must not be null");
        evaluationId = requireText(evaluationId, "evaluationId");
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        Objects.requireNonNull(authorizationBoundary, "authorizationBoundary must not be null");
        Objects.requireNonNull(sideEffectPolicy, "sideEffectPolicy must not be null");
        shadowRunIdempotencyKey = requireText(shadowRunIdempotencyKey, "shadowRunIdempotencyKey");
        traceId = requireText(traceId, "traceId");

        if (!SHA_256.matcher(artifactDigest).matches()) {
            throw new IllegalArgumentException("artifactDigest must be a lowercase SHA-256");
        }
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be after windowStart");
        }
        if (authorizationBoundary != ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY
                && authorizationBoundary != ShadowRunAuthorizationBoundary.REVIEW_ONLY) {
            throw new IllegalArgumentException("creation plan authorization boundary is not admission eligible");
        }
        if (!sideEffectPolicy.allNoSideEffects()) {
            throw new IllegalArgumentException("creation plan requires all no-side-effect flags");
        }
        if (!SHA_256.matcher(shadowRunIdempotencyKey).matches()) {
            throw new IllegalArgumentException("shadowRunIdempotencyKey must be a lowercase SHA-256");
        }
        if (traceId.length() > 128) {
            throw new IllegalArgumentException("traceId must fit shadow_runs.trace_id");
        }
    }

    boolean diagnosticOnly() {
        return true;
    }

    boolean noSideEffect() {
        return true;
    }

    boolean notTradingAuthorization() {
        return true;
    }

    boolean liveDisabled() {
        return true;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

/** 复用现有 Shadow Run 六项无副作用事实；false 值必须由 admission 明确拒绝。 */
record ShadowRunSideEffectPolicyPrototype(
        boolean noOrderSubmission,
        boolean noCredentialAccess,
        boolean noPrivateEndpoint,
        boolean noLedgerMutation,
        boolean noAccountMutation,
        boolean noExternalPrivateIo
) {

    boolean allNoSideEffects() {
        return noOrderSubmission
                && noCredentialAccess
                && noPrivateEndpoint
                && noLedgerMutation
                && noAccountMutation
                && noExternalPrivateIo;
    }
}

package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 未来创建 release-bound Shadow Run 所需的 immutable 纯数据计划。
 *
 * <p>该计划不是 {@code ShadowRun}，不会持久化、启动 runner 或产生交易授权。artifact digest 的格式和
 * release binding 统一复用 {@link ShadowRunReleaseBindingMode}，不维护第二套校验算法。
 */
public record ShadowRunCreationPlan(
        String releaseAnchorId,
        String publishRecordId,
        String artifactDigest,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        Instant windowStart,
        Instant windowEnd,
        String inputReference,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        SideEffectPolicy sideEffectPolicy,
        String manifestSchemaVersion,
        String provenanceReference,
        String traceId,
        String shadowRunIdempotencyKey
) {

    public ShadowRunCreationPlan {
        releaseAnchorId = requireText(releaseAnchorId, "releaseAnchorId");
        publishRecordId = requireText(publishRecordId, "publishRecordId");
        artifactDigest = requireText(artifactDigest, "artifactDigest");
        strategyVersionId = requireText(strategyVersionId, "strategyVersionId");
        Objects.requireNonNull(datasetId, "datasetId must not be null");
        evaluationId = requireText(evaluationId, "evaluationId");
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        inputReference = requireText(inputReference, "inputReference");
        Objects.requireNonNull(authorizationBoundary, "authorizationBoundary must not be null");
        Objects.requireNonNull(sideEffectPolicy, "sideEffectPolicy must not be null");
        manifestSchemaVersion = requireText(manifestSchemaVersion, "manifestSchemaVersion");
        provenanceReference = requireText(provenanceReference, "provenanceReference");
        traceId = requireText(traceId, "traceId");
        shadowRunIdempotencyKey = requireText(shadowRunIdempotencyKey, "shadowRunIdempotencyKey");

        if (!releaseAnchorId.equals(publishRecordId)) {
            throw new IllegalArgumentException("releaseAnchorId must equal publishRecordId");
        }
        if (ShadowRunReleaseBindingMode.derive(publishRecordId, artifactDigest)
                != ShadowRunReleaseBindingMode.RELEASE_BOUND) {
            throw new IllegalArgumentException("creation plan requires RELEASE_BOUND provenance");
        }
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be after windowStart");
        }
        if (authorizationBoundary != ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY
                && authorizationBoundary != ShadowRunAuthorizationBoundary.REVIEW_ONLY) {
            throw new IllegalArgumentException("creation plan authorization boundary is not eligible");
        }
        if (!sideEffectPolicy.allNoSideEffects()) {
            throw new IllegalArgumentException("creation plan requires all no-side-effect flags");
        }
        if (!StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION.equals(manifestSchemaVersion)) {
            throw new IllegalArgumentException("creation plan requires the supported manifest schema");
        }
        if (traceId.length() > 128) {
            throw new IllegalArgumentException("traceId must fit shadow_runs.trace_id");
        }
        if (!isLowercaseSha256(shadowRunIdempotencyKey)) {
            throw new IllegalArgumentException("shadowRunIdempotencyKey must be a lowercase SHA-256");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static boolean isLowercaseSha256(String value) {
        return value.length() == 64
                && value.chars().allMatch(character -> (character >= '0' && character <= '9')
                || (character >= 'a' && character <= 'f'));
    }

    /**
     * 复用 Shadow Run 六项 production no-side-effect 事实；任一 false 都必须阻断 admission。
     */
    public record SideEffectPolicy(
            boolean noOrderSubmission,
            boolean noCredentialAccess,
            boolean noPrivateEndpoint,
            boolean noLedgerMutation,
            boolean noAccountMutation,
            boolean noExternalPrivateIo
    ) {

        public boolean allNoSideEffects() {
            return noOrderSubmission
                    && noCredentialAccess
                    && noPrivateEndpoint
                    && noLedgerMutation
                    && noAccountMutation
                    && noExternalPrivateIo;
        }
    }
}

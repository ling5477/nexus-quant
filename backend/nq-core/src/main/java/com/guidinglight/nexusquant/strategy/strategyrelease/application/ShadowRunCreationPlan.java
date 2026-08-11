package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;

import java.time.Instant;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
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
     * 将 operator 提供的标准 {@code Idempotency-Key} 绑定为一次 materialization command identity。
     *
     * <p>Why：GateX-3 的 base key 只描述 immutable admission facts；加入独立 command identity 后，
     * 相同 command 重放仍命中同一 Shadow Run，而同一 release 的另一次合法人工创建可使用新 identity。
     * 原始 header 不进入数据库，持久化的仍是不可逆 SHA-256 identity。
     *
     * @param commandIdentity 1..128 字符且不含控制字符的标准幂等 header
     * @return 仅替换 materialization idempotency key 的 immutable plan
     */
    public ShadowRunCreationPlan bindMaterializationCommand(String commandIdentity) {
        String normalized = requireText(commandIdentity, "commandIdentity");
        if (normalized.length() > 128 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("commandIdentity must be 1..128 characters without control characters");
        }
        String materializationKey = deterministicSha256(List.of(
                "shadow-run-materialization-command.v1",
                normalized
        ));
        return new ShadowRunCreationPlan(
                releaseAnchorId,
                publishRecordId,
                artifactDigest,
                strategyVersionId,
                datasetId,
                evaluationId,
                windowStart,
                windowEnd,
                inputReference,
                authorizationBoundary,
                sideEffectPolicy,
                manifestSchemaVersion,
                provenanceReference,
                traceId,
                materializationKey
        );
    }

    private static String deterministicSha256(List<String> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : fields) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
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

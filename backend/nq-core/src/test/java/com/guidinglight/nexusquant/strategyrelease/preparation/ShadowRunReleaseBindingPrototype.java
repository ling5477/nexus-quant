package com.guidinglight.nexusquant.strategyrelease.preparation;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PRE-GATEX Shadow Run 发布锚点 test-only 合同。
 *
 * <p>发布锚点固定复用 {@code backtest_publish_records.publish_record_id}，不是独立 release UUID。
 * 本模型只冻结 provenance 规则；不创建 Shadow Run、不访问数据库或文件，也不产生交易授权。
 */
record ShadowRunReleaseBindingPrototype(
        String publishRecordId,
        String artifactDigest,
        ShadowRunReleaseBindingMode bindingMode
) {

    private static final Pattern ARTIFACT_DIGEST = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern WINDOWS_DRIVE_PATH = Pattern.compile("^[A-Za-z]:.*");
    private static final String[] FORBIDDEN_SENSITIVE_TERMS = {
            "credential", "apikey", "api_key", "api-key", "secret", "token", "passphrase", "privatekey",
            "private_key", "private-key", "password"
    };

    ShadowRunReleaseBindingPrototype {
        publishRecordId = trimToNull(publishRecordId);
        artifactDigest = trimToNull(artifactDigest);
        Objects.requireNonNull(bindingMode, "bindingMode must not be null");

        if (publishRecordId != null) {
            validatePublishRecordId(publishRecordId);
        }
        if (artifactDigest != null && !ARTIFACT_DIGEST.matcher(artifactDigest).matches()) {
            throw new IllegalArgumentException("artifactDigest must be a 64-character lowercase SHA-256");
        }

        ShadowRunReleaseBindingMode derivedMode = deriveBindingMode(publishRecordId, artifactDigest);
        if (bindingMode != derivedMode) {
            throw new IllegalArgumentException("bindingMode must match publishRecordId/artifactDigest presence");
        }
    }

    static ShadowRunReleaseBindingPrototype legacyUnbound() {
        return new ShadowRunReleaseBindingPrototype(null, null, ShadowRunReleaseBindingMode.LEGACY_UNBOUND);
    }

    static ShadowRunReleaseBindingPrototype legacyPublishOnly(String publishRecordId) {
        return new ShadowRunReleaseBindingPrototype(
                publishRecordId,
                null,
                ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY
        );
    }

    static ShadowRunReleaseBindingPrototype releaseBound(String publishRecordId, String artifactDigest) {
        return new ShadowRunReleaseBindingPrototype(
                publishRecordId,
                artifactDigest,
                ShadowRunReleaseBindingMode.RELEASE_BOUND
        );
    }

    /**
     * 供未来 production update guard 复用：绑定一旦写入不得原地升级、降级或重绑定。
     */
    static void requireUnchanged(
            ShadowRunReleaseBindingPrototype persisted,
            ShadowRunReleaseBindingPrototype requested
    ) {
        Objects.requireNonNull(persisted, "persisted binding must not be null");
        Objects.requireNonNull(requested, "requested binding must not be null");
        if (!persisted.equals(requested)) {
            throw new IllegalStateException("shadow run publish/artifact binding is immutable");
        }
    }

    boolean eligibleForFutureAdmission() {
        return bindingMode == ShadowRunReleaseBindingMode.RELEASE_BOUND;
    }

    boolean diagnosticOnly() {
        return true;
    }

    boolean notTradingAuthorization() {
        return true;
    }

    boolean liveDisabled() {
        return true;
    }

    private static ShadowRunReleaseBindingMode deriveBindingMode(String publishRecordId, String artifactDigest) {
        if (publishRecordId == null && artifactDigest == null) {
            return ShadowRunReleaseBindingMode.LEGACY_UNBOUND;
        }
        if (publishRecordId != null && artifactDigest == null) {
            return ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY;
        }
        if (publishRecordId != null) {
            return ShadowRunReleaseBindingMode.RELEASE_BOUND;
        }
        throw new IllegalArgumentException("artifactDigest requires publishRecordId");
    }

    private static void validatePublishRecordId(String value) {
        if (value.length() > 128) {
            throw new IllegalArgumentException("publishRecordId must be at most 128 characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("publishRecordId must not contain control characters");
        }
        if (value.contains("/")
                || value.contains("\\")
                || value.contains("..")
                || value.startsWith("~")
                || WINDOWS_DRIVE_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("publishRecordId must not contain path semantics");
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String forbiddenTerm : FORBIDDEN_SENSITIVE_TERMS) {
            if (normalized.contains(forbiddenTerm)) {
                throw new IllegalArgumentException("publishRecordId must not contain credential semantics");
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

/** Shadow Run 发布来源绑定模式；仅用于 test-only provenance 合同。 */
enum ShadowRunReleaseBindingMode {
    LEGACY_UNBOUND,
    LEGACY_PUBLISH_ONLY,
    RELEASE_BOUND
}

/**
 * 未来创建请求中与 provenance 并列的幂等键边界。
 *
 * <p>同一 publish/digest 可以生成多个 Shadow Run；创建去重始终由调用方的
 * {@code idempotencyKey} 承担，而不是由 publish/digest 的全局唯一约束承担。
 */
record ShadowRunCreationIdentityPrototype(
        String idempotencyKey,
        ShadowRunReleaseBindingPrototype releaseBinding
) {

    ShadowRunCreationIdentityPrototype {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (idempotencyKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("idempotencyKey must not contain control characters");
        }
        idempotencyKey = idempotencyKey.trim();
        Objects.requireNonNull(releaseBinding, "releaseBinding must not be null");
    }

    String creationDeduplicationKey() {
        return idempotencyKey;
    }

    boolean diagnosticOnly() {
        return true;
    }
}

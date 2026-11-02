package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import java.util.regex.Pattern;

/**
 * Shadow Run 的不可变 release provenance 绑定模式。
 *
 * <p>该模式只由 {@code publishId + artifactDigest} 派生，不单独持久化，也不表达
 * admission、交易批准或 LIVE readiness。历史行允许无绑定或只有 publish anchor；新 digest
 * 必须是 64 位小写 SHA-256，且必须同时存在 publish anchor。
 */
public enum ShadowRunReleaseBindingMode {
    LEGACY_UNBOUND,
    LEGACY_PUBLISH_ONLY,
    RELEASE_BOUND;

    private static final Pattern LOWERCASE_SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    /**
     * 校验并派生持久化事实对应的绑定模式。
     *
     * @param publishId      可空发布记录锚点
     * @param artifactDigest 可空 artifact SHA-256；非空时必须严格为小写 64 位十六进制
     * @return 仅由两个持久化字段确定的绑定模式
     * @throws IllegalArgumentException digest 非法或缺少 publishId 时抛出
     */
    public static ShadowRunReleaseBindingMode derive(String publishId, String artifactDigest) {
        if (artifactDigest != null && !LOWERCASE_SHA_256.matcher(artifactDigest).matches()) {
            throw new IllegalArgumentException("artifactDigest must be a 64-character lowercase SHA-256");
        }
        if (publishId == null) {
            if (artifactDigest != null) {
                throw new IllegalArgumentException("artifactDigest requires publishId");
            }
            return LEGACY_UNBOUND;
        }
        return artifactDigest == null ? LEGACY_PUBLISH_ONLY : RELEASE_BOUND;
    }
}

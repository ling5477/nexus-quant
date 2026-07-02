package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.time.Instant;
import java.util.Objects;

/**
 * PublicMarketDataOutboundDecision 表示一次 public outbound policy 评估结果。
 *
 * <p>Why: O-1 的核心安全边界是“先决策，再请求”。该 record 只描述类别、是否允许、拒绝分类和
 * 脱敏原因，供 client、测试和后续 Data Quality 映射复用；它不包含 URL query、headers、body、
 * credential 或 provider raw artifact。</p>
 *
 * @param endpointCategory 受检 endpoint 类别
 * @param allowed          是否允许进入 HTTP client
 * @param errorCategory    拒绝时的错误分类；允许时固定为 NONE
 * @param reason           脱敏原因；只允许描述规则，不允许包含 secret 或 raw query
 * @param checkedAt        决策时间
 */
public record PublicMarketDataOutboundDecision(
        PublicMarketDataEndpointCategory endpointCategory,
        boolean allowed,
        PublicMarketDataOutboundErrorCategory errorCategory,
        String reason,
        Instant checkedAt
) {

    public PublicMarketDataOutboundDecision {
        endpointCategory = Objects.requireNonNull(endpointCategory, "endpointCategory must not be null");
        errorCategory = Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        reason = reason == null ? "" : reason;
        if (allowed && errorCategory != PublicMarketDataOutboundErrorCategory.NONE) {
            throw new IllegalArgumentException("allowed decision must use NONE error category");
        }
        if (!allowed && errorCategory == PublicMarketDataOutboundErrorCategory.NONE) {
            throw new IllegalArgumentException("denied decision must carry an error category");
        }
    }

    /**
     * 构造允许决策。
     *
     * @param category  endpoint 类别；必须已经属于 allowlist
     * @param checkedAt 决策时间
     * @return allowed=true 的稳定决策
     */
    public static PublicMarketDataOutboundDecision allow(
            PublicMarketDataEndpointCategory category, Instant checkedAt) {
        return new PublicMarketDataOutboundDecision(
                category,
                true,
                PublicMarketDataOutboundErrorCategory.NONE,
                "public marketdata endpoint category allowed",
                checkedAt);
    }

    /**
     * 构造拒绝决策。
     *
     * @param category  endpoint 类别
     * @param reason    脱敏拒绝原因
     * @param checkedAt 决策时间
     * @return allowed=false 的 fail-closed 决策
     */
    public static PublicMarketDataOutboundDecision deny(
            PublicMarketDataEndpointCategory category, String reason, Instant checkedAt) {
        return new PublicMarketDataOutboundDecision(
                category == null ? PublicMarketDataEndpointCategory.UNKNOWN : category,
                false,
                PublicMarketDataOutboundErrorCategory.DENIED,
                reason,
                checkedAt);
    }
}

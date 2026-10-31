package com.guidinglight.nexusquant.marketdata.domain.instrument;

import java.time.Instant;

/**
 * VenueRuleFreshness 是本地 venue-rule snapshot 的 fail-closed 判定结果。
 *
 * @param source 数据来源
 * @param sourceSchemaVersion NQ parser/schema contract 版本
 * @param observedAt 官方公开响应完成解析后的本地观察时间
 * @param freshUntil 由 observedAt + threshold 与 nextRuleEffectiveAt 的较早者计算
 * @param ruleChecksum canonical venue-rule checksum
 * @param availability facts 是否可用；除 AVAILABLE 外均阻断后续 preview
 * @param freshnessStatus 新鲜度状态
 * @param blockingReason 稳定的阻断原因；FRESH 时为空
 */
public record VenueRuleFreshness(
        String source,
        String sourceSchemaVersion,
        Instant observedAt,
        Instant freshUntil,
        String ruleChecksum,
        Availability availability,
        FreshnessStatus freshnessStatus,
        String blockingReason
) {

    public enum Availability {
        AVAILABLE,
        UNAVAILABLE,
        BLOCKED
    }

    public enum FreshnessStatus {
        FRESH,
        STALE,
        UNKNOWN
    }
}

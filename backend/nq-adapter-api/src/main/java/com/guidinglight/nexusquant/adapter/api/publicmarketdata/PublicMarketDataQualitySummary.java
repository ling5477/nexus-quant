package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.util.Objects;

/**
 * PublicMarketDataQualitySummary 是 O-1 result 到 O-2 Data Quality 的最小桥接模型。
 *
 * <p>Why: 公开行情能成功读取不代表数据可靠，更不代表可以交易。该 summary 把 source health、
 * freshness、gap、source status 和 data origin 分开表达，并固定 tradingAuthorization=false，
 * 防止后续 UI/API 把 marketdata readiness 提升成交易授权。</p>
 *
 * @param sourceHealth         行情源健康状态
 * @param freshness            数据新鲜度
 * @param gapCount             已知缺口数；未知或无缺口为 0
 * @param sourceStatus         source 是否被配置开关禁用
 * @param dataOrigin           数据来源
 * @param fallbackUsed         是否走了 LOCAL_DB / FIXTURE / FAKE_SERVER fallback
 * @param tradingAuthorization 恒为 false；公开行情 readiness 不授权交易
 */
public record PublicMarketDataQualitySummary(
        SourceHealth sourceHealth,
        Freshness freshness,
        int gapCount,
        SourceStatus sourceStatus,
        DataOrigin dataOrigin,
        boolean fallbackUsed,
        boolean tradingAuthorization
) {

    public PublicMarketDataQualitySummary {
        sourceHealth = Objects.requireNonNull(sourceHealth, "sourceHealth must not be null");
        freshness = Objects.requireNonNull(freshness, "freshness must not be null");
        sourceStatus = Objects.requireNonNull(sourceStatus, "sourceStatus must not be null");
        dataOrigin = Objects.requireNonNull(dataOrigin, "dataOrigin must not be null");
        if (gapCount < 0) {
            throw new IllegalArgumentException("gapCount must not be negative");
        }
        if (tradingAuthorization) {
            throw new IllegalArgumentException("public marketdata quality must not authorize trading");
        }
    }

    /**
     * 行情源健康状态；只用于诊断，不是交易授权。
     */
    public enum SourceHealth {
        HEALTHY,
        DEGRADED,
        RATE_LIMITED,
        TIMEOUT,
        ERROR
    }

    /**
     * 数据 freshness 状态；STALE 可与 DEGRADED / ERROR 并存。
     */
    public enum Freshness {
        FRESH,
        STALE,
        UNKNOWN
    }

    /**
     * source 开关状态；DISABLED 表示 feature flag 或策略关闭。
     */
    public enum SourceStatus {
        ENABLED,
        DISABLED
    }

    /**
     * 数据来源；fallback 来源必须与真实 public outbound 分开。
     */
    public enum DataOrigin {
        PUBLIC_OUTBOUND,
        LOCAL_DB,
        FIXTURE,
        FAKE_SERVER
    }
}

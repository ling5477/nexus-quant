package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * DisabledPublicMarketDataOutboundClient 表示 feature flag 关闭时的 fallback client。
 *
 * <p>Why: local/test/CI/paper/freeze 默认 no-egress，调用方仍可能需要一个可测试的结果来映射 Data
 * Quality。该实现永远不创建 HTTP client、不访问网络、不读取 credential，只返回 DISABLED +
 * fallback origin（LOCAL_DB / FIXTURE / FAKE_SERVER）。</p>
 */
public final class DisabledPublicMarketDataOutboundClient implements PublicMarketDataOutboundClient {

    private final PublicMarketDataQualitySummary.DataOrigin fallbackOrigin;
    private final Clock clock;

    /**
     * @param fallbackOrigin fallback 数据来源；只能是 LOCAL_DB / FIXTURE / FAKE_SERVER 之一
     */
    public DisabledPublicMarketDataOutboundClient(PublicMarketDataQualitySummary.DataOrigin fallbackOrigin) {
        this(fallbackOrigin, Clock.systemUTC());
    }

    /**
     * 测试可注入固定时钟。
     *
     * @param fallbackOrigin fallback 数据来源
     * @param clock          时间来源
     */
    public DisabledPublicMarketDataOutboundClient(
            PublicMarketDataQualitySummary.DataOrigin fallbackOrigin, Clock clock) {
        this.fallbackOrigin = validateFallbackOrigin(fallbackOrigin);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PublicMarketDataOutboundResult fetch(PublicMarketDataOutboundRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return PublicMarketDataOutboundResult.disabled(request, fallbackOrigin, Instant.now(clock));
    }

    private static PublicMarketDataQualitySummary.DataOrigin validateFallbackOrigin(
            PublicMarketDataQualitySummary.DataOrigin origin) {
        Objects.requireNonNull(origin, "fallbackOrigin must not be null");
        if (origin == PublicMarketDataQualitySummary.DataOrigin.PUBLIC_OUTBOUND) {
            throw new IllegalArgumentException("disabled client fallback origin must not be PUBLIC_OUTBOUND");
        }
        return origin;
    }
}

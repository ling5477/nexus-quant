package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 仅为 OKX 现货小时级连续 SIM 提供受限公开观察；不携带账户或交易能力。 */
public interface ClosedBarMarketFeed {
    Observation observe(Instant firstOpen, int maximumBars);

    record Observation(Instant serverTime, Instant observedAt, List<HistoricalBar> bars,
                       Rule rule, Quote quote) { }

    /** 当前公开成交价只用于下一可交易时点；它不是已收盘策略输入 bar。 */
    record Quote(Instant observedAt, BigDecimal price) { }

    record Rule(Instant observedAt, String state, BigDecimal tickSize,
                BigDecimal lotSize, BigDecimal minimumSize) { }
}

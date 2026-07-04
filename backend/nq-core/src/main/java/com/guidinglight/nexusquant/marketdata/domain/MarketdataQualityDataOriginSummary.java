package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataQualityDataOriginSummary 描述 overview 中数据来源的当前能力边界。
 * <p>
 * Why:
 * GateP Batch 2 只读取本地表；即使请求带 dataOrigin，也不能把 `PUBLIC_OUTBOUND` 或 future provider
 * 语义写成当前 runtime fact。summary 明确当前 supportLevel，供文档和 UI 消费时 fail-closed。
 */
public record MarketdataQualityDataOriginSummary(
        String requestedDataOrigin,
        String effectiveDataOrigin,
        long localDbBars,
        long fixtureBars,
        long unknownOriginBars,
        String supportLevel
) {
    public MarketdataQualityDataOriginSummary {
        if (localDbBars < 0 || fixtureBars < 0 || unknownOriginBars < 0) {
            throw new IllegalArgumentException("origin bar counts must not be negative");
        }
    }
}

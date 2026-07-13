package com.guidinglight.nexusquant.adapter.okx.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OkxVenueRuleFact 是 OKX Public Instruments 中单个 Spot instrument 的已校验公开事实。
 *
 * <p>nullable max 字段保持 null；该模型不携带 raw payload、header、credential、request id 或交易授权。</p>
 */
public record OkxVenueRuleFact(
        String instId,
        String instType,
        String state,
        String baseCurrency,
        String quoteCurrency,
        BigDecimal tickSize,
        BigDecimal lotSize,
        BigDecimal minimumSize,
        BigDecimal maximumLimitSize,
        BigDecimal maximumMarketSize,
        String maximumMarketSizeUnit,
        BigDecimal maximumLimitAmountUsd,
        BigDecimal maximumMarketAmountUsd,
        Instant nextRuleEffectiveAt
) {
}

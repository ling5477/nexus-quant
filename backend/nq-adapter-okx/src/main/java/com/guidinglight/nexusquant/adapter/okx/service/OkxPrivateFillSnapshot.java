package com.guidinglight.nexusquant.adapter.okx.service;

import java.math.BigDecimal;
import java.time.Instant;

/** transport 内规范化的 OKX fill row；仅保留对账必需字段。 */
public record OkxPrivateFillSnapshot(
        String exchangeOrderId,
        String clientOrderId,
        String exchangeTradeId,
        String instrumentId,
        BigDecimal fillPrice,
        BigDecimal fillQuantity,
        Instant fillTime,
        Instant observedAt
) { }

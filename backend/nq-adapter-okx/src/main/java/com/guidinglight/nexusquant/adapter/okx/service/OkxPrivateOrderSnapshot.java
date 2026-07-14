package com.guidinglight.nexusquant.adapter.okx.service;

import java.math.BigDecimal;
import java.time.Instant;

/** transport 内规范化的 OKX order row；不保留 raw provider payload。 */
public record OkxPrivateOrderSnapshot(
        String exchangeOrderId,
        String clientOrderId,
        String instrumentId,
        String side,
        String orderType,
        BigDecimal price,
        BigDecimal originalQuantity,
        BigDecimal filledQuantity,
        String status,
        Instant observedAt,
        OkxPrivateReadOperation sourceOperation
) { }

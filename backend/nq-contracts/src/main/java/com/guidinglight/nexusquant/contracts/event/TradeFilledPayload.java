package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeFilledPayload 描述成交事实。
 *
 * Why:
 * docs/ARCHITECTURE.md 规定 Trade 为最终事实，后续模块据此纠偏订单、仓位与账本。
 */
public record TradeFilledPayload(
        @JsonProperty("trade_id") String tradeId,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("qty") BigDecimal qty,
        @JsonProperty("fee_amount") BigDecimal feeAmount,
        @JsonProperty("fee_currency") String feeCurrency,
        @JsonProperty("ts") Instant ts
) {
}

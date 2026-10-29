package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import java.time.Instant;

/**
 * OrderStatusChangedPayload 描述订单状态变化事实。
 */
public record OrderStatusChangedPayload(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("status") OrderStatus status,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}

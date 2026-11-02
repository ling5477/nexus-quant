package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * OrderSubmitted 表示订单已提交到执行适配层。
 *
 * @param orderId 系统订单 ID
 * @param clientOrderId 客户端幂等键
 * @param adapter 适配器标识，例如 PAPER
 * @param status 提交后状态快照
 * @param reason 额外说明，可空
 * @param ts 提交时间
 */
public record OrderSubmitted(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("adapter") String adapter,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}

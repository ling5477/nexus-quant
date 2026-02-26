package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * RiskPassed 表示订单通过风控。
 *
 * Why:
 * 通过与拒绝拆成两个事件类型，便于审计与订阅侧按 type 直接分流处理。
 *
 * @param orderId 系统订单 ID
 * @param clientOrderId 客户端幂等键
 * @param decision 固定为 PASS
 * @param reason 通过原因
 * @param ts 风控判定时间
 */
public record RiskPassed(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("decision") String decision,
        @JsonProperty("reason") String reason,
        @JsonProperty("ts") Instant ts
) {
}

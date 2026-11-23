package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * RiskRejected 表示订单被风控拒绝。
 *
 * @param orderId 系统订单 ID
 * @param clientOrderId 客户端幂等键
 * @param decision 固定为 REJECT
 * @param reason 拒绝原因
 * @param severity 风险级别
 * @param ts 风控判定时间
 */
public record RiskRejected(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("decision") String decision,
        @JsonProperty("reason") String reason,
        @JsonProperty("severity") String severity,
        @JsonProperty("ts") Instant ts
) {
}

package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * CancelAck 表示统一撤单接收回执。
 *
 * @param accountId 账户 ID
 * @param venue 交易场所
 * @param clientOrderId 客户端幂等键
 * @param externalOrderId 外部订单号，可空
 * @param status 回执后的内部状态
 * @param ts 回执时间
 */
public record CancelAck(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("venue") String venue,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("external_order_id") String externalOrderId,
        @JsonProperty("status") String status,
        @JsonProperty("ts") Instant ts
) {
}

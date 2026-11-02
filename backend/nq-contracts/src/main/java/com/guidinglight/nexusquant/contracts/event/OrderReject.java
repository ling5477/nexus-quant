package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * OrderReject 表示统一下单拒绝回执。
 *
 * @param accountId 账户 ID
 * @param venue 交易场所
 * @param clientOrderId 客户端幂等键
 * @param rejectCode 统一拒绝码
 * @param rejectReason 统一拒绝原因
 * @param ts 回执时间
 */
public record OrderReject(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("venue") String venue,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("reject_code") String rejectCode,
        @JsonProperty("reject_reason") String rejectReason,
        @JsonProperty("ts") Instant ts
) {
}

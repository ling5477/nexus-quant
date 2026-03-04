package com.guidinglight.nexusquant.contracts.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CancelOrderCommand 冻结撤单命令契约。
 *
 * @param orderId 订单 ID，可与 clientOrderId 二选一传入
 * @param accountId 账户 ID
 * @param venue 交易场所
 * @param symbol 交易对
 * @param clientOrderId 客户端幂等键
 * @param externalOrderId 外部订单号，可空
 * @param reason 撤单原因
 * @param traceId 链路追踪 ID
 */
public record CancelOrderCommand(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("venue") String venue,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("external_order_id") String externalOrderId,
        @JsonProperty("reason") String reason,
        @JsonProperty("trace_id") String traceId
) {
}

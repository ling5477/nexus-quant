package com.guidinglight.nexusquant.contracts.command;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CancelOrderCommand 冻结撤单命令契约。
 * <p>
 * Why:
 * GateD 要求撤单命令与下单命令一样具备 requestId、venue、accountId、symbol 等可审计字段，
 * 这样恢复、reconcile、手工撤单才能共享同一份命令语义。
 *
 * @param requestId       本次撤单请求 ID；用于区分同一订单上的多次撤单尝试
 * @param orderId         订单 ID，可与 clientOrderId 二选一传入
 * @param accountId       账户 ID
 * @param venue           交易场所
 * @param symbol          交易对
 * @param clientOrderId   客户端幂等键
 * @param externalOrderId 外部订单号，可空
 * @param reason          撤单原因
 * @param traceId         链路追踪 ID
 */
public record CancelOrderCommand(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("venue") String venue,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("external_order_id") String externalOrderId,
        @JsonProperty("reason") String reason,
        @JsonProperty("trace_id") String traceId
) {

    /**
     * 兼容旧构造器，保证第二批 contracts 收敛不会强制所有调用点同步改造。
     */
    public CancelOrderCommand(
            String orderId,
            Long accountId,
            String venue,
            String symbol,
            String clientOrderId,
            String externalOrderId,
            String reason,
            String traceId
    ) {
        this(orderId, traceId, accountId, venue, symbol, clientOrderId, externalOrderId, reason, traceId);
    }

    public CancelOrderCommand {
        traceId = requireText(traceId, "traceId");
        requestId = normalizeText(requestId, traceId);
        reason = requireText(reason, "reason");
        venue = normalizeText(venue, null);
        symbol = normalizeText(symbol, null);
        clientOrderId = normalizeText(clientOrderId, null);
        externalOrderId = normalizeText(externalOrderId, null);
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalizeText(value, null);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeText(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}

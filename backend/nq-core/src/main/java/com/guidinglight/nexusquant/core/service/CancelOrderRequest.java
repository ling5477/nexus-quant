package com.guidinglight.nexusquant.core.service;

/**
 * CancelOrderRequest 表示撤单编排入口参数。
 * <p>
 * Why:
 * 撤单既要支持通过 order_id 精确定位，也要支持 account_id + client_order_id 幂等定位，
 * 单独建模可以避免调用方拼装不一致导致误撤单；GateD 第二批还要求把 requestId、venue、symbol、
 * externalOrderId 等语义收口到 core，避免恢复链路再自行补字段。
 *
 * @param requestId       本次撤单请求 ID；未显式提供时回退为 traceId
 * @param orderId         系统订单 ID，可空；为空时要求 accountId + clientOrderId 非空
 * @param accountId       账户 ID，可空；当 orderId 为空时必须大于 0
 * @param venue           交易场所，可空；提供时会用于校验撤单目标是否漂移
 * @param symbol          交易对，可空；提供时会用于校验撤单目标是否漂移
 * @param clientOrderId   客户端幂等键，可空；当 orderId 为空时不能为空
 * @param externalOrderId 外部订单号，可空；提供时会用于校验撤单目标是否漂移
 * @param reason          撤单原因，不能为空
 * @param traceId         链路追踪 ID，不能为空
 */
public record CancelOrderRequest(
        String requestId,
        String orderId,
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String reason,
        String traceId
) {

    /**
     * 兼容旧构造器，允许第二批 contracts/core 收敛与第一批调用方共存。
     */
    public CancelOrderRequest(
            String orderId,
            Long accountId,
            String clientOrderId,
            String reason,
            String traceId
    ) {
        this(traceId, orderId, accountId, null, null, clientOrderId, null, reason, traceId);
    }

    public CancelOrderRequest {
        traceId = requireText(traceId, "traceId");
        requestId = normalizeText(requestId, traceId);
        venue = normalizeText(venue, null);
        symbol = normalizeText(symbol, null);
        clientOrderId = normalizeText(clientOrderId, null);
        externalOrderId = normalizeText(externalOrderId, null);
        reason = requireText(reason, "reason");
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

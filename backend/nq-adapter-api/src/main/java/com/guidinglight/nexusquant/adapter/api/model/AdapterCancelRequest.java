package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterCancelRequest 描述统一撤单请求。
 * <p>
 * Why:
 * GateD 要求撤单定位方式与 requestId/reason 语义在 adapter-api 内统一冻结，
 * 以便后续 query-confirm、真实交易所 ordId 和 paper 本地单号都走同一入口。
 *
 * @param requestId       本次撤单请求 ID
 * @param orderId         系统订单 ID
 * @param accountId       账户 ID
 * @param venue           交易场所
 * @param symbol          交易对
 * @param clientOrderId   客户端幂等键，可空
 * @param externalOrderId 外部订单号，可空；存在时优先使用
 * @param reason          撤单原因
 * @param traceId         链路追踪 ID
 */
public record AdapterCancelRequest(
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

    public AdapterCancelRequest {
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

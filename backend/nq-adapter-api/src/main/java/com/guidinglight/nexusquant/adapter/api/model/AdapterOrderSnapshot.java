package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterOrderSnapshot 表示 adapter 视角下的订单快照。
 *
 * @param accountId       账户 ID
 * @param venue           交易场所
 * @param symbol          交易对
 * @param clientOrderId   客户端幂等键
 * @param externalOrderId 外部订单号，可空
 * @param status          adapter 视角的订单状态
 * @param traceId         链路追踪 ID
 */
public record AdapterOrderSnapshot(
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String status,
        String traceId
) {
}

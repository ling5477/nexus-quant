package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterOrderQuery 描述统一查单条件。
 *
 * @param accountId       账户 ID
 * @param venue           交易场所
 * @param symbol          交易对
 * @param clientOrderId   客户端幂等键，可空
 * @param externalOrderId 外部订单号，可空
 * @param traceId         链路追踪 ID
 */
public record AdapterOrderQuery(
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String traceId
) {
}

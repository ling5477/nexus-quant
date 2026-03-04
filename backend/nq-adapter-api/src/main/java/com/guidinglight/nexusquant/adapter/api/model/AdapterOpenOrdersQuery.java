package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterOpenOrdersQuery 描述统一的未完成订单扫描条件。
 *
 * @param accountId 账户 ID
 * @param venue     交易场所
 * @param symbol    交易对，可空
 * @param traceId   链路追踪 ID
 */
public record AdapterOpenOrdersQuery(
        Long accountId,
        String venue,
        String symbol,
        String traceId
) {
}

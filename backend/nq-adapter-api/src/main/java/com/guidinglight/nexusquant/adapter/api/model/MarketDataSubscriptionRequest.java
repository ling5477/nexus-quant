package com.guidinglight.nexusquant.adapter.api.model;

/**
 * MarketDataSubscriptionRequest 描述统一行情订阅请求。
 *
 * @param accountId 账户 ID，可空；部分公共频道不依赖账户
 * @param venue 交易场所
 * @param symbol 交易对
 * @param traceId 链路追踪 ID
 */
public record MarketDataSubscriptionRequest(
        Long accountId,
        String venue,
        String symbol,
        String traceId
) {
}

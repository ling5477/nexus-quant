package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionAck;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionRequest;
import java.time.Instant;
import java.util.Objects;

/**
 * NoopMarketDataAdapter 提供 GateC-0 的最小行情 stub。
 * <p>
 * Why:
 * 文档要求先冻结三分法接口，行情能力在 GateC-1 之前允许 stub；
 * 这里返回可观测的占位响应，保证路由与装配先成立。
 */
public class NoopMarketDataAdapter implements MarketDataAdapter {

    private final String venue;

    public NoopMarketDataAdapter(String venue) {
        this.venue = Objects.requireNonNull(venue, "venue must not be null");
    }

    @Override
    public String venue() {
        return venue;
    }

    @Override
    public MarketDataSubscriptionAck subscribeBars(MarketDataSubscriptionRequest request) {
        return ack("bars", request.traceId());
    }

    @Override
    public MarketDataSubscriptionAck subscribeTrades(MarketDataSubscriptionRequest request) {
        return ack("trades", request.traceId());
    }

    @Override
    public MarketDataSubscriptionAck subscribeOrderBook(MarketDataSubscriptionRequest request) {
        return ack("order-book", request.traceId());
    }

    private MarketDataSubscriptionAck ack(String channel, String traceId) {
        return new MarketDataSubscriptionAck(true, venue, channel, null, Instant.now(), traceId);
    }
}

package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionAck;
import com.guidinglight.nexusquant.adapter.api.model.MarketDataSubscriptionRequest;

/**
 * MarketDataAdapter 冻结 GateC 的统一行情端口。
 */
public interface MarketDataAdapter {

    /**
     * @return 本 adapter 支持的 venue 标识
     */
    String venue();

    /**
     * 订阅 K 线。
     */
    MarketDataSubscriptionAck subscribeBars(MarketDataSubscriptionRequest request);

    /**
     * 订阅成交。
     */
    MarketDataSubscriptionAck subscribeTrades(MarketDataSubscriptionRequest request);

    /**
     * 订阅盘口。
     */
    MarketDataSubscriptionAck subscribeOrderBook(MarketDataSubscriptionRequest request);
}

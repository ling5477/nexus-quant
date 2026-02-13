package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;

/**
 * ExchangeAdapter 定义交易所适配抽象。
 *
 * Why:
 * Gate A 禁止接真实交易所网络；这里仅冻结调用接口，
 * 供 Gate B 在不改 core 依赖方向的情况下补实现。
 */
public interface ExchangeAdapter {

    /**
     * @return 交易所标识，例如 OKX/BINANCE。
     */
    String venue();

    /**
     * 提交下单请求。
     */
    AdapterOrderAck placeOrder(AdapterOrderRequest request);

    /**
     * 提交撤单请求。
     */
    void cancelOrder(String orderId, String traceId);
}

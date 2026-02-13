package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.service.ExchangeAdapter;
import java.time.Instant;

/**
 * BinanceExchangeAdapter 是 Binance 适配占位实现。
 */
public class BinanceExchangeAdapter implements ExchangeAdapter {

    @Override
    public String venue() {
        return "BINANCE";
    }

    @Override
    public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
        return new AdapterOrderAck(request.orderId(), "binance-placeholder-order", "ACKED", Instant.now(), request.traceId());
    }

    @Override
    public void cancelOrder(String orderId, String traceId) {
        // Gate A 占位：不执行真实网络调用。
    }
}

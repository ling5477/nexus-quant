package com.guidinglight.nexusquant.adapter.okx.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.service.ExchangeAdapter;
import java.time.Instant;

/**
 * OkxExchangeAdapter 是 OKX 适配占位实现。
 *
 * Why:
 * Gate A 不允许真实网络接入，本实现仅返回占位回执，
 * 用于固定模块边界与装配关系。
 */
public class OkxExchangeAdapter implements ExchangeAdapter {

    @Override
    public String venue() {
        return "OKX";
    }

    @Override
    public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
        return new AdapterOrderAck(request.orderId(), "okx-placeholder-order", "ACKED", Instant.now(), request.traceId());
    }

    @Override
    public void cancelOrder(String orderId, String traceId) {
        // Gate A 占位：不执行真实网络调用。
    }
}

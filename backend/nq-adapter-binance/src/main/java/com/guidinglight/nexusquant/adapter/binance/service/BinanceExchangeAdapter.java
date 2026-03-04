package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import java.time.Instant;
import java.util.List;

/**
 * BinanceExchangeAdapter 是 Binance 适配占位实现。
 */
public class BinanceExchangeAdapter implements TradingAdapter {

    @Override
    public String venue() {
        return "BINANCE";
    }

    @Override
    public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
        return new AdapterOrderAck(
                true,
                venue(),
                "binance-placeholder-order",
                null,
                Instant.now(),
                request.traceId()
        );
    }

    @Override
    public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
        return new AdapterCancelAck(true, venue(), request.externalOrderId(), null, Instant.now(), request.traceId());
    }

    @Override
    public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
        // Why: GateC-0 只冻结接口，不提供真实查单；返回 SENT 避免 scheduler 把真实 venue 当成本地撮合源。
        return new AdapterOrderSnapshot(
                query.accountId(),
                venue(),
                query.symbol(),
                query.clientOrderId(),
                query.externalOrderId(),
                "SENT",
                query.traceId()
        );
    }

    @Override
    public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
        return List.of();
    }
}

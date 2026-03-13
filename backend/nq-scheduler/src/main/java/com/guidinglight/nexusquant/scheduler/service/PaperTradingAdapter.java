package com.guidinglight.nexusquant.scheduler.service;

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
 * PaperTradingAdapter 把 PAPER 改造成标准 TradingAdapter 实现。
 * <p>
 * Why:
 * GateC-0 要消除 core 中的 paper 专用下单链路，因此 PAPER 也必须先走 adapter-api，
 * 这样 GateC-1 接 OKX 时只是在同一端口上补真实实现，而不是重写整个编排。
 */
public class PaperTradingAdapter implements TradingAdapter {

    @Override
    public String venue() {
        return "PAPER";
    }

    @Override
    public AdapterOrderAck placeOrder(AdapterOrderRequest request) {
        return new AdapterOrderAck(
                true,
                venue(),
                request.accountId(),
                request.symbol(),
                request.clientOrderId(),
                "paper-" + request.orderId(),
                "ACCEPTED",
                null,
                Instant.now(),
                "paper_place_ack",
                request.traceId()
        );
    }

    @Override
    public AdapterCancelAck cancelOrder(AdapterCancelRequest request) {
        return new AdapterCancelAck(
                true,
                venue(),
                request.externalOrderId(),
                null,
                Instant.now(),
                request.traceId()
        );
    }

    @Override
    public AdapterOrderSnapshot getOrder(AdapterOrderQuery query) {
        return new AdapterOrderSnapshot(
                query.accountId(),
                venue(),
                query.symbol(),
                query.clientOrderId(),
                resolveExternalOrderId(query),
                "ACCEPTED",
                null,
                null,
                null,
                null,
                Instant.now(),
                "paper_order_snapshot",
                query.traceId()
        );
    }

    @Override
    public List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query) {
        return List.of();
    }

    private String resolveExternalOrderId(AdapterOrderQuery query) {
        if (query.externalOrderId() != null && !query.externalOrderId().isBlank()) {
            return query.externalOrderId();
        }
        return "paper-" + query.clientOrderId();
    }
}

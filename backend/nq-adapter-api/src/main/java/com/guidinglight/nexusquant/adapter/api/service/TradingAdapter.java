package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderAck;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;

import java.util.List;

/**
 * TradingAdapter 冻结 GateC 的统一交易端口。
 * <p>
 * Why:
 * GateC-0 的核心目标是把 PAPER/OKX/BINANCE 抽象成同一种交易能力，避免 core 再走 paper 专用链路。
 */
public interface TradingAdapter {

    /**
     * @return 本 adapter 支持的 venue 标识
     */
    String venue();

    /**
     * 统一下单入口。
     */
    AdapterOrderAck placeOrder(AdapterOrderRequest request);

    /**
     * 统一撤单入口。
     */
    AdapterCancelAck cancelOrder(AdapterCancelRequest request);

    /**
     * 统一查单入口。
     */
    AdapterOrderSnapshot getOrder(AdapterOrderQuery query);

    /**
     * 统一扫描未完成订单入口。
     */
    List<AdapterOrderSnapshot> listOpenOrders(AdapterOpenOrdersQuery query);
}

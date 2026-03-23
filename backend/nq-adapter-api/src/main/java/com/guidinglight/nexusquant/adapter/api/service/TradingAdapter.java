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
 * TradingAdapter 冻结 GateD 的统一交易端口。
 * <p>
 * Why:
 * GateD 要求 core 只看到统一的 place / cancel / query / list-open-orders 语义，
 * 交易所方言必须留在 adapter 层内完成映射。
 */
public interface TradingAdapter {

    /**
     * @return 本 adapter 支持的 venue 标识
     */
    String venue();

    default String exchangeCode() {
        return venue();
    }

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

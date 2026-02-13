package com.guidinglight.nexusquant.api.service;

import com.guidinglight.nexusquant.api.model.OrderView;
import java.util.Optional;

/**
 * TradingQueryFacade 定义对外查询能力占位接口。
 */
public interface TradingQueryFacade {

    /**
     * 根据订单 ID 查询订单视图。
     */
    Optional<OrderView> queryOrder(String orderId, String traceId);
}

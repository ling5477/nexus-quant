package com.guidinglight.nexusquant.api.service;

import com.guidinglight.nexusquant.api.model.OrderView;
import java.util.Optional;

/**
 * NoopTradingQueryFacade 是 API 模块占位实现。
 */
public class NoopTradingQueryFacade implements TradingQueryFacade {

    @Override
    public Optional<OrderView> queryOrder(String orderId, String traceId) {
        return Optional.empty();
    }
}

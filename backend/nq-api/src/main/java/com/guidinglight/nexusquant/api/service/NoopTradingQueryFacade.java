package com.guidinglight.nexusquant.api.service;

import com.guidinglight.nexusquant.api.model.AccountView;
import com.guidinglight.nexusquant.api.model.OrderView;
import com.guidinglight.nexusquant.api.model.PositionView;
import com.guidinglight.nexusquant.api.model.TradeView;

import java.util.Optional;

/**
 * NoopTradingQueryFacade 是 API 模块占位实现。
 */
public class NoopTradingQueryFacade implements TradingQueryFacade {

    @Override
    public Optional<OrderView> queryOrder(String orderId, String traceId) {
        return Optional.empty();
    }

    @Override
    public Optional<TradeView> queryLatestTrade(String orderId, String traceId) {
        return Optional.empty();
    }

    @Override
    public Optional<PositionView> queryPosition(Long accountId, String symbol, String traceId) {
        return Optional.empty();
    }

    @Override
    public Optional<AccountView> queryAccount(Long accountId, String traceId) {
        return Optional.empty();
    }
}

package com.guidinglight.nexusquant.app.architecture.fixture.strategy;

import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;

/**
 * 仅供 ArchUnit negative regression 使用，证明 Strategy → Trading application 依赖会被拒绝。
 */
public final class InvalidStrategyTradingDependency {

    private PlaceOrderRequest forbiddenRequest;

    public PlaceOrderRequest forbiddenRequest() {
        return forbiddenRequest;
    }
}

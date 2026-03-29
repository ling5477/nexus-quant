package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderResult;

/**
 * StrategyExecutionGateway 抽象 GateE-1.2 到现有下单主链的最小桥接。
 */
public interface StrategyExecutionGateway {

    PlaceOrderResult placeOrder(PlaceOrderRequest request);
}



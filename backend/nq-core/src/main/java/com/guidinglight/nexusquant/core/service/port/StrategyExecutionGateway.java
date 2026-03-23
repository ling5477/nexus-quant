package com.guidinglight.nexusquant.core.service.port;

import com.guidinglight.nexusquant.core.service.PlaceOrderRequest;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;

/**
 * StrategyExecutionGateway 抽象 GateE-1.2 到现有下单主链的最小桥接。
 */
public interface StrategyExecutionGateway {

    PlaceOrderResult placeOrder(PlaceOrderRequest request);
}

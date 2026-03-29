package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;

import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * OrderCommandStrategyExecutionGateway 把 GateE-1.2 手动 trigger 桥接到现有下单主链。
 */
@Component
public class OrderCommandStrategyExecutionGateway implements StrategyExecutionGateway {

    private final OrderCommandService orderCommandService;

    public OrderCommandStrategyExecutionGateway(OrderCommandService orderCommandService) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
    }

    @Override
    public PlaceOrderResult placeOrder(PlaceOrderRequest request) {
        return orderCommandService.placeOrder(request);
    }
}




package com.guidinglight.nexusquant.trading.application.port;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionIntent;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionResult;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderResult;

import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * OrderCommandStrategyExecutionGateway 将 Strategy-owned intent 映射到既有下单主链。
 */
@Component
public class OrderCommandStrategyExecutionGateway implements StrategyExecutionGateway {

    private final OrderCommandService orderCommandService;

    public OrderCommandStrategyExecutionGateway(OrderCommandService orderCommandService) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
    }

    @Override
    public StrategyExecutionResult execute(StrategyExecutionIntent intent) {
        PlaceOrderResult result = orderCommandService.placeOrder(toPlaceOrderRequest(intent));
        return toStrategyExecutionResult(result);
    }

    static PlaceOrderRequest toPlaceOrderRequest(StrategyExecutionIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        return new PlaceOrderRequest(
                intent.requestId(),
                intent.accountId(),
                intent.strategyRunId(),
                intent.venue(),
                intent.symbol(),
                intent.clientOrderId(),
                intent.idempotencyKey(),
                intent.source(),
                intent.side(),
                intent.type(),
                intent.price(),
                intent.quantity(),
                intent.timeInForce(),
                intent.traceId()
        );
    }

    static StrategyExecutionResult toStrategyExecutionResult(PlaceOrderResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new StrategyExecutionResult(result.orderId(), result.status(), result.idempotentHit());
    }
}




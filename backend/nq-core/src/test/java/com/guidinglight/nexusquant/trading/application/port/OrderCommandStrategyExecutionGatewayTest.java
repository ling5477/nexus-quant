package com.guidinglight.nexusquant.trading.application.port;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionIntent;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionResult;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderResult;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OrderCommandStrategyExecutionGatewayTest {

    @Test
    void shouldMapStrategyIntentToExistingPlaceOrderRequestWithoutSemanticChanges() {
        StrategyExecutionIntent intent = new StrategyExecutionIntent(
                "req-0a",
                1001L,
                "run-0a",
                "BINANCE",
                "BTC-USDT",
                "coid-0a",
                "1001:coid-0a",
                "strategy_manual",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("100.00"),
                new BigDecimal("0.01"),
                null,
                "trace-0a"
        );

        PlaceOrderRequest request = OrderCommandStrategyExecutionGateway.toPlaceOrderRequest(intent);

        assertEquals(intent.requestId(), request.requestId());
        assertEquals(intent.accountId(), request.accountId());
        assertEquals(intent.strategyRunId(), request.strategyRunId());
        assertEquals(intent.venue(), request.venue());
        assertEquals(intent.symbol(), request.symbol());
        assertEquals(intent.clientOrderId(), request.clientOrderId());
        assertEquals(intent.idempotencyKey(), request.idempotencyKey());
        assertEquals(intent.source(), request.source());
        assertEquals(intent.side(), request.side());
        assertEquals(intent.type(), request.type());
        assertEquals(intent.price(), request.price());
        assertEquals(intent.quantity(), request.quantity());
        assertEquals("GTC", request.timeInForce());
        assertEquals(intent.traceId(), request.traceId());
    }

    @Test
    void shouldMapExistingPlaceOrderResultToStrategyResultWithoutSemanticChanges() {
        PlaceOrderResult placeOrderResult = new PlaceOrderResult("ord-0a", OrderStatus.REJECTED, false);

        StrategyExecutionResult result = OrderCommandStrategyExecutionGateway.toStrategyExecutionResult(
                placeOrderResult
        );

        assertEquals(placeOrderResult.orderId(), result.orderId());
        assertEquals(placeOrderResult.status(), result.status());
        assertFalse(result.idempotentHit());
    }
}

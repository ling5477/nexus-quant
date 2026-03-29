package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderResult;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * StrategyManualTriggerService 实现 GateE-1.2 的最小手动 trigger 主链。
 */
@Service
public class StrategyManualTriggerService {

    private final StrategyDefinitionRepository strategyDefinitionRepository;
    private final StrategyRunRepository strategyRunRepository;
    private final StrategyExecutionGateway strategyExecutionGateway;
    private final Clock clock;

    public StrategyManualTriggerService(
            StrategyDefinitionRepository strategyDefinitionRepository,
            StrategyRunRepository strategyRunRepository,
            StrategyExecutionGateway strategyExecutionGateway
    ) {
        this.strategyDefinitionRepository = Objects.requireNonNull(
                strategyDefinitionRepository,
                "strategyDefinitionRepository must not be null"
        );
        this.strategyRunRepository = Objects.requireNonNull(strategyRunRepository, "strategyRunRepository must not be null");
        this.strategyExecutionGateway = Objects.requireNonNull(
                strategyExecutionGateway,
                "strategyExecutionGateway must not be null"
        );
        this.clock = Clock.systemUTC();
    }

    public StrategyManualTriggerResult trigger(StrategyManualTriggerRequest request) {
        validateTriggerRequest(request);
        StrategyDefinition definition = strategyDefinitionRepository.findByStrategyId(request.strategyId())
                .orElseThrow(() -> new IllegalArgumentException("strategy definition not found: " + request.strategyId()));
        if (!definition.enabled()) {
            throw new IllegalStateException("strategy definition is disabled: " + request.strategyId());
        }

        Instant now = Instant.now(clock);
        String requestId = normalizeRequestId(request.requestId());
        String strategyRunId = "run-" + UUID.randomUUID();
        String clientOrderId = buildClientOrderId(requestId);

        StrategyRun createdRun = new StrategyRun(
                strategyRunId,
                definition.strategyId(),
                definition.accountId(),
                definition.exchangeCode(),
                definition.tradeEnv(),
                "MANUAL",
                StrategyRunStatus.CREATED,
                definition.configSnapshot(),
                requestId,
                now,
                null,
                null,
                request.traceId()
        );
        strategyRunRepository.insert(createdRun);
        strategyRunRepository.updateStatus(strategyRunId, StrategyRunStatus.DISPATCHING, null, null);

        PlaceOrderResult placeOrderResult = strategyExecutionGateway.placeOrder(new PlaceOrderRequest(
                requestId,
                definition.accountId(),
                strategyRunId,
                definition.exchangeCode(),
                request.symbol(),
                clientOrderId,
                definition.accountId() + ":" + clientOrderId,
                "strategy_manual",
                request.side(),
                request.orderType(),
                request.price(),
                request.quantity(),
                null,
                request.traceId()
        ));

        StrategyRunStatus finalStatus = isTriggerAccepted(placeOrderResult.status())
                ? StrategyRunStatus.RUNNING
                : StrategyRunStatus.FAILED;
        String errorMessage = finalStatus == StrategyRunStatus.FAILED
                ? "order_status=" + placeOrderResult.status().name()
                : null;
        strategyRunRepository.updateStatus(
                strategyRunId,
                finalStatus,
                finalStatus == StrategyRunStatus.FAILED ? Instant.now(clock) : null,
                errorMessage
        );

        return new StrategyManualTriggerResult(
                definition.strategyId(),
                strategyRunId,
                requestId,
                placeOrderResult.orderId(),
                placeOrderResult.status(),
                finalStatus,
                placeOrderResult.idempotentHit()
        );
    }

    private void validateTriggerRequest(StrategyManualTriggerRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.strategyId(), "strategyId");
        requireText(request.symbol(), "symbol");
        Objects.requireNonNull(request.side(), "side must not be null");
        Objects.requireNonNull(request.orderType(), "orderType must not be null");
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (request.traceId() == null || request.traceId().isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    private boolean isTriggerAccepted(OrderStatus orderStatus) {
        return orderStatus == OrderStatus.SENT
                || orderStatus == OrderStatus.ACCEPTED
                || orderStatus == OrderStatus.PARTIALLY_FILLED
                || orderStatus == OrderStatus.FILLED;
    }

    private String normalizeRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? "req-strategy-" + UUID.randomUUID() : requestId.trim();
    }

    private String buildClientOrderId(String requestId) {
        return "coid-" + requestId;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}



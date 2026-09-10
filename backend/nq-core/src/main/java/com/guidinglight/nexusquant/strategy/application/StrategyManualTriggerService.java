package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.trading.domain.TradingVenue;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionResult;
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
        StrategyDefinition definition = request.definitionSnapshot() == null
                ? strategyDefinitionRepository.findByStrategyId(request.strategyId())
                    .orElseThrow(() -> new IllegalArgumentException("strategy definition not found: " + request.strategyId()))
                : request.definitionSnapshot();
        if (!definition.strategyId().equals(request.strategyId())) throw new IllegalArgumentException("definition identity mismatch");
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
                TradingVenue.parse(definition.exchangeCode()).name(),
                definition.tradeEnv(),
                request.dispatchIdentity() == null ? "MANUAL" : "SCHEDULER",
                StrategyRunStatus.CREATED,
                definition.configSnapshot(),
                requestId,
                now,
                null,
                null,
                request.traceId()
        );
        var work = new StrategyDispatchWork(strategyRunId, 1, definition.version(), definition.accountId(),
                clientOrderId, request.symbol(), request.side(), request.orderType(), request.quantity(), request.price(),
                request.orderType() == OrderType.MARKET ? "IOC" : "GTC");
        work.intent(createdRun);
        var admission = strategyRunRepository.admit(createdRun, work, request.dispatchIdentity());
        if (!admission.admitted()) {
            var existing = admission.run();
            return new StrategyManualTriggerResult(existing.strategyId(), existing.strategyRunId(), existing.requestId(),
                    null, null, existing.status(), true, true);
        }
        // DISPATCHING 与 Order prepare 同事务；原 callback 不再猜测成功/失败或推进 cursor。
        StrategyExecutionResult executionResult = strategyExecutionGateway.execute(work.intent(admission.run()));
        StrategyRunStatus finalStatus = strategyRunRepository.findByStrategyRunId(strategyRunId).orElseThrow().status();

        return new StrategyManualTriggerResult(
                definition.strategyId(),
                strategyRunId,
                requestId,
                executionResult.orderId(),
                executionResult.status(),
                finalStatus,
                executionResult.idempotentHit()
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



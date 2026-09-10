package com.guidinglight.nexusquant.trading.application.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionIntent;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionResult;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.trading.application.StrategyOrderExecutionService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderResult;
import com.guidinglight.nexusquant.trading.domain.EffectiveOrderParameters;

import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * OrderCommandStrategyExecutionGateway 将 Strategy-owned intent 映射到既有下单主链。
 */
@Component
public class OrderCommandStrategyExecutionGateway implements StrategyExecutionGateway {

    private final StrategyOrderExecutionService executionService;
    private final StrategyRunExecutionRepository executions;
    private final StrategyRunRepository runs;
    private final TradingVenueGateway venue;

    public OrderCommandStrategyExecutionGateway(StrategyOrderExecutionService executionService,
            StrategyRunExecutionRepository executions, StrategyRunRepository runs, TradingVenueGateway venue) {
        this.executionService = Objects.requireNonNull(executionService);
        this.executions = Objects.requireNonNull(executions);
        this.runs = Objects.requireNonNull(runs);
        this.venue = Objects.requireNonNull(venue);
    }

    @Override
    public StrategyExecutionResult execute(StrategyExecutionIntent intent) {
        var run = runs.findByStrategyRunId(intent.strategyRunId()).orElseThrow();
        var work = executions.findWork(run.strategyRunId()).orElseThrow();
        if (!work.intent(run).equals(intent)) throw new IllegalArgumentException("STRATEGY_IMMUTABLE_WORK_OVERRIDE");
        return executeAndProject(run.strategyRunId(), false);
    }

    @Override
    public StrategyExecutionResult resume(String runId) {
        return executeAndProject(runId, true);
    }

    private StrategyExecutionResult executeAndProject(String runId, boolean recovery) {
        // instruments 网络读取在 B 事务之前；已有决定不读取新规则重算经济意图。
        var effective = executions.findEffective(runId).orElseGet(() -> {
            var request = toPlaceOrderRequest(executions.findWork(runId).orElseThrow(), runs.findByStrategyRunId(runId).orElseThrow());
            return "OKX".equals(request.venue()) ? venue.normalizePlaceOrder(request)
                    : new EffectiveOrderParameters(request.quantity(), request.price(), null);
        });
        PlaceOrderResult result = executionService.execute(runId, recovery, effective);
        executions.project(runId);
        return toStrategyExecutionResult(result);
    }

    public static PlaceOrderRequest toPlaceOrderRequest(StrategyDispatchWork work, StrategyRun run) {
        return toPlaceOrderRequest(work.intent(run));
    }

    /** 保留原意图身份，只将已冻结的有效经济值交给 Order/RiskGate。 */
    public static PlaceOrderRequest effectiveRequest(PlaceOrderRequest request, EffectiveOrderParameters effective) {
        return new PlaceOrderRequest(request.requestId(), request.accountId(), request.strategyRunId(), request.venue(),
                request.symbol(), request.clientOrderId(), request.idempotencyKey(), request.source(), request.side(), request.type(),
                effective.price(), effective.quantity(), request.timeInForce(), request.traceId(), request.tradeEnv(), request.executionScopeId());
    }

    public static PlaceOrderRequest toPlaceOrderRequest(StrategyExecutionIntent intent) {
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
                intent.traceId(),
                intent.tradeEnv(),
                null
        );
    }

    static StrategyExecutionResult toStrategyExecutionResult(PlaceOrderResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new StrategyExecutionResult(result.orderId(), result.status(), result.idempotentHit());
    }
}




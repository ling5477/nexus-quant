package com.guidinglight.nexusquant.trading.application;

import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.trading.application.port.OrderCommandStrategyExecutionGateway;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.port.StrategyOrderBindingRepository;
import com.guidinglight.nexusquant.trading.domain.EffectiveOrderParameters;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 事务 B 只承载本地事实；run 锁、唯一 Order、风险与初始 V49 必须一起提交。 */
@Service
public class StrategyOrderPreparationService {
    private final StrategyOrderBindingRepository executions;
    private final OrderRepository orders;
    private final OrderCommandWriteService writes;
    private final EventPublisherPort events;

    public StrategyOrderPreparationService(StrategyOrderBindingRepository executions, OrderRepository orders,
            OrderCommandWriteService writes, EventPublisherPort events) {
        this.executions = executions;
        this.orders = orders;
        this.writes = writes;
        this.events = events;
    }

    @Transactional(timeout = 5)
    public Prepared prepare(String runId, boolean recovery, EffectiveOrderParameters parameters) {
        var run = executions.lockRun(runId);
        var work = executions.findWork(runId).orElseThrow(() -> new IllegalStateException("LEGACY_STRATEGY_WORK_UNAVAILABLE"));
        var request = OrderCommandStrategyExecutionGateway.toPlaceOrderRequest(work, run);
        OrderCommandService.validateRequest(request);
        // B5 自动继续只使用已有 ordinary OKX 协议，不能顺便扩大到其它发送能力。
        if (recovery && !"OKX".equals(request.venue())) throw new IllegalStateException("STRATEGY_MUTATION_PROTOCOL_UNAVAILABLE");
        executions.bindEffective(runId, parameters);
        var effective = executions.findEffective(runId).orElseThrow();
        if (effective.rejectionCode() != null) {
            return new Prepared(request, OrderCommandWriteService.PlaceOrderPreparation.completed(
                    new PlaceOrderResult(null, OrderStatus.REJECTED, true)));
        }
        request = OrderCommandStrategyExecutionGateway.effectiveRequest(request, effective);
        OrderCommandService.validateRequest(request);
        var existing = orders.findByStrategyRunId(runId);
        if (existing.isPresent()) {
            var order = existing.get();
            if (!order.accountId().equals(request.accountId()) || !order.clientOrderId().equals(request.clientOrderId())
                    || !order.tradeEnv().equals(request.tradeEnv()) || !order.venue().equals(request.venue())
                    || !order.symbol().equals(request.symbol()) || !order.side().equals(request.side().name())
                    || !order.type().equals(request.type().name()) || order.qty().compareTo(request.quantity()) != 0
                    || !samePrice(order.price(), request.price())) throw new IllegalStateException("STRATEGY_ORDER_WORK_MISMATCH");
            boolean terminal = run.status() == StrategyRunStatus.FAILED || run.status() == StrategyRunStatus.SUCCEEDED;
            var preparation = !terminal && "OKX".equals(request.venue()) && order.status() == OrderStatus.SENT
                    ? OrderCommandWriteService.PlaceOrderPreparation.readyForAdapter(order)
                    : OrderCommandWriteService.PlaceOrderPreparation.completed(new PlaceOrderResult(order.orderId(), order.status(), true));
            return new Prepared(request, preparation);
        }
        if (!executions.beginDispatch(runId)) throw new IllegalStateException("strategy run cannot begin dispatch");
        String orderId = "ord-" + UUID.randomUUID();
        var command = ExecutionCommandMapper.toPlaceCommand(request, orderId);
        Instant now = Instant.now();
        events.append(TopicNames.ORDER_COMMAND_V1, new EventEnvelope<>("evt-" + UUID.randomUUID(),
                command.getClass().getSimpleName(), 1, now, "nq-core.strategy-order-preparation",
                request.traceId(), request.clientOrderId(), command));
        return new Prepared(request, writes.preparePlaceOrder(request, command, orderId, now));
    }

    private static boolean samePrice(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    public record Prepared(PlaceOrderRequest request, OrderCommandWriteService.PlaceOrderPreparation preparation) { }
}

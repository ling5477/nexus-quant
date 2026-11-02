package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.application.command.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.result.CancelOrderResult;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandService;
import com.guidinglight.nexusquant.trading.domain.OrderCancelFinality;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderCancelFinalityRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/** 自身独立SIM的撤单和恢复共用canonical取消证明；不能据此确认交易所取消或释放LIVE权限。 */
@Service
@ConditionalOnProperty(
        prefix = "nq.runtime.trading-components",
        name = "enabled",
        havingValue = "true")
public class StrategySimCancellationService {
    private final StrategySimOrderQuery visibleOrders;
    private final StrategySimDecisionRepository decisions;
    private final OrderRepository orders;
    private final OrderCommandService commands;
    private final OrderCancelFinalityRepository finality;

    public StrategySimCancellationService(
            StrategySimOrderQuery visibleOrders,
            StrategySimDecisionRepository decisions,
            OrderRepository orders,
            OrderCommandService commands,
            OrderCancelFinalityRepository finality) {
        this.visibleOrders = visibleOrders;
        this.decisions = decisions;
        this.orders = orders;
        this.commands = commands;
        this.finality = finality;
    }

    /** 与推进/撮合共用账户互斥；权限和原订单在锁内重读，前端无需填写内部订单身份。 */
    public Optional<CancelOrderResult> cancel(
            long owner, String paperRunId, String orderId, String traceId) {
        var visible = visibleOrders.read(owner, paperRunId, orderId);
        if (visible.isEmpty()) return Optional.empty();
        long accountId = ((Number) visible.get().order().get("account_id")).longValue();
        return decisions.withAccountMutex(
                accountId,
                () -> {
                    if (visibleOrders.read(owner, paperRunId, orderId).isEmpty())
                        return Optional.empty();
                    OrderRecord order = boundOrder(paperRunId, orderId, accountId);
                    var result =
                            commands.cancelOrder(
                                    new CancelOrderRequest(
                                            "sim-cancel-" + orderId,
                                            orderId,
                                            accountId,
                                            "PAPER",
                                            order.symbol(),
                                            order.clientOrderId(),
                                            order.externalOrderId(),
                                            "USER_REQUESTED_SIM_CANCEL",
                                            traceId));
                    if (result.status() == OrderStatus.CANCELLED)
                        finish(paperRunId, orderId, accountId);
                    return Optional.of(result);
                });
    }

    /** 刷新只能恢复自身订单的原取消事实；未知响应先查询，不生成新撤单身份。 */
    public Optional<Boolean> refresh(long owner, String paperRunId, String orderId) {
        var visible = visibleOrders.read(owner, paperRunId, orderId);
        if (visible.isEmpty()) return Optional.empty();
        long accountId = ((Number) visible.get().order().get("account_id")).longValue();
        return decisions.withAccountMutex(
                accountId,
                () -> {
                    if (visibleOrders.read(owner, paperRunId, orderId).isEmpty())
                        return Optional.empty();
                    return Optional.of(finish(paperRunId, orderId, accountId));
                });
    }

    /**
     * 推进调用时已持有同一账户互斥，不能再次取得该锁。PAPER撮合全量原子提交，取消只允许零成交。
     * 本地取消快照、真实累计成交和原订单版本共同交给既有受限函数；部分事实或版本冲突不得猜测终态。
     */
    boolean finish(String paperRunId, String orderId, long accountId) {
        OrderRecord observed = boundOrder(paperRunId, orderId, accountId);
        if (observed.status() != OrderStatus.CANCELLED) throw invalid();
        if (!orders.hasDurableSimCancellation(observed)) throw invalid();
        var executed = orders.durableExecutedQuantity(orderId);
        if (executed.signum() != 0) throw invalid();
        return finality.finish(new OrderCancelFinality(observed, executed));
    }

    private OrderRecord boundOrder(String paperRunId, String orderId, long accountId) {
        OrderRecord order =
                orders.findByOrderId(orderId).orElseThrow(StrategySimCancellationService::invalid);
        var decision =
                decisions
                        .findByOrderId(orderId)
                        .orElseThrow(StrategySimCancellationService::invalid);
        if (!"PAPER".equals(order.venue())
                || !"SIM".equals(order.tradeEnv())
                || order.accountId() != accountId
                || !paperRunId.equals(decision.paperRunId())
                || !"ACCEPTED".equals(decision.status())
                || !Objects.equals(order.strategyRunId(), decision.strategyRunId()))
            throw invalid();
        return order;
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("SIM_CANCEL_FINALITY_UNAVAILABLE");
    }
}

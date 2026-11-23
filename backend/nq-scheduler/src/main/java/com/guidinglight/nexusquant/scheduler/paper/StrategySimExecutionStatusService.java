package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/** 显式刷新已有SIM执行的终态；唯一写入委托canonical投影端口，不构造交易意图。 */
@Service
public class StrategySimExecutionStatusService {
    private final StrategySimOrderQuery orders;
    private final StrategyRunExecutionRepository executions;
    private final StrategyRunRepository runs;
    private StrategySimCancellationService cancellations;

    @Autowired(required = false)
    private void bindCancellations(StrategySimCancellationService service) {
        this.cancellations = service;
    }

    public StrategySimExecutionStatusService(
            StrategySimOrderQuery orders,
            StrategyRunExecutionRepository executions,
            StrategyRunRepository runs) {
        this.orders = orders;
        this.executions = executions;
        this.runs = runs;
    }

    /** 先确认订单可见且属于已有终态，再委托canonical投影；未知、在途或无关联执行身份时不能猜测完成。 */
    public Optional<Projection> refresh(long owner, String paperRunId, String orderId) {
        var details = orders.read(owner, paperRunId, orderId);
        if (details.isEmpty()) return Optional.empty();
        var order = details.get().order();
        Object linked = order.get("strategy_run_id");
        boolean cancelled = "CANCELLED".equals(order.get("status"));
        if (linked == null
                || !(Set.of("FILLED", "RISK_REJECTED", "REJECTED")
                                .contains(String.valueOf(order.get("status")))
                        || cancelled && cancellations != null)) {
            throw new IllegalStateException("SIM_ORDER_EXECUTION_NOT_FINAL");
        }
        String runId = linked.toString();
        // 投影端口在锁内重新判断Order/Trade事实；重复调用只返回既有终态，不能靠先前读快照猜测迁移。
        // 取消接受不代表交易所终态；此分支只使用自身PAPER/SIM零成交证明，不放宽通用投影规则。
        boolean projected =
                cancelled
                        ? cancellations.refresh(owner, paperRunId, orderId).orElseThrow()
                        : executions.project(runId);
        var run =
                runs.findByStrategyRunId(runId)
                        .orElseThrow(() -> new IllegalStateException("SIM_EXECUTION_NOT_FOUND"));
        return Optional.of(
                new Projection(paperRunId, orderId, runId, run.status().name(), projected));
    }

    public record Projection(
            String paperRunId,
            String orderId,
            String strategyRunId,
            String status,
            boolean projected) {}
}

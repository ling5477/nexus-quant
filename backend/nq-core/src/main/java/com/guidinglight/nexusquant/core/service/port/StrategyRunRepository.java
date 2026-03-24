package com.guidinglight.nexusquant.core.service.port;

import com.guidinglight.nexusquant.core.model.StrategyRun;
import com.guidinglight.nexusquant.core.model.StrategyRunStatus;

import java.time.Instant;
import java.util.Optional;

/**
 * StrategyRunRepository 定义 strategy_runs 的最小持久化端口。
 */
public interface StrategyRunRepository {

    /**
     * 持久化一条新的 strategy run。
     * <p>
     * Why:
     * GateE-1.2 / 2.x 的所有运行血缘都以 strategy_runs 为唯一事实表，因此任何 accepted trigger
     * 都必须先写入 run，再继续进入执行链。
     */
    void insert(StrategyRun strategyRun);

    /**
     * 按运行级身份查询 run。
     * <p>
     * Why:
     * 调度链和测试链都需要用 strategyRunId 回查运行状态，不能改成靠 requestId 反推。
     */
    Optional<StrategyRun> findByStrategyRunId(String strategyRunId);

    /**
     * 按请求级身份查询最近一次运行。
     * <p>
     * Why:
     * GateE-2.2 的最小 dedup 语义依赖 requestId 判定“这次 schedule 命中是否已经触发过 run”。
     */
    Optional<StrategyRun> findLatestByRequestId(String requestId);

    /**
     * 判断某个 strategy 当前是否存在未结束运行。
     * <p>
     * Why:
     * GateE-2.2 只要求单实例内最小串行化，但为了避免 schedule 触发把同策略 run 挤成并发双跑，
     * 仍需要在触发前检查 strategy_runs 里是否存在活动态。
     */
    boolean existsActiveRunByStrategyId(String strategyId);

    /**
     * 更新运行状态。
     * <p>
     * Why:
     * 运行状态只能通过显式事件推进；调度门禁 skip 时不得伪造 run，因此只有真正创建过 run
     * 才允许调用该方法推进状态。
     */
    boolean updateStatus(String strategyRunId, StrategyRunStatus status, Instant finishedAt, String errorMessage);
}

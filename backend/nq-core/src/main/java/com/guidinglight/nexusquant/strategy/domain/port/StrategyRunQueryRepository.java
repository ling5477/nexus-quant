package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderSummary;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeSummary;

import java.util.List;
import java.util.Optional;

/**
 * StrategyRunQueryRepository 定义 GateE-2.3 的最小运行查询端口。
 */
public interface StrategyRunQueryRepository {

    /**
     * 按 `strategyRunId` 查询运行事实。
     */
    Optional<StrategyRun> findRunByStrategyRunId(String strategyRunId);

    /**
     * 按 `strategyId` 查询最近运行列表。
     */
    List<StrategyRun> listRecentRunsByStrategyId(String strategyId, int limit);

    /**
     * 按 `scheduleJobId` 查询最近 schedule trigger 运行列表。
     * <p>
     * Why:
     * 当前 schema 没有 `strategy_runs.schedule_job_id`，因此这里只能通过稳定的 schedule requestId 前缀做最小可用查询。
     */
    List<StrategyRun> listRecentRunsByScheduleJobId(String scheduleJobId, int limit);

    /**
     * 查询某个运行关联的最小订单摘要。
     */
    List<StrategyRunOrderSummary> listOrderSummariesByStrategyRunId(String strategyRunId);

    /**
     * 查询某个运行关联的最小成交摘要。
     */
    List<StrategyRunTradeSummary> listTradeSummariesByStrategyRunId(String strategyRunId);
}



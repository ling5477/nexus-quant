package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StrategyScheduleRepository 定义 strategy_schedules 的最小持久化端口。
 */
public interface StrategyScheduleRepository {

    /**
     * 新建一条 schedule config。
     */
    void insert(StrategySchedule schedule);

    /**
     * 按计划级身份查询 schedule。
     */
    Optional<StrategySchedule> findByScheduleJobId(String scheduleJobId);

    /**
     * 按 strategyId 查询其下的全部 schedule。
     */
    List<StrategySchedule> listByStrategyId(String strategyId);

    /**
     * 列出当前所有 schedule。
     * <p>
     * Why:
     * GateE-2.2 的 scanOnce 需要返回 `skipped_disabled`，因此扫描入口不能只看 enabled schedules。
     */
    List<StrategySchedule> listAll();

    /**
     * 仅列出启用中的 schedule。
     */
    List<StrategySchedule> listEnabledSchedules();

    /**
     * 更新 schedule 启停状态。
     */
    boolean updateEnabled(String scheduleJobId, boolean enabled, Instant updatedAt);

    /**
     * 仅在真正触发 run 后刷新 last_triggered_at。
     */
    boolean updateLastTriggeredAt(String scheduleJobId, Instant lastTriggeredAt, Instant updatedAt);
}



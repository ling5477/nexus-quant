package com.guidinglight.nexusquant.core.service.port;

import com.guidinglight.nexusquant.core.model.StrategySchedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * StrategyScheduleRepository 定义 strategy_schedules 的最小持久化端口。
 */
public interface StrategyScheduleRepository {

    void insert(StrategySchedule schedule);

    Optional<StrategySchedule> findByScheduleJobId(String scheduleJobId);

    List<StrategySchedule> listByStrategyId(String strategyId);

    List<StrategySchedule> listEnabledSchedules();

    boolean updateEnabled(String scheduleJobId, boolean enabled, Instant updatedAt);

    boolean updateLastTriggeredAt(String scheduleJobId, Instant lastTriggeredAt, Instant updatedAt);
}

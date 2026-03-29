package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * StrategyScheduleService 提供 GateE-2.1 的计划配置管理能力。
 */
@Service
public class StrategyScheduleService {

    private final StrategyDefinitionRepository strategyDefinitionRepository;
    private final StrategyScheduleRepository strategyScheduleRepository;
    private final Clock clock;

    public StrategyScheduleService(
            StrategyDefinitionRepository strategyDefinitionRepository,
            StrategyScheduleRepository strategyScheduleRepository
    ) {
        this.strategyDefinitionRepository = Objects.requireNonNull(
                strategyDefinitionRepository,
                "strategyDefinitionRepository must not be null"
        );
        this.strategyScheduleRepository = Objects.requireNonNull(
                strategyScheduleRepository,
                "strategyScheduleRepository must not be null"
        );
        this.clock = Clock.systemUTC();
    }

    public StrategySchedule create(StrategyScheduleCreateRequest request) {
        validateCreateRequest(request);
        StrategyDefinition definition = strategyDefinitionRepository.findByStrategyId(request.strategyId())
                .orElseThrow(() -> new IllegalArgumentException("strategy definition not found: " + request.strategyId()));
        Instant now = Instant.now(clock);
        StrategySchedule schedule = new StrategySchedule(
                "sch-" + UUID.randomUUID(),
                definition.strategyId(),
                request.scheduleType().trim().toUpperCase(),
                request.cronExpr().trim(),
                normalizeTimezone(request.timezone()),
                request.enabled(),
                normalizeJson(request.windowConfig()),
                normalizeDedupScope(request.dedupScope()),
                definition.exchangeCode(),
                definition.accountId(),
                definition.tradeEnv(),
                null,
                now,
                now
        );
        try {
            strategyScheduleRepository.insert(schedule);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("failed to create strategy schedule", ex);
        }
        return schedule;
    }

    public List<StrategySchedule> listByStrategyId(String strategyId) {
        requireText(strategyId, "strategyId");
        return strategyScheduleRepository.listByStrategyId(strategyId);
    }

    /**
     * 列出当前所有计划配置。
     * <p>
     * Why:
     * GateE-2.2 的 scanOnce 需要把 disabled schedule 也纳入结构化结果，
     * 否则无法返回 `skipped_disabled`。
     */
    public List<StrategySchedule> listAllSchedules() {
        return strategyScheduleRepository.listAll();
    }

    public StrategySchedule getByScheduleJobId(String scheduleJobId) {
        return strategyScheduleRepository.findByScheduleJobId(requireText(scheduleJobId, "scheduleJobId"))
                .orElseThrow(() -> new IllegalArgumentException("strategy schedule not found: " + scheduleJobId));
    }

    public StrategySchedule enable(String scheduleJobId) {
        StrategySchedule current = getByScheduleJobId(scheduleJobId);
        if (!strategyScheduleRepository.updateEnabled(current.scheduleJobId(), true, Instant.now(clock))) {
            throw new IllegalStateException("failed to enable strategy schedule: " + scheduleJobId);
        }
        return current.withEnabled(true, Instant.now(clock));
    }

    public StrategySchedule disable(String scheduleJobId) {
        StrategySchedule current = getByScheduleJobId(scheduleJobId);
        if (!strategyScheduleRepository.updateEnabled(current.scheduleJobId(), false, Instant.now(clock))) {
            throw new IllegalStateException("failed to disable strategy schedule: " + scheduleJobId);
        }
        return current.withEnabled(false, Instant.now(clock));
    }

    public List<StrategySchedule> listEnabledSchedules() {
        return strategyScheduleRepository.listEnabledSchedules();
    }

    public void updateLastTriggeredAt(String scheduleJobId, Instant lastTriggeredAt) {
        if (!strategyScheduleRepository.updateLastTriggeredAt(scheduleJobId, lastTriggeredAt, Instant.now(clock))) {
            throw new IllegalStateException("failed to update last_triggered_at: " + scheduleJobId);
        }
    }

    private void validateCreateRequest(StrategyScheduleCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.strategyId(), "strategyId");
        String scheduleType = requireText(request.scheduleType(), "scheduleType").toUpperCase();
        if (!"CRON".equals(scheduleType)) {
            throw new IllegalArgumentException("scheduleType must be CRON in GateE-2.1");
        }
        CronExpression.parse(requireText(request.cronExpr(), "cronExpr"));
        normalizeTimezone(request.timezone());
        normalizeDedupScope(request.dedupScope());
    }

    private String normalizeTimezone(String timezone) {
        return requireText(timezone, "timezone");
    }

    private String normalizeDedupScope(String dedupScope) {
        String normalized = requireText(dedupScope, "dedupScope").toUpperCase();
        if (!"SCHEDULE_WINDOW".equals(normalized) && !"REQUEST".equals(normalized) && !"STRATEGY".equals(normalized)) {
            throw new IllegalArgumentException("dedupScope must be SCHEDULE_WINDOW, REQUEST or STRATEGY");
        }
        return normalized;
    }

    private String normalizeJson(String raw) {
        return raw == null || raw.isBlank() ? "{}" : raw.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}



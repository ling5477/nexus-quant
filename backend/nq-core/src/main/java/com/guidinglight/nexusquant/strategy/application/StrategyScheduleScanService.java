package com.guidinglight.nexusquant.strategy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyTriggerGateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * StrategyScheduleScanService 实现 GateE-2.2 的最小 schedule scan / gate / dispatch 入口。
 * <p>
 * 职责：
 * 1. 扫描全部 schedule 并返回结构化结果；
 * 2. 在真正触发前执行窗口、去重、单实例 busy 保护；
 * 3. 触发命中后仍然复用 GateE-1.2 的 StrategyManualTriggerService 主链。
 * <p>
 * 边界：
 * 1. `windowConfig` 只决定“这次是否允许创建 run”，不接管下单后的生命周期；
 * 2. `dedupScope` 只负责 schedule -> run 的最小去重，不等于订单幂等；
 * 3. 串行化只保证单实例内最小互斥，不承诺跨实例严格一致。
 */
@Service
public class StrategyScheduleScanService {

    private final StrategyScheduleService strategyScheduleService;
    private final StrategyDefinitionRepository strategyDefinitionRepository;
    private final StrategyRunRepository strategyRunRepository;
    private final StrategyTriggerGateway strategyTriggerGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Set<String> busyScheduleIds = ConcurrentHashMap.newKeySet();
    private final Set<String> busyStrategyIds = ConcurrentHashMap.newKeySet();

    public StrategyScheduleScanService(
            StrategyScheduleService strategyScheduleService,
            StrategyDefinitionRepository strategyDefinitionRepository,
            StrategyRunRepository strategyRunRepository,
            StrategyTriggerGateway strategyTriggerGateway,
            ObjectMapper objectMapper
    ) {
        this.strategyScheduleService = Objects.requireNonNull(strategyScheduleService, "strategyScheduleService must not be null");
        this.strategyDefinitionRepository = Objects.requireNonNull(
                strategyDefinitionRepository,
                "strategyDefinitionRepository must not be null"
        );
        this.strategyRunRepository = Objects.requireNonNull(strategyRunRepository, "strategyRunRepository must not be null");
        this.strategyTriggerGateway = Objects.requireNonNull(strategyTriggerGateway, "strategyTriggerGateway must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 执行一次最小 schedule 扫描。
     * <p>
     * Why:
     * GateE-2.2 不能引入新的调度入口，所以所有 window / dedup / busy 语义都必须在现有 scanOnce 内收口。
     *
     * @param traceId 本次扫描的链路 traceId；为空时会在具体 trigger 请求内补默认值
     * @return 批量扫描结果，包含统计摘要与逐条明细
     */
    public StrategyScheduleScanBatchResult scanOnce(String traceId) {
        Instant now = Instant.now(clock);
        List<StrategyScheduleScanResult> results = new ArrayList<>();
        for (StrategySchedule schedule : strategyScheduleService.listAllSchedules()) {
            results.add(scanSingleSchedule(schedule, traceId, now));
        }
        return StrategyScheduleScanBatchResult.from(results);
    }

    private StrategyScheduleScanResult scanSingleSchedule(StrategySchedule schedule, String traceId, Instant now) {
        try {
            if (!schedule.enabled()) {
                return result(schedule, StrategyScheduleScanOutcome.SKIPPED_DISABLED, null, null, "schedule_disabled");
            }

            StrategyDefinition definition = strategyDefinitionRepository.findByStrategyId(schedule.strategyId()).orElse(null);
            if (definition == null) {
                return result(schedule, StrategyScheduleScanOutcome.FAILED, null, null, "strategy_definition_missing");
            }
            if (!definition.enabled()) {
                return result(
                        schedule,
                        StrategyScheduleScanOutcome.SKIPPED_STRATEGY_DISABLED,
                        null,
                        null,
                        "strategy_definition_disabled"
                );
            }

            Instant dueAt = resolveDueAt(schedule, now);
            if (dueAt == null) {
                return result(schedule, StrategyScheduleScanOutcome.SKIPPED_NOT_DUE, null, null, "not_due");
            }

            WindowDecision windowDecision = evaluateWindow(schedule, now);
            if (!windowDecision.allowed()) {
                return result(
                        schedule,
                        StrategyScheduleScanOutcome.SKIPPED_WINDOW,
                        null,
                        null,
                        windowDecision.reason()
                );
            }

            BusyToken busyToken = acquireBusyToken(schedule);
            if (!busyToken.acquired()) {
                return result(schedule, StrategyScheduleScanOutcome.SKIPPED_BUSY, null, null, busyToken.reason());
            }

            try {
                if (strategyRunRepository.existsActiveRunByStrategyId(schedule.strategyId())) {
                    return result(schedule, StrategyScheduleScanOutcome.SKIPPED_BUSY, null, null, "strategy_run_active");
                }

                String requestId = buildScheduleRequestId(schedule, dueAt);
                if (isDedupHit(schedule, requestId)) {
                    return result(schedule, StrategyScheduleScanOutcome.SKIPPED_DEDUP, requestId, null, "dedup_hit");
                }

                StrategyManualTriggerRequest request = buildTriggerRequest(schedule, definition, requestId, traceId);
                StrategyManualTriggerResult triggerResult = strategyTriggerGateway.trigger(request);
                strategyScheduleService.updateLastTriggeredAt(schedule.scheduleJobId(), now);
                return result(
                        schedule,
                        StrategyScheduleScanOutcome.TRIGGERED,
                        triggerResult.requestId(),
                        triggerResult.strategyRunId(),
                        "triggered"
                );
            } finally {
                busyToken.release();
            }
        } catch (Exception ex) {
            return result(schedule, StrategyScheduleScanOutcome.FAILED, null, null, ex.getMessage());
        }
    }

    private StrategyScheduleScanResult result(
            StrategySchedule schedule,
            StrategyScheduleScanOutcome outcome,
            String requestId,
            String strategyRunId,
            String detail
    ) {
        return new StrategyScheduleScanResult(
                schedule.scheduleJobId(),
                schedule.strategyId(),
                outcome,
                requestId,
                strategyRunId,
                detail
        );
    }

    private Instant resolveDueAt(StrategySchedule schedule, Instant now) {
        if (!"CRON".equalsIgnoreCase(schedule.scheduleType())) {
            return null;
        }
        ZoneId zoneId = ZoneId.of(schedule.timezone());
        Instant referenceInstant = schedule.lastTriggeredAt() == null
                ? schedule.createdAt().minusSeconds(1)
                : schedule.lastTriggeredAt();
        ZonedDateTime reference = referenceInstant.atZone(zoneId);
        ZonedDateTime next = CronExpression.parse(schedule.cronExpr()).next(reference);
        return next != null && !next.toInstant().isAfter(now) ? next.toInstant() : null;
    }

    private WindowDecision evaluateWindow(StrategySchedule schedule, Instant now) {
        JsonNode config = parseJson(schedule.windowConfig());
        if (config.isEmpty() || config.path("enabled").asBoolean(true) && !config.hasNonNull("startTime")
                && !config.hasNonNull("endTime")) {
            return WindowDecision.allow();
        }
        if (config.has("enabled") && !config.path("enabled").asBoolean()) {
            return WindowDecision.allow();
        }

        ZoneId zoneId = ZoneId.of(config.path("timezone").asText(schedule.timezone()));
        ZonedDateTime current = now.atZone(zoneId);
        if (!isAllowedDay(config.path("daysOfWeek"), current.getDayOfWeek())) {
            return WindowDecision.block("window_day_blocked");
        }

        String rawStart = requireWindowText(config, "startTime");
        String rawEnd = requireWindowText(config, "endTime");
        LocalTime start = parseLocalTime(rawStart);
        LocalTime end = parseLocalTime(rawEnd);
        LocalTime currentTime = current.toLocalTime();
        boolean inWindow = contains(currentTime, start, end);
        return inWindow ? WindowDecision.allow() : WindowDecision.block("window_closed");
    }

    private boolean isAllowedDay(JsonNode node, DayOfWeek currentDay) {
        if (node == null || node.isMissingNode() || node.isEmpty()) {
            return true;
        }
        EnumSet<DayOfWeek> allowedDays = EnumSet.noneOf(DayOfWeek.class);
        node.forEach(item -> allowedDays.add(DayOfWeek.valueOf(item.asText().trim().toUpperCase(Locale.ROOT))));
        return allowedDays.contains(currentDay);
    }

    private LocalTime parseLocalTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalStateException("windowConfig time must use HH:mm or HH:mm:ss");
        }
    }

    private boolean contains(LocalTime current, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !current.isBefore(start) && current.isBefore(end);
        }
        return !current.isBefore(start) || current.isBefore(end);
    }

    private String requireWindowText(JsonNode config, String fieldName) {
        String value = config.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("windowConfig." + fieldName + " must not be blank");
        }
        return value.trim();
    }

    private BusyToken acquireBusyToken(StrategySchedule schedule) {
        boolean scheduleBusy = busyScheduleIds.add(schedule.scheduleJobId());
        if (!scheduleBusy) {
            return BusyToken.rejected("schedule_busy");
        }
        boolean strategyBusy = busyStrategyIds.add(schedule.strategyId());
        if (!strategyBusy) {
            busyScheduleIds.remove(schedule.scheduleJobId());
            return BusyToken.rejected("strategy_busy");
        }
        return BusyToken.acquired(schedule.scheduleJobId(), schedule.strategyId(), busyScheduleIds, busyStrategyIds);
    }

    private boolean isDedupHit(StrategySchedule schedule, String requestId) {
        StrategyRun existingRun = strategyRunRepository.findLatestByRequestId(requestId).orElse(null);
        if (existingRun == null) {
            return false;
        }
        return switch (schedule.dedupScope()) {
            case "STRATEGY" -> schedule.strategyId().equals(existingRun.strategyId());
            case "REQUEST", "SCHEDULE_WINDOW" -> true;
            default -> true;
        };
    }

    private String buildScheduleRequestId(StrategySchedule schedule, Instant dueAt) {
        String bucket = String.valueOf(dueAt.toEpochMilli());
        return switch (schedule.dedupScope()) {
            case "STRATEGY" -> "req-schedule-" + schedule.scheduleJobId()
                    + "-strategy-" + schedule.strategyId() + "-" + bucket;
            case "REQUEST" -> "req-schedule-" + schedule.scheduleJobId() + "-request-" + bucket;
            case "SCHEDULE_WINDOW" -> "req-schedule-" + schedule.scheduleJobId() + "-window-" + bucket;
            default -> throw new IllegalStateException("unsupported dedupScope: " + schedule.dedupScope());
        };
    }

    private StrategyManualTriggerRequest buildTriggerRequest(
            StrategySchedule schedule,
            StrategyDefinition definition,
            String requestId,
            String traceId
    ) {
        JsonNode config = parseJson(definition.configSnapshot());
        return new StrategyManualTriggerRequest(
                definition.strategyId(),
                requestId,
                requireText(config.path("symbol").asText(null), "config_snapshot.symbol"),
                OrderSide.valueOf(requireText(config.path("side").asText(null), "config_snapshot.side").toUpperCase(Locale.ROOT)),
                OrderType.valueOf(
                        requireText(config.path("orderType").asText(null), "config_snapshot.orderType")
                                .toUpperCase(Locale.ROOT)
                ),
                new BigDecimal(requireText(config.path("quantity").asText(null), "config_snapshot.quantity")),
                parsePrice(config.path("price").asText(null)),
                traceId == null || traceId.isBlank() ? "trc-schedule-scan-" + schedule.scheduleJobId() + "-" + UUID.randomUUID() : traceId
        );
    }

    private JsonNode parseJson(String rawJson) {
        try {
            return objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
        } catch (Exception ex) {
            throw new IllegalStateException("schedule config JSON is not valid", ex);
        }
    }

    private BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return null;
        }
        return new BigDecimal(rawPrice);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private record WindowDecision(boolean allowed, String reason) {
        private static WindowDecision allow() {
            return new WindowDecision(true, "window_allowed");
        }

        private static WindowDecision block(String reason) {
            return new WindowDecision(false, reason);
        }
    }

    private record BusyToken(
            boolean acquired,
            String reason,
            String scheduleJobId,
            String strategyId,
            Set<String> busyScheduleIds,
            Set<String> busyStrategyIds
    ) {
        private static BusyToken rejected(String reason) {
            return new BusyToken(false, reason, null, null, Set.of(), Set.of());
        }

        private static BusyToken acquired(
                String scheduleJobId,
                String strategyId,
                Set<String> busyScheduleIds,
                Set<String> busyStrategyIds
        ) {
            return new BusyToken(true, "busy_guard_acquired", scheduleJobId, strategyId, busyScheduleIds, busyStrategyIds);
        }

        private void release() {
            if (!acquired) {
                return;
            }
            busyScheduleIds.remove(scheduleJobId);
            busyStrategyIds.remove(strategyId);
        }
    }
}



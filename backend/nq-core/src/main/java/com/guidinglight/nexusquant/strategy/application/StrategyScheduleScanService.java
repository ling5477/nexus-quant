package com.guidinglight.nexusquant.strategy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchIdentity;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyTriggerGateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * 3. 本地 busy 和只读查询只是快速拒绝；跨实例同窗口唯一性由持久 admission 决定。
 */
@Service
public class StrategyScheduleScanService {

    private final StrategyScheduleService strategyScheduleService;
    private final StrategyDefinitionRepository strategyDefinitionRepository;
    private final StrategyRunRepository strategyRunRepository;
    private final StrategyRunRecoveryService strategyRunRecoveryService;
    private final StrategyTriggerGateway strategyTriggerGateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Set<String> busyScheduleIds = ConcurrentHashMap.newKeySet();
    private final Set<String> busyStrategyIds = ConcurrentHashMap.newKeySet();

    public StrategyScheduleScanService(
            StrategyScheduleService strategyScheduleService,
            StrategyDefinitionRepository strategyDefinitionRepository,
            StrategyRunRepository strategyRunRepository,
            StrategyRunRecoveryService strategyRunRecoveryService,
            StrategyTriggerGateway strategyTriggerGateway,
            ObjectMapper objectMapper
    ) {
        this.strategyScheduleService = Objects.requireNonNull(strategyScheduleService, "strategyScheduleService must not be null");
        this.strategyDefinitionRepository = Objects.requireNonNull(
                strategyDefinitionRepository,
                "strategyDefinitionRepository must not be null"
        );
        this.strategyRunRepository = Objects.requireNonNull(strategyRunRepository, "strategyRunRepository must not be null");
        this.strategyRunRecoveryService = Objects.requireNonNull(strategyRunRecoveryService);
        this.strategyTriggerGateway = Objects.requireNonNull(strategyTriggerGateway, "strategyTriggerGateway must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 执行一次最小 schedule 扫描。
     * <p>
     * Why:
     * 先独立恢复一批已接纳 run，再为每个 schedule 最多接纳一个按 dueAt 排序的窗口。
     *
     * @param traceId 本次扫描的链路 traceId；为空时会在具体 trigger 请求内补默认值
     * @return 批量扫描结果，包含统计摘要与逐条明细
     */
    public StrategyScheduleScanBatchResult scanOnce(String traceId) {
        strategyRunRecoveryService.recoverAll();
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

            Instant dueAt = StrategyScheduleTiming.nextDue(schedule, schedule.lastTriggeredAt(), now);
            if (dueAt == null) {
                return result(schedule, StrategyScheduleScanOutcome.SKIPPED_NOT_DUE, null, null, "not_due");
            }

            String blockedReason = StrategyScheduleTiming.blockedReason(schedule, now);
            if (blockedReason != null) {
                return result(
                        schedule,
                        StrategyScheduleScanOutcome.SKIPPED_WINDOW,
                        null,
                        null,
                        blockedReason
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
                StrategyManualTriggerRequest request = buildTriggerRequest(schedule, definition, requestId, traceId, dueAt);
                StrategyManualTriggerResult triggerResult = strategyTriggerGateway.trigger(request);
                if (triggerResult.duplicateAdmission()) {
                    return result(schedule, StrategyScheduleScanOutcome.SKIPPED_DEDUP,
                            requestId, triggerResult.strategyRunId(), "duplicate_admission");
                }
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
            String traceId,
            Instant dueAt
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
                traceId == null || traceId.isBlank() ? "trc-schedule-scan-" + schedule.scheduleJobId() + "-" + UUID.randomUUID() : traceId,
                new StrategyDispatchIdentity(schedule.scheduleJobId(), definition.strategyId(), definition.accountId(), dueAt),
                definition
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



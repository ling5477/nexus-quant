package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunDetail;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunExecutionResult;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunSummary;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunQueryRepository;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * StrategyRunQueryService 提供 GateE-2.3 的最小运行结果查询面。
 * <p>
 * Why:
 * 运行结果不能让 controller 自己去拼 `strategy_runs / orders / trades`。
 * 这里统一 manual trigger 与 schedule trigger 的查询视角，保证上层始终以 `strategyRunId` 为主轴。
 */
@Service
public class StrategyRunQueryService {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final String LEDGER_LIMITATION =
            "当前未纳入直接查询面：ledger_entries / ledger_events 缺少稳定 strategy_run_id 外键，只能通过 ref_type/ref_id 或 trace_id 间接追踪。";
    private static final String RISK_LIMITATION =
            "当前未纳入直接查询面：risk_events 没有稳定 strategy_run_id 外键，scope/scope_id 不能保证与 run 一一对应。";
    private static final String EVENT_LIMITATION =
            "当前未纳入直接查询面：event_store / audit_logs 主要依赖 trace_id 和业务键，尚未形成稳定的 run 级直连查询。";

    private final StrategyRunQueryRepository strategyRunQueryRepository;

    public StrategyRunQueryService(StrategyRunQueryRepository strategyRunQueryRepository) {
        this.strategyRunQueryRepository = Objects.requireNonNull(
                strategyRunQueryRepository,
                "strategyRunQueryRepository must not be null"
        );
    }

    /**
     * 按 `strategyRunId` 查询运行详情。
     */
    public StrategyRunDetail getRunDetail(String strategyRunId) {
        StrategyRun run = strategyRunQueryRepository.findRunByStrategyRunId(requireText(strategyRunId, "strategyRunId"))
                .orElseThrow(() -> new IllegalArgumentException("strategy run not found: " + strategyRunId));
        return toDetail(run);
    }

    /**
     * 按 `strategyId` 查询最近运行列表。
     */
    public List<StrategyRunSummary> listRecentRunsByStrategyId(String strategyId) {
        return strategyRunQueryRepository.listRecentRunsByStrategyId(requireText(strategyId, "strategyId"), DEFAULT_LIST_LIMIT)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 按 `scheduleJobId` 查询最近计划触发运行列表。
     * <p>
     * Why:
     * 当前没有独立 `trigger_id` 表，因此这里只做基于 schedule requestId 前缀的最小可用查询。
     */
    public List<StrategyRunSummary> listRecentRunsByScheduleJobId(String scheduleJobId) {
        return strategyRunQueryRepository.listRecentRunsByScheduleJobId(requireText(scheduleJobId, "scheduleJobId"), DEFAULT_LIST_LIMIT)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private StrategyRunDetail toDetail(StrategyRun run) {
        return new StrategyRunDetail(
                run.strategyId(),
                deriveScheduleJobId(run.requestId()),
                run.strategyRunId(),
                run.requestId(),
                normalizeTriggerType(run),
                run.status(),
                run.exchangeCode(),
                run.accountId(),
                run.tradeEnv(),
                run.startedAt(),
                run.finishedAt(),
                run.errorMessage(),
                new StrategyRunExecutionResult(
                        strategyRunQueryRepository.listOrderSummariesByStrategyRunId(run.strategyRunId()),
                        strategyRunQueryRepository.listTradeSummariesByStrategyRunId(run.strategyRunId()),
                        LEDGER_LIMITATION,
                        RISK_LIMITATION,
                        EVENT_LIMITATION
                )
        );
    }

    private StrategyRunSummary toSummary(StrategyRun run) {
        return new StrategyRunSummary(
                run.strategyId(),
                deriveScheduleJobId(run.requestId()),
                run.strategyRunId(),
                run.requestId(),
                normalizeTriggerType(run),
                run.status(),
                run.exchangeCode(),
                run.accountId(),
                run.tradeEnv(),
                run.startedAt(),
                run.finishedAt(),
                run.errorMessage()
        );
    }

    private String normalizeTriggerType(StrategyRun run) {
        if (run.requestId() != null && run.requestId().startsWith("req-schedule-")) {
            return "SCHEDULED";
        }
        return run.triggerType();
    }

    private String deriveScheduleJobId(String requestId) {
        if (requestId == null || !requestId.startsWith("req-schedule-")) {
            return null;
        }
        String remaining = requestId.substring("req-schedule-".length());
        for (String marker : List.of("-window-", "-request-", "-strategy-")) {
            int markerIndex = remaining.indexOf(marker);
            if (markerIndex > 0) {
                return remaining.substring(0, markerIndex);
            }
        }
        return null;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}



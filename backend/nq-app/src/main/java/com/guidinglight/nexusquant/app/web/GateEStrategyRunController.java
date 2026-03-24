package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.service.StrategyRunQueryService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * GateEStrategyRunController 提供 GateE-2.3 的最小内部运行结果查询入口。
 */
@RestController
@Profile("local")
@ConditionalOnProperty(name = "nq.gated.verify.enabled", havingValue = "true")
@RequestMapping("/__gated")
public class GateEStrategyRunController {

    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final StrategyRunQueryService strategyRunQueryService;

    public GateEStrategyRunController(StrategyRunQueryService strategyRunQueryService) {
        this.strategyRunQueryService = Objects.requireNonNull(strategyRunQueryService, "strategyRunQueryService must not be null");
    }

    @GetMapping("/strategy-runs/{strategyRunId}")
    public GateEStrategyRunDetailResponse detail(
            @PathVariable String strategyRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyRunDetailResponse.from(strategyRunQueryService.getRunDetail(strategyRunId)));
    }

    @GetMapping("/strategies/{strategyId}/runs")
    public List<GateEStrategyRunSummaryResponse> listByStrategyId(
            @PathVariable String strategyId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> strategyRunQueryService.listRecentRunsByStrategyId(strategyId)
                .stream()
                .map(GateEStrategyRunSummaryResponse::from)
                .toList());
    }

    @GetMapping("/strategy-schedules/{scheduleJobId}/runs")
    public List<GateEStrategyRunSummaryResponse> listByScheduleJobId(
            @PathVariable String scheduleJobId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> strategyRunQueryService.listRecentRunsByScheduleJobId(scheduleJobId)
                .stream()
                .map(GateEStrategyRunSummaryResponse::from)
                .toList());
    }

    private String resolveTraceId(String primaryTraceId, String fallbackTraceId) {
        if (primaryTraceId != null && !primaryTraceId.isBlank()) {
            return primaryTraceId.trim();
        }
        if (fallbackTraceId != null && !fallbackTraceId.isBlank()) {
            return fallbackTraceId.trim();
        }
        return "trc-gatee-run-query-" + UUID.randomUUID();
    }

    private <T> T withTrace(String traceId, TraceSupplier<T> supplier) {
        MDC.put("trace_id", traceId);
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } finally {
            MDC.remove("trace_id");
        }
    }

    @FunctionalInterface
    private interface TraceSupplier<T> {
        T get();
    }
}

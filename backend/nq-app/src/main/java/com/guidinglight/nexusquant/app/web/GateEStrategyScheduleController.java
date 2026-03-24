package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.service.StrategyScheduleCreateRequest;
import com.guidinglight.nexusquant.core.service.StrategyScheduleScanService;
import com.guidinglight.nexusquant.core.service.StrategyScheduleService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * GateEStrategyScheduleController 提供 GateE-2.1 的最小内部计划配置管理接口。
 */
@RestController
@Profile("local")
@ConditionalOnProperty(name = "nq.gated.verify.enabled", havingValue = "true")
@RequestMapping("/__gated")
public class GateEStrategyScheduleController {

    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final StrategyScheduleService strategyScheduleService;
    private final StrategyScheduleScanService strategyScheduleScanService;

    public GateEStrategyScheduleController(
            StrategyScheduleService strategyScheduleService,
            StrategyScheduleScanService strategyScheduleScanService
    ) {
        this.strategyScheduleService = Objects.requireNonNull(strategyScheduleService, "strategyScheduleService must not be null");
        this.strategyScheduleScanService = Objects.requireNonNull(strategyScheduleScanService, "strategyScheduleScanService must not be null");
    }

    @PostMapping("/strategies/{strategyId}/schedules")
    public GateEStrategyScheduleResponse create(
            @PathVariable String strategyId,
            @RequestBody GateEStrategyScheduleCreateRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyScheduleResponse.from(
                strategyScheduleService.create(new StrategyScheduleCreateRequest(
                        strategyId,
                        request.scheduleType(),
                        request.cronExpr(),
                        request.timezone(),
                        request.enabled(),
                        request.windowConfig(),
                        request.dedupScope()
                ))
        ));
    }

    @GetMapping("/strategies/{strategyId}/schedules")
    public List<GateEStrategyScheduleResponse> listByStrategyId(
            @PathVariable String strategyId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> strategyScheduleService.listByStrategyId(strategyId)
                .stream()
                .map(GateEStrategyScheduleResponse::from)
                .toList());
    }

    @GetMapping("/strategy-schedules/{scheduleJobId}")
    public GateEStrategyScheduleResponse detail(
            @PathVariable String scheduleJobId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyScheduleResponse.from(
                strategyScheduleService.getByScheduleJobId(scheduleJobId)
        ));
    }

    @PostMapping("/strategy-schedules/{scheduleJobId}/enable")
    public GateEStrategyScheduleResponse enable(
            @PathVariable String scheduleJobId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyScheduleResponse.from(
                strategyScheduleService.enable(scheduleJobId)
        ));
    }

    @PostMapping("/strategy-schedules/{scheduleJobId}/disable")
    public GateEStrategyScheduleResponse disable(
            @PathVariable String scheduleJobId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyScheduleResponse.from(
                strategyScheduleService.disable(scheduleJobId)
        ));
    }

    @PostMapping("/strategy-schedules/scanOnce")
    public List<GateEStrategyScheduleScanResponse> scanOnce(
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> strategyScheduleScanService.scanOnce(traceId)
                .stream()
                .map(GateEStrategyScheduleScanResponse::from)
                .toList());
    }

    private String resolveTraceId(String primaryTraceId, String fallbackTraceId) {
        if (primaryTraceId != null && !primaryTraceId.isBlank()) {
            return primaryTraceId.trim();
        }
        if (fallbackTraceId != null && !fallbackTraceId.isBlank()) {
            return fallbackTraceId.trim();
        }
        return "trc-gatee-schedule-" + UUID.randomUUID();
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

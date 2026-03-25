package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.app.research.GateFBacktestRunApplicationService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * GateFBacktestRunController 提供 GateF-2 的回测运行创建、启动与查询入口。
 */
@RestController
@Profile("local")
@ConditionalOnBean(GateFBacktestRunApplicationService.class)
@ConditionalOnProperty(name = "nq.gated.verify.enabled", havingValue = "true")
@RequestMapping("/__gated/backtest-runs")
public class GateFBacktestRunController {

    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final GateFBacktestRunApplicationService applicationService;

    public GateFBacktestRunController(GateFBacktestRunApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    @PostMapping
    public GateFBacktestRunResponse create(
            @RequestBody GateFBacktestRunStartRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateFBacktestRunResponse.from(applicationService.create(
                request.backtestConfigId()
        ), null, null));
    }

    @PostMapping("/{backtestRunId}/start")
    public GateFBacktestRunResponse startExecution(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        var publishRecord = applicationService.findPublishOrNull(backtestRunId);
        return withTrace(traceId, () -> GateFBacktestRunResponse.from(
                applicationService.startExecution(backtestRunId),
                GateFBacktestEvaluationResponse.summary(applicationService.findEvaluationOrNull(backtestRunId)),
                publishRecord == null ? null : publishRecord.toSummary()
        ));
    }

    @GetMapping("/{backtestRunId}")
    public GateFBacktestRunResponse detail(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        var publishRecord = applicationService.findPublishOrNull(backtestRunId);
        return withTrace(traceId, () -> GateFBacktestRunResponse.from(
                applicationService.getByBacktestRunId(backtestRunId),
                GateFBacktestEvaluationResponse.summary(applicationService.findEvaluationOrNull(backtestRunId)),
                publishRecord == null ? null : publishRecord.toSummary()
        ));
    }

    @GetMapping
    public List<GateFBacktestRunResponse> list(
            @RequestParam(required = false) String researchConfigId,
            @RequestParam(required = false) String backtestConfigId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> applicationService.list(researchConfigId, backtestConfigId)
                .stream()
                .map(run -> {
                    var publishRecord = applicationService.findPublishOrNull(run.backtestRunId());
                    return GateFBacktestRunResponse.from(
                            run,
                            GateFBacktestEvaluationResponse.summary(applicationService.findEvaluationOrNull(run.backtestRunId())),
                            publishRecord == null ? null : publishRecord.toSummary()
                    );
                })
                .toList());
    }

    @PostMapping("/{backtestRunId}/evaluate")
    public GateFBacktestEvaluationResponse evaluate(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateFBacktestEvaluationResponse.from(
                applicationService.evaluate(backtestRunId)
        ));
    }

    @GetMapping("/{backtestRunId}/evaluation")
    public GateFBacktestEvaluationResponse evaluation(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateFBacktestEvaluationResponse.from(
                applicationService.getEvaluation(backtestRunId)
        ));
    }

    @PostMapping("/{backtestRunId}/publish")
    public GateFBacktestPublishResponse publish(
            @PathVariable String backtestRunId,
            @RequestBody(required = false) GateFBacktestPublishRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateFBacktestPublishResponse.from(
                applicationService.publish(backtestRunId, request == null ? null : request.displayName())
        ));
    }

    @GetMapping("/{backtestRunId}/publish")
    public GateFBacktestPublishResponse publishDetail(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateFBacktestPublishResponse.from(
                applicationService.getPublish(backtestRunId)
        ));
    }

    @GetMapping("/{backtestRunId}/orders")
    public List<GateFSimOrderResponse> orders(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> applicationService.listOrders(backtestRunId)
                .stream()
                .map(GateFSimOrderResponse::from)
                .toList());
    }

    @GetMapping("/{backtestRunId}/trades")
    public List<GateFSimTradeResponse> trades(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> applicationService.listTrades(backtestRunId)
                .stream()
                .map(GateFSimTradeResponse::from)
                .toList());
    }

    @GetMapping("/{backtestRunId}/positions")
    public List<GateFSimPositionResponse> positions(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> applicationService.listPositions(backtestRunId)
                .stream()
                .map(GateFSimPositionResponse::from)
                .toList());
    }

    @GetMapping("/{backtestRunId}/pnl")
    public List<GateFSimPnlSnapshotResponse> pnl(
            @PathVariable String backtestRunId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> applicationService.listPnlSnapshots(backtestRunId)
                .stream()
                .map(GateFSimPnlSnapshotResponse::from)
                .toList());
    }

    private String resolveTraceId(String primaryTraceId, String fallbackTraceId) {
        if (primaryTraceId != null && !primaryTraceId.isBlank()) {
            return primaryTraceId.trim();
        }
        if (fallbackTraceId != null && !fallbackTraceId.isBlank()) {
            return fallbackTraceId.trim();
        }
        return "trc-gatef-backtest-run-" + UUID.randomUUID();
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

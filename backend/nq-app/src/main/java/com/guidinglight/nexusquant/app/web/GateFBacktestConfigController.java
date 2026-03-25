package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.app.research.GateFBacktestConfigApplicationService;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * GateFBacktestConfigController 提供 GateF-1 的回测配置创建入口。
 */
@RestController
@Profile("local")
@ConditionalOnBean(GateFBacktestConfigApplicationService.class)
@ConditionalOnProperty(name = "nq.gated.verify.enabled", havingValue = "true")
@RequestMapping("/__gated/backtest-configs")
public class GateFBacktestConfigController {

    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final GateFBacktestConfigApplicationService applicationService;

    public GateFBacktestConfigController(GateFBacktestConfigApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    @PostMapping
    public GateFBacktestConfigResponse create(
            @RequestBody GateFBacktestConfigCreateRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateFBacktestConfigResponse.from(applicationService.create(
                request.researchConfigId(),
                request.name(),
                request.description(),
                request.startTime(),
                request.endTime(),
                request.initialCapital(),
                request.executionSpec(),
                request.evaluationSpec()
        )));
    }

    private String resolveTraceId(String primaryTraceId, String fallbackTraceId) {
        if (primaryTraceId != null && !primaryTraceId.isBlank()) {
            return primaryTraceId.trim();
        }
        if (fallbackTraceId != null && !fallbackTraceId.isBlank()) {
            return fallbackTraceId.trim();
        }
        return "trc-gatef-backtest-config-" + UUID.randomUUID();
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

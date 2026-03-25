package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.app.research.GateFResearchConfigApplicationService;

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
 * GateFResearchConfigController 提供 GateF-1 的研究配置创建入口。
 */
@RestController
@Profile("local")
@ConditionalOnBean(GateFResearchConfigApplicationService.class)
@ConditionalOnProperty(name = "nq.gated.verify.enabled", havingValue = "true")
@RequestMapping("/__gated/research-configs")
public class GateFResearchConfigController {

    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final GateFResearchConfigApplicationService applicationService;

    public GateFResearchConfigController(GateFResearchConfigApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
    }

    @PostMapping
    public GateFResearchConfigResponse create(
            @RequestBody GateFResearchConfigCreateRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateFResearchConfigResponse.from(applicationService.create(
                request.sourceStrategyId(),
                request.name(),
                request.description(),
                request.parameterSchema(),
                request.parameterDefaults(),
                request.datasetSpec()
        )));
    }

    private String resolveTraceId(String primaryTraceId, String fallbackTraceId) {
        if (primaryTraceId != null && !primaryTraceId.isBlank()) {
            return primaryTraceId.trim();
        }
        if (fallbackTraceId != null && !fallbackTraceId.isBlank()) {
            return fallbackTraceId.trim();
        }
        return "trc-gatef-research-" + UUID.randomUUID();
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

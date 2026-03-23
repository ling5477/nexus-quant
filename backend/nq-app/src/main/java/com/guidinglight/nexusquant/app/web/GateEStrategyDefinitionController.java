package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.model.StrategyDefinition;
import com.guidinglight.nexusquant.core.service.StrategyDefinitionCreateRequest;
import com.guidinglight.nexusquant.core.service.StrategyDefinitionService;
import com.guidinglight.nexusquant.core.service.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.core.service.StrategyManualTriggerService;

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
 * GateEStrategyDefinitionController 提供 GateE-1.1 的最小内部策略定义管理接口。
 */
@RestController
@Profile("local")
@ConditionalOnProperty(name = "nq.gated.verify.enabled", havingValue = "true")
@RequestMapping("/__gated/strategies")
public class GateEStrategyDefinitionController {

    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final StrategyDefinitionService strategyDefinitionService;
    private final StrategyManualTriggerService strategyManualTriggerService;

    public GateEStrategyDefinitionController(
            StrategyDefinitionService strategyDefinitionService,
            StrategyManualTriggerService strategyManualTriggerService
    ) {
        this.strategyDefinitionService = Objects.requireNonNull(
                strategyDefinitionService,
                "strategyDefinitionService must not be null"
        );
        this.strategyManualTriggerService = Objects.requireNonNull(
                strategyManualTriggerService,
                "strategyManualTriggerService must not be null"
        );
    }

    @PostMapping
    public GateEStrategyDefinitionResponse create(
            @RequestBody GateEStrategyDefinitionCreateRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyDefinitionResponse.from(
                strategyDefinitionService.create(new StrategyDefinitionCreateRequest(
                        request.strategyCode(),
                        request.strategyName(),
                        request.strategyType(),
                        request.exchangeCode(),
                        request.accountId(),
                        request.tradeEnv(),
                        request.configSnapshot()
                ))
        ));
    }

    @GetMapping
    public List<GateEStrategyDefinitionResponse> list(
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> strategyDefinitionService.listAll()
                .stream()
                .map(GateEStrategyDefinitionResponse::from)
                .toList());
    }

    @GetMapping("/{strategyId}")
    public GateEStrategyDefinitionResponse detail(
            @PathVariable String strategyId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyDefinitionResponse.from(
                strategyDefinitionService.getByStrategyId(strategyId)
        ));
    }

    @PostMapping("/{strategyId}/enable")
    public GateEStrategyDefinitionResponse enable(
            @PathVariable String strategyId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyDefinitionResponse.from(
                strategyDefinitionService.enable(strategyId)
        ));
    }

    @PostMapping("/{strategyId}/disable")
    public GateEStrategyDefinitionResponse disable(
            @PathVariable String strategyId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyDefinitionResponse.from(
                strategyDefinitionService.disable(strategyId)
        ));
    }

    @PostMapping("/{strategyId}/trigger")
    public GateEStrategyManualTriggerResponse trigger(
            @PathVariable String strategyId,
            @RequestBody GateEStrategyManualTriggerRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> GateEStrategyManualTriggerResponse.from(
                strategyManualTriggerService.trigger(new StrategyManualTriggerRequest(
                        strategyId,
                        request.requestId(),
                        request.symbol(),
                        request.side(),
                        request.orderType(),
                        request.quantity(),
                        request.price(),
                        traceId
                ))
        ));
    }

    private String resolveTraceId(String primaryTraceId, String fallbackTraceId) {
        if (primaryTraceId != null && !primaryTraceId.isBlank()) {
            return primaryTraceId.trim();
        }
        if (fallbackTraceId != null && !fallbackTraceId.isBlank()) {
            return fallbackTraceId.trim();
        }
        return "trc-gatee-strategy-" + UUID.randomUUID();
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

package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.CancelOrderRequest;
import com.guidinglight.nexusquant.core.service.CancelOrderResult;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.PlaceOrderRequest;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
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
 * GateCAcceptanceController 提供 GateC 本地验收触发入口。
 * <p>
 * Why:
 * 当前仓库缺少合规入口去手动触发 place/cancel/reconcile/recovery，导致 Demo 验收必须旁路。
 * 该 controller 只在 `local` 或 `gatec-verify` profile 启用，且只做参数校验与 trace 透传，
 * 真正的业务仍全部委托给 core/scheduler/recovery 服务层。
 */
@RestController
@Profile({"local", "gatec-verify"})
@ConditionalOnProperty(name = "nq.gatec.acceptance.enabled", havingValue = "true")
@RequestMapping("/__gatec")
public class GateCAcceptanceController {

    private static final int DEFAULT_RECONCILE_LIMIT = 100;
    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final OrderCommandService orderCommandService;
    private final OkxRestReconcileService okxRestReconcileService;
    private final RecoveryService recoveryService;

    /**
     * @param orderCommandService     订单编排服务
     * @param okxRestReconcileService OKX REST reconcile 服务
     * @param recoveryService         恢复服务
     */
    public GateCAcceptanceController(
            OrderCommandService orderCommandService,
            OkxRestReconcileService okxRestReconcileService,
            RecoveryService recoveryService
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.okxRestReconcileService = Objects.requireNonNull(
                okxRestReconcileService,
                "okxRestReconcileService must not be null"
        );
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
    }

    /**
     * 触发一次下单编排。
     * <p>
     * Why:
     * 本地验收必须走 OrderCommandService，才能同时覆盖幂等、状态机、event_store 与审计链路。
     *
     * @param request         下单请求体
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 触发结果摘要
     */
    @PostMapping("/orders")
    public GateCTriggerResponse placeOrder(
            @RequestBody GateCOrderHttpRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            validateOrderRequest(request);
            PlaceOrderResult result = orderCommandService.placeOrder(new PlaceOrderRequest(
                    request.accountId(),
                    request.strategyRunId(),
                    request.venue(),
                    request.clientOrderId(),
                    request.symbol(),
                    request.side(),
                    request.type(),
                    request.price(),
                    request.qty(),
                    traceId
            ));
            return new GateCTriggerResponse(
                    "placeOrder",
                    traceId,
                    "order_id=" + result.orderId() + ", status=" + result.status() + ", idempotent_hit=" + result.idempotentHit()
            );
        });
    }

    /**
     * 触发一次撤单编排。
     *
     * @param request         撤单请求体
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 触发结果摘要
     */
    @PostMapping("/orders/cancel")
    public GateCTriggerResponse cancelOrder(
            @RequestBody GateCCancelOrderHttpRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            validateCancelRequest(request);
            CancelOrderResult result = orderCommandService.cancelOrder(new CancelOrderRequest(
                    blankToNull(request.orderId()),
                    request.accountId(),
                    blankToNull(request.clientOrderId()),
                    request.reason().trim(),
                    traceId
            ));
            return new GateCTriggerResponse(
                    "cancelOrder",
                    traceId,
                    "order_id=" + result.orderId() + ", status=" + result.status() + ", idempotent_hit=" + result.idempotentHit()
            );
        });
    }

    /**
     * 触发一次 REST reconcile。
     *
     * @param request         reconcile 请求；可为空
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 触发结果摘要
     */
    @PostMapping("/reconcile/runOnce")
    public GateCTriggerResponse runReconcile(
            @RequestBody(required = false) GateCReconcileRunOnceHttpRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            int limit = request == null || request.limit() == null ? DEFAULT_RECONCILE_LIMIT : request.limit();
            if (limit <= 0) {
                throw badRequest("limit must be positive");
            }
            int newTrades = okxRestReconcileService.reconcileOnce(limit);
            return new GateCTriggerResponse(
                    "reconcileOnce",
                    traceId,
                    "limit=" + limit + ", new_trades=" + newTrades
            );
        });
    }

    /**
     * 触发一次恢复流程。
     *
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 触发结果摘要
     */
    @PostMapping("/recovery/runOnce")
    public GateCTriggerResponse runRecovery(
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            RecoveryReport report = recoveryService.rebuild(traceId);
            return new GateCTriggerResponse(
                    "recoveryRunOnce",
                    traceId,
                    "processed_events=" + report.processedEventCount()
                            + ", processed_ledger=" + report.processedLedgerCount()
                            + ", invalid_transitions=" + report.invalidTransitionCount()
            );
        });
    }

    private void validateOrderRequest(GateCOrderHttpRequest request) {
        if (request == null) {
            throw badRequest("request body must not be null");
        }
        if (request.accountId() == null || request.accountId() <= 0) {
            throw badRequest("accountId must be positive");
        }
        requireText(request.venue(), "venue");
        requireText(request.clientOrderId(), "clientOrderId");
        requireText(request.symbol(), "symbol");
        if (request.side() == null) {
            throw badRequest("side must not be null");
        }
        if (request.type() == null) {
            throw badRequest("type must not be null");
        }
        if (request.qty() == null || request.qty().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("qty must be positive");
        }
        if (request.type().name().equals("LIMIT")
                && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) {
            throw badRequest("price must be positive for LIMIT");
        }
    }

    private void validateCancelRequest(GateCCancelOrderHttpRequest request) {
        if (request == null) {
            throw badRequest("request body must not be null");
        }
        requireText(request.reason(), "reason");
        boolean hasOrderId = request.orderId() != null && !request.orderId().isBlank();
        boolean hasLocator = request.accountId() != null
                && request.accountId() > 0
                && request.clientOrderId() != null
                && !request.clientOrderId().isBlank();
        if (!hasOrderId && !hasLocator) {
            throw badRequest("either orderId or accountId + clientOrderId must be provided");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + " must not be blank");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String resolveTraceId(String primaryTraceId, String fallbackTraceId) {
        String candidate = primaryTraceId != null && !primaryTraceId.isBlank() ? primaryTraceId : fallbackTraceId;
        if (candidate != null && !candidate.isBlank()) {
            return candidate.trim();
        }
        return "trc-gatec-" + UUID.randomUUID();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> T withTrace(String traceId, java.util.function.Supplier<T> action) {
        MDC.put("trace_id", traceId);
        try {
            return action.get();
        } finally {
            MDC.remove("trace_id");
        }
    }
}

package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.api.model.AccountView;
import com.guidinglight.nexusquant.api.model.OrderView;
import com.guidinglight.nexusquant.api.model.PositionView;
import com.guidinglight.nexusquant.api.model.TradeView;
import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.CancelOrderRequest;
import com.guidinglight.nexusquant.core.service.CancelOrderResult;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.PlaceOrderRequest;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;

import java.math.BigDecimal;
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
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;

/**
 * GateDAcceptanceController 提供 GateD 本地验收触发与最小查询入口。
 * <p>
 * Why:
 * 第四批开始清理 GateC 兼容层，因此 controller 现在只暴露 canonical 的 `/__gated`；
 * 所有业务动作仍委托给 core / scheduler / recovery / nq-api，自身只负责参数校验、trace 透传与
 * 最小 HTTP 语义转换。
 */
@RestController
@Profile("local")
@ConditionalOnProperty(name = "nq.gated.verify.enabled", havingValue = "true")
@RequestMapping("/__gated")
public class GateDAcceptanceController {

    private static final int DEFAULT_RECONCILE_LIMIT = 100;
    private static final String PRIMARY_TRACE_HEADER = "X-NQ-TRACE-ID";
    private static final String FALLBACK_TRACE_HEADER = "X-Trace-Id";

    private final OrderCommandService orderCommandService;
    private final TradingQueryFacade tradingQueryFacade;
    private final OkxRestReconcileService okxRestReconcileService;
    private final BinanceRestReconcileService binanceRestReconcileService;
    private final RecoveryService recoveryService;

    /**
     * @param orderCommandService         订单编排服务
     * @param okxRestReconcileService     OKX REST reconcile 服务
     * @param binanceRestReconcileService Binance REST reconcile 服务
     * @param recoveryService             恢复服务
     */
    public GateDAcceptanceController(
            OrderCommandService orderCommandService,
            TradingQueryFacade tradingQueryFacade,
            OkxRestReconcileService okxRestReconcileService,
            BinanceRestReconcileService binanceRestReconcileService,
            RecoveryService recoveryService
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.tradingQueryFacade = Objects.requireNonNull(tradingQueryFacade, "tradingQueryFacade must not be null");
        this.okxRestReconcileService = Objects.requireNonNull(
                okxRestReconcileService,
                "okxRestReconcileService must not be null"
        );
        this.binanceRestReconcileService = Objects.requireNonNull(
                binanceRestReconcileService,
                "binanceRestReconcileService must not be null"
        );
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
    }

    /**
     * 查询订单最小读视图。
     * <p>
     * Why:
     * GateD 第四批继续把最小查询闭环扩展到 trade / position / account，但订单视图仍然是
     * 本地 smoke 的首要核验项，因此保留在验收 controller 中。
     *
     * @param orderId         系统订单 ID
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 订单最小读视图
     */
    @GetMapping("/orders/{orderId}")
    public OrderView queryOrder(
            @PathVariable String orderId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            if (orderId == null || orderId.isBlank()) {
                throw badRequest("orderId must not be blank");
            }
            return tradingQueryFacade.queryOrder(orderId, traceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found: " + orderId));
        });
    }

    /**
     * 查询订单最近一笔成交视图。
     *
     * @param orderId         系统订单 ID
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 最近一笔成交视图
     */
    @GetMapping("/orders/{orderId}/trade")
    public TradeView queryLatestTrade(
            @PathVariable String orderId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            if (orderId == null || orderId.isBlank()) {
                throw badRequest("orderId must not be blank");
            }
            return tradingQueryFacade.queryLatestTrade(orderId, traceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "trade not found for order: " + orderId));
        });
    }

    /**
     * 查询账户在指定 symbol 上的最小持仓视图。
     *
     * @param accountId       账户 ID
     * @param symbol          交易对
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 持仓最小读视图
     */
    @GetMapping("/positions/{accountId}/{symbol}")
    public PositionView queryPosition(
            @PathVariable Long accountId,
            @PathVariable String symbol,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            if (accountId == null || accountId <= 0) {
                throw badRequest("accountId must be positive");
            }
            if (symbol == null || symbol.isBlank()) {
                throw badRequest("symbol must not be blank");
            }
            return tradingQueryFacade.queryPosition(accountId, symbol, traceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "position not found: accountId=" + accountId + ", symbol=" + symbol
                    ));
        });
    }

    /**
     * 查询账户最新余额快照集合。
     *
     * @param accountId       账户 ID
     * @param primaryTraceId  首选 trace header
     * @param fallbackTraceId 兼容 trace header
     * @return 账户最新余额视图
     */
    @GetMapping("/accounts/{accountId}")
    public AccountView queryAccount(
            @PathVariable Long accountId,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            if (accountId == null || accountId <= 0) {
                throw badRequest("accountId must be positive");
            }
            return tradingQueryFacade.queryAccount(accountId, traceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "account snapshot not found: accountId=" + accountId
                    ));
        });
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
    public GateDTriggerResponse placeOrder(
            @RequestBody GateDOrderHttpRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            validateOrderRequest(request);
            PlaceOrderResult result = orderCommandService.placeOrder(new PlaceOrderRequest(
                    buildRequestId("place", request.clientOrderId()),
                    request.accountId(),
                    request.strategyRunId(),
                    request.venue(),
                    request.symbol(),
                    request.clientOrderId(),
                    buildPlaceIdempotencyKey(request.accountId(), request.clientOrderId()),
                    "manual",
                    request.side(),
                    request.orderType(),
                    request.price(),
                    request.quantity(),
                    defaultTimeInForce(request.orderType()),
                    traceId
            ));
            return new GateDTriggerResponse(
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
    public GateDTriggerResponse cancelOrder(
            @RequestBody GateDCancelOrderHttpRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            validateCancelRequest(request);
            CancelOrderResult result = orderCommandService.cancelOrder(new CancelOrderRequest(
                    buildRequestId("cancel", request.orderId() != null ? request.orderId() : request.clientOrderId()),
                    blankToNull(request.orderId()),
                    request.accountId(),
                    null,
                    null,
                    blankToNull(request.clientOrderId()),
                    null,
                    request.reason().trim(),
                    traceId
            ));
            return new GateDTriggerResponse(
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
    public GateDTriggerResponse runReconcile(
            @RequestBody(required = false) GateDReconcileRunOnceHttpRequest request,
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            int limit = request == null || request.limit() == null ? DEFAULT_RECONCILE_LIMIT : request.limit();
            if (limit <= 0) {
                throw badRequest("limit must be positive");
            }
            String venue = request == null || request.venue() == null || request.venue().isBlank()
                    ? "OKX"
                    : request.venue().trim().toUpperCase();
            int newTrades = switch (venue) {
                case "OKX" -> okxRestReconcileService.reconcileOnce(limit);
                case "BINANCE" -> binanceRestReconcileService.reconcileOnce(limit);
                default -> throw badRequest("unsupported reconcile venue: " + venue);
            };
            return new GateDTriggerResponse(
                    "reconcileOnce",
                    traceId,
                    "venue=" + venue + ", limit=" + limit + ", new_trades=" + newTrades
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
    public GateDTriggerResponse runRecovery(
            @RequestHeader(value = PRIMARY_TRACE_HEADER, required = false) String primaryTraceId,
            @RequestHeader(value = FALLBACK_TRACE_HEADER, required = false) String fallbackTraceId
    ) {
        String traceId = resolveTraceId(primaryTraceId, fallbackTraceId);
        return withTrace(traceId, () -> {
            RecoveryReport report = recoveryService.rebuild(traceId);
            return new GateDTriggerResponse(
                    "recoveryRunOnce",
                    traceId,
                    "processed_events=" + report.processedEventCount()
                            + ", processed_ledger=" + report.processedLedgerCount()
                            + ", invalid_transitions=" + report.invalidTransitionCount()
            );
        });
    }

    private void validateOrderRequest(GateDOrderHttpRequest request) {
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
        if (request.orderType() == null) {
            throw badRequest("orderType must not be null");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("quantity must be positive");
        }
        if (request.orderType().name().equals("LIMIT")
                && (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0)) {
            throw badRequest("price must be positive for LIMIT");
        }
    }

    private void validateCancelRequest(GateDCancelOrderHttpRequest request) {
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
        return "trc-gated-" + UUID.randomUUID();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildRequestId(String action, String businessKey) {
        String suffix = businessKey == null || businessKey.isBlank() ? UUID.randomUUID().toString() : businessKey.trim();
        return "req-gated-" + action + "-" + suffix;
    }

    private String buildPlaceIdempotencyKey(Long accountId, String clientOrderId) {
        return accountId + ":" + clientOrderId.trim();
    }

    private String defaultTimeInForce(OrderType orderType) {
        return orderType == OrderType.MARKET ? "IOC" : "GTC";
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

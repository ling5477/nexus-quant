package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.api.model.AccountView;
import com.guidinglight.nexusquant.api.model.OrderView;
import com.guidinglight.nexusquant.api.model.PositionView;
import com.guidinglight.nexusquant.api.model.TradeView;
import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.CancelOrderRequest;
import com.guidinglight.nexusquant.core.service.CancelOrderResult;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.PlaceOrderRequest;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;
import com.guidinglight.nexusquant.scheduler.service.BinanceRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * TradingVerificationController 提供正式交易运行触发与最小查询接口。
 * <p>
 * Why:
 * Step 4 要把 trace 入口统一交给过滤器，不再让 Controller 手工解析 header 或包裹 MDC。
 */
@Validated
@RestController
@RequestMapping("/api/trading")
@Tag(name = "Trading API", description = "正式交易运行触发、恢复、对账与最小查询接口。")
public class TradingVerificationController {

    private static final int DEFAULT_RECONCILE_LIMIT = 100;

    private final OrderCommandService orderCommandService;
    private final TradingQueryFacade tradingQueryFacade;
    private final OkxRestReconcileService okxRestReconcileService;
    private final BinanceRestReconcileService binanceRestReconcileService;
    private final BinanceRecoveryService binanceRecoveryService;
    private final RecoveryService recoveryService;

    public TradingVerificationController(
            OrderCommandService orderCommandService,
            TradingQueryFacade tradingQueryFacade,
            OkxRestReconcileService okxRestReconcileService,
            BinanceRestReconcileService binanceRestReconcileService,
            BinanceRecoveryService binanceRecoveryService,
            RecoveryService recoveryService
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.tradingQueryFacade = Objects.requireNonNull(tradingQueryFacade, "tradingQueryFacade must not be null");
        this.okxRestReconcileService = Objects.requireNonNull(okxRestReconcileService, "okxRestReconcileService must not be null");
        this.binanceRestReconcileService = Objects.requireNonNull(
                binanceRestReconcileService,
                "binanceRestReconcileService must not be null"
        );
        this.binanceRecoveryService = Objects.requireNonNull(binanceRecoveryService, "binanceRecoveryService must not be null");
        this.recoveryService = Objects.requireNonNull(recoveryService, "recoveryService must not be null");
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "查询订单视图", description = "查询单笔订单的最小读模型，不触发任何写操作。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "订单 ID 非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "订单不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OrderView queryOrder(@PathVariable @NotBlank(message = "orderId must not be blank") String orderId) {
        String traceId = TraceIdContext.getOrCreate();
        return tradingQueryFacade.queryOrder(orderId, traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found: " + orderId));
    }

    @GetMapping("/orders/{orderId}/trade")
    @Operation(summary = "查询最新成交", description = "查询订单最近一笔成交事实，不触发补偿或重试。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "订单 ID 非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "成交不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TradeView queryLatestTrade(@PathVariable @NotBlank(message = "orderId must not be blank") String orderId) {
        String traceId = TraceIdContext.getOrCreate();
        return tradingQueryFacade.queryLatestTrade(orderId, traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "trade not found for order: " + orderId));
    }

    @GetMapping("/positions/{accountId}/{symbol}")
    @Operation(summary = "查询持仓视图", description = "按账户和交易对查询最小持仓读视图，不触发任何状态推进。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "持仓不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PositionView queryPosition(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @PathVariable @NotBlank(message = "symbol must not be blank") String symbol
    ) {
        String traceId = TraceIdContext.getOrCreate();
        return tradingQueryFacade.queryPosition(accountId, symbol, traceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "position not found: accountId=" + accountId + ", symbol=" + symbol
                ));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "查询账户快照", description = "返回账户当前的最小余额快照，用于核对事实链。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "账户 ID 非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户快照不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AccountView queryAccount(@PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        String traceId = TraceIdContext.getOrCreate();
        return tradingQueryFacade.queryAccount(accountId, traceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "account snapshot not found: accountId=" + accountId
                ));
    }

    @PostMapping("/orders")
    @Operation(summary = "触发下单编排", description = "触发一次正式下单流程，会进入订单编排、幂等和状态机链路。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "触发成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "业务状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OperationTriggerResponse placeOrder(@Valid @RequestBody OrderSubmitRequest request) {
        String traceId = TraceIdContext.getOrCreate();
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
        return new OperationTriggerResponse(
                "placeOrder",
                traceId,
                "order_id=" + result.orderId() + ", status=" + result.status() + ", idempotent_hit=" + result.idempotentHit()
        );
    }

    @PostMapping("/orders/cancel")
    @Operation(summary = "触发撤单编排", description = "触发一次撤单流程。必须提供 orderId，或提供 accountId 与 clientOrderId 组合。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "触发成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "业务状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OperationTriggerResponse cancelOrder(@Valid @RequestBody OrderCancelRequestBody request) {
        String traceId = TraceIdContext.getOrCreate();
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
        return new OperationTriggerResponse(
                "cancelOrder",
                traceId,
                "order_id=" + result.orderId() + ", status=" + result.status() + ", idempotent_hit=" + result.idempotentHit()
        );
    }

    @PostMapping("/reconciliation/run-once")
    @Operation(summary = "触发 reconciliation", description = "触发一次 OKX 或 Binance 的 reconcile 扫描，不会直接重复下单。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "触发成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OperationTriggerResponse runReconcile(@Valid @RequestBody(required = false) ReconcileRunOnceRequest request) {
        String traceId = TraceIdContext.getOrCreate();
        int limit = request == null || request.limit() == null ? DEFAULT_RECONCILE_LIMIT : request.limit();
        String venue = request == null || request.venue() == null || request.venue().isBlank()
                ? "OKX"
                : request.venue().trim().toUpperCase();
        int newTrades = switch (venue) {
            case "OKX" -> okxRestReconcileService.reconcileOnce(limit);
            case "BINANCE" -> binanceRestReconcileService.reconcileOnce(limit);
            default -> throw new IllegalArgumentException("unsupported reconcile venue: " + venue);
        };
        return new OperationTriggerResponse(
                "reconcileOnce",
                traceId,
                "venue=" + venue + ", limit=" + limit + ", new_trades=" + newTrades
        );
    }

    @PostMapping("/recovery/run-once")
    @Operation(summary = "触发 recovery", description = "按 venue 触发一次恢复流程。未知状态时只做确认与恢复，不做盲重试。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "触发成功"),
            @ApiResponse(responseCode = "400", description = "请求参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "系统内部错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OperationTriggerResponse runRecovery(@Valid @RequestBody(required = false) RecoveryRunOnceRequest request) {
        String traceId = TraceIdContext.getOrCreate();
        String venue = request == null || request.venue() == null || request.venue().isBlank()
                ? "OKX"
                : request.venue().trim().toUpperCase();
        RecoveryReport report = switch (venue) {
            case "OKX" -> recoveryService.rebuild(traceId);
            case "BINANCE" -> binanceRecoveryService.rebuild(traceId);
            default -> throw new IllegalArgumentException("unsupported recovery venue: " + venue);
        };
        return new OperationTriggerResponse(
                "recoveryRunOnce",
                traceId,
                "venue=" + venue
                        + ", processed_events=" + report.processedEventCount()
                        + ", processed_ledger=" + report.processedLedgerCount()
                        + ", invalid_transitions=" + report.invalidTransitionCount()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildRequestId(String action, String subject) {
        return action + "-" + (subject == null || subject.isBlank() ? "unknown" : subject.trim());
    }

    private String buildPlaceIdempotencyKey(Long accountId, String clientOrderId) {
        return accountId + ":" + clientOrderId;
    }

    private String defaultTimeInForce(OrderType orderType) {
        return orderType == OrderType.MARKET ? "IOC" : "GTC";
    }
}

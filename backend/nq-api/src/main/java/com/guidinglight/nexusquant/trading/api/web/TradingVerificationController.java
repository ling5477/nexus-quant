package com.guidinglight.nexusquant.trading.api.web;

import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.CancelOrderResult;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderResult;
import com.guidinglight.nexusquant.trading.application.RecoveryReport;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceService;
import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * TradingVerificationController 提供正式交易运行触发与最小查询接口。
 * <p>
 * Why:
 * PRE-3 之前，前端已经切换到 `exchangeAccountId` 作为正式账户上下文主键，
 * 但 trading 表仍然保留 legacy account_id。这里先做兼容映射：controller 接收正式账户上下文，
 * 内部再解析到当前 trading 链路仍使用的 legacy account id，避免继续把兼容字段暴露给前端。
 */
@Validated
@RestController
@RequestMapping("/api/trading")
@Tag(name = "Trading API", description = "正式交易运行触发、恢复、对账与最小查询接口。")
public class TradingVerificationController {

    private static final int DEFAULT_RECONCILE_LIMIT = 100;

    private final OrderCommandService orderCommandService;
    private final TradingQueryFacade tradingQueryFacade;
    private final TradingMaintenanceService tradingMaintenanceService;
    private final ExchangeAccountQueryService exchangeAccountQueryService;

    public TradingVerificationController(
            OrderCommandService orderCommandService,
            TradingQueryFacade tradingQueryFacade,
            TradingMaintenanceService tradingMaintenanceService,
            ExchangeAccountQueryService exchangeAccountQueryService
    ) {
        this.orderCommandService = Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.tradingQueryFacade = Objects.requireNonNull(tradingQueryFacade, "tradingQueryFacade must not be null");
        this.tradingMaintenanceService = Objects.requireNonNull(
                tradingMaintenanceService,
                "tradingMaintenanceService must not be null"
        );
        this.exchangeAccountQueryService = Objects.requireNonNull(
                exchangeAccountQueryService,
                "exchangeAccountQueryService must not be null"
        );
    }

    @GetMapping("/orders")
    @Operation(summary = "查询交易工作台订单列表", description = "按正式账户上下文查询订单列表，不触发任何写操作。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "查询参数非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "账户上下文不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OrderListResponse listOrders(
            @RequestParam @Positive(message = "accountId must be positive") Long accountId,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false, name = "environment") String environment,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be positive") @Max(value = 100, message = "size must not exceed 100") int size
    ) {
        String traceId = TraceIdContext.getOrCreate();
        ExchangeAccountSummary account = requireExchangeAccount(accountId);
        String resolvedVenue = resolveVenueFilter(venue, account);
        String resolvedEnvironment = resolveEnvironmentFilter(environment, account);
        Long tradingAccountId = resolveTradingAccountId(account);
        List<OrderView> items = tradingQueryFacade.listOrders(
                tradingAccountId,
                blankToNull(orderId),
                resolvedVenue,
                blankToNull(symbol),
                status,
                resolvedEnvironment,
                page,
                size,
                traceId
        ).stream().map(this::toOrderView).toList();
        long total = tradingQueryFacade.countOrders(
                tradingAccountId,
                blankToNull(orderId),
                resolvedVenue,
                blankToNull(symbol),
                status,
                resolvedEnvironment,
                traceId
        );
        return new OrderListResponse(items, page, size, total);
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
                .map(this::toOrderView)
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
                .map(queryView -> new TradeView(
                        queryView.tradeId(),
                        queryView.orderId(),
                        queryView.accountId(),
                        queryView.venue(),
                        queryView.symbol(),
                        queryView.externalOrderId(),
                        queryView.exchangeTradeId(),
                        queryView.price(),
                        queryView.quantity(),
                        queryView.fee(),
                        queryView.feeCurrency(),
                        queryView.tradeTs(),
                        queryView.traceId()
                ))
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
        Long tradingAccountId = resolveTradingAccountId(accountId);
        return tradingQueryFacade.queryPosition(tradingAccountId, symbol, traceId)
                .map(queryView -> new PositionView(
                        queryView.accountId(),
                        queryView.venue(),
                        queryView.symbol(),
                        queryView.quantity(),
                        queryView.availableQuantity(),
                        queryView.avgPrice(),
                        queryView.traceId()
                ))
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
        Long tradingAccountId = resolveTradingAccountId(accountId);
        return tradingQueryFacade.queryAccount(tradingAccountId, traceId)
                .map(queryView -> new AccountView(
                        queryView.accountId(),
                        queryView.venue(),
                        queryView.balances().stream()
                                .map(balance -> new AccountBalanceView(
                                        balance.currency(),
                                        balance.balance(),
                                        balance.available(),
                                        balance.frozen(),
                                        balance.snapshotTs(),
                                        balance.traceId()
                                ))
                                .toList(),
                        queryView.traceId()
                ))
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
        Long tradingAccountId = resolveTradingAccountId(request.accountId());
        PlaceOrderResult result = orderCommandService.placeOrder(new PlaceOrderRequest(
                buildRequestId("place", request.clientOrderId()),
                tradingAccountId,
                request.strategyRunId(),
                request.venue(),
                request.symbol(),
                request.clientOrderId(),
                buildPlaceIdempotencyKey(tradingAccountId, request.clientOrderId()),
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
        Long tradingAccountId = request.accountId() == null ? null : resolveTradingAccountId(request.accountId());
        CancelOrderResult result = orderCommandService.cancelOrder(new CancelOrderRequest(
                buildRequestId("cancel", request.orderId() != null ? request.orderId() : request.clientOrderId()),
                blankToNull(request.orderId()),
                tradingAccountId,
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
        int newTrades = tradingMaintenanceService.runReconcile(venue, limit);
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
        RecoveryReport report = tradingMaintenanceService.runRecovery(venue, traceId);
        return new OperationTriggerResponse(
                "recoveryRunOnce",
                traceId,
                "venue=" + venue
                        + ", processed_events=" + report.processedEventCount()
                        + ", processed_ledger=" + report.processedLedgerCount()
                        + ", invalid_transitions=" + report.invalidTransitionCount()
        );
    }

    /**
     * 将正式 exchangeAccountId 解析为当前 trading 链路仍需使用的历史 account_id。
     * Why: 这是过渡兼容解析，不是正式主上下文；前端和新 API 语义必须继续使用 exchangeAccountId。
     */
    private Long resolveTradingAccountId(Long requestedAccountId) {
        if (requestedAccountId == null || requestedAccountId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId must be positive");
        }
        return resolveTradingAccountId(requireExchangeAccount(requestedAccountId));
    }

    /**
     * 强制把 HTTP 账户上下文限定为已登记的 exchange account。
     *
     * <p>Why: GateH-1 后 `/trading` 不能再把任意数字当 legacy accountId 使用，
     * 否则前端账户上下文、SIM / LIVE 边界和后端查询会重新分叉。</p>
     */
    private ExchangeAccountSummary requireExchangeAccount(Long exchangeAccountId) {
        return exchangeAccountQueryService.findById(exchangeAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "exchange account not found: " + exchangeAccountId));
    }

    private Long resolveTradingAccountId(ExchangeAccountSummary summary) {
        return summary.legacyAccountId() == null ? summary.exchangeAccountId() : summary.legacyAccountId();
    }

    private String resolveVenueFilter(String requestedVenue, ExchangeAccountSummary account) {
        String normalized = blankToNull(requestedVenue);
        if (normalized == null) {
            return account.exchangeCode();
        }
        if (!normalized.equalsIgnoreCase(account.exchangeCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venue does not match account context");
        }
        return normalized.toUpperCase();
    }

    private String resolveEnvironmentFilter(String requestedEnvironment, ExchangeAccountSummary account) {
        String normalized = blankToNull(requestedEnvironment);
        if (normalized == null) {
            return account.tradeEnv();
        }
        if (!normalized.equalsIgnoreCase(account.tradeEnv())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "environment does not match account context");
        }
        return normalized.toUpperCase();
    }

    private OrderView toOrderView(com.guidinglight.nexusquant.trading.application.query.OrderQueryView queryView) {
        return new OrderView(
                queryView.orderId(),
                queryView.accountId(),
                queryView.venue(),
                queryView.symbol(),
                queryView.clientOrderId(),
                queryView.externalOrderId(),
                queryView.side(),
                queryView.type(),
                queryView.price(),
                queryView.quantity(),
                queryView.status(),
                queryView.tradeEnv(),
                queryView.createdAt(),
                queryView.updatedAt(),
                queryView.traceId()
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

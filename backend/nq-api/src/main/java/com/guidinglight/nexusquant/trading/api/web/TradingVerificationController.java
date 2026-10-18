package com.guidinglight.nexusquant.trading.api.web;

import com.guidinglight.nexusquant.account.application.service.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.AccountTradeEnvironmentException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.api.web.dto.ApiErrorResponse;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.trading.api.dto.AccountBalanceView;
import com.guidinglight.nexusquant.trading.api.dto.AccountView;
import com.guidinglight.nexusquant.trading.api.dto.OperationTriggerResponse;
import com.guidinglight.nexusquant.trading.api.dto.OrderCancelRequestBody;
import com.guidinglight.nexusquant.trading.api.dto.OrderListResponse;
import com.guidinglight.nexusquant.trading.api.dto.OrderSubmitRequest;
import com.guidinglight.nexusquant.trading.api.dto.OrderView;
import com.guidinglight.nexusquant.trading.api.dto.PositionView;
import com.guidinglight.nexusquant.trading.api.dto.ReconcileRunOnceRequest;
import com.guidinglight.nexusquant.trading.api.dto.RecoveryRunOnceRequest;
import com.guidinglight.nexusquant.trading.api.dto.TradeView;
import com.guidinglight.nexusquant.trading.application.command.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.command.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations.Kind;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations.Receipt;
import com.guidinglight.nexusquant.trading.application.query.OrderQueryView;
import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import com.guidinglight.nexusquant.trading.application.result.CancelOrderResult;
import com.guidinglight.nexusquant.trading.application.result.PlaceOrderResult;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandService;
import com.guidinglight.nexusquant.trading.domain.TradingVenue;

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
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TradingVerificationController 提供正式交易运行触发与最小查询接口。
 *
 * <p>Why: PRE-3 之前，前端已经切换到 `exchangeAccountId` 作为正式账户上下文主键， 但 trading 表仍然保留 legacy
 * account_id。这里先做兼容映射：controller 接收正式账户上下文， 内部再解析到当前 trading 链路仍使用的 legacy account
 * id，避免继续把兼容字段暴露给前端。
 */
@Validated
@RestController
@ConditionalOnProperty(
        prefix = "nq.runtime.trading-components",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@RequestMapping("/api/trading")
@Tag(name = "Trading API", description = "正式交易运行触发、恢复、对账与最小查询接口。")
public class TradingVerificationController {

    private final OrderCommandService orderCommandService;
    private final TradingQueryFacade tradingQueryFacade;
    private final TradingMaintenanceOperations tradingMaintenanceOperations;
    private final ExchangeAccountQueryService exchangeAccountQueryService;
    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;

    public TradingVerificationController(
            OrderCommandService orderCommandService,
            TradingQueryFacade tradingQueryFacade,
            TradingMaintenanceOperations tradingMaintenanceOperations,
            ExchangeAccountQueryService exchangeAccountQueryService,
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService) {
        this.orderCommandService =
                Objects.requireNonNull(orderCommandService, "orderCommandService must not be null");
        this.tradingQueryFacade =
                Objects.requireNonNull(tradingQueryFacade, "tradingQueryFacade must not be null");
        this.tradingMaintenanceOperations =
                Objects.requireNonNull(
                        tradingMaintenanceOperations, "tradingMaintenanceOperations must not be null");
        this.exchangeAccountQueryService =
                Objects.requireNonNull(
                        exchangeAccountQueryService,
                        "exchangeAccountQueryService must not be null");
        this.gatewayAuthFacade =
                Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService =
                Objects.requireNonNull(
                        currentUserProfileService, "currentUserProfileService must not be null");
    }

    @GetMapping("/orders")
    @Operation(summary = "查询交易工作台订单列表", description = "按正式账户上下文查询订单列表，不触发任何写操作。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(
                responseCode = "400",
                description = "查询参数非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "账户上下文不存在",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OrderListResponse listOrders(
            @RequestParam @Positive(message = "accountId must be positive") Long accountId,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false, name = "environment") String environment,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative")
                    int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "size must be positive")
                    @Max(value = 100, message = "size must not exceed 100")
                    int size) {
        String traceId = TraceIdContext.getOrCreate();
        ExchangeAccountSummary account = requireExchangeAccount(accountId);
        String resolvedVenue = resolveVenueFilter(venue, account);
        String resolvedEnvironment = resolveEnvironmentFilter(environment, account);
        Long tradingAccountId = resolveTradingAccountId(account);
        List<OrderView> items =
                tradingQueryFacade
                        .listOrders(
                                tradingAccountId,
                                blankToNull(orderId),
                                resolvedVenue,
                                blankToNull(symbol),
                                status,
                                resolvedEnvironment,
                                page,
                                size,
                                traceId)
                        .stream()
                        .map(this::toOrderView)
                        .toList();
        long total =
                tradingQueryFacade.countOrders(
                        tradingAccountId,
                        blankToNull(orderId),
                        resolvedVenue,
                        blankToNull(symbol),
                        status,
                        resolvedEnvironment,
                        traceId);
        return new OrderListResponse(items, page, size, total);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "查询订单视图", description = "查询单笔订单的最小读模型，不触发任何写操作。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(
                responseCode = "400",
                description = "订单 ID 非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "订单不存在",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OrderView queryOrder(
            @PathVariable @NotBlank(message = "orderId must not be blank") String orderId) {
        String traceId = TraceIdContext.getOrCreate();
        return toOrderView(requireOwnedOrder(orderId, traceId));
    }

    /** 原客户端身份只读定位，不把404解释为未执行，也不自动提交任何委托。 */
    @GetMapping("/orders/by-client")
    @Operation(summary = "按当前用户账户与客户端幂等身份查询原订单")
    public OrderView queryOrderByClientIdentity(
            @RequestParam @Positive Long accountId,
            @RequestParam @NotBlank @Size(max = 256) String clientOrderId) {
        String traceId = TraceIdContext.getOrCreate();
        ExchangeAccountSummary account = requireExchangeAccount(accountId);
        return tradingQueryFacade
                .queryOrderByClientIdentity(
                        resolveTradingAccountId(account),
                        account.exchangeCode(),
                        account.tradeEnv(),
                        clientOrderId,
                        traceId)
                .map(this::toOrderView)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }

    @GetMapping("/orders/{orderId}/trade")
    @Operation(summary = "查询最新成交", description = "查询订单最近一笔成交事实，不触发补偿或重试。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(
                responseCode = "400",
                description = "订单 ID 非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "成交不存在",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TradeView queryLatestTrade(
            @PathVariable @NotBlank(message = "orderId must not be blank") String orderId) {
        String traceId = TraceIdContext.getOrCreate();
        requireOwnedOrder(orderId, traceId);
        return tradingQueryFacade
                .queryLatestTrade(orderId, traceId)
                .map(
                        queryView ->
                                new TradeView(
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
                                        queryView.traceId()))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "trade not found for order: " + orderId));
    }

    @GetMapping("/positions/{accountId}/{symbol}")
    @Operation(summary = "查询持仓视图", description = "按账户和交易对查询最小持仓读视图，不触发任何状态推进。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(
                responseCode = "400",
                description = "参数非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "持仓不存在",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PositionView queryPosition(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId,
            @PathVariable @NotBlank(message = "symbol must not be blank") String symbol) {
        String traceId = TraceIdContext.getOrCreate();
        Long tradingAccountId = resolveTradingAccountId(accountId);
        return tradingQueryFacade
                .queryPosition(tradingAccountId, symbol, traceId)
                .map(
                        queryView ->
                                new PositionView(
                                        queryView.accountId(),
                                        queryView.venue(),
                                        queryView.symbol(),
                                        queryView.quantity(),
                                        queryView.availableQuantity(),
                                        queryView.avgPrice(),
                                        queryView.traceId()))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "position not found: accountId="
                                                + accountId
                                                + ", symbol="
                                                + symbol));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "查询账户快照", description = "返回账户当前的最小余额快照，用于核对事实链。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(
                responseCode = "400",
                description = "账户 ID 非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "账户快照不存在",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AccountView queryAccount(
            @PathVariable @Positive(message = "accountId must be positive") Long accountId) {
        String traceId = TraceIdContext.getOrCreate();
        Long tradingAccountId = resolveTradingAccountId(accountId);
        return tradingQueryFacade
                .queryAccount(tradingAccountId, traceId)
                .map(
                        queryView ->
                                new AccountView(
                                        queryView.accountId(),
                                        queryView.venue(),
                                        queryView.balances().stream()
                                                .map(
                                                        balance ->
                                                                new AccountBalanceView(
                                                                        balance.currency(),
                                                                        balance.balance(),
                                                                        balance.available(),
                                                                        balance.frozen(),
                                                                        balance.snapshotTs(),
                                                                        balance.traceId(),
                                                                        balance.tradeEnv(),
                                                                        balance.balanceBasis(),
                                                                        balance.balanceScope(),
                                                                        balance.recordedAt()))
                                                .toList(),
                                        queryView.traceId()))
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "account snapshot not found: accountId=" + accountId));
    }

    @PostMapping("/orders")
    @Operation(summary = "触发下单编排", description = "触发一次正式下单流程，会进入订单编排、幂等和状态机链路。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "触发成功"),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "业务状态冲突",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "系统内部错误",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OperationTriggerResponse placeOrder(@Valid @RequestBody OrderSubmitRequest request) {
        String traceId = TraceIdContext.getOrCreate();
        ExchangeAccountSummary account =
                requireLiveDisabledMutatingAccount(request.accountId(), "placeOrder");
        if (account.legacyAccountId() == null) {
            throw new AccountTradeEnvironmentException("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE");
        }
        Long tradingAccountId = resolveTradingAccountId(account);
        PlaceOrderResult result =
                orderCommandService.placeOrder(
                        new PlaceOrderRequest(
                                buildRequestId("place", request.clientOrderId()),
                                tradingAccountId,
                                request.strategyRunId(),
                                resolveVenueFilter(request.venue(), account),
                                request.symbol(),
                                request.clientOrderId(),
                                buildPlaceIdempotencyKey(tradingAccountId, request.clientOrderId()),
                                "manual",
                                request.side(),
                                request.orderType(),
                                request.price(),
                                request.quantity(),
                                defaultTimeInForce(request.orderType()),
                                traceId,
                                account.tradeEnv(),
                                null));
        return new OperationTriggerResponse(
                "placeOrder",
                traceId,
                "order_id="
                        + result.orderId()
                        + ", status="
                        + result.status()
                        + ", idempotent_hit="
                        + result.idempotentHit());
    }

    @PostMapping("/orders/cancel")
    @Operation(
            summary = "触发撤单编排",
            description = "触发一次撤单流程。必须提供 orderId，或提供 accountId 与 clientOrderId 组合。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "触发成功"),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "业务状态冲突",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "系统内部错误",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OperationTriggerResponse cancelOrder(
            @Valid @RequestBody OrderCancelRequestBody request) {
        String traceId = TraceIdContext.getOrCreate();
        ExchangeAccountSummary requestedAccount =
                request.accountId() == null
                        ? null
                        : requireLiveDisabledMutatingAccount(request.accountId(), "cancelOrder");
        Long tradingAccountId =
                requestedAccount == null ? null : resolveTradingAccountId(requestedAccount);
        String orderId = blankToNull(request.orderId());
        if (orderId == null) {
            if (requestedAccount == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId is required");
            }
            // 先按完整登记范围定位，不能让命令服务以缺少交易所和环境的组合键重选存量订单。
            orderId =
                    tradingQueryFacade
                            .queryOrderByClientIdentity(
                                    tradingAccountId,
                                    requestedAccount.exchangeCode(),
                                    requestedAccount.tradeEnv(),
                                    request.clientOrderId(),
                                    traceId)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND, "order not found"))
                            .orderId();
        }
        OrderQueryView order = requireOwnedOrder(orderId, traceId);
        if ("LIVE".equalsIgnoreCase(order.tradeEnv())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "cancelOrder blocked: LIVE trading is disabled");
        }
        if (requestedAccount != null
                && (!Objects.equals(tradingAccountId, order.accountId())
                        || !requestedAccount.exchangeCode().equals(order.venue())
                        || !requestedAccount.tradeEnv().equals(order.tradeEnv()))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "order not found in account context");
        }
        CancelOrderResult result =
                orderCommandService.cancelOrder(
                        new CancelOrderRequest(
                                buildRequestId("cancel", order.orderId()),
                                order.orderId(),
                                order.accountId(),
                                order.venue(),
                                order.symbol(),
                                null,
                                null,
                                request.reason().trim(),
                                traceId));
        return new OperationTriggerResponse(
                "cancelOrder",
                traceId,
                "order_id="
                        + result.orderId()
                        + ", status="
                        + result.status()
                        + ", idempotent_hit="
                        + result.idempotentHit());
    }

    @PostMapping("/reconciliation/run-once")
    @Operation(
            summary = "触发 reconciliation",
            description = "触发一次 OKX 或 Binance 的 reconcile 扫描，不会直接重复下单。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "触发成功"),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "系统内部错误",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Receipt runReconcile(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody(required = false) ReconcileRunOnceRequest request) {
        return tradingMaintenanceOperations.submit(operationId, resolveCurrentUserId(), Kind.RECONCILE,
                request == null ? null : request.venue(), request == null ? null : request.limit(),
                TraceIdContext.getOrCreate());
    }

    @PostMapping("/recovery/run-once")
    @Operation(summary = "触发 recovery", description = "按 venue 触发一次恢复流程。未知状态时只做确认与恢复，不做盲重试。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "触发成功"),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数非法",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "系统内部错误",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public Receipt runRecovery(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody(required = false) RecoveryRunOnceRequest request) {
        return tradingMaintenanceOperations.submit(operationId, resolveCurrentUserId(), Kind.RECOVERY,
                request == null ? null : request.venue(), null, TraceIdContext.getOrCreate());
    }

    /** 原身份仅限提交人查询；未找到不能证明请求未执行，查询绝不触发扫描。 */
    @GetMapping("/maintenance/operations/{operationId}")
    public Receipt maintenanceResult(@PathVariable UUID operationId) {
        return tradingMaintenanceOperations.find(operationId, resolveCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "maintenance operation not found"));
    }

    /**
     * 将正式 exchangeAccountId 解析为当前 trading 链路仍需使用的历史 account_id。 Why: 这是过渡兼容解析，不是正式主上下文；前端和新 API
     * 语义必须继续使用 exchangeAccountId。
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
     * <p>Why: 历史行情接入后 `/trading` 不能再把任意数字当 legacy accountId 使用， 否则前端账户上下文、SIM / LIVE 边界和后端查询会重新分叉。
     */
    private ExchangeAccountSummary requireExchangeAccount(Long exchangeAccountId) {
        return exchangeAccountQueryService
                .findByIdForOwner(resolveCurrentUserId(), exchangeAccountId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "exchange account not found: " + exchangeAccountId));
    }

    /**
     * 对正式交易 mutating API 强制 LIVE disabled fail-closed。
     *
     * <p>Why: 当前适配器就绪策略仍禁止 LIVE 和真实交易所执行。即使账户上下文存在 `tradeEnv=LIVE`， HTTP 下单 / 撤单入口也不能把它传入 order
     * command service 形成真实交易授权。
     */
    private ExchangeAccountSummary requireLiveDisabledMutatingAccount(
            Long exchangeAccountId, String action) {
        ExchangeAccountSummary account = requireExchangeAccount(exchangeAccountId);
        if ("LIVE".equalsIgnoreCase(account.tradeEnv())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    action
                            + " blocked: LIVE trading is disabled and cannot authorize real"
                            + " mutating path");
        }
        return account;
    }

    private Long resolveTradingAccountId(ExchangeAccountSummary summary) {
        // 两套ID恰好相同不能作为归属依据；未建立正式桥接时保留不可确认状态。
        if (summary.legacyAccountId() == null)
            throw new AccountTradeEnvironmentException("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE");
        return summary.legacyAccountId();
    }

    /** 无权和不存在统一404；订单仍须通过真实登记的账户、交易所及环境确认归属。 */
    private OrderQueryView requireOwnedOrder(String orderId, String traceId) {
        OrderQueryView order =
                tradingQueryFacade
                        .queryOrder(orderId, traceId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "order not found"));
        if (!exchangeAccountQueryService.hasTradingScopeForOwner(
                resolveCurrentUserId(), order.accountId(), order.venue(), order.tradeEnv())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        return order;
    }

    private Long resolveCurrentUserId() {
        var currentUser =
                gatewayAuthFacade
                        .currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return currentUserProfileService
                .findByUsername(currentUser.username())
                .map(AuthUserProfile::userId)
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"));
    }

    private String resolveVenueFilter(String requestedVenue, ExchangeAccountSummary account) {
        String normalized = blankToNull(requestedVenue);
        if (normalized == null) {
            return account.exchangeCode();
        }
        if (!normalized.equalsIgnoreCase(account.exchangeCode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "venue does not match account context");
        }
        return normalized.toUpperCase();
    }

    private String resolveEnvironmentFilter(
            String requestedEnvironment, ExchangeAccountSummary account) {
        String normalized = blankToNull(requestedEnvironment);
        if (normalized == null) {
            return account.tradeEnv();
        }
        if (!normalized.equalsIgnoreCase(account.tradeEnv())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "environment does not match account context");
        }
        return normalized.toUpperCase();
    }

    private OrderView toOrderView(OrderQueryView queryView) {
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
                queryView.traceId());
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

package com.guidinglight.nexusquant.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.observability.config.ObservabilityAutoConfiguration;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionService;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import com.guidinglight.nexusquant.strategy.application.StrategyRunQueryService;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleScanService;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleService;
import com.guidinglight.nexusquant.trading.api.dto.OrderCancelRequestBody;
import com.guidinglight.nexusquant.trading.api.dto.OrderSubmitRequest;
import com.guidinglight.nexusquant.trading.api.dto.ReconcileRunOnceRequest;
import com.guidinglight.nexusquant.trading.api.dto.RecoveryRunOnceRequest;
import com.guidinglight.nexusquant.trading.api.web.TradingVerificationController;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations.Kind;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations.Receipt;
import com.guidinglight.nexusquant.trading.application.query.AccountBalanceQueryView;
import com.guidinglight.nexusquant.trading.application.query.AccountQueryView;
import com.guidinglight.nexusquant.trading.application.query.OrderQueryView;
import com.guidinglight.nexusquant.trading.application.query.PositionQueryView;
import com.guidinglight.nexusquant.trading.application.query.TradeQueryView;
import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import com.guidinglight.nexusquant.trading.application.result.CancelOrderResult;
import com.guidinglight.nexusquant.trading.application.result.PlaceOrderResult;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** TradingVerificationControllerLocalTest 验证正式交易 API 的路由、成功响应与统一错误结构。 */
@ActiveProfiles("local")
@WithMockUser(
        username = "local-admin",
        roles = {"ADMIN", "OPERATOR", "VIEWER"})
@Import({ApiExceptionHandler.class, ObservabilityAutoConfiguration.class})
@WebMvcTest(controllers = TradingVerificationController.class)
@ContextConfiguration(classes = NexusQuantApplication.class)
class TradingVerificationControllerLocalTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private OrderCommandService orderCommandService;
    @MockitoBean private TradingQueryFacade tradingQueryFacade;
    @MockitoBean private TradingMaintenanceOperations tradingMaintenanceOperations;
    @MockitoBean private ExchangeAccountQueryService exchangeAccountQueryService;
    @MockitoBean private StrategyDefinitionService strategyDefinitionService;
    @MockitoBean private StrategyManualTriggerService strategyManualTriggerService;
    @MockitoBean private StrategyRunQueryService strategyRunQueryService;
    @MockitoBean private StrategyScheduleService strategyScheduleService;
    @MockitoBean private StrategyScheduleScanService strategyScheduleScanService;
    @MockitoBean private AuthUserRepository authUserRepository;
    @MockitoBean private CurrentUserProfileService currentUserProfileService;
    @MockitoBean private GatewayAuthFacade gatewayAuthFacade;
    // 该切片只验证交易工作台；SIM 所有者守卫由独立目标测试验证。
    @MockitoBean private com.guidinglight.nexusquant.paper.api.web.PaperRunReadAccess paperRunReadAccess;

    @BeforeEach
    void bindCurrentOwner() {
        // MVC切片的WithMockUser不是正式TokenClaims；明确提供认证主体，不弱化生产网关。
        TokenClaims claims = mock(TokenClaims.class);
        when(claims.username()).thenReturn("local-admin");
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.of(claims));
        when(currentUserProfileService.findByUsername("local-admin"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        1L,
                                        "local-admin",
                                        "test-only",
                                        List.of("ADMIN", "OPERATOR", "VIEWER"),
                                        true)));
    }

    @Test
    void shouldTriggerPlaceOrderThroughService() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        when(orderCommandService.placeOrder(any()))
                .thenReturn(new PlaceOrderResult("ord-1", OrderStatus.ACCEPTED, false));
        OrderSubmitRequest request =
                new OrderSubmitRequest(
                        1001L,
                        "run-1",
                        "OKX",
                        "coid-1",
                        "BTC-USDT",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("1.23"),
                        new BigDecimal("0.001"));
        mockMvc.perform(
                        post("/api/trading/orders")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-1")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-1"))
                .andExpect(jsonPath("$.action").value("placeOrder"))
                .andExpect(jsonPath("$.traceId").value("trc-local-1"));
        verify(orderCommandService).placeOrder(any());
    }

    @Test
    void shouldRejectLiveAccountBeforePlaceOrderService() throws Exception {
        mockExchangeAccount(1001L, 1001L, "LIVE");
        OrderSubmitRequest request =
                new OrderSubmitRequest(
                        1001L,
                        null,
                        "OKX",
                        "coid-live-blocked",
                        "BTC-USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("0.002"));

        mockMvc.perform(
                        post("/api/trading/orders")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-live-blocked")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "placeOrder blocked: LIVE trading is disabled and cannot"
                                                + " authorize real mutating path"));

        verify(orderCommandService, never()).placeOrder(any());
    }

    @Test
    void shouldRejectUnbridgedAccountBeforePlaceOrderService() throws Exception {
        mockExchangeAccount(1001L, null);
        OrderSubmitRequest request =
                new OrderSubmitRequest(
                        1001L,
                        null,
                        "OKX",
                        "coid-unbridged",
                        "BTC-USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("0.002"));
        mockMvc.perform(
                        post("/api/trading/orders")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-unbridged")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE"));
        verify(orderCommandService, never()).placeOrder(any());
    }

    @Test
    void shouldTriggerCancelOrderThroughService() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        when(tradingQueryFacade.queryOrder(eq("ord-1"), any()))
                .thenReturn(
                        Optional.of(
                                new OrderQueryView(
                                        "ord-1",
                                        1001L,
                                        "OKX",
                                        "BTC-USDT",
                                        "cid-1",
                                        null,
                                        "BUY",
                                        "LIMIT",
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        OrderStatus.ACCEPTED,
                                        "SIM",
                                        Instant.EPOCH,
                                        Instant.EPOCH,
                                        "trc-local-2")));
        when(orderCommandService.cancelOrder(any()))
                .thenReturn(new CancelOrderResult("ord-1", OrderStatus.CANCELLED, false));
        OrderCancelRequestBody request =
                new OrderCancelRequestBody("ord-1", null, null, "user_cancel");
        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-2")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("cancelOrder"))
                .andExpect(jsonPath("$.traceId").value("trc-local-2"));
        verify(orderCommandService).cancelOrder(any());
    }

    @Test
    void shouldRejectLiveAccountBeforeCancelOrderServiceWhenAccountLocatorIsPresent()
            throws Exception {
        mockExchangeAccount(1001L, 1001L, "LIVE");
        OrderCancelRequestBody request =
                new OrderCancelRequestBody(null, 1001L, "coid-live-cancel", "user_cancel");

        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-live-cancel-blocked")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "cancelOrder blocked: LIVE trading is disabled and cannot"
                                                + " authorize real mutating path"));

        verify(orderCommandService, never()).cancelOrder(any());
    }

    @Test
    void shouldTriggerReconcileAndOkxRecoveryServices() throws Exception {
        assertMaintenanceRoute(Kind.RECONCILE, "OKX", 25, "trc-local-3");
        assertMaintenanceRoute(Kind.RECOVERY, "OKX", null, "trc-local-4");
    }

    @Test
    void shouldTriggerBinanceReconcileThroughService() throws Exception {
        assertMaintenanceRoute(Kind.RECONCILE, "BINANCE", 12, "trc-local-5");
    }

    @Test
    void shouldTriggerBinanceRecoveryThroughService() throws Exception {
        assertMaintenanceRoute(Kind.RECOVERY, "BINANCE", null, "trc-local-5b");
    }

    /** 验证原URL与新稳定身份合同；这里只证明Servlet接线，不冒充真实worker或PG证明。 */
    private void assertMaintenanceRoute(Kind kind, String venue, Integer limit, String trace) throws Exception {
        UUID id = UUID.randomUUID();
        Receipt receipt = new Receipt(id, kind, venue, limit, "COMPLETED", trace,
                "synthetic-controlled-result", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        when(tradingMaintenanceOperations.submit(id, 1L, kind, venue, limit, trace)).thenReturn(receipt);
        String route = kind == Kind.RECONCILE ? "/api/trading/reconciliation/run-once" : "/api/trading/recovery/run-once";
        Object body = kind == Kind.RECONCILE ? new ReconcileRunOnceRequest(venue, limit) : new RecoveryRunOnceRequest(venue);
        mockMvc.perform(post(route).header("Idempotency-Key", id).header(TraceIdContext.TRACE_ID_HEADER, trace)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operationId").value(id.toString()))
                .andExpect(jsonPath("$.state").value("COMPLETED")).andExpect(jsonPath("$.traceId").value(trace));
        verify(tradingMaintenanceOperations).submit(id, 1L, kind, venue, limit, trace);
        when(tradingMaintenanceOperations.find(id, 1L)).thenReturn(Optional.of(receipt));
        mockMvc.perform(get("/api/trading/maintenance/operations/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operationId").value(id.toString()));
    }

    @Test
    void missingStableIdentityCannotExecuteMaintenance() throws Exception {
        mockMvc.perform(post("/api/trading/reconciliation/run-once").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tradingMaintenanceOperations);
    }

    @Test
    void shouldExposeCanonicalQueryRoutes() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        when(tradingQueryFacade.queryOrder(eq("ord-9"), eq("trc-local-6")))
                .thenReturn(
                        Optional.of(
                                new OrderQueryView(
                                        "ord-9",
                                        1001L,
                                        "OKX",
                                        "BTC-USDT",
                                        "cid-9",
                                        "paper-ord-9",
                                        "BUY",
                                        "LIMIT",
                                        new BigDecimal("100.01"),
                                        new BigDecimal("0.050"),
                                        OrderStatus.ACCEPTED,
                                        "SIM",
                                        Instant.parse("2026-03-12T07:59:00Z"),
                                        Instant.parse("2026-03-12T08:00:00Z"),
                                        "trc-order-9")));
        when(tradingQueryFacade.queryLatestTrade(eq("ord-9"), eq("trc-local-6")))
                .thenReturn(
                        Optional.of(
                                new TradeQueryView(
                                        "trd-9",
                                        "ord-9",
                                        1001L,
                                        "OKX",
                                        "BTC-USDT",
                                        "paper-ord-9",
                                        "paper-trd-9",
                                        new BigDecimal("100.50"),
                                        new BigDecimal("0.050"),
                                        BigDecimal.ZERO,
                                        "USDT",
                                        Instant.parse("2026-03-12T08:00:00Z"),
                                        "trc-trade-9")));
        when(tradingQueryFacade.queryPosition(eq(1001L), eq("BTC-USDT"), eq("trc-local-6")))
                .thenReturn(
                        Optional.of(
                                new PositionQueryView(
                                        1001L,
                                        "OKX",
                                        "BTC-USDT",
                                        new BigDecimal("0.050"),
                                        new BigDecimal("0.050"),
                                        new BigDecimal("100.50"),
                                        "trc-position-9")));
        when(tradingQueryFacade.queryAccount(eq(1001L), eq("trc-local-6")))
                .thenReturn(
                        Optional.of(
                                new AccountQueryView(
                                        1001L,
                                        "OKX",
                                        List.of(
                                                new AccountBalanceQueryView(
                                                        "BTC",
                                                        new BigDecimal("0.01400000"),
                                                        new BigDecimal("0.01400000"),
                                                        new BigDecimal("0.00000000"),
                                                        Instant.parse("2026-03-12T08:00:01Z"),
                                                        "trc-account-9",
                                                        "LIVE",
                                                        "POSITION_PROJECTION",
                                                        "NQ_MANAGED_ACCOUNT",
                                                        Instant.parse("2026-03-12T08:00:02Z")),
                                                new AccountBalanceQueryView(
                                                        "USDT",
                                                        new BigDecimal("0.00000000"),
                                                        new BigDecimal("0.00000000"),
                                                        new BigDecimal("0.00000000"),
                                                        Instant.parse("2026-03-12T08:00:01Z"),
                                                        "trc-account-9",
                                                        "SIM",
                                                        "LEDGER_CASH_PROJECTION",
                                                        "NQ_MANAGED_ACCOUNT",
                                                        Instant.parse("2026-03-12T08:00:02Z"))),
                                        "trc-account-9")));
        mockMvc.perform(
                        get("/api/trading/orders/ord-9")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord-9"))
                .andExpect(jsonPath("$.venue").value("OKX"));
        mockMvc.perform(
                        get("/api/trading/orders/ord-9/trade")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("trd-9"));
        mockMvc.perform(
                        get("/api/trading/positions/1001/BTC-USDT")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1001));
        mockMvc.perform(
                        get("/api/trading/accounts/1001")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.length()").value(2))
                .andExpect(jsonPath("$.balances[0].tradeEnv").value("LIVE"))
                .andExpect(jsonPath("$.balances[0].balanceBasis").value("POSITION_PROJECTION"))
                .andExpect(jsonPath("$.balances[1].tradeEnv").value("SIM"))
                .andExpect(jsonPath("$.balances[1].recordedAt").exists());
        verify(tradingQueryFacade, times(2)).queryOrder("ord-9", "trc-local-6");
        verify(tradingQueryFacade).queryLatestTrade("ord-9", "trc-local-6");
        verify(tradingQueryFacade).queryPosition(1001L, "BTC-USDT", "trc-local-6");
        verify(tradingQueryFacade).queryAccount(1001L, "trc-local-6");
    }

    @Test
    void shouldExposeTradingWorkspaceOrderListWithAccountContext() throws Exception {
        mockExchangeAccount(900001L, 1001L);
        when(tradingQueryFacade.listOrders(
                        eq(1001L),
                        eq(null),
                        eq("OKX"),
                        eq("BTC-USDT"),
                        eq(OrderStatus.ACCEPTED),
                        eq("SIM"),
                        eq(0),
                        eq(20),
                        eq("trc-local-list")))
                .thenReturn(
                        List.of(
                                new OrderQueryView(
                                        "ord-list-1",
                                        1001L,
                                        "OKX",
                                        "BTC-USDT",
                                        "cid-list-1",
                                        "okx-ord-list-1",
                                        "BUY",
                                        "LIMIT",
                                        new BigDecimal("100.01"),
                                        new BigDecimal("0.050"),
                                        OrderStatus.ACCEPTED,
                                        "SIM",
                                        Instant.parse("2026-03-12T07:59:00Z"),
                                        Instant.parse("2026-03-12T08:00:00Z"),
                                        "trc-order-list")));
        when(tradingQueryFacade.countOrders(
                        eq(1001L),
                        eq(null),
                        eq("OKX"),
                        eq("BTC-USDT"),
                        eq(OrderStatus.ACCEPTED),
                        eq("SIM"),
                        eq("trc-local-list")))
                .thenReturn(1L);

        mockMvc.perform(
                        get("/api/trading/orders")
                                .param("accountId", "900001")
                                .param("symbol", "BTC-USDT")
                                .param("status", "ACCEPTED")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value("ord-list-1"))
                .andExpect(jsonPath("$.items[0].tradeEnv").value("SIM"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldReturnUnifiedValidationErrorForMissingRequiredFields() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        mockMvc.perform(
                        post("/api/trading/orders")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-validation")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":1001,"venue":"","clientOrderId":"","symbol":"","side":"BUY","orderType":"LIMIT","quantity":0}
"""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-validation"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/trading/orders"))
                .andExpect(jsonPath("$.traceId").value("trc-local-validation"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));
    }

    @Test
    void shouldReturnUnifiedIllegalArgumentError() throws Exception {
        UUID maintenanceId = UUID.randomUUID();
        when(tradingMaintenanceOperations.submit(eq(maintenanceId), eq(1L), eq(Kind.RECOVERY), eq("UNKNOWN"), eq(null), eq("trc-local-invalid-venue")))
                .thenThrow(new IllegalArgumentException("unsupported trading venue"));
        mockMvc.perform(
                        post("/api/trading/recovery/run-once").header("Idempotency-Key", maintenanceId)
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-invalid-venue")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsBytes(
                                                new RecoveryRunOnceRequest("UNKNOWN"))))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-invalid-venue"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("unsupported trading venue"))
                .andExpect(jsonPath("$.path").value("/api/trading/recovery/run-once"))
                .andExpect(jsonPath("$.traceId").value("trc-local-invalid-venue"));
        verify(tradingMaintenanceOperations).submit(maintenanceId, 1L, Kind.RECOVERY, "UNKNOWN", null, "trc-local-invalid-venue");
    }

    @Test
    void shouldReturnUnifiedInternalError() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        when(orderCommandService.placeOrder(any())).thenThrow(new RuntimeException("boom"));
        OrderSubmitRequest request =
                new OrderSubmitRequest(
                        1001L,
                        null,
                        "OKX",
                        "cid-500",
                        "BTC-USDT",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        new BigDecimal("0.002"));
        mockMvc.perform(
                        post("/api/trading/orders")
                                .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-500")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-500"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("internal server error"))
                .andExpect(jsonPath("$.path").value("/api/trading/orders"))
                .andExpect(jsonPath("$.traceId").value("trc-local-500"));
    }

    @Test
    void shouldAcceptLegacyTraceHeaderButRespondWithStandardHeader() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        mockMvc.perform(
                        post("/api/trading/orders")
                                .header(TraceIdContext.LEGACY_TRACE_ID_HEADER, "trc-legacy-api")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":1001,"venue":"","clientOrderId":"","symbol":"","side":"BUY","orderType":"LIMIT","quantity":0}
"""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-legacy-api"))
                .andExpect(jsonPath("$.traceId").value("trc-legacy-api"));
    }

    @Test
    void shouldDenyForeignAccountBeforeAnyTradingReadOrWrite() throws Exception {
        mockMvc.perform(get("/api/trading/orders").param("accountId", "7001"))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get("/api/trading/orders/by-client")
                                .param("accountId", "7001")
                                .param("clientOrderId", "same-client"))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/api/trading/orders")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":7001,"venue":"OKX","clientOrderId":"foreign-place","symbol":"BTC-USDT","side":"BUY","orderType":"LIMIT","price":"100","quantity":"1"}
"""))
                .andExpect(status().isNotFound());
        verifyNoInteractions(tradingQueryFacade, orderCommandService);
    }

    @Test
    void shouldDenyForeignOrderAndTradeAndCancelWithoutCallingCommands() throws Exception {
        when(tradingQueryFacade.queryOrder(eq("foreign-order"), any()))
                .thenReturn(Optional.of(ownedOrder("foreign-order", 7001L, "SIM")));
        mockMvc.perform(get("/api/trading/orders/foreign-order")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/trading/orders/foreign-order/trade"))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"orderId\":\"foreign-order\",\"reason\":\"not-authorized\"}"))
                .andExpect(status().isNotFound());
        verify(tradingQueryFacade, never()).queryLatestTrade(any(), any());
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void shouldNotGuessAnUnmappedTradingIdentityFromEqualNumbers() throws Exception {
        mockExchangeAccount(1001L, null);
        mockMvc.perform(get("/api/trading/orders").param("accountId", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNT_TRADE_ENVIRONMENT_UNAVAILABLE"));
        verifyNoInteractions(tradingQueryFacade);
    }

    @Test
    void shouldUseOwnedCanonicalBridgeAndExactClientIdentityForReadOnlyLookup() throws Exception {
        mockExchangeAccount(900001L, 1001L);
        when(tradingQueryFacade.queryOrderByClientIdentity(
                        eq(1001L), eq("OKX"), eq("SIM"), eq("client-original"), any()))
                .thenReturn(Optional.of(ownedOrder("original-order", 1001L, "SIM")));
        mockMvc.perform(
                        get("/api/trading/orders/by-client")
                                .param("accountId", "900001")
                                .param("clientOrderId", "client-original"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("original-order"));
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void shouldRejectLiveCancelByOrderIdEvenWithoutAccountParameter() throws Exception {
        mockExchangeAccount(1001L, 1001L, "LIVE");
        when(tradingQueryFacade.queryOrder(eq("live-order"), any()))
                .thenReturn(Optional.of(ownedOrder("live-order", 1001L, "LIVE")));
        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"orderId\":\"live-order\",\"reason\":\"blocked\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void shouldDenyDifferentOwnedAccountContextForCancel() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        mockExchangeAccount(900002L, 2002L);
        when(tradingQueryFacade.queryOrder(eq("other-owned-order"), any()))
                .thenReturn(Optional.of(ownedOrder("other-owned-order", 2002L, "SIM")));
        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"orderId\":\"other-owned-order\",\"accountId\":1001,\"reason\":\"wrong-context\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void shouldRejectVenueMismatchBeforeCanonicalOrderCommand() throws Exception {
        mockExchangeAccount(1001L, 1001L);
        mockMvc.perform(
                        post("/api/trading/orders")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":1001,"venue":"BINANCE","clientOrderId":"wrong-venue","symbol":"BTC-USDT","side":"BUY","orderType":"LIMIT","price":"100","quantity":"1"}
"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void shouldResolveCompositeCancelToStableOrderInOwnedScope() throws Exception {
        mockExchangeAccount(900001L, 1001L);
        when(tradingQueryFacade.queryOrderByClientIdentity(
                        eq(1001L), eq("OKX"), eq("SIM"), eq(" client-original "), any()))
                .thenReturn(Optional.of(ownedOrder("resolved-order", 1001L, "SIM")));
        when(tradingQueryFacade.queryOrder(eq("resolved-order"), any()))
                .thenReturn(Optional.of(ownedOrder("resolved-order", 1001L, "SIM")));
        when(orderCommandService.cancelOrder(any()))
                .thenReturn(new CancelOrderResult("resolved-order", OrderStatus.CANCELLED, false));

        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":900001,"clientOrderId":" client-original ","reason":"cancel-original"}
"""))
                .andExpect(status().isOk());

        // 原客户端身份逐字用于查询；命令只携带已校验的稳定订单及范围，不再以组合键选单。
        verify(orderCommandService)
                .cancelOrder(
                        argThat(
                                command ->
                                        command.orderId().equals("resolved-order")
                                                && command.accountId().equals(1001L)
                                                && command.venue().equals("OKX")
                                                && command.symbol().equals("BTC-USDT")
                                                && command.clientOrderId() == null));
    }

    @Test
    void shouldRejectCompositeCancelWhenExactScopeHasNoOrder() throws Exception {
        mockExchangeAccount(900001L, 1001L);
        // 缺少匹配的SIM/交易所范围时，不能回退到宽泛的legacy账户组合查询。
        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":900001,"clientOrderId":"wrong-scope-client","reason":"must-deny"}
"""))
                .andExpect(status().isNotFound());
        verify(tradingQueryFacade)
                .queryOrderByClientIdentity(
                        eq(1001L), eq("OKX"), eq("SIM"), eq("wrong-scope-client"), any());
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void shouldRecheckLiveTargetAfterCompositeResolution() throws Exception {
        mockExchangeAccount(900001L, 1001L);
        mockExchangeAccount(900002L, 1001L, "LIVE");
        when(tradingQueryFacade.queryOrderByClientIdentity(
                        eq(1001L), eq("OKX"), eq("SIM"), eq("client-original"), any()))
                .thenReturn(Optional.of(ownedOrder("changed-target", 1001L, "SIM")));
        when(tradingQueryFacade.queryOrder(eq("changed-target"), any()))
                .thenReturn(Optional.of(ownedOrder("changed-target", 1001L, "LIVE")));
        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":900001,"clientOrderId":"client-original","reason":"must-deny"}
"""))
                .andExpect(status().isConflict());
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void shouldRecheckAccountContextAfterCompositeResolution() throws Exception {
        mockExchangeAccount(900001L, 1001L);
        mockExchangeAccount(900002L, 2002L);
        when(tradingQueryFacade.queryOrderByClientIdentity(
                        eq(1001L), eq("OKX"), eq("SIM"), eq("client-original"), any()))
                .thenReturn(Optional.of(ownedOrder("changed-target", 1001L, "SIM")));
        when(tradingQueryFacade.queryOrder(eq("changed-target"), any()))
                .thenReturn(Optional.of(ownedOrder("changed-target", 2002L, "SIM")));
        mockMvc.perform(
                        post("/api/trading/orders/cancel")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
{"accountId":900001,"clientOrderId":"client-original","reason":"must-deny"}
"""))
                .andExpect(status().isNotFound());
        verifyNoInteractions(orderCommandService);
    }

    @Test
    void accountFactPagesResolveOwnedBridgeAndReturnExplicitSource() throws Exception {
        mockExchangeAccount(1001L, 9001L);
        when(tradingQueryFacade.listAccountTrades(9001L,"OKX","SIM",null,0,10)).thenReturn(List.of());
        when(tradingQueryFacade.listAccountPositions(9001L,"OKX","SIM",null,0,10)).thenReturn(List.of());
        mockMvc.perform(get("/api/trading/trades").param("accountId","1001").param("size","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.exchangeAccountId").value(1001))
            .andExpect(jsonPath("$.tradingAccountId").value(9001)).andExpect(jsonPath("$.source").value("LOCAL_CANONICAL_TRADES"));
        mockMvc.perform(get("/api/trading/positions").param("accountId","1001").param("size","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("LOCAL_CANONICAL_POSITION_PROJECTION"));
        verify(tradingQueryFacade).listAccountTrades(9001L,"OKX","SIM",null,0,10);
        verify(tradingQueryFacade).listAccountPositions(9001L,"OKX","SIM",null,0,10);
    }

    @Test
    void accountFactPagesRejectUnownedAccountsAndInvalidWindows() throws Exception {
        mockMvc.perform(get("/api/trading/trades").param("accountId","999"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/trading/positions").param("accountId","999"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/trading/trades").param("accountId","1001").param("size","101"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/trading/positions").param("accountId","1001").param("page","-1"))
            .andExpect(status().isBadRequest());
    }

    /** 所有者正例仍使用真实legacy映射，避免夹具以相同数字绕过归属校验。 */
    private OrderQueryView ownedOrder(String id, Long accountId, String env) {
        return new OrderQueryView(
                id,
                accountId,
                "OKX",
                "BTC-USDT",
                "client-original",
                null,
                "BUY",
                "LIMIT",
                BigDecimal.ONE,
                BigDecimal.ONE,
                OrderStatus.ACCEPTED,
                env,
                Instant.EPOCH,
                Instant.EPOCH,
                "trace");
    }

    private void mockExchangeAccount(Long exchangeAccountId, Long legacyAccountId) {
        mockExchangeAccount(exchangeAccountId, legacyAccountId, "SIM");
    }

    private void mockExchangeAccount(
            Long exchangeAccountId, Long legacyAccountId, String tradeEnv) {
        when(exchangeAccountQueryService.hasTradingScopeForOwner(
                        1L, legacyAccountId, "OKX", tradeEnv))
                .thenReturn(legacyAccountId != null);

        when(exchangeAccountQueryService.findByIdForOwner(1L, exchangeAccountId))
                .thenReturn(
                        Optional.of(
                                new ExchangeAccountSummary(
                                        exchangeAccountId,
                                        legacyAccountId,
                                        1L,
                                        "OKX",
                                        tradeEnv,
                                        "local-admin",
                                        null,
                                        true,
                                        "ACTIVE")));
    }
}

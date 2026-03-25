package com.guidinglight.nexusquant.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.api.model.AccountBalanceView;
import com.guidinglight.nexusquant.api.model.AccountView;
import com.guidinglight.nexusquant.api.model.OrderView;
import com.guidinglight.nexusquant.api.model.PositionView;
import com.guidinglight.nexusquant.api.model.TradeView;
import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.api.web.OrderCancelRequestBody;
import com.guidinglight.nexusquant.api.web.OrderSubmitRequest;
import com.guidinglight.nexusquant.api.web.ReconcileRunOnceRequest;
import com.guidinglight.nexusquant.api.web.RecoveryRunOnceRequest;
import com.guidinglight.nexusquant.api.web.TradingVerificationController;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.observability.web.TraceIdFilter;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.CancelOrderResult;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;
import com.guidinglight.nexusquant.core.service.StrategyDefinitionService;
import com.guidinglight.nexusquant.core.service.StrategyManualTriggerService;
import com.guidinglight.nexusquant.core.service.StrategyRunQueryService;
import com.guidinglight.nexusquant.core.service.StrategyScheduleScanService;
import com.guidinglight.nexusquant.core.service.StrategyScheduleService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TradingVerificationControllerLocalTest 验证正式交易 API 的路由、成功响应与统一错误结构。
 */
@ActiveProfiles("local")
@Import({ApiExceptionHandler.class, TraceIdFilter.class})
@WebMvcTest(controllers = TradingVerificationController.class)
@ContextConfiguration(classes = NexusQuantApplication.class)
class TradingVerificationControllerLocalTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private OrderCommandService orderCommandService;
    @MockitoBean
    private TradingQueryFacade tradingQueryFacade;
    @MockitoBean
    private OkxRestReconcileService okxRestReconcileService;
    @MockitoBean
    private BinanceRestReconcileService binanceRestReconcileService;
    @MockitoBean
    private BinanceRecoveryService binanceRecoveryService;
    @MockitoBean
    private RecoveryService recoveryService;
    @MockitoBean
    private StrategyDefinitionService strategyDefinitionService;
    @MockitoBean
    private StrategyManualTriggerService strategyManualTriggerService;
    @MockitoBean
    private StrategyRunQueryService strategyRunQueryService;
    @MockitoBean
    private StrategyScheduleService strategyScheduleService;
    @MockitoBean
    private StrategyScheduleScanService strategyScheduleScanService;

    @Test
    void shouldTriggerPlaceOrderThroughService() throws Exception {
        when(orderCommandService.placeOrder(any())).thenReturn(new PlaceOrderResult("ord-1", OrderStatus.ACCEPTED, false));
        OrderSubmitRequest request = new OrderSubmitRequest(
                1001L,
                "run-1",
                "OKX",
                "coid-1",
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("1.23"),
                new BigDecimal("0.001")
        );
        mockMvc.perform(post("/api/trading/orders")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-1"))
                .andExpect(jsonPath("$.action").value("placeOrder"))
                .andExpect(jsonPath("$.traceId").value("trc-local-1"));
        verify(orderCommandService).placeOrder(any());
    }

    @Test
    void shouldTriggerCancelOrderThroughService() throws Exception {
        when(orderCommandService.cancelOrder(any())).thenReturn(new CancelOrderResult("ord-1", OrderStatus.CANCELLED, false));
        OrderCancelRequestBody request = new OrderCancelRequestBody("ord-1", null, null, "user_cancel");
        mockMvc.perform(post("/api/trading/orders/cancel")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("cancelOrder"))
                .andExpect(jsonPath("$.traceId").value("trc-local-2"));
        verify(orderCommandService).cancelOrder(any());
    }

    @Test
    void shouldTriggerReconcileAndOkxRecoveryServices() throws Exception {
        when(okxRestReconcileService.reconcileOnce(eq(25))).thenReturn(3);
        when(recoveryService.rebuild(eq("trc-local-4"))).thenReturn(new RecoveryReport(
                Instant.parse("2026-03-04T00:00:00Z"),
                Instant.parse("2026-03-04T00:00:01Z"),
                4L,
                2L,
                0L,
                0L,
                "trc-local-4"
        ));
        mockMvc.perform(post("/api/trading/reconciliation/run-once")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ReconcileRunOnceRequest("OKX", 25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("reconcileOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-3"));
        mockMvc.perform(post("/api/trading/recovery/run-once")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RecoveryRunOnceRequest("OKX"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("recoveryRunOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-4"));
        verify(okxRestReconcileService).reconcileOnce(25);
        verify(recoveryService).rebuild("trc-local-4");
    }

    @Test
    void shouldTriggerBinanceReconcileThroughService() throws Exception {
        when(binanceRestReconcileService.reconcileOnce(eq(12))).thenReturn(1);
        mockMvc.perform(post("/api/trading/reconciliation/run-once")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ReconcileRunOnceRequest("BINANCE", 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("reconcileOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-5"));
        verify(binanceRestReconcileService).reconcileOnce(12);
    }

    @Test
    void shouldTriggerBinanceRecoveryThroughService() throws Exception {
        when(binanceRecoveryService.rebuild(eq("trc-local-5b"))).thenReturn(new RecoveryReport(
                Instant.parse("2026-03-05T00:00:00Z"),
                Instant.parse("2026-03-05T00:00:01Z"),
                1L,
                0L,
                1L,
                0L,
                "trc-local-5b"
        ));
        mockMvc.perform(post("/api/trading/recovery/run-once")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-5b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RecoveryRunOnceRequest("BINANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("recoveryRunOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-5b"));
        verify(binanceRecoveryService).rebuild("trc-local-5b");
    }

    @Test
    void shouldExposeCanonicalQueryRoutes() throws Exception {
        when(tradingQueryFacade.queryOrder(eq("ord-9"), eq("trc-local-6"))).thenReturn(Optional.of(new OrderView(
                "ord-9",
                1001L,
                "PAPER",
                "BTC-USDT",
                "cid-9",
                "paper-ord-9",
                new BigDecimal("100.01"),
                new BigDecimal("0.050"),
                OrderStatus.ACCEPTED,
                "trc-order-9"
        )));
        when(tradingQueryFacade.queryLatestTrade(eq("ord-9"), eq("trc-local-6"))).thenReturn(Optional.of(new TradeView(
                "trd-9",
                "ord-9",
                1001L,
                "PAPER",
                "BTC-USDT",
                "paper-ord-9",
                "paper-trd-9",
                new BigDecimal("100.50"),
                new BigDecimal("0.050"),
                BigDecimal.ZERO,
                "USDT",
                Instant.parse("2026-03-12T08:00:00Z"),
                "trc-trade-9"
        )));
        when(tradingQueryFacade.queryPosition(eq(1001L), eq("BTC-USDT"), eq("trc-local-6"))).thenReturn(Optional.of(new PositionView(
                1001L,
                "PAPER",
                "BTC-USDT",
                new BigDecimal("0.050"),
                new BigDecimal("0.050"),
                new BigDecimal("100.50"),
                "trc-position-9"
        )));
        when(tradingQueryFacade.queryAccount(eq(1001L), eq("trc-local-6"))).thenReturn(Optional.of(new AccountView(
                1001L,
                "PAPER",
                List.of(
                        new AccountBalanceView(
                                "BTC",
                                new BigDecimal("0.01400000"),
                                new BigDecimal("0.01400000"),
                                new BigDecimal("0.00000000"),
                                Instant.parse("2026-03-12T08:00:01Z"),
                                "trc-account-9"
                        ),
                        new AccountBalanceView(
                                "USDT",
                                new BigDecimal("0.00000000"),
                                new BigDecimal("0.00000000"),
                                new BigDecimal("0.00000000"),
                                Instant.parse("2026-03-12T08:00:01Z"),
                                "trc-account-9"
                        )
                ),
                "trc-account-9"
        )));
        mockMvc.perform(get("/api/trading/orders/ord-9")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord-9"))
                .andExpect(jsonPath("$.venue").value("PAPER"));
        mockMvc.perform(get("/api/trading/orders/ord-9/trade")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("trd-9"));
        mockMvc.perform(get("/api/trading/positions/1001/BTC-USDT")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1001));
        mockMvc.perform(get("/api/trading/accounts/1001")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.length()").value(2));
        verify(tradingQueryFacade).queryOrder("ord-9", "trc-local-6");
        verify(tradingQueryFacade).queryLatestTrade("ord-9", "trc-local-6");
        verify(tradingQueryFacade).queryPosition(1001L, "BTC-USDT", "trc-local-6");
        verify(tradingQueryFacade).queryAccount(1001L, "trc-local-6");
    }

    @Test
    void shouldReturnUnifiedValidationErrorForMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/trading/orders")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
        mockMvc.perform(post("/api/trading/recovery/run-once")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-invalid-venue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RecoveryRunOnceRequest("UNKNOWN"))))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-local-invalid-venue"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("unsupported recovery venue: UNKNOWN"))
                .andExpect(jsonPath("$.path").value("/api/trading/recovery/run-once"))
                .andExpect(jsonPath("$.traceId").value("trc-local-invalid-venue"));
    }

    @Test
    void shouldReturnUnifiedInternalError() throws Exception {
        when(orderCommandService.placeOrder(any())).thenThrow(new RuntimeException("boom"));
        OrderSubmitRequest request = new OrderSubmitRequest(
                1001L,
                null,
                "PAPER",
                "cid-500",
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                new BigDecimal("0.002")
        );
        mockMvc.perform(post("/api/trading/orders")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-local-500")
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
        mockMvc.perform(post("/api/trading/orders")
                        .header(TraceIdContext.LEGACY_TRACE_ID_HEADER, "trc-legacy-api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":1001,"venue":"","clientOrderId":"","symbol":"","side":"BUY","orderType":"LIMIT","quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-legacy-api"))
                .andExpect(jsonPath("$.traceId").value("trc-legacy-api"));
    }
}

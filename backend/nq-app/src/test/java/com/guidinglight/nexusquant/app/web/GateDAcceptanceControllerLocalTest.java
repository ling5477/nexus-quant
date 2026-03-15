package com.guidinglight.nexusquant.app.web;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.api.model.AccountBalanceView;
import com.guidinglight.nexusquant.api.model.AccountView;
import com.guidinglight.nexusquant.api.model.OrderView;
import com.guidinglight.nexusquant.api.model.PositionView;
import com.guidinglight.nexusquant.api.model.TradeView;
import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.CancelOrderResult;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;
import com.guidinglight.nexusquant.scheduler.service.BinanceRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * GateDAcceptanceControllerLocalTest 楠岃瘉 local profile 涓?GateD 楠屾敹鍏ュ彛涓庢渶灏忔煡璇㈣鍥惧彲鐢ㄣ€? */
@ActiveProfiles("local")
@WebMvcTest(properties = "nq.gated.verify.enabled=true")
class GateDAcceptanceControllerLocalTest {
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
    @Test
    void shouldTriggerPlaceOrderThroughService() throws Exception {
        when(orderCommandService.placeOrder(any())).thenReturn(new PlaceOrderResult("ord-1", OrderStatus.ACCEPTED, false));
        GateDOrderHttpRequest request = new GateDOrderHttpRequest(
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
        mockMvc.perform(post("/__gated/orders")
                        .header("X-NQ-TRACE-ID", "trc-local-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("placeOrder"))
                .andExpect(jsonPath("$.traceId").value("trc-local-1"));
        verify(orderCommandService).placeOrder(any());
    }
    @Test
    void shouldTriggerCancelOrderThroughService() throws Exception {
        when(orderCommandService.cancelOrder(any())).thenReturn(new CancelOrderResult("ord-1", OrderStatus.CANCELLED, false));
        GateDCancelOrderHttpRequest request = new GateDCancelOrderHttpRequest("ord-1", null, null, "user_cancel");
        mockMvc.perform(post("/__gated/orders/cancel")
                        .header("X-NQ-TRACE-ID", "trc-local-2")
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
        mockMvc.perform(post("/__gated/reconcile/runOnce")
                        .header("X-NQ-TRACE-ID", "trc-local-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new GateDReconcileRunOnceHttpRequest("OKX", 25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("reconcileOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-3"));
        mockMvc.perform(post("/__gated/recovery/runOnce")
                        .header("X-NQ-TRACE-ID", "trc-local-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new GateDRecoveryRunOnceHttpRequest("OKX"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("recoveryRunOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-4"));
        verify(okxRestReconcileService).reconcileOnce(25);
        verify(recoveryService).rebuild("trc-local-4");
    }
    @Test
    void shouldTriggerBinanceReconcileThroughService() throws Exception {
        when(binanceRestReconcileService.reconcileOnce(eq(12))).thenReturn(1);
        mockMvc.perform(post("/__gated/reconcile/runOnce")
                        .header("X-NQ-TRACE-ID", "trc-local-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new GateDReconcileRunOnceHttpRequest("BINANCE", 12))))
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
        mockMvc.perform(post("/__gated/recovery/runOnce")
                        .header("X-NQ-TRACE-ID", "trc-local-5b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new GateDRecoveryRunOnceHttpRequest("BINANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("recoveryRunOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-5b"));
        verify(binanceRecoveryService).rebuild("trc-local-5b");
    }
    @Test
    void shouldExposeCanonicalQueryRoutesAndRejectRemovedGateCAlias() throws Exception {
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
        when(orderCommandService.placeOrder(any())).thenReturn(new PlaceOrderResult("ord-compat", OrderStatus.ACCEPTED, false));
        mockMvc.perform(get("/__gated/orders/ord-9")
                        .header("X-NQ-TRACE-ID", "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord-9"))
                .andExpect(jsonPath("$.venue").value("PAPER"));
        mockMvc.perform(get("/__gated/orders/ord-9/trade")
                        .header("X-NQ-TRACE-ID", "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value("trd-9"))
                .andExpect(jsonPath("$.venue").value("PAPER"));
        mockMvc.perform(get("/__gated/positions/1001/BTC-USDT")
                        .header("X-NQ-TRACE-ID", "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1001))
                .andExpect(jsonPath("$.symbol").value("BTC-USDT"));
        mockMvc.perform(get("/__gated/accounts/1001")
                        .header("X-NQ-TRACE-ID", "trc-local-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1001))
                .andExpect(jsonPath("$.balances.length()").value(2))
                .andExpect(jsonPath("$.balances[0].currency").value("BTC"))
                .andExpect(jsonPath("$.balances[0].balance").value(0.014))
                .andExpect(jsonPath("$.balances[0].available").value(0.014))
                .andExpect(jsonPath("$.balances[0].frozen").value(0.0))
                .andExpect(jsonPath("$.balances[1].currency").value("USDT"))
                .andExpect(jsonPath("$.balances[1].balance").value(0.0))
                .andExpect(jsonPath("$.balances[1].available").value(0.0))
                .andExpect(jsonPath("$.balances[1].frozen").value(0.0));
        mockMvc.perform(post("/__gated/orders")
                        .header("X-NQ-TRACE-ID", "trc-local-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new GateDOrderHttpRequest(
                                1001L,
                                null,
                                "PAPER",
                                "cid-compat",
                                "BTC-USDT",
                                OrderSide.BUY,
                                OrderType.MARKET,
                                null,
                                new BigDecimal("0.002")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trc-local-7"));
        mockMvc.perform(post("/__gatec/orders")
                        .header("X-NQ-TRACE-ID", "trc-local-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes("""
                                {"accountId":1001,"venue":"PAPER","clientOrderId":"cid-gatec","symbol":"BTC-USDT","side":"BUY","orderType":"MARKET","quantity":0.002}
                                """)))
                .andExpect(status().isNotFound());
        verify(tradingQueryFacade).queryOrder("ord-9", "trc-local-6");
        verify(tradingQueryFacade).queryLatestTrade("ord-9", "trc-local-6");
        verify(tradingQueryFacade).queryPosition(1001L, "BTC-USDT", "trc-local-6");
        verify(tradingQueryFacade).queryAccount(1001L, "trc-local-6");
        verify(orderCommandService).placeOrder(any());
    }
}
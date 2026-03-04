package com.guidinglight.nexusquant.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.recovery.RecoveryReport;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.CancelOrderResult;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.PlaceOrderResult;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GateCAcceptanceControllerLocalTest 验证 local profile 下 GateC 验收入口可用并触发服务层。
 */
@ActiveProfiles("local")
@WebMvcTest(properties = "nq.gatec.acceptance.enabled=true")
class GateCAcceptanceControllerLocalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OkxRestReconcileService okxRestReconcileService;

    @MockitoBean
    private RecoveryService recoveryService;

    @Test
    void shouldTriggerPlaceOrderThroughService() throws Exception {
        when(orderCommandService.placeOrder(any())).thenReturn(new PlaceOrderResult("ord-1", OrderStatus.ACCEPTED, false));

        GateCOrderHttpRequest request = new GateCOrderHttpRequest(
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

        mockMvc.perform(post("/__gatec/orders")
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

        GateCCancelOrderHttpRequest request = new GateCCancelOrderHttpRequest("ord-1", null, null, "user_cancel");

        mockMvc.perform(post("/__gatec/orders/cancel")
                        .header("X-NQ-TRACE-ID", "trc-local-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("cancelOrder"))
                .andExpect(jsonPath("$.traceId").value("trc-local-2"));

        verify(orderCommandService).cancelOrder(any());
    }

    @Test
    void shouldTriggerReconcileAndRecoveryServices() throws Exception {
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

        mockMvc.perform(post("/__gatec/reconcile/runOnce")
                        .header("X-NQ-TRACE-ID", "trc-local-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new GateCReconcileRunOnceHttpRequest(25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("reconcileOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-3"));

        mockMvc.perform(post("/__gatec/recovery/runOnce")
                        .header("X-NQ-TRACE-ID", "trc-local-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("recoveryRunOnce"))
                .andExpect(jsonPath("$.traceId").value("trc-local-4"));

        verify(okxRestReconcileService).reconcileOnce(25);
        verify(recoveryService).rebuild("trc-local-4");
    }
}

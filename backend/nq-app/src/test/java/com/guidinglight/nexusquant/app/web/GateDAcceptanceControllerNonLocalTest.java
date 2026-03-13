package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GateDAcceptanceControllerNonLocalTest 验证非 local profile 下不会暴露 GateD 验收路由。
 */
@ActiveProfiles("test")
@WebMvcTest(properties = "nq.gated.verify.enabled=true")
class GateDAcceptanceControllerNonLocalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private TradingQueryFacade tradingQueryFacade;

    @MockitoBean
    private OkxRestReconcileService okxRestReconcileService;

    @MockitoBean
    private BinanceRestReconcileService binanceRestReconcileService;

    @MockitoBean
    private RecoveryService recoveryService;

    @Test
    void shouldReturnNotFoundWhenProfileIsNotLocal() throws Exception {
        mockMvc.perform(post("/__gated/recovery/runOnce"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/__gatec/recovery/runOnce"))
                .andExpect(status().isNotFound());
    }
}

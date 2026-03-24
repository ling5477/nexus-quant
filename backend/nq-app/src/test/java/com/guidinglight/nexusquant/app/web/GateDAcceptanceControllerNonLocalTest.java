package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.StrategyManualTriggerService;
import com.guidinglight.nexusquant.core.service.StrategyRunQueryService;
import com.guidinglight.nexusquant.core.service.StrategyScheduleScanService;
import com.guidinglight.nexusquant.core.service.StrategyScheduleService;
import com.guidinglight.nexusquant.core.service.StrategyDefinitionService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GateDAcceptanceControllerNonLocalTest 楠岃瘉闈?local profile 涓嬩笉浼氭毚闇?GateD 楠屾敹璺敱銆?
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
    void shouldReturnNotFoundWhenProfileIsNotLocal() throws Exception {
        mockMvc.perform(post("/__gated/recovery/runOnce"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/__gatec/recovery/runOnce"))
                .andExpect(status().isNotFound());
    }
}

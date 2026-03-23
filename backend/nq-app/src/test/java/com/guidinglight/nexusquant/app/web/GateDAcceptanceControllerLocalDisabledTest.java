package com.guidinglight.nexusquant.app.web;
import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.StrategyManualTriggerService;
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
 * GateDAcceptanceControllerLocalDisabledTest 楠岃瘉 local profile 浣嗘湭鏄惧紡寮€鍚紑鍏虫椂锛? * 楠屾敹鍏ュ彛涓嶄細琚槧灏勶紝浠庤€屾弧瓒抽粯璁ら浂鏆撮湶瑕佹眰銆? */
@ActiveProfiles("local")
@WebMvcTest(properties = "nq.gated.verify.enabled=false")
class GateDAcceptanceControllerLocalDisabledTest {
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
    @Test
    void shouldReturnNotFoundWhenLocalButVerifyDisabled() throws Exception {
        mockMvc.perform(post("/__gated/recovery/runOnce"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/__gatec/recovery/runOnce"))
                .andExpect(status().isNotFound());
    }
}

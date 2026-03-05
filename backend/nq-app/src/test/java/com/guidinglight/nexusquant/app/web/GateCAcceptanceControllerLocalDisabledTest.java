package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
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
 * GateCAcceptanceControllerLocalDisabledTest 验证 local profile 但未显式开启开关时，
 * 验收入口不会被映射，从而满足“默认零暴露”要求。
 */
@ActiveProfiles("local")
@WebMvcTest(properties = "nq.gatec.verify.enabled=false")
class GateCAcceptanceControllerLocalDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OkxRestReconcileService okxRestReconcileService;

    @MockitoBean
    private RecoveryService recoveryService;

    @Test
    void shouldReturnNotFoundWhenLocalButVerifyDisabled() throws Exception {
        mockMvc.perform(post("/__gatec/recovery/runOnce"))
                .andExpect(status().isNotFound());
    }
}

package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
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
 * GateCAcceptanceControllerNonLocalTest 验证非 local profile 下不会暴露 GateC 验收路由。
 */
@ActiveProfiles("test")
@WebMvcTest
class GateCAcceptanceControllerNonLocalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OkxRestReconcileService okxRestReconcileService;

    @MockitoBean
    private RecoveryService recoveryService;

    @Test
    void shouldReturnNotFoundWhenProfileIsNotLocal() throws Exception {
        mockMvc.perform(post("/__gatec/recovery/runOnce"))
                .andExpect(status().isNotFound());
    }
}

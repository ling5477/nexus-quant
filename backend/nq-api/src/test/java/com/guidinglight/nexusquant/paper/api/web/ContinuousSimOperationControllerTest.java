package com.guidinglight.nexusquant.paper.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationService;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 旧命令缺少或损坏编号时在参数绑定阶段拒绝，不进入认证主体查询或业务写入。 */
class ContinuousSimOperationControllerTest {
    @Test
    void missingOrInvalidOperationHeaderIsBadRequestBeforeBusinessExecution() throws Exception {
        var operations = mock(ContinuousSimOperationService.class);
        var auth = mock(GatewayAuthFacade.class);
        var users = mock(CurrentUserProfileService.class);
        var http =
                MockMvcBuilders.standaloneSetup(
                                new ContinuousSimOperationController(operations, auth, users))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        http.perform(
                        post("/api/paper-trading/strategy-sim/runs/continuous")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"publishId\":\"publish-a\",\"budget\":\"100\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"));
        http.perform(
                        post("/api/paper-trading/strategy-sim/runs/ptr-a/continuous/stop")
                                .header("Idempotency-Key", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(operations, auth, users);
    }
}

package com.guidinglight.nexusquant.paper.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.research.application.paper.service.PaperAlertOperationService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperRunMonitorService;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

/** 参数绑定先拒绝不可恢复的旧写请求；不能在缺编号时先创建告警或查操作者。 */
class PaperAlertOperationControllerTest {
    @Test
    void invisibleSourcesRemain404WhileMalformedCommandsRemain400() throws Exception {
        var operations = mock(PaperAlertOperationService.class);
        var monitor = mock(PaperRunMonitorService.class);
        var auth = mock(GatewayAuthFacade.class);
        var users = mock(CurrentUserProfileService.class);
        var claims = mock(TokenClaims.class);
        var profile = mock(AuthUserProfile.class);
        when(claims.username()).thenReturn("operator");
        when(profile.userId()).thenReturn(7L);
        when(auth.currentUser()).thenReturn(Optional.of(claims));
        when(users.findByUsername("operator")).thenReturn(Optional.of(profile));
        when(operations.submit(any(), anyLong(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("paper run not found"));
        when(monitor.getAlert("ptr-invisible", "alt-one"))
                .thenThrow(new IllegalArgumentException("paper run not found"));
        var http =
                MockMvcBuilders.standaloneSetup(
                                new PaperAlertOperationController(operations, monitor, auth, users))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        http.perform(get("/api/paper-trading/runs/ptr-invisible/alerts/alt-one"))
                .andExpect(status().isNotFound());
        http.perform(
                        post("/api/paper-trading/runs/ptr-invisible/alert-operations/"
                                        + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"ACK\",\"alertId\":\"alt-one\"}"))
                .andExpect(status().isNotFound());
        http.perform(
                        post("/api/paper-trading/runs/ptr-invisible/alert-operations/"
                                        + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"type\":\"ACK\",\"alertId\":\"alt-one\",\"title\":\"invalid mixed command\"}"))
                .andExpect(status().isBadRequest());
        verify(operations, times(1))
                .submit(any(), anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void missingAndMalformedUuidAreRejectedBeforeMutation() throws Exception {
        var operations = mock(PaperAlertOperationService.class);
        var monitor = mock(PaperRunMonitorService.class);
        var auth = mock(GatewayAuthFacade.class);
        var users = mock(CurrentUserProfileService.class);
        var http =
                MockMvcBuilders.standaloneSetup(
                                new PaperAlertOperationController(operations, monitor, auth, users))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        http.perform(
                        post("/api/paper-trading/runs/ptr-one/alerts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"alertType\":\"SYSTEM_NOTICE\",\"severity\":\"LOW\",\"title\":\"notice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"));
        http.perform(patch("/api/paper-trading/runs/ptr-one/alerts/alt-one/ack"))
                .andExpect(status().isBadRequest());
        http.perform(
                        patch("/api/paper-trading/runs/ptr-one/alerts/alt-one/resolve")
                                .header("Idempotency-Key", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(operations, monitor, auth, users);
    }
}

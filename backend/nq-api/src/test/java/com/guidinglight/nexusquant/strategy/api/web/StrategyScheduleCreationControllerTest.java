package com.guidinglight.nexusquant.strategy.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleCreationService;
import com.guidinglight.nexusquant.strategy.application.exception.StrategyScheduleCreationConflictException;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreation;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreationSpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 验证服务端主体、角色及不可变回执投影；真实并发和持久化不能由这些mock代替。 */
class StrategyScheduleCreationControllerTest {
    private StrategyScheduleCreationController controller(
            StrategyScheduleCreationService service, String role) {
        var auth = mock(GatewayAuthFacade.class);
        var profiles = mock(CurrentUserProfileService.class);
        var claims = mock(TokenClaims.class);
        when(claims.username()).thenReturn("operator");
        when(auth.currentUser()).thenReturn(Optional.of(claims));
        when(profiles.findByUsername("operator"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        1L, "operator", "synthetic", List.of(role), true)));
        return new StrategyScheduleCreationController(service, auth, profiles);
    }

    @Test
    void usesServerOwnerAndKeepsLargeAccountAsText() throws Exception {
        var service = mock(StrategyScheduleCreationService.class);
        var id = UUID.randomUUID();
        var time = Instant.parse("2026-10-01T00:00:00Z");
        var spec =
                new StrategyScheduleCreationSpec(
                        "str", "CRON", "0 * * * * *", "UTC", false, "{}", "REQUEST");
        var plan =
                new StrategySchedule(
                        "sch",
                        "str",
                        "CRON",
                        spec.cronExpr(),
                        "UTC",
                        false,
                        "{}",
                        "REQUEST",
                        "BINANCE",
                        9007199254740993L,
                        "SIM",
                        null,
                        time,
                        time);
        when(service.submit(any(), anyLong(), anyString(), any(), anyString()))
                .thenReturn(
                        new StrategyScheduleCreation(
                                id,
                                1,
                                "operator",
                                1,
                                "a".repeat(64),
                                spec,
                                plan,
                                "original-trace",
                                time));
        var mvc =
                MockMvcBuilders.standaloneSetup(controller(service, "OPERATOR"))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        String body =
                "{\"creationId\":\""
                        + id
                        + "\",\"ownerUserId\":999,\"actor\":\"forged\",\"request\":{\"strategyId\":\"str\",\"scheduleType\":\"CRON\",\"cronExpr\":\"0 * * * * *\",\"timezone\":\"UTC\",\"enabled\":false,\"windowConfig\":\"{}\",\"dedupScope\":\"REQUEST\"}}";
        mvc.perform(
                        post("/api/strategy-schedule-creations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value("9007199254740993"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.traceId").value("original-trace"));
        verify(service).submit(eq(id), eq(1L), eq("operator"), any(), anyString());
    }

    @Test
    void viewerCannotReadOrCreateReceiptsAndMissingOwnedResultIs404() throws Exception {
        var service = mock(StrategyScheduleCreationService.class);
        var id = UUID.randomUUID();
        var viewer =
                MockMvcBuilders.standaloneSetup(controller(service, "VIEWER"))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        viewer.perform(get("/api/strategy-schedule-creations/" + id))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        when(service.find(id, 1)).thenReturn(Optional.empty());
        var admin =
                MockMvcBuilders.standaloneSetup(controller(service, "ADMIN"))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        admin.perform(get("/api/strategy-schedule-creations/" + id))
                .andExpect(status().isNotFound());
        verify(service).find(id, 1);
    }

    @Test
    void malformedIdentityAndMissingInputAreRejectedBeforeSubmission() throws Exception {
        var service = mock(StrategyScheduleCreationService.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(controller(service, "OPERATOR"))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        for (String body :
                List.of(
                        "{}",
                        "{\"creationId\":\"invalid\"}",
                        "{\"creationId\":\"" + UUID.randomUUID() + "\",\"request\":{}}"))
            mvc.perform(
                            post("/api/strategy-schedule-creations")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void busyIsAnExplicitConflictWithTrace() throws Exception {
        var service = mock(StrategyScheduleCreationService.class);
        var id = UUID.randomUUID();
        when(service.find(id, 1))
                .thenThrow(
                        new StrategyScheduleCreationConflictException(
                                "STRATEGY_SCHEDULE_CREATION_BUSY"));
        var mvc =
                MockMvcBuilders.standaloneSetup(controller(service, "OPERATOR"))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
        mvc.perform(get("/api/strategy-schedule-creations/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STRATEGY_SCHEDULE_CREATION_BUSY"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}

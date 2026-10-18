package com.guidinglight.nexusquant.marketdata.api.web;

import static org.mockito.ArgumentMatchers.any;
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
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataIngestionJobCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataIngestionService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 控制器绑定与当前角色边界证明；真实过滤链、数据库并发和浏览器恢复另行验证。 */
class MarketdataIngestionOperationControllerTest {
    private static final Instant AT = Instant.parse("2026-10-01T00:00:00Z");
    private final MarketdataIngestionService service = mock(MarketdataIngestionService.class);
    private final GatewayAuthFacade auth = mock(GatewayAuthFacade.class);
    private final CurrentUserProfileService profiles = mock(CurrentUserProfileService.class);
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(
                            new MarketdataIngestionOperationController(service, auth, profiles))
                    .setControllerAdvice(new ApiExceptionHandler())
                    .build();

    @Test
    void stableJobPathUsesCanonicalAuthenticatedCreator() throws Exception {
        actor(List.of("OPERATOR"), true);
        UUID jobId = UUID.randomUUID();
        when(service.createJob(eq(jobId), any(CreateMarketdataIngestionJobCommand.class)))
                .thenReturn(job(jobId));
        mvc.perform(
                        post("/api/marketdata/ingestion-jobs/{jobId}", jobId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(input()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));
        verify(service)
                .createJob(
                        jobId,
                        new CreateMarketdataIngestionJobCommand(
                                "OKX",
                                "SPOT",
                                "BTC-USDT",
                                "1m",
                                AT,
                                AT.plusSeconds(180),
                                "operator"));
    }

    @Test
    void currentProfileCannotBorrowAnOldTokenRole() throws Exception {
        actor(List.of("VIEWER"), true);
        mvc.perform(
                        post("/api/marketdata/ingestion-jobs/{jobId}", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(input()))
                .andExpect(status().isForbidden());
        actor(List.of("OPERATOR"), false);
        mvc.perform(
                        post(
                                "/api/marketdata/ingestion-jobs/{jobId}/runs/{runId}",
                                UUID.randomUUID(),
                                UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void stableExecutionAndExactSharedReadKeepBothIdentities() throws Exception {
        actor(List.of("OPERATOR"), true);
        UUID jobId = UUID.randomUUID(), runId = UUID.randomUUID();
        var run = run(jobId, runId);
        when(service.runOnce(jobId, runId)).thenReturn(run);
        when(service.findRun(jobId, runId)).thenReturn(Optional.of(run));
        mvc.perform(post("/api/marketdata/ingestion-jobs/{jobId}/runs/{runId}", jobId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()));
        actor(List.of("VIEWER"), true);
        mvc.perform(get("/api/marketdata/ingestion-jobs/{jobId}/runs/{runId}", jobId, runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));
        UUID other = UUID.randomUUID();
        when(service.findRun(other, runId)).thenReturn(Optional.empty());
        mvc.perform(get("/api/marketdata/ingestion-jobs/{jobId}/runs/{runId}", other, runId))
                .andExpect(status().isNotFound());
        verify(service).runOnce(jobId, runId);
    }

    @Test
    void malformedIdentityAndMissingInputNeverReachTheService() throws Exception {
        actor(List.of("OPERATOR"), true);
        mvc.perform(
                        post("/api/marketdata/ingestion-jobs/not-a-uuid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(input()))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/marketdata/ingestion-jobs/{jobId}", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private void actor(List<String> roles, boolean enabled) {
        when(auth.currentUser())
                .thenReturn(
                        Optional.of(
                                new TokenClaims(
                                        "1",
                                        "operator",
                                        List.of("ADMIN"),
                                        AT,
                                        AT.plusSeconds(600),
                                        "test",
                                        "synthetic")));
        when(profiles.findByUsername("operator"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        1L, "operator", "synthetic-not-a-login", roles, enabled)));
    }

    @Test
    void creationRecoveryReturns404ForMissingOrOtherCreatorWithoutStartingACommand()
            throws Exception {
        actor(List.of("VIEWER"), true);
        UUID id = UUID.randomUUID();
        when(service.findJob(id)).thenReturn(Optional.empty());
        mvc.perform(get("/api/marketdata/ingestion-jobs/{jobId}/creation-result", id))
                .andExpect(status().isNotFound());
        var original = job(id);
        when(service.findJob(id)).thenReturn(Optional.of(original));
        mvc.perform(get("/api/marketdata/ingestion-jobs/{jobId}/creation-result", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(id.toString()));
        when(auth.currentUser())
                .thenReturn(
                        Optional.of(
                                new TokenClaims(
                                        "2",
                                        "other-operator",
                                        List.of("ADMIN"),
                                        AT,
                                        AT.plusSeconds(600),
                                        "test",
                                        "other-synthetic")));
        mvc.perform(get("/api/marketdata/ingestion-jobs/{jobId}/creation-result", id))
                .andExpect(status().isNotFound());
    }

    private static String input() {
        return """
                {"exchangeCode":"OKX","marketType":"SPOT","symbol":"BTC-USDT","interval":"1m",
                 "startTime":"2026-10-01T00:00:00Z","endTime":"2026-10-01T00:03:00Z"}
                """;
    }

    private static MarketdataIngestionJob job(UUID id) {
        return new MarketdataIngestionJob(
                id,
                "OKX",
                "SPOT",
                "BTC-USDT",
                BarInterval.ONE_MINUTE,
                AT,
                AT.plusSeconds(180),
                MarketdataIngestionStatus.CREATED,
                "EXCHANGE_HISTORICAL",
                "operator",
                AT,
                AT,
                "{}");
    }

    private static MarketdataIngestionRun run(UUID job, UUID id) {
        return new MarketdataIngestionRun(
                id,
                job,
                MarketdataIngestionStatus.SUCCEEDED,
                AT,
                AT.plusSeconds(1),
                AT,
                AT.plusSeconds(180),
                AT,
                AT.plusSeconds(59),
                1,
                1,
                0,
                0,
                null,
                "{}",
                AT);
    }
}

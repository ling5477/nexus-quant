package com.guidinglight.nexusquant.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.app.config.auth.SecurityConfiguration;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.observability.config.ObservabilityAutoConfiguration;
import com.guidinglight.nexusquant.paper.api.web.PaperRunReadAccess;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.strategy.api.web.ShadowRunCreationResultController;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.exception.ShadowRunCreationEvidenceException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunCreationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.service.ShadowRunCreationResultQueryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 只读创建恢复的真实过滤器与 MVC 合同；业务事实与零写入另外由 PostgreSQL 证明。 */
@ActiveProfiles("test")
@Import({
    ApiExceptionHandler.class,
    ObservabilityAutoConfiguration.class,
    SecurityConfiguration.class
})
@TestPropertySource(
        properties = {
            "nq.security.issuer=nexus-quant-test",
            "nq.security.secret=test-change-me-test-change-me-123456",
            "nq.security.access-token-ttl=PT30M"
        })
@WebMvcTest(controllers = ShadowRunCreationResultController.class)
class ShadowRunCreationResultSecurityWebMvcTest {

    private static final String PUBLISH = "publish-shadow-result";
    private static final String COMMAND = "operator-action-001";
    private static final String ROUTE =
            "/api/strategy-releases/" + PUBLISH + "/shadow-run-creation-result";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaperRunReadAccess paperRunReadAccess;
    @MockitoBean private GatewayAuthFacade gatewayAuthFacade;
    @MockitoBean private CurrentUserProfileService profileService;
    @MockitoBean private AuthUserRepository authUserRepository;
    @MockitoBean private ShadowRunCreationResultQueryService queryService;

    @BeforeEach
    void currentActor() {
        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        when(gatewayAuthFacade.currentUser())
                .thenReturn(
                        Optional.of(
                                new TokenClaims(
                                        "sub",
                                        "operator",
                                        List.of("OPERATOR"),
                                        now,
                                        now.plusSeconds(60),
                                        "issuer",
                                        "jti")));
        when(profileService.findByUsername("operator"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        41L, "operator", "hash", List.of("OPERATOR"), true)));
    }

    @Test
    void anonymousCannotQuery() throws Exception {
        mockMvc.perform(get(ROUTE).header("Idempotency-Key", COMMAND))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotBypassOriginalCommandRoleBoundaryUsingGet() throws Exception {
        mockMvc.perform(get(ROUTE).header("Idempotency-Key", COMMAND))
                .andExpect(status().isForbidden());
        verifyNoInteractions(queryService);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void serverProfileOwnsQueryAndResponseSeparatesCreationFromRunStatus() throws Exception {
        when(queryService.query(
                        eq(PUBLISH),
                        eq(COMMAND),
                        argThat(
                                actor ->
                                        actor.actorId() == 41L
                                                && actor.roles().equals(List.of("OPERATOR")))))
                .thenReturn(Optional.of(result()));
        mockMvc.perform(get(ROUTE).header("Idempotency-Key", COMMAND).param("actorId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creationStatus").value("CREATED"))
                .andExpect(jsonPath("$.runStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.publishRecordId").value(PUBLISH))
                .andExpect(jsonPath("$.actorId").doesNotExist())
                .andExpect(jsonPath("$.commandIdentity").doesNotExist())
                .andExpect(jsonPath("$.idempotentReplay").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void notFoundDoesNotClaimThatTheOriginalCommandNeverExecuted() throws Exception {
        when(queryService.query(eq(PUBLISH), eq(COMMAND), any())).thenReturn(Optional.empty());
        mockMvc.perform(get(ROUTE).header("Idempotency-Key", COMMAND))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.creationStatus").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void missingAuditProducesStableUnavailableEnvelope() throws Exception {
        when(queryService.query(eq(PUBLISH), eq(COMMAND), any()))
                .thenThrow(new ShadowRunCreationEvidenceException());
        mockMvc.perform(get(ROUTE).header("Idempotency-Key", COMMAND))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SHADOW_CREATION_RESULT_UNAVAILABLE"))
                .andExpect(jsonPath("$.metadata").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void missingIdentityProducesBadRequestFromQueryContract() throws Exception {
        when(queryService.query(eq(PUBLISH), eq(null), any()))
                .thenThrow(new IllegalArgumentException("Idempotency-Key required"));
        mockMvc.perform(get(ROUTE)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void missingCurrentProfileCannotBecomeAnOwner() throws Exception {
        when(profileService.findByUsername("operator")).thenReturn(Optional.empty());
        mockMvc.perform(get(ROUTE).header("Idempotency-Key", COMMAND))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService);
    }

    private static ShadowRunCreationResult result() {
        Instant created = Instant.parse("2026-10-15T00:00:00Z");
        return new ShadowRunCreationResult(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                PUBLISH,
                "a".repeat(64),
                ShadowRunReleaseBindingMode.RELEASE_BOUND,
                ShadowRunStatus.CREATED,
                ShadowRunStatus.COMPLETED,
                created,
                created.plusSeconds(60));
    }
}

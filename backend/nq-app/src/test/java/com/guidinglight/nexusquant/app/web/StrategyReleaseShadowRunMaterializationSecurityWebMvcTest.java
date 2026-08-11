package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.app.config.auth.SecurityConfiguration;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.observability.config.ObservabilityAutoConfiguration;
import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.strategy.api.web.StrategyReleaseShadowRunMaterializationController;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunMaterializationActor;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunMaterializationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseShadowRunMaterializationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shadow materialization POST 必须执行 anonymous/VIEWER/OPERATOR 三层 RBAC 回归。 */
@ActiveProfiles("test")
@Import({ApiExceptionHandler.class, ObservabilityAutoConfiguration.class, SecurityConfiguration.class})
@TestPropertySource(properties = {
        "nq.security.issuer=nexus-quant-test",
        "nq.security.secret=test-change-me-test-change-me-123456",
        "nq.security.access-token-ttl=PT30M"
})
@WebMvcTest(controllers = StrategyReleaseShadowRunMaterializationController.class)
class StrategyReleaseShadowRunMaterializationSecurityWebMvcTest {

    private static final String PUBLISH_ID = "publish-gatex5-001";
    private static final String ROUTE = "/api/strategy-releases/" + PUBLISH_ID + "/shadow-runs";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayAuthFacade gatewayAuthFacade;

    @MockitoBean
    private CurrentUserProfileService currentUserProfileService;

    @MockitoBean
    private AuthUserRepository authUserRepository;

    @MockitoBean
    private StrategyReleaseShadowRunMaterializationService materializationService;

    @BeforeEach
    void setUpActor() {
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.of(new TokenClaims(
                "sub",
                "operator",
                List.of("OPERATOR"),
                now,
                now.plusSeconds(60),
                "issuer",
                "jti"
        )));
        when(currentUserProfileService.findByUsername("operator")).thenReturn(Optional.of(new AuthUserProfile(
                41L,
                "operator",
                "hash",
                List.of("OPERATOR"),
                true
        )));
    }

    @Test
    void shouldRejectAnonymousWrite() throws Exception {
        mockMvc.perform(post(ROUTE).header("Idempotency-Key", "operator-action-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "local-viewer", roles = "VIEWER")
    void shouldRejectViewerWrite() throws Exception {
        mockMvc.perform(post(ROUTE).header("Idempotency-Key", "operator-action-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void shouldAllowOperatorAndReturnCreatedWithoutStart() throws Exception {
        when(materializationService.materialize(
                eq(PUBLISH_ID),
                eq("operator-action-001"),
                any(ShadowRunMaterializationActor.class),
                anyString()
        )).thenReturn(Optional.of(result()));

        mockMvc.perform(post(ROUTE).header("Idempotency-Key", "operator-action-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shadowRunId").value("22222222-2222-4222-8222-222222222222"))
                .andExpect(jsonPath("$.publishRecordId").value(PUBLISH_ID))
                .andExpect(jsonPath("$.bindingMode").value("RELEASE_BOUND"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));
    }

    private static ShadowRunMaterializationResult result() {
        return new ShadowRunMaterializationResult(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                PUBLISH_ID,
                "a".repeat(64),
                ShadowRunReleaseBindingMode.RELEASE_BOUND,
                ShadowRunStatus.CREATED,
                Instant.parse("2026-08-11T00:00:00Z"),
                false
        );
    }
}

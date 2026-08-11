package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.app.config.auth.SecurityConfiguration;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.observability.config.ObservabilityAutoConfiguration;
import com.guidinglight.nexusquant.strategy.api.web.StrategyReleaseAdmissionPreviewController;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionDecision;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreview;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewService;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Preview endpoint 复用全局 authenticated GET RBAC 的集成回归。 */
@ActiveProfiles("test")
@Import({ApiExceptionHandler.class, ObservabilityAutoConfiguration.class, SecurityConfiguration.class})
@TestPropertySource(properties = {
        "nq.security.issuer=nexus-quant-test",
        "nq.security.secret=test-change-me-test-change-me-123456",
        "nq.security.access-token-ttl=PT30M"
})
@WebMvcTest(controllers = StrategyReleaseAdmissionPreviewController.class)
class StrategyReleaseAdmissionPreviewSecurityWebMvcTest {

    private static final String ROUTE =
            "/api/strategy-releases/publish-preview-001/shadow-admission-preview";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrategyReleaseAdmissionPreviewService previewService;

    @MockitoBean
    private AuthUserRepository authUserRepository;

    @Test
    void shouldRejectUnauthenticatedRead() throws Exception {
        mockMvc.perform(get(ROUTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "local-viewer", roles = "VIEWER")
    void shouldAllowViewerReadWithoutGrantingWriteAuthority() throws Exception {
        when(previewService.preview(
                org.mockito.ArgumentMatchers.eq("publish-preview-001"),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(Optional.of(preview()));

        mockMvc.perform(get(ROUTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissionDecision").value("ELIGIBLE"));
    }

    private static StrategyReleaseAdmissionPreview preview() {
        return new StrategyReleaseAdmissionPreview(
                "publish-preview-001",
                "publish-preview-001",
                "strategy-version-preview-001",
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "evaluation-preview-001",
                ShadowRunReleaseBindingMode.RELEASE_BOUND,
                StrategyReleaseStatus.VERIFIED,
                StrategyArtifactVerificationResult.Status.VERIFIED,
                StrategyValidationDecision.APPROVED,
                ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE,
                List.of("ELIGIBLE_FOR_CREATION_PLAN_ONLY"),
                "a".repeat(64)
        );
    }
}

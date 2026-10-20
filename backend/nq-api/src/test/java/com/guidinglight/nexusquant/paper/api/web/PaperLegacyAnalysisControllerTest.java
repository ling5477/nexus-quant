package com.guidinglight.nexusquant.paper.api.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.research.application.paper.assembler.PaperAutoReviewAssembler;
import com.guidinglight.nexusquant.research.application.paper.assembler.PaperExecutionDiagnosticsAssembler;
import com.guidinglight.nexusquant.research.application.paper.assembler.PaperPortfolioAssembler;
import com.guidinglight.nexusquant.research.application.paper.assembler.PaperStrategyEvaluationAssembler;
import com.guidinglight.nexusquant.research.application.paper.model.PaperLegacyAnalysisSnapshot;
import com.guidinglight.nexusquant.research.application.paper.service.PaperLegacyAnalysisService;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 用户身份只来自已认证资料，伪造查询参数不能把读取切到其他所有者。 */
class PaperLegacyAnalysisControllerTest {
    @Test
    void authenticatedProfileOverridesForgedOwnerParameter() throws Exception {
        var auth = mock(GatewayAuthFacade.class);
        var profile = mock(CurrentUserProfileService.class);
        var service = mock(PaperLegacyAnalysisService.class);
        var claims = mock(TokenClaims.class);
        when(claims.username()).thenReturn("legacy-reader");
        when(auth.currentUser()).thenReturn(Optional.of(claims));
        when(profile.findByUsername("legacy-reader"))
                .thenReturn(
                        Optional.of(
                                new AuthUserProfile(
                                        7L,
                                        "legacy-reader",
                                        "synthetic",
                                        List.of("VIEWER"),
                                        true)));
        var diagnostics = PaperExecutionDiagnosticsAssembler.assemble(List.of());
        var evaluation = PaperStrategyEvaluationAssembler.assemble(List.of(), Map.of());
        var time = Instant.parse("2026-10-01T00:00:00Z");
        when(service.read(7))
                .thenReturn(
                        new PaperLegacyAnalysisSnapshot(
                                "LEGACY_PAPER",
                                200,
                                0,
                                false,
                                time,
                                PaperPortfolioAssembler.assemble(List.of()),
                                List.of(),
                                diagnostics,
                                evaluation,
                                PaperAutoReviewAssembler.assemble(diagnostics, evaluation, time)));
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new PaperLegacyAnalysisController(auth, profile, service))
                        .build();
        mvc.perform(get("/api/paper-trading/legacy-analysis").param("owner", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("LEGACY_PAPER"))
                .andExpect(jsonPath("$.valuationStatus").value("UNIT_NOT_RECORDED"))
                .andExpect(jsonPath("$.maxRuns").value(200))
                .andExpect(jsonPath("$.includedRuns").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.portfolio.overview.totalRuns").value(0))
                .andExpect(jsonPath("$.runs").isEmpty())
                .andExpect(jsonPath("$.diagnostics.overview.totalRuns").value(0))
                .andExpect(jsonPath("$.owner").doesNotExist());
        verify(service).read(7);
    }

    @Test
    void missingAuthenticationOrProfileNeverInvokesAnalysis() {
        var auth = mock(GatewayAuthFacade.class);
        var profiles = mock(CurrentUserProfileService.class);
        var service = mock(PaperLegacyAnalysisService.class);
        when(auth.currentUser()).thenReturn(Optional.empty());
        var controller = new PaperLegacyAnalysisController(auth, profiles, service);
        assertThrows(AuthenticationCredentialsNotFoundException.class, controller::read);
        verifyNoInteractions(profiles, service);
        var claims = mock(TokenClaims.class);
        when(claims.username()).thenReturn("missing");
        when(auth.currentUser()).thenReturn(Optional.of(claims));
        when(profiles.findByUsername("missing")).thenReturn(Optional.empty());
        assertThrows(AuthenticationCredentialsNotFoundException.class, controller::read);
        verifyNoInteractions(service);
    }
}

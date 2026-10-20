package com.guidinglight.nexusquant.paper.api.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;

/** 读侧在查事实前拒绝越权，并保留legacy与命令的既有合同。 */
class PaperRunReadAccessTest {
    private final GatewayAuthFacade auth = mock(GatewayAuthFacade.class);
    private final CurrentUserProfileService profiles = mock(CurrentUserProfileService.class);
    private final StrategySimAnalysisQuery query = mock(StrategySimAnalysisQuery.class);
    private final PaperTradingApiService paper = mock(PaperTradingApiService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<StrategySimAnalysisQuery> provider = mock(ObjectProvider.class);
    private final PaperRunReadAccess access = new PaperRunReadAccess(auth, profiles, provider, paper);

    private void identity() {
        var claims = mock(TokenClaims.class);
        var profile = mock(AuthUserProfile.class);
        when(claims.username()).thenReturn("owner");
        when(profile.userId()).thenReturn(7L);
        when(auth.currentUser()).thenReturn(Optional.of(claims));
        when(profiles.findByUsername("owner")).thenReturn(Optional.of(profile));
        when(provider.getIfAvailable()).thenReturn(query);
    }
    private MockHttpServletRequest request(String method, String path) {
        var req = new MockHttpServletRequest(method, path);
        req.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("paperRunId", "run"));
        return req;
    }
    @Test void oldSimFactsDecisionsAndContinuousRequireOwner() {
        identity();
        for (String suffix : List.of("facts", "decisions", "continuous")) {
            var req = request("GET", "/api/paper-trading/strategy-sim/runs/run/" + suffix);
            when(query.readIdentity(7, "run")).thenReturn(Optional.empty());
            assertEquals(404, assertThrows(ResponseStatusException.class,
                    () -> access.preHandle(req, new MockHttpServletResponse(), new Object())).getStatusCode().value());
            when(query.readIdentity(7, "run")).thenReturn(Optional.of(mock(StrategySimAnalysisQuery.Run.class)));
            assertTrue(access.preHandle(req, new MockHttpServletResponse(), new Object()));
        }
    }
    @Test void paperAliasesRejectCanonicalButRetainLegacyAndCommands() {
        identity();
        var run = mock(PaperTradingRun.class);
        when(paper.getById("run")).thenReturn(run);
        when(run.canonicalAccountId()).thenReturn(2L);
        assertThrows(ResponseStatusException.class, () -> access.preHandle(
                request("GET", "/api/paper-trading/runs/run/trades"), new MockHttpServletResponse(), new Object()));
        when(run.canonicalAccountId()).thenReturn(null);
        assertTrue(access.preHandle(request("GET", "/api/paper-trading/runs/run/trades"), new MockHttpServletResponse(), new Object()));
        clearInvocations(paper, query);
        assertTrue(access.preHandle(request("POST", "/api/paper-trading/runs/run/start"), new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(paper, query);
    }
    @Test void catalogUsesBoundedBatchesWithoutEconomicReads() {
        identity();
        var ids = java.util.stream.IntStream.range(0, 205).mapToObj(i -> "r" + i).toList();
        when(query.readableRunIds(eq(7L), anyList())).thenAnswer(call -> new HashSet<>(call.<List<String>>getArgument(1)));
        assertEquals(new HashSet<>(ids), access.readableIds(ids));
        verify(query, times(3)).readableRunIds(eq(7L), argThat(batch -> batch.size() <= 100));
        verifyNoMoreInteractions(query);
    }
    @Test void disabledAnalysisFailsClosedForCanonicalButKeepsLegacyCatalog() {
        var run = mock(PaperTradingRun.class);
        when(run.paperRunId()).thenReturn("legacy");
        when(run.canonicalAccountId()).thenReturn(null);
        when(paper.list(null, null)).thenReturn(List.of(run));
        assertEquals(Set.of("legacy"), access.readableIds(List.of("legacy", "canonical")));
        assertThrows(ResponseStatusException.class, () -> access.preHandle(
                request("GET", "/api/paper-trading/strategy-sim/runs/run/facts"), new MockHttpServletResponse(), new Object()));
    }
}

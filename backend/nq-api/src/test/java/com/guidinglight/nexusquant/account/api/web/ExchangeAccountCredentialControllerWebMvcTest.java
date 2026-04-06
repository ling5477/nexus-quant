package com.guidinglight.nexusquant.account.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.TokenClaims;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class ExchangeAccountCredentialControllerWebMvcTest {

    private MockMvc mockMvc;
    private ExchangeAccountCredentialCommandService commandService;
    private ExchangeAccountCredentialVerificationService verificationService;

    @BeforeEach
    void setUp() {
        GatewayAuthFacade gatewayAuthFacade = mock(GatewayAuthFacade.class);
        CurrentUserProfileService currentUserProfileService = mock(CurrentUserProfileService.class);
        commandService = mock(ExchangeAccountCredentialCommandService.class);
        verificationService = mock(ExchangeAccountCredentialVerificationService.class);
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.of(new TokenClaims("sub", "admin", List.of("ADMIN"), Instant.now(), Instant.now().plusSeconds(60), "issuer", "jti")));
        when(currentUserProfileService.findByUsername("admin")).thenReturn(Optional.of(new AuthUserProfile(1L, "admin", "hash", List.of("ADMIN"), true)));
        mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeAccountCredentialController(gatewayAuthFacade, currentUserProfileService, commandService, verificationService, 1))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeActiveCredentialUpsertAndVerify() throws Exception {
        ExchangeAccountCredentialSummary summary = new ExchangeAccountCredentialSummary(1L, 900001L, "OKX_API_V5", "tes***ey", "VERIFIED", true, null, Instant.parse("2026-04-06T00:00:00Z"), null, Instant.parse("2026-04-06T00:00:00Z"));
        when(commandService.findActiveSummaryOrNull(1L, 900001L)).thenReturn(null);
        when(commandService.upsert(any(), any(), any(), anyInt())).thenReturn(summary);
        when(verificationService.verifyActive(1L, 900001L)).thenReturn(summary);

        mockMvc.perform(get("/api/exchange-accounts/900001/credentials/active").header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-active"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-credential-active"))
                .andExpect(jsonPath("$.exchangeAccountId").value(900001))
                .andExpect(jsonPath("$.activeCredential").doesNotExist());

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-upsert")
                        .contentType("application/json")
                        .content("{\"credentialType\":\"OKX_API_V5\",\"apiKey\":\"test-api-key\",\"secretKey\":\"secret\",\"passphrase\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAccessKey").value("tes***ey"));

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/verify").header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
    }

    private static final class TestTraceIdFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, java.io.IOException {
            String incoming = request.getHeader(TraceIdContext.TRACE_ID_HEADER);
            String traceId = TraceIdContext.putOrCreate(incoming);
            request.setAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, traceId);
            response.setHeader(TraceIdContext.TRACE_ID_HEADER, traceId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                TraceIdContext.clear();
            }
        }
    }
}

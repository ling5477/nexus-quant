package com.guidinglight.nexusquant.trading.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightCredentialTypeSummary;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReadiness;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReadinessService;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightReason;
import com.guidinglight.nexusquant.trading.application.preflight.TradingPreflightScope;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

class TradingPreflightControllerTest {

    private MockMvc mockMvc;
    private TradingPreflightReadinessService readinessService;

    @BeforeEach
    void setUp() {
        GatewayAuthFacade gatewayAuthFacade = mock(GatewayAuthFacade.class);
        CurrentUserProfileService currentUserProfileService = mock(CurrentUserProfileService.class);
        readinessService = mock(TradingPreflightReadinessService.class);
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.of(new TokenClaims(
                "sub",
                "admin",
                List.of("ADMIN"),
                Instant.now(),
                Instant.now().plusSeconds(60),
                "issuer",
                "jti"
        )));
        when(currentUserProfileService.findByUsername("admin")).thenReturn(Optional.of(new AuthUserProfile(
                1L,
                "admin",
                "hash",
                List.of("ADMIN"),
                true
        )));
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TradingPreflightController(
                        gatewayAuthFacade,
                        currentUserProfileService,
                        readinessService
                ))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeReadinessWithoutSensitiveOrAuthorizationFields() throws Exception {
        when(readinessService.readiness(argThat(query ->
                query.ownerUserId().equals(1L)
                        && "OKX".equals(query.exchangeCode())
                        && query.accountId().equals(900001L)
                        && "SPOT".equals(query.marketType())
                        && "BTC-USDT".equals(query.symbol())
                        && "strategy-alpha".equals(query.strategyId())
        ))).thenReturn(readiness());

        MvcResult result = mockMvc.perform(get("/api/trading/preflight/readiness")
                        .param("exchangeCode", "OKX")
                        .param("accountId", "900001")
                        .param("marketType", "SPOT")
                        .param("symbol", "BTC-USDT")
                        .param("strategyId", "strategy-alpha")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-preflight"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-preflight"))
                .andExpect(jsonPath("$.scope.exchangeCode").value("OKX"))
                .andExpect(jsonPath("$.accountId").value(900001))
                .andExpect(jsonPath("$.liveStatus").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.realProviderStatus").value("REAL_PROVIDER_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.privateTradingStatus").value("PRIVATE_TRADING_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.permissionProbeStatus").value("PERMISSION_PROBE_SKIPPED"))
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.credentialTypeSummary[0].credentialType").value("OKX_API_V5"))
                .andExpect(jsonPath("$.credentialTypeSummary[0].permissionProbeStatus").value("SKIPPED"))
                .andExpect(jsonPath("$.dataQualityStatus").value("OK"))
                .andExpect(jsonPath("$.riskPreflightStatus").value("RISK_PREFLIGHT_BLOCKED"))
                .andExpect(jsonPath("$.blockers[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.warnings[0].code").value("DATA_QUALITY_DIAGNOSTIC_ONLY"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("apiKey"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("passphrase"));
        assertFalse(body.contains("private key"));
        assertFalse(body.contains("privateKey"));
        assertFalse(body.contains("token"));
        assertFalse(body.contains("encrypted_payload"));
        assertFalse(body.contains("decrypted_payload"));
        assertFalse(body.contains("encryptedPayload"));
        assertFalse(body.contains("decryptedPayload"));
        assertFalse(body.contains("rawResponse"));
        assertFalse(body.contains("rawHeaders"));
        assertFalse(body.contains("tradingReady"));
        assertFalse(body.contains("liveReady"));
        assertFalse(body.contains("authorizedForTrading"));
    }

    @Test
    void shouldReturnUnauthorizedWhenCurrentUserMissing() throws Exception {
        GatewayAuthFacade gatewayAuthFacade = mock(GatewayAuthFacade.class);
        CurrentUserProfileService currentUserProfileService = mock(CurrentUserProfileService.class);
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.empty());
        MockMvc unauthenticatedMvc = MockMvcBuilders
                .standaloneSetup(new TradingPreflightController(
                        gatewayAuthFacade,
                        currentUserProfileService,
                        readinessService
                ))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        unauthenticatedMvc.perform(get("/api/trading/preflight/readiness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private TradingPreflightReadiness readiness() {
        return new TradingPreflightReadiness(
                new TradingPreflightScope("OKX", 900001L, "SPOT", "BTC-USDT", "strategy-alpha"),
                "OKX",
                900001L,
                "SPOT",
                "BTC-USDT",
                "LIVE_DISABLED",
                "REAL_PROVIDER_NOT_IMPLEMENTED",
                "PRIVATE_TRADING_NOT_IMPLEMENTED",
                "PERMISSION_PROBE_SKIPPED",
                true,
                "ACTIVE",
                List.of(new TradingPreflightCredentialTypeSummary(
                        10L,
                        "OKX_API_V5",
                        "ACTIVE",
                        "VERIFIED",
                        true,
                        "SKIPPED",
                        "READ_ONLY",
                        "SKIPPED",
                        0,
                        Instant.parse("2026-07-04T00:00:00Z"),
                        Instant.parse("2026-07-04T00:02:00Z")
                )),
                true,
                "ACTIVE",
                "OK",
                "RISK_PREFLIGHT_BLOCKED",
                List.of(
                        new TradingPreflightReason("LIVE_DISABLED", "BLOCKER", "LIVE is disabled."),
                        new TradingPreflightReason(
                                "REAL_PROVIDER_NOT_IMPLEMENTED",
                                "BLOCKER",
                                "Real provider is not implemented."
                        ),
                        new TradingPreflightReason(
                                "PRIVATE_TRADING_NOT_IMPLEMENTED",
                                "BLOCKER",
                                "Private trading is not implemented."
                        ),
                        new TradingPreflightReason(
                                "PERMISSION_PROBE_NOT_IMPLEMENTED",
                                "BLOCKER",
                                "Real permission probe is not implemented."
                        )
                ),
                List.of(
                        new TradingPreflightReason(
                                "DATA_QUALITY_DIAGNOSTIC_ONLY",
                                "WARNING",
                                "Data quality is diagnostic only."
                        ),
                        new TradingPreflightReason(
                                "RISK_PREFLIGHT_READONLY",
                                "WARNING",
                                "Risk preflight is readonly."
                        )
                ),
                List.of("Design and review a credential-material-free real permission probe in a separate gated task."),
                Instant.parse("2026-07-04T12:00:00Z")
        );
    }

    private static final class TestTraceIdFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, java.io.IOException {
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

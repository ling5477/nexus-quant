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

import com.guidinglight.nexusquant.account.application.CredentialPermissionProbeService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionProbeSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class ExchangeAccountCredentialControllerWebMvcTest {

    private MockMvc mockMvc;
    private ExchangeAccountCredentialCommandService commandService;
    private ExchangeAccountCredentialVerificationService verificationService;
    private CredentialPermissionProbeService permissionProbeService;

    @BeforeEach
    void setUp() {
        GatewayAuthFacade gatewayAuthFacade = mock(GatewayAuthFacade.class);
        CurrentUserProfileService currentUserProfileService = mock(CurrentUserProfileService.class);
        commandService = mock(ExchangeAccountCredentialCommandService.class);
        verificationService = mock(ExchangeAccountCredentialVerificationService.class);
        permissionProbeService = mock(CredentialPermissionProbeService.class);
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.of(new TokenClaims("sub", "admin", List.of("ADMIN"), Instant.now(), Instant.now().plusSeconds(60), "issuer", "jti")));
        when(currentUserProfileService.findByUsername("admin")).thenReturn(Optional.of(new AuthUserProfile(1L, "admin", "hash", List.of("ADMIN"), true)));
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeAccountCredentialController(gatewayAuthFacade, currentUserProfileService, commandService, verificationService, permissionProbeService, 1))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeActiveCredentialUpsertAndVerify() throws Exception {
        ExchangeAccountCredentialSummary summary = new ExchangeAccountCredentialSummary(1L, 900001L, "OKX_API_V5", "tes***ey", "ACTIVE", "VERIFIED", true, null, null, null, Instant.parse("2026-04-06T00:00:00Z"), null, Instant.parse("2026-04-06T00:00:00Z"));
        when(commandService.findActiveSummaryOrNull(1L, 900001L, null)).thenReturn(null);
        when(commandService.findActiveSummaryOrNull(1L, 900001L, "OKX_API_V5")).thenReturn(summary);
        when(commandService.upsert(any(), any(), any(), anyInt())).thenReturn(summary);
        when(commandService.rotate(any(), any(), any(), any(), any(), anyInt())).thenReturn(new ExchangeAccountCredentialSummary(2L, 900001L, "OKX_API_V5", "new***ey", "ACTIVE", "PENDING", true, null, 1L, null, null, null, Instant.parse("2026-04-06T00:02:00Z")));
        when(verificationService.verifyActive(1L, 900001L, null)).thenReturn(summary);
        when(verificationService.verifyActive(1L, 900001L, "OKX_API_V5")).thenReturn(summary);

        mockMvc.perform(get("/api/exchange-accounts/900001/credentials/active").header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-active"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-credential-active"))
                .andExpect(jsonPath("$.exchangeAccountId").value(900001))
                .andExpect(jsonPath("$.activeCredential").doesNotExist());

        mockMvc.perform(get("/api/exchange-accounts/900001/credentials/active")
                        .queryParam("credentialType", "OKX_API_V5")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-active-type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCredential.credentialType").value("OKX_API_V5"))
                .andExpect(jsonPath("$.activeCredential.maskedAccessKey").value("tes***ey"))
                .andExpect(jsonPath("$.activeCredential.permissionScope").doesNotExist())
                .andExpect(jsonPath("$.activeCredential.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.activeCredential.decryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.activeCredential.secretKey").doesNotExist())
                .andExpect(jsonPath("$.activeCredential.token").doesNotExist());

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-upsert")
                        .contentType("application/json")
                        .content("{\"credentialType\":\"OKX_API_V5\",\"apiKey\":\"test-api-key\",\"secretKey\":\"secret\",\"passphrase\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAccessKey").value("tes***ey"))
                .andExpect(jsonPath("$.credentialStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/verify").header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/verify")
                        .queryParam("credentialType", "OKX_API_V5")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-verify-type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.decryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.privateKeyPem").doesNotExist())
                .andExpect(jsonPath("$.passphrase").doesNotExist());

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/rotate")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-credential-rotate")
                        .contentType("application/json")
                        .content("{\"apiKey\":\"new-api-key\",\"secretKey\":\"new-secret\",\"passphrase\":\"new-pass\",\"reason\":\"scheduled key rotation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").value(2))
                .andExpect(jsonPath("$.credentialStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.rotatedFromCredentialId").value(1))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.decryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.privateKeyPem").doesNotExist())
                .andExpect(jsonPath("$.passphrase").doesNotExist());
    }

    @Test
    void shouldReturnConflictWhenActiveCredentialTypeIsAmbiguous() throws Exception {
        when(commandService.findActiveSummaryOrNull(1L, 900001L, null))
                .thenThrow(new IllegalStateException("multiple active credential types require credentialType"));
        when(verificationService.verifyActive(1L, 900001L, null))
                .thenThrow(new IllegalStateException("multiple active credential types require credentialType"));

        mockMvc.perform(get("/api/exchange-accounts/900001/credentials/active"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("multiple active credential types require credentialType"));

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/verify"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("multiple active credential types require credentialType"));
    }

    @Test
    void shouldExposeCredentialLifecycleCommandsWithoutSensitiveFields() throws Exception {
        ExchangeAccountCredentialSummary revoked = new ExchangeAccountCredentialSummary(
                1L,
                900001L,
                "OKX_API_V5",
                "tes***ey",
                "REVOKED",
                "VERIFIED",
                false,
                Instant.parse("2026-04-06T00:01:00Z"),
                null,
                null,
                Instant.parse("2026-04-06T00:00:00Z"),
                null,
                Instant.parse("2026-04-06T00:01:00Z")
        );
        ExchangeAccountCredentialSummary disabled = new ExchangeAccountCredentialSummary(
                1L,
                900001L,
                "OKX_API_V5",
                "tes***ey",
                "DISABLED",
                "VERIFIED",
                false,
                null,
                null,
                null,
                Instant.parse("2026-04-06T00:00:00Z"),
                null,
                Instant.parse("2026-04-06T00:02:00Z")
        );
        ExchangeAccountCredentialSummary expired = new ExchangeAccountCredentialSummary(
                1L,
                900001L,
                "OKX_API_V5",
                "tes***ey",
                "EXPIRED",
                "VERIFIED",
                false,
                null,
                null,
                null,
                Instant.parse("2026-04-06T00:00:00Z"),
                null,
                Instant.parse("2026-04-06T00:03:00Z")
        );
        ExchangeAccountCredentialSummary enabled = new ExchangeAccountCredentialSummary(
                1L,
                900001L,
                "OKX_API_V5",
                "tes***ey",
                "ACTIVE",
                "VERIFIED",
                true,
                null,
                null,
                null,
                Instant.parse("2026-04-06T00:04:00Z"),
                null,
                Instant.parse("2026-04-06T00:04:00Z")
        );
        when(commandService.revoke(any(), any(), any(), any(), any())).thenReturn(revoked);
        when(commandService.disable(any(), any(), any(), any(), any())).thenReturn(disabled);
        when(commandService.expire(any(), any(), any(), any(), any())).thenReturn(expired);
        when(commandService.enable(any(), any(), any(), any(), any())).thenReturn(enabled);

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/revoke")
                        .contentType("application/json")
                        .content("{\"reason\":\"operator offboarding\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialStatus").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").value("2026-04-06T00:01:00Z"))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/disable")
                        .contentType("application/json")
                        .content("{\"reason\":\"temporary stop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialStatus").value("DISABLED"))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist());

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/expire")
                        .contentType("application/json")
                        .content("{\"reason\":\"expired by policy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist());

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/enable")
                        .contentType("application/json")
                        .content("{\"reason\":\"operator approved re-enable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.decryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.privateKeyPem").doesNotExist())
                .andExpect(jsonPath("$.passphrase").doesNotExist());
    }

    @Test
    void shouldRejectEnableWhenReasonMissing() throws Exception {
        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/enable")
                        .contentType("application/json")
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldExposePermissionProbeWithoutSensitiveFields() throws Exception {
        CredentialPermissionProbeSummary summary = new CredentialPermissionProbeSummary(
                900001L,
                1L,
                "OKX_API_V5",
                "OKX",
                "SUCCEEDED",
                "READ_ONLY",
                false,
                "PASSED",
                2,
                Instant.parse("2026-06-13T00:00:01Z"),
                null,
                "req-1",
                "trace-probe"
        );
        when(permissionProbeService.probe(any(), any(), any(), any(), any(), any())).thenReturn(summary);
        when(permissionProbeService.latest(any(), any(), any(), any(), any())).thenReturn(summary);

        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/permission-probe")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trace-probe")
                        .contentType("application/json")
                        .content("{\"reason\":\"operator probe\",\"dryRun\":true,\"mode\":\"PAPER\",\"paperSafetyConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").value(1))
                .andExpect(jsonPath("$.credentialType").value("OKX_API_V5"))
                .andExpect(jsonPath("$.exchange").value("OKX"))
                .andExpect(jsonPath("$.permissionProbeStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.permissionScope").value("READ_ONLY"))
                .andExpect(jsonPath("$.withdrawEnabled").value(false))
                .andExpect(jsonPath("$.failedAuthCount").value(2))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.decryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.signature").doesNotExist())
                .andExpect(jsonPath("$.headers").doesNotExist())
                .andExpect(jsonPath("$.rawResponse").doesNotExist());

        mockMvc.perform(get("/api/exchange-accounts/900001/credentials/1/permission-probe/latest")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trace-probe-latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionProbeStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.decryptedPayload").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist());
    }

    @Test
    void shouldRejectCredentialMaterialInPermissionProbeRequestBody() throws Exception {
        mockMvc.perform(post("/api/exchange-accounts/900001/credentials/1/permission-probe")
                        .contentType("application/json")
                        .content("{\"dryRun\":true,\"mode\":\"PAPER\",\"paperSafetyConfirmed\":true,\"apiKey\":\"must-not-be-accepted\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldReturnUnauthorizedWhenPermissionProbeHasNoAuthenticatedUser() throws Exception {
        GatewayAuthFacade gatewayAuthFacade = mock(GatewayAuthFacade.class);
        CurrentUserProfileService currentUserProfileService = mock(CurrentUserProfileService.class);
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.empty());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        MockMvc unauthenticatedMvc = MockMvcBuilders.standaloneSetup(new ExchangeAccountCredentialController(
                        gatewayAuthFacade,
                        currentUserProfileService,
                        commandService,
                        verificationService,
                        permissionProbeService,
                        1
                ))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        unauthenticatedMvc.perform(post("/api/exchange-accounts/900001/credentials/1/permission-probe")
                        .contentType("application/json")
                        .content("{\"dryRun\":true,\"mode\":\"PAPER\",\"paperSafetyConfirmed\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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

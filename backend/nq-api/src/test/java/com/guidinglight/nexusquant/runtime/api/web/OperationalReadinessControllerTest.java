package com.guidinglight.nexusquant.runtime.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.runtime.api.OperationalReadinessService;

import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * OperationalReadinessControllerTest verifies the GateM-6B read-only HTTP contract.
 *
 * <p>Why: the endpoint must return current disabled capability boundaries as safe DTO fields and
 * must not expose runtime-sensitive terms or values in the serialized response.
 */
class OperationalReadinessControllerTest {

    private static final Set<String> FORBIDDEN_RESPONSE_TOKENS = Set.of(
            "secret",
            "token",
            "passphrase",
            "private key",
            "private_key",
            "cookie",
            "signature",
            "raw env",
            "raw_env"
    );

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OperationalReadinessController(new OperationalReadinessService()))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnDisabledCapabilitySummaryWithoutSensitiveMaterial() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/runtime/operational-readiness")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-operational-readiness"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-operational-readiness"))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.liveStatus.status").value("DISABLED"))
                .andExpect(jsonPath("$.liveStatus.ready").value(false))
                .andExpect(jsonPath("$.liveStatus.reasonCode").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.aiStatus.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.dhRuntimeStatus.status").value("NOT_INTEGRATED"))
                .andExpect(jsonPath("$.realProviderStatus.status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.credentialExposureStatus.status").value("NOT_EXPOSED"))
                .andExpect(jsonPath("$.externalExchangeCallStatus.status").value("DISABLED"))
                .andExpect(jsonPath("$.permissionProbeStatus.status").value("SKIPPED"))
                .andExpect(jsonPath("$.startupBoundaryStatus.status").value("SAFE_BY_DEFAULT"))
                .andExpect(jsonPath("$.profileBoundaryStatus.status").value("SAFE_SUMMARY_ONLY"))
                .andExpect(jsonPath("$.configDiagnosticsStatus.status").value("SAFE_SUMMARY_ONLY"))
                .andExpect(jsonPath("$.logDiagnosticsStatus.status").value("SAFE_SUMMARY_ONLY"))
                .andExpect(jsonPath("$.fakeDryRunOperations.mode").value("FAKE_ONLY_DRY_RUN"))
                .andExpect(jsonPath("$.fakeDryRunOperations.liveState").value("DISABLED"))
                .andExpect(jsonPath("$.fakeDryRunOperations.killState").value("UNKNOWN"))
                .andExpect(jsonPath("$.fakeDryRunOperations.tradingAuthorization").value(false))
                .andExpect(jsonPath("$.fakeDryRunOperations.productionStartAuthorization").value(false))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String lowerBody = body.toLowerCase();
        for (String token : FORBIDDEN_RESPONSE_TOKENS) {
            assertFalse(lowerBody.contains(token), "response must not expose '" + token + "': " + body);
        }
        assertFalse(body.contains("\"ready\":true"), "no 6B status may be ready: " + body);
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

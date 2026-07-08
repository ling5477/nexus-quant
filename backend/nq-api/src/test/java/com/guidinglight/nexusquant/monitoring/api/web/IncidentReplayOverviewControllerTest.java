package com.guidinglight.nexusquant.monitoring.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewQueryService;
import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewReadModel;
import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplaySeverity;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.filter.OncePerRequestFilter;

class IncidentReplayOverviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-08T09:00:00Z");

    private MockMvc mockMvc;
    private IncidentReplayOverviewQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(IncidentReplayOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IncidentReplayOverviewController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeOverviewWithBoundaryFlagsAndSafeFields() throws Exception {
        when(queryService.overview("trc-incident-overview")).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/incidents/replay/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-incident-overview"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-incident-overview"))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.totalEvidenceItems").value(6))
                .andExpect(jsonPath("$.shadowEventCount").value(2))
                .andExpect(jsonPath("$.consistencyDivergenceCount").value(1))
                .andExpect(jsonPath("$.paperAlertCount").value(1))
                .andExpect(jsonPath("$.recoveryEventCount").value(1))
                .andExpect(jsonPath("$.replayEventCount").value(1))
                .andExpect(jsonPath("$.incidentSeverity").value("HIGH"))
                .andExpect(jsonPath("$.latestEvidence[0].evidenceType").value("CONSISTENCY_DIVERGENCE"))
                .andExpect(jsonPath("$.blockers[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.blockers[3].code").value("NOT_TRADING_AUTHORIZATION"))
                .andExpect(jsonPath("$.warnings[0].code").value("INCIDENT_REPLAY_DIAGNOSTIC_ONLY"))
                .andExpect(jsonPath("$.nextSteps[0].code").value("REVIEW_INCIDENT_REPLAY_BOUNDARY"))
                .andExpect(jsonPath("$.traceId").value("trc-incident-overview"))
                .andReturn();

        verify(queryService).overview("trc-incident-overview");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeOnlyGetOverviewEndpointWithoutCommandActions() {
        List<Method> endpointMethods = Arrays.stream(IncidentReplayOverviewController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertEquals(1, endpointMethods.size());
        Method method = endpointMethods.getFirst();
        assertFalse(method.isAnnotationPresent(PostMapping.class));
        assertFalse(method.isAnnotationPresent(PatchMapping.class));
        assertFalse(method.isAnnotationPresent(PutMapping.class));
        assertFalse(method.isAnnotationPresent(DeleteMapping.class));
        String route = String.join(",", method.getAnnotation(GetMapping.class).value()).toLowerCase(Locale.ROOT);
        for (String forbiddenAction : List.of(
                "start",
                "stop",
                "cancel",
                "rerun",
                "execute",
                "trade",
                "placeorder",
                "cancelorder",
                "withdraw",
                "transfer"
        )) {
            assertFalse(route.contains(forbiddenAction), route);
        }
    }

    private IncidentReplayOverviewReadModel overview() {
        return new IncidentReplayOverviewReadModel(
                NOW,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                6,
                2,
                1,
                1,
                1,
                1,
                List.of(new IncidentReplayOverviewReadModel.LatestEvidence(
                        "CONSISTENCY_DIVERGENCE",
                        "rpt-1",
                        "DIVERGED",
                        "Divergence reasons count: 2",
                        NOW.minusSeconds(60),
                        "trace-diverged"
                )),
                IncidentReplaySeverity.HIGH,
                List.of(
                        new IncidentReplayOverviewReadModel.BoundaryMessage("LIVE_DISABLED", "CRITICAL", "LIVE disabled", "SYSTEM_BOUNDARY", null),
                        new IncidentReplayOverviewReadModel.BoundaryMessage("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "real provider absent", "SYSTEM_BOUNDARY", null),
                        new IncidentReplayOverviewReadModel.BoundaryMessage("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "private trading absent", "SYSTEM_BOUNDARY", null),
                        new IncidentReplayOverviewReadModel.BoundaryMessage("NOT_TRADING_AUTHORIZATION", "CRITICAL", "not trading authorization", "SYSTEM_BOUNDARY", null)
                ),
                List.of(new IncidentReplayOverviewReadModel.BoundaryMessage(
                        "INCIDENT_REPLAY_DIAGNOSTIC_ONLY",
                        "WARNING",
                        "diagnostic only",
                        "INCIDENT_REPLAY",
                        null
                )),
                List.of(new IncidentReplayOverviewReadModel.NextStep(
                        "REVIEW_INCIDENT_REPLAY_BOUNDARY",
                        "backend",
                        "Review incident replay boundary",
                        "boundary reviewed",
                        true
                )),
                List.of(new IncidentReplayOverviewReadModel.EvidenceAnchor(
                        "CONSISTENCY_DIVERGENCE",
                        "rpt-1",
                        "DIVERGED",
                        NOW.minusSeconds(60),
                        null
                )),
                "trc-incident-overview"
        );
    }

    private void assertNoForbiddenFields(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "apikey",
                "secret",
                "passphrase",
                "token",
                "privatekey",
                "credentialmaterial",
                "decryptedpayload",
                "encryptedpayload",
                "privateendpoint",
                "realorderid",
                "realaccountbalance",
                "realposition",
                "withdrawaddress",
                "transfertarget",
                "tradeapproved",
                "tradingready",
                "liveready",
                "authorizedfortrading"
        )) {
            assertFalse(normalized.contains(forbidden), "response must not contain " + forbidden + ": " + body);
        }
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

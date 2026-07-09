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
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewDecision;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewFreshness;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewQueryService;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewReadModel;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewSeverity;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewState;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
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

class IncidentReplayReviewOverviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:00:00Z");

    private MockMvc mockMvc;
    private IncidentReplayReviewOverviewQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(IncidentReplayReviewOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IncidentReplayReviewOverviewController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeOverviewWithBoundaryFlagsAndSafeFields() throws Exception {
        when(queryService.overview("trc-incident-review")).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/incidents/replay/review/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-incident-review"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-incident-review"))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.totalReviewItems").value(1))
                .andExpect(jsonPath("$.needsOperatorReviewCount").value(1))
                .andExpect(jsonPath("$.latestReviewItem.reviewItemId").value("irr-123"))
                .andExpect(jsonPath("$.latestReviewItem.reviewState").value("NEEDS_OPERATOR_REVIEW"))
                .andExpect(jsonPath("$.latestReviewItem.reviewDecision").value("ESCALATE_RECOMMENDED"))
                .andExpect(jsonPath("$.latestReviewItem.severity").value("HIGH"))
                .andExpect(jsonPath("$.latestReviewItem.evidenceFreshness").value("FRESH"))
                .andExpect(jsonPath("$.latestReviewItem.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.latestReviewItem.noSideEffect").value(true))
                .andExpect(jsonPath("$.latestReviewItem.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.blockers[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.warnings[0].code").value("INCIDENT_REPLAY_REVIEW_DIAGNOSTIC_ONLY"))
                .andExpect(jsonPath("$.nextSteps[0].code").value("KEEP_GET_ONLY_SELECT_ONLY"))
                .andExpect(jsonPath("$.evidenceAnchors[0].sourceType").value("PAPER_ALERT"))
                .andExpect(jsonPath("$.traceId").value("trc-incident-review"))
                .andReturn();

        verify(queryService).overview("trc-incident-review");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeOnlyGetOverviewEndpointWithoutWriteSideMappings() {
        List<Method> endpointMethods = Arrays.stream(IncidentReplayReviewOverviewController.class.getDeclaredMethods())
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
                "acknowledge",
                "escalate",
                "closeout",
                "approve",
                "reject",
                "create",
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

    private IncidentReplayReviewOverviewReadModel overview() {
        IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem item =
                new IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem(
                        "irr-123",
                        "PAPER_ALERT",
                        "alt-1",
                        "paper-alert:alt-1",
                        null,
                        null,
                        "paper-1",
                        null,
                        "op-123",
                        IncidentReplayReviewState.NEEDS_OPERATOR_REVIEW,
                        IncidentReplayReviewDecision.ESCALATE_RECOMMENDED,
                        IncidentReplayReviewSeverity.HIGH,
                        IncidentReplayReviewFreshness.FRESH,
                        "High paper alert",
                        List.of("REVIEW_RECOMMENDATION_ONLY"),
                        List.of(),
                        List.of(new IncidentReplayReviewOverviewReadModel.BoundaryMessage(
                                "HIGH_CRITICAL_ARE_PRIORITY_ONLY",
                                "WARNING",
                                "priority only",
                                "PAPER_ALERT",
                                "alt-1"
                        )),
                        List.of(new IncidentReplayReviewOverviewReadModel.NextStep(
                                "ESCALATE_MANUAL_REVIEW",
                                "operator",
                                "Escalate manually",
                                "No automatic escalation",
                                false
                        )),
                        List.of(new IncidentReplayReviewOverviewReadModel.EvidenceAnchor(
                                "PAPER_ALERT",
                                "alt-1",
                                "OPEN",
                                NOW.minusSeconds(60),
                                "trc-alert",
                                "Local alert"
                        )),
                        "trc-alert",
                        NOW,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false
                );
        LinkedHashMap<String, Long> severityBuckets = new LinkedHashMap<>();
        for (String key : List.of("NONE", "INFO", "WARNING", "HIGH", "CRITICAL", "UNKNOWN")) {
            severityBuckets.put(key, "HIGH".equals(key) ? 1L : 0L);
        }
        LinkedHashMap<String, Long> freshnessSummary = new LinkedHashMap<>();
        for (String key : List.of("FRESH", "STALE", "MISSING", "UNKNOWN")) {
            freshnessSummary.put(key, "FRESH".equals(key) ? 1L : 0L);
        }
        return new IncidentReplayReviewOverviewReadModel(
                NOW,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                1,
                0,
                0,
                1,
                0,
                0,
                0,
                0,
                item,
                List.of(item),
                severityBuckets,
                freshnessSummary,
                List.of(new IncidentReplayReviewOverviewReadModel.BoundaryMessage(
                        "LIVE_DISABLED",
                        "CRITICAL",
                        "LIVE disabled",
                        "SYSTEM_BOUNDARY",
                        null
                )),
                List.of(new IncidentReplayReviewOverviewReadModel.BoundaryMessage(
                        "INCIDENT_REPLAY_REVIEW_DIAGNOSTIC_ONLY",
                        "WARNING",
                        "diagnostic only",
                        "SYSTEM_BOUNDARY",
                        null
                )),
                List.of(new IncidentReplayReviewOverviewReadModel.NextStep(
                        "KEEP_GET_ONLY_SELECT_ONLY",
                        "backend",
                        "Keep GET-only",
                        "no write endpoint exists",
                        true
                )),
                item.evidenceAnchors(),
                "trc-incident-review"
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
                "rawsignature",
                "rawprivaterequest",
                "rawprivateresponse",
                "credentialmaterial",
                "decryptedpayload",
                "encryptedpayload",
                "privateendpointpayload",
                "realorderid",
                "realaccountbalance",
                "realposition",
                "withdrawaddress",
                "transfertarget",
                "tradeapproved",
                "tradingready",
                "liveready",
                "authorizedfortrading",
                "cantrade"
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

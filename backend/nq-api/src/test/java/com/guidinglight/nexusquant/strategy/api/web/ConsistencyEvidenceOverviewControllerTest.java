package com.guidinglight.nexusquant.strategy.api.web;

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
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceComparisonStatus;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceDivergenceSeverity;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceFreshness;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

class ConsistencyEvidenceOverviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-08T10:00:00Z");
    private static final UUID REPORT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DATASET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MockMvc mockMvc;
    private ConsistencyEvidenceOverviewQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(ConsistencyEvidenceOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConsistencyEvidenceOverviewController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeOverviewWithBoundaryFlagsAndSafeFields() throws Exception {
        when(queryService.overview("trc-consistency-evidence")).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/paper-shadow/consistency/evidence/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-consistency-evidence"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-consistency-evidence"))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.totalEvidenceItems").value(1))
                .andExpect(jsonPath("$.divergedCount").value(1))
                .andExpect(jsonPath("$.highSeverityCount").value(1))
                .andExpect(jsonPath("$.latestEvidenceItem.evidenceItemId").value("cse-123"))
                .andExpect(jsonPath("$.latestEvidenceItem.comparisonStatus").value("DIVERGED"))
                .andExpect(jsonPath("$.latestEvidenceItem.divergenceSeverity").value("HIGH"))
                .andExpect(jsonPath("$.latestEvidenceItem.evidenceFreshness").value("FRESH"))
                .andExpect(jsonPath("$.latestEvidenceItem.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.latestEvidenceItem.metricDelta.rawMetricDeltaExposed").value(false))
                .andExpect(jsonPath("$.latestEvidenceItem.metricDelta.profitConclusionInferred").value(false))
                .andExpect(jsonPath("$.latestEvidenceItem.metricDelta.tradingSignalInferred").value(false))
                .andExpect(jsonPath("$.blockers[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.warnings[0].code").value("METRIC_DELTA_SUMMARY_ONLY"))
                .andExpect(jsonPath("$.nextSteps[0].code").value("KEEP_GET_ONLY_SELECT_ONLY"))
                .andExpect(jsonPath("$.evidenceAnchors[0].sourceType").value("SHADOW_CONSISTENCY_REPORT"))
                .andExpect(jsonPath("$.traceId").value("trc-consistency-evidence"))
                .andReturn();

        verify(queryService).overview("trc-consistency-evidence");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeOnlyGetOverviewEndpointWithoutWriteSideMappings() {
        List<Method> endpointMethods = Arrays.stream(ConsistencyEvidenceOverviewController.class.getDeclaredMethods())
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
                "review",
                "acknowledge",
                "approve",
                "reject",
                "create",
                "report/create",
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

    private ConsistencyEvidenceOverviewReadModel overview() {
        ConsistencyEvidenceOverviewReadModel.MetricDeltaSummary metricDelta =
                new ConsistencyEvidenceOverviewReadModel.MetricDeltaSummary(
                        1,
                        1,
                        0,
                        List.of(new ConsistencyEvidenceOverviewReadModel.MetricDeltaItem(
                                "fillDelta",
                                2.0,
                                "count",
                                true,
                                List.of()
                        )),
                        List.of(),
                        0,
                        false,
                        false,
                        false
                );
        ConsistencyEvidenceOverviewReadModel.ConsistencyEvidenceItem item =
                new ConsistencyEvidenceOverviewReadModel.ConsistencyEvidenceItem(
                        "cse-123",
                        SHADOW_RUN_ID,
                        "paper-1",
                        REPORT_ID,
                        "sv-1",
                        DATASET_ID,
                        ConsistencyEvidenceComparisonStatus.DIVERGED,
                        ConsistencyEvidenceDivergenceSeverity.HIGH,
                        ConsistencyEvidenceFreshness.FRESH,
                        metricDelta,
                        List.of("fill mismatch"),
                        List.of("METRIC_DELTA_SUMMARY_ONLY"),
                        List.of(new ConsistencyEvidenceOverviewReadModel.EvidenceAnchor(
                                "SHADOW_CONSISTENCY_REPORT",
                                REPORT_ID.toString(),
                                "DIVERGED",
                                NOW.minusSeconds(60),
                                "trace-report",
                                "Local consistency report"
                        )),
                        "trace-report",
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
        severityBuckets.put("NONE", 0L);
        severityBuckets.put("LOW", 0L);
        severityBuckets.put("MEDIUM", 0L);
        severityBuckets.put("HIGH", 1L);
        severityBuckets.put("CRITICAL", 0L);
        severityBuckets.put("UNKNOWN", 0L);
        LinkedHashMap<String, Long> freshnessSummary = new LinkedHashMap<>();
        freshnessSummary.put("FRESH", 1L);
        freshnessSummary.put("STALE", 0L);
        freshnessSummary.put("MISSING", 0L);
        freshnessSummary.put("UNKNOWN", 0L);
        return new ConsistencyEvidenceOverviewReadModel(
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
                1,
                0,
                0,
                0,
                0,
                1,
                0,
                item,
                List.of(item),
                severityBuckets,
                freshnessSummary,
                metricDelta,
                List.of(new ConsistencyEvidenceOverviewReadModel.BoundaryMessage(
                        "LIVE_DISABLED",
                        "CRITICAL",
                        "LIVE disabled",
                        "SYSTEM_BOUNDARY",
                        null
                )),
                List.of(new ConsistencyEvidenceOverviewReadModel.BoundaryMessage(
                        "METRIC_DELTA_SUMMARY_ONLY",
                        "INFO",
                        "metric delta summary only",
                        "SYSTEM_BOUNDARY",
                        null
                )),
                List.of(new ConsistencyEvidenceOverviewReadModel.NextStep(
                        "KEEP_GET_ONLY_SELECT_ONLY",
                        "backend",
                        "Keep read model GET-only",
                        "no write endpoint exists",
                        true
                )),
                item.evidenceAnchors(),
                "trc-consistency-evidence"
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

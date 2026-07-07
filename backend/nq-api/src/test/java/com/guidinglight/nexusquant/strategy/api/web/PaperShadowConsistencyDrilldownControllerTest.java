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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownComparisonStatus;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownQueryService;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewDivergenceSeverity;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunReadOnlyNotFoundException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
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

class PaperShadowConsistencyDrilldownControllerTest {

    private static final UUID RUN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DATASET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-07T08:00:00Z");

    private MockMvc mockMvc;
    private PaperShadowConsistencyDrilldownQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(PaperShadowConsistencyDrilldownQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaperShadowConsistencyDrilldownController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnDrilldownWithFailClosedBoundaryFlagsAndSafeFields() throws Exception {
        when(queryService.drilldown(RUN_ID, "trc-drilldown")).thenReturn(drilldown());

        MvcResult result = mockMvc.perform(get("/api/paper-shadow/consistency/drilldown")
                        .param("shadowRunId", RUN_ID.toString())
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-drilldown"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-drilldown"))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.shadowRun.shadowRunId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.shadowRun.strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.shadowRun.datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.shadowRun.evaluationId").value("eval-1"))
                .andExpect(jsonPath("$.shadowRun.publishId").value("pub-1"))
                .andExpect(jsonPath("$.shadowRun.paperRunId").value("paper-1"))
                .andExpect(jsonPath("$.shadowRun.status").value("COMPLETED"))
                .andExpect(jsonPath("$.shadowRun.authorizationBoundary").value("DIAGNOSTIC_ONLY"))
                .andExpect(jsonPath("$.shadowRun.noOrderSubmission").value(true))
                .andExpect(jsonPath("$.shadowRun.noCredentialAccess").value(true))
                .andExpect(jsonPath("$.shadowRun.noPrivateEndpoint").value(true))
                .andExpect(jsonPath("$.shadowRun.noLedgerMutation").value(true))
                .andExpect(jsonPath("$.shadowRun.noAccountMutation").value(true))
                .andExpect(jsonPath("$.shadowRun.noExternalPrivateIo").value(true))
                .andExpect(jsonPath("$.latestConsistency.comparisonStatus").value("DIVERGED"))
                .andExpect(jsonPath("$.comparisonStatus").value("DIVERGED"))
                .andExpect(jsonPath("$.divergenceSeverity").value("HIGH"))
                .andExpect(jsonPath("$.metricDelta.returnDelta").value(0.12))
                .andExpect(jsonPath("$.divergenceReasons[0]").value("paper-shadow-diverged"))
                .andExpect(jsonPath("$.limitations[0]").value("diagnostic report only"))
                .andExpect(jsonPath("$.snapshotSummary.totalSnapshots").value(4))
                .andExpect(jsonPath("$.snapshotSummary.inputMarketdataSnapshots").value(1))
                .andExpect(jsonPath("$.snapshotSummary.strategyDecisionSnapshots").value(1))
                .andExpect(jsonPath("$.snapshotSummary.riskPreflightSnapshots").value(1))
                .andExpect(jsonPath("$.snapshotSummary.orderIntentPreviewSnapshots").value(1))
                .andExpect(jsonPath("$.snapshotSummary.latestSnapshotTypes[0]").value("ORDER_INTENT_PREVIEW"))
                .andExpect(jsonPath("$.eventSummary.totalEvents").value(3))
                .andExpect(jsonPath("$.eventSummary.latestEventType").value("COMPLETED"))
                .andExpect(jsonPath("$.eventSummary.latestReasonCode").value("COMPLETED"))
                .andExpect(jsonPath("$.blockers[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.blockers[1].code").value("REAL_PROVIDER_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.blockers[2].code").value("PRIVATE_TRADING_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.blockers[3].code").value("SHADOW_RUN_DIAGNOSTIC_ONLY"))
                .andExpect(jsonPath("$.blockers[4].code").value("NOT_TRADING_AUTHORIZATION"))
                .andExpect(jsonPath("$.nextSteps[0].action").value("Review diagnostic-only and not-trading-authorization boundary"))
                .andExpect(jsonPath("$.evidenceAnchors[0].sourceType").value("SHADOW_RUN"))
                .andExpect(jsonPath("$.traceId").value("trc-drilldown"))
                .andReturn();

        verify(queryService).drilldown(RUN_ID, "trc-drilldown");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldReturnNotFoundWhenShadowRunDoesNotExist() throws Exception {
        when(queryService.drilldown(RUN_ID, "trc-missing"))
                .thenThrow(new ShadowRunReadOnlyNotFoundException("shadow run not found: " + RUN_ID));

        MvcResult result = mockMvc.perform(get("/api/paper-shadow/consistency/drilldown")
                        .param("shadowRunId", RUN_ID.toString())
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("shadow run not found: " + RUN_ID))
                .andExpect(jsonPath("$.traceId").value("trc-missing"))
                .andReturn();

        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeOnlyGetDrilldownEndpointWithoutCommandActions() throws Exception {
        List<Method> endpointMethods = Arrays.stream(PaperShadowConsistencyDrilldownController.class.getDeclaredMethods())
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

        // 当前全局 ApiExceptionHandler 对 unsupported method 的 HTTP 状态不是本切片职责；
        // 这里固定的是 controller contract：drilldown 只声明一个 GET mapping，且 route 不含写侧动作。
    }

    private PaperShadowConsistencyDrilldownReadModel drilldown() {
        return new PaperShadowConsistencyDrilldownReadModel(
                NOW,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                new PaperShadowConsistencyDrilldownReadModel.ShadowRunSummary(
                        RUN_ID,
                        "sv-1",
                        DATASET_ID,
                        "eval-1",
                        "pub-1",
                        "paper-1",
                        "COMPLETED",
                        "DIAGNOSTIC_ONLY",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        NOW.minusSeconds(3600),
                        NOW,
                        NOW.minusSeconds(3500),
                        NOW
                ),
                new PaperShadowConsistencyDrilldownReadModel.ConsistencyReportSummary(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        RUN_ID,
                        "paper-1",
                        "DIVERGED",
                        JsonNodeFactory.instance.objectNode().put("returnDelta", 0.12),
                        JsonNodeFactory.instance.arrayNode().add("paper-shadow-diverged"),
                        JsonNodeFactory.instance.arrayNode().add("diagnostic report only"),
                        NOW.plusSeconds(30),
                        "trace-report"
                ),
                PaperShadowConsistencyDrilldownComparisonStatus.DIVERGED,
                ShadowRunOverviewDivergenceSeverity.HIGH,
                JsonNodeFactory.instance.objectNode().put("returnDelta", 0.12),
                JsonNodeFactory.instance.arrayNode().add("paper-shadow-diverged"),
                JsonNodeFactory.instance.arrayNode().add("diagnostic report only"),
                new PaperShadowConsistencyDrilldownReadModel.SnapshotSummary(
                        4,
                        1,
                        1,
                        1,
                        1,
                        NOW.plusSeconds(10),
                        List.of("ORDER_INTENT_PREVIEW")
                ),
                new PaperShadowConsistencyDrilldownReadModel.EventSummary(
                        3,
                        NOW.plusSeconds(5),
                        "COMPLETED",
                        "COMPLETED"
                ),
                List.of(
                        new PaperShadowConsistencyDrilldownReadModel.BoundaryMessage("LIVE_DISABLED", "CRITICAL", "LIVE disabled", "SYSTEM_BOUNDARY", null),
                        new PaperShadowConsistencyDrilldownReadModel.BoundaryMessage("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "real provider absent", "SYSTEM_BOUNDARY", null),
                        new PaperShadowConsistencyDrilldownReadModel.BoundaryMessage("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "private trading absent", "SYSTEM_BOUNDARY", null),
                        new PaperShadowConsistencyDrilldownReadModel.BoundaryMessage("SHADOW_RUN_DIAGNOSTIC_ONLY", "CRITICAL", "diagnostic only", "SHADOW_RUN", RUN_ID.toString()),
                        new PaperShadowConsistencyDrilldownReadModel.BoundaryMessage("NOT_TRADING_AUTHORIZATION", "CRITICAL", "not trading authorization", "SYSTEM_BOUNDARY", null)
                ),
                List.of(),
                List.of(new PaperShadowConsistencyDrilldownReadModel.NextStep(
                        "REVIEW_DRILLDOWN_BOUNDARY",
                        "backend",
                        "Review diagnostic-only and not-trading-authorization boundary",
                        "boundary reviewed",
                        true
                )),
                List.of(new PaperShadowConsistencyDrilldownReadModel.EvidenceAnchor(
                        "SHADOW_RUN",
                        RUN_ID.toString(),
                        "7",
                        NOW,
                        null
                )),
                "trc-drilldown"
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

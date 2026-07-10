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
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewDivergenceSeverity;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunReadOnlyNotFoundException;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunListResult;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunReadOnlyQueryService;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshotType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

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

class ShadowRunReadOnlyControllerTest {

    private static final UUID RUN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DATASET_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-07-06T14:00:00Z");

    private MockMvc mockMvc;
    private ShadowRunReadOnlyQueryService queryService;
    private ShadowRunOverviewQueryService overviewQueryService;

    @BeforeEach
    void setUp() {
        queryService = mock(ShadowRunReadOnlyQueryService.class);
        overviewQueryService = mock(ShadowRunOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShadowRunReadOnlyController(queryService, overviewQueryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnShadowRunListWithFiltersWithoutSensitiveOrTradingApprovalFields() throws Exception {
        ShadowRunListQuery expectedQuery = new ShadowRunListQuery(
                ShadowRunStatus.COMPLETED,
                "sv-1",
                DATASET_ID,
                "paper-1",
                25,
                10
        );
        when(queryService.list(expectedQuery)).thenReturn(new ShadowRunListResult(List.of(run()), 25, 10, 1));

        MvcResult result = mockMvc.perform(get("/api/shadow-runs")
                        .param("status", "COMPLETED")
                        .param("strategyVersionId", " sv-1 ")
                        .param("datasetId", DATASET_ID.toString())
                        .param("paperRunId", " paper-1 ")
                        .param("limit", "25")
                        .param("offset", "10")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-list"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-list"))
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.offset").value(10))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.items[0].datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.items[0].paperRunId").value("paper-1"))
                .andExpect(jsonPath("$.items[0].authorizationBoundary").value("DIAGNOSTIC_ONLY"))
                .andExpect(jsonPath("$.items[0].traceId").value("trace-shadow"))
                .andExpect(jsonPath("$.items[0].blockersCount").value(0))
                .andExpect(jsonPath("$.items[0].warningsCount").value(1))
                .andExpect(jsonPath("$.items[0].nextStepsCount").value(1))
                .andExpect(jsonPath("$.items[0].noOrderSubmission").value(true))
                .andExpect(jsonPath("$.items[0].noCredentialAccess").value(true))
                .andExpect(jsonPath("$.items[0].noPrivateEndpoint").value(true))
                .andExpect(jsonPath("$.items[0].noLedgerMutation").value(true))
                .andExpect(jsonPath("$.items[0].noAccountMutation").value(true))
                .andReturn();

        verify(queryService).list(expectedQuery);
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldReturnEmptyShadowRunListWhenNoFactsExist() throws Exception {
        ShadowRunListQuery expectedQuery = new ShadowRunListQuery(null, null, null, null, 50, 0);
        when(queryService.list(expectedQuery)).thenReturn(new ShadowRunListResult(List.of(), 50, 0, 0));

        mockMvc.perform(get("/api/shadow-runs")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-empty-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.total").value(0));

        verify(queryService).list(expectedQuery);
    }

    @Test
    void shouldReturnShadowRunOverviewWithFailClosedBoundaryFlags() throws Exception {
        when(overviewQueryService.overview("trc-shadow-overview")).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/shadow-runs/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-overview"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-overview"))
                .andExpect(jsonPath("$.evidenceMetadata.source").value("LOCAL_DB_SHADOW_FACTS"))
                .andExpect(jsonPath("$.evidenceMetadata.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.evidenceMetadata.freshnessStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.evidenceMetadata.staleReason").value("STALE_THRESHOLD_NOT_DEFINED"))
                .andExpect(jsonPath("$.evidenceMetadata.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.totalRuns").value(5))
                .andExpect(jsonPath("$.runningRuns").value(1))
                .andExpect(jsonPath("$.blockedRuns").value(1))
                .andExpect(jsonPath("$.failedRuns").value(1))
                .andExpect(jsonPath("$.completedRuns").value(2))
                .andExpect(jsonPath("$.staleRuns").value(1))
                .andExpect(jsonPath("$.latestRun.shadowRunId").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.latestRun.status").value("COMPLETED"))
                .andExpect(jsonPath("$.latestRun.noOrderSubmission").value(true))
                .andExpect(jsonPath("$.latestRun.noCredentialAccess").value(true))
                .andExpect(jsonPath("$.latestRun.noPrivateEndpoint").value(true))
                .andExpect(jsonPath("$.latestRun.noLedgerMutation").value(true))
                .andExpect(jsonPath("$.latestRun.noAccountMutation").value(true))
                .andExpect(jsonPath("$.latestRun.noExternalPrivateIo").value(true))
                .andExpect(jsonPath("$.latestConsistency.comparisonStatus").value("DIVERGED"))
                .andExpect(jsonPath("$.divergenceSeverity").value("HIGH"))
                .andExpect(jsonPath("$.blockers[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.blockers[1].code").value("REAL_PROVIDER_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.blockers[2].code").value("PRIVATE_TRADING_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.warnings[0].code").value("SHADOW_RUN_DIAGNOSTIC_ONLY"))
                .andExpect(jsonPath("$.nextSteps[0].action").value("review_shadow_overview"))
                .andExpect(jsonPath("$.evidenceAnchors[0].sourceType").value("SHADOW_RUN"))
                .andExpect(jsonPath("$.traceId").value("trc-shadow-overview"))
                .andReturn();

        verify(overviewQueryService).overview("trc-shadow-overview");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldReturnShadowRunDetailWithoutSensitiveOrTradingApprovalFields() throws Exception {
        when(queryService.getDetail(RUN_ID)).thenReturn(run());

        MvcResult result = mockMvc.perform(get("/api/shadow-runs/{id}", RUN_ID)
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-detail"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-detail"))
                .andExpect(jsonPath("$.id").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.authorizationBoundary").value("DIAGNOSTIC_ONLY"))
                .andExpect(jsonPath("$.sideEffectFlags.noOrderSubmission").value(true))
                .andExpect(jsonPath("$.sideEffectFlags.noCredentialAccess").value(true))
                .andExpect(jsonPath("$.sideEffectFlags.noPrivateEndpoint").value(true))
                .andExpect(jsonPath("$.warnings[0]").value("read-only diagnostic"))
                .andReturn();

        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldReturnEventsSnapshotsAndLatestConsistencyReport() throws Exception {
        when(queryService.listEvents(RUN_ID)).thenReturn(List.of(event()));
        when(queryService.listSnapshots(RUN_ID)).thenReturn(List.of(snapshot()));
        when(queryService.getLatestConsistencyReport(RUN_ID)).thenReturn(report());

        MvcResult eventsResult = mockMvc.perform(get("/api/shadow-runs/{id}/events", RUN_ID)
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-events"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-events"))
                .andExpect(jsonPath("$[0].eventType").value("COMPLETED"))
                .andExpect(jsonPath("$[0].fromStatus").value("RUNNING"))
                .andExpect(jsonPath("$[0].toStatus").value("COMPLETED"))
                .andExpect(jsonPath("$[0].metadata.diagnosticOnly").value(true))
                .andReturn();

        MvcResult snapshotsResult = mockMvc.perform(get("/api/shadow-runs/{id}/snapshots", RUN_ID)
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-snapshots"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-snapshots"))
                .andExpect(jsonPath("$[0].snapshotType").value("ORDER_INTENT_PREVIEW"))
                .andExpect(jsonPath("$[0].sequenceNo").value(1))
                .andExpect(jsonPath("$[0].payload.previewOnly").value(true))
                .andReturn();

        MvcResult reportResult = mockMvc.perform(get("/api/shadow-runs/{id}/consistency-report/latest", RUN_ID)
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-report"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-report"))
                .andExpect(jsonPath("$.comparisonStatus").value("CONSISTENT"))
                .andExpect(jsonPath("$.metricDelta.schemaVersion").value("shadow-consistency-report.v1"))
                .andExpect(jsonPath("$.limitations[0]").value("diagnostic only"))
                .andReturn();

        assertNoForbiddenFields(eventsResult.getResponse().getContentAsString());
        assertNoForbiddenFields(snapshotsResult.getResponse().getContentAsString());
        assertNoForbiddenFields(reportResult.getResponse().getContentAsString());
    }

    @Test
    void shouldReturnNotFoundWhenShadowRunDoesNotExist() throws Exception {
        when(queryService.getDetail(RUN_ID))
                .thenThrow(new ShadowRunReadOnlyNotFoundException("shadow run not found: " + RUN_ID));

        mockMvc.perform(get("/api/shadow-runs/{id}", RUN_ID)
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("shadow run not found: " + RUN_ID))
                .andExpect(jsonPath("$.traceId").value("trc-shadow-missing"));
    }

    @Test
    void shouldExposeOnlyReadOnlyGetRoutesWithoutCommandActions() {
        List<Method> endpointMethods = Arrays.stream(ShadowRunReadOnlyController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertEquals(6, endpointMethods.size());
        for (Method method : endpointMethods) {
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
    }

    private ShadowRunOverviewReadModel overview() {
        return new ShadowRunOverviewReadModel(
                NOW,
                testEvidenceMetadata("LOCAL_DB_SHADOW_FACTS"),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                5,
                1,
                1,
                1,
                2,
                1,
                new ShadowRunOverviewReadModel.LatestRun(
                        RUN_ID,
                        "sv-1",
                        DATASET_ID,
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
                new ShadowRunOverviewReadModel.LatestConsistency(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        RUN_ID,
                        "paper-1",
                        "DIVERGED",
                        JsonNodeFactory.instance.objectNode().put("returnDelta", 0.12),
                        JsonNodeFactory.instance.arrayNode().add("paper-shadow-diverged"),
                        JsonNodeFactory.instance.arrayNode().add("diagnostic only"),
                        NOW,
                        "trace-shadow"
                ),
                ShadowRunOverviewDivergenceSeverity.HIGH,
                List.of(
                        new ShadowRunOverviewReadModel.BoundaryMessage("LIVE_DISABLED", "CRITICAL", "LIVE disabled", "SYSTEM_BOUNDARY", null),
                        new ShadowRunOverviewReadModel.BoundaryMessage("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "real provider absent", "SYSTEM_BOUNDARY", null),
                        new ShadowRunOverviewReadModel.BoundaryMessage("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "private trading absent", "SYSTEM_BOUNDARY", null)
                ),
                List.of(new ShadowRunOverviewReadModel.BoundaryMessage("SHADOW_RUN_DIAGNOSTIC_ONLY", "INFO", "diagnostic only", "SYSTEM_BOUNDARY", null)),
                List.of(new ShadowRunOverviewReadModel.NextStep(
                        "REVIEW_SHADOW_OVERVIEW",
                        "backend",
                        "review_shadow_overview",
                        "overview reviewed",
                        true
                )),
                List.of(new ShadowRunOverviewReadModel.EvidenceAnchor(
                        "SHADOW_RUN",
                        RUN_ID.toString(),
                        "2",
                        NOW,
                        null
                )),
                "trc-shadow-overview"
        );
    }

    private ReadModelEvidenceMetadata testEvidenceMetadata(String source) {
        return new ReadModelEvidenceMetadata(
                source,
                ReadModelEvidenceMetadata.Availability.AVAILABLE,
                NOW,
                ReadModelEvidenceMetadata.FreshnessStatus.UNKNOWN,
                0L,
                null,
                "STALE_THRESHOLD_NOT_DEFINED",
                true,
                true,
                true,
                true
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
                "rawsignature",
                "rawprivaterequest",
                "rawprivateresponse",
                "privateendpointpayload",
                "realorderid",
                "realaccountbalance",
                "realposition",
                "withdrawaddress",
                "transfertarget",
                "tradingready",
                "liveready",
                "authorizedfortrading",
                "tradeapproved",
                "orderexecutioncommand",
                "privateadapterreference"
        )) {
            assertFalse(normalized.contains(forbidden), "response must not contain " + forbidden + ": " + body);
        }
    }

    private ShadowRun run() {
        return new ShadowRun(
                RUN_ID,
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                ShadowRunStatus.COMPLETED,
                NOW.minusSeconds(3600),
                NOW,
                JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "req-shadow",
                "idem-shadow",
                "trace-shadow",
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode().add("read-only diagnostic"),
                JsonNodeFactory.instance.arrayNode().add("review replay"),
                2,
                NOW.minusSeconds(3600),
                NOW,
                NOW.minusSeconds(3500),
                null,
                NOW
        );
    }

    private ShadowRunEvent event() {
        return new ShadowRunEvent(
                UUID.randomUUID(),
                RUN_ID,
                ShadowRunEventType.COMPLETED,
                ShadowRunStatus.RUNNING,
                ShadowRunStatus.COMPLETED,
                "COMPLETED",
                "local shadow run completed",
                JsonNodeFactory.instance.objectNode().put("diagnosticOnly", true),
                "req-shadow",
                "trace-shadow",
                NOW
        );
    }

    private ShadowRunSnapshot snapshot() {
        return new ShadowRunSnapshot(
                UUID.randomUUID(),
                RUN_ID,
                ShadowRunSnapshotType.ORDER_INTENT_PREVIEW,
                1,
                "LOCAL_CALLER_SUPPLIED_READONLY_INPUT",
                "shadow-order-intent-preview.v1",
                "sha256-demo",
                JsonNodeFactory.instance.objectNode().put("previewOnly", true),
                NOW,
                "trace-shadow",
                NOW
        );
    }

    private ShadowConsistencyReport report() {
        return new ShadowConsistencyReport(
                UUID.randomUUID(),
                RUN_ID,
                "paper-1",
                ShadowConsistencyComparisonStatus.CONSISTENT,
                JsonNodeFactory.instance.objectNode().put("schemaVersion", "shadow-consistency-report.v1"),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode().add("diagnostic only"),
                NOW,
                "trace-shadow",
                NOW
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

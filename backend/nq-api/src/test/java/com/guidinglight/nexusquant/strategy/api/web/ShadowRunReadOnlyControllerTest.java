package com.guidinglight.nexusquant.strategy.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunReadOnlyNotFoundException;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunReadOnlyQueryService;
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

    @BeforeEach
    void setUp() {
        queryService = mock(ShadowRunReadOnlyQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShadowRunReadOnlyController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
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

        assertEquals(4, endpointMethods.size());
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
                "rawprivaterequest",
                "rawprivateresponse",
                "privateendpointpayload",
                "realorderid",
                "realaccountbalance",
                "realposition",
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

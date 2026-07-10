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
import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;

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

class PythonEvaluationArtifactPreviewOverviewControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private MockMvc mockMvc;
    private PythonEvaluationArtifactPreviewOverviewQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(PythonEvaluationArtifactPreviewOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PythonEvaluationArtifactPreviewOverviewController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeNoFileBaselineOverviewWithoutTradingAuthorizationOrSensitiveFields() throws Exception {
        when(queryService.overview("trc-gatet4-artifact-preview")).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/strategy-validation/evaluation-artifacts/preview/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gatet4-artifact-preview"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-gatet4-artifact-preview"))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.pythonMlReady").value(false))
                .andExpect(jsonPath("$.pythonLiveExecutionReady").value(false))
                .andExpect(jsonPath("$.evidenceMetadata.source").value("LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW"))
                .andExpect(jsonPath("$.evidenceMetadata.availability").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.evidenceMetadata.lastCalculatedAt").isEmpty())
                .andExpect(jsonPath("$.evidenceMetadata.freshnessStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.evidenceMetadata.ageSeconds").isEmpty())
                .andExpect(jsonPath("$.evidenceMetadata.staleAfterSeconds").isEmpty())
                .andExpect(jsonPath("$.evidenceMetadata.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.evidenceMetadata.noSideEffect").value(true))
                .andExpect(jsonPath("$.evidenceMetadata.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.evidenceMetadata.liveDisabled").value(true))
                .andExpect(jsonPath("$.totalArtifactPreviews").value(0))
                .andExpect(jsonPath("$.validArtifactCount").value(0))
                .andExpect(jsonPath("$.invalidArtifactCount").value(0))
                .andExpect(jsonPath("$.staleArtifactCount").value(0))
                .andExpect(jsonPath("$.checksumFailedCount").value(0))
                .andExpect(jsonPath("$.latestArtifactPreview").isEmpty())
                .andExpect(jsonPath("$.artifactPreviews").isArray())
                .andExpect(jsonPath("$.artifactPreviews").isEmpty())
                .andExpect(jsonPath("$.schemaVersionSummary.NO_ARTIFACT_SOURCE_CONFIGURED").value(1))
                .andExpect(jsonPath("$.checksumSummary.NOT_CHECKED").value(1))
                .andExpect(jsonPath("$.metricSummaryCoverage.UNKNOWN").value(1))
                .andExpect(jsonPath("$.blockers[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.warnings[0].code").value("NO_ARTIFACT_SOURCE_CONFIGURED"))
                .andExpect(jsonPath("$.nextSteps[1].code").value("OPEN_MANIFEST_ONLY_SCHEMA_REVIEW"))
                .andExpect(jsonPath("$.evidenceAnchors[0].sourceType").value("EVALUATION_ARTIFACT_CONTRACT"))
                .andExpect(jsonPath("$.traceId").value("trc-gatet4-artifact-preview"))
                .andReturn();

        verify(queryService).overview("trc-gatet4-artifact-preview");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeOnlyGetOverviewEndpointWithoutRequestBodyOrWriteSideMappings() {
        List<Method> endpointMethods = Arrays.stream(PythonEvaluationArtifactPreviewOverviewController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertEquals(1, endpointMethods.size());
        Method method = endpointMethods.getFirst();
        assertEquals(0, method.getParameterCount());
        assertFalse(method.isAnnotationPresent(PostMapping.class));
        assertFalse(method.isAnnotationPresent(PatchMapping.class));
        assertFalse(method.isAnnotationPresent(PutMapping.class));
        assertFalse(method.isAnnotationPresent(DeleteMapping.class));
        String route = String.join(",", method.getAnnotation(GetMapping.class).value()).toLowerCase(Locale.ROOT);
        for (String forbiddenAction : List.of(
                "post",
                "put",
                "patch",
                "delete",
                "upload",
                "import",
                "bind",
                "execute",
                "validate-file",
                "file",
                "path",
                "body",
                "start",
                "stop",
                "runner",
                "scheduler",
                "backtest",
                "trade",
                "placeorder",
                "cancelorder",
                "withdraw",
                "transfer"
        )) {
            assertFalse(route.contains(forbiddenAction), route);
        }
    }

    private PythonEvaluationArtifactPreviewOverviewReadModel overview() {
        LinkedHashMap<String, Long> schemaVersionSummary = new LinkedHashMap<>();
        schemaVersionSummary.put(PythonEvaluationArtifactPreviewOverviewQueryService.SUPPORTED_SCHEMA_VERSION, 0L);
        schemaVersionSummary.put("NO_ARTIFACT_SOURCE_CONFIGURED", 1L);
        LinkedHashMap<String, Long> checksumSummary = new LinkedHashMap<>();
        checksumSummary.put("VALID", 0L);
        checksumSummary.put("INVALID", 0L);
        checksumSummary.put("MISSING", 0L);
        checksumSummary.put("NOT_CHECKED", 1L);
        checksumSummary.put("UNKNOWN", 0L);
        LinkedHashMap<String, Long> metricSummaryCoverage = new LinkedHashMap<>();
        metricSummaryCoverage.put("PRESENT", 0L);
        metricSummaryCoverage.put("INCOMPLETE", 0L);
        metricSummaryCoverage.put("FAKE_FIXTURE_ONLY", 0L);
        metricSummaryCoverage.put("MISSING", 0L);
        metricSummaryCoverage.put("UNKNOWN", 1L);
        return new PythonEvaluationArtifactPreviewOverviewReadModel(
                NOW,
                new ReadModelEvidenceMetadata(
                        "LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW",
                        ReadModelEvidenceMetadata.Availability.UNAVAILABLE,
                        null,
                        ReadModelEvidenceMetadata.FreshnessStatus.UNKNOWN,
                        null,
                        null,
                        "LAST_CALCULATED_AT_MISSING",
                        true,
                        true,
                        true,
                        true
                ),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                null,
                List.of(),
                schemaVersionSummary,
                checksumSummary,
                metricSummaryCoverage,
                List.of(new PythonEvaluationArtifactPreviewOverviewReadModel.BoundaryMessage(
                        "LIVE_DISABLED",
                        "CRITICAL",
                        "LIVE disabled",
                        "SYSTEM_BOUNDARY",
                        null
                )),
                List.of(new PythonEvaluationArtifactPreviewOverviewReadModel.BoundaryMessage(
                        "NO_ARTIFACT_SOURCE_CONFIGURED",
                        "WARNING",
                        "No artifact source is configured",
                        "NO_FILE_BASELINE",
                        null
                )),
                List.of(
                        new PythonEvaluationArtifactPreviewOverviewReadModel.NextStep(
                                "KEEP_NO_FILE_BASELINE",
                                "backend",
                                "Keep No-file baseline",
                                "no reader exists",
                                true
                        ),
                        new PythonEvaluationArtifactPreviewOverviewReadModel.NextStep(
                                "OPEN_MANIFEST_ONLY_SCHEMA_REVIEW",
                                "operator",
                                "Open separate Manifest-only review if real source is needed",
                                "separate review is approved",
                                true
                        )
                ),
                List.of(new PythonEvaluationArtifactPreviewOverviewReadModel.EvidenceAnchor(
                        "EVALUATION_ARTIFACT_CONTRACT",
                        "python-evaluation-artifact.v1",
                        "python-evaluation-artifact.v1",
                        NOW,
                        "trc-gatet4-artifact-preview",
                        "Offline contract only"
                )),
                "trc-gatet4-artifact-preview"
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
                "cantrade",
                "pythonliveexecutionreadytrue",
                "liveexecutionreadytrue"
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

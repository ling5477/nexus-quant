package com.guidinglight.nexusquant.research.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingEvidence;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingPreview;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingReason;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingScope;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingService;
import com.guidinglight.nexusquant.strategy.application.pyartifactbinding.PythonEvaluationArtifactBindingStatus;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

class PythonEvaluationArtifactBindingPreviewControllerTest {

    private static final String DATASET_ID = "ds_gateq4_sample";
    private static final String CHECKSUM = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PARAMETERS_HASH = "params_0123456789abcdef";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private PythonEvaluationArtifactBindingService bindingService;

    @BeforeEach
    void setUp() {
        bindingService = mock(PythonEvaluationArtifactBindingService.class);
        objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PythonEvaluationArtifactBindingPreviewController(bindingService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeBindingPreviewWithoutTradingAuthorizationOrSensitiveFields() throws Exception {
        when(bindingService.preview(argThat(query ->
                query.artifact() != null
                        && DATASET_ID.equals(query.expectedDatasetId())
                        && "sv-1".equals(query.expectedStrategyVersionId())
                        && "v1".equals(query.expectedStrategyVersion())
                        && "eval.v1".equals(query.expectedEvaluationVersion())
                        && CHECKSUM.equals(query.expectedChecksum())
                        && PARAMETERS_HASH.equals(query.expectedParametersHash())
                        && "PYTHON_OFFLINE".equals(query.source())
                        && Boolean.TRUE.equals(query.dryRun())
        ))).thenReturn(validPreview());

        MvcResult result = mockMvc.perform(post("/api/research/evaluation-artifacts/binding-preview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gateq4-binding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-gateq4-binding"))
                .andExpect(jsonPath("$.scope.expectedStrategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.bindingStatus").value("VALID_FOR_BINDING_PREVIEW"))
                .andExpect(jsonPath("$.validationStatus").value("VALID_FOR_BINDING_PREVIEW"))
                .andExpect(jsonPath("$.artifactType").value("PYTHON_OFFLINE_EVALUATION"))
                .andExpect(jsonPath("$.runMode").value("OFFLINE"))
                .andExpect(jsonPath("$.datasetId").value(DATASET_ID))
                .andExpect(jsonPath("$.strategyVersion").value("v1"))
                .andExpect(jsonPath("$.evaluationVersion").value("eval.v1"))
                .andExpect(jsonPath("$.checksumStatus").value("MATCHED"))
                .andExpect(jsonPath("$.schemaStatus").value("SUPPORTED"))
                .andExpect(jsonPath("$.metricsStatus").value("COMPLETE_WITH_NOT_AVAILABLE_OPTIONAL_METRICS"))
                .andExpect(jsonPath("$.offlineBoundaryStatus").value("OFFLINE_ONLY"))
                .andExpect(jsonPath("$.traceabilityStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.requiredEvidence[0].code").value("SCHEMA_VERSION"))
                .andExpect(jsonPath("$.warnings[0].code").value("BINDING_PREVIEW_NOT_IMPORT"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("tradingReady"));
        assertFalse(body.contains("liveReady"));
        assertFalse(body.contains("authorizedForTrading"));
        assertFalse(body.contains("TRADE_APPROVED"));
        assertFalse(body.contains("LIVE_READY"));
        assertFalse(body.contains("ML_READY"));
        assertFalse(body.contains("apiKey"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("token"));
        assertFalse(body.contains("passphrase"));
        assertFalse(body.contains("private key"));
    }

    @Test
    void shouldReturnBlockedPreviewWhenRequestBodyMissing() throws Exception {
        when(bindingService.preview(argThat(query -> query.artifact() == null)))
                .thenReturn(blockedPreview());

        mockMvc.perform(post("/api/research/evaluation-artifacts/binding-preview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-gateq4-binding-blocked"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-gateq4-binding-blocked"))
                .andExpect(jsonPath("$.bindingStatus").value("BLOCKED_SCHEMA_INVALID"))
                .andExpect(jsonPath("$.validationStatus").value("BLOCKED_SCHEMA_INVALID"))
                .andExpect(jsonPath("$.schemaStatus").value("INVALID"))
                .andExpect(jsonPath("$.blockers[0].code").value("BLOCKED_SCHEMA_INVALID"));
    }

    private String validRequestBody() throws Exception {
        return objectMapper.writeValueAsString(new PythonEvaluationArtifactBindingPreviewRequest(
                objectMapper.readTree("{\"schemaVersion\":\"python-evaluation-artifact.v1\"}"),
                DATASET_ID,
                "sv-1",
                "v1",
                "eval.v1",
                CHECKSUM,
                PARAMETERS_HASH,
                "PYTHON_OFFLINE",
                true
        ));
    }

    private PythonEvaluationArtifactBindingPreview validPreview() {
        return new PythonEvaluationArtifactBindingPreview(
                scope(),
                PythonEvaluationArtifactBindingStatus.VALID_FOR_BINDING_PREVIEW,
                PythonEvaluationArtifactBindingStatus.VALID_FOR_BINDING_PREVIEW,
                "PYTHON_OFFLINE_EVALUATION",
                "OFFLINE",
                DATASET_ID,
                "v1",
                "eval.v1",
                PARAMETERS_HASH,
                "MATCHED",
                "SUPPORTED",
                "COMPLETE_WITH_NOT_AVAILABLE_OPTIONAL_METRICS",
                "OFFLINE_ONLY",
                "COMPLETE",
                evidence(),
                List.of(),
                List.of(),
                List.of(new PythonEvaluationArtifactBindingReason(
                        "BINDING_PREVIEW_NOT_IMPORT",
                        "WARNING",
                        "VALID_FOR_BINDING_PREVIEW only allows read-only preview; it does not write Java facts."
                )),
                List.of("Use this result only as read-only binding preview evidence."),
                Instant.parse("2026-07-05T12:00:00Z")
        );
    }

    private PythonEvaluationArtifactBindingPreview blockedPreview() {
        return new PythonEvaluationArtifactBindingPreview(
                new PythonEvaluationArtifactBindingScope(null, true, null, null, null, null, null, null, null, null, null, null, null),
                PythonEvaluationArtifactBindingStatus.BLOCKED_SCHEMA_INVALID,
                PythonEvaluationArtifactBindingStatus.BLOCKED_SCHEMA_INVALID,
                "PYTHON_OFFLINE_EVALUATION",
                null,
                null,
                null,
                null,
                null,
                "MISSING",
                "INVALID",
                "MISSING",
                "MISSING",
                "BLOCKED",
                List.of(new PythonEvaluationArtifactBindingEvidence("SCHEMA_VERSION", "FAILED", "Artifact JSON object is required.")),
                List.of(new PythonEvaluationArtifactBindingEvidence("SCHEMA_VERSION", "FAILED", "Artifact JSON object is required.")),
                List.of(new PythonEvaluationArtifactBindingReason(
                        "BLOCKED_SCHEMA_INVALID",
                        "BLOCKER",
                        "SCHEMA_INVALID: Artifact schema is invalid or missing."
                )),
                List.of(),
                List.of("Regenerate the Python offline artifact with the supported schemaVersion and required fields."),
                Instant.parse("2026-07-05T12:00:00Z")
        );
    }

    private PythonEvaluationArtifactBindingScope scope() {
        return new PythonEvaluationArtifactBindingScope(
                "PYTHON_OFFLINE",
                true,
                DATASET_ID,
                "sv-1",
                "v1",
                "eval.v1",
                CHECKSUM,
                PARAMETERS_HASH,
                DATASET_ID,
                "v1",
                "eval.v1",
                CHECKSUM,
                PARAMETERS_HASH
        );
    }

    private List<PythonEvaluationArtifactBindingEvidence> evidence() {
        return List.of(
                new PythonEvaluationArtifactBindingEvidence("SCHEMA_VERSION", "SATISFIED", "Artifact schemaVersion is supported."),
                new PythonEvaluationArtifactBindingEvidence("RUN_MODE_OFFLINE", "SATISFIED", "Artifact runMode is OFFLINE."),
                new PythonEvaluationArtifactBindingEvidence("CHECKSUM", "SATISFIED", "checksum matches expectedChecksum.")
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

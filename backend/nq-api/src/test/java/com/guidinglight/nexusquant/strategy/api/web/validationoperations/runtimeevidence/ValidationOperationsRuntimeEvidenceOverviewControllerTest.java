package com.guidinglight.nexusquant.strategy.api.web.validationoperations.runtimeevidence;

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
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource;

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

class ValidationOperationsRuntimeEvidenceOverviewControllerTest {

    private MockMvc mockMvc;
    private ValidationOperationsRuntimeEvidenceOverviewQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(ValidationOperationsRuntimeEvidenceOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ValidationOperationsRuntimeEvidenceOverviewController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeFiveSourceDiagnosticAggregateWithoutTradingAuthorizationFields() throws Exception {
        when(queryService.overview("trace-runtime-evidence")).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/validation-operations/runtime-evidence/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trace-runtime-evidence"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trace-runtime-evidence"))
                .andExpect(jsonPath("$.evidenceMetadata.source").value("LOCAL_VALIDATION_OPERATIONS_RUNTIME_EVIDENCE"))
                .andExpect(jsonPath("$.evidenceMetadata.availability").value("PARTIAL"))
                .andExpect(jsonPath("$.evidenceMetadata.freshnessStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.evidenceMetadata.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.evidenceMetadata.noSideEffect").value(true))
                .andExpect(jsonPath("$.evidenceMetadata.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.evidenceMetadata.liveDisabled").value(true))
                .andExpect(jsonPath("$.sourceCount").value(5))
                .andExpect(jsonPath("$.availableCount").value(4))
                .andExpect(jsonPath("$.unavailableCount").value(1))
                .andExpect(jsonPath("$.unknownFreshnessCount").value(1))
                .andExpect(jsonPath("$.sources[0].sourceKey").value("SHADOW_VALIDATION_WORKFLOW"))
                .andExpect(jsonPath("$.sources[4].sourceKey").value("EVALUATION_ARTIFACT_PREVIEW"))
                .andExpect(jsonPath("$.sources[4].evidenceMetadata.availability").value("UNAVAILABLE"))
                .andReturn();

        verify(queryService).overview("trace-runtime-evidence");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeOnlyGetOverviewEndpoint() {
        List<Method> endpointMethods = Arrays.stream(ValidationOperationsRuntimeEvidenceOverviewController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertEquals(1, endpointMethods.size());
        Method method = endpointMethods.getFirst();
        assertEquals(0, method.getParameterCount());
        assertFalse(method.isAnnotationPresent(PostMapping.class));
        assertFalse(method.isAnnotationPresent(PatchMapping.class));
        assertFalse(method.isAnnotationPresent(PutMapping.class));
        assertFalse(method.isAnnotationPresent(DeleteMapping.class));
    }

    private ValidationOperationsRuntimeEvidenceOverviewReadModel overview() {
        Instant now = Instant.parse("2026-07-11T09:00:00Z");
        ReadModelEvidenceMetadata available = metadata("LOCAL_DB_SOURCE", Availability.AVAILABLE, FreshnessStatus.FRESH, now.minusSeconds(30));
        ReadModelEvidenceMetadata unavailable = metadata("LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW", Availability.UNAVAILABLE, FreshnessStatus.UNKNOWN, null);
        return new ValidationOperationsRuntimeEvidenceOverviewReadModel(
                now,
                metadata("LOCAL_VALIDATION_OPERATIONS_RUNTIME_EVIDENCE", Availability.PARTIAL, FreshnessStatus.UNKNOWN, now.minusSeconds(30)),
                5,
                4,
                0,
                1,
                0,
                4,
                0,
                1,
                List.of(
                        new RuntimeEvidenceSource("SHADOW_VALIDATION_WORKFLOW", "Shadow Validation Workflow", available),
                        new RuntimeEvidenceSource("SHADOW_RUNS", "Shadow Runs", available),
                        new RuntimeEvidenceSource("CONSISTENCY_EVIDENCE", "Consistency Evidence", available),
                        new RuntimeEvidenceSource("INCIDENT_REPLAY_REVIEW", "Incident / Replay Review", available),
                        new RuntimeEvidenceSource("EVALUATION_ARTIFACT_PREVIEW", "Evaluation Artifact Preview", unavailable)
                ),
                "trace-runtime-evidence"
        );
    }

    private ReadModelEvidenceMetadata metadata(
            String source,
            Availability availability,
            FreshnessStatus freshnessStatus,
            Instant lastCalculatedAt
    ) {
        return new ReadModelEvidenceMetadata(
                source,
                availability,
                lastCalculatedAt,
                freshnessStatus,
                lastCalculatedAt == null ? null : 30L,
                null,
                freshnessStatus == FreshnessStatus.FRESH ? null : "INCOMPLETE_OR_UNKNOWN_EVIDENCE_SOURCES",
                true,
                true,
                true,
                true
        );
    }

    private void assertNoForbiddenFields(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "cantrade", "readytotrade", "tradingready", "liveready", "authorizedfortrading",
                "approvedforlive", "tradeapproved", "executionallowed", "apikey", "secret", "passphrase",
                "privatekey", "credentialmaterial", "realaccountbalance", "realorderid"
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
            String traceId = TraceIdContext.putOrCreate(request.getHeader(TraceIdContext.TRACE_ID_HEADER));
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

package com.guidinglight.nexusquant.strategy.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparison;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonEvidence;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonReason;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonScope;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonService;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonStatus;

import java.time.Instant;
import java.util.List;
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
import org.springframework.web.filter.OncePerRequestFilter;

class PaperShadowComparisonControllerTest {

    private static final UUID DATASET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private MockMvc mockMvc;
    private PaperShadowComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        comparisonService = mock(PaperShadowComparisonService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaperShadowComparisonController(comparisonService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeComparisonWithoutSensitiveOrTradingAuthorizationFields() throws Exception {
        when(comparisonService.compare(argThat(query ->
                "strategy-alpha".equals(query.strategyId())
                        && "sv-1".equals(query.strategyVersionId())
                        && DATASET_ID.equals(query.datasetId())
                        && "eval-1".equals(query.evaluationId())
                        && "pub-1".equals(query.publishId())
                        && "ptr-1".equals(query.paperRunId())
                        && "shr-1".equals(query.shadowRunId())
        ))).thenReturn(readyComparison());

        MvcResult result = mockMvc.perform(get("/api/strategies/paper-shadow/comparison")
                        .param("strategyId", "strategy-alpha")
                        .param("strategyVersionId", "sv-1")
                        .param("datasetId", DATASET_ID.toString())
                        .param("evaluationId", "eval-1")
                        .param("publishId", "pub-1")
                        .param("paperRunId", "ptr-1")
                        .param("shadowRunId", "shr-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-paper-shadow"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-paper-shadow"))
                .andExpect(jsonPath("$.scope.strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.strategyId").value("strategy-alpha"))
                .andExpect(jsonPath("$.datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.comparisonStatus").value("READY_FOR_COMPARISON"))
                .andExpect(jsonPath("$.comparable").value(true))
                .andExpect(jsonPath("$.evaluationGateStatus").value("PASSED"))
                .andExpect(jsonPath("$.paperEvidenceStatus").value("SATISFIED"))
                .andExpect(jsonPath("$.shadowEvidenceStatus").value("SATISFIED"))
                .andExpect(jsonPath("$.requiredEvidence[0].code").value("STRATEGY_VERSION"))
                .andExpect(jsonPath("$.warnings[0].code").value("COMPARISON_NOT_TRADING_AUTHORIZATION"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("tradingReady"));
        assertFalse(body.contains("liveReady"));
        assertFalse(body.contains("authorizedForTrading"));
        assertFalse(body.contains("TRADE_APPROVED"));
        assertFalse(body.contains("LIVE_READY"));
        assertFalse(body.contains("AUTHORIZED"));
        assertFalse(body.contains("apiKey"));
        assertFalse(body.contains("secret"));
        assertFalse(body.contains("token"));
        assertFalse(body.contains("passphrase"));
        assertFalse(body.contains("credential"));
        assertFalse(body.contains("private key"));
        assertFalse(body.contains("encrypted_payload"));
        assertFalse(body.contains("decrypted_payload"));
    }

    @Test
    void shouldReturnBlockedResultWhenShadowRunnerNotImplemented() throws Exception {
        when(comparisonService.compare(argThat(query -> query.strategyVersionId() == null)))
                .thenReturn(blockedComparison());

        mockMvc.perform(get("/api/strategies/paper-shadow/comparison")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-paper-shadow-blocked"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-paper-shadow-blocked"))
                .andExpect(jsonPath("$.comparisonStatus").value("BLOCKED_SHADOW_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.shadowRunStatus").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.shadowEvidenceStatus").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.comparable").value(false))
                .andExpect(jsonPath("$.missingEvidence[0].code").value("SHADOW_RUN"));
    }

    private PaperShadowComparison readyComparison() {
        return new PaperShadowComparison(
                new PaperShadowComparisonScope("strategy-alpha", "sv-1", DATASET_ID, "eval-1", "pub-1", "ptr-1", "shr-1"),
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                "shr-1",
                "STOPPED",
                "COMPLETED",
                PaperShadowComparisonStatus.READY_FOR_COMPARISON,
                "PASSED",
                "SATISFIED",
                "SATISFIED",
                "OK",
                true,
                List.of(
                        new PaperShadowComparisonEvidence("STRATEGY_VERSION", "SATISFIED", "Strategy version exists."),
                        new PaperShadowComparisonEvidence("DATASET", "SATISFIED", "Dataset quality is OK."),
                        new PaperShadowComparisonEvidence("EVALUATION_GATE", "SATISFIED", "Evaluation passed."),
                        new PaperShadowComparisonEvidence("PUBLISH_TRACE", "SATISFIED", "Publish succeeded."),
                        new PaperShadowComparisonEvidence("PAPER_RUN", "SATISFIED", "Paper run exists."),
                        new PaperShadowComparisonEvidence("SHADOW_RUN", "SATISFIED", "Shadow run exists."),
                        new PaperShadowComparisonEvidence("TRACE_CHAIN", "SATISFIED", "Trace chain is complete.")
                ),
                List.of(),
                List.of(),
                List.of(new PaperShadowComparisonReason(
                        "COMPARISON_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "READY_FOR_COMPARISON is read-only evidence readiness only."
                )),
                List.of("Use this result only for read-only Paper vs Shadow inspection."),
                Instant.parse("2026-07-05T11:00:00Z")
        );
    }

    private PaperShadowComparison blockedComparison() {
        return new PaperShadowComparison(
                new PaperShadowComparisonScope(null, null, null, null, null, null, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "NOT_AVAILABLE",
                "NOT_IMPLEMENTED",
                PaperShadowComparisonStatus.BLOCKED_SHADOW_NOT_IMPLEMENTED,
                "NOT_AVAILABLE",
                "NOT_AVAILABLE",
                "NOT_IMPLEMENTED",
                "NOT_AVAILABLE",
                false,
                List.of(new PaperShadowComparisonEvidence(
                        "SHADOW_RUN",
                        "NOT_IMPLEMENTED",
                        "Shadow run fact source is not implemented."
                )),
                List.of(new PaperShadowComparisonEvidence(
                        "SHADOW_RUN",
                        "NOT_IMPLEMENTED",
                        "Shadow run fact source is not implemented."
                )),
                List.of(new PaperShadowComparisonReason(
                        "SHADOW_RUNNER_NOT_IMPLEMENTED",
                        "BLOCKER",
                        "Shadow runner and shadow fact source are not implemented."
                )),
                List.of(new PaperShadowComparisonReason(
                        "COMPARISON_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "READY_FOR_COMPARISON is read-only evidence readiness only."
                )),
                List.of("Do not fabricate Shadow facts."),
                Instant.parse("2026-07-05T11:00:00Z")
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

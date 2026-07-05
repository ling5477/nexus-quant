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
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGate;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateDecision;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateEvidence;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateReason;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateScope;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateService;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateStatus;

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

class StrategyEvaluationGateControllerTest {

    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockMvc mockMvc;
    private StrategyEvaluationGateService gateService;

    @BeforeEach
    void setUp() {
        gateService = mock(StrategyEvaluationGateService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StrategyEvaluationGateController(gateService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeEvaluationGateWithoutSensitiveOrTradingAuthorizationFields() throws Exception {
        when(gateService.evaluate(argThat(query ->
                "strategy-alpha".equals(query.strategyId())
                        && "sv-1".equals(query.strategyVersionId())
                        && DATASET_ID.equals(query.datasetId())
                        && "eval-1".equals(query.evaluationId())
                        && "pub-1".equals(query.publishId())
                        && "ptr-1".equals(query.paperRunId())
        ))).thenReturn(readyGate());

        MvcResult result = mockMvc.perform(get("/api/strategies/evaluation-gate")
                        .param("strategyId", "strategy-alpha")
                        .param("strategyVersionId", "sv-1")
                        .param("datasetId", DATASET_ID.toString())
                        .param("evaluationId", "eval-1")
                        .param("publishId", "pub-1")
                        .param("paperRunId", "ptr-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-evaluation-gate"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-evaluation-gate"))
                .andExpect(jsonPath("$.scope.strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.strategyId").value("strategy-alpha"))
                .andExpect(jsonPath("$.datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.gateStatus").value("READY_FOR_SHADOW_REVIEW"))
                .andExpect(jsonPath("$.gateDecision").value("RESEARCH_EVALUATION_READY_FOR_SHADOW_REVIEW"))
                .andExpect(jsonPath("$.evaluationStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.datasetQualityStatus").value("OK"))
                .andExpect(jsonPath("$.paperEvidenceStatus").value("STOPPED"))
                .andExpect(jsonPath("$.publishTraceStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.requiredEvidence[0].code").value("STRATEGY_VERSION"))
                .andExpect(jsonPath("$.warnings[0].code").value("EVALUATION_GATE_NOT_TRADING_AUTHORIZATION"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("tradingReady"));
        assertFalse(body.contains("liveReady"));
        assertFalse(body.contains("authorizedForTrading"));
        assertFalse(body.contains("TRADE_APPROVED"));
        assertFalse(body.contains("LIVE_READY"));
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
    void shouldReturnBlockedResultWhenStrategyVersionMissing() throws Exception {
        when(gateService.evaluate(argThat(query -> query.strategyVersionId() == null)))
                .thenReturn(blockedGate());

        mockMvc.perform(get("/api/strategies/evaluation-gate")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-evaluation-gate-missing"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-evaluation-gate-missing"))
                .andExpect(jsonPath("$.gateStatus").value("BLOCKED_MISSING_STRATEGY_VERSION"))
                .andExpect(jsonPath("$.gateDecision").value("RESEARCH_EVALUATION_BLOCKED"))
                .andExpect(jsonPath("$.missingEvidence[0].code").value("STRATEGY_VERSION"));
    }

    private StrategyEvaluationGate readyGate() {
        return new StrategyEvaluationGate(
                new StrategyEvaluationGateScope("strategy-alpha", "sv-1", DATASET_ID, "eval-1", "pub-1", "ptr-1"),
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW,
                StrategyEvaluationGateDecision.RESEARCH_EVALUATION_READY_FOR_SHADOW_REVIEW,
                "SUCCEEDED",
                "OK",
                "STOPPED",
                "SUCCEEDED",
                List.of(
                        new StrategyEvaluationGateEvidence("STRATEGY_VERSION", "SATISFIED", "Strategy version exists."),
                        new StrategyEvaluationGateEvidence("DATASET", "SATISFIED", "Dataset quality is OK."),
                        new StrategyEvaluationGateEvidence("EVALUATION", "SATISFIED", "Evaluation succeeded."),
                        new StrategyEvaluationGateEvidence("PUBLISH_TRACE", "SATISFIED", "Publish succeeded."),
                        new StrategyEvaluationGateEvidence("PAPER_EVIDENCE", "SATISFIED", "Paper evidence exists.")
                ),
                List.of(),
                List.of(),
                List.of(new StrategyEvaluationGateReason(
                        "EVALUATION_GATE_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "Evaluation gate is research/evaluation readiness only."
                )),
                List.of("Start a separate Shadow review task if approved."),
                Instant.parse("2026-07-05T10:00:00Z")
        );
    }

    private StrategyEvaluationGate blockedGate() {
        return new StrategyEvaluationGate(
                new StrategyEvaluationGateScope(null, null, null, null, null, null),
                null,
                null,
                null,
                null,
                null,
                null,
                StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION,
                StrategyEvaluationGateDecision.RESEARCH_EVALUATION_BLOCKED,
                "NOT_AVAILABLE",
                "NOT_AVAILABLE",
                "NOT_AVAILABLE",
                "NOT_AVAILABLE",
                List.of(new StrategyEvaluationGateEvidence(
                        "STRATEGY_VERSION",
                        "MISSING",
                        "strategyVersionId is required."
                )),
                List.of(new StrategyEvaluationGateEvidence(
                        "STRATEGY_VERSION",
                        "MISSING",
                        "strategyVersionId is required."
                )),
                List.of(new StrategyEvaluationGateReason(
                        "STRATEGY_VERSION_ID_REQUIRED",
                        "BLOCKER",
                        "strategyVersionId is required."
                )),
                List.of(new StrategyEvaluationGateReason(
                        "EVALUATION_GATE_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "Evaluation gate is research/evaluation readiness only."
                )),
                List.of("Provide a concrete strategyVersionId."),
                Instant.parse("2026-07-05T10:00:00Z")
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

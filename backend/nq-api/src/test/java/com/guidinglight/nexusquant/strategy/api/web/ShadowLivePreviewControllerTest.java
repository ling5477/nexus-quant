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
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreview;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewEvidence;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewReason;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewScope;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewService;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewSideEffectPolicy;
import com.guidinglight.nexusquant.strategy.application.shadowlivepreview.ShadowLivePreviewStatus;

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

class ShadowLivePreviewControllerTest {

    private static final UUID DATASET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MockMvc mockMvc;
    private ShadowLivePreviewService previewService;

    @BeforeEach
    void setUp() {
        previewService = mock(ShadowLivePreviewService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShadowLivePreviewController(previewService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposePreviewWithoutSensitiveOrTradingAuthorizationFields() throws Exception {
        when(previewService.preview(argThat(query ->
                "strategy-alpha".equals(query.strategyId())
                        && "sv-1".equals(query.strategyVersionId())
                        && DATASET_ID.equals(query.datasetId())
                        && "eval-1".equals(query.evaluationId())
                        && "pub-1".equals(query.publishId())
                        && "ptr-1".equals(query.paperRunId())
                        && "shr-1".equals(query.shadowRunId())
        ))).thenReturn(readyPreview());

        MvcResult result = mockMvc.perform(get("/api/strategies/shadow-live/preview")
                        .param("strategyId", "strategy-alpha")
                        .param("strategyVersionId", "sv-1")
                        .param("datasetId", DATASET_ID.toString())
                        .param("evaluationId", "eval-1")
                        .param("publishId", "pub-1")
                        .param("paperRunId", "ptr-1")
                        .param("shadowRunId", "shr-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-preview"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-preview"))
                .andExpect(jsonPath("$.scope.strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.runnerStatus").value("SKELETON_AVAILABLE"))
                .andExpect(jsonPath("$.previewStatus").value("READY_FOR_NO_SIDE_EFFECT_PREVIEW"))
                .andExpect(jsonPath("$.evaluationGateStatus").value("READY_FOR_SHADOW_REVIEW"))
                .andExpect(jsonPath("$.paperShadowComparisonStatus").value("READY_FOR_COMPARISON"))
                .andExpect(jsonPath("$.inputFactStatus").value("SATISFIED"))
                .andExpect(jsonPath("$.traceStatus").value("PREVIEW_ONLY"))
                .andExpect(jsonPath("$.orderIntentPreviewStatus").value("NOT_EXECUTED"))
                .andExpect(jsonPath("$.sideEffectPolicy[0].code").value("NO_DB_WRITE"))
                .andExpect(jsonPath("$.sideEffectPolicy[1].code").value("NO_EXTERNAL_IO"))
                .andExpect(jsonPath("$.sideEffectPolicy[2].code").value("NO_CREDENTIAL_ACCESS"))
                .andExpect(jsonPath("$.requiredEvidence[0].code").value("STRATEGY_VERSION"))
                .andExpect(jsonPath("$.warnings[0].code").value("SHADOW_LIVE_SKELETON_NOT_TRADING_AUTHORIZATION"))
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
    void shouldReturnBlockedPreviewWhenShadowFactsUnavailable() throws Exception {
        when(previewService.preview(argThat(query -> query.strategyVersionId() == null)))
                .thenReturn(blockedPreview());

        mockMvc.perform(get("/api/strategies/shadow-live/preview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-preview-blocked"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-preview-blocked"))
                .andExpect(jsonPath("$.previewStatus").value("PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.runnerStatus").value("SKELETON_AVAILABLE"))
                .andExpect(jsonPath("$.paperShadowComparisonStatus").value("BLOCKED_SHADOW_NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.traceStatus").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.orderIntentPreviewStatus").value("NOT_EXECUTED"))
                .andExpect(jsonPath("$.sideEffectPolicy[3].code").value("NO_PRIVATE_ENDPOINT"))
                .andExpect(jsonPath("$.blockers[0].code").value("SHADOW_FACTS_NOT_AVAILABLE"));
    }

    private ShadowLivePreview readyPreview() {
        return new ShadowLivePreview(
                new ShadowLivePreviewScope("strategy-alpha", "sv-1", DATASET_ID, "eval-1", "pub-1", "ptr-1", "shr-1"),
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                "shr-1",
                "SKELETON_AVAILABLE",
                ShadowLivePreviewStatus.READY_FOR_NO_SIDE_EFFECT_PREVIEW,
                "READY_FOR_SHADOW_REVIEW",
                "READY_FOR_COMPARISON",
                sideEffectPolicy(),
                "SATISFIED",
                "PREVIEW_ONLY",
                "NOT_EXECUTED",
                "PREVIEW_ONLY",
                readyEvidence(),
                List.of(),
                List.of(),
                List.of(new ShadowLivePreviewReason(
                        "SHADOW_LIVE_SKELETON_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "Shadow Live skeleton is preview-only validation."
                )),
                List.of("Use this result only as a read-only Shadow Live preview plan."),
                Instant.parse("2026-07-05T12:00:00Z")
        );
    }

    private ShadowLivePreview blockedPreview() {
        return new ShadowLivePreview(
                new ShadowLivePreviewScope(null, null, null, null, null, null, null),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "SKELETON_AVAILABLE",
                ShadowLivePreviewStatus.PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE,
                "READY_FOR_SHADOW_REVIEW",
                "BLOCKED_SHADOW_NOT_IMPLEMENTED",
                sideEffectPolicy(),
                "BLOCKED",
                "NOT_IMPLEMENTED",
                "NOT_EXECUTED",
                "NOT_EXECUTED",
                readyEvidence(),
                List.of(new ShadowLivePreviewEvidence(
                        "SHADOW_FACTS",
                        "NOT_IMPLEMENTED",
                        "Shadow facts are not implemented."
                )),
                List.of(new ShadowLivePreviewReason(
                        "SHADOW_FACTS_NOT_AVAILABLE",
                        "BLOCKER",
                        "Shadow facts are not available for preview."
                )),
                List.of(new ShadowLivePreviewReason(
                        "SHADOW_LIVE_SKELETON_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "Shadow Live skeleton is preview-only validation."
                )),
                List.of("Do not fabricate Shadow facts."),
                Instant.parse("2026-07-05T12:00:00Z")
        );
    }

    private List<ShadowLivePreviewSideEffectPolicy> sideEffectPolicy() {
        return List.of(
                new ShadowLivePreviewSideEffectPolicy("NO_DB_WRITE", "FORBIDDEN", "Persistence mutation is forbidden."),
                new ShadowLivePreviewSideEffectPolicy("NO_EXTERNAL_IO", "FORBIDDEN", "External I/O is forbidden."),
                new ShadowLivePreviewSideEffectPolicy("NO_CREDENTIAL_ACCESS", "FORBIDDEN", "Sensitive material access is forbidden."),
                new ShadowLivePreviewSideEffectPolicy("NO_PRIVATE_ENDPOINT", "FORBIDDEN", "Private provider route access is forbidden."),
                new ShadowLivePreviewSideEffectPolicy("NO_ORDER_SUBMISSION", "FORBIDDEN", "Execution submission is forbidden."),
                new ShadowLivePreviewSideEffectPolicy("NO_LEDGER_MUTATION", "FORBIDDEN", "Ledger mutation is forbidden."),
                new ShadowLivePreviewSideEffectPolicy("NO_ACCOUNT_MUTATION", "FORBIDDEN", "Account mutation is forbidden.")
        );
    }

    private List<ShadowLivePreviewEvidence> readyEvidence() {
        return List.of(
                new ShadowLivePreviewEvidence("STRATEGY_VERSION", "SATISFIED", "Strategy version exists."),
                new ShadowLivePreviewEvidence("DATASET", "SATISFIED", "Dataset quality is OK."),
                new ShadowLivePreviewEvidence("EVALUATION_GATE", "SATISFIED", "Evaluation gate passed."),
                new ShadowLivePreviewEvidence("PUBLISH_TRACE", "SATISFIED", "Publish succeeded."),
                new ShadowLivePreviewEvidence("PAPER_EVIDENCE", "SATISFIED", "Paper evidence exists."),
                new ShadowLivePreviewEvidence("PAPER_SHADOW_COMPARISON", "SATISFIED", "Comparison ready."),
                new ShadowLivePreviewEvidence("SHADOW_FACTS", "SATISFIED", "Shadow facts exist."),
                new ShadowLivePreviewEvidence("TRACE_CHAIN", "SATISFIED", "Trace chain complete."),
                new ShadowLivePreviewEvidence("SIDE_EFFECT_POLICY", "SATISFIED", "Side-effect policy is forbidden.")
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

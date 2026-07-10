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
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowEvidenceFreshness;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowSeverity;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowState;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowValidationDecision;

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

class ShadowValidationWorkflowOverviewControllerTest {

    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONSISTENCY_REPORT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-07-08T09:00:00Z");

    private MockMvc mockMvc;
    private ShadowValidationWorkflowOverviewQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(ShadowValidationWorkflowOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShadowValidationWorkflowOverviewController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeOverviewWithBoundaryFlagsAndOperatorItems() throws Exception {
        when(queryService.overview("trc-shadow-validation-workflow")).thenReturn(overview());

        MvcResult result = mockMvc.perform(get("/api/shadow-validation/workflow/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-validation-workflow"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-shadow-validation-workflow"))
                .andExpect(jsonPath("$.evidenceMetadata.source").value("LOCAL_DB_VALIDATION_WORKFLOW"))
                .andExpect(jsonPath("$.evidenceMetadata.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.evidenceMetadata.freshnessStatus").value("FRESH"))
                .andExpect(jsonPath("$.evidenceMetadata.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.totalOperatorItems").value(1))
                .andExpect(jsonPath("$.readyForOperatorReviewCount").value(1))
                .andExpect(jsonPath("$.latestOperatorItem.operatorItemId").value("op-123"))
                .andExpect(jsonPath("$.latestOperatorItem.workflowState").value("READY_FOR_OPERATOR_REVIEW"))
                .andExpect(jsonPath("$.latestOperatorItem.validationDecision").value("VALIDATION_READY"))
                .andExpect(jsonPath("$.latestOperatorItem.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.operatorItems[0].severity").value("INFO"))
                .andExpect(jsonPath("$.warnings[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.nextSteps[0].code").value("KEEP_GET_ONLY_SELECT_ONLY"))
                .andExpect(jsonPath("$.evidenceAnchors[0].sourceType").value("STRATEGY_VERSION"))
                .andExpect(jsonPath("$.traceId").value("trc-shadow-validation-workflow"))
                .andReturn();

        verify(queryService).overview("trc-shadow-validation-workflow");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeOnlyGetOverviewEndpointWithoutWriteSideMappings() {
        List<Method> endpointMethods = Arrays.stream(ShadowValidationWorkflowOverviewController.class.getDeclaredMethods())
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
                "close",
                "approve",
                "reject",
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

    private ShadowValidationWorkflowOverviewReadModel overview() {
        ShadowValidationWorkflowOverviewReadModel.OperatorItem item = new ShadowValidationWorkflowOverviewReadModel.OperatorItem(
                "op-123",
                "STRATEGY_VALIDATION",
                "sv-1",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "paper-1",
                SHADOW_RUN_ID,
                CONSISTENCY_REPORT_ID,
                null,
                ShadowValidationWorkflowState.READY_FOR_OPERATOR_REVIEW,
                ShadowValidationWorkflowValidationDecision.VALIDATION_READY,
                ShadowValidationWorkflowSeverity.INFO,
                ShadowValidationWorkflowEvidenceFreshness.FRESH,
                List.of(),
                List.of(new ShadowValidationWorkflowOverviewReadModel.BoundaryMessage(
                        "VALIDATION_READY_IS_REVIEW_ONLY",
                        "WARNING",
                        "review only",
                        "STRATEGY_VALIDATION",
                        "sv-1"
                )),
                List.of(new ShadowValidationWorkflowOverviewReadModel.NextStep(
                        "MANUAL_OPERATOR_REVIEW",
                        "operator",
                        "Review evidence",
                        "review completed without trading authorization",
                        false
                )),
                List.of(new ShadowValidationWorkflowOverviewReadModel.EvidenceAnchor(
                        "STRATEGY_VERSION",
                        "sv-1",
                        "ACTIVE",
                        NOW.minusSeconds(60),
                        "trace-ready",
                        "Local evidence"
                )),
                "trace-ready",
                NOW,
                true,
                true,
                true,
                true,
                false,
                false,
                false
        );
        return new ShadowValidationWorkflowOverviewReadModel(
                NOW,
                new ReadModelEvidenceMetadata(
                        "LOCAL_DB_VALIDATION_WORKFLOW",
                        ReadModelEvidenceMetadata.Availability.AVAILABLE,
                        NOW.minusSeconds(60),
                        ReadModelEvidenceMetadata.FreshnessStatus.FRESH,
                        60L,
                        604800L,
                        null,
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
                1,
                0,
                0,
                0,
                1,
                0,
                0,
                item,
                List.of(item),
                List.of(),
                List.of(new ShadowValidationWorkflowOverviewReadModel.BoundaryMessage(
                        "LIVE_DISABLED",
                        "CRITICAL",
                        "LIVE disabled",
                        "SYSTEM_BOUNDARY",
                        null
                )),
                List.of(new ShadowValidationWorkflowOverviewReadModel.NextStep(
                        "KEEP_GET_ONLY_SELECT_ONLY",
                        "backend",
                        "Keep read model GET-only",
                        "no write endpoint exists",
                        true
                )),
                List.of(new ShadowValidationWorkflowOverviewReadModel.EvidenceAnchor(
                        "STRATEGY_VERSION",
                        "sv-1",
                        "ACTIVE",
                        NOW.minusSeconds(60),
                        "trace-ready",
                        "Local evidence"
                )),
                "trc-shadow-validation-workflow"
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
                "privateendpoint",
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

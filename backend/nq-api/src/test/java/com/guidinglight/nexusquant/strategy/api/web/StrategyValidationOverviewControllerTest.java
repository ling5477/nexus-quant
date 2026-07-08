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
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewReadModel;

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

class StrategyValidationOverviewControllerTest {

    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-08T09:00:00Z");

    private MockMvc mockMvc;
    private StrategyValidationOverviewQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = mock(StrategyValidationOverviewQueryService.class);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StrategyValidationOverviewController(queryService))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeOverviewWithBoundaryFlagsAndSafeFields() throws Exception {
        when(queryService.overview("trc-validation-overview")).thenReturn(overview(StrategyValidationDecision.APPROVED));

        MvcResult result = mockMvc.perform(get("/api/strategy-validation/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-validation-overview"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-validation-overview"))
                .andExpect(jsonPath("$.diagnosticOnly").value(true))
                .andExpect(jsonPath("$.noSideEffect").value(true))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true))
                .andExpect(jsonPath("$.liveDisabled").value(true))
                .andExpect(jsonPath("$.realProviderImplemented").value(false))
                .andExpect(jsonPath("$.privateTradingImplemented").value(false))
                .andExpect(jsonPath("$.aiDhRuntimeIntegrated").value(false))
                .andExpect(jsonPath("$.totalStrategyVersions").value(4))
                .andExpect(jsonPath("$.evaluatedStrategyVersions").value(3))
                .andExpect(jsonPath("$.approvedForValidation").value(1))
                .andExpect(jsonPath("$.rejectedForValidation").value(1))
                .andExpect(jsonPath("$.needsReview").value(1))
                .andExpect(jsonPath("$.blocked").value(1))
                .andExpect(jsonPath("$.latestDecision.strategyVersionId").value("sv-1"))
                .andExpect(jsonPath("$.latestDecision.datasetId").value(DATASET_ID.toString()))
                .andExpect(jsonPath("$.latestDecision.evaluationReportId").value("eval-1"))
                .andExpect(jsonPath("$.latestDecision.publishId").value("pub-1"))
                .andExpect(jsonPath("$.latestDecision.paperRunId").value("paper-1"))
                .andExpect(jsonPath("$.latestDecision.shadowRunId").value(SHADOW_RUN_ID.toString()))
                .andExpect(jsonPath("$.latestDecision.decision").value("APPROVED"))
                .andExpect(jsonPath("$.latestDecision.limitations[0]").value("Decision is validation-layer only."))
                .andExpect(jsonPath("$.warnings[0].code").value("LIVE_DISABLED"))
                .andExpect(jsonPath("$.warnings[3].code").value("VALIDATION_IS_NOT_TRADING_AUTHORIZATION"))
                .andExpect(jsonPath("$.nextSteps[0].code").value("REVIEW_VALIDATION_BOUNDARY"))
                .andExpect(jsonPath("$.evidenceAnchors[0].sourceType").value("STRATEGY_VERSION"))
                .andExpect(jsonPath("$.traceId").value("trc-validation-overview"))
                .andReturn();

        verify(queryService).overview("trc-validation-overview");
        assertNoForbiddenFields(result.getResponse().getContentAsString());
    }

    @Test
    void shouldExposeNoEvidenceState() throws Exception {
        when(queryService.overview("trc-no-evidence")).thenReturn(overview(StrategyValidationDecision.NO_EVIDENCE));

        mockMvc.perform(get("/api/strategy-validation/overview")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-no-evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestDecision.decision").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.notTradingAuthorization").value(true));
    }

    @Test
    void shouldExposeOnlyGetOverviewEndpointWithoutCommandActions() {
        List<Method> endpointMethods = Arrays.stream(StrategyValidationOverviewController.class.getDeclaredMethods())
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
    }

    private StrategyValidationOverviewReadModel overview(StrategyValidationDecision decision) {
        return new StrategyValidationOverviewReadModel(
                NOW,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                4,
                3,
                1,
                1,
                1,
                1,
                new StrategyValidationOverviewReadModel.LatestDecision(
                        "sv-1",
                        DATASET_ID,
                        "eval-1",
                        "pub-1",
                        "paper-1",
                        SHADOW_RUN_ID,
                        decision,
                        List.of("Validation evidence is complete enough for review."),
                        List.of("Decision is validation-layer only."),
                        NOW.minusSeconds(60),
                        "trc-validation-overview"
                ),
                List.of(),
                List.of(
                        new StrategyValidationOverviewReadModel.BoundaryMessage("LIVE_DISABLED", "CRITICAL", "LIVE disabled", "SYSTEM_BOUNDARY", null),
                        new StrategyValidationOverviewReadModel.BoundaryMessage("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "real provider absent", "SYSTEM_BOUNDARY", null),
                        new StrategyValidationOverviewReadModel.BoundaryMessage("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "private trading absent", "SYSTEM_BOUNDARY", null),
                        new StrategyValidationOverviewReadModel.BoundaryMessage("VALIDATION_IS_NOT_TRADING_AUTHORIZATION", "CRITICAL", "not trading authorization", "SYSTEM_BOUNDARY", "sv-1")
                ),
                List.of(new StrategyValidationOverviewReadModel.NextStep(
                        "REVIEW_VALIDATION_BOUNDARY",
                        "backend",
                        "Review diagnostic-only and not-trading-authorization boundary",
                        "boundary reviewed",
                        true
                )),
                List.of(new StrategyValidationOverviewReadModel.EvidenceAnchor(
                        "STRATEGY_VERSION",
                        "sv-1",
                        "APPROVED",
                        NOW.minusSeconds(60),
                        null
                )),
                decision == StrategyValidationDecision.NO_EVIDENCE ? "trc-no-evidence" : "trc-validation-overview"
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

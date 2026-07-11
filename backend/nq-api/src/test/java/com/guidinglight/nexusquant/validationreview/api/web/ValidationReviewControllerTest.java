package com.guidinglight.nexusquant.validationreview.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewAction;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewActor;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewOperationsService;
import com.guidinglight.nexusquant.validationreview.application.ValidationReviewOperationalAuditService;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCase;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewCaseQuery;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEventType;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewException;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewSeverity;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;
import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewTransitionResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** GateV-2 Controller mappings、可信 actor、统一错误与 safety contract 回归。 */
class ValidationReviewControllerTest {

    private static final UUID CASE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-07-11T09:00:00Z");

    private MockMvc mockMvc;
    private ValidationReviewOperationsService operationsService;
    private ValidationReviewOperationalAuditService operationalAuditService;
    private ValidationReviewCase reviewCase;
    private ValidationReviewTransitionResult transitionResult;

    @BeforeEach
    void setUp() {
        GatewayAuthFacade gatewayAuthFacade = mock(GatewayAuthFacade.class);
        CurrentUserProfileService profileService = mock(CurrentUserProfileService.class);
        operationsService = mock(ValidationReviewOperationsService.class);
        operationalAuditService = mock(ValidationReviewOperationalAuditService.class);
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.of(new TokenClaims(
                "sub", "operator", List.of("OPERATOR"), NOW, NOW.plusSeconds(60), "issuer", "jti"
        )));
        when(profileService.findByUsername("operator")).thenReturn(Optional.of(new AuthUserProfile(
                11L, "operator", "hash", List.of("OPERATOR"), true
        )));
        reviewCase = openCase();
        ValidationReviewEvent event = new ValidationReviewEvent(
                UUID.randomUUID(), CASE_ID, ValidationReviewCase.LOCAL_TENANT_KEY,
                ValidationReviewEventType.ACKNOWLEDGED, ValidationReviewState.OPEN,
                ValidationReviewState.ACKNOWLEDGED, 1L, 11L, "idem", "hash", "req", "trc",
                JsonNodeFactory.instance.objectNode(), NOW
        );
        ValidationReviewCase acknowledged = new com.guidinglight.nexusquant.validationreview.domain.ValidationReviewStateMachine()
                .transition(reviewCase, ValidationReviewState.ACKNOWLEDGED, 11L, NOW);
        transitionResult = new ValidationReviewTransitionResult(acknowledged, event, false);

        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );
        ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationReviewController(
                        gatewayAuthFacade,
                        profileService,
                        operationsService,
                        operationalAuditService,
                        apiExceptionHandler
                ))
                .addFilters(new TestTraceIdFilter())
                .setMessageConverters(jsonConverter)
                .setControllerAdvice(apiExceptionHandler)
                .build();
    }

    @Test
    void shouldExposeBoundedQueriesStableEventsAndConservativeFields() throws Exception {
        when(operationsService.listCases(any(), any())).thenReturn(List.of(reviewCase));
        when(operationsService.detail(any(), any())).thenReturn(reviewCase);
        when(operationsService.events(any(), any())).thenReturn(List.of(new ValidationReviewEvent(
                UUID.randomUUID(), CASE_ID, ValidationReviewCase.LOCAL_TENANT_KEY,
                ValidationReviewEventType.ACKNOWLEDGED, ValidationReviewState.OPEN,
                ValidationReviewState.ACKNOWLEDGED, 1L, 11L, "idem", "hash", "req", "trc",
                JsonNodeFactory.instance.objectNode(), NOW
        )));

        MvcResult listResult = mockMvc.perform(get("/api/validation-review-cases")
                        .queryParam("state", "OPEN")
                        .queryParam("severity", "WARNING")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-list"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-list"))
                .andExpect(jsonPath("$[0].id").value(CASE_ID.toString()))
                .andExpect(jsonPath("$[0].diagnosticOnly").value(true))
                .andExpect(jsonPath("$[0].noSideEffect").value(true))
                .andExpect(jsonPath("$[0].notTradingAuthorization").value(true))
                .andExpect(jsonPath("$[0].liveDisabled").value(true))
                .andReturn();
        assertNoForbiddenFields(listResult.getResponse().getContentAsString());

        ArgumentCaptor<ValidationReviewCaseQuery> query = ArgumentCaptor.forClass(ValidationReviewCaseQuery.class);
        verify(operationsService).listCases(any(ValidationReviewActor.class), query.capture());
        assertEquals(50, query.getValue().limit());
        assertEquals(0, query.getValue().offset());
        assertEquals(ValidationReviewState.OPEN, query.getValue().state());
        assertEquals(ValidationReviewSeverity.WARNING, query.getValue().severity());

        mockMvc.perform(get("/api/validation-review-cases").queryParam("limit", "100"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/validation-review-cases").queryParam("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/validation-review-cases/{caseId}", CASE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        mockMvc.perform(get("/api/validation-review-cases/{caseId}/events", CASE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromState").value("OPEN"))
                .andExpect(jsonPath("$[0].toState").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$[0].requestHash").doesNotExist())
                .andExpect(jsonPath("$[0].metadata").doesNotExist());
    }

    @Test
    void shouldExposeFourActionsWithoutClientActorOwnerTenantOverride() throws Exception {
        when(operationsService.transition(
                any(), any(), any(), any(), any(), any(), any(), anyString(), anyString()
        )).thenReturn(transitionResult);
        String body = """
                {"expectedVersion":0,"reason":"local review","metadata":{"note":"safe"},
                 "actorId":999,"ownerId":999,"tenantKey":"OTHER","requestId":"client","traceId":"client"}
                """;

        for (String action : List.of("acknowledge", "escalate", "resolve", "close")) {
            mockMvc.perform(post("/api/validation-review-cases/{caseId}/{action}", CASE_ID, action)
                            .header(ValidationReviewController.IDEMPOTENCY_KEY_HEADER, "idem-" + action)
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-" + action)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"));
        }

        ArgumentCaptor<ValidationReviewActor> actor = ArgumentCaptor.forClass(ValidationReviewActor.class);
        ArgumentCaptor<ValidationReviewAction> action = ArgumentCaptor.forClass(ValidationReviewAction.class);
        verify(operationsService, org.mockito.Mockito.times(4)).transition(
                actor.capture(), any(), action.capture(), any(), any(), any(), any(), anyString(), anyString()
        );
        actor.getAllValues().forEach(value -> assertEquals(11L, value.userId()));
        assertEquals(Set.of(
                ValidationReviewAction.ACKNOWLEDGE,
                ValidationReviewAction.ESCALATE,
                ValidationReviewAction.RESOLVE,
                ValidationReviewAction.CLOSE
        ), Set.copyOf(action.getAllValues()));
    }

    @Test
    void shouldUseExistingErrorEnvelopeForNotFoundAndConflict() throws Exception {
        when(operationsService.detail(any(), any())).thenThrow(new ValidationReviewException(
                "REVIEW_CASE_NOT_FOUND", "not found", CASE_ID, null, null
        ));
        mockMvc.perform(get("/api/validation-review-cases/{caseId}", CASE_ID)
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_CASE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trc-not-found"));

        when(operationsService.transition(
                any(), any(), any(), any(), any(), any(), any(), anyString(), anyString()
        )).thenThrow(new ValidationReviewException(
                "REVIEW_CASE_VERSION_CONFLICT", "version conflict", CASE_ID,
                ValidationReviewState.OPEN, ValidationReviewState.ACKNOWLEDGED
        ));
        mockMvc.perform(post("/api/validation-review-cases/{caseId}/acknowledge", CASE_ID)
                        .header(ValidationReviewController.IDEMPOTENCY_KEY_HEADER, "idem-conflict")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-conflict")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":9,\"reason\":\"review\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_CASE_VERSION_CONFLICT"));
    }

    @Test
    void shouldAuditMalformedLifecycleBodyWithoutRawPayload() throws Exception {
        mockMvc.perform(post("/api/validation-review-cases/{caseId}/acknowledge", CASE_ID)
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-malformed")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("trc-malformed"));

        verify(operationalAuditService).recordRejected(
                org.mockito.ArgumentMatchers.eq(CASE_ID),
                org.mockito.ArgumentMatchers.eq(ValidationReviewAction.ACKNOWLEDGE),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(ValidationReviewState.ACKNOWLEDGED),
                org.mockito.ArgumentMatchers.eq(11L),
                anyString(),
                org.mockito.ArgumentMatchers.eq("trc-malformed"),
                org.mockito.ArgumentMatchers.eq("REVIEW_REQUEST_INVALID")
        );
    }

    @Test
    void shouldExposeOnlyThreeGetsAndFourLifecyclePosts() {
        List<Method> gets = Arrays.stream(ValidationReviewController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();
        List<Method> posts = Arrays.stream(ValidationReviewController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .toList();
        assertEquals(3, gets.size());
        assertEquals(4, posts.size());
        for (Method method : ValidationReviewController.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(DeleteMapping.class));
            assertFalse(method.isAnnotationPresent(PatchMapping.class));
            assertFalse(method.isAnnotationPresent(PutMapping.class));
        }
        String routes = posts.stream()
                .flatMap(method -> Arrays.stream(method.getAnnotation(PostMapping.class).value()))
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + "," + right);
        for (String required : List.of("acknowledge", "escalate", "resolve", "close")) {
            org.junit.jupiter.api.Assertions.assertTrue(routes.contains(required));
        }
        for (String forbidden : List.of("reopen", "approve", "authorize", "execute", "trade", "delete")) {
            assertFalse(routes.contains(forbidden));
        }
    }

    private static ValidationReviewCase openCase() {
        return new ValidationReviewCase(
                CASE_ID, ValidationReviewCase.LOCAL_TENANT_KEY, 11L, "LOCAL", "source",
                JsonNodeFactory.instance.objectNode().put("id", "evidence-1"),
                ValidationReviewSeverity.WARNING, ValidationReviewState.OPEN, "title", "summary", 0L, 11L,
                NOW.minusSeconds(60), NOW.minusSeconds(60), null, null, null, null, null, null, null, null,
                NOW.plusSeconds(86400)
        );
    }

    private static void assertNoForbiddenFields(String body) {
        String normalized = body.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "apikey", "secret", "passphrase", "privatekey", "credential", "cantrade",
                "tradingready", "liveready", "authorizedfortrading", "tradeapproved", "realorderid"
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

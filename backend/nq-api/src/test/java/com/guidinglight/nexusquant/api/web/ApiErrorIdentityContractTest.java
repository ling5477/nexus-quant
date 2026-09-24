package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.api.web.dto.ApiErrorResponse;
import com.guidinglight.nexusquant.api.web.dto.ApiFieldError;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.contracts.event.port.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.risk.application.port.RiskGate;
import com.guidinglight.nexusquant.trading.application.command.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.service.OrderCommandWriteService;
import com.guidinglight.nexusquant.trading.application.exception.OrderVersionConflictException;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceService;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import com.guidinglight.nexusquant.trading.api.web.TradingVerificationController;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;
import com.guidinglight.nexusquant.trading.domain.port.OrdinaryPlaceAuthorityRepository;
import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用现有交易控制器及订单准备路径验证错误身份；隔离仓储竞争，不创建任何真实外部交易。
 */
class ApiErrorIdentityContractTest {
    private static final String TRACE_ID = "trace-order-conflict-contract";
    private static final OrderRecord ORIGINAL = new OrderRecord("internal-order-id", 1001L, null,
            "PAPER", "BTC-USDT", "client-order", "BUY", "LIMIT", BigDecimal.TEN, BigDecimal.ONE,
            "external-order", OrderStatus.ACCEPTED, "accepted", TRACE_ID, "SIM", 4L);
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @AfterEach
    void clearTrace() {
        TraceIdContext.clear();
    }

    @Test
    void realStaleOrderPreparationMapsToSafeConflictWithoutRetryingMutation() throws Exception {
        OrderRepository repository = mock(OrderRepository.class);
        EventPublisherPort events = mock(EventPublisherPort.class);
        OrderCommandWriteService service = service(repository, events);
        when(repository.findByOrderId(ORIGINAL.orderId()))
                .thenReturn(Optional.of(ORIGINAL),
                        Optional.of(ORIGINAL.withStatus(OrderStatus.PARTIALLY_FILLED, "concurrent-fill")));
        // CAS 返回零只模拟已发生的代际竞争；执行真实应用分支，不替换状态机或业务拒绝条件。
        when(repository.compareAndSetStatus(anyString(), any(), anyLong(), any(), anyString(), any()))
                .thenReturn(0);
        TradingVenueGateway gateway = mock(TradingVenueGateway.class);
        OrderCommandService commands = new OrderCommandService(repository, mock(AuditLogRepository.class),
                mock(EventPublisherPort.class), gateway, service);
        TradingVerificationController controller = new TradingVerificationController(commands,
                mock(TradingQueryFacade.class), mock(TradingMaintenanceService.class),
                mock(ExchangeAccountQueryService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler()).build();

        TraceIdContext.putOrCreate(TRACE_ID);
        mvc.perform(post("/api/trading/orders/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"internal-order-id\",\"reason\":\"operator-request\"}")
                        .requestAttr(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, TRACE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.errorKey").value("ORDER_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.errorId").value("NQ-TRD-1001"))
                .andExpect(jsonPath("$.message").value(
                        "order changed concurrently; refresh the latest state before trying again"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.path").value("/api/trading/orders/cancel"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        verify(repository, times(1)).compareAndSetStatus(eq(ORIGINAL.orderId()), eq(OrderStatus.ACCEPTED),
                eq(4L), eq(OrderStatus.CANCEL_REQUESTED), eq("operator-request"), any());
        verify(repository, times(1)).compareAndSetStatus(anyString(), any(), anyLong(), any(), anyString(), any());
        verify(repository, times(2)).findByOrderId(ORIGINAL.orderId());
        verifyNoInteractions(events, gateway);
        assertEquals(4L, ORIGINAL.version());
        assertEquals(OrderStatus.ACCEPTED, ORIGINAL.status());
    }

    @Test
    void typedConflictRetainsLegacyExceptionContract() {
        OrderVersionConflictException exception = new OrderVersionConflictException("internal-order-id");
        assertInstanceOf(IllegalStateException.class, exception);
        assertEquals("stale order preparation: internal-order-id", exception.getMessage());
    }

    @Test
    void generationInvariantFailureIsNotMislabelledAsAnOrderVersionConflict() {
        OrderRepository repository = mock(OrderRepository.class);
        EventPublisherPort events = mock(EventPublisherPort.class);
        when(repository.findByOrderId(ORIGINAL.orderId())).thenReturn(Optional.of(ORIGINAL));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service(repository, events).prepareCancelOrder(request(), ORIGINAL));
        assertEquals(IllegalStateException.class, exception.getClass());
        ApiErrorResponse response = new ApiExceptionHandler().handleIllegalStateException(
                exception, new MockHttpServletRequest());
        assertEquals("STATE_CONFLICT", response.code());
        assertNull(response.errorId());
        assertNull(response.errorKey());
        verifyNoInteractions(events);
    }

    @Test
    void unregisteredLegacyCodesKeepTheirJsonShapeAndFieldDiagnostics() throws Exception {
        ApiErrorResponse response = new ApiErrorResponse(Instant.parse("2026-09-16T00:00:00Z"), 400,
                "Bad Request", "VALIDATION_ERROR", "request validation failed", "/api/example", TRACE_ID,
                List.of(new ApiFieldError("quantity", "bad-value", "must be numeric")));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));
        assertEquals(8, json.size());
        assertFalse(json.has("errorId"));
        assertFalse(json.has("errorKey"));
        assertEquals("VALIDATION_ERROR", json.get("code").asText());
        assertEquals(TRACE_ID, json.get("traceId").asText());
        assertEquals("quantity", json.at("/fieldErrors/0/field").asText());
        assertEquals("must be numeric", json.at("/fieldErrors/0/reason").asText());
        assertEquals("bad-value", json.at("/fieldErrors/0/rejectedValue").asText());
    }

    @Test
    void legacyConstructorDoesNotInferOrderIdentityFromCodeOrDiagnosticText() throws Exception {
        for (String code : List.of("STATE_CONFLICT", "ORDER_VERSION_CONFLICT", "FUTURE_ERROR")) {
            ApiErrorResponse response = new ApiErrorResponse(Instant.parse("2026-09-16T00:00:00Z"), 409,
                    "Conflict", code, "stale order preparation: internal-order-id", "/api/example",
                    TRACE_ID, List.of());
            JsonNode json = mapper.readTree(mapper.writeValueAsString(response));
            assertEquals(8, json.size());
            assertEquals(code, response.code());
            assertNull(response.errorId());
            assertNull(response.errorKey());
        }
        ApiErrorResponse generic = new ApiExceptionHandler().handleIllegalStateException(
                new IllegalStateException("stale order preparation: internal-order-id"),
                new MockHttpServletRequest());
        assertEquals("STATE_CONFLICT", generic.code());
        assertNull(generic.errorId());
        assertNull(generic.errorKey());
    }

    @Test
    void explicitIdentityRoundTripsWithLegacyWireCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/trading/orders/cancel");
        request.setAttribute(TraceIdContext.TRACE_ID_REQUEST_ATTRIBUTE, TRACE_ID);
        ApiErrorResponse response = new ApiExceptionHandler().handleOrderVersionConflict(
                new OrderVersionConflictException("internal-order-id"), request);
        String serialized = mapper.writeValueAsString(response);
        JsonNode json = mapper.readTree(serialized);
        assertEquals(10, json.size());
        assertEquals("STATE_CONFLICT", json.get("code").asText());
        assertEquals("ORDER_VERSION_CONFLICT", json.get("errorKey").asText());
        assertEquals("NQ-TRD-1001", json.get("errorId").asText());
        assertFalse(serialized.contains("internal-order-id"));
        assertEquals(response, mapper.readValue(serialized, ApiErrorResponse.class));
    }

    @Test
    void legacyJsonWithoutErrorIdStillDeserializes() throws Exception {
        ApiErrorResponse response = mapper.readValue("""
                {"timestamp":"2026-09-16T00:00:00Z","status":403,"error":"Forbidden",
                 "code":"FORBIDDEN","message":"access denied","path":"/api/example",
                 "traceId":"trace-legacy","fieldErrors":[]}
                """, ApiErrorResponse.class);
        assertEquals("FORBIDDEN", response.code());
        assertEquals("trace-legacy", response.traceId());
        assertNull(response.errorId());
        assertNull(response.errorKey());
    }

    private static OrderCommandWriteService service(OrderRepository repository, EventPublisherPort events) {
        return new OrderCommandWriteService(repository, new InMemoryOrderStateMachine(), mock(RiskGate.class),
                mock(AuditLogRepository.class), mock(RiskEventRepository.class), events,
                mock(OrdinaryPlaceAuthorityRepository.class));
    }

    private static CancelOrderRequest request() {
        return new CancelOrderRequest(ORIGINAL.orderId(), ORIGINAL.accountId(), ORIGINAL.clientOrderId(),
                "operator-request", TRACE_ID);
    }

}

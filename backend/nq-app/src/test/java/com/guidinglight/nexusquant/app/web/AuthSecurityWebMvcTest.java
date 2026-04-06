package com.guidinglight.nexusquant.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.api.web.AuthController;
import com.guidinglight.nexusquant.trading.application.query.OrderQueryView;
import com.guidinglight.nexusquant.trading.api.web.TradingVerificationController;
import com.guidinglight.nexusquant.app.config.auth.SecurityConfiguration;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.PlaceOrderResult;
import com.guidinglight.nexusquant.strategy.application.StrategyDefinitionService;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import com.guidinglight.nexusquant.strategy.application.StrategyRunQueryService;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleScanService;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleService;
import com.guidinglight.nexusquant.trading.application.TradingMaintenanceService;
import com.guidinglight.nexusquant.observability.config.ObservabilityAutoConfiguration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AuthSecurityWebMvcTest 验证最小真实认证鉴权链。
 */
@ActiveProfiles("test")
@Import({ApiExceptionHandler.class, ObservabilityAutoConfiguration.class, SecurityConfiguration.class})
@TestPropertySource(properties = {
        "nq.security.issuer=nexus-quant-test",
        "nq.security.secret=test-change-me-test-change-me-123456",
        "nq.security.access-token-ttl=PT30M",
        "nq.security.users[0].username=admin",
        "nq.security.users[0].password-hash=$2a$10$vwD9EsN2B2E/O6DkKhg60ewPvhbERSY9QNGkW1yocbpRk2BOzsO5S",
        "nq.security.users[0].roles[0]=ADMIN",
        "nq.security.users[0].roles[1]=OPERATOR",
        "nq.security.users[0].roles[2]=VIEWER",
        "nq.security.users[0].enabled=true",
        "nq.security.users[1].username=operator",
        "nq.security.users[1].password-hash=$2a$10$vwD9EsN2B2E/O6DkKhg60ewPvhbERSY9QNGkW1yocbpRk2BOzsO5S",
        "nq.security.users[1].roles[0]=OPERATOR",
        "nq.security.users[1].roles[1]=VIEWER",
        "nq.security.users[1].enabled=true",
        "nq.security.users[2].username=viewer",
        "nq.security.users[2].password-hash=$2a$10$vwD9EsN2B2E/O6DkKhg60ewPvhbERSY9QNGkW1yocbpRk2BOzsO5S",
        "nq.security.users[2].roles[0]=VIEWER",
        "nq.security.users[2].enabled=true",
        "nq.security.users[3].username=disabled",
        "nq.security.users[3].password-hash=$2a$10$vwD9EsN2B2E/O6DkKhg60ewPvhbERSY9QNGkW1yocbpRk2BOzsO5S",
        "nq.security.users[3].roles[0]=VIEWER",
        "nq.security.users[3].enabled=false"
})
@WebMvcTest(controllers = {AuthController.class, TradingVerificationController.class})
class AuthSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderCommandService orderCommandService;
    @MockitoBean
    private TradingQueryFacade tradingQueryFacade;
    @MockitoBean
    private TradingMaintenanceService tradingMaintenanceService;
    @MockitoBean
    private StrategyDefinitionService strategyDefinitionService;
    @MockitoBean
    private StrategyManualTriggerService strategyManualTriggerService;
    @MockitoBean
    private StrategyRunQueryService strategyRunQueryService;
    @MockitoBean
    private StrategyScheduleService strategyScheduleService;
    @MockitoBean
    private StrategyScheduleScanService strategyScheduleScanService;
    @MockitoBean
    private AuthUserRepository authUserRepository;
    @MockitoBean
    private ExchangeAccountQueryService exchangeAccountQueryService;

    @BeforeEach
    void setUpAuthRepository() {
        when(authUserRepository.hasAdminUser()).thenReturn(true);
        when(authUserRepository.findByUsername("admin")).thenReturn(Optional.of(profile(1L, "admin", true, "ADMIN", "OPERATOR", "VIEWER")));
        when(authUserRepository.findByUsername("operator")).thenReturn(Optional.of(profile(2L, "operator", true, "OPERATOR", "VIEWER")));
        when(authUserRepository.findByUsername("viewer")).thenReturn(Optional.of(profile(3L, "viewer", true, "VIEWER")));
        when(authUserRepository.findByUsername("disabled")).thenReturn(Optional.of(profile(4L, "disabled", false, "VIEWER")));
        when(authUserRepository.findByUsername("missing")).thenReturn(Optional.empty());
        when(exchangeAccountQueryService.findDefaultByOwnerUserId(any())).thenReturn(Optional.empty());
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-login-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-login-1"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-login-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("trc-login-2"));
    }

    @Test
    void shouldRejectDisabledUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-login-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"disabled\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").value("trc-login-3"));
    }

    @Test
    void shouldRejectUnknownUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-login-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("trc-login-4"));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedRequest() throws Exception {
        when(exchangeAccountQueryService.findDefaultByOwnerUserId(1L)).thenReturn(Optional.of(new ExchangeAccountSummary(
                900001L,
                900001L,
                1L,
                "OKX",
                "SIM",
                "rc1-admin-default",
                null,
                true,
                "ACTIVE"
        )));
        String token = loginAndExtractToken("admin", "ChangeMe123!");
        mockMvc.perform(get("/api/auth/me")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-me-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("OPERATOR"))
                .andExpect(jsonPath("$.roles[2]").value("VIEWER"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.defaultExchangeAccountId").value(900001))
                .andExpect(jsonPath("$.defaultExchangeCode").value("OKX"))
                .andExpect(jsonPath("$.defaultTradeEnv").value("SIM"))
                .andExpect(jsonPath("$.defaultAccountAlias").value("rc1-admin-default"));
    }

    @Test
    void shouldReturnNullDefaultAccountWhenUserHasNoDefaultAccount() throws Exception {
        when(exchangeAccountQueryService.findDefaultByOwnerUserId(1L)).thenReturn(Optional.empty());
        String token = loginAndExtractToken("admin", "ChangeMe123!");
        mockMvc.perform(get("/api/auth/me")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-me-no-default")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultExchangeAccountId").isEmpty())
                .andExpect(jsonPath("$.defaultExchangeCode").isEmpty())
                .andExpect(jsonPath("$.defaultTradeEnv").isEmpty())
                .andExpect(jsonPath("$.defaultAccountAlias").isEmpty());
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/trading/orders/ord-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-auth-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("trc-auth-401"));
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenInvalid() throws Exception {
        mockMvc.perform(get("/api/trading/orders/ord-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-auth-invalid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").value("trc-auth-invalid"));
    }

    @Test
    void shouldReturnForbiddenForViewerWriteRequest() throws Exception {
        String token = loginAndExtractToken("viewer", "ChangeMe123!");
        mockMvc.perform(post("/api/trading/orders")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-auth-403")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":1001,"venue":"PAPER","clientOrderId":"cid-1","symbol":"BTC-USDT","side":"BUY","orderType":"MARKET","quantity":0.001}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").value("trc-auth-403"));
    }

    @Test
    void shouldAllowOperatorWriteRequest() throws Exception {
        when(orderCommandService.placeOrder(any())).thenReturn(new PlaceOrderResult("ord-1", OrderStatus.ACCEPTED, false));
        String token = loginAndExtractToken("operator", "ChangeMe123!");
        mockMvc.perform(post("/api/trading/orders")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-auth-200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":1001,"venue":"PAPER","clientOrderId":"cid-2","symbol":"BTC-USDT","side":"BUY","orderType":"MARKET","quantity":0.001}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("placeOrder"))
                .andExpect(jsonPath("$.traceId").value("trc-auth-200"));
        verify(orderCommandService).placeOrder(any());
    }

    @Test
    void shouldAllowAuthenticatedGetRequest() throws Exception {
        String token = loginAndExtractToken("viewer", "ChangeMe123!");
        when(tradingQueryFacade.queryOrder(eq("ord-1"), eq("trc-auth-get"))).thenReturn(Optional.of(new OrderQueryView(
                "ord-1",
                1001L,
                "PAPER",
                "BTC-USDT",
                "cid-1",
                "ext-1",
                new BigDecimal("100"),
                new BigDecimal("0.001"),
                OrderStatus.ACCEPTED,
                "trc-auth-get"
        )));
        mockMvc.perform(get("/api/trading/orders/ord-1")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-auth-get")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord-1"));
    }

    private String loginAndExtractToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-login-helper-" + username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode jsonNode = objectMapper.readTree(body);
        return jsonNode.path("accessToken").asText();
    }

    private AuthUserProfile profile(Long userId, String username, boolean enabled, String... roles) {
        return new AuthUserProfile(
                userId,
                username,
                "$2a$10$vwD9EsN2B2E/O6DkKhg60ewPvhbERSY9QNGkW1yocbpRk2BOzsO5S",
                java.util.List.of(roles),
                enabled
        );
    }
}



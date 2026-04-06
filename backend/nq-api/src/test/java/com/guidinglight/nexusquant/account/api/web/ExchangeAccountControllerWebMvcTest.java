package com.guidinglight.nexusquant.account.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.account.application.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.TokenClaims;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class ExchangeAccountControllerWebMvcTest {

    private MockMvc mockMvc;
    private ExchangeAccountQueryService queryService;
    private ExchangeAccountCommandService commandService;

    @BeforeEach
    void setUp() {
        GatewayAuthFacade gatewayAuthFacade = mock(GatewayAuthFacade.class);
        CurrentUserProfileService currentUserProfileService = mock(CurrentUserProfileService.class);
        queryService = mock(ExchangeAccountQueryService.class);
        commandService = mock(ExchangeAccountCommandService.class);
        when(gatewayAuthFacade.currentUser()).thenReturn(Optional.of(new TokenClaims("sub", "admin", List.of("ADMIN"), Instant.now(), Instant.now().plusSeconds(60), "issuer", "jti")));
        when(currentUserProfileService.findByUsername("admin")).thenReturn(Optional.of(new AuthUserProfile(1L, "admin", "hash", List.of("ADMIN"), true)));
        mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeAccountController(gatewayAuthFacade, currentUserProfileService, queryService, commandService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeListDetailAndWriteActions() throws Exception {
        ExchangeAccountSummary summary = new ExchangeAccountSummary(900001L, 900001L, 1L, "OKX", "SIM", "demo", "ext-1", true, "ACTIVE");
        when(queryService.listByOwnerUserId(1L)).thenReturn(List.of(summary));
        when(queryService.findByIdForOwner(1L, 900001L)).thenReturn(Optional.of(summary));
        when(commandService.create(any(), any())).thenReturn(summary);
        when(commandService.updateProfile(any(), any(), any())).thenReturn(summary);
        when(commandService.enable(any(), any())).thenReturn(summary);
        when(commandService.disable(any(), any())).thenReturn(new ExchangeAccountSummary(900001L, 900001L, 1L, "OKX", "SIM", "demo", "ext-1", false, "DISABLED"));
        when(commandService.setDefault(any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/exchange-accounts").header(TraceIdContext.TRACE_ID_HEADER, "trc-account-list"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-account-list"))
                .andExpect(jsonPath("$[0].exchangeAccountId").value(900001));

        mockMvc.perform(get("/api/exchange-accounts/900001").header(TraceIdContext.TRACE_ID_HEADER, "trc-account-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeCode").value("OKX"));

        mockMvc.perform(post("/api/exchange-accounts")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-account-create")
                        .contentType("application/json")
                        .content("{\"exchangeCode\":\"OKX\",\"tradeEnv\":\"SIM\",\"accountAlias\":\"demo\",\"externalAccountRef\":\"ext-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountAlias").value("demo"));

        mockMvc.perform(patch("/api/exchange-accounts/900001")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-account-update")
                        .contentType("application/json")
                        .content("{\"accountAlias\":\"demo\",\"externalAccountRef\":\"ext-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalAccountRef").value("ext-1"));

        mockMvc.perform(post("/api/exchange-accounts/900001/set-default").header(TraceIdContext.TRACE_ID_HEADER, "trc-account-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    private static final class TestTraceIdFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, java.io.IOException {
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

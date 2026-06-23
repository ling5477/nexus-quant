package com.guidinglight.nexusquant.adapters.api.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.adapter.api.service.DefaultAdapterReadinessService;
import com.guidinglight.nexusquant.adapters.api.AdapterReadinessStatusService;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;

import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AdapterReadinessControllerTest 验证 GateM-5A 只读 readiness 端点的路由、映射与 fail-closed / 脱敏不变量。
 * <p>
 * Why:
 * standalone MockMvc 装配真实 {@link AdapterReadinessStatusService} + {@link DefaultAdapterReadinessService}（纯静态
 * fail-closed 策略），证明 {@code GET /api/adapters/readiness} 不触达真实 adapter delegate / 交易所 / credential，
 * 且响应中 OKX / Binance 全部 allowed=false、无 READY、PLACE/CANCEL liveAuthorized=false、不泄漏 secret。
 */
class AdapterReadinessControllerTest {

    private static final Set<String> SECRET_TOKENS =
            Set.of("secret", "apikey", "api_key", "token", "signature", "passphrase");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 用公共构造器装配真实 status service + 纯静态 fail-closed readiness 策略；clock 不影响 fail-closed 断言。
        AdapterReadinessStatusService service =
                new AdapterReadinessStatusService(new DefaultAdapterReadinessService());
        mockMvc = MockMvcBuilders.standaloneSetup(new AdapterReadinessController(service))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeFailClosedReadinessSnapshot() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/adapters/readiness")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-adapter-readiness"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-adapter-readiness"))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.items.length()").value(45))
                // OKX / BINANCE place order：fail-closed，未获 LIVE 授权。
                .andExpect(jsonPath("$.items[?(@.venue=='OKX' && @.capability=='PLACE_ORDER')].allowed", contains(false)))
                .andExpect(jsonPath("$.items[?(@.venue=='OKX' && @.capability=='PLACE_ORDER')].liveAuthorized", contains(false)))
                .andExpect(jsonPath("$.items[?(@.venue=='OKX' && @.capability=='PLACE_ORDER')].status", contains("NOT_READY")))
                .andExpect(jsonPath("$.items[?(@.venue=='BINANCE' && @.capability=='CANCEL_ORDER')].liveAuthorized", contains(false)))
                // permission probe：real provider 未实现。
                .andExpect(jsonPath("$.items[?(@.venue=='BINANCE' && @.capability=='PERMISSION_PROBE')].reasons[*]",
                        hasItem("REAL_PROVIDER_NOT_IMPLEMENTED")))
                // Noop 家族：不是 success，状态 NO_REAL。
                .andExpect(jsonPath("$.items[?(@.venue=='NOOP' && @.capability=='PLACE_ORDER')].status", contains("NO_REAL")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // 没有任何能力被判 READY 或 allowed=true。
        assertFalse(body.contains("\"status\":\"READY\""), "no capability may be READY: " + body);
        assertFalse(body.contains("\"allowed\":true"), "no capability may be allowed: " + body);
        // 整个响应体不得出现 secret / apiKey / token / signature / passphrase。
        String lowerBody = body.toLowerCase();
        for (String token : SECRET_TOKENS) {
            assertFalse(lowerBody.contains(token), "response must not leak '" + token + "': " + body);
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

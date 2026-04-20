package com.guidinglight.nexusquant.marketdata.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncResult;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncService;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

class InstrumentCatalogControllerTest {

    private MockMvc mockMvc;
    private InstrumentCatalogService instrumentCatalogService;
    private InstrumentCatalogSyncService instrumentCatalogSyncService;

    @BeforeEach
    void setUp() {
        instrumentCatalogService = mock(InstrumentCatalogService.class);
        instrumentCatalogSyncService = mock(InstrumentCatalogSyncService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InstrumentCatalogController(instrumentCatalogService, instrumentCatalogSyncService))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeInstrumentCatalogList() throws Exception {
        when(instrumentCatalogService.list("BINANCE")).thenReturn(List.of(new InstrumentCatalogItem(
                1L,
                "BINANCE",
                "SPOT",
                "BTCUSDT",
                "BTC-USDT",
                "BTC",
                "USDT",
                "TRADING",
                new BigDecimal("0.01"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.001"),
                "BINANCE_FILTERS_CACHE",
                Instant.parse("2026-04-18T09:00:00Z"),
                Instant.parse("2026-04-18T09:00:00Z"),
                Instant.parse("2026-04-18T09:00:00Z")
        )));

        mockMvc.perform(get("/api/instruments")
                        .param("exchangeCode", "BINANCE")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-instrument-list"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-instrument-list"))
                .andExpect(jsonPath("$[0].exchangeCode").value("BINANCE"))
                .andExpect(jsonPath("$[0].internalSymbol").value("BTC-USDT"))
                .andExpect(jsonPath("$[0].baseAsset").value("BTC"))
                .andExpect(jsonPath("$[0].tickSize").value(0.01));
    }

    @Test
    void shouldExposeInstrumentCatalogSyncSummary() throws Exception {
        when(instrumentCatalogSyncService.sync("BINANCE", "trc-instrument-sync")).thenReturn(new InstrumentCatalogSyncResult(
                List.of("BINANCE"),
                2,
                1,
                1,
                Instant.parse("2026-04-18T09:00:00Z"),
                Instant.parse("2026-04-18T09:00:01Z")
        ));

        mockMvc.perform(post("/api/instruments/sync")
                        .param("exchangeCode", "BINANCE")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-instrument-sync"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-instrument-sync"))
                .andExpect(jsonPath("$.exchangeCodes[0]").value("BINANCE"))
                .andExpect(jsonPath("$.rowsRead").value(2))
                .andExpect(jsonPath("$.rowsInserted").value(1))
                .andExpect(jsonPath("$.rowsUpdated").value(1));
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

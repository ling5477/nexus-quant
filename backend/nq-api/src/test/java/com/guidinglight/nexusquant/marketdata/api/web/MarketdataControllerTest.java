package com.guidinglight.nexusquant.marketdata.api.web;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.marketdata.application.MarketdataBarIngestService;
import com.guidinglight.nexusquant.marketdata.application.MarketdataFixtureIngestionResult;
import com.guidinglight.nexusquant.marketdata.application.MarketdataIngestionService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

class MarketdataControllerTest {

    private MockMvc mockMvc;
    private HistoricalMarketDataPort historicalMarketDataPort;
    private MarketdataBarIngestService marketdataBarIngestService;
    private MarketdataIngestionService marketdataIngestionService;

    @BeforeEach
    void setUp() {
        historicalMarketDataPort = mock(HistoricalMarketDataPort.class);
        marketdataBarIngestService = mock(MarketdataBarIngestService.class);
        marketdataIngestionService = mock(MarketdataIngestionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketdataController(
                        marketdataBarIngestService,
                        historicalMarketDataPort,
                        marketdataIngestionService
                ))
                .addFilters(new TestTraceIdFilter())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeBarsWithCanonicalRouteAndMappedResponse() throws Exception {
        when(historicalMarketDataPort.loadBars(argThat(this::matchesQuery))).thenReturn(List.of(new HistoricalBar(
                "BINANCE",
                "BTCUSDT",
                BarInterval.ONE_MINUTE,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:59Z"),
                new BigDecimal("100.00"),
                new BigDecimal("101.00"),
                new BigDecimal("99.50"),
                new BigDecimal("100.50"),
                new BigDecimal("12.34")
        )));

        mockMvc.perform(get("/api/marketdata/bars")
                        .param("exchangeCode", "BINANCE")
                        .param("marketType", "SPOT")
                        .param("symbol", "BTCUSDT")
                        .param("interval", "1m")
                        .param("startTime", "2025-01-01T00:00:00Z")
                        .param("endTime", "2025-01-01T00:00:59Z")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-marketdata-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-marketdata-1"))
                .andExpect(jsonPath("$[0].exchangeCode").value("BINANCE"))
                .andExpect(jsonPath("$[0].marketType").value("SPOT"))
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$[0].interval").value("1m"))
                .andExpect(jsonPath("$[0].openTime").exists())
                .andExpect(jsonPath("$[0].closePrice").value(100.50))
                .andExpect(jsonPath("$[0].volume").value(12.34));
    }

    @Test
    void shouldCreateAndRunMarketdataIngestionJob() throws Exception {
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID runId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(marketdataIngestionService.createJob(argThat(command ->
                "BINANCE".equals(command.exchangeCode())
                        && "SPOT".equals(command.marketType())
                        && "BTC-USDT".equals(command.symbol())
                        && "1m".equals(command.interval())
        ))).thenReturn(new MarketdataIngestionJob(
                jobId,
                "BINANCE",
                "SPOT",
                "BTC-USDT",
                BarInterval.ONE_MINUTE,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:05:59Z"),
                MarketdataIngestionStatus.CREATED,
                "EXCHANGE_HISTORICAL",
                "local",
                Instant.parse("2026-04-06T00:00:00Z"),
                Instant.parse("2026-04-06T00:00:00Z"),
                "{}"
        ));
        when(marketdataIngestionService.runOnce(jobId)).thenReturn(new MarketdataIngestionRun(
                runId,
                jobId,
                MarketdataIngestionStatus.SUCCEEDED,
                Instant.parse("2026-04-06T00:00:01Z"),
                Instant.parse("2026-04-06T00:00:02Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:05:59Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:05:59Z"),
                6,
                6,
                0,
                0,
                null,
                "{}",
                Instant.parse("2026-04-06T00:00:01Z")
        ));

        mockMvc.perform(post("/api/marketdata/ingestion-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"exchangeCode":"BINANCE","marketType":"SPOT","symbol":"BTC-USDT","interval":"1m","startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T00:05:59Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));

        mockMvc.perform(post("/api/marketdata/ingestion-jobs/{jobId}/run-once", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.fetchedBars").value(6))
                .andExpect(jsonPath("$.insertedBars").value(6));
    }

    @Test
    void shouldExposeFixtureIngestionSummary() throws Exception {
        when(marketdataBarIngestService.ingestFixture(argThat(command ->
                "BINANCE_BTCUSDT_1M_SAMPLE".equals(command.fixtureId())
                        && "BINANCE".equals(command.exchangeCode())
                        && "BTCUSDT".equals(command.symbol())
                        && "1m".equals(command.interval())
        ))).thenReturn(new MarketdataFixtureIngestionResult(
                "BINANCE_BTCUSDT_1M_SAMPLE",
                "BINANCE",
                "BTCUSDT",
                "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:05:59Z"),
                6,
                6,
                0,
                Instant.parse("2026-04-06T00:00:00Z"),
                Instant.parse("2026-04-06T00:00:01Z")
        ));

        mockMvc.perform(post("/api/marketdata/bars/ingestions/fixture")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-marketdata-ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fixtureId":"BINANCE_BTCUSDT_1M_SAMPLE","exchangeCode":"BINANCE","symbol":"BTCUSDT","interval":"1m","startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T00:05:59Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtureId").value("BINANCE_BTCUSDT_1M_SAMPLE"))
                .andExpect(jsonPath("$.rowsRead").value(6))
                .andExpect(jsonPath("$.rowsInserted").value(6))
                .andExpect(jsonPath("$.rowsUpdated").value(0))
                .andExpect(jsonPath("$.requestedRange.startTime").exists())
                .andExpect(jsonPath("$.requestedRange.endTime").exists());
    }

    private boolean matchesQuery(HistoricalMarketDataQuery query) {
        return query.exchangeCode().equals("BINANCE")
                && query.marketType().equals("SPOT")
                && query.symbol().equals("BTCUSDT")
                && query.interval() == BarInterval.ONE_MINUTE
                && query.datasetSpec().exchangeCode().equals("BINANCE")
                && query.startTime().equals(Instant.parse("2025-01-01T00:00:00Z"))
                && query.endTime().equals(Instant.parse("2025-01-01T00:00:59Z"));
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

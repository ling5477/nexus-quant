package com.guidinglight.nexusquant.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MarketdataControllerLocalIntegrationTest 验证 RC1-5-A 的 fixture ingest 与真实 DB query 闭环。
 * <p>
 * Why:
 * standalone controller 测试只能证明路由和 DTO 映射，RC1-5 还必须确认 bars 已真实写入 `marketdata_bars`
 * 且查询接口确实读到了数据库事实，而不是 mock 结果。
 */
@SpringBootTest(classes = NexusQuantApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@WithMockUser(username = "local-operator", roles = {"OPERATOR", "VIEWER"})
class MarketdataControllerLocalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldIngestFixtureAndQueryRealBarsFromDatabase() throws Exception {
        String ingestResponseBody = mockMvc.perform(post("/api/marketdata/bars/ingestions/fixture")
                        .with(csrf())
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-marketdata-local-ingest")
                        .contentType("application/json")
                        .content("""
                                {"fixtureId":"BINANCE_BTCUSDT_1M_SAMPLE","exchangeCode":"BINANCE","symbol":"BTCUSDT","interval":"1m","startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T00:05:59Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-marketdata-local-ingest"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode ingestResponse = objectMapper.readTree(ingestResponseBody);
        assertEquals(6, ingestResponse.path("rowsRead").asInt());
        assertEquals(6, ingestResponse.path("rowsInserted").asInt() + ingestResponse.path("rowsUpdated").asInt());
        assertNotNull(ingestResponse.path("startedAt").asText(null));
        assertNotNull(ingestResponse.path("finishedAt").asText(null));

        String queryResponseBody = mockMvc.perform(get("/api/marketdata/bars")
                        .header(TraceIdContext.TRACE_ID_HEADER, "trc-marketdata-local-query")
                        .param("exchangeCode", "BINANCE")
                        .param("symbol", "BTCUSDT")
                        .param("interval", "1m")
                        .param("startTime", "2025-01-01T00:00:00Z")
                        .param("endTime", "2025-01-01T00:05:59Z"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-marketdata-local-query"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode queryResponse = objectMapper.readTree(queryResponseBody);
        assertEquals(6, queryResponse.size());
        assertEquals("BTCUSDT", queryResponse.get(0).path("symbol").asText());
        assertEquals("1m", queryResponse.get(0).path("interval").asText());
        assertEquals(0, new java.math.BigDecimal(queryResponse.get(0).path("closePrice").asText()).compareTo(new java.math.BigDecimal("43010.00")));

        Integer storedRows = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM marketdata_bars
                        WHERE exchange_code = ?
                          AND symbol = ?
                          AND "interval" = ?
                          AND open_time >= ?
                          AND close_time <= ?
                        """,
                Integer.class,
                "BINANCE",
                "BTCUSDT",
                "1m",
                java.sql.Timestamp.from(java.time.Instant.parse("2025-01-01T00:00:00Z")),
                java.sql.Timestamp.from(java.time.Instant.parse("2025-01-01T00:05:59Z"))
        );
        assertEquals(6, storedRows);
    }
}

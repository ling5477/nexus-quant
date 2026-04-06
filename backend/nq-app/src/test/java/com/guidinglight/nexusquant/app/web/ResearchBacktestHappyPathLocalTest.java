package com.guidinglight.nexusquant.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ResearchBacktestHappyPathLocalTest 验证 RC1-5-B 的最小 DB-backed happy path。
 * <p>
 * Why:
 * 现有单测已经分别覆盖 create / start / evaluate 的局部行为，RC1-5 还需要证明在真实 Spring 装配下，
 * marketdata ingest 后可以串起 `research -> backtest -> eval` 的最小业务链。
 */
@SpringBootTest(classes = NexusQuantApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@WithMockUser(username = "local-operator", roles = {"OPERATOR", "VIEWER"})
class ResearchBacktestHappyPathLocalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRunMinimalDbBackedResearchBacktestEvalHappyPath() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String sourceStrategyId = "str-db-" + suffix;
        String strategyCode = "code-" + suffix;
        String researchConfigId = null;
        String backtestConfigId = null;
        String backtestRunId = null;
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT account_id FROM accounts ORDER BY account_id LIMIT 1",
                Long.class
        );
        if (accountId == null) {
            throw new IllegalStateException("accounts table must contain at least one legacy account for strategy seed");
        }
        jdbcTemplate.update(
                """
                        INSERT INTO strategy_definitions (
                            strategy_id,
                            strategy_code,
                            strategy_name,
                            strategy_type,
                            exchange_code,
                            account_id,
                            trade_env,
                            enabled,
                            config_snapshot,
                            version,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
                        """,
                sourceStrategyId,
                strategyCode,
                "DB Backtest Strategy " + suffix,
                "BUY_AND_HOLD_FIXTURE",
                "BINANCE",
                accountId,
                "SIM",
                true,
                "{\"strategy\":\"fixture\"}",
                1,
                java.sql.Timestamp.from(Instant.parse("2026-04-06T00:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2026-04-06T00:00:00Z"))
        );

        try {
            mockMvc.perform(post("/api/marketdata/bars/ingestions/fixture")
                            .with(csrf())
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-ingest")
                            .contentType("application/json")
                            .content("""
                                    {"fixtureId":"BINANCE_BTCUSDT_1M_SAMPLE","exchangeCode":"BINANCE","symbol":"BTCUSDT","interval":"1m","startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T00:05:59Z"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-ingest"));

            ObjectNode datasetSpec = objectMapper.createObjectNode();
            datasetSpec.put("provider", "db");
            datasetSpec.put("datasetId", "BINANCE_BTCUSDT_1M_SAMPLE");
            datasetSpec.put("exchangeCode", "BINANCE");
            datasetSpec.put("symbol", "BTCUSDT");
            datasetSpec.put("interval", "1m");
            datasetSpec.put("resourcePath", "marketdata_bars");

            ObjectNode researchRequest = objectMapper.createObjectNode();
            researchRequest.put("sourceStrategyId", sourceStrategyId);
            researchRequest.put("name", "RC1-5 Research " + suffix);
            researchRequest.put("description", "rc1-5 happy path");
            researchRequest.put("parameterSchema", "{}");
            researchRequest.put("parameterDefaults", "{}");
            researchRequest.put("datasetSpec", objectMapper.writeValueAsString(datasetSpec));
            String researchResponseBody = mockMvc.perform(post("/api/research-configs")
                            .with(csrf())
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-research")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsBytes(researchRequest)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-research"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            researchConfigId = objectMapper.readTree(researchResponseBody).path("researchConfigId").asText();

            ObjectNode backtestRequest = objectMapper.createObjectNode();
            backtestRequest.put("researchConfigId", researchConfigId);
            backtestRequest.put("name", "RC1-5 Backtest " + suffix);
            backtestRequest.put("description", "rc1-5 happy path");
            backtestRequest.put("startTime", "2025-01-01T00:00:00Z");
            backtestRequest.put("endTime", "2025-01-01T00:05:59Z");
            backtestRequest.put("initialCapital", new BigDecimal("100000"));
            backtestRequest.put("executionSpec", "{\"mode\":\"bar\",\"feeRate\":\"0.001\",\"slippageBps\":\"10\",\"orderQuantity\":\"1\"}");
            backtestRequest.put("evaluationSpec", "{}");
            String backtestResponseBody = mockMvc.perform(post("/api/backtest-configs")
                            .with(csrf())
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-backtest-config")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsBytes(backtestRequest)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-backtest-config"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            backtestConfigId = objectMapper.readTree(backtestResponseBody).path("backtestConfigId").asText();

            ObjectNode runRequest = objectMapper.createObjectNode();
            runRequest.put("backtestConfigId", backtestConfigId);
            String runResponseBody = mockMvc.perform(post("/api/backtest-runs")
                            .with(csrf())
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-run-create")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsBytes(runRequest)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-run-create"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            backtestRunId = objectMapper.readTree(runResponseBody).path("backtestRunId").asText();

            String startResponseBody = mockMvc.perform(post("/api/backtest-runs/" + backtestRunId + "/start")
                            .with(csrf())
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-run-start")
                            .contentType("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-run-start"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode startResponse = objectMapper.readTree(startResponseBody);
            assertEquals("SUCCEEDED", startResponse.path("status").asText());

            JsonNode ordersResponse = objectMapper.readTree(mockMvc.perform(get("/api/backtest-runs/" + backtestRunId + "/sim-orders")
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-orders"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-orders"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            assertFalse(ordersResponse.isEmpty());

            JsonNode pnlResponse = objectMapper.readTree(mockMvc.perform(get("/api/backtest-runs/" + backtestRunId + "/pnl-snapshots")
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-pnl"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-pnl"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            assertFalse(pnlResponse.isEmpty());

            String evaluationResponseBody = mockMvc.perform(post("/api/backtest-runs/" + backtestRunId + "/evaluate")
                            .with(csrf())
                            .header(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-eval")
                            .contentType("application/json"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdContext.TRACE_ID_HEADER, "trc-rc15-eval"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode evaluationResponse = objectMapper.readTree(evaluationResponseBody);
            assertEquals("SUCCEEDED", evaluationResponse.path("evaluationStatus").asText());

            Integer reportCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM backtest_eval_reports WHERE backtest_run_id = ?",
                    Integer.class,
                    backtestRunId
            );
            assertEquals(1, reportCount);
        } finally {
            if (backtestRunId != null) {
                jdbcTemplate.update("DELETE FROM backtest_eval_reports WHERE backtest_run_id = ?", backtestRunId);
                jdbcTemplate.update("DELETE FROM sim_pnl_snapshots WHERE backtest_run_id = ?", backtestRunId);
                jdbcTemplate.update("DELETE FROM sim_positions WHERE backtest_run_id = ?", backtestRunId);
                jdbcTemplate.update("DELETE FROM sim_trades WHERE backtest_run_id = ?", backtestRunId);
                jdbcTemplate.update("DELETE FROM sim_orders WHERE backtest_run_id = ?", backtestRunId);
                jdbcTemplate.update("DELETE FROM backtest_runs WHERE backtest_run_id = ?", backtestRunId);
            }
            if (backtestConfigId != null) {
                jdbcTemplate.update("DELETE FROM backtest_configs WHERE backtest_config_id = ?", backtestConfigId);
            }
            if (researchConfigId != null) {
                jdbcTemplate.update("DELETE FROM research_configs WHERE research_config_id = ?", researchConfigId);
            }
            jdbcTemplate.update("DELETE FROM strategy_definitions WHERE strategy_id = ?", sourceStrategyId);
        }
    }
}

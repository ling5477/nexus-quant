package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.research.ExperimentValidationService;
import com.guidinglight.nexusquant.app.research.StrategyExperimentService;
import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 正式Servlet、隔离PG16和原业务服务证明对照只读；故障注入只作用于本例数据库。 */
@EnabledIfSystemProperty(named = "nq.tenant.pg.required", matches = "true")
class ResearchSimComparisonPostgresIntegrationTest {
    @Test @Timeout(240)
    void channelComparisonRestoresDurableIntentAfterUntradedStaleEntry() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner, true, "SPOT_CHANNEL_BREAKOUT_V1");
                String token = http.login("experiment-owner");
                var persisted = db.admin.queryForList("SELECT decision_id,target_exposure,status,input_snapshot_json->'strategyDecision'->>'action' action,"
                        + "input_snapshot_json->'strategyDecision'->>'previousState' previous_state FROM strategy_sim_decisions WHERE paper_run_id=? ORDER BY signal_open_time", binding.paperRunId());
                assertEquals(3, persisted.size());
                assertEquals("ENTER_LONG", persisted.getFirst().get("action"));
                assertEquals("NOT_TRADABLE", persisted.getFirst().get("status"));
                assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) persisted.getFirst().get("target_exposure")));
                assertEquals("LONG", persisted.getLast().get("previous_state"));
                assertEquals("HOLD", persisted.getLast().get("action"));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM orders", Integer.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM trades", Integer.class));
                Map<String, String> before = economicFacts(db);
                JsonNode result = http.ok("GET", binding.fullPath(), token, null);
                JsonNode latest = result.path("decisions").path("items").get(0);
                assertEquals("MATCH", latest.path("status").asText(), result.toPrettyString());
                assertEquals("HOLD", latest.path("expectedAction").asText());
                assertEquals("HOLD", latest.path("actualDecision").asText());
                assertEquals(before, economicFacts(db));
                String previousId = persisted.get(1).get("decision_id").toString();
                String checksum = db.admin.queryForObject("SELECT strategy_checksum FROM strategy_sim_decisions WHERE decision_id=?", String.class, previousId);
                try {
                    inject(db, "UPDATE strategy_sim_decisions SET strategy_checksum=? WHERE decision_id=?", "0".repeat(64), previousId);
                    JsonNode unknown = http.ok("GET", binding.fullPath(), token, null);
                    assertEquals("NOT_COMPARABLE", unknown.path("decisions").path("items").get(0).path("status").asText());
                    assertAttribution(unknown, "PREVIOUS_STRATEGY_STATE_UNAVAILABLE");
                } finally { inject(db, "UPDATE strategy_sim_decisions SET strategy_checksum=? WHERE decision_id=?", checksum, previousId); }
                assertEquals(before, economicFacts(db));
                Path evidence = Path.of("..", "..", "artifacts", "nq-v102-comparison-channel-facts.json");
                Files.createDirectories(evidence.getParent());
                Files.writeString(evidence, ResearchSimComparisonFixture.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                        Map.of("comparison", result, "durableDecisions", persisted, "readOnlyFingerprints", before)));
            }
        }
    }

    @Test @Timeout(240)
    void selectedVersionPublicationForwardFactsIsolationAndReadOnlyComparison() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            assertEquals(16, db.admin.queryForObject("SELECT current_setting('server_version_num')::int/10000", Integer.class));
            var owner = db.local("experiment-owner");
            var other = db.local("comparison-other");
            var tenant = db.demo("COMPARISON_OTHER", "comparison-demo");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner, false);
                String token = http.login("experiment-owner");
                String otherToken = http.login("comparison-other");
                String tenantToken = http.login("comparison-demo");
                JsonNode none = http.ok("GET", binding.path(), token, null);
                assertEquals("SIM_NOT_STARTED", none.path("observation").path("state").asText());
                assertTrue(none.path("paperRunId").isNull());
                assertTrue(StreamSupport.stream(none.path("performance").spliterator(), false)
                        .filter(row -> row.path("phase").asText().equals("FINAL_HOLDOUT"))
                        .allMatch(row -> row.path("status").asText().equals("UNAVAILABLE")));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM experiment_holdout_views WHERE validation_id=?", Integer.class, binding.validationId()));
                db.as(owner, () -> app.getBean(ExperimentValidationService.class).revealHoldout(binding.validationId(), UUID.randomUUID(), owner.userId()));
                JsonNode publicationOnly = http.ok("GET", binding.path() + "&publishId=" + binding.publishId(), token, null);
                assertEquals("SIM_NOT_STARTED", publicationOnly.path("observation").path("state").asText());

                Map<String, String> before = economicFacts(db);
                JsonNode result = http.ok("GET", binding.fullPath(), token, null);
                assertEquals(binding.strategyVersionId(), result.path("strategyVersionId").asText());
                assertEquals(binding.publishId(), result.path("publishId").asText());
                assertEquals(binding.paperRunId(), result.path("paperRunId").asText());
                assertEquals("NOT_COMPARABLE", result.path("status").asText());
                assertEquals("SIM_RUNNING", result.path("observation").path("state").asText());
                assertTrue(result.path("performance").isArray());
                for (String phase : List.of("TRAINING", "WALK_FORWARD_OOS", "FINAL_HOLDOUT", "CONTINUOUS_SIM_FORWARD"))
                    assertTrue(StreamSupport.stream(result.path("performance").spliterator(), false)
                            .anyMatch(row -> row.path("phase").asText().equals(phase)), phase);
                assertTrue(result.path("decisions").path("items").size() > 0, result.toPrettyString());
                assertTrue(StreamSupport.stream(result.path("decisions").path("items").spliterator(), false)
                        .anyMatch(row -> row.path("status").asText().equals("MATCH")), result.toPrettyString());
                assertTrue(db.admin.queryForObject("SELECT count(*) FROM trades", Integer.class) > 0);
                assertEquals(before, economicFacts(db));
                assertEquals(result, http.ok("GET", binding.fullPath(), token, null));
                assertEquals(before, economicFacts(db));

                assertEquals(404, http.send("GET", binding.fullPath(), otherToken, null).statusCode());
                assertEquals(403, http.send("GET", binding.fullPath(), tenantToken, null).statusCode());
                assertNotEquals(owner.userId(), other.userId());
                assertNotEquals(owner.tenantId(), tenant.tenantId());
                assertEquals(409, http.send("GET", binding.fullPath().replace(binding.selectedTrialId().toString(), UUID.randomUUID().toString()), token, null).statusCode());
                assertTrue(http.send("GET", binding.fullPath().replace(binding.strategyVersionId(), "sv-incorrect-version"), token, null).statusCode() >= 400);
                assertTrue(http.send("GET", binding.fullPath().replace(binding.publishId(), "pub-incorrect-publish"), token, null).statusCode() >= 400);
                assertEquals(404, http.send("GET", binding.fullPath().replace(binding.paperRunId(), "sim-incorrect-run"), token, null).statusCode());

                String original = db.admin.queryForObject("SELECT version_snapshot_json::text FROM backtest_publish_records WHERE publish_record_id=?", String.class, binding.publishId());
                var corrupt = (ObjectNode) ResearchSimComparisonFixture.JSON.readTree(original);
                corrupt.put("checksum", "0".repeat(64));
                db.admin.update("UPDATE backtest_publish_records SET version_snapshot_json=?::jsonb WHERE publish_record_id=?", corrupt.toString(), binding.publishId());
                assertTrue(http.send("GET", binding.fullPath(), token, null).statusCode() >= 400);
                db.admin.update("UPDATE backtest_publish_records SET version_snapshot_json=?::jsonb WHERE publish_record_id=?", original, binding.publishId());
                corrupt = (ObjectNode) ResearchSimComparisonFixture.JSON.readTree(original);
                corrupt.putObject("paramSnapshot").put("window", 200).put("investedExposure", "1");
                db.admin.update("UPDATE backtest_publish_records SET version_snapshot_json=?::jsonb WHERE publish_record_id=?", corrupt.toString(), binding.publishId());
                assertTrue(http.send("GET", binding.fullPath(), token, null).statusCode() >= 400);
                db.admin.update("UPDATE backtest_publish_records SET version_snapshot_json=?::jsonb WHERE publish_record_id=?", original, binding.publishId());

                var source = db.as(owner, () -> app.getBean(StrategyExperimentService.class).get(binding.sourceExperimentId(), owner.userId()));
                String unrelatedPublish = db.as(owner, () -> ResearchSimComparisonFixture.publish(app, source, binding.strategyVersionId()));
                assertEquals(409, http.send("GET", binding.path() + "&publishId=" + unrelatedPublish
                        + "&paperRunId=" + binding.paperRunId(), token, null).statusCode());
                decisionAttributionFaults(db, http, binding, token, result);

                var validation = db.as(owner, () -> app.getBean(ExperimentValidationService.class).get(binding.validationId(), owner.userId()));
                String windowRun = validation.results().stream().filter(r -> r.step() == 13).findFirst().orElseThrow().backtestRunId();
                String windowPublish = db.as(owner, () -> app.getBean(BacktestPublishService.class)
                        .publish(new BacktestPublishRequest(windowRun, "Window publication cannot seed Continuous SIM", binding.strategyVersionId())).publishRecordId());
                assertTrue(http.send("GET", binding.path() + "&publishId=" + windowPublish, token, null).statusCode() >= 400);
                var fundingBefore = economicFacts(db);
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> app.getBean(ContinuousSimRunService.class)
                        .start(windowPublish, new BigDecimal("10000"), "experiment-owner")));
                assertEquals(fundingBefore, economicFacts(db));

                db.as(owner, () -> app.getBean(ContinuousSimRunService.class).stop(binding.paperRunId()));
                JsonNode stopped = http.ok("GET", binding.fullPath(), token, null);
                assertEquals("SIM_STOPPED", stopped.path("observation").path("state").asText());
                db.migration(null).validate();
                Path evidence = Path.of("..", "..", "artifacts", "nq-v102-comparison-postgres-facts.json");
                Files.createDirectories(evidence.getParent());
                Files.writeString(evidence, ResearchSimComparisonFixture.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                        Map.of("comparison", result, "readOnlyFingerprints", before,
                                "postgresVersion", db.admin.queryForObject("SELECT version()", String.class),
                                "verifiedDeviationCategories", List.of("DECISION_ACTION_DIFFERENCE", "SIGNAL_TIME_DIFFERENCE",
                                        "DATA_SOURCE_OR_CONTENT_DIFFERENCE", "MARKET_EXECUTION_EVENT_DIFFERENCE",
                                        "RISK_REJECTION_OR_EXECUTION_FAILURE", "FEE_OR_SLIPPAGE_ASSUMPTION_DIFFERENCE", "INSUFFICIENT_FACTS"),
                                "outsideCoverage", "NOT_COMPARABLE_FEE_AND_TRADE_COUNT_UNAVAILABLE")));
            }
        }
    }

    private static void decisionAttributionFaults(TenantIsolationTestDatabase db, TenantIsolationHttpIntegrationTest.Http http,
            ResearchSimComparisonFixture.Binding binding, String token, JsonNode result) throws Exception {
        JsonNode latest = result.path("decisions").path("items").get(0);
        String decision = latest.path("decisionId").asText();
        String snapshot = db.admin.queryForObject("SELECT input_snapshot_json::text FROM strategy_sim_decisions WHERE decision_id=?", String.class, decision);
        var changed = (ObjectNode) ResearchSimComparisonFixture.JSON.readTree(snapshot);
        ((ObjectNode) changed.path("strategyDecision")).put("action", "HOLD");
        try {
            inject(db, "UPDATE strategy_sim_decisions SET input_snapshot_json=?::jsonb WHERE decision_id=?", changed.toString(), decision);
            assertAttribution(http.ok("GET", binding.fullPath(), token, null), "DECISION_ACTION_DIFFERENCE");
        } finally { inject(db, "UPDATE strategy_sim_decisions SET input_snapshot_json=?::jsonb WHERE decision_id=?", snapshot, decision); }
        try {
            inject(db, "UPDATE strategy_sim_decisions SET signal_available_at=signal_available_at+interval '1 second' WHERE decision_id=?", decision);
            assertAttribution(http.ok("GET", binding.fullPath(), token, null), "SIGNAL_TIME_DIFFERENCE");
        } finally { inject(db, "UPDATE strategy_sim_decisions SET signal_available_at=signal_available_at-interval '1 second' WHERE decision_id=?", decision); }
        String input = db.admin.queryForObject("SELECT input_sha256 FROM strategy_sim_decisions WHERE decision_id=?", String.class, decision);
        try {
            inject(db, "UPDATE strategy_sim_decisions SET input_sha256=? WHERE decision_id=?", "0".repeat(64), decision);
            JsonNode rejected = http.ok("GET", binding.fullPath(), token, null);
            assertEquals("NOT_COMPARABLE", rejected.path("decisions").path("items").get(0).path("status").asText());
            assertAttribution(rejected, "DATA_SOURCE_OR_CONTENT_DIFFERENCE");
        } finally { inject(db, "UPDATE strategy_sim_decisions SET input_sha256=? WHERE decision_id=?", input, decision); }
        try {
            inject(db, "UPDATE strategy_sim_decisions SET execution_open_time=execution_open_time+interval '1 second' WHERE decision_id=?", decision);
            assertAttribution(http.ok("GET", binding.fullPath(), token, null), "MARKET_EXECUTION_EVENT_DIFFERENCE");
        } finally { inject(db, "UPDATE strategy_sim_decisions SET execution_open_time=execution_open_time-interval '1 second' WHERE decision_id=?", decision); }
        try {
            inject(db, "UPDATE strategy_sim_decisions SET status='RISK_REJECTED' WHERE decision_id=?", decision);
            assertAttribution(http.ok("GET", binding.fullPath(), token, null), "RISK_REJECTION_OR_EXECUTION_FAILURE");
        } finally { inject(db, "UPDATE strategy_sim_decisions SET status=? WHERE decision_id=?", latest.path("decisionStatus").asText(), decision); }
        var missingPrice = (ObjectNode) ResearchSimComparisonFixture.JSON.readTree(snapshot);
        var savedBars = missingPrice.path("signalBars");
        ((ObjectNode) savedBars.get(savedBars.size() - 1)).putNull("closePrice");
        ((ObjectNode) missingPrice.path("executionQuote")).putNull("price");
        try {
            inject(db, "UPDATE strategy_sim_decisions SET input_snapshot_json=?::jsonb WHERE decision_id=?", missingPrice.toString(), decision);
            JsonNode missing = http.ok("GET", binding.fullPath(), token, null);
            assertTrue(StreamSupport.stream(missing.path("performance").spliterator(), false)
                    .filter(row -> row.path("phase").asText().equals("CONTINUOUS_SIM_FORWARD"))
                    .allMatch(row -> row.path("status").asText().equals("UNAVAILABLE")
                            && row.path("netPnl").isNull() && row.path("totalReturn").isNull()), missing.toPrettyString());
        } finally { inject(db, "UPDATE strategy_sim_decisions SET input_snapshot_json=?::jsonb WHERE decision_id=?", snapshot, decision); }
        var fees = db.admin.queryForList("SELECT trade_id,fee FROM trades ORDER BY trade_id");
        try {
            inject(db, "UPDATE trades SET fee=fee+1");
            assertAttribution(http.ok("GET", binding.fullPath(), token, null), "FEE_OR_SLIPPAGE_ASSUMPTION_DIFFERENCE");
        } finally { for (var fee : fees) inject(db, "UPDATE trades SET fee=? WHERE trade_id=?", fee.get("fee"), fee.get("trade_id")); }
        try {
            inject(db, "UPDATE trades SET fee=NULL");
            JsonNode missing = http.ok("GET", binding.fullPath(), token, null);
            assertTrue(StreamSupport.stream(missing.path("performance").spliterator(), false)
                    .filter(row -> row.path("phase").asText().equals("CONTINUOUS_SIM_FORWARD"))
                    .allMatch(row -> row.path("feeStatus").asText().equals("UNAVAILABLE") && row.path("fee").isNull()));
            assertAttribution(missing, "INSUFFICIENT_FACTS");
        } finally { for (var fee : fees) inject(db, "UPDATE trades SET fee=? WHERE trade_id=?", fee.get("fee"), fee.get("trade_id")); }
        var executionTimes = db.admin.queryForList("SELECT trade_id,ts FROM trades ORDER BY trade_id");
        try {
            inject(db, "UPDATE trades SET ts=(SELECT max(execution_open_time)+interval '1 minute' FROM strategy_sim_decisions WHERE paper_run_id=?)", binding.paperRunId());
            JsonNode outside = http.ok("GET", binding.fullPath(), token, null);
            assertTrue(StreamSupport.stream(outside.path("performance").spliterator(), false)
                    .filter(row -> row.path("phase").asText().equals("CONTINUOUS_SIM_FORWARD"))
                    .allMatch(row -> row.path("status").asText().equals("NOT_COMPARABLE")
                            && row.path("reason").asText().equals("EXECUTIONS_OUTSIDE_VALUATION_COVERAGE")
                            && row.path("fee").isNull() && row.path("tradeCount").isNull()), outside.toPrettyString());
            assertEquals("OBSERVATION_INCOMPLETE", outside.path("observation").path("status").asText());
        } finally { for (var executionTime : executionTimes) inject(db, "UPDATE trades SET ts=? WHERE trade_id=?", executionTime.get("ts"), executionTime.get("trade_id")); }
    }

    private static void assertAttribution(JsonNode result, String attribution) {
        assertTrue(StreamSupport.stream(result.path("decisions").path("items").get(0).path("attribution").spliterator(), false)
                .anyMatch(value -> value.asText().equals(attribution)), result.toPrettyString());
    }

    private static void inject(TenantIsolationTestDatabase db, String sql, Object... arguments) {
        // 只为隔离数据库制造原系统不允许写出的坏快照；事务结束自动恢复trigger语义，正式服务永不使用该权限。
        new TransactionTemplate(new DataSourceTransactionManager(db.admin.getDataSource())).executeWithoutResult(status -> {
            db.admin.execute("SET LOCAL session_replication_role=replica");
            db.admin.update(sql, arguments);
        });
    }

    static Map<String, String> economicFacts(TenantIsolationTestDatabase db) {
        var values = new LinkedHashMap<String, String>();
        for (String table : List.of("orders", "trades", "ledger_entries", "strategy_runs", "strategy_run_dispatch_work",
                "strategy_sim_decisions", "continuous_sim_runs", "continuous_sim_bars", "paper_trading_runs",
                "backtest_runs", "backtest_eval_reports", "backtest_publish_records", "experiment_validations", "experiment_validation_steps", "experiment_holdout_views"))
            values.put(table, db.admin.queryForObject("SELECT md5(coalesce(string_agg(row_data::text, '' ORDER BY row_data::text),'')) FROM (SELECT to_jsonb(t) row_data FROM " + table + " t) records", String.class));
        return values;
    }
}

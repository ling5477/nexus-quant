package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.research.StrategyObservationService;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实PG16、正式研究/发布/SIM及撮合事实；合成时间推进和故障注入均只作用于任务随机库。 */
@EnabledIfSystemProperty(named = "nq.tenant.pg.required", matches = "true")
class ForwardSimEvaluationPostgresIntegrationTest {
    private static final String OBS = ForwardSimEvaluationFixture.ROOT;
    private static final MathContext MATH = MathContext.DECIMAL128;

    @Test @Timeout(360)
    void nonemptyUpgradeFormalEconomicOracleStableReadsAndAppendOnlyHumanReview() throws Exception {
        try (var db = TenantIsolationTestDatabase.create("51")) {
            var owner = db.local("experiment-owner"); var other = db.local("forward-other");
            var tenant = db.demo("FORWARD_OTHER", "forward-demo");
            int oldUsers = db.admin.queryForObject("SELECT count(*) FROM users", Integer.class);
            db.migrate(null); db.runtime(); db.migration(null).validate();
            assertEquals(oldUsers, db.admin.queryForObject("SELECT count(*) FROM users", Integer.class));
            assertEquals("52", db.admin.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class));
            assertEquals(16, db.admin.queryForObject("SELECT current_setting('server_version_num')::int/10000", Integer.class));
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var scenario = ForwardSimEvaluationFixture.seed(app, db, owner, "SPOT_SMA_TARGET_V1");
                String token = http.login("experiment-owner"), otherToken = http.login("forward-other"), demoToken = http.login("forward-demo");
                long account = ForwardSimEvaluationFixture.account(db, scenario.binding().paperRunId());
                assertTrue(db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=?", Long.class, account) > 0);
                var before = ForwardSimEvaluationFixture.allFacts(db);
                JsonNode evaluated = http.ok("GET", scenario.path(), token, null);
                assertIdentity(scenario, account, evaluated);
                assertEquals("AVAILABLE", evaluated.path("status").asText(), evaluated.toPrettyString());
                assertEquals(24, evaluated.path("samples").path("expected").asInt());
                assertEquals(24, evaluated.path("samples").path("observed").asInt());
                assertEquals(0, evaluated.path("samples").path("missing").asInt());
                assertEquals(evaluated, http.ok("GET", scenario.path(), token, null));
                assertEquals(before, ForwardSimEvaluationFixture.allFacts(db));
                Instant beforeRun = db.admin.queryForObject("SELECT created_at FROM paper_trading_runs WHERE paper_run_id=?",
                        Timestamp.class, scenario.binding().paperRunId()).toInstant().minusSeconds(1);
                var unavailableBeforeRun = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        scenario.from(), scenario.to(), beforeRun), token, null);
                assertEquals("UNAVAILABLE", unavailableBeforeRun.path("status").asText());
                assertEquals("RUN_NOT_AVAILABLE_AS_OF", unavailableBeforeRun.path("reason").asText());
                assertTrue(unavailableBeforeRun.path("costAssumptions").path("values").isEmpty(),
                        "a cutoff before the run exists must not disclose its future frozen cost assumptions");
                assertTrue(unavailableBeforeRun.path("performance").path("pnl").isNull());
                assertEquals(before, ForwardSimEvaluationFixture.allFacts(db));
                economicOracle(db, scenario, evaluated);
                assertEquals(404, http.send("GET", scenario.path(), otherToken, null).statusCode());
                assertTrue(List.of(403, 404).contains(http.send("GET", scenario.path(), demoToken, null).statusCode()));
                assertEquals(401, http.send("GET", scenario.path(), null, null).statusCode());
                assertEquals(404, http.send("GET", scenario.path().replace(scenario.binding().paperRunId(), "ptr-nonexistent"), token, null).statusCode());
                assertEquals(400, http.send("GET", ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        scenario.from(), scenario.to(), scenario.asOf().plusSeconds(1)), token, null).statusCode());
                replayRemainsHistoricalWithoutForwardEconomicDisclosure(app, db, http, scenario, token);

                Map<String, String> economics = ResearchSimComparisonPostgresIntegrationTest.economicFacts(db);
                var request = ForwardSimEvaluationFixture.createBody(db, scenario, "RETROSPECTIVE", scenario.from(), scenario.to());
                UUID operation = UUID.randomUUID(); JsonNode created;
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var a = pool.submit(() -> http.ok("POST", OBS + "/operations/" + operation, token, request));
                    var b = pool.submit(() -> http.ok("POST", OBS + "/operations/" + operation, token, request));
                    created = a.get(40, TimeUnit.SECONDS); assertEquals(created, b.get(40, TimeUnit.SECONDS));
                }
                UUID id = UUID.fromString(created.path("observation").path("observationId").asText());
                assertEquals(0, created.path("observation").path("revision").asLong());
                assertEquals("RETROSPECTIVE", created.path("observation").path("kind").asText());
                assertEquals(id.toString(), http.ok("GET", OBS + "/operations/" + operation, token, null).path("objectId").asText());
                var conflicted = new LinkedHashMap<>(request); conflicted.put("observationTo", scenario.to().plusSeconds(3600).toString());
                assertEquals(409, http.send("POST", OBS + "/operations/" + operation, token, conflicted).statusCode());
                rejectIdentityBindings(db, http, scenario, token, request);
                assertEquals(404, http.send("GET", OBS + "/" + id, otherToken, null).statusCode());
                assertTrue(List.of(403,404).contains(http.send("GET", OBS + "/" + id, demoToken, null).statusCode()));
                assertEquals(0, db.as(other, () -> db.jdbc.queryForObject("SELECT count(*) FROM strategy_observations WHERE observation_id=?", Integer.class, id)));
                assertEquals(0, db.as(tenant, () -> db.jdbc.queryForObject("SELECT count(*) FROM strategy_observations WHERE observation_id=?", Integer.class, id)));
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> db.jdbc.update(
                        "UPDATE strategy_observations SET observation_to=observation_to+interval '1 hour' WHERE observation_id=?", id)));

                var continueRequest = review(0, "CONTINUE_OBSERVATION", scenario.asOf(), evaluated.path("checksum").asText());
                UUID firstReviewOperation = UUID.randomUUID();
                var firstReview = http.ok("POST", OBS + "/" + id + "/reviews/operations/" + firstReviewOperation, token, continueRequest);
                assertTrue(sameProjection(evaluated, firstReview.path("evaluation")), "JSONB snapshot must preserve every semantic value");
                JsonNode preciseEvaluation = ForwardSimEvaluationFixture.JSON.reader()
                        .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .readTree(http.send("GET", scenario.path(), token, null).body());
                var preciseSnapshot = db.as(owner, () -> app.getBean(StrategyObservationService.class).readReview(id,
                        UUID.fromString(firstReview.path("review").path("reviewId").asText()), owner.userId()));
                assertTrue(sameProjection(preciseEvaluation, preciseSnapshot.evaluation()),
                        "Persisted review must retain full decimal precision as well as its checksum");
                assertEquals(1, firstReview.path("review").path("revision").asLong());
                assertEquals(firstReview, http.ok("POST", OBS + "/" + id + "/reviews/operations/" + firstReviewOperation, token, continueRequest));
                assertEquals(409, http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, continueRequest).statusCode());
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var concurrent = review(1, "CONTINUE_OBSERVATION", scenario.asOf(), evaluated.path("checksum").asText());
                    var a = pool.submit(() -> http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, concurrent));
                    var b = pool.submit(() -> http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, concurrent));
                    var statuses = List.of(a.get(40, TimeUnit.SECONDS).statusCode(), b.get(40, TimeUnit.SECONDS).statusCode());
                    assertEquals(1, statuses.stream().filter(status -> status == 200).count());
                    assertEquals(1, statuses.stream().filter(status -> status == 409).count());
                }
                var staleChecksum = review(2, "STOP_OBSERVATION", scenario.asOf(), "0".repeat(64));
                assertEquals(409, http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, staleChecksum).statusCode());
                var stop = review(2, "STOP_OBSERVATION", scenario.asOf(), evaluated.path("checksum").asText());
                JsonNode stopped = http.ok("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, stop);
                assertEquals("CLOSED", http.ok("GET", OBS + "/" + id, token, null).path("observation").path("status").asText());
                assertEquals(economics, ResearchSimComparisonPostgresIntegrationTest.economicFacts(db));
                assertEquals("RUNNING", db.admin.queryForObject("SELECT status FROM continuous_sim_runs WHERE paper_run_id=?", String.class, scenario.binding().paperRunId()));
                assertEquals(3, db.admin.queryForObject("SELECT count(*) FROM strategy_observation_reviews WHERE observation_id=?", Integer.class, id));
                UUID historicalReview = UUID.fromString(stopped.path("review").path("reviewId").asText());
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> db.jdbc.update(
                        "UPDATE strategy_observation_reviews SET reason='rewritten' WHERE review_id=?", historicalReview)));
                var metadataBefore = ForwardSimEvaluationFixture.allFacts(db);
                assertEquals(stopped, http.ok("GET", OBS + "/" + id + "/reviews/" + historicalReview, token, null));
                assertEquals(metadataBefore, ForwardSimEvaluationFixture.allFacts(db));
                restart(db, owner, id, historicalReview, evaluated.path("checksum").asText(), scenario.asOf());

                // 同一asOf之后新增的真实服务观察和经济事实不得进入旧评价；当前游标前进也不能改写旧结论。
                ForwardSimEvaluationFixture.advance(app, db, scenario, scenario.to().plus(Duration.ofDays(1)));
                assertEquals(evaluated, http.ok("GET", scenario.path(), token, null));
                assertEquals(stopped, http.ok("GET", OBS + "/" + id + "/reviews/" + historicalReview, token, null));
                Instant futureFrom = scenario.asOf().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
                var forward = ForwardSimEvaluationFixture.createBody(db, scenario, "FORWARD", futureFrom, futureFrom.plus(Duration.ofDays(1)));
                JsonNode planned = http.ok("POST", OBS + "/operations/" + UUID.randomUUID(), token, forward);
                assertEquals("FORWARD", planned.path("observation").path("kind").asText());
                var pending = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        futureFrom, futureFrom.plus(Duration.ofDays(1)), scenario.asOf()), token, null);
                assertEquals("INSUFFICIENT_SAMPLES", pending.path("status").asText());
                assertEquals(0, pending.path("samples").path("expected").asInt());
                var returned = http.ok("POST", OBS + "/" + planned.path("observation").path("observationId").asText()
                        + "/reviews/operations/" + UUID.randomUUID(), token,
                        review(0, "RETURN_TO_RESEARCH", scenario.asOf(), pending.path("checksum").asText()));
                assertEquals("RETURN_TO_RESEARCH", returned.path("review").path("decision").asText());
                assertEquals("RUNNING", db.admin.queryForObject("SELECT status FROM continuous_sim_runs WHERE paper_run_id=?", String.class, scenario.binding().paperRunId()));
                ForwardSimEvaluationFixture.evidence("postgres-normal-and-review.json", Map.of(
                        "source", ForwardSimEvaluationFixture.SOURCE, "evaluation", evaluated, "review", stopped,
                        "readOnlyFingerprints", before, "preservedV51Users", oldUsers,
                        "futureRegistration", planned, "futureEvaluation", pending));
            }
        }
    }

    @Test @Timeout(300)
    void validHoldSamplesAreNotMissingAndGapStallAreSeparateFromProfitability() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var scenario = ForwardSimEvaluationFixture.seed(app, db, owner, "SPOT_CHANNEL_BREAKOUT_V1");
                String token = http.login("experiment-owner");
                long account = ForwardSimEvaluationFixture.account(db, scenario.binding().paperRunId());
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=?", Integer.class, account));
                JsonNode held = http.ok("GET", scenario.path(), token, null);
                assertEquals("AVAILABLE", held.path("status").asText(), held.toPrettyString());
                assertEquals(24, held.path("samples").path("observed").asInt());
                assertEquals("AVAILABLE", held.path("metrics").path("tradeCountStatus").asText());
                assertEquals(0, held.path("metrics").path("tradeCount").asLong());
                assertEquals(0, decimal(held.path("performance").path("pnl")).signum());
                var noSamples = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        scenario.from(), scenario.from().plusSeconds(1800), scenario.asOf()), token, null);
                assertEquals("INSUFFICIENT_SAMPLES", noSamples.path("status").asText());
                var partial = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        scenario.from(), scenario.to(), scenario.from().plusSeconds(6 * 3600)), token, null);
                assertEquals("INSUFFICIENT_SAMPLES", partial.path("status").asText());
                assertEquals(6, partial.path("samples").path("expected").asLong());
                Instant lost = scenario.from().plusSeconds(10 * 3600);
                String saved = db.admin.queryForObject("SELECT to_jsonb(b)::text FROM continuous_sim_bars b WHERE paper_run_id=? AND open_time=?",
                        String.class, scenario.binding().paperRunId(), Timestamp.from(lost));
                JsonNode gap;
                try {
                    inject(db, "DELETE FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?", scenario.binding().paperRunId(), Timestamp.from(lost));
                    gap = http.ok("GET", scenario.path(), token, null);
                    assertEquals("UNAVAILABLE", gap.path("status").asText());
                    assertTrue(gap.path("samples").path("missing").asLong() + gap.path("samples").path("unknown").asLong() > 0);
                    assertTrue(gap.path("samples").path("gapRanges").size() > 0);
                } finally { inject(db, "INSERT INTO continuous_sim_bars SELECT * FROM jsonb_populate_record(NULL::continuous_sim_bars,?::jsonb)", saved); }
                assertEquals(held, http.ok("GET", scenario.path(), token, null));
                Instant failedAt = scenario.asOf().plusSeconds(5);
                var failure = new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class), app.getBean(StrategySimRunService.class),
                        app.getBean(PaperTradingRunService.class), (first, maximum) -> { throw new IllegalStateException("PUBLIC_MARKETDATA_UNAVAILABLE"); },
                        app.getBean(JdbcTemplate.class), Clock.fixed(failedAt, ZoneOffset.UTC));
                assertEquals("STALLED", db.as(owner, () -> failure.pollOnce(scenario.binding().paperRunId())).status());
                ForwardSimEvaluationFixture.clock(app, failedAt);
                var afterFailure = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        scenario.from(), scenario.to().plusSeconds(3600), failedAt), token, null);
                assertTrue(!"AVAILABLE".equals(afterFailure.path("status").asText()));
                assertEquals(held, http.ok("GET", scenario.path(), token, null), "later cursor failure cannot rewrite a fixed earlier asOf");
                ForwardSimEvaluationFixture.evidence("postgres-hold-gap-and-stall.json", Map.of("source", ForwardSimEvaluationFixture.SOURCE,
                        "held", held, "insufficient", noSamples, "partial", partial, "gap", gap, "stalled", afterFailure));
            }
        }
    }

    @Test @Timeout(300)
    void missingValuationFeeAndCoverageOutsideTradesRemainUnavailable() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var scenario = ForwardSimEvaluationFixture.seed(app, db, owner, "SPOT_SMA_TARGET_V1");
                String token = http.login("experiment-owner"); long account = ForwardSimEvaluationFixture.account(db, scenario.binding().paperRunId());
                JsonNode baseline = http.ok("GET", scenario.path(), token, null);
                var trade = db.admin.queryForMap("SELECT trade_id,fee,fee_currency,ts FROM trades WHERE account_id=? ORDER BY ts LIMIT 1", account);
                String tradeId = trade.get("trade_id").toString();
                var cases = new LinkedHashMap<String, JsonNode>();
                try {
                    inject(db, "UPDATE trades SET fee=NULL WHERE trade_id=?", tradeId);
                    var missing = http.ok("GET", scenario.path(), token, null); cases.put("missingFee", missing);
                    assertFalse("AVAILABLE".equals(missing.path("metrics").path("feeStatus").asText()));
                    assertTrue(missing.path("performance").path("pnl").isNull());
                } finally { inject(db, "UPDATE trades SET fee=? WHERE trade_id=?", trade.get("fee"), tradeId); }
                try {
                    inject(db, "UPDATE trades SET fee_currency='UNKNOWN_ASSET' WHERE trade_id=?", tradeId);
                    var mixed = http.ok("GET", scenario.path(), token, null); cases.put("unsupportedFeeCurrency", mixed);
                    assertFalse("AVAILABLE".equals(mixed.path("metrics").path("feeStatus").asText()));
                    assertTrue(mixed.path("metrics").path("fees").isEmpty(), "unsupported currencies cannot be silently added in USDT");
                } finally { inject(db, "UPDATE trades SET fee_currency=? WHERE trade_id=?", trade.get("fee_currency"), tradeId); }
                var row = db.admin.queryForMap("SELECT decision_id,input_snapshot_json::text AS snapshot FROM strategy_sim_decisions"
                        + " WHERE paper_run_id=? AND signal_available_at>=? AND signal_available_at<? ORDER BY signal_available_at LIMIT 1",
                        scenario.binding().paperRunId(), Timestamp.from(scenario.from()), Timestamp.from(scenario.to()));
                String original = row.get("snapshot").toString(); ObjectNode missingPrice = (ObjectNode) ForwardSimEvaluationFixture.JSON.readTree(original);
                var signal = missingPrice.path("signalBars"); ((ObjectNode) signal.get(signal.size() - 1)).putNull("closePrice");
                try {
                    inject(db, "UPDATE strategy_sim_decisions SET input_snapshot_json=?::jsonb WHERE decision_id=?", missingPrice.toString(), row.get("decision_id"));
                    var invalid = http.ok("GET", scenario.path(), token, null); cases.put("missingValuation", invalid);
                    assertEquals("UNAVAILABLE", invalid.path("status").asText());
                    assertTrue(invalid.path("performance").path("pnl").isNull());
                } finally { inject(db, "UPDATE strategy_sim_decisions SET input_snapshot_json=?::jsonb WHERE decision_id=?", original, row.get("decision_id")); }
                try {
                    // 末端不对齐小时且无价格覆盖，成交仍在用户所选半开区间内，不能按dueTo静默排除。
                    inject(db, "UPDATE trades SET ts=? WHERE trade_id=?", Timestamp.from(scenario.to().minusSeconds(30)), tradeId);
                    String outsidePath = ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                            scenario.from(), scenario.to().minusSeconds(5), scenario.asOf());
                    var outside = http.ok("GET", outsidePath, token, null); cases.put("outsideCoverage", outside);
                    assertEquals("NOT_COMPARABLE", outside.path("status").asText(), outside.toPrettyString());
                    assertEquals("EXECUTIONS_OUTSIDE_VALUATION_COVERAGE", outside.path("reason").asText());
                    assertTrue(outside.path("metrics").path("tradeCount").isNull());
                    assertFalse("AVAILABLE".equals(outside.path("metrics").path("feeStatus").asText()));
                } finally { inject(db, "UPDATE trades SET ts=? WHERE trade_id=?", trade.get("ts"), tradeId); }
                var ledgerTimes = db.admin.queryForList("SELECT entry_id,ts FROM ledger_entries WHERE ref_type='TRADE' AND ref_id=?", tradeId);
                try {
                    inject(db, "UPDATE trades SET ts=? WHERE trade_id=?", Timestamp.from(scenario.from()), tradeId);
                    inject(db, "UPDATE ledger_entries SET ts=? WHERE ref_type='TRADE' AND ref_id=?", Timestamp.from(scenario.from()), tradeId);
                    var boundary = http.ok("GET", scenario.path(), token, null); cases.put("fillExactlyAtFrom", boundary);
                    assertEquals("AVAILABLE", boundary.path("status").asText(), boundary.toPrettyString());
                    assertEquals(0, decimal(boundary.path("performance").path("initialEquity")).compareTo(new BigDecimal("10000")));
                    economicOracle(db, scenario, boundary);
                } finally {
                    inject(db, "UPDATE trades SET ts=? WHERE trade_id=?", trade.get("ts"), tradeId);
                    for (var time : ledgerTimes) inject(db, "UPDATE ledger_entries SET ts=? WHERE entry_id=?", time.get("ts"), time.get("entry_id"));
                }
                riskAndExecutionFaults(app, db, http, scenario, token, account, cases);
                assertEquals(baseline, http.ok("GET", scenario.path(), token, null));
                ForwardSimEvaluationFixture.evidence("postgres-valuation-fee-outside-coverage.json", Map.of(
                        "source", ForwardSimEvaluationFixture.SOURCE, "baseline", baseline, "boundedFaultInjection", cases));
            }
        }
    }

    private static void riskAndExecutionFaults(org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext app,
            TenantIsolationTestDatabase db, TenantIsolationHttpIntegrationTest.Http http, ForwardSimEvaluationFixture.Scenario scenario,
            String token, long account, Map<String, JsonNode> cases) throws Exception {
        var order = db.admin.queryForMap("SELECT order_id,status,reason,created_at,updated_at FROM orders WHERE account_id=? ORDER BY created_at LIMIT 1", account);
        String trace = "fwd-risk-" + UUID.randomUUID();
        db.as(scenario.owner(), () -> {
            app.getBean(RiskEventRepository.class).append("ORDER", order.get("order_id").toString(), RiskDecision.REJECT,
                    "SYNTHETIC_REVIEW_RISK_EVENT", RiskSeverity.HIGH, trace);
            return null;
        });
        var risk = db.admin.queryForMap("SELECT risk_event_id,created_at FROM risk_events WHERE trace_id=?", trace);
        try {
            // 原风险端口建立事件；仅本例把其时间定位在压缩观察期，明确属于受控异常解释负例。
            inject(db, "UPDATE risk_events SET created_at=? WHERE risk_event_id=?", Timestamp.from(scenario.from().plusSeconds(7200)), risk.get("risk_event_id"));
            var riskView = http.ok("GET", scenario.path(), token, null); cases.put("riskEventObserved", riskView);
            assertTrue(riskView.path("metrics").path("riskEventCount").asLong() > 0);
            assertFinding(riskView, "RISK_EVENT_OBSERVED");
            inject(db, "UPDATE orders SET status='RISK_REJECTED',reason='SYNTHETIC_STATE_FAULT',created_at=?,updated_at=? WHERE order_id=?",
                    Timestamp.from(scenario.from().plusSeconds(1800)), Timestamp.from(scenario.from().plusSeconds(1801)), order.get("order_id"));
            var anomaly = http.ok("GET", scenario.path(), token, null); cases.put("executionAnomaly", anomaly);
            assertTrue(anomaly.path("metrics").path("executionAnomalyCount").asLong() > 0);
            assertFinding(anomaly, "EXECUTION_ANOMALY");
        } finally {
            inject(db, "UPDATE orders SET status=?,reason=?,created_at=?,updated_at=? WHERE order_id=?",
                    order.get("status"), order.get("reason"), order.get("created_at"), order.get("updated_at"), order.get("order_id"));
            inject(db, "UPDATE risk_events SET created_at=? WHERE risk_event_id=?", risk.get("created_at"), risk.get("risk_event_id"));
        }
    }

    private static void assertFinding(JsonNode evaluation, String code) {
        assertTrue(java.util.stream.StreamSupport.stream(evaluation.path("findings").spliterator(), false)
                .anyMatch(finding -> code.equals(finding.path("code").asText())), evaluation.toPrettyString());
    }

    private static void assertIdentity(ForwardSimEvaluationFixture.Scenario scenario, long account, JsonNode evaluation) {
        assertEquals(scenario.binding().paperRunId(), evaluation.path("paperRunId").asText());
        assertEquals(account, evaluation.path("canonicalAccountId").asLong());
        assertEquals(scenario.binding().strategyVersionId(), evaluation.path("strategyVersionId").asText());
        assertEquals(scenario.binding().publishId(), evaluation.path("publishId").asText());
        assertEquals("ACTUAL_EVENT_TIME", evaluation.path("timeBasis").asText());
        assertEquals(scenario.from().toString(), evaluation.path("observationFrom").asText());
        assertEquals(scenario.to().toString(), evaluation.path("observationTo").asText());
        assertEquals(scenario.asOf().toString(), evaluation.path("observedAt").asText());
        assertTrue(evaluation.path("checksum").asText().matches("[0-9a-f]{64}"));
    }

    @Test @Timeout(300)
    void boundedPostgresPreparedProjectionCrossesLegacy1000AndRejects10001WithoutTruncation() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var scenario = ForwardSimEvaluationFixture.seed(app, db, owner, "SPOT_CHANNEL_BREAKOUT_V1");
                String token = http.login("experiment-owner");
                long account = ForwardSimEvaluationFixture.account(db, scenario.binding().paperRunId());
                long economicRows = db.admin.queryForObject("SELECT (SELECT count(*) FROM orders)+(SELECT count(*) FROM trades)+(SELECT count(*) FROM ledger_entries)", Long.class);
                Instant longFrom = scenario.to().plusSeconds(3600);
                // 容量专用保存投影：父研究/发布/运行由正式服务建立，不伪造订单、成交、账务或真实长时运行。
                // 额外闭合前一根bar只提供from时点已有的估值锚，不计入目标1002根观察样本。
                preparedProjection(db, scenario, longFrom.minusSeconds(3600), 1, 3600, 100000);
                preparedProjection(db, scenario, longFrom, 1002, 3600, 0);
                Instant longTo = longFrom.plusSeconds(1002L * 3600), asOf = longTo.plusSeconds(5);
                ForwardSimEvaluationFixture.clock(app, asOf);
                String path = ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(), longFrom, longTo, asOf);
                JsonNode moreThan1000 = http.ok("GET", path, token, null);
                assertEquals("AVAILABLE", moreThan1000.path("status").asText(), moreThan1000.toPrettyString());
                assertEquals(1002, moreThan1000.path("samples").path("observed").asLong());
                assertTrue(moreThan1000.path("performance").path("samples").size() <= 1000);
                assertEquals(0, decimal(moreThan1000.path("performance").path("pnl")).signum());
                JsonNode legacy = http.ok("GET", "/api/paper-trading/strategy-sim/analysis/runs/" + scenario.binding().paperRunId()
                        + "/performance?from=" + longFrom + "&to=" + longTo, token, null);
                assertEquals("HISTORY_WINDOW_LIMITED", legacy.path("status").asText(), "new observation cannot relax old performance entry limits");

                Instant defensiveFrom = longTo.plus(Duration.ofDays(1));
                // 90日合法小时输入不可能有10001个信号；以下分钟错位输入只证明防御预算，不宣称可交易样本。
                preparedProjection(db, scenario, defensiveFrom, 10000, 60, 2000);
                Instant defensiveTo = defensiveFrom.plusSeconds(10001L * 60 + 7200);
                Instant defensiveAsOf = defensiveTo.plusSeconds(5);
                ForwardSimEvaluationFixture.clock(app, defensiveAsOf);
                String defensivePath = ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(), defensiveFrom, defensiveTo, defensiveAsOf);
                JsonNode exactly10000 = http.ok("GET", defensivePath, token, null);
                assertFalse(exactly10000.path("metrics").path("windowLimited").asBoolean(), exactly10000.toPrettyString());
                preparedProjection(db, scenario, defensiveFrom.plusSeconds(10000L * 60), 1, 60, 12000);
                var beforeRead = ForwardSimEvaluationFixture.allFacts(db);
                JsonNode limited = http.ok("GET", defensivePath, token, null);
                assertEquals("UNAVAILABLE", limited.path("status").asText());
                assertTrue(limited.path("metrics").path("windowLimited").asBoolean());
                assertTrue(limited.path("samples").path("windowLimited").asBoolean());
                assertFinding(limited, "OBSERVATION_WINDOW_LIMITED");
                assertTrue(limited.path("performance").path("pnl").isNull());
                assertEquals(beforeRead, ForwardSimEvaluationFixture.allFacts(db));
                assertEquals(economicRows, db.admin.queryForObject("SELECT (SELECT count(*) FROM orders)+(SELECT count(*) FROM trades)+(SELECT count(*) FROM ledger_entries)", Long.class));
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=?", Integer.class, account));
                ForwardSimEvaluationFixture.evidence("postgres-bounded-projection.json", Map.of("source", "SYNTHETIC_PREPARED_READ_PROJECTION_ONLY",
                        "formalParentRun", scenario.binding().paperRunId(), "hourly1002", moreThan1000,
                        "legacy1000Guard", legacy, "defensive10000Status", exactly10000.path("status"),
                        "defensive10001", limited, "economicRowsBeforeAndAfter", economicRows));
            }
        }
    }

    private static void replayRemainsHistoricalWithoutForwardEconomicDisclosure(
            org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext app,
            TenantIsolationTestDatabase db, TenantIsolationHttpIntegrationTest.Http http,
            ForwardSimEvaluationFixture.Scenario scenario, String token) throws Exception {
        var sim = app.getBean(StrategySimRunService.class);
        var replay = db.as(scenario.owner(), () -> sim.create(scenario.binding().publishId(),
                new BigDecimal("10000"), "experiment-owner"));
        db.as(scenario.owner(), () -> {
            app.getBean(PaperTradingRunService.class).start(replay.paperRunId());
            // 合成行情仍由原回放服务推进并经canonical撮合，不直接制造成交或账务。
            for (int step = 0; step < 12; step++) {
                sim.advance(replay.paperRunId());
                app.getBean(PaperMatchingService.class).matchOnce(100);
            }
            return null;
        });
        assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM continuous_sim_runs WHERE paper_run_id=?",
                Integer.class, replay.paperRunId()));
        assertTrue(db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=?",
                Long.class, replay.canonicalAccountId()) > 0, "formal replay must have canonical fills before comparing query contracts");
        String legacyPath = "/api/paper-trading/strategy-sim/analysis/runs/" + replay.paperRunId() + "/performance";
        var facts = ForwardSimEvaluationFixture.allFacts(db);
        JsonNode legacy = http.ok("GET", legacyPath, token, null);
        assertEquals("AVAILABLE", legacy.path("status").asText(), legacy.toPrettyString());
        assertEquals("REPLAY_EVENT_TIME", legacy.path("timeBasis").asText());
        assertFalse(legacy.path("samples").isEmpty());
        assertFalse(legacy.path("pnl").isNull());
        assertFalse(legacy.path("initialEquity").isNull());
        assertFalse(legacy.path("ledgerAsOf").isNull());
        Instant cutoff = db.admin.queryForObject("SELECT created_at FROM paper_trading_runs WHERE paper_run_id=?",
                Timestamp.class, replay.paperRunId()).toInstant();
        JsonNode forward = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(replay.paperRunId(),
                scenario.market().start(), scenario.market().end(), cutoff), token, null);
        assertEquals("NOT_COMPARABLE", forward.path("status").asText(), forward.toPrettyString());
        assertEquals("REPLAY_EVENT_TIME", forward.path("reason").asText());
        for (String field : List.of("pnl", "initialEquity", "finalEquity", "ledgerAsOf", "fundingBudget",
                "returnRatio", "maxDrawdown", "maxDrawdownRatio"))
            assertTrue(forward.path("performance").path(field).isNull(), "new asOf entry must not expose replay " + field);
        assertTrue(forward.path("performance").path("samples").isEmpty());
        assertTrue(forward.path("metrics").path("fees").isEmpty());
        assertTrue(forward.path("metrics").path("tradeCount").isNull());
        assertEquals(0, forward.path("samples").path("observed").asLong());
        assertEquals(legacy, http.ok("GET", legacyPath, token, null), "old replay performance remains independently usable");
        assertEquals(facts, ForwardSimEvaluationFixture.allFacts(db), "both query contracts must remain read-only");
        ForwardSimEvaluationFixture.evidence("postgres-replay-asof-isolation.json", Map.of(
                "source", ForwardSimEvaluationFixture.SOURCE, "paperRunId", replay.paperRunId(),
                "cutoff", cutoff.toString(), "legacyPerformance", legacy, "forwardEvaluation", forward,
                "readOnlyFingerprints", facts));
    }

    private static void preparedProjection(TenantIsolationTestDatabase db, ForwardSimEvaluationFixture.Scenario scenario,
            Instant start, int count, long seconds, int ordinalBase) throws Exception {
        ObjectNode prototype = (ObjectNode) ForwardSimEvaluationFixture.JSON.readTree(db.admin.queryForObject(
                "SELECT to_jsonb(d)::text FROM strategy_sim_decisions d WHERE paper_run_id=? ORDER BY signal_open_time DESC LIMIT 1",
                String.class, scenario.binding().paperRunId()));
        var decisions = new ArrayList<Object[]>(); var bars = new ArrayList<Object[]>();
        for (int index = 0; index < count; index++) {
            Instant at = start.plusSeconds(index * seconds); Instant available = at.plusSeconds(3600);
            var bar = ResearchSimComparisonFixture.MarketServer.bar(at, new BigDecimal("52200"));
            var identity = SpotBarIdentity.capture(List.of(bar), ForwardSimEvaluationFixture.JSON);
            JsonNode inputBars = ForwardSimEvaluationFixture.JSON.readTree(identity.canonicalJson());
            ObjectNode decision = prototype.deepCopy();
            decision.put("decision_id", "fwd-projection-" + (ordinalBase + index) + "-" + UUID.randomUUID());
            decision.put("signal_open_time", at.toString()); decision.put("signal_available_at", available.toString());
            decision.putNull("execution_open_time"); decision.putNull("execution_bar_sha256"); decision.putNull("execution_price");
            decision.put("input_sha256", identity.sha256());
            decision.set("input_snapshot_json", ForwardSimEvaluationFixture.JSON.createObjectNode().set("signalBars", inputBars));
            decision.put("status", "NO_SIGNAL"); decision.put("reason", "SYNTHETIC_PREPARED_HOLD");
            decision.putNull("side"); decision.putNull("quantity"); decision.putNull("strategy_run_id"); decision.putNull("order_id");
            decision.put("created_at", available.toString());
            decisions.add(new Object[]{decision.toString()});
            var savedBar = ForwardSimEvaluationFixture.JSON.createObjectNode();
            savedBar.put("paper_run_id", scenario.binding().paperRunId()); savedBar.put("open_time", at.toString());
            savedBar.put("close_time", bar.closeTime().toString()); savedBar.put("available_at", available.toString());
            savedBar.put("bar_sha256", identity.sha256()); savedBar.set("bar_json", inputBars); savedBar.put("observed_at", available.toString());
            bars.add(new Object[]{savedBar.toString()});
        }
        // 即使是容量夹具，也通过受限runtime及可信租户事务写入；执行epoch、RLS和全部触发器保持启用。
        db.as(scenario.owner(), () -> {
            var transaction = new TransactionTemplate(db.manager);
            transaction.setTimeout(40);
            transaction.executeWithoutResult(status -> {
                db.jdbc.batchUpdate("INSERT INTO continuous_sim_bars SELECT * FROM jsonb_populate_record(NULL::continuous_sim_bars,?::jsonb)", bars);
                db.jdbc.batchUpdate("INSERT INTO strategy_sim_decisions SELECT * FROM jsonb_populate_record(NULL::strategy_sim_decisions,?::jsonb)", decisions);
            });
            return null;
        });
    }

    private static Map<String, Object> review(long revision, String decision, Instant asOf, String checksum) {
        return Map.of("expectedRevision", revision, "decision", decision, "reason", "Synthetic isolated research review only",
                "observedAt", asOf.toString(), "expectedEvaluationChecksum", checksum);
    }

    /** 比较所有字段及精确十进制值；JSONB的10000与10000.0不改变经济语义。 */
    private static boolean sameProjection(JsonNode expected, JsonNode actual) {
        return expected.equals((left, right) -> left.isNumber() && right.isNumber()
                ? left.decimalValue().compareTo(right.decimalValue()) : left.equals(right) ? 0 : 1, actual);
    }

    private static void rejectIdentityBindings(TenantIsolationTestDatabase db, TenantIsolationHttpIntegrationTest.Http http,
            ForwardSimEvaluationFixture.Scenario scenario, String token, Map<String, Object> request) throws Exception {
        for (String field : List.of("paperRunId", "strategyVersionId", "publishId", "validationId", "selectedTrialId", "canonicalAccountId")) {
            var invalid = new LinkedHashMap<>(request);
            invalid.put(field, field.equals("canonicalAccountId") ? ((Number) request.get(field)).longValue() + 9999
                    : field.endsWith("Id") && List.of("validationId", "selectedTrialId").contains(field) ? UUID.randomUUID().toString() : "unrelated-identity");
            var response = http.send("POST", OBS + "/operations/" + UUID.randomUUID(), token, invalid);
            assertTrue(List.of(400,403,404,409).contains(response.statusCode()), field + ": " + response.body());
        }
        var mislabeled = new LinkedHashMap<>(request); mislabeled.put("kind", "FORWARD");
        assertEquals(400, http.send("POST", OBS + "/operations/" + UUID.randomUUID(), token, mislabeled).statusCode());
        assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM strategy_observations", Integer.class));
    }

    private static void economicOracle(TenantIsolationTestDatabase db, ForwardSimEvaluationFixture.Scenario scenario, JsonNode evaluation) {
        long account = evaluation.path("canonicalAccountId").asLong();
        BigDecimal budget = db.admin.queryForObject("SELECT sum(delta) FROM ledger_entries WHERE account_id=?"
                + " AND ref_type='SIM_FUNDING_CASH' AND currency='USDT'", BigDecimal.class, account);
        var samples = evaluation.path("performance").path("samples"); assertTrue(samples.size() >= 2);
        BigDecimal first = null, last = null, peak = null, drawdown = BigDecimal.ZERO, drawdownRatio = BigDecimal.ZERO;
        for (JsonNode sample : samples) {
            Instant at = Instant.parse(sample.path("eventAt").asText());
            String boundary = first == null && at.equals(scenario.from()) ? "<" : "<=";
            BigDecimal cashDelta = db.admin.queryForObject("SELECT coalesce(sum(delta),0) FROM ledger_entries"
                    + " WHERE account_id=? AND ref_type='TRADE' AND currency='USDT' AND ts" + boundary + "? AND ts<?"
                    + " AND (idempotency_key=ref_id||':LEDGER:1' OR idempotency_key=ref_id||':LEDGER:FEE_1')",
                    BigDecimal.class, account, Timestamp.from(at), Timestamp.from(scenario.to()));
            BigDecimal quantity = db.admin.queryForObject("SELECT coalesce(sum(CASE WHEN o.side='BUY' THEN t.qty ELSE -t.qty END"
                    + "-CASE WHEN t.fee_currency='BTC' THEN t.fee ELSE 0 END),0) FROM trades t JOIN orders o USING(order_id)"
                    + " WHERE t.account_id=? AND t.ts" + boundary + "? AND t.ts<?", BigDecimal.class, account, Timestamp.from(at), Timestamp.from(scenario.to()));
            BigDecimal equity = budget.add(cashDelta).add(quantity.multiply(decimal(sample.path("markPrice"))));
            equal(equity, sample.path("equity"));
            if (first == null) { first = equity; peak = equity; }
            peak = peak.max(equity); drawdown = drawdown.max(peak.subtract(equity));
            drawdownRatio = drawdownRatio.max(peak.subtract(equity).divide(peak, MATH)); last = equity;
        }
        equal(last.subtract(first), evaluation.path("performance").path("pnl"));
        equal(last.subtract(first).divide(first, MATH), evaluation.path("performance").path("returnRatio"));
        equal(drawdown, evaluation.path("performance").path("maxDrawdown"));
        equal(drawdownRatio, evaluation.path("performance").path("maxDrawdownRatio"));
        long trades = db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=? AND ts>=? AND ts<? AND ts<=?", Long.class,
                account, Timestamp.from(scenario.from()), Timestamp.from(scenario.to()), Timestamp.from(scenario.asOf()));
        assertEquals(trades, evaluation.path("metrics").path("tradeCount").asLong());
        equal(BigDecimal.valueOf(trades), evaluation.path("metrics").path("tradeFrequencyPerDay"));
        var periodDecisionIds = new java.util.HashSet<>(db.admin.queryForList("""
            SELECT decision_id FROM strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time>=?
             AND signal_open_time+interval '1 hour'<=? AND signal_available_at<=? AND created_at<=?
            """, String.class, scenario.binding().paperRunId(), Timestamp.from(scenario.from()),
                Timestamp.from(scenario.to()), Timestamp.from(scenario.asOf()), Timestamp.from(scenario.asOf())));
        var hourly = new ArrayList<BigDecimal>(); samples.forEach(sample -> {
            if ("SIGNAL_CLOSE".equals(sample.path("markType").asText())
                    && periodDecisionIds.contains(sample.path("decisionId").asText())) hourly.add(decimal(sample.path("equity")));
        });
        assertEquals(24, hourly.size(), "24 completed target hours exclude the prior opening valuation anchor");
        assertEquals(hourly.size(), evaluation.path("methodDetails").path("turnoverEquitySampleCount").asLong());
        BigDecimal average = hourly.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(hourly.size()), MATH);
        BigDecimal notional = db.admin.queryForObject("SELECT coalesce(sum(abs(qty*price)),0) FROM trades WHERE account_id=? AND ts>=? AND ts<? AND ts<=?",
                BigDecimal.class, account, Timestamp.from(scenario.from()), Timestamp.from(scenario.to()), Timestamp.from(scenario.asOf()));
        equal(notional.divide(average, MATH), evaluation.path("metrics").path("twoWayTurnover"));
    }

    private static BigDecimal decimal(JsonNode value) { assertFalse(value.isNull() || value.isMissingNode()); return new BigDecimal(value.asText()); }
    private static void equal(BigDecimal expected, JsonNode actual) {
        assertTrue(expected.subtract(decimal(actual)).abs().compareTo(new BigDecimal("0.000000000001")) <= 0,
                "independent economic oracle expected=" + expected + " actual=" + actual);
    }

    static void inject(TenantIsolationTestDatabase db, String sql, Object... arguments) {
        new TransactionTemplate(new DataSourceTransactionManager(db.admin.getDataSource())).executeWithoutResult(status -> {
            db.admin.execute("SET LOCAL session_replication_role=replica"); db.admin.update(sql, arguments);
        });
    }

    private static void restart(TenantIsolationTestDatabase db, TenantIdentity owner, UUID observation, UUID review,
            String checksum, Instant asOf) throws Exception {
        Path directory = Path.of("..", "..", "artifacts", "nq-v103-forward"); Files.createDirectories(directory);
        Path log = Files.createTempFile(directory, "review-restart-", ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("java.class.path"), ForwardSimEvaluationRestartProbeMain.class.getName(),
                Long.toString(owner.userId()), observation.toString(), review.toString(), checksum, asOf.toString());
        builder.environment().put("TASK_FORWARD_URL", db.url()); builder.environment().put("TASK_FORWARD_USER", db.runtime);
        builder.environment().put("TASK_FORWARD_PASSWORD", db.runtimePassword);
        var child = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(90, TimeUnit.SECONDS), "fresh JVM review read timed out");
            assertEquals(0, child.exitValue(), Files.readString(log));
            assertTrue(Files.readString(log).contains("FORWARD_REVIEW_RESTART_VERIFIED"));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
}

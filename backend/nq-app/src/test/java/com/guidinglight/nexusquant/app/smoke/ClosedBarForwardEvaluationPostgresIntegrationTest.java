package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.RoundingMode;
import org.springframework.dao.DataAccessException;
import com.guidinglight.nexusquant.app.research.ResearchCreationOperationService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 新方法用正式SIM经济事实及合成接收时钟证明；故障注入仅作用于本例随机数据库。 */
@EnabledIfSystemProperty(named = "nq.tenant.pg.required", matches = "true")
class ClosedBarForwardEvaluationPostgresIntegrationTest {
    private static final String OBS = ForwardSimEvaluationFixture.ROOT;
    private static final String METHOD = ClosedBarForwardEvaluationFixture.METHOD;
    private static final MathContext MATH = MathContext.DECIMAL128;

    @Test @Timeout(420)
    void trustedGridCanonicalOracleReadonlyBenchmarksIsolationAndImmutableReviewRestart() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner"); var other = db.local("closed-other");
            var tenant = db.demo("CLOSED_OTHER", "closed-demo");
            db.migration(null).validate();
            assertEquals("52", db.admin.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class));
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var scenario = ClosedBarForwardEvaluationFixture.prepare(app, db, owner, "SPOT_SMA_TARGET_V1");
                String token = http.login("experiment-owner"), otherToken = http.login("closed-other"), demoToken = http.login("closed-demo");
                assertTrue(List.of(400, 409).contains(http.send("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null).statusCode()),
                        "an explicit method alone cannot upgrade an unregistered historical target");
                var request = ClosedBarForwardEvaluationFixture.request(db, scenario);
                UUID operation = UUID.randomUUID(); JsonNode created;
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var a = pool.submit(() -> http.ok("POST", OBS + "/operations/" + operation, token, request));
                    var b = pool.submit(() -> http.ok("POST", OBS + "/operations/" + operation, token, request));
                    created = a.get(40, TimeUnit.SECONDS); assertEquals(created, b.get(40, TimeUnit.SECONDS));
                }
                UUID id = UUID.fromString(created.path("observation").path("observationId").asText());
                assertEquals(METHOD, created.path("observation").path("policy").path("methodVersion").asText());
                assertEquals("FORWARD", created.path("observation").path("kind").asText());
                assertTrue(created.path("observation").path("policy").toString().length() < 16384);
                var retrospective = new LinkedHashMap<>(request); retrospective.put("kind", "RETROSPECTIVE");
                assertEquals(400, http.send("POST", OBS + "/operations/" + UUID.randomUUID(), token, retrospective).statusCode());
                ClosedBarForwardEvaluationFixture.advance(app, db, scenario);
                var facts = ForwardSimEvaluationFixture.allFacts(db);
                JsonNode legacy = http.ok("GET", scenario.path(), token, null);
                JsonNode evaluated = http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null);
                assertAvailable(evaluated);
                assertEquals(METHOD, evaluated.path("methodVersion").asText());
                assertEquals(24, evaluated.path("samples").path("observed").asInt());
                assertEquals(25, evaluated.path("performance").path("samples").size());
                assertTrue(decimal(evaluated.path("performance").path("samples").get(0).path("positionQuantity")).signum() > 0,
                        "the oracle must include a genuine nonzero opening position");
                oracle(db, scenario, evaluated);
                assertEquals(evaluated, http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null));
                assertEquals(legacy, http.ok("GET", scenario.path(), token, null), "explicit new method cannot change V1 results");
                assertEquals("CONTINUOUS_SIM_OBSERVATION_V1", legacy.path("methodVersion").asText());
                assertEquals(facts, ForwardSimEvaluationFixture.allFacts(db), "all GETs preserve canonical and observation facts");
                assertEquals(404, http.send("GET", ClosedBarForwardEvaluationFixture.path(scenario), otherToken, null).statusCode());
                assertTrue(List.of(403, 404).contains(http.send("GET", ClosedBarForwardEvaluationFixture.path(scenario), demoToken, null).statusCode()));
                assertEquals(0, db.as(other, () -> db.jdbc.queryForObject("SELECT count(*) FROM strategy_observations WHERE observation_id=?", Integer.class, id)));
                assertEquals(0, db.as(tenant, () -> db.jdbc.queryForObject("SELECT count(*) FROM strategy_observations WHERE observation_id=?", Integer.class, id)));
                assertEquals(400, http.send("GET", ClosedBarForwardEvaluationFixture.path(scenario).replace("asOf=" + scenario.asOf(),
                        "asOf=" + scenario.asOf().plusSeconds(1)), token, null).statusCode());
                var foreignWindow = ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        scenario.from().minusSeconds(3600), scenario.to(), scenario.asOf()) + "&methodVersion=" + METHOD;
                assertTrue(List.of(400, 409).contains(http.send("GET", foreignWindow, token, null).statusCode()));
                var economics = ResearchSimComparisonPostgresIntegrationTest.economicFacts(db);
                var firstRequest = review(0, scenario.asOf(), evaluated.path("checksum").asText());
                UUID reviewOperation = UUID.randomUUID();
                var saved = http.ok("POST", OBS + "/" + id + "/reviews/operations/" + reviewOperation, token, firstRequest);
                assertProjection(evaluated, saved.path("evaluation"));
                assertEquals(saved, http.ok("POST", OBS + "/" + id + "/reviews/operations/" + reviewOperation, token, firstRequest));
                assertEquals(409, http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, firstRequest).statusCode());
                assertEquals(409, http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token,
                        review(1, scenario.asOf(), "0".repeat(64))).statusCode());
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var concurrent = review(1, scenario.asOf(), evaluated.path("checksum").asText());
                    var a = pool.submit(() -> http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, concurrent));
                    var b = pool.submit(() -> http.send("POST", OBS + "/" + id + "/reviews/operations/" + UUID.randomUUID(), token, concurrent));
                    var statuses = List.of(a.get(40, TimeUnit.SECONDS).statusCode(), b.get(40, TimeUnit.SECONDS).statusCode());
                    assertEquals(1, statuses.stream().filter(code -> code == 200).count());
                    assertEquals(1, statuses.stream().filter(code -> code == 409).count());
                }
                assertEquals(economics, ResearchSimComparisonPostgresIntegrationTest.economicFacts(db));
                UUID invalidOperation = UUID.randomUUID();
                ObjectNode wrongMethod = ((ObjectNode) evaluated).deepCopy().put("methodVersion", "CONTINUOUS_SIM_OBSERVATION_V1");
                var mismatch = assertThrows(DataAccessException.class, () -> db.as(owner, () ->
                        app.getBean(ResearchCreationOperationService.class)
                                .execute(invalidOperation, owner.userId(), "OBSERVATION_REVIEW", id.toString(),
                                        ForwardSimEvaluationFixture.JSON.createObjectNode().put("syntheticMismatch", true), () -> {
                                            app.getBean(JdbcTemplate.class).update("""
                                                INSERT INTO strategy_observation_reviews(review_id,operation_id,observation_id,revision,
                                                  reviewer_user_id,decision,reason,reviewed_at,observed_at,evaluation_checksum,evaluation_json)
                                                VALUES(?,?,?,3,?,'CONTINUE_OBSERVATION','方法不匹配负例',?,?,?,?::jsonb)
                                                """, UUID.randomUUID(), invalidOperation, id, owner.userId(), Timestamp.from(scenario.asOf()),
                                                    Timestamp.from(scenario.asOf()), evaluated.path("checksum").asText(), wrongMethod.toString());
                                            return new ResearchCreationOperationService.Result("unreachable", id.toString());
                                        })));
                assertTrue(mismatch.getMostSpecificCause().getMessage().contains("OBSERVATION_REVIEW_SNAPSHOT_MISMATCH"), mismatch.getMostSpecificCause().getMessage());
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM research_creation_operations WHERE operation_id=?", Integer.class, invalidOperation),
                        "a rejected snapshot and its operation receipt must roll back together");
                UUID reviewId = UUID.fromString(saved.path("review").path("reviewId").asText());
                assertThrows(RuntimeException.class, () -> db.as(owner, () -> db.jdbc.update(
                        "UPDATE strategy_observation_reviews SET evaluation_json=jsonb_set(evaluation_json,'{methodVersion}','\"CONTINUOUS_SIM_OBSERVATION_V1\"') WHERE review_id=?", reviewId)));
                restart(db, owner.userId(), id, reviewId, evaluated.path("checksum").asText(), scenario.asOf());
                assertProjection(saved, http.ok("GET", OBS + "/" + id + "/reviews/" + reviewId, token, null));
                evidence("normal-review-isolation.json", Map.of("source", "SYNTHETIC_HOURLY_RECEIPT_CANONICAL_SIM",
                        "evaluation", evaluated, "legacy", legacy, "savedReview", saved, "factsBeforeRead", facts,
                        "economicFingerprints", economics, "policy", created.path("observation").path("policy")));
            }
        }
    }

    @Test @Timeout(420)
    void missingPricesReceiptTimeCutoffQualityPairFeesHalfOpenFactsAndBudgetsRejectFalseComparability() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var scenario = ClosedBarForwardEvaluationFixture.prepare(app, db, owner, "SPOT_SMA_TARGET_V1");
                String token = http.login("experiment-owner");
                http.ok("POST", OBS + "/operations/" + UUID.randomUUID(), token, ClosedBarForwardEvaluationFixture.request(db, scenario));
                ClosedBarForwardEvaluationFixture.advance(app, db, scenario);
                JsonNode baseline = http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null); assertAvailable(baseline);
                var cases = new LinkedHashMap<String, JsonNode>();
                String run = scenario.binding().paperRunId(); long account = ForwardSimEvaluationFixture.account(db, run);
                for (var entry : Map.of("opening-price", scenario.from().minusSeconds(3600), "ending-price", scenario.to().minusSeconds(3600),
                        "middle-price", scenario.from().plusSeconds(10 * 3600L)).entrySet()) {
                    String row = row(db, "continuous_sim_bars", "paper_run_id=? AND open_time=?", run, Timestamp.from(entry.getValue()));
                    try {
                        inject(db, "DELETE FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?", run, Timestamp.from(entry.getValue()));
                        cases.put(entry.getKey(), unavailable(http, scenario, token));
                    } finally { restore(db, "continuous_sim_bars", row); }
                }
                Instant lastOpen = scenario.to().minusSeconds(3600);
                String lastBar = row(db, "continuous_sim_bars", "paper_run_id=? AND open_time=?", run, Timestamp.from(lastOpen));
                for (String fault : List.of("old-collection", "late-receipt", "clock-skew", "wrong-source", "revision", "unconfirmed")) {
                    try {
                        ObjectNode changed = (ObjectNode) ForwardSimEvaluationFixture.JSON.readTree(lastBar);
                        ObjectNode bar = (ObjectNode) changed.path("bar_json").get(0);
                        ObjectNode raw = (ObjectNode) ForwardSimEvaluationFixture.JSON.readTree(bar.path("rawPayloadJson").asText());
                        switch (fault) {
                            case "old-collection" -> bar.put("rawPayloadJson", "{}");
                            case "late-receipt" -> {
                                Instant late = scenario.to().plusSeconds(901); changed.put("observed_at", late.toString());
                                raw.put("receivedAt", late.toString()); bar.put("rawPayloadJson", raw.toString());
                            }
                            case "clock-skew" -> {
                                Instant skew = scenario.to().minusSeconds(1); changed.put("observed_at", skew.toString());
                                raw.put("receivedAt", skew.toString()); bar.put("rawPayloadJson", raw.toString());
                            }
                            case "wrong-source" -> bar.put("symbol", "ETH-USDT");
                            case "revision" -> bar.put("closePrice", "1");
                            case "unconfirmed" -> { ((ArrayNode) raw.path("candle")).set(8, ForwardSimEvaluationFixture.JSON.getNodeFactory().textNode("0")); bar.put("rawPayloadJson", raw.toString()); }
                            default -> throw new AssertionError(fault);
                        }
                        inject(db, "DELETE FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?", run, Timestamp.from(lastOpen));
                        restore(db, "continuous_sim_bars", changed.toString()); cases.put(fault, unavailable(http, scenario, token));
                    } finally {
                        inject(db, "DELETE FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?", run, Timestamp.from(lastOpen));
                        restore(db, "continuous_sim_bars", lastBar);
                    }
                }
                // server time已知而响应尚未接收，不能因availableAt早于asOf而提前放行。
                String cutoff = ForwardSimEvaluationFixture.evaluationPath(run, scenario.from(), scenario.to(), scenario.to().plusSeconds(3))
                        + "&methodVersion=" + METHOD;
                var notReceived = http.ok("GET", cutoff, token, null); assertUnavailable(notReceived); cases.put("response-after-asof", notReceived);
                String missingDecision = db.admin.queryForObject("SELECT decision_id FROM strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time=?",
                        String.class, run, Timestamp.from(scenario.from().plusSeconds(8 * 3600L)));
                String decision = row(db, "strategy_sim_decisions", "decision_id=?", missingDecision);
                try {
                    inject(db, "DELETE FROM strategy_sim_decisions WHERE decision_id=?", missingDecision);
                    cases.put("bar-without-decision", unavailable(http, scenario, token));
                } finally { restore(db, "strategy_sim_decisions", decision); }
                var tradeRow = db.admin.queryForMap("SELECT trade_id,ts,fee,fee_currency FROM trades WHERE account_id=? ORDER BY ts LIMIT 1", account);
                String trade = tradeRow.get("trade_id").toString();
                for (Instant boundary : List.of(scenario.from(), scenario.to())) {
                    var originalTimes = db.admin.queryForList("SELECT entry_id,ts FROM ledger_entries WHERE ref_type='TRADE' AND ref_id=?", trade);
                    try {
                        inject(db, "UPDATE trades SET ts=? WHERE trade_id=?", Timestamp.from(boundary), trade);
                        inject(db, "UPDATE ledger_entries SET ts=? WHERE ref_type='TRADE' AND ref_id=?", Timestamp.from(boundary), trade);
                        JsonNode evaluated = http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null);
                        assertAvailable(evaluated); oracle(db, scenario, evaluated);
                        cases.put(boundary.equals(scenario.from()) ? "from-trade-included" : "to-trade-excluded", evaluated);
                    } finally {
                        inject(db, "UPDATE trades SET ts=? WHERE trade_id=?", tradeRow.get("ts"), trade);
                        for (var original : originalTimes) inject(db, "UPDATE ledger_entries SET ts=? WHERE entry_id=?", original.get("ts"), original.get("entry_id"));
                    }
                }
                try { inject(db, "UPDATE trades SET fee=NULL WHERE trade_id=?", trade); cases.put("missing-fee", unavailable(http, scenario, token)); }
                finally { inject(db, "UPDATE trades SET fee=? WHERE trade_id=?", tradeRow.get("fee"), trade); }
                try { inject(db, "UPDATE trades SET fee_currency='BNB' WHERE trade_id=?", trade); cases.put("unsupported-fee-currency", unavailable(http, scenario, token)); }
                finally { inject(db, "UPDATE trades SET fee_currency=? WHERE trade_id=?", tradeRow.get("fee_currency"), trade); }
                var feeEntries = db.admin.queryForList("SELECT entry_id,currency,delta FROM ledger_entries WHERE ref_id=? AND idempotency_key IN (?||':LEDGER:FEE_1',?||':LEDGER:FEE_2')", trade, trade, trade);
                try {
                    BigDecimal baseFee = db.admin.queryForObject("SELECT qty*0.001 FROM trades WHERE trade_id=?", BigDecimal.class, trade).setScale(8, RoundingMode.HALF_UP);
                    inject(db, "UPDATE trades SET fee=?,fee_currency='BTC' WHERE trade_id=?", baseFee, trade);
                    inject(db, "UPDATE ledger_entries SET currency='BTC',delta=CASE WHEN delta<0 THEN -? ELSE ? END WHERE ref_id=? AND idempotency_key IN (?||':LEDGER:FEE_1',?||':LEDGER:FEE_2')", baseFee, baseFee, trade, trade, trade);
                    JsonNode base = http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null); assertAvailable(base); oracle(db, scenario, base);
                    cases.put("base-currency-fee", base);
                } finally {
                    inject(db, "UPDATE trades SET fee=?,fee_currency=? WHERE trade_id=?", tradeRow.get("fee"), tradeRow.get("fee_currency"), trade);
                    for (var entry : feeEntries) inject(db, "UPDATE ledger_entries SET currency=?,delta=? WHERE entry_id=?", entry.get("currency"), entry.get("delta"), entry.get("entry_id"));
                }
                try { inject(db, "UPDATE paper_trading_runs SET symbol='ETH-USDT' WHERE paper_run_id=?", run); cases.put("different-pair", unavailable(http, scenario, token)); }
                finally { inject(db, "UPDATE paper_trading_runs SET symbol='BTC-USDT' WHERE paper_run_id=?", run); }
                var fundingTimes = db.admin.queryForList("SELECT entry_id,ts FROM ledger_entries WHERE account_id=? AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')", account);
                try {
                    inject(db, "UPDATE ledger_entries SET ts=? WHERE account_id=? AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')", Timestamp.from(scenario.from()), account);
                    cases.put("funding-at-opening", unavailable(http, scenario, token));
                } finally { for (var original : fundingTimes) inject(db, "UPDATE ledger_entries SET ts=? WHERE entry_id=?", original.get("ts"), original.get("entry_id")); }
                var fundingDeltas = db.admin.queryForList("SELECT entry_id,delta FROM ledger_entries WHERE account_id=? AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')", account);
                for (int factor : List.of(0, -1)) {
                    try {
                        inject(db, "UPDATE ledger_entries SET delta=delta*? WHERE account_id=? AND ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')", factor, account);
                        cases.put(factor == 0 ? "zero-funding" : "negative-funding", unavailable(http, scenario, token));
                    } finally { for (var original : fundingDeltas) inject(db, "UPDATE ledger_entries SET delta=? WHERE entry_id=?", original.get("delta"), original.get("entry_id")); }
                }
                var principal = db.admin.queryForList("SELECT entry_id,delta FROM ledger_entries WHERE ref_id=? AND idempotency_key IN (?||':LEDGER:1',?||':LEDGER:2')", trade, trade, trade);
                for (int below : List.of(0, 1)) {
                    try {
                        BigDecimal shift = decimal(baseline.path("performance").path("initialEquity")).add(BigDecimal.valueOf(below));
                        inject(db, "UPDATE ledger_entries SET delta=CASE WHEN delta<0 THEN delta-? ELSE delta+? END WHERE ref_id=? AND idempotency_key IN (?||':LEDGER:1',?||':LEDGER:2')", shift, shift, trade, trade, trade);
                        cases.put(below == 0 ? "zero-opening-equity" : "negative-opening-equity", unavailable(http, scenario, token));
                    } finally { for (var original : principal) inject(db, "UPDATE ledger_entries SET delta=? WHERE entry_id=?", original.get("delta"), original.get("entry_id")); }
                }
                try {
                    inject(db, """
                        INSERT INTO ledger_entries SELECT (jsonb_populate_record(NULL::ledger_entries,to_jsonb(e)||jsonb_build_object(
                          'entry_id','closed-extra-'||e.ref_type,'idempotency_key','closed-extra-'||e.ref_type))).*
                        FROM ledger_entries e WHERE e.account_id=? AND e.ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')
                        """, account);
                    cases.put("extra-funding", unavailable(http, scenario, token));
                } finally { inject(db, "DELETE FROM ledger_entries WHERE account_id=? AND entry_id LIKE 'closed-extra-%'", account); }
                // 容量投影只验证读取预算，不把故意缺账的复制行作为正式经济事实验收。
                int originalCount = db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=?", Integer.class, account);
                try {
                    inject(db, """
                        INSERT INTO trades SELECT (jsonb_populate_record(NULL::trades,to_jsonb(t)||jsonb_build_object(
                          'trade_id','closed-budget-'||g.n,'exchange_trade_id','closed-budget-'||g.n))).*
                        FROM trades t CROSS JOIN generate_series(1,?) g(n) WHERE t.trade_id=?
                        """, 10000 - originalCount, trade);
                    long started = System.nanoTime();
                    JsonNode exactly = unavailable(http, scenario, token);
                    assertEquals("LEDGER_INCOMPLETE", exactly.path("performance").path("status").asText(), exactly.toPrettyString());
                    assertFalse(exactly.path("metrics").path("windowLimited").asBoolean(), exactly.toPrettyString());
                    cases.put("exactly-10000-facts", exactly);
                    inject(db, """
                        INSERT INTO trades SELECT (jsonb_populate_record(NULL::trades,to_jsonb(t)||jsonb_build_object(
                          'trade_id','closed-budget-overflow','exchange_trade_id','closed-budget-overflow'))).* FROM trades t WHERE t.trade_id=?
                        """, trade);
                    JsonNode overflow = unavailable(http, scenario, token);
                    assertEquals("OBSERVATION_WINDOW_LIMITED", overflow.path("reason").asText(), overflow.toPrettyString());
                    assertTrue(Duration.ofNanos(System.nanoTime()-started).compareTo(Duration.ofSeconds(30)) < 0, "two bounded HTTP reads must finish within their combined timeout budget");
                    cases.put("10001-facts", overflow);
                } finally { inject(db, "DELETE FROM trades WHERE account_id=? AND trade_id LIKE 'closed-budget-%'", account); }
                assertEquals(baseline, http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null));
                evidence("negative-and-half-open.json", Map.of("source", "SYNTHETIC_ISOLATED_FAULT_INJECTION", "cases", cases));
            }
        }
    }

    @Test @Timeout(420)
    void noTradeCashAndIncompleteWindowKeepSampleSufficiencyDistinct() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local("experiment-owner");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var scenario = ClosedBarForwardEvaluationFixture.prepare(app, db, owner, "SPOT_CHANNEL_BREAKOUT_V1");
                String token = http.login("experiment-owner");
                http.ok("POST", OBS + "/operations/" + UUID.randomUUID(), token, ClosedBarForwardEvaluationFixture.request(db, scenario));
                ClosedBarForwardEvaluationFixture.advance(app, db, scenario, false);
                JsonNode held = http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null); assertAvailable(held);
                assertEquals(0, held.path("metrics").path("tradeCount").asLong());
                equal(BigDecimal.ZERO, held.path("performance").path("pnl"));
                oracle(db, scenario, held);
                var partial = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(scenario.binding().paperRunId(),
                        scenario.from(), scenario.to(), scenario.from().plusSeconds(6 * 3600L)) + "&methodVersion=" + METHOD, token, null);
                assertEquals("INSUFFICIENT_SAMPLES", partial.path("status").asText()); assertUnavailable(partial);
                var facts = ForwardSimEvaluationFixture.allFacts(db);
                assertEquals(held, http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null));
                assertEquals(facts, ForwardSimEvaluationFixture.allFacts(db));
                String run = scenario.binding().paperRunId();
                var economicBeforeRevision = new LinkedHashMap<>(ResearchSimComparisonPostgresIntegrationTest.economicFacts(db));
                economicBeforeRevision.remove("continuous_sim_runs");
                var barsBeforeRevision = facts.get("continuous_sim_bars");
                Instant requestStarted = scenario.to().plusSeconds(6), detectedAt = scenario.to().plusSeconds(12);
                var currentTime = new AtomicReference<>(requestStarted);
                Clock revisionClock = new Clock() {
                    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                    @Override public Clock withZone(ZoneId zone) { return this; }
                    @Override public Instant instant() { return currentTime.get(); }
                };
                ClosedBarMarketFeed revised = (firstOpen, maximum) -> {
                    currentTime.set(detectedAt);
                    return new ClosedBarMarketFeed.Observation(detectedAt, detectedAt,
                            List.of(ClosedBarForwardEvaluationFixture.bar(firstOpen, new BigDecimal("77777"), detectedAt, detectedAt)),
                            new ClosedBarMarketFeed.Rule(detectedAt, "LIVE", new BigDecimal("0.01"), new BigDecimal("0.0001"), new BigDecimal("0.0001")),
                            new ClosedBarMarketFeed.Quote(detectedAt, new BigDecimal("52200")));
                };
                var driver = new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class),
                        app.getBean(StrategySimRunService.class), app.getBean(PaperTradingRunService.class), revised,
                        app.getBean(JdbcTemplate.class), revisionClock);
                ReflectionTestUtils.setField(driver, "tenantWork", app.getBean(TenantWorkAccess.class));
                var stopped = db.as(owner, () -> driver.pollOnce(run));
                assertEquals("STOPPED", stopped.status()); assertEquals("DATA_REVISION_DETECTED", stopped.blockReason());
                assertEquals(detectedAt, db.admin.queryForObject("SELECT updated_at FROM continuous_sim_runs WHERE paper_run_id=?", Timestamp.class, run).toInstant());
                ForwardSimEvaluationFixture.clock(app, detectedAt);
                var beforeDetection = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(run, scenario.from(), scenario.to(),
                        requestStarted.plusSeconds(1)) + "&methodVersion=" + METHOD, token, null);
                assertAvailable(beforeDetection);
                var knownRevision = http.ok("GET", ForwardSimEvaluationFixture.evaluationPath(run, scenario.from(), scenario.to(),
                        detectedAt) + "&methodVersion=" + METHOD, token, null);
                assertUnavailable(knownRevision); assertEquals("DATA_REVISION_DETECTED", knownRevision.path("reason").asText());
                assertEquals(held, http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null));
                assertEquals(barsBeforeRevision, ForwardSimEvaluationFixture.allFacts(db).get("continuous_sim_bars"));
                var economicAfterRevision = new LinkedHashMap<>(ResearchSimComparisonPostgresIntegrationTest.economicFacts(db));
                economicAfterRevision.remove("continuous_sim_runs");
                assertEquals(economicBeforeRevision, economicAfterRevision);
                evidence("no-trade-and-pending.json", Map.of("source", "SYNTHETIC_HOURLY_RECEIPT_CANONICAL_SIM", "held", held,
                        "partial", partial, "beforeRevisionDetection", beforeDetection, "knownRevision", knownRevision));
            }
        }
    }

    private static void oracle(TenantIsolationTestDatabase db, ForwardSimEvaluationFixture.Scenario scenario, JsonNode evaluation) {
        long account = evaluation.path("canonicalAccountId").asLong();
        BigDecimal budget = db.admin.queryForObject("SELECT sum(delta) FROM ledger_entries WHERE account_id=? AND ref_type='SIM_FUNDING_CASH' AND currency='USDT'", BigDecimal.class, account);
        BigDecimal first = null, last = null, peak = null, drawdown = BigDecimal.ZERO, drawdownRatio = BigDecimal.ZERO;
        BigDecimal firstPrice = null, lastPrice = null;
        for (JsonNode sample : evaluation.path("performance").path("samples")) {
            Instant at = Instant.parse(sample.path("eventAt").asText());
            BigDecimal cash = db.admin.queryForObject("SELECT coalesce(sum(delta),0) FROM ledger_entries WHERE account_id=? AND ref_type='TRADE' AND currency='USDT' AND ts<?"
                    + " AND (idempotency_key=ref_id||':LEDGER:1' OR idempotency_key=ref_id||':LEDGER:FEE_1')", BigDecimal.class, account, Timestamp.from(at));
            BigDecimal quantity = db.admin.queryForObject("SELECT coalesce(sum(CASE WHEN o.side='BUY' THEN t.qty ELSE -t.qty END-CASE WHEN t.fee_currency='BTC' THEN t.fee ELSE 0 END),0)"
                    + " FROM trades t JOIN orders o USING(order_id) WHERE t.account_id=? AND t.ts<?", BigDecimal.class, account, Timestamp.from(at));
            BigDecimal price = db.admin.queryForObject("SELECT (bar_json->0->>'closePrice')::numeric FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?",
                    BigDecimal.class, scenario.binding().paperRunId(), Timestamp.from(at.minusSeconds(3600)));
            BigDecimal equity = budget.add(cash).add(quantity.multiply(price));
            equal(equity, sample.path("equity")); equal(quantity, sample.path("positionQuantity")); equal(price, sample.path("markPrice"));
            if (first == null) { first = equity; peak = equity; firstPrice = price; }
            peak = peak.max(equity); drawdown = drawdown.max(peak.subtract(equity));
            drawdownRatio = drawdownRatio.max(peak.subtract(equity).divide(peak, MATH)); last = equity; lastPrice = price;
        }
        equal(last.subtract(first), evaluation.path("performance").path("pnl"));
        BigDecimal ratio = last.subtract(first).divide(first, MATH);
        equal(ratio, evaluation.path("performance").path("returnRatio"));
        equal(drawdown, evaluation.path("performance").path("maxDrawdown"));
        equal(drawdownRatio, evaluation.path("performance").path("maxDrawdownRatio"));
        JsonNode benchmarks = evaluation.path("methodDetails").path("benchmarks");
        equal(BigDecimal.ZERO, benchmarks.path("cash").path("returnRatio"));
        equal(first, benchmarks.path("cash").path("finalEquity"));
        BigDecimal buyHold = lastPrice.divide(firstPrice, MATH).subtract(BigDecimal.ONE);
        equal(buyHold, benchmarks.path("buyHold").path("returnRatio"));
        equal(first.multiply(lastPrice).divide(firstPrice, MATH), benchmarks.path("buyHold").path("finalEquity"));
        equal(ratio.subtract(buyHold), benchmarks.path("buyHold").path("excessReturn"));
        long count = db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=? AND ts>=? AND ts<?", Long.class, account, Timestamp.from(scenario.from()), Timestamp.from(scenario.to()));
        assertEquals(count, evaluation.path("metrics").path("tradeCount").asLong());
    }

    private static JsonNode unavailable(TenantIsolationHttpIntegrationTest.Http http, ForwardSimEvaluationFixture.Scenario scenario, String token) throws Exception {
        JsonNode result = http.ok("GET", ClosedBarForwardEvaluationFixture.path(scenario), token, null); assertUnavailable(result); return result;
    }
    private static void assertAvailable(JsonNode value) { assertEquals("AVAILABLE", value.path("status").asText(), value.toPrettyString()); }
    private static void assertUnavailable(JsonNode value) {
        assertNotEquals("AVAILABLE", value.path("status").asText(), value.toPrettyString());
        assertTrue(value.path("performance").path("returnRatio").isNull(), value.toPrettyString());
        JsonNode benchmarks = value.path("methodDetails").path("benchmarks");
        assertTrue(benchmarks.path("buyHold").path("excessReturn").isNull(), value.toPrettyString());
    }
    private static BigDecimal decimal(JsonNode value) { assertFalse(value.isMissingNode() || value.isNull(), value.toString()); return new BigDecimal(value.asText()); }
    private static void equal(BigDecimal expected, JsonNode actual) { assertTrue(expected.subtract(decimal(actual)).abs().compareTo(new BigDecimal("0.000000000001")) <= 0, "oracle expected=" + expected + " actual=" + actual); }
    private static void assertProjection(JsonNode expected, JsonNode actual) {
        assertTrue(expected.equals((left, right) -> left.isNumber() && right.isNumber() ? left.decimalValue().compareTo(right.decimalValue()) : left.equals(right) ? 0 : 1, actual));
    }
    private static Map<String, Object> review(long revision, Instant asOf, String checksum) {
        return Map.of("expectedRevision", revision, "decision", "CONTINUE_OBSERVATION", "reason", "隔离合成观察人工复核",
                "observedAt", asOf.toString(), "expectedEvaluationChecksum", checksum);
    }
    private static String row(TenantIsolationTestDatabase db, String table, String where, Object... args) {
        return db.admin.queryForObject("SELECT to_jsonb(t)::text FROM " + table + " t WHERE " + where, String.class, args);
    }
    private static void restore(TenantIsolationTestDatabase db, String table, String json) {
        inject(db, "INSERT INTO " + table + " SELECT * FROM jsonb_populate_record(NULL::" + table + ",?::jsonb)", json);
    }
    private static void inject(TenantIsolationTestDatabase db, String sql, Object... args) {
        new TransactionTemplate(new DataSourceTransactionManager(db.admin.getDataSource())).executeWithoutResult(status -> {
            db.admin.execute("SET LOCAL session_replication_role=replica"); db.admin.update(sql, args);
        });
    }
    private static void evidence(String name, Object value) throws Exception {
        Path directory = Path.of("..", "..", "artifacts", "nq-v105-02-forward"); Files.createDirectories(directory);
        Files.writeString(directory.resolve(name), ForwardSimEvaluationFixture.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
    private static void restart(TenantIsolationTestDatabase db, long owner, UUID observation, UUID review, String checksum, Instant asOf) throws Exception {
        Path directory = Path.of("..", "..", "artifacts", "nq-v105-02-forward"); Files.createDirectories(directory);
        Path log = Files.createTempFile(directory, "closed-review-restart-", ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                ForwardSimEvaluationRestartProbeMain.class.getName(), Long.toString(owner), observation.toString(), review.toString(), checksum, asOf.toString());
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

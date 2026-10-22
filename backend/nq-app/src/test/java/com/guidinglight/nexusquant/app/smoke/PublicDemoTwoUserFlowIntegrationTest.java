package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import com.guidinglight.nexusquant.common.tenant.TenantWorkAccess;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 两名用户从真实注册至正式研究发布和原 canonical 执行链；共享行情只有合成批准目录。 */
@EnabledIfSystemProperty(named = "nq.demo.pg.required", matches = "true")
class PublicDemoTwoUserFlowIntegrationTest {
    private static final Instant START = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(5, ChronoUnit.HOURS);
    static final String OPERATIONS = "/api/paper-trading/strategy-sim/continuous-operations";
    private static final String COST = """
            {"quantityStep":"0.0001","priceTick":"0.01","minimumQuantity":"0.0001",
             "minimumNotional":"5","feeRate":"0.001","slippageBps":"10"}
            """;

    @Test void twoRegisteredUsersReachIndependentCanonicalFactsThroughFormalProductCommands() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var local = db.local("demo-flow-fixture");
            String runA, runB;
            try (var app = PublicDemoTestHttp.start(db);
                    var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
                User a = register(db, http, "demo-flow-a"), b = register(db, http, "demo-flow-b");
                assertNotEquals(a.identity().tenantId(), b.identity().tenantId());
                assertNotEquals(a.result().path("accountId").asLong(), b.result().path("accountId").asLong());
                PublicDemoRegistrationHttpIntegrationTest.assertFunding(db, 2);
                var bars = new ArrayList<HistoricalBar>();
                for (int i = 0; i < 5; i++) bars.add(hour(i));
                db.as(local, () -> { app.getBean(MarketdataBarRepository.class).upsertBars(bars, "FIXTURE_SYNC", Instant.now()); return null; });
                UUID dataset = sharedDataset(db, bars);
                String snapshot = db.as(a.identity(), () -> app.getBean(MarketdataDatasetService.class).buildDatasetSnapshot(dataset));
                Chain ca = chain(http, a, dataset, snapshot), cb = chain(http, b, dataset, snapshot);
                for (String suffix : List.of("/api/strategies/tenant-demo-sma/versions/" + cb.version(),
                        "/api/research-configs/" + cb.research(), "/api/backtest-configs/" + cb.config(),
                        "/api/backtest-runs/" + cb.run(), "/api/evaluations/" + cb.evaluation(), "/api/publishes/" + cb.publish())) {
                    int code = http.send("GET", suffix, a.token(), null).statusCode();
                    assertTrue(code == 400 || code == 404, suffix);
                }
                assertRejected(http.send("POST", OPERATIONS, a.token(), create(UUID.randomUUID(), cb.publish())).statusCode());
                assertEquals(0, PublicDemoRegistrationHttpIntegrationTest.count(db, "paper_trading_runs"));
                registerDriver(app);
                String secondPublication = publishNewRun(http, a.token(), ca.config(), ca.version());
                assertNotEquals(ca.publish(), secondPublication);
                UUID operationA = UUID.randomUUID(), contender = UUID.randomUUID();
                var firstRequest = create(operationA, ca.publish()); var secondRequest = create(contender, secondPublication);
                JsonNode createdA;
                try (var workers = Executors.newFixedThreadPool(2)) {
                    var ready = new CountDownLatch(1);
                    var first = workers.submit(() -> { ready.await(); return http.send("POST", OPERATIONS, a.token(), firstRequest); });
                    var second = workers.submit(() -> { ready.await(); return http.send("POST", OPERATIONS, a.token(), secondRequest); });
                    ready.countDown();
                    var x = first.get(40, TimeUnit.SECONDS); var y = second.get(40, TimeUnit.SECONDS);
                    assertEquals(1, (x.statusCode() == 200 ? 1 : 0) + (y.statusCode() == 200 ? 1 : 0),
                            "first binding responses: " + x.statusCode() + " " + x.body() + "; " + y.statusCode() + " " + y.body());
                    assertRejected(x.statusCode() == 200 ? y.statusCode() : x.statusCode());
                    createdA = PublicDemoTestHttp.JSON.readTree(x.statusCode() == 200 ? x.body() : y.body());
                    var acceptedRequest = x.statusCode() == 200 ? firstRequest : secondRequest;
                    assertEquals(createdA, http.ok("POST", OPERATIONS, a.token(), acceptedRequest));
                    var conflict = create(UUID.fromString(createdA.path("operationId").asText()),
                            x.statusCode() == 200 ? secondPublication : ca.publish());
                    assertRejected(http.send("POST", OPERATIONS, a.token(), conflict).statusCode());
                }
                var createdB = http.ok("POST", OPERATIONS, b.token(), create(UUID.randomUUID(), cb.publish()));
                runA = createdA.path("paperRunId").asText(); runB = createdB.path("paperRunId").asText();
                assertEquals(a.result().path("reservedPaperRunId").asText(), runA);
                assertEquals(b.result().path("reservedPaperRunId").asText(), runB);
                assertEquals(2, PublicDemoRegistrationHttpIntegrationTest.count(db, "paper_trading_runs"));
                PublicDemoRegistrationHttpIntegrationTest.assertFunding(db, 2);
                assertEquals(2, db.admin.queryForObject("SELECT count(*) FROM paper_trading_runs p JOIN tenant_sim_accounts d ON d.bound_paper_run_id=p.paper_run_id AND d.account_id=p.canonical_account_id AND d.tenant_id=p.tenant_id", Integer.class));
                var driver = app.getBean(ContinuousSimRunService.class);
                var polledA = driver.pollOnce(runA); var polledB = driver.pollOnce(runB);
                assertEquals(2, app.getBean(PaperMatchingService.class).matchOnce(64),
                        "canonical fill preconditions: " + polledA + "; " + polledB + "; "
                                + db.admin.queryForList("SELECT status FROM strategy_sim_decisions"));
                assertEquals(0, app.getBean(PaperMatchingService.class).matchOnce(64));
                for (var user : List.of(a, b)) {
                    String own = user == a ? runA : runB, foreign = user == a ? runB : runA;
                    var facts = http.ok("GET", "/api/paper-trading/strategy-sim/runs/" + own + "/facts", user.token(), null);
                    assertEquals(1, facts.path("orders").size()); assertEquals(1, facts.path("trades").size());
                    var performance = http.ok("GET", "/api/paper-trading/strategy-sim/analysis/runs/" + own + "/performance", user.token(), null);
                    assertEquals(0, new BigDecimal("10000").compareTo(performance.path("fundingBudget").decimalValue()));
                    BigDecimal pnl = performance.path("pnl").decimalValue();
                    assertEquals(0, performance.path("finalEquity").decimalValue().subtract(new BigDecimal("10000")).compareTo(pnl));
                    assertTrue(pnl.abs().compareTo(new BigDecimal("1000")) < 0, "funding cannot be counted as investment profit");
                    assertEquals(404, http.send("GET", "/api/paper-trading/strategy-sim/runs/" + foreign + "/facts", user.token(), null).statusCode());
                    assertEquals(404, http.send("GET", "/api/paper-trading/strategy-sim/analysis/runs/" + foreign + "/performance", user.token(), null).statusCode());
                    String ownPublication = user == a ? createdA.path("publishId").asText() : cb.publish();
                    String foreignPublication = user == a ? cb.publish() : createdA.path("publishId").asText();
                    var relation = http.ok("GET", "/api/publishes/" + ownPublication + "/sim-runs/page", user.token(), null);
                    assertEquals(1, relation.path("total").asInt());
                    assertEquals(own, relation.path("items").get(0).path("paperRunId").asText());
                    int hidden = http.send("GET", "/api/publishes/" + foreignPublication + "/sim-runs/page", user.token(), null).statusCode();
                    int absent = http.send("GET", "/api/publishes/absent-publication/sim-runs/page", user.token(), null).statusCode();
                    assertEquals(absent, hidden); assertTrue(hidden == 400 || hidden == 404);
                    assertRejected(http.send("POST", OPERATIONS, user.token(), create(UUID.randomUUID(), user == a ? ca.publish() : cb.publish())).statusCode());
                    assertEquals("SIM_RUNNING", http.ok("GET", "/api/demo/registration-result", user.token(), null).path("status").asText());
                }
                PublicDemoRegistrationHttpIntegrationTest.assertFunding(db, 2);
                assertEquals(2, db.admin.queryForObject("SELECT count(*) FROM positions WHERE qty>0", Integer.class));
                assertTrue(TenantContext.currentTenantId().isEmpty());
            }
            var beforeRestart = TenantIsolationHttpIntegrationTest.economicSnapshot(db);
            restartBound(db, runA, runB);
            assertEquals(beforeRestart, TenantIsolationHttpIntegrationTest.economicSnapshot(db));
            PublicDemoRegistrationHttpIntegrationTest.assertFunding(db, 2);
        }
    }

    private static void restartBound(TenantIsolationTestDatabase db, String runA, String runB) throws Exception {
        Path directory = Path.of("target", "demo-registration-evidence").toAbsolutePath(); Files.createDirectories(directory);
        Path arguments = directory.resolve("READ_BOUND.args"), log = directory.resolve("READ_BOUND.log");
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(arguments, "-Duser.timezone=UTC\n-cp\n\"" + cp.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\"\n" + PublicDemoRegistrationRestartProbeMain.class.getName() + "\n");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "@" + arguments);
        builder.environment().put("TASK_DEMO_URL", db.url()); builder.environment().put("TASK_DEMO_USER", db.runtime);
        builder.environment().put("TASK_DEMO_PASSWORD", db.runtimePassword); builder.environment().put("TASK_DEMO_MODE", "READ_BOUND");
        builder.environment().put("TASK_DEMO_RUN_A", runA); builder.environment().put("TASK_DEMO_RUN_B", runB);
        var process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(55, TimeUnit.SECONDS), "registered bound application restart timeout");
            assertEquals(0, process.exitValue(), "bound restart evidence: " + log);
            assertTrue(Files.readString(log).contains("DEMO_BOUND_RESTART_VERIFIED"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    static User register(TenantIsolationTestDatabase db, PublicDemoTestHttp http, String username) throws Exception {
        String email = PublicDemoRegistrationHttpIntegrationTest.email(username);
        http.ok("POST", "/api/auth/register", null, PublicDemoTestHttp.registration(UUID.randomUUID(), username, email));
        http.ok("POST", "/api/auth/verify-email", null, Map.of("token", http.mailToken(email)));
        String token = http.login(username); var result = http.ok("GET", "/api/demo/registration-result", token, null);
        UUID tenant = UUID.fromString(result.path("tenantId").asText());
        long user = db.admin.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
        String code = db.admin.queryForObject("SELECT tenant_code FROM tenants WHERE tenant_id=?", String.class, tenant);
        return new User(new TenantIdentity(new TenantId(tenant), code, TenantKind.DEMO, user), token, result);
    }

    static UUID sharedDataset(TenantIsolationTestDatabase db, List<HistoricalBar> bars) {
        UUID id = UUID.randomUUID();
        db.admin.update("""
                INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,symbol,"interval",start_time,end_time,
                    status,quality_status,bar_count,source,created_by,tenant_id,visibility)
                VALUES(?,'Approved generated hourly test bars','OKX','SPOT','BTC-USDT','1h',?,?,'READY','OK',5,'marketdata_bars','synthetic',NULL,'SHARED_READONLY')
                """, id, Timestamp.from(bars.getFirst().openTime()), Timestamp.from(bars.getLast().closeTime()));
        db.admin.update("""
                INSERT INTO marketdata_dataset_publications(dataset_id,source_reference,license_reference,permitted_from,permitted_until,expires_at,status)
                VALUES(?,'GENERATED_TEST_ONLY','SYNTHETIC_TEST_PERMISSION',?,?,now()+interval '1 hour','APPROVED')
                """, id, Timestamp.from(bars.getFirst().openTime()), Timestamp.from(bars.getLast().closeTime()));
        return id;
    }

    static Chain chain(PublicDemoTestHttp http, User user, UUID dataset, String snapshot) throws Exception {
        return chain(http,user,dataset,snapshot,START);
    }

    /** 当前时钟证明使用独立起点，不让固定历史 feed 的报价时间污染当前估值判断。 */
    static Chain chain(PublicDemoTestHttp http, User user, UUID dataset, String snapshot,Instant start) throws Exception {
        long account = user.result().path("accountId").asLong(); String token = user.token();
        var definition = http.ok("POST", "/api/strategies", token, Map.of("strategyCode", "tenant-demo-sma", "strategyName", "Demo SMA",
                "strategyType", "SPOT_SMA_TARGET_V1", "exchangeCode", "PAPER", "accountId", account, "tradeEnv", "SIM", "configSnapshot", "{}"));
        var version = http.ok("POST", "/api/strategies/tenant-demo-sma/versions", token, Map.of("versionName", "v1", "status", "ACTIVE",
                "paramSnapshotJson", "{\"window\":3,\"investedExposure\":\"1\"}", "configSnapshotJson", "{}", "sourceSnapshotJson", "{\"executable\":\"SPOT_SMA_TARGET_V1\"}"));
        String strategy = definition.path("strategyId").asText(), vid = version.path("strategyVersionId").asText();
        String research = http.ok("POST", "/api/research-configs", token, Map.of("sourceStrategyId", strategy, "name", "Demo research",
                "description", "generated test bars", "parameterSchema", "{}", "parameterDefaults", "{}", "datasetSpec", snapshot)).path("researchConfigId").asText();
        String config = http.ok("POST", "/api/backtest-configs", token, Map.of("researchConfigId", research, "name", "Demo backtest",
                "startTime", start.toString(), "endTime", start.plusSeconds(18000).minusMillis(1).toString(), "initialCapital", "10000", "executionSpec", COST, "evaluationSpec", "{}")).path("backtestConfigId").asText();
        http.ok("PATCH", "/api/backtest-configs/" + config + "/dataset", token, Map.of("datasetId", dataset.toString()));
        http.ok("PATCH", "/api/backtest-configs/" + config + "/strategy-version", token, Map.of("strategyVersionId", vid));
        String run = http.ok("POST", "/api/backtest-runs", token, Map.of("backtestConfigId", config)).path("backtestRunId").asText();
        assertEquals("SUCCEEDED", http.ok("POST", "/api/backtest-runs/" + run + "/start", token, null).path("status").asText());
        String request = "/api/backtest-evaluation-requests/" + UUID.randomUUID();
        var evaluated = http.ok("POST", request + "?target=" + run, token, null);
        assertEquals("SUCCEEDED", evaluated.path("state").asText());
        assertEquals(evaluated, http.ok("GET", request, token, null));
        assertEquals(evaluated, http.ok("POST", request + "?target=" + run, token, null));
        String eval = evaluated.path("result").path("evalReportId").asText();
        String publish = http.ok("POST", "/api/publishes?backtestRunId=" + run, token,
                Map.of("strategyVersionId", vid, "displayName", "Demo publication")).path("publishRecordId").asText();
        return new Chain(vid, research, config, run, eval, publish);
    }

    private static String publishNewRun(PublicDemoTestHttp http, String token, String config, String version) throws Exception {
        String run = http.ok("POST", "/api/backtest-runs", token, Map.of("backtestConfigId", config)).path("backtestRunId").asText();
        assertEquals("SUCCEEDED", http.ok("POST", "/api/backtest-runs/" + run + "/start", token, null).path("status").asText());
        assertEquals("SUCCEEDED", http.ok("POST", "/api/backtest-evaluation-requests/" + UUID.randomUUID() + "?target=" + run, token, null).path("state").asText());
        return http.ok("POST", "/api/publishes?backtestRunId=" + run, token,
                Map.of("strategyVersionId", version, "displayName", "Second candidate")).path("publishRecordId").asText();
    }

    static Map<String, Object> create(UUID operation, String publication) {
        return Map.of("operationId", operation.toString(), "type", "CREATE", "publishId", publication, "budget", "10000");
    }

    static void registerDriver(ServletWebServerApplicationContext app) {
        ClosedBarMarketFeed feed = (first, max) -> {
            Instant now = START.plusSeconds(21661);
            return new ClosedBarMarketFeed.Observation(now, now,
                    List.of(hour(3), hour(4), hour(5)).stream().filter(bar -> !bar.openTime().isBefore(first)).toList(),
                    new ClosedBarMarketFeed.Rule(now, "LIVE", new BigDecimal("0.01"), new BigDecimal("0.0001"), new BigDecimal("0.0001")),
                    new ClosedBarMarketFeed.Quote(now.plusSeconds(1), new BigDecimal("105")));
        };
        var driver = new ContinuousSimRunService(app.getBean(ContinuousSimRepository.class), app.getBean(StrategySimRunService.class),
                app.getBean(PaperTradingRunService.class), feed, app.getBean(JdbcTemplate.class), Clock.fixed(START.plusSeconds(21662), ZoneOffset.UTC));
        ReflectionTestUtils.setField(driver, "tenantWork", app.getBean(TenantWorkAccess.class));
        app.getBeanFactory().registerSingleton("demoFixtureContinuousDriver", driver);
    }

    static HistoricalBar hour(int value) {
        return hour(value,START);
    }

    static HistoricalBar hour(int value,Instant start) {
        Instant open = start.plusSeconds(value * 3600L); BigDecimal price = new BigDecimal(100 + value);
        return new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR, open, open.plusSeconds(3600).minusMillis(1),
                price, price, price, price, BigDecimal.ONE, null, null, "OK", "{}", open.plusSeconds(3600).minusMillis(1));
    }

    private static void assertRejected(int code) { assertTrue(code == 400 || code == 403 || code == 404 || code == 409, "unexpected rejection status " + code); }
    record User(TenantIdentity identity, String token, JsonNode result) {}
    record Chain(String version, String research, String config, String run, String evaluation, String publish) {}
}

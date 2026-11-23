package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 比较正式 HTTP、受租户约束的运行角色和独立 SQL 事实。仅使用隔离库与合成公开行情。 */
@EnabledIfSystemProperty(named = "nq.tenant.pg.required", matches = "true")
class BoundedContinuousSimPostgresIntegrationTest {
    static final String OPS = "/api/paper-trading/strategy-sim/continuous-operations";
    static final String RUNS = "/api/paper-trading/strategy-sim/runs/";
    static final String USER = "experiment-owner";
    static final Duration HOUR = Duration.ofHours(1);

    @Test @Timeout(135)
    void upgradesNonemptyV52AndRetriesAfterConflictingPostgresTransaction() throws Exception {
        try (var db = TenantIsolationTestDatabase.create("35")) {
            TenantIsolationMigrationIntegrationTest.seed(db);
            long stalledAccount = db.admin.queryForObject(
                    "INSERT INTO accounts(account_code,venue) VALUES('bounded-poll-offline','PAPER') RETURNING account_id", Long.class);
            db.admin.update("""
                    INSERT INTO paper_trading_runs(paper_run_id,publish_id,strategy_version_id,status,trade_env,
                      exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)
                    VALUES('stalled-paper','history-publish','history-version','RUNNING','SIM','OKX','SPOT','BTC-USDT',
                      '1h','historical-local',now(),now(),?)
                    """,stalledAccount);
            db.admin.update("""
                    INSERT INTO continuous_sim_runs(paper_run_id,status,strategy_version_id,strategy_checksum,cost_sha256,
                      seed_last_open_time,last_processed_open_time,last_processed_close_time,last_processed_sha256,
                      last_observed_open_time,started_at,updated_at)
                    VALUES('history-paper','STOPPED','history-version',repeat('a',64),repeat('b',64),
                      now(),now(),now()+interval '1 hour',repeat('c',64),now(),now(),now())
                    """);
            UUID historicalOperation = UUID.randomUUID();
            long historicalOwner = db.admin.queryForObject("SELECT id FROM users WHERE username='historical-local'", Long.class);
            // 独立重建 V21 回执的 V1 字节串：每个 UTF-8 段有四字节大端长度，NULL 为 -1。
            String historicalHash = db.admin.queryForObject("""
                    SELECT encode(sha256(string_agg(int4send(coalesce(octet_length(v),-1))
                        ||coalesce(convert_to(v,'UTF8'),''::bytea),''::bytea ORDER BY ordinal)),'hex')
                    FROM unnest(ARRAY['CONTINUOUS_SIM_OPERATION_V1',?,'PAUSE',NULL,NULL,'history-paper']::text[])
                        WITH ORDINALITY AS fields(v,ordinal)
                    """, String.class, Long.toString(historicalOwner));
            db.admin.update("""
                    INSERT INTO continuous_sim_operations(operation_id,owner_user_id,actor,operation_type,paper_run_id,
                        input_sha256,trace_id,result_json,completed_at)
                    VALUES(?,?,'historical-local','PAUSE','history-paper',?,'history-trace',
                        '{"paperRunId":"history-paper","status":"STOPPED","consecutivePollFailures":0}',now())
                    """, historicalOperation, historicalOwner, historicalHash);
            db.migrate("52");
            var facts = ForwardSimEvaluationFixture.allFacts(db);
            var history = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            String cursor = db.admin.queryForObject("SELECT to_jsonb(c)::text FROM continuous_sim_runs c WHERE paper_run_id='history-paper'", String.class);
            var v53 = db.migration("53");
            try (var locked = db.database.source().getConnection(); var lock = locked.createStatement()) {
                locked.setAutoCommit(false);
                lock.execute("LOCK TABLE continuous_sim_runs IN ACCESS EXCLUSIVE MODE");
                assertThrows(FlywayException.class, v53::migrate);
                locked.rollback();
            }
            assertEquals(history, db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank"));
            assertEquals(1, v53.migrate().migrationsExecuted);
            v53.validate();
            assertEquals(0, v53.migrate().migrationsExecuted);
            assertEquals(history, db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int<=52 ORDER BY installed_rank"));
            var after = ForwardSimEvaluationFixture.allFacts(db);
            // 新列使整行 JSON 形状不同；旧游标的每个原列在下方逐项比对。
            facts.remove("continuous_sim_runs");
            after.remove("continuous_sim_runs");
            assertEquals(facts, after);
            JsonNode prior = ForwardSimEvaluationFixture.JSON.readTree(cursor);
            JsonNode upgraded = ForwardSimEvaluationFixture.JSON.readTree(db.admin.queryForObject(
                    "SELECT to_jsonb(c)::text FROM continuous_sim_runs c WHERE paper_run_id='history-paper'", String.class));
            prior.fields().forEachRemaining(field -> assertEquals(field.getValue(), upgraded.get(field.getKey()), field.getKey()));
            assertTrue(upgraded.path("end_at").isNull());
            assertThrows(DataAccessException.class, () -> db.admin.update("""
                    UPDATE continuous_sim_runs SET end_at=date_trunc('hour',now())+interval '1 day',
                      lifecycle_status='RUNNING',final_valuation_status='PENDING' WHERE paper_run_id='history-paper'
                    """));
            // 模拟调度器停机越过整点，此时游标仍是 RUNNING。独立新订单事务直接验证数据库时钟守卫。
            db.runtime();
            var owner = db.local("historical-local");
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                String token = http.login("historical-local");
                var historicalReceipt = http.ok("GET", OPS + "/" + historicalOperation, token, null);
                assertEquals("STOPPED", historicalReceipt.path("result").path("status").asText());
                assertFalse(historicalReceipt.path("result").has("bounded"));
                assertEquals(historicalReceipt, http.ok("POST", OPS, token, Map.of("operationId", historicalOperation,
                        "type", "PAUSE", "paperRunId", "history-paper")));
                assertEquals(1, db.admin.queryForObject("""
                        SELECT count(*) FROM continuous_sim_operations WHERE operation_id=? AND input_sha256=?
                            AND trace_id='history-trace' AND requested_end_at IS NULL AND NOT jsonb_exists(result_json,'bounded')
                        """, Integer.class, historicalOperation, historicalHash));
            }
            db.as(owner, () -> db.jdbc.update("""
                    INSERT INTO continuous_sim_runs(paper_run_id,status,strategy_version_id,strategy_checksum,cost_sha256,
                      seed_last_open_time,last_processed_open_time,last_processed_close_time,last_processed_sha256,
                      last_observed_open_time,started_at,updated_at,end_at,lifecycle_status,final_valuation_status)
                    SELECT 'stalled-paper','RUNNING','history-version',repeat('a',64),repeat('b',64),
                      t-interval '4 hours',t-interval '3 hours',t-interval '2 hours',repeat('c',64),
                      t-interval '3 hours',t-interval '2 hours',t-interval '2 hours',t,'RUNNING','PENDING'
                    FROM (SELECT date_trunc('hour',clock_timestamp()) AS t) boundary
                    """));
            var rejected = assertThrows(DataAccessException.class, () -> db.as(owner, () -> db.jdbc.update("""
                    INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,status,trace_id,venue,exchange_code,trade_env)
                    VALUES('late-order',?,'BTC-USDT','late-client','BUY','LIMIT',100,1,'ACCEPTED','late-trace','PAPER','PAPER','SIM')
                    """,stalledAccount)));
            assertTrue(rejected.getMostSpecificCause().getMessage().contains("CONTINUOUS_SIM_ADMISSION_CLOSED"));
            assertEquals(0,countOrders(db,stalledAccount));
        }
    }

    @Test @Timeout(720)
    void deadlineAndRestartDrainOnePreviouslyAcceptedOrderWithOneFinalMark() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            db.local("bounded-other");
            db.demo("BOUNDED_OTHER", "bounded-demo");
            String run;
            Instant end;
            Map<String, Object> command;
            JsonNode receipt;
            int orders;
            String pendingOrder;
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var server = app.getBean(ResearchSimComparisonFixture.MarketServer.class);
                var driver = app.getBean(ContinuousSimRunService.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                // 首次可执行报价比创建时刻晚一分钟，避免宿主时钟落在整点之后改变第一个槽的语义。
                ReflectionTestUtils.setField(driver, "clock",
                        Clock.fixed(server.end().plus(HOUR).minusSeconds(60), ZoneOffset.UTC));
                Instant from = server.end().plus(HOUR.multipliedBy(3));
                end = from.plus(Duration.ofDays(7));
                command = create(binding.publishId(), end);
                receipt = http.ok("POST", OPS, token, command);
                run = receipt.path("paperRunId").asText();
                assertFalse(run.isEmpty());
                assertEquals(receipt, http.ok("POST", OPS, token, command));
                var conflict = new LinkedHashMap<>(command);
                conflict.put("endAt", end.plus(HOUR).toString());
                assertEquals(409, http.send("POST", OPS, token, conflict).statusCode());
                assertEquals(end, Instant.parse(status(http, token, run).path("endAt").asText()));
                for (String user : List.of("bounded-other", "bounded-demo")) {
                    assertTrue(List.of(403, 404).contains(http.send("GET", RUNS + run + "/continuous", http.login(user), null).statusCode()));
                }
                assertThrows(DataAccessException.class, () -> db.as(owner, () -> db.jdbc.update(
                        "UPDATE continuous_sim_runs SET end_at=end_at+interval '1 hour' WHERE paper_run_id=?", run)));
                var target = new ResearchSimComparisonFixture.Binding(binding.sourceExperimentId(), binding.validationId(),
                        binding.selectedTrialId(), binding.strategyVersionId(), binding.publishId(), run, driver);
                var scenario = new ForwardSimEvaluationFixture.Scenario(target, owner, from, end, end.plusSeconds(8), server);
                ForwardSimEvaluationFixture.clock(app, server.end().plus(HOUR));
                var requests = new ArrayList<String>();
                for (Instant to : List.of(from.plus(Duration.ofDays(1)), end)) {
                    var request = ForwardSimEvaluationFixture.createBody(db, scenario, "FORWARD", from, to);
                    request.put("methodVersion", ClosedBarForwardEvaluationFixture.METHOD);
                    var observation = http.ok("POST", ForwardSimEvaluationFixture.ROOT + "/operations/" + UUID.randomUUID(), token, request);
                    requests.add(observation.path("observation").path("observationId").asText());
                }
                var controls = controls(db);
                for (Instant at = server.end().plus(HOUR); at.isBefore(end); at = at.plus(HOUR)) {
                    configure(app, db, owner, run, at, end, true);
                    db.as(owner, () -> driver.pollOnce(run));
                    if (at.isBefore(end.minus(HOUR))) {
                        db.as(owner, () -> app.getBean(PaperMatchingService.class).matchOnce(100));
                        db.as(owner, () -> driver.pollOnce(run));
                    }
                }
                long account = ForwardSimEvaluationFixture.account(db, run);
                pendingOrder = db.admin.queryForObject("SELECT order_id FROM orders WHERE account_id=? AND status='ACCEPTED'", String.class, account);
                orders = countOrders(db, account);
                configure(app, db, owner, run, end, end, true);
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var a = pool.submit(() -> db.as(owner, () -> driver.pollOnce(run)));
                    var b = pool.submit(() -> db.as(owner, () -> driver.pollOnce(run)));
                    a.get(20, TimeUnit.SECONDS);
                    b.get(20, TimeUnit.SECONDS);
                }
                JsonNode pending = status(http, token, run);
                assertEquals("SETTLING", pending.path("lifecycleStatus").asText(), pending.toString());
                assertTrue(pending.path("pendingOrders").asInt() > 0);
                assertEquals(orders, countOrders(db, account));
                assertEquals(409, http.send("POST", RUNS + run + "/advance", token, null).statusCode());
                assertEquals(409, http.send("POST", OPS, token, operation("RESUME", run)).statusCode());
                assertEquals(controls, controls(db));
                for (String id : requests)
                    assertEquals("OPEN", http.ok("GET", ForwardSimEvaluationFixture.ROOT + "/" + id, token, null).path("observation").path("status").asText());
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM strategy_observation_reviews", Integer.class));
            }
            restart(db, owner, run, end);
            long account = ForwardSimEvaluationFixture.account(db, run);
            assertEquals(orders, countOrders(db, account));
            assertEquals("FILLED", db.admin.queryForObject("SELECT status FROM orders WHERE order_id=?", String.class, pendingOrder));
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM trades WHERE order_id=?", Integer.class, pendingOrder));
            assertEquals(4, db.admin.queryForObject("SELECT count(DISTINCT l.idempotency_key) FROM ledger_entries l JOIN trades t ON l.ref_id=t.trade_id WHERE t.order_id=?", Integer.class, pendingOrder));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time>=?", Integer.class, run, java.sql.Timestamp.from(end)));
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                String token = http.login(USER);
                assertEquals(receipt, http.ok("POST", OPS, token, command));
                var terminal = status(http, token, run);
                assertEquals("COMPLETED", terminal.path("lifecycleStatus").asText(), terminal.toString());
                assertEquals("VERIFIED", terminal.path("finalValuationStatus").asText());
                assertEquals(end, Instant.parse(terminal.path("lastValuationBoundary").asText()));
                ForwardSimEvaluationFixture.clock(app,end.plusSeconds(30));
                String evaluation = ForwardSimEvaluationFixture.evaluationPath(run,end.minus(Duration.ofDays(7)),end,end.plusSeconds(30))
                        + "&methodVersion=" + ClosedBarForwardEvaluationFixture.METHOD;
                var report = http.ok("GET",evaluation,token,null);
                assertEquals("AVAILABLE", report.path("status").asText(), report.path("reason").asText());
                assertEquals(168, report.path("samples").path("observed").asInt());
                assertEquals(169, report.path("methodDetails").path("closedBarValuation").path("gridPointCount").asInt());
                assertEquals(report.path("checksum"), http.ok("GET",evaluation,token,null).path("checksum"));
                var facts = ForwardSimEvaluationFixture.allFacts(db);
                for (int repeat=0; repeat<2; repeat++) {
                    db.as(owner, () -> app.getBean(ContinuousSimRunService.class).pollOnce(run));
                    db.as(owner, () -> app.getBean(PaperMatchingService.class).matchOnce(100));
                }
                assertEquals(facts, ForwardSimEvaluationFixture.allFacts(db));
                assertEquals(terminal, status(http, token, run));
                assertEquals(409, http.send("POST", OPS, token, operation("RESUME", run)).statusCode());
            }
        }
    }

    @Test @Timeout(180)
    void manualEndWinsBeforeDeadlineAndMissingCloseCannotBecomeSuccess() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
                Instant end = now.plus(Duration.ofDays(2));
                var legacy = http.ok("POST", OPS, token, Map.of("type","CREATE","operationId",UUID.randomUUID(),
                        "publishId",binding.publishId(),"budget","10000"));
                String legacyRun = legacy.path("paperRunId").asText();
                var legacyBoundary = status(http, token, legacyRun);
                assertTrue(legacyBoundary.isNull() || legacyBoundary.isMissingNode());
                http.ok("POST", OPS, token, operation("PAUSE", legacyRun));
                http.ok("POST", OPS, token, operation("RESUME", legacyRun));
                http.ok("POST", OPS, token, operation("PAUSE", legacyRun));
                for (Instant invalid : List.of(now, end.plusSeconds(1),end.plus(Duration.ofDays(90)))) {
                    assertTrue(List.of(400,409).contains(http.send("POST", OPS, token, create(binding.publishId(),invalid)).statusCode()));
                }
                String manual = http.ok("POST", OPS, token, create(binding.publishId(),end)).path("paperRunId").asText();
                assertEquals(409, http.send("POST", OPS, token, operation("PAUSE", manual)).statusCode());
                var finish = operation("END",manual);
                var intent = http.ok("POST", OPS, token, finish);
                assertEquals(intent,http.ok("POST", OPS, token, finish));
                assertThrows(DataAccessException.class, () -> db.as(owner, () -> db.jdbc.update(
                        "UPDATE continuous_sim_runs SET lifecycle_status='RUNNING' WHERE paper_run_id=?",manual)));
                db.as(owner, () -> driver.pollOnce(manual));
                assertEquals("MANUALLY_STOPPED",status(http,token,manual).path("lifecycleStatus").asText());
                assertEquals(intent,http.ok("POST", OPS, token, finish));
                assertEquals(409,http.send("POST", OPS, token, operation("RESUME",manual)).statusCode());

                String missed = http.ok("POST", OPS, token, create(binding.publishId(),end)).path("paperRunId").asText();
                var before = ForwardSimEvaluationFixture.allFacts(db);
                configure(app,db,owner,missed,end,end,false);
                // 截止轮询与另一个已持有游标行锁的事务冲突；该轮超时应完整回滚，下一次仍可从原状态收敛。
                try (var held = db.database.source().getConnection();
                        var lock = held.prepareStatement("SELECT 1 FROM continuous_sim_runs WHERE paper_run_id=? FOR UPDATE")) {
                    held.setAutoCommit(false);
                    lock.setString(1, missed);
                    try (var locked = lock.executeQuery()) { assertTrue(locked.next()); }
                    var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
                    var conflict = assertThrows(DataAccessException.class, () -> db.as(owner, () ->
                            transaction.execute(ignored -> {
                                app.getBean(JdbcTemplate.class).execute("SET LOCAL lock_timeout='250ms'");
                                return driver.pollOnce(missed);
                            })));
                    var cause = assertInstanceOf(java.sql.SQLException.class, conflict.getMostSpecificCause());
                    assertEquals("55P03", cause.getSQLState());
                    held.rollback();
                }
                assertEquals(before, ForwardSimEvaluationFixture.allFacts(db));
                db.as(owner, () -> driver.pollOnce(missed));
                assertEquals("UNAVAILABLE",status(http,token,missed).path("finalValuationStatus").asText());
                ReflectionTestUtils.setField(driver,"clock",Clock.fixed(end.plusSeconds(901),ZoneOffset.UTC));
                db.as(owner, () -> driver.pollOnce(missed));
                var result = status(http,token,missed);
                assertEquals("BLOCKED",result.path("lifecycleStatus").asText(),result.toString());
                assertEquals("UNAVAILABLE",result.path("finalValuationStatus").asText());
                assertTrue(result.path("lastValuationBoundary").isNull() || result.path("lastValuationBoundary").isMissingNode());
                assertFalse(result.path("reason").asText("").isBlank());
                assertEquals(0,countOrders(db,ForwardSimEvaluationFixture.account(db,missed)));
                assertEquals(before.get("ledger_entries"),ForwardSimEvaluationFixture.allFacts(db).get("ledger_entries"));
                assertEquals(409,http.send("POST", OPS, token, operation("RESUME",missed)).statusCode());
            }
        }
    }

    private static int countOrders(TenantIsolationTestDatabase db, long account) {
        return db.admin.queryForObject("SELECT count(*) FROM orders WHERE account_id=?", Integer.class, account);
    }

    private static List<Map<String,Object>> controls(TenantIsolationTestDatabase db) {
        return db.admin.queryForList("SELECT job_key,enabled,fixed_delay_ms,version FROM scheduled_job_controls ORDER BY job_key");
    }

    static Map<String,Object> operation(String type, String run) {
        return Map.of("operationId", UUID.randomUUID().toString(), "type", type, "paperRunId", run);
    }

    private static Map<String,Object> create(String publish, Instant end) {
        return Map.of("operationId", UUID.randomUUID().toString(), "type","CREATE","publishId",publish,"budget","10000","endAt",end.toString());
    }

    static JsonNode status(TenantIsolationHttpIntegrationTest.Http http, String token, String run) throws Exception {
        return http.ok("GET", RUNS + run + "/continuous", token, null).path("bounded");
    }

    static void configure(ServletWebServerApplicationContext app, TenantIsolationTestDatabase db,
            TenantIdentity owner, String run, Instant boundary, Instant end, boolean complete) {
        var saved = new HashMap<Instant,HistoricalBar>();
        db.as(owner, () -> app.getBean(ContinuousSimRepository.class).recentBars(run))
                .forEach(bar -> saved.put(bar.openTime(),bar));
        var driver = app.getBean(ContinuousSimRunService.class);
        configure(driver, saved, boundary, end, complete);
    }

    static void configure(ContinuousSimRunService driver, Map<Instant,HistoricalBar> saved,
            Instant boundary, Instant end, boolean complete) {
        Instant server = boundary.plusSeconds(2), received = boundary.plusSeconds(5);
        Clock clock = Clock.fixed(received, ZoneOffset.UTC);
        ClosedBarMarketFeed feed = (first, limit) -> {
            List<HistoricalBar> bars = new ArrayList<>();
            for (Instant open=first; open.isBefore(boundary) && bars.size()<limit; open=open.plus(HOUR)) {
                if (!complete && !open.isBefore(end.minus(HOUR))) break;
                BigDecimal price = open.equals(end.minus(HOUR.multipliedBy(2))) ? new BigDecimal("45000")
                        : BigDecimal.valueOf(52500 + ChronoUnit.HOURS.between(end.minus(Duration.ofDays(8)), open));
                bars.add(saved.getOrDefault(open, ClosedBarForwardEvaluationFixture.bar(open, price, server, received)));
            }
            return new ClosedBarMarketFeed.Observation(server,received,bars,new ClosedBarMarketFeed.Rule(server,
                    "LIVE",new BigDecimal("0.01"),new BigDecimal("0.0001"),new BigDecimal("0.0001")),
                    new ClosedBarMarketFeed.Quote(server.plusSeconds(1),new BigDecimal("52300")));
        };
        ReflectionTestUtils.setField(driver, "feed", feed);
        ReflectionTestUtils.setField(driver, "clock", clock);
    }

    private static void restart(TenantIsolationTestDatabase db, TenantIdentity owner, String run, Instant end) throws Exception {
        Path log = Files.createTempFile("bounded-continuous-restart-", ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(), "-cp",
                System.getProperty("java.class.path"), BoundedContinuousSimRestartProbeMain.class.getName(),
                Long.toString(owner.userId()),run,end.toString());
        builder.environment().put("TASK_BOUNDED_DB_URL",db.url());
        builder.environment().put("TASK_BOUNDED_DB_USER",db.runtime);
        builder.environment().put("TASK_BOUNDED_DB_PASSWORD",db.runtimePassword);
        var child = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(90,TimeUnit.SECONDS), "bounded restart timed out");
            assertEquals(0,child.exitValue(),Files.readString(log));
            assertTrue(Files.readString(log).contains("BOUNDED_RESTART_SETTLED"));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
}

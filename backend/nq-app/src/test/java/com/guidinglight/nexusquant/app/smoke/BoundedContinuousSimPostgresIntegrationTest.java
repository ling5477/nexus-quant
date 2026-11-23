package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationRequest;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxClosedBarMarketFeed;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
            var v53History = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var v54 = db.migration("54");
            try (var locked = db.database.source().getConnection(); var lock = locked.createStatement()) {
                locked.setAutoCommit(false);
                lock.execute("LOCK TABLE continuous_sim_runs IN ACCESS EXCLUSIVE MODE");
                assertThrows(FlywayException.class, v54::migrate);
                locked.rollback();
            }
            assertEquals(v53History, db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank"));
            assertEquals(1, v54.migrate().migrationsExecuted);
            v54.validate();
            assertEquals(0, v54.migrate().migrationsExecuted);
            assertEquals(v53History, db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int<=53 ORDER BY installed_rank"));
            var recovered = ForwardSimEvaluationFixture.JSON.readTree(db.admin.queryForObject(
                    "SELECT to_jsonb(c)::text FROM continuous_sim_runs c WHERE paper_run_id='history-paper'", String.class));
            upgraded.fields().forEachRemaining(field -> assertEquals(field.getValue(), recovered.get(field.getKey()), field.getKey()));
            assertFalse(recovered.required("overlap_check_pending").asBoolean());
            assertTrue(recovered.required("pending_revision_detected_at").isNull());
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
            // 本例没有安装测试时钟，直接用 PG 实际时钟核对延迟调度也能关闭准入。
            var repository = new ContinuousSimRepository(db.jdbc,ForwardSimEvaluationFixture.JSON);
            assertEquals(Boolean.TRUE, db.as(owner, () -> new TransactionTemplate(db.manager)
                    .execute(tx -> repository.closeAdmissionIfDue("stalled-paper"))));
            assertEquals("ADMISSION_CLOSED",db.admin.queryForObject(
                    "SELECT lifecycle_status FROM continuous_sim_runs WHERE paper_run_id='stalled-paper'",String.class));
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
            try (var app = startWithDatabaseClock(db);
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
                long account = ForwardSimEvaluationFixture.account(db, run);
                for (Instant at = server.end().plus(HOUR); at.isBefore(end); at = at.plus(HOUR)) {
                    boolean interruptAcknowledgment = at.equals(end.minus(HOUR));
                    // 原报价遵守闭合槽的及时性合同；单独控制真正准入时的数据库时刻。
                    setDatabaseTime(db, interruptAcknowledgment ? end.minusSeconds(60) : at.plusSeconds(5));
                    if (interruptAcknowledgment) {
                        assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM orders WHERE account_id=? AND status='ACCEPTED'",
                                Integer.class, account));
                        // 在真正的游标更新处中断事务，保留此前正式准入已提交的 Order 和决策。
                        db.admin.execute("""
                                CREATE FUNCTION interrupt_cursor_ack() RETURNS trigger LANGUAGE plpgsql AS $$
                                BEGIN RAISE EXCEPTION 'fixture cursor acknowledgement interrupted'; END; $$
                                """);
                        db.admin.execute("CREATE TRIGGER interrupt_cursor BEFORE UPDATE OF last_processed_open_time ON continuous_sim_runs "
                                + "FOR EACH ROW EXECUTE FUNCTION interrupt_cursor_ack()");
                    }
                    configure(app, db, owner, run, at, end, true);
                    if (interruptAcknowledgment) {
                        assertEquals("STALLED", db.as(owner, () -> driver.pollOnce(run)).status());
                    } else {
                        db.as(owner, () -> driver.pollOnce(run));
                    }
                    if (at.isBefore(end.minus(HOUR))) {
                        db.as(owner, () -> app.getBean(PaperMatchingService.class).matchOnce(100));
                        db.as(owner, () -> driver.pollOnce(run));
                    }
                }
                db.admin.execute("DROP TRIGGER interrupt_cursor ON continuous_sim_runs");
                assertEquals(end.minus(HOUR.multipliedBy(3)), db.admin.queryForObject(
                        "SELECT last_processed_open_time FROM continuous_sim_runs WHERE paper_run_id=?", Timestamp.class, run).toInstant());
                assertEquals(end.minusSeconds(60), db.as(owner, () -> db.jdbc.queryForObject(
                        "SELECT clock_timestamp()", Timestamp.class).toInstant()));
                pendingOrder = db.admin.queryForObject("SELECT order_id FROM orders WHERE account_id=? AND status='ACCEPTED'", String.class, account);
                orders = countOrders(db, account);
                assertEquals(end.minus(HOUR.multipliedBy(2)), db.admin.queryForObject(
                        "SELECT signal_open_time FROM strategy_sim_decisions WHERE order_id=?", Timestamp.class, pendingOrder).toInstant());
                // 关闭持有旧游标的应用。截止轮询和撮合均交给下一 JVM，恢复不能依赖进程内状态。
                assertEquals(409, http.send("POST", RUNS + run + "/advance", token, null).statusCode());
                assertEquals(409, http.send("POST", OPS, token, operation("RESUME", run)).statusCode());
                assertEquals(orders, countOrders(db, account));
                assertEquals(controls, controls(db));
                for (String id : requests)
                    assertEquals("OPEN", http.ok("GET", ForwardSimEvaluationFixture.ROOT + "/" + id, token, null).path("observation").path("status").asText());
                assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM strategy_observation_reviews", Integer.class));
            }
            setDatabaseTime(db, end.plusSeconds(1));
            assertEquals("RUNNING", db.admin.queryForObject(
                    "SELECT lifecycle_status FROM continuous_sim_runs WHERE paper_run_id=?",String.class,run));
            restart(db, owner, run, end);
            long account = ForwardSimEvaluationFixture.account(db, run);
            assertEquals(orders, countOrders(db, account));
            assertEquals("FILLED", db.admin.queryForObject("SELECT status FROM orders WHERE order_id=?", String.class, pendingOrder));
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM trades WHERE order_id=?", Integer.class, pendingOrder));
            assertEquals(4, db.admin.queryForObject("SELECT count(DISTINCT l.idempotency_key) FROM ledger_entries l JOIN trades t ON l.ref_id=t.trade_id WHERE t.order_id=?", Integer.class, pendingOrder));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time>=?", Integer.class, run, java.sql.Timestamp.from(end)));
            assertEquals(end.plusSeconds(1), db.admin.queryForObject(
                    "SELECT ts FROM trades WHERE order_id=?", Timestamp.class, pendingOrder).toInstant());
            assertTrue(db.admin.queryForObject("""
                    SELECT bool_and(l.ts=t.ts AND t.ts>d.execution_open_time)
                    FROM trades t JOIN ledger_entries l ON l.ref_id=t.trade_id JOIN strategy_sim_decisions d ON d.order_id=t.order_id
                    WHERE t.order_id=? AND l.ref_type='TRADE'
                    """, Boolean.class, pendingOrder));
            assertEquals(end.minus(HOUR), db.admin.queryForObject(
                    "SELECT last_processed_open_time FROM continuous_sim_runs WHERE paper_run_id=?", Timestamp.class, run).toInstant());
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
                var inWindow = db.admin.queryForMap("""
                        SELECT count(*) AS n, coalesce(sum(fee),0) AS fees FROM trades
                        WHERE account_id=? AND ts>=? AND ts<?
                        """, account, Timestamp.from(end.minus(Duration.ofDays(7))), Timestamp.from(end));
                assertEquals(((Number) inWindow.get("n")).longValue(), report.path("metrics").required("tradeCount").longValue());
                BigDecimal fees = BigDecimal.ZERO;
                for (JsonNode fee : report.path("metrics").required("fees"))
                    fees = fees.add(new BigDecimal(fee.required("totalFee").asText()));
                assertEquals(0, fees.compareTo((BigDecimal) inWindow.get("fees")));
                BigDecimal finalEquity = db.admin.queryForObject("""
                        SELECT (SELECT sum(delta) FROM ledger_entries WHERE account_id=? AND currency='USDT'
                                  AND (ref_type='SIM_FUNDING_CASH' OR (ref_type='TRADE' AND ts<?
                                      AND idempotency_key IN (ref_id||':LEDGER:1',ref_id||':LEDGER:FEE_1'))))
                             + (SELECT coalesce(sum(CASE WHEN o.side='BUY' THEN t.qty ELSE -t.qty END),0)
                                  FROM trades t JOIN orders o USING(order_id) WHERE t.account_id=? AND t.ts<?)
                               * (SELECT (bar_json->0->>'closePrice')::numeric FROM continuous_sim_bars
                                  WHERE paper_run_id=? AND open_time=?)
                        """, BigDecimal.class,account,Timestamp.from(end),account,Timestamp.from(end),run,Timestamp.from(end.minus(HOUR)));
                assertEquals(0, finalEquity.compareTo(new BigDecimal(report.required("performance").required("finalEquity").asText())));
                assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM trades WHERE account_id=? AND ts>=?",
                        Integer.class, account, Timestamp.from(end)));
                assertFalse(terminal.hasNonNull("manualEndAt"));
                assertTrue(terminal.hasNonNull("terminatedAt"));
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
            try (var app = startWithDatabaseClock(db);
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

                for (boolean hasFinalMark : List.of(false,true)) {
                    ReflectionTestUtils.setField(driver,"clock",Clock.fixed(now,ZoneOffset.UTC));
                    setDatabaseTime(db,now);
                    String late = http.ok("POST", OPS, token, create(binding.publishId(),end)).path("paperRunId").asText();
                    assertFalse(status(http,token,late).hasNonNull("manualEndAt"));
                    assertFalse(status(http,token,late).hasNonNull("terminatedAt"));
                    Instant requested = end.plusSeconds(5);
                    setDatabaseTime(db,requested);
                    ReflectionTestUtils.setField(driver,"clock",Clock.fixed(requested,ZoneOffset.UTC));
                    if (hasFinalMark) {
                        var stored = ClosedBarForwardEvaluationFixture.bar(end.minus(HOUR),new BigDecimal("52300"),
                                end.plusSeconds(2),requested);
                        db.as(owner, () -> {
                            app.getBean(ContinuousSimRepository.class).insertBar(late,stored,requested);
                            return null;
                        });
                    }
                    var reads = new AtomicInteger();
                    ReflectionTestUtils.setField(driver, "feed", (ClosedBarMarketFeed) (first,limit) -> {
                        reads.incrementAndGet();
                        return new ClosedBarMarketFeed.Observation(requested,requested,List.of(),null,null);
                    });
                    http.ok("POST",OPS,token,operation("END",late));
                    var ending = status(http,token,late);
                    assertEquals(requested,Instant.parse(ending.required("manualEndAt").asText()));
                    assertFalse(ending.hasNonNull("terminatedAt"));
                    Instant completed = requested.plusSeconds(2);
                    setDatabaseTime(db,completed);
                    ReflectionTestUtils.setField(driver,"clock",Clock.fixed(completed,ZoneOffset.UTC));
                    db.as(owner, () -> driver.pollOnce(late));
                    var stopped = status(http,token,late);
                    assertEquals("MANUALLY_STOPPED",stopped.path("lifecycleStatus").asText());
                    assertEquals(hasFinalMark ? "VERIFIED" : "UNAVAILABLE",stopped.path("finalValuationStatus").asText());
                    assertEquals(requested,Instant.parse(stopped.required("manualEndAt").asText()));
                    assertEquals(completed,Instant.parse(stopped.required("terminatedAt").asText()));
                    db.as(owner, () -> driver.pollOnce(late));
                    assertEquals(stopped,status(http,token,late));
                    assertEquals(0,reads.get());
                }

                ReflectionTestUtils.setField(driver,"clock",Clock.fixed(now,ZoneOffset.UTC));
                setDatabaseTime(db,now);
                String missed = http.ok("POST", OPS, token, create(binding.publishId(),end)).path("paperRunId").asText();
                setDatabaseTime(db, end);
                var before = ForwardSimEvaluationFixture.allFacts(db);
                configure(app,db,owner,missed,end,end,false);
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(end, ZoneOffset.UTC));
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
                assertEquals("ADMISSION_CLOSED", db.admin.queryForObject(
                        "SELECT lifecycle_status FROM continuous_sim_runs WHERE paper_run_id=?", String.class, missed));
                assertEquals("UNAVAILABLE",status(http,token,missed).path("finalValuationStatus").asText());
                setDatabaseTime(db, end.plusSeconds(901));
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

    @Test @Timeout(180)
    void clockOrderAndInFlightCandleRevisionUseTheDeadlineTransaction() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            try (var app = startWithDatabaseClock(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                var market = app.getBean(ResearchSimComparisonFixture.MarketServer.class);
                Instant end = market.end().plus(HOUR);
                Instant before = end.minusSeconds(1), after = end.plusSeconds(4);
                var tick = new java.util.concurrent.atomic.AtomicReference<>(before);
                Clock clock = org.mockito.Mockito.mock(Clock.class);
                org.mockito.Mockito.when(clock.instant()).thenAnswer(call -> tick.get());
                var feed = new OkxClosedBarMarketFeed("http://127.0.0.1:" + market.server.getAddress().getPort());
                ReflectionTestUtils.setField(feed, "clock", clock);
                market.server.createContext("/api/v5/public/time", ex -> reply(ex,
                        "{\"code\":\"0\",\"data\":[{\"ts\":\"" + tick.get().toEpochMilli() + "\"}]}"));
                market.server.createContext("/api/v5/market/ticker", ex -> reply(ex,
                        "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"last\":\"50000\",\"ts\":\"" + tick.get().toEpochMilli() + "\"}]}"));
                for (boolean revised : List.of(false, true)) {
                    tick.set(before);
                    setDatabaseTime(db, before);
                    ReflectionTestUtils.setField(driver, "clock", Clock.fixed(market.end(), ZoneOffset.UTC));
                    String run = http.ok("POST", OPS, token, create(binding.publishId(),end)).path("paperRunId").asText();
                    var rows = db.as(owner, () -> app.getBean(ContinuousSimRepository.class).recentBars(run));
                    var response = ForwardSimEvaluationFixture.JSON.createObjectNode().put("code","0");
                    var data = response.putArray("data");
                    var overlap = rows.getLast();
                    assertEquals(end.minus(HOUR.multipliedBy(2)), overlap.openTime());
                    for (HistoricalBar bar : rows.subList(rows.size()-2, rows.size())) {
                        BigDecimal close = revised && bar.equals(overlap) ? bar.closePrice().add(new BigDecimal("0.01")) : bar.closePrice();
                        data.addArray().add(Long.toString(bar.openTime().toEpochMilli())).add(bar.openPrice().toPlainString())
                                .add(bar.highPrice().toPlainString()).add(bar.lowPrice().toPlainString()).add(close.toPlainString())
                                .add(bar.volume().toPlainString()).add("1").add(bar.quoteVolume().toPlainString()).add("1");
                    }
                    String digest = ContinuousSimRepository.contentSha(overlap);
                    var crossed = new AtomicInteger();
                    market.server.removeContext("/api/v5/market/history-candles");
                    market.server.createContext("/api/v5/market/history-candles", ex -> {
                        if (ex.getRequestURI().getQuery().endsWith("limit=24")) {
                            crossed.incrementAndGet();
                            setDatabaseTime(db, after);
                            tick.set(after);
                            reply(ex, response.toString());
                        } else {
                            reply(ex, "{\"code\":\"0\",\"data\":[[\"" + end.minus(HOUR).toEpochMilli()
                                    + "\",\"51000\",\"51001\",\"50999\",\"51000\",\"1\",\"1\",\"51000\",\"1\"]]}");
                        }
                    });
                    // JVM 已跨 T 但数据库仍在 T 前时，正式轮询必须仍发出普通行情请求。
                    Clock ahead = org.mockito.Mockito.mock(Clock.class);
                    org.mockito.Mockito.when(ahead.instant()).thenAnswer(call -> tick.get().equals(before) ? end : tick.get());
                    ReflectionTestUtils.setField(driver, "clock", ahead);
                    ReflectionTestUtils.setField(driver, "feed", feed);
                    var result = db.as(owner, () -> driver.pollOnce(run));
                    assertEquals(1, crossed.get(), "JVM ahead must not close the admission before the DB deadline");
                    assertEquals(revised ? "BLOCKED" : "COMPLETED", result.bounded().lifecycleStatus());
                    Timestamp detected = db.admin.queryForObject(
                            "SELECT revision_detected_at FROM continuous_sim_runs WHERE paper_run_id=?",Timestamp.class,run);
                    assertEquals(revised ? after : null, detected == null ? null : detected.toInstant());
                    assertEquals(digest, db.admin.queryForObject("SELECT bar_sha256 FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?",
                            String.class,run,Timestamp.from(overlap.openTime())));
                    assertEquals(0,countOrders(db,ForwardSimEvaluationFixture.account(db,run)));
                    assertEquals(revised ? 0 : 1, db.admin.queryForObject(
                            "SELECT count(*) FROM strategy_sim_decisions WHERE paper_run_id=?", Integer.class, run));
                    // 完整末价相同，只有修订版本应阻断；终态重读不得因可用末价变为成功。
                    Instant exhausted = end.plusSeconds(901);
                    setDatabaseTime(db,exhausted);
                    tick.set(exhausted);
                    var terminal = db.as(owner, () -> driver.pollOnce(run));
                    assertEquals("STOPPED",terminal.status());
                    assertEquals(revised ? "BLOCKED" : "COMPLETED",terminal.bounded().lifecycleStatus());
                    assertEquals(revised ? "UNAVAILABLE" : "VERIFIED",terminal.bounded().finalValuationStatus());
                }
                // 数据库先到 T，JVM 尚未到 T：禁止进入普通行情/策略推进，原始订单准入也应被拒绝。
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(market.end(), ZoneOffset.UTC));
                setDatabaseTime(db,before);
                String slow = http.ok("POST", OPS, token, create(binding.publishId(),end)).path("paperRunId").asText();
                assertAdmissionWaitsAndRejectsAtDeadline(db,owner,slow,end);
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(before, ZoneOffset.UTC));
                ReflectionTestUtils.setField(driver, "feed", new ClosedBarMarketFeed() {
                    public Observation observe(Instant first,int limit) { throw new AssertionError("late ordinary poll"); }
                    public Observation observeFinalBar(Instant first) { return new Observation(end,end,List.of(),null,null); }
                });
                var closed = db.as(owner, () -> driver.pollOnce(slow));
                assertEquals("ADMISSION_CLOSED",closed.bounded().lifecycleStatus());
                assertEquals(0,countOrders(db,ForwardSimEvaluationFixture.account(db,slow)));
            }
        }
    }

    @Test @Timeout(360)
    void crossedDeadlineRevisionSurvivesInterruptedWritesAndSeparateJvm() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            try (var app = startWithDatabaseClock(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                var repository = app.getBean(ContinuousSimRepository.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                Instant end = app.getBean(ResearchSimComparisonFixture.MarketServer.class).end().plus(HOUR);
                Instant before = end.minusSeconds(1);
                Instant seen = end.plusSeconds(4);
                for (String interruptedColumn : List.of("pending_revision_detected_at", "revision_detected_at", "final_valuation_status")) {
                    setDatabaseTime(db, before);
                    var tick = new java.util.concurrent.atomic.AtomicReference<>(before);
                    Clock clock = org.mockito.Mockito.mock(Clock.class);
                    org.mockito.Mockito.when(clock.instant()).thenAnswer(call -> tick.get());
                    ReflectionTestUtils.setField(driver, "clock", clock);
                    String run = http.ok("POST", OPS, token, create(binding.publishId(), end)).path("paperRunId").asText();
                    var original = db.as(owner, () -> repository.recentBars(run)).getLast();
                    assertEquals(end.minus(HOUR.multipliedBy(2)), original.openTime());
                    String digest = ContinuousSimRepository.contentSha(original);
                    var changed = ClosedBarForwardEvaluationFixture.bar(original.openTime(),
                            original.closePrice().add(new BigDecimal("0.01")), end, seen);
                    assertNotEquals(digest, ContinuousSimRepository.contentSha(changed));
                    ReflectionTestUtils.setField(driver, "feed", (ClosedBarMarketFeed) (first, limit) -> {
                        assertEquals(24, limit, "未证实的行情不得补取末价");
                        setDatabaseTime(db, seen);
                        tick.set(seen);
                        return new ClosedBarMarketFeed.Observation(end, seen, List.of(changed), null, null);
                    });
                    db.admin.execute("""
                            CREATE OR REPLACE FUNCTION interrupt_revision_recovery() RETURNS trigger LANGUAGE plpgsql AS $$
                            BEGIN RAISE EXCEPTION 'fixture revision persistence interrupted'; END; $$
                            """);
                    // 只中止目标写入，保留请求前短事务，以及中断前已经提交的证据。
                    String condition = switch (interruptedColumn) {
                        case "pending_revision_detected_at", "revision_detected_at" -> "NEW." + interruptedColumn + " IS NOT NULL";
                        case "final_valuation_status" -> "NEW.status='STOPPED'";
                        default -> throw new AssertionError(interruptedColumn);
                    };
                    db.admin.execute("CREATE TRIGGER interrupt_revision BEFORE UPDATE OF " + interruptedColumn
                            + " ON continuous_sim_runs FOR EACH ROW WHEN (" + condition
                            + ") EXECUTE FUNCTION interrupt_revision_recovery()");
                    var failure = assertThrows(DataAccessException.class, () -> db.as(owner, () -> driver.pollOnce(run)));
                    assertTrue(failure.getMostSpecificCause().getMessage().contains("fixture revision persistence interrupted"));
                    db.admin.execute("DROP TRIGGER interrupt_revision ON continuous_sim_runs");
                    boolean known = !"pending_revision_detected_at".equals(interruptedColumn);
                    boolean promoted = "final_valuation_status".equals(interruptedColumn);
                    assertEquals(!promoted, db.admin.queryForObject(
                            "SELECT overlap_check_pending FROM continuous_sim_runs WHERE paper_run_id=?", Boolean.class, run));
                    assertEquals(known && !promoted ? seen : null, readTime(db, "pending_revision_detected_at", run));
                    assertEquals(promoted ? seen : null, readTime(db, "revision_detected_at", run));
                    assertThrows(DataAccessException.class, () -> db.admin.update("""
                            UPDATE continuous_sim_runs SET lifecycle_status='COMPLETED',status='STOPPED',
                                pending_orders=0,final_valuation_status='VERIFIED',last_valuation_boundary=end_at
                            WHERE paper_run_id=?
                            """, run));
                    restart(db, owner, run, end, "BLOCKED");
                    JsonNode terminal = status(http, token, run);
                    assertEquals("BLOCKED", terminal.required("lifecycleStatus").asText());
                    assertEquals("UNAVAILABLE", terminal.required("finalValuationStatus").asText());
                    assertEquals(known ? "DATA_REVISION_DETECTED" : "OVERLAP_CHECK_INCOMPLETE", terminal.required("reason").asText());
                    assertEquals(known ? seen : null, readTime(db, "revision_detected_at", run));
                    assertNull(readTime(db, "pending_revision_detected_at", run));
                    assertEquals(digest, db.admin.queryForObject(
                            "SELECT bar_sha256 FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?",
                            String.class, run, Timestamp.from(original.openTime())));
                    assertEquals(0, countOrders(db, ForwardSimEvaluationFixture.account(db, run)));
                    var facts = ForwardSimEvaluationFixture.allFacts(db);
                    db.as(owner, () -> driver.pollOnce(run));
                    assertEquals(facts, ForwardSimEvaluationFixture.allFacts(db));
                    assertEquals(terminal, status(http, token, run));
                }
            }
        }
    }

    @Test @Timeout(240)
    void manualEndWinsAfterOldCursorAndRejectsAnInFlightFinalResponse() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            try (var app = startWithDatabaseClock(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort());
                    var workers = Executors.newFixedThreadPool(2)) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                Instant end = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(2));
                Instant receivedAt = end.plusSeconds(5);
                for (boolean requestInFlight : List.of(false, true)) {
                    setDatabaseTime(db, end.minusSeconds(60));
                    ReflectionTestUtils.setField(driver, "clock", Clock.fixed(end.minusSeconds(60), ZoneOffset.UTC));
                    String run = http.ok("POST", OPS, token, create(binding.publishId(), end)).path("paperRunId").asText();
                    setDatabaseTime(db, receivedAt);
                    var reached = new CountDownLatch(1);
                    var proceed = new CountDownLatch(1);
                    var reads = new AtomicInteger();
                    var clock = org.mockito.Mockito.mock(Clock.class);
                    var samples = new AtomicInteger();
                    org.mockito.Mockito.when(clock.instant()).thenAnswer(call -> {
                        if (!requestInFlight && samples.incrementAndGet() == 2) {
                            // 第二次取时发生在到期路径已读取游标、准备验证末价时。
                            reached.countDown();
                            await(proceed);
                        }
                        return receivedAt;
                    });
                    ReflectionTestUtils.setField(driver, "clock", clock);
                    ReflectionTestUtils.setField(driver, "feed", new ClosedBarMarketFeed() {
                        public Observation observe(Instant first, int max) { throw new AssertionError("ordinary poll after deadline"); }
                        public Observation observeFinalBar(Instant open) {
                            reads.incrementAndGet();
                            reached.countDown();
                            await(proceed);
                            return new Observation(end, receivedAt, List.of(ClosedBarForwardEvaluationFixture.bar(
                                    open, new BigDecimal("52300"), end, receivedAt)), null, null);
                        }
                    });
                    var before = ForwardSimEvaluationFixture.allFacts(db);
                    var poll = workers.submit(() -> db.as(owner, () -> driver.pollOnce(run)));
                    var command = operation("END", run);
                    try {
                        await(reached);
                        var endRequest = workers.submit(() -> http.ok("POST", OPS, token, command));
                        if (requestInFlight) {
                            // 末价请求先进入，确认真实 END 事务在等待同一行锁。
                            waitForEndLock(db);
                            assertFalse(endRequest.isDone());
                            proceed.countDown();
                        }
                        var receipt = endRequest.get(12, TimeUnit.SECONDS);
                        assertEquals(receipt, http.ok("POST", OPS, token, command));
                    } finally {
                        proceed.countDown();
                    }
                    poll.get(15, TimeUnit.SECONDS);
                    restart(db, owner, run, end, "MANUALLY_STOPPED");
                    var terminal = status(http, token, run);
                    assertEquals("MANUALLY_STOPPED", terminal.required("lifecycleStatus").asText());
                    assertEquals("UNAVAILABLE", terminal.required("finalValuationStatus").asText());
                    assertEquals(requestInFlight ? 1 : 0, reads.get());
                    assertEquals(0, db.admin.queryForObject("""
                            SELECT count(*) FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?
                            """, Integer.class, run, Timestamp.from(end.minus(HOUR))));
                    var after = ForwardSimEvaluationFixture.allFacts(db);
                    for (String table : List.of("orders", "trades", "ledger_entries"))
                        assertEquals(before.get(table), after.get(table), table);
                    http.ok("POST", OPS, token, operation("END", run));
                    assertEquals(terminal, status(http, token, run));
                }
                setDatabaseTime(db, end.minusSeconds(60));
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(end.minusSeconds(60), ZoneOffset.UTC));
                String interrupted = http.ok("POST", OPS, token, create(binding.publishId(), end)).path("paperRunId").asText();
                setDatabaseTime(db, receivedAt);
                http.ok("POST", OPS, token, operation("END", interrupted));
                assertFalse(status(http, token, interrupted).hasNonNull("terminatedAt"));
                restart(db, owner, interrupted, end, "MANUALLY_STOPPED");
                assertEquals("UNAVAILABLE", status(http, token, interrupted).required("finalValuationStatus").asText());
            }
        }
    }

    @Test @Timeout(130)
    void databaseSamplesTerminationAfterCompetingEndCommits() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort());
                    var workers = Executors.newSingleThreadExecutor()) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                var repository = app.getBean(ContinuousSimRepository.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                Instant start = actualDatabaseTime(db);
                Instant end = start.truncatedTo(ChronoUnit.HOURS).minus(HOUR);
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(end.minusSeconds(60), ZoneOffset.UTC));
                String run = http.ok("POST", OPS, token, create(binding.publishId(), end)).path("paperRunId").asText();
                var attempted = new CompletableFuture<Integer>();
                var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
                var command = operation("END", run);
                var receipt = db.as(owner, () -> transaction.execute(tx -> {
                    JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
                    jdbc.queryForObject("SELECT paper_run_id FROM continuous_sim_runs WHERE paper_run_id=? FOR UPDATE", String.class, run);
                    var termination = workers.submit(() -> db.as(owner, () -> transaction.execute(other -> {
                        attempted.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        repository.recordSettlement(run, "BLOCKED", "UNAVAILABLE", 0, "CLOSING_PRICE_UNAVAILABLE", true);
                        return Boolean.TRUE;
                    })));
                    try {
                        waitForLock(db, attempted.get(5, TimeUnit.SECONDS));
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                    assertFalse(termination.isDone());
                    // 同一事务作为正式 END 的竞争者先取得锁并持久化人工意图。
                    var result = app.getBean(ContinuousSimOperationService.class)
                            .submit(new ContinuousSimOperationRequest(
                                    UUID.fromString(command.get("operationId").toString()),
                                    ContinuousSimOperationRequest.Type.END, null, null, run),
                                    owner.userId(), USER, "bounded-real-db-time");
                    assertEquals(run, result.paperRunId());
                    return termination;
                }));
                assertTrue(receipt.get(15, TimeUnit.SECONDS));
                Instant requested = readTime(db, "manual_end_at", run);
                Instant terminated = readTime(db, "updated_at", run);
                Instant finished = actualDatabaseTime(db);
                assertFalse(requested.isBefore(start));
                assertFalse(terminated.isBefore(requested));
                assertFalse(terminated.isAfter(finished));
                assertEquals(terminated, db.admin.queryForObject(
                        "SELECT stopped_at FROM paper_trading_runs WHERE paper_run_id=?", Timestamp.class, run).toInstant());
                var terminal = status(http, token, run);
                assertEquals("MANUALLY_STOPPED", terminal.required("lifecycleStatus").asText());
                assertEquals(requested, Instant.parse(terminal.required("manualEndAt").asText()));
                assertEquals(terminated, Instant.parse(terminal.required("terminatedAt").asText()));
                var replay = http.ok("POST", OPS, token, command);
                assertEquals(replay, http.ok("GET", OPS + "/" + command.get("operationId"), token, null));
                assertEquals("bounded-real-db-time", db.admin.queryForObject(
                        "SELECT trace_id FROM continuous_sim_operations WHERE operation_id=?", String.class,
                        UUID.fromString(command.get("operationId").toString())));
                assertEquals(terminal, status(http, token, run));
                System.out.println("BOUNDED_TERMINATION_DATABASE_TIMES manualEndAt=" + requested
                        + " terminatedAt=" + terminated + " stoppedAt=" + terminated);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "fixture barrier timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void waitForEndLock(TenantIsolationTestDatabase db) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Boolean.TRUE.equals(db.admin.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                    AND query LIKE 'SELECT nq_request_continuous_end%' AND cardinality(pg_blocking_pids(pid))>0)
                """, Boolean.class))) {
            if (System.nanoTime() >= deadline) fail("END did not reach the cursor lock");
            Thread.sleep(15);
        }
    }

    private static void waitForLock(TenantIsolationTestDatabase db, int backend) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!db.admin.queryForObject("SELECT cardinality(pg_blocking_pids(?))>0", Boolean.class, backend)) {
            if (System.nanoTime() >= deadline) fail("transaction did not reach the cursor lock");
            Thread.sleep(15);
        }
    }

    private static Instant actualDatabaseTime(TenantIsolationTestDatabase db) {
        return db.admin.queryForObject("SELECT pg_catalog.clock_timestamp()", Timestamp.class).toInstant();
    }

    private static Instant readTime(TenantIsolationTestDatabase db, String column, String run) {
        if (!List.of("pending_revision_detected_at", "revision_detected_at", "manual_end_at", "updated_at").contains(column))
            throw new IllegalArgumentException(column);
        Timestamp value = db.admin.queryForObject("SELECT " + column + " FROM continuous_sim_runs WHERE paper_run_id=?",
                Timestamp.class, run);
        return value == null ? null : value.toInstant();
    }

    private static void reply(HttpExchange exchange, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200,data.length);
        try (var out = exchange.getResponseBody()) { out.write(data); }
    }

    private static void assertAdmissionWaitsAndRejectsAtDeadline(TenantIsolationTestDatabase db,
            TenantIdentity owner,String run,Instant end) throws Exception {
        long account = ForwardSimEvaluationFixture.account(db,run);
        try (var holding = db.database.source().getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            holding.setAutoCommit(false);
            try (var lock=holding.prepareStatement("SELECT * FROM continuous_sim_runs WHERE paper_run_id=? FOR UPDATE")) {
                lock.setString(1,run);
                try (var result=lock.executeQuery()) { assertTrue(result.next()); }
            }
            try {
                var pid = new CompletableFuture<Integer>();
                var admission = pool.submit(() -> assertThrows(DataAccessException.class, () -> db.as(owner, () ->
                        new TransactionTemplate(db.manager).execute(tx -> {
                            db.jdbc.execute("SET LOCAL lock_timeout='8s'");
                            pid.complete(db.jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            return db.jdbc.update("""
                                    INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,status,trace_id,venue,exchange_code,trade_env)
                                    VALUES('race-order',?,'BTC-USDT','race-client','BUY','LIMIT',100,1,'ACCEPTED','race','PAPER','PAPER','SIM')
                                    """,account);
                        }))));
                int backend = pid.get(3,TimeUnit.SECONDS);
                long until = System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
                while (!db.admin.queryForObject("SELECT cardinality(pg_blocking_pids(?))>0",Boolean.class,backend)) {
                    if (System.nanoTime() >= until) fail("order admission did not reach the cursor lock");
                    Thread.sleep(15);
                }
                setDatabaseTime(db,end);
                holding.commit();
                assertTrue(admission.get(5,TimeUnit.SECONDS).getMostSpecificCause().getMessage().contains("CONTINUOUS_SIM_ADMISSION_CLOSED"));
            } finally { holding.rollback(); }
        }
    }

    /** 仅让临时库的专属运行角色看到受控时钟；真实 PG 对照仍在 V52 升级用例中执行。 */
    static ServletWebServerApplicationContext startWithDatabaseClock(TenantIsolationTestDatabase db) {
        String name = db.admin.queryForObject("SELECT current_database()", String.class);
        assertTrue(name.matches("nq_baseline_test_[a-f0-9]{32}"));
        db.admin.execute("CREATE SCHEMA bounded_test_clock");
        db.admin.execute("CREATE TABLE bounded_test_clock.tick(at_time timestamptz NOT NULL)");
        db.admin.execute("INSERT INTO bounded_test_clock.tick VALUES (pg_catalog.clock_timestamp())");
        db.admin.execute("""
                CREATE FUNCTION bounded_test_clock.clock_timestamp() RETURNS timestamptz
                LANGUAGE sql VOLATILE AS 'SELECT at_time FROM bounded_test_clock.tick'
                """);
        db.admin.execute("GRANT USAGE ON SCHEMA bounded_test_clock TO " + db.runtime);
        db.admin.execute("GRANT SELECT ON bounded_test_clock.tick TO " + db.runtime);
        db.admin.execute("ALTER ROLE " + db.runtime + " IN DATABASE " + name
                + " SET search_path TO bounded_test_clock, pg_catalog, public");
        // Hikari 可能尚未建立连接；角色设置只在新连接上生效。
        if (db.pool.getHikariPoolMXBean() != null) db.pool.getHikariPoolMXBean().softEvictConnections();
        assertEquals("bounded_test_clock", db.jdbc.queryForObject("SELECT current_schema()", String.class));
        assertEquals(db.admin.queryForObject("SELECT at_time FROM bounded_test_clock.tick",Timestamp.class),
                db.jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class));
        return ResearchSimComparisonFixture.start(db);
    }

    static void setDatabaseTime(TenantIsolationTestDatabase db, Instant time) {
        assertEquals(1, db.admin.update("UPDATE bounded_test_clock.tick SET at_time=?", Timestamp.from(time)));
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
        Instant server = boundary.equals(end) ? end : boundary.plusSeconds(2);
        Instant received = boundary.equals(end) ? end.plusSeconds(1) : boundary.plusSeconds(5);
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
        restart(db, owner, run, end, null);
    }

    private static void restart(TenantIsolationTestDatabase db, TenantIdentity owner, String run, Instant end, String terminal) throws Exception {
        Path log = Files.createTempFile("bounded-continuous-restart-", ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(), "-cp",
                System.getProperty("java.class.path"), BoundedContinuousSimRestartProbeMain.class.getName(),
                Long.toString(owner.userId()),run,end.toString());
        if (terminal != null) builder.command().add(terminal);
        builder.environment().put("TASK_BOUNDED_DB_URL",db.url());
        builder.environment().put("TASK_BOUNDED_DB_USER",db.runtime);
        builder.environment().put("TASK_BOUNDED_DB_PASSWORD",db.runtimePassword);
        var child = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(90,TimeUnit.SECONDS), "bounded restart timed out");
            assertEquals(0,child.exitValue(),Files.readString(log));
            String expected = terminal == null ? "BOUNDED_RESTART_SETTLED" : "BOUNDED_RESTART_" + terminal;
            var marker = Files.readAllLines(log).stream().filter(line -> line.startsWith(expected)).findFirst();
            assertTrue(marker.isPresent(), log.toString());
            System.out.println(marker.orElseThrow());
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
}

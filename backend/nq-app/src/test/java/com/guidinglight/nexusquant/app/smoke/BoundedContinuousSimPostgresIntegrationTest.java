package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.ClosedBarMarketFeed;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationRequest;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimOperationService;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRepository;
import com.guidinglight.nexusquant.scheduler.paper.ContinuousSimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRecoveryService;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRepository;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest @ValueSource(booleans = {true, false}) @Timeout(180)
    void upgradesV56RunWithoutInferringAnOriginalEndFromReceipts(boolean historicalEnd) throws Exception {
        try (var db = TenantIsolationTestDatabase.create("56")) {
            db.runtime();
            var owner = db.local(USER);
            String run;
            Instant end;
            UUID endId = UUID.randomUUID();
            LegacyRecovery legacy = null;
            try (var app = startWithDatabaseClock(db)) {
                Instant start = app.getBean(ResearchSimComparisonFixture.MarketServer.class).end();
                end = start.plus(HOUR);
                setDatabaseTime(db, start);
                var paper = app.getBean(PaperTradingRunService.class);
                var runs = app.getBean(ContinuousSimRepository.class);
                run = seedBoundedHistory(db, app, owner, start, end);
                Instant requestAt = end.plusSeconds(5);
                setDatabaseTime(db, requestAt);
                db.as(owner, () -> {
                    runs.insertBar(run, ClosedBarForwardEvaluationFixture.bar(end.minus(HOUR), new BigDecimal("52300"), end, requestAt),
                            requestAt);
                    return null;
                });
                if (historicalEnd) {
                    // 父 stop 首先写入人工时间，后续 V56 END 返回同一时间，但没有改变原意图。
                    assertEquals("RUNNING", db.as(owner, () -> paper.stop(run)).status().name());
                    assertEquals(requestAt, readTime(db, "manual_end_at", run));
                    legacy = saveV56MisassociatedRecovery(db, app, owner, run, endId, requestAt);
                }
            }
            var history = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var before = historicalEconomics(db);
            var v57 = db.migration("57");
            assertEquals(1, v57.migrate().migrationsExecuted);
            v57.validate();
            assertNull(db.admin.queryForObject("SELECT terminal_intent_id FROM continuous_sim_runs WHERE paper_run_id=?", UUID.class, run));
            if (historicalEnd) assertEquals(legacy.row(), recoveryRow(db, legacy.operationId()));
            db.migrate("58");
            assertEquals(history, db.admin.queryForList(
                    "SELECT version,checksum FROM flyway_schema_history WHERE version::int<=56 ORDER BY installed_rank"));
            assertEquals(before, historicalEconomics(db));
            db.grantRuntimePrivileges(db.runtime);

            String path = RUNS + run + "/projection-recovery";
            Map<String, String> recheck;
            try (var app = ResearchSimComparisonFixture.start(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                String token = http.login(USER);
                var endCommand = Map.of("operationId", endId, "type", "END", "paperRunId", run);
                long account = ForwardSimEvaluationFixture.account(db, run);
                var projections = projectionRows(db, account);
                var endReceipt = http.ok("POST", OPS, token, endCommand);
                assertEquals(endReceipt, http.ok("GET", OPS + "/" + endId, token, null));
                assertEquals(end.plusSeconds(5).toString(), endReceipt.required("result").required("bounded").required("manualEndAt").asText());
                var driver = app.getBean(ContinuousSimRunService.class);
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(end.plusSeconds(6), ZoneOffset.UTC));
                if (historicalEnd) {
                    assertEquals(legacy.endRow(), historicalEndRow(db, endId));
                    var additionalEnd = http.ok("POST", OPS, token, operation("END", run));
                    assertEquals(endReceipt.required("result").required("bounded").required("manualEndAt"),
                            additionalEnd.required("result").required("bounded").required("manualEndAt"));
                    var refusedPreview = http.send("GET", path, token, null);
                    assertEquals(409, refusedPreview.statusCode(), refusedPreview.body());
                    assertTrue(refusedPreview.body().contains("SIM_PROJECTION_END_INTENT_UNPROVEN"), refusedPreview.body());
                    var replay = http.ok("POST", path, token, legacy.request());
                    assertEquals(replay, http.ok("GET", path + "/operations/" + legacy.operationId(), token, null));
                    var fresh = new HashMap<>(legacy.request());
                    fresh.put("operationId", UUID.randomUUID().toString());
                    assertConflict(http, path, token, fresh, "SIM_PROJECTION_END_INTENT_UNPROVEN");
                    recheck = Map.of("operationId", UUID.randomUUID().toString(), "endOperationId", endId.toString(),
                            "recoveryOperationId", legacy.operationId().toString());
                    assertConflict(http, path + "/finalization", token, recheck, "SIM_PROJECTION_END_INTENT_UNPROVEN");
                    db.as(owner, () -> driver.pollOnce(run));
                    var blocked = status(http, token, run);
                    assertEquals("BLOCKED", blocked.required("lifecycleStatus").asText());
                    assertEquals("UNAVAILABLE", blocked.required("finalValuationStatus").asText());
                    assertEquals("SIM_PROJECTION_END_INTENT_UNPROVEN", blocked.required("reason").asText());
                    assertFalse(blocked.hasNonNull("terminatedAt"));
                    assertEquals(projections, projectionRows(db, account));
                    assertEquals(before, historicalEconomics(db));
                } else {
                    assertEquals(endId, db.admin.queryForObject("""
                            SELECT c.terminal_intent_id FROM continuous_sim_runs c JOIN continuous_sim_operations e
                                ON c.paper_run_id=e.paper_run_id AND c.terminal_intent_id=e.terminal_intent_id
                            WHERE c.paper_run_id=? AND c.terminal_intent_source='MANUAL_END' AND e.operation_id=c.terminal_intent_id
                            """, UUID.class, run));
                    damageCashProjection(db, owner, account);
                    db.as(owner, () -> driver.pollOnce(run));
                    var preview = http.ok("GET", path, token, null);
                    assertEquals(endId.toString(), preview.required("endOperationId").asText());
                    var recovered = http.ok("POST", path, token, projectionRecoveryRequest(preview));
                    recheck = Map.of("operationId", UUID.randomUUID().toString(), "endOperationId", endId.toString(),
                            "recoveryOperationId", recovered.required("operationId").asText());
                    var finalized = http.ok("POST", path + "/finalization", token, recheck).required("finalization");
                    assertEquals("MANUALLY_STOPPED", finalized.required("lifecycleStatus").asText());
                    assertEquals("VERIFIED", finalized.required("finalValuationStatus").asText());
                }
            }
            if (historicalEnd) {
                var child = projectionProcess(db, run, "legacy-unproven", recheck);
                try {
                    assertTrue(child.process().waitFor(75, TimeUnit.SECONDS));
                    String output = Files.readString(child.log());
                    assertEquals(0, child.process().exitValue(), output);
                    assertTrue(output.contains("LEGACY_END_RESTART_UNPROVEN"), output);
                    System.out.println(output.lines().filter(line -> line.startsWith("LEGACY_END_RESTART_UNPROVEN")).findFirst().orElseThrow());
                } finally { if (child.process().isAlive()) child.process().destroyForcibly(); }
                assertEquals(legacy.row(), recoveryRow(db, legacy.operationId()));
                assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM sim_account_projection_operations", Integer.class));
                assertNull(db.admin.queryForObject("SELECT terminal_intent_id FROM continuous_sim_runs WHERE paper_run_id=?", UUID.class, run));
                assertEquals("RUNNING", db.admin.queryForObject("SELECT status FROM paper_trading_runs WHERE paper_run_id=?", String.class, run));
                assertEquals(before, historicalEconomics(db));
            }
        }
    }

    private static LegacyRecovery saveV56MisassociatedRecovery(TenantIsolationTestDatabase db,
            ServletWebServerApplicationContext app, TenantIdentity owner, String run, UUID endId, Instant at) {
        long account = ForwardSimEvaluationFixture.account(db, run);
        UUID operationId = UUID.randomUUID();
        var command = new ContinuousSimOperationRequest(endId, ContinuousSimOperationRequest.Type.END, null, null, run);
        var json = app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        db.as(owner, () -> new TransactionTemplate(db.manager).execute(tx -> {
            assertTrue(db.jdbc.queryForObject("SELECT nq_request_continuous_end(?,?)", Boolean.class, run, Timestamp.from(at)));
            var bounded = new ContinuousSimRunService.Bounded(at.minusSeconds(5), "ADMISSION_CLOSED", "PENDING",
                    null, null, null, at, null, null, at, null);
            var status = new ContinuousSimRunService.Status(run, "RUNNING", at.minus(HOUR), null, null, null,
                    null, null, null, null, null, null, 0, bounded);
            assertEquals(1, db.jdbc.update("""
                    INSERT INTO continuous_sim_operations(operation_id,owner_user_id,actor,operation_type,paper_run_id,
                        input_sha256,trace_id,result_json,completed_at)
                    VALUES(?,?,?,'END',?,?,'v56-end',?::jsonb,?)
                    """, endId, owner.userId(), USER, run, command.fingerprint(owner.userId()),
                    json.valueToTree(status).toString(), Timestamp.from(at)));
            return null;
        }));
        assertEquals(at, readTime(db, "manual_end_at", run));
        damageCashProjection(db, owner, account);
        db.as(owner, () -> db.jdbc.update("""
                UPDATE continuous_sim_runs SET status='STALLED',lifecycle_status='BLOCKED',
                    final_valuation_status='UNAVAILABLE',pending_orders=0,block_reason='SIM_CASH_PROJECTION_MISMATCH'
                WHERE paper_run_id=?
                """, run));
        // 按 V56 的锁、投影恢复和回执约束提交；原 END UUID 是当时可被误认的唯一同时间回执。
        var request = db.as(owner, () -> new TransactionTemplate(db.manager).execute(tx -> {
            var projections = app.getBean(SimAccountProjectionRepository.class);
            ReflectionTestUtils.invokeMethod(projections, "lock", account);
            var expected = ReflectionTestUtils.invokeMethod(projections, "expected", run, account, new BigDecimal("10000"));
            var actual = ReflectionTestUtils.invokeMethod(projections, "actual", account, false);
            String version = ReflectionTestUtils.invokeMethod(actual, "version");
            SimAccountProjectionRepository.Watermark watermark = ReflectionTestUtils.invokeMethod(expected, "watermark");
            SimAccountProjectionRecoveryService.Differences before = ReflectionTestUtils.invokeMethod(
                    SimAccountProjectionRecoveryService.class, "compare", expected, actual);
            assertFalse(before.cashConsistent());
            ReflectionTestUtils.invokeMethod(projections, "restore", account, expected, actual, at, "v56-recovery");
            SimAccountProjectionRecoveryService.Differences after = ReflectionTestUtils.invokeMethod(
                    SimAccountProjectionRecoveryService.class, "compare", expected,
                    ReflectionTestUtils.invokeMethod(projections, "actual", account, false));
            assertTrue(after.cashConsistent() && after.positionConsistent());
            var receipt = new SimAccountProjectionRecoveryService.Receipt(operationId, endId, null, account,
                    watermark, before, after, "CONSISTENT", "CONSISTENT", "UNCHANGED", null, at);
            String identity = owner.tenantId().value() + "/" + account + "/" + owner.userId();
            String input = identity + "/recover/" + endId + "/" + watermark.sha256() + "/" + version;
            String hash = db.jdbc.queryForObject("SELECT encode(sha256(convert_to(?,'UTF8')),'hex')", String.class, input);
            assertEquals(1, db.jdbc.update("""
                    INSERT INTO sim_account_projection_operations(operation_id,tenant_id,account_id,paper_run_id,
                        owner_user_id,actor,trace_id,end_operation_id,request_sha256,ledger_watermark,result_json,recorded_at)
                    VALUES(?,?,?,?,?,?,'v56-recovery',?,?,?,?::jsonb,?)
                    """, operationId, owner.tenantId().value(), account, run, owner.userId(), USER, endId, hash,
                    watermark.sha256(), json.valueToTree(receipt).toString(), Timestamp.from(at)));
            return Map.of("operationId", operationId.toString(), "endOperationId", endId.toString(),
                    "ledgerWatermark", watermark.sha256(), "projectionVersion", version);
        }));
        return new LegacyRecovery(operationId, request, recoveryRow(db, operationId), historicalEndRow(db, endId));
    }

    private static Map<String, String> historicalEconomics(TenantIsolationTestDatabase db) {
        var facts = ForwardSimEvaluationFixture.allFacts(db);
        facts.remove("continuous_sim_runs");
        return facts;
    }

    private static String historicalEndRow(TenantIsolationTestDatabase db, UUID operationId) {
        return db.admin.queryForObject("""
                SELECT (to_jsonb(e)-'terminal_intent_id')::text FROM continuous_sim_operations e WHERE operation_id=?
                """, String.class, operationId);
    }

    private static String recoveryRow(TenantIsolationTestDatabase db, UUID operationId) {
        return db.admin.queryForObject("SELECT to_jsonb(r)::text FROM sim_account_projection_operations r WHERE operation_id=?",
                String.class, operationId);
    }

    private record LegacyRecovery(UUID operationId, Map<String, String> request, String row, String endRow) {}

    private static String seedBoundedHistory(TenantIsolationTestDatabase db, ServletWebServerApplicationContext app,
            TenantIdentity owner, Instant start, Instant end) {
        var publication = ResearchSimComparisonFixture.seedPublication(app, db, owner, true, "SPOT_SMA_TARGET_V1");
        var sim = app.getBean(StrategySimRunService.class);
        var paper = app.getBean(PaperTradingRunService.class);
        var runs = app.getBean(ContinuousSimRepository.class);
        // 使用正式发布、创建和注资服务，同时允许夹具运行在当前操作回执 schema 之前。
        return db.as(owner, () -> new TransactionTemplate(db.manager).execute(tx -> {
            var created = sim.create(publication.publishId(), new BigDecimal("10000"), USER).paperRunId();
            var seed = sim.continuousSeed(created);
            paper.start(created);
            runs.create(created, seed.strategyVersionId(), seed.strategyChecksum(), seed.costSha256(), seed.bars(), start, end);
            return created;
        }));
    }

    @Test @Timeout(75)
    void v57RetryWithoutRowScanEvidenceRemainsBlockedAfterMigration() throws Exception {
        try (var db = TenantIsolationTestDatabase.create("57")) {
            db.runtime();
            var owner = db.local(USER);
            String run;
            HistoricalBar earlier;
            HistoricalBar last;
            try (var app = ResearchSimComparisonFixture.start(db)) {
                Instant start = app.getBean(ResearchSimComparisonFixture.MarketServer.class).end();
                run = seedBoundedHistory(db, app, owner, start, start.plus(HOUR));
                var bars = db.as(owner, () -> app.getBean(ContinuousSimRepository.class).recentBars(run));
                earlier = bars.get(bars.size() - 2);
                last = bars.getLast();
            }
            Instant first = earlier.openTime(), latest = last.openTime();
            assertEquals(1, db.as(owner, () -> db.jdbc.update("""
                    UPDATE continuous_sim_runs SET status='STALLED',lifecycle_status='BLOCKED',
                        final_valuation_status='UNAVAILABLE',overlap_check_pending=true WHERE paper_run_id=?
                    """, run)));
            long id = db.admin.queryForObject("""
                    INSERT INTO continuous_sim_overlap_checks(paper_run_id,first_open_time,last_open_time,retry)
                    VALUES (?,?,?,false) RETURNING check_id
                    """, Long.class, run, Timestamp.from(first), Timestamp.from(latest));
            byte[] prefix = ("{\"code\":\"0\",\"data\":["
                    + httpCandle(earlier, earlier.closePrice())
                    + ",false," + httpCandle(last, last.closePrice().add(new BigDecimal("0.01")))
                    + ",[\"truncated").getBytes(StandardCharsets.UTF_8);
            db.admin.update("""
                    UPDATE continuous_sim_overlap_checks SET response_kind='PARSE_FAILED',response_fingerprint=repeat('a',64),
                        response_prefix=?,response_captured_at=clock_timestamp(),captured_result='INCOMPLETE',
                        captured_failure_reason='INCOMPLETE_HTTP_RESPONSE',response_retryable=true WHERE check_id=?
                    """, prefix, id);
            db.admin.update("""
                    UPDATE continuous_sim_overlap_checks SET result=captured_result, failure_reason=captured_failure_reason,
                        finished_at=clock_timestamp() WHERE check_id=?
                    """, id);
            String row = db.admin.queryForObject("SELECT to_jsonb(c)::text FROM continuous_sim_overlap_checks c WHERE check_id=?",
                    String.class, id);
            db.migrate("58");
            try (var app = ResearchSimComparisonFixture.start(db)) {
                var repository = app.getBean(ContinuousSimRepository.class);
                var previous = new ContinuousSimRepository.OverlapCheck(id, first, latest, false);
                assertNull(db.as(owner, () -> repository.beginOverlapCheck(run, first, latest, previous)));
                assertTrue(db.as(owner, () -> repository.get(run)).overlapCheckPending());
            }
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM continuous_sim_overlap_checks WHERE paper_run_id=?", Integer.class, run));
            assertEquals(row, db.admin.queryForObject("""
                    SELECT (to_jsonb(c)-'response_row_failures')::text FROM continuous_sim_overlap_checks c
                    WHERE check_id=? AND response_row_failures IS NULL
                    """, String.class, id));
        }
    }

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
            var v54History = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var v55 = db.migration("55");
            assertEquals(1, v55.migrate().migrationsExecuted);
            v55.validate();
            assertEquals(0, v55.migrate().migrationsExecuted);
            assertEquals(v54History, db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int<=54 ORDER BY installed_rank"));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM continuous_sim_overlap_checks", Integer.class));
            var v55History = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var v56 = db.migration("56");
            assertEquals(1, v56.migrate().migrationsExecuted);
            v56.validate();
            assertEquals(0, v56.migrate().migrationsExecuted);
            assertEquals(v55History, db.admin.queryForList(
                    "SELECT version,checksum FROM flyway_schema_history WHERE version::int<=55 ORDER BY installed_rank"));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM sim_account_projection_operations", Integer.class));
            var v56History = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var v57 = db.migration("57");
            assertEquals(1, v57.migrate().migrationsExecuted);
            v57.validate();
            assertEquals(0, v57.migrate().migrationsExecuted);
            assertEquals(v56History, db.admin.queryForList(
                    "SELECT version,checksum FROM flyway_schema_history WHERE version::int<=56 ORDER BY installed_rank"));
            var v57History = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var v58 = db.migration("58");
            assertEquals(1, v58.migrate().migrationsExecuted);
            v58.validate();
            assertEquals(0, v58.migrate().migrationsExecuted);
            assertEquals(v57History, db.admin.queryForList(
                    "SELECT version,checksum FROM flyway_schema_history WHERE version::int<=57 ORDER BY installed_rank"));
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
            var decisions = new com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository(db.jdbc);
            var ledger = new com.guidinglight.nexusquant.ledger.infra.jdbc.JdbcLedgerPostingRepository(db.jdbc);
            var repository = new ContinuousSimRepository(db.jdbc, ForwardSimEvaluationFixture.JSON,
                    decisions, ledger, new com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRepository(
                            db.jdbc, ledger, decisions));
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
                for (String sample : List.of("original", "revised", "malformed", "missing")) {
                    boolean malformed = "malformed".equals(sample);
                    boolean missing = "missing".equals(sample);
                    boolean revised = malformed || "revised".equals(sample);
                    boolean incomplete = malformed || missing;
                    boolean blocked = revised || incomplete;
                    tick.set(before);
                    setDatabaseTime(db, before);
                    ReflectionTestUtils.setField(driver, "clock", Clock.fixed(market.end(), ZoneOffset.UTC));
                    String run = http.ok("POST", OPS, token, create(binding.publishId(),end)).path("paperRunId").asText();
                    var rows = db.as(owner, () -> app.getBean(ContinuousSimRepository.class).recentBars(run));
                    var response = ForwardSimEvaluationFixture.JSON.createObjectNode().put("code","0");
                    var data = response.putArray("data");
                    var overlap = rows.getLast();
                    assertEquals(end.minus(HOUR.multipliedBy(2)), overlap.openTime());
                    for (HistoricalBar bar : List.of(overlap, rows.get(rows.size() - 2))) {
                        if (missing && !bar.equals(overlap)) continue;
                        BigDecimal close = revised && bar.equals(overlap) ? bar.closePrice().add(new BigDecimal("0.01")) : bar.closePrice();
                        data.addArray().add(Long.toString(bar.openTime().toEpochMilli())).add(bar.openPrice().toPlainString())
                                .add(bar.highPrice().toPlainString()).add(bar.lowPrice().toPlainString()).add(close.toPlainString())
                                .add(bar.volume().toPlainString()).add("1").add(bar.quoteVolume().toPlainString()).add("1");
                    }
                    if (malformed) data.addArray().add("invalid trailing row");
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
                    assertEquals(blocked ? "BLOCKED" : "COMPLETED", result.bounded().lifecycleStatus(), sample);
                    assertEquals(incomplete, db.admin.queryForObject(
                            "SELECT overlap_check_pending FROM continuous_sim_runs WHERE paper_run_id=?", Boolean.class, run), sample);
                    assertEquals(revised ? after : null, readTime(db, "revision_detected_at", run), sample);
                    assertNull(readTime(db, "pending_revision_detected_at", run));
                    assertEquals(digest, db.admin.queryForObject("SELECT bar_sha256 FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?",
                            String.class,run,Timestamp.from(overlap.openTime())));
                    assertEquals(0,countOrders(db,ForwardSimEvaluationFixture.account(db,run)));
                    assertEquals(blocked ? 0 : 1, db.admin.queryForObject(
                            "SELECT count(*) FROM strategy_sim_decisions WHERE paper_run_id=?", Integer.class, run));
                    if (malformed) {
                        assertFalse(status(http, token, run).hasNonNull("terminatedAt"));
                        restart(db, owner, run, end, "BLOCKED");
                        assertEquals(after, readTime(db, "revision_detected_at", run));
                        assertTrue(db.admin.queryForObject(
                                "SELECT overlap_check_pending FROM continuous_sim_runs WHERE paper_run_id=?", Boolean.class, run));
                    }
                    if (blocked) {
                        assertEquals(revised ? "DATA_REVISION_DETECTED" : "OVERLAP_CHECK_INCOMPLETE",
                                status(http, token, run).required("reason").asText());
                        assertEquals(0, db.admin.queryForObject(
                                "SELECT count(*) FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?",
                                Integer.class, run, Timestamp.from(end.minus(HOUR))));
                    }
                    // 原始对照有相同末价来源；只有完整无修订的普通响应可以继续采集该末价。
                    Instant exhausted = end.plusSeconds(901);
                    setDatabaseTime(db,exhausted);
                    tick.set(exhausted);
                    var terminal = db.as(owner, () -> driver.pollOnce(run));
                    assertEquals("STOPPED",terminal.status());
                    assertEquals(blocked ? "BLOCKED" : "COMPLETED",terminal.bounded().lifecycleStatus(), sample);
                    assertEquals(blocked ? "UNAVAILABLE" : "VERIFIED",terminal.bounded().finalValuationStatus(), sample);
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

    @Test @Timeout(300)
    void truncatedHttpResponseRequiresANewDurableOverlapCheck() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            var anotherTenant = db.demo("OVERLAP_CHECK_OTHER", "overlap-check-demo");
            try (var app = startWithDatabaseClock(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                var repository = app.getBean(ContinuousSimRepository.class);
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
                        "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"last\":\"50000\",\"ts\":\""
                                + tick.get().toEpochMilli() + "\"}]}"));
                for (String mode : List.of("retry", "commit-failure", "revised", "retry-commit-failure",
                        "transport-revised", "transport-empty", "mixed-revised-truncated", "mixed-revised-json", "mixed-matching")) {
                    tick.set(before);
                    setDatabaseTime(db, before);
                    ReflectionTestUtils.setField(driver, "clock", Clock.fixed(market.end(), ZoneOffset.UTC));
                    String run = http.ok("POST", OPS, token, create(binding.publishId(), end)).required("paperRunId").asText();
                    var bars = db.as(owner, () -> repository.recentBars(run));
                    var earlier = bars.get(bars.size() - 2);
                    var overlap = bars.getLast();
                    assertEquals(end.minus(HOUR.multipliedBy(2)), overlap.openTime());
                    String digest = ContinuousSimRepository.contentSha(overlap);
                    String original = httpCandle(overlap, overlap.closePrice());
                    String revised = httpCandle(overlap, overlap.closePrice().add(new BigDecimal("0.01")));
                    boolean transport = mode.startsWith("transport-");
                    String prefix = List.of("revised", "transport-revised").contains(mode) ? revised : original;
                    String closing = httpCandle(ClosedBarForwardEvaluationFixture.bar(
                            end.minus(HOUR), new BigDecimal("52300"), end, after), new BigDecimal("52300"));
                    String complete = "{\"code\":\"0\",\"data\":[" + httpCandle(earlier, earlier.closePrice())
                            + "," + original + "," + closing + "]}";
                    boolean mixed = mode.startsWith("mixed-");
                    boolean mixedMatching = "mixed-matching".equals(mode);
                    boolean mixedJson = mixedMatching || "mixed-revised-json".equals(mode);
                    String incomplete = "{\"code\":\"0\",\"data\":[" + (mixed
                            ? httpCandle(earlier, earlier.closePrice()) + ",\"invalid-element\","
                                + (mixedMatching ? original : revised) + (mixedJson ? "," + closing + "]}" : ",[\"truncated")
                            : prefix + ",[\"truncated");
                    var calls = new AtomicInteger();
                    var allowComplete = new java.util.concurrent.atomic.AtomicBoolean(true);
                    var closingCalls = new AtomicInteger();
                    market.server.removeContext("/api/v5/market/history-candles");
                    market.server.createContext("/api/v5/market/history-candles", ex -> {
                        if (ex.getRequestURI().getQuery().endsWith("limit=24")) {
                            int sent = calls.incrementAndGet();
                            tick.set(after);
                            setDatabaseTime(db, after);
                            // 同一次 GET 如有内置重试，第二次即可得到原样完整页。
                            if (sent == 1 && transport) {
                                String partial = "transport-empty".equals(mode) ? "{\"code\":\"0\",\"data\":[" : incomplete;
                                try (ex) {
                                    byte[] data = partial.getBytes(StandardCharsets.UTF_8);
                                    ex.sendResponseHeaders(200, data.length + 32);
                                    ex.getResponseBody().write(data);
                                }
                            } else if (sent == 2 && "retry-commit-failure".equals(mode)) {
                                reply(ex, complete.replace(original, revised));
                            } else {
                                reply(ex, sent == 1 || !allowComplete.get() ? incomplete : complete);
                            }
                        } else {
                            closingCalls.incrementAndGet();
                            reply(ex, "{\"code\":\"0\",\"data\":[" + closing + "]}");
                        }
                    });
                    ReflectionTestUtils.setField(driver, "clock", clock);
                    ReflectionTestUtils.setField(driver, "feed", feed);
                    if ("commit-failure".equals(mode)) {
                        installOverlapCommitFailure(db);
                        assertOverlapCommitFailure(db, owner, driver, run);
                        db.admin.execute("DROP TRIGGER interrupt_http_response_evidence ON continuous_sim_overlap_checks");
                    } else {
                        db.as(owner, () -> driver.pollOnce(run));
                    }
                    assertEquals(1, calls.get());
                    assertEquals(0, closingCalls.get());
                    assertNull(readTime(db, "pending_revision_detected_at", run));
                    boolean firstRevised = List.of("revised", "transport-revised").contains(mode) || mixed && !mixedMatching;
                    assertEquals(firstRevised ? after : null, readTime(db, "revision_detected_at", run));
                    assertTrue(db.admin.queryForObject(
                            "SELECT overlap_check_pending FROM continuous_sim_runs WHERE paper_run_id=?", Boolean.class, run));
                    var first = overlapAttempts(db, run).getFirst();
                    assertEquals("commit-failure".equals(mode) ? null
                            : mixedJson ? "OVERLAP_CHECK_INCOMPLETE" : "INCOMPLETE_HTTP_RESPONSE", first.get("failure_reason"));
                    assertEquals("commit-failure".equals(mode) ? null : firstRevised ? "REVISED" : "INCOMPLETE", first.get("result"));
                    assertEquals(transport ? "TRANSPORT_INTERRUPTED" : "PARSE_FAILED", first.get("response_kind"));
                    assertArrayEquals(("transport-empty".equals(mode) ? "{\"code\":\"0\",\"data\":[" : incomplete)
                            .getBytes(StandardCharsets.UTF_8), overlapPrefix(db, run));
                    if (firstRevised) assertEquals(digest, first.get("original_bar_sha256"));
                    var rowFailures = ForwardSimEvaluationFixture.JSON.readTree((String) first.get("response_row_failures"));
                    assertEquals(mixed ? 1 : 0, rowFailures.size());
                    if (mixed) {
                        assertEquals(1, rowFailures.get(0).required("index").asInt());
                        assertEquals("PUBLIC_MARKET_BAR_INVALID_SHAPE", rowFailures.get(0).required("reason").asText());
                        assertEquals(mixedMatching, first.get("response_retryable"));
                    }
                    assertEquals(firstRevised ? after : null, first.get("captured_revision_at") == null
                            ? null : ((Timestamp) first.get("captured_revision_at")).toInstant());
                    if ("transport-empty".equals(mode)) {
                        var check = new ContinuousSimRepository.OverlapCheck(
                                ((Number) first.get("check_id")).longValue(), earlier.openTime(), overlap.openTime(), false);
                        var prefixBytes = overlapPrefix(db, run);
                        db.as(owner, () -> {
                            repository.captureOverlapResponse(run, check, List.of(), false, "INCOMPLETE_HTTP_RESPONSE",
                                    ClosedBarMarketFeed.ResponseKind.TRANSPORT_INTERRUPTED, prefixBytes, null, List.of(), clock);
                            repository.finishOverlapCheck(run, check);
                            return null;
                        });
                        assertEquals(first, overlapAttempts(db, run).getFirst());
                        assertThrows(IllegalStateException.class, () -> db.as(owner, () -> {
                            repository.captureOverlapResponse(run, check, List.of(), false, "INCOMPLETE_HTTP_RESPONSE",
                                    ClosedBarMarketFeed.ResponseKind.TRANSPORT_INTERRUPTED, new byte[0], null, List.of(), clock);
                            return null;
                        }));
                    }
                    assertEquals(0, db.as(anotherTenant, () -> db.jdbc.queryForObject(
                            "SELECT count(*) FROM continuous_sim_overlap_checks WHERE paper_run_id=?", Integer.class, run)));
                    assertFalse(status(http, token, run).hasNonNull("terminatedAt"));
                    if (mixedMatching) {
                        db.as(owner, () -> driver.pollOnce(run));
                        var attempts = overlapAttempts(db, run);
                        assertEquals(2, calls.get());
                        assertEquals(2, attempts.size());
                        assertEquals(first, attempts.getFirst());
                        assertNotEquals(first.get("check_id"), attempts.getLast().get("check_id"));
                        assertEquals("VERIFIED", attempts.getLast().get("result"));
                        assertEquals("COMPLETED", status(http, token, run).required("lifecycleStatus").asText());
                    } else if ("retry".equals(mode)) {
                        allowComplete.set(false);
                        db.as(owner, () -> driver.pollOnce(run));
                        assertEquals(2, calls.get());
                        assertEquals(0, closingCalls.get());
                        assertEquals("INCOMPLETE", overlapAttempts(db, run).getLast().get("result"));
                        allowComplete.set(true);
                        db.as(owner, () -> driver.pollOnce(run));
                        assertEquals(3, calls.get());
                        assertEquals(1, closingCalls.get());
                        var attempts = overlapAttempts(db, run);
                        assertEquals(first, attempts.getFirst());
                        assertEquals(3, attempts.stream().map(row -> row.get("check_id")).distinct().count());
                        assertEquals("VERIFIED", attempts.getLast().get("result"));
                        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                .digest(complete.getBytes(StandardCharsets.UTF_8))), attempts.getLast().get("response_body_sha256"));
                        assertFalse(db.admin.queryForObject(
                                "SELECT overlap_check_pending FROM continuous_sim_runs WHERE paper_run_id=?", Boolean.class, run));
                        assertEquals("COMPLETED", status(http, token, run).required("lifecycleStatus").asText());
                        db.as(owner, () -> driver.pollOnce(run));
                        assertEquals(3, calls.get());
                        assertEquals(attempts, overlapAttempts(db, run));
                    } else {
                        if ("retry-commit-failure".equals(mode)) {
                            installOverlapCommitFailure(db);
                            assertOverlapCommitFailure(db, owner, driver, run);
                            db.admin.execute("DROP TRIGGER interrupt_http_response_evidence ON continuous_sim_overlap_checks");
                            var retry = overlapAttempts(db, run).getLast();
                            assertNotEquals(first.get("check_id"), retry.get("check_id"));
                            assertNull(retry.get("result"));
                            assertEquals("REVISED", retry.get("captured_result"));
                            assertEquals(after, ((Timestamp) retry.get("captured_revision_at")).toInstant());
                            assertEquals(digest, retry.get("original_bar_sha256"));
                            assertEquals(after, readTime(db, "pending_revision_detected_at", run));
                        }
                        var captured = overlapAttempts(db, run);
                        restart(db, owner, run, end, "BLOCKED");
                        assertEquals(captured, overlapAttempts(db, run));
                        assertTrue(db.admin.queryForObject(
                                "SELECT overlap_check_pending FROM continuous_sim_runs WHERE paper_run_id=?", Boolean.class, run));
                        assertEquals("UNAVAILABLE", status(http, token, run).required("finalValuationStatus").asText());
                        assertEquals(firstRevised || "retry-commit-failure".equals(mode) ? after : null,
                                readTime(db, "revision_detected_at", run));
                        db.as(owner, () -> driver.pollOnce(run));
                        assertEquals("retry-commit-failure".equals(mode) ? 2 : 1, calls.get());
                        assertEquals(0, closingCalls.get());
                    }
                    assertEquals(0, countOrders(db, ForwardSimEvaluationFixture.account(db, run)));
                    assertEquals(digest, db.admin.queryForObject(
                            "SELECT bar_sha256 FROM continuous_sim_bars WHERE paper_run_id=? AND open_time=?",
                            String.class, run, Timestamp.from(overlap.openTime())));
                }
            }
        }
    }

    private static void installOverlapCommitFailure(TenantIsolationTestDatabase db) {
        db.admin.execute("""
                CREATE OR REPLACE FUNCTION interrupt_http_response_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'fixture http evidence commit interrupted'; END; $$;
                """);
        db.admin.execute("""
                CREATE CONSTRAINT TRIGGER interrupt_http_response_evidence AFTER UPDATE OF finished_at
                ON continuous_sim_overlap_checks DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW EXECUTE FUNCTION interrupt_http_response_evidence()
                """);
    }

    private static void assertOverlapCommitFailure(TenantIsolationTestDatabase db, TenantIdentity owner,
            ContinuousSimRunService driver, String run) {
        var rejected = assertThrows(RuntimeException.class, () -> db.as(owner, () -> driver.pollOnce(run)));
        assertTrue(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(rejected)
                .getMessage().contains("fixture http evidence commit interrupted"));
    }

    private static byte[] overlapPrefix(TenantIsolationTestDatabase db, String run) {
        return db.admin.queryForObject("""
                SELECT response_prefix FROM continuous_sim_overlap_checks WHERE paper_run_id=?
                ORDER BY check_id LIMIT 1
                """, byte[].class, run);
    }

    private static List<Map<String, Object>> overlapAttempts(TenantIsolationTestDatabase db, String run) {
        return db.admin.queryForList("""
                SELECT check_id,result,failure_reason,started_at,finished_at,response_kind,captured_result,
                    captured_revision_at,original_bar_sha256,response_body_sha256,response_retryable,
                    response_row_failures::text AS response_row_failures
                FROM continuous_sim_overlap_checks WHERE paper_run_id=? ORDER BY check_id
                """, run);
    }

    private static String httpCandle(HistoricalBar bar, BigDecimal close) {
        return ForwardSimEvaluationFixture.JSON.createArrayNode().add(Long.toString(bar.openTime().toEpochMilli()))
                .add(bar.openPrice().toPlainString()).add(bar.highPrice().toPlainString()).add(bar.lowPrice().toPlainString())
                .add(close.toPlainString()).add(bar.volume().toPlainString()).add("1")
                .add(bar.quoteVolume().toPlainString()).add("1").toString();
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
                    var overlap = db.as(owner, () -> repository.recentBars(run));
                    var original = overlap.getLast();
                    assertEquals(end.minus(HOUR.multipliedBy(2)), original.openTime());
                    String digest = ContinuousSimRepository.contentSha(original);
                    var changed = ClosedBarForwardEvaluationFixture.bar(original.openTime(),
                            original.closePrice().add(new BigDecimal("0.01")), end, seen);
                    assertNotEquals(digest, ContinuousSimRepository.contentSha(changed));
                    var earlier = overlap.get(overlap.size() - 2);
                    ReflectionTestUtils.setField(driver, "feed", (ClosedBarMarketFeed) (first, limit) -> {
                        assertEquals(24, limit, "未证实的行情不得补取末价");
                        setDatabaseTime(db, seen);
                        tick.set(seen);
                        var finalBar = ClosedBarForwardEvaluationFixture.bar(end.minus(HOUR),
                                new BigDecimal("52300"), end, seen);
                        return new ClosedBarMarketFeed.Observation(end, seen, List.of(earlier, changed, finalBar), null, null);
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

    @ParameterizedTest @ValueSource(strings = {"cash", "position", "both", "none", "average"}) @Timeout(300)
    void manualEndRequiresConsistentCashAndPositionProjections(String fault) throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            try (var app = startWithDatabaseClock(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort())) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                Instant firstBoundary = app.getBean(ResearchSimComparisonFixture.MarketServer.class).end().plus(HOUR);
                Instant end = firstBoundary.plus(HOUR.multipliedBy(7));
                Instant before = firstBoundary.minusSeconds(60);
                Instant requestAt = end.plusSeconds(5);
                setDatabaseTime(db, before);
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(before, ZoneOffset.UTC));
                String run = http.ok("POST", OPS, token, create(binding.publishId(), end)).required("paperRunId").asText();
                long account = ForwardSimEvaluationFixture.account(db, run);
                String recoveryPath = RUNS + run + "/projection-recovery";
                var preTrade = http.ok("GET", recoveryPath, token, null);
                var matching = app.getBean(PaperMatchingService.class);
                int fills = 0;
                boolean consistent = "none".equals(fault);
                for (Instant boundary = firstBoundary; boundary.isBefore(end) && (consistent || fills == 0);
                        boundary = boundary.plus(HOUR)) {
                    setDatabaseTime(db, boundary.plusSeconds(5));
                    configure(app, db, owner, run, boundary, end, true);
                    db.as(owner, () -> driver.pollOnce(run));
                    fills += db.as(owner, () -> matching.matchOnce(100));
                }
                assertTrue(fills > 0);
                assertEquals(fills, countOrders(db, account));
                assertEquals(fills, db.admin.queryForObject(
                        "SELECT count(*) FROM trades WHERE account_id=?", Integer.class, account));
                if (consistent) {
                    assertEquals(List.of("BUY", "SELL"), db.admin.queryForList(
                            "SELECT DISTINCT side FROM orders WHERE account_id=? AND status='FILLED' ORDER BY side",
                            String.class, account));
                    assertEquals(0, BigDecimal.ZERO.compareTo(db.admin.queryForObject(
                            "SELECT qty FROM positions WHERE account_id=? AND symbol='BTC-USDT'", BigDecimal.class, account)));
                }
                setDatabaseTime(db, requestAt);
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(requestAt, ZoneOffset.UTC));
                var mark = ClosedBarForwardEvaluationFixture.bar(end.minus(HOUR), new BigDecimal("52300"), end, requestAt);
                db.as(owner, () -> {
                    app.getBean(ContinuousSimRepository.class).insertBar(run, mark, requestAt);
                    return null;
                });
                ReflectionTestUtils.setField(driver, "feed", (ClosedBarMarketFeed) (first, limit) -> {
                    throw new AssertionError("manual settlement must reuse the stored closing bar");
                });
                Map<String, Object> command = operation("END", run);
                var receipt = http.ok("POST", OPS, token, command);
                assertEquals(requestAt, readTime(db, "manual_end_at", run));
                var economics = app.getBean(StrategySimRunService.class);
                assertNull(db.as(owner, () -> economics.settleBounded(run)).blockingReason());
                var original = ForwardSimEvaluationFixture.allFacts(db);
                var originalLedgerEvents = db.admin.queryForList("""
                        SELECT to_jsonb(v)::text FROM ledger_events v JOIN ledger_entries e USING(entry_id)
                        WHERE e.account_id=? ORDER BY v.ledger_event_id
                        """, String.class, account);
                var positions = db.admin.queryForList("SELECT * FROM positions WHERE account_id=? ORDER BY symbol", account);
                assertFalse(positions.isEmpty());
                // 仅损坏隔离库的派生投影，保留正式撮合的 Order、Trade、Ledger 与原现金快照。
                if ("cash".equals(fault) || "both".equals(fault)) {
                    damageCashProjection(db, owner, account);
                }
                if ("position".equals(fault) || "both".equals(fault)) {
                    assertEquals(1, db.as(owner, () -> db.jdbc.update("""
                            UPDATE positions SET qty=qty+0.001,avg_price=avg_price+1,frozen_qty=0.001 WHERE account_id=? AND symbol='BTC-USDT'
                            """, account)));
                }
                if ("average".equals(fault)) {
                    assertEquals(1, db.as(owner, () -> db.jdbc.update("""
                            UPDATE positions SET avg_price=avg_price+1 WHERE account_id=? AND symbol='BTC-USDT'
                            """, account)));
                }
                var settlement = db.as(owner, () -> economics.settleBounded(run));
                assertEquals(0, settlement.pendingOrders());
                assertNull(settlement.blockingReason());
                if (!consistent) {
                    String reason = switch (fault) {
                        case "cash" -> "SIM_CASH_PROJECTION_MISMATCH";
                        case "position", "average" -> "SIM_POSITION_PROJECTION_MISMATCH";
                        default -> "SIM_ACCOUNT_PROJECTIONS_MISMATCH";
                    };
                    var damagedPositions = db.admin.queryForList("SELECT * FROM positions WHERE account_id=?", account);
                    var damagedCash = db.admin.queryForMap("""
                            SELECT * FROM account_snapshots WHERE account_id=? AND currency='USDT' AND trade_env='SIM'
                            ORDER BY snapshot_id DESC LIMIT 1
                            """, account);
                    db.as(owner, () -> driver.pollOnce(run));
                    var blocked = status(http, token, run);
                    assertEquals("BLOCKED", blocked.required("lifecycleStatus").asText());
                    assertEquals(0, blocked.required("pendingOrders").asInt());
                    assertEquals(reason, blocked.required("reason").asText());
                    assertEquals("UNAVAILABLE", blocked.required("finalValuationStatus").asText());
                    assertFalse(blocked.hasNonNull("terminatedAt"));
                    assertEquals("RUNNING", db.admin.queryForObject(
                            "SELECT status FROM paper_trading_runs WHERE paper_run_id=? AND stopped_at IS NULL", String.class, run));
                    var rejected = assertThrows(DataAccessException.class, () -> db.as(owner, () -> db.jdbc.update("""
                            INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,status,trace_id,venue,exchange_code,trade_env)
                            VALUES('accounting-block-order',?,'BTC-USDT','accounting-block-client',
                                'BUY','LIMIT',100,1,'ACCEPTED','accounting-block','PAPER','PAPER','SIM')
                            """, account)));
                    assertTrue(rejected.getMostSpecificCause().getMessage().contains("CONTINUOUS_SIM_ADMISSION_CLOSED"));
                    if ("both".equals(fault)) restart(db, owner, run, end, reason);
                    assertEquals(receipt, http.ok("POST", OPS, token, command));
                    assertEquals(receipt, http.ok("GET", OPS + "/" + command.get("operationId"), token, null));
                    assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM continuous_sim_operations WHERE operation_id=?",
                            Integer.class, UUID.fromString(command.get("operationId").toString())));
                    assertEquals(blocked, status(http, token, run));
                    assertEquals(requestAt, readTime(db, "manual_end_at", run));
                    assertEquals(damagedPositions, db.admin.queryForList("SELECT * FROM positions WHERE account_id=?", account));
                    assertEquals(damagedCash, db.admin.queryForMap("""
                            SELECT * FROM account_snapshots WHERE account_id=? AND currency='USDT' AND trade_env='SIM'
                            ORDER BY snapshot_id DESC LIMIT 1
                            """, account));
                    var beforePreview = ForwardSimEvaluationFixture.allFacts(db);
                    var beforePreviewProjections = projectionRows(db, account);
                    var preview = http.ok("GET", recoveryPath, token, null);
                    assertEquals(beforePreview, ForwardSimEvaluationFixture.allFacts(db));
                    assertEquals(beforePreviewProjections, projectionRows(db, account));
                    assertEquals(account, preview.required("accountId").asLong());
                    assertEquals(command.get("operationId").toString(), preview.required("endOperationId").asText());
                    var differences = preview.required("differences");
                    assertEquals("position".equals(fault) || "average".equals(fault),
                            differences.required("cashConsistent").asBoolean());
                    assertEquals("cash".equals(fault), differences.required("positionConsistent").asBoolean());
                    var recoverRequest = projectionRecoveryRequest(preview);
                    var stale = new HashMap<String, String>(recoverRequest);
                    stale.put("ledgerWatermark", preTrade.required("ledgerWatermark").required("sha256").asText());
                    assertConflict(http, recoveryPath, token, stale, "SIM_PROJECTION_PREVIEW_EXPIRED");

                    if ("both".equals(fault)) {
                        db.local("projection-other-owner");
                        db.user("projection-viewer", "VIEWER");
                        var otherTenant = db.demo("PROJECTION_OTHER_TENANT", "projection-demo");
                        assertEquals(0, db.as(otherTenant, () -> db.jdbc.queryForObject(
                                "SELECT count(*) FROM sim_account_projection_operations WHERE account_id=?", Integer.class, account)));
                        String other = http.login("projection-other-owner");
                        assertEquals(404, http.send("GET", recoveryPath, other, null).statusCode());
                        assertEquals(404, http.send("POST", recoveryPath, other, recoverRequest).statusCode());
                        assertEquals(403, http.send("POST", recoveryPath, http.login("projection-viewer"), recoverRequest).statusCode());
                        assertEquals(403, http.send("POST", recoveryPath, http.login("projection-demo"), recoverRequest).statusCode());
                        assertEquals(401, http.send("GET", recoveryPath, null, null).statusCode());
                        var later = operation("END", run);
                        http.ok("POST", OPS, token, later);
                        assertEquals(command.get("operationId").toString(),
                                http.ok("GET", recoveryPath, token, null).required("endOperationId").asText());
                    }

                    JsonNode recovery;
                    if ("both".equals(fault)) {
                        assertRecoveryRequiresCanonicalFacts(db, http, recoveryPath, token, recoverRequest);
                        assertProjectionRollbackAndJvmDeath(db, http, recoveryPath, token, recoverRequest, run, account);
                        recovery = recoverWithContenders(db, app, owner, http, recoveryPath, token, recoverRequest, account);
                    } else {
                        recovery = http.ok("POST", recoveryPath, token, recoverRequest);
                    }
                    assertEquals("CONSISTENT", recovery.required("cash").asText());
                    assertEquals("CONSISTENT", recovery.required("position").asText());
                    assertEquals("UNCHANGED", recovery.required("ledger").asText());
                    assertEquals(differences, recovery.required("before"));
                    assertEquals(preview.required("ledgerWatermark"), recovery.required("ledgerWatermark"));
                    assertEquals(recovery, http.ok("POST", recoveryPath, token, recoverRequest));
                    assertEquals(recovery, http.ok("GET", recoveryPath + "/operations/" + recoverRequest.get("operationId"), token, null));
                    assertConflict(http, recoveryPath, token, stale, "SIM_PROJECTION_OPERATION_ID_CONFLICT");
                    assertEquals(1, db.admin.queryForObject("""
                            SELECT count(*) FROM audit_logs WHERE action='SIM_ACCOUNT_PROJECTION_RECOVERED'
                            """, Integer.class));

                    if ("both".equals(fault)) {
                        var sameTenantOther = db.local("projection-other-owner");
                        var otherTenant = db.demo("PROJECTION_RECEIPT_TENANT", "projection-receipt-demo");
                        String receiptCount = "SELECT count(*) FROM sim_account_projection_operations WHERE account_id=?";
                        assertEquals(1, db.as(owner, () -> db.jdbc.queryForObject(receiptCount, Integer.class, account)));
                        assertEquals(0, db.as(sameTenantOther, () -> db.jdbc.queryForObject(receiptCount, Integer.class, account)));
                        assertEquals(0, db.as(otherTenant, () -> db.jdbc.queryForObject(receiptCount, Integer.class, account)));
                        assertEquals(Boolean.TRUE, db.as(sameTenantOther, () -> db.jdbc.queryForObject(
                                "SELECT nq_sim_projection_recheck_required(?)", Boolean.class, run)));
                        assertEquals(Boolean.FALSE, db.as(otherTenant, () -> db.jdbc.queryForObject(
                                "SELECT nq_sim_projection_recheck_required(?)", Boolean.class, run)));
                    }
                    db.as(owner, () -> driver.pollOnce(run));
                    assertEquals("SIM_PROJECTION_FINALIZATION_REQUIRED", status(http, token, run).required("reason").asText());
                    var finalRequest = Map.of("operationId", UUID.randomUUID().toString(),
                            "endOperationId", recoverRequest.get("endOperationId"),
                            "recoveryOperationId", recoverRequest.get("operationId"));
                    if ("both".equals(fault)) {
                        db.admin.execute("""
                                CREATE POLICY test_projection_missing_mark ON continuous_sim_bars AS RESTRICTIVE FOR SELECT
                                USING (false)
                                """);
                        try {
                            assertConflict(http, recoveryPath + "/finalization", token, finalRequest,
                                    "SIM_PROJECTION_FINALIZATION_UNPROVEN");
                            assertFalse(status(http, token, run).hasNonNull("terminatedAt"));
                        } finally {
                            db.admin.execute("DROP POLICY test_projection_missing_mark ON continuous_sim_bars");
                        }
                        var probe = projectionProcess(db, run, "recheck", finalRequest);
                        try {
                            assertTrue(probe.process().waitFor(75, TimeUnit.SECONDS), Files.readString(probe.log()));
                            assertEquals(0, probe.process().exitValue(), Files.readString(probe.log()));
                            assertTrue(Files.readString(probe.log()).contains("PROJECTION_RECHECK_RESTART_VERIFIED"));
                        } finally {
                            if (probe.process().isAlive()) probe.process().destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                        }
                    }
                    var finalized = http.ok("POST", recoveryPath + "/finalization", token, finalRequest);
                    assertEquals(recovery.required("operationId"), finalized.required("recoveryOperationId"));
                    var stopped = status(http, token, run);
                    assertEquals("MANUALLY_STOPPED", stopped.required("lifecycleStatus").asText());
                    assertEquals("VERIFIED", stopped.required("finalValuationStatus").asText());
                    assertEquals(0, stopped.required("pendingOrders").asInt());
                    assertEquals(requestAt, readTime(db, "manual_end_at", run));
                    assertEquals(stopped.required("terminatedAt"), finalized.required("finalization").required("terminatedAt"));
                    assertEquals(Instant.parse(stopped.required("terminatedAt").asText()), db.admin.queryForObject("""
                            SELECT stopped_at FROM paper_trading_runs WHERE paper_run_id=? AND status='STOPPED'
                            """, Timestamp.class, run).toInstant());
                    assertEquals(finalized, http.ok("POST", recoveryPath + "/finalization", token, finalRequest));
                    assertEquals(receipt, http.ok("GET", OPS + "/" + command.get("operationId"), token, null));
                    assertEquals(2, db.admin.queryForObject(
                            "SELECT count(*) FROM sim_account_projection_operations WHERE account_id=?", Integer.class, account));
                    if ("both".equals(fault)) {
                        String resultPath = recoveryPath + "/operations/" + recoverRequest.get("operationId");
                        assertEquals(404, http.send("GET", resultPath, http.login("projection-other-owner"), null).statusCode());
                        assertEquals(403, http.send("GET", resultPath, http.login("projection-demo"), null).statusCode());
                    }
                    System.out.printf("SIM_PROJECTION_PROOF fault=%s account=%d end=%s recovery=%s finalization=%s "
                                    + "ledger=%s cash=%s position=%s lifecycle=%s valuation=%s terminatedAt=%s%n",
                            fault, account, recovery.required("endOperationId").asText(), recovery.required("operationId").asText(),
                            finalized.required("operationId").asText(), recovery.required("ledgerWatermark").required("sha256").asText(),
                            recovery.required("cash").asText(), recovery.required("position").asText(),
                            stopped.required("lifecycleStatus").asText(), stopped.required("finalValuationStatus").asText(),
                            stopped.required("terminatedAt").asText());
                } else {
                    db.as(owner, () -> driver.pollOnce(run));
                    var stopped = status(http, token, run);
                    assertEquals("MANUALLY_STOPPED", stopped.required("lifecycleStatus").asText());
                    assertEquals("VERIFIED", stopped.required("finalValuationStatus").asText());
                    assertEquals(end, Instant.parse(stopped.required("lastValuationBoundary").asText()));
                    assertEquals(Instant.parse(stopped.required("terminatedAt").asText()), db.admin.queryForObject(
                            "SELECT stopped_at FROM paper_trading_runs WHERE paper_run_id=? AND status='STOPPED'",
                            Timestamp.class, run).toInstant());
                }
                var finalFacts = ForwardSimEvaluationFixture.allFacts(db);
                assertEquals(originalLedgerEvents, db.admin.queryForList("""
                        SELECT to_jsonb(v)::text FROM ledger_events v JOIN ledger_entries e USING(entry_id)
                        WHERE e.account_id=? ORDER BY v.ledger_event_id
                        """, String.class, account));
                for (String table : List.of("orders", "trades", "ledger_entries", "continuous_sim_bars")) {
                    assertEquals(original.get(table), finalFacts.get(table), table);
                }
            }
        }
    }

    @Test @Timeout(150)
    void concurrentEndOperationsLinkOneIntentAndOtherStopSourcesKeepTheirIdentity() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            var owner = db.local(USER);
            try (var app = startWithDatabaseClock(db);
                    var http = new TenantIsolationHttpIntegrationTest.Http(app.getWebServer().getPort());
                    var worker = Executors.newSingleThreadExecutor()) {
                var binding = ResearchSimComparisonFixture.seed(app, db, owner);
                var driver = app.getBean(ContinuousSimRunService.class);
                var commands = app.getBean(ContinuousSimOperationService.class);
                var repository = app.getBean(ContinuousSimRepository.class);
                db.as(owner, () -> driver.stop(binding.paperRunId()));
                String token = http.login(USER);
                Instant now = app.getBean(ResearchSimComparisonFixture.MarketServer.class).end();
                Instant end = now.plus(HOUR);
                setDatabaseTime(db, now);
                ReflectionTestUtils.setField(driver, "clock", Clock.fixed(now, ZoneOffset.UTC));
                String run = http.ok("POST", OPS, token, create(binding.publishId(), end)).required("paperRunId").asText();
                UUID first = UUID.randomUUID();
                UUID second = UUID.randomUUID();
                var attempted = new CompletableFuture<Integer>();
                var tx = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
                JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
                var waiting = db.as(owner, () -> tx.execute(ignored -> {
                    jdbc.queryForObject("SELECT paper_run_id FROM continuous_sim_runs WHERE paper_run_id=? FOR UPDATE",
                            String.class, run);
                    var contender = worker.submit(() -> db.as(owner, () -> tx.execute(other -> {
                        attempted.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        return commands.submit(new ContinuousSimOperationRequest(
                                        second, ContinuousSimOperationRequest.Type.END, null, null, run),
                                owner.userId(), USER, "concurrent-end-second");
                    })));
                    try {
                        waitForLock(db, attempted.get(5, TimeUnit.SECONDS));
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                    assertFalse(contender.isDone());
                    commands.submit(new ContinuousSimOperationRequest(
                                    first, ContinuousSimOperationRequest.Type.END, null, null, run),
                            owner.userId(), USER, "concurrent-end-first");
                    return contender;
                }));
                assertEquals(now, waiting.get(15, TimeUnit.SECONDS).result().bounded().manualEndAt());
                assertEquals(first, terminalIntent(db, run, "MANUAL_END"));
                assertEquals(List.of(first, first), db.admin.queryForList("""
                        SELECT terminal_intent_id FROM continuous_sim_operations
                        WHERE paper_run_id=? AND operation_type='END' ORDER BY operation_id
                        """, UUID.class, run));
                assertEquals(now, readTime(db, "manual_end_at", run));
                assertEquals(first.toString(), http.ok("GET", RUNS + run + "/projection-recovery", token, null)
                        .required("endOperationId").asText());
                assertEquals(409, http.send("POST", OPS, token, Map.of(
                        "operationId", first.toString(), "type", "PAUSE", "paperRunId", run)).statusCode());
                assertThrows(DataAccessException.class, () -> db.admin.update("""
                        UPDATE continuous_sim_runs SET terminal_intent_id=? WHERE paper_run_id=?
                        """, UUID.randomUUID(), run));
                // 通过正式收敛释放原 Run 的容量，再验证另一个首次命令丢失响应的场景。
                assertEquals("STOPPED", db.as(owner, () -> driver.pollOnce(run)).status());
                // 客户端丢弃已提交 END 的正文，独立进程以原 UUID 读取。
                String lostResponseRun = http.ok("POST", OPS, token, create(binding.publishId(), end))
                        .required("paperRunId").asText();
                var lostResponseEnd = operation("END", lostResponseRun);
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(http.base + OPS))
                        .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token).POST(java.net.http.HttpRequest.BodyPublishers
                                .ofString(ForwardSimEvaluationFixture.JSON.writeValueAsString(lostResponseEnd))).build();
                assertEquals(200, http.client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
                UUID lostResponseId = UUID.fromString(lostResponseEnd.get("operationId").toString());
                assertEquals(lostResponseId, terminalIntent(db, lostResponseRun, "MANUAL_END"));
                var probe = projectionProcess(db, lostResponseRun, "end-receipt", Map.of(
                        "operationId", lostResponseId.toString(), "manualEndAt", now.toString()));
                try {
                    assertTrue(probe.process().waitFor(75, TimeUnit.SECONDS), Files.readString(probe.log()));
                    assertEquals(0, probe.process().exitValue(), Files.readString(probe.log()));
                    String log = Files.readString(probe.log());
                    assertTrue(log.contains("TERMINAL_INTENT_RESTART_VERIFIED"), log);
                    System.out.println(log.lines().filter(line -> line.startsWith("TERMINAL_INTENT_RESTART_VERIFIED"))
                            .findFirst().orElseThrow());
                } finally {
                    if (probe.process().isAlive()) probe.process().destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                }

                assertEquals("STOPPED", db.as(owner, () -> driver.pollOnce(lostResponseRun)).status());
                for (String stopPath : List.of(RUNS, "/api/paper-trading/runs/")) {
                    String stopped = http.ok("POST", OPS, token, create(binding.publishId(), end)).required("paperRunId").asText();
                    http.ok("POST", stopPath + stopped + "/stop", token, null);
                    UUID stoppedIntent = terminalIntent(db, stopped, "PAPER_RUN_STOP");
                    var later = operation("END", stopped);
                    http.ok("POST", OPS, token, later);
                    assertEquals(stoppedIntent, terminalIntent(db, stopped, "PAPER_RUN_STOP"));
                    assertEquals(now, readTime(db, "manual_end_at", stopped));
                    assertEquals(stoppedIntent, db.admin.queryForObject("""
                            SELECT terminal_intent_id FROM continuous_sim_operations WHERE operation_id=?
                            """, UUID.class, UUID.fromString(later.get("operationId").toString())));
                    assertEquals(409, http.send("GET", RUNS + stopped + "/projection-recovery", token, null).statusCode());
                    assertEquals("STOPPED", db.as(owner, () -> driver.pollOnce(stopped)).status());
                }

                String automatic = http.ok("POST", OPS, token, create(binding.publishId(), end)).required("paperRunId").asText();
                setDatabaseTime(db, end.plusSeconds(180));
                db.as(owner, () -> {
                    repository.recordSettlement(automatic, "BLOCKED", "UNAVAILABLE", 0, "CLOSING_PRICE_UNAVAILABLE", true);
                    return null;
                });
                UUID automaticIntent = terminalIntent(db, automatic, "AUTOMATIC_DEADLINE");
                var automaticEnd = http.ok("POST", OPS, token, operation("END", automatic));
                assertFalse(automaticEnd.required("result").required("bounded").hasNonNull("manualEndAt"));
                assertEquals(automaticIntent, terminalIntent(db, automatic, "AUTOMATIC_DEADLINE"));
            }
        }
    }

    private static UUID terminalIntent(TenantIsolationTestDatabase db, String run, String source) {
        return db.admin.queryForObject("""
                SELECT terminal_intent_id FROM continuous_sim_runs WHERE paper_run_id=? AND terminal_intent_source=?
                """, UUID.class, run, source);
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

    private static Map<String, List<String>> projectionRows(TenantIsolationTestDatabase db, long account) {
        return Map.of(
                "cash-and-assets", db.admin.queryForList("""
                        SELECT to_jsonb(s)::text FROM account_snapshots s WHERE account_id=? ORDER BY snapshot_id
                        """, String.class, account),
                "positions", db.admin.queryForList("""
                        SELECT to_jsonb(p)::text FROM positions p WHERE account_id=? ORDER BY symbol
                        """, String.class, account),
                "operations", db.admin.queryForList("""
                        SELECT to_jsonb(r)::text FROM sim_account_projection_operations r WHERE account_id=? ORDER BY operation_id
                        """, String.class, account));
    }

    private static void assertRecoveryRequiresCanonicalFacts(TenantIsolationTestDatabase db,
            TenantIsolationHttpIntegrationTest.Http http, String path, String token, Map<String, String> request)
            throws Exception {
        // 只模拟读链缺失，经济事实在数据库中逐字保留。
        var before = ForwardSimEvaluationFixture.allFacts(db);
        db.admin.execute("""
                CREATE POLICY test_projection_missing_trade_leg ON ledger_entries AS RESTRICTIVE FOR SELECT
                USING (NOT (ref_type='TRADE' AND right(idempotency_key,9)=':LEDGER:2'))
                """);
        try {
            assertConflict(http, path, token, request, "SIM_TRADE_LEDGER_MISMATCH");
        } finally {
            db.admin.execute("DROP POLICY test_projection_missing_trade_leg ON ledger_entries");
        }
        db.admin.execute("""
                CREATE POLICY test_projection_missing_posted ON ledger_events AS RESTRICTIVE FOR SELECT
                USING (event_type<>'POSTED')
                """);
        try {
            assertConflict(http, path, token, request, "SIM_LEDGER_POSTED_FACT_UNPROVEN");
        } finally {
            db.admin.execute("DROP POLICY test_projection_missing_posted ON ledger_events");
        }
        assertEquals(before, ForwardSimEvaluationFixture.allFacts(db));
        assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM sim_account_projection_operations", Integer.class));
    }

    private static void assertProjectionRollbackAndJvmDeath(TenantIsolationTestDatabase db,
            TenantIsolationHttpIntegrationTest.Http http, String path, String token,
            Map<String, String> request, String run, long account) throws Exception {
        var before = ForwardSimEvaluationFixture.allFacts(db);
        var beforeProjections = projectionRows(db, account);
        db.admin.execute("""
                CREATE FUNCTION test_abort_projection_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected projection receipt failure' USING ERRCODE='23514'; END;
                $$;
                CREATE TRIGGER test_abort_projection_receipt BEFORE INSERT ON sim_account_projection_operations
                FOR EACH ROW EXECUTE FUNCTION test_abort_projection_receipt()
                """);
        try {
            assertTrue(http.send("POST", path, token, request).statusCode() >= 400);
            assertEquals(before, ForwardSimEvaluationFixture.allFacts(db));
            assertEquals(beforeProjections, projectionRows(db, account));
        } finally {
            db.admin.execute("DROP TRIGGER test_abort_projection_receipt ON sim_account_projection_operations");
        }

        installProjectionReceiptGate(db);
        try (var gate = db.database.source().getConnection(); var sql = gate.createStatement()) {
            gate.setAutoCommit(false);
            sql.execute("SELECT pg_advisory_xact_lock(851005::bigint)");
            var probe = projectionProcess(db, run, "recover", request);
            try {
                waitForProjectionReceiptGate(db);
                assertEquals(before, ForwardSimEvaluationFixture.allFacts(db));
            assertEquals(beforeProjections, projectionRows(db, account));
                assertTrue(probe.process().destroyForcibly().waitFor(10, TimeUnit.SECONDS));
            } finally {
                if (probe.process().isAlive()) probe.process().destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                gate.rollback();
            }
        } finally {
            db.admin.execute("DROP TRIGGER test_projection_receipt_gate ON sim_account_projection_operations");
        }
        assertEquals(before, ForwardSimEvaluationFixture.allFacts(db));
        assertEquals(beforeProjections, projectionRows(db, account));
        assertEquals(404, http.send("GET", path + "/operations/" + request.get("operationId"), token, null).statusCode());
    }

    private static JsonNode recoverWithContenders(TenantIsolationTestDatabase db,
            ServletWebServerApplicationContext app, TenantIdentity owner, TenantIsolationHttpIntegrationTest.Http http,
            String path, String token, Map<String, String> request, long account) throws Exception {
        var original = ForwardSimEvaluationFixture.allFacts(db);
        var replayTrade = db.admin.query("""
                SELECT t.*,o.side FROM trades t JOIN orders o USING(order_id)
                WHERE t.account_id=? ORDER BY t.trade_id LIMIT 1
                """, (rs, n) -> new com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest(
                rs.getString("trade_id"), rs.getString("order_id"), account, rs.getString("symbol"),
                com.guidinglight.nexusquant.contracts.model.OrderSide.valueOf(rs.getString("side")),
                rs.getBigDecimal("price"), rs.getBigDecimal("qty"), rs.getBigDecimal("fee"), rs.getString("fee_currency"),
                rs.getString("trace_id"), rs.getTimestamp("ts").toInstant(), "SIM"), account).getFirst();
        installProjectionReceiptGate(db);
        try (var gate = db.database.source().getConnection(); var sql = gate.createStatement();
                var accountLock = db.database.source().getConnection(); var accountSql = accountLock.createStatement();
                var workers = Executors.newFixedThreadPool(4)) {
            gate.setAutoCommit(false);
            sql.execute("SELECT pg_advisory_xact_lock(851005::bigint)");
            accountLock.setAutoCommit(false);
            int backend;
            try (var pid = accountSql.executeQuery("SELECT pg_backend_pid()")) {
                assertTrue(pid.next());
                backend = pid.getInt(1);
            }
            accountSql.execute("SELECT account_id FROM accounts WHERE account_id=" + account + " FOR NO KEY UPDATE");
            try {
                var responseLost = workers.submit(() -> {
                    var call = java.net.http.HttpRequest.newBuilder(java.net.URI.create(http.base + path))
                            .timeout(Duration.ofSeconds(35)).header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token).POST(java.net.http.HttpRequest.BodyPublishers
                                    .ofString(ForwardSimEvaluationFixture.JSON.writeValueAsString(request))).build();
                    return http.client.send(call, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
                });
                assertTrue(awaitProjection(() -> db.admin.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)))
                        """, Boolean.class, backend)), "recovery did not wait for the account row");
                assertEquals(original, ForwardSimEvaluationFixture.allFacts(db));
                accountLock.commit();
                waitForProjectionReceiptGate(db);

                var other = new HashMap<String, String>(request);
                other.put("operationId", UUID.randomUUID().toString());
                var sameRequest = workers.submit(() -> http.ok("POST", path, token, request));
                var otherRequest = workers.submit(() -> http.send("POST", path, token, other));
                var ledgerReplay = workers.submit(() -> db.as(owner, () ->
                        app.getBean(com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort.class).postTrade(replayTrade)));
                assertTrue(awaitProjection(() -> db.admin.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM pg_locks waiting JOIN pg_locks holder ON holder.locktype=waiting.locktype
                            AND holder.classid=waiting.classid AND holder.objid=waiting.objid AND holder.objsubid=waiting.objsubid
                        WHERE waiting.locktype='advisory' AND NOT waiting.granted
                            AND holder.granted AND holder.pid IN (
                                SELECT pid FROM pg_locks WHERE locktype='advisory' AND NOT granted AND objid=851005::oid)
                            AND waiting.objid=(hashtextextended(?,0) & 4294967295)::text::oid)
                        """, Boolean.class, "nq:account-environment:" + account)), "ledger writer did not wait on the account advisory lock");
                assertFalse(ledgerReplay.isDone(), "ledger posting must wait for projection recovery");
                gate.commit();
                assertEquals(200, responseLost.get(25, TimeUnit.SECONDS));
                var replay = sameRequest.get(25, TimeUnit.SECONDS);
                var stale = otherRequest.get(25, TimeUnit.SECONDS);
                assertEquals(409, stale.statusCode(), stale.body());
                assertTrue(stale.body().contains("SIM_PROJECTION_PREVIEW_EXPIRED"), stale.body());
                assertTrue(ledgerReplay.get(25, TimeUnit.SECONDS).idempotentHit());
                return replay;
            } finally {
                accountLock.rollback();
                gate.rollback();
            }
        } finally {
            db.admin.execute("DROP TRIGGER test_projection_receipt_gate ON sim_account_projection_operations");
        }
    }

    private static boolean awaitProjection(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private static void installProjectionReceiptGate(TenantIsolationTestDatabase db) {
        db.admin.execute("""
                CREATE OR REPLACE FUNCTION test_projection_receipt_gate() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN PERFORM pg_advisory_xact_lock(851005::bigint); RETURN NEW; END;
                $$;
                CREATE TRIGGER test_projection_receipt_gate BEFORE INSERT ON sim_account_projection_operations
                FOR EACH ROW EXECUTE FUNCTION test_projection_receipt_gate()
                """);
    }

    private static void waitForProjectionReceiptGate(TenantIsolationTestDatabase db) throws InterruptedException {
        for (int attempt = 0; attempt < 450; attempt++) {
            if (Boolean.TRUE.equals(db.admin.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM pg_locks
                        WHERE locktype='advisory' AND objid=851005::oid AND NOT granted)
                    """, Boolean.class))) return;
            Thread.sleep(100);
        }
        fail("recovery did not reach its uncommitted receipt insert");
    }

    private static ProjectionProbe projectionProcess(TenantIsolationTestDatabase db, String run, String mode,
            Map<String, String> input) throws Exception {
        Path log = Files.createTempFile("sim-projection-restart-", ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), SimProjectionRecoveryProbeMain.class.getName(),
                run, mode, java.util.Base64.getEncoder().encodeToString(
                        ForwardSimEvaluationFixture.JSON.writeValueAsBytes(input)));
        builder.environment().put("TASK_BOUNDED_DB_URL", db.url());
        builder.environment().put("TASK_BOUNDED_DB_USER", db.runtime);
        builder.environment().put("TASK_BOUNDED_DB_PASSWORD", db.runtimePassword);
        return new ProjectionProbe(builder.redirectErrorStream(true).redirectOutput(log.toFile()).start(), log);
    }

    private record ProjectionProbe(Process process, Path log) {}

    private static Map<String, String> projectionRecoveryRequest(JsonNode preview) {
        return Map.of("operationId", UUID.randomUUID().toString(), "endOperationId", preview.required("endOperationId").asText(),
                "ledgerWatermark", preview.required("ledgerWatermark").required("sha256").asText(),
                "projectionVersion", preview.required("projectionVersion").asText());
    }

    private static void assertConflict(TenantIsolationHttpIntegrationTest.Http http, String path,
            String token, Object body, String reason) throws Exception {
        var conflict = http.send("POST", path, token, body);
        assertEquals(409, conflict.statusCode(), conflict.body());
        assertTrue(conflict.body().contains(reason), conflict.body());
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

    private static void damageCashProjection(TenantIsolationTestDatabase db, TenantIdentity owner, long account) {
        assertEquals(1, db.as(owner, () -> db.jdbc.update("""
                INSERT INTO account_snapshots(account_id,currency,balance,available,frozen,ts,trace_id,
                    trade_env,balance_basis,balance_scope,recorded_at)
                SELECT account_id,currency,balance+1,available+1,frozen,ts,'fixture-cash-projection',
                    trade_env,balance_basis,balance_scope,clock_timestamp()
                FROM account_snapshots WHERE account_id=? AND currency='USDT' AND trade_env='SIM'
                ORDER BY snapshot_id DESC LIMIT 1
                """, account)));
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

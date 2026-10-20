package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.audit.infra.jdbc.JdbcAuditLogRepository;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobControlRepository;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperation;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperationRepository;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperationRequest;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobOperationService;
import com.guidinglight.nexusquant.scheduler.control.ScheduledJobRegistry;
import com.guidinglight.nexusquant.scheduler.control.SchedulerDispatcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 新随机PG与真实Servlet验证原调度身份、并发和回滚；不访问旧验收库或任何真实provider。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
        classes = NexusQuantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "nq.strategy-sim.enabled=true",
            "nq.continuous-sim.enabled=false",
            "nq.instrument.catalog-sync.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.env-safety.no-outbound=true",
            "nq.env-safety.live-enabled=false",
            "nq.env-safety.real-client-enabled=false",
            "nq.env-safety.real-exchange-enabled=false",
            "nq.env-safety.real-provider-enabled=false",
            "nq.account.performance.sampling-enabled=false",
            "nq.okx.adapter.stub-on-bootstrap-failure=false"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(ScheduledJobOperationPostgresIntegrationTest.CompletionFaultConfiguration.class)
class ScheduledJobOperationPostgresIntegrationTest {
    private static String database;
    private static JdbcTemplate admin;
    private static final String PASSWORD = "synthetic-scheduler-operation-password";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired ScheduledJobOperationService operations;
    @Autowired ScheduledJobOperationRepository receipts;
    @Autowired ScheduledJobControlRepository controls;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ScheduledJobRegistry jobs;
    @Autowired SchedulerDispatcher dispatcher;
    @LocalServerPort int port;
    private String ownerName, otherName, operatorName, viewerName, token;
    private long owner;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static final AtomicBoolean FAIL_COMPLETION_AUDIT = new AtomicBoolean();

    /** 只在独立测试库注入一次性完成审计故障；其他调用仍使用正式JDBC审计实现。 */
    @TestConfiguration
    static class CompletionFaultConfiguration {
        @Bean
        @Primary
        AuditLogRepository completionFaultAudit(JdbcAuditLogRepository delegate) {
            return (domain, action, actor, trace, detail) -> {
                if ("SCHEDULER".equals(domain)
                        && "RUN_ONCE".equals(action)
                        && FAIL_COMPLETION_AUDIT.compareAndSet(true, false)) {
                    throw new IllegalStateException("SYNTHETIC_ONE_SHOT_COMPLETION_AUDIT_FAILURE");
                }
                delegate.append(domain, action, actor, trace, detail);
            };
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("nq.postgres.smoke.url", ""),
                user = System.getProperty("nq.postgres.smoke.user", ""),
                password = System.getProperty("nq.postgres.smoke.password", "");
        URI uri = URI.create(url.substring(5));
        assertTrue(
                Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                        && uri.getRawQuery() == null
                        && !user.isBlank()
                        && !password.isBlank());
        admin =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url + "?connectTimeout=5&socketTimeout=30", user, password));
        admin.setQueryTimeout(20);
        assertEquals(
                16,
                admin.queryForObject(
                        "SELECT current_setting('server_version_num')::integer/10000",
                        Integer.class));
        database = "scheduled_operation_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE \"" + database + "\"");
        registry.add(
                "spring.datasource.url",
                () -> url.substring(0, url.lastIndexOf('/') + 1) + database);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        // 仅记录非敏感身份，保留本轮库以便失败复现；当前夹具不清理其他数据库。
        try {
            Path root =
                    Path.of(
                            System.getProperty(
                                    "nq.scheduler.operation.evidence",
                                    "E:/Project/nq-production-reconstruction-output/scheduler-operation-pg"));
            Files.createDirectories(root);
            Files.writeString(root.resolve("database-" + database + ".txt"), database);
        } catch (Exception failure) {
            throw new IllegalStateException("test identity write failed", failure);
        }
    }

    @BeforeEach
    void identities() throws Exception {
        String suffix = UUID.randomUUID().toString();
        ownerName = "sched-owner-" + suffix;
        otherName = "sched-other-" + suffix;
        operatorName = "sched-operator-" + suffix;
        viewerName = "sched-viewer-" + suffix;
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                ownerName, passwords.encode(PASSWORD), List.of("ADMIN"), true),
                        new SeedUserCommand(
                                otherName, passwords.encode(PASSWORD), List.of("ADMIN"), true),
                        new SeedUserCommand(
                                operatorName,
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR"),
                                true),
                        new SeedUserCommand(
                                viewerName, passwords.encode(PASSWORD), List.of("VIEWER"), true)));
        owner = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, ownerName);
        token = login(ownerName);
    }

    @AfterEach
    void facts() {
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
    }

    @Test
    void versionedPatchReplaysOriginalSnapshotAndRejectsChangedIdentityOrOldVersion()
            throws Exception {
        JsonNode row = json(send("GET", "/api/scheduler/jobs/PAPER_MATCHING", token, null));
        long version = row.path("version").asLong();
        UUID id = UUID.randomUUID();
        var request =
                new ScheduledJobOperationRequest(
                        id,
                        ScheduledJobOperation.Type.UPDATE_CONTROL,
                        "PAPER_MATCHING",
                        null,
                        3000L,
                        version);
        JsonNode first = json(send("POST", "/api/scheduler/operations", token, request));
        assertEquals("COMPLETED", first.path("status").asText());
        assertEquals(version + 1, first.path("control").path("version").asLong());
        JsonNode second =
                json(
                        send(
                                "POST",
                                "/api/scheduler/operations",
                                token,
                                new ScheduledJobOperationRequest(
                                        UUID.randomUUID(),
                                        ScheduledJobOperation.Type.UPDATE_CONTROL,
                                        "PAPER_MATCHING",
                                        null,
                                        4000L,
                                        version + 1)));
        assertEquals(4000, second.path("control").path("fixedDelayMs").asLong());
        assertEquals(first, json(send("POST", "/api/scheduler/operations", token, request)));
        assertEquals(first, json(send("GET", "/api/scheduler/operations/" + id, token, null)));
        assertEquals(version + 2, controls.find("PAPER_MATCHING").orElseThrow().version());
        var mismatch =
                send(
                        "POST",
                        "/api/scheduler/operations",
                        token,
                        new ScheduledJobOperationRequest(
                                id,
                                ScheduledJobOperation.Type.UPDATE_CONTROL,
                                "PAPER_MATCHING",
                                null,
                                5000L,
                                version));
        assertEquals(409, mismatch.statusCode());
        assertEquals(
                "SCHEDULER_OPERATION_ID_CONFLICT",
                mapper.readTree(mismatch.body()).path("code").asText());
        UUID stale = UUID.randomUUID();
        assertEquals(
                409,
                send(
                                "POST",
                                "/api/scheduler/operations",
                                token,
                                new ScheduledJobOperationRequest(
                                        stale,
                                        ScheduledJobOperation.Type.UPDATE_CONTROL,
                                        "PAPER_MATCHING",
                                        null,
                                        5000L,
                                        version))
                        .statusCode());
        assertTrue(receipts.find(stale).isEmpty());
    }

    @Test
    void realConcurrentSameIdUpdateCommitsOneControlVersionAndOneAcceptanceAudit()
            throws Exception {
        long version = controls.find("LEDGER_RECONCILIATION").orElseThrow().version();
        UUID id = UUID.randomUUID();
        var request =
                new ScheduledJobOperationRequest(
                        id,
                        ScheduledJobOperation.Type.UPDATE_CONTROL,
                        "LEDGER_RECONCILIATION",
                        null,
                        31000L,
                        version);
        var latch = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<JsonNode>>();
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    latch.await();
                                    return json(
                                            send(
                                                    "POST",
                                                    "/api/scheduler/operations",
                                                    token,
                                                    request));
                                }));
            latch.countDown();
            JsonNode first = futures.getFirst().get(30, TimeUnit.SECONDS);
            for (var future : futures) assertEquals(first, future.get(30, TimeUnit.SECONDS));
        }
        assertEquals(version + 1, controls.find("LEDGER_RECONCILIATION").orElseThrow().version());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM scheduled_job_operations WHERE operation_id=?",
                        Long.class,
                        id));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE detail_json->>'operationId'=? AND action='OPERATION_ACCEPTED'",
                        Long.class,
                        id.toString()));
    }

    @Test
    void canonicalRunPersistsOriginalResultAndRepeatDoesNotChangeRunOrAuditFacts()
            throws Exception {
        UUID id = UUID.randomUUID();
        var request =
                new ScheduledJobOperationRequest(
                        id,
                        ScheduledJobOperation.Type.RUN_ONCE,
                        "PAPER_MATCHING",
                        null,
                        null,
                        null);
        JsonNode first = json(send("POST", "/api/scheduler/operations", token, request));
        assertEquals("COMPLETED", first.path("status").asText());
        assertEquals("SUCCESS", first.path("result").asText());
        assertFalse(first.path("startedAt").isNull());
        String control =
                jdbc.queryForObject(
                        "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key='PAPER_MATCHING'",
                        String.class);
        long audits = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        assertEquals(first, json(send("POST", "/api/scheduler/operations", token, request)));
        assertEquals(first, json(send("GET", "/api/scheduler/operations/" + id, token, null)));
        assertEquals(
                control,
                jdbc.queryForObject(
                        "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key='PAPER_MATCHING'",
                        String.class));
        assertEquals(audits, jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class));
    }

    @Test
    void concurrentSameRunIdClaimsAndCompletesExactlyOneExecution() throws Exception {
        // 八轮独立UUID、每轮六个并发请求，保留全部成功响应与一次领取/审计的原断言。
        for (int round = 0; round < 8; round++) {
            UUID id = UUID.randomUUID();
            var request =
                    new ScheduledJobOperationRequest(
                            id,
                            ScheduledJobOperation.Type.RUN_ONCE,
                            "PAPER_MATCHING",
                            null,
                            null,
                            null);
            var release = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(6)) {
                var futures = new ArrayList<Future<JsonNode>>();
                for (int i = 0; i < 6; i++)
                    futures.add(
                            pool.submit(
                                    () -> {
                                        release.await();
                                        return json(
                                                send(
                                                        "POST",
                                                        "/api/scheduler/operations",
                                                        token,
                                                        request));
                                    }));
                release.countDown();
                for (var future : futures) {
                    JsonNode received = future.get(30, TimeUnit.SECONDS);
                    assertEquals(id.toString(), received.path("operationId").asText());
                    assertTrue(
                            Set.of("STARTING", "RUNNING", "OUTCOME_UNKNOWN", "COMPLETED")
                                    .contains(received.path("status").asText()));
                }
            }
            JsonNode completed = json(send("GET", "/api/scheduler/operations/" + id, token, null));
            assertEquals("COMPLETED", completed.path("status").asText());
            assertEquals("SUCCESS", completed.path("result").asText());
            assertEquals(
                    receipts.find(id).orElseThrow().startedAt(),
                    controls.find("PAPER_MATCHING").orElseThrow().lastStartedAt());
            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM audit_logs WHERE detail_json->>'operationId'=? AND action='RUN_ONCE'",
                            Long.class,
                            id.toString()));
        }
    }

    @Test
    void committedSameUuidIsVerifiedAfterPendingIndexDuplicateError() throws Exception {
        UUID id = UUID.randomUUID();
        var request =
                new ScheduledJobOperationRequest(
                        id,
                        ScheduledJobOperation.Type.RUN_ONCE,
                        "PAPER_MATCHING",
                        null,
                        null,
                        null);
        JsonNode original = json(send("POST", "/api/scheduler/operations", token, request));
        assertEquals("COMPLETED", original.path("status").asText());
        long audits = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        String control =
                jdbc.queryForObject(
                        "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key='PAPER_MATCHING'",
                        String.class);
        // 新库注入真实SQL23505，确定性覆盖冲突事务回滚后的读取分支；实际索引竞态另由并发测试覆盖。
        jdbc.execute(
                "CREATE FUNCTION fail_scheduler_pending_index() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                        + "IF NEW.operation_id='"
                        + id
                        + "'::uuid THEN RAISE EXCEPTION 'synthetic pending index race' "
                        + "USING ERRCODE='23505', CONSTRAINT='idx_scheduled_operations_pending_run'; END IF; RETURN NEW; END; $$");
        jdbc.execute(
                "CREATE TRIGGER fail_scheduler_pending_index BEFORE INSERT ON scheduled_job_operations "
                        + "FOR EACH ROW EXECUTE FUNCTION fail_scheduler_pending_index()");
        try {
            assertEquals(original, json(send("POST", "/api/scheduler/operations", token, request)));
            assertEquals(
                    409,
                    send("POST", "/api/scheduler/operations", login(otherName), request)
                            .statusCode());
            assertEquals(
                    409,
                    send(
                                    "POST",
                                    "/api/scheduler/operations",
                                    token,
                                    new ScheduledJobOperationRequest(
                                            id,
                                            ScheduledJobOperation.Type.RUN_ONCE,
                                            "LEDGER_RECONCILIATION",
                                            null,
                                            null,
                                            null))
                            .statusCode());
            assertEquals(
                    original, json(send("GET", "/api/scheduler/operations/" + id, token, null)));
            assertEquals(
                    control,
                    jdbc.queryForObject(
                            "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key='PAPER_MATCHING'",
                            String.class));
            assertEquals(
                    audits, jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class));
        } finally {
            jdbc.execute("DROP TRIGGER fail_scheduler_pending_index ON scheduled_job_operations");
            jdbc.execute("DROP FUNCTION fail_scheduler_pending_index()");
        }
    }

    @Test
    void missingOrWrongParametersDoNotCreateReceiptAndManualNoopKeepsOriginalVersion()
            throws Exception {
        var missingId = Map.of("type", "RUN_ONCE", "jobKey", "PAPER_MATCHING");
        long before =
                jdbc.queryForObject("SELECT count(*) FROM scheduled_job_operations", Long.class);
        assertEquals(400, send("POST", "/api/scheduler/operations", token, missingId).statusCode());
        UUID wrong = UUID.randomUUID();
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/scheduler/operations",
                                token,
                                new ScheduledJobOperationRequest(
                                        wrong,
                                        ScheduledJobOperation.Type.RUN_ONCE,
                                        "PAPER_MATCHING",
                                        true,
                                        null,
                                        null))
                        .statusCode());
        assertEquals(
                before,
                jdbc.queryForObject("SELECT count(*) FROM scheduled_job_operations", Long.class));
        var decimal =
                Map.of(
                        "operationId",
                        UUID.randomUUID().toString(),
                        "type",
                        "UPDATE_CONTROL",
                        "jobKey",
                        "PAPER_MATCHING",
                        "fixedDelayMs",
                        3000.5,
                        "expectedVersion",
                        0);
        assertEquals(400, send("POST", "/api/scheduler/operations", token, decimal).statusCode());
        assertEquals(
                before,
                jdbc.queryForObject("SELECT count(*) FROM scheduled_job_operations", Long.class));
        var control = controls.find("PAPER_MATCHING").orElseThrow();
        var noop =
                new ScheduledJobOperationRequest(
                        UUID.randomUUID(),
                        ScheduledJobOperation.Type.UPDATE_CONTROL,
                        "PAPER_MATCHING",
                        null,
                        control.fixedDelayMs(),
                        control.version());
        JsonNode result = json(send("POST", "/api/scheduler/operations", token, noop));
        assertEquals(control.version(), result.path("control").path("version").asLong());
        assertEquals(result, json(send("POST", "/api/scheduler/operations", token, noop)));
    }

    @Test
    void fixedCapabilityAndAdminOwnerBoundariesRemainClosed() throws Exception {
        UUID id = UUID.randomUUID();
        var request =
                new ScheduledJobOperationRequest(
                        id,
                        ScheduledJobOperation.Type.RUN_ONCE,
                        "STRATEGY_RECOVERY",
                        null,
                        null,
                        null);
        JsonNode rejected = json(send("POST", "/api/scheduler/operations", token, request));
        assertEquals("REJECTED", rejected.path("status").asText());
        assertEquals("SCHEDULER_CAPABILITY_UNAVAILABLE", rejected.path("reasonCode").asText());
        assertTrue(rejected.path("startedAt").isNull());
        assertEquals(
                404,
                send("GET", "/api/scheduler/operations/" + id, login(otherName), null)
                        .statusCode());
        for (String name : List.of(operatorName, viewerName)) {
            String denied = login(name);
            assertEquals(
                    403, send("GET", "/api/scheduler/operations/" + id, denied, null).statusCode());
            assertEquals(
                    403,
                    send(
                                    "POST",
                                    "/api/scheduler/operations",
                                    denied,
                                    new ScheduledJobOperationRequest(
                                            UUID.randomUUID(),
                                            ScheduledJobOperation.Type.RUN_ONCE,
                                            "PAPER_MATCHING",
                                            null,
                                            null,
                                            null))
                            .statusCode());
        }
        assertEquals(401, send("POST", "/api/scheduler/operations", null, request).statusCode());
    }

    @Test
    void partialWriteFailsAtomicallyAndUnknownExecutionCannotBeGivenAnotherId() throws Exception {
        UUID id = UUID.randomUUID();
        var request =
                new ScheduledJobOperationRequest(
                        id,
                        ScheduledJobOperation.Type.RUN_ONCE,
                        "PAPER_MATCHING",
                        null,
                        null,
                        null);
        // 只构造本测试库的尚未执行回执；领取、完成与失败均走真正Spring事务代理。
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        ignored ->
                                receipts.create(
                                        request,
                                        owner,
                                        ownerName,
                                        "a".repeat(64),
                                        "scheduler-rollback-fixture",
                                        Instant.now()));
        assertTrue(controls.claim("PAPER_MATCHING", id, Instant.now(), true, id));
        String before =
                jdbc.queryForObject(
                        "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key='PAPER_MATCHING'",
                        String.class);
        jdbc.execute(
                "CREATE FUNCTION fail_scheduler_finish() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='COMPLETED' THEN RAISE EXCEPTION 'synthetic completion failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute(
                "CREATE TRIGGER fail_scheduler_finish BEFORE UPDATE ON scheduled_job_operations FOR EACH ROW EXECUTE FUNCTION fail_scheduler_finish()");
        try {
            assertThrows(
                    DataAccessException.class,
                    () ->
                            controls.complete(
                                    "PAPER_MATCHING", id, Instant.now(), "SUCCESS", null, id));
        } finally {
            jdbc.execute("DROP TRIGGER fail_scheduler_finish ON scheduled_job_operations");
            jdbc.execute("DROP FUNCTION fail_scheduler_finish()");
        }
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key='PAPER_MATCHING'",
                        String.class));
        assertEquals(
                ScheduledJobOperation.Status.RUNNING, receipts.find(id).orElseThrow().status());
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(ignored -> receipts.markUnknown(id));
        assertEquals(
                409,
                send(
                                "POST",
                                "/api/scheduler/operations",
                                token,
                                new ScheduledJobOperationRequest(
                                        UUID.randomUUID(),
                                        ScheduledJobOperation.Type.RUN_ONCE,
                                        "PAPER_MATCHING",
                                        null,
                                        null,
                                        null))
                        .statusCode());
        assertEquals(
                "OUTCOME_UNKNOWN",
                json(send("GET", "/api/scheduler/operations/" + id, token, null))
                        .path("status")
                        .asText());
        controls.complete("PAPER_MATCHING", id, Instant.now(), "SUCCESS", null, id);
        assertEquals(
                ScheduledJobOperation.Status.COMPLETED, receipts.find(id).orElseThrow().status());
    }

    @Test
    void transientCompletionAuditFailureKeepsUnknownAndBlocksAnotherExecution() throws Exception {
        UUID id = UUID.randomUUID();
        var request =
                new ScheduledJobOperationRequest(
                        id,
                        ScheduledJobOperation.Type.RUN_ONCE,
                        "PAPER_MATCHING",
                        null,
                        null,
                        null);
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS scheduler_proof_effects (operation_id uuid NOT NULL)");
        Field definitions = ScheduledJobRegistry.class.getDeclaredField("jobs");
        definitions.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ScheduledJobRegistry.Job> original =
                (Map<String, ScheduledJobRegistry.Job>) definitions.get(jobs);
        var controlled = new HashMap<>(original);
        // 合成任务先真实提交副作用再返回SUCCESS；不以mock调用次数替代持久事实。
        controlled.put(
                "PAPER_MATCHING",
                new ScheduledJobRegistry.Job(
                        "PAPER_MATCHING",
                        2000,
                        1000,
                        60000,
                        () -> true,
                        () -> {
                            jdbc.update(
                                    "INSERT INTO scheduler_proof_effects(operation_id) VALUES (?)",
                                    id);
                            return ScheduledJobRegistry.JobResult.success();
                        }));
        definitions.set(jobs, Map.copyOf(controlled));
        try {
            FAIL_COMPLETION_AUDIT.set(true);
            JsonNode first = json(send("POST", "/api/scheduler/operations", token, request));
            assertEquals("OUTCOME_UNKNOWN", first.path("status").asText());
            assertTrue(first.path("result").isNull());
            assertEquals(id, controls.find("PAPER_MATCHING").orElseThrow().activeRunId());
            assertEquals("RUNNING", controls.find("PAPER_MATCHING").orElseThrow().lastStatus());
            assertEquals(first, json(send("POST", "/api/scheduler/operations", token, request)));
            assertEquals(first, json(send("GET", "/api/scheduler/operations/" + id, token, null)));
            assertEquals(
                    409,
                    send(
                                    "POST",
                                    "/api/scheduler/operations",
                                    token,
                                    new ScheduledJobOperationRequest(
                                            UUID.randomUUID(),
                                            ScheduledJobOperation.Type.RUN_ONCE,
                                            "PAPER_MATCHING",
                                            null,
                                            null,
                                            null))
                            .statusCode());
            assertEquals(
                    SchedulerDispatcher.ExecutionOutcome.SKIPPED,
                    dispatcher.runOnce("PAPER_MATCHING"));
            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM scheduler_proof_effects WHERE operation_id=?",
                            Long.class,
                            id));
            assertEquals(
                    0L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM audit_logs WHERE action='RUN_ONCE' AND detail_json->>'operationId'=?",
                            Long.class,
                            id.toString()));
            // 完成事务的失败不取消原业务事实；仅原执行可在故障消失后记录真实结束结果。
            controls.complete("PAPER_MATCHING", id, Instant.now(), "SUCCESS", null, id);
            JsonNode completed = json(send("GET", "/api/scheduler/operations/" + id, token, null));
            assertEquals("COMPLETED", completed.path("status").asText());
            assertEquals("SUCCESS", completed.path("result").asText());
            assertEquals(
                    completed, json(send("POST", "/api/scheduler/operations", token, request)));
            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM scheduler_proof_effects WHERE operation_id=?",
                            Long.class,
                            id));
            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM audit_logs WHERE action='RUN_ONCE' AND detail_json->>'operationId'=?",
                            Long.class,
                            id.toString()));
        } finally {
            FAIL_COMPLETION_AUDIT.set(false);
            definitions.set(jobs, original);
        }
    }

    @Test
    void unavailableBackgroundSkipKeepsExpiredUnknownIdentityAndAllowsOriginalCompletion()
            throws Exception {
        String key = "STRATEGY_RECOVERY";
        UUID id = UUID.randomUUID();
        Instant started = Instant.now();
        var request =
                new ScheduledJobOperationRequest(
                        id, ScheduledJobOperation.Type.RUN_ONCE, key, null, null, null);
        var old = controls.find(key).orElseThrow();
        var fingerprint =
                ScheduledJobOperationService.class.getDeclaredMethod(
                        "fingerprint", ScheduledJobOperationRequest.class, long.class);
        fingerprint.setAccessible(true);
        String originalHash = (String) fingerprint.invoke(null, request, owner);
        Field dispatcherClock = SchedulerDispatcher.class.getDeclaredField("clock");
        dispatcherClock.setAccessible(true);
        Clock originalClock = (Clock) dispatcherClock.get(dispatcher);
        // 原领取发生在当前时刻，只有观察Clock前移六分钟；不回溯开始时间或改真实副作用时点。
        // 同事务建立全套状态，后台不会看到半套setup；不启用真实恢复链。
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        ignored -> {
                            receipts.create(
                                    request,
                                    owner,
                                    ownerName,
                                    originalHash,
                                    "scheduler-expired-unknown-proof",
                                    started);
                            assertTrue(
                                    controls.patch(
                                            key, old.version(), true, 5000, ownerName, started));
                            assertTrue(controls.claim(key, id, started, true, id));
                            receipts.markUnknown(id);
                        });
        try {
            dispatcherClock.set(dispatcher, Clock.fixed(started.plusSeconds(360), ZoneOffset.UTC));
            String before =
                    jdbc.queryForObject(
                            "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key=?",
                            String.class,
                            key);
            dispatcher.scheduledTick();
            controls.skipUnavailable(key, started.plusSeconds(360));
            assertEquals(
                    before,
                    jdbc.queryForObject(
                            "SELECT md5(to_jsonb(x)::text) FROM scheduled_job_controls x WHERE job_key=?",
                            String.class,
                            key));
            assertEquals(id, controls.find(key).orElseThrow().activeRunId());
            assertEquals(
                    ScheduledJobOperation.Status.OUTCOME_UNKNOWN,
                    receipts.find(id).orElseThrow().status());
            assertEquals(
                    "OUTCOME_UNKNOWN",
                    json(send("POST", "/api/scheduler/operations", token, request))
                            .path("status")
                            .asText());
            assertFalse(controls.claim(key, UUID.randomUUID(), started.plusSeconds(360), false));
            dispatcherClock.set(dispatcher, originalClock);
            controls.complete(key, id, Instant.now(), "SUCCESS", null, id);
            assertEquals(
                    ScheduledJobOperation.Status.COMPLETED,
                    receipts.find(id).orElseThrow().status());
            assertEquals(
                    "SUCCESS",
                    json(send("GET", "/api/scheduler/operations/" + id, token, null))
                            .path("result")
                            .asText());
            assertTrue(controls.find(key).orElseThrow().activeRunId() == null);
        } finally {
            dispatcherClock.set(dispatcher, originalClock);
            var current = controls.find(key).orElseThrow();
            controls.patch(key, current.version(), false, 5000, ownerName, Instant.now());
        }
    }

    private String login(String name) throws Exception {
        return json(send(
                        "POST",
                        "/api/auth/login",
                        null,
                        Map.of("username", name, "password", PASSWORD)))
                .path("accessToken")
                .asText();
    }

    private HttpResponse<String> send(String method, String path, String bearer, Object body)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(25));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else
            request.header("Content-Type", "application/json")
                    .method(
                            method,
                            HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        assertEquals(
                200,
                response.statusCode(),
                "unexpected scoped scheduler HTTP response: " + response.body());
        JsonNode root = mapper.readTree(response.body());
        return root.has("data") ? root.path("data") : root;
    }
}

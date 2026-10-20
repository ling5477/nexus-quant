package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.app.marketdata.PublicMarketCaptureOperationService.OperationView;
import com.sun.net.httpserver.HttpServer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

/** 真实PG迁移、模板事务、行锁、失败冻结和回滚重读；本机来源是明确的协议夹具，不是真实OKX资格。 */
@EnabledIfSystemProperty(named = "nq.public.capture.pg.required", matches = "true")
@Timeout(120)
class PublicMarketCaptureOperationPostgresIntegrationTest {
    private static final Instant START = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-23T00:00:00Z");
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void upgradesWithExistingCaptureAndPreservesOriginalOwnershipAtomicityAndDurableResults()
            throws Exception {
        String maintenance = System.getenv("TASK_PUBLIC_CAPTURE_MAINTENANCE_URL");
        String user = System.getenv("TASK_PUBLIC_CAPTURE_DB_USER");
        String password = System.getenv("TASK_PUBLIC_CAPTURE_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        assertNotNull(user);
        assertNotNull(password);
        String database = "public_capture_" + UUID.randomUUID().toString().replace("-", "");
        String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(System.getProperty("nq.public.capture.pg.evidence")).resolve(database);
        Files.createDirectories(evidence);
        var control = new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password));
        control.setQueryTimeout(10);
        control.execute("CREATE DATABASE " + database);
        boolean passed = false;
        try (var protocol = new ProtocolSource();
                var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var dataSource = new DriverManagerDataSource(url, user, password);
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.setQueryTimeout(10);
            migrate(url, user, password, "24");
            jdbc.update(
                    "INSERT INTO users(id,username,password_hash) VALUES (1,'capture-owner','!test-only-not-login'),(2,'capture-other','!test-only-not-login')");
            var manager = new DataSourceTransactionManager(dataSource);
            var originalTemplate = new TransactionTemplate(manager);
            originalTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            var provider =
                    new PublicMarketReplayCaptureService(
                            HttpClient.newHttpClient(),
                            protocol.origin(),
                            mapper,
                            jdbc,
                            originalTemplate,
                            Clock.systemUTC());
            var legacy = provider.capture(START, END, "capture-owner");
            String before = fingerprint(jdbc);
            migrate(url, user, password, "25");
            assertEquals(before, fingerprint(jdbc), "升级不改写已有捕获或数据集");
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM public_market_capture_operations",
                            Integer.class));
            Files.writeString(
                    evidence.resolve("v25-applied.txt"),
                    "V1-V25 migrated and validated; existing formal legacy capture fingerprint preserved\n",
                    StandardCharsets.UTF_8);

            OperationView success;
            OperationView failed;
            UUID rollbackId = UUID.randomUUID();
            try (var context = context(dataSource, provider);
                    var another = context(dataSource, provider)) {
                var service = context.getBean(PublicMarketCaptureOperationService.class);
                var second = another.getBean(PublicMarketCaptureOperationService.class);
                UUID id = UUID.randomUUID();
                int calls = protocol.calls.get();
                success = service.submit(id, START, END, 1, "capture-owner");
                assertEquals("SUCCEEDED", success.state());
                assertEquals(id, success.result().datasetId());
                assertEquals(72, success.result().barCount());
                assertEquals(calls + 2, protocol.calls.get());
                assertEquals(success, service.submit(id, START, END, 1, "capture-owner"));
                assertEquals(success, service.find(id, 1).orElseThrow());
                assertTrue(service.find(id, 2).isEmpty());
                assertThrows(
                        IllegalStateException.class,
                        () -> service.submit(id, START.plusSeconds(3600), END, 1, "capture-owner"));
                assertThrows(
                        IllegalStateException.class,
                        () -> service.submit(id, START, END, 2, "capture-other"));
                assertEquals(calls + 2, protocol.calls.get());

                protocol.rateLimited.set(true);
                UUID failureId = UUID.randomUUID();
                failed = service.submit(failureId, START, END, 1, "capture-owner");
                assertEquals("FAILED", failed.state());
                assertEquals("PUBLIC_MARKET_RATE_LIMITED", failed.failureCode());
                assertEquals(0, count(jdbc, "marketdata_datasets", failureId));
                calls = protocol.calls.get();
                protocol.rateLimited.set(false);
                assertEquals(failed, service.submit(failureId, START, END, 1, "capture-owner"));
                assertEquals(calls, protocol.calls.get(), "已确认失败不能自动再次访问来源");

                UUID concurrentId = UUID.randomUUID();
                var gate = new Gate();
                protocol.gate.set(gate);
                var first =
                        threads.submit(
                                () -> service.submit(concurrentId, START, END, 1, "capture-owner"));
                assertTrue(gate.entered.await(3, TimeUnit.SECONDS));
                assertEquals("PENDING", second.find(concurrentId, 1).orElseThrow().state());
                var duplicate =
                        threads.submit(
                                () -> second.submit(concurrentId, START, END, 1, "capture-owner"));
                assertTrue(observeBlockedLock(jdbc, database), "必须实际观察另一事务在原请求行锁上等待");
                gate.release.countDown();
                protocol.gate.set(null);
                assertEquals(first.get(10, TimeUnit.SECONDS), duplicate.get(10, TimeUnit.SECONDS));
                assertEquals(1, count(jdbc, "public_market_captures", concurrentId));

                // 注入完成阶段约束失败，验证即使来源已读取、两张事实表已写入，也不会拆开提交。
                jdbc.execute(
                        "CREATE FUNCTION test_capture_commit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'isolated test commit failure' USING ERRCODE='23514'; END $$");
                jdbc.execute(
                        "CREATE TRIGGER test_capture_commit_failure BEFORE UPDATE ON public_market_capture_operations FOR EACH ROW WHEN (NEW.capture_id='"
                                + rollbackId
                                + "'::uuid AND NEW.state='SUCCEEDED') EXECUTE FUNCTION test_capture_commit_failure()");
                assertThrows(
                        DataAccessException.class,
                        () -> service.submit(rollbackId, START, END, 1, "capture-owner"));
                assertEquals("PENDING", service.find(rollbackId, 1).orElseThrow().state());
                assertEquals(0, count(jdbc, "marketdata_datasets", rollbackId));
                assertEquals(0, count(jdbc, "public_market_captures", rollbackId));
                jdbc.execute(
                        "DROP TRIGGER test_capture_commit_failure ON public_market_capture_operations");
                jdbc.execute("DROP FUNCTION test_capture_commit_failure()");

                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE public_market_capture_operations SET requested_end=requested_end+interval '1 hour' WHERE capture_id=?",
                                        rollbackId));
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE public_market_capture_operations SET failure_code='PUBLIC_CHANGED' WHERE capture_id=?",
                                        failureId));
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        "DELETE FROM public_market_capture_operations WHERE capture_id=?",
                                        id));
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        """
                        INSERT INTO public_market_capture_operations(capture_id,owner_user_id,actor,requested_start,requested_end,state,created_at,updated_at)
                        VALUES (?,1,'capture-owner',?::timestamptz,?::timestamptz,'PENDING',now(),now())
                        """,
                                        UUID.randomUUID(),
                                        "2026-09-20T00:00:00.000500Z",
                                        "2026-09-20T01:00:00.000500Z"));
            }

            try (var reopened = context(dataSource, null)) {
                var service = reopened.getBean(PublicMarketCaptureOperationService.class);
                int calls = protocol.calls.get();
                assertEquals(success, service.find(success.captureId(), 1).orElseThrow());
                assertEquals(
                        success,
                        service.submit(success.captureId(), START, END, 1, "capture-owner"));
                assertEquals(
                        failed, service.submit(failed.captureId(), START, END, 1, "capture-owner"));
                assertEquals("PENDING", service.find(rollbackId, 1).orElseThrow().state());
                assertEquals(calls, protocol.calls.get(), "来源关闭时查询与终态重放仍不得外联");
            }
            try (var reopened = context(dataSource, provider)) {
                var service = reopened.getBean(PublicMarketCaptureOperationService.class);
                assertEquals(
                        "SUCCEEDED",
                        service.submit(rollbackId, START, END, 1, "capture-owner").state());
                assertEquals(1, count(jdbc, "marketdata_datasets", rollbackId));
                assertEquals(1, count(jdbc, "public_market_captures", rollbackId));
            }
            assertFalse(protocol.credentialHeaderSeen.get());
            assertEquals(
                    legacy.datasetId(),
                    jdbc.queryForObject(
                            "SELECT dataset_id FROM public_market_captures WHERE dataset_id=?",
                            UUID.class,
                            legacy.datasetId()));
            for (String table :
                    List.of(
                            "accounts",
                            "orders",
                            "trades",
                            "ledger_entries",
                            "paper_trading_runs",
                            "strategy_runs")) {
                assertEquals(
                        0, jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class));
            }
            Files.writeString(
                    evidence.resolve("result.json"),
                    mapper.writeValueAsString(
                            jdbc.queryForMap(
                                    """
                    SELECT (SELECT count(*) FROM public_market_capture_operations) AS operations,
                           (SELECT count(*) FROM public_market_captures) AS captures,
                           (SELECT count(*) FROM marketdata_datasets) AS datasets,
                           (SELECT max(installed_rank) FROM flyway_schema_history) AS migration_count
                    """)),
                    StandardCharsets.UTF_8);
            passed = true;
        } finally {
            if (passed) control.execute("DROP DATABASE " + database);
        }
    }

    private void migrate(String url, String user, String password, String target) {
        var flyway =
                Flyway.configure()
                        .dataSource(url, user, password)
                        .locations("classpath:db/migration")
                        .target(target)
                        .cleanDisabled(true)
                        .load();
        flyway.migrate();
        flyway.validate();
    }

    private AnnotationConfigApplicationContext context(
            DataSource source, PublicMarketReplayCaptureService provider) {
        var context = new AnnotationConfigApplicationContext();
        var environment =
                new MockEnvironment().withProperty("nq.public-marketdata.outbound.enabled", "true");
        environment.setActiveProfiles("public-marketdata-manual");
        context.setEnvironment(environment);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(source));
        context.registerBean(ObjectMapper.class, () -> mapper);
        context.registerBean(
                PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source));
        if (provider != null)
            context.registerBean(PublicMarketReplayCaptureService.class, () -> provider);
        context.register(PublicMarketCaptureOperationService.class);
        context.refresh();
        return context;
    }

    private static int count(JdbcTemplate jdbc, String table, UUID id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE dataset_id=?", Integer.class, id);
    }

    private static String fingerprint(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                """
                SELECT md5(jsonb_build_object('captures',(SELECT jsonb_agg(to_jsonb(c) ORDER BY dataset_id) FROM public_market_captures c),
                         'datasets',(SELECT jsonb_agg(to_jsonb(d) ORDER BY dataset_id) FROM marketdata_datasets d))::text)
                """,
                String.class);
    }

    private static boolean observeBlockedLock(JdbcTemplate jdbc, String database)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            Integer waiting =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM pg_stat_activity WHERE datname=? AND query LIKE '%public_market_capture_operations%FOR UPDATE%' AND cardinality(pg_blocking_pids(pid))>0",
                            Integer.class, database);
            if (waiting > 0) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
    }

    private static final class ProtocolSource implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicBoolean rateLimited = new AtomicBoolean();
        private final AtomicBoolean credentialHeaderSeen = new AtomicBoolean();
        private final AtomicReference<Gate> gate = new AtomicReference<>();

        private ProtocolSource() throws Exception {
            byte[] candles;
            byte[] rules;
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/public-market-replay/okx-btc-usdt-1h-20260920-20260923.json")) {
                candles = input.readAllBytes();
            }
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/public-market-replay/okx-btc-usdt-public-instrument-20260925.json")) {
                rules = input.readAllBytes();
            }
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        calls.incrementAndGet();
                        if (exchange.getRequestHeaders().containsKey("Authorization")
                                || exchange.getRequestHeaders().containsKey("OK-ACCESS-KEY"))
                            credentialHeaderSeen.set(true);
                        Gate current = gate.get();
                        if (current != null
                                && exchange.getRequestURI().getPath().contains("history-candles")) {
                            current.entered.countDown();
                            try {
                                current.release.await(7, TimeUnit.SECONDS);
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        byte[] body =
                                rateLimited.get()
                                        ? "{\"code\":\"50011\",\"msg\":\"explicit fixture limit\"}"
                                                .getBytes(StandardCharsets.UTF_8)
                                        : exchange.getRequestURI()
                                                        .getPath()
                                                        .contains("history-candles")
                                                ? candles
                                                : rules;
                        exchange.sendResponseHeaders(rateLimited.get() ? 429 : 200, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            server.start();
        }

        private URI origin() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            Gate current = gate.get();
            if (current != null) current.release.countDown();
            server.stop(0);
        }
    }
}

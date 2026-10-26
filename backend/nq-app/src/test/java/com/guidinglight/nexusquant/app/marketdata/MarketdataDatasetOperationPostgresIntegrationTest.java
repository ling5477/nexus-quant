package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.app.marketdata.MarketdataDatasetOperationService.OperationView;
import com.guidinglight.nexusquant.marketdata.api.dto.MarketdataDatasetResponse;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetCoverage;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetRepository.QualityWriter;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataDatasetRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.AdmissionMutationCoordinator;
import com.sun.net.httpserver.HttpServer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

/** 真实隔离PG、Spring事务代理和多上下文锁证明；不是Servlet、浏览器或真实公开来源资格。 */
@EnabledIfSystemProperty(named = "nq.dataset.operation.pg.required", matches = "true")
@Timeout(120)
class MarketdataDatasetOperationPostgresIntegrationTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void preservesLegacyFactsAtomicWritesOriginalResultsAndOrderedRealLocks() throws Exception {
        String maintenance = System.getenv("TASK_DATASET_MAINTENANCE_URL"),
                user = System.getenv("TASK_DATASET_DB_USER"),
                password = System.getenv("TASK_DATASET_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        assertNotNull(user);
        assertNotNull(password);
        String database = "dataset_operation_" + UUID.randomUUID().toString().replace("-", "");
        String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + database;
        Path evidence =
                Path.of(System.getProperty("nq.dataset.operation.pg.evidence")).resolve(database);
        Files.createDirectories(evidence);
        var control = new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password));
        control.execute("CREATE DATABASE " + database);
        var source = new DriverManagerDataSource(url, user, password);
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        var gate = new AtomicReference<Gate>();
        var calculations = new AtomicInteger();
        var checks = new ArrayList<String>();
        boolean passed = false;
        try (var workers = Executors.newFixedThreadPool(3)) {
            migrate(url, user, password, "25");
            jdbc.update(
                    "INSERT INTO users(id,username,password_hash,enabled,must_change_password)"
                        + " VALUES(17,'dataset_owner','unused-test-hash',true,false),(18,'dataset_other','unused-test-hash',true,false)");
            UUID legacyId;
            try (var context = context(source, gate, calculations, false)) {
                var service = context.getBean(MarketdataDatasetService.class);
                assertTrue(AopUtils.isAopProxy(service));
                legacyId = service.createDataset(command("legacy")).datasetId();
            }
            String before = fingerprint(jdbc);
            migrate(url, user, password, "26");
            assertEquals(before, fingerprint(jdbc));
            byte[] migration;
            try (var resource =
                    getClass()
                            .getResourceAsStream(
                                    "/db/migration/V26__marketdata_dataset_operations.sql")) {
                migration = resource.readAllBytes();
            }
            // 迁移一经成功执行即记录精确身份；之后只能新增forward migration修正，不就地改写。
            Files.writeString(
                    evidence.resolve("migration-v26-applied.sha256"),
                    HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(migration)));
            checks.add(
                    "V25 real legacy dataset/coverage -> V26 migrate/validate with exact old"
                            + " fingerprint preserved");
            OperationView created;
            UUID createId = UUID.randomUUID();
            try (var context = context(source, gate, calculations, true);
                    var other = context(source, gate, calculations, true)) {
                var service = context.getBean(MarketdataDatasetService.class);
                var operations = context.getBean(MarketdataDatasetOperationService.class);
                var otherOperations = other.getBean(MarketdataDatasetOperationService.class);
                var manager = context.getBean(PlatformTransactionManager.class);
                var failures =
                        List.of(
                                command(""),
                                command("x".repeat(256)),
                                new CreateMarketdataDatasetCommand(
                                        "bad",
                                        "BINANCE",
                                        "SPOT",
                                        "BTC-USDT",
                                        "1m",
                                        START,
                                        START,
                                        "dataset_owner"),
                                new CreateMarketdataDatasetCommand(
                                        "bad",
                                        "BINANCE",
                                        "SPOT",
                                        "BTC-USDT",
                                        "1m",
                                        START.plusNanos(1),
                                        START.plusSeconds(60),
                                        "dataset_owner"));
                for (var invalid : failures)
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    operations.create(
                                            UUID.randomUUID(), invalid, 17, "dataset_owner"));
                assertEquals(0, count(jdbc, "marketdata_dataset_operations"));
                assertEquals(
                        "BNB-USDT",
                        service.normalizeCreateCommand(
                                        new CreateMarketdataDatasetCommand(
                                                " bnb ",
                                                "binance",
                                                "spot",
                                                "bnb-usdt",
                                                "1h",
                                                START,
                                                START.plusSeconds(3600),
                                                "dataset_owner"))
                                .symbol());
                for (String interval : List.of("1m", "5m", "15m", "1h", "4h", "1d")) {
                    assertEquals(
                            interval,
                            service.normalizeCreateCommand(
                                            new CreateMarketdataDatasetCommand(
                                                    "interval",
                                                    "BINANCE",
                                                    "SPOT",
                                                    "BTC-USDT",
                                                    interval,
                                                    START,
                                                    START.plusSeconds(86400),
                                                    "dataset_owner"))
                                    .interval());
                }
                checks.add(
                        "Canonical validation keeps BNB and existing six periods, rejects"
                                + " inverse/oversized name/nonroundtrip time before registration");

                jdbc.execute(
                        "CREATE FUNCTION reject_dataset_coverage_probe() RETURNS trigger LANGUAGE"
                            + " plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled coverage failure'"
                            + " USING ERRCODE='23514'; END; $$");
                jdbc.execute(
                        "CREATE TRIGGER reject_dataset_coverage_probe BEFORE INSERT ON"
                                + " marketdata_dataset_coverage FOR EACH ROW EXECUTE FUNCTION"
                                + " reject_dataset_coverage_probe()");
                long datasetsBefore = count(jdbc, "marketdata_datasets"),
                        coverageBefore = count(jdbc, "marketdata_dataset_coverage");
                assertThrows(
                        DataAccessException.class,
                        () -> service.createDataset(command("legacy-rejected")));
                assertEquals(datasetsBefore, count(jdbc, "marketdata_datasets"));
                assertEquals(coverageBefore, count(jdbc, "marketdata_dataset_coverage"));
                assertThrows(
                        DataAccessException.class,
                        () -> operations.create(createId, command("stable"), 17, "dataset_owner"));
                assertEquals("PENDING", operations.find(createId, 17).orElseThrow().state());
                assertEquals(0, rows(jdbc, "marketdata_datasets", "dataset_id", createId));
                assertEquals(0, rows(jdbc, "marketdata_dataset_coverage", "coverage_id", createId));
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE marketdata_dataset_operations SET"
                                                + " actor='dataset_other' WHERE operation_id=?",
                                        createId));
                jdbc.execute(
                        "DROP TRIGGER reject_dataset_coverage_probe ON"
                                + " marketdata_dataset_coverage");
                created = operations.create(createId, command("stable"), 17, "dataset_owner");
                assertEquals("SUCCEEDED", created.state());
                assertEquals(createId, created.result().coverage().coverageId());
                assertEquals(1, rows(jdbc, "marketdata_datasets", "dataset_id", createId));
                assertEquals(1, rows(jdbc, "marketdata_dataset_coverage", "coverage_id", createId));
                assertEquals(
                        created,
                        operations.create(createId, command("stable"), 17, "dataset_owner"));
                assertTrue(operations.find(createId, 18).isEmpty());
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                operations.create(
                                        createId, command("different"), 17, "dataset_owner"));
                assertThrows(
                        IllegalStateException.class,
                        () -> operations.create(createId, command("stable"), 18, "dataset_other"));
                jdbc.update("UPDATE users SET username='dataset_renamed' WHERE id=17");
                assertEquals(
                        created,
                        operations.create(createId, command("stable"), 17, "dataset_renamed"));
                jdbc.update("UPDATE users SET username='dataset_owner' WHERE id=17");
                checks.add(
                        "Prechange orphan counterexample closed: legacy and stable failed coverage"
                            + " writes roll back dataset, stable independent PENDING survives; same"
                            + " original resumes once, owner/input immutable");

                UUID completionId = UUID.randomUUID();
                jdbc.execute(
                        "CREATE FUNCTION reject_dataset_completion_probe() RETURNS trigger LANGUAGE"
                                + " plpgsql AS $$ BEGIN IF NEW.operation_id='"
                                + completionId
                                + "'::uuid AND NEW.state='SUCCEEDED' THEN RAISE EXCEPTION"
                                + " 'controlled receipt failure' USING ERRCODE='23514'; END IF;"
                                + " RETURN NEW; END; $$");
                jdbc.execute(
                        "CREATE TRIGGER reject_dataset_completion_probe BEFORE UPDATE ON"
                                + " marketdata_dataset_operations FOR EACH ROW EXECUTE FUNCTION"
                                + " reject_dataset_completion_probe()");
                assertThrows(
                        DataAccessException.class,
                        () ->
                                operations.create(
                                        completionId, command("completion"), 17, "dataset_owner"));
                assertEquals("PENDING", operations.find(completionId, 17).orElseThrow().state());
                assertEquals(0, rows(jdbc, "marketdata_datasets", "dataset_id", completionId));
                assertEquals(
                        0, rows(jdbc, "marketdata_dataset_coverage", "coverage_id", completionId));
                jdbc.execute(
                        "DROP TRIGGER reject_dataset_completion_probe ON"
                                + " marketdata_dataset_operations");
                assertEquals(
                        "SUCCEEDED",
                        operations
                                .create(completionId, command("completion"), 17, "dataset_owner")
                                .state());
                checks.add(
                        "Completion-trigger failure rolls back dataset, coverage, primary quality"
                            + " and success receipt together; original PENDING remains and same ID"
                            + " recovers");

                UUID refreshId = UUID.randomUUID();
                long oldCoverage = count(jdbc, "marketdata_dataset_coverage");
                MarketdataDataset old = service.getDataset(legacyId);
                jdbc.execute(
                        "CREATE FUNCTION reject_dataset_update_probe() RETURNS trigger LANGUAGE"
                                + " plpgsql AS $$ BEGIN IF NEW.dataset_id='"
                                + legacyId
                                + "'::uuid THEN RAISE EXCEPTION 'controlled quality update failure'"
                                + " USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");
                jdbc.execute(
                        "CREATE TRIGGER reject_dataset_update_probe BEFORE UPDATE ON"
                                + " marketdata_datasets FOR EACH ROW EXECUTE FUNCTION"
                                + " reject_dataset_update_probe()");
                assertThrows(DataAccessException.class, () -> service.refreshQuality(legacyId));
                assertEquals(oldCoverage, count(jdbc, "marketdata_dataset_coverage"));
                assertEquals(old, service.getDataset(legacyId));
                assertThrows(
                        DataAccessException.class,
                        () -> operations.refresh(refreshId, legacyId, 17, "dataset_owner"));
                assertEquals("PENDING", operations.find(refreshId, 17).orElseThrow().state());
                assertEquals(oldCoverage, count(jdbc, "marketdata_dataset_coverage"));
                jdbc.execute("DROP TRIGGER reject_dataset_update_probe ON marketdata_datasets");
                OperationView refresh =
                        operations.refresh(refreshId, legacyId, 17, "dataset_owner");
                assertEquals("SUCCEEDED", refresh.state());
                service.refreshQuality(legacyId);
                assertEquals(refresh, operations.find(refreshId, 17).orElseThrow());
                assertEquals(refresh, operations.refresh(refreshId, legacyId, 17, "dataset_owner"));
                var escaped = new AtomicReference<QualityWriter>();
                context.getBean(MarketdataDatasetRepository.class)
                        .withQualityMutationLock(
                                legacyId,
                                writer -> {
                                    escaped.set(writer);
                                    return true;
                                });
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                escaped.get()
                                        .update(
                                                old.status(),
                                                old.qualityStatus(),
                                                old.barCount(),
                                                old.gapCount(),
                                                old.updatedAt()));
                assertEquals(created, operations.find(createId, 17).orElseThrow());
                UUID nestedId = UUID.randomUUID();
                var nested = new TransactionTemplate(manager);
                nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                // 尚不存在的对象不能靠外层空行锁保护；内层正式创建提交后，外层写入器仍必须拒绝越界。
                new TransactionTemplate(manager)
                        .executeWithoutResult(
                                outer -> {
                                    context.getBean(MarketdataDatasetRepository.class)
                                            .withQualityMutationLock(
                                                    nestedId,
                                                    writer -> {
                                                        nested.executeWithoutResult(
                                                                inner -> {
                                                                    var actual =
                                                                            service.createDataset(
                                                                                    nestedId,
                                                                                    command(
                                                                                            "nested"));
                                                                    var error =
                                                                            assertThrows(
                                                                                    IllegalStateException
                                                                                            .class,
                                                                                    () ->
                                                                                            writer
                                                                                                    .update(
                                                                                                            actual.dataset()
                                                                                                                    .status(),
                                                                                                            actual.dataset()
                                                                                                                    .qualityStatus(),
                                                                                                            777,
                                                                                                            0,
                                                                                                            actual.dataset()
                                                                                                                    .updatedAt()));
                                                                    assertEquals(
                                                                            "DATASET_QUALITY_WRITER_OUT_OF_SCOPE",
                                                                            error.getMessage());
                                                                });
                                                        return true;
                                                    });
                                    outer.setRollbackOnly();
                                });
                assertEquals(0, service.getDataset(nestedId).barCount());
                assertEquals(
                        0L,
                        jdbc.queryForObject(
                                "SELECT actual_bars FROM marketdata_dataset_coverage WHERE"
                                        + " dataset_id=?",
                                Long.class,
                                nestedId));
                checks.add(
                        "Same-thread REQUIRES_NEW rejects the outer quality writer; inner canonical"
                            + " dataset/coverage commit remains consistent after outer rollback");
                checks.add(
                        "Legacy and stable refresh failures leave no orphan coverage or"
                                + " primary-state change; later refreshes cannot replace original"
                                + " immutable result snapshots");

                UUID missingId = UUID.randomUUID(), missingDataset = UUID.randomUUID();
                var missing = operations.refresh(missingId, missingDataset, 17, "dataset_owner");
                assertEquals("DATASET_NOT_FOUND", missing.failureCode());
                service.createDataset(missingDataset, command("arrived-later"));
                assertEquals(
                        missing,
                        operations.refresh(missingId, missingDataset, 17, "dataset_owner"));
                assertEquals(
                        "DATASET_ID_OCCUPIED",
                        operations
                                .create(legacyId, command("occupied"), 17, "dataset_owner")
                                .failureCode());
                UUID publicId = publicCapture(jdbc, manager);
                assertEquals(
                        "PUBLIC_CAPTURE_DATASET_IMMUTABLE",
                        operations
                                .refresh(UUID.randomUUID(), publicId, 17, "dataset_owner")
                                .failureCode());
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE marketdata_dataset_operations SET"
                                                + " result_json='{}'::jsonb WHERE operation_id=?",
                                        createId));
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        "DELETE FROM marketdata_dataset_operations WHERE"
                                                + " operation_id=?",
                                        missingId));
                assertThrows(
                        DataAccessException.class,
                        () ->
                                jdbc.update(
                                        "UPDATE marketdata_dataset_operations SET state='PENDING'"
                                                + " WHERE operation_id=?",
                                        createId));
                checks.add(
                        "Missing, occupied and public-immutable targets freeze bounded failures;"
                                + " later target creation does not turn old failure into success;"
                                + " direct terminal mutation/deletion rejected");

                UUID concurrentId = UUID.randomUUID();
                Gate same = new Gate();
                gate.set(same);
                int callsBefore = calculations.get();
                var left =
                        workers.submit(
                                () ->
                                        operations.create(
                                                concurrentId,
                                                command("concurrent"),
                                                17,
                                                "dataset_owner"));
                assertTrue(same.entered.await(5, TimeUnit.SECONDS));
                assertEquals(
                        "PENDING", otherOperations.find(concurrentId, 17).orElseThrow().state());
                var right =
                        workers.submit(
                                () ->
                                        otherOperations.create(
                                                concurrentId,
                                                command("concurrent"),
                                                17,
                                                "dataset_owner"));
                assertTrue(waiting(jdbc, "marketdata_dataset_operations"));
                same.release.countDown();
                assertEquals(left.get(8, TimeUnit.SECONDS), right.get(8, TimeUnit.SECONDS));
                gate.set(null);
                assertEquals(callsBefore + 1, calculations.get());
                assertEquals(
                        1, rows(jdbc, "marketdata_dataset_coverage", "coverage_id", concurrentId));
                checks.add(
                        "Two independent application contexts: same UUID has actual PG lock wait,"
                                + " independently committed PENDING and exactly one"
                                + " calculation/coverage");

                Gate different = new Gate();
                gate.set(different);
                UUID firstRefresh = UUID.randomUUID(), secondRefresh = UUID.randomUUID();
                var earlier =
                        workers.submit(
                                () ->
                                        operations.refresh(
                                                firstRefresh, legacyId, 17, "dataset_owner"));
                assertTrue(different.entered.await(5, TimeUnit.SECONDS));
                var later =
                        workers.submit(
                                () ->
                                        otherOperations.refresh(
                                                secondRefresh, legacyId, 17, "dataset_owner"));
                assertTrue(waiting(jdbc, "marketdata_datasets"));
                different.release.countDown();
                var early = earlier.get(8, TimeUnit.SECONDS);
                var late = later.get(8, TimeUnit.SECONDS);
                gate.set(null);
                assertFalse(
                        late.result()
                                .coverage()
                                .createdAt()
                                .isBefore(early.result().coverage().createdAt()));
                assertEquals(early, operations.find(firstRefresh, 17).orElseThrow());
                assertEquals(
                        late.result().dataset(),
                        MarketdataDatasetResponse.from(service.getDataset(legacyId)));
                checks.add(
                        "Different refresh IDs serialize on actual dataset row across contexts;"
                                + " second calculation starts after lock and old original receipt"
                                + " remains exact");

                String publishId = seedPublishedReadFixture(jdbc, legacyId);
                var entered = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                var holder =
                        workers.submit(
                                () ->
                                        new TransactionTemplate(manager)
                                                .execute(
                                                        status -> {
                                                            jdbc.queryForList(
                                                                    "SELECT publish_record_id FROM"
                                                                        + " strategy_release_admission_state"
                                                                        + " WHERE"
                                                                        + " publish_record_id=? FOR"
                                                                        + " UPDATE",
                                                                    String.class,
                                                                    publishId);
                                                            entered.countDown();
                                                            await(release);
                                                            return true;
                                                        }));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var ordered =
                        workers.submit(
                                () ->
                                        operations.refresh(
                                                UUID.randomUUID(), legacyId, 17, "dataset_owner"));
                assertTrue(waiting(jdbc, "strategy_release_admission_state"));
                new TransactionTemplate(manager)
                        .executeWithoutResult(
                                status ->
                                        jdbc.queryForList(
                                                "SELECT dataset_id FROM marketdata_datasets WHERE"
                                                        + " dataset_id=? FOR UPDATE NOWAIT",
                                                UUID.class,
                                                legacyId));
                release.countDown();
                assertTrue(holder.get(8, TimeUnit.SECONDS));
                assertEquals("SUCCEEDED", ordered.get(8, TimeUnit.SECONDS).state());
                checks.add(
                        "Published-dataset lock ordering: when admission state held, refresh waits"
                            + " there while another transaction can NOWAIT-lock dataset; original"
                            + " state-first order preserved");
            }
            try (var reopened = context(source, gate, calculations, true)) {
                var operations = reopened.getBean(MarketdataDatasetOperationService.class);
                assertEquals(created, operations.find(createId, 17).orElseThrow());
                assertEquals(
                        created,
                        operations.create(createId, command("stable"), 17, "dataset_owner"));
                assertEquals(0, count(jdbc, "orders"));
                assertEquals(0, count(jdbc, "trades"));
                assertEquals(0, count(jdbc, "ledger_entries"));
                checks.add(
                        "Reopened Spring context returns same original receipt and source-free"
                                + " replay; zero order/trade/ledger facts; supporting published"
                                + " metadata is an explicit isolated SQL fixture");
            }
            Files.writeString(
                    evidence.resolve("result.json"),
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(
                                    Map.of(
                                            "status",
                                            "PASS_SCOPED_REAL_PG",
                                            "database",
                                            database,
                                            "checks",
                                            checks,
                                            "notCovered",
                                            "Servlet/GUI/new JVM/real exchange")));
            passed = true;
        } finally {
            if (passed) control.execute("DROP DATABASE " + database);
        }
    }

    private CreateMarketdataDatasetCommand command(String name) {
        return new CreateMarketdataDatasetCommand(
                name,
                "BINANCE",
                "SPOT",
                "BTC-USDT",
                "1m",
                START,
                START.plusSeconds(180),
                "dataset_owner");
    }

    private void migrate(String url, String user, String password, String version) {
        var flyway =
                Flyway.configure()
                        .dataSource(url, user, password)
                        .locations("classpath:db/migration")
                        .target(version)
                        .cleanDisabled(true)
                        .load();
        flyway.migrate();
        flyway.validate();
    }

    @Configuration
    @EnableTransactionManagement
    static class Transactions {}

    private AnnotationConfigApplicationContext context(
            DataSource source,
            AtomicReference<Gate> gate,
            AtomicInteger calculations,
            boolean operations) {
        var context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(ObjectMapper.class, () -> mapper);
        context.registerBean(
                JdbcTemplate.class,
                () -> {
                    var value = new JdbcTemplate(source);
                    value.setQueryTimeout(10);
                    return value;
                });
        context.registerBean(
                PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort.class,
                () -> new com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcHistoricalMarketDataPort(
                        context.getBean(JdbcTemplate.class)));
        context.registerBean(
                AdmissionMutationCoordinator.class,
                () ->
                        new JdbcAdmissionMutationCoordinator(
                                context.getBean(JdbcTemplate.class),
                                context.getBean(PlatformTransactionManager.class),
                                256));
        context.registerBean(
                MarketdataDatasetRepository.class,
                () ->
                        new JdbcMarketdataDatasetRepository(
                                context.getBean(JdbcTemplate.class),
                                mapper,
                                context.getBean(AdmissionMutationCoordinator.class)) {
                            @Override
                            public MarketdataDatasetCoverage calculateCoverage(
                                    MarketdataDataset dataset, Instant at) {
                                calculations.incrementAndGet();
                                Gate current = gate.get();
                                if (current != null && current.used.compareAndSet(false, true)) {
                                    current.entered.countDown();
                                    await(current.release);
                                }
                                return super.calculateCoverage(dataset, at);
                            }
                        });
        context.register(MarketdataDatasetService.class);
        if (operations) context.register(MarketdataDatasetOperationService.class);
        context.refresh();
        return context;
    }

    private static class Gate {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean used = new AtomicBoolean();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS))
                throw new IllegalStateException("controlled wait timeout");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static boolean waiting(JdbcTemplate jdbc, String table) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < end) {
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()"
                                    + " AND query LIKE ? AND cardinality(pg_blocking_pids(pid))>0",
                            Integer.class,
                            "%" + table + "%FOR UPDATE%")
                    > 0) return true;
            Thread.sleep(20);
        }
        return false;
    }

    private static long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static long rows(JdbcTemplate jdbc, String table, String column, UUID id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + "=?", Long.class, id);
    }

    private static String fingerprint(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT md5(jsonb_build_object('datasets',(SELECT jsonb_agg(to_jsonb(d) ORDER BY"
                        + " dataset_id) FROM marketdata_datasets d),'coverage',(SELECT"
                        + " jsonb_agg(to_jsonb(c) ORDER BY coverage_id) FROM"
                        + " marketdata_dataset_coverage c))::text)",
                String.class);
    }

    private UUID publicCapture(JdbcTemplate jdbc, PlatformTransactionManager manager)
            throws Exception {
        byte[] candles, rules;
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
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    byte[] body =
                            exchange.getRequestURI().getPath().contains("history-candles")
                                    ? candles
                                    : rules;
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.start();
        try {
            var capture =
                    new PublicMarketReplayCaptureService(
                            HttpClient.newHttpClient(),
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                            mapper,
                            jdbc,
                            new TransactionTemplate(manager),
                            Clock.systemUTC());
            return capture.capture(
                            Instant.parse("2026-09-20T00:00:00Z"),
                            Instant.parse("2026-09-23T00:00:00Z"),
                            "dataset_owner")
                    .datasetId();
        } finally {
            server.stop(0);
        }
    }

    /** 只构造发布关系和准入锁元数据，不作为真实发布、交易或完整业务链证明。 */
    private String seedPublishedReadFixture(JdbcTemplate jdbc, UUID dataset) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String strategy = "dataset-strategy-" + suffix,
                code = "DATASET_" + suffix,
                version = "dataset-version-" + suffix,
                research = "dataset-research-" + suffix,
                config = "dataset-config-" + suffix,
                run = "dataset-run-" + suffix,
                evaluation = "dataset-eval-" + suffix,
                publish = "dataset-publish-" + suffix;
        Long account =
                jdbc.queryForObject(
                        "INSERT INTO accounts(account_code,venue,status) VALUES(?,'PAPER','ACTIVE')"
                                + " RETURNING account_id",
                        Long.class,
                        "dataset-fixture-" + suffix);
        jdbc.update(
                "INSERT INTO"
                    + " strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)"
                    + " VALUES(?,?,'dataset lock"
                    + " fixture','ADMISSION_GUARD','PAPER',?,'SIM',false,'{}'::jsonb,1)",
                strategy,
                code,
                account);
        jdbc.update(
                "INSERT INTO"
                    + " research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)"
                    + " VALUES(?,?,'dataset fixture','{}'::jsonb)",
                research,
                strategy);
        jdbc.update(
                "INSERT INTO backtest_configs(backtest_config_id,research_config_id,name)"
                        + " VALUES(?,?,'dataset fixture')",
                config,
                research);
        jdbc.update(
                "INSERT INTO"
                    + " strategy_versions(strategy_version_id,strategy_code,version,version_name,status,checksum,created_by)"
                    + " VALUES(?,?,1,'dataset fixture','ACTIVE',?,'dataset_owner')",
                version,
                code,
                "a".repeat(64));
        jdbc.update(
                "INSERT INTO"
                    + " backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,strategy_snapshot,strategy_version_id,backtest_config_snapshot,dataset_snapshot_json,requested_at)"
                    + " VALUES(?,?,?,?,'SUCCEEDED','{}'::jsonb,?,'{}'::jsonb,jsonb_build_object('datasetId',?::text),now())",
                run,
                config,
                research,
                strategy,
                version,
                dataset.toString());
        jdbc.update(
                "INSERT INTO"
                    + " backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status,evaluated_at)"
                    + " VALUES(?,?,'SUCCEEDED',now())",
                evaluation,
                run);
        jdbc.update(
                "INSERT INTO"
                    + " backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,eval_report_id,strategy_version_id,publish_status,publish_name,artifact_storage_key,manifest_storage_key)"
                    + " VALUES(?,?,?,?,?,?,?,'SUCCEEDED','dataset fixture',?,?)",
                publish,
                run,
                research,
                config,
                strategy,
                evaluation,
                version,
                "artifact-" + suffix,
                "manifest-" + suffix);
        return publish;
    }
}

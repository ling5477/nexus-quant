package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataIngestionJobCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataIngestionService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalKlineProvider;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataBarRepository;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataIngestionJobRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

/** 真实PG与Spring事务代理的幂等、锁竞争和重读证明；不是完整Servlet或真实公开来源资格。 */
@EnabledIfSystemProperty(named = "nq.ingestion.operation.pg.required", matches = "true")
class MarketdataIngestionOperationPostgresIntegrationTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void realUniqueKeysLocksRollbackAndReopenedReadsPreserveOneOriginalResult() throws Exception {
        String maintenance = System.getenv("TASK_INGESTION_MAINTENANCE_URL");
        String user = System.getenv("TASK_INGESTION_DB_USER");
        String password = System.getenv("TASK_INGESTION_DB_PASSWORD");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        assertNotNull(user);
        assertNotNull(password);
        String name = "ingestion_operation_" + UUID.randomUUID().toString().replace("-", "");
        String url = maintenance.substring(0, maintenance.lastIndexOf('/') + 1) + name;
        Path evidence =
                Path.of(System.getProperty("nq.ingestion.operation.pg.evidence")).resolve(name);
        Files.createDirectories(evidence);
        var control = new JdbcTemplate(new DriverManagerDataSource(maintenance, user, password));
        control.setQueryTimeout(10);
        control.execute("CREATE DATABASE " + name);
        boolean passed = false;
        var provider =
                new AtomicReference<HistoricalKlineProvider>((job, from, to) -> List.of(bar(from)));
        var calls = new AtomicInteger();
        try {
            Flyway.configure()
                    .dataSource(url, user, password)
                    .cleanDisabled(true)
                    .locations("classpath:db/migration")
                    .target("24")
                    .load()
                    .migrate();
            var source = new DriverManagerDataSource(url, user, password);
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            try (var context =
                    context(
                            source,
                            (job, from, to) -> {
                                calls.incrementAndGet();
                                return provider.get().fetchBars(job, from, to);
                            })) {
                var service = context.getBean(MarketdataIngestionService.class);
                assertTrue(
                        AopUtils.isAopProxy(service), "actual service transaction proxy required");
                UUID jobId = UUID.randomUUID();
                var first = service.createJob(jobId, command(START.plusSeconds(180)));
                assertEquals(first, service.createJob(jobId, command(START.plusSeconds(180))));
                assertThrows(
                        IllegalStateException.class,
                        () -> service.createJob(jobId, command(START.plusSeconds(181))));
                assertEquals(1, count(jdbc, "marketdata_ingestion_jobs"));

                var entered = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                provider.set(
                        (job, from, to) -> {
                            entered.countDown();
                            try {
                                if (!release.await(8, TimeUnit.SECONDS))
                                    throw new IllegalStateException(
                                            "controlled source release timed out");
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "controlled source interrupted", ex);
                            }
                            return List.of(bar(from));
                        });
                UUID runId = UUID.randomUUID();
                MarketdataIngestionRun original;
                var workers = Executors.newFixedThreadPool(2);
                try {
                    var left = workers.submit(() -> service.runOnce(jobId, runId));
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    var right = workers.submit(() -> service.runOnce(jobId, runId));
                    // 下载期间RUNNING已提交；并发同编号只读返回，来源线程不持有长事务。
                    var concurrent = right.get(5, TimeUnit.SECONDS);
                    assertEquals(MarketdataIngestionStatus.RUNNING, concurrent.status());
                    assertEquals(concurrent, service.findRun(jobId, runId).orElseThrow());
                    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                            + "WHERE datname=current_database() AND state='idle in transaction'", Integer.class));
                    release.countDown();
                    original = left.get(10, TimeUnit.SECONDS);
                } finally {
                    release.countDown();
                    workers.shutdownNow();
                    assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
                }
                assertEquals(1, calls.get());
                assertEquals(1, count(jdbc, "marketdata_ingestion_runs"));
                assertEquals(1, count(jdbc, "marketdata_bars"));
                assertEquals(original, service.runOnce(jobId, runId));
                assertEquals(1, calls.get());

                provider.set((job, from, to) -> List.of(bar(from)));
                var resumed = service.runOnce(jobId, UUID.randomUUID());
                assertEquals(START.plusSeconds(60), resumed.requestedStartTime());
                assertEquals(2, count(jdbc, "marketdata_bars"));
                UUID other = service.createJob(command(START.plusSeconds(240))).jobId();
                assertThrows(IllegalStateException.class, () -> service.runOnce(other, runId));
                assertTrue(service.findRun(other, runId).isEmpty());

                provider.set(
                        (job, from, to) -> {
                            throw new IllegalStateException("controlled source failure");
                        });
                UUID failedId = UUID.randomUUID();
                var failed = service.runOnce(jobId, failedId);
                assertEquals(MarketdataIngestionStatus.FAILED, failed.status());
                int afterFailure = calls.get();
                assertEquals(failed, service.runOnce(jobId, failedId));
                assertEquals(afterFailure, calls.get());
                assertEquals(2, count(jdbc, "marketdata_bars"));

                provider.set((job, from, to) -> List.of(bar(from)));
                UUID rolledBack = UUID.randomUUID();
                var transactions =
                        new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                transactions.executeWithoutResult(
                        status -> {
                            service.runOnce(jobId, rolledBack);
                            status.setRollbackOnly();
                        });
                assertTrue(service.findRun(jobId, rolledBack).isPresent());
                assertEquals(3, count(jdbc, "marketdata_bars"));
                var committed = service.runOnce(jobId, rolledBack);
                assertEquals(MarketdataIngestionStatus.SUCCEEDED, committed.status());
                assertEquals(3, count(jdbc, "marketdata_bars"));

                try (var reopened =
                        context(
                                new DriverManagerDataSource(url, user, password),
                                (job, from, to) -> {
                                    throw new AssertionError("reopen lookup must not call source");
                                })) {
                    var reader = reopened.getBean(MarketdataIngestionService.class);
                    assertEquals(original, reader.runOnce(jobId, runId));
                    assertEquals(failed, reader.findRun(jobId, failedId).orElseThrow());
                    assertEquals(committed, reader.findRun(jobId, rolledBack).orElseThrow());
                }
                // 明确不匹配标的的来源行只允许成为跳过项，不能把部分采集写成完整成功。
                provider.set((job, from, to) -> List.of(bar(from), new HistoricalBar(
                        "OKX", "ETH-USDT", BarInterval.ONE_MINUTE, from,
                        from.plusSeconds(60).minusMillis(1), BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE)));
                UUID partialJob = service.createJob(command(START.plusSeconds(600))).jobId();
                UUID partialId = UUID.randomUUID();
                var partial = service.runOnce(partialJob, partialId);
                assertEquals(MarketdataIngestionStatus.PARTIAL, partial.status());
                assertEquals(2, partial.fetchedBars());
                assertEquals(1, partial.skippedBars());
                assertEquals(1, partial.insertedBars() + partial.updatedBars());
                assertEquals(partial, service.findRun(partialJob, partialId).orElseThrow());
                assertEquals(partial, service.runOnce(partialJob, partialId));
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM marketdata_bars WHERE symbol='ETH-USDT'", Integer.class));
                // 超过500根的真实数据库前缀、来源失败及重建服务后的续拉，均通过持久化数量证明。
                Instant thirtyDayStart = START.minusSeconds(30 * 86400L);
                var pageCalls = new AtomicInteger();
                provider.set((job, from, to) -> {
                    if (pageCalls.incrementAndGet() == 2)
                        throw new IllegalStateException("controlled second page interruption");
                    return hourlyBars(job, from, to);
                });
                UUID longJob = service.createJob(new CreateMarketdataIngestionJobCommand(
                        "OKX", "SPOT", "ETH-USDT", "1h", thirtyDayStart,
                        START.minusMillis(1), "synthetic-ingestion-owner")).jobId();
                var interrupted = service.runOnce(longJob);
                assertEquals(MarketdataIngestionStatus.FAILED, interrupted.status());
                assertEquals(300, interrupted.insertedBars());
                assertEquals(thirtyDayStart.plusSeconds(300 * 3600L).minusMillis(1), interrupted.actualEndTime());
                assertEquals(300, jdbc.queryForObject("SELECT count(*) FROM marketdata_bars WHERE symbol='ETH-USDT'", Integer.class));
                try (var reopened = context(new DriverManagerDataSource(url, user, password),
                        MarketdataIngestionOperationPostgresIntegrationTest::hourlyBars)) {
                    var resumedService = reopened.getBean(MarketdataIngestionService.class);
                    assertEquals(interrupted, resumedService.findRun(longJob, interrupted.runId()).orElseThrow());
                    var continued = resumedService.runOnce(longJob);
                    assertEquals(MarketdataIngestionStatus.SUCCEEDED, continued.status());
                    assertEquals(thirtyDayStart.plusSeconds(300 * 3600L), continued.requestedStartTime());
                    assertEquals(420, continued.insertedBars());
                    assertEquals(0, continued.updatedBars());
                    assertEquals(continued, resumedService.runOnce(longJob, continued.runId()));
                }
                assertEquals(720, jdbc.queryForObject("SELECT count(*) FROM marketdata_bars WHERE symbol='ETH-USDT'", Integer.class));
                assertEquals(720, jdbc.queryForObject("SELECT count(DISTINCT open_time) FROM marketdata_bars WHERE symbol='ETH-USDT'", Integer.class));
                assertEquals(0, count(jdbc, "orders"));
                assertEquals(0, count(jdbc, "trades"));
                assertEquals(0, count(jdbc, "ledger_entries"));
                Files.writeString(
                        evidence.resolve("result.txt"),
                        "PASS_SERVICE_REPOSITORY_PG24_REAL_SPRING_TRANSACTION_PROXY\n"
                                + "same-id replay; changed-input409; committed RUNNING and no idle network transaction; source called once; "
                                + "adjacent-bar resume; failure replay; independent batch commit survives caller rollback; "
                                + "new context exact read; ETH720 database count; second-page failure preserves300; reopened service resumes420; zero trading facts\n"
                                + "No Servlet, browser, application-process restart, real provider or installer claim.\n");
            }
            passed = true;
        } finally {
            if (passed) control.execute("DROP DATABASE " + name);
        }
    }

    private static List<HistoricalBar> hourlyBars(
            com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob job,
            Instant from, Instant to) {
        var values = new java.util.ArrayList<HistoricalBar>();
        for (Instant open = from; !open.plusSeconds(3600).minusMillis(1).isAfter(to);
                open = open.plusSeconds(3600)) {
            values.add(new HistoricalBar("OKX", job.symbol(), BarInterval.ONE_HOUR, open,
                    open.plusSeconds(3600).minusMillis(1), BigDecimal.TEN, BigDecimal.TEN,
                    BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE));
        }
        java.util.Collections.reverse(values);
        return values;
    }

    private static AnnotationConfigApplicationContext context(
            DataSource dataSource, HistoricalKlineProvider provider) {
        var context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.registerBean(
                PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(HistoricalKlineProvider.class, () -> provider);
        context.register(
                JdbcMarketdataIngestionJobRepository.class,
                JdbcMarketdataBarRepository.class,
                MarketdataIngestionService.class);
        context.refresh();
        return context;
    }

    private static int count(JdbcTemplate jdbc, String table) {
        if (!List.of(
                        "marketdata_ingestion_jobs",
                        "marketdata_ingestion_runs",
                        "marketdata_bars",
                        "orders",
                        "trades",
                        "ledger_entries")
                .contains(table)) throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void awaitDatabaseLock(JdbcTemplate jdbc) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (jdbc.queryForObject(
                        """
                SELECT count(*) FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock'
                  AND query LIKE '%marketdata_ingestion_jobs%' AND query LIKE '%FOR UPDATE%'
                """,
                        Integer.class)
                == 0) {
            assertTrue(System.nanoTime() < deadline, "actual concurrent row-lock wait required");
            Thread.sleep(10);
        }
    }

    private static CreateMarketdataIngestionJobCommand command(Instant end) {
        return new CreateMarketdataIngestionJobCommand(
                "OKX", "SPOT", "BTC-USDT", "1m", START, end, "synthetic-ingestion-owner");
    }

    private static HistoricalBar bar(Instant open) {
        return new HistoricalBar(
                "OKX",
                "BTC-USDT",
                BarInterval.ONE_MINUTE,
                open,
                open.plusSeconds(60).minusMillis(1),
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ONE);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}
}

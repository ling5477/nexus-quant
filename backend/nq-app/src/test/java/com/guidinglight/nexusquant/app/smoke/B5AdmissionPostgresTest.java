package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchIdentity;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunAdmission;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunRepository;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 独立真实事务验证唯一约束、冲突结果、窗口隔离、旧行和回滚；不以mock证明数据库行为。 */
@EnabledIfSystemProperty(named = "nq.b5.admission", matches = "true")
class B5AdmissionPostgresTest {
    @Configuration @EnableTransactionManagement
    static class Transactions { }

    @Test void uniquenessScopeRollbackAndLegacyRefusal() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            fixture.initialize(true, "http://127.0.0.1:1", B0Processes.cleanEnvironment());
            B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var s = owner.createStatement()) {
                s.execute("INSERT INTO accounts(account_code,venue,status) VALUES ('b5-second','OKX','ACTIVE')");
                // 不同账户对应独立定义；当前定义的account是固定绑定，不伪造跨账户复用同一注册项。
                s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env) "
                        + "VALUES ('b5-other','b5-other','other','TEST','OKX',1,'SIM'),('b5-account','b5-account','account','TEST','OKX',2,'SIM')");
                s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,account_id,exchange_code,trade_env) "
                        + "VALUES ('other','b5-other','0 0 0 1 1 *','UTC',1,'OKX','SIM'),('account','b5-account','0 0 0 1 1 *','UTC',2,'OKX','SIM')");
            }
            try (var context = new AnnotationConfigApplicationContext(); var reader = fixture.checker()) {
                context.register(Transactions.class);
                context.registerBean(DataSource.class, () -> new DriverManagerDataSource(fixture.url(), B0Fixture.APP, ""));
                context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(context.getBean(DataSource.class)));
                context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(context.getBean(DataSource.class)));
                context.registerBean(JdbcStrategyRunRepository.class);
                context.refresh();
                var repo = context.getBean(StrategyRunRepository.class);
                Instant due = Instant.parse("2026-01-01T00:00:00Z");
                var key = new StrategyDispatchIdentity("b5", "b5-strategy", 1L, due);
                StrategyRunAdmission winner;
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var gate = new CyclicBarrier(2);
                    var a = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return repo.admit(run(key), key); });
                    var b = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return repo.admit(run(key), key); });
                    var first = a.get(15, TimeUnit.SECONDS); var second = b.get(15, TimeUnit.SECONDS);
                    assertTrue(first.admitted() ^ second.admitted());
                    assertEquals(first.run().strategyRunId(), second.run().strategyRunId());
                    winner = first.admitted() ? first : second;
                }
                // 不同调用requestId不能绕过同一个结构化key；已结束run仍占有该窗口。
                assertFalse(repo.admit(run(key), key).admitted());
                assertTrue(repo.updateStatus(winner.run().strategyRunId(), StrategyRunStatus.DISPATCHING, null, null));
                assertTrue(repo.updateStatus(winner.run().strategyRunId(), StrategyRunStatus.FAILED, Instant.now(), "pg-only"));
                assertFalse(repo.admit(run(key), key).admitted());
                var next = new StrategyDispatchIdentity("b5", "b5-strategy", 1L, due.plusSeconds(86400));
                var other = new StrategyDispatchIdentity("other", "b5-other", 1L, due);
                var account = new StrategyDispatchIdentity("account", "b5-account", 2L, due);
                for (var independent : List.of(next, other, account)) assertTrue(repo.admit(run(independent), independent).admitted());
                var rollback = new StrategyDispatchIdentity("b5", "b5-strategy", 1L, due.plusSeconds(172800));
                B4TransactionFaults.arm(repo, context.getBean(JdbcTemplate.class), "admit", "ROLLBACK");
                assertThrows(IllegalStateException.class, () -> repo.admit(run(rollback), rollback));
                assertEquals("4", B5StrategyRunRecoveryProcessTest.value(reader, "SELECT count(*) FROM strategy_runs"));
                assertTrue(repo.admit(run(rollback), rollback).admitted());
                var legacy = new StrategyDispatchIdentity("b5", "b5-strategy", 1L, due.plusSeconds(259200));
                StrategyRun old = run(legacy);
                repo.insert(new StrategyRun(old.strategyRunId(), old.strategyId(), old.accountId(), old.exchangeCode(),
                        old.tradeEnv(), "MANUAL", StrategyRunStatus.FAILED, old.configSnapshot(), legacy.legacyRequestIds().get(0),
                        old.startedAt(), old.startedAt(), "legacy", old.traceId()));
                assertFalse(repo.admit(run(legacy), legacy).admitted());
                assertThrows(RuntimeException.class, () -> context.getBean(JdbcTemplate.class).update(
                        "UPDATE strategy_runs SET admission_due_at=admission_due_at+INTERVAL '1 day' WHERE strategy_run_id=?", winner.run().strategyRunId()));
                assertEquals("6", B5StrategyRunRecoveryProcessTest.value(reader, "SELECT count(*) FROM strategy_runs"));
                assertEquals("0", B5StrategyRunRecoveryProcessTest.value(reader, "SELECT count(*) FROM orders"));
                System.out.println("B5_ADMISSION_PG_PASS winners=1 sameKeyDifferentRequests=true duplicateAfterFinality=true differentWindow=true differentStrategy=true differentAccount=true rollback=true legacyRefused=true identityImmutable=true");
            }
        }
    }

    @Test void v49UpgradePreservesLegacyDuplicatesAndAddsDatabaseConstraints() throws Exception {
        try (var pg = B0Processes.Pg.start()) {
            String name = "b5_migration_" + UUID.randomUUID().toString().replace("-", "");
            String admin = pg.ownedUrl();
            try (var c = DriverManager.getConnection(admin, "postgres", ""); var s = c.createStatement()) {
                s.execute("CREATE DATABASE " + name);
            }
            String url = admin.substring(0, admin.lastIndexOf('/') + 1) + name;
            try {
                Flyway.configure().dataSource(url, "postgres", "").locations("classpath:db/migration").target("49").load().migrate();
                try (var c = DriverManager.getConnection(url, "postgres", ""); var s = c.createStatement()) {
                    s.execute("INSERT INTO accounts(account_code,venue) VALUES ('migration','OKX')");
                    s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env) VALUES ('legacy','legacy','legacy','TEST','OKX',1,'SIM')");
                    s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,account_id,exchange_code,trade_env) VALUES ('legacy','legacy','0 0 0 1 1 *','UTC',1,'OKX','SIM')");
                    s.execute("INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,trigger_type,request_id,started_at,trace_id,exchange_code,trade_env) "
                            + "VALUES ('old1','legacy',1,'FAILED','MANUAL','same-old-request',now(),'migration','OKX','SIM'),('old2','legacy',1,'DISPATCHING','MANUAL','same-old-request',now(),'migration','OKX','SIM')");
                }
                var flyway = Flyway.configure().dataSource(url, "postgres", "").locations("classpath:db/migration").load();
                assertEquals(1, flyway.migrate().migrationsExecuted); flyway.validate();
                try (var c = DriverManager.getConnection(url, "postgres", ""); var s = c.createStatement()) {
                    assertEquals("2", B5StrategyRunRecoveryProcessTest.value(c, "SELECT count(*) FROM strategy_runs WHERE admission_schedule_id IS NULL AND admission_due_at IS NULL"));
                    String insert = "INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,trigger_type,started_at,trace_id,exchange_code,trade_env,admission_schedule_id,admission_due_at) VALUES ";
                    s.execute(insert + "('new1','legacy',1,'CREATED','SCHEDULER',now(),'migration','OKX','SIM','legacy','2026-01-01T00:00:00Z')");
                    assertThrows(Exception.class, () -> s.execute(insert + "('new2','legacy',1,'CREATED','SCHEDULER',now(),'migration','OKX','SIM','legacy','2026-01-01T00:00:00Z')"));
                    assertThrows(Exception.class, () -> s.execute(insert + "('bad-pair','legacy',1,'CREATED','SCHEDULER',now(),'migration','OKX','SIM','legacy',NULL)"));
                    assertThrows(Exception.class, () -> s.execute(insert + "('bad-fk','legacy',1,'CREATED','SCHEDULER',now(),'migration','OKX','SIM','absent','2026-01-01T00:00:00Z')"));
                }
                System.out.println("B5_ADMISSION_MIGRATION_PASS from=49 to=50 legacyDuplicates=2 noBackfill=true unique=true pair=true fk=true");
            } finally {
                try (var c = DriverManager.getConnection(admin, "postgres", ""); var s = c.createStatement()) {
                    s.execute("DROP DATABASE " + name + " WITH (FORCE)");
                }
                assertTrue(pg.databaseAbsent(name));
            }
        }
    }

    private StrategyRun run(StrategyDispatchIdentity key) {
        return new StrategyRun("run-" + UUID.randomUUID(), key.strategyId(), key.accountId(), "OKX", "SIM",
                "SCHEDULER", StrategyRunStatus.CREATED, "{}", "per-call-" + UUID.randomUUID(), Instant.now(), null, null, "admission-pg");
    }
}

package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchIdentity;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import java.math.BigDecimal;
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
                        + "VALUES ('b5-other','b5-other','other','TEST','OKX',1,'SIM'),('b5-account','b5-account','account','TEST','OKX',3,'SIM')");
                s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,account_id,exchange_code,trade_env) "
                        + "VALUES ('other','b5-other','0 0 0 1 1 *','UTC',1,'OKX','SIM'),('account','b5-account','0 0 0 1 1 *','UTC',3,'OKX','SIM')");
            }
            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", ""); var statement = owner.createStatement()) {
                statement.execute("UPDATE strategy_definitions SET enabled=true, config_snapshot=(SELECT config_snapshot FROM strategy_definitions WHERE strategy_id='b5-strategy')");
                statement.execute("UPDATE strategy_schedules SET created_at='2025-12-31T23:59:59Z', enabled=true");
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
                    var a = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return admit(repo, run(key), key); });
                    var b = pool.submit(() -> { gate.await(5, TimeUnit.SECONDS); return admit(repo, run(key), key); });
                    var first = a.get(15, TimeUnit.SECONDS); var second = b.get(15, TimeUnit.SECONDS);
                    assertTrue(first.admitted() ^ second.admitted());
                    assertEquals(first.run().strategyRunId(), second.run().strategyRunId());
                    winner = first.admitted() ? first : second;
                }
                // 不同调用requestId不能绕过同一个结构化key；已结束run仍占有该窗口。
                assertFalse(admit(repo, run(key), key).admitted());
                assertTrue(context.getBean(JdbcTemplate.class).queryForObject(
                        "SELECT nq_bind_strategy_effective(?,NULL,NULL,'TEST_REFUSAL')", Boolean.class, winner.run().strategyRunId()));
                assertEquals(StrategyRunStatus.FAILED, repo.findByStrategyRunId(winner.run().strategyRunId()).orElseThrow().status());
                assertFalse(admit(repo, run(key), key).admitted());
                var next = new StrategyDispatchIdentity("b5", "b5-strategy", 1L, due.plusSeconds(365L * 86400));
                var other = new StrategyDispatchIdentity("other", "b5-other", 1L, due);
                var account = new StrategyDispatchIdentity("account", "b5-account", 3L, due);
                for (var independent : List.of(next, other, account)) {
                    var admitted = admit(repo, run(independent), independent);
                    assertTrue(admitted.admitted());
                    assertTrue(context.getBean(JdbcTemplate.class).queryForObject("SELECT nq_bind_strategy_effective(?,NULL,NULL,'TEST_REFUSAL')",
                            Boolean.class, admitted.run().strategyRunId()));
                }
                var rollback = new StrategyDispatchIdentity("b5", "b5-strategy", 1L, due.plusSeconds(2L * 365 * 86400));
                B4TransactionFaults.arm(repo, context.getBean(JdbcTemplate.class), "admit", "ROLLBACK");
                assertThrows(IllegalStateException.class, () -> admit(repo, run(rollback), rollback));
                assertEquals("4", B5StrategyRunRecoveryProcessTest.value(reader, "SELECT count(*) FROM strategy_runs"));
                var retried = admit(repo, run(rollback), rollback);
                assertTrue(retried.admitted());
                assertTrue(context.getBean(JdbcTemplate.class).queryForObject("SELECT nq_bind_strategy_effective(?,NULL,NULL,'TEST_REFUSAL')",
                        Boolean.class, retried.run().strategyRunId()));
                var legacy = new StrategyDispatchIdentity("b5", "b5-strategy", 1L,
                        due.atOffset(java.time.ZoneOffset.UTC).plusYears(3).toInstant());
                StrategyRun candidate = run(legacy);
                StrategyRun old = new StrategyRun(candidate.strategyRunId(), candidate.strategyId(), candidate.accountId(), candidate.exchangeCode(),
                        candidate.tradeEnv(), "MANUAL", StrategyRunStatus.CREATED, candidate.configSnapshot(), legacy.legacyRequestIds().get(0),
                        candidate.startedAt(), null, null, candidate.traceId());
                admit(repo, old, null);
                assertTrue(context.getBean(JdbcTemplate.class).queryForObject(
                        "SELECT nq_bind_strategy_effective(?,NULL,NULL,'TEST_REFUSAL')", Boolean.class, old.strategyRunId()));
                assertFalse(admit(repo, run(legacy), legacy).admitted());
                assertThrows(RuntimeException.class, () -> context.getBean(JdbcTemplate.class).update(
                        "UPDATE strategy_runs SET admission_due_at=admission_due_at+INTERVAL '1 day' WHERE strategy_run_id=?", winner.run().strategyRunId()));
                assertEquals("6", B5StrategyRunRecoveryProcessTest.value(reader, "SELECT count(*) FROM strategy_runs"));
                assertEquals("0", B5StrategyRunRecoveryProcessTest.value(reader, "SELECT count(*) FROM orders"));
                System.out.println("B5_ADMISSION_PG_PASS winners=1 sameKeyDifferentRequests=true duplicateAfterFinality=true differentWindow=true differentStrategy=true differentAccount=true rollback=true legacyRefused=true identityImmutable=true");
            }
        }
    }

    @Test void freshBaselineRejectsFabricatedLegacyStateAndNonAtomicWork() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            fixture.initialize(true, "http://127.0.0.1:1", B0Processes.cleanEnvironment());
            B5StrategyRunRecoveryProcessTest.seed(fixture);
            var flyway = Flyway.configure().dataSource(fixture.url(), "postgres", "").locations("classpath:db/migration").load();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            flyway.validate();
            try (var connection = DriverManager.getConnection(fixture.url(), "postgres", ""); var statement = connection.createStatement()) {
                String insert = "INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,trigger_type,exchange_code,trade_env,started_at,trace_id) "
                        + "VALUES ('invalid','b5-strategy',1,'%s','MANUAL','OKX','SIM',now(),'baseline-test')";
                assertThrows(Exception.class, () -> statement.execute(insert.formatted("FAILED")));
                assertThrows(Exception.class, () -> statement.execute(insert.formatted("DISPATCHING")));
                assertThrows(Exception.class, () -> statement.execute(insert.formatted("CREATED")));
                assertEquals("0", B5StrategyRunRecoveryProcessTest.value(connection, "SELECT count(*) FROM strategy_runs"));
                assertEquals("0", B5StrategyRunRecoveryProcessTest.value(connection, "SELECT count(*) FROM strategy_run_dispatch_work"));
            }
        }
    }

    private StrategyRunAdmission admit(StrategyRunRepository repository, StrategyRun run, StrategyDispatchIdentity identity) {
        return repository.admit(run, new StrategyDispatchWork(run.strategyRunId(), 1, 1, run.accountId(), "coid-" + run.requestId(),
                "BTC-USDT", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN, new BigDecimal("100"), "GTC"), identity);
    }

    private StrategyRun run(StrategyDispatchIdentity key) {
        return new StrategyRun("run-" + UUID.randomUUID(), key.strategyId(), key.accountId(), "OKX", "SIM",
                "SCHEDULER", StrategyRunStatus.CREATED, "{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"10\"}", "per-call-" + UUID.randomUUID(), key.dueAt().plusSeconds(10), null, null, "admission-pg");
    }
}

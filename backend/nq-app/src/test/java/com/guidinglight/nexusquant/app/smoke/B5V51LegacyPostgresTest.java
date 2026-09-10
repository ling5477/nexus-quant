package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.strategy.application.StrategyScheduleTiming;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyScheduleRepository;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 升级前种子只用于 legacy/索引诊断，不冒充真实交易或发送证明。 */
@EnabledIfSystemProperty(named = "nq.b5.v51", matches = "true")
class B5V51LegacyPostgresTest {
    @Test void upgradePreservesLegacyAndRecoveryCursorSurvivesRestart() throws Exception {
        try (var pg = B0Processes.Pg.start(); var db = LegacyDatabase.create(pg)) {
            try (var c = DriverManager.getConnection(db.url, "postgres", ""); var s = c.createStatement()) {
                s.execute("INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,trigger_type,request_id,started_at,trace_id,exchange_code,trade_env) "
                        + "SELECT 'legacy-'||lpad(i::text,3,'0'),'legacy',1,'CREATED','MANUAL','legacy-'||i,"
                        + "'2025-01-01'::timestamptz+i*INTERVAL '1 second','legacy-proof','OKX','SIM' FROM generate_series(1,70) i");
                s.execute("UPDATE strategy_runs SET trigger_type='SCHEDULER',admission_schedule_id='legacy',admission_due_at='2026-01-01T00:00:00Z' WHERE strategy_run_id='legacy-001'");
                String before = B5StrategyRunRecoveryProcessTest.value(c, "SELECT jsonb_agg(to_jsonb(r) ORDER BY strategy_run_id)::text FROM strategy_runs r");
                var migration = Flyway.configure().dataSource(db.url, "postgres", "").locations("classpath:db/migration").load();
                assertEquals(1, migration.migrate().migrationsExecuted); migration.validate();
                assertEquals(before, B5StrategyRunRecoveryProcessTest.value(c, "SELECT jsonb_agg(to_jsonb(r) ORDER BY strategy_run_id)::text FROM strategy_runs r"));
                assertEquals("0", B5StrategyRunRecoveryProcessTest.value(c, "SELECT count(*) FROM strategy_run_dispatch_work"));
            }
            List<String> first;
            try (var context = context(db.url)) {
                var executions = context.getBean(StrategyRunExecutionRepository.class);
                first = executions.reserveCandidates(50);
                assertEquals(50, new HashSet<>(first).size());
                var schedule = context.getBean(JdbcStrategyScheduleRepository.class).listAll().getFirst();
                assertEquals(Instant.parse("2026-01-01T00:00:00Z"), schedule.lastTriggeredAt());
                assertEquals(Instant.parse("2027-01-01T00:00:00Z"), StrategyScheduleTiming.nextDue(schedule,
                        schedule.lastTriggeredAt(), Instant.parse("2027-01-02T00:00:00Z")));
            }
            try (var context = context(db.url)) {
                var executions = context.getBean(StrategyRunExecutionRepository.class);
                List<String> second = executions.reserveCandidates(50);
                assertEquals(50, new HashSet<>(second).size());
                var all = new HashSet<>(first); all.addAll(second); assertEquals(70, all.size());
                for (String run : second) {
                    assertTrue(executions.findWork(run).isEmpty());
                    assertEquals(false, executions.project(run));
                }
            }
            System.out.println("B5_V51_LEGACY_PASS unchanged=70 workBackfill=0 restartFairness=70 batch=50 effectiveCursor=true");
        }
    }

    @Test void legacyMultipleOrdersRejectEntireUpgrade() throws Exception {
        try (var pg = B0Processes.Pg.start(); var db = LegacyDatabase.create(pg)) {
            try (var c = DriverManager.getConnection(db.url, "postgres", ""); var s = c.createStatement()) {
                s.execute("INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,started_at,trace_id,exchange_code,trade_env) VALUES('multi','legacy',1,'DISPATCHING',now(),'legacy','OKX','SIM')");
                for (String id : List.of("one", "two")) {
                    s.execute("SELECT nq_create_ordinary_place_order('" + id + "',1,'multi','OKX','BTC-USDT','" + id
                            + "','BUY','LIMIT',100,10,'NEW','LEGACY_DIAGNOSTIC','legacy','SIM',0,CURRENT_TIMESTAMP)");
                }
                assertThrows(RuntimeException.class, () -> Flyway.configure().dataSource(db.url, "postgres", "")
                        .locations("classpath:db/migration").load().migrate());
                assertEquals("2", B5StrategyRunRecoveryProcessTest.value(c, "SELECT count(*) FROM orders WHERE strategy_run_id='multi'"));
                assertEquals("50", B5StrategyRunRecoveryProcessTest.value(c, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                assertEquals("t", B5StrategyRunRecoveryProcessTest.value(c, "SELECT to_regclass('strategy_run_dispatch_work') IS NULL"));
            }
            System.out.println("B5_V51_PREFLIGHT_PASS historicalMultipleOrders=2 migrationRejected=true unchanged=true");
        }
    }

    private static AnnotationConfigApplicationContext context(String url) {
        var context = new AnnotationConfigApplicationContext();
        context.register(B5AdmissionPostgresTest.Transactions.class);
        context.registerBean(DataSource.class, () -> new DriverManagerDataSource(url, "postgres", ""));
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(context.getBean(DataSource.class)));
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(context.getBean(DataSource.class)));
        context.registerBean(JdbcStrategyRunRepository.class); context.registerBean(JdbcStrategyRunExecutionRepository.class);
        context.registerBean(JdbcStrategyScheduleRepository.class); context.refresh(); return context;
    }

    private static final class LegacyDatabase implements AutoCloseable {
        private final String admin;
        private final String name;
        private final String url;
        private LegacyDatabase(String admin, String name) {
            this.admin = admin; this.name = name; this.url = admin.substring(0, admin.lastIndexOf('/') + 1) + name;
        }
        static LegacyDatabase create(B0Processes.Pg pg) throws Exception {
            var db = new LegacyDatabase(pg.ownedUrl(), "b5_v51_legacy_" + UUID.randomUUID().toString().replace("-", ""));
            try (var c = DriverManager.getConnection(db.admin, "postgres", ""); var s = c.createStatement()) { s.execute("CREATE DATABASE " + db.name); }
            try {
                Flyway.configure().dataSource(db.url, "postgres", "").locations("classpath:db/migration").target("50").load().migrate();
                try (var c = DriverManager.getConnection(db.url, "postgres", ""); var s = c.createStatement()) {
                    s.execute("INSERT INTO accounts(account_code,venue) VALUES('legacy','OKX')");
                    s.execute("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env) VALUES('legacy','legacy','legacy','TEST','OKX',1,'SIM')");
                    s.execute("INSERT INTO strategy_schedules(schedule_job_id,strategy_id,cron_expr,timezone,account_id,exchange_code,trade_env,last_triggered_at) VALUES('legacy','legacy','0 0 0 1 1 *','UTC',1,'OKX','SIM','2025-01-01T00:00:00Z')");
                }
                return db;
            } catch (Exception failure) { db.close(); throw failure; }
        }
        @Override public void close() throws Exception {
            B0Fixture.requireLoopbackDatabase(url, name);
            try (var c = DriverManager.getConnection(admin, "postgres", ""); var s = c.createStatement()) { s.execute("DROP DATABASE " + name + " WITH (FORCE)"); }
        }
    }
}

package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 正式基线的扫描位置必须跨连接持久化；检查公平性不授予执行或发送权限。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class RecoveryScanCursorPostgresIntegrationTest {
    private static final String CONFIG = "{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\","
            + "\"orderType\":\"LIMIT\",\"price\":\"100\",\"quantity\":\"10\"}";

    @Configuration
    @EnableTransactionManagement
    static class Transactions { }

    @Test
    void durableCursorSurvivesContextRestartWithoutStarvationAndRollsBackAtomically() throws Exception {
        try (var database = ReleaseBaselineTestDatabase.create()) {
            var flyway = database.flyway();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals("1", flyway.info().current().getVersion().getVersion());
            var reader = new JdbcTemplate(database.source());
            assertEquals(null, cursor(reader));
            List<String> first;
            String firstCursor;
            String facts;
            try (var context = context(database)) {
                seed(context);
                facts = facts(reader);
                var executions = context.getBean(StrategyRunExecutionRepository.class);
                first = executions.reserveCandidates(50);
                assertEquals(ids(1, 50), first);
                assertEquals(50, new HashSet<>(first).size());
                firstCursor = cursor(reader);
                assertEquals("recover-050", firstCursor);
            }

            // 关闭原上下文后重新创建连接和代理，排除进程内游标掩盖数据库丢失。
            try (var context = context(database)) {
                var executions = context.getBean(StrategyRunExecutionRepository.class);
                var jdbc = context.getBean(JdbcTemplate.class);
                assertEquals(firstCursor, cursor(jdbc));
                List<String> second = executions.reserveCandidates(50);
                var expected = new java.util.ArrayList<>(ids(51, 70));
                expected.addAll(ids(1, 30));
                assertEquals(expected, second);
                assertEquals(50, new HashSet<>(second).size());
                var visited = new HashSet<>(first);
                visited.addAll(second);
                assertEquals(new HashSet<>(ids(1, 70)), visited);
                assertEquals("recover-030", cursor(reader));
                assertEquals(facts, facts(reader));

                assertThrows(IllegalArgumentException.class, () -> executions.reserveCandidates(0));
                assertThrows(IllegalArgumentException.class, () -> executions.reserveCandidates(51));
                assertEquals("recover-030", cursor(reader));

                // 在真实事务内、游标 UPDATE 后抛错；独立连接必须仍看到上次提交位置。
                var armed = new AtomicBoolean(true);
                assertTrue(executions instanceof Advised);
                ((Advised) executions).addAdvice((MethodInterceptor) invocation -> {
                    Object result = invocation.proceed();
                    if ("reserveCandidates".equals(invocation.getMethod().getName())
                            && armed.compareAndSet(true, false)) {
                        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                        assertEquals("recover-010", cursor(jdbc));
                        assertEquals("recover-030", cursor(reader));
                        throw new IllegalStateException("TEST_CURSOR_ROLLBACK_BEFORE_COMMIT");
                    }
                    return result;
                });
                assertThrows(IllegalStateException.class, () -> executions.reserveCandidates(50));
                assertEquals("recover-030", cursor(reader));
                List<String> replay = executions.reserveCandidates(50);
                var expectedReplay = new java.util.ArrayList<>(ids(31, 70));
                expectedReplay.addAll(ids(1, 10));
                assertEquals(expectedReplay, replay);
                assertEquals(50, new HashSet<>(replay).size());
                assertEquals("recover-010", cursor(reader));
                for (String run : replay) assertTrue(executions.findWork(run).isPresent());
                assertEquals(facts, facts(reader));
                assertEquals(70L, reader.queryForObject("SELECT count(*) FROM strategy_runs WHERE status='CREATED'", Long.class));
                assertEquals(0L, reader.queryForObject("SELECT count(*) FROM orders", Long.class));
                assertEquals(0L, reader.queryForObject("SELECT count(*) FROM ordinary_place_authorities", Long.class));
            }
            assertEquals(0, flyway.migrate().migrationsExecuted);
            flyway.validate();
            System.out.println("RECOVERY_CURSOR_BASELINE_PASS runs=70 batch=50 contextRestart=true union=70 "
                    + "pendingPrefixFair=true rollback=true replay=true economicFactsUnchanged=true");
        }
    }

    private static AnnotationConfigApplicationContext context(ReleaseBaselineTestDatabase database) {
        var context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSource.class, database::source);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(context.getBean(DataSource.class)));
        context.registerBean(PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(context.getBean(DataSource.class)));
        context.registerBean(JdbcStrategyRunRepository.class);
        context.registerBean(JdbcStrategyRunExecutionRepository.class);
        context.refresh();
        return context;
    }

    private static void seed(AnnotationConfigApplicationContext context) {
        var jdbc = context.getBean(JdbcTemplate.class);
        Long account = jdbc.queryForObject("INSERT INTO accounts(account_code,venue,status) "
                + "VALUES ('recovery-cursor','OKX','ACTIVE') RETURNING account_id", Long.class);
        var runs = context.getBean(StrategyRunRepository.class);
        // 每个定义仅有一个合法 active run，原子 admission 同时创建不可变工作。
        for (int i = 1; i <= 70; i++) {
            String id = "recover-%03d".formatted(i);
            jdbc.update("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,"
                    + "exchange_code,account_id,trade_env,enabled,config_snapshot) VALUES (?,?,?,'TEST','OKX',?,'SIM',true,CAST(? AS jsonb))",
                    id, id, id, account, CONFIG);
            var run = new StrategyRun(id, id, account, "OKX", "SIM", "MANUAL", StrategyRunStatus.CREATED,
                    CONFIG, id, Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i), null, null, "recovery-cursor");
            var work = new StrategyDispatchWork(id, 1, 1, account, "coid-" + id, "BTC-USDT", OrderSide.BUY,
                    OrderType.LIMIT, BigDecimal.TEN, new BigDecimal("100"), "GTC");
            assertTrue(runs.admit(run, work, null).admitted());
        }
    }

    private static List<String> ids(int from, int to) {
        return IntStream.rangeClosed(from, to).mapToObj(i -> "recover-%03d".formatted(i)).toList();
    }

    private static String cursor(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT last_run_id FROM strategy_run_recovery_scan_cursor WHERE cursor_id=1", String.class);
    }

    private static String facts(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(r) || jsonb_build_object('work',to_jsonb(w)) "
                + "ORDER BY r.strategy_run_id)::text FROM strategy_runs r JOIN strategy_run_dispatch_work w USING(strategy_run_id)", String.class);
    }
}

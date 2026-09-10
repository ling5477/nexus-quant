package com.guidinglight.nexusquant.app.smoke;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 只证明真实 PostgreSQL 的 schema/原子关系；直接 SQL 约束输入不冒充 RiskGate/PLACE 证明。 */
@EnabledIfSystemProperty(named = "nq.b5.v51", matches = "true")
class B5V51SchemaPostgresTest {
    @Test void immutableWorkBindingRollbackAndRestrictedTransitions() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            fixture.initialize(true, "http://127.0.0.1:1", B0Processes.cleanEnvironment());
            B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var app = DriverManager.getConnection(fixture.url(), B0Fixture.APP, ""); var reader = fixture.checker()) {
                assertEquals("51", value(reader, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
                assertEquals("v51-run", value(app, "SELECT nq_admit_strategy_work('v51-run',strategy_id,account_id,'OKX','SIM',"
                        + "'MANUAL',config_snapshot,'v51-request',CURRENT_TIMESTAMP,'v51-proof',NULL,NULL,version,"
                        + "'coid-v51-request','BTC-USDT','BUY','LIMIT',10,100,'GTC',NULL) FROM strategy_definitions WHERE strategy_id='b5-strategy'"));
                assertEquals("1", value(reader, "SELECT count(*) FROM strategy_run_dispatch_work"));
                assertEquals("CREATED", value(reader, "SELECT status FROM strategy_runs"));
                reject(app, "SELECT nq_create_ordinary_place_order('hijack',1,NULL,'OKX','BTC-USDT','coid-v51-request',"
                        + "'BUY','LIMIT',100,10,'NEW','DIAGNOSTIC','v51-proof','SIM',0,CURRENT_TIMESTAMP)");
                app.setAutoCommit(false);
                assertEquals("t", value(app, "SELECT nq_bind_strategy_effective('v51-run',10,100,NULL)"));
                assertEquals("t", value(app, "SELECT nq_begin_strategy_dispatch('v51-run')"));
                createOrder(app, "v51-rolled-back", "coid-v51-request");
                app.rollback();
                app.setAutoCommit(true);
                assertEquals("CREATED", value(reader, "SELECT status FROM strategy_runs"));
                assertEquals("0", value(reader, "SELECT count(*) FROM orders"));
                assertEquals("0", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                app.setAutoCommit(false);
                assertEquals("t", value(app, "SELECT nq_bind_strategy_effective('v51-run',10,100,NULL)"));
                assertEquals("t", value(app, "SELECT nq_begin_strategy_dispatch('v51-run')"));
                createOrder(app, "v51-bound", "coid-v51-request");
                app.commit();
                app.setAutoCommit(true);
                assertEquals("1", value(reader, "SELECT count(*) FROM orders WHERE strategy_run_id='v51-run'"));
                assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                assertThrows(SQLException.class, () -> createOrder(app, "v51-second", "different-client"));
                assertThrows(SQLException.class, () -> createOrder(app, "v51-second", "coid-v51-request"));
                reject(app, "UPDATE orders SET strategy_run_id=NULL WHERE order_id='v51-bound'");
                reject(app, "DELETE FROM orders WHERE order_id='v51-bound'");
                reject(app, "UPDATE strategy_run_dispatch_work SET quantity=11 WHERE strategy_run_id='v51-run'");
                reject(app, "DELETE FROM strategy_run_dispatch_work WHERE strategy_run_id='v51-run'");
                reject(app, "UPDATE strategy_runs SET status='FAILED',finished_at=now() WHERE strategy_run_id='v51-run'");
                reject(app, "UPDATE strategy_schedules SET last_triggered_at=now() WHERE schedule_job_id='b5'");
                reject(app, "INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,exchange_code,trade_env,started_at,trace_id) "
                        + "VALUES('missing-work','b5-strategy',1,'CREATED','OKX','SIM',now(),'v51-proof')");
                assertEquals("1", value(reader, "SELECT count(*) FROM strategy_runs"));
                assertEquals("1", value(reader, "SELECT count(*) FROM orders"));
                assertEquals("1", value(reader, "SELECT count(*) FROM ordinary_place_authorities"));
                System.out.println("B5_V51_SCHEMA_PASS version=51 immutableWork=true oneOrder=true oneAuthority=true rollback=true invalidTransitionRejected=true");
                // 仅为 SQL 谓词诊断，直接种子状态不作为 canonical venue 取消证明。
                try (var statement = app.createStatement()) {
                    statement.execute("UPDATE orders SET status='CANCELLED',version=version+1,external_order_id='schema-external' WHERE order_id='v51-bound'");
                }
                String finish = "SELECT nq_finish_strategy_cancel('v51-run','v51-bound',%s,1,'OKX','SIM','BTC-USDT',"
                        + "'coid-v51-request','schema-external',10,%s)";
                assertEquals("f", value(app, finish.formatted(0, 0)));
                assertEquals("f", value(app, finish.formatted(1, 1)));
                assertEquals("0", value(reader, "SELECT count(*) FROM ordinary_order_cancel_finality"));
                assertEquals("t", value(app, finish.formatted(1, 0)));
                assertEquals("f", value(app, finish.formatted(1, 0)));
                assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                try (var statement = app.createStatement()) {
                    statement.execute("INSERT INTO trades(trade_id,order_id,account_id,symbol,exchange,exchange_trade_id,"
                            + "price,qty,trace_id,ts,external_order_id,trade_env) VALUES('contradiction','v51-bound',1,"
                            + "'BTC-USDT','OKX','contradiction',100,1,'v51-proof',now(),'schema-external','SIM')");
                }
                assertEquals("1", value(reader, "SELECT count(*) FROM trades"));
                assertEquals("1", value(reader, "SELECT count(*) FROM audit_logs WHERE action='CANCEL_FINALITY_CONTRADICTION'"));
                assertEquals("FAILED", value(reader, "SELECT status FROM strategy_runs"));
                System.out.println("B5_V51_CANCEL_PREDICATE_PASS staleVersionRejected=true incompleteFillsRejected=true replay=true contradictionKeepsTradeAndAlerts=true");
            }
        }
    }

    private static void createOrder(Connection connection, String order, String client) throws Exception {
        try (var statement = connection.prepareStatement("SELECT nq_create_ordinary_place_order(?,1,'v51-run','OKX','BTC-USDT',?,"
                + "'BUY','LIMIT',100,10,'NEW','SCHEMA_DIAGNOSTIC','v51-proof','SIM',0,CURRENT_TIMESTAMP)")) {
            statement.setString(1, order);
            statement.setString(2, client);
            statement.execute();
        }
    }

    private static void reject(Connection connection, String sql) {
        assertThrows(SQLException.class, () -> {
            try (var statement = connection.createStatement()) { statement.execute(sql); }
        });
    }

    private static String value(Connection connection, String sql) throws Exception {
        return B5StrategyRunRecoveryProcessTest.value(connection, sql);
    }
}

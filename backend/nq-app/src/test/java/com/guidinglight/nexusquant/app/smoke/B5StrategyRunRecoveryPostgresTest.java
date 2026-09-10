package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRecoveryRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunRecoveryRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunRepository;
import java.sql.*;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;
import static com.guidinglight.nexusquant.app.smoke.B5StrategyRunRecoveryProcessTest.value;

/** SQL 对抗夹具在调用恢复前构造；真实 PG 和 Spring 事务证明有界恢复、拒绝及回滚。 */
@EnabledIfSystemProperty(named="nq.b5.strategy", matches="true")
class B5StrategyRunRecoveryPostgresTest {
    @Configuration @EnableTransactionManagement
    static class Transactions { }

    @Test void durableEligibilityBoundsRollbackAndLateCallback() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            fixture.initialize(true, "http://127.0.0.1:1", B0Processes.cleanEnvironment());
            B5StrategyRunRecoveryProcessTest.seed(fixture);
            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", "")) {
                for (int i=0; i<60; i++) seedRun(owner, "a-unresolved-"+i, "DISPATCHING");
                for (int i=0; i<51; i++) {
                    String id="z-safe-"+i;
                    seedRun(owner,id,"DISPATCHING"); order(owner,id,id,true);
                }
                for (String id : new String[]{"created","running","may","mismatch","multiple","filled","missing-authority"}) {
                    seedRun(owner,id,id.equals("created") ? "CREATED" : id.equals("running") ? "RUNNING" : "DISPATCHING");
                    if (id.equals("missing-authority")) {
                        execute(owner,"INSERT INTO orders(order_id,account_id,strategy_run_id,venue,symbol,client_order_id,side,type,price,qty,status,trace_id,exchange_code,trade_env) "
                                + "SELECT 'missing-authority',account_id,'missing-authority','OKX','BTC-USDT','missing-authority','BUY','LIMIT',100,10,'CANCELLED','pg','OKX','SIM' FROM accounts WHERE account_code='b0-account'");
                    } else order(owner,id,id,!id.equals("may") && !id.equals("filled"));
                }
                execute(owner,"UPDATE strategy_runs SET trade_env='LIVE' WHERE strategy_run_id='mismatch'");
                order(owner,"multiple-2","multiple",false);
                execute(owner,"UPDATE ordinary_place_authorities SET state='MAY_HAVE_ESCAPED',decided_at=now() WHERE order_id IN ('may','filled')");
                execute(owner,"UPDATE orders SET status='FILLED',version=version+1 WHERE order_id='filled'");
            }
            try (var context = new AnnotationConfigApplicationContext(); var reader=fixture.checker()) {
                context.register(Transactions.class);
                context.registerBean(DataSource.class, () -> new DriverManagerDataSource(fixture.url(),B0Fixture.APP,""));
                context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(context.getBean(DataSource.class)));
                context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(context.getBean(DataSource.class)));
                context.registerBean(JdbcStrategyRunRecoveryRepository.class);
                context.refresh();
                var recovery = context.getBean(StrategyRunRecoveryRepository.class);
                var jdbc=context.getBean(JdbcTemplate.class);
                assertThrows(IllegalArgumentException.class,()->recovery.recoverNoSendDispatches("b5-strategy",51));
                B4TransactionFaults.arm(recovery,jdbc,"recoverNoSendDispatches","ROLLBACK");
                assertThrows(IllegalStateException.class,()->recovery.recoverNoSendDispatches("b5-strategy",50));
                assertEquals("0",value(reader,"SELECT count(*) FROM strategy_runs WHERE status='FAILED'"));
                assertEquals("0",value(reader,"SELECT count(*) FROM b5_run_transitions"));
                assertEquals(50,recovery.recoverNoSendDispatches("b5-strategy",50));
                assertEquals(1,recovery.recoverNoSendDispatches("b5-strategy",50));
                assertEquals(0,recovery.recoverNoSendDispatches("b5-strategy",50));
                assertEquals("51",value(reader,"SELECT count(*) FROM b5_run_transitions WHERE new_status='FAILED'"));
                assertEquals("67",value(reader,"SELECT count(*) FROM strategy_runs WHERE status<>'FAILED'"));
                var writer=new JdbcStrategyRunRepository(jdbc);
                String before=value(reader,"SELECT row_to_json(r)::text FROM strategy_runs r WHERE strategy_run_id='z-safe-0'");
                assertFalse(writer.updateStatus("z-safe-0",StrategyRunStatus.RUNNING,null,null));
                assertFalse(writer.updateStatus("z-safe-0",StrategyRunStatus.FAILED,Instant.now(),"late"));
                assertEquals(before,value(reader,"SELECT row_to_json(r)::text FROM strategy_runs r WHERE strategy_run_id='z-safe-0'"));
                assertTrue(writer.updateStatus("created",StrategyRunStatus.DISPATCHING,null,null));
                assertTrue(writer.updateStatus("created",StrategyRunStatus.RUNNING,null,null));
                assertFalse(writer.updateStatus("created",StrategyRunStatus.DISPATCHING,null,null));
                assertEquals(0,recovery.recoverNoSendDispatches("b5-strategy",50));
                System.out.println("B5_STRATEGY_PG_PASS recovered=51 protected=67 filterBeforeLimit=true rollback=true lateCallbackNoop=true");
            }
        }
    }

    private void seedRun(Connection c,String id,String status) throws Exception {
        try (var s=c.prepareStatement("INSERT INTO strategy_runs(strategy_run_id,strategy_id,account_id,status,trigger_type,exchange_code,trade_env,config_snapshot,request_id,started_at,trace_id) "
                + "SELECT ?,'b5-strategy',account_id,?,'MANUAL','OKX','SIM','{}',?,now(),'pg' FROM accounts WHERE account_code='b0-account'")) {
            s.setString(1,id); s.setString(2,status); s.setString(3,"request-"+id); s.executeUpdate();
        }
    }
    private void order(Connection c,String id,String run,boolean revoke) throws Exception {
        try (var s=c.prepareStatement("SELECT nq_create_ordinary_place_order(?,account_id,?,'OKX','BTC-USDT',?,'BUY','LIMIT',100,10,'NEW','ORDER_CREATED','pg','SIM',0,now()) FROM accounts WHERE account_code='b0-account'")) {
            s.setString(1,id); s.setString(2,run); s.setString(3,id); s.execute();
        }
        try (var s=c.prepareStatement("UPDATE orders SET status='SENT',version=2 WHERE order_id=?")) { s.setString(1,id); s.executeUpdate(); }
        if (revoke) {
            c.setAutoCommit(false);
            try (var s=c.prepareStatement("SELECT nq_decide_ordinary_place(?,'SENT',2,false)")) {
                s.setString(1,id); try (var r=s.executeQuery()) { assertTrue(r.next()); assertTrue(r.getBoolean(1)); }
            }
            try (var s=c.prepareStatement("UPDATE orders SET status='CANCELLED',version=4,reason='ORDER_NOT_FOUND/OKX_51603' WHERE order_id=?")) { s.setString(1,id); s.executeUpdate(); }
            c.commit(); c.setAutoCommit(true);
        }
    }
    private void execute(Connection c,String sql) throws Exception { try (var s=c.createStatement()) { s.execute(sql); } }
}

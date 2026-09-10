package com.guidinglight.nexusquant.app.smoke;

import java.sql.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** SQL 对抗测试的直接写入仅构造数据库约束输入，不冒充真实交易链证明。 */
@EnabledIfSystemProperty(named="nq.b5", matches="true")
class B5AuthorityPostgresTest {
    @Test void databaseEnforcesOneShotAndRestrictedPaths() throws Exception {
        try (var pg = B0Processes.Pg.start(); var fixture = B0Fixture.create(pg)) {
            var env = B0Processes.cleanEnvironment();
            fixture.initialize(true, "http://127.0.0.1:1", env);
            try (var owner = DriverManager.getConnection(fixture.url(), "postgres", "");
                 var app = DriverManager.getConnection(fixture.url(), B0Fixture.APP, "");
                 var reader = fixture.checker()) {
                assertEquals("false", value(app,"SELECT rolsuper OR rolcreaterole FROM pg_roles WHERE rolname=current_user"));
                assertEquals("3", value(reader,"SELECT count(*) FROM information_schema.columns WHERE table_name='ordinary_place_authorities'"));
                create(app,"one");
                execute(owner,"DELETE FROM risk_events WHERE scope_id='one'");
                assertEquals("false",value(app,"SELECT nq_decide_ordinary_place('one','SENT',2,true)"));
                allow(owner,"one");
                assertThrows(SQLException.class, () -> create(app,"one"));
                assertEquals("1", value(reader,"SELECT count(*) FROM ordinary_place_authorities"));
                reject(app,"UPDATE ordinary_place_authorities SET state='MAY_HAVE_ESCAPED',decided_at=now() WHERE order_id='one'");
                reject(app,"INSERT INTO ordinary_place_authorities VALUES('one','NOT_ARMED',NULL)");
                reject(reader,"SELECT nq_decide_ordinary_place('one','SENT',2,true)");
                reject(reader,createSql("reader"));
                // 调用者 temp/search_path 不能把安全函数指向伪造的 Kill/Order/authority。
                execute(app,"CREATE TEMP TABLE orders (order_id text)");
                execute(app,"CREATE TEMP TABLE ordinary_place_authorities (order_id text,state text,decided_at timestamptz)");
                execute(app,"CREATE TEMP TABLE kill_switch_states (scope text,status text)");
                execute(app,"SET search_path=pg_temp,public");
                assertEquals("true",value(app,"SELECT public.nq_decide_ordinary_place('one','SENT',2,true)"));
                assertEquals("MAY_HAVE_ESCAPED",value(reader,"SELECT state FROM ordinary_place_authorities WHERE order_id='one'"));
                execute(app,"SET search_path=public");
                reject(owner,"UPDATE ordinary_place_authorities SET state='NOT_ARMED',decided_at=NULL WHERE order_id='one'");
                reject(owner,"UPDATE ordinary_place_authorities SET state='REVOKED_BEFORE_SEND' WHERE order_id='one'");
                reject(owner,"UPDATE ordinary_place_authorities SET decided_at=now() WHERE order_id='one'");
                reject(owner,"DELETE FROM ordinary_place_authorities WHERE order_id='one'");
                reject(owner,"TRUNCATE ordinary_place_authorities");
                reject(app,"DELETE FROM public.orders WHERE order_id='one'");
                for (String assignment : new String[]{"client_order_id='replacement'", "account_id=999", "venue='PAPER'",
                        "trade_env='LIVE'", "symbol='ETH-USDT'", "side='SELL'", "type='MARKET'", "price=2", "qty=2"})
                    reject(app,"UPDATE public.orders SET " + assignment + " WHERE order_id='one'");
                create(owner,"invalid");
                reject(owner,"UPDATE ordinary_place_authorities SET state='INVALID',decided_at=now() WHERE order_id='invalid'");
                reject(owner,"UPDATE ordinary_place_authorities SET state='MAY_HAVE_ESCAPED',decided_at=NULL WHERE order_id='invalid'");
                reject(owner,"UPDATE ordinary_place_authorities SET state='REVOKED_BEFORE_SEND',decided_at=now() WHERE order_id='invalid'");
                assertEquals("NOT_ARMED",value(reader,"SELECT state FROM ordinary_place_authorities WHERE order_id='invalid'"));
                // 先建旧订单再试图新建 authority 必须失败；missing 永不被解释为 NOT_ARMED。
                for (int i=0;i<3;i++) execute(owner,"INSERT INTO orders(order_id,account_id,venue,symbol,client_order_id,side,type,price,qty,status,trace_id,exchange_code,trade_env,version) "
                        + "VALUES('legacy"+i+"',1,'OKX','BTC-USDT','legacy"+i+"','BUY','LIMIT',1,1,'SENT','db-proof','OKX','SIM',2)");
                assertThrows(SQLException.class,()->create(owner,"legacy0"));
                assertEquals("false",value(app,"SELECT public.nq_decide_ordinary_place('legacy0','SENT',2,true)"));
                assertEquals("false",value(app,"SELECT public.nq_decide_ordinary_place('legacy0','SENT',2,false)"));
                reject(app,"SELECT nq_backfill_ordinary_place_authorities(1)");
                reject(owner,"SELECT nq_backfill_ordinary_place_authorities(501)");
                owner.setAutoCommit(false); assertEquals("1",value(owner,"SELECT nq_backfill_ordinary_place_authorities(1)")); owner.rollback(); owner.setAutoCommit(true);
                assertEquals("3",value(reader,"SELECT count(*) FROM orders o WHERE NOT EXISTS(SELECT 1 FROM ordinary_place_authorities a WHERE a.order_id=o.order_id)"));
                assertEquals("1",value(owner,"SELECT nq_backfill_ordinary_place_authorities(1)"));
                assertEquals("2",value(owner,"SELECT nq_backfill_ordinary_place_authorities(500)"));
                assertEquals("0",value(owner,"SELECT nq_backfill_ordinary_place_authorities(500)"));
                assertEquals("3",value(reader,"SELECT count(*) FROM ordinary_place_authorities WHERE order_id LIKE 'legacy%' AND state='MAY_HAVE_ESCAPED'"));
                for (int round=0;round<6;round++) {
                    String id="race"+round; create(owner,id);
                    var start = new CyclicBarrier(2);
                    boolean mixed = round % 2 == 0;
                    try (var workers=Executors.newFixedThreadPool(2)) {
                        var first=workers.submit(()->decide(fixture.url(),id,true,start));
                        var second=workers.submit(()->decide(fixture.url(),id,!mixed,start));
                        boolean s=first.get(15,TimeUnit.SECONDS), r=second.get(15,TimeUnit.SECONDS);
                        assertTrue(s ^ r,"exactly one committed decision: "+id);
                        assertEquals(mixed && r ? "REVOKED_BEFORE_SEND" : "MAY_HAVE_ESCAPED",
                                value(reader,"SELECT state FROM ordinary_place_authorities WHERE order_id='"+id+"'"));
                    }
                }
                System.out.println("B5_PG_PASS schema=49 restrictedRole=true races=6 backfillRollback=true");
            }
        }
    }

    private boolean decide(String url,String id,boolean send,CyclicBarrier start) throws Exception {
        try (var c=DriverManager.getConnection(url,B0Fixture.APP,"")) {
            c.setAutoCommit(false); start.await(5,TimeUnit.SECONDS);
            boolean won=Boolean.parseBoolean(value(c,"SELECT nq_decide_ordinary_place('"+id+"','SENT',2,"+send+")"));
            if (won && !send) execute(c,"UPDATE orders SET status='CANCELLED',reason='ORDER_NOT_FOUND/OKX_51603',version=4 WHERE order_id='"+id+"'");
            c.commit(); return won;
        }
    }
    private void create(Connection c,String id) throws Exception {
        execute(c,createSql(id));
        execute(c,"UPDATE public.orders SET status='SENT',version=2 WHERE order_id='"+id+"'");
        allow(c,id);
    }
    private void allow(Connection c,String id) throws SQLException {
        execute(c,"INSERT INTO public.risk_events(risk_event_id,scope,scope_id,decision,trace_id) VALUES('risk-"+id+"','ORDER','"+id+"','ALLOW','db-proof')");
    }
    private String createSql(String id) {
        return "SELECT public.nq_create_ordinary_place_order('"+id+"',1,NULL,'OKX','BTC-USDT','"+id+"','BUY','LIMIT',1,1,'NEW',NULL,'db-proof','SIM',0,now())";
    }
    private static void execute(Connection c,String sql) throws SQLException { try(var s=c.createStatement()){s.execute(sql);} }
    private static String value(Connection c,String sql) throws SQLException { try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return String.valueOf(r.getObject(1));} }
    private static void reject(Connection c,String sql) { assertThrows(SQLException.class,()->execute(c,sql),sql); }
}

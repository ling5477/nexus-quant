package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.event.*;
import com.guidinglight.nexusquant.contracts.model.*;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.infra.jdbc.*;
import com.guidinglight.nexusquant.ledger.service.TradeLedgerPostingService;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import com.guidinglight.nexusquant.scheduler.infra.jdbc.JdbcTradeRepository;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;

/** 隔离 PG16/V48 的真实事务代理、提交拒绝及 source 行锁并发证明；不删除事件制造历史缺口。 */
@EnabledIfSystemProperty(named="nq.b4.pg",matches="true")
class B4TradeEventPostgresTest {
    @Configuration @EnableTransactionManagement static class Transactions { }

    @Test void eventCommitRejectionRollsBackTradeAndEventTogether() throws Exception {
        try(var pg=B0Processes.Pg.start(); var f=new Fixture(pg,"SIM")) {
            f.reject("event_store");
            assertThrows(RuntimeException.class,()->f.trades.insertWithRequiredEvent(f.trade));
            assertEquals(0,f.count("trades"));assertEquals(0,f.events());
            assertEquals(0,f.count("ledger_entries"));
            f.allow("event_store");f.trades.insertWithRequiredEvent(f.trade);
            assertEquals(1,f.count("trades"));assertEquals(1,f.events());
            assertEquals("SIM",f.jdbc.queryForObject("SELECT payload_json->'payload'->>'trade_env' FROM event_store WHERE event_type='TradeExecuted'",String.class));
            System.out.println("B4_PG_PASS atomic_commit_rejection");
        }
    }

    @Test void concurrentRecoveryAndLegacyEventAreUniqueInBothEnvironments() throws Exception {
        try(var pg=B0Processes.Pg.start()) {
            for(String env:new String[]{"SIM","LIVE"}) for(boolean legacy:new boolean[]{false,true}) {
                try(var f=new Fixture(pg,env);var second=f.context();var pool=Executors.newFixedThreadPool(2)) {
                    f.trades.insert(f.trade);
                    if(legacy)f.legacyEvent(f.trade.qty());
                    String before=legacy?f.eventSnapshot():null;
                    var other=second.getBean(TradeRepository.class);
                    // 先锁住同一 source，使两个独立连接都实际排队，而非顺序调用冒充并发。
                    try(var lock=f.source.getConnection()) {
                        lock.setAutoCommit(false);
                        try(var s=lock.createStatement()){s.executeQuery("SELECT trade_id FROM trades FOR UPDATE").close();}
                        var ready=new CountDownLatch(2);
                        var a=pool.submit(()->{ready.countDown();f.trades.ensureRequiredEvent(f.trade.tradeId());});
                        var b=pool.submit(()->{ready.countDown();other.ensureRequiredEvent(f.trade.tradeId());});
                        assertTrue(ready.await(5,TimeUnit.SECONDS));
                        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                        while(f.jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class)<2){
                            assertTrue(System.nanoTime()<end,"two database recovery attempts must wait on source lock");Thread.sleep(20);
                        }
                        lock.rollback();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
                    }
                    assertEquals(1,f.events());assertEquals(1,f.count("trades"));
                    if(legacy)assertEquals(before,f.eventSnapshot());
                    String stable=f.eventSnapshot();
                    for(int i=0;i<5;i++)f.trades.ensureRequiredEvent(f.trade.tradeId());
                    assertEquals(stable,f.eventSnapshot());
                    assertEquals(env,f.jdbc.queryForObject("SELECT trade_env FROM trades",String.class));
                    System.out.println("B4_PG_PASS concurrency env="+env+" legacy="+legacy+" waiters=2 event=1");
                }
            }
        }
    }

    @Test void eventAndLedgerFailureDoNotRepeatTheOtherDurableFact() throws Exception {
        try(var pg=B0Processes.Pg.start();var f=new Fixture(pg,"LIVE")) {
            // 历史 source-only API + canonical Ledger 形成分离事务状态；进程版另用真实旧 producer 窗口证明。
            f.trades.insert(f.trade);assertTrue(f.ledger.postTrade(f.request()).posted());
            String ledger=f.ledgerSnapshot();assertEquals(0,f.events());
            f.reject("event_store");assertThrows(RuntimeException.class,()->f.trades.ensureRequiredEvent(f.trade.tradeId()));
            assertEquals(ledger,f.ledgerSnapshot());assertEquals(0,f.events());
            f.allow("event_store");f.trades.ensureRequiredEvent(f.trade.tradeId());
            assertTrue(f.ledger.postTrade(f.request()).idempotentHit());assertEquals(ledger,f.ledgerSnapshot());
            assertEquals(1,f.events());
        }
        try(var pg=B0Processes.Pg.start();var f=new Fixture(pg,"SIM")) {
            f.trades.insertWithRequiredEvent(f.trade);String event=f.eventSnapshot();
            f.reject("ledger_entries");assertThrows(RuntimeException.class,()->f.ledger.postTrade(f.request()));
            assertEquals(0,f.count("ledger_entries"));assertEquals(0,f.count("ledger_events"));
            assertEquals(0,f.count("positions"));assertEquals(event,f.eventSnapshot());
            f.allow("ledger_entries");f.trades.ensureRequiredEvent(f.trade.tradeId());
            assertTrue(f.ledger.postTrade(f.request()).posted());assertEquals(4,f.count("ledger_entries"));
            String ledger=f.ledgerSnapshot();assertTrue(f.ledger.postTrade(f.request()).idempotentHit());
            assertEquals(ledger,f.ledgerSnapshot());assertEquals(event,f.eventSnapshot());
            System.out.println("B4_PG_PASS event_ledger_independent_recovery");
        }
    }

    @Test void conflictingHistoricalEventFailsClosedWithoutRewritingSource() throws Exception {
        try(var pg=B0Processes.Pg.start();var f=new Fixture(pg,"SIM")) {
            f.trades.insert(f.trade);f.legacyEvent(new BigDecimal("11"));String before=f.eventSnapshot();
            assertThrows(IllegalStateException.class,()->f.trades.ensureRequiredEvent(f.trade.tradeId()));
            assertEquals(before,f.eventSnapshot());assertEquals(1,f.count("trades"));
            assertEquals(0,f.trade.qty().compareTo(f.trades.findByOrderId("b4-order").orElseThrow().qty()));
            System.out.println("B4_PG_PASS conflict_fail_closed");
        }
    }

    static final class Fixture implements AutoCloseable {
        final B0Fixture database;
        final DriverManagerDataSource source;
        final JdbcTemplate jdbc;
        final AnnotationConfigApplicationContext app;
        final TradeRepository trades;
        final TradeLedgerPort ledger;
        final PaperTradeRecord trade;
        Fixture(B0Processes.Pg pg,String env)throws Exception {
            database=B0Fixture.create(pg);source=new DriverManagerDataSource(database.url(),"postgres","");jdbc=new JdbcTemplate(source);
            long account=jdbc.queryForObject("INSERT INTO accounts(account_code,venue,status) VALUES('b4-pg','OKX','ACTIVE') RETURNING account_id",Long.class);
            new JdbcOrderRepository(jdbc).insert(new OrderRecord("b4-order",account,null,"OKX","BTC-USDT","b4-client","BUY","LIMIT",
                    new BigDecimal("100"),new BigDecimal("10"),"b4-venue",OrderStatus.FILLED,"B4_PG","b4-trace",env),Instant.parse("2026-09-09T00:00:00Z"));
            trade=new PaperTradeRecord("b4-trade","b4-order",account,"BTC-USDT","OKX","b4-venue","b4-fill",new BigDecimal("100"),new BigDecimal("10"),new BigDecimal("0.01"),"USDT","b4-trace",Instant.parse("2026-09-09T00:00:00Z"));
            app=context();trades=app.getBean(TradeRepository.class);ledger=app.getBean(TradeLedgerPort.class);
        }
        AnnotationConfigApplicationContext context(){
            var c=new AnnotationConfigApplicationContext();c.register(Transactions.class);
            c.registerBean(JdbcTemplate.class,()->new JdbcTemplate(source));
            c.registerBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
            c.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(source));
            c.register(JdbcTradeRepository.class,EventStoreAppender.class,JdbcLedgerPostingRepository.class,
                    JdbcLedgerRiskAuditRepository.class,TradeLedgerPostingService.class);c.refresh();return c;
        }
        void reject(String table){
            jdbc.execute("CREATE OR REPLACE FUNCTION b4_reject() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'B4 commit rejected'; END $$");
            jdbc.execute("CREATE CONSTRAINT TRIGGER b4_reject AFTER INSERT ON "+table+" DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION b4_reject()");
        }
        void allow(String table){jdbc.execute("DROP TRIGGER b4_reject ON "+table);}
        int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
        int events(){return jdbc.queryForObject("SELECT count(*) FROM event_store WHERE event_type='TradeExecuted'",Integer.class);}
        String eventSnapshot(){return jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(e) ORDER BY event_id)::text,'[]') FROM event_store e WHERE event_type='TradeExecuted'",String.class);}
        String ledgerSnapshot(){return jdbc.queryForObject("SELECT jsonb_build_object('entries',(SELECT jsonb_agg(to_jsonb(e) ORDER BY entry_id) FROM ledger_entries e),'events',(SELECT jsonb_agg(to_jsonb(e) ORDER BY ledger_event_id) FROM ledger_events e),'positions',(SELECT jsonb_agg(to_jsonb(p)) FROM positions p),'snapshots',(SELECT jsonb_agg(to_jsonb(s) ORDER BY snapshot_id) FROM account_snapshots s))::text",String.class);}
        TradeLedgerRequest request(){return new TradeLedgerRequest(trade.tradeId(),trade.orderId(),trade.accountId(),trade.symbol(),OrderSide.BUY,trade.price(),trade.qty(),trade.fee(),trade.feeCurrency(),trade.traceId(),trade.ts());}
        void legacyEvent(BigDecimal qty){
            var payload=new TradeExecuted(trade.tradeId(),trade.orderId(),"b4-client",trade.accountId(),trade.symbol(),"OKX",trade.exchange(),trade.externalOrderId(),trade.exchangeTradeId(),trade.price(),qty,trade.fee(),trade.feeCurrency(),trade.ts());
            app.getBean(EventStoreAppender.class).append(TopicNames.TRADE_EVENT_V1,new EventEnvelope<>("b4-legacy-event","TradeExecuted",1,trade.ts(),"nq-scheduler.okx-rest-reconcile",trade.traceId(),"b4-client",payload));
        }
        public void close()throws Exception{app.close();database.close();}
    }
}

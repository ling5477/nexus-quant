package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.trading.infra.query.JdbcTradingQueryFacade;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 用独立PG验证账户、交易所、环境和分页隔离，不修改任何真实业务数据库。 */
@EnabledIfSystemProperty(named="nq.product.pg.required",matches="true")
class AccountActivityPostgresIntegrationTest {
    @Test void accountFactsRejectCrossScopeAndRetainExactPageAndAmounts() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            var flyway = Flyway.configure().dataSource(db.source()).locations("classpath:db/migration").cleanDisabled(true).load();
            var expected = TradingRestartRecoveryPostgresIntegrationTest.releaseMigrationScripts(
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                            B0Processes.root().resolve("release/source-manifest.json").toFile()));
            flyway.migrate();
            flyway.validate();
            var jdbc = new JdbcTemplate(db.source());
            assertEquals(expected.lastKey(), flyway.info().current().getVersion());
            TradingRestartRecoveryPostgresIntegrationTest.assertReleaseMigrationScripts(expected, flyway.info().applied());
            Long owner = jdbc.queryForObject("INSERT INTO users(username,password_hash) VALUES ('product-owner','NON_LOGIN_FIXTURE') RETURNING id",Long.class);
            long a = account(jdbc,owner,"a"), b = account(jdbc,owner,"b");
            orderTrade(jdbc,a,"a1","OKX","SIM","BTC-USDT");
            orderTrade(jdbc,a,"a2","OKX","SIM","BTC-USDT");
            orderTrade(jdbc,a,"a3","OKX","SIM","ETH-USDT");
            orderTrade(jdbc,b,"b1","OKX","SIM","BTC-USDT");
            orderTrade(jdbc,a,"wrong-venue","BINANCE","SIM","BTC-USDT");
            orderTrade(jdbc,a,"wrong-env","OKX","LIVE","BTC-USDT");
            var query = new JdbcTradingQueryFacade(jdbc);
            assertEquals(3,query.countAccountTrades(a,"OKX","SIM",null));
            assertEquals(2,query.countAccountTrades(a,"OKX","SIM","BTC-USDT"));
            var first=query.listAccountTrades(a,"OKX","SIM","BTC-USDT",0,1);
            var second=query.listAccountTrades(a,"OKX","SIM","BTC-USDT",1,1);
            assertEquals(1,first.size());assertEquals(1,second.size());
            assertNotEquals(first.getFirst().tradeId(),second.getFirst().tradeId());
            assertEquals(0,new java.math.BigDecimal("0.12345678").compareTo(first.getFirst().fee()));
            assertEquals("USDT",first.getFirst().feeCurrency());
            assertTrue(query.listAccountTrades(a,"OKX","SIM","BTC-USDT",2,1).isEmpty());
            assertEquals(1,query.countAccountTrades(b,"OKX","SIM",null));
            assertEquals(0,query.countAccountTrades(999999L,"OKX","SIM",null));
            assertThrows(IllegalArgumentException.class,()->query.listAccountTrades(a,"OKX","SIM",null,0,101));
            assertThrows(IllegalArgumentException.class,()->query.listAccountPositions(a,"OKX","DEMO",null,0,10));
            jdbc.update("INSERT INTO positions(account_id,symbol,qty,available_qty,avg_price,trace_id) VALUES (?,'BTC-USDT',1.25,1,100,'product')",a);
            jdbc.update("INSERT INTO positions(account_id,symbol,qty,available_qty,avg_price,trace_id) VALUES (?,'BTC-USDT',9,9,200,'product')",b);
            assertEquals(1,query.countAccountPositions(a,"OKX","SIM",null));
            assertEquals(0,query.countAccountPositions(a,"BINANCE","SIM",null));
            assertEquals(0,query.countAccountPositions(a,"OKX","LIVE",null));
            assertEquals(0,query.countAccountPositions(a,"OKX","SIM","ETH-USDT"));
            var position=query.listAccountPositions(a,"OKX","SIM",null,0,10).getFirst();
            assertEquals(a,position.accountId());
            assertEquals(0,new java.math.BigDecimal("1.25").compareTo(position.quantity()));
        }
    }
    private long account(JdbcTemplate jdbc,long owner,String alias) {
        Long exchange=jdbc.queryForObject("INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,status) VALUES (?,'OKX','SIM',?,'ACTIVE') RETURNING exchange_account_id",Long.class,owner,alias);
        Long legacy=jdbc.queryForObject("INSERT INTO accounts(account_code,venue,status) VALUES (?,'OKX','ACTIVE') RETURNING account_id",Long.class,"nq-okx-sim-"+exchange);
        jdbc.update("UPDATE exchange_accounts SET legacy_account_id=? WHERE exchange_account_id=?",legacy,exchange);
        return legacy;
    }
    private void orderTrade(JdbcTemplate jdbc,long account,String id,String venue,String env,String symbol) {
        jdbc.update("INSERT INTO orders(order_id,account_id,venue,symbol,client_order_id,side,type,price,qty,status,trade_env,trace_id) VALUES (?,?,?,?,?,'BUY','LIMIT',100,10,'FILLED',?,'product')",id,account,venue,symbol,id,env);
        jdbc.update("INSERT INTO trades(trade_id,order_id,account_id,symbol,exchange,price,qty,fee,fee_currency,trace_id,ts,trade_env) VALUES (?,?,?,?,?,100,1,0.12345678,'USDT','product',now(),?)","fill-"+id,id,account,symbol,venue,env);
    }
}

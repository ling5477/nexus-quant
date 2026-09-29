package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 在一次性 PostgreSQL schema 中验证 canonical account 绑定、空状态与跨 run 隔离。 */
class JdbcPaperRunCanonicalFactsRepositoryPostgresTest {
    @Test
    void readsOnlyFactsBoundToTheRequestedRun() {
        String url = System.getProperty("nq.postgres.smoke.url");
        boolean required = Boolean.getBoolean("nq.postgres.smoke.required");
        if (!required) assumeTrue(url != null, "Requires explicit disposable PostgreSQL source");
        assertTrue(url != null && url.startsWith("jdbc:postgresql://127.0.0.1:") && !url.contains("?"));
        String user = System.getProperty("nq.postgres.smoke.user");
        String password = System.getProperty("nq.postgres.smoke.password");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String schema = "paper_projection_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    url + "?currentSchema=" + schema, user, password));
            jdbc.execute("""
                    CREATE TABLE paper_trading_runs(paper_run_id text PRIMARY KEY, canonical_account_id bigint UNIQUE);
                    CREATE TABLE orders(order_id text PRIMARY KEY, account_id bigint, symbol text, side text,
                      type text, qty numeric, price numeric, status text, reason text,
                      created_at timestamptz, updated_at timestamptz);
                    CREATE TABLE trades(trade_id text PRIMARY KEY, order_id text, account_id bigint, symbol text,
                      price numeric, qty numeric, fee numeric, ts timestamptz, created_at timestamptz);
                    CREATE TABLE positions(id bigint PRIMARY KEY, account_id bigint, symbol text,
                      qty numeric, avg_price numeric, updated_at timestamptz);
                    INSERT INTO paper_trading_runs VALUES('run-a',11),('run-b',12),('legacy',NULL);
                    INSERT INTO orders VALUES('ord-a',11,'BTC-USDT','BUY','MARKET',1.25,100,'FILLED',NULL,
                      '2026-09-29T00:00:00Z','2026-09-29T00:01:00Z');
                    INSERT INTO trades VALUES('trd-a','ord-a',11,'BTC-USDT',100,1.25,0.1,
                      '2026-09-29T00:01:00Z','2026-09-29T00:01:00Z');
                    INSERT INTO positions VALUES(41,11,'BTC-USDT',1.25,100,'2026-09-29T00:01:00Z');
                    """);
            JdbcPaperRunCanonicalFactsRepository query = new JdbcPaperRunCanonicalFactsRepository(jdbc);
            assertTrue(query.isStrategySim("run-a"));
            assertTrue(query.isStrategySim("run-b"));
            assertFalse(query.isStrategySim("legacy"));
            assertEquals("ord-a", query.orders("run-a").getFirst().paperOrderId());
            assertEquals("FILLED", query.orders("run-a").getFirst().status().name());
            assertEquals("trd-a", query.trades("run-a").getFirst().paperTradeId());
            assertEquals("ord-a", query.trades("run-a").getFirst().paperOrderId());
            assertEquals(0, query.trades("run-a").getFirst().quantity().compareTo(new BigDecimal("1.25")));
            assertEquals(0, query.trades("run-a").getFirst().price().compareTo(new BigDecimal("100")));
            assertEquals(0, query.positions("run-a").getFirst().quantity().compareTo(new BigDecimal("1.25")));
            assertTrue(query.orders("run-b").isEmpty());
            assertTrue(query.trades("run-b").isEmpty());
            assertTrue(query.positions("run-b").isEmpty());
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}

package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 在显式提供的一次性 loopback PostgreSQL 中证明 V51 到 V52 的升级与约束。 */
@EnabledIfSystemProperty(named = "nq.strategy-sim.pg.required", matches = "true")
class StrategySimMigrationPostgresTest {
    @Test
    void migratesAndValidatesFreshAndUpgradePaths() {
        String url = System.getProperty("nq.strategy-sim.pg.url");
        if (url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:")) {
            throw new IllegalArgumentException("disposable loopback PostgreSQL URL required");
        }
        String schema = "strategy_sim_migration_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource source = new DriverManagerDataSource(url, "postgres", "disposable");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway before = Flyway.configure().dataSource(source).schemas(schema)
                    .locations("classpath:db/migration").target("51").load();
            before.migrate();
            before.validate();
            JdbcTemplate legacy = new JdbcTemplate(new DriverManagerDataSource(
                    url + "?currentSchema=" + schema, "postgres", "disposable"));
            legacy.update("""
                    INSERT INTO marketdata_bars(exchange_code,symbol,interval,open_time,close_time,
                        open_price,high_price,low_price,close_price,volume,source,ingested_at)
                    VALUES ('OKX','BTC-USDT','1m','2026-01-01T00:00:00Z','2026-01-01T00:00:59Z',
                        100,100,100,100,1,'IMPORT','2026-01-01T00:10:00Z')
                    """);
            Flyway after = Flyway.configure().dataSource(source).schemas(schema)
                    .locations("classpath:db/migration").target("52").load();
            assertEquals(1, after.migrate().migrationsExecuted);
            after.validate();
            assertEquals("52", after.info().current().getVersion().getVersion());
            assertEquals(0, after.info().pending().length);
            JdbcTemplate scoped = new JdbcTemplate(new DriverManagerDataSource(
                    url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                    "postgres", "disposable"));
            assertNotNull(scoped.queryForObject("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema=? AND table_name='paper_trading_runs'
                      AND column_name='canonical_account_id'
                    """, String.class, schema));
            assertNotNull(scoped.queryForObject("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema=? AND table_name='marketdata_bars'
                      AND column_name='available_at'
                    """, String.class, schema));
            assertEquals(1, scoped.queryForObject("""
                    SELECT COUNT(*) FROM pg_constraint c
                    JOIN pg_class t ON t.oid=c.conrelid
                    JOIN pg_namespace n ON n.oid=t.relnamespace
                    WHERE n.nspname=? AND t.relname='strategy_sim_decisions'
                      AND c.conname='uq_strategy_sim_decision_window'
                    """, Integer.class, schema));
            assertTrue(scoped.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.triggers
                    WHERE trigger_schema=? AND event_object_table='strategy_sim_decisions'
                      AND trigger_name='trg_strategy_sim_preserve_decision'
                    """, Integer.class, schema) >= 1);
            assertEquals(1, scoped.queryForObject("""
                    SELECT COUNT(*) FROM pg_constraint c
                    JOIN pg_class t ON t.oid=c.conrelid
                    JOIN pg_namespace n ON n.oid=t.relnamespace
                    WHERE n.nspname=? AND t.relname='marketdata_bars'
                      AND c.conname='chk_marketdata_bar_available_at'
                    """, Integer.class, schema));
            assertNull(scoped.queryForObject("SELECT available_at FROM marketdata_bars WHERE symbol='BTC-USDT'",
                    java.sql.Timestamp.class));
            assertEquals("2026-01-01T00:10:00Z", scoped.queryForObject("""
                    SELECT COALESCE(available_at,ingested_at) FROM marketdata_bars WHERE symbol='BTC-USDT'
                    """, java.sql.Timestamp.class).toInstant().toString());
            assertThrows(DataIntegrityViolationException.class, () -> scoped.update("""
                    UPDATE marketdata_bars SET available_at=close_time - interval '1 second'
                    WHERE symbol='BTC-USDT'
                    """));
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}

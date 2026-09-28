package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 随机隔离 schema 中证明既有 V55 可前向升级并校验连续 SIM 游标约束。 */
@EnabledIfSystemProperty(named = "nq.strategy-sim.pg.required", matches = "true")
class ContinuousSimMigrationPostgresTest {
    @Test
    void upgradesFromV55AndValidatesV56() {
        String url = System.getProperty("nq.strategy-sim.pg.url", "");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:")) {
            throw new IllegalArgumentException("disposable loopback PostgreSQL URL required");
        }
        String schema = "continuous_sim_migration_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource source = new DriverManagerDataSource(url,
                System.getProperty("nq.strategy-sim.pg.user", "postgres"),
                System.getProperty("nq.strategy-sim.pg.password", "disposable"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway before = Flyway.configure().dataSource(source).schemas(schema)
                    .locations("classpath:db/migration").target("55").load();
            before.migrate();
            before.validate();
            Flyway after = Flyway.configure().dataSource(source).schemas(schema)
                    .locations("classpath:db/migration").load();
            assertEquals(1, after.migrate().migrationsExecuted);
            after.validate();
            assertEquals("56", after.info().current().getVersion().getVersion());
            assertEquals(0, after.info().pending().length);
            JdbcTemplate scoped = new JdbcTemplate(new DriverManagerDataSource(
                    url + "?currentSchema=" + schema,
                    System.getProperty("nq.strategy-sim.pg.user", "postgres"),
                    System.getProperty("nq.strategy-sim.pg.password", "disposable")));
            assertEquals(2, scoped.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema=? AND table_name IN ('continuous_sim_runs','continuous_sim_bars')
                    """, Integer.class, schema));
            assertFalse(scoped.queryForList("""
                    SELECT conname FROM pg_constraint c
                    JOIN pg_class t ON t.oid=c.conrelid
                    JOIN pg_namespace n ON n.oid=t.relnamespace
                    WHERE n.nspname=? AND t.relname='continuous_sim_runs'
                      AND conname='chk_continuous_sim_cursor_order'
                    """, String.class, schema).isEmpty());
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}

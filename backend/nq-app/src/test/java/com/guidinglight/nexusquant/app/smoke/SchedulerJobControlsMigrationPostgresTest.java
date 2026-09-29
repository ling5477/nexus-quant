package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 隔离 PG16 上证明 V56 到 V57 前向升级、默认关闭和固定身份约束。 */
@EnabledIfSystemProperty(named = "nq.strategy-sim.pg.required", matches = "true")
class SchedulerJobControlsMigrationPostgresTest {
    @Test
    void upgradesFromV56WithEightDisabledFixedJobs() {
        String url = System.getProperty("nq.strategy-sim.pg.url", "");
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:")) {
            throw new IllegalArgumentException("disposable loopback PostgreSQL URL required");
        }
        String user = System.getProperty("nq.strategy-sim.pg.user", "postgres");
        String password = System.getProperty("nq.strategy-sim.pg.password", "disposable");
        String schema = "scheduler_controls_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        admin.execute("CREATE SCHEMA " + schema);
        try {
            // 历史迁移依赖 public 中已安装的 pgcrypto；Flyway 指定目标 schema 即可隔离表。
            Flyway.configure().dataSource(admin.getDataSource()).schemas(schema)
                    .locations("classpath:db/migration").target("56").load().migrate();
            Flyway latest = Flyway.configure().dataSource(admin.getDataSource()).schemas(schema)
                    .locations("classpath:db/migration").target("57").load();
            assertEquals(1, latest.migrate().migrationsExecuted);
            latest.validate();
            assertEquals("57", latest.info().current().getVersion().getVersion());
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    url + "?currentSchema=" + schema, user, password));
            assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM scheduled_job_controls", Integer.class));
            assertEquals(0, jdbc.queryForObject("""
                    SELECT count(*) FROM scheduled_job_controls
                    WHERE enabled OR next_run_at IS NOT NULL OR last_status <> 'NEVER_RUN'
                    """, Integer.class));
            assertThrows(DataAccessException.class, () -> jdbc.update("""
                    INSERT INTO scheduled_job_controls(job_key,fixed_delay_ms) VALUES ('UNKNOWN_JOB', 2000)
                    """));
            assertThrows(DataAccessException.class, () -> jdbc.update("""
                    UPDATE scheduled_job_controls SET fixed_delay_ms=1 WHERE job_key='PAPER_MATCHING'
                    """));
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}

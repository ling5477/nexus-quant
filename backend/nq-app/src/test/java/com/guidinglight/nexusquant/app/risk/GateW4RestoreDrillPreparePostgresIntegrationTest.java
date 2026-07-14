package com.guidinglight.nexusquant.app.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * GateW-4 disposable backup/restore drill 的 prepare 阶段。
 *
 * <p>该测试只接受 loopback 上、名称带 {@code nq_gatew4_disposable_} 前缀的临时数据库。
 * 它执行 V1-V35、写入一条无敏感信息的 validation review fixture，并把后续 dump/restore
 * 留给受保护的 PowerShell drill。默认 Maven 回归中没有显式 opt-in 时跳过。</p>
 */
class GateW4RestoreDrillPreparePostgresIntegrationTest {

    static final UUID REVIEW_CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000004004");

    @Test
    void migratesAndSeedsOnlyExplicitDisposableDatabase() {
        PostgresConfig config = PostgresConfig.fromEnvironment();
        assumeTrue(config.required(), "GateW-4 restore drill is disabled");
        assertTrue(config.safeDisposableTarget(), "restore drill target must be a disposable loopback database");

        Flyway flyway = Flyway.configure()
                .dataSource(config.url(), config.user(), config.password())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
        flyway.migrate();
        flyway.validate();

        MigrationInfo current = flyway.info().current();
        assertNotNull(current);
        assertEquals("35", current.getVersion().getVersion());
        assertEquals(0, flyway.info().pending().length);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(config.url());
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Long ownerId = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'system-migrated'",
                Long.class
        );
        assertNotNull(ownerId);
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        int inserted = jdbc.update(
                """
                        INSERT INTO validation_review_cases (
                            id, tenant_key, owner_id, evidence_type, evidence_source,
                            evidence_anchor, severity, state, title, summary, version,
                            created_by, created_at, updated_at, retention_until
                        ) VALUES (?, 'NQ_LOCAL', ?, 'GATEW4_OPERATIONAL_SAFETY',
                                  'LOCAL_DISPOSABLE_RESTORE_DRILL', CAST(? AS JSONB),
                                  'INFO', 'OPEN', 'GateW-4 disposable restore fixture',
                                  'Non-sensitive local drill fact', 0, ?, ?, ?, ?)
                        """,
                REVIEW_CASE_ID,
                ownerId,
                "{\"subject\":\"NQ-GATEW-4\",\"reference\":\"restore-drill-fixture-v1\"}",
                ownerId,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now.plusSeconds(2_592_000))
        );
        assertEquals(1, inserted);
        assertEquals("ENGAGED", jdbc.queryForObject(
                "SELECT status FROM kill_switch_states WHERE scope = 'GLOBAL_TRADING'",
                String.class
        ));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM kill_switch_events WHERE scope = 'GLOBAL_TRADING'",
                Long.class
        ));
    }

    private record PostgresConfig(String url, String user, String password, boolean required) {

        static PostgresConfig fromEnvironment() {
            return new PostgresConfig(
                    env("NQ_GATEW4_RESTORE_DB_URL"),
                    env("NQ_GATEW4_RESTORE_DB_USER"),
                    env("NQ_GATEW4_RESTORE_DB_PASSWORD"),
                    Boolean.parseBoolean(env("NQ_GATEW4_RESTORE_DRILL_REQUIRED"))
            );
        }

        boolean safeDisposableTarget() {
            if (url.isBlank() || user.isBlank() || password.isBlank()) {
                return false;
            }
            try {
                URI uri = URI.create(url.substring("jdbc:".length()));
                String host = uri.getHost();
                String path = uri.getPath();
                return ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                        && path != null
                        && path.substring(1).startsWith("nq_gatew4_disposable_");
            } catch (RuntimeException ex) {
                return false;
            }
        }
    }

    private static String env(String name) {
        return System.getenv().getOrDefault(name, "").trim();
    }
}

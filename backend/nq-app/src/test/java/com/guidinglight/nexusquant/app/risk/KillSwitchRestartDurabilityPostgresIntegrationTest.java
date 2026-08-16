package com.guidinglight.nexusquant.app.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.risk.infra.jdbc.JdbcKillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchVersionConflictException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * V35 migration、optimistic engage、append-only event 与跨 Spring context restart durability 的
 * 真实 PostgreSQL integration test。
 *
 * <p>只使用随机 disposable schema 与脱敏 fixture；不启动 web、scheduler、adapter、credential、
 * order、account、ledger 或任何外部网络。</p>
 */
class KillSwitchRestartDurabilityPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.postgres.smoke.required";
    private static final String URL_PROPERTY = "nq.postgres.smoke.url";
    private static final String USER_PROPERTY = "nq.postgres.smoke.user";
    private static final String PASSWORD_PROPERTY = "nq.postgres.smoke.password";
    private static final Instant FIRST_NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant SECOND_NOW = Instant.parse("2030-01-01T00:00:01Z");

    @Test
    void freshMigrationAndTwoSpringContextsPreserveEngagedState() {
        PostgresConfig config = PostgresConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "kill-switch PostgreSQL integration is disabled");
        }
        assertTrue(config.configured(), "missing required local disposable PostgreSQL properties");

        String schema = "gatew4_ks_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
                .dataSource(config.url(), config.user(), config.password())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .cleanDisabled(false)
                .target("35")
                .load();
        try {
            flyway.migrate();
            MigrationInfo current = flyway.info().current();
            assertNotNull(current);
            assertEquals("35", current.getVersion().getVersion());

            long durableVersion;
            Instant durableUpdatedAt;
            try (AnnotationConfigApplicationContext first = context(config, schema, FIRST_NOW)) {
                JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
                KillSwitchService service = first.getBean(KillSwitchService.class);

                KillSwitchSnapshot seeded = service.snapshot();
                assertEquals(KillSwitchStatus.ENGAGED, seeded.status());
                assertEquals(1, seeded.version());
                assertEquals(1L, eventCount(jdbc));

                seedDisengagedFixture(jdbc);
                KillSwitchSnapshot engaged = service.engage(
                        2,
                        "RESTART_DURABILITY_TEST",
                        "integration-test",
                        "trace-restart-durability"
                );
                assertEquals(KillSwitchStatus.ENGAGED, engaged.status());
                assertEquals(3, engaged.version());
                assertEquals(FIRST_NOW, engaged.updatedAt());
                assertEquals(3L, eventCount(jdbc));

                KillSwitchSnapshot idempotent = service.engage(
                        3,
                        "REPEATED_ENGAGE",
                        "integration-test",
                        "trace-repeat"
                );
                assertEquals(3, idempotent.version());
                assertEquals(3L, eventCount(jdbc));
                assertThrows(KillSwitchVersionConflictException.class, () -> service.engage(
                        2,
                        "STALE_VERSION",
                        "integration-test",
                        "trace-stale-version"
                ));

                durableVersion = engaged.version();
                durableUpdatedAt = engaged.updatedAt();
            }

            try (AnnotationConfigApplicationContext second = context(config, schema, SECOND_NOW)) {
                KillSwitchSnapshot afterRestart = second.getBean(KillSwitchService.class).snapshot();
                assertEquals(KillSwitchStatus.ENGAGED, afterRestart.status());
                assertEquals(durableVersion, afterRestart.version());
                assertEquals(durableUpdatedAt, afterRestart.updatedAt());
                assertEquals(3L, eventCount(second.getBean(JdbcTemplate.class)));
            }
        } finally {
            flyway.clean();
        }
    }

    private static AnnotationConfigApplicationContext context(
            PostgresConfig config,
            String schema,
            Instant now
    ) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(withCurrentSchema(config.url(), schema));
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(KillSwitchStateRepository.class,
                () -> new JdbcKillSwitchStateRepository(context.getBean(JdbcTemplate.class)));
        context.registerBean(KillSwitchService.class, () -> new KillSwitchService(
                context.getBean(KillSwitchStateRepository.class),
                Clock.fixed(now, ZoneOffset.UTC)
        ));
        context.refresh();
        return context;
    }

    private static void seedDisengagedFixture(JdbcTemplate jdbc) {
        Instant occurredAt = FIRST_NOW.minusSeconds(1);
        int updated = jdbc.update(
                """
                        UPDATE kill_switch_states
                        SET status = 'DISENGAGED', version = 2, reason_code = 'TEST_ONLY_DISENGAGED',
                            source = 'POSTGRES_TEST_FIXTURE', updated_at = ?, updated_by = 'integration-test',
                            trace_id = 'trace-test-disengaged'
                        WHERE scope = 'GLOBAL_TRADING' AND version = 1 AND status = 'ENGAGED'
                        """,
                java.sql.Timestamp.from(occurredAt)
        );
        assertEquals(1, updated);
        jdbc.update(
                """
                        INSERT INTO kill_switch_events (
                            id, scope, from_status, to_status, state_version, reason_code,
                            source, actor_id, trace_id, occurred_at
                        ) VALUES (?, 'GLOBAL_TRADING', 'ENGAGED', 'DISENGAGED', 2,
                                  'TEST_ONLY_DISENGAGED', 'POSTGRES_TEST_FIXTURE',
                                  'integration-test', 'trace-test-disengaged', ?)
                        """,
                UUID.randomUUID(),
                java.sql.Timestamp.from(occurredAt)
        );
    }

    private static long eventCount(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM kill_switch_events WHERE scope = 'GLOBAL_TRADING'",
                Long.class
        );
        return count == null ? 0 : count;
    }

    private static String withCurrentSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }

    private record PostgresConfig(String url, String user, String password, boolean required) {
        static PostgresConfig fromSystemProperties() {
            return new PostgresConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
            );
        }

        boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }
    }

    private static String property(String name) {
        return System.getProperty(name, "").trim();
    }
}

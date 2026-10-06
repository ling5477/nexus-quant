package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

/**
 * 正式基线的 Flyway/PostgreSQL 运行装配验证。
 *
 * <p>Why：nq-app 持有 production Flyway PostgreSQL database plugin；该测试在显式配置的
 * PostgreSQL 中创建唯一随机 schema，从空 schema 安装正式 V1，并只清理该随机 schema。
 * 它不 clean public/shared schema，不启动 Spring context、scheduler、adapter 或 credential 路径。
 */
class ValidationReviewFlywayPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.postgres.smoke.required";
    private static final String URL_PROPERTY = "nq.postgres.smoke.url";
    private static final String USER_PROPERTY = "nq.postgres.smoke.user";
    private static final String PASSWORD_PROPERTY = "nq.postgres.smoke.password";

    @Test
    void shouldInstallAndValidateSingleReleaseBaseline() {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "PostgreSQL Flyway integration is disabled");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        String schema = "gatev_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
                .dataSource(config.url(), config.user(), config.password())
                .locations("filesystem:../nq-infra/src/main/resources/db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .cleanDisabled(false)
                .target("1")
                .load();
        try {
            MigrateResult result = flyway.migrate();
            MigrationInfo current = flyway.info().current();

            assertNotNull(current);
            assertEquals("1", current.getVersion().getVersion());
            assertEquals(1, result.migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.info().pending().length);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals("Success", result.success ? "Success" : "Failure");
        } finally {
            flyway.clean();
        }

        // Repository integration 使用同一显式 PostgreSQL 的 standard public schema；这里只做
        // forward-only migrate/validate，不执行 clean，兼容空库和已迁移 CI service database。
        Flyway publicFlyway = Flyway.configure()
                .dataSource(config.url(), config.user(), config.password())
                .locations("filesystem:../nq-infra/src/main/resources/db/migration")
                .load();
        publicFlyway.migrate();
        assertEquals("1", publicFlyway.info().current().getVersion().getVersion());
    }

    private record SmokeConfig(String url, String user, String password, boolean required) {
        static SmokeConfig fromSystemProperties() {
            return new SmokeConfig(
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

package com.guidinglight.nexusquant.app.livecontrol;

import com.guidinglight.nexusquant.account.infra.jdbc.CanonicalLegacyAccountBridgeService;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 在一次性 PostgreSQL 上证明业务名称收敛、账户桥接和数据库拒绝边界。 */
class SchemaSemanticConsolidationPostgresIntegrationTest {
    @Test
    void materializesFreshSchemaWithoutChangingObjectCountsOrLeavingRetiredNames() {
        try (Fixture fixture = new Fixture()) {
            fixture.before.migrate();
            assertEquals("58", fixture.before.info().current().getVersion().getVersion());
            fixture.assertAuthorityLabelCheck("OPERATOR_PILOT", "OPERATOR_CONTROLLED_EXECUTION");
            List<Integer> beforeCounts = fixture.counts();
            fixture.latest.migrate();
            fixture.latest.validate();
            assertEquals("59", fixture.latest.info().current().getVersion().getVersion());
            fixture.assertAuthorityLabelCheck("OPERATOR_CONTROLLED_EXECUTION", "OPERATOR_PILOT");
            assertEquals(0, fixture.latest.info().pending().length);
            assertEquals(beforeCounts, fixture.counts());
            assertEquals(0, fixture.jdbc.queryForObject("""
                    SELECT count(*) FROM (
                        SELECT relname AS name FROM pg_class WHERE relnamespace=?::regnamespace
                        UNION ALL
                        SELECT proname FROM pg_proc WHERE pronamespace=?::regnamespace
                        UNION ALL
                        SELECT conname FROM pg_constraint WHERE connamespace=?::regnamespace
                        UNION ALL
                        SELECT tgname FROM pg_trigger trigger JOIN pg_class relation ON relation.oid=trigger.tgrelid
                        WHERE relation.relnamespace=?::regnamespace AND NOT trigger.tgisinternal
                    ) names WHERE name ~* '(gate|pilot|phase|stage|attempt)'
                    """, Integer.class, fixture.schema, fixture.schema, fixture.schema, fixture.schema));
            for (String function : List.of("canonical_legacy_account_code",
                    "guard_canonical_account_compatibility_bridge", "controlled_execution_boundary_is_empty",
                    "operator_execution_authority_digest", "reconstruct_execution_scope_hash")) {
                assertEquals(1, fixture.jdbc.queryForObject(
                        "SELECT count(*) FROM pg_proc WHERE pronamespace=?::regnamespace AND proname=?",
                        Integer.class, fixture.schema, function));
            }
            assertEquals(Boolean.TRUE, fixture.jdbc.queryForObject(
                    "SELECT controlled_execution_boundary_is_empty()", Boolean.class));
        }
    }

    @Test
    void preservesSimAndLiveBridgeIdentityAndRejectsInvalidOrMutableBindings() {
        try (Fixture fixture = new Fixture()) {
            fixture.latest.migrate();
            fixture.latest.validate();
            JdbcTemplate jdbc = fixture.jdbc;
            long owner = jdbc.queryForObject("""
                    INSERT INTO users(username,password_hash) VALUES (?, 'isolated-schema-fixture')
                    RETURNING id
                    """, Long.class, "schema-owner-" + UUID.randomUUID());
            JdbcExchangeAccountRepository accounts = new JdbcExchangeAccountRepository(jdbc);
            CanonicalLegacyAccountBridgeService bridge = new CanonicalLegacyAccountBridgeService(jdbc);
            TransactionTemplate transactions = new TransactionTemplate(
                    new DataSourceTransactionManager(jdbc.getDataSource()));
            for (String environment : List.of("SIM", "LIVE")) {
                var account = accounts.create(owner, "OKX", environment, "schema-" + environment, null, Instant.now());
                long identity = transactions.execute(status -> "SIM".equals(environment)
                        ? bridge.resolveOrCreateSim(account, "schema-proof", Instant.now())
                        : bridge.resolveOrCreate(account, "schema-proof", Instant.now()));
                assertEquals("nq-okx-" + environment.toLowerCase(java.util.Locale.ROOT) + "-" + account.exchangeAccountId(),
                        jdbc.queryForObject("SELECT account_code FROM accounts WHERE account_id=?", String.class, identity));
                assertEquals(identity, jdbc.queryForObject(
                        "SELECT legacy_account_id FROM exchange_accounts WHERE exchange_account_id=?",
                        Long.class, account.exchangeAccountId()));
                assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                        "UPDATE exchange_accounts SET legacy_account_id=NULL WHERE exchange_account_id=?",
                        account.exchangeAccountId()));
            }
            var invalid = accounts.create(owner, "OKX", "SIM", "invalid-bridge", null, Instant.now());
            long wrong = jdbc.queryForObject("""
                    INSERT INTO accounts(account_code,venue,status) VALUES ('wrong-identity','OKX','ACTIVE')
                    RETURNING account_id
                    """, Long.class);
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE exchange_accounts SET legacy_account_id=? WHERE exchange_account_id=?",
                    wrong, invalid.exchangeAccountId()));
            assertEquals(null, accounts.findById(invalid.exchangeAccountId()).orElseThrow().legacyAccountId());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM exchange_account_credentials", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trades", Integer.class));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final String schema = "schema_semantics_" + UUID.randomUUID().toString().replace("-", "");
        final JdbcTemplate jdbc;
        final Flyway before;
        final Flyway latest;

        Fixture() {
            String url = System.getProperty("nq.postgres.smoke.url", "");
            String user = System.getProperty("nq.postgres.smoke.user", "");
            String password = System.getProperty("nq.postgres.smoke.password", "");
            boolean required = Boolean.parseBoolean(System.getProperty("nq.postgres.smoke.required", "false"));
            if (!required) assumeTrue(!url.isBlank() && !user.isBlank() && !password.isBlank());
            assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:"), "必须使用隔离的 loopback PostgreSQL");
            String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
            var dataSource = new DriverManagerDataSource(schemaUrl, user, password);
            jdbc = new JdbcTemplate(dataSource);
            jdbc.setQueryTimeout(15);
            assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("16."));
            before = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema).defaultSchema(schema)
                    .createSchemas(true).cleanDisabled(false).target("58")
                    .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
            latest = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema).defaultSchema(schema)
                    .createSchemas(true).cleanDisabled(false)
                    .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        }

        List<Integer> counts() {
            return List.of(
                    jdbc.queryForObject("SELECT count(*) FROM pg_class WHERE relnamespace=?::regnamespace AND relkind IN ('r','p')",
                            Integer.class, schema),
                    jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=?::regnamespace", Integer.class, schema),
                    jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE pronamespace=?::regnamespace", Integer.class, schema),
                    jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid WHERE c.relnamespace=?::regnamespace AND NOT t.tgisinternal",
                            Integer.class, schema));
        }

        void assertAuthorityLabelCheck(String accepted, String retired) {
            String check = jdbc.queryForObject("""
                    SELECT pg_get_constraintdef(oid) FROM pg_constraint
                    WHERE connamespace=?::regnamespace AND conname='chk_live_sessions_authority_type'
                    """, String.class, schema);
            // 复制真实 catalog CHECK，在同一列类型上验证无损 cast 和标签迁移的拒绝边界。
            jdbc.execute("CREATE TABLE authority_label_proof(authority_type varchar(32) NOT NULL)");
            try {
                jdbc.execute("ALTER TABLE authority_label_proof ADD CONSTRAINT authority_label_check " + check);
                jdbc.update("INSERT INTO authority_label_proof VALUES (?)", "STRATEGY");
                jdbc.update("INSERT INTO authority_label_proof VALUES (?)", accepted);
                assertThrows(DataIntegrityViolationException.class,
                        () -> jdbc.update("INSERT INTO authority_label_proof VALUES (?)", retired));
                assertThrows(DataIntegrityViolationException.class,
                        () -> jdbc.update("INSERT INTO authority_label_proof VALUES (?)", "UNSUPPORTED"));
                assertThrows(DataIntegrityViolationException.class,
                        () -> jdbc.update("INSERT INTO authority_label_proof VALUES (NULL)"));
                assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM authority_label_proof", Integer.class));
            } finally {
                jdbc.execute("DROP TABLE authority_label_proof");
            }
        }

        @Override
        public void close() {
            latest.clean();
        }
    }
}

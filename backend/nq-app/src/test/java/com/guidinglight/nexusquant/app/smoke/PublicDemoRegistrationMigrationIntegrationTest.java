package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V44 原有身份和经济行完整保留；升级冲突整体回滚，不自动改名既有用户。 */
@EnabledIfSystemProperty(named = "nq.demo.pg.required", matches = "true")
class PublicDemoRegistrationMigrationIntegrationTest {
    @Test void upgradesFromAcceptedTenantBaselineWithoutChangingHistoricalFactsOrChecksums() throws Exception {
        try (var db = TenantIsolationTestDatabase.create("44")) {
            historicalUser(db, "preserved-local", List.of("ADMIN", "OPERATOR", "VIEWER"));
            var tx = new TransactionTemplate(new JdbcTransactionManager(db.admin.getDataSource()));
            tx.executeWithoutResult(status -> {
                db.admin.queryForObject("SELECT set_config('nq.tenant_id',?,true)", String.class, TenantId.LOCAL.value().toString());
                db.admin.queryForObject("SELECT set_config('nq.execution_epoch','1',true)", String.class);
                long account = db.admin.queryForObject("INSERT INTO accounts(account_code,venue) VALUES('preserved-economics','PAPER') RETURNING account_id", Long.class);
                TenantIsolationTestDatabase.attachFundingRun(db.admin, account, "ptr-preserved-before-demo");
                db.admin.update("""
                        INSERT INTO ledger_entries(entry_id,account_id,currency,delta,balance_after,direction,ref_type,ref_id,idempotency_key,trace_id,ts)
                        VALUES('le-preserved-cash',?,'USDT',123.45678901,123.45678901,'CREDIT','SIM_FUNDING_CASH','ptr-preserved-before-demo','preserved-cash','synthetic-preservation',now())
                        """, account);
                db.admin.update("""
                        INSERT INTO ledger_entries(entry_id,account_id,currency,delta,balance_after,direction,ref_type,ref_id,idempotency_key,trace_id,ts)
                        VALUES('le-preserved-contra',?,'USDT',-123.45678901,123.45678901,'DEBIT','SIM_FUNDING_CONTRA','ptr-preserved-before-demo','preserved-contra','synthetic-preservation',now())
                        """, account);
            });
            var before = snapshot(db);
            var users = db.admin.queryForList("SELECT to_jsonb(u)::text FROM users u ORDER BY id", String.class);
            var checksums = db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var migration = db.migration(null);
            assertEquals(1, migration.migrate().migrationsExecuted); migration.validate();
            assertEquals("45", migration.info().current().getVersion().toString());
            assertEquals(before, snapshot(db));
            assertEquals(users, db.admin.queryForList("SELECT (to_jsonb(u)-'email'-'email_verified_at')::text FROM users u ORDER BY id", String.class));
            assertEquals(checksums, db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int<=44 ORDER BY installed_rank"));
            assertEquals(0, migration.migrate().migrationsExecuted);
            assertEquals(0, PublicDemoRegistrationHttpIntegrationTest.count(db, "tenant_sim_accounts"));
        }
    }

    @Test void preexistingNormalizedUsernameCollisionFailsWholeMigrationWithoutChangingExistingUsers() throws Exception {
        try (var db = TenantIsolationTestDatabase.create("44")) {
            historicalUser(db, "Existing.Case", List.of("VIEWER")); historicalUser(db, "existing.case", List.of("VIEWER"));
            var before = db.admin.queryForList("SELECT to_jsonb(u)::text FROM users u ORDER BY id", String.class);
            assertThrows(FlywayException.class, () -> db.migration(null).migrate());
            assertEquals(before, db.admin.queryForList("SELECT to_jsonb(u)::text FROM users u ORDER BY id", String.class));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='email'", Integer.class));
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='45'", Integer.class));
        }
    }

    private static Map<String, List<String>> snapshot(TenantIsolationTestDatabase db) {
        var result = new TreeMap<String, List<String>>();
        for (String table : List.of("tenants", "tenant_memberships", "accounts", "exchange_accounts", "paper_trading_runs", "ledger_entries", "ledger_events", "account_snapshots", "positions", "orders", "trades"))
            result.put(table, db.admin.queryForList("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY to_jsonb(t)::text", String.class));
        return result;
    }

    private static void historicalUser(TenantIsolationTestDatabase db, String name, List<String> roles) {
        new JdbcAuthUserRepository(db.admin).upsertSeedUser(new SeedUserCommand(name,
                new BCryptPasswordEncoder().encode("SyntheticHistoricalPassword123!"), roles, true));
    }
}

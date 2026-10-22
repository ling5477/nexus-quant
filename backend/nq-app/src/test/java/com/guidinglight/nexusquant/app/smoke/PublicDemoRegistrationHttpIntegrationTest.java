package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 Servlet、受限 PostgreSQL 角色和本机 SMTP 证明注册并发及资金事务，禁止访问共享用户库。 */
@EnabledIfSystemProperty(named = "nq.demo.pg.required", matches = "true")
class PublicDemoRegistrationHttpIntegrationTest {
    @Test void eachAnonymousEndpointHasDatabaseBackedRequestLimitsAndRejectsPrivilegeFields() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null);
                var app = PublicDemoTestHttp.start(db);
                var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
            assertEquals(413, http.send("POST", "/api/auth/register", null, Map.of("padding", "x".repeat(100_000))).statusCode());
            var forged = new java.util.LinkedHashMap<>(PublicDemoTestHttp.registration(UUID.randomUUID(), "forged-demo", email("forged")));
            forged.put("initialCapital", "999999"); forged.put("role", "ADMIN"); forged.put("tradeEnv", "LIVE");
            assertEquals(400, http.send("POST", "/api/auth/register", null, forged).statusCode());
            for (int i = 1; i < 30; i++) assertEquals(400, http.send("POST", "/api/auth/register", null, Map.of()).statusCode());
            assertEquals(429, http.send("POST", "/api/auth/register", null, Map.of()).statusCode());
            for (int i = 0; i < 60; i++) assertEquals(400, http.send("POST", "/api/auth/verify-email", null, Map.of("token", "invalid")).statusCode());
            assertEquals(429, http.send("POST", "/api/auth/verify-email", null, Map.of("token", "invalid")).statusCode());
            for (int i = 0; i < 20; i++) assertEquals(400, http.send("POST", "/api/auth/resend-verification", null, Map.of("email", "invalid")).statusCode());
            assertEquals(429, http.send("POST", "/api/auth/resend-verification", null, Map.of("email", "invalid")).statusCode());
            assertEquals(0, count(db, "users")); assertEquals(0, count(db, "tenant_sim_accounts"));
        }
    }

    @Test void distinctRegistrationOperationsCompetingForSameNormalizedUsernameOrEmailCreateOnlyOneUser() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null);
                var app = PublicDemoTestHttp.start(db);
                var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
            race(2, () -> http.ok("POST", "/api/auth/register", null,
                    PublicDemoTestHttp.registration(UUID.randomUUID(), "same-name-demo", email("unique"))));
            String shared = email("same-email");
            race(2, () -> http.ok("POST", "/api/auth/register", null,
                    PublicDemoTestHttp.registration(UUID.randomUUID(), "demo-" + UUID.randomUUID(), shared)));
            assertEquals(2, count(db, "users")); assertEquals(2, count(db, "demo_registrations"));
            assertEquals(2, count(db, "demo_email_verifications")); assertEquals(0, count(db, "tenant_sim_accounts"));
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM users WHERE username='same-name-demo'", Integer.class));
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM users WHERE email=?", Integer.class, shared));
        }
    }

    @Test void concurrentRegistrationAndVerificationMintExactlyOnceAndReplayOriginalIdentity() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null);
                var app = PublicDemoTestHttp.start(db);
                var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
            UUID registration = UUID.randomUUID();
            String email = email("concurrent");
            var request = PublicDemoTestHttp.registration(registration, "Concurrent.Demo", email.toUpperCase());
            race(4, () -> assertEquals("ACCEPTED", http.ok("POST", "/api/auth/register", null, request).path("status").asText()));
            assertEquals(1, count(db, "users"));
            assertEquals(1, count(db, "demo_registrations"));
            assertEquals(1, count(db, "demo_email_verifications"));
            assertEquals(0, count(db, "tenant_sim_accounts"));
            assertFalse(db.admin.queryForObject("SELECT enabled FROM users WHERE username='concurrent.demo'", Boolean.class));
            int pendingLogin = http.send("POST", "/api/auth/login", null,
                    Map.of("username", "concurrent.demo", "password", PublicDemoTestHttp.PASSWORD)).statusCode();
            assertTrue(pendingLogin == 401 || pendingLogin == 403, "pending user must not obtain session");
            db.admin.update("UPDATE users SET enabled=true WHERE username='concurrent.demo'");
            assertEquals(401, http.send("POST", "/api/auth/login", null,
                    Map.of("username", "concurrent.demo", "password", PublicDemoTestHttp.PASSWORD)).statusCode());
            db.admin.update("UPDATE users SET enabled=false WHERE username='concurrent.demo'");
            assertEquals(401, http.send("GET", "/api/research-configs", null, null).statusCode());
            assertEquals(401, http.send("GET", "/api/paper-trading/strategy-sim/analysis/runs", null, null).statusCode());
            var sameName = PublicDemoTestHttp.registration(UUID.randomUUID(), " CONCURRENT.DEMO ", email("other"));
            var sameEmail = PublicDemoTestHttp.registration(UUID.randomUUID(), "other-demo", email);
            race(2, () -> http.ok("POST", "/api/auth/register", null, sameName));
            race(2, () -> http.ok("POST", "/api/auth/register", null, sameEmail));
            assertEquals(1, count(db, "users"));
            assertEquals(409, http.send("POST", "/api/auth/register", null,
                    PublicDemoTestHttp.registration(registration, "different-demo", email)).statusCode());
            String token = http.mailToken(email);
            assertFalse(db.admin.queryForList("SELECT to_jsonb(v)::text FROM demo_email_verifications v", String.class)
                    .stream().anyMatch(row -> row.contains(token)), "database must store verification digest only");
            race(4, () -> assertEquals("READY", http.ok("POST", "/api/auth/verify-email", null, Map.of("token", token)).path("status").asText()));
            String session = http.login(" CONCURRENT.DEMO ");
            var result = http.ok("GET", "/api/demo/registration-result", session, null);
            var me = http.ok("GET", "/api/auth/me", session, null);
            var ownAccount = http.ok("GET", "/api/demo/sim-account", session, null);
            assertEquals(result.path("exchangeAccountId").asLong(), me.path("defaultExchangeAccountId").asLong());
            assertEquals(result.path("exchangeAccountId").asLong(), ownAccount.path("exchangeAccountId").asLong());
            assertEquals("PAPER", me.path("defaultExchangeCode").asText()); assertEquals("SIM", me.path("defaultTradeEnv").asText());
            assertEquals("SIM_NOT_BOUND", result.path("status").asText());
            assertEquals("10000.00000000", result.path("initialCapital").asText());
            assertNull(db.admin.queryForObject("SELECT bound_paper_run_id FROM tenant_sim_accounts", String.class));
            assertEquals(0, count(db, "paper_trading_runs"));
            assertFunding(db, 1);
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM tenants WHERE tenant_kind='DEMO'", Integer.class));
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM tenant_memberships WHERE membership_kind='DEMO'", Integer.class));
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM tenant_execution_controls WHERE tenant_id IN (SELECT tenant_id FROM tenant_sim_accounts)", Integer.class));
            assertEquals(1, db.admin.queryForObject("SELECT count(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.role_code='DEMO_USER'", Integer.class));
            assertEquals(0, count(db, "exchange_account_credentials"));
            http.ok("POST", "/api/auth/register", null, request);
            assertEquals(result, http.ok("GET", "/api/demo/registration-result", http.login("concurrent.demo"), null));
            assertFunding(db, 1);
        }
    }

    @Test void expiredAndExhaustedTokensAreRejectedAndInitializationFailureRollsBackEveryBusinessFact() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null);
                var app = PublicDemoTestHttp.start(db);
                var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
            UUID expired = UUID.randomUUID(); String expiredEmail = email("expired");
            http.ok("POST", "/api/auth/register", null, PublicDemoTestHttp.registration(expired, "expired-demo", expiredEmail));
            String expiredToken = http.mailToken(expiredEmail);
            db.admin.update("UPDATE demo_email_verifications SET created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' WHERE registration_id=?", expired);
            assertEquals(400, http.send("POST", "/api/auth/verify-email", null, Map.of("token", expiredToken)).statusCode());
            assertEquals(0, count(db, "tenant_sim_accounts"));
            assertEquals("ACCEPTED", http.ok("POST", "/api/auth/resend-verification", null, Map.of("email", expiredEmail)).path("status").asText());
            String refreshed = http.mailToken(expiredEmail, expiredToken);
            assertNotEquals(expiredToken, refreshed);
            assertEquals(400, http.send("POST", "/api/auth/verify-email", null, Map.of("token", expiredToken)).statusCode());

            UUID exhausted = UUID.randomUUID(); String exhaustedEmail = email("exhausted");
            http.ok("POST", "/api/auth/register", null, PublicDemoTestHttp.registration(exhausted, "exhausted-demo", exhaustedEmail));
            String good = http.mailToken(exhaustedEmail);
            String forged = good.substring(0, 37) + (good.charAt(37) == 'A' ? "B" : "A") + good.substring(38);
            for (int i = 0; i < 5; i++) assertEquals(400, http.send("POST", "/api/auth/verify-email", null, Map.of("token", forged)).statusCode());
            assertEquals(400, http.send("POST", "/api/auth/verify-email", null, Map.of("token", good)).statusCode());
            assertEquals(5, db.admin.queryForObject("SELECT attempts FROM demo_email_verifications WHERE registration_id=?", Integer.class, exhausted));

            UUID rollback = UUID.randomUUID(); String rollbackEmail = email("rollback");
            http.ok("POST", "/api/auth/register", null, PublicDemoTestHttp.registration(rollback, "rollback-demo", rollbackEmail));
            String rollbackToken = http.mailToken(rollbackEmail);
            db.admin.execute("""
                    CREATE FUNCTION demo_test_reject_funding() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN IF NEW.ref_type='SIM_FUNDING_CONTRA' THEN RAISE EXCEPTION 'synthetic funding interruption'; END IF; RETURN NEW; END $$
                    """);
            db.admin.execute("CREATE TRIGGER demo_test_funding_failure BEFORE INSERT ON ledger_entries FOR EACH ROW EXECUTE FUNCTION demo_test_reject_funding()");
            assertTrue(http.send("POST", "/api/auth/verify-email", null, Map.of("token", rollbackToken)).statusCode() >= 400);
            assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM tenants WHERE tenant_kind='DEMO'", Integer.class));
            assertEquals(0, count(db, "tenant_sim_accounts"));
            assertEquals(0, count(db, "exchange_accounts"));
            assertEquals(0, count(db, "accounts"));
            assertEquals(0, count(db, "ledger_entries"));
            assertEquals("PENDING", db.admin.queryForObject("SELECT status FROM demo_registrations WHERE registration_id=?", String.class, rollback));
            assertNull(db.admin.queryForObject("SELECT consumed_at FROM demo_email_verifications WHERE registration_id=?", Object.class, rollback));
            db.admin.execute("DROP TRIGGER demo_test_funding_failure ON ledger_entries");
            db.admin.execute("DROP FUNCTION demo_test_reject_funding()");
            assertEquals("READY", http.ok("POST", "/api/auth/verify-email", null, Map.of("token", rollbackToken)).path("status").asText());
            assertFunding(db, 1);
            assertEquals("READY", http.ok("POST", "/api/auth/verify-email", null, Map.of("token", refreshed)).path("status").asText());
            assertFunding(db, 2);
            var absent = http.ok("POST", "/api/auth/resend-verification", null, Map.of("email", email("absent")));
            var present = http.ok("POST", "/api/auth/resend-verification", null, Map.of("email", rollbackEmail));
            assertEquals(absent, present);
        }
    }

    @Test void failedMailDeliveryAndPendingRegistrationRecoverAcrossRealProcessRestartWithoutMintingAgain() throws Exception {
        try (var db = TenantIsolationTestDatabase.create(null)) {
            UUID id = UUID.randomUUID(); String email = email("restart");
            try (var app = PublicDemoTestHttp.start(db, "--spring.mail.port=1");
                    var http = new PublicDemoTestHttp(app.getWebServer().getPort())) {
                http.ok("POST", "/api/auth/register", null, PublicDemoTestHttp.registration(id, "restart-demo", email));
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                while (db.admin.queryForObject("SELECT COALESCE(max(attempts),0) FROM demo_email_outbox", Integer.class) == 0 && System.nanoTime() < deadline)
                    Thread.sleep(100);
                assertTrue(db.admin.queryForObject("SELECT max(attempts)>0 FROM demo_email_outbox", Boolean.class));
                assertEquals(0, count(db, "tenant_sim_accounts"));
            }
            db.admin.update("UPDATE demo_email_outbox SET next_attempt_at=now() WHERE status='PENDING'");
            restart(db, "VERIFY_PENDING", email, id);
            assertFunding(db, 1);
            var before = TenantIsolationHttpIntegrationTest.economicSnapshot(db);
            restart(db, "READ_READY", email, id);
            assertEquals(before, TenantIsolationHttpIntegrationTest.economicSnapshot(db));
            assertFunding(db, 1);
        }
    }

    static int count(TenantIsolationTestDatabase db, String table) {
        if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("test table required");
        return db.admin.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    static void assertFunding(TenantIsolationTestDatabase db, int expected) {
        assertEquals(expected, count(db, "tenant_sim_accounts"));
        assertEquals(expected, count(db, "exchange_accounts"));
        assertEquals(expected, count(db, "accounts"));
        assertEquals(expected * 2, db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type IN ('SIM_FUNDING_CASH','SIM_FUNDING_CONTRA')", Integer.class));
        assertEquals(expected, db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type='SIM_FUNDING_CASH' AND currency='USDT' AND delta=10000.00000000", Integer.class));
        assertEquals(expected, db.admin.queryForObject("SELECT count(*) FROM ledger_entries WHERE ref_type='SIM_FUNDING_CONTRA' AND currency='USDT' AND delta=-10000.00000000", Integer.class));
        assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM ledger_entries e JOIN tenant_sim_accounts d ON d.account_id=e.account_id WHERE e.ref_type LIKE 'SIM_FUNDING_%' AND e.ref_id<>d.reserved_paper_run_id", Integer.class));
        assertEquals(0, db.admin.queryForObject("SELECT count(*) FROM tenant_sim_accounts d JOIN exchange_accounts e USING(exchange_account_id) JOIN tenant_memberships m ON m.tenant_id=d.tenant_id WHERE e.owner_user_id<>m.user_id OR e.trade_env<>'SIM' OR e.exchange_code<>'PAPER'", Integer.class));
        assertEquals(0, new BigDecimal("0").compareTo(db.admin.queryForObject("SELECT COALESCE(sum(delta),0) FROM ledger_entries WHERE ref_type LIKE 'SIM_FUNDING_%'", BigDecimal.class)));
    }

    static String email(String prefix) { return prefix + "-" + UUID.randomUUID().toString().replace("-", "") + "@demo.test"; }

    private static void race(int threads, CheckedAction action) throws Exception {
        try (var workers = Executors.newFixedThreadPool(threads)) {
            var ready = new CountDownLatch(1); var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < threads; i++) tasks.add(workers.submit(() -> { ready.await(); action.run(); return null; }));
            ready.countDown();
            for (var result : tasks) result.get(40, TimeUnit.SECONDS);
        }
    }

    private static void restart(TenantIsolationTestDatabase db, String mode, String email, UUID registration) throws Exception {
        Path directory = Path.of("target", "demo-registration-evidence").toAbsolutePath(); Files.createDirectories(directory);
        Path arguments = directory.resolve(mode + ".args"); Path log = directory.resolve(mode + ".log");
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Files.writeString(arguments, "-Duser.timezone=UTC\n-cp\n\"" + cp.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\"\n" + PublicDemoRegistrationRestartProbeMain.class.getName() + "\n");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(), "@" + arguments);
        builder.environment().put("TASK_DEMO_URL", db.url()); builder.environment().put("TASK_DEMO_USER", db.runtime);
        builder.environment().put("TASK_DEMO_PASSWORD", db.runtimePassword); builder.environment().put("TASK_DEMO_MODE", mode);
        builder.environment().put("TASK_DEMO_EMAIL", email); builder.environment().put("TASK_DEMO_REGISTRATION", registration.toString());
        var process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(55, TimeUnit.SECONDS), "demo application restart timeout");
            assertEquals(0, process.exitValue(), "restart evidence: " + log);
            assertTrue(Files.readString(log).contains("DEMO_REGISTRATION_RESTART_VERIFIED"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}

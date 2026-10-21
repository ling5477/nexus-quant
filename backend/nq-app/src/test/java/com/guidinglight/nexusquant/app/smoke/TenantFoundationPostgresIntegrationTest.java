package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.security.token.port.TokenService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务独占 PostgreSQL 中证明升级保持事实、成员约束、真实认证与 Servlet 权限边界。 */
@EnabledIfSystemProperty(named = "nq.product.pg.required", matches = "true")
class TenantFoundationPostgresIntegrationTest {
    private static final String PASSWORD = "SyntheticTenantPassword123";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void upgradesHistoricalUsersInBatchesWithoutChangingAnyExistingBusinessRow() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            var baseline = flyway(db, "34");
            baseline.migrate(); baseline.validate();
            var jdbc = new JdbcTemplate(db.source());
            var users = new JdbcAuthUserRepository(jdbc);
            users.upsertSeedUser(new SeedUserCommand("old-admin", HASH, List.of("ADMIN", "OPERATOR", "VIEWER"), true));
            jdbc.update("""
                    INSERT INTO users(username,password_hash,enabled,auth_version)
                    SELECT 'old-user-'||n,?,n%2=0,3 FROM generate_series(1,1005) n
                    """, HASH);
            long admin = users.findByUsername("old-admin").orElseThrow().userId();
            jdbc.update("""
                    INSERT INTO auth_sessions(session_id,user_id,auth_version,created_at,last_active_at,expires_at)
                    VALUES(?,?,1,now(),now(),now()+interval '1 hour')
                    """, UUID.randomUUID().toString(), admin);
            seedHistoricalEconomics(jdbc);
            var before = snapshot(jdbc);
            var oldChecksums = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
            var upgrade = flyway(db, null);
            assertEquals(1, upgrade.migrate().migrationsExecuted);
            upgrade.validate();
            assertEquals("35", upgrade.info().current().getVersion().toString());
            assertEquals(before, snapshot(jdbc, before.keySet().stream().toList()));
            assertEquals(oldChecksums, jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version::int<=34 ORDER BY installed_rank"));
            assertEquals(1006L, jdbc.queryForObject("SELECT count(*) FROM tenant_memberships", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM tenant_memberships WHERE tenant_id<>? OR membership_role<>'LOCAL_MEMBER'",
                    Long.class, TenantId.LOCAL.value()));
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            assertEquals(before, snapshot(jdbc, before.keySet().stream().toList()));
        }
    }

    @Test
    void migrationFailureRollsBackCompletelyAndCanResumeWithoutRepairOrDuplicateUsers() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            flyway(db, "34").migrate();
            var jdbc = new JdbcTemplate(db.source());
            new JdbcAuthUserRepository(jdbc).upsertSeedUser(new SeedUserCommand("rollback-admin", HASH, List.of("ADMIN"), true));
            var before = snapshot(jdbc);
            // 故障发生在用户归属回填之后，检验整个正向迁移事务的回滚，不修改正式脚本。
            jdbc.execute("""
                    CREATE FUNCTION reject_tenant_test_ddl() RETURNS event_trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF EXISTS(SELECT 1 FROM pg_event_trigger_ddl_commands()
                            WHERE object_identity LIKE '%trg_preserve_tenant_identity%') THEN
                            RAISE EXCEPTION 'synthetic migration interruption';
                        END IF;
                    END $$
                    """);
            jdbc.execute("CREATE EVENT TRIGGER tenant_test_interruption ON ddl_command_end WHEN TAG IN ('CREATE TRIGGER') EXECUTE FUNCTION reject_tenant_test_ddl()");
            assertThrows(FlywayException.class, () -> flyway(db, null).migrate());
            assertEquals(before, snapshot(jdbc, before.keySet().stream().toList()));
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('tenants','tenant_memberships')", Long.class));
            jdbc.execute("DROP EVENT TRIGGER tenant_test_interruption");
            jdbc.execute("DROP FUNCTION reject_tenant_test_ddl()");
            var retry = flyway(db, null);
            assertEquals(1, retry.migrate().migrationsExecuted);
            retry.validate();
            assertEquals(before, snapshot(jdbc, before.keySet().stream().toList()));
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM tenant_memberships", Long.class));
        }
    }

    @Test
    void databaseEnforcesMembershipKindsUniquenessForeignKeysAndStableIdentity() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            flyway(db, null).migrate();
            var jdbc = new JdbcTemplate(db.source());
            var users = new JdbcAuthUserRepository(jdbc);
            long localA = seedUser(users, "local-a", "ADMIN");
            seedUser(users, "local-b", "VIEWER");
            assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM tenant_memberships WHERE tenant_id=?", Long.class, TenantId.LOCAL.value()));
            long demoA = seedUser(users, "demo-a", "DEMO_USER");
            long demoB = seedUser(users, "demo-b", "DEMO_USER");
            UUID tenant = demoTenant(jdbc, "DEMO_CONSTRAINTS");
            membership(jdbc, tenant, demoA);
            assertThrows(DataIntegrityViolationException.class, () -> membership(jdbc, tenant, demoA));
            assertThrows(DataIntegrityViolationException.class, () -> membership(jdbc, tenant, demoB));
            assertThrows(DataIntegrityViolationException.class, () -> membership(jdbc, UUID.randomUUID(), demoB));
            assertThrows(DataIntegrityViolationException.class, () -> membership(jdbc, tenant, localA));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tenant_memberships SET membership_role='PLATFORM_ADMIN' WHERE user_id=?", demoA));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tenant_memberships SET membership_kind='LOCAL',membership_role='LOCAL_MEMBER' WHERE user_id=?", demoA));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tenant_memberships SET status='UNKNOWN' WHERE user_id=?", demoA));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tenants SET tenant_code='REBOUND' WHERE tenant_id=?", tenant));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tenant_memberships SET tenant_id=? WHERE user_id=?", TenantId.LOCAL.value(), demoA));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO tenants(tenant_id,tenant_code,tenant_name,tenant_kind) VALUES('00000000-0000-0000-0000-000000000000','ZERO','零','DEMO')"));
            assertEquals(1, users.findTenantMemberships(demoA).size());
        }
    }

    @Test
    void realApplicationResolvesDatabaseIdentityAndDeniesAllUnmigratedDemoBusinessAccess() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            flyway(db, null).migrate();
            var jdbc = new JdbcTemplate(db.source());
            var users = new JdbcAuthUserRepository(jdbc);
            seedUser(users, "foundation-local", "ADMIN", "OPERATOR", "VIEWER");
            long demoA = seedUser(users, "foundation-demo-a", "DEMO_USER");
            long demoB = seedUser(users, "foundation-demo-b", "DEMO_USER");
            long missing = seedUser(users, "foundation-missing", "DEMO_USER");
            seedUser(users, "foundation-platform", "PLATFORM_ADMIN");
            UUID tenantA = demoTenant(jdbc, "DEMO_HTTP_A"), tenantB = demoTenant(jdbc, "DEMO_HTTP_B");
            membership(jdbc, tenantA, demoA); membership(jdbc, tenantB, demoB);
            var application = new SpringApplication(NexusQuantApplication.class, ProbeController.class);
            application.setRegisterShutdownHook(false);
            try (var context = (ServletWebServerApplicationContext) application.run(
                    "--server.address=127.0.0.1", "--server.port=0", "--server.tomcat.threads.max=1", "--server.tomcat.threads.min-spare=1",
                    "--spring.profiles.active=ci,tenant-foundation-test", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                    "--spring.datasource.url=" + db.url(), "--spring.datasource.username=" + System.getProperty("nq.product.pg.user"),
                    "--spring.datasource.password=" + System.getProperty("nq.product.pg.password"),
                    "--spring.datasource.hikari.maximum-pool-size=2", "--nq.env-safety.no-outbound=true", "--nq.env-safety.live-enabled=false",
                    "--nq.strategy-sim.enabled=false", "--nq.continuous-sim.enabled=false", "--nq.instrument.catalog-sync.enabled=false",
                    "--nq.okx.ws.enabled=false", "--nq.binance.ws.enabled=false", "--nq.okx.recovery.enabled=false",
                    "--nq.auth.bootstrap-admin.enabled=false", "--nq.public-news.enabled=false", "--nq.account.performance.sampling-enabled=false",
                    "--nq.security.issuer=tenant-foundation-test", "--nq.security.secret=tenant-foundation-synthetic-signing-material-123456")) {
                try (var http = new Http(context.getWebServer().getPort())) {
                assertEquals(1, context.getBeansOfType(ProbeController.class).size());
                String local = http.login("foundation-local"), a = http.login("foundation-demo-a"), b = http.login("foundation-demo-b");
                String platform = http.login("foundation-platform");
                assertEquals(TenantId.LOCAL.toString(), http.json("/api/auth/tenant", local).path("tenantId").asText());
                assertEquals(tenantA.toString(), http.json("/api/auth/tenant", a).path("tenantId").asText());
                assertEquals(tenantB.toString(), http.json("/api/auth/tenant", b).path("tenantId").asText());
                assertEquals(200, http.send("GET", "/api/auth/me", a, null).statusCode());
                assertTrue(http.json("/api/auth/me", a).path("defaultExchangeAccountId").isNull());
                assertEquals(401, http.send("GET", "/api/auth/tenant", null, null).statusCode());
                assertEquals(403, http.send("GET", "/api/auth/tenant?tenantId=" + tenantB, a, null).statusCode());
                assertEquals(403, http.send("GET", "/api/auth/tenant", a, null, "X-Tenant-ID", tenantB.toString()).statusCode());
                for (String path : List.of("/api/strategies", "/api/backtest-runs", "/api/paper-trading/runs", "/api/scheduler/jobs", "/api/platform/tenants", "/actuator/readonlyproviderobservation")) {
                    assertEquals(403, http.send("GET", path, a, null).statusCode(), path);
                    assertEquals(403, http.send("GET", path, platform, null).statusCode(), path);
                }
                for (String path : List.of("/api/trading/orders", "/api/strategies", "/api/platform/users/1/disable"))
                    assertEquals(403, http.send("POST", path, a, "{}").statusCode(), path);
                assertEquals(403, http.send("HEAD", "/api/strategies", a, null).statusCode());
                assertEquals(403, http.send("GET", "/api/auth/tenant", platform, null).statusCode());
                assertEquals(200, http.send("GET", "/api/auth/me", platform, null).statusCode());
                for (String path : List.of("/api/strategies", "/api/research-configs", "/api/backtest-runs", "/api/paper-trading/runs"))
                    assertEquals(200, http.send("GET", path, local, null).statusCode(), path);

                long sessionCount = jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Long.class);
                assertEquals(401, http.loginResponse("foundation-missing").statusCode());
                assertEquals(sessionCount, jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Long.class));
                assertTrue(users.findTenantMemberships(missing).isEmpty());
                jdbc.update("UPDATE tenant_memberships SET status='DISABLED' WHERE user_id=?", demoA);
                assertEquals(401, http.send("GET", "/api/auth/tenant", a, null).statusCode());
                assertEquals(401, http.loginResponse("foundation-demo-a").statusCode());
                jdbc.update("UPDATE tenant_memberships SET status='ACTIVE' WHERE user_id=?", demoA);
                jdbc.update("UPDATE tenants SET status='DISABLED' WHERE tenant_id=?", tenantA);
                assertEquals(401, http.send("GET", "/api/auth/tenant", a, null).statusCode());
                jdbc.update("UPDATE tenants SET status='ACTIVE' WHERE tenant_id=?", tenantA);
                jdbc.update("UPDATE users SET enabled=false WHERE id=?", demoA);
                assertEquals(401, http.send("GET", "/api/auth/tenant", a, null).statusCode());
                jdbc.update("UPDATE users SET enabled=true WHERE id=?", demoA);

                var tokens = context.getBean(TokenService.class);
                Instant now = Instant.now();
                String legacy = tokens.issue(new TokenClaims("foundation-local", "foundation-local", List.of("VIEWER"), now, now.plusSeconds(60),
                        "tenant-foundation-test", "legacy-fixture", 1, null));
                assertEquals(200, http.send("GET", "/api/auth/tenant", legacy, null).statusCode());
                var claims = tokens.parse(a).orElseThrow();
                String forgedRoles = tokens.issue(new TokenClaims(claims.subject(), claims.username(), List.of("ADMIN", "OPERATOR", "PLATFORM_ADMIN"),
                        claims.issuedAt(), claims.expiresAt(), claims.issuer(), "forged-fixture", claims.authVersion(), claims.sessionId()));
                assertEquals(403, http.send("GET", "/api/strategies", forgedRoles, null).statusCode());
                String noSession = tokens.issue(new TokenClaims(claims.subject(), claims.username(), List.of("ADMIN"), now, now.plusSeconds(60),
                        claims.issuer(), "missing-session-fixture", claims.authVersion(), null));
                assertEquals(401, http.send("GET", "/api/auth/tenant", noSession, null).statusCode());
                jdbc.update("INSERT INTO user_roles(user_id,role_id,granted_at) SELECT ?,id,now() FROM roles WHERE role_code='OPERATOR'", demoA);
                assertEquals(401, http.send("GET", "/api/strategies", a, null).statusCode());
                jdbc.update("DELETE FROM user_roles WHERE user_id=? AND role_id=(SELECT id FROM roles WHERE role_code='OPERATOR')", demoA);

                assertEquals(200, http.send("POST", "/api/auth/renew", b, null).statusCode());
                assertEquals(204, http.send("POST", "/api/auth/logout", b, null).statusCode());
                assertEquals(401, http.send("POST", "/api/auth/renew", b, null).statusCode());
                assertEquals(500, http.send("GET", "/test/tenant-failure", local, null).statusCode());
                assertEquals("NONE", http.send("GET", "/test/tenant-probe", null, null).body());
                assertFalse(context.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
                        .flatMap(mapping -> mapping.getHandlerMethods().keySet().stream())
                        .anyMatch(mapping -> mapping.getPatternValues().contains("/api/auth/register")));
                assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
                }
            }
        }
    }

    private static Flyway flyway(ReleaseBaselineTestDatabase db, String target) {
        var configuration = Flyway.configure().dataSource(db.source()).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static long seedUser(AuthUserRepository users, String username, String... roles) {
        users.upsertSeedUser(new SeedUserCommand(username, HASH, List.of(roles), true));
        return users.findByUsername(username).orElseThrow().userId();
    }

    private static UUID demoTenant(JdbcTemplate jdbc, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants(tenant_id,tenant_code,tenant_name,tenant_kind) VALUES(?,?,?,'DEMO')", id, code, "隔离测试租户");
        return id;
    }

    private static void membership(JdbcTemplate jdbc, UUID tenant, long user) {
        jdbc.update("INSERT INTO tenant_memberships(tenant_id,user_id,membership_kind,membership_role) VALUES(?,?,'DEMO','DEMO_USER')", tenant, user);
    }

    private static Map<String, List<String>> snapshot(JdbcTemplate jdbc) {
        return snapshot(jdbc, jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename", String.class));
    }

    private static Map<String, List<String>> snapshot(JdbcTemplate jdbc, List<String> tables) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : tables) {
            if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("invalid fixture table");
            String filter = table.equals("roles") ? " WHERE role_code NOT IN ('DEMO_USER','PLATFORM_ADMIN')" : "";
            result.put(table, jdbc.queryForList("SELECT to_jsonb(t)::text FROM " + table + " t" + filter + " ORDER BY to_jsonb(t)::text", String.class));
        }
        return result;
    }

    private static void seedHistoricalEconomics(JdbcTemplate jdbc) {
        long account = jdbc.queryForObject("INSERT INTO accounts(account_code,venue) VALUES('tenant-upgrade-fixture','PAPER') RETURNING account_id", Long.class);
        jdbc.update("""
                INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,status,trace_id,venue,exchange_code,trade_env)
                VALUES('upgrade-order',?,'BTC-USDT','upgrade-client','BUY','LIMIT',100,1,'FILLED','upgrade-trace','PAPER','PAPER','SIM')
                """, account);
        jdbc.update("""
                INSERT INTO trades(trade_id,order_id,account_id,symbol,exchange,price,qty,fee,fee_currency,trace_id,ts,exchange_code,trade_env)
                VALUES('upgrade-trade','upgrade-order',?,'BTC-USDT','PAPER',100,1,0.01,'USDT','upgrade-trace',now(),'PAPER','SIM')
                """, account);
        jdbc.update("""
                INSERT INTO ledger_entries(entry_id,account_id,currency,delta,balance_after,direction,ref_type,ref_id,idempotency_key,trace_id,ts)
                VALUES('upgrade-entry',?,'USDT',-100.01,9899.99,'DEBIT','TRADE','upgrade-trade','upgrade-ledger-key','upgrade-trace',now())
                """, account);
        jdbc.update("INSERT INTO ledger_events(entry_id,event_type,payload_json,trace_id) VALUES('upgrade-entry','POSTED','{}','upgrade-trace')");
        jdbc.update("INSERT INTO positions(account_id,symbol,qty,available_qty,frozen_qty,avg_price,trace_id,updated_at) VALUES(?,'BTC-USDT',1,1,0,100,'upgrade-trace',now())", account);
        assertNotNull(jdbc.queryForObject("SELECT balance_after FROM ledger_entries WHERE entry_id='upgrade-entry'", String.class));
    }

    @RestController
    @Profile("tenant-foundation-test")
    static class ProbeController {
        @GetMapping("/test/tenant-failure") String fail() throws IOException {
            TenantContext.requireCurrent();
            throw new IOException("synthetic request failure");
        }
        @GetMapping("/test/tenant-probe") String probe() {
            return TenantContext.current().map(identity -> identity.tenantId().toString()).orElse("NONE");
        }
    }

    private static final class Http implements AutoCloseable {
        private final URI base;
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        Http(int port) { base = URI.create("http://127.0.0.1:" + port); }

        @Override public void close() { client.close(); }

        String login(String username) throws Exception {
            var response = loginResponse(username);
            assertEquals(200, response.statusCode(), "login status for " + username);
            return JSON.readTree(response.body()).path("accessToken").asText();
        }

        HttpResponse<String> loginResponse(String username) throws Exception {
            return send("POST", "/api/auth/login", null, JSON.writeValueAsString(Map.of("username", username, "password", PASSWORD)));
        }

        JsonNode json(String path, String token) throws Exception {
            var response = send("GET", path, token, null);
            assertEquals(200, response.statusCode(), path);
            return JSON.readTree(response.body());
        }

        HttpResponse<String> send(String method, String path, String token, String body, String... headers) throws Exception {
            var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10));
            if (token != null) request.header("Authorization", "Bearer " + token);
            if (headers.length > 0) request.headers(headers);
            if (body != null) request.header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}

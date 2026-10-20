package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.auth.application.command.LoginRequest;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.port.AuthService;
import com.guidinglight.nexusquant.auth.application.service.DbAuthService;
import com.guidinglight.nexusquant.auth.application.service.PasswordChangeService;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.security.token.config.JwtTokenSettings;
import com.guidinglight.nexusquant.security.token.service.JwtTokenService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** 明确隔离PG中的真实会话、升级、并发和代际撤销；时钟可控，不等待真实半小时。 */
@EnabledIfSystemProperty(named = "nq.product.pg.required", matches = "true")
class AuthSessionPostgresIntegrationTest {
    static class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(Instant.now());
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time.get(); }
        void advance(long seconds) { time.updateAndGet(value -> value.plusSeconds(seconds)); }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean AuthUserRepository users(DataSource source) { return new JdbcAuthUserRepository(new JdbcTemplate(source)); }
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean JwtTokenService tokens() { return new JwtTokenService(new JwtTokenSettings("session-test", "synthetic-session-test-signing-key-123456789", Duration.ofHours(1))); }
        @Bean AuthService auth(AuthUserRepository users, MutableClock clock, JwtTokenService tokens) {
            return new DbAuthService(tokens, new BCryptPasswordEncoder(), users, "session-test", 3600, 1800, 28800, clock);
        }
        @Bean PasswordChangeService passwords(AuthUserRepository users) { return new PasswordChangeService(users, new BCryptPasswordEncoder()); }
    }

    private AnnotationConfigApplicationContext context(DataSource source) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source); context.register(Config.class); context.refresh();
        return context;
    }

    private void seed(AuthUserRepository users) {
        users.upsertSeedUser(new SeedUserCommand("session-admin", new BCryptPasswordEncoder().encode("SyntheticSession123"), List.of("ADMIN", "VIEWER"), true));
    }

    @Test void upgradePreservesUserAndRenewalCannotExtendMaximumOrResurrectIdle() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            var before = Flyway.configure().dataSource(db.source()).locations("classpath:db/migration").target("33").load();
            before.migrate();
            var jdbc = new JdbcTemplate(db.source());
            seed(new JdbcAuthUserRepository(jdbc));
            var beforeUser = jdbc.queryForMap("SELECT id,password_hash,auth_version FROM users WHERE username='session-admin'");
            var upgrade = Flyway.configure().dataSource(db.source()).locations("classpath:db/migration").load();
            assertEquals(1, upgrade.migrate().migrationsExecuted); upgrade.validate();
            assertEquals(beforeUser, jdbc.queryForMap("SELECT id,password_hash,auth_version FROM users WHERE username='session-admin'"));
            try (var context = context(db.source())) {
                var auth = context.getBean(AuthService.class); var clock = context.getBean(MutableClock.class);
                var tokens = context.getBean(JwtTokenService.class); var users = context.getBean(AuthUserRepository.class);
                var login = auth.login(new LoginRequest("session-admin", "SyntheticSession123"));
                var current = login;
                for (int i = 0; i < 23; i++) {
                    clock.advance(1200);
                    current = auth.renew(tokens.parse(current.accessToken()).orElseThrow());
                    assertEquals(login.sessionId(), current.sessionId());
                    assertEquals(login.sessionExpiresAt(), current.sessionExpiresAt());
                }
                clock.advance(1200);
                var last = tokens.parse(current.accessToken()).orElseThrow();
                assertThrows(BadCredentialsException.class, () -> auth.renew(last));
                assertFalse(users.isSessionActive(login.sessionId(), login.username(), 1, clock.instant(), clock.instant().minusSeconds(1800)));
                // 独立新登录验证空闲边界；不通过任何查询更新last_active_at。
                var idle = auth.login(new LoginRequest("session-admin", "SyntheticSession123"));
                var idleClaims = tokens.parse(idle.accessToken()).orElseThrow();
                clock.advance(1800);
                assertThrows(BadCredentialsException.class, () -> auth.renew(idleClaims));
                assertFalse(users.isSessionActive(idle.sessionId(), idle.username(), 1, clock.instant(), clock.instant().minusSeconds(1800)));
            }
        }
    }

    @Test void logoutRacesCannotResurrectAndPasswordGenerationRevokesSessions() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            Flyway.configure().dataSource(db.source()).locations("classpath:db/migration").load().migrate();
            try (var context = context(db.source())) {
                var auth = context.getBean(AuthService.class); var users = context.getBean(AuthUserRepository.class);
                var tokens = context.getBean(JwtTokenService.class); seed(users);
                var one = auth.login(new LoginRequest("session-admin", "SyntheticSession123"));
                var two = auth.login(new LoginRequest("session-admin", "SyntheticSession123"));
                var claims = tokens.parse(one.accessToken()).orElseThrow();
                try (var pool = Executors.newFixedThreadPool(2)) {
                    var renewal = pool.submit(() -> { try { auth.renew(claims); } catch (BadCredentialsException expected) { /* 注销先获锁时拒绝续期。 */ } });
                    var logout = pool.submit(() -> auth.logout(claims));
                    renewal.get(); logout.get();
                }
                assertThrows(BadCredentialsException.class, () -> auth.renew(claims));
                assertNotNull(auth.renew(tokens.parse(two.accessToken()).orElseThrow()));
                context.getBean(PasswordChangeService.class).changePassword("session-admin", 1, "SyntheticSession123", "SyntheticChanged456");
                assertThrows(BadCredentialsException.class, () -> auth.renew(tokens.parse(two.accessToken()).orElseThrow()));
                var jdbc = new JdbcTemplate(db.source());
                assertNotNull(jdbc.queryForObject("SELECT revoked_at FROM auth_sessions WHERE session_id=?", java.sql.Timestamp.class, one.sessionId()));
                assertEquals(2, users.findByUsername("session-admin").orElseThrow().authVersion());
            }
        }
    }

    @Test void sessionLimitOwnershipAndRestartPersistenceRemainEnforced() throws Exception {
        try (var db = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            Flyway.configure().dataSource(db.source()).locations("classpath:db/migration").load().migrate();
            String token; String sessionId;
            try (var context = context(db.source())) {
                var users = context.getBean(AuthUserRepository.class); seed(users);
                var auth = context.getBean(AuthService.class);
                // 带空格身份同样必须取得用户锁；并发登录不能越过十条会话上限。
                try (var pool = Executors.newFixedThreadPool(4)) {
                    var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                    for (int i = 0; i < 12; i++) tasks.add(pool.submit(() -> auth.login(new LoginRequest(" session-admin ", "SyntheticSession123"))));
                    for (var task : tasks) task.get();
                }
                assertEquals(10, new JdbcTemplate(db.source()).queryForObject("SELECT count(*) FROM auth_sessions", Integer.class));
                var login = auth.login(new LoginRequest("session-admin", "SyntheticSession123"));
                token = login.accessToken(); sessionId = login.sessionId();
                var jdbc = new JdbcTemplate(db.source());
                assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Integer.class));
                assertFalse(users.isSessionActive(sessionId, "another-user", 1, Instant.now(), Instant.now().minusSeconds(1800)));
                assertTrue(users.renewSession(sessionId, -1, 1, Instant.now(), Instant.now().minusSeconds(1800)).isEmpty());
            }
            try (var context = context(db.source())) {
                var claims = context.getBean(JwtTokenService.class).parse(token).orElseThrow();
                assertEquals(sessionId, context.getBean(AuthService.class).renew(claims).sessionId());
                var jdbc = new JdbcTemplate(db.source());
                jdbc.update("UPDATE users SET enabled=FALSE WHERE username='session-admin'");
                assertThrows(BadCredentialsException.class, () -> context.getBean(AuthService.class).renew(claims));
            }
        }
    }
}

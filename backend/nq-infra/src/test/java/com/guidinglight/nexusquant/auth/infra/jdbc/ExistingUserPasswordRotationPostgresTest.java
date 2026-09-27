package com.guidinglight.nexusquant.auth.infra.jdbc;

import com.guidinglight.nexusquant.auth.application.service.ExistingUserPasswordRotationService;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationException;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationTarget;
import com.guidinglight.nexusquant.auth.domain.port.ExistingUserPasswordRotationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 在显式一次性 PostgreSQL 的独立 schema 中验证窄更新、关联不变与并发 CAS。 */
class ExistingUserPasswordRotationPostgresTest {
    private static final String OLD_PASSWORD = "synthetic-original-owner-password";
    private static final String NEW_PASSWORD = "synthetic-replacement-owner-password";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String schema;
    private String oldHash;
    private JdbcExistingUserPasswordRotationRepository repository;
    private ExistingUserPasswordRotationService service;

    @BeforeEach
    void setUp() {
        String url = System.getProperty("nq.postgres.smoke.url");
        boolean required = Boolean.getBoolean("nq.postgres.smoke.required");
        if (!required) {
            assumeTrue(url != null, "Requires explicit disposable PostgreSQL source");
        }
        assertTrue(url != null && url.startsWith("jdbc:postgresql://127.0.0.1:") && !url.contains("?"));
        String user = System.getProperty("nq.postgres.smoke.user");
        String password = System.getProperty("nq.postgres.smoke.password");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "auth_rotation_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        DriverManagerDataSource source = new DriverManagerDataSource(url + "?currentSchema=" + schema, user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE users(id bigint PRIMARY KEY, username varchar(64) UNIQUE NOT NULL,
                  enabled boolean NOT NULL, password_hash varchar(255) NOT NULL,
                  created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL);
                CREATE TABLE roles(id bigint PRIMARY KEY, role_code varchar(64) NOT NULL);
                CREATE TABLE user_roles(user_id bigint REFERENCES users(id), role_id bigint REFERENCES roles(id),
                  PRIMARY KEY(user_id, role_id));
                CREATE TABLE exchange_accounts(exchange_account_id bigint PRIMARY KEY,
                  owner_user_id bigint REFERENCES users(id));
                CREATE TABLE exchange_account_credentials(credential_id bigint PRIMARY KEY,
                  exchange_account_id bigint REFERENCES exchange_accounts(exchange_account_id));
                INSERT INTO roles VALUES(1,'ADMIN'),(2,'OPERATOR'),(3,'VIEWER');
                """);
        oldHash = encoder.encode(OLD_PASSWORD);
        jdbc.update("INSERT INTO users VALUES(2,'synthetic-owner',true,?,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')", oldHash);
        jdbc.update("INSERT INTO users VALUES(3,'other-user',true,?,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')", oldHash);
        jdbc.execute("""
                INSERT INTO user_roles VALUES(2,1),(2,2),(2,3);
                INSERT INTO exchange_accounts VALUES(1,2);
                INSERT INTO exchange_account_credentials VALUES(2,1);
                """);
        repository = new JdbcExistingUserPasswordRotationRepository(source);
        service = new ExistingUserPasswordRotationService(repository, encoder, Clock.systemUTC());
    }

    @AfterEach
    void cleanUp() {
        if (admin != null && schema != null) {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void onlyPasswordAndUpdatedAtChangeWhileIdentityRolesAndOwnershipRemainExact() {
        String before = unchangedFacts();
        Timestamp beforeTime = jdbc.queryForObject("SELECT updated_at FROM users WHERE id=2", Timestamp.class);
        service.rotate(2, "synthetic-owner", fingerprint(), NEW_PASSWORD.toCharArray());
        String newHash = repository.findTarget(2).orElseThrow().passwordHash();
        assertNotEquals(oldHash, newHash);
        assertTrue(encoder.matches(NEW_PASSWORD, newHash));
        assertFalse(encoder.matches(OLD_PASSWORD, newHash));
        assertEquals(before, unchangedFacts());
        assertNotEquals(beforeTime, jdbc.queryForObject("SELECT updated_at FROM users WHERE id=2", Timestamp.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
        assertEquals(oldHash, repository.findTarget(3).orElseThrow().passwordHash());
    }

    @Test
    void missingMismatchedDisabledAndStaleTargetsRemainUnmodified() {
        String before = unchangedFacts();
        assertThrows(PasswordRotationException.class,
                () -> service.rotate(99, "synthetic-owner", fingerprint(), NEW_PASSWORD.toCharArray()));
        assertThrows(PasswordRotationException.class,
                () -> service.rotate(2, "other-user", fingerprint(), NEW_PASSWORD.toCharArray()));
        assertThrows(PasswordRotationException.class,
                () -> service.rotate(2, "synthetic-owner", "0".repeat(64), NEW_PASSWORD.toCharArray()));
        assertEquals(before, unchangedFacts());
        jdbc.update("UPDATE users SET enabled=false WHERE id=2");
        String disabled = unchangedFacts();
        assertThrows(PasswordRotationException.class,
                () -> service.rotate(2, "synthetic-owner", fingerprint(), NEW_PASSWORD.toCharArray()));
        assertEquals(disabled, unchangedFacts());
        assertEquals(oldHash, repository.findTarget(2).orElseThrow().passwordHash());
        assertThrows(PasswordRotationException.class, () -> repository.updatePasswordHash(
                2, "synthetic-owner", oldHash, encoder.encode(NEW_PASSWORD), Instant.now()));
        assertFalse(repository.findTarget(2).orElseThrow().enabled());
    }

    @Test
    void concurrentRotationsCannotOverwriteTheWinnerAndReplayFailsClosed() throws Exception {
        String before = unchangedFacts();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExistingUserPasswordRotationRepository racing = new ExistingUserPasswordRotationRepository() {
            @Override
            public Optional<PasswordRotationTarget> findTarget(long id) {
                Optional<PasswordRotationTarget> target = repository.findTarget(id);
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Synthetic concurrency fixture failed", exception);
                }
                return target;
            }

            @Override
            public void updatePasswordHash(long id, String username, String old, String replacement, Instant time) {
                repository.updatePasswordHash(id, username, old, replacement, time);
            }
        };
        ExistingUserPasswordRotationService concurrent = new ExistingUserPasswordRotationService(
                racing, encoder, Clock.systemUTC());
        Callable<String> rotate = () -> {
            try {
                concurrent.rotate(2, "synthetic-owner", fingerprint(), NEW_PASSWORD.toCharArray());
                return "ROTATED";
            } catch (PasswordRotationException exception) {
                return exception.reason().name();
            }
        };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(rotate);
            var second = executor.submit(rotate);
            List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("ROTATED"::equals).count());
            assertEquals(1, outcomes.stream().filter("STALE_AUTH_IDENTITY"::equals).count());
        }
        assertTrue(encoder.matches(NEW_PASSWORD, repository.findTarget(2).orElseThrow().passwordHash()));
        assertEquals(before, unchangedFacts());
        assertEquals(PasswordRotationException.Reason.STALE_AUTH_IDENTITY,
                assertThrows(PasswordRotationException.class, () -> service.rotate(
                        2, "synthetic-owner", fingerprint(), NEW_PASSWORD.toCharArray())).reason());
    }

    private String fingerprint() {
        return ExistingUserPasswordRotationService.fingerprint(oldHash);
    }

    private String unchangedFacts() {
        return jdbc.queryForObject("""
                SELECT jsonb_build_object(
                  'users',(SELECT jsonb_agg(to_jsonb(u)-'password_hash'-'updated_at' ORDER BY id) FROM users u),
                  'roles',(SELECT jsonb_agg(to_jsonb(r) ORDER BY id) FROM roles r),
                  'user_roles',(SELECT jsonb_agg(to_jsonb(r) ORDER BY user_id,role_id) FROM user_roles r),
                  'accounts',(SELECT jsonb_agg(to_jsonb(a) ORDER BY exchange_account_id) FROM exchange_accounts a),
                  'credentials',(SELECT jsonb_agg(to_jsonb(c) ORDER BY credential_id) FROM exchange_account_credentials c)
                )::text
                """, String.class);
    }
}

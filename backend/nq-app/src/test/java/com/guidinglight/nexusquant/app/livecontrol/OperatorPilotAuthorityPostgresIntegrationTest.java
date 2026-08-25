package com.guidinglight.nexusquant.app.livecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.OperatorPilotAuthorityService;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionAuthorityType;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlAuthorization;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcOperatorPilotAuthorityRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V44 disposable PostgreSQL：operator authority、conditional session 与 lease lifecycle。
 */
class OperatorPilotAuthorityPostgresIntegrationTest {

    @Test
    void enforcesOperatorAuthorityIsolationDigestLeaseAndCloseout() {
        String url = System.getProperty("nq.postgres.smoke.url", "").trim();
        String user = System.getProperty("nq.postgres.smoke.user", "").trim();
        String password = System.getProperty("nq.postgres.smoke.password", "").trim();
        boolean required = Boolean.parseBoolean(System.getProperty("nq.postgres.smoke.required", "false"));
        if (!required) {
            assumeTrue(!url.isBlank() && !user.isBlank() && !password.isBlank(),
                    "PostgreSQL V44 integration is disabled");
        }
        assertTrue(!url.isBlank() && !user.isBlank() && !password.isBlank());

        String schema = "gatey44_" + UUID.randomUUID().toString().replace("-", "");
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        Flyway flyway = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false)
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        flyway.migrate();
        flyway.validate();
        try {
            assertEquals("44", flyway.info().current().getVersion().getVersion());
            DriverManagerDataSource dataSource = new DriverManagerDataSource(schemaUrl, user, password);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture fixture = seedOperator(jdbc);
            jdbc.update("""
                    UPDATE exchange_account_credentials
                    SET permission_probe_status='SUCCEEDED',permission_scope='TRADE',withdraw_enabled=FALSE,
                        ip_allowlist_probe_status='PASSED',last_permission_probe_at=?
                    WHERE credential_id=?
                    """, Timestamp.from(Instant.now()), fixture.credentialId());

            JdbcLiveControlRepository sessions = new JdbcLiveControlRepository(jdbc);
            JdbcOperatorPilotAuthorityRepository authorities = new JdbcOperatorPilotAuthorityRepository(jdbc);
            JdbcLiveControlAuthorization authorization = new JdbcLiveControlAuthorization(jdbc);
            OperatorPilotAuthorityService authorityService = new OperatorPilotAuthorityService(
                    authorities, authorization);
            LiveSessionControlService sessionService = new LiveSessionControlService(sessions, authorization);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            OperatorPilotAuthority authority = OperatorPilotAuthority.active(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), fixture.credentialId(),
                    "BTC-USDT", OperatorPilotAuthority.Side.BUY, OperatorPilotAuthority.OrderType.LIMIT,
                    new BigDecimal("10.00000000"), now, now.plusSeconds(120), fixture.ownerId(), now);
            OperatorPilotAuthority stored = transactions.execute(status -> authorityService.materialize(
                    new AuthenticatedLiveControlActor(fixture.ownerId()), authority));
            assertEquals(authority.canonicalDigest(), jdbc.queryForObject("""
                    SELECT gate_y44_operator_pilot_authority_digest(
                        authority_id,owner_user_id,exchange_account_id,credential_reference_id,
                        instrument,side,order_type,max_notional,max_place_count,max_cancel_count,
                        transfer_allowed,withdraw_allowed,valid_from,expires_at,created_by,created_at)
                    FROM operator_pilot_authorities WHERE authority_id=?
                    """, String.class, authority.id()));

            LiveSession session = LiveSession.createOperatorPilot(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), authority.id(),
                    authority.canonicalDigest(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), now, now.plusSeconds(120), fixture.ownerId(), now);
            LiveSessionEvent event = new LiveSessionEvent(
                    UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                    "CREATE", fixture.ownerId(), "request-v44", "trace-v44", "SESSION_CREATED",
                    "idempotency-v44", session.approvalScopeHash(), "{}", now);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    new AuthenticatedLiveControlActor(fixture.ownerId()), session, stored, event));
            LiveSession reloaded = sessions.findSession(session.id()).orElseThrow();
            assertEquals(LiveSessionAuthorityType.OPERATOR_PILOT, reloaded.authorityType());
            assertNull(reloaded.strategyReleaseId());
            assertNull(reloaded.riskLimitSetId());
            assertEquals(authority.id(), reloaded.operatorPilotAuthorityId());

            assertRejectedSessionVariant(jdbc, reloaded, "STRATEGY", null, null,
                    null, null, null, null, null, LiveSession.APPROVAL_SCOPE_SCHEMA);
            assertRejectedSessionVariant(jdbc, reloaded, "STRATEGY", authority.id(), authority.canonicalDigest(),
                    "synthetic-release", "b".repeat(64), 1L, UUID.randomUUID(), "c".repeat(64),
                    LiveSession.APPROVAL_SCOPE_SCHEMA);
            assertRejectedSessionVariant(jdbc, reloaded, "OPERATOR_PILOT", null, null,
                    null, null, null, null, null, LiveSession.OPERATOR_PILOT_APPROVAL_SCOPE_SCHEMA);
            assertRejectedSessionVariant(jdbc, reloaded, "OPERATOR_PILOT", authority.id(),
                    authority.canonicalDigest(), "synthetic-release", "b".repeat(64), 1L,
                    UUID.randomUUID(), "c".repeat(64), LiveSession.OPERATOR_PILOT_APPROVAL_SCOPE_SCHEMA);

            UUID leaseId = UUID.randomUUID();
            jdbc.update("""
                            INSERT INTO pilot_execution_leases(
                                lease_id,live_session_id,operator_pilot_authority_id,binding_id,binding_digest,
                                status,max_notional,valid_from,expires_at,created_by,version,created_at,updated_at)
                            VALUES (?,?,?,?,?,'CREATED',?,?,?,?,1,?,?)
                            """, leaseId, session.id(), authority.id(), UUID.randomUUID(), "d".repeat(64),
                    new BigDecimal("10.00000000"), Timestamp.from(now), Timestamp.from(now.plusSeconds(120)),
                    fixture.ownerId(), Timestamp.from(now), Timestamp.from(now));
            Instant closedAt = now.plusSeconds(1);
            jdbc.update("""
                    UPDATE pilot_execution_leases
                    SET status='FAILED',closed_at=?,version=2,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(closedAt), Timestamp.from(closedAt), leaseId);
            assertEquals("CLOSED", jdbc.queryForObject(
                    "SELECT status FROM operator_pilot_authorities WHERE authority_id=?",
                    String.class, authority.id()));
        } finally {
            flyway.clean();
        }
    }

    private static void assertRejectedSessionVariant(
            JdbcTemplate jdbc,
            LiveSession base,
            String authorityType,
            UUID operatorAuthorityId,
            String operatorAuthorityDigest,
            String strategyReleaseId,
            String releaseDigest,
            Long releaseRevision,
            UUID riskId,
            String riskDigest,
            String scopeSchema
    ) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                        INSERT INTO live_sessions(
                            session_id,owner_id,exchange_account_id,venue,authority_type,
                            operator_pilot_authority_id,operator_pilot_authority_digest,strategy_release_id,
                            release_digest,release_admission_revision,risk_limit_set_id,risk_limit_set_digest,
                            credential_reference,symbol_allowlist,capital_cap,execution_window_start,
                            execution_window_end,state,version,approval_scope_hash,approval_scope_schema_version,
                            next_event_sequence,created_by,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'APPROVAL_PENDING',1,?,?,1,?,?,?)
                        """, UUID.randomUUID(), base.ownerId(), base.exchangeAccountId(), base.venue(), authorityType,
                operatorAuthorityId, operatorAuthorityDigest, strategyReleaseId, releaseDigest,
                releaseRevision, riskId, riskDigest, base.credentialReference(),
                base.symbolAllowlist().toArray(String[]::new), base.capitalCap(),
                Timestamp.from(base.executionWindowStart()), Timestamp.from(base.executionWindowEnd()),
                "a".repeat(64), scopeSchema, base.createdBy(),
                Timestamp.from(base.createdAt()), Timestamp.from(base.updatedAt())));
    }

    private static Fixture seedOperator(JdbcTemplate jdbc) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long owner = jdbc.queryForObject(
                "INSERT INTO users(username,password_hash) VALUES (?, 'fixture') RETURNING id",
                Long.class, "gatey44_" + suffix);
        long role = jdbc.queryForObject("SELECT id FROM roles WHERE role_code='OPERATOR'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", owner, role);
        long account = jdbc.queryForObject("""
                INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,status)
                VALUES (?,'OKX','LIVE',?,'ACTIVE') RETURNING exchange_account_id
                """, Long.class, owner, "gatey44-" + suffix);
        long credential = jdbc.queryForObject("""
                INSERT INTO exchange_account_credentials(
                    exchange_account_id,credential_type,encrypted_payload,key_version,cipher_suite,
                    verification_status,is_active,credential_status)
                VALUES (?,'OKX_API_V5',?,1,'PGP_SYM_AES256','VERIFIED',TRUE,'ACTIVE')
                RETURNING credential_id
                """, Long.class, account, new byte[]{1, 2, 3});
        return new Fixture(owner, account, credential);
    }

    private record Fixture(long ownerId, long accountId, long credentialId) {
    }
}

package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionAuthorityType;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL JDBC adapter；只持久化事实，不决定业务状态迁移。
 */
@Repository
public class JdbcLiveControlRepository implements LiveControlRepository {

    private static final String SESSION_SELECT = """
            SELECT session_id, owner_id, exchange_account_id, venue, authority_type,
                   operator_pilot_authority_id, operator_pilot_authority_digest, strategy_release_id,
                   release_digest, release_admission_revision, risk_limit_set_id,
                   risk_limit_set_digest, credential_reference, symbol_allowlist, capital_cap,
                   execution_window_start, execution_window_end, state, version,
                   approval_scope_hash, next_event_sequence, created_by, created_at, updated_at
            FROM live_sessions
            """;
    private static final String RISK_SELECT = """
            SELECT risk_limit_set_id, version, capital_cap, max_order_notional,
                   max_symbol_position_notional, max_daily_realized_loss, max_daily_total_loss,
                   max_open_orders, max_intraday_orders, symbol_allowlist,
                   max_session_duration_seconds, spread_limit_bps, slippage_limit_bps,
                   max_market_data_age_ms, min_data_coverage_bps, created_by, created_at
            FROM risk_limit_sets
            """;
    private static final String APPROVAL_SELECT = """
            SELECT approval_id, session_id, scope_schema_version, pilot_scope_id,
                   scope_hash, release_digest, risk_limit_set_digest,
                   approver_id, approver_role, decision, reason, approved_at, expires_at
            FROM operator_approvals
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLiveControlRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Instant currentTime() {
        return jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant()
        );
    }

    @Override
    public void createRiskLimitSet(RiskLimitSet value) {
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO risk_limit_sets (
                        risk_limit_set_id, digest_schema_version, version, effective_scope, quote_currency,
                        capital_cap, max_order_notional, max_symbol_position_notional,
                        max_daily_realized_loss, max_daily_total_loss, max_open_orders,
                        max_intraday_orders, symbol_allowlist, order_type_allowlist,
                        max_session_duration_seconds, spread_limit_bps, slippage_limit_bps,
                        max_market_data_age_ms, min_data_coverage_bps, required_data_source,
                        data_quality_action, canonical_digest, created_by, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            int index = 1;
            statement.setObject(index++, value.id());
            statement.setString(index++, RiskLimitSet.DIGEST_SCHEMA);
            statement.setInt(index++, value.version());
            statement.setString(index++, RiskLimitSet.EFFECTIVE_SCOPE);
            statement.setString(index++, RiskLimitSet.QUOTE_CURRENCY);
            statement.setBigDecimal(index++, value.capitalCap());
            statement.setBigDecimal(index++, value.maxOrderNotional());
            statement.setBigDecimal(index++, value.maxSymbolPositionNotional());
            statement.setBigDecimal(index++, value.maxDailyRealizedLoss());
            statement.setBigDecimal(index++, value.maxDailyTotalLoss());
            statement.setInt(index++, value.maxOpenOrders());
            statement.setInt(index++, value.maxIntradayOrders());
            statement.setArray(index++, textArray(connection, value.symbolAllowlist()));
            statement.setArray(index++, textArray(connection, List.of("LIMIT")));
            statement.setInt(index++, value.maxSessionDurationSeconds());
            statement.setBigDecimal(index++, value.spreadLimitBps());
            statement.setBigDecimal(index++, value.slippageLimitBps());
            statement.setInt(index++, value.maxMarketDataAgeMs());
            statement.setInt(index++, value.minDataCoverageBps());
            statement.setString(index++, "OKX_PRIMARY");
            statement.setString(index++, "BLOCK");
            statement.setString(index++, value.canonicalDigest());
            statement.setLong(index++, value.createdBy());
            statement.setTimestamp(index, timestamp(value.createdAt()));
            return statement;
        });
    }

    @Override
    public Optional<RiskLimitSet> findRiskLimitSet(UUID id) {
        return first(jdbcTemplate.query(RISK_SELECT + " WHERE risk_limit_set_id = ?", this::mapRisk, id));
    }

    @Override
    public boolean lockAndValidateSessionReferences(LiveSession value) {
        if (value.authorityType() == LiveSessionAuthorityType.OPERATOR_PILOT) {
            List<Integer> matches = jdbcTemplate.query("""
                              SELECT 1
                              FROM exchange_accounts account
                              JOIN exchange_account_credentials credential
                                ON credential.credential_id = ?
                               AND credential.exchange_account_id = account.exchange_account_id
                              JOIN operator_pilot_authorities authority
                                ON authority.authority_id = ?
                              WHERE account.exchange_account_id = ?
                                AND account.owner_user_id = ?
                                AND account.exchange_code = 'OKX'
                                AND account.trade_env = 'LIVE'
                                AND account.status = 'ACTIVE'
                                AND credential.credential_type = 'OKX_API_V5'
                                AND credential.credential_status = 'ACTIVE'
                                AND credential.verification_status = 'VERIFIED'
                                AND credential.permission_probe_status = 'SUCCEEDED'
                                AND credential.permission_scope = 'TRADE'
                                AND credential.withdraw_enabled = FALSE
                            AND credential.ip_allowlist_probe_status = 'PASSED'
                            AND credential.last_permission_probe_at IS NOT NULL
                            AND credential.last_permission_probe_at <= CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                            AND credential.last_permission_probe_at + INTERVAL '1 minute' >= CURRENT_TIMESTAMP
                            AND credential.revoked_at IS NULL
                                AND authority.owner_user_id = ?
                                AND authority.exchange_account_id = ?
                                AND authority.credential_reference_id = ?
                                AND authority.instrument = ?
                                AND authority.max_notional >= ?
                                AND authority.valid_from <= ?
                                AND authority.expires_at >= ?
                            AND authority.status = 'ACTIVE'
                            AND authority.valid_from <= CURRENT_TIMESTAMP
                            AND authority.expires_at > CURRENT_TIMESTAMP
                                AND authority.canonical_digest = ?
                              FOR UPDATE OF account, credential, authority
                            """, (resultSet, rowNumber) -> resultSet.getInt(1),
                    value.credentialReference(), value.operatorPilotAuthorityId(),
                    value.exchangeAccountId(), value.ownerId(), value.ownerId(),
                    value.exchangeAccountId(), value.credentialReference(),
                    value.symbolAllowlist().getFirst(), value.capitalCap(),
                    timestamp(value.executionWindowStart()), timestamp(value.executionWindowEnd()),
                    value.operatorPilotAuthorityDigest());
            return matches.size() == 1;
        }
        List<Integer> matches = jdbcTemplate.query("""
                        SELECT 1
                        FROM exchange_accounts account
                        JOIN exchange_account_credentials credential
                          ON credential.credential_id = ?
                         AND credential.exchange_account_id = account.exchange_account_id
                        JOIN strategy_release_admission_state admission
                          ON admission.publish_record_id = ?
                        JOIN risk_limit_sets risk
                          ON risk.risk_limit_set_id = ?
                        WHERE account.exchange_account_id = ?
                          AND account.owner_user_id = ?
                          AND account.exchange_code = 'OKX'
                          AND account.trade_env = 'LIVE'
                          AND admission.release_artifact_digest = ?
                          AND admission.admission_revision = ?
                          AND admission.manifest_fingerprint IS NOT NULL
                          AND admission.manifest_schema_version = 'strategy-release-manifest.v1'
                          AND admission.identity_bound_at IS NOT NULL
                          AND risk.canonical_digest = ?
                        FOR UPDATE OF account, credential, admission, risk
                        """, (resultSet, rowNumber) -> resultSet.getInt(1),
                value.credentialReference(), value.strategyReleaseId(), value.riskLimitSetId(),
                value.exchangeAccountId(), value.ownerId(), value.releaseDigest(),
                value.releaseAdmissionRevision(), value.riskLimitSetDigest());
        return matches.size() == 1;
    }

    @Override
    public boolean lockAndValidatePostExecutionReconciliation(LiveSession session, UUID leaseId) {
        List<Integer> matches = jdbcTemplate.query("""
                SELECT 1
                FROM pilot_execution_leases lease
                JOIN pilot_execution_lease_intents link
                  ON link.lease_id=lease.lease_id AND link.action='PLACE'
                JOIN execution_intents intent
                  ON intent.intent_id=link.intent_id AND intent.state='RECONCILED'
                JOIN execution_receipts receipt
                  ON receipt.intent_id=intent.intent_id
                 AND receipt.outcome IN ('QUERY_CONFIRMED','QUERY_NOT_FOUND')
                JOIN orders local_order
                  ON local_order.order_id=intent.local_order_id
                 AND local_order.trade_env='LIVE'
                 AND local_order.status IN ('FILLED','CANCELLED','REJECTED')
                WHERE lease.lease_id=?
                  AND lease.live_session_id=?
                  AND lease.status='CONSUMED'
                FOR SHARE OF lease,intent,local_order
                """, (row, ignored) -> row.getInt(1), leaseId, session.id());
        return matches.size() == 1;
    }

    @Override
    public void createSession(LiveSession value) {
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO live_sessions (
                        session_id, owner_id, exchange_account_id, venue, authority_type,
                        operator_pilot_authority_id, operator_pilot_authority_digest, strategy_release_id,
                        release_digest, release_admission_revision, risk_limit_set_id,
                        risk_limit_set_digest, credential_reference, symbol_allowlist, capital_cap,
                        execution_window_start, execution_window_end, state, version,
                        approval_scope_hash, approval_scope_schema_version, next_event_sequence,
                        created_by, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            int index = 1;
            statement.setObject(index++, value.id());
            statement.setLong(index++, value.ownerId());
            statement.setLong(index++, value.exchangeAccountId());
            statement.setString(index++, value.venue());
            statement.setString(index++, value.authorityType().name());
            statement.setObject(index++, value.operatorPilotAuthorityId());
            statement.setString(index++, value.operatorPilotAuthorityDigest());
            statement.setString(index++, value.strategyReleaseId());
            statement.setString(index++, value.releaseDigest());
            if (value.authorityType() == LiveSessionAuthorityType.STRATEGY) {
                statement.setLong(index++, value.releaseAdmissionRevision());
            } else {
                statement.setNull(index++, java.sql.Types.BIGINT);
            }
            statement.setObject(index++, value.riskLimitSetId());
            statement.setString(index++, value.riskLimitSetDigest());
            statement.setLong(index++, value.credentialReference());
            statement.setArray(index++, textArray(connection, value.symbolAllowlist()));
            statement.setBigDecimal(index++, value.capitalCap());
            statement.setTimestamp(index++, timestamp(value.executionWindowStart()));
            statement.setTimestamp(index++, timestamp(value.executionWindowEnd()));
            statement.setString(index++, value.state().name());
            statement.setLong(index++, value.version());
            statement.setString(index++, value.approvalScopeHash());
            statement.setString(index++, value.approvalScopeSchemaVersion());
            statement.setLong(index++, value.nextEventSequence());
            statement.setLong(index++, value.createdBy());
            statement.setTimestamp(index++, timestamp(value.createdAt()));
            statement.setTimestamp(index, timestamp(value.updatedAt()));
            return statement;
        });
    }

    @Override
    public Optional<LiveSession> findSession(UUID id) {
        return first(jdbcTemplate.query(SESSION_SELECT + " WHERE session_id = ?", this::mapSession, id));
    }

    @Override
    public Optional<LiveSession> lockSession(UUID id) {
        return first(jdbcTemplate.query(
                SESSION_SELECT + " WHERE session_id = ? FOR UPDATE",
                this::mapSession,
                id
        ));
    }

    @Override
    public boolean compareAndSetSession(LiveSession expected, LiveSession updated) {
        int changed = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE live_sessions
                    SET state = ?, version = ?, approval_scope_hash = ?, risk_limit_set_id = ?,
                        risk_limit_set_digest = ?, symbol_allowlist = ?, capital_cap = ?,
                        execution_window_start = ?, execution_window_end = ?, updated_at = ?
                    WHERE session_id = ? AND version = ? AND state = ?
                    """);
            statement.setString(1, updated.state().name());
            statement.setLong(2, updated.version());
            statement.setString(3, updated.approvalScopeHash());
            statement.setObject(4, updated.riskLimitSetId());
            statement.setString(5, updated.riskLimitSetDigest());
            statement.setArray(6, textArray(connection, updated.symbolAllowlist()));
            statement.setBigDecimal(7, updated.capitalCap());
            statement.setTimestamp(8, timestamp(updated.executionWindowStart()));
            statement.setTimestamp(9, timestamp(updated.executionWindowEnd()));
            statement.setTimestamp(10, timestamp(updated.updatedAt()));
            statement.setObject(11, expected.id());
            statement.setLong(12, expected.version());
            statement.setString(13, expected.state().name());
            return statement;
        });
        return changed == 1;
    }

    @Override
    public LiveSessionEvent appendSessionEvent(LiveSessionEvent event) {
        List<Long> sequenceRows = jdbcTemplate.query(
                "SELECT next_event_sequence FROM live_sessions WHERE session_id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getLong(1),
                event.sessionId()
        );
        if (sequenceRows.isEmpty()) {
            throw new LiveControlException("LIVE_SESSION_NOT_FOUND", "live session was not found for event append");
        }
        long sequence = sequenceRows.getFirst();
        int incremented = jdbcTemplate.update("""
                UPDATE live_sessions
                SET next_event_sequence = next_event_sequence + 1
                WHERE session_id = ? AND next_event_sequence = ?
                """, event.sessionId(), sequence);
        if (incremented != 1) {
            throw new LiveControlException("LIVE_SESSION_EVENT_SEQUENCE_CONFLICT", "event sequence changed concurrently");
        }
        LiveSessionEvent sequenced = event.withSequence(sequence);
        jdbcTemplate.update("""
                        INSERT INTO live_session_events (
                            event_id, session_id, sequence_no, from_state, to_state, command, actor_id,
                            request_id, trace_id, reason_code, idempotency_key, command_payload_hash,
                            command_payload_schema_version, metadata, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'live-session-command.v1', CAST(? AS JSONB), ?)
                        """,
                sequenced.id(), sequenced.sessionId(), sequenced.sequence(),
                sequenced.fromState() == null ? null : sequenced.fromState().name(),
                sequenced.toState().name(), sequenced.command(), sequenced.actorId(),
                sequenced.requestId(), sequenced.traceId(), sequenced.reasonCode(),
                sequenced.idempotencyKey(), sequenced.commandPayloadHash(), sequenced.metadataJson(),
                timestamp(sequenced.createdAt())
        );
        return sequenced;
    }

    @Override
    public void appendApproval(OperatorApproval value) {
        jdbcTemplate.update("""
                        INSERT INTO operator_approvals (
                            approval_id, session_id, scope_schema_version, pilot_scope_id,
                            scope_hash, release_digest, risk_limit_set_digest,
                            approver_id, approver_role, decision, reason, approved_at, expires_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                value.id(), value.sessionId(), value.scopeSchemaVersion(), value.pilotScopeId(),
                value.scopeHash(), value.releaseDigest(),
                value.riskLimitSetDigest(), value.approverId(), value.approverRole(),
                value.decision().name(), value.reason(), timestamp(value.approvedAt()), timestamp(value.expiresAt())
        );
    }

    @Override
    public Optional<OperatorApproval> findApproval(UUID id) {
        return first(jdbcTemplate.query(APPROVAL_SELECT + " WHERE approval_id = ?", this::mapApproval, id));
    }

    @Override
    public Optional<OperatorApproval> findValidApproval(LiveSession session, Instant now) {
        return first(jdbcTemplate.query(APPROVAL_SELECT + """
                        WHERE session_id = ?
                          AND scope_schema_version = 'approval-scope.v1'
                          AND pilot_scope_id IS NULL
                          AND scope_hash = ?
                          AND release_digest = ?
                          AND risk_limit_set_digest = ?
                          AND decision = 'APPROVED'
                          AND approved_at <= ?
                          AND expires_at > ?
                        ORDER BY approved_at DESC, approval_id DESC LIMIT 1
                        """, this::mapApproval, session.id(), session.approvalScopeHash(),
                session.releaseDigest(), session.riskLimitSetDigest(), timestamp(now), timestamp(now)));
    }

    private LiveSession mapSession(ResultSet row, int rowNumber) throws SQLException {
        return new LiveSession(
                row.getObject("session_id", UUID.class), row.getLong("owner_id"),
                row.getLong("exchange_account_id"), row.getString("venue"),
                LiveSessionAuthorityType.valueOf(row.getString("authority_type")),
                row.getObject("operator_pilot_authority_id", UUID.class),
                row.getString("operator_pilot_authority_digest"),
                row.getString("strategy_release_id"), row.getString("release_digest"),
                row.getLong("release_admission_revision"), row.getObject("risk_limit_set_id", UUID.class),
                row.getString("risk_limit_set_digest"), row.getLong("credential_reference"),
                textArray(row.getArray("symbol_allowlist")), row.getBigDecimal("capital_cap"),
                instant(row, "execution_window_start"), instant(row, "execution_window_end"),
                LiveSessionState.valueOf(row.getString("state")), row.getLong("version"),
                row.getString("approval_scope_hash"), row.getLong("next_event_sequence"),
                row.getLong("created_by"), instant(row, "created_at"), instant(row, "updated_at")
        );
    }

    private RiskLimitSet mapRisk(ResultSet row, int rowNumber) throws SQLException {
        return new RiskLimitSet(
                row.getObject("risk_limit_set_id", UUID.class), row.getInt("version"),
                row.getBigDecimal("capital_cap"), row.getBigDecimal("max_order_notional"),
                row.getBigDecimal("max_symbol_position_notional"), row.getBigDecimal("max_daily_realized_loss"),
                row.getBigDecimal("max_daily_total_loss"), row.getInt("max_open_orders"),
                row.getInt("max_intraday_orders"), textArray(row.getArray("symbol_allowlist")),
                row.getInt("max_session_duration_seconds"), row.getBigDecimal("spread_limit_bps"),
                row.getBigDecimal("slippage_limit_bps"), row.getInt("max_market_data_age_ms"),
                row.getInt("min_data_coverage_bps"), row.getLong("created_by"), instant(row, "created_at")
        );
    }

    private OperatorApproval mapApproval(ResultSet row, int rowNumber) throws SQLException {
        return new OperatorApproval(
                row.getObject("approval_id", UUID.class), row.getObject("session_id", UUID.class),
                row.getString("scope_schema_version"), row.getObject("pilot_scope_id", UUID.class),
                row.getString("scope_hash"), row.getString("release_digest"),
                row.getString("risk_limit_set_digest"), row.getLong("approver_id"),
                row.getString("approver_role"), OperatorApproval.Decision.valueOf(row.getString("decision")),
                row.getString("reason"), instant(row, "approved_at"), instant(row, "expires_at")
        );
    }

    private static Array textArray(java.sql.Connection connection, List<String> values) throws SQLException {
        return connection.createArrayOf("TEXT", values.toArray(String[]::new));
    }

    private static List<String> textArray(Array value) throws SQLException {
        return List.copyOf(Arrays.asList((String[]) value.getArray()));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet row, String name) throws SQLException {
        Timestamp value = row.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }
}

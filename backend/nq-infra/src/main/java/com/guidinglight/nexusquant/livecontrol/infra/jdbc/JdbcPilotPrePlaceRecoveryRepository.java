package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotPrePlaceRecoveryRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL是replacement最终防线；Java预判只用于稳定错误分类。 */
@Repository
public class JdbcPilotPrePlaceRecoveryRepository implements PilotPrePlaceRecoveryRepository {

    private final JdbcTemplate jdbc;

    public JdbcPilotPrePlaceRecoveryRepository(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional
    public Optional<Authorization> decide(
            long ownerId,
            long exchangeAccountId,
            long credentialReferenceId,
            String instrument,
            BigDecimal maxNotional,
            UUID decisionId,
            String requestId,
            String traceId,
            Instant decidedAt
    ) {
        List<Candidate> candidates = jdbc.query("""
                SELECT lease.lease_id,lease.live_session_id,lease.status,lease.consumed_at,
                       session.owner_id,session.exchange_account_id,session.credential_reference,
                       session.symbol_allowlist,session.capital_cap,
                       (SELECT count(*) FROM pilot_execution_lease_intents link
                         WHERE link.lease_id=lease.lease_id) AS lease_intents,
                       (SELECT count(*) FROM execution_intents intent
                         WHERE intent.session_id=session.session_id) AS intents,
                       (SELECT count(*) FROM execution_intents intent
                         WHERE intent.session_id=session.session_id AND intent.send_started_at IS NOT NULL) AS sends,
                       (SELECT count(*) FROM execution_receipts receipt
                         JOIN execution_intents intent ON intent.intent_id=receipt.intent_id
                         WHERE intent.session_id=session.session_id) AS receipts,
                       (SELECT count(*) FROM orders value JOIN execution_intents intent
                         ON intent.local_order_id=value.order_id
                         WHERE intent.session_id=session.session_id) AS orders_count,
                       (SELECT count(*) FROM trades value JOIN orders local_order ON local_order.order_id=value.order_id
                         JOIN execution_intents intent ON intent.local_order_id=local_order.order_id
                         WHERE intent.session_id=session.session_id) AS trades_count
                FROM pilot_execution_leases lease
                JOIN live_sessions session ON session.session_id=lease.live_session_id
                ORDER BY lease.created_at,lease.lease_id
                FOR UPDATE OF lease,session
                """, (row, ignored) -> new Candidate(
                row.getObject("lease_id", UUID.class), row.getObject("live_session_id", UUID.class),
                row.getString("status"), row.getTimestamp("consumed_at") != null,
                row.getLong("owner_id"), row.getLong("exchange_account_id"),
                row.getLong("credential_reference"),
                java.util.List.of((String[]) row.getArray("symbol_allowlist").getArray()),
                row.getBigDecimal("capital_cap"), row.getLong("lease_intents"),
                row.getLong("intents"), row.getLong("sends"), row.getLong("receipts"),
                row.getLong("orders_count"), row.getLong("trades_count")));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() != 1) {
            throw rejected("REPLACEMENT_FORBIDDEN_STATE_AMBIGUOUS");
        }
        Candidate candidate = candidates.getFirst();
        boolean exactScope = candidate.ownerId() == ownerId
                && candidate.exchangeAccountId() == exchangeAccountId
                && candidate.credentialReferenceId() == credentialReferenceId
                && candidate.symbols().equals(List.of(instrument))
                && candidate.capitalCap().compareTo(maxNotional) == 0;
        boolean terminal = List.of("EXPIRED", "FAILED").contains(candidate.status())
                && !candidate.consumed();
        boolean zero = candidate.leaseIntents() == 0 && candidate.intents() == 0
                && candidate.sends() == 0 && candidate.receipts() == 0
                && candidate.orders() == 0 && candidate.trades() == 0;
        if (!exactScope || !terminal) {
            throw rejected("REPLACEMENT_FORBIDDEN_STATE_AMBIGUOUS");
        }
        if (!zero) {
            throw rejected("REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED");
        }
        jdbc.update("""
                INSERT INTO pilot_pre_place_recovery_decisions(
                    decision_id,predecessor_lease_id,predecessor_session_id,decision,
                    place_intent_count,send_started_count,execution_intent_count,
                    execution_receipt_count,order_count,trade_count,ledger_count,
                    decided_by,request_id,trace_id,decided_at
                ) VALUES (?,?,?,'REPLACEMENT_ALLOWED_ZERO_INTENT',0,0,0,0,0,0,0,?,?,?,?)
                ON CONFLICT (predecessor_lease_id) DO NOTHING
                """, decisionId, candidate.leaseId(), candidate.sessionId(), ownerId,
                requestId, traceId, Timestamp.from(decidedAt));
        return jdbc.queryForObject("""
                SELECT decision_id,predecessor_lease_id,predecessor_session_id
                FROM pilot_pre_place_recovery_decisions WHERE predecessor_lease_id=?
                """, (row, ignored) -> Optional.of(new Authorization(
                row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getObject(3, UUID.class), 1)), candidate.leaseId());
    }

    @Override
    public boolean lockAndValidateSessionRecovery(LiveSession session, UUID decisionId) {
        List<Integer> matches = jdbc.query("""
                SELECT 1
                FROM pilot_pre_place_recovery_decisions decision
                JOIN pilot_execution_leases lease
                  ON lease.lease_id=decision.predecessor_lease_id
                 AND lease.live_session_id=decision.predecessor_session_id
                JOIN operator_pilot_authorities authority
                  ON authority.authority_id=lease.operator_pilot_authority_id
                JOIN exchange_accounts account ON account.exchange_account_id=?
                JOIN exchange_account_credentials credential
                  ON credential.credential_id=?
                 AND credential.exchange_account_id=account.exchange_account_id
                JOIN kill_switch_states kill ON kill.scope='GLOBAL_TRADING'
                WHERE decision.decision_id=?
                  AND decision.predecessor_session_id=?
                  AND decision.decision='REPLACEMENT_ALLOWED_ZERO_INTENT'
                  AND lease.status IN ('EXPIRED','FAILED') AND lease.consumed_at IS NULL
                  AND account.owner_user_id=? AND account.status='ACTIVE'
                  AND account.exchange_code='OKX' AND account.trade_env='LIVE'
                  AND credential.credential_status='ACTIVE' AND credential.is_active=TRUE
                  AND credential.verification_status='VERIFIED' AND credential.revoked_at IS NULL
                  AND authority.owner_user_id=? AND authority.exchange_account_id=?
                  AND authority.credential_reference_id=? AND authority.instrument=?
                  AND authority.canonical_digest=?
                  AND kill.status='ENGAGED'
                FOR UPDATE OF lease,account,credential,kill
                """, (row, ignored) -> row.getInt(1),
                session.exchangeAccountId(), session.credentialReference(),
                decisionId, session.id(), session.ownerId(), session.ownerId(),
                session.exchangeAccountId(), session.credentialReference(),
                session.symbolAllowlist().getFirst(), session.operatorPilotAuthorityDigest());
        return matches.size() == 1;
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "pre-place replacement recovery rejected");
    }

    private record Candidate(
            UUID leaseId, UUID sessionId, String status, boolean consumed,
            long ownerId, long exchangeAccountId, long credentialReferenceId,
            List<String> symbols, BigDecimal capitalCap, long leaseIntents,
            long intents, long sends, long receipts, long orders, long trades
    ) {
    }
}

package com.guidinglight.nexusquant.livecontrol.execution.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentAction;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentStateMachine;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptCanonicalEncoder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V39 execution runtime 的 JDBC/CAS 实现。每个 public 方法各自使用短事务，绝不调用 exchange。
 */
@Repository
public class JdbcExecutionIntentRepository implements ExecutionIntentRepository {

    private static final String SELECT_COLUMNS = """
            SELECT intent_id,session_id,sequence,action,symbol,side,order_type,quantity,limit_price,
                   payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state,version,
                   claimed_by,claim_token,claimed_at,lease_expires_at,send_started_at,created_at
            FROM execution_intents
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ExecutionIntentStateMachine stateMachine = new ExecutionIntentStateMachine();

    @Autowired
    public JdbcExecutionIntentRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public ExecutionIntent createOrGet(ExecutionIntentDraft draft) {
        requireCanonicalDraft(draft);
        return required(transactions.execute(status -> createOrGetInTransaction(draft)));
    }

    @Override
    public Optional<ExecutionIntent> find(UUID intentId) {
        return findInternal(intentId, false);
    }

    @Override
    public Optional<ExecutionIntent> claim(UUID intentId, String workerId, UUID claimToken, Duration lease) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 128) {
            throw new IllegalArgumentException("workerId is blank or exceeds V39 limit");
        }
        if (claimToken == null || lease == null || lease.isZero() || lease.isNegative()
                || lease.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("claim token and bounded positive lease are required");
        }
        return required(transactions.execute(status -> {
            Optional<ExecutionIntent> locked = findInternal(intentId, true);
            if (locked.isEmpty()) {
                return Optional.empty();
            }
            ExecutionIntent current = locked.get();
            boolean eligible = current.state() == ExecutionIntentState.CREATED
                    || (current.state() == ExecutionIntentState.CLAIMED
                    && current.sendStartedAt() == null
                    && !current.leaseExpiresAt().isAfter(currentDatabaseTime()));
            if (!eligible) {
                return Optional.empty();
            }
            int updated = jdbc.update("""
                    UPDATE execution_intents
                    SET state='CLAIMED',version=version+1,claimed_by=?,claim_token=?,
                        claimed_at=CURRENT_TIMESTAMP,lease_expires_at=CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                    WHERE intent_id=? AND state=? AND version=?
                      AND claim_token IS NOT DISTINCT FROM ? AND send_started_at IS NULL
                    """, workerId, claimToken, lease.toMillis(), intentId, current.state().name(),
                    current.version(), current.claimToken());
            return updated == 1 ? findInternal(intentId, false) : Optional.empty();
        }));
    }

    @Override
    public Optional<ExecutionIntent> markSendStarted(UUID intentId, long expectedVersion, UUID claimToken) {
        return required(transactions.execute(status -> {
            ExecutionIntent current = findForSendWithSessionLock(intentId).orElse(null);
            if (current == null || current.version() != expectedVersion
                    || current.state() != ExecutionIntentState.CLAIMED
                    || !current.claimToken().equals(claimToken)
                    || current.sendStartedAt() != null
                    || !current.leaseExpiresAt().isAfter(currentDatabaseTime())) {
                return Optional.empty();
            }
            if (current.action() == ExecutionIntentAction.PLACE) {
                requirePlaceSafetyGate(current.sessionId());
            }
            int updated = jdbc.update("""
                    UPDATE execution_intents
                    SET state='SEND_STARTED',version=version+1,send_started_at=CURRENT_TIMESTAMP
                    WHERE intent_id=? AND state='CLAIMED' AND version=? AND claim_token=?
                      AND send_started_at IS NULL AND lease_expires_at > CURRENT_TIMESTAMP
                    """, intentId, expectedVersion, claimToken);
            return updated == 1 ? findInternal(intentId, false) : Optional.empty();
        }));
    }

    @Override
    public Optional<ExecutionIntent> markAmbiguousForRecovery(
            UUID intentId,
            long expectedVersion,
            UUID claimToken
    ) {
        return required(transactions.execute(status -> {
            int updated = jdbc.update("""
                    UPDATE execution_intents SET state='UNKNOWN',version=version+1
                    WHERE intent_id=? AND state='SEND_STARTED' AND version=? AND claim_token=?
                      AND send_started_at IS NOT NULL
                    """, intentId, expectedVersion, claimToken);
            return updated == 1 ? findInternal(intentId, false) : Optional.empty();
        }));
    }

    @Override
    public ExecutionIntent appendReceiptAndTransition(
            UUID intentId,
            long expectedVersion,
            UUID claimToken,
            ExecutionReceiptDraft receipt,
            ExecutionIntentState target
    ) {
        ExecutionReceiptDraft canonicalReceipt = ExecutionReceiptCanonicalEncoder.draft(
                receipt.receiptId(), receipt.intentId(), receipt.outcome(), receipt.exchangeRequestId(),
                receipt.exchangeOrderId(), receipt.errorCategory(), receipt.errorCode(), receipt.receivedAt());
        if (!canonicalReceipt.equals(receipt) || !intentId.equals(receipt.intentId())) {
            throw new LiveControlException(
                    "EXECUTION_RECEIPT_DIGEST_INVALID", "receipt is not bound to its canonical envelope");
        }
        return required(transactions.execute(status -> {
            ExecutionIntent current = findInternal(intentId, true)
                    .orElseThrow(() -> new LiveControlException(
                            "EXECUTION_INTENT_NOT_FOUND", "intent was not found"));
            if (current.version() != expectedVersion || !current.claimToken().equals(claimToken)) {
                throw new LiveControlException("EXECUTION_INTENT_CAS_CONFLICT", "intent version or claim changed");
            }
            stateMachine.transition(current.state(), target);
            Integer attemptNo = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(attempt_no),0)+1 FROM execution_receipts WHERE intent_id=?",
                    Integer.class, intentId);
            jdbc.update("""
                    INSERT INTO execution_receipts(
                        receipt_id,intent_id,attempt_no,outcome,exchange_request_id,exchange_order_id,
                        error_category,error_code,received_at,payload_digest,payload_digest_schema_version
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, receipt.receiptId(), intentId, attemptNo, receipt.outcome().name(),
                    receipt.exchangeRequestId(), receipt.exchangeOrderId(), receipt.errorCategory(),
                    receipt.errorCode(), Timestamp.from(receipt.receivedAt()), receipt.payloadDigest(),
                    ExecutionReceiptDraft.DIGEST_SCHEMA);
            int updated = jdbc.update("""
                    UPDATE execution_intents SET state=?,version=version+1
                    WHERE intent_id=? AND state=? AND version=? AND claim_token=?
                    """, target.name(), intentId, current.state().name(), expectedVersion, claimToken);
            if (updated != 1) {
                throw new LiveControlException("EXECUTION_INTENT_CAS_CONFLICT", "intent changed during receipt append");
            }
            return findInternal(intentId, false).orElseThrow();
        }));
    }

    private ExecutionIntent createOrGetInTransaction(ExecutionIntentDraft draft) {
        // intentId 是跨 session 全局幂等键；transaction advisory lock 避免不同 session 并发插入竞态。
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text),0))",
                Object.class,
                draft.intentId().toString()
        );
        Optional<ExecutionIntent> existing = findInternal(draft.intentId(), false);
        if (existing.isPresent()) {
            return requireSamePayload(existing.get(), draft);
        }

        SessionOrderFacts facts = lockAndValidateSessionOrder(draft);
        existing = findInternal(draft.intentId(), false);
        if (existing.isPresent()) {
            return requireSamePayload(existing.get(), draft);
        }
        if (draft.action() == ExecutionIntentAction.CANCEL) {
            List<OriginalPlaceFacts> places = jdbc.query("""
                    SELECT state,
                           (SELECT outcome FROM execution_receipts er
                            WHERE er.intent_id=ei.intent_id ORDER BY attempt_no DESC LIMIT 1) AS latest_outcome
                    FROM execution_intents ei
                    WHERE session_id=? AND local_order_id=? AND action='PLACE' AND client_order_id=?
                    FOR UPDATE
                    """, (row, ignored) -> new OriginalPlaceFacts(
                    ExecutionIntentState.valueOf(row.getString("state")), row.getString("latest_outcome")),
                    draft.sessionId(), draft.localOrderId(), draft.clientOrderId());
            if (places.size() != 1) {
                throw new LiveControlException(
                        "CANCEL_PLACE_IDENTITY_UNVERIFIED",
                        "CANCEL must bind the original PLACE clientOrderId"
                );
            }
            OriginalPlaceFacts place = places.getFirst();
            boolean confirmed = place.state() == ExecutionIntentState.SEND_SUCCEEDED
                    || (place.state() == ExecutionIntentState.RECONCILED
                    && "QUERY_CONFIRMED".equals(place.latestOutcome()));
            if (!confirmed) {
                throw new LiveControlException(
                        "CANCEL_PLACE_RECONCILIATION_REQUIRED",
                        "CANCEL requires a confirmed original PLACE identity"
                );
            }
        }
        Long sequence = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence),0)+1 FROM execution_intents WHERE session_id=?",
                Long.class, draft.sessionId());
        jdbc.update("""
                INSERT INTO execution_intents(
                    intent_id,session_id,sequence,action,symbol,side,order_type,quantity,limit_price,
                    payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'CREATED')
                """, draft.intentId(), draft.sessionId(), sequence, draft.action().name(), draft.symbol(),
                draft.side(), draft.orderType(), draft.quantity(), draft.limitPrice(),
                ExecutionIntentDraft.PAYLOAD_SCHEMA, draft.payloadHash(), draft.clientOrderId(), draft.localOrderId());
        ExecutionIntent created = findInternal(draft.intentId(), false).orElseThrow();
        if (!facts.symbol().equals(created.symbol())) {
            throw new IllegalStateException("validated order symbol changed in transaction");
        }
        return created;
    }

    private static void requireCanonicalDraft(ExecutionIntentDraft draft) {
        ExecutionIntentDraft canonical = draft.action() == ExecutionIntentAction.PLACE
                ? ExecutionIntentCanonicalEncoder.place(
                draft.intentId(), draft.sessionId(), draft.symbol(), draft.side(),
                draft.quantity(), draft.limitPrice(), draft.localOrderId())
                : ExecutionIntentCanonicalEncoder.cancel(
                draft.intentId(), draft.sessionId(), draft.symbol(), draft.localOrderId(), draft.clientOrderId());
        if (!canonical.equals(draft)) {
            throw new LiveControlException(
                    "INTENT_CANONICAL_PAYLOAD_INVALID", "intent is not bound to its canonical payload");
        }
    }

    private SessionOrderFacts lockAndValidateSessionOrder(ExecutionIntentDraft draft) {
        List<SessionOrderFacts> values = jdbc.query("""
                SELECT ls.state,ls.venue,ls.symbol_allowlist,ls.exchange_account_id,ls.created_by,
                       ea.owner_user_id,ea.legacy_account_id,
                       o.account_id,o.exchange_code,o.trade_env,o.symbol,o.client_order_id,o.side,o.type,o.qty,o.price
                FROM live_sessions ls
                JOIN exchange_accounts ea ON ea.exchange_account_id=ls.exchange_account_id
                JOIN orders o ON o.order_id=?
                WHERE ls.session_id=?
                FOR UPDATE OF ls,o
                """, (row, ignored) -> new SessionOrderFacts(
                row.getString("state"), row.getString("venue"),
                java.util.List.of((String[]) row.getArray("symbol_allowlist").getArray()),
                row.getLong("exchange_account_id"),
                row.getLong("created_by"), row.getLong("owner_user_id"),
                nullableLong(row, "legacy_account_id"), row.getLong("account_id"),
                row.getString("exchange_code"), row.getString("trade_env"), row.getString("symbol"),
                row.getString("client_order_id"), row.getString("side"), row.getString("type"),
                row.getBigDecimal("qty"), row.getBigDecimal("price")),
                draft.localOrderId(), draft.sessionId());
        if (values.size() != 1) {
            throw new LiveControlException(
                    "ACCOUNT_IDENTITY_BRIDGE_UNVERIFIED", "session/order account identity cannot be proven");
        }
        SessionOrderFacts value = values.getFirst();
        if (value.legacyAccountId() == null || value.legacyAccountId() != value.orderAccountId()
                || value.accountOwnerId() != value.sessionCreatorId()) {
            throw new LiveControlException(
                    "ACCOUNT_IDENTITY_BRIDGE_UNVERIFIED",
                    "exchange account legacy identity does not equal orders.account_id"
            );
        }
        if (draft.action() == ExecutionIntentAction.PLACE && !"LIVE_ACTIVE".equals(value.sessionState())) {
            throw new LiveControlException("LIVE_SESSION_NOT_ACTIVE", "new PLACE requires LIVE_ACTIVE session state");
        }
        if (draft.action() == ExecutionIntentAction.PLACE) {
            requirePlaceSafetyGate(draft.sessionId());
        }
        if (!"OKX_SPOT".equals(value.sessionVenue()) || !"OKX".equals(value.orderExchangeCode())
                || !"LIVE".equals(value.orderTradeEnv()) || !value.symbolAllowlist().contains(draft.symbol())
                || !value.symbol().equals(draft.symbol())
                || !value.orderClientOrderId().equals(draft.clientOrderId())) {
            throw new LiveControlException(
                    "ORDER_INTENT_IDENTITY_MISMATCH",
                    "order venue/environment/symbol/clientOrderId does not match the session intent"
            );
        }
        if (draft.action() == ExecutionIntentAction.PLACE
                && (!value.side().equals(draft.side()) || !value.type().equals(draft.orderType())
                || value.quantity().compareTo(draft.quantity()) != 0
                || value.price() == null || value.price().compareTo(draft.limitPrice()) != 0)) {
            throw new LiveControlException(
                    "ORDER_INTENT_IDENTITY_MISMATCH",
                    "PLACE intent does not match the existing order fact"
            );
        }
        return value;
    }

    private Optional<ExecutionIntent> findForSendWithSessionLock(UUID intentId) {
        List<UUID> sessions = jdbc.query(
                "SELECT session_id FROM execution_intents WHERE intent_id=?",
                (row, ignored) -> row.getObject("session_id", UUID.class), intentId);
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        jdbc.queryForObject("SELECT state FROM live_sessions WHERE session_id=? FOR UPDATE",
                String.class, sessions.getFirst());
        return findInternal(intentId, true);
    }

    private void requirePlaceSafetyGate(UUID sessionId) {
        String sessionState = jdbc.queryForObject(
                "SELECT state FROM live_sessions WHERE session_id=?", String.class, sessionId);
        List<String> killStates = jdbc.query(
                "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING' FOR SHARE",
                (row, ignored) -> row.getString("status"));
        if (!"LIVE_ACTIVE".equals(sessionState)) {
            throw new LiveControlException("LIVE_SESSION_NOT_ACTIVE", "new PLACE requires LIVE_ACTIVE session state");
        }
        if (killStates.size() != 1 || !"DISENGAGED".equals(killStates.getFirst())) {
            throw new LiveControlException(
                    "GLOBAL_KILL_SWITCH_NOT_DISENGAGED", "new PLACE is blocked by the global kill switch");
        }
    }

    private ExecutionIntent requireSamePayload(ExecutionIntent existing, ExecutionIntentDraft draft) {
        boolean sameCanonicalPayload = ExecutionIntentDraft.PAYLOAD_SCHEMA.equals(
                existing.payloadHashSchemaVersion())
                && existing.samePayload(draft.payloadHash())
                && existing.sessionId().equals(draft.sessionId())
                && existing.action() == draft.action()
                && existing.symbol().equals(draft.symbol())
                && java.util.Objects.equals(existing.side(), draft.side())
                && java.util.Objects.equals(existing.orderType(), draft.orderType())
                && decimalEquals(existing.quantity(), draft.quantity())
                && decimalEquals(existing.limitPrice(), draft.limitPrice())
                && existing.clientOrderId().equals(draft.clientOrderId())
                && existing.localOrderId().equals(draft.localOrderId());
        if (!sameCanonicalPayload) {
            throw new LiveControlException(
                    "IDEMPOTENCY_CONFLICT",
                    "intent id already exists with a different canonical payload"
            );
        }
        return existing;
    }

    private static boolean decimalEquals(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private Optional<ExecutionIntent> findInternal(UUID intentId, boolean lock) {
        List<ExecutionIntent> values = jdbc.query(
                SELECT_COLUMNS + " WHERE intent_id=?" + (lock ? " FOR UPDATE" : ""),
                JdbcExecutionIntentRepository::mapIntent, intentId);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private Instant currentDatabaseTime() {
        return java.util.Objects.requireNonNull(
                jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class)).toInstant();
    }

    private static ExecutionIntent mapIntent(ResultSet row, int ignored) throws SQLException {
        return new ExecutionIntent(
                row.getObject("intent_id", UUID.class), row.getObject("session_id", UUID.class),
                row.getLong("sequence"), ExecutionIntentAction.valueOf(row.getString("action")),
                row.getString("symbol"), row.getString("side"), row.getString("order_type"),
                row.getBigDecimal("quantity"), row.getBigDecimal("limit_price"),
                row.getString("payload_hash_schema_version"), row.getString("payload_hash"),
                row.getString("client_order_id"), row.getString("local_order_id"),
                ExecutionIntentState.valueOf(row.getString("state")), row.getLong("version"),
                row.getString("claimed_by"), row.getObject("claim_token", UUID.class),
                instant(row, "claimed_at"), instant(row, "lease_expires_at"),
                instant(row, "send_started_at"), row.getTimestamp("created_at").toInstant());
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static <T> T required(T value) {
        return java.util.Objects.requireNonNull(value, "transaction returned null");
    }

    private record SessionOrderFacts(
            String sessionState,
            String sessionVenue,
            java.util.List<String> symbolAllowlist,
            long exchangeAccountId,
            long sessionCreatorId,
            long accountOwnerId,
            Long legacyAccountId,
            long orderAccountId,
            String orderExchangeCode,
            String orderTradeEnv,
            String symbol,
            String orderClientOrderId,
            String side,
            String type,
            java.math.BigDecimal quantity,
            java.math.BigDecimal price
    ) {
    }

    private record OriginalPlaceFacts(ExecutionIntentState state, String latestOutcome) {
    }
}

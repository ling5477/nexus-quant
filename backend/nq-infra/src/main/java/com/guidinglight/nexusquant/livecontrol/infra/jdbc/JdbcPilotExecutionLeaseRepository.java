package com.guidinglight.nexusquant.livecontrol.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.PilotExecutionLease;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL lease lifecycle；所有状态变化先锁 lease，provider 调用永远不在本类发生。
 */
@Repository
public class JdbcPilotExecutionLeaseRepository implements PilotExecutionLeaseRepository {

    private static final String COLUMNS = """
            SELECT lease_id,live_session_id,operator_pilot_authority_id,binding_id,binding_digest,status,max_notional,
                   valid_from,expires_at,consumed_at,closed_at,created_by,version,created_at,updated_at
            FROM pilot_execution_leases
            """;

    private final JdbcTemplate jdbc;

    public JdbcPilotExecutionLeaseRepository(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    @Override
    @Transactional
    public PilotExecutionLease create(PilotExecutionLease lease, String requestId, String traceId) {
        requireText(requestId, "requestId");
        requireText(traceId, "traceId");
        Optional<PilotExecutionLease> existing = findByBinding(lease.bindingId(), true);
        if (existing.isPresent()) {
            if (existing.get().equals(lease)) return existing.get();
            throw rejected("PILOT_LEASE_IDEMPOTENCY_CONFLICT");
        }
        jdbc.update("""
                        INSERT INTO pilot_execution_leases(
                            lease_id,live_session_id,operator_pilot_authority_id,binding_id,binding_digest,status,max_notional,
                            valid_from,expires_at,created_by,version,created_at,updated_at
                        ) VALUES (?,?,?,?,?,?,?,?,?,?,1,?,?)
                        """, lease.id(), lease.liveSessionId(), lease.operatorPilotAuthorityId(),
                lease.bindingId(), lease.bindingDigest(),
                lease.status().name(), lease.maxNotional(), Timestamp.from(lease.validFrom()),
                Timestamp.from(lease.expiresAt()), lease.createdBy(), Timestamp.from(lease.createdAt()),
                Timestamp.from(lease.updatedAt()));
        appendEvent(lease.id(), null, lease.status(), 1, "PILOT_LEASE_CREATED",
                requestId, traceId, lease.createdAt());
        return findLocked(lease.id()).orElseThrow();
    }

    @Override
    public Optional<PilotExecutionLease> find(UUID leaseId) {
        return first(jdbc.query(COLUMNS + " WHERE lease_id=?", JdbcPilotExecutionLeaseRepository::map, leaseId));
    }

    @Override
    @Transactional
    public PilotExecutionLease activate(UUID leaseId, long expectedVersion, Instant occurredAt,
                                        String requestId, String traceId) {
        PilotExecutionLease lease = findLocked(leaseId).orElseThrow(() -> rejected("PILOT_LEASE_NOT_FOUND"));
        if (lease.status() != PilotExecutionLease.Status.CREATED || lease.version() != expectedVersion
                || occurredAt.isBefore(lease.validFrom()) || !occurredAt.isBefore(lease.expiresAt())) {
            throw rejected("PILOT_LEASE_ACTIVATION_REJECTED");
        }
        updateStatus(lease, PilotExecutionLease.Status.ACTIVE, occurredAt, null, null);
        appendEvent(lease.id(), lease.status(), PilotExecutionLease.Status.ACTIVE, lease.version() + 1,
                "PILOT_LEASE_ACTIVATED", requestId, traceId, occurredAt);
        return findLocked(leaseId).orElseThrow();
    }

    @Override
    @Transactional
    public PilotExecutionLease bindPlaceAndConsume(
            UUID leaseId,
            UUID intentId,
            ExactPilotBinding binding,
            Instant occurredAt,
            String requestId,
            String traceId
    ) {
        PilotExecutionLease lease = findLocked(leaseId).orElseThrow(() -> rejected("PILOT_LEASE_NOT_FOUND"));
        if (!lease.activeAt(occurredAt) || !lease.liveSessionId().equals(binding.sessionId())
                || !java.util.Objects.equals(lease.operatorPilotAuthorityId(),
                binding.operatorPilotAuthority() == null
                        ? null : binding.operatorPilotAuthority().authorityId())
                || !lease.bindingId().equals(binding.id()) || !lease.bindingDigest().equals(binding.bindingDigest())
                || !binding.hasCanonicalDigest() || binding.order().notional().compareTo(lease.maxNotional()) > 0) {
            throw rejected("PILOT_LEASE_SCOPE_MISMATCH");
        }
        List<Integer> exact = jdbc.query("""
                        SELECT 1 FROM execution_intents
                        WHERE intent_id=? AND session_id=? AND action='PLACE' AND symbol=? AND side=?
                          AND order_type='LIMIT' AND quantity=? AND limit_price=?
                        FOR UPDATE
                        """, (row, ignored) -> row.getInt(1), intentId, binding.sessionId(),
                binding.order().exchangeInstrumentId(), binding.order().side().name(),
                binding.order().quantity(), binding.order().price());
        if (exact.size() != 1) throw rejected("PILOT_LEASE_INTENT_MISMATCH");
        try {
            jdbc.update("INSERT INTO pilot_execution_lease_intents(lease_id,intent_id,action,created_at) "
                    + "VALUES (?,?,'PLACE',?)", leaseId, intentId, Timestamp.from(occurredAt));
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            throw rejected("PILOT_LEASE_PLACE_ALREADY_BOUND");
        }
        updateStatus(lease, PilotExecutionLease.Status.CONSUMED, occurredAt, occurredAt, null);
        appendEvent(lease.id(), lease.status(), PilotExecutionLease.Status.CONSUMED, lease.version() + 1,
                "PILOT_LEASE_PLACE_BOUND", requestId, traceId, occurredAt);
        return findLocked(leaseId).orElseThrow();
    }

    @Override
    @Transactional
    public void bindCancel(UUID leaseId, UUID intentId, Instant occurredAt) {
        PilotExecutionLease lease = findLocked(leaseId).orElseThrow(() -> rejected("PILOT_LEASE_NOT_FOUND"));
        if (lease.status() != PilotExecutionLease.Status.CONSUMED || !occurredAt.isBefore(lease.expiresAt())) {
            throw rejected("PILOT_LEASE_CANCEL_REJECTED");
        }
        List<Integer> exact = jdbc.query("""
                SELECT 1 FROM execution_intents
                WHERE intent_id=? AND session_id=? AND action='CANCEL'
                FOR UPDATE
                """, (row, ignored) -> row.getInt(1), intentId, lease.liveSessionId());
        if (exact.size() != 1) throw rejected("PILOT_LEASE_INTENT_MISMATCH");
        try {
            jdbc.update("INSERT INTO pilot_execution_lease_intents(lease_id,intent_id,action,created_at) "
                    + "VALUES (?,?,'CANCEL',?)", leaseId, intentId, Timestamp.from(occurredAt));
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            throw rejected("PILOT_LEASE_CANCEL_ALREADY_BOUND");
        }
    }

    @Override
    @Transactional
    public PilotExecutionLease close(UUID leaseId, PilotExecutionLease.Status terminal, Instant occurredAt,
                                     String reasonCode, String requestId, String traceId) {
        if (terminal != PilotExecutionLease.Status.CLOSED
                && terminal != PilotExecutionLease.Status.EXPIRED
                && terminal != PilotExecutionLease.Status.FAILED) {
            throw new IllegalArgumentException("terminal lease status is required");
        }
        PilotExecutionLease lease = findLocked(leaseId).orElseThrow(() -> rejected("PILOT_LEASE_NOT_FOUND"));
        if (lease.status() == terminal) return lease;
        if (lease.status() != PilotExecutionLease.Status.CREATED
                && lease.status() != PilotExecutionLease.Status.ACTIVE
                && lease.status() != PilotExecutionLease.Status.CONSUMED) {
            throw rejected("PILOT_LEASE_ALREADY_TERMINAL");
        }
        updateStatus(lease, terminal, occurredAt, lease.consumedAt(), occurredAt);
        appendEvent(lease.id(), lease.status(), terminal, lease.version() + 1,
                reasonCode, requestId, traceId, occurredAt);
        return findLocked(leaseId).orElseThrow();
    }

    @Override
    public List<PilotExecutionLease> findRecoverable(Instant decisionAt) {
        return jdbc.query(COLUMNS + " WHERE status IN ('CREATED','ACTIVE','CONSUMED') "
                + "ORDER BY expires_at,lease_id", JdbcPilotExecutionLeaseRepository::map);
    }

    private Optional<PilotExecutionLease> findLocked(UUID leaseId) {
        return first(jdbc.query(COLUMNS + " WHERE lease_id=? FOR UPDATE",
                JdbcPilotExecutionLeaseRepository::map, leaseId));
    }

    private Optional<PilotExecutionLease> findByBinding(UUID bindingId, boolean lock) {
        return first(jdbc.query(COLUMNS + " WHERE binding_id=?" + (lock ? " FOR UPDATE" : ""),
                JdbcPilotExecutionLeaseRepository::map, bindingId));
    }

    private void updateStatus(PilotExecutionLease lease, PilotExecutionLease.Status target, Instant occurredAt,
                              Instant consumedAt, Instant closedAt) {
        int updated = jdbc.update("""
                        UPDATE pilot_execution_leases
                        SET status=?,consumed_at=?,closed_at=?,version=version+1,updated_at=?
                        WHERE lease_id=? AND status=? AND version=?
                        """, target.name(), timestamp(consumedAt), timestamp(closedAt), Timestamp.from(occurredAt),
                lease.id(), lease.status().name(), lease.version());
        if (updated != 1) throw rejected("PILOT_LEASE_VERSION_CONFLICT");
    }

    private void appendEvent(UUID leaseId, PilotExecutionLease.Status from, PilotExecutionLease.Status to,
                             long version, String reason, String requestId, String traceId, Instant occurredAt) {
        requireText(reason, "reasonCode");
        requireText(requestId, "requestId");
        requireText(traceId, "traceId");
        jdbc.update("""
                        INSERT INTO pilot_execution_lease_events(
                            event_id,lease_id,from_status,to_status,lease_version,reason_code,
                            request_id,trace_id,occurred_at
                        ) VALUES (?,?,?,?,?,?,?,?,?)
                        """, UUID.randomUUID(), leaseId, from == null ? null : from.name(), to.name(), version,
                reason, requestId, traceId, Timestamp.from(occurredAt));
    }

    private static PilotExecutionLease map(ResultSet row, int ignored) throws SQLException {
        return new PilotExecutionLease(
                row.getObject("lease_id", UUID.class), row.getObject("live_session_id", UUID.class),
                row.getObject("operator_pilot_authority_id", UUID.class),
                row.getObject("binding_id", UUID.class), row.getString("binding_digest"),
                PilotExecutionLease.Status.valueOf(row.getString("status")), row.getBigDecimal("max_notional"),
                row.getTimestamp("valid_from").toInstant(), row.getTimestamp("expires_at").toInstant(),
                instant(row, "consumed_at"), instant(row, "closed_at"), row.getLong("created_by"),
                row.getLong("version"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private static Instant instant(ResultSet row, String name) throws SQLException {
        Timestamp value = row.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static LiveControlException rejected(String code) {
        return new LiveControlException(code, "pilot execution lease operation rejected");
    }
}

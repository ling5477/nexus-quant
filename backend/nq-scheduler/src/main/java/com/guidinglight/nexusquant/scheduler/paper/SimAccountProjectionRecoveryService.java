package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantKind;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("public-marketdata-manual")
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class SimAccountProjectionRecoveryService {
    private final SimAccountProjectionRepository projections;
    private final ContinuousSimRepository runs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogRepository audit;

    public SimAccountProjectionRecoveryService(SimAccountProjectionRepository projections,
            ContinuousSimRepository runs, JdbcTemplate jdbc, ObjectMapper mapper, AuditLogRepository audit) {
        this.projections = projections;
        this.runs = runs;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Preview preview(String run, String actor) {
        var owner = owner(run, actor);
        var cursor = runs.get(run);
        UUID end = cursor.manualEndAt() == null ? null : originalEnd(owner, cursor);
        var expected = projections.expected(run, owner.accountId(), owner.budget());
        var actual = projections.actual(owner.accountId(), expected.position() != null);
        var values = compare(expected, actual);
        return new Preview(owner.accountId(), owner.tenantId(), end, expected.watermark(), actual.version(), values,
                end == null ? null : latestRecovery(owner, end).map(Receipt::operationId).orElse(null));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public Receipt recover(String run, String actor, String trace, RecoveryRequest request) {
        var owner = owner(run, actor);
        validate(request.operationId(), request.endOperationId(), trace);
        validateHash(request.ledgerWatermark());
        validateHash(request.projectionVersion());
        var hash = SimAccountProjectionRepository.sha256(owner.identity() + "/recover/"
                + request.endOperationId() + "/" + request.ledgerWatermark() + "/" + request.projectionVersion());
        lockOperation(request.operationId());
        var original = replay(owner, request.operationId(), hash);
        if (original.isPresent()) return original.orElseThrow();

        projections.lock(owner.accountId());
        var cursor = runs.lockCursor(run);
        if (!request.endOperationId().equals(originalEnd(owner(run, actor), cursor))) {
            throw new IllegalStateException("SIM_PROJECTION_END_INTENT_CHANGED");
        }
        if (!"BLOCKED".equals(cursor.bounded().lifecycleStatus())) {
            throw new IllegalStateException("SIM_PROJECTION_REQUIRES_TERMINAL_BLOCK");
        }
        var expected = projections.expected(run, owner.accountId(), owner.budget());
        var actual = projections.actual(owner.accountId(), expected.position() != null);
        if (!expected.watermark().sha256().equals(request.ledgerWatermark())
                || !actual.version().equals(request.projectionVersion())) {
            throw new IllegalStateException("SIM_PROJECTION_PREVIEW_EXPIRED");
        }
        var before = compare(expected, actual);
        if (before.cashConsistent() && before.positionConsistent()) {
            throw new IllegalStateException("SIM_PROJECTIONS_ALREADY_CONSISTENT");
        }

        var now = clock();
        projections.restore(owner.accountId(), expected, actual, now, trace);
        var verified = projections.expected(run, owner.accountId(), owner.budget());
        var after = compare(verified, projections.actual(owner.accountId(), verified.position() != null));
        if (!verified.watermark().equals(expected.watermark()) || !after.cashConsistent() || !after.positionConsistent()) {
            throw new IllegalStateException("SIM_PROJECTION_RECOVERY_VERIFICATION_FAILED");
        }
        return complete(owner, hash, trace, new Receipt(request.operationId(), request.endOperationId(), null,
                owner.accountId(), verified.watermark(), before, after, "CONSISTENT", "CONSISTENT", "UNCHANGED",
                null, now));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 20)
    public Receipt recheck(String run, String actor, String trace, RecheckRequest request) {
        var owner = owner(run, actor);
        validate(request.operationId(), request.endOperationId(), trace);
        if (request.recoveryOperationId() == null || request.operationId().equals(request.recoveryOperationId())) {
            throw new IllegalArgumentException("INVALID_SIM_PROJECTION_REQUEST");
        }
        var hash = SimAccountProjectionRepository.sha256(owner.identity() + "/recheck/"
                + request.endOperationId() + "/" + request.recoveryOperationId());
        lockOperation(request.operationId());
        var original = replay(owner, request.operationId(), hash);
        if (original.isPresent()) return original.orElseThrow();

        projections.lock(owner.accountId());
        var cursor = runs.lockCursor(run);
        if (!request.endOperationId().equals(originalEnd(owner(run, actor), cursor))) {
            throw new IllegalStateException("SIM_PROJECTION_END_INTENT_CHANGED");
        }
        var recovery = latestRecovery(owner, request.endOperationId())
                .filter(r -> r.operationId().equals(request.recoveryOperationId()))
                .orElseThrow(() -> new IllegalStateException("SIM_PROJECTION_RECOVERY_REQUIRED"));
        var expected = projections.expected(run, owner.accountId(), owner.budget());
        var values = compare(expected, projections.actual(owner.accountId(), expected.position() != null));
        if (!recovery.ledgerWatermark().equals(expected.watermark()) || !values.cashConsistent()
                || !values.positionConsistent()) {
            throw new IllegalStateException("SIM_PROJECTION_FINALIZATION_UNPROVEN");
        }
        var terminal = runs.finalizeRecovered(cursor, owner.accountId());
        return complete(owner, hash, trace, new Receipt(request.operationId(), request.endOperationId(),
                recovery.operationId(), owner.accountId(), expected.watermark(), null, values,
                "CONSISTENT", "CONSISTENT", "UNCHANGED",
                new Finalization("MANUALLY_STOPPED", "VERIFIED", 0, terminal), clock()));
    }

    @Transactional(readOnly = true, timeout = 10)
    public Optional<Receipt> receipt(String run, String actor, UUID operationId) {
        return find(owner(run, actor), operationId).map(Saved::receipt);
    }

    private Owner owner(String run, String actor) {
        var identity = TenantContext.requireCurrent();
        if (identity.kind() != TenantKind.LOCAL) throw new ScopeNotFound();
        return jdbc.query("""
                SELECT a.account_id,(p.config_snapshot_json->>'budget')::numeric AS budget
                FROM paper_trading_runs p JOIN accounts a ON a.account_id=p.canonical_account_id
                JOIN continuous_sim_runs c ON c.paper_run_id=p.paper_run_id
                WHERE p.paper_run_id=? AND p.created_by=? AND p.tenant_id=? AND a.tenant_id=p.tenant_id
                    AND p.trade_env='SIM' AND p.symbol='BTC-USDT' AND c.end_at IS NOT NULL
                    AND a.venue='PAPER' AND a.account_code='STRATEGY-SIM-'||substring(p.paper_run_id from 5)
                """, (rs, n) -> new Owner(run, rs.getLong(1), identity.tenantId().value(), identity.userId(),
                        actor, rs.getBigDecimal(2)), run, actor, identity.tenantId().value())
                .stream().findFirst().orElseThrow(ScopeNotFound::new);
    }

    private UUID originalEnd(Owner owner, ContinuousSimRepository.Cursor cursor) {
        if (cursor.manualEndAt() == null || "STOPPED".equals(cursor.status())) {
            throw new IllegalStateException("SIM_PROJECTION_REQUIRES_PENDING_MANUAL_END");
        }
        var linked = jdbc.queryForList("""
                SELECT end_operation_id FROM sim_account_projection_operations
                WHERE tenant_id=? AND owner_user_id=? AND account_id=? AND paper_run_id=? AND recovery_operation_id IS NULL
                ORDER BY recorded_at,operation_id LIMIT 1
                """, UUID.class, owner.tenantId(), owner.user(), owner.accountId(), owner.run());
        if (!linked.isEmpty()) return linked.getFirst();
        var ids = jdbc.queryForList("""
                SELECT operation_id FROM continuous_sim_operations
                WHERE paper_run_id=? AND owner_user_id=? AND actor=? AND operation_type='END'
                    AND (result_json#>>'{bounded,manualEndAt}')::timestamptz=?
                LIMIT 2
                """, UUID.class, owner.run(), owner.user(), owner.actor(), Timestamp.from(cursor.manualEndAt()));
        if (ids.size() != 1) throw new IllegalStateException("SIM_PROJECTION_END_INTENT_UNPROVEN");
        return ids.getFirst();
    }

    private void lockOperation(UUID operation) {
        // 同 UUID 的并发重放等待原事务，回滚或提交后再从当前快照读回执。
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,9255))",
                Object.class, "sim-projection-operation:" + operation);
    }

    private Optional<Saved> find(Owner owner, UUID operation) {
        return jdbc.query("""
                SELECT request_sha256,result_json::text FROM sim_account_projection_operations
                WHERE operation_id=? AND tenant_id=? AND owner_user_id=? AND account_id=? AND paper_run_id=?
                """, (rs, n) -> new Saved(rs.getString(1), decode(rs.getString(2))), operation,
                owner.tenantId(), owner.user(), owner.accountId(), owner.run()).stream().findFirst();
    }

    private Optional<Receipt> latestRecovery(Owner owner, UUID end) {
        return jdbc.query("""
                SELECT result_json::text FROM sim_account_projection_operations
                WHERE tenant_id=? AND owner_user_id=? AND account_id=? AND paper_run_id=?
                    AND end_operation_id=? AND recovery_operation_id IS NULL
                ORDER BY recorded_at DESC,operation_id LIMIT 1
                """, (rs, n) -> decode(rs.getString(1)), owner.tenantId(), owner.user(), owner.accountId(),
                owner.run(), end).stream().findFirst();
    }

    private Optional<Receipt> replay(Owner owner, UUID operation, String hash) {
        return find(owner, operation).map(saved -> {
            if (!saved.hash().equals(hash)) throw new IllegalStateException("SIM_PROJECTION_OPERATION_ID_CONFLICT");
            return saved.receipt();
        });
    }

    private Receipt complete(Owner owner, String hash, String trace, Receipt receipt) {
        int written = jdbc.update("""
                INSERT INTO sim_account_projection_operations(operation_id,tenant_id,account_id,paper_run_id,
                    owner_user_id,actor,trace_id,end_operation_id,recovery_operation_id,request_sha256,
                    ledger_watermark,result_json,recorded_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) ON CONFLICT (operation_id) DO NOTHING
                """, receipt.operationId(), owner.tenantId(), owner.accountId(), owner.run(), owner.user(),
                owner.actor(), trace, receipt.endOperationId(), receipt.recoveryOperationId(), hash,
                receipt.ledgerWatermark().sha256(), encode(receipt), Timestamp.from(receipt.recordedAt()));
        if (written != 1) throw new IllegalStateException("SIM_PROJECTION_OPERATION_ID_CONFLICT");
        audit.append("PAPER_TRADING", receipt.finalization() == null
                ? "SIM_ACCOUNT_PROJECTION_RECOVERED" : "SIM_MANUAL_END_RECHECKED", owner.actor(), trace,
                Map.of("operationId", receipt.operationId(), "endOperationId", receipt.endOperationId(),
                        "paperRunId", owner.run(), "tenantId", owner.tenantId(), "accountId", owner.accountId(),
                        "ownerUserId", owner.user(), "ledgerWatermark", receipt.ledgerWatermark().sha256()));
        return receipt;
    }

    private Instant clock() {
        return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
    }

    private Receipt decode(String json) {
        try {
            return mapper.readValue(json, Receipt.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("SIM_PROJECTION_RECEIPT_DECODING_FAILED", failure);
        }
    }

    private String encode(Receipt receipt) {
        try {
            return mapper.writeValueAsString(receipt);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("SIM_PROJECTION_RECEIPT_ENCODING_FAILED", failure);
        }
    }

    private static void validate(UUID operation, UUID end, String trace) {
        if (operation == null || end == null || operation.equals(end)
                || trace == null || trace.isBlank() || trace.length() > 64) {
            throw new IllegalArgumentException("INVALID_SIM_PROJECTION_REQUEST");
        }
    }

    private static void validateHash(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("INVALID_SIM_PROJECTION_REQUEST");
        }
    }

    private static Differences compare(SimAccountProjectionRepository.Expected expected,
            SimAccountProjectionRepository.Actual actual) {
        return new Differences(actual.cash(), expected.cash(), actual.asset(), expected.asset(),
                actual.holding(), expected.holding(), Objects.equals(actual.cash(), expected.cash()),
                Objects.equals(actual.asset(), expected.asset()) && Objects.equals(actual.holding(), expected.holding()));
    }

    public static class ScopeNotFound extends RuntimeException {}
    private record Saved(String hash, Receipt receipt) {}
    private record Owner(String run, long accountId, UUID tenantId, long user, String actor, BigDecimal budget) {
        String identity() { return tenantId + "/" + accountId + "/" + user; }
    }

    public record RecoveryRequest(UUID operationId, UUID endOperationId, String ledgerWatermark, String projectionVersion) {}
    public record RecheckRequest(UUID operationId, UUID endOperationId, UUID recoveryOperationId) {}
    public record Preview(long accountId, UUID tenantId, UUID endOperationId,
            SimAccountProjectionRepository.Watermark ledgerWatermark, String projectionVersion, Differences differences, UUID recoveryOperationId) {}
    public record Differences(SimAccountProjectionRepository.Balance actualCash, SimAccountProjectionRepository.Balance expectedCash,
            SimAccountProjectionRepository.Balance actualAsset, SimAccountProjectionRepository.Balance expectedAsset,
            SimAccountProjectionRepository.Holding actualPosition, SimAccountProjectionRepository.Holding expectedPosition,
            boolean cashConsistent, boolean positionConsistent) {}
    public record Receipt(UUID operationId, UUID endOperationId, UUID recoveryOperationId, long accountId,
            SimAccountProjectionRepository.Watermark ledgerWatermark, Differences before, Differences after,
            String cash, String position, String ledger, Finalization finalization, Instant recordedAt) {}
    public record Finalization(String lifecycleStatus, String finalValuationStatus, int pendingOrders, Instant terminatedAt) {}
}

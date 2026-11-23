package com.guidinglight.nexusquant.scheduler.infra;

import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceOperations;
import com.guidinglight.nexusquant.trading.application.maintenance.TradingMaintenanceService;
import com.guidinglight.nexusquant.trading.domain.TradingVenue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 先独立提交原请求，再调用已有维护服务；进程中断不得再次领取。 原恢复服务可能扫描跨账户事件，因此未确认请求全局互斥，不能用更换交易所或编号绕过。 */
@Service
@ConditionalOnProperty(name = "nq.runtime.trading-components.enabled", havingValue = "true")
public final class JdbcTradingMaintenanceOperations implements TradingMaintenanceOperations {
    private final JdbcTemplate jdbc;
    private final TradingMaintenanceService maintenance;
    private final TransactionTemplate transaction;

    public JdbcTradingMaintenanceOperations(
            JdbcTemplate jdbc,
            TradingMaintenanceService maintenance,
            PlatformTransactionManager manager) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.maintenance = Objects.requireNonNull(maintenance);
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
    }

    @Override
    public Receipt submit(
            UUID id, long owner, Kind kind, String venue, Integer limit, String traceId) {
        if (id == null
                || owner <= 0
                || kind == null
                || traceId == null
                || traceId.isBlank()
                || traceId.length() > 128)
            throw new IllegalArgumentException("MAINTENANCE_IDENTITY_INVALID");
        String normalized = TradingVenue.parse(venue == null ? "OKX" : venue).name();
        if (!normalized.equals("OKX") && !normalized.equals("BINANCE"))
            throw new IllegalArgumentException("MAINTENANCE_VENUE_INVALID");
        Integer bound = kind == Kind.RECONCILE ? (limit == null ? 100 : limit) : null;
        if ((bound != null && (bound < 1 || bound > 1000))
                || (kind == Kind.RECOVERY && limit != null))
            throw new IllegalArgumentException("MAINTENANCE_LIMIT_INVALID");

        // INSERT的行数是唯一领取凭据；并发同编号只能读取，不能再次进入原维护服务。
        boolean claimed;
        try {
            claimed =
                    Boolean.TRUE.equals(
                            transaction.execute(
                                    status -> {
                                        jdbc.queryForObject(
                                                "SELECT set_config('lock_timeout','2s',true)",
                                                String.class);
                                        return jdbc.update(
                                                        """
INSERT INTO trading_maintenance_operations
    (operation_id,owner_user_id,kind,venue,scan_limit,state,trace_id,created_at)
VALUES (?,?,?,?,?,'STARTED',?,clock_timestamp())
ON CONFLICT(operation_id) DO NOTHING
""",
                                                        id,
                                                        owner,
                                                        kind.name(),
                                                        normalized,
                                                        bound,
                                                        traceId)
                                                == 1;
                                    }));
        } catch (DuplicateKeyException busy) {
            // 不同编号撞到未确认执行的全局唯一索引；失败事务已经回滚。
            if (find(id, owner).isEmpty())
                throw new IllegalStateException("MAINTENANCE_UNCONFIRMED_OPERATION_EXISTS");
            claimed = false;
        }
        Receipt original =
                find(id, owner)
                        .orElseThrow(
                                () -> new IllegalStateException("MAINTENANCE_IDENTITY_CONFLICT"));
        if (original.kind() != kind
                || !original.venue().equals(normalized)
                || !Objects.equals(original.limit(), bound))
            throw new IllegalStateException("MAINTENANCE_IDENTITY_CONFLICT");
        if (!claimed) return original;

        try {
            String detail;
            if (kind == Kind.RECONCILE) {
                int count = maintenance.runReconcile(normalized, bound);
                detail = "venue=" + normalized + ", limit=" + bound + ", new_trades=" + count;
            } else {
                var report = maintenance.runRecovery(normalized, original.traceId());
                // Binance旧适配器用共享报告承载候选数/新成交数/关联数，不能误称为账务或非法迁移。
                detail =
                        normalized.equals("BINANCE")
                                ? "venue=BINANCE, candidate_orders="
                                        + report.processedEventCount()
                                        + ", new_trades="
                                        + report.processedLedgerCount()
                                        + ", linked_external_order_ids="
                                        + report.invalidTransitionCount()
                                : "venue=OKX, processed_events="
                                        + report.processedEventCount()
                                        + ", processed_ledger="
                                        + report.processedLedgerCount()
                                        + ", invalid_transitions="
                                        + report.invalidTransitionCount()
                                        + ", imbalances="
                                        + report.imbalanceCount();
            }
            transaction.executeWithoutResult(
                    status ->
                            jdbc.update(
                                    """
UPDATE trading_maintenance_operations SET state='COMPLETED',detail=?,completed_at=clock_timestamp()
WHERE operation_id=? AND state='STARTED'
""",
                                    detail,
                                    id));
        } catch (RuntimeException unconfirmed) {
            // 原服务可能已部分写入；异常不能证明失败或安全重试，也不保存可能含敏感内容的异常文本。
            transaction.executeWithoutResult(
                    status ->
                            jdbc.update(
                                    "UPDATE trading_maintenance_operations SET state='UNKNOWN'"
                                        + " WHERE operation_id=? AND state='STARTED'",
                                    id));
        }
        return find(id, owner).orElseThrow();
    }

    @Override
    public Optional<Receipt> find(UUID id, long owner) {
        if (id == null || owner <= 0)
            throw new IllegalArgumentException("MAINTENANCE_IDENTITY_INVALID");
        return transaction.execute(
                status ->
                        jdbc
                                .query(
                                        """
SELECT operation_id,kind,venue,scan_limit,state,trace_id,detail,created_at,completed_at
FROM trading_maintenance_operations WHERE operation_id=? AND owner_user_id=?
""",
                                        (row, index) ->
                                                new Receipt(
                                                        row.getObject("operation_id", UUID.class),
                                                        Kind.valueOf(row.getString("kind")),
                                                        row.getString("venue"),
                                                        row.getObject("scan_limit", Integer.class),
                                                        row.getString("state"),
                                                        row.getString("trace_id"),
                                                        row.getString("detail"),
                                                        row.getTimestamp("created_at").toInstant(),
                                                        instant(row.getTimestamp("completed_at"))),
                                        id,
                                        owner)
                                .stream()
                                .findFirst());
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}

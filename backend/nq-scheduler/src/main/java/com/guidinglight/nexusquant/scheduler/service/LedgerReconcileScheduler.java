package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;
import com.guidinglight.nexusquant.scheduler.service.port.LedgerReconcileRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LedgerReconcileScheduler 提供 Gate B 最小对账任务。
 * <p>
 * Why:
 * 对账是闭环可靠性的兜底机制。即使 Gate B 仅是模拟盘，也需要周期性扫描差异并输出可审计证据，
 * 否则账本偏差只能在人工排查时被动发现。
 */
@Component
public class LedgerReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(LedgerReconcileScheduler.class);

    private final LedgerReconcileRepository ledgerReconcileRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * @param ledgerReconcileRepository 对账差异数据源
     * @param auditLogRepository        审计日志仓储
     */
    public LedgerReconcileScheduler(
            LedgerReconcileRepository ledgerReconcileRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.ledgerReconcileRepository = Objects.requireNonNull(
                ledgerReconcileRepository,
                "ledgerReconcileRepository must not be null"
        );
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.clock = Clock.systemUTC();
    }

    /**
     * 周期执行最小对账任务。
     */
    @Scheduled(
            fixedDelayString = "${nq.ledger.reconcile.fixed-delay-ms:30000}",
            initialDelayString = "${nq.ledger.reconcile.initial-delay-ms:5000}"
    )
    public void scheduledReconcile() {
        reconcileOnce();
    }

    /**
     * 手动执行一轮对账，便于测试与运维脚本直接调用。
     *
     * @return 差异条数
     */
    public int reconcileOnce() {
        List<LedgerReconcileDiff> diffs = ledgerReconcileRepository.findDiffs();
        if (diffs.isEmpty()) {
            auditLogRepository.append(
                    "LEDGER_RECONCILE",
                    "RECONCILE_MATCH",
                    "SYSTEM",
                    buildTraceId(),
                    detail("diff_count", 0)
            );
            return 0;
        }
        String traceId = buildTraceId();
        for (LedgerReconcileDiff diff : diffs) {
            log.warn(
                    "ledger_reconcile_diff trace_id={} account_id={} currency={} ledger_balance={} snapshot_balance={} diff={} reason={}",
                    traceId,
                    diff.accountId(),
                    diff.currency(),
                    diff.ledgerBalance(),
                    diff.snapshotBalance(),
                    diff.diffAmount(),
                    diff.reason()
            );
            auditLogRepository.append(
                    "LEDGER_RECONCILE",
                    "RECONCILE_DIFF_FOUND",
                    String.valueOf(diff.accountId()),
                    traceId,
                    detail(
                            "account_id", diff.accountId(),
                            "currency", diff.currency(),
                            "ledger_balance", diff.ledgerBalance(),
                            "snapshot_balance", diff.snapshotBalance(),
                            "diff", diff.diffAmount(),
                            "reason", diff.reason()
                    )
            );
        }
        return diffs.size();
    }

    private String buildTraceId() {
        return "trc-ledger-reconcile-" + Instant.now(clock).toEpochMilli();
    }

    private Map<String, Object> detail(Object... fields) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            detail.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return detail;
    }
}

package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * LedgerReconcileSchedulerTest 验证最小对账任务会输出差异审计。
 */
class LedgerReconcileSchedulerTest {

    /**
     * 当存在差异时应返回差异数并写入 RECONCILE_DIFF_FOUND 审计。
     */
    @Test
    void shouldWriteAuditWhenDiffExists() {
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        LedgerReconcileScheduler scheduler = new LedgerReconcileScheduler(
                () -> List.of(new LedgerReconcileDiff(
                        1001L,
                        "USDT",
                        new BigDecimal("100.00000000"),
                        new BigDecimal("90.00000000"),
                        new BigDecimal("10.00000000"),
                        "BALANCE_MISMATCH"
                )),
                auditLogRepository
        );

        int diffCount = scheduler.reconcileOnce();

        assertEquals(1, diffCount);
        assertTrue(auditLogRepository.containsAction("RECONCILE_DIFF_FOUND"));
    }

    /**
     * 当不存在差异时应记录 RECONCILE_MATCH。
     */
    @Test
    void shouldWriteMatchAuditWhenNoDiff() {
        RecordingAuditLogRepository auditLogRepository = new RecordingAuditLogRepository();
        LedgerReconcileScheduler scheduler = new LedgerReconcileScheduler(
                List::of,
                auditLogRepository
        );

        int diffCount = scheduler.reconcileOnce();

        assertEquals(0, diffCount);
        assertTrue(auditLogRepository.containsAction("RECONCILE_MATCH"));
    }

    private static final class RecordingAuditLogRepository implements AuditLogRepository {

        private final List<String> actions = new ArrayList<>();

        @Override
        public void append(String domain, String action, String actorId, String traceId, Map<String, Object> detail) {
            actions.add(action);
        }

        boolean containsAction(String action) {
            return actions.contains(action);
        }
    }
}


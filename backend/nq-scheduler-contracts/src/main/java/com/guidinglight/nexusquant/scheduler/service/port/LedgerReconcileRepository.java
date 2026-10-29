package com.guidinglight.nexusquant.scheduler.service.port;

import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;

import java.util.List;

/**
 * LedgerReconcileRepository 提供最小对账查询能力。
 */
public interface LedgerReconcileRepository {
    /** 后台按已恢复的可信账户身份执行有界对账。 */
    default List<LedgerReconcileDiff> findDiffs(long accountId) {
        throw new UnsupportedOperationException("scoped reconciliation required");
    }

    List<LedgerReconcileDiff> findDiffs();
}

package com.guidinglight.nexusquant.scheduler.service.port;

import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;

import java.util.List;

/**
 * LedgerReconcileRepository 提供最小对账查询能力。
 */
public interface LedgerReconcileRepository {

    List<LedgerReconcileDiff> findDiffs();
}

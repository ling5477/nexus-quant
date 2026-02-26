package com.guidinglight.nexusquant.scheduler.service.port;

import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;

import java.util.List;

/**
 * LedgerReconcileRepository 抽象对账任务对差异数据源的访问能力。
 */
public interface LedgerReconcileRepository {

    /**
     * 拉取当前全部账本差异。
     *
     * @return 差异列表；为空表示本轮无差异
     */
    List<LedgerReconcileDiff> findDiffs();
}

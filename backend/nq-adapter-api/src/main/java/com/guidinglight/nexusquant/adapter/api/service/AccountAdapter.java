package com.guidinglight.nexusquant.adapter.api.service;

import com.guidinglight.nexusquant.adapter.api.model.AccountBalanceSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AccountSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.PositionSnapshot;

import java.util.List;

/**
 * AccountAdapter 冻结 GateC 的统一账户端口。
 */
public interface AccountAdapter {

    /**
     * @return 本 adapter 支持的 venue 标识
     */
    String venue();

    /**
     * 拉取余额。
     */
    List<AccountBalanceSnapshot> getBalances(Long accountId, String traceId);

    /**
     * 拉取持仓。
     */
    List<PositionSnapshot> getPositions(Long accountId, String traceId);

    /**
     * 拉取账户聚合快照。
     */
    AccountSnapshot getAccountSnapshot(Long accountId, String traceId);
}

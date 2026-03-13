package com.guidinglight.nexusquant.api.model;

import java.util.List;

/**
 * AccountView 表示 GateD 最小账户查询视图。
 * <p>
 * Why:
 * 本轮目标是形成最小查询闭环，因此账户视图聚合账户标识、venue 与各币种最新余额集合，
 * 避免 controller 直接组装底表结果，确保 `nq-app` 仍只依赖 `nq-api` 的只读 facade。
 */
public record AccountView(
        Long accountId,
        String venue,
        List<AccountBalanceView> balances,
        String traceId
) {
}

package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;

/**
 * AccountBalanceSnapshot 表示统一余额快照。
 *
 * @param accountId 账户 ID
 * @param venue 交易场所
 * @param currency 币种
 * @param balance 总余额
 * @param available 可用余额
 * @param frozen 冻结余额
 * @param traceId 链路追踪 ID
 */
public record AccountBalanceSnapshot(
        Long accountId,
        String venue,
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        String traceId
) {
}

package com.guidinglight.nexusquant.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AccountSnapshotProjection 表示写入 `account_snapshots` 的最小账户快照。
 * <p>
 * Why:
 * GateD 第五批只要求把本地 PAPER 成交后的账户快照数据链补齐，因此该模型只冻结 account query
 * 直接依赖的最小字段，不额外扩成真实交易所的全量账户权益模型。
 *
 * @param accountId  账户 ID
 * @param currency   币种
 * @param balance    总余额
 * @param available  可用余额
 * @param frozen     冻结余额
 * @param snapshotTs 快照时间
 * @param traceId    链路追踪 ID
 */
public record AccountSnapshotProjection(
        Long accountId,
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        Instant snapshotTs,
        String traceId
) {
}

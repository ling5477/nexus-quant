package com.guidinglight.nexusquant.ledger.model;

import com.guidinglight.nexusquant.contracts.model.LedgerDirection;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * LedgerPostingEntry 表示待落库的账本分录。
 *
 * @param entryId 分录 ID
 * @param accountId 账户 ID
 * @param currency 币种
 * @param delta 增量（可正可负）
 * @param balanceAfter 分录落库后的余额快照
 * @param direction 分录方向
 * @param refType 引用类型
 * @param refId 引用 ID
 * @param idempotencyKey 幂等键
 * @param traceId 链路追踪 ID
 * @param ts 业务时间
 */
public record LedgerPostingEntry(
        String entryId,
        Long accountId,
        String currency,
        BigDecimal delta,
        BigDecimal balanceAfter,
        LedgerDirection direction,
        String refType,
        String refId,
        String idempotencyKey,
        String traceId,
        Instant ts
) {
}

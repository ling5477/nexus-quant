package com.guidinglight.nexusquant.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** 保留成交关联分录的双边原始事实；不将分录余额解释为当前账户余额，也不在查询中重新记账。 */
public record StrategyRunLedgerEntryItem(
        String entryId,
        String tradeId,
        String orderId,
        long accountId,
        String currency,
        BigDecimal delta,
        BigDecimal balanceAfter,
        String direction,
        String refType,
        String idempotencyKey,
        String traceId,
        Instant occurredAt) {}

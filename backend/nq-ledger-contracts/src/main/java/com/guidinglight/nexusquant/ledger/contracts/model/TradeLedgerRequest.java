package com.guidinglight.nexusquant.ledger.contracts.model;

import com.guidinglight.nexusquant.contracts.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * TradeLedgerRequest 描述“成交驱动记账”的输入事实。
 *
 * Why:
 * scheduler 与其他运行时编排模块只能依赖 ledger contracts，
 * 不能继续直接依赖 `nq-ledger` 内部模型。
 */
public record TradeLedgerRequest(
        String tradeId,
        String orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal fee,
        String feeCurrency,
        String traceId,
        Instant ts
) {
}

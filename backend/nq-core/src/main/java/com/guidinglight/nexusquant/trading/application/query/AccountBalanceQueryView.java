package com.guidinglight.nexusquant.trading.application.query;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AccountBalanceQueryView 定义 trading 查询门面输出的内部账户余额快照。
 */
public record AccountBalanceQueryView(
        String currency,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal frozen,
        Instant snapshotTs,
        String traceId
) {
}

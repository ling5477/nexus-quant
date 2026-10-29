package com.guidinglight.nexusquant.ledger.service.port;

import java.math.BigDecimal;

/** 隔离 PAPER 账户的显式测试预算入口；仅作用于 canonical ledger。 */
public interface SimCashFundingPort {
    BigDecimal fundOnce(long accountId, String paperRunId, BigDecimal budget, String traceId);
    BigDecimal cashBalance(long accountId);
}

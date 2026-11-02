package com.guidinglight.nexusquant.ledger.contracts.model;

/** 账户快照数值的本地计算来源；两种来源均不声明与交易所全账户口径相同。 */
public enum AccountBalanceBasis {
    POSITION_PROJECTION,
    LEDGER_CASH_PROJECTION
}

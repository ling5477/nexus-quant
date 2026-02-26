package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.ledger.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.model.TradeLedgerRequest;

/**
 * TradeLedgerGateway 抽象 scheduler 到 ledger 的调用。
 */
public interface TradeLedgerGateway {

    /**
     * 对成交执行记账。
     *
     * @param request 记账请求
     * @return 记账结果
     */
    LedgerPostingResult postTrade(TradeLedgerRequest request);
}

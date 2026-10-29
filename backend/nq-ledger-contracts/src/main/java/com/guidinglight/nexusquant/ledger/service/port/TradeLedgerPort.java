package com.guidinglight.nexusquant.ledger.service.port;

import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;

/**
 * TradeLedgerPort 定义 runtime 编排层调用 ledger 的最小写侧入口。
 * <p>
 * Why:
 * `nq-scheduler` 只能依赖 contracts port，不能继续直接绑定 `TradeLedgerPostingService`。
 */
public interface TradeLedgerPort {

    LedgerPostingResult postTrade(TradeLedgerRequest request);
}

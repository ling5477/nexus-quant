package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.ledger.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.service.TradeLedgerPostingService;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * LedgerModuleTradeLedgerGateway 复用 nq-ledger 的记账服务。
 */
@Component
public class LedgerModuleTradeLedgerGateway implements TradeLedgerGateway {

    private final TradeLedgerPostingService tradeLedgerPostingService;

    /**
     * @param tradeLedgerPostingService ledger 记账编排服务
     */
    public LedgerModuleTradeLedgerGateway(TradeLedgerPostingService tradeLedgerPostingService) {
        this.tradeLedgerPostingService = Objects.requireNonNull(
                tradeLedgerPostingService,
                "tradeLedgerPostingService must not be null"
        );
    }

    @Override
    public LedgerPostingResult postTrade(TradeLedgerRequest request) {
        return tradeLedgerPostingService.postTrade(request);
    }
}

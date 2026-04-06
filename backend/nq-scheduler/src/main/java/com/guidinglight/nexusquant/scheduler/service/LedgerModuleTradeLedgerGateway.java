package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.ledger.contracts.model.TradeLedgerRequest;
import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * LedgerModuleTradeLedgerGateway 复用 nq-ledger 的记账服务。
 */
@Component
public class LedgerModuleTradeLedgerGateway implements TradeLedgerGateway {

    private final TradeLedgerPort tradeLedgerPort;

    /**
     * @param tradeLedgerPort ledger contracts 写侧端口
     */
    public LedgerModuleTradeLedgerGateway(TradeLedgerPort tradeLedgerPort) {
        this.tradeLedgerPort = Objects.requireNonNull(
                tradeLedgerPort,
                "tradeLedgerPort must not be null"
        );
    }

    @Override
    public LedgerPostingResult postTrade(TradeLedgerRequest request) {
        return tradeLedgerPort.postTrade(request);
    }
}

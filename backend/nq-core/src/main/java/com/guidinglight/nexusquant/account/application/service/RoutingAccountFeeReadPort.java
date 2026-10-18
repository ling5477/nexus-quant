package com.guidinglight.nexusquant.account.application.service;

import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;

import java.util.List;

/** 多来源只按各自显式账户范围路由；零条或多条匹配均拒绝，不静默选择某个交易所或错误版本。 */
public final class RoutingAccountFeeReadPort implements AccountFeeReadPort {
    private final List<AccountFeeReadPort> sources;

    public RoutingAccountFeeReadPort(List<AccountFeeReadPort> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public boolean supports(ExchangeAccountSummary account, String symbol) {
        return matches(account, symbol).size() == 1;
    }

    @Override
    public AccountFeeQuote read(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String symbol) {
        var selected = matches(account, symbol);
        if (selected.size() != 1)
            throw new AccountFeeSyncException("FEE_SOURCE_ROUTING_UNAVAILABLE");
        return selected.getFirst().read(account, credential, symbol);
    }

    private List<AccountFeeReadPort> matches(ExchangeAccountSummary account, String symbol) {
        return sources.stream()
                .filter(source -> source.supports(account, symbol))
                .limit(2)
                .toList();
    }
}

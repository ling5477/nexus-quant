package com.guidinglight.nexusquant.core.account.application;

import com.guidinglight.nexusquant.core.account.application.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.core.account.domain.ExchangeAccountSummary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ExchangeAccountQueryService 提供账户上下文与账户管理页的最小查询能力。
 */
public class ExchangeAccountQueryService {

    private final ExchangeAccountRepository exchangeAccountRepository;

    public ExchangeAccountQueryService(ExchangeAccountRepository exchangeAccountRepository) {
        this.exchangeAccountRepository = Objects.requireNonNull(
                exchangeAccountRepository,
                "exchangeAccountRepository must not be null"
        );
    }

    public List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            return List.of();
        }
        return exchangeAccountRepository.listByOwnerUserId(ownerUserId);
    }

    public Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            return Optional.empty();
        }
        return exchangeAccountRepository.findDefaultByOwnerUserId(ownerUserId);
    }
}

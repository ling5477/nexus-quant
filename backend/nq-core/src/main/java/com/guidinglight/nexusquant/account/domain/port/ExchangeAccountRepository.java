package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;

import java.util.List;
import java.util.Optional;

/**
 * ExchangeAccountRepository 定义 exchange account 读模型端口。
 */
public interface ExchangeAccountRepository {

    List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId);

    Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId);
}



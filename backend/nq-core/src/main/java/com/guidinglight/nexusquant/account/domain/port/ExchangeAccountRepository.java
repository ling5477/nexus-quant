package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ExchangeAccountRepository 定义 exchange account 读写端口。
 * <p>
 * Why:
 * RC1-4 需要把账户列表、默认账户上下文和最小写侧动作收敛到同一个业务域端口，
 * 避免 controller 或 auth 链路直接拼 SQL 处理默认账户切换和状态更新。
 */
public interface ExchangeAccountRepository {

    List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId);

    Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long exchangeAccountId);

    Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId);

    ExchangeAccountSummary create(
            Long ownerUserId,
            String exchangeCode,
            String tradeEnv,
            String accountAlias,
            String externalAccountRef,
            Instant now
    );

    boolean updateProfile(
            Long ownerUserId,
            Long exchangeAccountId,
            String accountAlias,
            String externalAccountRef,
            Instant now
    );

    boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now);

    boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now);

    void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now);

    boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now);
}



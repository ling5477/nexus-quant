package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ExchangeAccountRepository 定义 exchange account 读写端口。
 *
 * <p>Why: 需要把账户列表、默认账户上下文和最小写侧动作收敛到同一个业务域端口， 避免 controller 或 auth 链路直接拼 SQL 处理默认账户切换和状态更新。
 */
public interface ExchangeAccountRepository {

    List<ExchangeAccountSummary> listByOwnerUserId(Long ownerUserId);

    Optional<ExchangeAccountSummary> findById(Long exchangeAccountId);

    Optional<ExchangeAccountSummary> findByIdForOwner(Long ownerUserId, Long exchangeAccountId);

    /** 按已登记的交易身份校验归属；没有真实兼容映射的仓储实现默认拒绝，不能猜测两个ID相同。 */
    default boolean hasTradingScopeForOwner(
            Long ownerUserId, Long tradingAccountId, String venue, String tradeEnv) {
        return false;
    }

    Optional<ExchangeAccountSummary> findDefaultByOwnerUserId(Long ownerUserId);

    ExchangeAccountSummary create(
            Long ownerUserId,
            String exchangeCode,
            String tradeEnv,
            String accountAlias,
            String externalAccountRef,
            Instant now);

    boolean updateProfile(
            Long ownerUserId,
            Long exchangeAccountId,
            String accountAlias,
            String externalAccountRef,
            Instant now);

    boolean enable(Long ownerUserId, Long exchangeAccountId, Instant now);

    boolean disable(Long ownerUserId, Long exchangeAccountId, Instant now);

    void clearDefaultByScope(Long ownerUserId, String exchangeCode, String tradeEnv, Instant now);

    boolean markDefault(Long ownerUserId, Long exchangeAccountId, Instant now);

    /** 持有用户行锁并原子切换唯一默认账户；不赋予选中账户任何交易权限。 */
    default boolean switchDefaultForOwner(Long ownerUserId, Long exchangeAccountId, Instant now) {
        throw new UnsupportedOperationException("atomic user default switching is unavailable");
    }
}

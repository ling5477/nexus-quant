package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 仅保存派生费率证据，不拥有订单、成交、余额或账务事实。 */
public interface AccountFeeRepository {
    /** 在当前事务中按用户/操作身份及账户顺序加锁；实现须限制等待，不能在读取网络前后释放身份锁。 */
    void lock(long owner, long account, UUID operationId);

    /** 按用户及稳定同步身份读取已提交证据；未查到回执不代表同步请求已经失败。 */
    Optional<AccountFeeObservation> findOperation(long owner, UUID operationId);

    /** 仅允许读取该用户的观察身份；不得跨用户解析冻结费用来源。 */
    Optional<AccountFeeObservation> findObservation(long owner, UUID observationId);

    /** 按账户、标的及观察时间/身份倒序分页；游标必须来自同一范围，最多返回51条以判断下一页。 */
    List<AccountFeeObservation> history(
            long owner, long account, String symbol, UUID before, int limit);

    /** 一次插入同时保存观察事实和成功回执；加入现有事务，不更新或覆盖历史证据。 */
    void save(AccountFeeObservation observation);
}

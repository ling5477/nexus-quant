package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.AccountCommandReceipt;

import java.util.Optional;
import java.util.UUID;

/** 账户命令成功回执端口；身份锁、业务变更与回执须处于同一事务，不能另建账户事实源。 */
public interface AccountCommandReceiptRepository {
    /** 锁定用户与命令身份并持有到事务结束；实现必须限制等待时间，禁止无事务调用。 */
    void lock(Long ownerUserId, UUID commandId);

    /** 仅查询该用户已提交的成功回执；空结果不证明另一事务失败或不会稍后提交。 */
    Optional<AccountCommandReceipt> find(Long ownerUserId, UUID commandId);

    /** 与对应账户变更一同提交不可变回执；同身份只能保存一次，失败时整体回滚。 */
    void save(AccountCommandReceipt receipt);
}

package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.CredentialOperationReceipt;

import java.util.Optional;
import java.util.UUID;

/** 锁与回执必须加入业务事务；带密钥指纹避免低熵敏感输入遭离线枚举。 */
public interface CredentialOperationRepository {
    /** 先锁用户与操作身份，再锁其所属账户，串行化该账户版本链；锁须加入业务事务。 */
    void lock(Long owner, Long account, UUID operationId);

    /** 限定用户、账户及原操作身份读取成功回执；未提交结果不能据此判定失败。 */
    Optional<CredentialOperationReceipt> find(Long owner, Long account, UUID operationId);

    /** 以服务端密钥计算敏感输入指纹；不保存明文，调用方计算后应清零临时字节数组。 */
    String fingerprint(byte[] input);

    /** 返回非敏感指纹密钥身份；重放须同时匹配密钥身份及加密版本，防止配置变化误认原输入。 */
    String fingerprintKeyId();

    /** 版本变化、审计与成功回执须同事务提交；只持久化脱敏摘要和带密钥指纹。 */
    void save(CredentialOperationReceipt receipt);
}

package com.guidinglight.nexusquant.account.domain;

/**
 * ExchangeCredentialPermissionProbeRequest 是传入 adapter port 的权限探活上下文。
 *
 * <p>Why: Service 负责 owner/account/credential gate，adapter 只接收服务端派生的非敏感引用。
 * 真实 adapter 必须通过 infrastructure 内的 scoped credential executor 加载和清理凭证材料；
 * decrypted payload 不得进入本 record、API response、audit metadata、日志或持久化字段。</p>
 */
public record ExchangeCredentialPermissionProbeRequest(
        Long ownerUserId,
        Long accountId,
        Long credentialId,
        String exchange,
        String tradeEnv,
        String credentialType,
        CredentialPermissionExpectation permissionExpectation,
        boolean dryRun,
        String traceId
) {
}

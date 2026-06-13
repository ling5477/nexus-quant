package com.guidinglight.nexusquant.account.domain;

/**
 * ExchangeCredentialPermissionProbeRequest 是传入 adapter port 的权限探活上下文。
 *
 * <p>Why: Service 负责 owner/account/credential gate，adapter 只需要最小交易所上下文和
 * credential material。该 record 允许携带 decryptedPayloadJson，但它只能在内存中传递给 port，
 * 禁止进入 API response、audit metadata、日志或持久化字段。</p>
 */
public record ExchangeCredentialPermissionProbeRequest(
        Long accountId,
        Long credentialId,
        String exchange,
        String tradeEnv,
        String credentialType,
        String requestedMode,
        boolean dryRun,
        String traceId,
        String decryptedPayloadJson
) {
}

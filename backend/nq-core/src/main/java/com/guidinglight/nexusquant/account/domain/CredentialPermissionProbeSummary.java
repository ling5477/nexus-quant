package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;

/**
 * CredentialPermissionProbeSummary 是 API 可返回的最新 permission probe 脱敏摘要。
 *
 * <p>Why: 该对象只暴露状态、scope、IP allowlist、失败计数和 trace/request id，
 * 不包含 maskedAccessKey、encrypted_payload、decrypted payload、API key、secret、
 * passphrase、signature、headers 或 raw exchange response。</p>
 */
public record CredentialPermissionProbeSummary(
        Long accountId,
        Long credentialId,
        String credentialType,
        String exchange,
        String permissionProbeStatus,
        String permissionScope,
        boolean withdrawEnabled,
        String ipAllowlistProbeStatus,
        int failedAuthCount,
        Instant lastPermissionProbeAt,
        String sanitizedErrorCategory,
        String requestId,
        String traceId
) {

    public static CredentialPermissionProbeSummary from(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String requestId,
            String traceId
    ) {
        return new CredentialPermissionProbeSummary(
                account.exchangeAccountId(),
                credential.credentialId(),
                credential.credentialType(),
                account.exchangeCode(),
                credential.permissionProbeStatus(),
                credential.permissionScope(),
                credential.withdrawEnabled(),
                credential.ipAllowlistProbeStatus(),
                credential.failedAuthCount(),
                credential.lastPermissionProbeAt(),
                credential.lastPermissionProbeError(),
                requestId,
                traceId
        );
    }
}

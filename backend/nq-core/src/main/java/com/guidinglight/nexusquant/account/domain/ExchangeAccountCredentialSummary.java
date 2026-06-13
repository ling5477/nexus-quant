package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;

/**
 * ExchangeAccountCredentialSummary 描述前端可见的当前 active 凭证摘要。
 * <p>
 * Why:
 * RC1-4 需要展示 active / verification / rotated-from 状态，
 * 但绝不能把明文 secret / passphrase / private key 暴露给 API 或前端。
 * Batch 5-C 只补充 credentialStatus、revokedAt、rotatedAt 等非敏感生命周期摘要，
 * 避免 API 为了展示撤销状态而读取 encrypted_payload 或 decrypted payload。
 */
public record ExchangeAccountCredentialSummary(
        Long credentialId,
        Long exchangeAccountId,
        String credentialType,
        String maskedAccessKey,
        String credentialStatus,
        String verificationStatus,
        boolean isActive,
        Instant revokedAt,
        Long rotatedFromCredentialId,
        Instant rotatedAt,
        Instant lastVerifiedAt,
        String lastVerificationError,
        Instant updatedAt,
        String permissionProbeStatus,
        String permissionScope,
        boolean withdrawEnabled,
        String ipAllowlistProbeStatus,
        int failedAuthCount,
        Instant lastPermissionProbeAt,
        String lastPermissionProbeError
) {

    public ExchangeAccountCredentialSummary(
            Long credentialId,
            Long exchangeAccountId,
            String credentialType,
            String maskedAccessKey,
            String credentialStatus,
            String verificationStatus,
            boolean isActive,
            Instant revokedAt,
            Long rotatedFromCredentialId,
            Instant rotatedAt,
            Instant lastVerifiedAt,
            String lastVerificationError,
            Instant updatedAt
    ) {
        this(
                credentialId,
                exchangeAccountId,
                credentialType,
                maskedAccessKey,
                credentialStatus,
                verificationStatus,
                isActive,
                revokedAt,
                rotatedFromCredentialId,
                rotatedAt,
                lastVerifiedAt,
                lastVerificationError,
                updatedAt,
                "NOT_PROBED",
                null,
                false,
                "NOT_CHECKED",
                0,
                null,
                null
        );
    }
}

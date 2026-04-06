package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;

/**
 * ExchangeAccountCredentialSummary 描述前端可见的当前 active 凭证摘要。
 * <p>
 * Why:
 * RC1-4 需要展示 active / verification / rotated-from 状态，
 * 但绝不能把明文 secret / passphrase / private key 暴露给 API 或前端。
 */
public record ExchangeAccountCredentialSummary(
        Long credentialId,
        Long exchangeAccountId,
        String credentialType,
        String maskedAccessKey,
        String verificationStatus,
        boolean isActive,
        Long rotatedFromCredentialId,
        Instant lastVerifiedAt,
        String lastVerificationError,
        Instant updatedAt
) {
}

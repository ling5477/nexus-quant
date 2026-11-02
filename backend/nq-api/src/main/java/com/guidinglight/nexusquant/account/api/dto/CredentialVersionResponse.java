package com.guidinglight.nexusquant.account.api.dto;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;

import java.time.Instant;

/** 历史和操作回执使用完整脱敏版本摘要，不改变旧active响应合同。 */
public record CredentialVersionResponse(
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
        String lastPermissionProbeError) {
    public static CredentialVersionResponse from(ExchangeAccountCredentialSummary s) {
        return new CredentialVersionResponse(
                s.credentialId(),
                s.exchangeAccountId(),
                s.credentialType(),
                s.maskedAccessKey(),
                s.credentialStatus(),
                s.verificationStatus(),
                s.isActive(),
                s.revokedAt(),
                s.rotatedFromCredentialId(),
                s.rotatedAt(),
                s.lastVerifiedAt(),
                s.lastVerificationError(),
                s.updatedAt(),
                s.permissionProbeStatus(),
                s.permissionScope(),
                s.withdrawEnabled(),
                s.ipAllowlistProbeStatus(),
                s.failedAuthCount(),
                s.lastPermissionProbeAt(),
                s.lastPermissionProbeError());
    }
}

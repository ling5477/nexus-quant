package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;

/**
 * ExchangeAccountCredentialMaterial 表示服务端校验时可读取的 active 凭证材料。
 * <p>
 * Why:
 * 结构性校验必须在服务端拿到解密后的 payload 才能构造 OKX/Binance 凭证对象，
 * 但该材料只能停留在应用层内存，不可直接透出到 API 响应。
 * credentialStatus 只用于服务端判断 active material 是否仍可用；
 * decryptedPayloadJson 仍不得进入 API DTO、日志或 audit metadata。
 */
public record ExchangeAccountCredentialMaterial(
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
        String decryptedPayloadJson,
        String permissionProbeStatus,
        String permissionScope,
        boolean withdrawEnabled,
        String ipAllowlistProbeStatus,
        int failedAuthCount,
        Instant lastPermissionProbeAt,
        String lastPermissionProbeError
) {

    public ExchangeAccountCredentialMaterial(
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
            String decryptedPayloadJson
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
                decryptedPayloadJson,
                "NOT_PROBED",
                null,
                false,
                "NOT_CHECKED",
                0,
                null,
                null
        );
    }

    public ExchangeAccountCredentialSummary toSummary() {
        return new ExchangeAccountCredentialSummary(
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
                permissionProbeStatus,
                permissionScope,
                withdrawEnabled,
                ipAllowlistProbeStatus,
                failedAuthCount,
                lastPermissionProbeAt,
                lastPermissionProbeError
        );
    }
}

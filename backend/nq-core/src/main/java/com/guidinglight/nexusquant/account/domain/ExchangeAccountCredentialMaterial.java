package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;

/**
 * ExchangeAccountCredentialMaterial 表示服务端校验时可读取的 active 凭证材料。
 * <p>
 * Why:
 * 结构性校验必须在服务端拿到解密后的 payload 才能构造 OKX/Binance 凭证对象，
 * 但该材料只能停留在应用层内存，不可直接透出到 API 响应。
 */
public record ExchangeAccountCredentialMaterial(
        Long credentialId,
        Long exchangeAccountId,
        String credentialType,
        String maskedAccessKey,
        String verificationStatus,
        boolean isActive,
        Long rotatedFromCredentialId,
        Instant lastVerifiedAt,
        String lastVerificationError,
        Instant updatedAt,
        String decryptedPayloadJson
) {

    public ExchangeAccountCredentialSummary toSummary() {
        return new ExchangeAccountCredentialSummary(
                credentialId,
                exchangeAccountId,
                credentialType,
                maskedAccessKey,
                verificationStatus,
                isActive,
                rotatedFromCredentialId,
                lastVerifiedAt,
                lastVerificationError,
                updatedAt
        );
    }
}

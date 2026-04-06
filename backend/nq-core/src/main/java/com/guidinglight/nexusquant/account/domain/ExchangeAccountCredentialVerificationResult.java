package com.guidinglight.nexusquant.account.domain;

/**
 * ExchangeAccountCredentialVerificationResult 描述一次凭证结构性校验的结果。
 * <p>
 * Why:
 * RC1-4 首版不连真实外网，只做“可构造 / 可签名”校验；
 * 因此需要稳定表达 success/failure 与错误摘要，回写到 verification 状态流。
 */
public record ExchangeAccountCredentialVerificationResult(
        boolean verified,
        String errorMessage
) {

    public static ExchangeAccountCredentialVerificationResult success() {
        return new ExchangeAccountCredentialVerificationResult(true, null);
    }

    public static ExchangeAccountCredentialVerificationResult failed(String errorMessage) {
        return new ExchangeAccountCredentialVerificationResult(false, errorMessage);
    }
}

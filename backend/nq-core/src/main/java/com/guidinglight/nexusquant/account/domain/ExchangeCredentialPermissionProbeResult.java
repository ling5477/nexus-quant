package com.guidinglight.nexusquant.account.domain;

import java.time.Instant;

/**
 * ExchangeCredentialPermissionProbeResult 表示 adapter 返回的脱敏权限探活结果。
 *
 * <p>Why: permission probe 需要把认证失败、IP allowlist、限流和远端错误分类写回 NQ，
 * 但绝不能把 raw response、headers、signature、request body 或 credential material 带回
 * Service、audit metadata 或 API response。</p>
 */
public record ExchangeCredentialPermissionProbeResult(
        String exchange,
        String credentialType,
        String permissionProbeStatus,
        String detectedPermissionScope,
        boolean withdrawEnabledDetected,
        String ipAllowlistProbeStatus,
        String sanitizedErrorCategory,
        String sanitizedErrorMessage,
        int retryCount,
        String requestId,
        String traceId,
        Instant startedAt,
        Instant finishedAt
) {

    public static ExchangeCredentialPermissionProbeResult succeeded(
            String exchange,
            String credentialType,
            String detectedPermissionScope,
            String ipAllowlistProbeStatus,
            String requestId,
            String traceId,
            Instant startedAt,
            Instant finishedAt
    ) {
        return new ExchangeCredentialPermissionProbeResult(
                exchange,
                credentialType,
                "SUCCEEDED",
                detectedPermissionScope,
                false,
                ipAllowlistProbeStatus,
                null,
                null,
                0,
                requestId,
                traceId,
                startedAt,
                finishedAt
        );
    }

    public static ExchangeCredentialPermissionProbeResult failed(
            String exchange,
            String credentialType,
            String errorCategory,
            String ipAllowlistProbeStatus,
            String requestId,
            String traceId,
            Instant startedAt,
            Instant finishedAt
    ) {
        return new ExchangeCredentialPermissionProbeResult(
                exchange,
                credentialType,
                "FAILED",
                null,
                false,
                ipAllowlistProbeStatus,
                errorCategory,
                errorCategory,
                0,
                requestId,
                traceId,
                startedAt,
                finishedAt
        );
    }

    public static ExchangeCredentialPermissionProbeResult skipped(
            String exchange,
            String credentialType,
            String errorCategory,
            String requestId,
            String traceId,
            Instant startedAt,
            Instant finishedAt
    ) {
        return new ExchangeCredentialPermissionProbeResult(
                exchange,
                credentialType,
                "SKIPPED",
                null,
                false,
                "SKIPPED",
                errorCategory,
                errorCategory,
                0,
                requestId,
                traceId,
                startedAt,
                finishedAt
        );
    }
}

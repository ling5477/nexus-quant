package com.guidinglight.nexusquant.trading.application.preflight;

import java.time.Instant;

/**
 * TradingPreflightCredentialTypeSummary 是 preflight 可返回的 credential metadata 摘要。
 *
 * <p>Why: 该摘要只暴露 credential type、生命周期、verification 与 latest permission probe metadata。
 * 它不包含 maskedAccessKey、encrypted payload、decrypted payload、API key、secret、passphrase、
 * private key、token、signature、headers 或 raw provider response。
 */
public record TradingPreflightCredentialTypeSummary(
        Long credentialId,
        String credentialType,
        String credentialStatus,
        String verificationStatus,
        boolean active,
        String permissionProbeStatus,
        String permissionScope,
        String ipAllowlistProbeStatus,
        int failedAuthCount,
        Instant lastVerifiedAt,
        Instant lastPermissionProbeAt
) {
}

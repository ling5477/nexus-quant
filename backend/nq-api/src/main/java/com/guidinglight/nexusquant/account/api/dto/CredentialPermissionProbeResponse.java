package com.guidinglight.nexusquant.account.api.dto;

import com.guidinglight.nexusquant.account.domain.CredentialPermissionProbeSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * CredentialPermissionProbeResponse 是 permission probe 对外脱敏摘要。
 *
 * <p>Why: response 只允许返回状态、scope、IP allowlist、失败计数和 request/trace id，
 * 不返回 raw response、headers、signature、encrypted/decrypted payload、API key、secret、
 * private key 或 passphrase。</p>
 */
@Schema(name = "CredentialPermissionProbeResponse", description = "credential permission probe 脱敏摘要")
public record CredentialPermissionProbeResponse(
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

    public static CredentialPermissionProbeResponse from(CredentialPermissionProbeSummary summary) {
        return new CredentialPermissionProbeResponse(
                summary.accountId(),
                summary.credentialId(),
                summary.credentialType(),
                summary.exchange(),
                summary.permissionProbeStatus(),
                summary.permissionScope(),
                summary.withdrawEnabled(),
                summary.ipAllowlistProbeStatus(),
                summary.failedAuthCount(),
                summary.lastPermissionProbeAt(),
                summary.sanitizedErrorCategory(),
                summary.requestId(),
                summary.traceId()
        );
    }
}

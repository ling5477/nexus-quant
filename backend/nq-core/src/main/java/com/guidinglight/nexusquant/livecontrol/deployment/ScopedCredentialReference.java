package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 控制面可见的 credential reference/capability 事实；不包含 masked key 或任何 credential material。
 */
public record ScopedCredentialReference(
        long ownerId,
        long exchangeAccountId,
        long credentialReference,
        String venue,
        String credentialType,
        ScopedCredentialCapability capability,
        String lifecycleStatus,
        boolean active,
        String verificationStatus,
        String permissionProbeStatus,
        String permissionScopeDigest,
        boolean remotelyVerifiedReadOnly,
        boolean withdrawEnabled,
        boolean ipAllowlistConfigured,
        RemoteIpVerificationStatus remoteIpVerificationStatus,
        Instant lastPermissionProbeAt,
        Instant revokedAt,
        Instant rotatedAt
) {
    public ScopedCredentialReference {
        venue = requireText(venue, "venue");
        credentialType = requireText(credentialType, "credentialType");
        Objects.requireNonNull(capability, "capability must not be null");
        lifecycleStatus = requireText(lifecycleStatus, "lifecycleStatus");
        verificationStatus = requireText(verificationStatus, "verificationStatus");
        permissionProbeStatus = requireText(permissionProbeStatus, "permissionProbeStatus");
        permissionScopeDigest = permissionScopeDigest == null
                ? null : requireText(permissionScopeDigest, "permissionScopeDigest");
        Objects.requireNonNull(remoteIpVerificationStatus, "remoteIpVerificationStatus must not be null");
    }

    /**
     * 从既有 credential summary 构造最小控制面事实；不复制 maskedAccessKey 或错误详情。
     */
    public static ScopedCredentialReference fromSummary(
            long ownerId,
            String venue,
            ScopedCredentialCapability capability,
            ExchangeAccountCredentialSummary summary
    ) {
        Objects.requireNonNull(summary, "summary must not be null");
        boolean remoteIpVerified = "PASSED".equals(summary.ipAllowlistProbeStatus());
        return new ScopedCredentialReference(
                ownerId,
                summary.exchangeAccountId(),
                summary.credentialId(),
                venue,
                summary.credentialType(),
                capability,
                summary.credentialStatus(),
                summary.isActive(),
                summary.verificationStatus(),
                summary.permissionProbeStatus(),
                digestPermissionScope(summary.permissionScope()),
                "SUCCEEDED".equals(summary.permissionProbeStatus())
                        && "READ_ONLY".equals(summary.permissionScope()),
                summary.withdrawEnabled(),
                remoteIpVerified,
                remoteIpVerified
                        ? RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED
                        : RemoteIpVerificationStatus.NOT_VERIFIABLE,
                summary.lastPermissionProbeAt(),
                summary.revokedAt(),
                summary.rotatedAt()
        );
    }

    public static String digestPermissionScope(String permissionScope) {
        if (permissionScope == null || permissionScope.isBlank()) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(permissionScope.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public enum RemoteIpVerificationStatus {
        REMOTE_PERMISSION_IP_VERIFIED,
        REMOTE_PERMISSION_IP_MISMATCH,
        REMOTE_PERMISSION_IP_MISSING,
        NOT_VERIFIABLE,
        UNKNOWN
    }
}

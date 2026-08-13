package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialReference.RemoteIpVerificationStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Scoped credential reference/capability fail-closed policy。
 *
 * <p>本策略只读取脱敏 metadata，不读取或解密 material。JIT credential executor 必须在该策略通过后，
 * 再按 owner/account/reference/type 精确绑定，并把 material 限制在同步 adapter callback 生命周期。</p>
 */
public final class ScopedCredentialCapabilityPolicy {

    private static final String OKX_VENUE = "OKX_SPOT";
    private static final String OKX_TYPE = "OKX_API_V5";
    private final Duration maximumProbeAge;

    public ScopedCredentialCapabilityPolicy(Duration maximumProbeAge) {
        this.maximumProbeAge = Objects.requireNonNull(maximumProbeAge, "maximumProbeAge must not be null");
        if (maximumProbeAge.isZero() || maximumProbeAge.isNegative()) {
            throw new IllegalArgumentException("maximumProbeAge must be positive");
        }
    }

    public Decision evaluate(ScopedCredentialReference reference, Instant now) {
        if (reference == null) return Decision.denied(Reason.CREDENTIAL_REFERENCE_MISSING);
        Objects.requireNonNull(now, "now must not be null");
        if (!reference.capability().privateReadonlyDiagnosticCallable()) {
            return Decision.denied(reference.capability() == ScopedCredentialCapability.FORBIDDEN
                    ? Reason.CAPABILITY_FORBIDDEN : Reason.FUTURE_CAPABILITY_NOT_CALLABLE);
        }
        if (reference.ownerId() <= 0 || reference.exchangeAccountId() <= 0
                || reference.credentialReference() <= 0) {
            return Decision.denied(Reason.CREDENTIAL_REFERENCE_INVALID);
        }
        if (!OKX_VENUE.equals(reference.venue()) || !OKX_TYPE.equals(reference.credentialType())) {
            return Decision.denied(Reason.CREDENTIAL_SCOPE_MISMATCH);
        }
        if (!reference.active() || !"ACTIVE".equals(reference.lifecycleStatus())) {
            return Decision.denied(Reason.CREDENTIAL_NOT_ACTIVE);
        }
        if (reference.revokedAt() != null || reference.rotatedAt() != null) {
            return Decision.denied(Reason.CREDENTIAL_REVOKED_OR_ROTATED);
        }
        if (!"VERIFIED".equals(reference.verificationStatus())) {
            return Decision.denied(Reason.CREDENTIAL_NOT_VERIFIED);
        }
        if (!"SUCCEEDED".equals(reference.permissionProbeStatus())
                || !reference.remotelyVerifiedReadOnly()
                || !Objects.equals(
                        ScopedCredentialReference.digestPermissionScope("READ_ONLY"),
                        reference.permissionScopeDigest())
                || reference.withdrawEnabled()) {
            return Decision.denied(Reason.REMOTE_PERMISSION_NOT_READ_ONLY);
        }
        if (!reference.ipAllowlistConfigured()) {
            return Decision.denied(Reason.IP_ALLOWLIST_NOT_CONFIGURED);
        }
        if (reference.remoteIpVerificationStatus() != RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED) {
            return Decision.denied(reference.remoteIpVerificationStatus() == RemoteIpVerificationStatus.NOT_VERIFIABLE
                    ? Reason.REMOTE_PERMISSION_IP_NOT_VERIFIABLE
                    : Reason.REMOTE_PERMISSION_IP_NOT_VERIFIED);
        }
        Instant probedAt = reference.lastPermissionProbeAt();
        if (probedAt == null || probedAt.isAfter(now)
                || Duration.between(probedAt, now).compareTo(maximumProbeAge) > 0) {
            return Decision.denied(Reason.PERMISSION_PROBE_STALE);
        }
        return Decision.allowed();
    }

    public record Decision(Status status, Reason reason) {
        public Decision {
            Objects.requireNonNull(status);
            if ((status == Status.ALLOWED) != (reason == null)) {
                throw new IllegalArgumentException("allowed status and reason are inconsistent");
            }
        }

        public static Decision allowed() {
            return new Decision(Status.ALLOWED, null);
        }

        public static Decision denied(Reason reason) {
            return new Decision(Status.DENIED, Objects.requireNonNull(reason));
        }
    }

    public enum Status { ALLOWED, DENIED }

    public enum Reason {
        CREDENTIAL_REFERENCE_MISSING,
        CREDENTIAL_REFERENCE_INVALID,
        CAPABILITY_FORBIDDEN,
        FUTURE_CAPABILITY_NOT_CALLABLE,
        CREDENTIAL_SCOPE_MISMATCH,
        CREDENTIAL_NOT_ACTIVE,
        CREDENTIAL_REVOKED_OR_ROTATED,
        CREDENTIAL_NOT_VERIFIED,
        REMOTE_PERMISSION_NOT_READ_ONLY,
        IP_ALLOWLIST_NOT_CONFIGURED,
        REMOTE_PERMISSION_IP_NOT_VERIFIED,
        REMOTE_PERMISSION_IP_NOT_VERIFIABLE,
        PERMISSION_PROBE_STALE
    }
}

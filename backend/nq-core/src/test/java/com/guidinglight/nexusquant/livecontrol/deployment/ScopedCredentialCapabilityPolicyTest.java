package com.guidinglight.nexusquant.livecontrol.deployment;

import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapabilityPolicy.Reason;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialReference.RemoteIpVerificationStatus;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Credential reference/capability 与 permission/IP fail-closed regression。 */
class ScopedCredentialCapabilityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-13T02:00:00Z");
    private final ScopedCredentialCapabilityPolicy policy =
            new ScopedCredentialCapabilityPolicy(Duration.ofHours(1));

    @Test
    void shouldAllowOnlyFreshRemotelyVerifiedPrivateReadonlyDiagnostic() {
        assertEquals(ScopedCredentialCapabilityPolicy.Status.ALLOWED,
                policy.evaluate(reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC), NOW).status());

        assertDenied(reference(ScopedCredentialCapability.FUTURE_MICRO_LIVE), Reason.FUTURE_CAPABILITY_NOT_CALLABLE);
        assertDenied(reference(ScopedCredentialCapability.FORBIDDEN), Reason.CAPABILITY_FORBIDDEN);
    }

    @Test
    void shouldRejectTradeWithdrawInactiveMismatchedOrUnverifiableFacts() {
        assertDenied(withPermission("TRADE", false), Reason.REMOTE_PERMISSION_NOT_READ_ONLY);
        assertDenied(withPermission("READ_ONLY", true), Reason.REMOTE_PERMISSION_NOT_READ_ONLY);
        ScopedCredentialReference inactive = copy(
                reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC),
                "DISABLED", false, "READ_ONLY", false, true,
                RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED, NOW.minusSeconds(60));
        assertDenied(inactive, Reason.CREDENTIAL_NOT_ACTIVE);
        ScopedCredentialReference mismatch = copy(
                reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC),
                "ACTIVE", true, "READ_ONLY", false, true,
                RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_MISMATCH, NOW.minusSeconds(60));
        assertDenied(mismatch, Reason.REMOTE_PERMISSION_IP_NOT_VERIFIED);
        ScopedCredentialReference notVerifiable = copy(
                reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC),
                "ACTIVE", true, "READ_ONLY", false, true,
                RemoteIpVerificationStatus.NOT_VERIFIABLE, NOW.minusSeconds(60));
        assertDenied(notVerifiable, Reason.REMOTE_PERMISSION_IP_NOT_VERIFIABLE);
        ScopedCredentialReference stale = copy(
                reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC),
                "ACTIVE", true, "READ_ONLY", false, true,
                RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED, NOW.minus(Duration.ofHours(2)));
        assertDenied(stale, Reason.PERMISSION_PROBE_STALE);
    }

    @Test
    void shouldRejectWrongVenueInvalidReferenceRevokedAndExpiredWithoutLeakingRawScope() {
        ScopedCredentialReference valid = reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC);
        ScopedCredentialReference wrongVenue = new ScopedCredentialReference(
                valid.ownerId(), valid.exchangeAccountId(), valid.credentialReference(), "BINANCE_SPOT",
                valid.credentialType(), valid.capability(), valid.lifecycleStatus(), valid.active(),
                valid.verificationStatus(), valid.permissionProbeStatus(), valid.permissionScopeDigest(),
                valid.remotelyVerifiedReadOnly(), valid.withdrawEnabled(), valid.ipAllowlistConfigured(),
                valid.remoteIpVerificationStatus(), valid.lastPermissionProbeAt(), null, null);
        assertDenied(wrongVenue, Reason.CREDENTIAL_SCOPE_MISMATCH);
        ScopedCredentialReference invalid = new ScopedCredentialReference(
                valid.ownerId(), valid.exchangeAccountId(), 0, valid.venue(), valid.credentialType(),
                valid.capability(), valid.lifecycleStatus(), valid.active(), valid.verificationStatus(),
                valid.permissionProbeStatus(), valid.permissionScopeDigest(), valid.remotelyVerifiedReadOnly(),
                valid.withdrawEnabled(), valid.ipAllowlistConfigured(), valid.remoteIpVerificationStatus(),
                valid.lastPermissionProbeAt(), null, null);
        assertDenied(invalid, Reason.CREDENTIAL_REFERENCE_INVALID);
        assertDenied(copy(valid, "REVOKED", false, "READ_ONLY", false, true,
                RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED, NOW.minusSeconds(60)),
                Reason.CREDENTIAL_NOT_ACTIVE);
        assertDenied(copy(valid, "EXPIRED", false, "READ_ONLY", false, true,
                RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED, NOW.minusSeconds(60)),
                Reason.CREDENTIAL_NOT_ACTIVE);

        String rendered = valid.toString();
        assertFalse(rendered.contains("READ_ONLY"));
        assertFalse(rendered.toLowerCase().contains("apikey"));
        assertFalse(rendered.toLowerCase().contains("passphrase"));
        assertFalse(rendered.toLowerCase().contains("secret="));
    }

    @Test
    void shouldRejectConflictingActiveButRevokedOrRotatedFacts() {
        ScopedCredentialReference valid = reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC);
        ScopedCredentialReference revoked = copyWithLifecycleTimestamps(
                valid, NOW.minusSeconds(30), null);
        ScopedCredentialReference rotated = copyWithLifecycleTimestamps(
                valid, null, NOW.minusSeconds(30));

        assertDenied(revoked, Reason.CREDENTIAL_REVOKED_OR_ROTATED);
        assertDenied(rotated, Reason.CREDENTIAL_REVOKED_OR_ROTATED);
    }

    private void assertDenied(ScopedCredentialReference reference, Reason reason) {
        assertEquals(reason, policy.evaluate(reference, NOW).reason());
    }

    private ScopedCredentialReference withPermission(String permission, boolean withdraw) {
        return copy(reference(ScopedCredentialCapability.PRIVATE_READONLY_DIAGNOSTIC),
                "ACTIVE", true, permission, withdraw, true,
                RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED, NOW.minusSeconds(60));
    }

    static ScopedCredentialReference reference(ScopedCredentialCapability capability) {
        return new ScopedCredentialReference(
                7, 11, 13, "OKX_SPOT", "OKX_API_V5", capability,
                "ACTIVE", true, "VERIFIED", "SUCCEEDED",
                ScopedCredentialReference.digestPermissionScope("READ_ONLY"), true, false,
                true, RemoteIpVerificationStatus.REMOTE_PERMISSION_IP_VERIFIED, NOW.minusSeconds(60), null, null);
    }

    private static ScopedCredentialReference copy(
            ScopedCredentialReference source,
            String lifecycle,
            boolean active,
            String permission,
            boolean withdraw,
            boolean ipConfigured,
            RemoteIpVerificationStatus ipStatus,
            Instant probedAt
    ) {
        return new ScopedCredentialReference(
                source.ownerId(), source.exchangeAccountId(), source.credentialReference(), source.venue(),
                source.credentialType(), source.capability(), lifecycle, active, source.verificationStatus(),
                source.permissionProbeStatus(), ScopedCredentialReference.digestPermissionScope(permission),
                "READ_ONLY".equals(permission), withdraw, ipConfigured, ipStatus, probedAt,
                "REVOKED".equals(lifecycle) ? NOW.minusSeconds(30) : null,
                source.rotatedAt());
    }

    private static ScopedCredentialReference copyWithLifecycleTimestamps(
            ScopedCredentialReference source,
            Instant revokedAt,
            Instant rotatedAt
    ) {
        return new ScopedCredentialReference(
                source.ownerId(), source.exchangeAccountId(), source.credentialReference(), source.venue(),
                source.credentialType(), source.capability(), source.lifecycleStatus(), source.active(),
                source.verificationStatus(), source.permissionProbeStatus(), source.permissionScopeDigest(),
                source.remotelyVerifiedReadOnly(), source.withdrawEnabled(), source.ipAllowlistConfigured(),
                source.remoteIpVerificationStatus(), source.lastPermissionProbeAt(), revokedAt, rotatedAt);
    }
}

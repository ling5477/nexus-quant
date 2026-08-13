package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** LIVE control-plane aggregate；不拥有 order、trade、position、ledger 或 risk decision。 */
public record LiveSession(
        UUID id,
        long ownerId,
        long exchangeAccountId,
        String venue,
        String strategyReleaseId,
        String releaseDigest,
        long releaseAdmissionRevision,
        UUID riskLimitSetId,
        String riskLimitSetDigest,
        long credentialReference,
        List<String> symbolAllowlist,
        BigDecimal capitalCap,
        Instant executionWindowStart,
        Instant executionWindowEnd,
        LiveSessionState state,
        long version,
        String approvalScopeHash,
        long nextEventSequence,
        long createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String VENUE = "OKX_SPOT";
    public static final String APPROVAL_SCOPE_SCHEMA = "approval-scope.v1";

    public LiveSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(strategyReleaseId, "strategyReleaseId must not be null");
        requireDigest(releaseDigest, "releaseDigest");
        Objects.requireNonNull(riskLimitSetId, "riskLimitSetId must not be null");
        requireDigest(riskLimitSetDigest, "riskLimitSetDigest");
        requireDigest(approvalScopeHash, "approvalScopeHash");
        Objects.requireNonNull(state, "state must not be null");
        capitalCap = CanonicalDigestSupport.money(capitalCap, "capitalCap");
        symbolAllowlist = normalizeSymbols(symbolAllowlist);
        Objects.requireNonNull(executionWindowStart, "executionWindowStart must not be null");
        Objects.requireNonNull(executionWindowEnd, "executionWindowEnd must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        require(ownerId > 0 && exchangeAccountId > 0 && credentialReference > 0 && createdBy > 0,
                "identity references must be positive");
        require(VENUE.equals(venue), "venue must be OKX_SPOT");
        require(releaseAdmissionRevision > 0, "releaseAdmissionRevision must be positive");
        require(version > 0 && nextEventSequence > 0, "versions must be positive");
        require(capitalCap.signum() > 0, "capitalCap must be positive");
        require(executionWindowEnd.isAfter(executionWindowStart), "execution window must be non-empty");
    }

    public static LiveSession create(
            UUID id,
            long ownerId,
            long exchangeAccountId,
            String strategyReleaseId,
            String releaseDigest,
            long releaseAdmissionRevision,
            UUID riskLimitSetId,
            String riskLimitSetDigest,
            long credentialReference,
            List<String> symbolAllowlist,
            BigDecimal capitalCap,
            Instant executionWindowStart,
            Instant executionWindowEnd,
            long createdBy,
            Instant now
    ) {
        LiveSession draft = new LiveSession(
                id, ownerId, exchangeAccountId, VENUE, strategyReleaseId, releaseDigest,
                releaseAdmissionRevision, riskLimitSetId, riskLimitSetDigest, credentialReference,
                symbolAllowlist, capitalCap, executionWindowStart, executionWindowEnd,
                LiveSessionState.APPROVAL_PENDING, 1, "0".repeat(64), 1, createdBy, now, now
        );
        return draft.withScopeHash(LiveSessionApprovalScopeEncoder.digest(draft));
    }

    /** 人工审批持久化只允许批准或拒绝两个状态结果。 */
    public LiveSession recordApprovalDecision(LiveSessionState target, Instant now) {
        if (state != LiveSessionState.APPROVAL_PENDING
                || (target != LiveSessionState.APPROVED && target != LiveSessionState.REJECTED)) {
            throw new LiveControlException(
                    "LIVE_SESSION_APPROVAL_TRANSITION_REQUIRED",
                    "approval persistence accepts only approval or rejection transitions"
            );
        }
        return new LiveSession(
                id, ownerId, exchangeAccountId, venue, strategyReleaseId, releaseDigest,
                releaseAdmissionRevision, riskLimitSetId, riskLimitSetDigest, credentialReference,
                symbolAllowlist, capitalCap, executionWindowStart, executionWindowEnd, target,
                Math.addExact(version, 1), approvalScopeHash, nextEventSequence,
                createdBy, createdAt, Objects.requireNonNull(now, "now must not be null")
        );
    }

    /** scope 只可在待审批态变更；新 hash 会让旧 approval 自动失配。 */
    public LiveSession changeScope(
            UUID newRiskLimitSetId,
            String newRiskLimitSetDigest,
            List<String> newSymbols,
            BigDecimal newCapitalCap,
            Instant newWindowStart,
            Instant newWindowEnd,
            Instant now
    ) {
        if (state != LiveSessionState.APPROVAL_PENDING) {
            throw new LiveControlException("LIVE_SESSION_SCOPE_LOCKED", "scope can change only while approval is pending");
        }
        LiveSession changed = new LiveSession(
                id, ownerId, exchangeAccountId, venue, strategyReleaseId, releaseDigest,
                releaseAdmissionRevision, newRiskLimitSetId, newRiskLimitSetDigest, credentialReference,
                newSymbols, newCapitalCap, newWindowStart, newWindowEnd, state,
                Math.addExact(version, 1), "0".repeat(64), nextEventSequence,
                createdBy, createdAt, Objects.requireNonNull(now, "now must not be null")
        );
        String newHash = LiveSessionApprovalScopeEncoder.digest(changed);
        if (newHash.equals(approvalScopeHash)) {
            throw new LiveControlException("LIVE_SESSION_SCOPE_UNCHANGED", "scope mutation must change its digest");
        }
        return changed.withScopeHash(newHash);
    }

    public void requireWithinRiskLimit(RiskLimitSet riskLimitSet) {
        Objects.requireNonNull(riskLimitSet, "riskLimitSet must not be null");
        require(riskLimitSet.id().equals(riskLimitSetId), "risk limit set identity mismatch");
        require(riskLimitSet.canonicalDigest().equals(riskLimitSetDigest), "risk limit set digest mismatch");
        require(capitalCap.compareTo(riskLimitSet.capitalCap()) <= 0, "session capital exceeds risk limit");
        require(!Duration.between(executionWindowStart, executionWindowEnd)
                        .minusSeconds(riskLimitSet.maxSessionDurationSeconds()).isPositive(),
                "execution window exceeds risk limit");
        require(riskLimitSet.symbolAllowlist().containsAll(symbolAllowlist),
                "session symbol is outside risk limit allowlist");
    }

    public boolean hasCanonicalApprovalScopeHash() {
        return approvalScopeHash.equals(LiveSessionApprovalScopeEncoder.digest(this));
    }

    private LiveSession withScopeHash(String hash) {
        return new LiveSession(
                id, ownerId, exchangeAccountId, venue, strategyReleaseId, releaseDigest,
                releaseAdmissionRevision, riskLimitSetId, riskLimitSetDigest, credentialReference,
                symbolAllowlist, capitalCap, executionWindowStart, executionWindowEnd, state,
                version, hash, nextEventSequence, createdBy, createdAt, updatedAt
        );
    }

    private static List<String> normalizeSymbols(List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("symbolAllowlist must not be null");
        }
        List<String> normalized = values.stream()
                .map(value -> Objects.requireNonNull(value, "symbol must not be null").trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        require(normalized.size() >= 1 && normalized.size() <= 2, "one or two unique symbols are required");
        require(normalized.stream().allMatch(value -> value.matches("[A-Z0-9]{2,20}-USDT")),
                "invalid OKX Spot symbol");
        return normalized;
    }

    private static void requireDigest(String value, String name) {
        require(value != null && value.matches("[0-9a-f]{64}"), name + " must be lowercase SHA-256");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

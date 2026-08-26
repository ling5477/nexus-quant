package com.guidinglight.nexusquant.livecontrol.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Exact pilot binding 的 durable、短时、一次性执行租约。
 *
 * <p>租约不是通用 LIVE 权限。它只能约束一个 binding、一个 PLACE intent 与可选的一个
 * CANCEL intent；任何缺失、过期、消费或 digest 漂移都必须在 provider 调用前阻断。</p>
 */
public record PilotExecutionLease(
        UUID id,
        UUID liveSessionId,
        UUID operatorPilotAuthorityId,
        UUID bindingId,
        String bindingDigest,
        Status status,
        BigDecimal maxNotional,
        Instant validFrom,
        Instant expiresAt,
        Instant consumedAt,
        Instant closedAt,
        long createdBy,
        long version,
        Instant createdAt,
        Instant updatedAt,
        UUID predecessorLeaseId,
        UUID recoveryDecisionId,
        int replacementOrdinal,
        String replacementReason
) {
    public static final Duration MAXIMUM_LIFETIME = Duration.ofMinutes(5);

    /**
     * V42 source-compatible constructor；STRATEGY lease 不绑定 operator authority。
     */
    public PilotExecutionLease(
            UUID id,
            UUID liveSessionId,
            UUID bindingId,
            String bindingDigest,
            Status status,
            BigDecimal maxNotional,
            Instant validFrom,
            Instant expiresAt,
            Instant consumedAt,
            Instant closedAt,
            long createdBy,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, liveSessionId, null, bindingId, bindingDigest, status, maxNotional, validFrom,
                expiresAt, consumedAt, closedAt, createdBy, version, createdAt, updatedAt,
                null, null, 0, null);
    }

    public PilotExecutionLease {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(liveSessionId, "liveSessionId must not be null");
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        ExactPilotBinding.requireDigest(bindingDigest, "bindingDigest");
        Objects.requireNonNull(status, "status must not be null");
        maxNotional = CanonicalDigestSupport.money(maxNotional, "maxNotional");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        ExactPilotBinding.require(createdBy > 0 && version > 0, "lease identity/version must be positive");
        ExactPilotBinding.require(maxNotional.signum() > 0, "maxNotional must be positive");
        ExactPilotBinding.require(expiresAt.isAfter(validFrom), "lease window must be non-empty");
        ExactPilotBinding.require(!Duration.between(validFrom, expiresAt).minus(MAXIMUM_LIFETIME).isPositive(),
                "lease lifetime exceeds the hard upper bound");
        boolean open = status == Status.CREATED || status == Status.ACTIVE;
        boolean consumed = status == Status.CONSUMED;
        boolean terminal = status == Status.EXPIRED || status == Status.CLOSED || status == Status.FAILED;
        ExactPilotBinding.require(!open || consumedAt == null && closedAt == null,
                "open lease cannot have terminal timestamps");
        ExactPilotBinding.require(!consumed || consumedAt != null && closedAt == null,
                "consumed lease requires consumedAt only");
        ExactPilotBinding.require(!terminal || closedAt != null, "terminal lease requires closedAt");
        boolean original = replacementOrdinal == 0 && predecessorLeaseId == null
                && recoveryDecisionId == null && replacementReason == null;
        boolean replacement = replacementOrdinal == 1 && predecessorLeaseId != null
                && recoveryDecisionId != null
                && "PRE_PLACE_ZERO_INTENT_FAILURE".equals(replacementReason);
        ExactPilotBinding.require(original || replacement, "lease replacement lineage is invalid");
    }

    public static PilotExecutionLease created(
            UUID id,
            ExactPilotBinding binding,
            BigDecimal maxNotional,
            long createdBy,
            Instant validFrom,
            Instant expiresAt
    ) {
        Objects.requireNonNull(binding, "binding must not be null");
        ExactPilotBinding.require(binding.hasCanonicalDigest(), "binding digest must be canonical");
        ExactPilotBinding.require(binding.account().ownerId() == createdBy, "lease creator must own binding");
        ExactPilotBinding.require(binding.order().notional().compareTo(maxNotional) <= 0,
                "binding notional exceeds operator maximum");
        return new PilotExecutionLease(
                id, binding.sessionId(), binding.operatorPilotAuthority() == null
                ? null : binding.operatorPilotAuthority().authorityId(),
                binding.id(), binding.bindingDigest(), Status.CREATED,
                maxNotional, validFrom, expiresAt, null, null, createdBy, 1, validFrom, validFrom,
                null, null, 0, null);
    }

    public static PilotExecutionLease createdReplacement(
            UUID id,
            ExactPilotBinding binding,
            BigDecimal maxNotional,
            long createdBy,
            Instant validFrom,
            Instant expiresAt,
            UUID predecessorLeaseId,
            UUID recoveryDecisionId
    ) {
        PilotExecutionLease original = created(
                id, binding, maxNotional, createdBy, validFrom, expiresAt);
        return new PilotExecutionLease(
                original.id(), original.liveSessionId(), original.operatorPilotAuthorityId(),
                original.bindingId(), original.bindingDigest(), original.status(), original.maxNotional(),
                original.validFrom(), original.expiresAt(), null, null, original.createdBy(),
                original.version(), original.createdAt(), original.updatedAt(), predecessorLeaseId,
                recoveryDecisionId, 1, "PRE_PLACE_ZERO_INTENT_FAILURE");
    }

    public boolean activeAt(Instant decisionAt) {
        return status == Status.ACTIVE && !decisionAt.isBefore(validFrom) && decisionAt.isBefore(expiresAt);
    }

    public enum Status {
        CREATED,
        ACTIVE,
        CONSUMED,
        EXPIRED,
        CLOSED,
        FAILED
    }
}

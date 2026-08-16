package com.guidinglight.nexusquant.livecontrol.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 不可变人工审批事实。 */
public record OperatorApproval(
        UUID id,
        UUID sessionId,
        String scopeSchemaVersion,
        UUID pilotScopeId,
        String scopeHash,
        String releaseDigest,
        String riskLimitSetDigest,
        long approverId,
        String approverRole,
        Decision decision,
        String reason,
        Instant approvedAt,
        Instant expiresAt
) {
    public static final String REQUIRED_ROLE = "LIVE_APPROVER";
    public static final String LEGACY_SCOPE_SCHEMA = "approval-scope.v1";
    public static final String PILOT_SCOPE_SCHEMA = "pilot-scope.v1";

    /** V39 source-compatible constructor；V40 JDBC 会显式持久化真实 legacy schema label。 */
    public OperatorApproval(
            UUID id,
            UUID sessionId,
            String scopeHash,
            String releaseDigest,
            String riskLimitSetDigest,
            long approverId,
            String approverRole,
            Decision decision,
            String reason,
            Instant approvedAt,
            Instant expiresAt
    ) {
        this(id, sessionId, LEGACY_SCOPE_SCHEMA, null, scopeHash, releaseDigest, riskLimitSetDigest,
                approverId, approverRole, decision, reason, approvedAt, expiresAt);
    }

    public OperatorApproval {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        require((LEGACY_SCOPE_SCHEMA.equals(scopeSchemaVersion) && pilotScopeId == null)
                        || (PILOT_SCOPE_SCHEMA.equals(scopeSchemaVersion) && pilotScopeId != null),
                "approval scope schema and pilot scope identity are inconsistent");
        requireDigest(scopeHash, "scopeHash");
        requireDigest(releaseDigest, "releaseDigest");
        requireDigest(riskLimitSetDigest, "riskLimitSetDigest");
        require(approverId > 0, "approverId must be positive");
        require(REQUIRED_ROLE.equals(approverRole), "approverRole must be LIVE_APPROVER");
        Objects.requireNonNull(decision, "decision must not be null");
        require(reason != null && !reason.isBlank() && reason.length() <= 1024, "reason is invalid");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        require(expiresAt.isAfter(approvedAt), "approval expiry must follow decision time");
    }

    public boolean validFor(LiveSession session, Instant now) {
        return LEGACY_SCOPE_SCHEMA.equals(scopeSchemaVersion)
                && decision == Decision.APPROVED
                && !now.isBefore(approvedAt)
                && now.isBefore(expiresAt)
                && session.id().equals(sessionId)
                && session.approvalScopeHash().equals(scopeHash)
                && session.releaseDigest().equals(releaseDigest)
                && session.riskLimitSetDigest().equals(riskLimitSetDigest)
                && !expiresAt.isAfter(session.executionWindowEnd());
    }

    public boolean validFor(PilotScopeBinding scope, LiveSession session, Instant now) {
        return PILOT_SCOPE_SCHEMA.equals(scopeSchemaVersion)
                && decision == Decision.APPROVED
                && !now.isBefore(approvedAt)
                && now.isBefore(expiresAt)
                && session.id().equals(sessionId)
                && scope.id().equals(pilotScopeId)
                && scope.sessionId().equals(sessionId)
                && scope.pilotScopeHash().equals(scopeHash)
                && session.releaseDigest().equals(releaseDigest)
                && session.riskLimitSetDigest().equals(riskLimitSetDigest)
                && !expiresAt.isAfter(session.executionWindowEnd());
    }

    private static void requireDigest(String value, String name) {
        require(value != null && value.matches("[0-9a-f]{64}"), name + " must be lowercase SHA-256");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public enum Decision {
        APPROVED,
        REJECTED
    }
}

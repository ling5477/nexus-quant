package com.guidinglight.nexusquant.livecontrol.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LiveSessionDomainTest {

    private final LiveSessionStateMachine stateMachine = new LiveSessionStateMachine();

    @Test
    void shouldAllowOnlyContractTransitionsAndNeverRestoreTerminalState() {
        assertEquals(LiveSessionState.APPROVED,
                stateMachine.transition(LiveSessionState.APPROVAL_PENDING, LiveSessionCommand.APPROVE));
        assertEquals(LiveSessionState.KILLED,
                stateMachine.transition(LiveSessionState.LIVE_ACTIVE, LiveSessionCommand.KILL));
        assertThrows(LiveControlException.class,
                () -> stateMachine.transition(LiveSessionState.APPROVAL_PENDING, LiveSessionCommand.ACTIVATE));
        assertThrows(LiveControlException.class,
                () -> stateMachine.transition(LiveSessionState.KILLED, LiveSessionCommand.RESUME));
        assertThrows(LiveControlException.class,
                () -> stateMachine.transition(LiveSessionState.RECONCILIATION_BLOCKED, LiveSessionCommand.RESUME));
    }

    @Test
    void shouldInvalidateApprovalWhenScopeChanges() {
        LiveSession session = session();
        OperatorApproval approval = approval(session, Instant.parse("2026-08-12T00:30:00Z"));

        LiveSession changed = session.changeScope(
                UUID.fromString("20000000-0000-0000-0000-000000000099"),
                "c".repeat(64), List.of("ETH-USDT"), new BigDecimal("20"),
                session.executionWindowStart(), session.executionWindowEnd(),
                Instant.parse("2026-08-12T00:05:00Z")
        );

        assertNotEquals(session.approvalScopeHash(), changed.approvalScopeHash());
        assertFalse(approval.validFor(changed, Instant.parse("2026-08-12T00:10:00Z")));
    }

    @Test
    void shouldChangeApprovalDigestForEverySecuritySensitiveScopeField() {
        LiveSession baseline = session();

        List<LiveSession> changed = List.of(
                copy(baseline, baseline.ownerId() + 1, baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId() + 1, baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        "release-2", baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), "c".repeat(64), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision() + 1,
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        UUID.randomUUID(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), "d".repeat(64), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference() + 1,
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        List.of("ETH-USDT"), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap().add(BigDecimal.ONE),
                        baseline.executionWindowStart(), baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(),
                        baseline.executionWindowStart().plusSeconds(1), baseline.executionWindowEnd()),
                copy(baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                        baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                        baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                        baseline.symbolAllowlist(), baseline.capitalCap(), baseline.executionWindowStart(),
                        baseline.executionWindowEnd().minusSeconds(1))
        );

        assertTrue(changed.stream().allMatch(value -> !baseline.approvalScopeHash()
                .equals(LiveSessionApprovalScopeEncoder.digest(value))));
    }

    @Test
    void shouldCanonicalizeUtcTimestampsAndRejectSubMicrosecondPrecision() {
        LiveSession baseline = session();
        LiveSession sameInstant = copy(
                baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                baseline.symbolAllowlist(), baseline.capitalCap(),
                Instant.parse("2026-08-12T08:00:00+08:00"), Instant.parse("2026-08-12T09:00:00+08:00"));
        assertEquals(baseline.approvalScopeHash(), LiveSessionApprovalScopeEncoder.digest(sameInstant));

        LiveSession nanosecond = copy(
                baseline, baseline.ownerId(), baseline.exchangeAccountId(), baseline.venue(),
                baseline.strategyReleaseId(), baseline.releaseDigest(), baseline.releaseAdmissionRevision(),
                baseline.riskLimitSetId(), baseline.riskLimitSetDigest(), baseline.credentialReference(),
                baseline.symbolAllowlist(), baseline.capitalCap(),
                baseline.executionWindowStart().plusNanos(1), baseline.executionWindowEnd());
        assertThrows(IllegalArgumentException.class, () -> LiveSessionApprovalScopeEncoder.digest(nanosecond));
    }

    @Test
    void shouldEncodeControlCharactersIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertEquals("\"\\u0001\"", CanonicalDigestSupport.quote("\u0001"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void shouldFailClosedForExpiredOrMismatchedApproval() {
        LiveSession session = session();
        OperatorApproval approval = approval(session, Instant.parse("2026-08-12T00:20:00Z"));

        assertTrue(approval.validFor(session, Instant.parse("2026-08-12T00:10:00Z")));
        assertFalse(approval.validFor(session, Instant.parse("2026-08-12T00:20:00Z")));
        assertThrows(IllegalArgumentException.class, () -> new OperatorApproval(
                UUID.randomUUID(), session.id(), session.approvalScopeHash(), session.releaseDigest(),
                session.riskLimitSetDigest(), 9, "OPERATOR", OperatorApproval.Decision.APPROVED,
                "approved", Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T00:20:00Z")
        ));
    }

    static LiveSession session() {
        return LiveSession.create(
                UUID.fromString("30000000-0000-0000-0000-000000000001"), 7, 11,
                "release-1", "a".repeat(64), 1,
                UUID.fromString("20000000-0000-0000-0000-000000000001"), "b".repeat(64), 13,
                List.of("BTC-USDT"), new BigDecimal("25"),
                Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T01:00:00Z"),
                7, Instant.parse("2026-08-11T23:59:00Z")
        );
    }

    private static LiveSession copy(
            LiveSession source,
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
            Instant executionWindowEnd
    ) {
        return new LiveSession(
                source.id(), ownerId, exchangeAccountId, venue, strategyReleaseId, releaseDigest,
                releaseAdmissionRevision, riskLimitSetId, riskLimitSetDigest, credentialReference,
                symbolAllowlist, capitalCap, executionWindowStart, executionWindowEnd, source.state(),
                source.version(), source.approvalScopeHash(), source.nextEventSequence(), source.createdBy(),
                source.createdAt(), source.updatedAt()
        );
    }

    private static OperatorApproval approval(LiveSession session, Instant expiresAt) {
        return new OperatorApproval(
                UUID.randomUUID(), session.id(), session.approvalScopeHash(), session.releaseDigest(),
                session.riskLimitSetDigest(), 9, OperatorApproval.REQUIRED_ROLE,
                OperatorApproval.Decision.APPROVED, "approved",
                Instant.parse("2026-08-12T00:00:00Z"), expiresAt
        );
    }
}

package com.guidinglight.nexusquant.strategyrelease.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategyrelease.preparation.StrategyReleaseServicePrototype.CreateCommand;
import com.guidinglight.nexusquant.strategyrelease.preparation.StrategyReleaseServicePrototype.StateCommand;
import com.guidinglight.nexusquant.strategyrelease.preparation.StrategyReleaseServicePrototype.VerifyCommand;
import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.FindingCode;
import com.guidinglight.nexusquant.strategyrelease.preparation.TrustedRootArtifactVerifierPrototype.Status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PRE-GATEX Strategy Release aggregate、幂等、乐观锁和 append-only event test-only 原型。
 */
class StrategyReleaseAggregateIdempotencyPrototypeTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-23T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    private static final String DIGEST = "a".repeat(64);
    private static final String OTHER_DIGEST = "b".repeat(64);

    @Test
    void shouldCreateReleaseWithCompleteImmutableAnchorsAndSafetyFlags() {
        Fixture fixture = fixture();

        StrategyReleaseCommandResultPrototype result = fixture.create("create-001");
        StrategyReleaseAggregatePrototype aggregate = result.aggregate();

        assertTrue(result.accepted());
        assertEquals("release-001", aggregate.releaseId());
        assertEquals("publish-001", aggregate.publishId());
        assertEquals("sv-001", aggregate.strategyVersionId());
        assertEquals("dataset-001", aggregate.datasetId());
        assertEquals("evaluation-001", aggregate.evaluationId());
        assertEquals(StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA, aggregate.manifestSchemaVersion());
        assertEquals(DIGEST, aggregate.artifactDigest());
        assertEquals(StrategyReleaseState.DRAFT, aggregate.state());
        assertEquals(0, aggregate.version());
        assertEquals(FIXED_INSTANT, aggregate.createdAt());
        assertBoundaryFlags(result);

        List<StrategyReleaseEventPrototype> events = fixture.repository().findEvents("release-001");
        assertEquals(1, events.size());
        assertEquals("RELEASE_CREATED", events.getFirst().eventType());
        assertEquals(-1, events.getFirst().versionBefore());
        assertEquals(0, events.getFirst().versionAfter());
    }

    @Test
    void shouldReplaySameCreateActionWithoutSecondReleaseEventOrVersion() {
        Fixture fixture = fixture();

        StrategyReleaseCommandResultPrototype first = fixture.create("create-replay");
        StrategyReleaseCommandResultPrototype replay = fixture.create("create-replay");

        assertSame(first, replay);
        assertEquals(1, fixture.repository().releaseCount());
        assertEquals(1, fixture.repository().findEvents("release-001").size());
        assertEquals(0, replay.aggregate().version());
    }

    @Test
    void shouldRejectSameActionIdWithDifferentRequestFingerprint() {
        Fixture fixture = fixture();
        fixture.create("create-conflict");

        StrategyReleaseCommandResultPrototype conflict = fixture.service().create(new CreateCommand(
                "create-conflict",
                "release-001",
                "publish-001",
                "sv-001",
                "dataset-001",
                "evaluation-001",
                StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA,
                OTHER_DIGEST,
                0
        ));

        assertFalse(conflict.accepted());
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.reasonCode());
        assertEquals(DIGEST, fixture.repository().findById("release-001").orElseThrow().artifactDigest());
        assertEquals(1, fixture.repository().findEvents("release-001").size());
    }

    @Test
    void shouldReuseExactBusinessAnchorAndRejectConflictingAnchorPayload() {
        Fixture fixture = fixture();
        StrategyReleaseCommandResultPrototype first = fixture.create("create-business-first");

        StrategyReleaseCommandResultPrototype duplicate = fixture.service().create(new CreateCommand(
                "create-business-duplicate",
                "release-001",
                "publish-001",
                "sv-001",
                "dataset-001",
                "evaluation-001",
                StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA,
                DIGEST,
                0
        ));
        StrategyReleaseCommandResultPrototype conflict = fixture.service().create(new CreateCommand(
                "create-business-conflict",
                "release-002",
                "publish-001",
                "sv-002",
                "dataset-001",
                "evaluation-001",
                StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA,
                DIGEST,
                0
        ));

        assertTrue(duplicate.accepted());
        assertSame(first.aggregate(), duplicate.aggregate());
        assertFalse(conflict.accepted());
        assertEquals("BUSINESS_IDENTITY_CONFLICT", conflict.reasonCode());
        assertEquals(1, fixture.repository().releaseCount());
        assertEquals(1, fixture.repository().findEvents("release-001").size());
    }

    @Test
    void shouldFailClosedForUnknownRejectedMismatchAndBlockingVerification() {
        assertVerificationRejected(
                verification(Status.UNKNOWN, null, 0, List.of()),
                "VERIFICATION_UNKNOWN"
        );
        assertVerificationRejected(
                verification(Status.REJECTED, null, 0, List.of(FindingCode.DIGEST_MISMATCH)),
                "VERIFICATION_REJECTED"
        );
        assertVerificationRejected(
                verification(Status.VERIFIED, OTHER_DIGEST, 128, List.of()),
                "ARTIFACT_DIGEST_MISMATCH"
        );
        assertVerificationRejected(
                verification(Status.VERIFIED, DIGEST, 128, List.of(FindingCode.VERIFICATION_IO_FAILED)),
                "BLOCKING_VERIFICATION_FINDING"
        );
        assertVerificationRejected(
                verification(Status.VERIFIED, DIGEST, 0, List.of()),
                "VERIFIED_SIZE_INVALID"
        );
    }

    @Test
    void shouldRejectUnsupportedSchemaAndBlankArtifactDigest() {
        Fixture fixture = fixture();
        fixture.service().create(new CreateCommand(
                "create-unsupported-schema",
                "release-001",
                "publish-001",
                "sv-001",
                "dataset-001",
                "evaluation-001",
                "strategy-release-manifest.v999",
                DIGEST,
                0
        ));
        fixture.service().markCandidate(new StateCommand("candidate-unsupported", "release-001", 0));

        StrategyReleaseCommandResultPrototype result = fixture.service().verify(new VerifyCommand(
                "verify-unsupported",
                "release-001",
                1,
                verified()
        ));

        assertFalse(result.accepted());
        assertEquals("UNSUPPORTED_MANIFEST_SCHEMA", result.reasonCode());
        assertEquals(StrategyReleaseState.CANDIDATE, result.aggregate().state());
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().create(new CreateCommand(
                        "create-blank-digest",
                        "release-002",
                        "publish-002",
                        "sv-002",
                        "dataset-002",
                        "evaluation-002",
                        StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA,
                        " ",
                        0
                ))
        );
    }

    @Test
    void shouldCompleteDeclaredLifecycleWithoutStartingShadowOrAuthorizingLive() {
        Fixture fixture = fixture();
        fixture.create("create-lifecycle");
        StrategyReleaseCommandResultPrototype candidate =
                fixture.service().markCandidate(new StateCommand("candidate-lifecycle", "release-001", 0));
        StrategyReleaseCommandResultPrototype verified =
                fixture.service().verify(new VerifyCommand("verify-lifecycle", "release-001", 1, verified()));
        StrategyReleaseCommandResultPrototype published =
                fixture.service().publish(new StateCommand("publish-lifecycle", "release-001", 2));
        StrategyReleaseCommandResultPrototype retired =
                fixture.service().retire(new StateCommand("retire-lifecycle", "release-001", 3));

        assertEquals(StrategyReleaseState.CANDIDATE, candidate.aggregate().state());
        assertEquals(StrategyReleaseState.VERIFIED, verified.aggregate().state());
        assertEquals(StrategyReleaseState.PUBLISHED, published.aggregate().state());
        assertEquals(StrategyReleaseState.RETIRED, retired.aggregate().state());
        assertEquals(4, retired.aggregate().version());
        assertEquals(FIXED_INSTANT, verified.aggregate().verifiedAt());
        assertEquals(FIXED_INSTANT, published.aggregate().publishedAt());
        assertEquals(FIXED_INSTANT, retired.aggregate().retiredAt());
        assertBoundaryFlags(published);
    }

    @Test
    void shouldRejectPublishBeforeVerificationWithoutStateMutation() {
        Fixture fixture = fixture();
        fixture.create("create-publish-early");
        fixture.service().markCandidate(new StateCommand("candidate-publish-early", "release-001", 0));

        StrategyReleaseCommandResultPrototype result =
                fixture.service().publish(new StateCommand("publish-too-early", "release-001", 1));

        assertFalse(result.accepted());
        assertEquals("RELEASE_NOT_VERIFIED", result.reasonCode());
        assertEquals(StrategyReleaseState.CANDIDATE, result.aggregate().state());
        assertEquals(1, result.aggregate().version());
        assertEquals("ILLEGAL_TRANSITION_REJECTED", fixture.repository()
                .findEvents("release-001")
                .getLast()
                .eventType());
    }

    @Test
    void shouldDetectVersionConflictWithoutMutationOrEvent() {
        Fixture fixture = fixture();
        fixture.create("create-version");
        int eventCountBefore = fixture.repository().findEvents("release-001").size();

        StrategyReleaseCommandResultPrototype result =
                fixture.service().markCandidate(new StateCommand("candidate-stale", "release-001", 99));

        assertFalse(result.accepted());
        assertEquals("VERSION_CONFLICT", result.reasonCode());
        assertEquals(StrategyReleaseState.DRAFT, result.aggregate().state());
        assertEquals(0, result.aggregate().version());
        assertEquals(eventCountBefore, fixture.repository().findEvents("release-001").size());
    }

    @Test
    void shouldKeepRejectedAndRetiredStatesTerminal() {
        Fixture rejectedFixture = fixture();
        rejectedFixture.create("create-rejected");
        StrategyReleaseCommandResultPrototype rejected =
                rejectedFixture.service().reject(new StateCommand("reject", "release-001", 0));
        StrategyReleaseCommandResultPrototype reopenRejected =
                rejectedFixture.service().markCandidate(new StateCommand("reopen-rejected", "release-001", 1));

        assertEquals(StrategyReleaseState.REJECTED, rejected.aggregate().state());
        assertFalse(reopenRejected.accepted());
        assertEquals("RELEASE_TERMINAL_STATE_LOCKED", reopenRejected.reasonCode());
        assertEquals(StrategyReleaseState.REJECTED, reopenRejected.aggregate().state());

        Fixture retiredFixture = publishedFixture();
        StrategyReleaseCommandResultPrototype retired =
                retiredFixture.service().retire(new StateCommand("retire", "release-001", 3));
        StrategyReleaseCommandResultPrototype reopenRetired =
                retiredFixture.service().markCandidate(new StateCommand("reopen-retired", "release-001", 4));

        assertEquals(StrategyReleaseState.RETIRED, retired.aggregate().state());
        assertFalse(reopenRetired.accepted());
        assertEquals("RELEASE_TERMINAL_STATE_LOCKED", reopenRetired.reasonCode());
        assertEquals(StrategyReleaseState.RETIRED, reopenRetired.aggregate().state());
    }

    @Test
    void shouldReplaySuccessfulActionAfterLaterVersionChanges() {
        Fixture fixture = fixture();
        fixture.create("create-late-replay");
        StrategyReleaseCommandResultPrototype firstCandidate =
                fixture.service().markCandidate(new StateCommand("candidate-late-replay", "release-001", 0));
        fixture.service().verify(new VerifyCommand("verify-late-replay", "release-001", 1, verified()));

        StrategyReleaseCommandResultPrototype replay =
                fixture.service().markCandidate(new StateCommand("candidate-late-replay", "release-001", 0));

        assertSame(firstCandidate, replay);
        assertEquals(StrategyReleaseState.CANDIDATE, replay.aggregate().state());
        assertEquals(StrategyReleaseState.VERIFIED, fixture.repository().findById("release-001").orElseThrow().state());
        assertEquals(3, fixture.repository().findEvents("release-001").size());
    }

    @Test
    void shouldRejectActionIdReuseAcrossReleases() {
        Fixture fixture = fixture();
        fixture.create("shared-action");
        fixture.service().create(new CreateCommand(
                "create-second",
                "release-002",
                "publish-002",
                "sv-002",
                "dataset-002",
                "evaluation-002",
                StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA,
                OTHER_DIGEST,
                0
        ));

        StrategyReleaseCommandResultPrototype conflict = fixture.service().markCandidate(
                new StateCommand("shared-action", "release-002", 0)
        );

        assertFalse(conflict.accepted());
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.reasonCode());
        assertEquals(StrategyReleaseState.DRAFT, fixture.repository().findById("release-002").orElseThrow().state());
    }

    @Test
    void shouldTreatDifferentActionForCompletedPayloadAsIdempotentSuccess() {
        Fixture fixture = fixture();
        fixture.create("create-completed-action");
        StrategyReleaseCommandResultPrototype first =
                fixture.service().markCandidate(new StateCommand("candidate-first", "release-001", 0));
        int eventsBefore = fixture.repository().findEvents("release-001").size();

        StrategyReleaseCommandResultPrototype duplicate =
                fixture.service().markCandidate(new StateCommand("candidate-second", "release-001", 1));

        assertTrue(duplicate.accepted());
        assertNotSame(first, duplicate);
        assertEquals(1, duplicate.aggregate().version());
        assertEquals(eventsBefore, fixture.repository().findEvents("release-001").size());
    }

    @Test
    void shouldRequireIdenticalVerificationPayloadForDifferentActionOnCompletedState() {
        Fixture fixture = fixture();
        fixture.create("create-verified-payload");
        fixture.service().markCandidate(new StateCommand("candidate-verified-payload", "release-001", 0));
        fixture.service().verify(new VerifyCommand("verify-first-payload", "release-001", 1, verified()));
        int eventsBefore = fixture.repository().findEvents("release-001").size();

        StrategyReleaseCommandResultPrototype samePayload = fixture.service().verify(new VerifyCommand(
                "verify-same-payload",
                "release-001",
                2,
                verified()
        ));
        StrategyReleaseCommandResultPrototype differentPayload = fixture.service().verify(new VerifyCommand(
                "verify-different-payload",
                "release-001",
                2,
                verification(Status.VERIFIED, OTHER_DIGEST, 128, List.of())
        ));

        assertTrue(samePayload.accepted());
        assertFalse(differentPayload.accepted());
        assertEquals("STATE_PAYLOAD_CONFLICT", differentPayload.reasonCode());
        assertEquals(StrategyReleaseState.VERIFIED, differentPayload.aggregate().state());
        assertEquals(2, differentPayload.aggregate().version());
        assertEquals(eventsBefore, fixture.repository().findEvents("release-001").size());
    }

    @Test
    void shouldKeepEventsOrderedAppendOnlyAndFreeOfSensitiveFields() {
        Fixture fixture = publishedFixture();
        List<StrategyReleaseEventPrototype> events = fixture.repository().findEvents("release-001");

        assertEquals(
                List.of(
                        "RELEASE_CREATED",
                        "RELEASE_MARKED_CANDIDATE",
                        "ARTIFACT_VERIFIED",
                        "RELEASE_PUBLISHED"
                ),
                events.stream().map(StrategyReleaseEventPrototype::eventType).toList()
        );
        assertEquals(List.of(-1L, 0L, 1L, 2L), events.stream()
                .map(StrategyReleaseEventPrototype::versionBefore)
                .toList());
        assertEquals(List.of(0L, 1L, 2L, 3L), events.stream()
                .map(StrategyReleaseEventPrototype::versionAfter)
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> events.add(events.getFirst()));

        String serialized = events.toString().toLowerCase();
        List.of(
                "credential",
                "apikey",
                "secret",
                "passphrase",
                "token",
                "privatekey",
                "c:\\",
                "/users/",
                "account",
                "order",
                "balance"
        ).forEach(forbidden -> assertFalse(serialized.contains(forbidden), forbidden));
    }

    @Test
    void shouldUseFixedClockForAggregateAndEveryEvent() {
        Fixture fixture = publishedFixture();
        StrategyReleaseAggregatePrototype aggregate =
                fixture.repository().findById("release-001").orElseThrow();

        assertEquals(FIXED_INSTANT, aggregate.createdAt());
        assertEquals(FIXED_INSTANT, aggregate.updatedAt());
        assertEquals(FIXED_INSTANT, aggregate.verifiedAt());
        assertEquals(FIXED_INSTANT, aggregate.publishedAt());
        assertTrue(fixture.repository().findEvents("release-001").stream()
                .allMatch(event -> FIXED_INSTANT.equals(event.occurredAt())));
    }

    @Test
    void shouldReturnImmutableSnapshotsAndKeepRepositoryInstancesIndependent() {
        Fixture first = fixture();
        Fixture second = fixture();
        first.create("create-first-repository");

        assertEquals(1, first.repository().releaseCount());
        assertEquals(0, second.repository().releaseCount());
        List<StrategyReleaseEventPrototype> snapshot = first.repository().findEvents("release-001");
        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        first.service().markCandidate(new StateCommand("candidate-after-snapshot", "release-001", 0));
        assertEquals(1, snapshot.size());
        assertEquals(2, first.repository().findEvents("release-001").size());
    }

    private void assertVerificationRejected(
            ArtifactVerificationResultPrototype verification,
            String expectedReason
    ) {
        Fixture fixture = fixture();
        fixture.create("create-" + expectedReason);
        fixture.service().markCandidate(new StateCommand("candidate-" + expectedReason, "release-001", 0));
        int eventCountBefore = fixture.repository().findEvents("release-001").size();

        StrategyReleaseCommandResultPrototype result = fixture.service().verify(new VerifyCommand(
                "verify-" + expectedReason,
                "release-001",
                1,
                verification
        ));

        assertFalse(result.accepted());
        assertEquals(expectedReason, result.reasonCode());
        assertEquals(StrategyReleaseState.CANDIDATE, result.aggregate().state());
        assertEquals(1, result.aggregate().version());
        assertEquals(eventCountBefore + 1, fixture.repository().findEvents("release-001").size());
        assertEquals("ARTIFACT_VERIFICATION_REJECTED", fixture.repository()
                .findEvents("release-001")
                .getLast()
                .eventType());
    }

    private Fixture publishedFixture() {
        Fixture fixture = fixture();
        fixture.create("create-published-fixture");
        fixture.service().markCandidate(new StateCommand("candidate-published-fixture", "release-001", 0));
        fixture.service().verify(new VerifyCommand("verify-published-fixture", "release-001", 1, verified()));
        fixture.service().publish(new StateCommand("publish-published-fixture", "release-001", 2));
        return fixture;
    }

    private ArtifactVerificationResultPrototype verified() {
        return verification(Status.VERIFIED, DIGEST, 128, List.of());
    }

    private ArtifactVerificationResultPrototype verification(
            Status status,
            String digest,
            long verifiedSizeBytes,
            List<FindingCode> findingCodes
    ) {
        return new ArtifactVerificationResultPrototype(status, digest, verifiedSizeBytes, findingCodes);
    }

    private Fixture fixture() {
        StrategyReleaseRepositoryPrototype repository = new StrategyReleaseRepositoryPrototype();
        return new Fixture(repository, new StrategyReleaseServicePrototype(repository, FIXED_CLOCK));
    }

    private void assertBoundaryFlags(StrategyReleaseCommandResultPrototype result) {
        assertTrue(result.diagnosticOnly());
        assertTrue(result.notTradingAuthorization());
        assertTrue(result.liveDisabled());
    }

    private record Fixture(
            StrategyReleaseRepositoryPrototype repository,
            StrategyReleaseServicePrototype service
    ) {

        StrategyReleaseCommandResultPrototype create(String actionId) {
            return service.create(new CreateCommand(
                    actionId,
                    "release-001",
                    "publish-001",
                    "sv-001",
                    "dataset-001",
                    "evaluation-001",
                    StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA,
                    DIGEST,
                    0
            ));
        }
    }
}

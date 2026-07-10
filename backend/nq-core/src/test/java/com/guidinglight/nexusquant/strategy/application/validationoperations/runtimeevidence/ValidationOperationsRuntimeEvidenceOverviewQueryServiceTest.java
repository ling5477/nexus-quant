package com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ValidationOperationsRuntimeEvidenceOverviewQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T09:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldAggregateAllAvailableFreshSourcesInFixedOrderAndCallEachOnce() {
        Fixture fixture = fixture();
        fixture.respondWith(
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(50)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(40)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(30)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(20)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(10))
        );

        ValidationOperationsRuntimeEvidenceOverviewReadModel model = fixture.service.overview("trace-runtime-evidence");

        assertEquals(Availability.AVAILABLE, model.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.FRESH, model.evidenceMetadata().freshnessStatus());
        assertEquals(NOW.minusSeconds(10), model.evidenceMetadata().lastCalculatedAt());
        assertEquals(10L, model.evidenceMetadata().ageSeconds());
        assertNull(model.evidenceMetadata().staleAfterSeconds());
        assertNull(model.evidenceMetadata().staleReason());
        assertEquals(5, model.sourceCount());
        assertEquals(5, model.availableCount());
        assertEquals(5, model.freshCount());
        assertEquals(List.of(
                "SHADOW_VALIDATION_WORKFLOW",
                "SHADOW_RUNS",
                "CONSISTENCY_EVIDENCE",
                "INCIDENT_REPLAY_REVIEW",
                "EVALUATION_ARTIFACT_PREVIEW"
        ), model.sources().stream().map(ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource::sourceKey).toList());
        assertSafetyFlags(model.evidenceMetadata());
        fixture.assertEachSourceCalledOnce();
    }

    @Test
    void shouldFailClosedToPartialUnknownWhenOneSourceIsUnavailableOrPartial() {
        Fixture unavailableFixture = fixture();
        unavailableFixture.respondWith(
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(50)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(40)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(30)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(20)),
                metadata(Availability.UNAVAILABLE, FreshnessStatus.UNKNOWN, null)
        );

        ValidationOperationsRuntimeEvidenceOverviewReadModel unavailable = unavailableFixture.service.overview("trace-unavailable");
        assertEquals(Availability.PARTIAL, unavailable.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, unavailable.evidenceMetadata().freshnessStatus());
        assertEquals(1, unavailable.unavailableCount());
        assertEquals(1, unavailable.unknownFreshnessCount());

        Fixture partialFixture = fixture();
        partialFixture.respondWith(
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(50)),
                metadata(Availability.PARTIAL, FreshnessStatus.UNKNOWN, NOW.minusSeconds(40)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(30)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(20)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(10))
        );

        ValidationOperationsRuntimeEvidenceOverviewReadModel partial = partialFixture.service.overview("trace-partial");
        assertEquals(Availability.PARTIAL, partial.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, partial.evidenceMetadata().freshnessStatus());
        assertEquals(1, partial.partialCount());
    }

    @Test
    void shouldPreserveStaleAsAggregateStaleWithoutInventingThreshold() {
        Fixture fixture = fixture();
        fixture.respondWith(
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(50)),
                metadata(Availability.AVAILABLE, FreshnessStatus.STALE, NOW.minusSeconds(40)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(30)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(20)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(10))
        );

        ValidationOperationsRuntimeEvidenceOverviewReadModel model = fixture.service.overview("trace-stale");

        assertEquals(FreshnessStatus.STALE, model.evidenceMetadata().freshnessStatus());
        assertEquals("ONE_OR_MORE_EVIDENCE_SOURCES_STALE", model.evidenceMetadata().staleReason());
        assertEquals(1, model.staleCount());
        assertNull(model.evidenceMetadata().staleAfterSeconds());
    }

    @Test
    void shouldReturnUnavailableOrUnknownForUniformUnavailableOrUnknownSources() {
        Fixture unavailableFixture = fixture();
        unavailableFixture.respondWith(List.of(metadata(Availability.UNAVAILABLE, FreshnessStatus.UNKNOWN, null)));

        ValidationOperationsRuntimeEvidenceOverviewReadModel unavailable = unavailableFixture.service.overview("trace-all-unavailable");
        assertEquals(Availability.UNAVAILABLE, unavailable.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, unavailable.evidenceMetadata().freshnessStatus());
        assertEquals(5, unavailable.unavailableCount());
        assertNull(unavailable.evidenceMetadata().lastCalculatedAt());
        assertNull(unavailable.evidenceMetadata().ageSeconds());

        Fixture unknownFixture = fixture();
        unknownFixture.respondWith(List.of(metadata(Availability.UNKNOWN, FreshnessStatus.UNKNOWN, null)));

        ValidationOperationsRuntimeEvidenceOverviewReadModel unknown = unknownFixture.service.overview("trace-all-unknown");
        assertEquals(Availability.UNKNOWN, unknown.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, unknown.evidenceMetadata().freshnessStatus());
        assertEquals(5, unknown.unknownAvailabilityCount());
    }

    @Test
    void shouldUseNullAgeForFutureObservedTime() {
        Fixture fixture = fixture();
        fixture.respondWith(
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(50)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.plusSeconds(5)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(30)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(20)),
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH, NOW.minusSeconds(10))
        );

        ValidationOperationsRuntimeEvidenceOverviewReadModel model = fixture.service.overview("trace-future");

        assertEquals(NOW.plusSeconds(5), model.evidenceMetadata().lastCalculatedAt());
        assertNull(model.evidenceMetadata().ageSeconds());
        assertEquals(FreshnessStatus.UNKNOWN, model.evidenceMetadata().freshnessStatus());
        assertEquals("LAST_CALCULATED_AT_IN_FUTURE", model.evidenceMetadata().staleReason());
    }

    @Test
    void shouldPropagateUnknownSourceFailureWithoutReturningSyntheticSuccess() {
        Fixture fixture = fixture();
        fixture.shadowValidationWorkflowSource.failure = new IllegalStateException("source unavailable");

        assertThrows(IllegalStateException.class, () -> fixture.service.overview("trace-source-error"));
        assertEquals(1, fixture.shadowValidationWorkflowSource.calls.get());
        assertEquals(0, fixture.shadowRunSource.calls.get());
    }

    private ReadModelEvidenceMetadata metadata(Availability availability, FreshnessStatus freshness, Instant lastCalculatedAt) {
        return new ReadModelEvidenceMetadata(
                "TEST_" + availability.name(),
                availability,
                lastCalculatedAt,
                freshness,
                lastCalculatedAt == null ? null : 0L,
                null,
                freshness == FreshnessStatus.FRESH ? null : "TEST_REASON",
                true,
                true,
                true,
                true
        );
    }

    private void assertSafetyFlags(ReadModelEvidenceMetadata metadata) {
        assertTrue(metadata.diagnosticOnly());
        assertTrue(metadata.noSideEffect());
        assertTrue(metadata.notTradingAuthorization());
        assertTrue(metadata.liveDisabled());
    }

    private Fixture fixture() {
        return new Fixture();
    }

    private static final class Fixture {
        private final CountingSource shadowValidationWorkflowSource = new CountingSource();
        private final CountingSource shadowRunSource = new CountingSource();
        private final CountingSource consistencyEvidenceSource = new CountingSource();
        private final CountingSource incidentReplayReviewSource = new CountingSource();
        private final CountingSource evaluationArtifactPreviewSource = new CountingSource();
        private final ValidationOperationsRuntimeEvidenceOverviewQueryService service = new ValidationOperationsRuntimeEvidenceOverviewQueryService(
                shadowValidationWorkflowSource,
                shadowRunSource,
                consistencyEvidenceSource,
                incidentReplayReviewSource,
                evaluationArtifactPreviewSource,
                FIXED_CLOCK
        );

        private void respondWith(ReadModelEvidenceMetadata... metadata) {
            respondWith(List.of(metadata));
        }

        private void respondWith(List<ReadModelEvidenceMetadata> metadata) {
            ReadModelEvidenceMetadata first = metadata.getFirst();
            shadowValidationWorkflowSource.metadata = first;
            shadowRunSource.metadata = metadata.size() == 1 ? first : metadata.get(1);
            consistencyEvidenceSource.metadata = metadata.size() == 1 ? first : metadata.get(2);
            incidentReplayReviewSource.metadata = metadata.size() == 1 ? first : metadata.get(3);
            evaluationArtifactPreviewSource.metadata = metadata.size() == 1 ? first : metadata.get(4);
        }

        private void assertEachSourceCalledOnce() {
            assertEquals(1, shadowValidationWorkflowSource.calls.get());
            assertEquals(1, shadowRunSource.calls.get());
            assertEquals(1, consistencyEvidenceSource.calls.get());
            assertEquals(1, incidentReplayReviewSource.calls.get());
            assertEquals(1, evaluationArtifactPreviewSource.calls.get());
        }
    }

    private static final class CountingSource implements ValidationOperationsRuntimeEvidenceOverviewQueryService.EvidenceMetadataSource {
        private final AtomicInteger calls = new AtomicInteger();
        private ReadModelEvidenceMetadata metadata;
        private RuntimeException failure;

        @Override
        public ReadModelEvidenceMetadata load(String traceId) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return metadata;
        }
    }
}

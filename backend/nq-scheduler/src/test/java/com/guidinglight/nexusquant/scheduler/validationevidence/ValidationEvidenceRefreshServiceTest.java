package com.guidinglight.nexusquant.scheduler.validationevidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证 refresh 只调用 aggregate 一次，并保守映射 availability/freshness/blocker/warning。 */
class ValidationEvidenceRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T12:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldReturnSuccessForFiveAvailableFreshSources() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        when(queryService.overview("trc-test")).thenReturn(overview(
                metadata(Availability.AVAILABLE, FreshnessStatus.FRESH),
                List.of(
                        source("ONE", Availability.AVAILABLE, FreshnessStatus.FRESH),
                        source("TWO", Availability.AVAILABLE, FreshnessStatus.FRESH),
                        source("THREE", Availability.AVAILABLE, FreshnessStatus.FRESH),
                        source("FOUR", Availability.AVAILABLE, FreshnessStatus.FRESH),
                        source("FIVE", Availability.AVAILABLE, FreshnessStatus.FRESH)
                )
        ));

        ValidationEvidenceRefreshResult result = new ValidationEvidenceRefreshService(queryService, CLOCK)
                .refresh("trc-test");

        assertEquals(ValidationEvidenceRefreshResult.Result.SUCCESS, result.result());
        assertEquals(0, result.blockerCount());
        assertEquals(0, result.warningCount());
        verify(queryService, times(1)).overview("trc-test");
    }

    @Test
    void shouldReturnDegradedAndCountDistinctBlockersAndWarnings() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        when(queryService.overview("trc-test")).thenReturn(overview(
                metadata(Availability.PARTIAL, FreshnessStatus.STALE),
                List.of(
                        source("ONE", Availability.AVAILABLE, FreshnessStatus.FRESH),
                        source("TWO", Availability.PARTIAL, FreshnessStatus.UNKNOWN),
                        source("THREE", Availability.UNAVAILABLE, FreshnessStatus.UNKNOWN),
                        source("FOUR", Availability.UNKNOWN, FreshnessStatus.UNKNOWN),
                        source("FIVE", Availability.AVAILABLE, FreshnessStatus.STALE)
                )
        ));

        ValidationEvidenceRefreshResult result = new ValidationEvidenceRefreshService(queryService, CLOCK)
                .refresh("trc-test");

        assertEquals(ValidationEvidenceRefreshResult.Result.DEGRADED, result.result());
        assertEquals(2, result.blockerCount());
        assertEquals(2, result.warningCount());
        verify(queryService, times(1)).overview("trc-test");
    }

    @Test
    void shouldPropagateQueryFailureWithoutSyntheticSuccess() {
        ValidationOperationsRuntimeEvidenceOverviewQueryService queryService = mock(
                ValidationOperationsRuntimeEvidenceOverviewQueryService.class
        );
        when(queryService.overview("trc-test")).thenThrow(new IllegalStateException("query failed"));

        ValidationEvidenceRefreshService service = new ValidationEvidenceRefreshService(queryService, CLOCK);

        assertThrows(IllegalStateException.class, () -> service.refresh("trc-test"));
        verify(queryService, times(1)).overview("trc-test");
    }

    private static ValidationOperationsRuntimeEvidenceOverviewReadModel overview(
            ReadModelEvidenceMetadata aggregate,
            List<RuntimeEvidenceSource> sources
    ) {
        return new ValidationOperationsRuntimeEvidenceOverviewReadModel(
                NOW,
                aggregate,
                sources.size(),
                sources.stream().filter(item -> item.evidenceMetadata().availability() == Availability.AVAILABLE).count(),
                sources.stream().filter(item -> item.evidenceMetadata().availability() == Availability.PARTIAL).count(),
                sources.stream().filter(item -> item.evidenceMetadata().availability() == Availability.UNAVAILABLE).count(),
                sources.stream().filter(item -> item.evidenceMetadata().availability() == Availability.UNKNOWN).count(),
                sources.stream().filter(item -> item.evidenceMetadata().freshnessStatus() == FreshnessStatus.FRESH).count(),
                sources.stream().filter(item -> item.evidenceMetadata().freshnessStatus() == FreshnessStatus.STALE).count(),
                sources.stream().filter(item -> item.evidenceMetadata().freshnessStatus() == FreshnessStatus.UNKNOWN).count(),
                sources,
                "trc-test"
        );
    }

    private static RuntimeEvidenceSource source(
            String key,
            Availability availability,
            FreshnessStatus freshnessStatus
    ) {
        return new RuntimeEvidenceSource(key, key, metadata(availability, freshnessStatus));
    }

    private static ReadModelEvidenceMetadata metadata(
            Availability availability,
            FreshnessStatus freshnessStatus
    ) {
        Instant calculatedAt = freshnessStatus == FreshnessStatus.UNKNOWN ? null : NOW.minusSeconds(10);
        return new ReadModelEvidenceMetadata(
                "TEST",
                availability,
                calculatedAt,
                freshnessStatus,
                calculatedAt == null ? null : 10L,
                null,
                freshnessStatus == FreshnessStatus.FRESH ? null : "TEST_REASON",
                true,
                true,
                true,
                true
        );
    }
}

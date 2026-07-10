package com.guidinglight.nexusquant.strategy.application.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewFacts;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ShadowRunOverviewQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldReturnUnavailableUnknownMetadataForEmptyFacts() {
        ShadowRunOverviewReadModel model = service(ShadowRunOverviewFacts.empty()).overview("trace-empty");

        assertEquals("LOCAL_DB_SHADOW_FACTS", model.evidenceMetadata().source());
        assertEquals(Availability.UNAVAILABLE, model.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, model.evidenceMetadata().freshnessStatus());
        assertNull(model.evidenceMetadata().lastCalculatedAt());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
    }

    @Test
    void shouldExposeRealEvidenceAgeButKeepFreshnessUnknownWithoutPolicy() {
        Instant sourceTimestamp = NOW.minusSeconds(90);
        ShadowRunOverviewFacts facts = new ShadowRunOverviewFacts(
                1,
                0,
                0,
                0,
                0,
                1,
                Optional.empty(),
                Optional.empty(),
                Optional.of(new ShadowRunOverviewEvidenceFact(
                        "SHADOW_RUN_EVENT",
                        "event-1",
                        "1",
                        sourceTimestamp,
                        null
                )),
                Optional.empty()
        );

        ShadowRunOverviewReadModel model = service(facts).overview("trace-partial");

        assertEquals(Availability.PARTIAL, model.evidenceMetadata().availability());
        assertEquals(sourceTimestamp, model.evidenceMetadata().lastCalculatedAt());
        assertEquals(90L, model.evidenceMetadata().ageSeconds());
        assertNull(model.evidenceMetadata().staleAfterSeconds());
        assertEquals(FreshnessStatus.UNKNOWN, model.evidenceMetadata().freshnessStatus());
        assertEquals("SOURCE_PARTIAL", model.evidenceMetadata().staleReason());
    }

    private ShadowRunOverviewQueryService service(ShadowRunOverviewFacts facts) {
        return new ShadowRunOverviewQueryService(() -> facts, FIXED_CLOCK);
    }
}

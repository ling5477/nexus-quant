package com.guidinglight.nexusquant.strategy.application.shadowrun.service;

import com.guidinglight.nexusquant.strategy.application.shadowrun.model.ShadowRunOverviewReadModel;
import com.guidinglight.nexusquant.strategy.application.shadowrun.service.ShadowRunOverviewQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

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
    void shouldExposeRealEvidenceAgeButKeepPartialEvidenceUnknown() {
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
        assertEquals(604800L, model.evidenceMetadata().staleAfterSeconds());
        assertEquals(FreshnessStatus.UNKNOWN, model.evidenceMetadata().freshnessStatus());
        assertEquals("SOURCE_PARTIAL", model.evidenceMetadata().staleReason());
    }

    @Test
    void shouldUseExistingSevenDayWindowWithInclusiveSecondBoundary() {
        for (long age : new long[]{0, 90, 604799, 604800, 604801}) {
            var metadata = service(completeFacts(NOW.minusSeconds(age), NOW.minusSeconds(age)))
                    .overview("trace-window").evidenceMetadata();
            assertEquals(Availability.AVAILABLE, metadata.availability());
            assertEquals(age <= 604800 ? FreshnessStatus.FRESH : FreshnessStatus.STALE, metadata.freshnessStatus());
            assertEquals(NOW.minusSeconds(age), metadata.lastCalculatedAt());
            assertEquals(age, metadata.ageSeconds());
            assertEquals(604800L, metadata.staleAfterSeconds());
            assertEquals(age <= 604800 ? null : "STALE_THRESHOLD_EXCEEDED", metadata.staleReason());
        }
    }

    @Test
    void shouldRetainCanonicalLatestFactTimeAcrossRepeatedReadsAndRejectFutureFacts() {
        var facts = completeFacts(NOW.minusSeconds(604801), NOW.minusSeconds(20));
        var first = service(facts).overview("trace-first").evidenceMetadata();
        var later = new ShadowRunOverviewQueryService(() -> facts,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC)).overview("trace-later").evidenceMetadata();
        assertEquals(NOW.minusSeconds(20), first.lastCalculatedAt());
        assertEquals(first.lastCalculatedAt(), later.lastCalculatedAt());
        assertEquals(80L, later.ageSeconds());
        var future = service(completeFacts(NOW, NOW.plusSeconds(1))).overview("trace-future").evidenceMetadata();
        assertEquals(FreshnessStatus.UNKNOWN, future.freshnessStatus());
        assertEquals("LAST_CALCULATED_AT_IN_FUTURE", future.staleReason());
    }

    private ShadowRunOverviewFacts completeFacts(Instant runTime, Instant evidenceTime) {
        var json = new ObjectMapper();
        var runId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var run = new ShadowRun(runId, "version-1", runId, null, null, null, null,
                ShadowRunStatus.CREATED, null, null, json.createObjectNode(),
                true, true, true, true, true, true, ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                null, "freshness-fixture", "trace-fixture", json.createArrayNode(), json.createArrayNode(),
                json.createArrayNode(), 0, runTime, runTime, null, null, null);
        var report = new ShadowConsistencyReport(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                runId, null, ShadowConsistencyComparisonStatus.NOT_COMPARABLE, json.createObjectNode(),
                json.createArrayNode(), json.createArrayNode().add("诊断输入不证明比较成功"),
                evidenceTime, "trace-fixture", evidenceTime);
        var event = new ShadowRunOverviewEvidenceFact("SHADOW_EVENT", "event-1", "1", evidenceTime, null);
        var snapshot = new ShadowRunOverviewEvidenceFact("SHADOW_SNAPSHOT", "snapshot-1", "1", evidenceTime, "fixture");
        return new ShadowRunOverviewFacts(1, 0, 0, 0, 0, 0, Optional.of(run), Optional.of(report),
                Optional.of(event), Optional.of(snapshot));
    }

    private ShadowRunOverviewQueryService service(ShadowRunOverviewFacts facts) {
        return new ShadowRunOverviewQueryService(() -> facts, FIXED_CLOCK);
    }
}

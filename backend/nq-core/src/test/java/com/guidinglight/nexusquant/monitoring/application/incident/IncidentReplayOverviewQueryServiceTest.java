package com.guidinglight.nexusquant.monitoring.application.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewFacts;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewFacts.LatestEvidenceFact;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewQueryPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class IncidentReplayOverviewQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-08T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReturnSafeOverviewForEmptyFacts() {
        IncidentReplayOverviewReadModel model = service(IncidentReplayOverviewFacts.empty()).overview("trace-empty");

        assertEquals(IncidentReplaySeverity.NONE, model.incidentSeverity());
        assertEquals(0, model.totalEvidenceItems());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.liveDisabled());
        assertFalse(model.realProviderImplemented());
        assertFalse(model.privateTradingImplemented());
        assertFalse(model.aiDhRuntimeIntegrated());
        assertHasMessage(model.blockers(), "LIVE_DISABLED");
        assertHasMessage(model.blockers(), "REAL_PROVIDER_NOT_IMPLEMENTED");
        assertHasMessage(model.blockers(), "PRIVATE_TRADING_NOT_IMPLEMENTED");
        assertHasMessage(model.blockers(), "NOT_TRADING_AUTHORIZATION");
        assertHasMessage(model.warnings(), "INCIDENT_REPLAY_DIAGNOSTIC_ONLY");
        assertHasMessage(model.warnings(), "SOURCE_NOT_AVAILABLE");
        assertHasMessage(model.warnings(), "NO_LOCAL_EVIDENCE");
        assertEquals("trace-empty", model.traceId());
    }

    @Test
    void shouldReturnInfoWhenOnlyShadowEventsExist() {
        IncidentReplayOverviewReadModel model = service(new IncidentReplayOverviewFacts(
                3,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(new LatestEvidenceFact(
                        "SHADOW_EVENT",
                        "evt-1",
                        "COMPLETED",
                        "Shadow event completed",
                        Instant.parse("2026-07-08T08:59:00Z"),
                        "trace-shadow"
                ))
        )).overview("trace-shadow-overview");

        assertEquals(IncidentReplaySeverity.INFO, model.incidentSeverity());
        assertEquals(3, model.totalEvidenceItems());
        assertEquals(1, model.latestEvidence().size());
        assertEquals("SHADOW_EVENT", model.latestEvidence().getFirst().evidenceType());
    }

    @Test
    void shouldReturnHighSeverityForConsistencyDivergence() {
        IncidentReplayOverviewReadModel model = service(new IncidentReplayOverviewFacts(
                2,
                1,
                0,
                0,
                0,
                0,
                0,
                List.of(new LatestEvidenceFact(
                        "CONSISTENCY_DIVERGENCE",
                        "rpt-1",
                        "DIVERGED",
                        "Divergence reasons count: 2",
                        Instant.parse("2026-07-08T08:58:00Z"),
                        "trace-diverged"
                ))
        )).overview("trace-diverged-overview");

        assertEquals(IncidentReplaySeverity.HIGH, model.incidentSeverity());
        assertEquals(1, model.consistencyDivergenceCount());
        assertHasNextStep(model.nextSteps(), "REVIEW_DIVERGENCE_OR_ALERT_EVIDENCE");
    }

    @Test
    void shouldReturnCriticalForCriticalPaperAlert() {
        IncidentReplayOverviewReadModel model = service(new IncidentReplayOverviewFacts(
                0,
                0,
                2,
                1,
                0,
                0,
                1,
                List.of(new LatestEvidenceFact(
                        "PAPER_ALERT",
                        "alt-1",
                        "CRITICAL:OPEN",
                        "Critical paper alert",
                        Instant.parse("2026-07-08T08:57:00Z"),
                        null
                ))
        )).overview("trace-critical-overview");

        assertEquals(IncidentReplaySeverity.CRITICAL, model.incidentSeverity());
        assertEquals(3, model.totalEvidenceItems());
    }

    @Test
    void shouldFilterSensitiveOrMisleadingEvidenceText() {
        IncidentReplayOverviewReadModel model = service(new IncidentReplayOverviewFacts(
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(new LatestEvidenceFact(
                        "SHADOW_EVENT",
                        "evt-1",
                        "apiKey exposed",
                        "ready to trade",
                        Instant.parse("2026-07-08T08:56:00Z"),
                        "trace-safe"
                ))
        )).overview("trace-safe-overview");

        String rendered = model.toString().toLowerCase(Locale.ROOT);
        assertFalse(rendered.contains("apikey"));
        assertFalse(rendered.contains("ready to trade"));
        assertTrue(rendered.contains("[filtered diagnostic text]"));
    }

    private IncidentReplayOverviewQueryService service(IncidentReplayOverviewFacts facts) {
        return new IncidentReplayOverviewQueryService(new InMemoryQueryPort(facts), FIXED_CLOCK);
    }

    private void assertHasMessage(
            Iterable<IncidentReplayOverviewReadModel.BoundaryMessage> messages,
            String code
    ) {
        for (IncidentReplayOverviewReadModel.BoundaryMessage message : messages) {
            if (code.equals(message.code())) {
                return;
            }
        }
        throw new AssertionError("expected message code: " + code);
    }

    private void assertHasNextStep(
            Iterable<IncidentReplayOverviewReadModel.NextStep> nextSteps,
            String code
    ) {
        for (IncidentReplayOverviewReadModel.NextStep step : nextSteps) {
            if (code.equals(step.code())) {
                return;
            }
        }
        throw new AssertionError("expected next step code: " + code);
    }

    private record InMemoryQueryPort(IncidentReplayOverviewFacts facts) implements IncidentReplayOverviewQueryPort {
        @Override
        public IncidentReplayOverviewFacts loadOverviewFacts() {
            return facts;
        }
    }
}

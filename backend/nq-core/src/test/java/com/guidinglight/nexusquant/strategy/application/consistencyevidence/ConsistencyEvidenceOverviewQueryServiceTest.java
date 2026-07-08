package com.guidinglight.nexusquant.strategy.application.consistencyevidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts.ConsistencyReportFact;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewQueryPort;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConsistencyEvidenceOverviewQueryServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-08T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID REPORT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DATASET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldReturnSafeOverviewForEmptyFactsWithoutCreatingReport() {
        ConsistencyEvidenceOverviewReadModel model = service(ConsistencyEvidenceOverviewFacts.empty())
                .overview("trace-empty");

        assertEquals(0, model.totalEvidenceItems());
        assertEquals(0, model.evidenceItems().size());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.liveDisabled());
        assertFalse(model.realProviderImplemented());
        assertFalse(model.privateTradingImplemented());
        assertFalse(model.aiDhRuntimeIntegrated());
        assertHasMessage(model.warnings(), "NO_CONSISTENCY_EVIDENCE");
        assertHasNextStep(model.nextSteps(), "INSPECT_LOCAL_CONSISTENCY_FACTS");
        assertEquals("NO_CONSISTENCY_EVIDENCE", model.evidenceAnchors().getFirst().sourceVersion());
    }

    @Test
    void shouldAggregateStatusesFreshnessAndSeverityWithoutAuthorizationSemantics() {
        ConsistencyEvidenceOverviewReadModel model = service(facts(
                fact("CONSISTENT", freshGeneratedAt(), metric("pnlDelta", 0.0), array(), array()),
                fact("DIVERGED", freshGeneratedAt(), metric("fillDelta", 2.0), array("fill mismatch"), array("diagnostic only")),
                fact("PARTIAL", freshGeneratedAt(), metric("latencyDelta", 1.5), array("partial evidence"), array()),
                fact("FAILED", freshGeneratedAt(), metric("errorCountDelta", 1.0), array("comparison failed"), array()),
                fact("NOT_COMPARABLE", Instant.parse("2026-06-28T10:00:00Z"), metric("schemaDelta", 1.0), array(), array("schema mismatch"))
        )).overview("trace-status");

        assertEquals(5, model.totalEvidenceItems());
        assertEquals(1, model.consistentCount());
        assertEquals(1, model.divergedCount());
        assertEquals(1, model.partialCount());
        assertEquals(1, model.failedCount());
        assertEquals(1, model.notComparableCount());
        assertEquals(1, model.staleEvidenceCount());
        assertEquals(1, model.highSeverityCount());
        assertEquals(1, model.criticalSeverityCount());
        assertEquals(1L, model.severityBuckets().get("HIGH"));
        assertEquals(1L, model.severityBuckets().get("CRITICAL"));
        assertEquals(4L, model.freshnessSummary().get("FRESH"));
        assertEquals(1L, model.freshnessSummary().get("STALE"));
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.evidenceItems().stream().allMatch(ConsistencyEvidenceOverviewReadModel.ConsistencyEvidenceItem::notTradingAuthorization));
        assertHasMessage(model.warnings(), "DIVERGED_IS_DIAGNOSTIC_ONLY");
        assertHasMessage(model.warnings(), "HIGH_CRITICAL_ARE_PRIORITY_ONLY");
        assertHasNextStep(model.nextSteps(), "REVIEW_FAILED_COMPARISON");
    }

    @Test
    void shouldSummarizeMetricDeltaAndFilterSensitiveFieldsWithoutProfitOrTradeInference() throws Exception {
        ObjectNode metricDelta = OBJECT_MAPPER.createObjectNode()
                .put("returnDelta", 0.12)
                .put("apiKey", "must-not-leak");
        metricDelta.set("nested", OBJECT_MAPPER.createObjectNode()
                .put("readyToTrade", 1.0)
                .put("spreadDelta", -0.04));
        ConsistencyEvidenceOverviewReadModel model = service(facts(fact(
                "DIVERGED",
                freshGeneratedAt(),
                metricDelta,
                array("ready to trade"),
                array("token leaked", "diagnostic report only")
        ))).overview("trace-safe");

        ConsistencyEvidenceOverviewReadModel.ConsistencyEvidenceItem item = model.evidenceItems().getFirst();

        assertEquals(2, item.metricDelta().metricCount());
        assertEquals(2, item.metricDelta().comparableMetricCount());
        assertEquals(0, item.metricDelta().nonComparableMetricCount());
        assertEquals(2, item.metricDelta().sensitiveFieldFilteredCount());
        assertFalse(item.metricDelta().rawMetricDeltaExposed());
        assertFalse(item.metricDelta().profitConclusionInferred());
        assertFalse(item.metricDelta().tradingSignalInferred());
        assertTrue(item.limitations().contains("METRIC_DELTA_SUMMARY_ONLY"));
        assertTrue(item.limitations().contains("SENSITIVE_FIELD_FILTERED"));
        assertTrue(model.warnings().stream().anyMatch(message -> message.code().equals("SENSITIVE_FIELD_FILTERED")));
        String rendered = model.toString().toLowerCase(Locale.ROOT);
        assertFalse(rendered.contains("apikey"));
        assertFalse(rendered.contains("must-not-leak"));
        assertFalse(rendered.contains("ready to trade"));
        assertFalse(rendered.contains("token leaked"));
        assertTrue(rendered.contains("[filtered diagnostic text]"));
    }

    @Test
    void shouldGenerateDeterministicEvidenceItemId() {
        ConsistencyEvidenceOverviewQueryService service = service(facts(
                fact("DIVERGED", freshGeneratedAt(), metric("fillDelta", 2.0), array("reason"), array())
        ));

        String first = service.overview("trace-1").evidenceItems().getFirst().evidenceItemId();
        String second = service.overview("trace-2").evidenceItems().getFirst().evidenceItemId();

        assertEquals(first, second);
        assertTrue(first.startsWith("cse-"));
    }

    @Test
    void shouldKeepServiceDependencyAwayFromRunnerAdapterAccountLedgerAndOrderPorts() {
        List<String> dependencyNames = List.of(ConsistencyEvidenceOverviewQueryService.class.getDeclaredFields()).stream()
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getName)
                .toList();

        assertEquals(List.of(ConsistencyEvidenceOverviewQueryPort.class.getName(), Clock.class.getName()), dependencyNames);
        String joined = String.join("|", dependencyNames).toLowerCase(Locale.ROOT);
        assertFalse(joined.contains("runner"));
        assertFalse(joined.contains("adapter"));
        assertFalse(joined.contains("account"));
        assertFalse(joined.contains("ledger"));
        assertFalse(joined.contains("order"));
        assertFalse(joined.contains("client"));
    }

    private ConsistencyEvidenceOverviewQueryService service(ConsistencyEvidenceOverviewFacts facts) {
        return new ConsistencyEvidenceOverviewQueryService(new InMemoryQueryPort(facts), FIXED_CLOCK);
    }

    private ConsistencyEvidenceOverviewFacts facts(ConsistencyReportFact... facts) {
        return new ConsistencyEvidenceOverviewFacts(List.of(facts));
    }

    private ConsistencyReportFact fact(
            String status,
            Instant generatedAt,
            JsonNode metricDelta,
            JsonNode divergenceReasons,
            JsonNode limitations
    ) {
        return new ConsistencyReportFact(
                REPORT_ID,
                SHADOW_RUN_ID,
                "paper-1",
                "sv-1",
                DATASET_ID,
                status,
                metricDelta,
                divergenceReasons,
                limitations,
                generatedAt,
                "trace-report",
                "snapshot-1",
                "ORDER_INTENT_PREVIEW",
                "shadow-order-intent-preview.v1",
                "sha256-demo",
                generatedAt,
                "event-1",
                "COMPLETED",
                "COMPLETED",
                generatedAt
        );
    }

    private Instant freshGeneratedAt() {
        return Instant.parse("2026-07-08T09:59:00Z");
    }

    private ObjectNode metric(String name, double value) {
        return JsonNodeFactory.instance.objectNode().put(name, value);
    }

    private JsonNode array(String... values) {
        var array = JsonNodeFactory.instance.arrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private void assertHasMessage(
            Iterable<ConsistencyEvidenceOverviewReadModel.BoundaryMessage> messages,
            String code
    ) {
        for (ConsistencyEvidenceOverviewReadModel.BoundaryMessage message : messages) {
            if (code.equals(message.code())) {
                return;
            }
        }
        throw new AssertionError("expected message code: " + code);
    }

    private void assertHasNextStep(
            Iterable<ConsistencyEvidenceOverviewReadModel.NextStep> nextSteps,
            String code
    ) {
        for (ConsistencyEvidenceOverviewReadModel.NextStep step : nextSteps) {
            if (code.equals(step.code())) {
                return;
            }
        }
        throw new AssertionError("expected next step code: " + code);
    }

    private record InMemoryQueryPort(ConsistencyEvidenceOverviewFacts facts)
            implements ConsistencyEvidenceOverviewQueryPort {

        @Override
        public ConsistencyEvidenceOverviewFacts loadOverviewFacts() {
            return facts;
        }
    }
}

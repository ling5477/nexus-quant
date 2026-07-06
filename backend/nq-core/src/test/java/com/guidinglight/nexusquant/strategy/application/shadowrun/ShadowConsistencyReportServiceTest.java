package com.guidinglight.nexusquant.strategy.application.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatusUpdateResult;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ShadowConsistencyReportServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-06T11:00:00Z"), ZoneOffset.UTC);
    private static final UUID SHADOW_RUN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DATASET_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant WINDOW_START = Instant.parse("2026-07-06T10:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-07-06T11:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldGenerateConsistentReportWithinThresholdAndPersistEventAndLatestReport() {
        InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
        ShadowConsistencyReportService service = service(repository);

        ShadowConsistencyReportResult result = service.generate(command(threshold(1)));

        assertEquals(ShadowConsistencyComparisonStatus.CONSISTENT, result.comparisonStatus());
        assertTrue(result.persisted());
        assertNoSideEffectFlags(result);
        assertEquals(1, repository.createReportCalls);
        assertEquals(ShadowRunEventType.CONSISTENCY_REPORT_GENERATED, repository.events.getFirst().eventType());
        assertEquals(Optional.of(repository.reports.getFirst()), repository.findLatestReport(SHADOW_RUN_ID));
        assertMetric(result.metricDelta(), "orderIntentCount", "2", "3", "1", true, true);
        assertMetric(result.metricDelta(), "blockedCount", "0", "0", "0", true, true);
        assertMetric(result.metricDelta(), "warningCount", "1", "1", "0", true, true);
        assertTrue(result.divergenceReasons().isEmpty());
        assertTrue(result.limitations().isEmpty());
    }

    @Test
    void shouldGenerateDivergedReportWhenCountExceedsThresholdOrSideDiffers() {
        InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
        ShadowConsistencyReportService service = service(repository);
        PaperRunComparisonInput paper = paperInput("ptr-1", 1, 0, 0, "LONG_BIAS");
        ShadowRunComparisonInput shadow = shadowInput(4, 0, 0, "SHORT_BIAS");

        ShadowConsistencyReportResult result = service.generate(command(paper, shadow, threshold(1)));

        assertEquals(ShadowConsistencyComparisonStatus.DIVERGED, result.comparisonStatus());
        assertMetric(result.metricDelta(), "orderIntentCount", "1", "4", "3", true, false);
        assertHasReason(result.divergenceReasons(), "SIDE_MISMATCH");
        assertHasReason(result.divergenceReasons(), "COUNT_DELTA_EXCEEDED");
        assertEquals(1, repository.createReportCalls);
    }

    @Test
    void shouldGenerateNotComparableWhenPaperRunIdOrShadowInputIsMissing() {
        InMemoryShadowRunFactRepository repository = repository(run(null));
        ShadowConsistencyReportService service = service(repository);
        ShadowConsistencyReportCommand command = new ShadowConsistencyReportCommand(
                SHADOW_RUN_ID,
                paperInput(null, 1, 0, 0, "LONG_BIAS"),
                null,
                threshold(0),
                "req-report",
                "trace-report",
                null,
                null
        );

        ShadowConsistencyReportResult result = service.generate(command);

        assertEquals(ShadowConsistencyComparisonStatus.NOT_COMPARABLE, result.comparisonStatus());
        assertHasLimitation(result.limitations(), "MISSING_PAPER_RUN_ID");
        assertHasLimitation(result.limitations(), "MISSING_SHADOW_INPUT");
        assertEquals(1, repository.createReportCalls);
    }

    @Test
    void shouldGenerateNotComparableWhenWindowMismatches() {
        InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
        ShadowConsistencyReportService service = service(repository);
        ShadowRunComparisonInput shadow = new ShadowRunComparisonInput(
                SHADOW_RUN_ID,
                ShadowRunStatus.COMPLETED,
                2,
                0,
                1,
                "LONG_BIAS",
                "BTC-USDT",
                "1m",
                WINDOW_START.minusSeconds(60),
                WINDOW_END,
                "sv-1",
                DATASET_ID,
                objectMapper.createObjectNode().put("source", "shadow")
        );

        ShadowConsistencyReportResult result = service.generate(command(paperInput("ptr-1", 2, 0, 1, "LONG_BIAS"),
                shadow,
                threshold(0)));

        assertEquals(ShadowConsistencyComparisonStatus.NOT_COMPARABLE, result.comparisonStatus());
        assertHasLimitation(result.limitations(), "WINDOW_MISMATCH");
    }

    @Test
    void shouldGeneratePartialReportWhenOnlySomeMetricsAreComparable() {
        InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
        ShadowConsistencyReportService service = service(repository);
        PaperRunComparisonInput paper = paperInput("ptr-1", 2, null, 1, "LONG_BIAS");
        ShadowRunComparisonInput shadow = shadowInput(2, 0, null, "LONG_BIAS");

        ShadowConsistencyReportResult result = service.generate(command(paper, shadow, threshold(0)));

        assertEquals(ShadowConsistencyComparisonStatus.PARTIAL, result.comparisonStatus());
        assertMetric(result.metricDelta(), "orderIntentCount", "2", "2", "0", true, true);
        assertHasLimitation(result.limitations(), "METRIC_NOT_AVAILABLE");
        assertTrue(result.divergenceReasons().isEmpty());
    }

    @Test
    void shouldGenerateFailedReportWhenCallerReportsComparisonFailure() {
        InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
        ShadowConsistencyReportService service = service(repository);
        ShadowConsistencyReportCommand command = new ShadowConsistencyReportCommand(
                SHADOW_RUN_ID,
                paperInput("ptr-1", 2, 0, 1, "LONG_BIAS"),
                shadowInput(2, 0, 1, "LONG_BIAS"),
                threshold(0),
                "req-report",
                "trace-report",
                "LOCAL_COMPARISON_EXCEPTION",
                "local summary could not be compared"
        );

        ShadowConsistencyReportResult result = service.generate(command);

        assertEquals(ShadowConsistencyComparisonStatus.FAILED, result.comparisonStatus());
        assertHasReason(result.divergenceReasons(), "LOCAL_COMPARISON_EXCEPTION");
        assertHasLimitation(result.limitations(), "COMPARISON_PROCESS_FAILED");
        assertEquals(1, repository.createReportCalls);
    }

    @Test
    void shouldRejectForbiddenSensitivePayloadBeforePersistence() {
        List<String> forbiddenFields = List.of(
                "apiKey",
                "secret",
                "passphrase",
                "token",
                "credentialMaterial",
                "realOrderId",
                "realAccountBalance",
                "tradingReady",
                "liveReady",
                "authorizedForTrading",
                "tradeApproved"
        );

        for (String field : forbiddenFields) {
            InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
            JsonNode forbiddenPayload = objectMapper.createObjectNode().put(field, "redacted");

            assertThrows(IllegalArgumentException.class,
                    () -> new PaperRunComparisonInput(
                            "ptr-1",
                            2,
                            0,
                            1,
                            "LONG_BIAS",
                            "BTC-USDT",
                            "1m",
                            WINDOW_START,
                            WINDOW_END,
                            "sv-1",
                            DATASET_ID,
                            forbiddenPayload
                    ));
            assertThrows(IllegalArgumentException.class,
                    () -> new ShadowRunComparisonInput(
                            SHADOW_RUN_ID,
                            ShadowRunStatus.COMPLETED,
                            2,
                            0,
                            1,
                            "LONG_BIAS",
                            "BTC-USDT",
                            "1m",
                            WINDOW_START,
                            WINDOW_END,
                            "sv-1",
                            DATASET_ID,
                            forbiddenPayload
                    ));
            assertEquals(0, repository.createReportCalls, "report must not be persisted for " + field);
        }
    }

    @Test
    void shouldKeepReportPayloadAwayFromTradingApprovalFields() {
        InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
        ShadowConsistencyReportService service = service(repository);

        ShadowConsistencyReportResult result = service.generate(command(threshold(0)));
        String resultFieldNames = Arrays.stream(ShadowConsistencyReportResult.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.joining("|"));
        String serialized = (resultFieldNames
                + result.metricDelta()
                + result.divergenceReasons()
                + result.limitations()).toLowerCase(Locale.ROOT);

        assertFalse(serialized.contains("approval"));
        assertFalse(serialized.contains("authorization"));
        assertFalse(serialized.contains("authorizedfortrading"));
        assertFalse(serialized.contains("tradeapproved"));
        assertFalse(serialized.contains("tradingready"));
        assertFalse(serialized.contains("liveready"));
    }

    @Test
    void shouldUseOnlyRepositoryPortAndAvoidExternalMutationDependencies() {
        InMemoryShadowRunFactRepository repository = repository(run("ptr-1"));
        ShadowConsistencyReportService service = service(repository);

        ShadowConsistencyReportResult result = service.generate(command(threshold(0)));

        assertEquals(ShadowConsistencyComparisonStatus.DIVERGED, result.comparisonStatus());
        assertEquals(0, repository.updateStatusCalls);
        String fieldTypes = Arrays.stream(ShadowConsistencyReportService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.joining("\n"));
        assertFalse(fieldTypes.contains("Adapter"));
        assertFalse(fieldTypes.contains("Gateway"));
        assertFalse(fieldTypes.contains("HttpClient"));
        assertFalse(fieldTypes.toLowerCase(Locale.ROOT).contains("credential"));
        assertFalse(fieldTypes.toLowerCase(Locale.ROOT).contains("ledger"));
        assertFalse(fieldTypes.toLowerCase(Locale.ROOT).contains("account"));

        String methodNames = Arrays.stream(ShadowConsistencyReportService.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.joining("\n"));
        assertFalse(methodNames.contains("placeOrder"));
        assertFalse(methodNames.contains("cancelOrder"));
        assertFalse(methodNames.contains("withdraw"));
        assertFalse(methodNames.contains("transfer"));
    }

    private InMemoryShadowRunFactRepository repository(ShadowRun run) {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        repository.runs.put(run.id(), run);
        return repository;
    }

    private ShadowConsistencyReportService service(InMemoryShadowRunFactRepository repository) {
        return new ShadowConsistencyReportService(repository, objectMapper, FIXED_CLOCK);
    }

    private ShadowConsistencyReportCommand command(ConsistencyThreshold threshold) {
        return command(paperInput("ptr-1", 2, 0, 1, "LONG_BIAS"),
                shadowInput(3, 0, 1, "LONG_BIAS"),
                threshold);
    }

    private ShadowConsistencyReportCommand command(
            PaperRunComparisonInput paper,
            ShadowRunComparisonInput shadow,
            ConsistencyThreshold threshold
    ) {
        return new ShadowConsistencyReportCommand(
                SHADOW_RUN_ID,
                paper,
                shadow,
                threshold,
                "req-report",
                "trace-report",
                null,
                null
        );
    }

    private ConsistencyThreshold threshold(int countTolerance) {
        return new ConsistencyThreshold(countTolerance, BigDecimal.ZERO);
    }

    private PaperRunComparisonInput paperInput(
            String paperRunId,
            Integer paperOrderCount,
            Integer paperBlockedCount,
            Integer paperWarningCount,
            String side
    ) {
        return new PaperRunComparisonInput(
                paperRunId,
                paperOrderCount,
                paperBlockedCount,
                paperWarningCount,
                side,
                "BTC-USDT",
                "1m",
                WINDOW_START,
                WINDOW_END,
                "sv-1",
                DATASET_ID,
                objectMapper.createObjectNode().put("source", "paper-summary")
        );
    }

    private ShadowRunComparisonInput shadowInput(
            Integer shadowOrderIntentCount,
            Integer shadowBlockedCount,
            Integer shadowWarningCount,
            String side
    ) {
        return new ShadowRunComparisonInput(
                SHADOW_RUN_ID,
                ShadowRunStatus.COMPLETED,
                shadowOrderIntentCount,
                shadowBlockedCount,
                shadowWarningCount,
                side,
                "BTC-USDT",
                "1m",
                WINDOW_START,
                WINDOW_END,
                "sv-1",
                DATASET_ID,
                objectMapper.createObjectNode().put("source", "shadow-summary")
        );
    }

    private ShadowRun run(String paperRunId) {
        return new ShadowRun(
                SHADOW_RUN_ID,
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                paperRunId,
                ShadowRunStatus.COMPLETED,
                WINDOW_START,
                WINDOW_END,
                objectMapper.createObjectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "req-shadow",
                "idem-shadow",
                "trace-report",
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode(),
                objectMapper.createArrayNode().add("review consistency report"),
                4,
                WINDOW_END,
                WINDOW_END,
                WINDOW_START,
                null,
                WINDOW_END
        );
    }

    private void assertNoSideEffectFlags(ShadowConsistencyReportResult result) {
        assertTrue(result.noOrderSubmission());
        assertTrue(result.noCredentialAccess());
        assertTrue(result.noPrivateEndpoint());
        assertTrue(result.noLedgerMutation());
        assertTrue(result.noAccountMutation());
        assertTrue(result.noExternalPrivateIo());
    }

    private void assertMetric(
            JsonNode metricDelta,
            String metricName,
            String paperValue,
            String shadowValue,
            String delta,
            boolean comparable,
            boolean withinTolerance
    ) {
        JsonNode metric = findMetric(metricDelta, metricName);
        assertEquals(paperValue, metric.path("paperValue").asText(null));
        assertEquals(shadowValue, metric.path("shadowValue").asText(null));
        assertEquals(delta, metric.path("delta").asText());
        assertEquals(comparable, metric.path("comparable").asBoolean());
        assertEquals(withinTolerance, metric.path("withinTolerance").asBoolean());
    }

    private JsonNode findMetric(JsonNode metricDelta, String metricName) {
        for (JsonNode metric : metricDelta.path("metrics")) {
            if (metricName.equals(metric.path("metricName").asText())) {
                return metric;
            }
        }
        throw new AssertionError("missing metric " + metricName + " in " + metricDelta);
    }

    private void assertHasReason(JsonNode reasons, String reasonCode) {
        assertTrue(containsCode(reasons, reasonCode), "missing reason " + reasonCode + " in " + reasons);
    }

    private void assertHasLimitation(JsonNode limitations, String limitationCode) {
        assertTrue(containsCode(limitations, limitationCode), "missing limitation " + limitationCode + " in " + limitations);
    }

    private boolean containsCode(JsonNode array, String code) {
        for (JsonNode node : array) {
            if (code.equals(node.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private static final class InMemoryShadowRunFactRepository implements ShadowRunFactRepository {

        private final Map<UUID, ShadowRun> runs = new LinkedHashMap<>();
        private final List<ShadowRunEvent> events = new ArrayList<>();
        private final List<ShadowConsistencyReport> reports = new ArrayList<>();
        private int createReportCalls;
        private int updateStatusCalls;

        @Override
        public ShadowRun create(ShadowRun run) {
            runs.put(run.id(), run);
            return run;
        }

        @Override
        public Optional<ShadowRun> findById(UUID shadowRunId) {
            return Optional.ofNullable(runs.get(shadowRunId));
        }

        @Override
        public Optional<ShadowRun> findByIdempotencyKey(String idempotencyKey) {
            return runs.values().stream()
                    .filter(run -> run.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public void appendEvent(ShadowRunEvent event) {
            events.add(event);
        }

        @Override
        public void appendSnapshot(ShadowRunSnapshot snapshot) {
            throw new UnsupportedOperationException("consistency report service must not append snapshots");
        }

        @Override
        public ShadowConsistencyReport createConsistencyReport(ShadowConsistencyReport report) {
            createReportCalls++;
            reports.add(report);
            return report;
        }

        @Override
        public ShadowRunStatusUpdateResult updateStatus(
                UUID shadowRunId,
                ShadowRunStatus toStatus,
                long expectedVersion,
                String reasonCode,
                String message,
                String requestId,
                String traceId
        ) {
            updateStatusCalls++;
            throw new UnsupportedOperationException("consistency report service must not update run status");
        }

        @Override
        public List<ShadowRunEvent> listEvents(UUID shadowRunId) {
            return events.stream().filter(event -> event.shadowRunId().equals(shadowRunId)).toList();
        }

        @Override
        public List<ShadowRunSnapshot> listSnapshots(UUID shadowRunId) {
            return List.of();
        }

        @Override
        public Optional<ShadowConsistencyReport> findLatestReport(UUID shadowRunId) {
            return reports.stream()
                    .filter(report -> report.shadowRunId().equals(shadowRunId))
                    .max(Comparator.comparing(ShadowConsistencyReport::generatedAt)
                            .thenComparing(ShadowConsistencyReport::createdAt)
                            .thenComparing(ShadowConsistencyReport::id));
        }
    }
}

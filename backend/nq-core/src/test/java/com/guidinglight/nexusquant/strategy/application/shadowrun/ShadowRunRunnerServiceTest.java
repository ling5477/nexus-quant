package com.guidinglight.nexusquant.strategy.application.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunOptimisticLockException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshotType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateMachine;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatusUpdateResult;
import com.guidinglight.nexusquant.trading.application.port.OrderCommandStrategyExecutionGateway;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ShadowRunRunnerServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID DATASET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant WINDOW_START = Instant.parse("2026-07-06T09:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-07-06T10:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCompleteRunnerHappyPathAndWriteEventsAndSnapshots() throws Exception {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        ShadowRunRunnerService service = service(repository);

        ShadowRunRunnerResult result = service.run(command("idem-success", List.of()));

        assertEquals(ShadowRunStatus.COMPLETED, result.status());
        assertEquals(4, result.snapshotCount());
        assertTrue(result.eventCount() >= 9);
        assertEquals(List.of(
                ShadowRunRunnerStep.CREATE_RUN,
                ShadowRunRunnerStep.PRECHECKING,
                ShadowRunRunnerStep.NO_SIDE_EFFECT_GUARD,
                ShadowRunRunnerStep.READY,
                ShadowRunRunnerStep.INPUT_MARKETDATA_SNAPSHOT,
                ShadowRunRunnerStep.RUNNING,
                ShadowRunRunnerStep.STRATEGY_DECISION_SNAPSHOT,
                ShadowRunRunnerStep.RISK_PREFLIGHT_SNAPSHOT,
                ShadowRunRunnerStep.ORDER_INTENT_PREVIEW_SNAPSHOT,
                ShadowRunRunnerStep.COMPLETED
        ), result.completedSteps());
        assertNoSideEffectFlags(result);
        assertAllRunnerSnapshotTypes(repository, result.shadowRunId());
        assertStructuredDecisionTraceSnapshot(repository, result);
        assertRiskPreflightSnapshot(repository, result, true, false);
        assertOrderIntentPreviewSnapshot(repository, result);
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.code().equals("RISK_PREFLIGHT_WARN")));
        assertEquals(List.of("Review structured decision trace before consistency comparison."), result.nextSteps());
        assertHasTransition(repository.events, ShadowRunStatus.CREATED, ShadowRunStatus.PRECHECKING);
        assertHasTransition(repository.events, ShadowRunStatus.PRECHECKING, ShadowRunStatus.READY);
        assertHasTransition(repository.events, ShadowRunStatus.READY, ShadowRunStatus.RUNNING);
        assertHasTransition(repository.events, ShadowRunStatus.RUNNING, ShadowRunStatus.COMPLETED);
    }

    @Test
    void shouldBlockFromRunningWhenCallerSuppliesLocalBlocker() throws Exception {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        ShadowRunRunnerService service = service(repository);

        ShadowRunRunnerResult result = service.run(blockedCommand());

        assertEquals(ShadowRunStatus.BLOCKED, result.status());
        assertEquals(List.of(new ShadowRunRunnerIssue(
                "RISK_PREFLIGHT_BLOCKED",
                "Local risk preflight preview blocked the run."
        )), result.blockers());
        assertEquals(List.of(new ShadowRunRunnerIssue(
                "RISK_PREFLIGHT_WARN",
                "Local risk warning remains visible in the result."
        )), result.warnings().stream()
                .filter(warning -> warning.code().equals("RISK_PREFLIGHT_WARN"))
                .toList());
        assertEquals(List.of("Resolve risk preview blocker before retry."), result.nextSteps());
        assertHasTransition(repository.events, ShadowRunStatus.RUNNING, ShadowRunStatus.BLOCKED);
        assertAllRunnerSnapshotTypes(repository, result.shadowRunId());
        assertRiskPreflightSnapshot(repository, result, false, true);
    }

    @Test
    void shouldMoveRunToFailedAndRethrowWhenRuntimeSnapshotFails() {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        repository.failOnceOnRiskPreflightSnapshot();
        ShadowRunRunnerService service = service(repository);

        ShadowRunRunnerException ex = assertThrows(
                ShadowRunRunnerException.class,
                () -> service.run(command("idem-failed", List.of()))
        );

        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertNotNull(ex.failureResult());
        assertEquals(ShadowRunStatus.FAILED, ex.failureResult().status());
        assertEquals("SHADOW_RUN_RUNNER_EXCEPTION", ex.failureResult().failureCode());
        ShadowRun stored = repository.findById(ex.failureResult().shadowRunId()).orElseThrow();
        assertEquals(ShadowRunStatus.FAILED, stored.status());
        assertHasTransition(repository.events, ShadowRunStatus.RUNNING, ShadowRunStatus.FAILED);
    }

    @Test
    void shouldUseIdempotencyKeyAndNeverForceTerminalRunBackToRunning() throws Exception {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        ShadowRunRunnerService service = service(repository);

        ShadowRunRunnerResult first = service.run(command("idem-replay", List.of()));
        int eventCountAfterFirstRun = repository.events.size();
        int snapshotCountAfterFirstRun = repository.snapshots.size();
        ShadowRunRunnerResult replay = service.run(command("idem-replay", List.of()));

        assertEquals(first.shadowRunId(), replay.shadowRunId());
        assertEquals(ShadowRunStatus.COMPLETED, replay.status());
        assertTrue(replay.idempotentReplay());
        assertEquals(List.of(ShadowRunRunnerStep.IDEMPOTENT_REPLAY), replay.completedSteps());
        assertEquals(eventCountAfterFirstRun, repository.events.size());
        assertEquals(snapshotCountAfterFirstRun, repository.snapshots.size());
        assertFalse(repository.events.stream()
                .filter(event -> event.shadowRunId().equals(first.shadowRunId()))
                .dropWhile(event -> event.toStatus() != ShadowRunStatus.COMPLETED)
                .anyMatch(event -> event.toStatus() == ShadowRunStatus.RUNNING));
    }

    @Test
    void shouldRejectForbiddenSensitivePayloadBeforeCreatingRun() {
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
            InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
            ShadowRunRunnerService service = service(repository);
            JsonNode forbiddenPayload = objectMapper.createObjectNode().put(field, "redacted");

            assertThrows(IllegalArgumentException.class,
                    () -> service.run(commandWithStrategyFeatures("idem-sensitive-" + field, forbiddenPayload)));
            assertEquals(0, repository.createCalls, "run should not be created for forbidden field " + field);
            assertTrue(repository.runs.isEmpty(), "no local fact should be persisted for forbidden field " + field);

            InMemoryShadowRunFactRepository inputRefRepository = new InMemoryShadowRunFactRepository();
            ShadowRunRunnerService inputRefService = service(inputRefRepository);
            assertThrows(IllegalArgumentException.class,
                    () -> inputRefService.run(commandWithStrategyInputRefs("idem-sensitive-ref-" + field, forbiddenPayload)));
            assertEquals(0, inputRefRepository.createCalls, "run should not be created for inputRefs field " + field);
            assertTrue(inputRefRepository.runs.isEmpty(), "no local fact should be persisted for inputRefs field " + field);
        }
    }

    @Test
    void shouldKeepStructuredModelFieldNamesAwayFromForbiddenSensitiveFields() {
        List<Class<? extends Record>> models = List.of(
                StrategyDecisionTrace.class,
                RiskPreflightSnapshot.class,
                RiskPreflightRuleResult.class,
                OrderIntentPreview.class
        );

        for (Class<? extends Record> model : models) {
            Arrays.stream(model.getRecordComponents())
                    .map(component -> component.getName())
                    .forEach(name -> assertFalse(
                            ShadowRunSensitiveDataGuard.isForbiddenFieldName(name),
                            model.getSimpleName() + " contains forbidden field name: " + name
                    ));
        }
    }

    @Test
    void shouldExposeNoSideEffectGuardAndAvoidExternalExecutionDependencies() throws Exception {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        ShadowRunRunnerService service = service(repository);

        ShadowRunRunnerResult result = service.run(command("idem-boundary", List.of()));

        assertNoSideEffectFlags(result);
        assertTrue(result.orderIntentPreviewOnly());

        String fieldTypes = Arrays.stream(ShadowRunRunnerService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.joining("\n"));
        assertFalse(fieldTypes.contains("Adapter"));
        assertFalse(fieldTypes.contains("Gateway"));
        assertFalse(fieldTypes.toLowerCase(Locale.ROOT).contains("credential"));
        assertFalse(fieldTypes.toLowerCase(Locale.ROOT).contains("ledger"));
        assertFalse(fieldTypes.toLowerCase(Locale.ROOT).contains("account"));

        String methodNames = Arrays.stream(ShadowRunRunnerService.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.joining("\n"));
        assertFalse(methodNames.contains("placeOrder"));
        assertFalse(methodNames.contains("cancelOrder"));
        assertFalse(methodNames.contains("withdraw"));
        assertFalse(methodNames.contains("transfer"));
    }

    @Test
    void shouldRejectOrderIntentPreviewWhenPreviewOnlyIsFalse() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new OrderIntentPreview(
                        false,
                        "OBSERVE",
                        "BTC-USDT",
                        "0",
                        "LIMIT",
                        "0",
                        "GTC",
                        "TEST",
                        "risk-ok",
                        "trace-preview-false"
                )
        );

        assertTrue(ex.getMessage().contains("previewOnly"));
    }

    @Test
    void shouldRejectMismatchedStructuredTraceIdBeforeCreatingRun() throws Exception {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        ShadowRunRunnerService service = service(repository);

        ShadowRunRunnerCommand baseline = command("idem-trace-mismatch", List.of());
        ShadowRunRunnerCommand mismatched = new ShadowRunRunnerCommand(
                baseline.strategyVersionId(),
                baseline.datasetId(),
                baseline.evaluationId(),
                baseline.publishId(),
                baseline.paperRunId(),
                baseline.windowStart(),
                baseline.windowEnd(),
                baseline.requestId(),
                baseline.idempotencyKey(),
                baseline.traceId(),
                baseline.inputMarketdataPayload(),
                new StrategyDecisionTrace(
                        baseline.strategyVersionId(),
                        baseline.datasetId(),
                        "OBSERVE",
                        "OBSERVE",
                        "LOW",
                        List.of("NO_SIGNAL"),
                        objectMapper.createObjectNode(),
                        objectMapper.createObjectNode(),
                        "trace-other"
                ),
                baseline.riskPreflightSnapshot(),
                baseline.orderIntentPreview(),
                baseline.blockers()
        );

        assertThrows(IllegalArgumentException.class, () -> service.run(mismatched));
        assertEquals(0, repository.createCalls);
    }

    @Test
    void shouldWireRunnerInMinimalSpringContextWithoutOrderGatewayBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(ShadowRunFactRepository.class, InMemoryShadowRunFactRepository::new);
            context.registerBean(ShadowRunRunnerService.class);

            context.refresh();

            assertNotNull(context.getBean(ShadowRunRunner.class));
            assertEquals(0, context.getBeanNamesForType(OrderCommandStrategyExecutionGateway.class).length);
        }
    }

    private ShadowRunRunnerService service(InMemoryShadowRunFactRepository repository) {
        return new ShadowRunRunnerService(repository, objectMapper, new ShadowRunStateMachine(), FIXED_CLOCK);
    }

    private ShadowRunRunnerCommand command(String idempotencyKey, List<ShadowRunRunnerIssue> blockers) throws Exception {
        String traceId = "trace-" + idempotencyKey;
        return new ShadowRunRunnerCommand(
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                WINDOW_START,
                WINDOW_END,
                "req-" + idempotencyKey,
                idempotencyKey,
                traceId,
                inputMarketdataPayload(),
                strategyDecisionTrace(traceId),
                riskAllowed(traceId),
                orderIntentPreview(traceId),
                blockers
        );
    }

    private ShadowRunRunnerCommand blockedCommand() throws Exception {
        String idempotencyKey = "idem-blocked";
        String traceId = "trace-" + idempotencyKey;
        return new ShadowRunRunnerCommand(
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                WINDOW_START,
                WINDOW_END,
                "req-" + idempotencyKey,
                idempotencyKey,
                traceId,
                inputMarketdataPayload(),
                strategyDecisionTrace(traceId),
                riskBlocked(traceId),
                orderIntentPreview(traceId),
                List.of()
        );
    }

    private ShadowRunRunnerCommand commandWithStrategyFeatures(String idempotencyKey, JsonNode features) throws Exception {
        ShadowRunRunnerCommand baseline = command(idempotencyKey, List.of());
        return commandWithStrategyJson(
                baseline,
                features,
                objectMapper.createObjectNode().put("inputSnapshot", "fixture-bars")
        );
    }

    private ShadowRunRunnerCommand commandWithStrategyInputRefs(String idempotencyKey, JsonNode inputRefs) throws Exception {
        ShadowRunRunnerCommand baseline = command(idempotencyKey, List.of());
        return commandWithStrategyJson(
                baseline,
                objectMapper.createObjectNode().put("featureSet", "safe"),
                inputRefs
        );
    }

    private ShadowRunRunnerCommand commandWithStrategyJson(
            ShadowRunRunnerCommand baseline,
            JsonNode features,
            JsonNode inputRefs
    ) {
        return new ShadowRunRunnerCommand(
                baseline.strategyVersionId(),
                baseline.datasetId(),
                baseline.evaluationId(),
                baseline.publishId(),
                baseline.paperRunId(),
                baseline.windowStart(),
                baseline.windowEnd(),
                baseline.requestId(),
                baseline.idempotencyKey(),
                baseline.traceId(),
                baseline.inputMarketdataPayload(),
                new StrategyDecisionTrace(
                        baseline.strategyVersionId(),
                        baseline.datasetId(),
                        "OBSERVE",
                        "OBSERVE",
                        "LOW",
                        List.of("NO_SIGNAL"),
                        features,
                        inputRefs,
                        baseline.traceId()
                ),
                baseline.riskPreflightSnapshot(),
                baseline.orderIntentPreview(),
                baseline.blockers()
        );
    }

    private StrategyDecisionTrace strategyDecisionTrace(String traceId) {
        return new StrategyDecisionTrace(
                "sv-1",
                DATASET_ID,
                "OBSERVE",
                "OBSERVE",
                "LOW",
                List.of("NO_SIGNAL", "LOW_CONFIDENCE"),
                objectMapper.createObjectNode()
                        .put("movingAverageFast", "100.12")
                        .put("movingAverageSlow", "101.02"),
                objectMapper.createObjectNode()
                        .put("marketdataSnapshot", "fixture-bars")
                        .put("datasetId", DATASET_ID.toString()),
                traceId
        );
    }

    private RiskPreflightSnapshot riskAllowed(String traceId) {
        return new RiskPreflightSnapshot(
                true,
                false,
                "WARN",
                List.of(new RiskPreflightRuleResult(
                        "NO_REAL_ORDER",
                        "WARN",
                        "WARN",
                        "Preview remains diagnostic only."
                )),
                List.of(),
                List.of(new ShadowRunRunnerIssue(
                        "RISK_PREFLIGHT_WARN",
                        "Local risk warning remains visible in the result."
                )),
                List.of("Review structured decision trace before consistency comparison."),
                traceId
        );
    }

    private RiskPreflightSnapshot riskBlocked(String traceId) {
        return new RiskPreflightSnapshot(
                false,
                true,
                "BLOCK",
                List.of(new RiskPreflightRuleResult(
                        "MAX_EXPOSURE_PREVIEW",
                        "BLOCK",
                        "BLOCK",
                        "Local risk preview blocked the Shadow Run."
                )),
                List.of(new ShadowRunRunnerIssue(
                        "RISK_PREFLIGHT_BLOCKED",
                        "Local risk preflight preview blocked the run."
                )),
                List.of(new ShadowRunRunnerIssue(
                        "RISK_PREFLIGHT_WARN",
                        "Local risk warning remains visible in the result."
                )),
                List.of("Resolve risk preview blocker before retry."),
                traceId
        );
    }

    private OrderIntentPreview orderIntentPreview(String traceId) {
        return new OrderIntentPreview(
                true,
                "OBSERVE",
                "BTC-USDT",
                "0",
                "LIMIT",
                "0",
                "GTC",
                "DIAGNOSTIC_ONLY",
                "risk-ok",
                traceId
        );
    }

    private JsonNode inputMarketdataPayload() throws Exception {
        return objectMapper.readTree("{\"symbol\":\"BTC-USDT\",\"barCount\":2,\"source\":\"fixture\"}");
    }

    private void assertNoSideEffectFlags(ShadowRunRunnerResult result) {
        assertTrue(result.noOrderSubmission());
        assertTrue(result.noCredentialAccess());
        assertTrue(result.noPrivateEndpoint());
        assertTrue(result.noLedgerMutation());
        assertTrue(result.noAccountMutation());
        assertTrue(result.noExternalPrivateIo());
    }

    private void assertAllRunnerSnapshotTypes(InMemoryShadowRunFactRepository repository, UUID shadowRunId) {
        EnumSet<ShadowRunSnapshotType> actual = repository.snapshots.stream()
                .filter(snapshot -> snapshot.shadowRunId().equals(shadowRunId))
                .map(ShadowRunSnapshot::snapshotType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ShadowRunSnapshotType.class)));
        assertEquals(EnumSet.of(
                ShadowRunSnapshotType.INPUT_MARKETDATA,
                ShadowRunSnapshotType.STRATEGY_DECISION,
                ShadowRunSnapshotType.RISK_PREFLIGHT,
                ShadowRunSnapshotType.ORDER_INTENT_PREVIEW
        ), actual);
    }

    private void assertStructuredDecisionTraceSnapshot(
            InMemoryShadowRunFactRepository repository,
            ShadowRunRunnerResult result
    ) {
        ShadowRunSnapshot snapshot = findSnapshot(repository, result.shadowRunId(), ShadowRunSnapshotType.STRATEGY_DECISION);
        assertSnapshotEnvelope(snapshot, result.traceId(), "shadow-strategy-decision-trace.v1");
        assertEquals("OBSERVE", snapshot.payload().path("strategyDecisionTrace").path("decisionType").asText());
        assertEquals("LOW", snapshot.payload().path("strategyDecisionTrace").path("confidence").asText());

        ShadowRunEvent event = findStrategySnapshotEvent(repository, result.shadowRunId());
        assertEquals("OBSERVE", event.metadata().path("decisionTraceSummary").path("decisionType").asText());
        assertEquals("OBSERVE", event.metadata().path("decisionTraceSummary").path("signalSide").asText());
    }

    private void assertRiskPreflightSnapshot(
            InMemoryShadowRunFactRepository repository,
            ShadowRunRunnerResult result,
            boolean allowed,
            boolean blocked
    ) {
        ShadowRunSnapshot snapshot = findSnapshot(repository, result.shadowRunId(), ShadowRunSnapshotType.RISK_PREFLIGHT);
        assertSnapshotEnvelope(snapshot, result.traceId(), "shadow-risk-preflight.v1");
        assertEquals(allowed, snapshot.payload().path("riskPreflight").path("allowed").asBoolean());
        assertEquals(blocked, snapshot.payload().path("riskPreflight").path("blocked").asBoolean());
        assertTrue(snapshot.payload().path("riskPreflight").path("ruleResults").isArray());
    }

    private void assertOrderIntentPreviewSnapshot(
            InMemoryShadowRunFactRepository repository,
            ShadowRunRunnerResult result
    ) {
        ShadowRunSnapshot snapshot = findSnapshot(repository, result.shadowRunId(), ShadowRunSnapshotType.ORDER_INTENT_PREVIEW);
        assertSnapshotEnvelope(snapshot, result.traceId(), "shadow-order-intent-preview.v1");
        assertTrue(snapshot.payload().path("orderIntentPreview").path("previewOnly").asBoolean());
        assertEquals("BTC-USDT", snapshot.payload().path("orderIntentPreview").path("symbol").asText());
        assertEquals("OBSERVE", snapshot.payload().path("orderIntentPreview").path("side").asText());
    }

    private void assertSnapshotEnvelope(ShadowRunSnapshot snapshot, String traceId, String schemaVersion) {
        assertEquals(traceId, snapshot.traceId());
        assertEquals(traceId, snapshot.payload().path("traceId").asText());
        assertEquals("LOCAL_CALLER_SUPPLIED_READONLY_INPUT", snapshot.source());
        assertEquals("LOCAL_CALLER_SUPPLIED_READONLY_INPUT", snapshot.payload().path("source").asText());
        assertEquals(schemaVersion, snapshot.schemaVersion());
        assertEquals(schemaVersion, snapshot.payload().path("schemaVersion").asText());
        assertTrue(snapshot.checksum().startsWith("sha256:"));
        assertTrue(snapshot.payload().path("checksum").asText().startsWith("sha256:"));
    }

    private ShadowRunSnapshot findSnapshot(
            InMemoryShadowRunFactRepository repository,
            UUID shadowRunId,
            ShadowRunSnapshotType snapshotType
    ) {
        return repository.snapshots.stream()
                .filter(snapshot -> snapshot.shadowRunId().equals(shadowRunId))
                .filter(snapshot -> snapshot.snapshotType() == snapshotType)
                .findFirst()
                .orElseThrow();
    }

    private ShadowRunEvent findStrategySnapshotEvent(InMemoryShadowRunFactRepository repository, UUID shadowRunId) {
        return repository.events.stream()
                .filter(event -> event.shadowRunId().equals(shadowRunId))
                .filter(event -> event.eventType() == ShadowRunEventType.SNAPSHOT_CAPTURED)
                .filter(event -> event.metadata().path("snapshotType").asText()
                        .equals(ShadowRunSnapshotType.STRATEGY_DECISION.name()))
                .findFirst()
                .orElseThrow();
    }

    private void assertHasTransition(
            List<ShadowRunEvent> events,
            ShadowRunStatus fromStatus,
            ShadowRunStatus toStatus
    ) {
        assertTrue(events.stream().anyMatch(event -> event.fromStatus() == fromStatus && event.toStatus() == toStatus),
                "expected transition " + fromStatus + " -> " + toStatus);
    }

    private static final class InMemoryShadowRunFactRepository implements ShadowRunFactRepository {
        private static final ObjectMapper JSON = new ObjectMapper();

        private final Map<UUID, ShadowRun> runs = new LinkedHashMap<>();
        private final Map<String, UUID> idempotencyKeys = new HashMap<>();
        private final List<ShadowRunEvent> events = new ArrayList<>();
        private final List<ShadowRunSnapshot> snapshots = new ArrayList<>();
        private final ShadowRunStateMachine stateMachine = new ShadowRunStateMachine();
        private ShadowRunSnapshotType failSnapshotType;
        private boolean failedSnapshot;
        private int createCalls;

        private void failOnceOnRiskPreflightSnapshot() {
            this.failSnapshotType = ShadowRunSnapshotType.RISK_PREFLIGHT;
        }

        @Override
        public ShadowRun create(ShadowRun run) {
            createCalls++;
            UUID existingId = idempotencyKeys.get(run.idempotencyKey());
            if (existingId != null) {
                return runs.get(existingId);
            }
            runs.put(run.id(), run);
            idempotencyKeys.put(run.idempotencyKey(), run.id());
            return run;
        }

        @Override
        public Optional<ShadowRun> findById(UUID shadowRunId) {
            return Optional.ofNullable(runs.get(shadowRunId));
        }

        @Override
        public Optional<ShadowRun> findByIdempotencyKey(String idempotencyKey) {
            UUID id = idempotencyKeys.get(idempotencyKey);
            return id == null ? Optional.empty() : findById(id);
        }

        @Override
        public void appendEvent(ShadowRunEvent event) {
            events.add(event);
        }

        @Override
        public void appendSnapshot(ShadowRunSnapshot snapshot) {
            if (snapshot.snapshotType() == failSnapshotType && !failedSnapshot) {
                failedSnapshot = true;
                throw new IllegalStateException("in-memory snapshot append failed");
            }
            snapshots.add(snapshot);
        }

        @Override
        public ShadowConsistencyReport createConsistencyReport(ShadowConsistencyReport report) {
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
            ShadowRun current = findById(shadowRunId).orElseThrow();
            if (current.version() != expectedVersion) {
                throw new ShadowRunOptimisticLockException(shadowRunId, expectedVersion);
            }
            stateMachine.transition(current.status(), toStatus);
            Instant updatedAt = Instant.parse("2026-07-06T10:00:00Z").plusSeconds(expectedVersion + 1);
            ShadowRun updated = copyWithStatus(current, toStatus, expectedVersion + 1, updatedAt);
            runs.put(shadowRunId, updated);
            events.add(new ShadowRunEvent(
                    UUID.randomUUID(),
                    shadowRunId,
                    eventTypeFor(toStatus),
                    current.status(),
                    toStatus,
                    reasonCode,
                    message,
                    JSON.createObjectNode(),
                    requestId,
                    traceId,
                    updatedAt
            ));
            return new ShadowRunStatusUpdateResult(shadowRunId, current.status(), toStatus, expectedVersion, expectedVersion + 1);
        }

        @Override
        public List<ShadowRunEvent> listEvents(UUID shadowRunId) {
            return events.stream().filter(event -> event.shadowRunId().equals(shadowRunId)).toList();
        }

        @Override
        public List<ShadowRunSnapshot> listSnapshots(UUID shadowRunId) {
            return snapshots.stream().filter(snapshot -> snapshot.shadowRunId().equals(shadowRunId)).toList();
        }

        @Override
        public Optional<ShadowConsistencyReport> findLatestReport(UUID shadowRunId) {
            return Optional.empty();
        }

        private ShadowRun copyWithStatus(
                ShadowRun current,
                ShadowRunStatus status,
                long version,
                Instant updatedAt
        ) {
            return new ShadowRun(
                    current.id(),
                    current.strategyVersionId(),
                    current.datasetId(),
                    current.evaluationId(),
                    current.publishId(),
                    current.paperRunId(),
                    status,
                    current.windowStart(),
                    current.windowEnd(),
                    current.sideEffectPolicy(),
                    current.noOrderSubmission(),
                    current.noCredentialAccess(),
                    current.noPrivateEndpoint(),
                    current.noLedgerMutation(),
                    current.noAccountMutation(),
                    current.noExternalPrivateIo(),
                    current.authorizationBoundary() == null
                            ? ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY
                            : current.authorizationBoundary(),
                    current.requestId(),
                    current.idempotencyKey(),
                    current.traceId(),
                    current.blockers(),
                    current.warnings(),
                    current.nextSteps(),
                    version,
                    current.createdAt(),
                    updatedAt,
                    status == ShadowRunStatus.RUNNING && current.startedAt() == null ? updatedAt : current.startedAt(),
                    status == ShadowRunStatus.STOPPED && current.stoppedAt() == null ? updatedAt : current.stoppedAt(),
                    status.terminal() && current.completedAt() == null ? updatedAt : current.completedAt()
            );
        }

        private ShadowRunEventType eventTypeFor(ShadowRunStatus toStatus) {
            return switch (toStatus) {
                case CREATED -> ShadowRunEventType.CREATED;
                case PRECHECKING -> ShadowRunEventType.PRECHECK_STARTED;
                case READY -> ShadowRunEventType.PRECHECK_PASSED;
                case RUNNING -> ShadowRunEventType.RUN_STARTED;
                case STOP_REQUESTED -> ShadowRunEventType.STOP_REQUESTED;
                case STOPPED -> ShadowRunEventType.STOPPED;
                case COMPLETED -> ShadowRunEventType.COMPLETED;
                case BLOCKED -> ShadowRunEventType.PRECHECK_BLOCKED;
                case FAILED -> ShadowRunEventType.FAILED;
                case CANCELLED -> ShadowRunEventType.CANCELLED;
            };
        }
    }
}

package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatusUpdateResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CreationPlan 映射必须保持 CREATED/RELEASE_BOUND、audit 可追溯且依赖图无 runtime/trading。 */
class ShadowRunMaterializationWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-11T01:02:03Z");

    @Test
    void shouldMapPlanToCreatedRunAndAppendAudit() {
        RecordingRepository repository = new RecordingRepository();
        ShadowRunCreationPlan plan = StrategyReleaseShadowRunMaterializationServiceTest.plan()
                .bindMaterializationCommand("operator-action-001");
        WriterFixture fixture = writer(repository, plan);

        ShadowRunMaterializationResult result = fixture.writer().materialize(plan, fixture.guard(), 41L);

        ShadowRun run = repository.requested;
        ShadowRunEvent event = repository.event;
        assertEquals(ShadowRunStatus.CREATED, run.status());
        assertEquals(ShadowRunReleaseBindingMode.RELEASE_BOUND, run.releaseBindingMode());
        assertEquals(plan.publishRecordId(), run.publishId());
        assertEquals(plan.artifactDigest(), run.artifactDigest());
        assertNull(run.startedAt());
        assertTrue(run.noOrderSubmission());
        assertTrue(run.noCredentialAccess());
        assertTrue(run.noPrivateEndpoint());
        assertTrue(run.noLedgerMutation());
        assertTrue(run.noAccountMutation());
        assertTrue(run.noExternalPrivateIo());
        assertEquals(41L, event.metadata().get("actorId").asLong());
        assertEquals(run.id(), event.shadowRunId());
        assertFalse(result.idempotentReplay());
    }

    @Test
    void shouldReturnExistingRunWithoutDuplicateAudit() {
        ShadowRunCreationPlan plan = StrategyReleaseShadowRunMaterializationServiceTest.plan()
                .bindMaterializationCommand("operator-action-001");
        RecordingRepository repository = new RecordingRepository();
        repository.existing = existing(plan);

        WriterFixture fixture = writer(repository, plan);
        ShadowRunMaterializationResult result = fixture.writer().materialize(plan, fixture.guard(), 41L);

        assertTrue(result.idempotentReplay());
        assertEquals(repository.existing.id(), result.shadowRunId());
        assertNull(repository.event);
    }

    @Test
    void shouldHaveNoRunnerSchedulerTradingOrNetworkDependency() {
        assertTrue(Arrays.stream(ShadowRunMaterializationWriter.class.getDeclaredFields())
                .map(Field::getType)
                .noneMatch(type -> type.getName().contains("Runner")
                        || type.getName().contains("Scheduler")
                        || type.getName().contains("TradingVenue")
                        || type.getName().contains("OrderCommand")
                        || type.getName().contains("Ledger")
                        || type.getName().contains("Account")
                        || type.getName().contains("Credential")
                        || type.getName().contains("Client")));
    }

    private static WriterFixture writer(ShadowRunFactRepository repository, ShadowRunCreationPlan plan) {
        StrategyReleaseAdmissionState state = new StrategyReleaseAdmissionState(
                plan.publishRecordId(),
                7L,
                1,
                plan.artifactDigest(),
                "c".repeat(64),
                plan.manifestSchemaVersion(),
                NOW.minusSeconds(60),
                NOW.minusSeconds(120),
                NOW.minusSeconds(60)
        );
        LatestDecisionFact validation = new LatestDecisionFact(
                plan.strategyVersionId(),
                plan.datasetId(),
                plan.evaluationId(),
                plan.publishRecordId(),
                "paper-run-001",
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                null,
                null,
                NOW.minusSeconds(30),
                NOW.minusSeconds(30)
        );
        StrategyReleaseAdmissionPreviewFacts facts = new StrategyReleaseAdmissionPreviewFacts(
                "backtest-run-001",
                validation,
                plan.windowStart(),
                plan.windowEnd(),
                new StrategyReleaseAdmissionPreviewFacts.PaperEvidenceIdentity(
                        "paper-run-001",
                        "STOPPED",
                        "SIM",
                        NOW.minusSeconds(30)
                ),
                null,
                null,
                plan.authorizationBoundary(),
                plan.sideEffectPolicy()
        );
        StrategyReleaseAdmissionStateRepository stateRepository = new StrategyReleaseAdmissionStateRepository() {
            @Override
            public StrategyReleaseAdmissionState loadByPublishRecordId(String publishRecordId) {
                return state;
            }

            @Override
            public StrategyReleaseAdmissionState bindVerifiedReleaseIdentity(VerifiedStrategyReleaseIdentity identity) {
                throw new UnsupportedOperationException();
            }
        };
        StrategyReleaseAdmissionPreviewFactsRepository factsRepository = publishRecordId -> facts;
        AdmissionGuardDecisionService decisionService = new AdmissionGuardDecisionService(
                new StrategyValidationOverviewQueryService(() -> new StrategyValidationOverviewFacts(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Optional.empty()
                ))
        );
        AdmissionGuardFingerprinter fingerprinter = new AdmissionGuardFingerprinter();
        AdmissionGuard guard = fingerprinter.issue(state, facts, NOW);
        ShadowRunMaterializationWriter writer = new ShadowRunMaterializationWriter(
                repository,
                new ObjectMapper(),
                directCoordinator(),
                stateRepository,
                factsRepository,
                decisionService,
                fingerprinter,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new WriterFixture(writer, guard);
    }

    private static AdmissionMutationCoordinator directCoordinator() {
        return new AdmissionMutationCoordinator() {
            @Override
            public <T> T withLockedAdmissionStates(Collection<String> publishRecordIds, Supplier<T> mutation) {
                return mutation.get();
            }
        };
    }

    private static ShadowRun existing(ShadowRunCreationPlan plan) {
        ObjectMapper mapper = new ObjectMapper();
        return new ShadowRun(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                plan.strategyVersionId(),
                plan.datasetId(),
                plan.evaluationId(),
                plan.publishRecordId(),
                plan.artifactDigest(),
                null,
                ShadowRunStatus.CREATED,
                plan.windowStart(),
                plan.windowEnd(),
                mapper.createObjectNode()
                        .put("policyVersion", "gate-x5-release-materialization.v1")
                        .put("noOrderSubmission", true)
                        .put("noCredentialAccess", true)
                        .put("noPrivateEndpoint", true)
                        .put("noLedgerMutation", true)
                        .put("noAccountMutation", true)
                        .put("noExternalPrivateIo", true),
                true,
                true,
                true,
                true,
                true,
                true,
                plan.authorizationBoundary(),
                plan.shadowRunIdempotencyKey(),
                plan.shadowRunIdempotencyKey(),
                plan.traceId(),
                mapper.createArrayNode(),
                mapper.createArrayNode(),
                mapper.createArrayNode(),
                0,
                NOW,
                NOW,
                null,
                null,
                null
        );
    }

    private static final class RecordingRepository implements ShadowRunFactRepository {
        private ShadowRun requested;
        private ShadowRun existing;
        private ShadowRunEvent event;

        @Override
        public ShadowRun create(ShadowRun run) {
            requested = run;
            return existing == null ? run : existing;
        }

        @Override
        public Optional<ShadowRun> findById(UUID shadowRunId) {
            return Optional.empty();
        }

        @Override
        public Optional<ShadowRun> findByIdempotencyKey(String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void appendEvent(ShadowRunEvent event) {
            this.event = event;
        }

        @Override
        public void appendSnapshot(ShadowRunSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShadowConsistencyReport createConsistencyReport(ShadowConsistencyReport report) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ShadowRunEvent> listEvents(UUID shadowRunId) {
            return List.of();
        }

        @Override
        public List<ShadowRunSnapshot> listSnapshots(UUID shadowRunId) {
            return List.of();
        }

        @Override
        public Optional<ShadowConsistencyReport> findLatestReport(UUID shadowRunId) {
            return Optional.empty();
        }
    }

    private record WriterFixture(ShadowRunMaterializationWriter writer, AdmissionGuard guard) {
    }
}

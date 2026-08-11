package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
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
        ShadowRunMaterializationWriter writer = writer(repository);
        ShadowRunCreationPlan plan = StrategyReleaseShadowRunMaterializationServiceTest.plan()
                .bindMaterializationCommand("operator-action-001");

        ShadowRunMaterializationResult result = writer.materialize(plan, 41L);

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

        ShadowRunMaterializationResult result = writer(repository).materialize(plan, 41L);

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

    private static ShadowRunMaterializationWriter writer(ShadowRunFactRepository repository) {
        return new ShadowRunMaterializationWriter(
                repository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
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
}

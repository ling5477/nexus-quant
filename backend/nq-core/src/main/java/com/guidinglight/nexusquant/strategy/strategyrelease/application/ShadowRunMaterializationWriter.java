package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将已验证的 {@link ShadowRunCreationPlan} 原子物化为现有 Shadow Run aggregate 与 CREATED audit event。
 *
 * <p>依赖图固定为本地 repository、JSON mapper 和 clock；不存在 runner、scheduler、交易、risk write、
 * ledger、account、credential、private client 或 network 依赖。事务只覆盖 DB insert + audit append，
 * artifact filesystem verification 和 admission evaluation 均在调用本 writer 之前完成。
 */
@Service
public class ShadowRunMaterializationWriter {

    private static final String POLICY_VERSION = "gate-x5-release-materialization.v1";

    private final ShadowRunFactRepository repository;
    private final ObjectMapper objectMapper;
    private final AdmissionMutationCoordinator admissionMutationCoordinator;
    private final StrategyReleaseAdmissionStateRepository admissionStateRepository;
    private final StrategyReleaseAdmissionPreviewFactsRepository factsRepository;
    private final AdmissionGuardDecisionService guardDecisionService;
    private final AdmissionGuardFingerprinter guardFingerprinter;
    private final Clock clock;

    @Autowired
    public ShadowRunMaterializationWriter(
            ShadowRunFactRepository repository,
            ObjectMapper objectMapper,
            AdmissionMutationCoordinator admissionMutationCoordinator,
            StrategyReleaseAdmissionStateRepository admissionStateRepository,
            StrategyReleaseAdmissionPreviewFactsRepository factsRepository,
            AdmissionGuardDecisionService guardDecisionService,
            AdmissionGuardFingerprinter guardFingerprinter
    ) {
        this(
                repository,
                objectMapper,
                admissionMutationCoordinator,
                admissionStateRepository,
                factsRepository,
                guardDecisionService,
                guardFingerprinter,
                Clock.systemUTC()
        );
    }

    ShadowRunMaterializationWriter(
            ShadowRunFactRepository repository,
            ObjectMapper objectMapper,
            AdmissionMutationCoordinator admissionMutationCoordinator,
            StrategyReleaseAdmissionStateRepository admissionStateRepository,
            StrategyReleaseAdmissionPreviewFactsRepository factsRepository,
            AdmissionGuardDecisionService guardDecisionService,
            AdmissionGuardFingerprinter guardFingerprinter,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.admissionMutationCoordinator = Objects.requireNonNull(
                admissionMutationCoordinator,
                "admissionMutationCoordinator must not be null"
        );
        this.admissionStateRepository = Objects.requireNonNull(
                admissionStateRepository,
                "admissionStateRepository must not be null"
        );
        this.factsRepository = Objects.requireNonNull(factsRepository, "factsRepository must not be null");
        this.guardDecisionService = Objects.requireNonNull(guardDecisionService, "guardDecisionService must not be null");
        this.guardFingerprinter = Objects.requireNonNull(guardFingerprinter, "guardFingerprinter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 原子创建一个 CREATED / RELEASE_BOUND Shadow Run；相同 plan key 重放返回同一事实。
     *
     * @param plan 唯一业务写入输入，必须已经绑定 materialization command identity
     * @param actorId 服务端认证 profile 的 actor id，仅进入脱敏 audit metadata
     * @return 新建或幂等命中的同一 Shadow Run
     */
    @Transactional
    public ShadowRunMaterializationResult materialize(
            ShadowRunCreationPlan plan,
            AdmissionGuard guard,
            long actorId
    ) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(guard, "guard must not be null");
        if (actorId <= 0) {
            throw new IllegalArgumentException("actorId must be positive");
        }

        return admissionMutationCoordinator.withLockedAdmissionStates(
                List.of(plan.publishRecordId()),
                () -> materializeUnderAdmissionLock(plan, guard, actorId)
        );
    }

    private ShadowRunMaterializationResult materializeUnderAdmissionLock(
            ShadowRunCreationPlan plan,
            AdmissionGuard guard,
            long actorId
    ) {
        StrategyReleaseAdmissionState currentState = admissionStateRepository.loadByPublishRecordId(
                plan.publishRecordId()
        );
        if (!guard.hasSupportedSchema()
                || currentState.guardSchemaVersion() != AdmissionGuard.SUPPORTED_GUARD_SCHEMA_VERSION
                || !guard.matchesState(currentState)
                || !planMatchesGuard(plan, guard)) {
            throw new AdmissionStaleException();
        }

        StrategyReleaseAdmissionPreviewFacts currentFacts = factsRepository.loadByPublishRecordId(
                plan.publishRecordId()
        );
        String currentFingerprint = guardFingerprinter.fingerprint(
                currentState,
                currentFacts,
                guard.evaluatedAt()
        );
        if (!Objects.equals(currentFingerprint, guard.admissionFingerprint())) {
            throw new AdmissionStaleException();
        }
        if (guardDecisionService.evaluate(currentFacts) != ReleaseToShadowAdmissionDecision.Decision.ELIGIBLE) {
            throw new ShadowRunMaterializationRejectedException(List.of("ADMISSION_BLOCKED"));
        }

        Instant now = Instant.now(clock);
        ShadowRun requested = toShadowRun(plan, now);
        ShadowRun persisted = repository.create(requested);
        boolean idempotentReplay = !persisted.id().equals(requested.id());
        if (!idempotentReplay) {
            repository.appendEvent(createdEvent(plan, persisted, actorId, now));
        }
        return new ShadowRunMaterializationResult(
                persisted.id(),
                persisted.publishId(),
                persisted.artifactDigest(),
                persisted.releaseBindingMode(),
                persisted.status(),
                persisted.createdAt(),
                idempotentReplay
        );
    }

    private ShadowRun toShadowRun(ShadowRunCreationPlan plan, Instant now) {
        ArrayNode empty = objectMapper.createArrayNode();
        ObjectNode policy = objectMapper.createObjectNode()
                .put("policyVersion", POLICY_VERSION)
                .put("inputReference", plan.inputReference())
                .put("provenanceReference", plan.provenanceReference())
                .put("manifestSchemaVersion", plan.manifestSchemaVersion())
                .put("noOrderSubmission", plan.sideEffectPolicy().noOrderSubmission())
                .put("noCredentialAccess", plan.sideEffectPolicy().noCredentialAccess())
                .put("noPrivateEndpoint", plan.sideEffectPolicy().noPrivateEndpoint())
                .put("noLedgerMutation", plan.sideEffectPolicy().noLedgerMutation())
                .put("noAccountMutation", plan.sideEffectPolicy().noAccountMutation())
                .put("noExternalPrivateIo", plan.sideEffectPolicy().noExternalPrivateIo());
        return new ShadowRun(
                UUID.randomUUID(),
                plan.strategyVersionId(),
                plan.datasetId(),
                plan.evaluationId(),
                plan.publishRecordId(),
                plan.artifactDigest(),
                null,
                ShadowRunStatus.CREATED,
                plan.windowStart(),
                plan.windowEnd(),
                policy,
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
                empty,
                empty.deepCopy(),
                empty.deepCopy(),
                0,
                now,
                now,
                null,
                null,
                null
        );
    }

    private boolean planMatchesGuard(ShadowRunCreationPlan plan, AdmissionGuard guard) {
        return Objects.equals(plan.publishRecordId(), guard.publishRecordId())
                && Objects.equals(plan.artifactDigest(), guard.releaseArtifactDigest())
                && Objects.equals(plan.manifestSchemaVersion(), guard.manifestSchemaVersion())
                && Objects.equals(plan.strategyVersionId(), guard.strategyVersionId())
                && Objects.equals(plan.datasetId(), guard.datasetId())
                && Objects.equals(plan.evaluationId(), guard.evaluationId())
                && Objects.equals(plan.windowStart(), guard.windowStart())
                && Objects.equals(plan.windowEnd(), guard.windowEnd())
                && plan.authorizationBoundary() == guard.authorizationBoundary()
                && Objects.equals(plan.sideEffectPolicy(), guard.sideEffectPolicy());
    }

    private ShadowRunEvent createdEvent(
            ShadowRunCreationPlan plan,
            ShadowRun persisted,
            long actorId,
            Instant now
    ) {
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("actorId", actorId)
                .put("publishRecordId", plan.publishRecordId())
                .put("releaseBindingMode", ShadowRunReleaseBindingMode.RELEASE_BOUND.name())
                .put("idempotencyIdentity", plan.shadowRunIdempotencyKey())
                .put("manifestSchemaVersion", plan.manifestSchemaVersion())
                .put("inputReference", plan.inputReference())
                .put("provenanceReference", plan.provenanceReference())
                .put("materializationOnly", true)
                .put("shadowRunStarted", false)
                .put("tradingAuthorized", false);
        return new ShadowRunEvent(
                UUID.randomUUID(),
                persisted.id(),
                ShadowRunEventType.CREATED,
                null,
                ShadowRunStatus.CREATED,
                "RELEASE_TO_SHADOW_MATERIALIZED",
                "Verified Strategy Release materialized as a non-started Shadow Run.",
                metadata,
                plan.shadowRunIdempotencyKey(),
                plan.traceId(),
                now
        );
    }
}

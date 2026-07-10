package com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence;

import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewQueryService;
import com.guidinglight.nexusquant.strategy.application.validationoperations.runtimeevidence.ValidationOperationsRuntimeEvidenceOverviewReadModel.RuntimeEvidenceSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ValidationOperationsRuntimeEvidenceOverviewQueryService 聚合五个既有诊断 overview 的 evidence metadata。
 *
 * <p>Why: Validation Operations 需要一个固定顺序的只读证据入口，但不能绕过来源的 QueryService、复制底层
 * freshness 规则或把 aggregate 误写成交易就绪状态。该服务只调用来源的 public read API 一次，不依赖
 * repository、JDBC、HTTP client、scheduler、runner、adapter、credential 或交易写侧。
 */
@Service
public class ValidationOperationsRuntimeEvidenceOverviewQueryService {

    private static final String AGGREGATE_SOURCE = "LOCAL_VALIDATION_OPERATIONS_RUNTIME_EVIDENCE";

    private final EvidenceMetadataSource shadowValidationWorkflowSource;
    private final EvidenceMetadataSource shadowRunSource;
    private final EvidenceMetadataSource consistencyEvidenceSource;
    private final EvidenceMetadataSource incidentReplayReviewSource;
    private final EvidenceMetadataSource evaluationArtifactPreviewSource;
    private final Clock clock;

    /**
     * 创建生产聚合服务；所有来源都由 Spring 以其既有只读实现注入。
     */
    @Autowired
    public ValidationOperationsRuntimeEvidenceOverviewQueryService(
            ShadowValidationWorkflowOverviewQueryService shadowValidationWorkflowQueryService,
            ShadowRunOverviewQueryService shadowRunOverviewQueryService,
            ConsistencyEvidenceOverviewQueryService consistencyEvidenceOverviewQueryService,
            IncidentReplayReviewOverviewQueryService incidentReplayReviewOverviewQueryService,
            PythonEvaluationArtifactPreviewOverviewQueryService evaluationArtifactPreviewOverviewQueryService
    ) {
        this(
                traceId -> Objects.requireNonNull(shadowValidationWorkflowQueryService, "shadowValidationWorkflowQueryService must not be null")
                        .overview(traceId).evidenceMetadata(),
                traceId -> Objects.requireNonNull(shadowRunOverviewQueryService, "shadowRunOverviewQueryService must not be null")
                        .overview(traceId).evidenceMetadata(),
                traceId -> Objects.requireNonNull(consistencyEvidenceOverviewQueryService, "consistencyEvidenceOverviewQueryService must not be null")
                        .overview(traceId).evidenceMetadata(),
                traceId -> Objects.requireNonNull(incidentReplayReviewOverviewQueryService, "incidentReplayReviewOverviewQueryService must not be null")
                        .overview(traceId).evidenceMetadata(),
                traceId -> Objects.requireNonNull(evaluationArtifactPreviewOverviewQueryService, "evaluationArtifactPreviewOverviewQueryService must not be null")
                        .overview(traceId).evidenceMetadata(),
                Clock.systemUTC()
        );
    }

    ValidationOperationsRuntimeEvidenceOverviewQueryService(
            EvidenceMetadataSource shadowValidationWorkflowSource,
            EvidenceMetadataSource shadowRunSource,
            EvidenceMetadataSource consistencyEvidenceSource,
            EvidenceMetadataSource incidentReplayReviewSource,
            EvidenceMetadataSource evaluationArtifactPreviewSource,
            Clock clock
    ) {
        this.shadowValidationWorkflowSource = Objects.requireNonNull(shadowValidationWorkflowSource, "shadowValidationWorkflowSource must not be null");
        this.shadowRunSource = Objects.requireNonNull(shadowRunSource, "shadowRunSource must not be null");
        this.consistencyEvidenceSource = Objects.requireNonNull(consistencyEvidenceSource, "consistencyEvidenceSource must not be null");
        this.incidentReplayReviewSource = Objects.requireNonNull(incidentReplayReviewSource, "incidentReplayReviewSource must not be null");
        this.evaluationArtifactPreviewSource = Objects.requireNonNull(evaluationArtifactPreviewSource, "evaluationArtifactPreviewSource must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 Validation Operations Runtime Evidence Overview。
     *
     * <p>事务：read-only。副作用：无。每个来源严格调用一次并保留既有 metadata；来源异常直接沿用现有
     * 异常链路，不吞掉后伪造 AVAILABLE、FRESH 或 HTTP 200 成功结果。
     *
     * @param traceId 当前请求 trace id，仅用于只读追踪
     * @return 固定五来源顺序的 runtime evidence read model
     */
    @Transactional(readOnly = true)
    public ValidationOperationsRuntimeEvidenceOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }

        List<RuntimeEvidenceSource> sources = List.of(
                source(
                        "SHADOW_VALIDATION_WORKFLOW",
                        "Shadow Validation Workflow",
                        shadowValidationWorkflowSource.load(traceId)
                ),
                source(
                        "SHADOW_RUNS",
                        "Shadow Runs",
                        shadowRunSource.load(traceId)
                ),
                source(
                        "CONSISTENCY_EVIDENCE",
                        "Consistency Evidence",
                        consistencyEvidenceSource.load(traceId)
                ),
                source(
                        "INCIDENT_REPLAY_REVIEW",
                        "Incident / Replay Review",
                        incidentReplayReviewSource.load(traceId)
                ),
                source(
                        "EVALUATION_ARTIFACT_PREVIEW",
                        "Evaluation Artifact Preview",
                        evaluationArtifactPreviewSource.load(traceId)
                )
        );
        ReadModelEvidenceMetadata metadata = aggregateMetadata(sources);

        return new ValidationOperationsRuntimeEvidenceOverviewReadModel(
                clock.instant(),
                metadata,
                sources.size(),
                countAvailability(sources, Availability.AVAILABLE),
                countAvailability(sources, Availability.PARTIAL),
                countAvailability(sources, Availability.UNAVAILABLE),
                countAvailability(sources, Availability.UNKNOWN),
                countFreshness(sources, FreshnessStatus.FRESH),
                countFreshness(sources, FreshnessStatus.STALE),
                countFreshness(sources, FreshnessStatus.UNKNOWN),
                sources,
                traceId
        );
    }

    private RuntimeEvidenceSource source(
            String sourceKey,
            String displayName,
            ReadModelEvidenceMetadata evidenceMetadata
    ) {
        return new RuntimeEvidenceSource(sourceKey, displayName, evidenceMetadata);
    }

    /**
     * 基于来源已有 metadata 计算 aggregate 状态，不重算任何底层业务事实或来源 stale threshold。
     */
    private ReadModelEvidenceMetadata aggregateMetadata(List<RuntimeEvidenceSource> sources) {
        Availability availability = aggregateAvailability(sources);
        Instant lastCalculatedAt = sources.stream()
                .map(source -> source.evidenceMetadata().lastCalculatedAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        FreshnessStatus freshnessStatus = aggregateFreshness(sources, lastCalculatedAt);
        Long ageSeconds = ageSeconds(lastCalculatedAt);
        String staleReason = staleReason(freshnessStatus, lastCalculatedAt);

        return new ReadModelEvidenceMetadata(
                AGGREGATE_SOURCE,
                availability,
                lastCalculatedAt,
                freshnessStatus,
                ageSeconds,
                null,
                staleReason,
                true,
                true,
                true,
                true
        );
    }

    private Availability aggregateAvailability(List<RuntimeEvidenceSource> sources) {
        if (sources.stream().allMatch(source -> source.evidenceMetadata().availability() == Availability.AVAILABLE)) {
            return Availability.AVAILABLE;
        }
        if (sources.stream().allMatch(source -> source.evidenceMetadata().availability() == Availability.UNAVAILABLE)) {
            return Availability.UNAVAILABLE;
        }
        if (sources.stream().allMatch(source -> source.evidenceMetadata().availability() == Availability.UNKNOWN)) {
            return Availability.UNKNOWN;
        }
        boolean hasAvailableOrPartial = sources.stream().anyMatch(source -> {
            Availability availability = source.evidenceMetadata().availability();
            return availability == Availability.AVAILABLE || availability == Availability.PARTIAL;
        });
        return hasAvailableOrPartial ? Availability.PARTIAL : Availability.UNKNOWN;
    }

    private FreshnessStatus aggregateFreshness(List<RuntimeEvidenceSource> sources, Instant lastCalculatedAt) {
        if (lastCalculatedAt == null || lastCalculatedAt.isAfter(clock.instant())) {
            return FreshnessStatus.UNKNOWN;
        }
        if (sources.stream().anyMatch(source -> source.evidenceMetadata().freshnessStatus() == FreshnessStatus.STALE)) {
            return FreshnessStatus.STALE;
        }
        boolean allAvailableAndFresh = sources.stream().allMatch(source -> source.evidenceMetadata().availability() == Availability.AVAILABLE
                && source.evidenceMetadata().freshnessStatus() == FreshnessStatus.FRESH);
        return allAvailableAndFresh ? FreshnessStatus.FRESH : FreshnessStatus.UNKNOWN;
    }

    private Long ageSeconds(Instant lastCalculatedAt) {
        if (lastCalculatedAt == null || lastCalculatedAt.isAfter(clock.instant())) {
            return null;
        }
        return Duration.between(lastCalculatedAt, clock.instant()).getSeconds();
    }

    private String staleReason(FreshnessStatus freshnessStatus, Instant lastCalculatedAt) {
        if (lastCalculatedAt == null) {
            return "INCOMPLETE_OR_UNKNOWN_EVIDENCE_SOURCES";
        }
        if (lastCalculatedAt.isAfter(clock.instant())) {
            return "LAST_CALCULATED_AT_IN_FUTURE";
        }
        return switch (freshnessStatus) {
            case STALE -> "ONE_OR_MORE_EVIDENCE_SOURCES_STALE";
            case UNKNOWN -> "INCOMPLETE_OR_UNKNOWN_EVIDENCE_SOURCES";
            case FRESH -> null;
        };
    }

    private long countAvailability(List<RuntimeEvidenceSource> sources, Availability expected) {
        return sources.stream().filter(source -> source.evidenceMetadata().availability() == expected).count();
    }

    private long countFreshness(List<RuntimeEvidenceSource> sources, FreshnessStatus expected) {
        return sources.stream().filter(source -> source.evidenceMetadata().freshnessStatus() == expected).count();
    }

    /**
     * 仅供 service 内部隔离既有 QueryService 调用；它不代表 repository、HTTP client 或新的数据来源。
     */
    @FunctionalInterface
    interface EvidenceMetadataSource {
        ReadModelEvidenceMetadata load(String traceId);
    }
}

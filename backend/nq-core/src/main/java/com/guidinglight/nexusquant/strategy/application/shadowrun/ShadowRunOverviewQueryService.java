package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadataCalculator;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel.LatestConsistency;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel.LatestRun;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel.NextStep;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewQueryPort;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ShadowRunOverviewQueryService 计算 GateS-1 Shadow Run overview read model。
 *
 * <p>Why: GateS-1 需要把 GateR 已落地的本地 Shadow Run facts 从单 run 查询推进到整体运行
 * 状态可观测。该 service 只依赖 {@link ShadowRunOverviewQueryPort} 的 SELECT-only 投影，
 * 固定返回 fail-closed 边界，不调用写侧 repository、runner、scheduler、adapter、credential、
 * order、account 或 ledger 逻辑。
 */
@Service
public class ShadowRunOverviewQueryService {

    private final ShadowRunOverviewQueryPort queryPort;
    private final Clock clock;

    /**
     * 生产构造器。
     *
     * @param queryPort SELECT-only overview query port
     */
    @Autowired
    public ShadowRunOverviewQueryService(ShadowRunOverviewQueryPort queryPort) {
        this(queryPort, Clock.systemUTC());
    }

    ShadowRunOverviewQueryService(ShadowRunOverviewQueryPort queryPort, Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 Shadow Run overview。
     *
     * <p>事务：read-only。副作用：无；不会写库、不会追加事件、不会创建 report、不会启动 runner。
     * 失败模式：空数据返回稳定 overview；缺 consistency report 时返回 `UNKNOWN` severity 和诊断 warning。
     *
     * @param traceId 当前请求 trace id，用于响应追踪，不用于幂等或授权
     * @return GateS-1 overview read model
     */
    @Transactional(readOnly = true)
    public ShadowRunOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        ShadowRunOverviewFacts facts = queryPort.loadOverviewFacts();
        ShadowConsistencyReport latestReport = facts.latestConsistency().orElse(null);
        return new ShadowRunOverviewReadModel(
                clock.instant(),
                evidenceMetadata(facts),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                facts.totalRuns(),
                facts.runningRuns(),
                facts.blockedRuns(),
                facts.failedRuns(),
                facts.completedRuns(),
                facts.staleRuns(),
                facts.latestRun().map(this::latestRun).orElse(null),
                latestReport == null ? null : latestConsistency(latestReport),
                severity(latestReport),
                blockers(),
                warnings(facts, latestReport),
                nextSteps(facts, latestReport),
                evidenceAnchors(facts),
                traceId
        );
    }

    private ReadModelEvidenceMetadata evidenceMetadata(ShadowRunOverviewFacts facts) {
        Instant lastCalculatedAt = Stream.of(
                        facts.latestRun().map(ShadowRun::updatedAt),
                        facts.latestConsistency().map(ShadowConsistencyReport::generatedAt),
                        facts.latestEvent().map(ShadowRunOverviewEvidenceFact::sourceTimestamp),
                        facts.latestSnapshot().map(ShadowRunOverviewEvidenceFact::sourceTimestamp)
                )
                .flatMap(java.util.Optional::stream)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        boolean hasAnyFacts = facts.totalRuns() > 0
                || facts.latestRun().isPresent()
                || facts.latestConsistency().isPresent()
                || facts.latestEvent().isPresent()
                || facts.latestSnapshot().isPresent();
        boolean completeEvidence = facts.latestRun().isPresent()
                && facts.latestConsistency().isPresent()
                && facts.latestEvent().isPresent()
                && facts.latestSnapshot().isPresent()
                && facts.staleRuns() == 0;
        Availability availability = !hasAnyFacts
                ? Availability.UNAVAILABLE
                : completeEvidence ? Availability.AVAILABLE : Availability.PARTIAL;
        return new ReadModelEvidenceMetadataCalculator(clock).calculate(
                "LOCAL_DB_SHADOW_FACTS",
                availability,
                lastCalculatedAt,
                null
        );
    }

    private LatestRun latestRun(ShadowRun run) {
        return new LatestRun(
                run.id(),
                run.strategyVersionId(),
                run.datasetId(),
                run.paperRunId(),
                run.status().name(),
                run.authorizationBoundary().name(),
                run.noOrderSubmission(),
                run.noCredentialAccess(),
                run.noPrivateEndpoint(),
                run.noLedgerMutation(),
                run.noAccountMutation(),
                run.noExternalPrivateIo(),
                run.createdAt(),
                run.updatedAt(),
                run.startedAt(),
                run.completedAt()
        );
    }

    private LatestConsistency latestConsistency(ShadowConsistencyReport report) {
        return new LatestConsistency(
                report.id(),
                report.shadowRunId(),
                report.paperRunId(),
                report.comparisonStatus().name(),
                report.metricDelta(),
                report.divergenceReasons(),
                report.limitations(),
                report.generatedAt(),
                report.traceId()
        );
    }

    private ShadowRunOverviewDivergenceSeverity severity(ShadowConsistencyReport report) {
        if (report == null) {
            return ShadowRunOverviewDivergenceSeverity.UNKNOWN;
        }
        ShadowConsistencyComparisonStatus status = report.comparisonStatus();
        return switch (status) {
            case CONSISTENT -> ShadowRunOverviewDivergenceSeverity.NONE;
            case PARTIAL -> report.divergenceReasons().size() == 0
                    ? ShadowRunOverviewDivergenceSeverity.LOW
                    : ShadowRunOverviewDivergenceSeverity.MEDIUM;
            case NOT_COMPARABLE -> ShadowRunOverviewDivergenceSeverity.MEDIUM;
            case DIVERGED -> ShadowRunOverviewDivergenceSeverity.HIGH;
            case FAILED -> ShadowRunOverviewDivergenceSeverity.CRITICAL;
        };
    }

    private List<BoundaryMessage> blockers() {
        return List.of(
                message("LIVE_DISABLED", "CRITICAL", "LIVE disabled; overview is diagnostic only.", "SYSTEM_BOUNDARY", null),
                message("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "Real provider is not implemented.", "SYSTEM_BOUNDARY", null),
                message("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "Private trading adapter is not implemented.", "SYSTEM_BOUNDARY", null)
        );
    }

    private List<BoundaryMessage> warnings(ShadowRunOverviewFacts facts, ShadowConsistencyReport latestReport) {
        List<BoundaryMessage> warnings = new ArrayList<>();
        warnings.add(message("SHADOW_RUN_DIAGNOSTIC_ONLY", "INFO", "Shadow Run overview is diagnostic only and has no side effects.", "SYSTEM_BOUNDARY", null));
        if (facts.totalRuns() == 0) {
            warnings.add(message("SHADOW_RUN_MISSING", "WARNING", "No local Shadow Run facts exist yet.", "SHADOW_RUN", null));
        }
        if (latestReport == null) {
            warnings.add(message("CONSISTENCY_REPORT_MISSING", "WARNING", "No local consistency report exists yet.", "SHADOW_CONSISTENCY_REPORT", null));
        }
        if (facts.staleRuns() > 0) {
            warnings.add(message("STALE_EVIDENCE", "WARNING", "At least one Shadow Run is missing a local snapshot or consistency report.", "SHADOW_RUN", null));
        }
        return warnings;
    }

    private List<NextStep> nextSteps(ShadowRunOverviewFacts facts, ShadowConsistencyReport latestReport) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(new NextStep(
                "REVIEW_SHADOW_OVERVIEW",
                "backend",
                "review_shadow_overview",
                "overview counts, latest run and boundary blockers reviewed",
                true
        ));
        facts.latestRun().ifPresent(run -> steps.add(new NextStep(
                "INSPECT_LATEST_RUN",
                "backend",
                "inspect_latest_shadow_run",
                "latest run detail, events and snapshots inspected",
                false
        )));
        if (latestReport == null) {
            steps.add(new NextStep(
                    "INSPECT_CONSISTENCY_INPUTS",
                    "backend",
                    "inspect_shadow_consistency_inputs",
                    "missing local consistency report explained",
                    false
            ));
        } else {
            steps.add(new NextStep(
                    "COMPARE_PAPER_SHADOW",
                    "backend",
                    "compare_paper_shadow_report",
                    "latest local consistency report reviewed",
                    false
            ));
        }
        steps.add(new NextStep(
                "IMPLEMENT_FRONTEND_READONLY_OVERVIEW",
                "frontend",
                "implement_frontend_readonly_overview",
                "read-only page displays diagnostic boundary and no trading action",
                false
        ));
        return steps;
    }

    private List<EvidenceAnchor> evidenceAnchors(ShadowRunOverviewFacts facts) {
        List<EvidenceAnchor> anchors = new ArrayList<>();
        facts.latestRun().ifPresent(run -> {
            anchors.add(new EvidenceAnchor("SHADOW_RUN", run.id().toString(), Long.toString(run.version()), run.updatedAt(), null));
            anchors.add(new EvidenceAnchor("STRATEGY_VERSION", run.strategyVersionId(), null, null, null));
            anchors.add(new EvidenceAnchor("DATASET", run.datasetId().toString(), null, null, null));
            if (run.paperRunId() != null && !run.paperRunId().isBlank()) {
                anchors.add(new EvidenceAnchor("PAPER_RUN", run.paperRunId(), null, null, null));
            }
        });
        facts.latestConsistency().ifPresent(report -> anchors.add(new EvidenceAnchor(
                "SHADOW_CONSISTENCY_REPORT",
                report.id().toString(),
                report.comparisonStatus().name(),
                report.generatedAt(),
                null
        )));
        facts.latestEvent().map(this::evidenceAnchor).ifPresent(anchors::add);
        facts.latestSnapshot().map(this::evidenceAnchor).ifPresent(anchors::add);
        return anchors;
    }

    private EvidenceAnchor evidenceAnchor(ShadowRunOverviewEvidenceFact fact) {
        return new EvidenceAnchor(
                fact.sourceType(),
                fact.sourceId(),
                fact.sourceVersion(),
                fact.sourceTimestamp(),
                fact.checksum()
        );
    }

    private BoundaryMessage message(String code, String severity, String message, String sourceType, String sourceId) {
        return new BoundaryMessage(code, severity, message, sourceType, sourceId);
    }
}

package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel.ConsistencyReportSummary;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel.EventSummary;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel.NextStep;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel.ShadowRunSummary;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel.SnapshotSummary;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestEventFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestSnapshotFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.SnapshotFacts;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownQueryPort;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PaperShadowConsistencyDrilldownQueryService 组装 GateS-2 Paper vs Shadow consistency drilldown。
 *
 * <p>Why：GateS-2 需要按 shadowRunId 深挖 consistency 证据，但仍必须保持 GET-only、SELECT-only
 * 和 no-side-effect。该 service 只依赖 {@link PaperShadowConsistencyDrilldownQueryPort}，不会依赖
 * runner、scheduler、adapter、credential、order、account 或 ledger 逻辑。
 */
@Service
public class PaperShadowConsistencyDrilldownQueryService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final PaperShadowConsistencyDrilldownQueryPort queryPort;
    private final Clock clock;

    /**
     * 生产构造器。
     *
     * @param queryPort SELECT-only drilldown query port
     */
    @Autowired
    public PaperShadowConsistencyDrilldownQueryService(PaperShadowConsistencyDrilldownQueryPort queryPort) {
        this(queryPort, Clock.systemUTC());
    }

    PaperShadowConsistencyDrilldownQueryService(PaperShadowConsistencyDrilldownQueryPort queryPort, Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询单个 Shadow Run 的 consistency drilldown。
     *
     * <p>事务：read-only。副作用：无；不会写库、追加事件、创建 report、启动 runner/scheduler 或调用外部系统。
     * 失败模式：shadowRunId 不存在时抛出 read-only not-found，由 API 层映射为 HTTP 404；缺 report 时返回
     * `NO_REPORT` 和 warning/nextStep，而不是自动生成 report。
     *
     * @param shadowRunId 本地 Shadow Run id
     * @param traceId     当前请求 trace id，只用于响应追踪
     * @return GateS-2 drilldown read model
     */
    @Transactional(readOnly = true)
    public PaperShadowConsistencyDrilldownReadModel drilldown(UUID shadowRunId, String traceId) {
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        PaperShadowConsistencyDrilldownFacts facts = queryPort.loadDrilldownFacts(shadowRunId);
        ShadowRun run = facts.shadowRun()
                .orElseThrow(() -> new ShadowRunReadOnlyNotFoundException("shadow run not found: " + shadowRunId));
        ShadowConsistencyReport report = facts.latestConsistency().orElse(null);

        return new PaperShadowConsistencyDrilldownReadModel(
                clock.instant(),
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                shadowRun(run),
                report == null ? null : latestConsistency(report),
                comparisonStatus(report),
                divergenceSeverity(report),
                report == null ? JSON.objectNode() : report.metricDelta(),
                report == null ? JSON.arrayNode() : report.divergenceReasons(),
                report == null ? JSON.arrayNode() : report.limitations(),
                snapshotSummary(facts.snapshotFacts()),
                eventSummary(facts),
                blockers(run),
                warnings(facts, report),
                nextSteps(facts, report),
                evidenceAnchors(run, report, facts),
                traceId
        );
    }

    private ShadowRunSummary shadowRun(ShadowRun run) {
        return new ShadowRunSummary(
                run.id(),
                run.strategyVersionId(),
                run.datasetId(),
                run.evaluationId(),
                run.publishId(),
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

    private ConsistencyReportSummary latestConsistency(ShadowConsistencyReport report) {
        return new ConsistencyReportSummary(
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

    private PaperShadowConsistencyDrilldownComparisonStatus comparisonStatus(ShadowConsistencyReport report) {
        if (report == null) {
            return PaperShadowConsistencyDrilldownComparisonStatus.NO_REPORT;
        }
        return PaperShadowConsistencyDrilldownComparisonStatus.valueOf(report.comparisonStatus().name());
    }

    private ShadowRunOverviewDivergenceSeverity divergenceSeverity(ShadowConsistencyReport report) {
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

    private SnapshotSummary snapshotSummary(SnapshotFacts facts) {
        return new SnapshotSummary(
                facts.totalSnapshots(),
                facts.inputMarketdataSnapshots(),
                facts.strategyDecisionSnapshots(),
                facts.riskPreflightSnapshots(),
                facts.orderIntentPreviewSnapshots(),
                facts.latestSnapshotAt(),
                facts.latestSnapshotTypes()
        );
    }

    private EventSummary eventSummary(PaperShadowConsistencyDrilldownFacts facts) {
        LatestEventFact latest = facts.latestEvent().orElse(null);
        return new EventSummary(
                facts.totalEvents(),
                latest == null ? null : latest.createdAt(),
                latest == null ? null : latest.eventType(),
                latest == null ? null : latest.reasonCode()
        );
    }

    private List<BoundaryMessage> blockers(ShadowRun run) {
        List<BoundaryMessage> blockers = new ArrayList<>();
        blockers.add(message("LIVE_DISABLED", "CRITICAL", "LIVE is disabled; drilldown is diagnostic only.", "SYSTEM_BOUNDARY", null));
        blockers.add(message("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "Real provider is not implemented.", "SYSTEM_BOUNDARY", null));
        blockers.add(message("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "Private trading adapter is not implemented.", "SYSTEM_BOUNDARY", null));
        blockers.add(message("SHADOW_RUN_DIAGNOSTIC_ONLY", "CRITICAL", "Shadow Run is diagnostic only.", "SHADOW_RUN", run.id().toString()));
        blockers.add(message("NOT_TRADING_AUTHORIZATION", "CRITICAL", "Consistency drilldown is not trading authorization.", "SYSTEM_BOUNDARY", null));
        return blockers;
    }

    private List<BoundaryMessage> warnings(
            PaperShadowConsistencyDrilldownFacts facts,
            ShadowConsistencyReport report
    ) {
        List<BoundaryMessage> warnings = new ArrayList<>();
        if (report == null) {
            warnings.add(message(
                    "NO_CONSISTENCY_REPORT",
                    "WARNING",
                    "No local consistency report exists for this shadow run.",
                    "SHADOW_CONSISTENCY_REPORT",
                    null
            ));
        }
        if (snapshotsIncomplete(facts.snapshotFacts())) {
            warnings.add(message(
                    "INCOMPLETE_SNAPSHOT_EVIDENCE",
                    "WARNING",
                    "Snapshot evidence is incomplete for this shadow run.",
                    "SHADOW_SNAPSHOT",
                    null
            ));
        }
        return warnings;
    }

    private List<NextStep> nextSteps(
            PaperShadowConsistencyDrilldownFacts facts,
            ShadowConsistencyReport report
    ) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(new NextStep(
                "REVIEW_DRILLDOWN_BOUNDARY",
                "backend",
                "Review diagnostic-only and not-trading-authorization boundary",
                "boundary blockers reviewed before any future GateS action",
                true
        ));
        if (report == null) {
            steps.add(new NextStep(
                    "GENERATE_OR_INSPECT_CONSISTENCY_REPORT_FUTURE_BATCH",
                    "backend",
                    "Generate or inspect consistency report in future GateS batch",
                    "latest consistency report exists or absence is explained",
                    false
            ));
        } else {
            steps.add(new NextStep(
                    "COMPARE_LATEST_CONSISTENCY_REPORT",
                    "backend",
                    "Compare latest consistency report",
                    "metricDelta, divergenceReasons and limitations reviewed",
                    false
            ));
        }
        if (snapshotsIncomplete(facts.snapshotFacts())) {
            steps.add(new NextStep(
                    "INSPECT_SHADOW_SNAPSHOTS",
                    "backend",
                    "Inspect shadow snapshots",
                    "required snapshot types are present or gaps are explained",
                    false
            ));
        }
        return steps;
    }

    private boolean snapshotsIncomplete(SnapshotFacts facts) {
        return facts.totalSnapshots() == 0
                || facts.inputMarketdataSnapshots() == 0
                || facts.strategyDecisionSnapshots() == 0
                || facts.riskPreflightSnapshots() == 0
                || facts.orderIntentPreviewSnapshots() == 0;
    }

    private List<EvidenceAnchor> evidenceAnchors(
            ShadowRun run,
            ShadowConsistencyReport report,
            PaperShadowConsistencyDrilldownFacts facts
    ) {
        List<EvidenceAnchor> anchors = new ArrayList<>();
        anchors.add(new EvidenceAnchor("SHADOW_RUN", run.id().toString(), Long.toString(run.version()), run.updatedAt(), null));
        anchors.add(new EvidenceAnchor("STRATEGY_VERSION", run.strategyVersionId(), null, null, null));
        anchors.add(new EvidenceAnchor("DATASET", run.datasetId().toString(), null, null, null));
        addOptionalAnchor(anchors, "EVALUATION", run.evaluationId());
        addOptionalAnchor(anchors, "PUBLISH", run.publishId());
        addOptionalAnchor(anchors, "PAPER_RUN", run.paperRunId());
        if (report != null) {
            anchors.add(new EvidenceAnchor(
                    "SHADOW_CONSISTENCY_REPORT",
                    report.id().toString(),
                    report.comparisonStatus().name(),
                    report.generatedAt(),
                    null
            ));
        }
        facts.latestEvent().ifPresent(event -> anchors.add(new EvidenceAnchor(
                "SHADOW_EVENT",
                event.eventId(),
                event.eventType(),
                event.createdAt(),
                null
        )));
        facts.latestSnapshot().ifPresent(snapshot -> anchors.add(new EvidenceAnchor(
                "SHADOW_SNAPSHOT",
                snapshot.snapshotId(),
                snapshot.schemaVersion(),
                snapshot.capturedAt(),
                snapshot.checksum()
        )));
        return anchors;
    }

    private void addOptionalAnchor(List<EvidenceAnchor> anchors, String sourceType, String sourceId) {
        if (sourceId != null && !sourceId.isBlank()) {
            anchors.add(new EvidenceAnchor(sourceType, sourceId, null, null, null));
        }
    }

    private BoundaryMessage message(String code, String severity, String message, String sourceType, String sourceId) {
        return new BoundaryMessage(code, severity, message, sourceType, sourceId);
    }
}

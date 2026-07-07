package com.guidinglight.nexusquant.strategy.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.application.shadowrun.PaperShadowConsistencyDrilldownReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PaperShadowConsistencyDrilldownResponse 是 GateS-2 consistency drilldown 的 GET-only 响应 DTO。
 *
 * <p>该 DTO 只暴露本地只读诊断事实和安全边界 flags。它不包含 trade approval、LIVE ready、
 * real provider ready、credential material、private endpoint 原始载荷、真实账户余额、真实仓位或真实订单字段。
 */
@Schema(name = "PaperShadowConsistencyDrilldownResponse", description = "GateS-2 read-only Paper vs Shadow consistency drilldown")
public record PaperShadowConsistencyDrilldownResponse(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        ShadowRun shadowRun,
        LatestConsistency latestConsistency,
        String comparisonStatus,
        String divergenceSeverity,
        JsonNode metricDelta,
        JsonNode divergenceReasons,
        JsonNode limitations,
        SnapshotSummary snapshotSummary,
        EventSummary eventSummary,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static PaperShadowConsistencyDrilldownResponse from(PaperShadowConsistencyDrilldownReadModel model) {
        return new PaperShadowConsistencyDrilldownResponse(
                model.generatedAt(),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                ShadowRun.from(model.shadowRun()),
                model.latestConsistency() == null ? null : LatestConsistency.from(model.latestConsistency()),
                model.comparisonStatus().name(),
                model.divergenceSeverity().name(),
                model.metricDelta(),
                model.divergenceReasons(),
                model.limitations(),
                SnapshotSummary.from(model.snapshotSummary()),
                EventSummary.from(model.eventSummary()),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /**
     * ShadowRun 只描述本地 Shadow Run 主事实和无副作用边界。
     */
    public record ShadowRun(
            UUID shadowRunId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            String publishId,
            String paperRunId,
            String status,
            String authorizationBoundary,
            boolean noOrderSubmission,
            boolean noCredentialAccess,
            boolean noPrivateEndpoint,
            boolean noLedgerMutation,
            boolean noAccountMutation,
            boolean noExternalPrivateIo,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant completedAt
    ) {
        private static ShadowRun from(PaperShadowConsistencyDrilldownReadModel.ShadowRunSummary value) {
            return new ShadowRun(
                    value.shadowRunId(),
                    value.strategyVersionId(),
                    value.datasetId(),
                    value.evaluationId(),
                    value.publishId(),
                    value.paperRunId(),
                    value.status(),
                    value.authorizationBoundary(),
                    value.noOrderSubmission(),
                    value.noCredentialAccess(),
                    value.noPrivateEndpoint(),
                    value.noLedgerMutation(),
                    value.noAccountMutation(),
                    value.noExternalPrivateIo(),
                    value.createdAt(),
                    value.updatedAt(),
                    value.startedAt(),
                    value.completedAt()
            );
        }
    }

    /**
     * LatestConsistency 只描述 latest report，不表达 approval 或 trading authorization。
     */
    public record LatestConsistency(
            UUID reportId,
            UUID shadowRunId,
            String paperRunId,
            String comparisonStatus,
            JsonNode metricDelta,
            JsonNode divergenceReasons,
            JsonNode limitations,
            Instant generatedAt,
            String traceId
    ) {
        private static LatestConsistency from(PaperShadowConsistencyDrilldownReadModel.ConsistencyReportSummary value) {
            return new LatestConsistency(
                    value.reportId(),
                    value.shadowRunId(),
                    value.paperRunId(),
                    value.comparisonStatus(),
                    value.metricDelta(),
                    value.divergenceReasons(),
                    value.limitations(),
                    value.generatedAt(),
                    value.traceId()
            );
        }
    }

    /**
     * SnapshotSummary 只暴露计数和 latest 类型，不暴露 payload。
     */
    public record SnapshotSummary(
            long totalSnapshots,
            long inputMarketdataSnapshots,
            long strategyDecisionSnapshots,
            long riskPreflightSnapshots,
            long orderIntentPreviewSnapshots,
            Instant latestSnapshotAt,
            List<String> latestSnapshotTypes
    ) {
        private static SnapshotSummary from(PaperShadowConsistencyDrilldownReadModel.SnapshotSummary value) {
            return new SnapshotSummary(
                    value.totalSnapshots(),
                    value.inputMarketdataSnapshots(),
                    value.strategyDecisionSnapshots(),
                    value.riskPreflightSnapshots(),
                    value.orderIntentPreviewSnapshots(),
                    value.latestSnapshotAt(),
                    value.latestSnapshotTypes()
            );
        }
    }

    /**
     * EventSummary 只暴露 latest event 摘要，不追加事件。
     */
    public record EventSummary(
            long totalEvents,
            Instant latestEventAt,
            String latestEventType,
            String latestReasonCode
    ) {
        private static EventSummary from(PaperShadowConsistencyDrilldownReadModel.EventSummary value) {
            return new EventSummary(
                    value.totalEvents(),
                    value.latestEventAt(),
                    value.latestEventType(),
                    value.latestReasonCode()
            );
        }
    }

    /**
     * BoundaryMessage 用于 blockers / warnings 的机器可读展示。
     */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
        private static BoundaryMessage from(PaperShadowConsistencyDrilldownReadModel.BoundaryMessage value) {
            return new BoundaryMessage(
                    value.code(),
                    value.severity(),
                    value.message(),
                    value.sourceType(),
                    value.sourceId()
            );
        }
    }

    /**
     * NextStep 只允许诊断、复核、检查和对比动作。
     */
    public record NextStep(
            String code,
            String owner,
            String action,
            String expectedEvidence,
            boolean blocking
    ) {
        private static NextStep from(PaperShadowConsistencyDrilldownReadModel.NextStep value) {
            return new NextStep(
                    value.code(),
                    value.owner(),
                    value.action(),
                    value.expectedEvidence(),
                    value.blocking()
            );
        }
    }

    /**
     * EvidenceAnchor 只保存事实指针，不返回 JSON payload。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
        private static EvidenceAnchor from(PaperShadowConsistencyDrilldownReadModel.EvidenceAnchor value) {
            return new EvidenceAnchor(
                    value.sourceType(),
                    value.sourceId(),
                    value.sourceVersion(),
                    value.sourceTimestamp(),
                    value.checksum()
            );
        }
    }
}

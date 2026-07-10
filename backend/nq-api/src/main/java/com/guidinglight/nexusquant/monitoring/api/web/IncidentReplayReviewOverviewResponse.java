package com.guidinglight.nexusquant.monitoring.api.web;

import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewReadModel;
import com.guidinglight.nexusquant.strategy.api.web.ReadModelEvidenceMetadataResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * IncidentReplayReviewOverviewResponse 是 GateT-3 Incident / Replay Review overview 的 GET-only HTTP DTO。
 *
 * <p>该 DTO 只暴露 derived review item、evidence anchor 和固定 safety boundary。它不包含交易批准、
 * 实盘就绪、凭证、private provider payload、真实账户、真实订单、ledger mutation 或自动处置字段。
 */
@Schema(name = "IncidentReplayReviewOverviewResponse", description = "GateT-3 read-only incident replay review overview")
public record IncidentReplayReviewOverviewResponse(
        Instant generatedAt,
        ReadModelEvidenceMetadataResponse evidenceMetadata,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalReviewItems,
        long intakeCount,
        long evidenceReviewCount,
        long needsOperatorReviewCount,
        long acknowledgedRecommendationCount,
        long escalatedRecommendationCount,
        long closedRecommendationCount,
        long blockedCount,
        IncidentReplayReviewItem latestReviewItem,
        List<IncidentReplayReviewItem> reviewItems,
        Map<String, Long> severityBuckets,
        Map<String, Long> freshnessSummary,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static IncidentReplayReviewOverviewResponse from(IncidentReplayReviewOverviewReadModel model) {
        return new IncidentReplayReviewOverviewResponse(
                model.generatedAt(),
                ReadModelEvidenceMetadataResponse.from(model.evidenceMetadata()),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                model.totalReviewItems(),
                model.intakeCount(),
                model.evidenceReviewCount(),
                model.needsOperatorReviewCount(),
                model.acknowledgedRecommendationCount(),
                model.escalatedRecommendationCount(),
                model.closedRecommendationCount(),
                model.blockedCount(),
                IncidentReplayReviewItem.fromNullable(model.latestReviewItem()),
                model.reviewItems().stream().map(IncidentReplayReviewItem::from).toList(),
                model.severityBuckets(),
                model.freshnessSummary(),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /** IncidentReplayReviewItem 是 response 层 derived item，不是持久实体、review 记录或交易授权记录。 */
    public record IncidentReplayReviewItem(
            String reviewItemId,
            String sourceType,
            String sourceId,
            String incidentEvidenceId,
            String replayRecordId,
            String shadowRunId,
            String paperRunId,
            String consistencyReportId,
            String operatorItemId,
            String reviewState,
            String reviewDecision,
            String severity,
            String evidenceFreshness,
            String summary,
            List<String> limitations,
            List<BoundaryMessage> blockers,
            List<BoundaryMessage> warnings,
            List<NextStep> nextSteps,
            List<EvidenceAnchor> evidenceAnchors,
            String traceId,
            Instant generatedAt,
            boolean diagnosticOnly,
            boolean noSideEffect,
            boolean notTradingAuthorization,
            boolean liveDisabled,
            boolean realProviderImplemented,
            boolean privateTradingImplemented,
            boolean aiDhRuntimeIntegrated
    ) {
        private static IncidentReplayReviewItem fromNullable(IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem item) {
            return item == null ? null : from(item);
        }

        private static IncidentReplayReviewItem from(IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem item) {
            return new IncidentReplayReviewItem(
                    item.reviewItemId(),
                    item.sourceType(),
                    item.sourceId(),
                    item.incidentEvidenceId(),
                    item.replayRecordId(),
                    item.shadowRunId(),
                    item.paperRunId(),
                    item.consistencyReportId(),
                    item.operatorItemId(),
                    item.reviewState().name(),
                    item.reviewDecision().name(),
                    item.severity().name(),
                    item.evidenceFreshness().name(),
                    item.summary(),
                    item.limitations(),
                    item.blockers().stream().map(BoundaryMessage::from).toList(),
                    item.warnings().stream().map(BoundaryMessage::from).toList(),
                    item.nextSteps().stream().map(NextStep::from).toList(),
                    item.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                    item.traceId(),
                    item.generatedAt(),
                    item.diagnosticOnly(),
                    item.noSideEffect(),
                    item.notTradingAuthorization(),
                    item.liveDisabled(),
                    item.realProviderImplemented(),
                    item.privateTradingImplemented(),
                    item.aiDhRuntimeIntegrated()
            );
        }
    }

    /** BoundaryMessage 描述 blocker / warning，不携带敏感材料。 */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
        private static BoundaryMessage from(IncidentReplayReviewOverviewReadModel.BoundaryMessage value) {
            return new BoundaryMessage(value.code(), value.severity(), value.message(), value.sourceType(), value.sourceId());
        }
    }

    /** NextStep 只描述后续人工复核或补证，不是交易执行指令。 */
    public record NextStep(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        private static NextStep from(IncidentReplayReviewOverviewReadModel.NextStep value) {
            return new NextStep(value.code(), value.owner(), value.action(), value.completionCondition(), value.boundaryCritical());
        }
    }

    /** EvidenceAnchor 只定位本地 read-only fact source。 */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String traceId,
            String description
    ) {
        private static EvidenceAnchor from(IncidentReplayReviewOverviewReadModel.EvidenceAnchor value) {
            return new EvidenceAnchor(
                    value.sourceType(),
                    value.sourceId(),
                    value.sourceVersion(),
                    value.sourceTimestamp(),
                    value.traceId(),
                    value.description()
            );
        }
    }
}

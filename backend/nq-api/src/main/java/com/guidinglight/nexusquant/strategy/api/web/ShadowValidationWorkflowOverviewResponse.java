package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ShadowValidationWorkflowOverviewResponse 是 GateT-1 workflow overview 的 GET-only HTTP DTO。
 *
 * <p>Why: 该 DTO 只暴露 derived operator item、evidence anchor 和 safety boundary。它不包含交易批准、
 * 实盘就绪、凭证、private provider payload、真实账户、真实订单或 ledger mutation 字段。
 */
@Schema(name = "ShadowValidationWorkflowOverviewResponse", description = "GateT-1 read-only shadow validation workflow overview")
public record ShadowValidationWorkflowOverviewResponse(
        Instant generatedAt,
        ReadModelEvidenceMetadataResponse evidenceMetadata,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalOperatorItems,
        long intakeCount,
        long evidenceReviewCount,
        long needsEvidenceCount,
        long readyForOperatorReviewCount,
        long blockedCount,
        long closedRecommendationCount,
        OperatorItem latestOperatorItem,
        List<OperatorItem> operatorItems,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static ShadowValidationWorkflowOverviewResponse from(ShadowValidationWorkflowOverviewReadModel model) {
        return new ShadowValidationWorkflowOverviewResponse(
                model.generatedAt(),
                ReadModelEvidenceMetadataResponse.from(model.evidenceMetadata()),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                model.totalOperatorItems(),
                model.intakeCount(),
                model.evidenceReviewCount(),
                model.needsEvidenceCount(),
                model.readyForOperatorReviewCount(),
                model.blockedCount(),
                model.closedRecommendationCount(),
                OperatorItem.fromNullable(model.latestOperatorItem()),
                model.operatorItems().stream().map(OperatorItem::from).toList(),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /**
     * OperatorItem 是 response 层的 derived item，不是持久实体或交易授权记录。
     */
    public record OperatorItem(
            String operatorItemId,
            String sourceType,
            String sourceId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationReportId,
            String paperRunId,
            UUID shadowRunId,
            UUID consistencyReportId,
            String incidentEvidenceId,
            String workflowState,
            String validationDecision,
            String severity,
            String evidenceFreshness,
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
        private static OperatorItem fromNullable(ShadowValidationWorkflowOverviewReadModel.OperatorItem item) {
            return item == null ? null : from(item);
        }

        private static OperatorItem from(ShadowValidationWorkflowOverviewReadModel.OperatorItem item) {
            return new OperatorItem(
                    item.operatorItemId(),
                    item.sourceType(),
                    item.sourceId(),
                    item.strategyVersionId(),
                    item.datasetId(),
                    item.evaluationReportId(),
                    item.paperRunId(),
                    item.shadowRunId(),
                    item.consistencyReportId(),
                    item.incidentEvidenceId(),
                    item.workflowState().name(),
                    item.validationDecision().name(),
                    item.severity().name(),
                    item.evidenceFreshness().name(),
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

    /**
     * BoundaryMessage 描述 blocker / warning，不携带敏感材料。
     */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
        private static BoundaryMessage from(ShadowValidationWorkflowOverviewReadModel.BoundaryMessage value) {
            return new BoundaryMessage(value.code(), value.severity(), value.message(), value.sourceType(), value.sourceId());
        }
    }

    /**
     * NextStep 只描述后续人工复核或补证，不是交易执行指令。
     */
    public record NextStep(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        private static NextStep from(ShadowValidationWorkflowOverviewReadModel.NextStep value) {
            return new NextStep(value.code(), value.owner(), value.action(), value.completionCondition(), value.boundaryCritical());
        }
    }

    /**
     * EvidenceAnchor 只定位本地 read-only fact source。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String traceId,
            String description
    ) {
        private static EvidenceAnchor from(ShadowValidationWorkflowOverviewReadModel.EvidenceAnchor value) {
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

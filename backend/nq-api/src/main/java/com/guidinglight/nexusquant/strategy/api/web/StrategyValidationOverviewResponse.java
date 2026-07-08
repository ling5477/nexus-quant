package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * StrategyValidationOverviewResponse 是 GateS-3 runtime baseline 的 GET-only HTTP DTO。
 *
 * <p>Why: 该 DTO 只暴露 validation 层面的诊断状态和边界说明。它不包含 tradeApproved /
 * tradingReady / liveReady / authorizedForTrading 字段，不返回 credential、secret、token、passphrase、
 * private key、raw provider payload 或任何真实交易材料。
 */
@Schema(name = "StrategyValidationOverviewResponse", description = "GateS-3 read-only strategy validation overview")
public record StrategyValidationOverviewResponse(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalStrategyVersions,
        long evaluatedStrategyVersions,
        long approvedForValidation,
        long rejectedForValidation,
        long needsReview,
        long blocked,
        LatestDecision latestDecision,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static StrategyValidationOverviewResponse from(StrategyValidationOverviewReadModel model) {
        return new StrategyValidationOverviewResponse(
                model.generatedAt(),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                model.totalStrategyVersions(),
                model.evaluatedStrategyVersions(),
                model.approvedForValidation(),
                model.rejectedForValidation(),
                model.needsReview(),
                model.blocked(),
                LatestDecision.from(model.latestDecision()),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /** LatestDecision 是 validation 层面决策，不代表交易授权。 */
    public record LatestDecision(
            String strategyVersionId,
            UUID datasetId,
            String evaluationReportId,
            String publishId,
            String paperRunId,
            UUID shadowRunId,
            String decision,
            List<String> decisionReasons,
            List<String> limitations,
            Instant generatedAt,
            String traceId
    ) {
        private static LatestDecision from(StrategyValidationOverviewReadModel.LatestDecision value) {
            return new LatestDecision(
                    value.strategyVersionId(),
                    value.datasetId(),
                    value.evaluationReportId(),
                    value.publishId(),
                    value.paperRunId(),
                    value.shadowRunId(),
                    value.decision().name(),
                    value.decisionReasons(),
                    value.limitations(),
                    value.generatedAt(),
                    value.traceId()
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
        private static BoundaryMessage from(StrategyValidationOverviewReadModel.BoundaryMessage value) {
            return new BoundaryMessage(value.code(), value.severity(), value.message(), value.sourceType(), value.sourceId());
        }
    }

    /** NextStep 只描述后续审查或补证，不是执行指令。 */
    public record NextStep(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        private static NextStep from(StrategyValidationOverviewReadModel.NextStep value) {
            return new NextStep(value.code(), value.owner(), value.action(), value.completionCondition(), value.boundaryCritical());
        }
    }

    /** EvidenceAnchor 只定位本地事实来源，不暴露内部表数据或敏感字段。 */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
        private static EvidenceAnchor from(StrategyValidationOverviewReadModel.EvidenceAnchor value) {
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

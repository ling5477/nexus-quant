package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.pyartifactpreview.PythonEvaluationArtifactPreviewOverviewReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * PythonEvaluationArtifactPreviewOverviewResponse 是 GateT-4 Python Evaluation Artifact preview 的 GET-only HTTP DTO。
 *
 * <p>该 DTO 只暴露 No-file baseline 派生出的诊断边界、计数、摘要和证据锚点。它不包含 artifact raw JSON、
 * 本地路径、manifest、上传入口、交易批准、实盘就绪、凭证、private provider payload、真实账户、真实订单
 * 或 ledger mutation 字段。
 */
@Schema(
        name = "PythonEvaluationArtifactPreviewOverviewResponse",
        description = "GateT-4 read-only Python Evaluation Artifact binding preview No-file baseline overview"
)
public record PythonEvaluationArtifactPreviewOverviewResponse(
        Instant generatedAt,
        ReadModelEvidenceMetadataResponse evidenceMetadata,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        boolean pythonMlReady,
        boolean pythonLiveExecutionReady,
        long totalArtifactPreviews,
        long validArtifactCount,
        long invalidArtifactCount,
        long staleArtifactCount,
        long checksumFailedCount,
        PythonEvaluationArtifactPreviewItem latestArtifactPreview,
        List<PythonEvaluationArtifactPreviewItem> artifactPreviews,
        Map<String, Long> schemaVersionSummary,
        Map<String, Long> checksumSummary,
        Map<String, Long> metricSummaryCoverage,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static PythonEvaluationArtifactPreviewOverviewResponse from(PythonEvaluationArtifactPreviewOverviewReadModel model) {
        return new PythonEvaluationArtifactPreviewOverviewResponse(
                model.generatedAt(),
                ReadModelEvidenceMetadataResponse.from(model.evidenceMetadata()),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                model.pythonMlReady(),
                model.pythonLiveExecutionReady(),
                model.totalArtifactPreviews(),
                model.validArtifactCount(),
                model.invalidArtifactCount(),
                model.staleArtifactCount(),
                model.checksumFailedCount(),
                PythonEvaluationArtifactPreviewItem.fromNullable(model.latestArtifactPreview()),
                model.artifactPreviews().stream().map(PythonEvaluationArtifactPreviewItem::from).toList(),
                model.schemaVersionSummary(),
                model.checksumSummary(),
                model.metricSummaryCoverage(),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /**
     * PythonEvaluationArtifactPreviewItem 是 response 层预留的 derived item，不是 artifact 导入记录或交易授权记录。
     */
    public record PythonEvaluationArtifactPreviewItem(
            String artifactPreviewId,
            String artifactId,
            String experimentId,
            String strategyId,
            String strategyVersion,
            String strategyVersionId,
            String datasetId,
            String datasetVersion,
            String parameterSetId,
            String schemaVersion,
            String source,
            String checksumStatus,
            String artifactFreshness,
            String metricSummaryStatus,
            String costAssumptionsStatus,
            String slippageAssumptionsStatus,
            List<String> validationWarnings,
            List<String> limitations,
            List<EvidenceAnchor> evidenceAnchors,
            String traceId,
            Instant generatedAt,
            boolean diagnosticOnly,
            boolean noSideEffect,
            boolean notTradingAuthorization,
            boolean liveExecutionReady,
            boolean pythonMlReady,
            boolean pythonLiveExecutionReady
    ) {
        private static PythonEvaluationArtifactPreviewItem fromNullable(
                PythonEvaluationArtifactPreviewOverviewReadModel.PythonEvaluationArtifactPreviewItem item
        ) {
            return item == null ? null : from(item);
        }

        private static PythonEvaluationArtifactPreviewItem from(
                PythonEvaluationArtifactPreviewOverviewReadModel.PythonEvaluationArtifactPreviewItem item
        ) {
            return new PythonEvaluationArtifactPreviewItem(
                    item.artifactPreviewId(),
                    item.artifactId(),
                    item.experimentId(),
                    item.strategyId(),
                    item.strategyVersion(),
                    item.strategyVersionId(),
                    item.datasetId(),
                    item.datasetVersion(),
                    item.parameterSetId(),
                    item.schemaVersion(),
                    item.source(),
                    item.checksumStatus().name(),
                    item.artifactFreshness().name(),
                    item.metricSummaryStatus().name(),
                    item.costAssumptionsStatus(),
                    item.slippageAssumptionsStatus(),
                    item.validationWarnings(),
                    item.limitations(),
                    item.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                    item.traceId(),
                    item.generatedAt(),
                    item.diagnosticOnly(),
                    item.noSideEffect(),
                    item.notTradingAuthorization(),
                    item.liveExecutionReady(),
                    item.pythonMlReady(),
                    item.pythonLiveExecutionReady()
            );
        }
    }

    /**
     * BoundaryMessage 描述 blocker / warning，不携带敏感材料或本地路径。
     */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
        private static BoundaryMessage from(PythonEvaluationArtifactPreviewOverviewReadModel.BoundaryMessage value) {
            return new BoundaryMessage(value.code(), value.severity(), value.message(), value.sourceType(), value.sourceId());
        }
    }

    /**
     * NextStep 只描述后续人工复核或另起任务，不是上传、导入、执行、发布或交易动作。
     */
    public record NextStep(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        private static NextStep from(PythonEvaluationArtifactPreviewOverviewReadModel.NextStep value) {
            return new NextStep(value.code(), value.owner(), value.action(), value.completionCondition(), value.boundaryCritical());
        }
    }

    /**
     * EvidenceAnchor 只定位只读事实来源，不暴露 artifact 文件路径、raw JSON、credential 或 private payload。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String traceId,
            String description
    ) {
        private static EvidenceAnchor from(PythonEvaluationArtifactPreviewOverviewReadModel.EvidenceAnchor value) {
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

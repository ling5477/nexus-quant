package com.guidinglight.nexusquant.strategy.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.application.shadowrun.ShadowRunOverviewReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ShadowRunOverviewResponse 是 GateS-1 Shadow Run overview 的 GET-only 响应 DTO。
 *
 * <p>该 DTO 只暴露本地 read model 诊断事实，固定表达 diagnosticOnly、noSideEffect 和
 * notTradingAuthorization。它不包含 trade approval、LIVE ready、real provider ready、credential
 * material、private endpoint payload 或真实账户/订单字段。
 */
@Schema(name = "ShadowRunOverviewResponse", description = "GateS-1 read-only Shadow Run overview")
public record ShadowRunOverviewResponse(
        Instant generatedAt,
        ReadModelEvidenceMetadataResponse evidenceMetadata,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalRuns,
        long runningRuns,
        long blockedRuns,
        long failedRuns,
        long completedRuns,
        long staleRuns,
        LatestRun latestRun,
        LatestConsistency latestConsistency,
        String divergenceSeverity,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static ShadowRunOverviewResponse from(ShadowRunOverviewReadModel model) {
        return new ShadowRunOverviewResponse(
                model.generatedAt(),
                ReadModelEvidenceMetadataResponse.from(model.evidenceMetadata()),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                model.totalRuns(),
                model.runningRuns(),
                model.blockedRuns(),
                model.failedRuns(),
                model.completedRuns(),
                model.staleRuns(),
                model.latestRun() == null ? null : LatestRun.from(model.latestRun()),
                model.latestConsistency() == null ? null : LatestConsistency.from(model.latestConsistency()),
                model.divergenceSeverity().name(),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /**
     * LatestRun 只描述本地 Shadow Run 主事实与无副作用 flags。
     */
    public record LatestRun(
            UUID shadowRunId,
            String strategyVersionId,
            UUID datasetId,
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
        private static LatestRun from(ShadowRunOverviewReadModel.LatestRun value) {
            return new LatestRun(
                    value.shadowRunId(),
                    value.strategyVersionId(),
                    value.datasetId(),
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
     * LatestConsistency 只描述证据层 comparison，不表达交易授权。
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
        private static LatestConsistency from(ShadowRunOverviewReadModel.LatestConsistency value) {
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
     * BoundaryMessage 用于 blocker/warning 机器可读展示。
     */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
        private static BoundaryMessage from(ShadowRunOverviewReadModel.BoundaryMessage value) {
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
     * NextStep 只允许诊断、复核、对比、只读前端实现类动作。
     */
    public record NextStep(
            String code,
            String owner,
            String action,
            String expectedEvidence,
            boolean blocking
    ) {
        private static NextStep from(ShadowRunOverviewReadModel.NextStep value) {
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
     * EvidenceAnchor 只保存证据指针，不返回 payload。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
        private static EvidenceAnchor from(ShadowRunOverviewReadModel.EvidenceAnchor value) {
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

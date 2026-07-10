package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ConsistencyEvidenceOverviewResponse 是 GateT-2 consistency evidence overview 的 GET-only HTTP DTO。
 *
 * <p>该 DTO 只暴露 derived evidence item、metricDelta 摘要、evidence anchor 和固定 safety boundary。
 * 它不包含交易批准、实盘就绪、凭证、private provider payload、真实账户、真实订单或 ledger mutation 字段。
 */
@Schema(name = "ConsistencyEvidenceOverviewResponse", description = "GateT-2 read-only consistency evidence overview")
public record ConsistencyEvidenceOverviewResponse(
        Instant generatedAt,
        ReadModelEvidenceMetadataResponse evidenceMetadata,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalEvidenceItems,
        long consistentCount,
        long divergedCount,
        long partialCount,
        long notComparableCount,
        long failedCount,
        long staleEvidenceCount,
        long highSeverityCount,
        long criticalSeverityCount,
        ConsistencyEvidenceItem latestEvidenceItem,
        List<ConsistencyEvidenceItem> evidenceItems,
        Map<String, Long> severityBuckets,
        Map<String, Long> freshnessSummary,
        MetricDeltaSummary metricDeltaSummary,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static ConsistencyEvidenceOverviewResponse from(ConsistencyEvidenceOverviewReadModel model) {
        return new ConsistencyEvidenceOverviewResponse(
                model.generatedAt(),
                ReadModelEvidenceMetadataResponse.from(model.evidenceMetadata()),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                model.totalEvidenceItems(),
                model.consistentCount(),
                model.divergedCount(),
                model.partialCount(),
                model.notComparableCount(),
                model.failedCount(),
                model.staleEvidenceCount(),
                model.highSeverityCount(),
                model.criticalSeverityCount(),
                ConsistencyEvidenceItem.fromNullable(model.latestEvidenceItem()),
                model.evidenceItems().stream().map(ConsistencyEvidenceItem::from).toList(),
                model.severityBuckets(),
                model.freshnessSummary(),
                MetricDeltaSummary.from(model.metricDeltaSummary()),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /**
     * ConsistencyEvidenceItem 是 response 层的 derived item，不是持久实体、review 记录或交易授权记录。
     */
    public record ConsistencyEvidenceItem(
            String evidenceItemId,
            UUID shadowRunId,
            String paperRunId,
            UUID consistencyReportId,
            String strategyVersionId,
            UUID datasetId,
            String comparisonStatus,
            String divergenceSeverity,
            String evidenceFreshness,
            MetricDeltaSummary metricDelta,
            List<String> divergenceReasons,
            List<String> limitations,
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
        private static ConsistencyEvidenceItem fromNullable(ConsistencyEvidenceOverviewReadModel.ConsistencyEvidenceItem item) {
            return item == null ? null : from(item);
        }

        private static ConsistencyEvidenceItem from(ConsistencyEvidenceOverviewReadModel.ConsistencyEvidenceItem item) {
            return new ConsistencyEvidenceItem(
                    item.evidenceItemId(),
                    item.shadowRunId(),
                    item.paperRunId(),
                    item.consistencyReportId(),
                    item.strategyVersionId(),
                    item.datasetId(),
                    item.comparisonStatus().name(),
                    item.divergenceSeverity().name(),
                    item.evidenceFreshness().name(),
                    MetricDeltaSummary.from(item.metricDelta()),
                    item.divergenceReasons(),
                    item.limitations(),
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
     * MetricDeltaSummary 只暴露摘要，不暴露 raw JSONB。
     */
    public record MetricDeltaSummary(
            long metricCount,
            long comparableMetricCount,
            long nonComparableMetricCount,
            List<MetricDeltaItem> topDeltaMetrics,
            List<String> limitationCodes,
            long sensitiveFieldFilteredCount,
            boolean rawMetricDeltaExposed,
            boolean profitConclusionInferred,
            boolean tradingSignalInferred
    ) {
        private static MetricDeltaSummary from(ConsistencyEvidenceOverviewReadModel.MetricDeltaSummary value) {
            return new MetricDeltaSummary(
                    value.metricCount(),
                    value.comparableMetricCount(),
                    value.nonComparableMetricCount(),
                    value.topDeltaMetrics().stream().map(MetricDeltaItem::from).toList(),
                    value.limitationCodes(),
                    value.sensitiveFieldFilteredCount(),
                    value.rawMetricDeltaExposed(),
                    value.profitConclusionInferred(),
                    value.tradingSignalInferred()
            );
        }
    }

    /**
     * MetricDeltaItem 是 metric delta 的单项摘要。
     */
    public record MetricDeltaItem(
            String name,
            Double delta,
            String unit,
            boolean comparable,
            List<String> limitationCodes
    ) {
        private static MetricDeltaItem from(ConsistencyEvidenceOverviewReadModel.MetricDeltaItem value) {
            return new MetricDeltaItem(
                    value.name(),
                    value.delta(),
                    value.unit(),
                    value.comparable(),
                    value.limitationCodes()
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
        private static BoundaryMessage from(ConsistencyEvidenceOverviewReadModel.BoundaryMessage value) {
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
        private static NextStep from(ConsistencyEvidenceOverviewReadModel.NextStep value) {
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
        private static EvidenceAnchor from(ConsistencyEvidenceOverviewReadModel.EvidenceAnchor value) {
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

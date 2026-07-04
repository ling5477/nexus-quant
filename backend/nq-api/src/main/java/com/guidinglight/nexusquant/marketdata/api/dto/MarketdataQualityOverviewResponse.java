package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDataOriginSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIssue;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityMetric;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverview;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MarketdataQualityOverviewResponse 是 GateP Batch 2 Data Quality Center 的安全 HTTP 响应。
 * <p>
 * Why:
 * 该 DTO 只暴露本地 DB 聚合后的数据质量诊断，不输出 raw payload、credential material、
 * provider response，也不提供 tradingReady / liveReady / authorizedForTrading 之类容易被误解为
 * 交易授权的字段。
 */
@Schema(name = "MarketdataQualityOverviewResponse", description = "Read-only marketdata data quality overview")
public record MarketdataQualityOverviewResponse(
        Scope scope,
        long totalBars,
        Long expectedBars,
        Long gapCount,
        Metric duplicateCount,
        Metric outOfOrderCount,
        Metric staleCount,
        Instant latestBarTime,
        Instant earliestBarTime,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        UUID lastIngestionRunId,
        String sourceHealth,
        String freshnessStatus,
        String qualityStatus,
        DataOriginSummary dataOriginSummary,
        DatasetCoverageSummary datasetCoverageSummary,
        List<Issue> topIssues,
        Instant generatedAt
) {
    public static MarketdataQualityOverviewResponse from(MarketdataQualityOverview overview) {
        return new MarketdataQualityOverviewResponse(
                Scope.from(overview.scope()),
                overview.totalBars(),
                overview.expectedBars(),
                overview.gapCount(),
                Metric.from(overview.duplicateCount()),
                Metric.from(overview.outOfOrderCount()),
                Metric.from(overview.staleCount()),
                overview.latestBarTime(),
                overview.earliestBarTime(),
                overview.lastSuccessAt(),
                overview.lastFailureAt(),
                overview.lastIngestionRunId(),
                overview.sourceHealth().name(),
                overview.freshnessStatus().name(),
                overview.qualityStatus().name(),
                DataOriginSummary.from(overview.dataOriginSummary()),
                DatasetCoverageSummary.from(overview.datasetCoverageSummary()),
                overview.topIssues().stream().map(Issue::from).toList(),
                overview.generatedAt()
        );
    }

    /**
     * Scope 回显本次 overview 的只读筛选边界，避免 broad diagnostic 被解释成单一 provider 授权。
     */
    public record Scope(
            String exchangeCode,
            String marketType,
            String symbol,
            String interval,
            String sourceType,
            String dataOrigin,
            UUID datasetId,
            Instant from,
            Instant to
    ) {
        private static Scope from(MarketdataQualityOverviewScope scope) {
            return new Scope(
                    scope.exchangeCode(),
                    scope.marketType(),
                    scope.symbol(),
                    scope.interval(),
                    scope.sourceType(),
                    scope.dataOrigin(),
                    scope.datasetId(),
                    scope.from(),
                    scope.to()
            );
        }
    }

    /**
     * Metric 用 status 区分“可计算的 0”和“当前 schema 不支持”，防止前端误判为质量通过。
     */
    public record Metric(Long value, String status, String reason) {
        private static Metric from(MarketdataQualityMetric metric) {
            return new Metric(metric.value(), metric.status().name(), metric.reason());
        }
    }

    /**
     * DataOriginSummary 明确 current effective origin 仍为 LOCAL_DB，不代表 PUBLIC_OUTBOUND runtime 已启用。
     */
    public record DataOriginSummary(
            String requestedDataOrigin,
            String effectiveDataOrigin,
            long localDbBars,
            long fixtureBars,
            long unknownOriginBars,
            String supportLevel
    ) {
        private static DataOriginSummary from(MarketdataQualityDataOriginSummary summary) {
            return new DataOriginSummary(
                    summary.requestedDataOrigin(),
                    summary.effectiveDataOrigin(),
                    summary.localDbBars(),
                    summary.fixtureBars(),
                    summary.unknownOriginBars(),
                    summary.supportLevel()
            );
        }
    }

    /**
     * DatasetCoverageSummary 只回显现有 coverage 事实，不触发 refresh-quality 或任何 DB 写入。
     */
    public record DatasetCoverageSummary(
            long datasetCount,
            Long expectedBars,
            Long actualBars,
            Long missingBars,
            Long duplicateBars,
            Long invalidBars,
            UUID latestDatasetId,
            Instant latestCoverageAt
    ) {
        private static DatasetCoverageSummary from(MarketdataQualityDatasetCoverageSummary summary) {
            return new DatasetCoverageSummary(
                    summary.datasetCount(),
                    summary.expectedBars(),
                    summary.actualBars(),
                    summary.missingBars(),
                    summary.duplicateBars(),
                    summary.invalidBars(),
                    summary.latestDatasetId(),
                    summary.latestCoverageAt()
            );
        }
    }

    /**
     * Issue 是本地聚合后的可读问题项；message 不能包含 provider raw response 或 credential 信息。
     */
    public record Issue(String code, String severity, long count, String message) {
        private static Issue from(MarketdataQualityIssue issue) {
            return new Issue(issue.code(), issue.severity(), issue.count(), issue.message());
        }
    }
}

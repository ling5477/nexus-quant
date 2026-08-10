package com.guidinglight.nexusquant.research.domain;

import java.time.Instant;

/**
 * BacktestPublishRecord 表示 GateF-5 的发布事实。
 */
public record BacktestPublishRecord(
        String publishRecordId,
        String backtestRunId,
        String researchConfigId,
        String backtestConfigId,
        String sourceStrategyId,
        String evalReportId,
        String targetStrategyDefinitionId,
        String strategyVersionId,
        PublishStatus publishStatus,
        String publishName,
        String publishSnapshotJson,
        String versionSnapshotJson,
        String evaluationSummaryJson,
        String failureCode,
        String failureMessage,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        String artifactStorageKey,
        String manifestStorageKey
) {
    public BacktestPublishRecord {
        new BacktestPublishArtifactLocator(artifactStorageKey, manifestStorageKey);
    }

    public BacktestPublishRecord(
            String publishRecordId,
            String backtestRunId,
            String researchConfigId,
            String backtestConfigId,
            String sourceStrategyId,
            String evalReportId,
            String targetStrategyDefinitionId,
            String strategyVersionId,
            PublishStatus publishStatus,
            String publishName,
            String publishSnapshotJson,
            String versionSnapshotJson,
            String evaluationSummaryJson,
            String failureCode,
            String failureMessage,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                publishRecordId,
                backtestRunId,
                researchConfigId,
                backtestConfigId,
                sourceStrategyId,
                evalReportId,
                targetStrategyDefinitionId,
                strategyVersionId,
                publishStatus,
                publishName,
                publishSnapshotJson,
                versionSnapshotJson,
                evaluationSummaryJson,
                failureCode,
                failureMessage,
                publishedAt,
                createdAt,
                updatedAt,
                null,
                null
        );
    }

    public BacktestPublishRecord(
            String publishRecordId,
            String backtestRunId,
            String researchConfigId,
            String backtestConfigId,
            String sourceStrategyId,
            String evalReportId,
            String targetStrategyDefinitionId,
            PublishStatus publishStatus,
            String publishName,
            String publishSnapshotJson,
            String evaluationSummaryJson,
            String failureCode,
            String failureMessage,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                publishRecordId,
                backtestRunId,
                researchConfigId,
                backtestConfigId,
                sourceStrategyId,
                evalReportId,
                targetStrategyDefinitionId,
                null,
                publishStatus,
                publishName,
                publishSnapshotJson,
                "{}",
                evaluationSummaryJson,
                failureCode,
                failureMessage,
                publishedAt,
                createdAt,
                updatedAt,
                null,
                null
        );
    }

    public ArtifactLocatorBindingStatus artifactLocatorBindingStatus() {
        return artifactStorageKey == null
                ? ArtifactLocatorBindingStatus.LEGACY_ARTIFACT_UNBOUND
                : ArtifactLocatorBindingStatus.PERSISTENT_ARTIFACT_BOUND;
    }

    public PublishSummary toSummary() {
        return new PublishSummary(
                publishStatus,
                publishedAt,
                targetStrategyDefinitionId,
                publishName,
                backtestRunId
        );
    }

    public enum ArtifactLocatorBindingStatus {
        LEGACY_ARTIFACT_UNBOUND,
        PERSISTENT_ARTIFACT_BOUND
    }
}

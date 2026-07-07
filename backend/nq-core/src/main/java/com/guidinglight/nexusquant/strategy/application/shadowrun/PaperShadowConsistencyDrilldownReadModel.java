package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PaperShadowConsistencyDrilldownReadModel 是 GateS-2 Paper vs Shadow consistency drilldown 的只读合同。
 *
 * <p>职责：围绕单个 `shadowRunId` 聚合 Shadow Run 主事实、latest consistency report、snapshot/event
 * 摘要、证据锚点、blocker/warning/nextStep 和固定安全边界 flags。该 read model 不承载写侧命令，
 * 不创建 consistency report，不启动 runner/scheduler，不调用 adapter，不读取 credential，不表达交易授权。
 */
public record PaperShadowConsistencyDrilldownReadModel(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        ShadowRunSummary shadowRun,
        ConsistencyReportSummary latestConsistency,
        PaperShadowConsistencyDrilldownComparisonStatus comparisonStatus,
        ShadowRunOverviewDivergenceSeverity divergenceSeverity,
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
    public PaperShadowConsistencyDrilldownReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(shadowRun, "shadowRun must not be null");
        Objects.requireNonNull(comparisonStatus, "comparisonStatus must not be null");
        Objects.requireNonNull(divergenceSeverity, "divergenceSeverity must not be null");
        Objects.requireNonNull(metricDelta, "metricDelta must not be null");
        Objects.requireNonNull(divergenceReasons, "divergenceReasons must not be null");
        Objects.requireNonNull(limitations, "limitations must not be null");
        Objects.requireNonNull(snapshotSummary, "snapshotSummary must not be null");
        Objects.requireNonNull(eventSummary, "eventSummary must not be null");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        nextSteps = List.copyOf(Objects.requireNonNull(nextSteps, "nextSteps must not be null"));
        evidenceAnchors = List.copyOf(Objects.requireNonNull(evidenceAnchors, "evidenceAnchors must not be null"));
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * ShadowRunSummary 只表达本地 Shadow Run 主事实与 no-side-effect flags。
     */
    public record ShadowRunSummary(
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
    }

    /**
     * ConsistencyReportSummary 只表达 latest local report 的脱敏差异分析。
     */
    public record ConsistencyReportSummary(
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
    }

    /**
     * SnapshotSummary 聚合本地 snapshot 证据完整性，不读取 payload。
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
        public SnapshotSummary {
            if (totalSnapshots < 0
                    || inputMarketdataSnapshots < 0
                    || strategyDecisionSnapshots < 0
                    || riskPreflightSnapshots < 0
                    || orderIntentPreviewSnapshots < 0) {
                throw new IllegalArgumentException("snapshot counts must not be negative");
            }
            latestSnapshotTypes = List.copyOf(Objects.requireNonNull(
                    latestSnapshotTypes,
                    "latestSnapshotTypes must not be null"
            ));
        }
    }

    /**
     * EventSummary 聚合本地 append-only event 摘要，不写入新事件。
     */
    public record EventSummary(
            long totalEvents,
            Instant latestEventAt,
            String latestEventType,
            String latestReasonCode
    ) {
        public EventSummary {
            if (totalEvents < 0) {
                throw new IllegalArgumentException("totalEvents must not be negative");
            }
        }
    }

    /**
     * BoundaryMessage 用于 blocker/warning，所有 code 都是诊断信息，不是交易放行条件。
     */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
    }

    /**
     * NextStep 只允许 inspect/review/compare 类诊断动作，不允许交易动作。
     */
    public record NextStep(
            String code,
            String owner,
            String action,
            String expectedEvidence,
            boolean blocking
    ) {
    }

    /**
     * EvidenceAnchor 只保存本地事实锚点，不复制 payload 或敏感材料。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
    }
}

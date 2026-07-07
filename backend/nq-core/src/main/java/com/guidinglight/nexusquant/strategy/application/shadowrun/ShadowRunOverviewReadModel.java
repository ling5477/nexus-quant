package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ShadowRunOverviewReadModel 是 GateS-1 后端只读 overview 合同。
 *
 * <p>职责：表达 Shadow Run 系统整体诊断状态、最新 run、最新 consistency report、边界阻断、
 * warning、next step 和证据锚点。该 read model 固定 diagnosticOnly/noSideEffect/
 * notTradingAuthorization，不承载交易批准、LIVE readiness、真实 provider readiness 或写侧动作。
 */
public record ShadowRunOverviewReadModel(
        Instant generatedAt,
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
        ShadowRunOverviewDivergenceSeverity divergenceSeverity,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public ShadowRunOverviewReadModel {
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        Objects.requireNonNull(divergenceSeverity, "divergenceSeverity must not be null");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers must not be null"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        nextSteps = List.copyOf(Objects.requireNonNull(nextSteps, "nextSteps must not be null"));
        evidenceAnchors = List.copyOf(Objects.requireNonNull(evidenceAnchors, "evidenceAnchors must not be null"));
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * LatestRun 只表达最新本地 Shadow Run 主事实和 no-side-effect flags。
     *
     * <p>这些字段不得解释为交易授权；`authorizationBoundary` 只能为 diagnostic/review/replay 边界。
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
    }

    /**
     * LatestConsistency 只表达 Paper vs Shadow 证据层复盘，不回写核心状态机。
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
     * NextStep 只允许 review/inspect/compare/investigate/readonly frontend 类诊断动作。
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
     * EvidenceAnchor 只保存本地事实锚点，不复制 JSON payload 或敏感材料。
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

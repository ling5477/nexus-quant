package com.guidinglight.nexusquant.monitoring.api.web;

import com.guidinglight.nexusquant.monitoring.application.incident.IncidentReplayOverviewReadModel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * IncidentReplayOverviewResponse 是 GateS-6 Incident / Replay overview 的 GET-only HTTP DTO。
 *
 * <p>Why: 该 DTO 只暴露 diagnostic-only、no-side-effect 的本地事实摘要；不包含 tradeApproved、
 * tradingReady、liveReady、authorizedForTrading 字段，不返回 credential、secret、token、passphrase、
 * private key、raw provider payload、真实账户或真实订单材料。
 */
@Schema(name = "IncidentReplayOverviewResponse", description = "GateS-6 read-only incident replay overview")
public record IncidentReplayOverviewResponse(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalEvidenceItems,
        long shadowEventCount,
        long consistencyDivergenceCount,
        long paperAlertCount,
        long recoveryEventCount,
        long replayEventCount,
        List<LatestEvidence> latestEvidence,
        String incidentSeverity,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public static IncidentReplayOverviewResponse from(IncidentReplayOverviewReadModel model) {
        return new IncidentReplayOverviewResponse(
                model.generatedAt(),
                model.diagnosticOnly(),
                model.noSideEffect(),
                model.notTradingAuthorization(),
                model.liveDisabled(),
                model.realProviderImplemented(),
                model.privateTradingImplemented(),
                model.aiDhRuntimeIntegrated(),
                model.totalEvidenceItems(),
                model.shadowEventCount(),
                model.consistencyDivergenceCount(),
                model.paperAlertCount(),
                model.recoveryEventCount(),
                model.replayEventCount(),
                model.latestEvidence().stream().map(LatestEvidence::from).toList(),
                model.incidentSeverity().name(),
                model.blockers().stream().map(BoundaryMessage::from).toList(),
                model.warnings().stream().map(BoundaryMessage::from).toList(),
                model.nextSteps().stream().map(NextStep::from).toList(),
                model.evidenceAnchors().stream().map(EvidenceAnchor::from).toList(),
                model.traceId()
        );
    }

    /** LatestEvidence 是脱敏本地事实摘要。 */
    public record LatestEvidence(
            String evidenceType,
            String sourceId,
            String sourceStatus,
            String summary,
            Instant occurredAt,
            String traceId
    ) {
        private static LatestEvidence from(IncidentReplayOverviewReadModel.LatestEvidence value) {
            return new LatestEvidence(
                    value.evidenceType(),
                    value.sourceId(),
                    value.sourceStatus(),
                    value.summary(),
                    value.occurredAt(),
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
        private static BoundaryMessage from(IncidentReplayOverviewReadModel.BoundaryMessage value) {
            return new BoundaryMessage(value.code(), value.severity(), value.message(), value.sourceType(), value.sourceId());
        }
    }

    /** NextStep 只描述后续人工复核或补证，不是执行指令。 */
    public record NextStep(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        private static NextStep from(IncidentReplayOverviewReadModel.NextStep value) {
            return new NextStep(value.code(), value.owner(), value.action(), value.completionCondition(), value.boundaryCritical());
        }
    }

    /** EvidenceAnchor 定位本地事实来源，不暴露 raw payload。 */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
        private static EvidenceAnchor from(IncidentReplayOverviewReadModel.EvidenceAnchor value) {
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

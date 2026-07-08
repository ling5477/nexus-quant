/**
 * GateS-6 Incident / Replay overview read-only frontend types.
 *
 * Why:
 * 这些类型只承接 `GET /api/incidents/replay/overview` 的诊断 overview 响应；前端不得在这里扩展
 * 写侧 incident/replay 操作、真实 provider 能力、交易放行语义或敏感材料字段。
 */
export type IncidentReplaySeverity = 'NONE' | 'INFO' | 'WARNING' | 'HIGH' | 'CRITICAL' | 'UNKNOWN' | string;

export interface IncidentReplayOverviewResponse {
    generatedAt: string;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
    totalEvidenceItems: number;
    shadowEventCount: number;
    consistencyDivergenceCount: number;
    paperAlertCount: number;
    recoveryEventCount: number;
    replayEventCount: number;
    latestEvidence: IncidentReplayLatestEvidence[];
    incidentSeverity: IncidentReplaySeverity;
    blockers: IncidentReplayBlocker[];
    warnings: IncidentReplayWarning[];
    nextSteps: IncidentReplayNextStep[];
    evidenceAnchors: IncidentReplayEvidenceAnchor[];
    traceId: string;
}

export interface IncidentReplayLatestEvidence {
    evidenceType: string;
    sourceId: string | null;
    sourceStatus: string | null;
    summary: string | null;
    occurredAt: string | null;
    traceId: string | null;
}

export interface IncidentReplayEvidenceAnchor {
    sourceType: string;
    sourceId: string | null;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    checksum: string | null;
}

export interface IncidentReplayBoundaryMessage {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export type IncidentReplayBlocker = IncidentReplayBoundaryMessage;

export type IncidentReplayWarning = IncidentReplayBoundaryMessage;

export interface IncidentReplayNextStep {
    code: string;
    owner: string;
    action: string;
    completionCondition: string;
    boundaryCritical: boolean;
}

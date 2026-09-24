/**
 * GateT-3 Incident / Replay Review overview frontend types.
 *
 * Why:
 * These types only model `GET /api/incidents/replay/review/overview`.
 * Review items are derived diagnostic rows, not persisted review records,
 * incident closeout, automatic remediation, trading authorization, credential
 * views, private provider facts, or real order/account state.
 */
export type IncidentReplayReviewState =
    'INTAKE'
    | 'EVIDENCE_REVIEW'
    | 'NEEDS_OPERATOR_REVIEW'
    | 'ACKNOWLEDGED_RECOMMENDATION'
    | 'ESCALATED_RECOMMENDATION'
    | 'CLOSED_RECOMMENDATION'
    | 'BLOCKED'
    | string;

/**
 * Review decisions are recommendation semantics only.
 * ACKNOWLEDGE_RECOMMENDED / ESCALATE_RECOMMENDED / CLOSEOUT_RECOMMENDED do not
 * mean the system already acknowledged, escalated, closed an incident, or
 * approved trading.
 */
export type IncidentReplayReviewDecision =
    'NO_DECISION'
    | 'REVIEW_NEEDED'
    | 'ACKNOWLEDGE_RECOMMENDED'
    | 'ESCALATE_RECOMMENDED'
    | 'CLOSEOUT_RECOMMENDED'
    | 'BLOCKED'
    | 'STALE_EVIDENCE'
    | string;

/**
 * Severity only expresses diagnostic priority.
 * HIGH / CRITICAL do not imply automatic remediation, market direction,
 * trading readiness, or authorization.
 */
export type IncidentReplayReviewSeverity =
    'NONE'
    | 'INFO'
    | 'WARNING'
    | 'HIGH'
    | 'CRITICAL'
    | 'UNKNOWN'
    | string;

/**
 * Evidence freshness only describes local evidence quality.
 * STALE / MISSING / PARTIAL / UNKNOWN must be rendered fail-closed.
 */
export type IncidentReplayEvidenceFreshness =
    'FRESH'
    | 'STALE'
    | 'MISSING'
    | 'PARTIAL'
    | 'UNKNOWN'
    | string;

export interface IncidentReplayReviewEvidenceAnchor {
    sourceType: string;
    sourceId: string | null;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    traceId: string | null;
    description: string | null;
}

export interface IncidentReplayReviewBoundaryMessage {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export type IncidentReplayReviewBlocker = IncidentReplayReviewBoundaryMessage;

export type IncidentReplayReviewWarning = IncidentReplayReviewBoundaryMessage;

/**
 * Next steps are manual review or evidence collection guidance.
 * They are not write-side commands and must not create review, acknowledge,
 * escalate, closeout, start, stop, execute, or trade behavior.
 */
export interface IncidentReplayReviewNextStep {
    code: string;
    owner: string;
    action: string;
    completionCondition: string;
    boundaryCritical: boolean;
}

/**
 * Derived review item for display only.
 * Safety flags are repeated per item so the page can detect boundary drift and
 * fail closed if any item stops matching the GateT-3 no-side-effect contract.
 */
export interface IncidentReplayReviewItem {
    reviewItemId: string;
    sourceType: string;
    sourceId: string;
    incidentEvidenceId: string | null;
    replayRecordId: string | null;
    shadowRunId: string | null;
    paperRunId: string | null;
    consistencyReportId: string | null;
    operatorItemId: string | null;
    reviewState: IncidentReplayReviewState;
    reviewDecision: IncidentReplayReviewDecision;
    severity: IncidentReplayReviewSeverity;
    evidenceFreshness: IncidentReplayEvidenceFreshness;
    summary: string;
    limitations: string[];
    blockers: IncidentReplayReviewBlocker[];
    warnings: IncidentReplayReviewWarning[];
    nextSteps: IncidentReplayReviewNextStep[];
    evidenceAnchors: IncidentReplayReviewEvidenceAnchor[];
    traceId: string;
    generatedAt: string;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
}

export interface IncidentReplayReviewOverviewResponse {
    generatedAt: string;
    evidenceMetadata?: ReadModelEvidenceMetadata | null;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
    totalReviewItems: number;
    intakeCount: number;
    evidenceReviewCount: number;
    needsOperatorReviewCount: number;
    acknowledgedRecommendationCount: number;
    escalatedRecommendationCount: number;
    closedRecommendationCount: number;
    blockedCount: number;
    latestReviewItem: IncidentReplayReviewItem | null;
    reviewItems: IncidentReplayReviewItem[];
    severityBuckets: Record<string, number>;
    freshnessSummary: Record<string, number>;
    blockers: IncidentReplayReviewBlocker[];
    warnings: IncidentReplayReviewWarning[];
    nextSteps: IncidentReplayReviewNextStep[];
    evidenceAnchors: IncidentReplayReviewEvidenceAnchor[];
    traceId: string;
}
import type {ReadModelEvidenceMetadata} from '@/types/read-model-evidence';

/**
 * GateT-4 Evaluation Artifact Preview frontend types.
 *
 * Why:
 * These types only model `GET /api/strategy-validation/evaluation-artifacts/preview/overview`.
 * Artifact preview items are diagnostic derived rows, not artifact import records,
 * strategy approvals, ML readiness, live execution readiness, trading authorization,
 * credential views, private provider facts, or real order/account state.
 */
import type {ReadModelEvidenceMetadata} from '@/types/read-model-evidence';

export type ArtifactChecksumStatus =
    'VALID'
    | 'INVALID'
    | 'MISSING'
    | 'NOT_CHECKED'
    | 'UNKNOWN'
    | string;

/**
 * Artifact freshness only describes local diagnostic source freshness.
 * FRESH does not mean live execution readiness; STALE / MISSING / UNKNOWN must fail closed.
 */
export type ArtifactFreshness =
    'FRESH'
    | 'STALE'
    | 'MISSING'
    | 'UNKNOWN'
    | string;

/**
 * Metric summary status only describes offline diagnostic metric coverage.
 * FAKE_FIXTURE_ONLY must be rendered as test fixture data, not real strategy performance.
 */
export type ArtifactMetricSummaryStatus =
    'PRESENT'
    | 'INCOMPLETE'
    | 'FAKE_FIXTURE_ONLY'
    | 'MISSING'
    | 'UNKNOWN'
    | string;

export interface EvaluationArtifactPreviewEvidenceAnchor {
    sourceType: string;
    sourceId: string | null;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    traceId: string | null;
    description: string | null;
}

export interface EvaluationArtifactPreviewBoundaryMessage {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export type EvaluationArtifactPreviewBlocker = EvaluationArtifactPreviewBoundaryMessage;

export type EvaluationArtifactPreviewWarning = EvaluationArtifactPreviewBoundaryMessage;

/**
 * Next steps are follow-up planning or review guidance.
 * They are not upload, import, execute, publish, start, stop, trade, or Python execution commands.
 */
export interface EvaluationArtifactPreviewNextStep {
    code: string;
    owner: string;
    action: string;
    completionCondition: string;
    boundaryCritical: boolean;
}

/**
 * Derived preview item for display only.
 * Every readiness flag must remain safe; if any item reports liveExecutionReady / pythonMlReady /
 * pythonLiveExecutionReady as true, the page must fail closed.
 */
export interface PythonEvaluationArtifactPreviewItem {
    artifactPreviewId: string;
    artifactId: string | null;
    experimentId: string | null;
    strategyId: string | null;
    strategyVersion: string | null;
    strategyVersionId: string | null;
    datasetId: string | null;
    datasetVersion: string | null;
    parameterSetId: string | null;
    schemaVersion: string | null;
    source: string | null;
    checksumStatus: ArtifactChecksumStatus;
    artifactFreshness: ArtifactFreshness;
    metricSummaryStatus: ArtifactMetricSummaryStatus;
    costAssumptionsStatus: string | null;
    slippageAssumptionsStatus: string | null;
    validationWarnings: string[];
    limitations: string[];
    evidenceAnchors: EvaluationArtifactPreviewEvidenceAnchor[];
    traceId: string;
    generatedAt: string;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveExecutionReady: boolean;
    pythonMlReady: boolean;
    pythonLiveExecutionReady: boolean;
}

/**
 * Overview response is the only GateT-4 frontend DTO consumed by this slice.
 * The type intentionally contains no file path, upload/import request shape, raw artifact JSON,
 * credential, private provider, real account, real order, runner, scheduler, or trade field.
 */
export interface PythonEvaluationArtifactPreviewOverviewResponse {
    generatedAt: string;
    evidenceMetadata?: ReadModelEvidenceMetadata | null;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
    pythonMlReady: boolean;
    pythonLiveExecutionReady: boolean;
    totalArtifactPreviews: number;
    validArtifactCount: number;
    invalidArtifactCount: number;
    staleArtifactCount: number;
    checksumFailedCount: number;
    latestArtifactPreview: PythonEvaluationArtifactPreviewItem | null;
    artifactPreviews: PythonEvaluationArtifactPreviewItem[];
    schemaVersionSummary: Record<string, number>;
    checksumSummary: Record<string, number>;
    metricSummaryCoverage: Record<string, number>;
    blockers: EvaluationArtifactPreviewBlocker[];
    warnings: EvaluationArtifactPreviewWarning[];
    nextSteps: EvaluationArtifactPreviewNextStep[];
    evidenceAnchors: EvaluationArtifactPreviewEvidenceAnchor[];
    traceId: string;
}

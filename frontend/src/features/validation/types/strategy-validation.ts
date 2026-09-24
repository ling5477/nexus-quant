/**
 * GateQ-5 strategy validation read-only types.
 *
 * Why:
 * 这些类型只描述 GateQ-1 / GateQ-2 / GateQ-3 已有只读 API 的响应结构，前端不得在这里扩展
 * trading authorization、LIVE enable、credential 或 runner write-side 字段。
 */
export interface StrategyValidationQuery {
    strategyId?: string;
    strategyVersionId?: string;
    datasetId?: string;
    evaluationId?: string;
    publishId?: string;
    paperRunId?: string;
    shadowRunId?: string;
}

export interface StrategyValidationScope {
    strategyId?: string | null;
    strategyVersionId?: string | null;
    datasetId?: string | null;
    evaluationId?: string | null;
    publishId?: string | null;
    paperRunId?: string | null;
    shadowRunId?: string | null;
}

export interface StrategyValidationEvidence {
    code: string;
    status: string;
    message: string;
}

export interface StrategyValidationReason {
    code: string;
    severity: string;
    message: string;
}

export type StrategyValidationDecision =
    'APPROVED'
    | 'REJECTED'
    | 'NEEDS_REVIEW'
    | 'BLOCKED'
    | 'NO_EVIDENCE'
    | 'STALE_EVIDENCE'
    | string;

/**
 * GateS-3 Strategy Validation overview response.
 *
 * Why:
 * 该结构只承接 `GET /api/strategy-validation/overview` 的 read-only validation runtime baseline。
 * `APPROVED` 只表示 validation 层面通过，不代表交易授权、实盘就绪或真实 provider 就绪。
 */
export interface StrategyValidationOverviewResponse {
    generatedAt: string;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
    totalStrategyVersions: number;
    evaluatedStrategyVersions: number;
    approvedForValidation: number;
    rejectedForValidation: number;
    needsReview: number;
    blocked: number;
    latestDecision: StrategyValidationLatestDecision | null;
    blockers: StrategyValidationBlocker[];
    warnings: StrategyValidationWarning[];
    nextSteps: StrategyValidationNextStep[];
    evidenceAnchors: StrategyValidationEvidenceAnchor[];
    traceId: string;
}

export interface StrategyValidationLatestDecision {
    strategyVersionId: string | null;
    datasetId: string | null;
    evaluationReportId: string | null;
    publishId: string | null;
    paperRunId: string | null;
    shadowRunId: string | null;
    decision: StrategyValidationDecision;
    decisionReasons: string[];
    limitations: string[];
    generatedAt: string;
    traceId: string;
}

export interface StrategyValidationEvidenceAnchor {
    sourceType: string;
    sourceId: string | null;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    checksum: string | null;
}

export interface StrategyValidationBlocker {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export interface StrategyValidationWarning {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export interface StrategyValidationNextStep {
    code: string;
    owner: string;
    action: string;
    completionCondition: string;
    boundaryCritical: boolean;
}

export interface StrategyEvaluationGateResponse {
    scope: StrategyValidationScope;
    strategyId?: string | null;
    strategyVersionId?: string | null;
    datasetId?: string | null;
    evaluationId?: string | null;
    publishId?: string | null;
    paperRunId?: string | null;
    gateStatus: string;
    gateDecision: string;
    evaluationStatus?: string | null;
    datasetQualityStatus?: string | null;
    paperEvidenceStatus?: string | null;
    publishTraceStatus?: string | null;
    requiredEvidence: StrategyValidationEvidence[];
    missingEvidence: StrategyValidationEvidence[];
    blockers: StrategyValidationReason[];
    warnings: StrategyValidationReason[];
    nextSteps: string[];
    generatedAt: string;
}

export interface PaperShadowComparisonResponse {
    scope: StrategyValidationScope;
    strategyId?: string | null;
    strategyVersionId?: string | null;
    datasetId?: string | null;
    evaluationId?: string | null;
    publishId?: string | null;
    paperRunId?: string | null;
    shadowRunId?: string | null;
    paperRunStatus?: string | null;
    shadowRunStatus?: string | null;
    comparisonStatus: string;
    evaluationGateStatus?: string | null;
    paperEvidenceStatus?: string | null;
    shadowEvidenceStatus?: string | null;
    dataQualityStatus?: string | null;
    comparable: boolean;
    requiredEvidence: StrategyValidationEvidence[];
    missingEvidence: StrategyValidationEvidence[];
    blockers: StrategyValidationReason[];
    warnings: StrategyValidationReason[];
    nextSteps: string[];
    generatedAt: string;
}

export interface ShadowLiveSideEffectPolicy {
    code: string;
    status: string;
    message: string;
}

export interface ShadowLivePreviewResponse {
    scope: StrategyValidationScope;
    strategyId?: string | null;
    strategyVersionId?: string | null;
    datasetId?: string | null;
    evaluationId?: string | null;
    publishId?: string | null;
    paperRunId?: string | null;
    shadowRunId?: string | null;
    runnerStatus?: string | null;
    previewStatus: string;
    evaluationGateStatus?: string | null;
    paperShadowComparisonStatus?: string | null;
    sideEffectPolicy: ShadowLiveSideEffectPolicy[];
    inputFactStatus?: string | null;
    traceStatus?: string | null;
    orderIntentPreviewStatus?: string | null;
    riskPreflightPreviewStatus?: string | null;
    requiredEvidence: StrategyValidationEvidence[];
    missingEvidence: StrategyValidationEvidence[];
    blockers: StrategyValidationReason[];
    warnings: StrategyValidationReason[];
    nextSteps: string[];
    generatedAt: string;
}

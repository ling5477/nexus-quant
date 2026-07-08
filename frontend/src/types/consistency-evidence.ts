/**
 * GateT-2 Consistency Evidence frontend types.
 *
 * Why:
 * 这些类型只描述 `GET /api/paper-shadow/consistency/evidence/overview` 的 read-only response。
 * Consistency evidence item 是 derived / deterministic 诊断条目，不是持久 review 记录、交易授权、
 * runner command、credential view、真实 provider 状态或自动处置结果。
 */
export type ConsistencyEvidenceComparisonStatus =
    'CONSISTENT'
    | 'DIVERGED'
    | 'PARTIAL'
    | 'NOT_COMPARABLE'
    | 'FAILED'
    | 'NO_REPORT'
    | string;

/**
 * Divergence severity 只表达诊断优先级。
 * HIGH / CRITICAL 不表示自动处置完成、行情方向、风控通过或交易授权。
 */
export type ConsistencyEvidenceDivergenceSeverity =
    'NONE'
    | 'LOW'
    | 'MEDIUM'
    | 'HIGH'
    | 'CRITICAL'
    | 'UNKNOWN'
    | string;

/**
 * Evidence freshness 只描述本地 evidence 新鲜度。
 * STALE / MISSING / PARTIAL / UNKNOWN 必须按 fail-closed 状态展示。
 */
export type ConsistencyEvidenceFreshness =
    'FRESH'
    | 'STALE'
    | 'MISSING'
    | 'PARTIAL'
    | 'UNKNOWN'
    | string;

export interface ConsistencyEvidenceMetricDeltaItem {
    name: string;
    delta: number | null;
    unit: string | null;
    comparable: boolean;
    limitationCodes: string[];
}

/**
 * MetricDeltaSummary 只承接后端摘要字段。
 * rawMetricDeltaExposed / profitConclusionInferred / tradingSignalInferred 必须保持 false 才符合本页边界。
 */
export interface ConsistencyEvidenceMetricDeltaSummary {
    metricCount: number;
    comparableMetricCount: number;
    nonComparableMetricCount: number;
    topDeltaMetrics: ConsistencyEvidenceMetricDeltaItem[];
    limitationCodes: string[];
    sensitiveFieldFilteredCount: number;
    rawMetricDeltaExposed: boolean;
    profitConclusionInferred: boolean;
    tradingSignalInferred: boolean;
}

export interface ConsistencyEvidenceAnchor {
    sourceType: string;
    sourceId: string | null;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    traceId: string | null;
    description: string | null;
}

export interface ConsistencyEvidenceBlocker {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export interface ConsistencyEvidenceWarning {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

/**
 * NextStep 只描述人工复核、补证据或排查动作。
 * 它不是 approve / reject / acknowledge 写侧命令，也不是 start / stop / execute / trade 动作。
 */
export interface ConsistencyEvidenceNextStep {
    code: string;
    owner: string;
    action: string;
    completionCondition: string;
    boundaryCritical: boolean;
}

/**
 * ConsistencyEvidenceItem 是从本地 consistency report 派生的前端展示项。
 * 所有 safety flags 必须原样展示；任一 flags 漂移时页面按 fail-closed 处理。
 */
export interface ConsistencyEvidenceItem {
    evidenceItemId: string;
    shadowRunId: string | null;
    paperRunId: string | null;
    consistencyReportId: string | null;
    strategyVersionId: string | null;
    datasetId: string | null;
    comparisonStatus: ConsistencyEvidenceComparisonStatus;
    divergenceSeverity: ConsistencyEvidenceDivergenceSeverity;
    evidenceFreshness: ConsistencyEvidenceFreshness;
    metricDelta: ConsistencyEvidenceMetricDeltaSummary;
    divergenceReasons: string[];
    limitations: string[];
    evidenceAnchors: ConsistencyEvidenceAnchor[];
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

/**
 * Overview response 是 GateT-2 前端唯一消费的 Consistency Evidence DTO。
 * 本类型不包含写侧 client、交易按钮、credential 字段、private provider 字段或真实账户 / 订单字段。
 */
export interface ConsistencyEvidenceOverviewResponse {
    generatedAt: string;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
    totalEvidenceItems: number;
    consistentCount: number;
    divergedCount: number;
    partialCount: number;
    notComparableCount: number;
    failedCount: number;
    staleEvidenceCount: number;
    highSeverityCount: number;
    criticalSeverityCount: number;
    latestEvidenceItem: ConsistencyEvidenceItem | null;
    evidenceItems: ConsistencyEvidenceItem[];
    severityBuckets: Record<string, number>;
    freshnessSummary: Record<string, number>;
    metricDeltaSummary: ConsistencyEvidenceMetricDeltaSummary;
    blockers: ConsistencyEvidenceBlocker[];
    warnings: ConsistencyEvidenceWarning[];
    nextSteps: ConsistencyEvidenceNextStep[];
    evidenceAnchors: ConsistencyEvidenceAnchor[];
    traceId: string;
}

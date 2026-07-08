/**
 * GateT-1 Shadow Validation Workflow frontend types.
 *
 * Why:
 * 这些类型只描述 `GET /api/shadow-validation/workflow/overview` 的 read-only response。
 * Operator item 是 derived / deterministic 诊断条目，不是持久 review 实体、交易授权记录、
 * runner command、credential view 或真实 provider 状态。
 */
export type ShadowValidationWorkflowState =
    'INTAKE'
    | 'EVIDENCE_REVIEW'
    | 'NEEDS_EVIDENCE'
    | 'READY_FOR_OPERATOR_REVIEW'
    | 'BLOCKED'
    | 'CLOSED_RECOMMENDATION'
    | string;

/**
 * Validation decision 只表达材料是否可进入人工复核。
 * `VALIDATION_READY` 不代表交易授权、LIVE 启用、策略批准或自动处置完成。
 */
export type ShadowValidationDecision =
    'NO_DECISION'
    | 'VALIDATION_READY'
    | 'NEEDS_REVIEW'
    | 'REJECTED'
    | 'BLOCKED'
    | 'STALE_EVIDENCE'
    | string;

/**
 * Severity 只用于诊断优先级排序。
 * HIGH / CRITICAL 不映射为行情方向、盈亏、自动风控处置或交易状态。
 */
export type ShadowValidationSeverity =
    'NONE'
    | 'INFO'
    | 'WARNING'
    | 'HIGH'
    | 'CRITICAL'
    | 'UNKNOWN'
    | string;

/**
 * Evidence freshness 只描述本地 evidence 是否足够新鲜。
 * STALE / MISSING / PARTIAL 必须 fail-closed 展示为需要补证据。
 */
export type ShadowValidationEvidenceFreshness =
    'FRESH'
    | 'STALE'
    | 'MISSING'
    | 'PARTIAL'
    | 'UNKNOWN'
    | string;

/**
 * Blocker 描述人工复核前必须处理的诊断阻断。
 * sourceId 可空，因为某些全局安全边界没有单一事实 id。
 */
export interface ShadowValidationBlocker {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

/**
 * Warning 描述非阻断但必须保留可见的诊断风险。
 * 前端只展示脱敏文本，不把 warning 缺失解释为 workflow 已完成。
 */
export interface ShadowValidationWarning {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

/**
 * Next step 只描述人工复核、补证据或工程检查。
 * 它不是 approve / reject / review / acknowledge 写侧命令，也不是交易动作。
 */
export interface ShadowValidationNextStep {
    code: string;
    owner: string;
    action: string;
    completionCondition: string;
    boundaryCritical: boolean;
}

/**
 * Evidence anchor 只定位本地 read-only fact source。
 * description 用于解释证据来源，不得承载 raw payload、credential 或 provider response。
 */
export interface ShadowValidationEvidenceAnchor {
    sourceType: string;
    sourceId: string | null;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    traceId: string | null;
    description: string | null;
}

/**
 * Operator item 是从 GateS 本地事实派生的人工复核视图项。
 * 所有 safety flags 必须按后端返回展示，任一 flags 漂移时页面应 fail-closed。
 */
export interface ShadowValidationOperatorItem {
    operatorItemId: string;
    sourceType: string;
    sourceId: string;
    strategyVersionId: string | null;
    datasetId: string | null;
    evaluationReportId: string | null;
    paperRunId: string | null;
    shadowRunId: string | null;
    consistencyReportId: string | null;
    incidentEvidenceId: string | null;
    workflowState: ShadowValidationWorkflowState;
    validationDecision: ShadowValidationDecision;
    severity: ShadowValidationSeverity;
    evidenceFreshness: ShadowValidationEvidenceFreshness;
    blockers: ShadowValidationBlocker[];
    warnings: ShadowValidationWarning[];
    nextSteps: ShadowValidationNextStep[];
    evidenceAnchors: ShadowValidationEvidenceAnchor[];
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
 * Overview response 是 GateT-1 前端唯一消费的 Shadow Validation Workflow DTO。
 * 本类型不包含 start / stop / execute / trade / credential / private provider 字段。
 */
export interface ShadowValidationWorkflowOverviewResponse {
    generatedAt: string;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
    totalOperatorItems: number;
    intakeCount: number;
    evidenceReviewCount: number;
    needsEvidenceCount: number;
    readyForOperatorReviewCount: number;
    blockedCount: number;
    closedRecommendationCount: number;
    latestOperatorItem: ShadowValidationOperatorItem | null;
    operatorItems: ShadowValidationOperatorItem[];
    blockers: ShadowValidationBlocker[];
    warnings: ShadowValidationWarning[];
    nextSteps: ShadowValidationNextStep[];
    evidenceAnchors: ShadowValidationEvidenceAnchor[];
    traceId: string;
}

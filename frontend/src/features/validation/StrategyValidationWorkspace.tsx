import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    ClearOutlined,
    ReloadOutlined,
    SearchOutlined,
} from '@ant-design/icons';
import {
    Alert,
    Button,
    Card,
    Descriptions,
    Empty,
    Form,
    Input,
    Skeleton,
    Space,
    Table,
    Tag,
    Tooltip,
    Typography
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useMemo, type ReactNode} from 'react';
import {Link} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {DataFreshness, type FreshnessState} from '@/nq-design-system/status/DataFreshness';
import {StatusTag as CanonicalStatusTag} from '@/nq-design-system/status/StatusTag';
import {useStrategyValidationWorkspaceQueries} from '@/features/validation/hooks/useStrategyValidationWorkspaceQueries';
import {StrategyReleaseAdmissionPreviewPanel} from '@/features/validation/StrategyReleaseAdmissionPreviewPanel';
import {ValidationReviewSection} from '@/features/validation/review/ValidationReviewSection';
import type {AppApiError} from '@/types/api';
import type {ReadModelEvidenceMetadata} from '@/types/read-model-evidence';
import type {
    ConsistencyEvidenceAnchor,
    ConsistencyEvidenceBlocker,
    ConsistencyEvidenceItem,
    ConsistencyEvidenceMetricDeltaItem,
    ConsistencyEvidenceNextStep,
    ConsistencyEvidenceOverviewResponse,
    ConsistencyEvidenceWarning,
} from '@/features/validation/types/consistency-evidence';
import type {
    EvaluationArtifactPreviewBlocker,
    EvaluationArtifactPreviewEvidenceAnchor,
    EvaluationArtifactPreviewNextStep,
    EvaluationArtifactPreviewWarning,
    PythonEvaluationArtifactPreviewItem,
    PythonEvaluationArtifactPreviewOverviewResponse,
} from '@/features/validation/types/evaluation-artifact-preview';
import type {
    IncidentReplayBlocker,
    IncidentReplayEvidenceAnchor,
    IncidentReplayLatestEvidence,
    IncidentReplayNextStep,
    IncidentReplayOverviewResponse,
    IncidentReplaySeverity,
    IncidentReplayWarning,
} from '@/features/validation/types/incident-replay';
import type {
    IncidentReplayReviewBlocker,
    IncidentReplayReviewEvidenceAnchor,
    IncidentReplayReviewItem,
    IncidentReplayReviewNextStep,
    IncidentReplayReviewOverviewResponse,
    IncidentReplayReviewWarning,
} from '@/features/validation/types/incident-replay-review';
import type {
    JsonValue,
    PaperShadowConsistencyDrilldownResponse,
    ShadowRunOverviewResponse,
} from '@/features/validation/types/shadow-runs';
import type {
    ShadowValidationBlocker,
    ShadowValidationEvidenceAnchor,
    ShadowValidationNextStep,
    ShadowValidationOperatorItem,
    ShadowValidationWarning,
    ShadowValidationWorkflowOverviewResponse,
} from '@/features/validation/types/shadow-validation-workflow';
import type {
    PaperShadowComparisonResponse,
    ShadowLivePreviewResponse,
    ShadowLiveSideEffectPolicy,
    StrategyEvaluationGateResponse,
    StrategyValidationBlocker,
    StrategyValidationEvidence,
    StrategyValidationEvidenceAnchor,
    StrategyValidationLatestDecision,
    StrategyValidationNextStep,
    StrategyValidationOverviewResponse,
    StrategyValidationQuery,
    StrategyValidationReason,
    StrategyValidationScope,
    StrategyValidationWarning,
} from '@/features/validation/types/strategy-validation';
import type {
    ValidationOperationsRuntimeEvidenceOverviewResponse,
    ValidationOperationsRuntimeEvidenceSource,
} from '@/features/validation/types/validation-operations-runtime-evidence';
import {formatDateTime} from '@/utils/formatters';

const {Paragraph, Text} = Typography;

type StatusTone = 'success' | 'info' | 'neutral' | 'warning' | 'danger';

interface StatusPresentation {
    label: string;
    tone: StatusTone;
}

const WORKBENCH_SENSITIVE_TEXT_PATTERN = /(api[_-]?key|secret|passphrase|private[_ -]?key|credentialMaterial|encrypted[_ -]?payload|decrypted[_ -]?payload|rawSignature|rawPrivate|private endpoint|realOrderId|realAccountBalance|authorizedForTrading|tradingReady|liveReady|tradeApproved|token)/i;
const WORKBENCH_FORBIDDEN_ACTION_TEXT_PATTERN = /(ready\s+to\s+trade|live\s+ready|trade[_\s-]+approved|can\s+trade|placeOrder|cancelOrder|withdraw|transfer)/i;

interface PanelQueryState<TData> {
    data?: TData;
    isLoading: boolean;
    isFetching: boolean;
    isError: boolean;
    error: unknown;
    refetch: () => void;
}

interface ResultPanelProps<TData> {
    title: string;
    subtitle: string;
    status?: string | null;
    submitted: boolean;
    query: PanelQueryState<TData>;
    requiredEvidence?: StrategyValidationEvidence[];
    missingEvidence?: StrategyValidationEvidence[];
    blockers?: StrategyValidationReason[];
    warnings?: StrategyValidationReason[];
    nextSteps?: string[];
    boundaryDescription: ReactNode;
    children: ReactNode;
}

interface LifecycleTraceItem {
    key: string;
    label: string;
    value?: string | null;
    status: string;
    source: string;
    detail: ReactNode;
}

type EvidenceMatrixCategory = 'requiredEvidence' | 'missingEvidence' | 'blockers' | 'warnings' | 'nextSteps';

interface EvidenceMatrixRow {
    key: string;
    source: string;
    category: EvidenceMatrixCategory;
    code: string;
    status: string;
    message: string;
}

interface EvidenceSourceData {
    requiredEvidence?: StrategyValidationEvidence[];
    missingEvidence?: StrategyValidationEvidence[];
    blockers?: StrategyValidationReason[];
    warnings?: StrategyValidationReason[];
    nextSteps?: string[];
}

type StrategyValidationOverviewIssue = StrategyValidationBlocker | StrategyValidationWarning;

type IncidentReplayOverviewIssue = IncidentReplayBlocker | IncidentReplayWarning;

type ShadowValidationWorkflowIssue = ShadowValidationBlocker | ShadowValidationWarning;

type ConsistencyEvidenceOverviewIssue = ConsistencyEvidenceBlocker | ConsistencyEvidenceWarning;

type IncidentReplayReviewOverviewIssue = IncidentReplayReviewBlocker | IncidentReplayReviewWarning;

type EvaluationArtifactPreviewOverviewIssue = EvaluationArtifactPreviewBlocker | EvaluationArtifactPreviewWarning;

type OverviewStateLevel = 'info' | 'warning' | 'error';

interface OverviewPanelState {
    level: OverviewStateLevel;
    message: string;
    description: string;
}

interface StatusExplanationRow {
    status: string;
    meaning: string;
    boundary: string;
}

interface WorkbenchQueryBundle {
    strategyOverview: PanelQueryState<StrategyValidationOverviewResponse>;
    shadowOverview: PanelQueryState<ShadowRunOverviewResponse>;
    drilldown: PanelQueryState<PaperShadowConsistencyDrilldownResponse>;
    shadowRunId: string | null;
}

interface WorkbenchSignalRow {
    key: string;
    source: string;
    kind: 'blocker' | 'warning';
    code: string;
    severity: string;
    message: string;
}

interface WorkbenchNextStepRow {
    key: string;
    source: string;
    code: string;
    owner: string;
    action: string;
    evidence: string;
    blocking: boolean;
}

interface WorkbenchEvidenceAnchorRow {
    key: string;
    source: string;
    sourceType: string;
    sourceId: string | null;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    checksum: string | null;
}

interface ConsistencyEvidenceBucketRow {
    key: string;
    source: 'severityBuckets' | 'freshnessSummary';
    bucket: string;
    count: number;
}

interface IncidentReplayReviewBucketRow {
    key: string;
    source: 'severityBuckets' | 'freshnessSummary';
    bucket: string;
    count: number;
}

interface EvaluationArtifactPreviewBucketRow {
    key: string;
    source: 'schemaVersionSummary' | 'checksumSummary' | 'metricSummaryCoverage';
    bucket: string;
    count: number;
}

interface ValidationOperationsQueryBundle {
    strategyOverview: PanelQueryState<StrategyValidationOverviewResponse>;
    shadowWorkflow: PanelQueryState<ShadowValidationWorkflowOverviewResponse>;
    consistencyEvidence: PanelQueryState<ConsistencyEvidenceOverviewResponse>;
    incidentReplayReview: PanelQueryState<IncidentReplayReviewOverviewResponse>;
    artifactPreview: PanelQueryState<PythonEvaluationArtifactPreviewOverviewResponse>;
}

interface ValidationOperationsSummaryRow {
    key: string;
    lane: string;
    status: string;
    primaryMetric: string;
    blockers: number;
    warnings: number;
    nextStep: string;
    generatedAt: string | null;
}

interface ValidationOperationsEvidenceRow {
    key: string;
    lane: string;
    evidence: string;
    status: string;
    count: string;
    detail: string;
}

interface ValidationOperationsOperatorQueueRow {
    key: string;
    source: string;
    itemId: string;
    state: string;
    severity: string;
    freshness: string;
    decision: string;
    traceId: string;
}

const STATUS_PRESENTATION: Record<string, StatusPresentation> = {
    APPROVED: {get label() { return t('pages:validationPassedNotTradingAuthorization'); }, tone: 'info'},
    REJECTED: {get label() { return t('pages:validationRejected'); }, tone: 'danger'},
    NEEDS_REVIEW: {get label() { return t('pages:reviewRequired'); }, tone: 'warning'},
    BLOCKED: {get label() { return t('pages:blocked2'); }, tone: 'danger'},
    NO_EVIDENCE: {get label() { return t('pages:noEvidence'); }, tone: 'neutral'},
    STALE_EVIDENCE: {get label() { return t('pages:staleOrIncompleteEvidence'); }, tone: 'warning'},
    NO_ARTIFACT_SOURCE_CONFIGURED: {get label() { return t('pages:artifactSourceNotConfigured'); }, tone: 'warning'},
    NO_FILE_BASELINE: {get label() { return t('pages:noFileBaseline'); }, tone: 'warning'},
    DIAGNOSTIC_ONLY: {get label() { return t('pages:diagnosticOnly'); }, tone: 'info'},
    VALID: {get label() { return t('pages:checksumConsistentNotStrategyValidity'); }, tone: 'info'},
    INVALID: {get label() { return t('pages:checksumFailed'); }, tone: 'danger'},
    NOT_CHECKED: {get label() { return t('pages:notChecked'); }, tone: 'neutral'},
    FAKE_FIXTURE_ONLY: {get label() { return t('pages:testFixtureNotRealPerformance'); }, tone: 'warning'},
    PRESENT: {get label() { return t('pages:summaryAvailableNotAReturnConclusion'); }, tone: 'info'},
    INCOMPLETE: {get label() { return t('pages:incompleteSummary'); }, tone: 'warning'},
    CONSISTENT: {get label() { return t('pages:evidenceConsistentNotAProfitConclusion'); }, tone: 'info'},
    DIVERGED: {get label() { return t('pages:evidenceDiverged'); }, tone: 'warning'},
    NO_REPORT: {get label() { return t('pages:noConsistencyReport'); }, tone: 'neutral'},
    NOT_COMPARABLE: {get label() { return t('pages:notComparable'); }, tone: 'warning'},
    NO_CONSISTENCY_EVIDENCE: {get label() { return t('pages:noConsistencyEvidence'); }, tone: 'warning'},
    INTAKE: {get label() { return t('pages:awaitingEvidenceWorkflow'); }, tone: 'info'},
    EVIDENCE_REVIEW: {get label() { return t('pages:evidenceUnderReview'); }, tone: 'warning'},
    NEEDS_EVIDENCE: {get label() { return t('pages:additionalEvidenceRequired'); }, tone: 'warning'},
    READY_FOR_OPERATOR_REVIEW: {get label() { return t('pages:readyForManualReviewNotTradingAuthorization'); }, tone: 'info'},
    VALIDATION_READY: {get label() { return t('pages:validationEvidenceReviewableNotTradingAuthorization'); }, tone: 'info'},
    CLOSED_RECOMMENDATION: {get label() { return t('pages:closeoutRecommendedNotActualClosure'); }, tone: 'info'},
    NEEDS_OPERATOR_REVIEW: {get label() { return t('pages:manualReviewRequired'); }, tone: 'warning'},
    ACKNOWLEDGED_RECOMMENDATION: {get label() { return t('pages:manualAcknowledgmentRecommendedNotAutomaticHandling'); }, tone: 'info'},
    ESCALATED_RECOMMENDATION: {get label() { return t('pages:manualEscalationRecommended'); }, tone: 'danger'},
    NO_DECISION: {get label() { return t('pages:noDiagnosticRecommendation'); }, tone: 'neutral'},
    REVIEW_NEEDED: {get label() { return t('pages:reviewRequired'); }, tone: 'warning'},
    ACKNOWLEDGE_RECOMMENDED: {get label() { return t('pages:manualAcknowledgmentRecommendedNotAutomaticHandling'); }, tone: 'info'},
    ESCALATE_RECOMMENDED: {get label() { return t('pages:manualEscalationRecommendedNotSystemEscalation'); }, tone: 'danger'},
    CLOSEOUT_RECOMMENDED: {get label() { return t('pages:diagnosticCloseoutRecommendedNotActualClosure'); }, tone: 'info'},
    READY_FOR_SHADOW_REVIEW: {get label() { return t('pages:readyForShadowReview'); }, tone: 'info'},
    READY_FOR_COMPARISON: {get label() { return t('pages:readOnlyComparisonAvailable'); }, tone: 'info'},
    READY_FOR_NO_SIDE_EFFECT_PREVIEW: {get label() { return t('pages:noSideEffectPreviewAvailable'); }, tone: 'info'},
    VALID_FOR_BINDING_PREVIEW: {get label() { return t('pages:readOnlyBindingPreviewAvailable'); }, tone: 'info'},
    PENDING_FRONTEND_SUPPORT: {get label() { return t('pages:awaitingFrontendSupport'); }, tone: 'warning'},
    NOT_CONNECTED: {get label() { return t('pages:notConnected'); }, tone: 'warning'},
    ACTION_REQUIRED: {get label() { return t('pages:followUpRequired'); }, tone: 'warning'},
    SKELETON_AVAILABLE: {get label() { return t('pages:skeletonAvailable'); }, tone: 'info'},
    PREVIEW_ONLY: {get label() { return t('pages:previewOnly'); }, tone: 'info'},
    NOT_EXECUTED: {get label() { return t('pages:notExecuted'); }, tone: 'neutral'},
    NOT_IMPLEMENTED: {get label() { return t('pages:capabilityNotImplemented'); }, tone: 'warning'},
    UNKNOWN: {get label() { return t('pages:unknown'); }, tone: 'neutral'},
    NOT_AVAILABLE: {get label() { return t('pages:unavailable'); }, tone: 'neutral'},
    PARTIAL: {get label() { return t('pages:partiallyAvailable'); }, tone: 'warning'},
    BLOCKED_SHADOW_NOT_IMPLEMENTED: {get label() { return t('pages:blockedShadowNotImplemented'); }, tone: 'danger'},
    PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE: {get label() { return t('pages:shadowFactsUnavailable'); }, tone: 'danger'},
    PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE: {get label() { return t('pages:traceChainIncomplete'); }, tone: 'danger'},
    PREVIEW_BLOCKED_EVALUATION_GATE: {get label() { return t('pages:evaluationGateBlocked'); }, tone: 'danger'},
    PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON: {get label() { return t('pages:paperShadowComparisonBlocked'); }, tone: 'danger'},
    SATISFIED: {get label() { return t('pages:satisfied'); }, tone: 'success'},
    MISSING: {get label() { return t('pages:missing'); }, tone: 'warning'},
    FAILED: {get label() { return t('pages:failed'); }, tone: 'danger'},
    FORBIDDEN: {get label() { return t('pages:prohibited'); }, tone: 'danger'},
    BLOCKER: {get label() { return t('pages:blocked2'); }, tone: 'danger'},
    WARNING: {get label() { return t('pages:warnings'); }, tone: 'warning'},
    SUCCEEDED: {get label() { return t('pages:success'); }, tone: 'success'},
    ACTIVE: {get label() { return t('pages:valid'); }, tone: 'success'},
    CREATED: {get label() { return t('pages:created2'); }, tone: 'info'},
    READY: {get label() { return t('pages:diagnosticsReadyNotTradingClearance'); }, tone: 'info'},
    RUNNING: {get label() { return t('pages:diagnosticsRunning'); }, tone: 'info'},
    COMPLETED: {get label() { return t('pages:diagnosticsCompleteNotAReturnConclusion'); }, tone: 'info'},
    STOPPED: {get label() { return t('pages:stopped2'); }, tone: 'neutral'},
    CANCELLED: {get label() { return t('pages:cancelled2'); }, tone: 'neutral'},
    LOW: {get label() { return t('pages:lowDivergence'); }, tone: 'warning'},
    MEDIUM: {get label() { return t('pages:mediumDivergence'); }, tone: 'warning'},
    HIGH: {get label() { return t('pages:highDivergence'); }, tone: 'danger'},
    CRITICAL: {get label() { return t('pages:criticalDivergence'); }, tone: 'danger'},
};

const EVIDENCE_CATEGORY_LABELS: Record<EvidenceMatrixCategory, string> = {
    requiredEvidence: 'requiredEvidence',
    missingEvidence: 'missingEvidence',
    blockers: 'blockers',
    warnings: 'warnings',
    nextSteps: 'nextSteps',
};

const STATUS_EXPLANATIONS: StatusExplanationRow[] = [
    {
        status: 'READY_FOR_SHADOW_REVIEW',
        get meaning() { return t('pages:readyForShadowReview'); },
        get boundary() { return t('pages:researchAndEvaluationEvidenceMayProceedToReviewThisDoesNotPermitTradingOrderPlacementOrEnablingLive'); },
    },
    {
        status: 'READY_FOR_COMPARISON',
        get meaning() { return t('pages:readOnlyComparisonAvailable'); },
        get boundary() { return t('pages:readOnlyPaperAndShadowEvidenceCanBeComparedNoShadowRunIsCreatedAndNoTradingAuthorizationIsGranted'); },
    },
    {
        status: 'READY_FOR_NO_SIDE_EFFECT_PREVIEW',
        get meaning() { return t('pages:noSideEffectPreviewAvailable'); },
        get boundary() { return t('pages:aNoSideEffectPreviewMayBeGeneratedNoStrategyExecutionOrderSubmissionOrRealStateWriteOccurs'); },
    },
    {
        status: 'VALID_FOR_BINDING_PREVIEW',
        get meaning() { return t('pages:bindingPreviewAvailable'); },
        get boundary() { return t('pages:theArtifactSupportsReadOnlyValidationPreviewOnlyNotImportPublicationMlReadinessOrLiveExecutionReadin'); },
    },
    {
        status: 'UNKNOWN / NOT_AVAILABLE / NOT_IMPLEMENTED / BLOCKED_*',
        get meaning() { return t('pages:unknownUnavailableUnimplementedOrBlocked'); },
        get boundary() { return t('pages:displayAsMissingOrBlockedNeverSuccessRetainBlockersAndNextsteps'); },
    },
];

const FORBIDDEN_BOUNDARY_ITEMS = [
    'pages:noRealOrderSubmission',
    'pages:noRealCredentialReads',
    'pages:noLiveEnablement',
    'pages:noPrivateEndpointCalls',
    'pages:noRealAccountFundOrLedgerWrites',
    'pages:noAiDhRuntimeExecution',
];

const TONE_TO_COLOR: Record<StatusTone, string> = {
    success: 'success',
    info: 'processing',
    neutral: 'default',
    warning: 'warning',
    danger: 'error',
};

const QUERY_FIELDS: Array<keyof StrategyValidationQuery> = [
    'strategyId',
    'strategyVersionId',
    'datasetId',
    'evaluationId',
    'publishId',
    'paperRunId',
    'shadowRunId',
];

const FIELD_LABELS: Record<keyof StrategyValidationQuery, string> = {
    strategyId: 'strategyId',
    strategyVersionId: 'strategyVersionId',
    datasetId: 'datasetId',
    evaluationId: 'evaluationId',
    publishId: 'publishId',
    paperRunId: 'paperRunId',
    shadowRunId: 'shadowRunId',
};

const evidenceColumns: ColumnsType<StrategyValidationEvidence> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 220,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 180,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const reasonColumns: ColumnsType<StrategyValidationReason> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:level'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 140,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const sideEffectColumns: ColumnsType<ShadowLiveSideEffectPolicy> = [
    {
        get title() { return t('pages:policy'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 140,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:scope'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const lifecycleColumns: ColumnsType<LifecycleTraceItem> = [
    {
        get title() { return t('pages:node'); },
        dataIndex: 'label',
        key: 'label',
        width: 230,
        render: (_value: string, record) => (
            <Space direction="vertical" size={2}>
                <Text strong>{record.label}</Text>
                <Text code>{record.key}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:traceValue'); },
        dataIndex: 'value',
        key: 'value',
        width: 220,
        render: (value?: string | null) => optionalCode(value),
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 220,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 260,
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
    {
        get title() { return t('pages:scope'); },
        dataIndex: 'detail',
        key: 'detail',
        render: (value: ReactNode) => <Text type="secondary">{value}</Text>,
    },
];

const evidenceMatrixColumns: ColumnsType<EvidenceMatrixRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 210,
        render: (value: string) => <Text>{value}</Text>,
    },
    {
        get title() { return t('pages:category'); },
        dataIndex: 'category',
        key: 'category',
        width: 170,
        render: (value: EvidenceMatrixCategory) => <Text code>{EVIDENCE_CATEGORY_LABELS[value]}</Text>,
    },
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 180,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:explanationNextsteps'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const statusExplanationColumns: ColumnsType<StatusExplanationRow> = [
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 280,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:pageInterpretation'); },
        dataIndex: 'meaning',
        key: 'meaning',
        width: 220,
        render: (value: string) => <Text>{value}</Text>,
    },
    {
        get title() { return t('pages:invalidInterpretation'); },
        dataIndex: 'boundary',
        key: 'boundary',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const overviewIssueColumns: ColumnsType<StrategyValidationOverviewIssue> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:level'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 150,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{record.sourceType}</Text>
                {record.sourceId ? <Text code>{record.sourceId}</Text> : <Text type="secondary">{t('pages:noSourceid')}</Text>}
            </Space>
        ),
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const overviewNextStepColumns: ColumnsType<StrategyValidationNextStep> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:owner'); },
        dataIndex: 'owner',
        key: 'owner',
        width: 160,
    },
    {
        get title() { return t('pages:action'); },
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{value}</Text>,
    },
    {
        get title() { return t('pages:completionCriteria'); },
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
    {
        get title() { return t('pages:boundaryCritical'); },
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
    },
];

const overviewEvidenceAnchorColumns: ColumnsType<StrategyValidationEvidenceAnchor> = [
    {
        title: 'sourceType',
        dataIndex: 'sourceType',
        key: 'sourceType',
        width: 190,
        render: (value: string) => <Text>{value}</Text>,
    },
    {
        title: 'sourceId',
        dataIndex: 'sourceId',
        key: 'sourceId',
        width: 230,
        render: (value: string | null) => optionalCode(value),
    },
    {
        title: 'sourceVersion',
        dataIndex: 'sourceVersion',
        key: 'sourceVersion',
        width: 180,
        render: (value: string | null) => optionalCode(value),
    },
    {
        title: 'sourceTimestamp',
        dataIndex: 'sourceTimestamp',
        key: 'sourceTimestamp',
        width: 210,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'checksum',
        dataIndex: 'checksum',
        key: 'checksum',
        width: 220,
        render: (value: string | null) => optionalCode(value),
    },
];

const incidentLatestEvidenceColumns: ColumnsType<IncidentReplayLatestEvidence> = [
    {
        title: 'evidenceType',
        dataIndex: 'evidenceType',
        key: 'evidenceType',
        width: 210,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'sourceStatus',
        dataIndex: 'sourceStatus',
        key: 'sourceStatus',
        width: 170,
        render: (value: string | null) => <StatusTag status={value}/>,
    },
    {
        title: 'summary',
        dataIndex: 'summary',
        key: 'summary',
        render: (value: string | null) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'occurredAt',
        dataIndex: 'occurredAt',
        key: 'occurredAt',
        width: 210,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'traceId',
        dataIndex: 'traceId',
        key: 'traceId',
        width: 240,
        render: (value: string | null) => optionalSafeCode(value),
    },
];

const incidentOverviewIssueColumns: ColumnsType<IncidentReplayOverviewIssue> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:level'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 150,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">{t('pages:noSourceid')}</Text>
                )}
            </Space>
        ),
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const shadowValidationWorkflowIssueColumns: ColumnsType<ShadowValidationWorkflowIssue> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:diagnosticPriority'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">{t('pages:noSourceid')}</Text>
                )}
            </Space>
        ),
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const shadowValidationWorkflowNextStepColumns: ColumnsType<ShadowValidationNextStep> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:owner'); },
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:action'); },
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:completionCriteria'); },
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:boundaryCritical'); },
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
    },
];

const shadowValidationWorkflowEvidenceAnchorColumns: ColumnsType<ShadowValidationEvidenceAnchor> = [
    {
        title: 'sourceType',
        dataIndex: 'sourceType',
        key: 'sourceType',
        width: 190,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'sourceId',
        dataIndex: 'sourceId',
        key: 'sourceId',
        width: 230,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceVersion',
        dataIndex: 'sourceVersion',
        key: 'sourceVersion',
        width: 170,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceTimestamp',
        dataIndex: 'sourceTimestamp',
        key: 'sourceTimestamp',
        width: 190,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'traceId',
        dataIndex: 'traceId',
        key: 'traceId',
        width: 240,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'description',
        dataIndex: 'description',
        key: 'description',
        render: (value: string | null) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const shadowValidationWorkflowOperatorColumns: ColumnsType<ShadowValidationOperatorItem> = [
    {
        title: 'operatorItemId',
        dataIndex: 'operatorItemId',
        key: 'operatorItemId',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'workflowState',
        dataIndex: 'workflowState',
        key: 'workflowState',
        width: 260,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'validationDecision',
        dataIndex: 'validationDecision',
        key: 'validationDecision',
        width: 260,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'severity',
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'evidenceFreshness',
        dataIndex: 'evidenceFreshness',
        key: 'evidenceFreshness',
        width: 220,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        key: 'source',
        width: 260,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                <Text code>{workbenchSafeText(record.sourceId)}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:blockersWarnings'); },
        key: 'signals',
        width: 190,
        render: (_, record) => (
            <Space size={6} wrap>
                <Tag color={record.blockers.length > 0 ? 'error' : 'default'}>
                    blockers {record.blockers.length}
                </Tag>
                <Tag color={record.warnings.length > 0 ? 'warning' : 'default'}>
                    warnings {record.warnings.length}
                </Tag>
            </Space>
        ),
    },
];

const consistencyEvidenceIssueColumns: ColumnsType<ConsistencyEvidenceOverviewIssue> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:diagnosticPriority'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">{t('pages:noSourceid')}</Text>
                )}
            </Space>
        ),
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const consistencyEvidenceNextStepColumns: ColumnsType<ConsistencyEvidenceNextStep> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:owner'); },
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:action'); },
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:completionCriteria'); },
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:boundaryCritical'); },
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
    },
];

const consistencyEvidenceAnchorColumns: ColumnsType<ConsistencyEvidenceAnchor> = [
    {
        title: 'sourceType',
        dataIndex: 'sourceType',
        key: 'sourceType',
        width: 190,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'sourceId',
        dataIndex: 'sourceId',
        key: 'sourceId',
        width: 230,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceVersion',
        dataIndex: 'sourceVersion',
        key: 'sourceVersion',
        width: 170,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceTimestamp',
        dataIndex: 'sourceTimestamp',
        key: 'sourceTimestamp',
        width: 190,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'traceId',
        dataIndex: 'traceId',
        key: 'traceId',
        width: 240,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'description',
        dataIndex: 'description',
        key: 'description',
        render: (value: string | null) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const consistencyEvidenceBucketColumns: ColumnsType<ConsistencyEvidenceBucketRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 190,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:bucket'); },
        dataIndex: 'bucket',
        key: 'bucket',
        width: 220,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'count',
        dataIndex: 'count',
        key: 'count',
        width: 120,
        render: (value: number) => <Text>{value}</Text>,
    },
];

const consistencyEvidenceMetricColumns: ColumnsType<ConsistencyEvidenceMetricDeltaItem> = [
    {
        title: 'metric',
        dataIndex: 'name',
        key: 'name',
        width: 220,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'delta',
        dataIndex: 'delta',
        key: 'delta',
        width: 140,
        render: (value: number | null) => value === null ? <StatusTag status="NOT_AVAILABLE"/> : <Text>{value}</Text>,
    },
    {
        title: 'unit',
        dataIndex: 'unit',
        key: 'unit',
        width: 120,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'comparable',
        dataIndex: 'comparable',
        key: 'comparable',
        width: 140,
        render: (value: boolean) => (
            <Tag color={value ? 'processing' : 'warning'}>
                {value ? t('pages:trueDiagnosticallyComparable') : t('pages:falseNotComparable')}
            </Tag>
        ),
    },
    {
        title: 'limitationCodes',
        dataIndex: 'limitationCodes',
        key: 'limitationCodes',
        render: (value: string[]) => <Text type="secondary">{safeTextListSummary(value)}</Text>,
    },
];

const consistencyEvidenceItemColumns: ColumnsType<ConsistencyEvidenceItem> = [
    {
        title: 'evidenceItemId',
        dataIndex: 'evidenceItemId',
        key: 'evidenceItemId',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'comparisonStatus',
        dataIndex: 'comparisonStatus',
        key: 'comparisonStatus',
        width: 240,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'divergenceSeverity',
        dataIndex: 'divergenceSeverity',
        key: 'divergenceSeverity',
        width: 220,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'evidenceFreshness',
        dataIndex: 'evidenceFreshness',
        key: 'evidenceFreshness',
        width: 220,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:relatedId'); },
        key: 'ids',
        width: 300,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>shadowRunId {record.shadowRunId ?
                    <Text code>{workbenchSafeText(record.shadowRunId)}</Text> : t('pages:none')}</Text>
                <Text>paperRunId {record.paperRunId ?
                    <Text code>{workbenchSafeText(record.paperRunId)}</Text> : t('pages:none')}</Text>
                <Text>consistencyReportId {record.consistencyReportId ? (
                    <Text code>{workbenchSafeText(record.consistencyReportId)}</Text>
                ) : t('pages:none')}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:divergenceReasonsLimitations'); },
        key: 'summaries',
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text type="secondary">{t('pages:reasons3')}{safeTextListSummary(record.divergenceReasons)}</Text>
                <Text type="secondary">{t('pages:limitations')}{safeTextListSummary(record.limitations)}</Text>
            </Space>
        ),
    },
];

const incidentReplayReviewIssueColumns: ColumnsType<IncidentReplayReviewOverviewIssue> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:diagnosticPriority'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">{t('pages:noSourceid')}</Text>
                )}
            </Space>
        ),
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const incidentReplayReviewNextStepColumns: ColumnsType<IncidentReplayReviewNextStep> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:owner'); },
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:action'); },
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:completionCriteria'); },
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:boundaryCritical'); },
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
    },
];

const incidentReplayReviewEvidenceAnchorColumns: ColumnsType<IncidentReplayReviewEvidenceAnchor> = [
    {
        title: 'sourceType',
        dataIndex: 'sourceType',
        key: 'sourceType',
        width: 190,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'sourceId',
        dataIndex: 'sourceId',
        key: 'sourceId',
        width: 230,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceVersion',
        dataIndex: 'sourceVersion',
        key: 'sourceVersion',
        width: 170,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceTimestamp',
        dataIndex: 'sourceTimestamp',
        key: 'sourceTimestamp',
        width: 190,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'traceId',
        dataIndex: 'traceId',
        key: 'traceId',
        width: 240,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'description',
        dataIndex: 'description',
        key: 'description',
        render: (value: string | null) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const incidentReplayReviewBucketColumns: ColumnsType<IncidentReplayReviewBucketRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 190,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:bucket'); },
        dataIndex: 'bucket',
        key: 'bucket',
        width: 220,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'count',
        dataIndex: 'count',
        key: 'count',
        width: 120,
        render: (value: number) => <Text>{value}</Text>,
    },
];

const incidentReplayReviewItemColumns: ColumnsType<IncidentReplayReviewItem> = [
    {
        title: 'reviewItemId',
        dataIndex: 'reviewItemId',
        key: 'reviewItemId',
        width: 270,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'reviewState',
        dataIndex: 'reviewState',
        key: 'reviewState',
        width: 280,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'reviewDecision',
        dataIndex: 'reviewDecision',
        key: 'reviewDecision',
        width: 300,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'severity',
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'evidenceFreshness',
        dataIndex: 'evidenceFreshness',
        key: 'evidenceFreshness',
        width: 220,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:relatedId'); },
        key: 'ids',
        width: 320,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>source {workbenchSafeText(record.sourceType)} / <Text code>{workbenchSafeText(record.sourceId)}</Text></Text>
                <Text>shadowRunId {record.shadowRunId ? <Text code>{workbenchSafeText(record.shadowRunId)}</Text> : t('pages:none')}</Text>
                <Text>paperRunId {record.paperRunId ? <Text code>{workbenchSafeText(record.paperRunId)}</Text> : t('pages:none')}</Text>
                <Text>consistencyReportId {record.consistencyReportId ? (
                    <Text code>{workbenchSafeText(record.consistencyReportId)}</Text>
                ) : t('pages:none')}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:summaryLimitations'); },
        key: 'summaries',
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.summary)}</Text>
                <Text type="secondary">{t('pages:limitations')}{safeTextListSummary(record.limitations)}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:blockersWarnings'); },
        key: 'signals',
        width: 190,
        render: (_, record) => (
            <Space size={6} wrap>
                <Tag color={record.blockers.length > 0 ? 'error' : 'default'}>
                    blockers {record.blockers.length}
                </Tag>
                <Tag color={record.warnings.length > 0 ? 'warning' : 'default'}>
                    warnings {record.warnings.length}
                </Tag>
            </Space>
        ),
    },
];

const evaluationArtifactPreviewIssueColumns: ColumnsType<EvaluationArtifactPreviewOverviewIssue> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 280,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:diagnosticPriority'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 180,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:source'); },
        key: 'source',
        width: 250,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">{t('pages:noSourceid')}</Text>
                )}
            </Space>
        ),
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const evaluationArtifactPreviewNextStepColumns: ColumnsType<EvaluationArtifactPreviewNextStep> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:owner'); },
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:action'); },
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:completionCriteria'); },
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:boundaryCritical'); },
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
    },
];

const evaluationArtifactPreviewEvidenceAnchorColumns: ColumnsType<EvaluationArtifactPreviewEvidenceAnchor> = [
    {
        title: 'sourceType',
        dataIndex: 'sourceType',
        key: 'sourceType',
        width: 210,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'sourceId',
        dataIndex: 'sourceId',
        key: 'sourceId',
        width: 230,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceVersion',
        dataIndex: 'sourceVersion',
        key: 'sourceVersion',
        width: 170,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceTimestamp',
        dataIndex: 'sourceTimestamp',
        key: 'sourceTimestamp',
        width: 190,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'traceId',
        dataIndex: 'traceId',
        key: 'traceId',
        width: 240,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'description',
        dataIndex: 'description',
        key: 'description',
        render: (value: string | null) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const evaluationArtifactPreviewBucketColumns: ColumnsType<EvaluationArtifactPreviewBucketRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 230,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:bucket'); },
        dataIndex: 'bucket',
        key: 'bucket',
        width: 260,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'count',
        dataIndex: 'count',
        key: 'count',
        width: 120,
        render: (value: number) => <Text>{value}</Text>,
    },
];

const evaluationArtifactPreviewItemColumns: ColumnsType<PythonEvaluationArtifactPreviewItem> = [
    {
        title: 'artifactPreviewId',
        dataIndex: 'artifactPreviewId',
        key: 'artifactPreviewId',
        width: 280,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'checksumStatus',
        dataIndex: 'checksumStatus',
        key: 'checksumStatus',
        width: 240,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'artifactFreshness',
        dataIndex: 'artifactFreshness',
        key: 'artifactFreshness',
        width: 220,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: 'metricSummaryStatus',
        dataIndex: 'metricSummaryStatus',
        key: 'metricSummaryStatus',
        width: 270,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        get title() { return t('pages:schemaSource'); },
        key: 'schemaSource',
        width: 320,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>schemaVersion {record.schemaVersion ? <Text code>{workbenchSafeText(record.schemaVersion)}</Text> : t('pages:none')}</Text>
                <Text>source {record.source ? <Text code>{workbenchSafeText(record.source)}</Text> : t('pages:none')}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:relatedId'); },
        key: 'ids',
        width: 360,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>artifactId {record.artifactId ? <Text code>{workbenchSafeText(record.artifactId)}</Text> : t('pages:none')}</Text>
                <Text>strategyVersionId {record.strategyVersionId ? (
                    <Text code>{workbenchSafeText(record.strategyVersionId)}</Text>
                ) : t('pages:none')}</Text>
                <Text>datasetId {record.datasetId ? <Text code>{workbenchSafeText(record.datasetId)}</Text> : t('pages:none')}</Text>
                <Text>parameterSetId {record.parameterSetId ? (
                    <Text code>{workbenchSafeText(record.parameterSetId)}</Text>
                ) : t('pages:none')}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:assumptions'); },
        key: 'assumptions',
        width: 300,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>cost {optionalText(record.costAssumptionsStatus)}</Text>
                <Text>slippage {optionalText(record.slippageAssumptionsStatus)}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:warningsLimitations'); },
        key: 'diagnostics',
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text type="secondary">{t('pages:warnings4')}{safeTextListSummary(record.validationWarnings)}</Text>
                <Text type="secondary">{t('pages:limitations')}{safeTextListSummary(record.limitations)}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:readinessFlags'); },
        key: 'readinessFlags',
        width: 260,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Tag color={record.liveExecutionReady ? 'error' : 'default'}>
                    liveExecutionReady={String(record.liveExecutionReady)}
                </Tag>
                <Tag color={record.pythonMlReady ? 'error' : 'default'}>
                    pythonMlReady={String(record.pythonMlReady)}
                </Tag>
                <Tag color={record.pythonLiveExecutionReady ? 'error' : 'default'}>
                    pythonLiveExecutionReady={String(record.pythonLiveExecutionReady)}
                </Tag>
            </Space>
        ),
    },
];

const incidentNextStepColumns: ColumnsType<IncidentReplayNextStep> = [
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:owner'); },
        dataIndex: 'owner',
        key: 'owner',
        width: 160,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:action'); },
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:completionCriteria'); },
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:boundaryCritical'); },
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
    },
];

const incidentEvidenceAnchorColumns: ColumnsType<IncidentReplayEvidenceAnchor> = [
    {
        title: 'sourceType',
        dataIndex: 'sourceType',
        key: 'sourceType',
        width: 190,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'sourceId',
        dataIndex: 'sourceId',
        key: 'sourceId',
        width: 230,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceVersion',
        dataIndex: 'sourceVersion',
        key: 'sourceVersion',
        width: 180,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceTimestamp',
        dataIndex: 'sourceTimestamp',
        key: 'sourceTimestamp',
        width: 210,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'checksum',
        dataIndex: 'checksum',
        key: 'checksum',
        width: 220,
        render: (value: string | null) => optionalSafeCode(value),
    },
];

const workbenchSignalColumns: ColumnsType<WorkbenchSignalRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 210,
    },
    {
        get title() { return t('pages:category'); },
        dataIndex: 'kind',
        key: 'kind',
        width: 120,
        render: (value: WorkbenchSignalRow['kind']) => (
            <Tag color={value === 'blocker' ? 'error' : 'warning'}>{value}</Tag>
        ),
    },
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        get title() { return t('pages:level'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 150,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const workbenchNextStepColumns: ColumnsType<WorkbenchNextStepRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 210,
    },
    {
        get title() { return t('pages:code'); },
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:owner'); },
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:action'); },
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:evidenceCompletionCriteria'); },
        dataIndex: 'evidence',
        key: 'evidence',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:blocked2'); },
        dataIndex: 'blocking',
        key: 'blocking',
        width: 110,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? t('pages:yes') : t('pages:no')}</Tag>,
    },
];

const workbenchEvidenceAnchorColumns: ColumnsType<WorkbenchEvidenceAnchorRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 210,
    },
    {
        title: 'sourceType',
        dataIndex: 'sourceType',
        key: 'sourceType',
        width: 180,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'sourceId',
        dataIndex: 'sourceId',
        key: 'sourceId',
        width: 230,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceVersion',
        dataIndex: 'sourceVersion',
        key: 'sourceVersion',
        width: 170,
        render: (value: string | null) => optionalSafeCode(value),
    },
    {
        title: 'sourceTimestamp',
        dataIndex: 'sourceTimestamp',
        key: 'sourceTimestamp',
        width: 190,
        render: (value: string | null) => generatedAtText(value),
    },
    {
        title: 'checksum',
        dataIndex: 'checksum',
        key: 'checksum',
        width: 210,
        render: (value: string | null) => optionalSafeCode(value),
    },
];

const validationOperationsSummaryColumns: ColumnsType<ValidationOperationsSummaryRow> = [
    {
        get title() { return t('pages:operationalTrack'); },
        dataIndex: 'lane',
        key: 'lane',
        width: 240,
        render: (value: string) => <Text strong>{value}</Text>,
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 240,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:keyMetrics2'); },
        dataIndex: 'primaryMetric',
        key: 'primaryMetric',
        width: 260,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'blockers',
        dataIndex: 'blockers',
        key: 'blockers',
        width: 110,
        render: (value: number) => <Tag color={value > 0 ? 'error' : 'default'}>{value}</Tag>,
    },
    {
        title: 'warnings',
        dataIndex: 'warnings',
        key: 'warnings',
        width: 110,
        render: (value: number) => <Tag color={value > 0 ? 'warning' : 'default'}>{value}</Tag>,
    },
    {
        title: 'nextStep',
        dataIndex: 'nextStep',
        key: 'nextStep',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'generatedAt',
        dataIndex: 'generatedAt',
        key: 'generatedAt',
        width: 210,
        render: (value: string | null) => generatedAtText(value),
    },
];

const validationOperationsEvidenceColumns: ColumnsType<ValidationOperationsEvidenceRow> = [
    {
        get title() { return t('pages:evidenceLane'); },
        dataIndex: 'lane',
        key: 'lane',
        width: 230,
        render: (value: string) => <Text strong>{value}</Text>,
    },
    {
        get title() { return t('pages:evidence'); },
        dataIndex: 'evidence',
        key: 'evidence',
        width: 230,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 230,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:quantity'); },
        dataIndex: 'count',
        key: 'count',
        width: 180,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        get title() { return t('pages:explanation2'); },
        dataIndex: 'detail',
        key: 'detail',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const validationOperationsOperatorQueueColumns: ColumnsType<ValidationOperationsOperatorQueueRow> = [
    {
        get title() { return t('pages:source'); },
        dataIndex: 'source',
        key: 'source',
        width: 210,
    },
    {
        title: 'itemId',
        dataIndex: 'itemId',
        key: 'itemId',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'state',
        dataIndex: 'state',
        key: 'state',
        width: 240,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: 'severity',
        dataIndex: 'severity',
        key: 'severity',
        width: 150,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: 'freshness',
        dataIndex: 'freshness',
        key: 'freshness',
        width: 170,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        get title() { return t('pages:decisionRecommendation'); },
        dataIndex: 'decision',
        key: 'decision',
        width: 280,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: 'traceId',
        dataIndex: 'traceId',
        key: 'traceId',
        width: 260,
        render: (value: string) => optionalSafeCode(value),
    },
];

function normalizeStatus(status: string | null | undefined): string {
    const normalized = status?.trim().toUpperCase();
    return normalized || 'UNKNOWN';
}

/**
 * 状态展示必须 fail-closed。
 *
 * Why:
 * GateQ 的 READY_FOR_* 仅表示评审或只读预览阶段可继续，不是交易授权；UNKNOWN / NOT_AVAILABLE /
 * NOT_IMPLEMENTED / BLOCKED_* 也不能用绿色成功态展示。
 */
function statusPresentation(status: string | null | undefined): StatusPresentation {
    const normalized = normalizeStatus(status);
    const direct = STATUS_PRESENTATION[normalized];
    if (direct) {
        return direct;
    }
    if (normalized.startsWith('BLOCKED') || normalized.startsWith('PREVIEW_BLOCKED') || normalized.includes('FAILED')) {
        return {label: normalized, tone: 'danger'};
    }
    if (normalized.includes('WARNING') || normalized.includes('MISSING') || normalized.includes('INCOMPLETE')) {
        return {label: normalized, tone: 'warning'};
    }
    if (normalized.includes('PENDING') || normalized === 'NOT_CONNECTED') {
        return {label: normalized, tone: 'warning'};
    }
    if (normalized.startsWith('READY_FOR')) {
        return {label: normalized, tone: 'info'};
    }
    return {label: normalized, tone: 'neutral'};
}

function isProblemStatus(status: string | null | undefined): boolean {
    const normalized = normalizeStatus(status);
    return normalized === 'UNKNOWN'
        || normalized === 'NOT_AVAILABLE'
        || normalized === 'NOT_IMPLEMENTED'
        || normalized === 'NOT_CONNECTED'
        || normalized.includes('PENDING')
        || normalized.startsWith('BLOCKED')
        || normalized.startsWith('PREVIEW_BLOCKED')
        || normalized.includes('FAILED')
        || normalized.includes('ERROR');
}

function statusText(status: string | null | undefined): string {
    const normalized = normalizeStatus(status);
    const presentation = statusPresentation(normalized);
    return presentation.label === normalized ? normalized : `${normalized}（${presentation.label}）`;
}

function optionalCode(value: string | null | undefined): ReactNode {
    const normalized = value?.trim();
    return normalized ? <Text code>{normalized}</Text> : <StatusTag status="NOT_AVAILABLE"/>;
}

function workbenchSafeText(value: string | null | undefined): string {
    const normalized = value?.trim();
    if (!normalized) {
        return t('pages:none');
    }
    if (WORKBENCH_SENSITIVE_TEXT_PATTERN.test(normalized) || WORKBENCH_FORBIDDEN_ACTION_TEXT_PATTERN.test(normalized)) {
        return '[filtered diagnostic text]';
    }
    return normalized;
}

function optionalSafeCode(value: string | null | undefined): ReactNode {
    const normalized = value?.trim();
    return normalized ? <Text code>{workbenchSafeText(normalized)}</Text> : <StatusTag status="NOT_AVAILABLE"/>;
}

function optionalText(value: string | null | undefined): ReactNode {
    const normalized = value?.trim();
    return normalized ? <Text>{normalized}</Text> : <StatusTag status="NOT_AVAILABLE"/>;
}

function generatedAtText(value: string | null | undefined): ReactNode {
    return value ? formatDateTime(value) : <StatusTag status="NOT_AVAILABLE"/>;
}

function readModelFreshnessState(metadata: ReadModelEvidenceMetadata | null | undefined): FreshnessState {
    const availability = normalizeStatus(metadata?.availability);
    const freshness = normalizeStatus(metadata?.freshnessStatus);
    if (availability === 'UNAVAILABLE') {
        return 'error';
    }
    if (availability === 'PARTIAL') {
        return 'degraded';
    }
    if (freshness === 'FRESH') {
        return 'fresh';
    }
    if (freshness === 'STALE') {
        return 'stale';
    }
    return 'no_data';
}

function ReadModelEvidenceMetadataSummary({metadata, testId}: {
    metadata?: ReadModelEvidenceMetadata | null;
    testId?: string;
}) {
    useTranslation('pages');
    const source = metadata?.source?.trim() || 'UNKNOWN_SOURCE';
    const availability = normalizeStatus(metadata?.availability) || 'UNKNOWN';
    const freshness = normalizeStatus(metadata?.freshnessStatus) || 'UNKNOWN';
    const availabilityColor = availability === 'AVAILABLE' && freshness === 'FRESH'
        ? 'success'
        : availability === 'PARTIAL' || freshness === 'STALE'
            ? 'warning'
            : availability === 'UNAVAILABLE' ? 'error' : 'default';
    const freshnessText = freshness === 'FRESH'
        ? t('pages:fresh')
        : freshness === 'STALE' ? t('pages:stale') : t('pages:freshnessUnknown');

    return (
        <Space data-testid={testId ?? 'read-model-evidence-metadata'} direction="vertical" size={6}
               style={{display: 'flex'}}>
            <DataFreshness
                source={t('pages:dataSourceValue1', {value1: source})}
                state={readModelFreshnessState(metadata)}
                detail={metadata?.ageSeconds == null ? freshnessText : `${freshnessText}；age ${metadata.ageSeconds}s`}
            />
            <Space size={[8, 6]} wrap>
                <Tag color={availabilityColor}>{t('pages:availability')}{availability}</Tag>
                <Text>{t('pages:freshness')}{freshness}（{freshnessText}）</Text>
                <Text>{t('pages:lastComputed')}{metadata?.lastCalculatedAt ? formatDateTime(metadata.lastCalculatedAt) : t('pages:notProvided')}</Text>
            </Space>
        </Space>
    );
}

/**
 * 运行证据总览只展示后端聚合后的 metadata；各来源详细业务语义仍保留在下方既有 panel。
 */
function ValidationOperationsRuntimeEvidenceOverviewPanel({query}: {
    query: PanelQueryState<ValidationOperationsRuntimeEvidenceOverviewResponse>
}) {
    useTranslation('pages');
    const overview = query.data;
    return (
        <Card
            className="page-section"
            data-testid="validation-operations-runtime-evidence-card"
            variant="borderless"
            title={t('pages:runtimeEvidenceOverview')}
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching} onClick={() => query.refetch()}>
                    {t('pages:refreshOverview2')}</Button>
            )}
        >
            <Space data-testid="validation-operations-runtime-evidence-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyAggregateGetOverFiveExistingEvidenceMetadataSourcesToAssessCompletenessItDoesNotReplaceSourc')}</Paragraph>
                {query.isLoading ? <Skeleton active paragraph={{rows: 5}}/> : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToQueryRuntimeEvidenceOverview')}
                        description={t('pages:aFailedAggregateGetIsUnavailableNotHealthyExecutableOrAuthorizedForTradingValue1', {value1: formatApiError(query.error as AppApiError)})}
                    />
                ) : !overview ? (
                    <Empty description={t('pages:noRuntimeEvidenceOverviewFailClosedHandlingApplies')}/>
                ) : (
                    <>
                        <ReadModelEvidenceMetadataSummary
                            metadata={overview.evidenceMetadata}
                            testId="validation-operations-runtime-evidence-metadata"
                        />
                        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
                            <Descriptions.Item label={t('pages:evidenceSourceCount')}>{overview.sourceCount}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:availableIncompleteUnavailableUnknown')}>
                                {`${overview.availableCount} / ${overview.partialCount} / ${overview.unavailableCount} / ${overview.unknownAvailabilityCount}`}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:freshStaleUnknown')}>
                                {`${overview.freshCount} / ${overview.staleCount} / ${overview.unknownFreshnessCount}`}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:latestFactTime')}>
                                {overview.evidenceMetadata.lastCalculatedAt
                                    ? formatDateTime(overview.evidenceMetadata.lastCalculatedAt)
                                    : t('pages:noAuthoritativeFactTime')}
                            </Descriptions.Item>
                        </Descriptions>
                        <div>
                            <Text strong>{t('pages:evidenceSources')}</Text>
                            <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
                                {overview.sources.map((source: ValidationOperationsRuntimeEvidenceSource) => (
                                    <Descriptions.Item key={source.sourceKey} label={source.displayName}>
                                        <Space size={[8, 6]} wrap>
                                            <Text code>{source.sourceKey}</Text>
                                            <Text>{t('pages:availability')}{source.evidenceMetadata.availability}</Text>
                                            <Text>{t('pages:freshness')}{source.evidenceMetadata.freshnessStatus}</Text>
                                        </Space>
                                    </Descriptions.Item>
                                ))}
                            </Descriptions>
                        </div>
                        <Alert
                            type="info"
                            showIcon
                            message={t('pages:forDiagnosticsOnly')}
                            description={t('pages:evenIfAllFiveSourcesAreAvailableOnlyDiagnosticEvidenceIsAvailableNoTradingAuthorizationIsGrantedAndL')}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function normalizeQuery(values: StrategyValidationQuery): StrategyValidationQuery {
    return QUERY_FIELDS.reduce<StrategyValidationQuery>((query, field) => {
        const normalized = values[field]?.trim();
        if (normalized) {
            query[field] = normalized;
        }
        return query;
    }, {});
}

export function hasQueryValue(query: StrategyValidationQuery): boolean {
    return QUERY_FIELDS.some((field) => Boolean(query[field]?.trim()));
}

export function queryFromSearchParams(searchParams: URLSearchParams): StrategyValidationQuery {
    return normalizeQuery(QUERY_FIELDS.reduce<StrategyValidationQuery>((query, field) => {
        const value = searchParams.get(field);
        if (value) {
            query[field] = value;
        }
        return query;
    }, {}));
}

function firstText(...values: Array<string | null | undefined>): string | null {
    const matched = values.find((value) => Boolean(value?.trim()));
    return matched?.trim() ?? null;
}

function numberValue(value: number | null | undefined): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function jsonSummary(value: JsonValue | null | undefined): string {
    if (value === null || value === undefined) {
        return t('pages:none');
    }
    if (Array.isArray(value)) {
        return value.length === 0 ? t('pages:emptyArray') : t('pages:arrayWithValue1Items', {value1: value.length});
    }
    if (typeof value === 'object') {
        return t('pages:objectWithValue1Fields', {value1: Object.keys(value).length});
    }
    if (typeof value === 'string') {
        return value.trim() ? workbenchSafeText(value) : t('pages:emptyText');
    }
    return String(value);
}

function safeTextListSummary(items: string[] | null | undefined): string {
    const values = (items ?? [])
        .filter((item) => Boolean(item?.trim()) && item.trim() !== '无')
        .map((item) => workbenchSafeText(item));
    if (values.length === 0) {
        return t('pages:none');
    }
    return values.slice(0, 3).join('；');
}

function workbenchSignalRows(
    strategyOverview?: StrategyValidationOverviewResponse,
    shadowOverview?: ShadowRunOverviewResponse,
    drilldown?: PaperShadowConsistencyDrilldownResponse,
): WorkbenchSignalRow[] {
    const rows: WorkbenchSignalRow[] = [];
    const pushIssues = (
        source: string,
        kind: WorkbenchSignalRow['kind'],
        issues: Array<{ code: string; severity: string; message: string }>,
    ) => {
        issues.slice(0, 4).forEach((issue, index) => {
            rows.push({
                key: `${source}-${kind}-${index}`,
                source,
                kind,
                code: workbenchSafeText(issue.code),
                severity: issue.severity,
                message: workbenchSafeText(issue.message),
            });
        });
    };

    pushIssues(t('pages:strategyValidation'), 'blocker', strategyOverview?.blockers ?? []);
    pushIssues(t('pages:strategyValidation'), 'warning', strategyOverview?.warnings ?? []);
    pushIssues(t('pages:shadowRunOverview'), 'blocker', shadowOverview?.blockers ?? []);
    pushIssues(t('pages:shadowRunOverview'), 'warning', shadowOverview?.warnings ?? []);
    pushIssues(t('pages:paperShadowDrilldown'), 'blocker', drilldown?.blockers ?? []);
    pushIssues(t('pages:paperShadowDrilldown'), 'warning', drilldown?.warnings ?? []);
    return rows;
}

function workbenchNextStepRows(
    strategyOverview?: StrategyValidationOverviewResponse,
    shadowOverview?: ShadowRunOverviewResponse,
    drilldown?: PaperShadowConsistencyDrilldownResponse,
): WorkbenchNextStepRow[] {
    return [
        ...(strategyOverview?.nextSteps ?? []).slice(0, 4).map((item, index) => ({
            key: `strategy-${index}`,
            source: t('pages:strategyValidation'),
            code: workbenchSafeText(item.code),
            owner: workbenchSafeText(item.owner),
            action: workbenchSafeText(item.action),
            evidence: workbenchSafeText(item.completionCondition),
            blocking: item.boundaryCritical,
        })),
        ...(shadowOverview?.nextSteps ?? []).slice(0, 4).map((item, index) => ({
            key: `shadow-overview-${index}`,
            source: t('pages:shadowRunOverview'),
            code: workbenchSafeText(item.code),
            owner: workbenchSafeText(item.owner),
            action: workbenchSafeText(item.action),
            evidence: workbenchSafeText(item.expectedEvidence),
            blocking: item.blocking,
        })),
        ...(drilldown?.nextSteps ?? []).slice(0, 4).map((item, index) => ({
            key: `drilldown-${index}`,
            source: t('pages:paperShadowDrilldown'),
            code: workbenchSafeText(item.code),
            owner: workbenchSafeText(item.owner),
            action: workbenchSafeText(item.action),
            evidence: workbenchSafeText(item.expectedEvidence),
            blocking: item.blocking,
        })),
    ];
}

function workbenchEvidenceRows(
    strategyOverview?: StrategyValidationOverviewResponse,
    shadowOverview?: ShadowRunOverviewResponse,
    drilldown?: PaperShadowConsistencyDrilldownResponse,
): WorkbenchEvidenceAnchorRow[] {
    const toRows = (
        source: string,
        anchors: Array<{
            sourceType: string;
            sourceId: string | null;
            sourceVersion: string | null;
            sourceTimestamp: string | null;
            checksum: string | null
        }>,
    ): WorkbenchEvidenceAnchorRow[] => anchors.slice(0, 4).map((anchor, index) => ({
        key: `${source}-${index}`,
        source,
        sourceType: workbenchSafeText(anchor.sourceType),
        sourceId: anchor.sourceId ? workbenchSafeText(anchor.sourceId) : null,
        sourceVersion: anchor.sourceVersion ? workbenchSafeText(anchor.sourceVersion) : null,
        sourceTimestamp: anchor.sourceTimestamp,
        checksum: anchor.checksum ? workbenchSafeText(anchor.checksum) : null,
    }));

    return [
        ...toRows(t('pages:strategyValidation'), strategyOverview?.evidenceAnchors ?? []),
        ...toRows(t('pages:shadowRunOverview'), shadowOverview?.evidenceAnchors ?? []),
        ...toRows(t('pages:paperShadowDrilldown'), drilldown?.evidenceAnchors ?? []),
    ];
}

function firstScope(
    query: StrategyValidationQuery | null,
    gate?: StrategyEvaluationGateResponse,
    comparison?: PaperShadowComparisonResponse,
    preview?: ShadowLivePreviewResponse,
): StrategyValidationScope {
    return {
        strategyId: firstText(preview?.scope?.strategyId, comparison?.scope?.strategyId, gate?.scope?.strategyId, query?.strategyId),
        strategyVersionId: firstText(
            preview?.scope?.strategyVersionId,
            comparison?.scope?.strategyVersionId,
            gate?.scope?.strategyVersionId,
            query?.strategyVersionId,
        ),
        datasetId: firstText(preview?.scope?.datasetId, comparison?.scope?.datasetId, gate?.scope?.datasetId, query?.datasetId),
        evaluationId: firstText(preview?.scope?.evaluationId, comparison?.scope?.evaluationId, gate?.scope?.evaluationId, query?.evaluationId),
        publishId: firstText(preview?.scope?.publishId, comparison?.scope?.publishId, gate?.scope?.publishId, query?.publishId),
        paperRunId: firstText(preview?.scope?.paperRunId, comparison?.scope?.paperRunId, gate?.scope?.paperRunId, query?.paperRunId),
        shadowRunId: firstText(preview?.scope?.shadowRunId, comparison?.scope?.shadowRunId, query?.shadowRunId),
    };
}

/**
 * Evidence Matrix 聚合三个只读 GET 响应。
 *
 * Why:
 * GateQ-6 需要横向查看 requiredEvidence / missingEvidence / blockers / warnings / nextSteps；
 * 聚合只发生在前端内存中，不发起写侧请求，也不补造后端没有返回的通过态。
 */
function evidenceMatrixRows(source: string, data?: EvidenceSourceData): EvidenceMatrixRow[] {
    if (!data) {
        return [];
    }

    const rows: EvidenceMatrixRow[] = [];
    data.requiredEvidence?.forEach((item, index) => {
        rows.push({
            key: `${source}-required-${index}-${item.code}`,
            source,
            category: 'requiredEvidence',
            code: item.code,
            status: item.status,
            message: item.message,
        });
    });
    data.missingEvidence?.forEach((item, index) => {
        rows.push({
            key: `${source}-missing-${index}-${item.code}`,
            source,
            category: 'missingEvidence',
            code: item.code,
            status: item.status,
            message: item.message,
        });
    });
    data.blockers?.forEach((item, index) => {
        rows.push({
            key: `${source}-blocker-${index}-${item.code}`,
            source,
            category: 'blockers',
            code: item.code,
            status: item.severity,
            message: item.message,
        });
    });
    data.warnings?.forEach((item, index) => {
        rows.push({
            key: `${source}-warning-${index}-${item.code}`,
            source,
            category: 'warnings',
            code: item.code,
            status: item.severity,
            message: item.message,
        });
    });
    data.nextSteps?.forEach((item, index) => {
        rows.push({
            key: `${source}-next-${index}-${item}`,
            source,
            category: 'nextSteps',
            code: `NEXT_STEP_${index + 1}`,
            status: 'ACTION_REQUIRED',
            message: item,
        });
    });
    return rows;
}

function evaluationArtifactPreviewMatrixRows(
    overview: PythonEvaluationArtifactPreviewOverviewResponse | undefined,
): EvidenceMatrixRow[] {
    if (!overview) {
        return [{
            key: 'Python Artifact Binding Preview-overview-unknown',
            source: t('pages:pythonArtifactBindingPreview'),
            category: 'missingEvidence',
            code: 'EVALUATION_ARTIFACT_PREVIEW_OVERVIEW',
            status: 'UNKNOWN',
            message: t('pages:theArtifactPreviewOverviewIsUnavailableThePageRemainsFailClosedAndDoesNotFabricateAnArtifactSource'),
        }];
    }

    const rows: EvidenceMatrixRow[] = [];
    overview.blockers.forEach((blocker) => {
        rows.push({
            key: `Python Artifact Binding Preview-blocker-${blocker.code}-${blocker.sourceId ?? 'none'}`,
            source: t('pages:pythonArtifactBindingPreview'),
            category: 'blockers',
            code: workbenchSafeText(blocker.code),
            status: workbenchSafeText(blocker.severity),
            message: workbenchSafeText(blocker.message),
        });
    });
    overview.warnings.forEach((warning) => {
        rows.push({
            key: `Python Artifact Binding Preview-warning-${warning.code}-${warning.sourceId ?? 'none'}`,
            source: t('pages:pythonArtifactBindingPreview'),
            category: 'warnings',
            code: workbenchSafeText(warning.code),
            status: workbenchSafeText(warning.severity),
            message: workbenchSafeText(warning.message),
        });
    });
    if (evaluationArtifactPreviewIsNoFileBaseline(overview)) {
        rows.push({
            key: 'Python Artifact Binding Preview-no-file-baseline',
            source: t('pages:pythonArtifactBindingPreview'),
            category: 'missingEvidence',
            code: 'NO_ARTIFACT_SOURCE_CONFIGURED',
            status: 'NO_FILE_BASELINE',
            message: t('pages:noArtifactSourceIsConfiguredNoArtifactFilesAreReadPythonIsNotExecutedAndNoDatabaseImportOccurs'),
        });
    }
    overview.nextSteps.forEach((step) => {
        rows.push({
            key: `Python Artifact Binding Preview-next-${step.code}`,
            source: t('pages:pythonArtifactBindingPreview'),
            category: 'nextSteps',
            code: workbenchSafeText(step.code),
            status: 'ACTION_REQUIRED',
            message: workbenchSafeText(step.action),
        });
    });
    return rows;
}

function firstNextStepCode(
    items: Array<{ code: string }> | null | undefined,
    emptyCode = 'NO_NEXT_STEP_RETURNED',
): string {
    return workbenchSafeText(items?.[0]?.code ?? emptyCode);
}

function validationOperationsSummaryRows(
    strategyOverview?: StrategyValidationOverviewResponse,
    shadowWorkflow?: ShadowValidationWorkflowOverviewResponse,
    consistencyEvidence?: ConsistencyEvidenceOverviewResponse,
    incidentReplayReview?: IncidentReplayReviewOverviewResponse,
    artifactPreview?: PythonEvaluationArtifactPreviewOverviewResponse,
): ValidationOperationsSummaryRow[] {
    return [
        {
            key: 'strategy-validation',
            lane: t('pages:strategyValidation'),
            status: strategyOverview ? decisionOf(strategyOverview) : 'UNKNOWN',
            primaryMetric: `versions ${numberValue(strategyOverview?.evaluatedStrategyVersions)}/${numberValue(strategyOverview?.totalStrategyVersions)} · needsReview ${numberValue(strategyOverview?.needsReview)}`,
            blockers: strategyOverview?.blockers.length ?? 0,
            warnings: strategyOverview?.warnings.length ?? 0,
            nextStep: firstNextStepCode(strategyOverview?.nextSteps),
            generatedAt: strategyOverview?.generatedAt ?? null,
        },
        {
            key: 'shadow-validation-workflow',
            lane: t('pages:shadowValidationWorkflow'),
            status: shadowWorkflow?.latestOperatorItem?.workflowState ?? (shadowWorkflow ? 'NO_OPERATOR_ITEMS' : 'UNKNOWN'),
            primaryMetric: `operatorItems ${numberValue(shadowWorkflow?.totalOperatorItems)} · readyForOperatorReview ${numberValue(shadowWorkflow?.readyForOperatorReviewCount)}`,
            blockers: shadowWorkflow?.blockers.length ?? 0,
            warnings: shadowWorkflow?.warnings.length ?? 0,
            nextStep: firstNextStepCode(shadowWorkflow?.nextSteps),
            generatedAt: shadowWorkflow?.generatedAt ?? null,
        },
        {
            key: 'consistency-evidence',
            lane: t('pages:consistencyEvidence'),
            status: consistencyEvidence?.latestEvidenceItem?.comparisonStatus ?? (consistencyEvidence ? 'NO_EVIDENCE' : 'UNKNOWN'),
            primaryMetric: `evidenceItems ${numberValue(consistencyEvidence?.totalEvidenceItems)} · diverged ${numberValue(consistencyEvidence?.divergedCount)} · stale ${numberValue(consistencyEvidence?.staleEvidenceCount)}`,
            blockers: consistencyEvidence?.blockers.length ?? 0,
            warnings: consistencyEvidence?.warnings.length ?? 0,
            nextStep: firstNextStepCode(consistencyEvidence?.nextSteps),
            generatedAt: consistencyEvidence?.generatedAt ?? null,
        },
        {
            key: 'incident-replay-review',
            lane: t('pages:incidentReplayReview'),
            status: incidentReplayReview?.latestReviewItem?.reviewState ?? (incidentReplayReview ? 'NO_REVIEW_ITEMS' : 'UNKNOWN'),
            primaryMetric: `reviewItems ${numberValue(incidentReplayReview?.totalReviewItems)} · acknowledged ${numberValue(incidentReplayReview?.acknowledgedRecommendationCount)} · escalated ${numberValue(incidentReplayReview?.escalatedRecommendationCount)}`,
            blockers: incidentReplayReview?.blockers.length ?? 0,
            warnings: incidentReplayReview?.warnings.length ?? 0,
            nextStep: firstNextStepCode(incidentReplayReview?.nextSteps),
            generatedAt: incidentReplayReview?.generatedAt ?? null,
        },
        {
            key: 'evaluation-artifact-preview',
            lane: t('pages:evaluationArtifactPreview'),
            status: artifactPreview
                ? evaluationArtifactPreviewIsNoFileBaseline(artifactPreview)
                    ? 'NO_ARTIFACT_SOURCE_CONFIGURED'
                    : artifactPreview.latestArtifactPreview?.checksumStatus ?? 'DIAGNOSTIC_ONLY'
                : 'UNKNOWN',
            primaryMetric: `artifactPreviews ${numberValue(artifactPreview?.totalArtifactPreviews)} · valid ${numberValue(artifactPreview?.validArtifactCount)} · checksumFailed ${numberValue(artifactPreview?.checksumFailedCount)}`,
            blockers: artifactPreview?.blockers.length ?? 0,
            warnings: artifactPreview?.warnings.length ?? 0,
            nextStep: firstNextStepCode(artifactPreview?.nextSteps),
            generatedAt: artifactPreview?.generatedAt ?? null,
        },
    ];
}

function validationOperationsEvidenceRows(
    strategyOverview?: StrategyValidationOverviewResponse,
    shadowWorkflow?: ShadowValidationWorkflowOverviewResponse,
    consistencyEvidence?: ConsistencyEvidenceOverviewResponse,
    incidentReplayReview?: IncidentReplayReviewOverviewResponse,
    artifactPreview?: PythonEvaluationArtifactPreviewOverviewResponse,
): ValidationOperationsEvidenceRow[] {
    return [
        {
            key: 'strategy-validation-evidence',
            lane: t('pages:strategyValidation'),
            evidence: 'latestDecision / evidenceAnchors / blockers',
            status: strategyOverview ? decisionOf(strategyOverview) : 'UNKNOWN',
            count: `anchors ${numberValue(strategyOverview?.evidenceAnchors.length)} · blockers ${numberValue(strategyOverview?.blockers.length)}`,
            detail: t('pages:validationEvidenceIsForManualReviewOnlyNotTradingAuthorization'),
        },
        {
            key: 'shadow-validation-evidence',
            lane: t('pages:shadowValidation'),
            evidence: 'operatorItems / workflowState / evidenceFreshness',
            status: shadowWorkflow?.latestOperatorItem?.validationDecision ?? (shadowWorkflow ? 'NO_DECISION' : 'UNKNOWN'),
            count: `operatorItems ${numberValue(shadowWorkflow?.totalOperatorItems)} · needsEvidence ${numberValue(shadowWorkflow?.needsEvidenceCount)}`,
            detail: t('pages:anOperatorItemIsADerivedDiagnosticRowNotAnApproveRejectOrExecuteWriteTask'),
        },
        {
            key: 'consistency-evidence',
            lane: t('pages:consistencyEvidence'),
            evidence: 'latestEvidenceItem / metricDeltaSummary / freshnessSummary',
            status: consistencyEvidence?.latestEvidenceItem?.comparisonStatus ?? (consistencyEvidence ? 'NO_REPORT' : 'UNKNOWN'),
            count: `consistent ${numberValue(consistencyEvidence?.consistentCount)} · diverged ${numberValue(consistencyEvidence?.divergedCount)}`,
            detail: t('pages:consistentMeansLocalEvidenceAgreesNotThatTradingIsPermitted'),
        },
        {
            key: 'incident-replay-review-evidence',
            lane: t('pages:incidentReplayReview'),
            evidence: 'reviewItems / recommendation / replay anchors',
            status: incidentReplayReview?.latestReviewItem?.reviewDecision ?? (incidentReplayReview ? 'NO_DECISION' : 'UNKNOWN'),
            count: `reviewItems ${numberValue(incidentReplayReview?.totalReviewItems)} · blocked ${numberValue(incidentReplayReview?.blockedCount)}`,
            detail: t('pages:acknowledgeRecommendedEscalateRecommendedRecommendManualReviewOnly'),
        },
        {
            key: 'python-artifact-preview-evidence',
            lane: t('pages:pythonArtifactPreview'),
            evidence: 'no-file baseline / checksum / schema coverage',
            status: artifactPreview
                ? evaluationArtifactPreviewIsNoFileBaseline(artifactPreview)
                    ? 'NO_ARTIFACT_SOURCE_CONFIGURED'
                    : artifactPreview.latestArtifactPreview?.checksumStatus ?? 'NOT_CHECKED'
                : 'UNKNOWN',
            count: `previews ${numberValue(artifactPreview?.totalArtifactPreviews)} · stale ${numberValue(artifactPreview?.staleArtifactCount)}`,
            detail: t('pages:aValidChecksumDoesNotEstablishStrategyValidityPythonArtifactPreviewDoesNotEstablishMlOrLiveExecution'),
        },
    ];
}

function validationOperationsOperatorQueueRows(
    shadowWorkflow?: ShadowValidationWorkflowOverviewResponse,
    incidentReplayReview?: IncidentReplayReviewOverviewResponse,
): ValidationOperationsOperatorQueueRow[] {
    return [
        ...(shadowWorkflow?.operatorItems ?? []).slice(0, 5).map((item) => ({
            key: `operator-${item.operatorItemId}`,
            source: t('pages:derivedOperatorItem'),
            itemId: item.operatorItemId,
            state: item.workflowState,
            severity: item.severity,
            freshness: item.evidenceFreshness,
            decision: item.validationDecision,
            traceId: item.traceId,
        })),
        ...(incidentReplayReview?.reviewItems ?? []).slice(0, 5).map((item) => ({
            key: `review-${item.reviewItemId}`,
            source: t('pages:reviewItem'),
            itemId: item.reviewItemId,
            state: item.reviewState,
            severity: item.severity,
            freshness: item.evidenceFreshness,
            decision: item.reviewDecision,
            traceId: item.traceId,
        })),
    ];
}

function StatusTag({status}: { status?: string | null }) {
    useTranslation('pages');
    const presentation = statusPresentation(status);
    return (
        <CanonicalStatusTag
            status={normalizeStatus(status)}
            label={statusText(status)}
            tone={presentation.tone}
            variant="pill"
        />
    );
}

function workflowStatusPresentation(status: string | null | undefined): {
    label: string;
    color: string;
    tooltip: string
} {
    const normalized = normalizeStatus(status);
    switch (normalized) {
        case 'INTAKE':
            return {
                label: t('pages:enteredIntake'),
                color: 'default',
                tooltip: t('pages:intakeMeansTheItemEnteredADerivedReadOnlyQueueNoReviewConclusionExistsYet'),
            };
        case 'EVIDENCE_REVIEW':
            return {
                label: t('pages:evidenceUnderReview'),
                color: 'warning',
                tooltip: t('pages:evidenceReviewRequiresFurtherEvidenceInspectionItIsNotAValidationPass'),
            };
        case 'NEEDS_OPERATOR_REVIEW':
            return {
                label: t('pages:manualReviewRequired'),
                color: 'warning',
                tooltip: t('pages:needsOperatorReviewRequiresManualDiagnosticReviewTheSystemHasNotHandledTheIssue'),
            };
        case 'NEEDS_EVIDENCE':
            return {
                label: t('pages:evidenceRequired'),
                color: 'warning',
                tooltip: t('pages:needsEvidenceMeansEvidenceIsMissingOrInsufficientAndMustRemainFailClosed'),
            };
        case 'READY_FOR_OPERATOR_REVIEW':
            return {
                label: t('pages:readyForManualReviewNotTradingAuthorization'),
                color: 'processing',
                tooltip: t('pages:readyForOperatorReviewMeansEvidenceCanBeManuallyReviewedNotTradedOn'),
            };
        case 'BLOCKED':
            return {
                label: t('pages:blocked2'),
                color: 'error',
                tooltip: t('pages:blockedMeansDiagnosticsAreBlockedNoAutomaticHandlingOrTradingActionsArePermitted'),
            };
        case 'CLOSED_RECOMMENDATION':
            return {
                label: t('pages:diagnosticRecommendationFormedNoAutomaticHandling'),
                color: 'processing',
                tooltip: t('pages:closedRecommendationMeansARecommendationExistsNotCompletedHandlingOrTradingClearance'),
            };
        case 'ACKNOWLEDGED_RECOMMENDATION':
            return {
                label: t('pages:manualAcknowledgmentOfDiagnosticFactsRecommended'),
                color: 'processing',
                tooltip: t('pages:acknowledgedRecommendationRecordsAnAcknowledgmentRecommendationNotAutomaticAcknowledgmentOrHandling'),
            };
        case 'ESCALATED_RECOMMENDATION':
            return {
                label: t('pages:manualEscalationRecommended'),
                color: 'warning',
                tooltip: t('pages:escalatedRecommendationRecommendsManualEscalationTheSystemHasNotEscalatedAnything'),
            };
        case 'VALIDATION_READY':
            return {
                label: t('pages:validationEvidenceReviewableNotTradingAuthorization'),
                color: 'processing',
                tooltip: t('pages:validationReadyMeansEvidenceIsReadyForManualReviewNotStrategyApprovalOrTradingAuthorization'),
            };
        case 'REVIEW_NEEDED':
            return {
                label: t('pages:reviewRequired'),
                color: 'warning',
                tooltip: t('pages:reviewNeededRequiresManualReviewNotAutomaticHandlingOrTradingAuthorization'),
            };
        case 'NEEDS_REVIEW':
            return {
                label: t('pages:manualInspectionRequired'),
                color: 'warning',
                tooltip: t('pages:needsReviewRequiresManualInspectionOfEvidenceBlockersWarningsAndNextsteps'),
            };
        case 'ACKNOWLEDGE_RECOMMENDED':
            return {
                label: t('pages:manualAcknowledgmentRecommendedNotAutomaticHandling'),
                color: 'processing',
                tooltip: t('pages:acknowledgeRecommendedRecommendsManualAcknowledgmentOfDiagnosticFactsNoSystemHandlingIsImplied'),
            };
        case 'ESCALATE_RECOMMENDED':
            return {
                label: t('pages:manualEscalationRecommendedNotSystemEscalation'),
                color: 'warning',
                tooltip: t('pages:escalateRecommendedRecommendsManualEscalationTheSystemHasNotEscalatedAnything'),
            };
        case 'CLOSEOUT_RECOMMENDED':
            return {
                label: t('pages:diagnosticCloseoutRecommendedNotActualClosure'),
                color: 'processing',
                tooltip: t('pages:closeoutRecommendedIsADiagnosticCloseoutRecommendationNotClosureOfARealIncident'),
            };
        case 'REJECTED':
            return {
                label: t('pages:validationCriteriaNotMet'),
                color: 'error',
                tooltip: t('pages:rejectedAppliesToValidationEvidenceNotMarketDirection'),
            };
        case 'STALE_EVIDENCE':
            return {
                label: t('pages:staleEvidence'),
                color: 'warning',
                tooltip: t('pages:staleEvidenceMeansEvidenceIsNotFreshEnoughAndNeedsUpdating'),
            };
        case 'VALID':
            return {
                label: t('pages:checksumConsistentNotStrategyValidity'),
                color: 'processing',
                tooltip: t('pages:aValidChecksumVerifiesPayloadIntegrityOnlyNotStrategyValidityMlReadinessRealReturnsOrTradingAuthoriz'),
            };
        case 'INVALID':
            return {
                label: t('pages:checksumFailed'),
                color: 'error',
                tooltip: t('pages:anInvalidChecksumMeansArtifactVerificationFailedAndMustBeShownFailClosed'),
            };
        case 'NOT_CHECKED':
            return {
                label: t('pages:notChecked'),
                color: 'default',
                tooltip: t('pages:notCheckedMeansANoFileBaselineOrUnconfiguredSourceArtifactVerificationWasNotPerformed'),
            };
        case 'PRESENT':
            return {
                label: t('pages:summaryAvailableNotAReturnConclusion'),
                color: 'processing',
                tooltip: t('pages:presentMeansAnOfflineMetricSummaryExistsNotRealReturnsStrategyValidityOrTradingAuthorization'),
            };
        case 'INCOMPLETE':
            return {
                label: t('pages:incompleteSummary'),
                color: 'warning',
                tooltip: t('pages:incompleteMeansTheMetricSummaryIsIncompleteAndMustBeShownAsADiagnosticRisk'),
            };
        case 'FAKE_FIXTURE_ONLY':
            return {
                label: t('pages:testFixtureNotRealPerformance'),
                color: 'warning',
                tooltip: t('pages:fakeFixtureOnlyIsATestFixtureNotRealStrategyPerformanceOrReturns'),
            };
        case 'CONSISTENT':
            return {
                label: t('pages:diagnosticsConsistentNoTradingAuthorization'),
                color: 'processing',
                tooltip: t('pages:consistentMeansNoDifferencesWereFoundInPaperVersusShadowEvidenceNotProfitApprovalOrTradingAuthorizat'),
            };
        case 'DIVERGED':
            return {
                label: t('pages:paperShadowEvidenceDiffers'),
                color: 'warning',
                tooltip: t('pages:divergedMeansLocalPaperAndShadowEvidenceDiffersAndNeedsReviewNotMarketDirectionOrAutomaticHandling'),
            };
        case 'NOT_COMPARABLE':
            return {
                label: t('pages:notComparable'),
                color: 'warning',
                tooltip: t('pages:notComparableMeansInsufficientComparisonDataOrIncompatibleSchemasAndMustRemainFailClosed'),
            };
        case 'FAILED':
            return {
                label: t('pages:diagnosticsFailed'),
                color: 'error',
                tooltip: t('pages:failedMeansConsistencyDiagnosticsCouldNotBeReadOrCalculatedAndRequireInvestigationNotAutomaticHandli'),
            };
        case 'NO_REPORT':
            return {
                label: t('pages:noConsistencyReport'),
                color: 'default',
                tooltip: t('pages:noReportMeansNoLocalConsistencyReportExistsThePageDoesNotCreateOneAutomatically'),
            };
        case 'NO_DECISION':
            return {
                label: t('pages:noDecision'),
                color: 'default',
                tooltip: t('pages:noDecisionMeansValidationEvidenceCannotCurrentlySupportADecision'),
            };
        case 'FRESH':
            return {
                label: t('pages:evidenceFreshReviewStillRequired'),
                color: 'processing',
                tooltip: t('pages:freshDescribesEvidenceFreshnessOnlyNotReturnsApprovalOrAuthorization'),
            };
        case 'STALE':
            return {
                label: t('pages:staleEvidence'),
                color: 'warning',
                tooltip: t('pages:staleMeansEvidenceNeedsRefreshingOrCompletion'),
            };
        case 'MISSING':
            return {
                label: t('pages:evidenceMissing'),
                color: 'warning',
                tooltip: t('pages:missingMeansEvidenceIsAbsentAndMustNotBeShownAsAPass'),
            };
        case 'PARTIAL':
            return {
                label: t('pages:evidencePartiallyAvailable'),
                color: 'warning',
                tooltip: t('pages:partialMeansEvidenceIsIncompleteAndRequiresManualCompletionOrConfirmation'),
            };
        case 'NONE':
            return {
                label: t('pages:noDiagnosticPriority'),
                color: 'default',
                tooltip: t('pages:noneMeansNoCurrentDiagnosticPriorityNotWorkflowCompletion'),
            };
        case 'INFO':
            return {
                label: t('pages:generalDiagnosticInformation'),
                color: 'processing',
                tooltip: t('pages:infoIndicatesGeneralDiagnosticInformationOnly'),
            };
        case 'WARNING':
            return {
                label: t('pages:diagnosticWarning'),
                color: 'warning',
                tooltip: t('pages:warningIndicatesADiagnosticWarningRequiringInspection'),
            };
        case 'HIGH':
            return {
                label: t('pages:highDiagnosticPriority'),
                color: 'error',
                tooltip: t('pages:highIndicatesDiagnosticPriorityNotAutomaticHandlingOrTradingState'),
            };
        case 'CRITICAL':
            return {
                label: t('pages:criticalDiagnosticPriority'),
                color: 'error',
                tooltip: t('pages:criticalRequiresPriorityReviewAutomaticHandlingIsNotComplete'),
            };
        default:
            return {
                label: normalized === 'UNKNOWN' ? t('pages:unknownState') : normalized,
                color: normalized === 'UNKNOWN' ? 'default' : statusPresentation(normalized).tone === 'danger' ? 'error' : TONE_TO_COLOR[statusPresentation(normalized).tone],
                tooltip: t('pages:unknownOrUnmappedStatesRemainFailClosedAndCannotMeanAuthorizationOrSuccess'),
            };
    }
}

function WorkflowStatusTag({status}: { status?: string | null }) {
    useTranslation('pages');
    const normalized = normalizeStatus(status);
    const presentation = workflowStatusPresentation(normalized);
    const tone = presentation.color === 'error'
        ? 'danger'
        : presentation.color === 'warning'
            ? 'warning'
            : presentation.color === 'processing' ? 'info' : 'neutral';
    return (
        <Tooltip title={presentation.tooltip}>
            <CanonicalStatusTag
                status={normalized}
                label={`${normalized}（${presentation.label}）`}
                tone={tone}
                title={presentation.tooltip}
                variant="pill"
            />
        </Tooltip>
    );
}

function QueryForm({
                       initialValues,
                       onSubmit,
                       onReset,
                       loading,
                   }: {
    initialValues: StrategyValidationQuery;
    onSubmit: (values: StrategyValidationQuery) => void;
    onReset: () => void;
    loading: boolean;
}) {
    useTranslation('pages');
    const [form] = useLocalizedForm<StrategyValidationQuery>();

    useEffect(() => {
        form.setFieldsValue(initialValues);
    }, [form, initialValues]);

    return (
        <Card className="page-section" variant="borderless" title={t('pages:readOnlyQueryFilters')}>
            <Form<StrategyValidationQuery>
                form={form}
                layout="vertical"
                initialValues={initialValues}
                onFinish={(values) => onSubmit(normalizeQuery(values))}
            >
                <div style={{display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12}}>
                    {QUERY_FIELDS.map((field) => (
                        <Form.Item key={field} label={FIELD_LABELS[field]} name={field}>
                            <Input allowClear placeholder={t('pages:enterValue1', {value1: FIELD_LABELS[field]})}/>
                        </Form.Item>
                    ))}
                </div>
                <Space size={8} wrap>
                    <Button type="primary" htmlType="submit" icon={<SearchOutlined/>} loading={loading}>
                        {t('pages:queryReadOnlyEvidence')}</Button>
                    <Button
                        icon={<ClearOutlined/>}
                        onClick={() => {
                            form.resetFields();
                            onReset();
                        }}
                    >
                        {t('pages:clear')}</Button>
                </Space>
            </Form>
        </Card>
    );
}

function ResultPanel<TData>({
                                title,
                                subtitle,
                                status,
                                submitted,
                                query,
                                requiredEvidence = [],
                                missingEvidence = [],
                                blockers = [],
                                warnings = [],
                                nextSteps = [],
                                boundaryDescription,
                                children,
                            }: ResultPanelProps<TData>) {
    useTranslation('pages');
    return (
        <Card
            className="page-section"
            variant="borderless"
            title={title}
            extra={submitted ? (
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    {t('pages:refresh')}</Button>
            ) : null}
        >
            {!submitted ? (
                <Empty description={t('pages:noReadOnlyQuerySubmitted')}/>
            ) : query.isLoading ? (
                <Skeleton active paragraph={{rows: 6}}/>
            ) : query.isError ? (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:failedToQueryValue1', {value1: title})}
                    description={(
                        <Paragraph style={{marginBottom: 0}}>
                            {t('pages:thisResultIsUnavailableNotPassedOrAuthorized')}{formatApiError(query.error as AppApiError)}
                        </Paragraph>
                    )}
                />
            ) : !query.data ? (
                <Empty description={t('pages:noDataAvailableFromTheReadOnlyApi')}/>
            ) : (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Space size={8} wrap>
                        <StatusTag status={status}/>
                        <Text type="secondary">{subtitle}</Text>
                    </Space>
                    <Alert type="info" showIcon message={t('pages:readOnlyBoundary')} description={boundaryDescription}/>
                    {isProblemStatus(status) ? (
                        <Alert
                            type="warning"
                            showIcon
                            message={t('pages:theQueryResultIsNotAPass')}
                            description={t('pages:unknownNotAvailableNotImplementedBlockedAreNotSuccessStatesAddressBlockersAndNextstepsFirst')}
                        />
                    ) : null}
                    {children}
                    <EvidenceTables requiredEvidence={requiredEvidence} missingEvidence={missingEvidence}/>
                    <ReasonTables blockers={blockers} warnings={warnings}/>
                    <NextStepsList nextSteps={nextSteps}/>
                </Space>
            )}
        </Card>
    );
}

function EvidenceTables({
                            requiredEvidence,
                            missingEvidence,
                        }: {
    requiredEvidence: StrategyValidationEvidence[];
    missingEvidence: StrategyValidationEvidence[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:requiredEvidence')}</Text>
                <Table<StrategyValidationEvidence>
                    size="small"
                    rowKey={(record) => record.code}
                    columns={evidenceColumns}
                    dataSource={requiredEvidence}
                    pagination={false}
                    scroll={{x: 720}}
                    locale={{emptyText: t('pages:noRequiredEvidence')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:missingEvidence')}</Text>
                <Table<StrategyValidationEvidence>
                    size="small"
                    rowKey={(record) => record.code}
                    columns={evidenceColumns}
                    dataSource={missingEvidence}
                    pagination={false}
                    scroll={{x: 720}}
                    locale={{emptyText: t('pages:noMissingEvidence')}}
                />
            </div>
        </Space>
    );
}

function ReasonTables({
                          blockers,
                          warnings,
                      }: {
    blockers: StrategyValidationReason[];
    warnings: StrategyValidationReason[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:blockers2')}</Text>
                <Table<StrategyValidationReason>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}`}
                    columns={reasonColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 760}}
                    locale={{emptyText: t('pages:noBlockers')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:warnings3')}</Text>
                <Table<StrategyValidationReason>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}`}
                    columns={reasonColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 760}}
                    locale={{emptyText: t('pages:noWarnings2')}}
                />
            </div>
        </Space>
    );
}

function NextStepsList({nextSteps}: { nextSteps: string[] }) {
    useTranslation('pages');
    if (!nextSteps.length) {
        return (
            <Alert
                type="info"
                showIcon
                message={t('pages:nextSteps')}
                description={t('pages:theResponseContainsNoNextstepsMissingNextStepsDoNotMeanCompletion')}
            />
        );
    }
    return (
        <Alert
            type="warning"
            showIcon
            message={t('pages:nextSteps')}
            description={(
                <ul style={{margin: 0, paddingInlineStart: 20}}>
                    {nextSteps.map((step) => (
                        <li key={step}>{step}</li>
                    ))}
                </ul>
            )}
        />
    );
}

function countValue(value: number | null | undefined): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function decisionOf(overview: StrategyValidationOverviewResponse | undefined): string {
    return normalizeStatus(overview?.latestDecision?.decision);
}

function overviewHasNoEvidence(overview: StrategyValidationOverviewResponse): boolean {
    return countValue(overview.totalStrategyVersions) === 0
        || countValue(overview.evaluatedStrategyVersions) === 0
        || overview.evidenceAnchors.length === 0
        || decisionOf(overview) === 'NO_EVIDENCE';
}

function overviewIsEmpty(overview: StrategyValidationOverviewResponse): boolean {
    return countValue(overview.totalStrategyVersions) === 0
        && countValue(overview.evaluatedStrategyVersions) === 0
        && countValue(overview.approvedForValidation) === 0
        && countValue(overview.rejectedForValidation) === 0
        && countValue(overview.needsReview) === 0
        && countValue(overview.blocked) === 0
        && !overview.latestDecision
        && overview.blockers.length === 0
        && overview.warnings.length === 0
        && overview.nextSteps.length === 0
        && overview.evidenceAnchors.length === 0;
}

function resolveOverviewState(overview: StrategyValidationOverviewResponse): OverviewPanelState {
    const decision = decisionOf(overview);

    if (overviewIsEmpty(overview)) {
        return {
            level: 'info',
            message: t('pages:noStrategyValidationOverviewData'),
            description: t('pages:theReadOnlyResponseIsEmptyEvidenceIsNotFabricatedAndAnEmptyStateIsNotAValidationPass'),
        };
    }
    if (overviewHasNoEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:strategyValidationOverviewLacksEvidence'),
            description: t('pages:noEvidenceOrMissingAnchorsMeansInsufficientValidationEvidenceCompleteTheReadOnlyFactSourcesFirst'),
        };
    }
    if (decision === 'BLOCKED' || countValue(overview.blocked) > 0 || overview.blockers.length > 0) {
        return {
            level: 'error',
            message: t('pages:strategyValidationOverviewBlocked'),
            description: t('pages:blockedRefersToTheValidationDiagnosticChainAddressBlockersTradingStateIsUnchanged'),
        };
    }
    if (decision === 'REJECTED' || countValue(overview.rejectedForValidation) > 0) {
        return {
            level: 'error',
            message: t('pages:strategyValidationOverviewRejected'),
            description: t('pages:rejectedMeansValidationEvidenceDoesNotQualifyForFurtherReviewNotATradingOrMarketDirectionJudgment'),
        };
    }
    if (decision === 'NEEDS_REVIEW' || countValue(overview.needsReview) > 0 || decision === 'STALE_EVIDENCE') {
        return {
            level: 'warning',
            message: t('pages:strategyValidationOverviewNeedsReview'),
            description: t('pages:manuallyInspectDecisionreasonsLimitationsWarningsAndNextstepsThisIsNotClearance'),
        };
    }
    if (decision === 'APPROVED' || countValue(overview.approvedForValidation) > 0) {
        return {
            level: 'info',
            message: t('pages:strategyValidationEvidencePassed'),
            description: t('pages:approvedMeansValidationEvidenceCurrentlySupportsFurtherReviewNotTradingAuthorizationLiveEnablementOr'),
        };
    }
    return {
        level: 'info',
        message: t('pages:strategyValidationOverviewLoaded'),
        description: t('pages:thisResultSupportsReadOnlyValidationDiagnosticsOnlyWithoutTradingOrRuntimeSideEffects'),
    };
}

function BoundaryBadge({label, tooltip, color}: { label: string; tooltip: string; color?: string }) {
    useTranslation('pages');
    return (
        <Tooltip title={tooltip}>
            <Tag color={color}>{label}</Tag>
        </Tooltip>
    );
}

function StrategyValidationOverviewBoundaryBadges({overview}: { overview?: StrategyValidationOverviewResponse }) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataRemainsFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t('pages:livedisabledTrueThisPageMustNotShowLiveAvailabilityOrRealTradingReadinessValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t('pages:realproviderimplementedFalseRealProviderAvailabilityIsNotEstablishedValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t('pages:privatetradingimplementedFalseNoOrdersCancellationsTransfersOrWithdrawalsValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:validationIsNotTradingAuthorization')}
                tooltip={t('pages:validationResultsAreForReviewOnlyNotTradingAuthorizationValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t('pages:nottradingauthorizationTrueApprovedDoesNotMeanTradableValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t('pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue1', {value1: pending})}
            />
        </Space>
    );
}

function OverviewBoundaryDriftAlert({overview}: { overview?: StrategyValidationOverviewResponse }) {
    useTranslation('pages');
    if (!overview) {
        return null;
    }
    const drift = !overview.diagnosticOnly
        || !overview.noSideEffect
        || !overview.notTradingAuthorization
        || !overview.liveDisabled
        || overview.realProviderImplemented
        || overview.privateTradingImplemented
        || overview.aiDhRuntimeIntegrated;

    return drift ? (
        <Alert
            type="error"
            showIcon
            message={t('pages:overviewBoundaryFlagsConflictWithTheSafetyBaseline')}
            description={t('pages:theResponseRemainsFailClosedUnexpectedFlagsDoNotIndicateTradabilityExecutabilityOrRealTradingReadine')}
        />
    ) : null;
}

function OverviewCounts({overview}: { overview?: StrategyValidationOverviewResponse }) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="totalStrategyVersions">
                {countValue(overview?.totalStrategyVersions)}
            </Descriptions.Item>
            <Descriptions.Item label="evaluatedStrategyVersions">
                {countValue(overview?.evaluatedStrategyVersions)}
            </Descriptions.Item>
            <Descriptions.Item label="approvedForValidation">
                {countValue(overview?.approvedForValidation)}
            </Descriptions.Item>
            <Descriptions.Item label="rejectedForValidation">
                {countValue(overview?.rejectedForValidation)}
            </Descriptions.Item>
            <Descriptions.Item label="needsReview">
                {countValue(overview?.needsReview)}
            </Descriptions.Item>
            <Descriptions.Item label="blocked">
                {countValue(overview?.blocked)}
            </Descriptions.Item>
        </Descriptions>
    );
}

function LatestDecisionSummary({latestDecision}: { latestDecision?: StrategyValidationLatestDecision | null }) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
                <Descriptions.Item label="latestDecision.decision">
                    <Tooltip title={t('pages:approvedIsAValidationLevelPassOnlyNotTradingAuthorization')}>
                        <span><StatusTag status={latestDecision?.decision}/></span>
                    </Tooltip>
                </Descriptions.Item>
                <Descriptions.Item label="strategyVersionId">
                    {optionalCode(latestDecision?.strategyVersionId)}
                </Descriptions.Item>
                <Descriptions.Item label="datasetId">{optionalCode(latestDecision?.datasetId)}</Descriptions.Item>
                <Descriptions.Item label="evaluationReportId">
                    {optionalCode(latestDecision?.evaluationReportId)}
                </Descriptions.Item>
                <Descriptions.Item label="publishId">{optionalCode(latestDecision?.publishId)}</Descriptions.Item>
                <Descriptions.Item label="paperRunId">{optionalCode(latestDecision?.paperRunId)}</Descriptions.Item>
                <Descriptions.Item label="shadowRunId">{optionalCode(latestDecision?.shadowRunId)}</Descriptions.Item>
                <Descriptions.Item
                    label="latestDecision.traceId">{optionalCode(latestDecision?.traceId)}</Descriptions.Item>
                <Descriptions.Item
                    label="generatedAt">{generatedAtText(latestDecision?.generatedAt)}</Descriptions.Item>
            </Descriptions>
            <TextList
                title="decisionReasons"
                items={latestDecision?.decisionReasons ?? []}
                emptyText={t('pages:noDecisionreasonsApprovalReasonsMustNotBeFabricated')}
            />
            <TextList
                title="limitations"
                items={latestDecision?.limitations ?? []}
                emptyText={t('pages:noLimitationsListedFixedSafetyBoundariesStillApply')}
            />
        </Space>
    );
}

function TextList({title, items, emptyText}: { title: string; items: string[]; emptyText: string }) {
    useTranslation('pages');
    return (
        <section aria-label={title}>
            <Text strong>{title}</Text>
            {items.length === 0 ? (
                <Paragraph type="secondary" style={{marginBottom: 0}}>{emptyText}</Paragraph>
            ) : (
                <ul style={{margin: 0, paddingInlineStart: 20}}>
                    {items.map((item) => (
                        <li key={item}>{item}</li>
                    ))}
                </ul>
            )}
        </section>
    );
}

function OverviewIssueTables({
                                 blockers,
                                 warnings,
                             }: {
    blockers: StrategyValidationBlocker[];
    warnings: StrategyValidationWarning[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:blockers2')}</Text>
                <Table<StrategyValidationBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={overviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noBlockersListedFixedSafetyBoundariesStillApply')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:warnings3')}</Text>
                <Table<StrategyValidationWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={overviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noWarningsFixedSafetyBoundariesStillApply')}}
                />
            </div>
        </Space>
    );
}

function StrategyValidationOverviewPanel({query}: { query: PanelQueryState<StrategyValidationOverviewResponse> }) {
    useTranslation('pages');
    const overview = query.data;
    const overviewState = overview ? resolveOverviewState(overview) : null;
    const stateAlertType = overviewState?.level ?? 'info';

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:strategyValidationOverview')}
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    {t('pages:refreshOverview')}</Button>
            )}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyGetApiStrategyValidationOverviewForTheValidationRuntimeBaselineWithoutNewRoutesDashboardV2Or')}</Paragraph>
                <StrategyValidationOverviewBoundaryBadges overview={overview}/>
                <OverviewBoundaryDriftAlert overview={overview}/>
                <OverviewCounts overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToQueryStrategyValidationOverview')}
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                {t('pages:aFailedOverviewIsUnavailableNotPassedAuthorizedOrExecutable')}{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description={t('pages:noStrategyValidationOverviewResponseFixedSafetyBoundariesRemainFailClosed')}/>
                ) : (
                    <>
                        {overviewState ? (
                            <Alert
                                type={stateAlertType}
                                showIcon
                                message={overviewState.message}
                                description={overviewState.description}
                            />
                        ) : null}
                        {overviewHasNoEvidence(overview) ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:noEvidence2')}
                                description={t('pages:missingEvidenceanchorsOrDecisionNoEvidenceMeansEvidenceIsMissingThePageDoesNotFabricateIt')}
                            />
                        ) : null}
                        <LatestDecisionSummary latestDecision={overview.latestDecision}/>
                        <OverviewIssueTables blockers={overview.blockers} warnings={overview.warnings}/>
                        <Table<StrategyValidationNextStep>
                            size="small"
                            rowKey={(record) => record.code}
                            columns={overviewNextStepColumns}
                            dataSource={overview.nextSteps}
                            pagination={false}
                            scroll={{x: 1000}}
                            locale={{emptyText: t('pages:noNextstepsThisDoesNotPermitTrading2')}}
                        />
                        <Table<StrategyValidationEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.checksum ?? 'none'}`}
                            columns={overviewEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1030}}
                            locale={{emptyText: t('pages:noEvidenceanchorsEvidenceCannotBeConsideredComplete')}}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function shadowWorkflowIsEmpty(overview: ShadowValidationWorkflowOverviewResponse): boolean {
    return countValue(overview.totalOperatorItems) === 0
        && overview.operatorItems.length === 0
        && !overview.latestOperatorItem
        && overview.blockers.length === 0
        && overview.warnings.length === 0
        && overview.nextSteps.length === 0
        && overview.evidenceAnchors.length === 0;
}

function shadowWorkflowHasNoEvidence(overview: ShadowValidationWorkflowOverviewResponse): boolean {
    return countValue(overview.totalOperatorItems) === 0
        || overview.operatorItems.length === 0
        || overview.evidenceAnchors.length === 0
        || normalizeStatus(overview.latestOperatorItem?.evidenceFreshness) === 'MISSING';
}

function shadowWorkflowNeedsEvidence(overview: ShadowValidationWorkflowOverviewResponse): boolean {
    const item = overview.latestOperatorItem;
    const freshness = normalizeStatus(item?.evidenceFreshness);
    const decision = normalizeStatus(item?.validationDecision);
    const state = normalizeStatus(item?.workflowState);
    return state === 'NEEDS_EVIDENCE'
        || decision === 'STALE_EVIDENCE'
        || freshness === 'STALE'
        || freshness === 'MISSING'
        || freshness === 'PARTIAL';
}

function shadowWorkflowBlocked(overview: ShadowValidationWorkflowOverviewResponse): boolean {
    return countValue(overview.blockedCount) > 0
        || overview.blockers.length > 0
        || normalizeStatus(overview.latestOperatorItem?.workflowState) === 'BLOCKED'
        || normalizeStatus(overview.latestOperatorItem?.validationDecision) === 'BLOCKED';
}

/**
 * GateT-1 workflow 面板状态按用户指定优先级 fail-closed 解析。
 *
 * Why:
 * 前端不能把 ready-like 状态提前显示成正向结论；error / loading 由渲染分支优先处理，
 * 数据态内部继续按 empty -> blocked -> needs evidence -> review -> ready -> closed 排序。
 */
function resolveShadowValidationWorkflowState(overview: ShadowValidationWorkflowOverviewResponse): OverviewPanelState {
    const state = normalizeStatus(overview.latestOperatorItem?.workflowState);
    const decision = normalizeStatus(overview.latestOperatorItem?.validationDecision);

    if (shadowWorkflowIsEmpty(overview) || shadowWorkflowHasNoEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:shadowValidationWorkflowHasNoOperatorItemsOrLacksEvidence'),
            description: t('pages:emptyOrNoEvidenceMeansInsufficientLocalFactsForReviewOperatorItemsAreNotFabricatedAndThisIsNotAPass'),
        };
    }
    if (shadowWorkflowBlocked(overview)) {
        return {
            level: 'error',
            message: t('pages:shadowValidationWorkflowBlocked'),
            description: t('pages:blockedRefersToDiagnosticsAddressBlockersItDoesNotChangeTradingStateHandleRiskOrCloseItemsAutomatica'),
        };
    }
    if (shadowWorkflowNeedsEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:shadowValidationWorkflowNeedsEvidence'),
            description: t('pages:needsEvidenceStaleEvidenceStalePartialRequireCompletingOrRefreshingReadOnlyEvidenceFirst'),
        };
    }
    if (state === 'EVIDENCE_REVIEW' || decision === 'NEEDS_REVIEW' || decision === 'REJECTED') {
        return {
            level: decision === 'REJECTED' ? 'error' : 'warning',
            message: t('pages:shadowValidationWorkflowUnderEvidenceReview'),
            description: t('pages:evidenceReviewNeedsReviewRejectedRequireManualReviewOfEvidenceWarningsAndNextStepsNoTradingAuthoriza'),
        };
    }
    if (state === 'READY_FOR_OPERATOR_REVIEW' || decision === 'VALIDATION_READY') {
        return {
            level: 'info',
            message: t('pages:shadowValidationWorkflowReadyForManualReview'),
            description: t('pages:readyForOperatorReviewValidationReadyMeanEvidenceIsReviewableNotTradableApprovedOrLiveReady'),
        };
    }
    if (state === 'CLOSED_RECOMMENDATION') {
        return {
            level: 'info',
            message: t('pages:shadowValidationWorkflowHasADiagnosticRecommendation'),
            description: t('pages:closedRecommendationMeansADiagnosticRecommendationExistsNotCompletedHandlingOrTradingClearance'),
        };
    }
    return {
        level: 'info',
        message: t('pages:shadowValidationWorkflowLoaded'),
        description: t('pages:thisResultSupportsReadOnlyOperatorReviewDiagnosticsWithoutTradingRuntimeOrPersistenceSideEffects'),
    };
}

function ShadowValidationWorkflowBoundaryBadges({overview}: { overview?: ShadowValidationWorkflowOverviewResponse }) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataRemainsFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t('pages:livedisabledTrueThisWorkflowDoesNotEstablishLiveAvailabilityValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t('pages:realproviderimplementedFalseThisWorkflowDoesNotCallRealProvidersValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t('pages:privatetradingimplementedFalseThisWorkflowOffersNoOrdersCancellationsTransfersOrWithdrawalsValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:validationWorkflowIsDiagnosticOnly')}
                tooltip={t('pages:diagnosticonlyTrueOperatorItemsAreDerivedDiagnosticsWithoutPersistenceOrExecutionValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t('pages:nottradingauthorizationTrueValidationReadyDoesNotGrantTradingAuthorizationValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t('pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue12', {value1: pending})}
            />
        </Space>
    );
}

function ShadowValidationWorkflowBoundaryDriftAlert({overview}: {
    overview?: ShadowValidationWorkflowOverviewResponse
}) {
    useTranslation('pages');
    if (!overview) {
        return null;
    }
    const overviewDrift = !overview.diagnosticOnly
        || !overview.noSideEffect
        || !overview.notTradingAuthorization
        || !overview.liveDisabled
        || overview.realProviderImplemented
        || overview.privateTradingImplemented
        || overview.aiDhRuntimeIntegrated;
    const itemDrift = overview.operatorItems.some((item) => !item.diagnosticOnly
        || !item.noSideEffect
        || !item.notTradingAuthorization
        || !item.liveDisabled
        || item.realProviderImplemented
        || item.privateTradingImplemented
        || item.aiDhRuntimeIntegrated);

    return overviewDrift || itemDrift ? (
        <Alert
            type="error"
            showIcon
            message={t('pages:shadowValidationWorkflowBoundaryFlagsConflictWithTheSafetyBaseline')}
            description={t('pages:theResponseRemainsFailClosedUnexpectedFlagsDoNotPermitExecutionTradingHandlingOrLiveUse')}
        />
    ) : null;
}

function ShadowValidationWorkflowCounts({overview}: { overview?: ShadowValidationWorkflowOverviewResponse }) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="totalOperatorItems">
                {countValue(overview?.totalOperatorItems)}
            </Descriptions.Item>
            <Descriptions.Item label="intakeCount">
                {countValue(overview?.intakeCount)}
            </Descriptions.Item>
            <Descriptions.Item label="evidenceReviewCount">
                {countValue(overview?.evidenceReviewCount)}
            </Descriptions.Item>
            <Descriptions.Item label="needsEvidenceCount">
                {countValue(overview?.needsEvidenceCount)}
            </Descriptions.Item>
            <Descriptions.Item label="readyForOperatorReviewCount">
                {countValue(overview?.readyForOperatorReviewCount)}
            </Descriptions.Item>
            <Descriptions.Item label="blockedCount">
                {countValue(overview?.blockedCount)}
            </Descriptions.Item>
            <Descriptions.Item label="closedRecommendationCount">
                {countValue(overview?.closedRecommendationCount)}
            </Descriptions.Item>
            <Descriptions.Item label="generatedAt">
                {generatedAtText(overview?.generatedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="traceId">
                {optionalSafeCode(overview?.traceId)}
            </Descriptions.Item>
        </Descriptions>
    );
}

function ShadowValidationLatestOperatorItem({item}: { item?: ShadowValidationOperatorItem | null }) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
            <Descriptions.Item label="workflowState">
                <WorkflowStatusTag status={item?.workflowState}/>
            </Descriptions.Item>
            <Descriptions.Item label="validationDecision">
                <WorkflowStatusTag status={item?.validationDecision}/>
            </Descriptions.Item>
            <Descriptions.Item label="severity">
                <WorkflowStatusTag status={item?.severity}/>
            </Descriptions.Item>
            <Descriptions.Item label="evidenceFreshness">
                <WorkflowStatusTag status={item?.evidenceFreshness}/>
            </Descriptions.Item>
            <Descriptions.Item label="sourceType">{optionalText(item?.sourceType)}</Descriptions.Item>
            <Descriptions.Item label="sourceId">{optionalSafeCode(item?.sourceId)}</Descriptions.Item>
            <Descriptions.Item label="strategyVersionId">{optionalSafeCode(item?.strategyVersionId)}</Descriptions.Item>
            <Descriptions.Item label="shadowRunId">{optionalSafeCode(item?.shadowRunId)}</Descriptions.Item>
            <Descriptions.Item
                label="consistencyReportId">{optionalSafeCode(item?.consistencyReportId)}</Descriptions.Item>
            <Descriptions.Item label="latestOperatorItem.traceId">{optionalSafeCode(item?.traceId)}</Descriptions.Item>
        </Descriptions>
    );
}

function ShadowValidationWorkflowIssueTables({
                                                 blockers,
                                                 warnings,
                                             }: {
    blockers: ShadowValidationBlocker[];
    warnings: ShadowValidationWarning[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:blockers')}</Text>
                <Table<ShadowValidationBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={shadowValidationWorkflowIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noBlockersListedFixedSafetyBoundariesStillApply')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:warnings2')}</Text>
                <Table<ShadowValidationWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={shadowValidationWorkflowIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noWarningsTheWorkflowIsNotNecessarilyComplete')}}
                />
            </div>
        </Space>
    );
}

function ShadowValidationWorkflowPanel({query}: { query: PanelQueryState<ShadowValidationWorkflowOverviewResponse> }) {
    useTranslation('pages');
    const overview = query.data;
    const panelState = overview ? resolveShadowValidationWorkflowState(overview) : null;

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:shadowValidationWorkflowOverview')}
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    {t('pages:refreshOverview2')}</Button>
            )}
        >
            <Space data-testid="shadow-validation-workflow-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyGetApiShadowValidationWorkflowOverviewShowsDerivedOperatorItemsWorkflowstateValidationdecisi')}</Paragraph>
                <ShadowValidationWorkflowBoundaryBadges overview={overview}/>
                <ShadowValidationWorkflowBoundaryDriftAlert overview={overview}/>
                <ReadModelEvidenceMetadataSummary metadata={overview?.evidenceMetadata}/>
                <ShadowValidationWorkflowCounts overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToQueryShadowValidationWorkflowOverview')}
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                {t('pages:aFailedWorkflowOverviewIsUnavailableNotReviewReadyAuthorizedAutomaticallyHandledOrExecutable')}{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty
                        description={t('pages:noShadowValidationWorkflowOverviewResponseFixedSafetyBoundariesRemainFailClosed')}/>
                ) : (
                    <>
                        {panelState ? (
                            <Alert
                                type={panelState.level}
                                showIcon
                                message={panelState.message}
                                description={panelState.description}
                            />
                        ) : null}
                        {shadowWorkflowIsEmpty(overview) ? (
                            <Empty description={t('pages:noOperatorItemsAnEmptyStateDoesNotMeanValidationCompletionRecommendedClosureOrTradability')}/>
                        ) : null}
                        <ShadowValidationLatestOperatorItem item={overview.latestOperatorItem}/>
                        <ShadowValidationWorkflowIssueTables blockers={overview.blockers} warnings={overview.warnings}/>
                        <Table<ShadowValidationNextStep>
                            size="small"
                            rowKey={(record) => record.code}
                            columns={shadowValidationWorkflowNextStepColumns}
                            dataSource={overview.nextSteps}
                            pagination={false}
                            scroll={{x: 1000}}
                            locale={{emptyText: t('pages:noNextstepsReviewOrHandlingCannotBeConsideredComplete')}}
                        />
                        <Table<ShadowValidationEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={shadowValidationWorkflowEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: t('pages:noEvidenceanchorsEvidenceCannotBeConsideredComplete')}}
                        />
                        <Table<ShadowValidationOperatorItem>
                            size="small"
                            rowKey={(record) => record.operatorItemId}
                            columns={shadowValidationWorkflowOperatorColumns}
                            dataSource={overview.operatorItems}
                            pagination={false}
                            scroll={{x: 1550}}
                            locale={{emptyText: t('pages:noOperatoritemsReviewEntriesMustNotBeFabricated')}}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function consistencyEvidenceBucketRows(
    source: ConsistencyEvidenceBucketRow['source'],
    buckets: Record<string, number> | undefined,
): ConsistencyEvidenceBucketRow[] {
    return Object.entries(buckets ?? {}).map(([bucket, count]) => ({
        key: `${source}-${bucket}`,
        source,
        bucket,
        count: numberValue(count),
    }));
}

function consistencyEvidenceIsEmpty(overview: ConsistencyEvidenceOverviewResponse): boolean {
    return countValue(overview.totalEvidenceItems) === 0
        && overview.evidenceItems.length === 0
        && !overview.latestEvidenceItem
        && overview.blockers.length === 0
        && overview.warnings.length === 0
        && overview.nextSteps.length === 0
        && overview.evidenceAnchors.length === 0;
}

function consistencyEvidenceHasNoEvidence(overview: ConsistencyEvidenceOverviewResponse): boolean {
    return countValue(overview.totalEvidenceItems) === 0
        || overview.evidenceItems.length === 0
        || overview.evidenceAnchors.length === 0
        || !overview.latestEvidenceItem;
}

function consistencyEvidenceHasStaleEvidence(overview: ConsistencyEvidenceOverviewResponse): boolean {
    const freshness = normalizeStatus(overview.latestEvidenceItem?.evidenceFreshness);
    return countValue(overview.staleEvidenceCount) > 0
        || freshness === 'STALE'
        || freshness === 'MISSING'
        || freshness === 'PARTIAL'
        || freshness === 'UNKNOWN';
}

function consistencyEvidenceHasUnknown(overview: ConsistencyEvidenceOverviewResponse): boolean {
    const item = overview.latestEvidenceItem;
    return normalizeStatus(item?.comparisonStatus) === 'UNKNOWN'
        || normalizeStatus(item?.divergenceSeverity) === 'UNKNOWN'
        || normalizeStatus(item?.evidenceFreshness) === 'UNKNOWN';
}

function resolveConsistencyEvidenceState(overview: ConsistencyEvidenceOverviewResponse): OverviewPanelState {
    const comparisonStatus = normalizeStatus(overview.latestEvidenceItem?.comparisonStatus);
    const divergenceSeverity = normalizeStatus(overview.latestEvidenceItem?.divergenceSeverity);

    if (consistencyEvidenceIsEmpty(overview) || consistencyEvidenceHasNoEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:noConsistencyEvidenceInTheOverview'),
            description: t('pages:emptyOrMissingConsistencyEvidenceMeansInsufficientLocalPaperVersusShadowEvidenceThePageFabricatesNoI'),
        };
    }
    if (countValue(overview.failedCount) > 0 || comparisonStatus === 'FAILED' || countValue(overview.criticalSeverityCount) > 0 || divergenceSeverity === 'CRITICAL') {
        return {
            level: 'error',
            message: t('pages:consistencyEvidenceOverviewContainsFailedOrCriticalDiagnosticBlockers'),
            description: t('pages:failedCriticalDescribeDiagnosticPriorityOrReadCalculationFailuresRequiringInvestigationNotAutomaticH'),
        };
    }
    if (countValue(overview.highSeverityCount) > 0 || divergenceSeverity === 'HIGH') {
        return {
            level: 'warning',
            message: t('pages:consistencyEvidenceOverviewHasHighDiagnosticPriority'),
            description: t('pages:highPrioritizesPaperVersusShadowEvidenceReviewNotAutomaticHandlingOrTradingState'),
        };
    }
    if (countValue(overview.divergedCount) > 0 || comparisonStatus === 'DIVERGED') {
        return {
            level: 'warning',
            message: t('pages:consistencyEvidenceOverviewContainsPaperVersusShadowDifferences'),
            description: t('pages:divergedMeansLocalEvidenceDiffersInspectDivergencereasonsLimitationsAndAnchorsItDoesNotIndicateMarke'),
        };
    }
    if (countValue(overview.partialCount) > 0 || countValue(overview.notComparableCount) > 0 || comparisonStatus === 'PARTIAL' || comparisonStatus === 'NOT_COMPARABLE') {
        return {
            level: 'warning',
            message: t('pages:consistencyEvidenceOverviewContainsPartialOrIncomparableEvidence'),
            description: t('pages:partialNotComparableRemainFailClosedForIncompleteOrIncomparableEvidenceRetainingNextsteps'),
        };
    }
    if (consistencyEvidenceHasStaleEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:consistencyEvidenceOverviewContainsStaleEvidence'),
            description: t('pages:staleMissingPartialUnknownFreshnessIndicateInsufficientLocalEvidenceFreshnessNotCompletionOrAPass'),
        };
    }
    if (consistencyEvidenceHasUnknown(overview)) {
        return {
            level: 'warning',
            message: t('pages:consistencyEvidenceOverviewContainsUnknownStates'),
            description: t('pages:unknownRemainsFailClosedUnknownComparisonSeverityOrFreshnessDoesNotMeanConsistencyOrPermissionToCont'),
        };
    }
    if (comparisonStatus === 'CONSISTENT' || countValue(overview.consistentCount) > 0) {
        return {
            level: 'info',
            message: t('pages:consistencyEvidenceAgreesNoTradingAuthorization'),
            description: t('pages:consistentMeansNoLocalPaperVersusShadowDifferencesWereFoundNotProfitTradeApprovalLiveAvailabilityOrC'),
        };
    }
    return {
        level: 'info',
        message: t('pages:consistencyEvidenceOverviewLoaded'),
        description: t('pages:readOnlyConsistencyEvidenceDiagnosticsOnlyWithoutTradingRuntimeReportCreationOrPersistenceSideEffect'),
    };
}

function ConsistencyEvidenceBoundaryBadges({overview}: { overview?: ConsistencyEvidenceOverviewResponse }) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataRemainsFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t('pages:livedisabledTrueConsistencyEvidenceDoesNotEstablishLiveAvailabilityValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t('pages:realproviderimplementedFalseThisOverviewDoesNotCallRealProvidersValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t('pages:privatetradingimplementedFalseThisOverviewOffersNoOrdersCancellationsTransfersOrWithdrawalsValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:consistencyEvidenceIsDiagnosticOnly')}
                tooltip={t('pages:diagnosticonlyTrueEvidenceItemsAreDerivedDiagnosticsWithoutPersistenceOrExecutionValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t('pages:nottradingauthorizationTrueConsistentDoesNotGrantTradingAuthorizationValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t('pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue12', {value1: pending})}
            />
        </Space>
    );
}

function ConsistencyEvidenceBoundaryDriftAlert({overview}: { overview?: ConsistencyEvidenceOverviewResponse }) {
    useTranslation('pages');
    if (!overview) {
        return null;
    }
    const overviewDrift = !overview.diagnosticOnly
        || !overview.noSideEffect
        || !overview.notTradingAuthorization
        || !overview.liveDisabled
        || overview.realProviderImplemented
        || overview.privateTradingImplemented
        || overview.aiDhRuntimeIntegrated;
    const itemDrift = overview.evidenceItems.some((item) => !item.diagnosticOnly
        || !item.noSideEffect
        || !item.notTradingAuthorization
        || !item.liveDisabled
        || item.realProviderImplemented
        || item.privateTradingImplemented
        || item.aiDhRuntimeIntegrated);
    const metricDrift = overview.metricDeltaSummary.rawMetricDeltaExposed
        || overview.metricDeltaSummary.profitConclusionInferred
        || overview.metricDeltaSummary.tradingSignalInferred;

    return overviewDrift || itemDrift || metricDrift ? (
        <Alert
            type="error"
            showIcon
            message={t('pages:consistencyEvidenceBoundaryFlagsConflictWithTheSafetyBaseline')}
            description={t('pages:theResponseRemainsFailClosedUnexpectedFlagsRawMetricdeltaInferredReturnsOrTradingSignalsDoNotIndicat')}
        />
    ) : null;
}

function ConsistencyEvidenceCounts({overview}: { overview?: ConsistencyEvidenceOverviewResponse }) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="totalEvidenceItems">{countValue(overview?.totalEvidenceItems)}</Descriptions.Item>
            <Descriptions.Item label="consistentCount">{countValue(overview?.consistentCount)}</Descriptions.Item>
            <Descriptions.Item label="divergedCount">{countValue(overview?.divergedCount)}</Descriptions.Item>
            <Descriptions.Item label="partialCount">{countValue(overview?.partialCount)}</Descriptions.Item>
            <Descriptions.Item label="notComparableCount">{countValue(overview?.notComparableCount)}</Descriptions.Item>
            <Descriptions.Item label="failedCount">{countValue(overview?.failedCount)}</Descriptions.Item>
            <Descriptions.Item label="staleEvidenceCount">{countValue(overview?.staleEvidenceCount)}</Descriptions.Item>
            <Descriptions.Item label="highSeverityCount">{countValue(overview?.highSeverityCount)}</Descriptions.Item>
            <Descriptions.Item
                label="criticalSeverityCount">{countValue(overview?.criticalSeverityCount)}</Descriptions.Item>
            <Descriptions.Item label="generatedAt">{generatedAtText(overview?.generatedAt)}</Descriptions.Item>
            <Descriptions.Item label="traceId">{optionalSafeCode(overview?.traceId)}</Descriptions.Item>
        </Descriptions>
    );
}

function ConsistencyEvidenceLatestItem({item}: { item?: ConsistencyEvidenceItem | null }) {
    useTranslation('pages');
    if (!item) {
        return <Empty description={t('pages:noLatestevidenceitemAnEmptyStateDoesNotMeanCompleteComparableOrTradableEvidence')}/>;
    }
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
            <Descriptions.Item label="latestEvidenceItem.comparisonStatus">
                <WorkflowStatusTag status={item.comparisonStatus}/>
            </Descriptions.Item>
            <Descriptions.Item label="latestEvidenceItem.divergenceSeverity">
                <WorkflowStatusTag status={item.divergenceSeverity}/>
            </Descriptions.Item>
            <Descriptions.Item label="latestEvidenceItem.evidenceFreshness">
                <WorkflowStatusTag status={item.evidenceFreshness}/>
            </Descriptions.Item>
            <Descriptions.Item label="shadowRunId">{optionalSafeCode(item.shadowRunId)}</Descriptions.Item>
            <Descriptions.Item label="paperRunId">{optionalSafeCode(item.paperRunId)}</Descriptions.Item>
            <Descriptions.Item
                label="consistencyReportId">{optionalSafeCode(item.consistencyReportId)}</Descriptions.Item>
            <Descriptions.Item label="strategyVersionId">{optionalSafeCode(item.strategyVersionId)}</Descriptions.Item>
            <Descriptions.Item label="datasetId">{optionalSafeCode(item.datasetId)}</Descriptions.Item>
            <Descriptions.Item label="latestEvidenceItem.traceId">{optionalSafeCode(item.traceId)}</Descriptions.Item>
            <Descriptions.Item
                label="latestEvidenceItem.generatedAt">{generatedAtText(item.generatedAt)}</Descriptions.Item>
        </Descriptions>
    );
}

function ConsistencyEvidenceMetricDeltaSummary({
                                                   summary,
                                               }: {
    summary?: ConsistencyEvidenceOverviewResponse['metricDeltaSummary'];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
                <Descriptions.Item label="metricCount">{countValue(summary?.metricCount)}</Descriptions.Item>
                <Descriptions.Item label="comparableMetricCount">
                    {countValue(summary?.comparableMetricCount)}
                </Descriptions.Item>
                <Descriptions.Item label="nonComparableMetricCount">
                    {countValue(summary?.nonComparableMetricCount)}
                </Descriptions.Item>
                <Descriptions.Item label="sensitiveFieldFilteredCount">
                    {countValue(summary?.sensitiveFieldFilteredCount)}
                </Descriptions.Item>
                <Descriptions.Item label="rawMetricDeltaExposed">
                    <Tag color={summary?.rawMetricDeltaExposed ? 'error' : 'default'}>
                        {String(Boolean(summary?.rawMetricDeltaExposed))}{t('pages:rawMetricdeltaMustNotBeExposed')}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="profitConclusionInferred">
                    <Tag color={summary?.profitConclusionInferred ? 'error' : 'default'}>
                        {String(Boolean(summary?.profitConclusionInferred))}{t('pages:noInferredReturnConclusions')}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="tradingSignalInferred">
                    <Tag color={summary?.tradingSignalInferred ? 'error' : 'default'}>
                        {String(Boolean(summary?.tradingSignalInferred))}{t('pages:noTradingSignalsGenerated')}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="limitationCodes">
                    {safeTextListSummary(summary?.limitationCodes)}
                </Descriptions.Item>
            </Descriptions>
            <Table<ConsistencyEvidenceMetricDeltaItem>
                size="small"
                rowKey={(record) => `${record.name}-${record.unit ?? 'none'}`}
                columns={consistencyEvidenceMetricColumns}
                dataSource={summary?.topDeltaMetrics ?? []}
                pagination={false}
                scroll={{x: 980}}
                locale={{emptyText: t('pages:noTopdeltametricsMetricDeltasAndReturnConclusionsMustNotBeFabricated')}}
            />
        </Space>
    );
}

function ConsistencyEvidenceIssueTables({
                                            blockers,
                                            warnings,
                                        }: {
    blockers: ConsistencyEvidenceBlocker[];
    warnings: ConsistencyEvidenceWarning[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:blockers')}</Text>
                <Table<ConsistencyEvidenceBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={consistencyEvidenceIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noBlockersListedFixedSafetyBoundariesStillApply')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:warnings2')}</Text>
                <Table<ConsistencyEvidenceWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={consistencyEvidenceIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noWarningsConsistencyEvidenceIsNotNecessarilyComplete')}}
                />
            </div>
        </Space>
    );
}

function ConsistencyEvidenceOverviewPanel({query}: { query: PanelQueryState<ConsistencyEvidenceOverviewResponse> }) {
    useTranslation('pages');
    const overview = query.data;
    const panelState = overview ? resolveConsistencyEvidenceState(overview) : null;
    const bucketRows = overview ? [
        ...consistencyEvidenceBucketRows('severityBuckets', overview.severityBuckets),
        ...consistencyEvidenceBucketRows('freshnessSummary', overview.freshnessSummary),
    ] : [];

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:consistencyEvidenceOverview')}
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    {t('pages:refreshEvidence')}</Button>
            )}
        >
            <Space data-testid="consistency-evidence-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyGetApiPaperShadowConsistencyEvidenceOverviewShowsEvidenceCountsLatestevidenceitemSeveritybuc')}</Paragraph>
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:interpretationBoundary')}
                    description={t('pages:consistentDoesNotMeanProfitOrTradabilityDivergedMeansPaperVersusShadowEvidenceDiffersHighCriticalInd')}
                />
                <ConsistencyEvidenceBoundaryBadges overview={overview}/>
                <ConsistencyEvidenceBoundaryDriftAlert overview={overview}/>
                <ReadModelEvidenceMetadataSummary
                    metadata={overview?.evidenceMetadata}
                    testId="consistency-evidence-metadata"
                />
                <ConsistencyEvidenceCounts overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToQueryConsistencyEvidenceOverview')}
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                {t('pages:aFailedConsistencyOverviewIsUnavailableNotConsistentAuthorizedAutomaticallyHandledOrExecutable')}{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description={t('pages:noConsistencyEvidenceOverviewResponseSafetyBoundariesRemainFailClosed')}/>
                ) : (
                    <>
                        {panelState ? (
                            <Alert
                                type={panelState.level}
                                showIcon
                                message={panelState.message}
                                description={panelState.description}
                            />
                        ) : null}
                        {consistencyEvidenceIsEmpty(overview) ? (
                            <Empty description={t('pages:noConsistencyEvidenceAnEmptyStateDoesNotMeanEvidenceIsCompleteComparableReviewedOrTradable')}/>
                        ) : null}
                        <ConsistencyEvidenceLatestItem item={overview.latestEvidenceItem}/>
                        <div>
                            <Text strong>{t('pages:evidenceSummaries')}</Text>
                            <Table<ConsistencyEvidenceBucketRow>
                                size="small"
                                rowKey={(record) => record.key}
                                columns={consistencyEvidenceBucketColumns}
                                dataSource={bucketRows}
                                pagination={false}
                                scroll={{x: 560}}
                                locale={{emptyText: t('pages:noSeveritybucketsOrFreshnesssummaryBucketStatisticsMustNotBeFabricated')}}
                            />
                        </div>
                        <div>
                            <Text strong>{t('pages:metricdeltasummaryDiagnosticDifferences')}</Text>
                            <ConsistencyEvidenceMetricDeltaSummary summary={overview.metricDeltaSummary}/>
                        </div>
                        <ConsistencyEvidenceIssueTables blockers={overview.blockers} warnings={overview.warnings}/>
                        <Table<ConsistencyEvidenceNextStep>
                            size="small"
                            rowKey={(record) => record.code}
                            columns={consistencyEvidenceNextStepColumns}
                            dataSource={overview.nextSteps}
                            pagination={false}
                            scroll={{x: 1000}}
                            locale={{emptyText: t('pages:noNextstepsReviewOrHandlingCannotBeConsideredComplete')}}
                        />
                        <Table<ConsistencyEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={consistencyEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: t('pages:noEvidenceanchorsEvidenceCannotBeConsideredComplete')}}
                        />
                        <Table<ConsistencyEvidenceItem>
                            size="small"
                            rowKey={(record) => record.evidenceItemId}
                            columns={consistencyEvidenceItemColumns}
                            dataSource={overview.evidenceItems}
                            pagination={false}
                            scroll={{x: 1660}}
                            locale={{emptyText: t('pages:noEvidenceitemsConsistencyEvidenceMustNotBeFabricated')}}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function incidentReplayReviewBucketRows(
    source: IncidentReplayReviewBucketRow['source'],
    buckets: Record<string, number> | undefined,
): IncidentReplayReviewBucketRow[] {
    return Object.entries(buckets ?? {}).map(([bucket, count]) => ({
        key: `${source}-${bucket}`,
        source,
        bucket,
        count: numberValue(count),
    }));
}

function incidentReplayReviewIsEmpty(overview: IncidentReplayReviewOverviewResponse): boolean {
    return countValue(overview.totalReviewItems) === 0
        && overview.reviewItems.length === 0
        && !overview.latestReviewItem
        && overview.blockers.length === 0
        && overview.warnings.length === 0
        && overview.nextSteps.length === 0
        && overview.evidenceAnchors.length === 0;
}

function incidentReplayReviewHasNoEvidence(overview: IncidentReplayReviewOverviewResponse): boolean {
    return countValue(overview.totalReviewItems) === 0
        || overview.reviewItems.length === 0
        || overview.evidenceAnchors.length === 0
        || !overview.latestReviewItem;
}

function incidentReplayReviewHasStaleEvidence(overview: IncidentReplayReviewOverviewResponse): boolean {
    const freshness = normalizeStatus(overview.latestReviewItem?.evidenceFreshness);
    const decision = normalizeStatus(overview.latestReviewItem?.reviewDecision);
    return decision === 'STALE_EVIDENCE'
        || freshness === 'STALE'
        || freshness === 'MISSING'
        || freshness === 'PARTIAL'
        || freshness === 'UNKNOWN';
}

function incidentReplayReviewBlocked(overview: IncidentReplayReviewOverviewResponse): boolean {
    return countValue(overview.blockedCount) > 0
        || overview.blockers.length > 0
        || normalizeStatus(overview.latestReviewItem?.reviewState) === 'BLOCKED'
        || normalizeStatus(overview.latestReviewItem?.reviewDecision) === 'BLOCKED';
}

function incidentReplayReviewPriority(overview: IncidentReplayReviewOverviewResponse): 'critical' | 'high' | 'normal' {
    const severity = normalizeStatus(overview.latestReviewItem?.severity);
    if (severity === 'CRITICAL' || numberValue(overview.severityBuckets.CRITICAL) > 0) {
        return 'critical';
    }
    if (severity === 'HIGH' || numberValue(overview.severityBuckets.HIGH) > 0) {
        return 'high';
    }
    return 'normal';
}

function resolveIncidentReplayReviewState(overview: IncidentReplayReviewOverviewResponse): OverviewPanelState {
    const state = normalizeStatus(overview.latestReviewItem?.reviewState);
    const decision = normalizeStatus(overview.latestReviewItem?.reviewDecision);
    const priority = incidentReplayReviewPriority(overview);

    if (incidentReplayReviewIsEmpty(overview) || incidentReplayReviewHasNoEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:incidentReplayReviewOverviewHasNoEvidence'),
            description: t('pages:missingReviewEvidenceMeansInsufficientLocalFactsReviewItemsAreNotFabricatedOrShownAsAcknowledgedEsca'),
        };
    }
    if (incidentReplayReviewBlocked(overview)) {
        return {
            level: 'error',
            message: t('pages:incidentReplayReviewOverviewBlocked'),
            description: t('pages:blockedMeansDiagnosticReviewIsBlockedAddressBlockersTradingStateRiskHandlingAndRealIncidentClosureAr'),
        };
    }
    if (priority === 'critical') {
        return {
            level: 'error',
            message: t('pages:incidentReplayReviewHasCriticalDiagnosticPriority'),
            description: t('pages:criticalRequiresPriorityManualReviewNotAutomaticHandlingEscalationIncidentClosureOrTradingAuthorizat'),
        };
    }
    if (priority === 'high') {
        return {
            level: 'warning',
            message: t('pages:incidentReplayReviewHasHighDiagnosticPriority'),
            description: t('pages:highRequiresManualInspectionOfReviewItemsBlockersWarningsAndNextstepsNotAutomaticHandlingOrTradingCh'),
        };
    }
    if (incidentReplayReviewHasStaleEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:incidentReplayReviewContainsStaleEvidence'),
            description: t('pages:staleEvidenceStaleMissingPartialUnknownRequireCompletingOrRefreshingReadOnlyLocalFactSourcesFirst'),
        };
    }
    if (state === 'EVIDENCE_REVIEW' || state === 'NEEDS_OPERATOR_REVIEW' || decision === 'REVIEW_NEEDED') {
        return {
            level: 'warning',
            message: t('pages:incidentReplayReviewNeedsManualReview'),
            description: t('pages:evidenceReviewNeedsOperatorReviewReviewNeededRequireManualDiagnosticInspectionNoSystemHandlingIsImpl'),
        };
    }
    if (state === 'ACKNOWLEDGED_RECOMMENDATION' || decision === 'ACKNOWLEDGE_RECOMMENDED') {
        return {
            level: 'info',
            message: t('pages:incidentReplayReviewRecommendsManualAcknowledgment'),
            description: t('pages:acknowledgeRecommendedSuggestsManualAcknowledgmentNotSystemAcknowledgmentAutomaticHandlingOrTradingA'),
        };
    }
    if (state === 'ESCALATED_RECOMMENDATION' || decision === 'ESCALATE_RECOMMENDED') {
        return {
            level: 'warning',
            message: t('pages:incidentReplayReviewRecommendsManualEscalation'),
            description: t('pages:escalateRecommendedSuggestsManualEscalationNoSystemEscalationOrExternalProcessIsTriggered'),
        };
    }
    if (state === 'CLOSED_RECOMMENDATION' || decision === 'CLOSEOUT_RECOMMENDED') {
        return {
            level: 'info',
            message: t('pages:incidentReplayReviewHasADiagnosticCloseoutRecommendation'),
            description: t('pages:closedRecommendationCloseoutRecommendedSuggestDiagnosticCloseoutNotActualIncidentClosureOrCompletedA'),
        };
    }
    if (state === 'INTAKE') {
        return {
            level: 'info',
            message: t('pages:incidentReplayReviewIsInIntake'),
            description: t('pages:intakeMeansADerivedReviewItemEnteredTheDiagnosticViewNoHandlingOrClosureRecommendationExistsYet'),
        };
    }
    return {
        level: 'info',
        message: t('pages:incidentReplayReviewOverviewLoaded'),
        description: t('pages:readOnlyIncidentReplayReviewDiagnosticsOnlyWithoutPersistenceEscalationClosureTradingOrRuntimeSideEf'),
    };
}

function IncidentReplayReviewBoundaryBadges({overview}: { overview?: IncidentReplayReviewOverviewResponse }) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataRemainsFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t('pages:livedisabledTrueThisReviewOverviewDoesNotEstablishLiveAvailabilityValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t('pages:realproviderimplementedFalseThisReviewOverviewDoesNotCallRealProvidersValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t('pages:privatetradingimplementedFalseThisReviewOverviewOffersNoOrdersCancellationsTransfersOrWithdrawalsVal', {value1: pending})}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:incidentReplayReviewIsDiagnosticOnly')}
                tooltip={t('pages:diagnosticonlyTrueReviewItemsAreDerivedDiagnosticsWithoutPersistenceOrAutomaticHandlingValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t('pages:nottradingauthorizationTrueAcknowledgeEscalateClosedRecommendationsDoNotGrantTradingAuthorizationVal', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t('pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue12', {value1: pending})}
            />
        </Space>
    );
}

function IncidentReplayReviewBoundaryDriftAlert({overview}: { overview?: IncidentReplayReviewOverviewResponse }) {
    useTranslation('pages');
    if (!overview) {
        return null;
    }
    const overviewDrift = !overview.diagnosticOnly
        || !overview.noSideEffect
        || !overview.notTradingAuthorization
        || !overview.liveDisabled
        || overview.realProviderImplemented
        || overview.privateTradingImplemented
        || overview.aiDhRuntimeIntegrated;
    const itemDrift = overview.reviewItems.some((item) => !item.diagnosticOnly
        || !item.noSideEffect
        || !item.notTradingAuthorization
        || !item.liveDisabled
        || item.realProviderImplemented
        || item.privateTradingImplemented
        || item.aiDhRuntimeIntegrated);

    return overviewDrift || itemDrift ? (
        <Alert
            type="error"
            showIcon
            message={t('pages:incidentReplayReviewBoundaryFlagsConflictWithTheSafetyBaseline')}
            description={t('pages:theResponseRemainsFailClosedUnexpectedFlagsDoNotMeanAcknowledgmentEscalationClosureTradabilityExecut')}
        />
    ) : null;
}

function IncidentReplayReviewCounts({overview}: { overview?: IncidentReplayReviewOverviewResponse }) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="totalReviewItems">{countValue(overview?.totalReviewItems)}</Descriptions.Item>
            <Descriptions.Item label="intakeCount">{countValue(overview?.intakeCount)}</Descriptions.Item>
            <Descriptions.Item label="evidenceReviewCount">{countValue(overview?.evidenceReviewCount)}</Descriptions.Item>
            <Descriptions.Item
                label="needsOperatorReviewCount">{countValue(overview?.needsOperatorReviewCount)}</Descriptions.Item>
            <Descriptions.Item
                label="acknowledgedRecommendationCount">{countValue(overview?.acknowledgedRecommendationCount)}</Descriptions.Item>
            <Descriptions.Item
                label="escalatedRecommendationCount">{countValue(overview?.escalatedRecommendationCount)}</Descriptions.Item>
            <Descriptions.Item
                label="closedRecommendationCount">{countValue(overview?.closedRecommendationCount)}</Descriptions.Item>
            <Descriptions.Item label="blockedCount">{countValue(overview?.blockedCount)}</Descriptions.Item>
            <Descriptions.Item label="generatedAt">{generatedAtText(overview?.generatedAt)}</Descriptions.Item>
            <Descriptions.Item label="traceId">{optionalSafeCode(overview?.traceId)}</Descriptions.Item>
        </Descriptions>
    );
}

function IncidentReplayReviewLatestItem({item}: { item?: IncidentReplayReviewItem | null }) {
    useTranslation('pages');
    if (!item) {
        return <Empty description={t('pages:noLatestreviewitemAnEmptyStateDoesNotMeanReviewEvidenceIsCompleteAcknowledgedEscalatedOrTradable')}/>;
    }
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
            <Descriptions.Item label="latestReviewItem.reviewState">
                <WorkflowStatusTag status={item.reviewState}/>
            </Descriptions.Item>
            <Descriptions.Item label="latestReviewItem.reviewDecision">
                <WorkflowStatusTag status={item.reviewDecision}/>
            </Descriptions.Item>
            <Descriptions.Item label="latestReviewItem.severity">
                <WorkflowStatusTag status={item.severity}/>
            </Descriptions.Item>
            <Descriptions.Item label="latestReviewItem.evidenceFreshness">
                <WorkflowStatusTag status={item.evidenceFreshness}/>
            </Descriptions.Item>
            <Descriptions.Item label="sourceType">{optionalText(item.sourceType)}</Descriptions.Item>
            <Descriptions.Item label="sourceId">{optionalSafeCode(item.sourceId)}</Descriptions.Item>
            <Descriptions.Item label="shadowRunId">{optionalSafeCode(item.shadowRunId)}</Descriptions.Item>
            <Descriptions.Item label="paperRunId">{optionalSafeCode(item.paperRunId)}</Descriptions.Item>
            <Descriptions.Item label="consistencyReportId">{optionalSafeCode(item.consistencyReportId)}</Descriptions.Item>
            <Descriptions.Item label="replayRecordId">{optionalSafeCode(item.replayRecordId)}</Descriptions.Item>
            <Descriptions.Item label="operatorItemId">{optionalSafeCode(item.operatorItemId)}</Descriptions.Item>
            <Descriptions.Item label="latestReviewItem.traceId">{optionalSafeCode(item.traceId)}</Descriptions.Item>
            <Descriptions.Item label="latestReviewItem.generatedAt">{generatedAtText(item.generatedAt)}</Descriptions.Item>
        </Descriptions>
    );
}

function IncidentReplayReviewIssueTables({
                                             blockers,
                                             warnings,
                                         }: {
    blockers: IncidentReplayReviewBlocker[];
    warnings: IncidentReplayReviewWarning[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:blockers')}</Text>
                <Table<IncidentReplayReviewBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentReplayReviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noBlockersListedFixedSafetyBoundariesStillApply')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:warnings2')}</Text>
                <Table<IncidentReplayReviewWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentReplayReviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noWarningsReviewIsNotNecessarilyCompleteAcknowledgedOrClosed')}}
                />
            </div>
        </Space>
    );
}

function IncidentReplayReviewOverviewPanel({query}: {
    query: PanelQueryState<IncidentReplayReviewOverviewResponse>
}) {
    useTranslation('pages');
    const overview = query.data;
    const panelState = overview ? resolveIncidentReplayReviewState(overview) : null;
    const bucketRows = overview ? [
        ...incidentReplayReviewBucketRows('severityBuckets', overview.severityBuckets),
        ...incidentReplayReviewBucketRows('freshnessSummary', overview.freshnessSummary),
    ] : [];

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:incidentReplayReviewOverview')}
        >
            <Space data-testid="incident-replay-review-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyGetApiIncidentsReplayReviewOverviewShowsReviewCountsItemsSeverityFreshnessBlockersWarningsNe')}</Paragraph>
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:interpretationBoundary')}
                    description={t('pages:acknowledgeRecommendedSuggestsManualAcknowledgmentEscalateRecommendedSuggestsManualEscalationCloseou')}
                />
                <IncidentReplayReviewBoundaryBadges overview={overview}/>
                <IncidentReplayReviewBoundaryDriftAlert overview={overview}/>
                <ReadModelEvidenceMetadataSummary
                    metadata={overview?.evidenceMetadata}
                    testId="incident-replay-review-evidence-metadata"
                />
                <IncidentReplayReviewCounts overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToQueryIncidentReplayReviewOverview')}
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                {t('pages:aFailedReviewOverviewIsUnavailableNotAcknowledgedEscalatedClosedAuthorizedAutomaticallyHandledOrExec')}{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description={t('pages:noIncidentReplayReviewOverviewResponseSafetyBoundariesRemainFailClosed')}/>
                ) : (
                    <>
                        {panelState ? (
                            <Alert
                                type={panelState.level}
                                showIcon
                                message={panelState.message}
                                description={panelState.description}
                            />
                        ) : null}
                        {incidentReplayReviewIsEmpty(overview) ? (
                            <Empty description={t('pages:noReviewEvidenceAnEmptyStateDoesNotMeanIncidentClosureAcknowledgedRecommendationsOrTradability')}/>
                        ) : null}
                        <IncidentReplayReviewLatestItem item={overview.latestReviewItem}/>
                        <div>
                            <Text strong>{t('pages:reviewSummaries')}</Text>
                            <Table<IncidentReplayReviewBucketRow>
                                size="small"
                                rowKey={(record) => record.key}
                                columns={incidentReplayReviewBucketColumns}
                                dataSource={bucketRows}
                                pagination={false}
                                scroll={{x: 560}}
                                locale={{emptyText: t('pages:noSeveritybucketsOrFreshnesssummaryBucketStatisticsMustNotBeFabricated')}}
                            />
                        </div>
                        <IncidentReplayReviewIssueTables blockers={overview.blockers} warnings={overview.warnings}/>
                        <Table<IncidentReplayReviewNextStep>
                            size="small"
                            rowKey={(record) => record.code}
                            columns={incidentReplayReviewNextStepColumns}
                            dataSource={overview.nextSteps}
                            pagination={false}
                            scroll={{x: 1000}}
                            locale={{emptyText: t('pages:noNextstepsReviewEscalationClosureOrHandlingCannotBeConsideredComplete')}}
                        />
                        <Table<IncidentReplayReviewEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={incidentReplayReviewEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: t('pages:noEvidenceanchorsEvidenceCannotBeConsideredComplete')}}
                        />
                        <Table<IncidentReplayReviewItem>
                            size="small"
                            rowKey={(record) => record.reviewItemId}
                            columns={incidentReplayReviewItemColumns}
                            dataSource={overview.reviewItems}
                            pagination={false}
                            scroll={{x: 1820}}
                            locale={{emptyText: t('pages:noReviewitemsReviewEntriesMustNotBeFabricated')}}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function evaluationArtifactPreviewBucketRows(
    source: EvaluationArtifactPreviewBucketRow['source'],
    buckets: Record<string, number> | undefined,
): EvaluationArtifactPreviewBucketRow[] {
    return Object.entries(buckets ?? {}).map(([bucket, count]) => ({
        key: `${source}-${bucket}`,
        source,
        bucket,
        count: numberValue(count),
    }));
}

function hasSummaryCount(summary: Record<string, number> | undefined, ...keys: string[]): boolean {
    return keys.some((key) => numberValue(summary?.[key]) > 0);
}

function artifactItemsHaveStatus(
    items: PythonEvaluationArtifactPreviewItem[],
    field: keyof Pick<PythonEvaluationArtifactPreviewItem, 'checksumStatus' | 'artifactFreshness' | 'metricSummaryStatus'>,
    ...statuses: string[]
): boolean {
    const expected = new Set(statuses.map((status) => normalizeStatus(status)));
    return items.some((item) => expected.has(normalizeStatus(item[field])));
}

function evaluationArtifactPreviewIsNoFileBaseline(overview: PythonEvaluationArtifactPreviewOverviewResponse): boolean {
    return countValue(overview.totalArtifactPreviews) === 0
        && overview.artifactPreviews.length === 0
        && !overview.latestArtifactPreview
        && overview.warnings.some((warning) => normalizeStatus(warning.code) === 'NO_ARTIFACT_SOURCE_CONFIGURED');
}

function evaluationArtifactPreviewHasChecksumMissing(overview: PythonEvaluationArtifactPreviewOverviewResponse): boolean {
    return hasSummaryCount(overview.checksumSummary, 'MISSING')
        || artifactItemsHaveStatus(overview.artifactPreviews, 'checksumStatus', 'MISSING');
}

function evaluationArtifactPreviewHasChecksumFailed(overview: PythonEvaluationArtifactPreviewOverviewResponse): boolean {
    return countValue(overview.checksumFailedCount) > 0
        || hasSummaryCount(overview.checksumSummary, 'INVALID')
        || artifactItemsHaveStatus(overview.artifactPreviews, 'checksumStatus', 'INVALID');
}

function evaluationArtifactPreviewHasFakeFixture(overview: PythonEvaluationArtifactPreviewOverviewResponse): boolean {
    return hasSummaryCount(overview.metricSummaryCoverage, 'FAKE_FIXTURE_ONLY')
        || artifactItemsHaveStatus(overview.artifactPreviews, 'metricSummaryStatus', 'FAKE_FIXTURE_ONLY')
        || overview.warnings.some((warning) => normalizeStatus(warning.code).includes('FAKE_FIXTURE'));
}

function evaluationArtifactPreviewHasStaleArtifact(overview: PythonEvaluationArtifactPreviewOverviewResponse): boolean {
    return countValue(overview.staleArtifactCount) > 0
        || artifactItemsHaveStatus(overview.artifactPreviews, 'artifactFreshness', 'STALE');
}

function evaluationArtifactPreviewHasUnknown(overview: PythonEvaluationArtifactPreviewOverviewResponse): boolean {
    return hasSummaryCount(overview.checksumSummary, 'UNKNOWN')
        || hasSummaryCount(overview.metricSummaryCoverage, 'UNKNOWN')
        || artifactItemsHaveStatus(overview.artifactPreviews, 'checksumStatus', 'UNKNOWN')
        || artifactItemsHaveStatus(overview.artifactPreviews, 'artifactFreshness', 'UNKNOWN')
        || artifactItemsHaveStatus(overview.artifactPreviews, 'metricSummaryStatus', 'UNKNOWN');
}

function resolveEvaluationArtifactPreviewState(
    overview: PythonEvaluationArtifactPreviewOverviewResponse,
): OverviewPanelState {
    if (evaluationArtifactPreviewHasChecksumFailed(overview)) {
        return {
            level: 'error',
            message: t('pages:evaluationArtifactPreviewChecksumFailed'),
            description: t('pages:invalidOrFailedChecksumsMeanFailedArtifactValidationAndRemainFailClosedValidChecksumsDoNotEstablishS'),
        };
    }
    if (evaluationArtifactPreviewIsNoFileBaseline(overview)) {
        return {
            level: 'warning',
            message: t('pages:noArtifactSourceConfigured'),
            description: t('pages:theEvaluationArtifactPreviewUsesANoFileBaselineNoFileOrManifestReadsPathsUploadsPythonExecutionOrDat'),
        };
    }
    if (evaluationArtifactPreviewHasChecksumMissing(overview)) {
        return {
            level: 'warning',
            message: t('pages:evaluationArtifactPreviewChecksumMissing'),
            description: t('pages:aMissingChecksumMeansInsufficientIntegrityEvidenceTheArtifactIsNotShownAsValidPublishableOrExecutabl'),
        };
    }
    if (evaluationArtifactPreviewHasFakeFixture(overview)) {
        return {
            level: 'warning',
            message: t('pages:evaluationArtifactPreviewContainsAFakeFixture'),
            description: t('pages:fakeFixtureOnlyIsATestFixtureNotRealStrategyPerformanceReturnsOrLiveExecutionReadiness'),
        };
    }
    if (evaluationArtifactPreviewHasStaleArtifact(overview)) {
        return {
            level: 'warning',
            message: t('pages:evaluationArtifactPreviewContainsAStaleArtifact'),
            description: t('pages:staleMeansInsufficientArtifactFreshnessCompleteControlledSourcesAndReviewBeforeConsideringReadiness'),
        };
    }
    if (evaluationArtifactPreviewHasUnknown(overview)) {
        return {
            level: 'warning',
            message: t('pages:evaluationArtifactPreviewHasAnUnknownFailClosedState'),
            description: t('pages:unknownNotCheckedMeanSchemaChecksumOrMetricCoverageCannotBeConfirmedFailClosedHandlingApplies'),
        };
    }
    return {
        level: 'info',
        message: t('pages:evaluationArtifactPreviewLoaded'),
        description: t('pages:pythonOfflineArtifactDiagnosticPreviewOnlyNotMlReadinessLiveExecutionReadinessTradingAuthorizationOr'),
    };
}

function EvaluationArtifactPreviewBoundaryBadges({overview}: {
    overview?: PythonEvaluationArtifactPreviewOverviewResponse
}) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataRemainsFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t('pages:livedisabledTrueArtifactPreviewDoesNotEstablishLiveAvailabilityValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t('pages:realproviderimplementedFalseThisOverviewDoesNotCallRealProvidersValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t('pages:privatetradingimplementedFalseThisOverviewOffersNoOrdersCancellationsTransfersOrWithdrawalsValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:pythonArtifactPreviewIsDiagnosticOnly')}
                tooltip={t('pages:diagnosticonlyTruePreviewsShowOfflineDiagnosticMaterialsWithoutImportExecutionOrPersistenceValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t('pages:nottradingauthorizationTrueValidChecksumsMetricSummariesAndArtifactPreviewsDoNotGrantTradingAuthoriz', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:pythonMlReadyNo')}
                tooltip={t('pages:pythonmlreadyFalsePythonMlReadinessIsNotEstablishedValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:pythonLiveExecutionReadyNo')}
                tooltip={t('pages:pythonliveexecutionreadyFalsePythonLiveExecutionReadinessIsNotEstablishedValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t('pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue12', {value1: pending})}
            />
        </Space>
    );
}

function EvaluationArtifactPreviewBoundaryDriftAlert({overview}: {
    overview?: PythonEvaluationArtifactPreviewOverviewResponse
}) {
    useTranslation('pages');
    if (!overview) {
        return null;
    }
    const overviewDrift = !overview.diagnosticOnly
        || !overview.noSideEffect
        || !overview.notTradingAuthorization
        || !overview.liveDisabled
        || overview.realProviderImplemented
        || overview.privateTradingImplemented
        || overview.aiDhRuntimeIntegrated
        || overview.pythonMlReady
        || overview.pythonLiveExecutionReady;
    const itemDrift = overview.artifactPreviews.some((item) => !item.diagnosticOnly
        || !item.noSideEffect
        || !item.notTradingAuthorization
        || item.liveExecutionReady
        || item.pythonMlReady
        || item.pythonLiveExecutionReady);

    return overviewDrift || itemDrift ? (
        <Alert
            type="error"
            showIcon
            message={t('pages:evaluationArtifactPreviewBoundaryFlagsConflictWithTheSafetyBaseline')}
            description={t('pages:theResponseRemainsFailClosedUnexpectedFlagsDoNotEstablishMlReadinessLiveExecutionReadinessTradingAut')}
        />
    ) : null;
}

function EvaluationArtifactPreviewCounts({overview}: {
    overview?: PythonEvaluationArtifactPreviewOverviewResponse
}) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="totalArtifactPreviews">{countValue(overview?.totalArtifactPreviews)}</Descriptions.Item>
            <Descriptions.Item label="validArtifactCount">{countValue(overview?.validArtifactCount)}</Descriptions.Item>
            <Descriptions.Item label="invalidArtifactCount">{countValue(overview?.invalidArtifactCount)}</Descriptions.Item>
            <Descriptions.Item label="staleArtifactCount">{countValue(overview?.staleArtifactCount)}</Descriptions.Item>
            <Descriptions.Item label="checksumFailedCount">{countValue(overview?.checksumFailedCount)}</Descriptions.Item>
            <Descriptions.Item label="generatedAt">{generatedAtText(overview?.generatedAt)}</Descriptions.Item>
            <Descriptions.Item label="traceId">{optionalSafeCode(overview?.traceId)}</Descriptions.Item>
        </Descriptions>
    );
}

function EvaluationArtifactPreviewReadinessFlags({overview}: {
    overview?: PythonEvaluationArtifactPreviewOverviewResponse
}) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="diagnosticOnly">
                <Tag color={overview?.diagnosticOnly === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.diagnosticOnly))}{t('pages:readOnlyDiagnostics')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="noSideEffect">
                <Tag color={overview?.noSideEffect === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.noSideEffect))}{t('pages:noSideEffects')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="notTradingAuthorization">
                <Tag color={overview?.notTradingAuthorization === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.notTradingAuthorization))}{t('pages:noTradingAuthorization')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="liveDisabled">
                <Tag color={overview?.liveDisabled === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.liveDisabled))}{t('pages:liveDisabled4')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="pythonMlReady">
                <Tag color={overview?.pythonMlReady ? 'error' : 'default'}>
                    {String(Boolean(overview?.pythonMlReady))}{t('pages:pythonMlReadyNo2')}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="pythonLiveExecutionReady">
                <Tag color={overview?.pythonLiveExecutionReady ? 'error' : 'default'}>
                    {String(Boolean(overview?.pythonLiveExecutionReady))}{t('pages:pythonLiveExecutionReadyNo2')}</Tag>
            </Descriptions.Item>
        </Descriptions>
    );
}

function EvaluationArtifactPreviewLatestItem({item}: {
    item?: PythonEvaluationArtifactPreviewItem | null
}) {
    useTranslation('pages');
    if (!item) {
        return <Empty description={t('pages:noArtifactSourceIsConfiguredTheNoFileBaselineReadsNoArtifactFilesExecutesNoPythonAndImportsNothingIn')}/>;
    }
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
            <Descriptions.Item label="latestArtifactPreview.checksumStatus">
                <WorkflowStatusTag status={item.checksumStatus}/>
            </Descriptions.Item>
            <Descriptions.Item label="latestArtifactPreview.artifactFreshness">
                <WorkflowStatusTag status={item.artifactFreshness}/>
            </Descriptions.Item>
            <Descriptions.Item label="latestArtifactPreview.metricSummaryStatus">
                <WorkflowStatusTag status={item.metricSummaryStatus}/>
            </Descriptions.Item>
            <Descriptions.Item label="artifactPreviewId">{optionalSafeCode(item.artifactPreviewId)}</Descriptions.Item>
            <Descriptions.Item label="artifactId">{optionalSafeCode(item.artifactId)}</Descriptions.Item>
            <Descriptions.Item label="strategyVersionId">{optionalSafeCode(item.strategyVersionId)}</Descriptions.Item>
            <Descriptions.Item label="datasetId">{optionalSafeCode(item.datasetId)}</Descriptions.Item>
            <Descriptions.Item label="parameterSetId">{optionalSafeCode(item.parameterSetId)}</Descriptions.Item>
            <Descriptions.Item label="schemaVersion">{optionalSafeCode(item.schemaVersion)}</Descriptions.Item>
            <Descriptions.Item label="source">{optionalSafeCode(item.source)}</Descriptions.Item>
            <Descriptions.Item label="latestArtifactPreview.traceId">{optionalSafeCode(item.traceId)}</Descriptions.Item>
            <Descriptions.Item label="latestArtifactPreview.generatedAt">{generatedAtText(item.generatedAt)}</Descriptions.Item>
        </Descriptions>
    );
}

function EvaluationArtifactPreviewIssueTables({
                                                  blockers,
                                                  warnings,
                                              }: {
    blockers: EvaluationArtifactPreviewBlocker[];
    warnings: EvaluationArtifactPreviewWarning[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:blockers')}</Text>
                <Table<EvaluationArtifactPreviewBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={evaluationArtifactPreviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 960}}
                    locale={{emptyText: t('pages:noBlockersListedFixedSafetyBoundariesStillApply')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:warnings2')}</Text>
                <Table<EvaluationArtifactPreviewWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={evaluationArtifactPreviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 960}}
                    locale={{emptyText: t('pages:noWarningsThisDoesNotMeanAnArtifactSourceIsConfiguredOrPythonExecutionIsAvailable')}}
                />
            </div>
        </Space>
    );
}

function EvaluationArtifactPreviewOverviewPanel({query}: {
    query: PanelQueryState<PythonEvaluationArtifactPreviewOverviewResponse>
}) {
    useTranslation('pages');
    const overview = query.data;
    const panelState = overview ? resolveEvaluationArtifactPreviewState(overview) : null;
    const bucketRows = overview ? [
        ...evaluationArtifactPreviewBucketRows('schemaVersionSummary', overview.schemaVersionSummary),
        ...evaluationArtifactPreviewBucketRows('checksumSummary', overview.checksumSummary),
        ...evaluationArtifactPreviewBucketRows('metricSummaryCoverage', overview.metricSummaryCoverage),
    ] : [];

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:pythonEvaluationArtifactPreviewNoFileBaseline')}
        >
            <Space data-testid="evaluation-artifact-preview-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyGetApiStrategyValidationEvaluationArtifactsPreviewOverviewShowsTheNoFileBaselinePreviewCount')}</Paragraph>
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:interpretationBoundary')}
                    description={t('pages:colorsShowDiagnosticsNotProfitOrMarketDirectionValidChecksumsIndicatePayloadIntegrityNotStrategyVali')}
                />
                <EvaluationArtifactPreviewBoundaryBadges overview={overview}/>
                <EvaluationArtifactPreviewBoundaryDriftAlert overview={overview}/>
                <EvaluationArtifactPreviewCounts overview={overview}/>
                <ReadModelEvidenceMetadataSummary
                    metadata={overview?.evidenceMetadata}
                    testId="evaluation-artifact-preview-evidence-metadata"
                />
                <EvaluationArtifactPreviewReadinessFlags overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToQueryEvaluationArtifactPreviewOverview')}
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                {t('pages:aFailedArtifactOverviewIsUnavailableNotAConfiguredSourcePythonExecutionMlReadinessLiveExecutionReadi')}{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description={t('pages:noEvaluationArtifactPreviewOverviewResponseSafetyBoundariesRemainFailClosed')}/>
                ) : (
                    <>
                        {panelState ? (
                            <Alert
                                type={panelState.level}
                                showIcon
                                message={panelState.message}
                                description={panelState.description}
                            />
                        ) : null}
                        {evaluationArtifactPreviewHasChecksumMissing(overview) ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:checksumMissing')}
                                description={t('pages:missingChecksumsCannotIndicateArtifactOrStrategyValidityMlReadinessOrTradingAuthorization')}
                            />
                        ) : null}
                        {evaluationArtifactPreviewHasChecksumFailed(overview) ? (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:checksumInvalidFailed')}
                                description={t('pages:checksumFailuresRemainFailClosedUploadsImportsExecutionAndTradingAreNotPermitted')}
                            />
                        ) : null}
                        {evaluationArtifactPreviewHasFakeFixture(overview) ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="FAKE_FIXTURE_ONLY"
                                description={t('pages:fakeFixtureOnlyIsATestFixtureNotRealStrategyPerformanceReturnsOrLiveExecutionReadiness')}
                            />
                        ) : null}
                        {evaluationArtifactPreviewHasUnknown(overview) ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:unknownFailClosed')}
                                description={t('pages:unknownNotCheckedMeanSourceChecksumOrMetricCoverageCannotBeConfirmedThePageRemainsFailClosed')}
                            />
                        ) : null}
                        <EvaluationArtifactPreviewLatestItem item={overview.latestArtifactPreview}/>
                        <div>
                            <Text strong>{t('pages:schemaChecksumMetricCoverage')}</Text>
                            <Table<EvaluationArtifactPreviewBucketRow>
                                size="small"
                                rowKey={(record) => record.key}
                                columns={evaluationArtifactPreviewBucketColumns}
                                dataSource={bucketRows}
                                pagination={false}
                                scroll={{x: 640}}
                                locale={{emptyText: t('pages:noCoverageSummarySchemaChecksumOrMetricStatusMustNotBeFabricated')}}
                            />
                        </div>
                        <EvaluationArtifactPreviewIssueTables
                            blockers={overview.blockers}
                            warnings={overview.warnings}
                        />
                        <Table<EvaluationArtifactPreviewNextStep>
                            size="small"
                            rowKey={(record) => record.code}
                            columns={evaluationArtifactPreviewNextStepColumns}
                            dataSource={overview.nextSteps}
                            pagination={false}
                            scroll={{x: 1040}}
                            locale={{emptyText: t('pages:noNextstepsThisDoesNotMeanAConfiguredArtifactSourcePythonExecutionOrCompletion')}}
                        />
                        <Table<EvaluationArtifactPreviewEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={evaluationArtifactPreviewEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: t('pages:noEvidenceanchorsEvidenceCannotBeConsideredComplete')}}
                        />
                        <Table<PythonEvaluationArtifactPreviewItem>
                            size="small"
                            rowKey={(record) => record.artifactPreviewId}
                            columns={evaluationArtifactPreviewItemColumns}
                            dataSource={overview.artifactPreviews}
                            pagination={false}
                            scroll={{x: 2300}}
                            locale={{emptyText: t('pages:noFileBaselineNoArtifactSourceConfiguredNoArtifactFileReadsNoPythonExecutionAndNoDatabaseImport')}}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function normalizeIncidentSeverity(severity: IncidentReplaySeverity | null | undefined): string {
    return normalizeStatus(severity);
}

function incidentSeverityPresentation(severity: IncidentReplaySeverity | null | undefined): {
    alertType: 'info' | 'warning' | 'error';
    color: string;
    label: string;
    message: string;
    description: string;
} {
    const normalized = normalizeIncidentSeverity(severity);
    switch (normalized) {
        case 'CRITICAL':
            return {
                alertType: 'error',
                color: 'error',
                label: t('pages:criticalCriticalDiagnosticPriority'),
                message: t('pages:incidentReplayOverviewCriticalDiagnosticPriority'),
                description: t('pages:criticalPrioritizesManualLocalEvidenceReviewThePageDoesNotHandleIssuesAutomaticallyAuthorizeTradingO'),
            };
        case 'HIGH':
            return {
                alertType: 'error',
                color: 'error',
                label: t('pages:highHighDiagnosticPriority'),
                message: t('pages:incidentReplayOverviewHighDiagnosticPriority'),
                description: t('pages:highMeansDiagnosticEvidenceNeedsPromptReviewNotAutomaticRecoveryExecutionOrTradingAuthorization'),
            };
        case 'WARNING':
            return {
                alertType: 'warning',
                color: 'warning',
                label: t('pages:warningDiagnosticWarning'),
                message: t('pages:incidentReplayOverviewWarning'),
                description: t('pages:warningIndicatesDiagnosticSignalsRequiringReviewNotMarketDirectionReturnsOrAutomaticHandling'),
            };
        case 'NONE':
            return {
                alertType: 'info',
                color: 'default',
                label: t('pages:noneNoCurrentDiagnosticPriority'),
                message: t('pages:incidentReplayOverviewNoDiagnosticPriority'),
                description: t('pages:noneMeansNoIncidentLikePriorityWasProvidedFixedSafetyBoundariesStillApply'),
            };
        case 'INFO':
            return {
                alertType: 'info',
                color: 'processing',
                label: t('pages:infoDiagnosticInformation'),
                message: t('pages:incidentReplayOverviewInfo'),
                description: t('pages:infoIndicatesGeneralDiagnosticsNotAPassReturnsTradingAuthorizationOrLiveReadiness'),
            };
        default:
            return {
                alertType: 'warning',
                color: 'warning',
                label: t('pages:value1UnknownDiagnosticPriority', {value1: normalized}),
                message: t('pages:incidentReplayOverviewUnknownDiagnosticPriority'),
                description: t('pages:unknownSeverityRemainsFailClosedAndRequiresManualConfirmationOfBackendFactsAndBoundaryMeanings'),
            };
    }
}

function IncidentSeverityTag({severity}: { severity?: IncidentReplaySeverity | null }) {
    useTranslation('pages');
    const presentation = incidentSeverityPresentation(severity);
    return (
        <Tooltip title={t('pages:severityIndicatesDiagnosticPriorityOnlyNotAutomaticHandlingTradingAuthorizationOrRealTradingReadines')}>
            <Tag color={presentation.color}>{presentation.label}</Tag>
        </Tooltip>
    );
}

function incidentReplayIsEmpty(overview: IncidentReplayOverviewResponse): boolean {
    return countValue(overview.totalEvidenceItems) === 0
        && countValue(overview.shadowEventCount) === 0
        && countValue(overview.consistencyDivergenceCount) === 0
        && countValue(overview.paperAlertCount) === 0
        && countValue(overview.recoveryEventCount) === 0
        && countValue(overview.replayEventCount) === 0
        && overview.latestEvidence.length === 0
        && overview.blockers.length === 0
        && overview.warnings.length === 0
        && overview.nextSteps.length === 0
        && overview.evidenceAnchors.length === 0;
}

function incidentReplayHasSourceUnavailable(overview: IncidentReplayOverviewResponse): boolean {
    const sourceSignals = [
        ...overview.latestEvidence.map((item) => `${item.evidenceType} ${item.sourceStatus ?? ''} ${item.summary ?? ''}`),
        ...overview.blockers.map((item) => `${item.code} ${item.severity} ${item.message}`),
        ...overview.warnings.map((item) => `${item.code} ${item.severity} ${item.message}`),
    ];
    return sourceSignals.some((value) => /SOURCE_UNAVAILABLE|UNAVAILABLE|NOT_AVAILABLE|NO_SOURCE|SOURCE_MISSING/i.test(value));
}

function incidentReplayHasPartialData(overview: IncidentReplayOverviewResponse): boolean {
    if (incidentReplayIsEmpty(overview)) {
        return false;
    }
    return overview.latestEvidence.length === 0
        || overview.evidenceAnchors.length === 0
        || normalizeIncidentSeverity(overview.incidentSeverity) === 'UNKNOWN'
        || overview.latestEvidence.some((item) => /PARTIAL|INCOMPLETE|STALE/i.test(item.sourceStatus ?? ''));
}

function IncidentReplayBoundaryBadges({overview}: { overview?: IncidentReplayOverviewResponse }) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataRemainsFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t('pages:livedisabledTrueIncidentReplayOverviewDoesNotEstablishRealTradingReadinessValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t('pages:realproviderimplementedFalseThisPanelDoesNotCallRealProvidersValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t('pages:privatetradingimplementedFalseThisPanelOffersNoOrdersCancellationsTransfersOrWithdrawalsValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:incidentReplayIsDiagnosticOnly')}
                tooltip={t('pages:diagnosticonlyTrueAggregatesLocalDiagnosticEvidenceOnlyWithoutCreatingIncidentsOrStartingReplayValue', {value1: pending})}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t('pages:nottradingauthorizationTrueHighCriticalDoNotGrantTradingAuthorizationValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t('pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue12', {value1: pending})}
            />
        </Space>
    );
}

function IncidentReplayBoundaryDriftAlert({overview}: { overview?: IncidentReplayOverviewResponse }) {
    useTranslation('pages');
    if (!overview) {
        return null;
    }
    const drift = !overview.diagnosticOnly
        || !overview.noSideEffect
        || !overview.notTradingAuthorization
        || !overview.liveDisabled
        || overview.realProviderImplemented
        || overview.privateTradingImplemented
        || overview.aiDhRuntimeIntegrated;

    return drift ? (
        <Alert
            type="error"
            showIcon
            message={t('pages:incidentReplayBoundaryFlagsConflictWithTheSafetyBaseline')}
            description={t('pages:theResponseRemainsFailClosedUnexpectedFlagsDoNotMeanExecutableTradableOrLiveReady')}
        />
    ) : null;
}

function IncidentReplayCounts({overview}: { overview?: IncidentReplayOverviewResponse }) {
    useTranslation('pages');
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="incidentSeverity">
                <IncidentSeverityTag severity={overview?.incidentSeverity}/>
            </Descriptions.Item>
            <Descriptions.Item label="totalEvidenceItems">
                {countValue(overview?.totalEvidenceItems)}
            </Descriptions.Item>
            <Descriptions.Item label="shadowEventCount">
                {countValue(overview?.shadowEventCount)}
            </Descriptions.Item>
            <Descriptions.Item label="consistencyDivergenceCount">
                {countValue(overview?.consistencyDivergenceCount)}
            </Descriptions.Item>
            <Descriptions.Item label="paperAlertCount">
                {countValue(overview?.paperAlertCount)}
            </Descriptions.Item>
            <Descriptions.Item label="recoveryEventCount">
                {countValue(overview?.recoveryEventCount)}
            </Descriptions.Item>
            <Descriptions.Item label="replayEventCount">
                {countValue(overview?.replayEventCount)}
            </Descriptions.Item>
            <Descriptions.Item label="generatedAt">
                {generatedAtText(overview?.generatedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="traceId">
                {optionalSafeCode(overview?.traceId)}
            </Descriptions.Item>
        </Descriptions>
    );
}

function IncidentReplayIssueTables({
                                       blockers,
                                       warnings,
                                   }: {
    blockers: IncidentReplayBlocker[];
    warnings: IncidentReplayWarning[];
}) {
    useTranslation('pages');
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>{t('pages:blockers2')}</Text>
                <Table<IncidentReplayBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentOverviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noBlockersListedFixedSafetyBoundariesStillApply')}}
                />
            </div>
            <div>
                <Text strong>{t('pages:warnings3')}</Text>
                <Table<IncidentReplayWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentOverviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: t('pages:noWarningsTheDiagnosticChainIsNotNecessarilyComplete')}}
                />
            </div>
        </Space>
    );
}

function IncidentReplayOverviewPanel({query}: { query: PanelQueryState<IncidentReplayOverviewResponse> }) {
    useTranslation('pages');
    const overview = query.data;
    const severity = overview ? incidentSeverityPresentation(overview.incidentSeverity) : null;
    const sourceUnavailable = overview ? incidentReplayHasSourceUnavailable(overview) : false;
    const partialData = overview ? incidentReplayHasPartialData(overview) : false;

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:incidentReplayOverview')}
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    {t('pages:refreshIncidentReplay')}</Button>
            )}
        >
            <Space data-testid="incident-replay-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyGetApiIncidentsReplayOverviewAggregatesLocalShadowConsistencyPaperAlertRecoveryAndReplayDiag')}</Paragraph>
                <IncidentReplayBoundaryBadges overview={overview}/>
                <IncidentReplayBoundaryDriftAlert overview={overview}/>
                <IncidentReplayCounts overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToQueryIncidentReplayOverview')}
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                {t('pages:aFailedOverviewMeansTheSourceIsUnavailableNotPassedAuthorizedAutomaticallyHandledOrExecutable')}{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description={t('pages:noIncidentReplayOverviewResponseSafetyBoundariesRemainFailClosed')}/>
                ) : (
                    <>
                        {sourceUnavailable ? (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:sourceUnavailable')}
                                description={t('pages:theOverviewReportsUnavailableOrMissingSourcesOnlyReturnedEvidenceIsShownCountsLatestevidenceAndNexts')}
                            />
                        ) : partialData ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:partialData')}
                                description={t('pages:whenLatestevidenceAnchorsSourcestatusOrSeverityIsIncompleteOnlyAvailableFactsAreShownMissingDataIsNo')}
                            />
                        ) : severity ? (
                            <Alert
                                type={severity.alertType}
                                showIcon
                                message={severity.message}
                                description={severity.description}
                            />
                        ) : null}
                        {incidentReplayIsEmpty(overview) ? (
                            <Empty description={t('pages:noIncidentReplayEvidenceAnEmptyStateDoesNotMeanResolutionOrTradability')}/>
                        ) : null}
                        <Table<IncidentReplayLatestEvidence>
                            size="small"
                            rowKey={(record) => `${record.evidenceType}-${record.sourceId ?? 'none'}-${record.traceId ?? record.occurredAt ?? 'none'}`}
                            columns={incidentLatestEvidenceColumns}
                            dataSource={overview.latestEvidence}
                            pagination={false}
                            scroll={{x: 1080}}
                            locale={{emptyText: t('pages:noLatestevidenceLatestEvidenceMustNotBeFabricated')}}
                        />
                        <IncidentReplayIssueTables blockers={overview.blockers} warnings={overview.warnings}/>
                        <Table<IncidentReplayNextStep>
                            size="small"
                            rowKey={(record) => record.code}
                            columns={incidentNextStepColumns}
                            dataSource={overview.nextSteps}
                            pagination={false}
                            scroll={{x: 1000}}
                            locale={{emptyText: t('pages:noNextstepsHandlingCannotBeConsideredComplete')}}
                        />
                        <Table<IncidentReplayEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.checksum ?? 'none'}`}
                            columns={incidentEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1030}}
                            locale={{emptyText: t('pages:noEvidenceanchorsEvidenceCannotBeConsideredComplete')}}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function ValidationOperationsBoundaryStrip() {
    useTranslation('pages');
    return (
        <Space data-testid="validation-operations-boundary-strip" size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED"
                tooltip={t('pages:liveIsDisabledThisWorkbenchDoesNotIndicateRealTradingReadinessApprovalOrCapability')}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented3')}
                tooltip={t('pages:realProvidersAreNotImplementedThisPageDoesNotCallRealExchanges')}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented2')}
                tooltip={t('pages:privateTradingIsNotImplementedNoOrdersCancellationsTransfersWithdrawalsOrPrivateEndpointsAreProvided')}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:notTradingAuthorization3')}
                tooltip={t('pages:validationConsistencyReviewAndArtifactPreviewDoNotGrantTradingAuthorization')}
            />
            <BoundaryBadge
                label={t('pages:pythonMlReadyNo3')}
                tooltip={t('pages:pythonArtifactPreviewDoesNotMeanPythonMlReadiness')}
            />
            <BoundaryBadge
                label={t('pages:pythonLiveExecutionReadyNo3')}
                tooltip={t('pages:pythonArtifactPreviewDoesNotMeanLiveExecutionReadiness')}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated2')}
                tooltip={t('pages:aiRemainsNotStartedDhRuntimeRemainsNotIntegrated')}
            />
        </Space>
    );
}

function ValidationOperationsTopSummary({queries}: { queries: ValidationOperationsQueryBundle }) {
    const {i18n: pageI18n} = useTranslation('pages');
    const rows = useMemo(
        () => validationOperationsSummaryRows(
            queries.strategyOverview.data,
            queries.shadowWorkflow.data,
            queries.consistencyEvidence.data,
            queries.incidentReplayReview.data,
            queries.artifactPreview.data,
        ),
        [
            queries.strategyOverview.data,
            queries.shadowWorkflow.data,
            queries.consistencyEvidence.data,
            queries.incidentReplayReview.data,
            queries.artifactPreview.data,
        , pageI18n.resolvedLanguage],
    );

    return (
        <div data-testid="validation-operations-top-summary">
            <Text strong>{t('pages:validationOperationsSummary')}</Text>
            <Table<ValidationOperationsSummaryRow>
                size="small"
                rowKey={(record) => record.key}
                columns={validationOperationsSummaryColumns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 1450}}
                locale={{emptyText: t('pages:noSummaryValidationOperationsCannotBeConsideredComplete')}}
            />
        </div>
    );
}

function ValidationOperationsEvidenceMatrix({queries}: { queries: ValidationOperationsQueryBundle }) {
    const {i18n: pageI18n} = useTranslation('pages');
    const rows = useMemo(
        () => validationOperationsEvidenceRows(
            queries.strategyOverview.data,
            queries.shadowWorkflow.data,
            queries.consistencyEvidence.data,
            queries.incidentReplayReview.data,
            queries.artifactPreview.data,
        ),
        [
            queries.strategyOverview.data,
            queries.shadowWorkflow.data,
            queries.consistencyEvidence.data,
            queries.incidentReplayReview.data,
            queries.artifactPreview.data,
        , pageI18n.resolvedLanguage],
    );

    return (
        <div data-testid="validation-operations-evidence-matrix">
            <Text strong>{t('pages:evidenceMatrix')}</Text>
            <Table<ValidationOperationsEvidenceRow>
                size="small"
                rowKey={(record) => record.key}
                columns={validationOperationsEvidenceColumns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 1150}}
                locale={{emptyText: t('pages:noEvidenceMatrixEvidenceCannotBeConsideredComplete')}}
            />
        </div>
    );
}

function ValidationOperationsOperatorQueuePreview({queries}: { queries: ValidationOperationsQueryBundle }) {
    const {i18n: pageI18n} = useTranslation('pages');
    const rows = useMemo(
        () => validationOperationsOperatorQueueRows(
            queries.shadowWorkflow.data,
            queries.incidentReplayReview.data,
        ),
        [queries.shadowWorkflow.data, queries.incidentReplayReview.data, pageI18n.resolvedLanguage],
    );

    return (
        <div data-testid="validation-operations-operator-queue">
            <Text strong>{t('pages:operatorReviewQueuePreview')}</Text>
            <Table<ValidationOperationsOperatorQueueRow>
                size="small"
                rowKey={(record) => record.key}
                columns={validationOperationsOperatorQueueColumns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 1500}}
                locale={{emptyText: t('pages:noOperatorOrReviewItemsThisDoesNotMeanAcknowledgmentEscalationOrClosure')}}
            />
        </div>
    );
}

function ValidationOperationsWorkbench({queries}: { queries: ValidationOperationsQueryBundle }) {
    useTranslation('pages');
    const isLoading = queries.strategyOverview.isLoading
        || queries.shadowWorkflow.isLoading
        || queries.consistencyEvidence.isLoading
        || queries.incidentReplayReview.isLoading
        || queries.artifactPreview.isLoading;
    const isFetching = queries.strategyOverview.isFetching
        || queries.shadowWorkflow.isFetching
        || queries.consistencyEvidence.isFetching
        || queries.incidentReplayReview.isFetching
        || queries.artifactPreview.isFetching;
    const hasError = queries.strategyOverview.isError
        || queries.shadowWorkflow.isError
        || queries.consistencyEvidence.isError
        || queries.incidentReplayReview.isError
        || queries.artifactPreview.isError;
    const hasPartialData = !queries.strategyOverview.data
        || !queries.shadowWorkflow.data
        || !queries.consistencyEvidence.data
        || !queries.incidentReplayReview.data
        || !queries.artifactPreview.data;

    function refetchWorkbench() {
        queries.strategyOverview.refetch();
        queries.shadowWorkflow.refetch();
        queries.consistencyEvidence.refetch();
        queries.incidentReplayReview.refetch();
        queries.artifactPreview.refetch();
    }

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:validationOperationsWorkbench')}
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={isFetching} onClick={refetchWorkbench}>
                    {t('pages:refreshWorkbench')}</Button>
            )}
        >
            <Space data-testid="validation-operations-workbench" direction="vertical" size={14}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:aggregatesReadOnlyShadowWorkflowConsistencyEvidenceIncidentReplayReviewAndArtifactPreviewDiagnostics')}</Paragraph>
                <ValidationOperationsBoundaryStrip/>
                {hasError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:workbenchReadOnlyDataFailedToLoad')}
                        description={t('pages:failedTracksRemainBlockedOrUnknownAndFailClosedMissingResponsesDoNotMeanAPassAcknowledgmentOrTrading')}
                    />
                ) : null}
                {isLoading ? <Skeleton active paragraph={{rows: 8}}/> : null}
                {!isLoading && hasPartialData ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:partialData')}
                        description={t('pages:whenAnyValidationOrShadowOverviewIsMissingOnlyReturnedFactsAreShownEvidenceReviewDecisionsAndArtifac')}
                    />
                ) : null}
                <ValidationOperationsTopSummary queries={queries}/>
                <ValidationOperationsEvidenceMatrix queries={queries}/>
                <ValidationOperationsOperatorQueuePreview queries={queries}/>
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:detailSectionsRetained')}
                    description={t('pages:readOnlyShadowAndValidationPanelsBelowRetainEachTrackSOriginalSummaryBlockersWarningsNextstepsTracei')}
                />
            </Space>
        </Card>
    );
}

function ValidationOperationsDetailSections({children}: { children: ReactNode }) {
    useTranslation('pages');
    return (
        <Space data-testid="validation-operations-detail-sections" direction="vertical" size={16}
               style={{display: 'flex'}}>
            <Alert
                type="info"
                showIcon
                message={t('pages:readOnlyDetailSections')}
                description={t('pages:panelsRetainReadOnlyDiagnosticSemanticsTheSummaryPrioritizesReviewsDetailsExpandTheEvidence')}
            />
            {children}
        </Space>
    );
}

function StrategyValidationShadowWorkbench({queries}: { queries: WorkbenchQueryBundle }) {
    const {i18n: pageI18n} = useTranslation('pages');
    const strategyOverview = queries.strategyOverview.data;
    const shadowOverview = queries.shadowOverview.data;
    const drilldown = queries.drilldown.data;
    const isLoading = queries.strategyOverview.isLoading || queries.shadowOverview.isLoading || queries.drilldown.isLoading;
    const hasError = queries.strategyOverview.isError || queries.shadowOverview.isError || queries.drilldown.isError;
    const hasPartialData = !strategyOverview || !shadowOverview || !queries.shadowRunId || !drilldown;
    const signalRows = useMemo(
        () => workbenchSignalRows(strategyOverview, shadowOverview, drilldown),
        [strategyOverview, shadowOverview, drilldown, pageI18n.resolvedLanguage],
    );
    const nextStepRows = useMemo(
        () => workbenchNextStepRows(strategyOverview, shadowOverview, drilldown),
        [strategyOverview, shadowOverview, drilldown, pageI18n.resolvedLanguage],
    );
    const evidenceRows = useMemo(
        () => workbenchEvidenceRows(strategyOverview, shadowOverview, drilldown),
        [strategyOverview, shadowOverview, drilldown, pageI18n.resolvedLanguage],
    );

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:strategyValidationShadowWorkbench')}
            extra={(
                <Space size={8} wrap>
                    {queries.shadowRunId ? (
                        <Link to={`/strategies/shadow-runs/${queries.shadowRunId}`}>
                            <Button size="small">{t('pages:viewShadowRunDetails')}</Button>
                        </Link>
                    ) : null}
                    <Button
                        size="small"
                        icon={<ReloadOutlined/>}
                        loading={queries.strategyOverview.isFetching || queries.shadowOverview.isFetching || queries.drilldown.isFetching}
                        onClick={() => {
                            queries.strategyOverview.refetch();
                            queries.shadowOverview.refetch();
                            if (queries.shadowRunId) {
                                queries.drilldown.refetch();
                            }
                        }}
                    >
                        {t('pages:refreshWorkbench')}</Button>
                </Space>
            )}
        >
            <Space data-testid="strategy-validation-shadow-workbench" direction="vertical" size={14}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    {t('pages:readOnlyOperationalViewOfStrategyValidationShadowRunsAndPaperVersusShadowDrilldownNoNewRoutesRunners')}</Paragraph>
                <Space size={[8, 8]} wrap>
                    <BoundaryBadge
                        color="error"
                        label={t('pages:liveDisabled')}
                        tooltip={t('pages:liveRemainsDisabledThisWorkbenchDoesNotEstablishLiveReadinessOrAvailability')}
                    />
                    <BoundaryBadge
                        label={t('pages:realProviderNotImplemented')}
                        tooltip={t('pages:realProvidersAreNotImplementedThisPageDoesNotCallRealExchanges')}
                    />
                    <BoundaryBadge
                        label={t('pages:privateTradingNotImplemented')}
                        tooltip={t('pages:noOrdersCancellationsTransfersWithdrawalsOrPrivateEndpointCapability')}
                    />
                    <BoundaryBadge
                        color="warning"
                        label={t('pages:validationIsNotTradingAuthorization')}
                        tooltip={t('pages:approvedIsAValidationLevelPassNotTradingAuthorization')}
                    />
                    <BoundaryBadge
                        color="warning"
                        label={t('pages:shadowRunIsDiagnosticOnly2')}
                        tooltip={t('pages:shadowRunFactsAreForDiagnosticsAndReplayOnlyNotEnabledShadowTrading')}
                    />
                    <BoundaryBadge
                        label={t('pages:aiDhRuntimeNotIntegrated')}
                        tooltip={t('pages:aiRemainsNotStartedDhRuntimeRemainsNotIntegrated')}
                    />
                </Space>

                {hasError ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:workbenchReadOnlyDataFailedToLoad')}
                        description={t('pages:failedSectionsRemainUnavailableReturnedPartialDataIsRetainedMissingDataIsNotAPassOrAuthorization')}
                    />
                ) : null}
                {isLoading ? <Skeleton active paragraph={{rows: 8}}/> : null}
                {!isLoading && hasPartialData ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:partialData')}
                        description={t('pages:missingStrategyOrShadowOverviewsShadowrunidOrDrilldownLimitTheWorkbenchToAvailableFactsEvidenceCompa')}
                    />
                ) : null}

                <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
                    <Descriptions.Item label="totalStrategyVersions">
                        {numberValue(strategyOverview?.totalStrategyVersions)}
                    </Descriptions.Item>
                    <Descriptions.Item label="evaluatedStrategyVersions">
                        {numberValue(strategyOverview?.evaluatedStrategyVersions)}
                    </Descriptions.Item>
                    <Descriptions.Item label="approvedForValidation">
                        {numberValue(strategyOverview?.approvedForValidation)}
                    </Descriptions.Item>
                    <Descriptions.Item label="rejectedForValidation">
                        {numberValue(strategyOverview?.rejectedForValidation)}
                    </Descriptions.Item>
                    <Descriptions.Item label="needsReview">
                        {numberValue(strategyOverview?.needsReview)}
                    </Descriptions.Item>
                    <Descriptions.Item label="blocked">
                        {numberValue(strategyOverview?.blocked)}
                    </Descriptions.Item>
                    <Descriptions.Item label="latestDecision.decision">
                        <Tooltip title={t('pages:approvedMeansValidationPassedNotTradingAuthorization')}>
                            <span><StatusTag status={strategyOverview?.latestDecision?.decision}/></span>
                        </Tooltip>
                    </Descriptions.Item>
                    <Descriptions.Item label="latestDecision.traceId">
                        {optionalCode(strategyOverview?.latestDecision?.traceId)}
                    </Descriptions.Item>
                    <Descriptions.Item label="strategy traceId">
                        {optionalCode(strategyOverview?.traceId)}
                    </Descriptions.Item>
                </Descriptions>

                <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
                    <Descriptions.Item label="totalRuns">{numberValue(shadowOverview?.totalRuns)}</Descriptions.Item>
                    <Descriptions.Item
                        label="runningRuns">{numberValue(shadowOverview?.runningRuns)}</Descriptions.Item>
                    <Descriptions.Item
                        label="blockedRuns">{numberValue(shadowOverview?.blockedRuns)}</Descriptions.Item>
                    <Descriptions.Item label="failedRuns">{numberValue(shadowOverview?.failedRuns)}</Descriptions.Item>
                    <Descriptions.Item
                        label="completedRuns">{numberValue(shadowOverview?.completedRuns)}</Descriptions.Item>
                    <Descriptions.Item label="staleRuns">{numberValue(shadowOverview?.staleRuns)}</Descriptions.Item>
                    <Descriptions.Item label="latestRun.status">
                        <StatusTag status={shadowOverview?.latestRun?.status}/>
                    </Descriptions.Item>
                    <Descriptions.Item label="shadowRunId">
                        {queries.shadowRunId ? (
                            <Link to={`/strategies/shadow-runs/${queries.shadowRunId}`}>
                                <Text code>{queries.shadowRunId}</Text>
                            </Link>
                        ) : <StatusTag status="NOT_AVAILABLE"/>}
                    </Descriptions.Item>
                    <Descriptions.Item label="divergenceSeverity">
                        <StatusTag status={shadowOverview?.divergenceSeverity}/>
                    </Descriptions.Item>
                    <Descriptions.Item label="latestConsistency.comparisonStatus">
                        <StatusTag
                            status={drilldown?.comparisonStatus ?? shadowOverview?.latestConsistency?.comparisonStatus}/>
                    </Descriptions.Item>
                    <Descriptions.Item label="divergenceReasons">
                        {jsonSummary(drilldown?.divergenceReasons ?? shadowOverview?.latestConsistency?.divergenceReasons)}
                    </Descriptions.Item>
                    <Descriptions.Item label="limitations">
                        {jsonSummary(drilldown?.limitations ?? shadowOverview?.latestConsistency?.limitations)}
                    </Descriptions.Item>
                    <Descriptions.Item label="drilldown traceId">
                        {optionalCode(drilldown?.traceId ?? shadowOverview?.latestConsistency?.traceId)}
                    </Descriptions.Item>
                </Descriptions>

                {signalRows.length === 0 && nextStepRows.length === 0 && evidenceRows.length === 0 ? (
                    <Empty
                        description={t('pages:noBlockersWarningsNextstepsOrAnchorsEvidenceIsNotNecessarilyCompleteOrExecutable')}/>
                ) : null}
                <Table<WorkbenchSignalRow>
                    size="small"
                    rowKey={(record) => record.key}
                    columns={workbenchSignalColumns}
                    dataSource={signalRows}
                    pagination={false}
                    scroll={{x: 980}}
                    locale={{emptyText: t('pages:noBlockersOrWarningsFixedSafetyBoundariesStillApply')}}
                />
                <Table<WorkbenchNextStepRow>
                    size="small"
                    rowKey={(record) => record.key}
                    columns={workbenchNextStepColumns}
                    dataSource={nextStepRows}
                    pagination={false}
                    scroll={{x: 1180}}
                    locale={{emptyText: t('pages:noNextstepsThisDoesNotPermitTrading2')}}
                />
                <Table<WorkbenchEvidenceAnchorRow>
                    size="small"
                    rowKey={(record) => record.key}
                    columns={workbenchEvidenceAnchorColumns}
                    dataSource={evidenceRows}
                    pagination={false}
                    scroll={{x: 1190}}
                    locale={{emptyText: t('pages:noEvidenceAnchorsEvidenceMustNotBeFabricated')}}
                />
            </Space>
        </Card>
    );
}

function EvaluationGatePanel({
                                 submitted,
                                 query,
                             }: {
    submitted: boolean;
    query: PanelQueryState<StrategyEvaluationGateResponse>;
}) {
    useTranslation('pages');
    const data = query.data;
    return (
        <ResultPanel
            title={t('pages:strategyEvaluationGate')}
            subtitle={t('pages:theEvaluationGateOnlyIndicatesWhetherResearchEvidenceCanEnterShadowReview')}
            status={data?.gateStatus}
            submitted={submitted}
            query={query}
            requiredEvidence={data?.requiredEvidence}
            missingEvidence={data?.missingEvidence}
            blockers={data?.blockers}
            warnings={data?.warnings}
            nextSteps={data?.nextSteps}
            boundaryDescription={t('pages:theEvaluationGateDoesNotGrantTradingAuthorizationEnableLiveOrPermitRealStrategyExecution')}
        >
            <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
                <Descriptions.Item label="strategyVersionId">{optionalCode(data?.strategyVersionId)}</Descriptions.Item>
                <Descriptions.Item label="gateDecision">{optionalText(data?.gateDecision)}</Descriptions.Item>
                <Descriptions.Item label="evaluationStatus"><StatusTag
                    status={data?.evaluationStatus}/></Descriptions.Item>
                <Descriptions.Item label="datasetQualityStatus"><StatusTag
                    status={data?.datasetQualityStatus}/></Descriptions.Item>
                <Descriptions.Item label="publishTraceStatus"><StatusTag
                    status={data?.publishTraceStatus}/></Descriptions.Item>
                <Descriptions.Item label="paperEvidenceStatus"><StatusTag
                    status={data?.paperEvidenceStatus}/></Descriptions.Item>
                <Descriptions.Item label="generatedAt">{generatedAtText(data?.generatedAt)}</Descriptions.Item>
            </Descriptions>
        </ResultPanel>
    );
}

function PaperShadowPanel({
                              submitted,
                              query,
                          }: {
    submitted: boolean;
    query: PanelQueryState<PaperShadowComparisonResponse>;
}) {
    useTranslation('pages');
    const data = query.data;
    return (
        <ResultPanel
            title={t('pages:paperVersusShadowComparison')}
            subtitle={t('pages:paperShadowComparisonOnlyIndicatesEvidenceCompletenessAndAvailability')}
            status={data?.comparisonStatus}
            submitted={submitted}
            query={query}
            requiredEvidence={data?.requiredEvidence}
            missingEvidence={data?.missingEvidence}
            blockers={data?.blockers}
            warnings={data?.warnings}
            nextSteps={data?.nextSteps}
            boundaryDescription={t('pages:paperVersusShadowComparisonDoesNotGrantTradingAuthorizationEnableShadowLiveExecutionOrCreateOrStartA')}
        >
            <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
                <Descriptions.Item label="paperRunId">{optionalCode(data?.paperRunId)}</Descriptions.Item>
                <Descriptions.Item label="shadowRunId">{optionalCode(data?.shadowRunId)}</Descriptions.Item>
                <Descriptions.Item label="paperRunStatus"><StatusTag status={data?.paperRunStatus}/></Descriptions.Item>
                <Descriptions.Item label="shadowRunStatus"><StatusTag
                    status={data?.shadowRunStatus}/></Descriptions.Item>
                <Descriptions.Item label="evaluationGateStatus"><StatusTag
                    status={data?.evaluationGateStatus}/></Descriptions.Item>
                <Descriptions.Item label="paperEvidenceStatus"><StatusTag
                    status={data?.paperEvidenceStatus}/></Descriptions.Item>
                <Descriptions.Item label="shadowEvidenceStatus"><StatusTag
                    status={data?.shadowEvidenceStatus}/></Descriptions.Item>
                <Descriptions.Item label="dataQualityStatus"><StatusTag
                    status={data?.dataQualityStatus}/></Descriptions.Item>
                <Descriptions.Item label="comparable">
                    {data?.comparable ? <Tag color="processing">{t('pages:trueReadOnlyComparisonAvailable')}</Tag> :
                        <Tag color="default">{t('pages:falseNotComparable')}</Tag>}
                </Descriptions.Item>
                <Descriptions.Item label="generatedAt">{generatedAtText(data?.generatedAt)}</Descriptions.Item>
            </Descriptions>
        </ResultPanel>
    );
}

function ShadowLivePreviewPanel({
                                    submitted,
                                    query,
                                }: {
    submitted: boolean;
    query: PanelQueryState<ShadowLivePreviewResponse>;
}) {
    useTranslation('pages');
    const data = query.data;
    return (
        <ResultPanel
            title={t('pages:shadowLiveNoSideEffectPreview')}
            subtitle={t('pages:shadowLivePreviewGeneratesNoSideEffectPlansOnlyItExecutesNoStrategiesOrOrders')}
            status={data?.previewStatus}
            submitted={submitted}
            query={query}
            requiredEvidence={data?.requiredEvidence}
            missingEvidence={data?.missingEvidence}
            blockers={data?.blockers}
            warnings={data?.warnings}
            nextSteps={data?.nextSteps}
            boundaryDescription={t('pages:shadowLivePreviewHasNoSideEffectsNoDatabaseWritesExternalConnectionsRealCredentialReadsOrRealOrderSu')}
        >
            <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
                <Descriptions.Item label="runnerStatus"><StatusTag status={data?.runnerStatus}/></Descriptions.Item>
                <Descriptions.Item label="evaluationGateStatus"><StatusTag
                    status={data?.evaluationGateStatus}/></Descriptions.Item>
                <Descriptions.Item label="paperShadowComparisonStatus"><StatusTag
                    status={data?.paperShadowComparisonStatus}/></Descriptions.Item>
                <Descriptions.Item label="inputFactStatus"><StatusTag
                    status={data?.inputFactStatus}/></Descriptions.Item>
                <Descriptions.Item label="traceStatus"><StatusTag status={data?.traceStatus}/></Descriptions.Item>
                <Descriptions.Item label="orderIntentPreviewStatus"><StatusTag status={data?.orderIntentPreviewStatus}/></Descriptions.Item>
                <Descriptions.Item label="riskPreflightPreviewStatus"><StatusTag
                    status={data?.riskPreflightPreviewStatus}/></Descriptions.Item>
                <Descriptions.Item label="generatedAt">{generatedAtText(data?.generatedAt)}</Descriptions.Item>
            </Descriptions>
            <SideEffectPolicyTable policies={data?.sideEffectPolicy ?? []}/>
        </ResultPanel>
    );
}

function SideEffectPolicyTable({policies}: { policies: ShadowLiveSideEffectPolicy[] }) {
    useTranslation('pages');
    return (
        <div>
            <Text strong>{t('pages:noSideEffectPolicy')}</Text>
            <Table<ShadowLiveSideEffectPolicy>
                size="small"
                rowKey={(record) => record.code}
                columns={sideEffectColumns}
                dataSource={policies}
                pagination={false}
                scroll={{x: 760}}
                locale={{emptyText: t('pages:noSideeffectpolicyExecutionIsNotPermitted')}}
            />
        </div>
    );
}

function TraceabilityChain({
                               submittedQuery,
                               gate,
                               comparison,
                               preview,
                               artifactPreview,
                           }: {
    submittedQuery: StrategyValidationQuery | null;
    gate?: StrategyEvaluationGateResponse;
    comparison?: PaperShadowComparisonResponse;
    preview?: ShadowLivePreviewResponse;
    artifactPreview?: PythonEvaluationArtifactPreviewOverviewResponse;
}) {
    useTranslation('pages');
    const scope = firstScope(submittedQuery, gate, comparison, preview);
    const artifactPreviewStatus = artifactPreview
        ? evaluationArtifactPreviewIsNoFileBaseline(artifactPreview) ? 'NO_ARTIFACT_SOURCE_CONFIGURED' : 'DIAGNOSTIC_ONLY'
        : 'UNKNOWN';
    const items: LifecycleTraceItem[] = [
        {
            key: 'strategyVersion',
            label: t('pages:strategyVersion'),
            value: scope.strategyVersionId,
            status: scope.strategyVersionId ? gate?.gateStatus ?? 'SATISFIED' : 'NOT_AVAILABLE',
            source: t('pages:strategyEvaluationGateQuery'),
            detail: t('pages:theStrategyVersionIsTheMainAnchorForThisQueryChain'),
        },
        {
            key: 'dataset',
            label: t('pages:dataset'),
            value: scope.datasetId,
            status: gate?.datasetQualityStatus ?? comparison?.dataQualityStatus ?? 'NOT_AVAILABLE',
            source: t('pages:evaluationGatePaperShadow'),
            detail: t('pages:theDatasetTracesEvaluationEvidenceOnlyNotTradableMarketData'),
        },
        {
            key: 'evaluationGate',
            label: t('pages:evaluationGate'),
            value: scope.evaluationId,
            status: gate?.gateStatus ?? comparison?.evaluationGateStatus ?? 'NOT_AVAILABLE',
            source: 'GateQ-1 GET /api/strategies/evaluation-gate',
            detail: t('pages:theEvaluationGateProvidesReadOnlyEvidenceForShadowReviewNotStrategyApprovalOrTradingAuthorization'),
        },
        {
            key: 'publishTrace',
            label: t('pages:publishTrace'),
            value: scope.publishId,
            status: gate?.publishTraceStatus ?? 'NOT_AVAILABLE',
            source: t('pages:strategyEvaluationGate'),
            detail: t('pages:publishTracesProvideChainEvidenceOnlyAndTriggerNoPublishWrites'),
        },
        {
            key: 'paperRun',
            label: t('pages:paperRun'),
            value: scope.paperRunId,
            status: comparison?.paperRunStatus ?? gate?.paperEvidenceStatus ?? 'NOT_AVAILABLE',
            source: t('pages:evaluationGatePaperShadow'),
            detail: t('pages:paperEvidenceDescribesSimPaperFactsOnlyAndStartsNoPaperRun'),
        },
        {
            key: 'paperShadowComparison',
            label: t('pages:paperShadowComparison'),
            value: scope.shadowRunId,
            status: comparison?.comparisonStatus ?? 'NOT_AVAILABLE',
            source: 'GateQ-2 GET /api/strategies/paper-shadow/comparison',
            detail: t('pages:readOnlyComparisonIndicatesComparabilityOnlyMissingUnknownOrUnimplementedShadowDataIsNotSuccess'),
        },
        {
            key: 'shadowLivePreview',
            label: t('pages:shadowLivePreview'),
            value: scope.shadowRunId,
            status: preview?.previewStatus ?? comparison?.shadowRunStatus ?? 'NOT_AVAILABLE',
            source: 'GateQ-3 GET /api/strategies/shadow-live/preview',
            detail: t('pages:shadowLivePreviewHasNoSideEffectsAndExecutesNoStrategiesOrRealOrders'),
        },
        {
            key: 'pythonArtifactBindingPreview',
            label: t('pages:pythonArtifactBindingPreview'),
            value: artifactPreview?.traceId ?? 'NO_FILE_BASELINE',
            status: artifactPreviewStatus,
            source: 'GateT-4 GET /api/strategy-validation/evaluation-artifacts/preview/overview',
            detail: t('pages:thisPageConsumesTheNoFileOverviewOnlyNoArtifactReadsUploadsImportsPythonExecutionOrJavaFactSourceWri'),
        },
    ];

    return (
        <Card className="page-section" variant="borderless" title={t('pages:lifecycleTraceChain')}>
            {!submittedQuery ? (
                <Empty
                    description={t('pages:searchToViewStrategyVersionDatasetEvaluationGatePublishPaperRunPaperShadowComparisonShadowLivePrevie')}/>
            ) : (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:tracePath')}
                        description={t('pages:strategyversionDatasetEvaluationPublishPaperShadowPythonartifactbindingpreviewAllNodesAreReadOnlyMis')}
                    />
                    <Table<LifecycleTraceItem>
                        size="small"
                        rowKey={(record) => record.key}
                        columns={lifecycleColumns}
                        dataSource={items}
                        pagination={false}
                        scroll={{x: 1180}}
                    />
                </Space>
            )}
        </Card>
    );
}

function EvidenceMatrix({
                            submittedQuery,
                            gate,
                            comparison,
                            preview,
                            artifactPreview,
                        }: {
    submittedQuery: StrategyValidationQuery | null;
    gate?: StrategyEvaluationGateResponse;
    comparison?: PaperShadowComparisonResponse;
    preview?: ShadowLivePreviewResponse;
    artifactPreview?: PythonEvaluationArtifactPreviewOverviewResponse;
}) {
    const {i18n: pageI18n} = useTranslation('pages');
    const rows = useMemo(() => [
        ...evidenceMatrixRows(t('pages:evaluationGate'), gate),
        ...evidenceMatrixRows(t('pages:paperShadowComparison'), comparison),
        ...evidenceMatrixRows(t('pages:shadowLivePreview'), preview),
        ...evaluationArtifactPreviewMatrixRows(artifactPreview),
    ], [gate, comparison, preview, artifactPreview, pageI18n.resolvedLanguage]);

    return (
        <Card className="page-section" variant="borderless" title={t('pages:evidenceMatrix2')}>
            {!submittedQuery ? (
                <Empty
                    description={t('pages:searchToViewRequiredevidenceMissingevidenceBlockersWarningsAndNextsteps')}/>
            ) : (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:theEvidenceMatrixAggregatesReceivedReadOnlyResponsesOnly')}
                        description={t('pages:requiredevidenceMissingevidenceBlockersWarningsAndNextstepsSupportTraceabilityAndReviewOnlyMissingNe')}
                    />
                    <Table<EvidenceMatrixRow>
                        size="small"
                        rowKey={(record) => record.key}
                        columns={evidenceMatrixColumns}
                        dataSource={rows}
                        pagination={false}
                        scroll={{x: 1080}}
                        locale={{emptyText: t('pages:noEvidenceMatrixEvidenceCannotBeConsideredComplete')}}
                    />
                </Space>
            )}
        </Card>
    );
}

function StatusSemantics() {
    useTranslation('pages');
    return (
        <Card className="page-section" variant="borderless" title={t('pages:statusMeanings')}>
            <Table<StatusExplanationRow>
                size="small"
                rowKey={(record) => record.status}
                columns={statusExplanationColumns}
                dataSource={STATUS_EXPLANATIONS}
                pagination={false}
                scroll={{x: 900}}
            />
        </Card>
    );
}

function BoundarySummary() {
    useTranslation('pages');
    return (
        <Card className="page-section" variant="borderless" title={t('pages:noSideEffectAuthorizationBoundary')}>
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Alert
                    type="warning"
                    showIcon
                    message={t('pages:thisPageIsForStrategyLifecycleTracingAndReadOnlyEvidenceInspection')}
                    description={t('pages:theEvaluationGateAndPaperShadowComparisonDoNotGrantTradingAuthorizationShadowLivePreviewSubmitsNoRea')}
                />
                <Space size={[8, 8]} wrap>
                    <Tag color="default">{t('pages:readOnlyValidation')}</Tag>
                    <Tag color="error">{t('pages:notTradingAuthorization2')}</Tag>
                    <Tag color="error">{t('pages:liveIsNotEnabled')}</Tag>
                    {FORBIDDEN_BOUNDARY_ITEMS.map((item) => (
                        <Tag key={item} color="error">{t(item)}</Tag>
                    ))}
                    <Tag color="default">LIVE: DISABLED</Tag>
                    <Tag color="default">real provider: NOT_IMPLEMENTED</Tag>
                    <Tag color="default">private trading adapter: NOT_IMPLEMENTED</Tag>
                    <Tag color="default">real permission probe: NOT_IMPLEMENTED</Tag>
                    <Tag color="default">AI: NOT STARTED</Tag>
                    <Tag color="default">DH runtime: NOT INTEGRATED</Tag>
                </Space>
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:missingStateHandling')}
                    description={t('pages:unknownNotAvailableNotImplementedPendingFrontendSupportAndBlockedAreNonSuccessStatesAddressBlockersA')}
                />
            </Space>
        </Card>
    );
}

export function StrategyValidationWorkspace({
                                                initialQuery,
                                                submittedQuery,
                                                onSubmit,
                                                onReset,
                                            }: {
    initialQuery: StrategyValidationQuery;
    submittedQuery: StrategyValidationQuery | null;
    onSubmit: (query: StrategyValidationQuery) => void;
    onReset: () => void;
}) {
    useTranslation('pages');
    const {
        overviewQuery,
        shadowValidationWorkflowQuery,
        consistencyEvidenceQuery,
        evaluationArtifactPreviewQuery,
        incidentReplayReviewQuery,
        runtimeEvidenceQuery,
        incidentReplayQuery,
        shadowOverviewQuery,
        evaluationGateQuery,
        paperShadowQuery,
        shadowLivePreviewQuery,
        releaseAdmissionPreviewQuery,
        selectedShadowRunId,
        consistencyDrilldownQuery,
        loading,
    } = useStrategyValidationWorkspaceQueries(submittedQuery);

    return (
        <Space data-testid="strategy-validation-page" direction="vertical" size={16} style={{display: 'flex'}}>
            <ValidationReviewSection/>
            <ValidationOperationsRuntimeEvidenceOverviewPanel query={runtimeEvidenceQuery}/>
            <ValidationOperationsWorkbench
                queries={{
                    strategyOverview: overviewQuery,
                    shadowWorkflow: shadowValidationWorkflowQuery,
                    consistencyEvidence: consistencyEvidenceQuery,
                    incidentReplayReview: incidentReplayReviewQuery,
                    artifactPreview: evaluationArtifactPreviewQuery,
                }}
            />
            <BoundarySummary/>
            <ValidationOperationsDetailSections>
                <StrategyValidationOverviewPanel query={overviewQuery}/>
                <ShadowValidationWorkflowPanel query={shadowValidationWorkflowQuery}/>
                <ConsistencyEvidenceOverviewPanel query={consistencyEvidenceQuery}/>
                <EvaluationArtifactPreviewOverviewPanel query={evaluationArtifactPreviewQuery}/>
                <IncidentReplayReviewOverviewPanel query={incidentReplayReviewQuery}/>
                <IncidentReplayOverviewPanel query={incidentReplayQuery}/>
                <StrategyValidationShadowWorkbench
                    queries={{
                        strategyOverview: overviewQuery,
                        shadowOverview: shadowOverviewQuery,
                        drilldown: consistencyDrilldownQuery,
                        shadowRunId: selectedShadowRunId,
                    }}
                />
            </ValidationOperationsDetailSections>
            <QueryForm initialValues={initialQuery} onSubmit={onSubmit} onReset={onReset} loading={loading}/>
            <StatusSemantics/>
            <StrategyReleaseAdmissionPreviewPanel
                publishRecordId={submittedQuery?.publishId?.trim() || null}
                query={releaseAdmissionPreviewQuery}
            />
            <TraceabilityChain
                submittedQuery={submittedQuery}
                gate={evaluationGateQuery.data}
                comparison={paperShadowQuery.data}
                preview={shadowLivePreviewQuery.data}
                artifactPreview={evaluationArtifactPreviewQuery.data}
            />
            <EvidenceMatrix
                submittedQuery={submittedQuery}
                gate={evaluationGateQuery.data}
                comparison={paperShadowQuery.data}
                preview={shadowLivePreviewQuery.data}
                artifactPreview={evaluationArtifactPreviewQuery.data}
            />
            <EvaluationGatePanel submitted={Boolean(submittedQuery)} query={evaluationGateQuery}/>
            <PaperShadowPanel submitted={Boolean(submittedQuery)} query={paperShadowQuery}/>
            <ShadowLivePreviewPanel submitted={Boolean(submittedQuery)} query={shadowLivePreviewQuery}/>
        </Space>
    );
}

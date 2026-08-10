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
} from '@/types/consistency-evidence';
import type {
    EvaluationArtifactPreviewBlocker,
    EvaluationArtifactPreviewEvidenceAnchor,
    EvaluationArtifactPreviewNextStep,
    EvaluationArtifactPreviewWarning,
    PythonEvaluationArtifactPreviewItem,
    PythonEvaluationArtifactPreviewOverviewResponse,
} from '@/types/evaluation-artifact-preview';
import type {
    IncidentReplayBlocker,
    IncidentReplayEvidenceAnchor,
    IncidentReplayLatestEvidence,
    IncidentReplayNextStep,
    IncidentReplayOverviewResponse,
    IncidentReplaySeverity,
    IncidentReplayWarning,
} from '@/types/incident-replay';
import type {
    IncidentReplayReviewBlocker,
    IncidentReplayReviewEvidenceAnchor,
    IncidentReplayReviewItem,
    IncidentReplayReviewNextStep,
    IncidentReplayReviewOverviewResponse,
    IncidentReplayReviewWarning,
} from '@/types/incident-replay-review';
import type {
    JsonValue,
    PaperShadowConsistencyDrilldownResponse,
    ShadowRunOverviewResponse,
} from '@/types/shadow-runs';
import type {
    ShadowValidationBlocker,
    ShadowValidationEvidenceAnchor,
    ShadowValidationNextStep,
    ShadowValidationOperatorItem,
    ShadowValidationWarning,
    ShadowValidationWorkflowOverviewResponse,
} from '@/types/shadow-validation-workflow';
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
} from '@/types/strategy-validation';
import type {
    ValidationOperationsRuntimeEvidenceOverviewResponse,
    ValidationOperationsRuntimeEvidenceSource,
} from '@/types/validation-operations-runtime-evidence';
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
    APPROVED: {label: '验证层通过，非交易授权', tone: 'info'},
    REJECTED: {label: '验证层拒绝', tone: 'danger'},
    NEEDS_REVIEW: {label: '需要复核', tone: 'warning'},
    BLOCKED: {label: '阻断', tone: 'danger'},
    NO_EVIDENCE: {label: '无证据', tone: 'neutral'},
    STALE_EVIDENCE: {label: '证据过期或不完整', tone: 'warning'},
    NO_ARTIFACT_SOURCE_CONFIGURED: {label: '未配置 artifact source', tone: 'warning'},
    NO_FILE_BASELINE: {label: 'No-file baseline', tone: 'warning'},
    DIAGNOSTIC_ONLY: {label: '仅诊断', tone: 'info'},
    VALID: {label: 'checksum 自洽，非策略有效', tone: 'info'},
    INVALID: {label: 'checksum 失败', tone: 'danger'},
    NOT_CHECKED: {label: '未检查', tone: 'neutral'},
    FAKE_FIXTURE_ONLY: {label: '测试 fixture，非真实表现', tone: 'warning'},
    PRESENT: {label: '摘要存在，非收益结论', tone: 'info'},
    INCOMPLETE: {label: '摘要不完整', tone: 'warning'},
    CONSISTENT: {label: '证据一致，非盈利结论', tone: 'info'},
    DIVERGED: {label: '证据偏离', tone: 'warning'},
    NO_REPORT: {label: '无一致性报告', tone: 'neutral'},
    NOT_COMPARABLE: {label: '不可比较', tone: 'warning'},
    NO_CONSISTENCY_EVIDENCE: {label: '无 consistency evidence', tone: 'warning'},
    INTAKE: {label: '待进入证据流转', tone: 'info'},
    EVIDENCE_REVIEW: {label: '证据复核中', tone: 'warning'},
    NEEDS_EVIDENCE: {label: '需要补充证据', tone: 'warning'},
    READY_FOR_OPERATOR_REVIEW: {label: '可人工复核，非交易授权', tone: 'info'},
    VALIDATION_READY: {label: '验证材料可复核，非交易授权', tone: 'info'},
    CLOSED_RECOMMENDATION: {label: '建议闭环，非真实关闭', tone: 'info'},
    NEEDS_OPERATOR_REVIEW: {label: '需要人工复核', tone: 'warning'},
    ACKNOWLEDGED_RECOMMENDATION: {label: '建议人工确认，非自动处置', tone: 'info'},
    ESCALATED_RECOMMENDATION: {label: '建议人工升级复核', tone: 'danger'},
    NO_DECISION: {label: '未形成诊断建议', tone: 'neutral'},
    REVIEW_NEEDED: {label: '需要复核', tone: 'warning'},
    ACKNOWLEDGE_RECOMMENDED: {label: '建议人工确认，非自动处置', tone: 'info'},
    ESCALATE_RECOMMENDED: {label: '建议人工升级复核，非系统已升级', tone: 'danger'},
    CLOSEOUT_RECOMMENDED: {label: '建议形成诊断闭环，非真实关闭', tone: 'info'},
    READY_FOR_SHADOW_REVIEW: {label: '可进入 Shadow 评审', tone: 'info'},
    READY_FOR_COMPARISON: {label: '可查看只读对照', tone: 'info'},
    READY_FOR_NO_SIDE_EFFECT_PREVIEW: {label: '可生成无副作用预览', tone: 'info'},
    VALID_FOR_BINDING_PREVIEW: {label: '可进入只读绑定预览', tone: 'info'},
    PENDING_FRONTEND_SUPPORT: {label: '等待前端接入支持', tone: 'warning'},
    NOT_CONNECTED: {label: '未接入', tone: 'warning'},
    ACTION_REQUIRED: {label: '需要后续处理', tone: 'warning'},
    SKELETON_AVAILABLE: {label: '骨架可用', tone: 'info'},
    PREVIEW_ONLY: {label: '仅预览', tone: 'info'},
    NOT_EXECUTED: {label: '未执行', tone: 'neutral'},
    NOT_IMPLEMENTED: {label: '能力未实现', tone: 'warning'},
    UNKNOWN: {label: '未知', tone: 'neutral'},
    NOT_AVAILABLE: {label: '不可用', tone: 'neutral'},
    PARTIAL: {label: '部分可见', tone: 'warning'},
    BLOCKED_SHADOW_NOT_IMPLEMENTED: {label: 'Shadow 未实现阻断', tone: 'danger'},
    PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE: {label: 'Shadow facts 不可用', tone: 'danger'},
    PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE: {label: '追踪链不完整', tone: 'danger'},
    PREVIEW_BLOCKED_EVALUATION_GATE: {label: 'Evaluation Gate 阻断', tone: 'danger'},
    PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON: {label: 'Paper / Shadow 对照阻断', tone: 'danger'},
    SATISFIED: {label: '已满足', tone: 'success'},
    MISSING: {label: '缺失', tone: 'warning'},
    FAILED: {label: '失败', tone: 'danger'},
    FORBIDDEN: {label: '禁止', tone: 'danger'},
    BLOCKER: {label: '阻断', tone: 'danger'},
    WARNING: {label: '警告', tone: 'warning'},
    SUCCEEDED: {label: '成功', tone: 'success'},
    ACTIVE: {label: '有效', tone: 'success'},
    CREATED: {label: '已创建', tone: 'info'},
    READY: {label: '诊断就绪，非交易放行', tone: 'info'},
    RUNNING: {label: '诊断运行中', tone: 'info'},
    COMPLETED: {label: '诊断完成，非收益结论', tone: 'info'},
    STOPPED: {label: '已停止', tone: 'neutral'},
    CANCELLED: {label: '已取消', tone: 'neutral'},
    LOW: {label: '低偏离', tone: 'warning'},
    MEDIUM: {label: '中等偏离', tone: 'warning'},
    HIGH: {label: '高偏离', tone: 'danger'},
    CRITICAL: {label: '严重偏离', tone: 'danger'},
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
        meaning: '可进入 Shadow 评审',
        boundary: '只表示研究与评估证据可进入后续评审，不表示可交易、可下单或可启用 LIVE。',
    },
    {
        status: 'READY_FOR_COMPARISON',
        meaning: '可查看只读对照',
        boundary: '只表示 Paper / Shadow 只读证据可比较，不创建 Shadow run，不表示交易授权。',
    },
    {
        status: 'READY_FOR_NO_SIDE_EFFECT_PREVIEW',
        meaning: '可生成无副作用预览',
        boundary: '只表示可以生成 no-side-effect preview，不执行策略、不提交订单、不写真实状态。',
    },
    {
        status: 'VALID_FOR_BINDING_PREVIEW',
        meaning: '可进入绑定预览',
        boundary: '只表示 artifact 可做只读校验预览，不代表已入库、已发布、ML ready 或 live execution ready。',
    },
    {
        status: 'UNKNOWN / NOT_AVAILABLE / NOT_IMPLEMENTED / BLOCKED_*',
        meaning: '未知、不可用、能力未实现或阻断',
        boundary: '必须按缺失或阻断展示，不能显示为成功态；页面必须保留 blockers 与 nextSteps。',
    },
];

const FORBIDDEN_BOUNDARY_ITEMS = [
    '不提交真实订单',
    '不读取真实凭证',
    '不启用 LIVE',
    '不调用 private endpoint',
    '不写真实账户 / 资金 / ledger',
    '不接 AI / DH runtime 执行链路',
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
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 220,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 180,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const reasonColumns: ColumnsType<StrategyValidationReason> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: '级别',
        dataIndex: 'severity',
        key: 'severity',
        width: 140,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const sideEffectColumns: ColumnsType<ShadowLiveSideEffectPolicy> = [
    {
        title: 'Policy',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 140,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '边界说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const lifecycleColumns: ColumnsType<LifecycleTraceItem> = [
    {
        title: '节点',
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
        title: 'Trace value',
        dataIndex: 'value',
        key: 'value',
        width: 220,
        render: (value?: string | null) => optionalCode(value),
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 220,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 260,
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
    {
        title: '边界说明',
        dataIndex: 'detail',
        key: 'detail',
        render: (value: ReactNode) => <Text type="secondary">{value}</Text>,
    },
];

const evidenceMatrixColumns: ColumnsType<EvidenceMatrixRow> = [
    {
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 210,
        render: (value: string) => <Text>{value}</Text>,
    },
    {
        title: '类别',
        dataIndex: 'category',
        key: 'category',
        width: 170,
        render: (value: EvidenceMatrixCategory) => <Text code>{EVIDENCE_CATEGORY_LABELS[value]}</Text>,
    },
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 180,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '说明 / nextSteps',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const statusExplanationColumns: ColumnsType<StatusExplanationRow> = [
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 280,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: '页面解释',
        dataIndex: 'meaning',
        key: 'meaning',
        width: 220,
        render: (value: string) => <Text>{value}</Text>,
    },
    {
        title: '禁止误读',
        dataIndex: 'boundary',
        key: 'boundary',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const overviewIssueColumns: ColumnsType<StrategyValidationOverviewIssue> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '级别',
        dataIndex: 'severity',
        key: 'severity',
        width: 150,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '来源',
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{record.sourceType}</Text>
                {record.sourceId ? <Text code>{record.sourceId}</Text> : <Text type="secondary">无 sourceId</Text>}
            </Space>
        ),
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const overviewNextStepColumns: ColumnsType<StrategyValidationNextStep> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner',
        width: 160,
    },
    {
        title: '动作',
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{value}</Text>,
    },
    {
        title: '完成条件',
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
    {
        title: '边界关键',
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? '是' : '否'}</Tag>,
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
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '级别',
        dataIndex: 'severity',
        key: 'severity',
        width: 150,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '来源',
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">无 sourceId</Text>
                )}
            </Space>
        ),
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const shadowValidationWorkflowIssueColumns: ColumnsType<ShadowValidationWorkflowIssue> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '诊断优先级',
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: '来源',
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">无 sourceId</Text>
                )}
            </Space>
        ),
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const shadowValidationWorkflowNextStepColumns: ColumnsType<ShadowValidationNextStep> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '动作',
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '完成条件',
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        title: '边界关键',
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? '是' : '否'}</Tag>,
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
        title: '来源',
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
        title: 'blockers / warnings',
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
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '诊断优先级',
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: '来源',
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">无 sourceId</Text>
                )}
            </Space>
        ),
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const consistencyEvidenceNextStepColumns: ColumnsType<ConsistencyEvidenceNextStep> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '动作',
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '完成条件',
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        title: '边界关键',
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? '是' : '否'}</Tag>,
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
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 190,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: 'Bucket',
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
                {value ? 'true（可诊断比较）' : 'false（不可比较）'}
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
        title: '关联 id',
        key: 'ids',
        width: 300,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>shadowRunId {record.shadowRunId ?
                    <Text code>{workbenchSafeText(record.shadowRunId)}</Text> : '无'}</Text>
                <Text>paperRunId {record.paperRunId ?
                    <Text code>{workbenchSafeText(record.paperRunId)}</Text> : '无'}</Text>
                <Text>consistencyReportId {record.consistencyReportId ? (
                    <Text code>{workbenchSafeText(record.consistencyReportId)}</Text>
                ) : '无'}</Text>
            </Space>
        ),
    },
    {
        title: 'divergenceReasons / limitations',
        key: 'summaries',
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text type="secondary">reasons: {safeTextListSummary(record.divergenceReasons)}</Text>
                <Text type="secondary">limitations: {safeTextListSummary(record.limitations)}</Text>
            </Space>
        ),
    },
];

const incidentReplayReviewIssueColumns: ColumnsType<IncidentReplayReviewOverviewIssue> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '诊断优先级',
        dataIndex: 'severity',
        key: 'severity',
        width: 190,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: '来源',
        key: 'source',
        width: 240,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">无 sourceId</Text>
                )}
            </Space>
        ),
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const incidentReplayReviewNextStepColumns: ColumnsType<IncidentReplayReviewNextStep> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '动作',
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '完成条件',
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        title: '边界关键',
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? '是' : '否'}</Tag>,
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
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 190,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: 'Bucket',
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
        title: '关联 id',
        key: 'ids',
        width: 320,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>source {workbenchSafeText(record.sourceType)} / <Text code>{workbenchSafeText(record.sourceId)}</Text></Text>
                <Text>shadowRunId {record.shadowRunId ? <Text code>{workbenchSafeText(record.shadowRunId)}</Text> : '无'}</Text>
                <Text>paperRunId {record.paperRunId ? <Text code>{workbenchSafeText(record.paperRunId)}</Text> : '无'}</Text>
                <Text>consistencyReportId {record.consistencyReportId ? (
                    <Text code>{workbenchSafeText(record.consistencyReportId)}</Text>
                ) : '无'}</Text>
            </Space>
        ),
    },
    {
        title: 'summary / limitations',
        key: 'summaries',
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.summary)}</Text>
                <Text type="secondary">limitations: {safeTextListSummary(record.limitations)}</Text>
            </Space>
        ),
    },
    {
        title: 'blockers / warnings',
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
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 280,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '诊断优先级',
        dataIndex: 'severity',
        key: 'severity',
        width: 180,
        render: (value: string) => <WorkflowStatusTag status={value}/>,
    },
    {
        title: '来源',
        key: 'source',
        width: 250,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>{workbenchSafeText(record.sourceType)}</Text>
                {record.sourceId ? <Text code>{workbenchSafeText(record.sourceId)}</Text> : (
                    <Text type="secondary">无 sourceId</Text>
                )}
            </Space>
        ),
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const evaluationArtifactPreviewNextStepColumns: ColumnsType<EvaluationArtifactPreviewNextStep> = [
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '动作',
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '完成条件',
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        title: '边界关键',
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? '是' : '否'}</Tag>,
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
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 230,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: 'Bucket',
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
        title: 'Schema / source',
        key: 'schemaSource',
        width: 320,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>schemaVersion {record.schemaVersion ? <Text code>{workbenchSafeText(record.schemaVersion)}</Text> : '无'}</Text>
                <Text>source {record.source ? <Text code>{workbenchSafeText(record.source)}</Text> : '无'}</Text>
            </Space>
        ),
    },
    {
        title: '关联 id',
        key: 'ids',
        width: 360,
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text>artifactId {record.artifactId ? <Text code>{workbenchSafeText(record.artifactId)}</Text> : '无'}</Text>
                <Text>strategyVersionId {record.strategyVersionId ? (
                    <Text code>{workbenchSafeText(record.strategyVersionId)}</Text>
                ) : '无'}</Text>
                <Text>datasetId {record.datasetId ? <Text code>{workbenchSafeText(record.datasetId)}</Text> : '无'}</Text>
                <Text>parameterSetId {record.parameterSetId ? (
                    <Text code>{workbenchSafeText(record.parameterSetId)}</Text>
                ) : '无'}</Text>
            </Space>
        ),
    },
    {
        title: 'Assumptions',
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
        title: 'warnings / limitations',
        key: 'diagnostics',
        render: (_, record) => (
            <Space direction="vertical" size={2}>
                <Text type="secondary">warnings: {safeTextListSummary(record.validationWarnings)}</Text>
                <Text type="secondary">limitations: {safeTextListSummary(record.limitations)}</Text>
            </Space>
        ),
    },
    {
        title: 'readiness flags',
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
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner',
        width: 160,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '动作',
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '完成条件',
        dataIndex: 'completionCondition',
        key: 'completionCondition',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        title: '边界关键',
        dataIndex: 'boundaryCritical',
        key: 'boundaryCritical',
        width: 130,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? '是' : '否'}</Tag>,
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
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 210,
    },
    {
        title: '类别',
        dataIndex: 'kind',
        key: 'kind',
        width: 120,
        render: (value: WorkbenchSignalRow['kind']) => (
            <Tag color={value === 'blocker' ? 'error' : 'warning'}>{value}</Tag>
        ),
    },
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 260,
        render: (value: string) => <Text code>{value}</Text>,
    },
    {
        title: '级别',
        dataIndex: 'severity',
        key: 'severity',
        width: 150,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '说明',
        dataIndex: 'message',
        key: 'message',
        render: (value: string) => <Text type="secondary">{value}</Text>,
    },
];

const workbenchNextStepColumns: ColumnsType<WorkbenchNextStepRow> = [
    {
        title: '来源',
        dataIndex: 'source',
        key: 'source',
        width: 210,
    },
    {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        width: 240,
        render: (value: string) => <Text code>{workbenchSafeText(value)}</Text>,
    },
    {
        title: 'Owner',
        dataIndex: 'owner',
        key: 'owner',
        width: 150,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '动作',
        dataIndex: 'action',
        key: 'action',
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '证据 / 完成条件',
        dataIndex: 'evidence',
        key: 'evidence',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
    {
        title: '阻断',
        dataIndex: 'blocking',
        key: 'blocking',
        width: 110,
        render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? '是' : '否'}</Tag>,
    },
];

const workbenchEvidenceAnchorColumns: ColumnsType<WorkbenchEvidenceAnchorRow> = [
    {
        title: '来源',
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
        title: '运营主线',
        dataIndex: 'lane',
        key: 'lane',
        width: 240,
        render: (value: string) => <Text strong>{value}</Text>,
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 240,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '核心指标',
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
        title: 'Evidence lane',
        dataIndex: 'lane',
        key: 'lane',
        width: 230,
        render: (value: string) => <Text strong>{value}</Text>,
    },
    {
        title: 'Evidence',
        dataIndex: 'evidence',
        key: 'evidence',
        width: 230,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 230,
        render: (value: string) => <StatusTag status={value}/>,
    },
    {
        title: '数量',
        dataIndex: 'count',
        key: 'count',
        width: 180,
        render: (value: string) => <Text>{workbenchSafeText(value)}</Text>,
    },
    {
        title: '说明',
        dataIndex: 'detail',
        key: 'detail',
        render: (value: string) => <Text type="secondary">{workbenchSafeText(value)}</Text>,
    },
];

const validationOperationsOperatorQueueColumns: ColumnsType<ValidationOperationsOperatorQueueRow> = [
    {
        title: '来源',
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
        title: 'decision / recommendation',
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
        return '无';
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
    const source = metadata?.source?.trim() || 'UNKNOWN_SOURCE';
    const availability = normalizeStatus(metadata?.availability) || 'UNKNOWN';
    const freshness = normalizeStatus(metadata?.freshnessStatus) || 'UNKNOWN';
    const availabilityColor = availability === 'AVAILABLE' && freshness === 'FRESH'
        ? 'success'
        : availability === 'PARTIAL' || freshness === 'STALE'
            ? 'warning'
            : availability === 'UNAVAILABLE' ? 'error' : 'default';
    const freshnessText = freshness === 'FRESH'
        ? '新鲜'
        : freshness === 'STALE' ? '已过期' : '无法判断新鲜度';

    return (
        <Space data-testid={testId ?? 'read-model-evidence-metadata'} direction="vertical" size={6}
               style={{display: 'flex'}}>
            <DataFreshness
                source={`数据来源：${source}`}
                state={readModelFreshnessState(metadata)}
                detail={metadata?.ageSeconds == null ? freshnessText : `${freshnessText}；age ${metadata.ageSeconds}s`}
            />
            <Space size={[8, 6]} wrap>
                <Tag color={availabilityColor}>可用性：{availability}</Tag>
                <Text>新鲜度：{freshness}（{freshnessText}）</Text>
                <Text>最近计算时间：{metadata?.lastCalculatedAt ? formatDateTime(metadata.lastCalculatedAt) : '未提供'}</Text>
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
    const overview = query.data;
    return (
        <Card
            className="page-section"
            data-testid="validation-operations-runtime-evidence-card"
            variant="borderless"
            title="运行证据总览"
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching} onClick={() => query.refetch()}>
                    刷新总览
                </Button>
            )}
        >
            <Space data-testid="validation-operations-runtime-evidence-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费五个既有 evidence metadata 的 aggregate GET；用于识别证据是否完整，不替代来源详情，
                    不构成交易授权，也不会启动 runner、scheduler 或任何写侧动作。
                </Paragraph>
                {query.isLoading ? <Skeleton active paragraph={{rows: 5}}/> : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message="运行证据总览查询失败"
                        description={`聚合 GET 失败按不可用处理；不会显示为全部正常、可执行或已获交易授权。${formatApiError(query.error as AppApiError)}`}
                    />
                ) : !overview ? (
                    <Empty description="暂无运行证据总览；按 fail-closed 处理。"/>
                ) : (
                    <>
                        <ReadModelEvidenceMetadataSummary
                            metadata={overview.evidenceMetadata}
                            testId="validation-operations-runtime-evidence-metadata"
                        />
                        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
                            <Descriptions.Item label="证据来源数">{overview.sourceCount}</Descriptions.Item>
                            <Descriptions.Item label="可用 / 不完整 / 不可用 / 未知">
                                {`${overview.availableCount} / ${overview.partialCount} / ${overview.unavailableCount} / ${overview.unknownAvailabilityCount}`}
                            </Descriptions.Item>
                            <Descriptions.Item label="新鲜 / 过期 / 未知">
                                {`${overview.freshCount} / ${overview.staleCount} / ${overview.unknownFreshnessCount}`}
                            </Descriptions.Item>
                            <Descriptions.Item label="最新事实时间">
                                {overview.evidenceMetadata.lastCalculatedAt
                                    ? formatDateTime(overview.evidenceMetadata.lastCalculatedAt)
                                    : '暂无权威事实时间'}
                            </Descriptions.Item>
                        </Descriptions>
                        <div>
                            <Text strong>证据来源</Text>
                            <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
                                {overview.sources.map((source: ValidationOperationsRuntimeEvidenceSource) => (
                                    <Descriptions.Item key={source.sourceKey} label={source.displayName}>
                                        <Space size={[8, 6]} wrap>
                                            <Text code>{source.sourceKey}</Text>
                                            <Text>可用性：{source.evidenceMetadata.availability}</Text>
                                            <Text>新鲜度：{source.evidenceMetadata.freshnessStatus}</Text>
                                        </Space>
                                    </Descriptions.Item>
                                ))}
                            </Descriptions>
                        </div>
                        <Alert
                            type="info"
                            showIcon
                            message="仅用于诊断"
                            description="五个来源当前均可用也只表示诊断证据可用；不构成交易授权，LIVE 保持已禁用。"
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
        return '无';
    }
    if (Array.isArray(value)) {
        return value.length === 0 ? '空数组' : `数组 ${value.length} 项`;
    }
    if (typeof value === 'object') {
        return `对象 ${Object.keys(value).length} 个字段`;
    }
    if (typeof value === 'string') {
        return value.trim() ? workbenchSafeText(value) : '空文本';
    }
    return String(value);
}

function safeTextListSummary(items: string[] | null | undefined): string {
    const values = (items ?? [])
        .map((item) => workbenchSafeText(item))
        .filter((item) => item !== '无');
    if (values.length === 0) {
        return '无';
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

    pushIssues('Strategy Validation', 'blocker', strategyOverview?.blockers ?? []);
    pushIssues('Strategy Validation', 'warning', strategyOverview?.warnings ?? []);
    pushIssues('Shadow Run Overview', 'blocker', shadowOverview?.blockers ?? []);
    pushIssues('Shadow Run Overview', 'warning', shadowOverview?.warnings ?? []);
    pushIssues('Paper / Shadow Drilldown', 'blocker', drilldown?.blockers ?? []);
    pushIssues('Paper / Shadow Drilldown', 'warning', drilldown?.warnings ?? []);
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
            source: 'Strategy Validation',
            code: workbenchSafeText(item.code),
            owner: workbenchSafeText(item.owner),
            action: workbenchSafeText(item.action),
            evidence: workbenchSafeText(item.completionCondition),
            blocking: item.boundaryCritical,
        })),
        ...(shadowOverview?.nextSteps ?? []).slice(0, 4).map((item, index) => ({
            key: `shadow-overview-${index}`,
            source: 'Shadow Run Overview',
            code: workbenchSafeText(item.code),
            owner: workbenchSafeText(item.owner),
            action: workbenchSafeText(item.action),
            evidence: workbenchSafeText(item.expectedEvidence),
            blocking: item.blocking,
        })),
        ...(drilldown?.nextSteps ?? []).slice(0, 4).map((item, index) => ({
            key: `drilldown-${index}`,
            source: 'Paper / Shadow Drilldown',
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
        ...toRows('Strategy Validation', strategyOverview?.evidenceAnchors ?? []),
        ...toRows('Shadow Run Overview', shadowOverview?.evidenceAnchors ?? []),
        ...toRows('Paper / Shadow Drilldown', drilldown?.evidenceAnchors ?? []),
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
            source: 'Python Artifact Binding Preview',
            category: 'missingEvidence',
            code: 'EVALUATION_ARTIFACT_PREVIEW_OVERVIEW',
            status: 'UNKNOWN',
            message: 'Evaluation Artifact Preview overview 尚未返回；页面按 fail-closed 处理，不补造 artifact source。',
        }];
    }

    const rows: EvidenceMatrixRow[] = [];
    overview.blockers.forEach((blocker) => {
        rows.push({
            key: `Python Artifact Binding Preview-blocker-${blocker.code}-${blocker.sourceId ?? 'none'}`,
            source: 'Python Artifact Binding Preview',
            category: 'blockers',
            code: workbenchSafeText(blocker.code),
            status: workbenchSafeText(blocker.severity),
            message: workbenchSafeText(blocker.message),
        });
    });
    overview.warnings.forEach((warning) => {
        rows.push({
            key: `Python Artifact Binding Preview-warning-${warning.code}-${warning.sourceId ?? 'none'}`,
            source: 'Python Artifact Binding Preview',
            category: 'warnings',
            code: workbenchSafeText(warning.code),
            status: workbenchSafeText(warning.severity),
            message: workbenchSafeText(warning.message),
        });
    });
    if (evaluationArtifactPreviewIsNoFileBaseline(overview)) {
        rows.push({
            key: 'Python Artifact Binding Preview-no-file-baseline',
            source: 'Python Artifact Binding Preview',
            category: 'missingEvidence',
            code: 'NO_ARTIFACT_SOURCE_CONFIGURED',
            status: 'NO_FILE_BASELINE',
            message: '当前未配置 artifact source；不读取 artifact 文件、不执行 Python、不导入 DB。',
        });
    }
    overview.nextSteps.forEach((step) => {
        rows.push({
            key: `Python Artifact Binding Preview-next-${step.code}`,
            source: 'Python Artifact Binding Preview',
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
            lane: 'Strategy validation',
            status: strategyOverview ? decisionOf(strategyOverview) : 'UNKNOWN',
            primaryMetric: `versions ${numberValue(strategyOverview?.evaluatedStrategyVersions)}/${numberValue(strategyOverview?.totalStrategyVersions)} · needsReview ${numberValue(strategyOverview?.needsReview)}`,
            blockers: strategyOverview?.blockers.length ?? 0,
            warnings: strategyOverview?.warnings.length ?? 0,
            nextStep: firstNextStepCode(strategyOverview?.nextSteps),
            generatedAt: strategyOverview?.generatedAt ?? null,
        },
        {
            key: 'shadow-validation-workflow',
            lane: 'Shadow validation workflow',
            status: shadowWorkflow?.latestOperatorItem?.workflowState ?? (shadowWorkflow ? 'NO_OPERATOR_ITEMS' : 'UNKNOWN'),
            primaryMetric: `operatorItems ${numberValue(shadowWorkflow?.totalOperatorItems)} · readyForOperatorReview ${numberValue(shadowWorkflow?.readyForOperatorReviewCount)}`,
            blockers: shadowWorkflow?.blockers.length ?? 0,
            warnings: shadowWorkflow?.warnings.length ?? 0,
            nextStep: firstNextStepCode(shadowWorkflow?.nextSteps),
            generatedAt: shadowWorkflow?.generatedAt ?? null,
        },
        {
            key: 'consistency-evidence',
            lane: 'Consistency evidence',
            status: consistencyEvidence?.latestEvidenceItem?.comparisonStatus ?? (consistencyEvidence ? 'NO_EVIDENCE' : 'UNKNOWN'),
            primaryMetric: `evidenceItems ${numberValue(consistencyEvidence?.totalEvidenceItems)} · diverged ${numberValue(consistencyEvidence?.divergedCount)} · stale ${numberValue(consistencyEvidence?.staleEvidenceCount)}`,
            blockers: consistencyEvidence?.blockers.length ?? 0,
            warnings: consistencyEvidence?.warnings.length ?? 0,
            nextStep: firstNextStepCode(consistencyEvidence?.nextSteps),
            generatedAt: consistencyEvidence?.generatedAt ?? null,
        },
        {
            key: 'incident-replay-review',
            lane: 'Incident / replay review',
            status: incidentReplayReview?.latestReviewItem?.reviewState ?? (incidentReplayReview ? 'NO_REVIEW_ITEMS' : 'UNKNOWN'),
            primaryMetric: `reviewItems ${numberValue(incidentReplayReview?.totalReviewItems)} · acknowledged ${numberValue(incidentReplayReview?.acknowledgedRecommendationCount)} · escalated ${numberValue(incidentReplayReview?.escalatedRecommendationCount)}`,
            blockers: incidentReplayReview?.blockers.length ?? 0,
            warnings: incidentReplayReview?.warnings.length ?? 0,
            nextStep: firstNextStepCode(incidentReplayReview?.nextSteps),
            generatedAt: incidentReplayReview?.generatedAt ?? null,
        },
        {
            key: 'evaluation-artifact-preview',
            lane: 'Evaluation artifact preview',
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
            lane: 'strategy validation',
            evidence: 'latestDecision / evidenceAnchors / blockers',
            status: strategyOverview ? decisionOf(strategyOverview) : 'UNKNOWN',
            count: `anchors ${numberValue(strategyOverview?.evidenceAnchors.length)} · blockers ${numberValue(strategyOverview?.blockers.length)}`,
            detail: '验证材料只用于人工复核，不代表交易授权。',
        },
        {
            key: 'shadow-validation-evidence',
            lane: 'shadow validation',
            evidence: 'operatorItems / workflowState / evidenceFreshness',
            status: shadowWorkflow?.latestOperatorItem?.validationDecision ?? (shadowWorkflow ? 'NO_DECISION' : 'UNKNOWN'),
            count: `operatorItems ${numberValue(shadowWorkflow?.totalOperatorItems)} · needsEvidence ${numberValue(shadowWorkflow?.needsEvidenceCount)}`,
            detail: 'operator item 是 derived diagnostic row，不是 approve / reject / execute 写侧任务。',
        },
        {
            key: 'consistency-evidence',
            lane: 'consistency evidence',
            evidence: 'latestEvidenceItem / metricDeltaSummary / freshnessSummary',
            status: consistencyEvidence?.latestEvidenceItem?.comparisonStatus ?? (consistencyEvidence ? 'NO_REPORT' : 'UNKNOWN'),
            count: `consistent ${numberValue(consistencyEvidence?.consistentCount)} · diverged ${numberValue(consistencyEvidence?.divergedCount)}`,
            detail: 'CONSISTENT 只表示本地 evidence 一致，不表示可交易。',
        },
        {
            key: 'incident-replay-review-evidence',
            lane: 'incident / replay review',
            evidence: 'reviewItems / recommendation / replay anchors',
            status: incidentReplayReview?.latestReviewItem?.reviewDecision ?? (incidentReplayReview ? 'NO_DECISION' : 'UNKNOWN'),
            count: `reviewItems ${numberValue(incidentReplayReview?.totalReviewItems)} · blocked ${numberValue(incidentReplayReview?.blockedCount)}`,
            detail: 'ACKNOWLEDGE_RECOMMENDED / ESCALATE_RECOMMENDED 只表示建议人工复核。',
        },
        {
            key: 'python-artifact-preview-evidence',
            lane: 'Python artifact preview',
            evidence: 'no-file baseline / checksum / schema coverage',
            status: artifactPreview
                ? evaluationArtifactPreviewIsNoFileBaseline(artifactPreview)
                    ? 'NO_ARTIFACT_SOURCE_CONFIGURED'
                    : artifactPreview.latestArtifactPreview?.checksumStatus ?? 'NOT_CHECKED'
                : 'UNKNOWN',
            count: `previews ${numberValue(artifactPreview?.totalArtifactPreviews)} · stale ${numberValue(artifactPreview?.staleArtifactCount)}`,
            detail: 'checksum VALID 不表示策略有效；Python artifact preview 不表示 ML ready 或 live execution ready。',
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
            source: 'derived operator item',
            itemId: item.operatorItemId,
            state: item.workflowState,
            severity: item.severity,
            freshness: item.evidenceFreshness,
            decision: item.validationDecision,
            traceId: item.traceId,
        })),
        ...(incidentReplayReview?.reviewItems ?? []).slice(0, 5).map((item) => ({
            key: `review-${item.reviewItemId}`,
            source: 'review item',
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
                label: '已进入 intake',
                color: 'default',
                tooltip: 'INTAKE 只表示 item 已进入只读派生队列，尚未形成复核材料结论。',
            };
        case 'EVIDENCE_REVIEW':
            return {
                label: '证据复核中',
                color: 'warning',
                tooltip: 'EVIDENCE_REVIEW 表示需要继续查看 evidence，不代表验证通过。',
            };
        case 'NEEDS_OPERATOR_REVIEW':
            return {
                label: '需要人工复核',
                color: 'warning',
                tooltip: 'NEEDS_OPERATOR_REVIEW 表示需要 operator 人工复核诊断证据，不表示系统已处置。',
            };
        case 'NEEDS_EVIDENCE':
            return {
                label: '需要补证据',
                color: 'warning',
                tooltip: 'NEEDS_EVIDENCE 表示证据缺失或不足，必须 fail-closed 展示。',
            };
        case 'READY_FOR_OPERATOR_REVIEW':
            return {
                label: '可人工复核，非交易授权',
                color: 'processing',
                tooltip: 'READY_FOR_OPERATOR_REVIEW 只表示材料可给 operator 人工复核，不表示可交易。',
            };
        case 'BLOCKED':
            return {
                label: '阻断',
                color: 'error',
                tooltip: 'BLOCKED 表示诊断阻断，不能自动处置或触发交易动作。',
            };
        case 'CLOSED_RECOMMENDATION':
            return {
                label: '诊断建议已形成，非自动处置',
                color: 'processing',
                tooltip: 'CLOSED_RECOMMENDATION 只表示建议已形成，不表示处置完成或交易放行。',
            };
        case 'ACKNOWLEDGED_RECOMMENDATION':
            return {
                label: '建议人工确认诊断事实',
                color: 'processing',
                tooltip: 'ACKNOWLEDGED_RECOMMENDATION 只表示形成确认建议，不表示系统已自动确认或处置。',
            };
        case 'ESCALATED_RECOMMENDATION':
            return {
                label: '建议人工升级复核',
                color: 'warning',
                tooltip: 'ESCALATED_RECOMMENDATION 只表示建议升级人工复核，不表示系统已执行升级。',
            };
        case 'VALIDATION_READY':
            return {
                label: '验证材料可复核，非交易授权',
                color: 'processing',
                tooltip: 'VALIDATION_READY 只表示材料可进入人工复核，不表示策略批准或交易授权。',
            };
        case 'REVIEW_NEEDED':
            return {
                label: '需要复核',
                color: 'warning',
                tooltip: 'REVIEW_NEEDED 表示需要人工 review，不表示自动处置或交易授权。',
            };
        case 'NEEDS_REVIEW':
            return {
                label: '需要人工查看',
                color: 'warning',
                tooltip: 'NEEDS_REVIEW 表示仍需人工检查 evidence、blockers、warnings 与 nextSteps。',
            };
        case 'ACKNOWLEDGE_RECOMMENDED':
            return {
                label: '建议人工确认，非自动处置',
                color: 'processing',
                tooltip: 'ACKNOWLEDGE_RECOMMENDED 只表示建议人工确认诊断事实，不表示系统已处置。',
            };
        case 'ESCALATE_RECOMMENDED':
            return {
                label: '建议人工升级复核，非系统已升级',
                color: 'warning',
                tooltip: 'ESCALATE_RECOMMENDED 只表示建议人工升级复核，不表示系统已执行升级。',
            };
        case 'CLOSEOUT_RECOMMENDED':
            return {
                label: '建议形成诊断闭环，非真实关闭',
                color: 'processing',
                tooltip: 'CLOSEOUT_RECOMMENDED 只表示形成诊断闭环建议，不表示真实 incident 已关闭。',
            };
        case 'REJECTED':
            return {
                label: '验证条件不满足',
                color: 'error',
                tooltip: 'REJECTED 是验证材料层面的拒绝，不表示行情方向。',
            };
        case 'STALE_EVIDENCE':
            return {
                label: '证据过期',
                color: 'warning',
                tooltip: 'STALE_EVIDENCE 表示证据新鲜度不足，需要补证据。',
            };
        case 'VALID':
            return {
                label: 'checksum 自洽，非策略有效',
                color: 'processing',
                tooltip: 'VALID checksum 只表示 artifact payload 与 checksum 自洽，不表示策略有效、ML ready、收益真实或交易授权。',
            };
        case 'INVALID':
            return {
                label: 'checksum 失败',
                color: 'error',
                tooltip: 'INVALID checksum 表示 artifact 校验失败，必须 fail-closed 展示。',
            };
        case 'NOT_CHECKED':
            return {
                label: '未检查',
                color: 'default',
                tooltip: 'NOT_CHECKED 表示 No-file baseline 或未配置 source，没有执行 artifact 校验。',
            };
        case 'PRESENT':
            return {
                label: '摘要存在，非收益结论',
                color: 'processing',
                tooltip: 'PRESENT 只表示离线 metric summary 存在，不表示真实收益、策略有效或交易授权。',
            };
        case 'INCOMPLETE':
            return {
                label: '摘要不完整',
                color: 'warning',
                tooltip: 'INCOMPLETE 表示 metric summary 不完整，必须按诊断风险展示。',
            };
        case 'FAKE_FIXTURE_ONLY':
            return {
                label: '测试 fixture，非真实表现',
                color: 'warning',
                tooltip: 'FAKE_FIXTURE_ONLY 只能解释为测试 fixture，不是真实策略表现或收益结论。',
            };
        case 'CONSISTENT':
            return {
                label: '诊断一致，非交易授权',
                color: 'processing',
                tooltip: 'CONSISTENT 只表示 Paper vs Shadow evidence 暂未发现差异，不表示盈利、批准或交易授权。',
            };
        case 'DIVERGED':
            return {
                label: 'Paper / Shadow 证据不一致',
                color: 'warning',
                tooltip: 'DIVERGED 只表示本地 Paper vs Shadow 证据不一致，需要复核，不表示行情方向或自动处置。',
            };
        case 'NOT_COMPARABLE':
            return {
                label: '不可比较',
                color: 'warning',
                tooltip: 'NOT_COMPARABLE 表示比较基础不足或 schema 不兼容，必须 fail-closed 展示。',
            };
        case 'FAILED':
            return {
                label: '诊断失败',
                color: 'error',
                tooltip: 'FAILED 表示一致性诊断读取或计算失败，需要排查，不表示自动处置。',
            };
        case 'NO_REPORT':
            return {
                label: '无一致性报告',
                color: 'default',
                tooltip: 'NO_REPORT 表示缺少本地 consistency report，页面不会自动创建 report。',
            };
        case 'NO_DECISION':
            return {
                label: '无判断',
                color: 'default',
                tooltip: 'NO_DECISION 表示当前无法形成验证材料判断。',
            };
        case 'FRESH':
            return {
                label: '证据新鲜，仍需复核',
                color: 'processing',
                tooltip: 'FRESH 只描述 evidence freshness，不代表收益、批准或授权。',
            };
        case 'STALE':
            return {
                label: '证据过期',
                color: 'warning',
                tooltip: 'STALE 表示 evidence 需要刷新或补齐。',
            };
        case 'MISSING':
            return {
                label: '证据缺失',
                color: 'warning',
                tooltip: 'MISSING 表示缺少 evidence，不得显示为通过。',
            };
        case 'PARTIAL':
            return {
                label: '证据部分可见',
                color: 'warning',
                tooltip: 'PARTIAL 表示证据不完整，仍需人工补充或确认。',
            };
        case 'NONE':
            return {
                label: '无诊断优先级',
                color: 'default',
                tooltip: 'NONE 表示无当前诊断优先级，不代表流程完成。',
            };
        case 'INFO':
            return {
                label: '普通诊断信息',
                color: 'processing',
                tooltip: 'INFO 只表示普通诊断信息。',
            };
        case 'WARNING':
            return {
                label: '诊断警告',
                color: 'warning',
                tooltip: 'WARNING 表示需要查看的诊断警告。',
            };
        case 'HIGH':
            return {
                label: '高诊断优先级',
                color: 'error',
                tooltip: 'HIGH 只表示诊断优先级高，不表示自动处置或交易状态。',
            };
        case 'CRITICAL':
            return {
                label: '严重诊断优先级',
                color: 'error',
                tooltip: 'CRITICAL 只表示需要优先复核，不表示自动处置完成。',
            };
        default:
            return {
                label: normalized === 'UNKNOWN' ? '未知状态' : normalized,
                color: normalized === 'UNKNOWN' ? 'default' : statusPresentation(normalized).tone === 'danger' ? 'error' : TONE_TO_COLOR[statusPresentation(normalized).tone],
                tooltip: '未知或未专门映射的状态按 fail-closed 展示，不能解释为授权或成功。',
            };
    }
}

function WorkflowStatusTag({status}: { status?: string | null }) {
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
    const [form] = Form.useForm<StrategyValidationQuery>();

    useEffect(() => {
        form.setFieldsValue(initialValues);
    }, [form, initialValues]);

    return (
        <Card className="page-section" variant="borderless" title="只读查询条件">
            <Form<StrategyValidationQuery>
                form={form}
                layout="vertical"
                initialValues={initialValues}
                onFinish={(values) => onSubmit(normalizeQuery(values))}
            >
                <div style={{display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12}}>
                    {QUERY_FIELDS.map((field) => (
                        <Form.Item key={field} label={FIELD_LABELS[field]} name={field}>
                            <Input allowClear placeholder={`输入 ${FIELD_LABELS[field]}`}/>
                        </Form.Item>
                    ))}
                </div>
                <Space size={8} wrap>
                    <Button type="primary" htmlType="submit" icon={<SearchOutlined/>} loading={loading}>
                        查询只读证据
                    </Button>
                    <Button
                        icon={<ClearOutlined/>}
                        onClick={() => {
                            form.resetFields();
                            onReset();
                        }}
                    >
                        清空
                    </Button>
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
    return (
        <Card
            className="page-section"
            variant="borderless"
            title={title}
            extra={submitted ? (
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    刷新
                </Button>
            ) : null}
        >
            {!submitted ? (
                <Empty description="尚未提交只读查询条件"/>
            ) : query.isLoading ? (
                <Skeleton active paragraph={{rows: 6}}/>
            ) : query.isError ? (
                <Alert
                    type="error"
                    showIcon
                    message={`${title} 查询失败`}
                    description={(
                        <Paragraph style={{marginBottom: 0}}>
                            该结果按不可用处理，不会显示为通过或授权。{formatApiError(query.error as AppApiError)}
                        </Paragraph>
                    )}
                />
            ) : !query.data ? (
                <Empty description="只读 API 暂无可展示数据"/>
            ) : (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Space size={8} wrap>
                        <StatusTag status={status}/>
                        <Text type="secondary">{subtitle}</Text>
                    </Space>
                    <Alert type="info" showIcon message="只读边界" description={boundaryDescription}/>
                    {isProblemStatus(status) ? (
                        <Alert
                            type="warning"
                            showIcon
                            message="查询结果不是通过态"
                            description="UNKNOWN / NOT_AVAILABLE / NOT_IMPLEMENTED / BLOCKED 状态不会显示为成功；请先处理 blockers 与 nextSteps。"
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Required evidence</Text>
                <Table<StrategyValidationEvidence>
                    size="small"
                    rowKey={(record) => record.code}
                    columns={evidenceColumns}
                    dataSource={requiredEvidence}
                    pagination={false}
                    scroll={{x: 720}}
                    locale={{emptyText: '暂无 required evidence'}}
                />
            </div>
            <div>
                <Text strong>Missing evidence</Text>
                <Table<StrategyValidationEvidence>
                    size="small"
                    rowKey={(record) => record.code}
                    columns={evidenceColumns}
                    dataSource={missingEvidence}
                    pagination={false}
                    scroll={{x: 720}}
                    locale={{emptyText: '暂无 missing evidence'}}
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Blockers</Text>
                <Table<StrategyValidationReason>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}`}
                    columns={reasonColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 760}}
                    locale={{emptyText: '暂无 blockers'}}
                />
            </div>
            <div>
                <Text strong>Warnings</Text>
                <Table<StrategyValidationReason>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}`}
                    columns={reasonColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 760}}
                    locale={{emptyText: '暂无 warnings'}}
                />
            </div>
        </Space>
    );
}

function NextStepsList({nextSteps}: { nextSteps: string[] }) {
    if (!nextSteps.length) {
        return (
            <Alert
                type="info"
                showIcon
                message="Next steps"
                description="当前响应未返回 nextSteps；页面不会把缺失下一步解释为已完成。"
            />
        );
    }
    return (
        <Alert
            type="warning"
            showIcon
            message="Next steps"
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
            message: 'Strategy Validation overview 暂无数据',
            description: '当前只读响应为空；页面不会补造 evidence，也不会把空态解释为验证通过。',
        };
    }
    if (overviewHasNoEvidence(overview)) {
        return {
            level: 'warning',
            message: 'Strategy Validation overview 缺少 evidence',
            description: 'NO_EVIDENCE / 无 evidence anchors 表示验证证据不足，需要先补齐只读事实来源。',
        };
    }
    if (decision === 'BLOCKED' || countValue(overview.blocked) > 0 || overview.blockers.length > 0) {
        return {
            level: 'error',
            message: 'Strategy Validation overview 被阻断',
            description: 'blocked 只表示 validation 诊断链路阻断，需要处理 blockers；不代表交易状态变化。',
        };
    }
    if (decision === 'REJECTED' || countValue(overview.rejectedForValidation) > 0) {
        return {
            level: 'error',
            message: 'Strategy Validation overview 被拒绝',
            description: 'REJECTED 表示 validation 层证据不满足进入后续 review 的条件，不是交易方向或行情判断。',
        };
    }
    if (decision === 'NEEDS_REVIEW' || countValue(overview.needsReview) > 0 || decision === 'STALE_EVIDENCE') {
        return {
            level: 'warning',
            message: 'Strategy Validation overview 需要复核',
            description: '需要人工检查 decisionReasons、limitations、warnings 与 nextSteps；不得解释为放行。',
        };
    }
    if (decision === 'APPROVED' || countValue(overview.approvedForValidation) > 0) {
        return {
            level: 'info',
            message: 'Strategy Validation overview 验证层通过',
            description: 'APPROVED 只表示 validation evidence 暂时满足后续 review，不表示交易授权、LIVE enable 或实盘就绪。',
        };
    }
    return {
        level: 'info',
        message: 'Strategy Validation overview 已加载',
        description: '当前结果只用于只读 validation 诊断，不产生任何交易或运行副作用。',
    };
}

function BoundaryBadge({label, tooltip, color}: { label: string; tooltip: string; color?: string }) {
    return (
        <Tooltip title={tooltip}>
            <Tag color={color}>{label}</Tag>
        </Tooltip>
    );
}

function StrategyValidationOverviewBoundaryBadges({overview}: { overview?: StrategyValidationOverviewResponse }) {
    const pending = overview ? '' : '；overview 尚未返回时按 fail-closed 展示';
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED（LIVE 关闭）"
                tooltip={`liveDisabled=true，页面不得展示 LIVE 可用或实盘就绪${pending}`}
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                tooltip={`realProviderImplemented=false，不存在真实 provider 可用结论${pending}`}
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                tooltip={`privateTradingImplemented=false，不存在下单、撤单、转账或提现入口${pending}`}
            />
            <BoundaryBadge
                color="warning"
                label="Validation is not trading authorization（验证不是交易授权）"
                tooltip={`validation 结果仅用于 review，不能解释为交易授权${pending}`}
            />
            <BoundaryBadge
                color="error"
                label="Not trading authorization（非交易授权）"
                tooltip={`notTradingAuthorization=true，APPROVED 也不能解释为可交易${pending}`}
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                tooltip={`aiDhRuntimeIntegrated=false，不表示 AI started 或 DH integrated${pending}`}
            />
        </Space>
    );
}

function OverviewBoundaryDriftAlert({overview}: { overview?: StrategyValidationOverviewResponse }) {
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
            message="Overview boundary flags 与当前安全基线不一致"
            description="页面按 fail-closed 处理该响应；本前端不会把异常 flags 展示成可交易、可执行或实盘就绪。"
        />
    ) : null;
}

function OverviewCounts({overview}: { overview?: StrategyValidationOverviewResponse }) {
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Descriptions size="small" bordered column={{xs: 1, sm: 1, md: 2}}>
                <Descriptions.Item label="latestDecision.decision">
                    <Tooltip title="APPROVED 只表示 validation 层面通过，不表示交易授权。">
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
                emptyText="暂无 decisionReasons；不能补造通过原因。"
            />
            <TextList
                title="limitations"
                items={latestDecision?.limitations ?? []}
                emptyText="暂无 limitations；仍需遵守固定安全边界。"
            />
        </Space>
    );
}

function TextList({title, items, emptyText}: { title: string; items: string[]; emptyText: string }) {
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Blockers</Text>
                <Table<StrategyValidationBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={overviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 blockers；仍需遵守固定安全边界。'}}
                />
            </div>
            <div>
                <Text strong>Warnings</Text>
                <Table<StrategyValidationWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={overviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 warnings；仍需遵守固定安全边界。'}}
                />
            </div>
        </Space>
    );
}

function StrategyValidationOverviewPanel({query}: { query: PanelQueryState<StrategyValidationOverviewResponse> }) {
    const overview = query.data;
    const overviewState = overview ? resolveOverviewState(overview) : null;
    const stateAlertType = overviewState?.level ?? 'info';

    return (
        <Card
            className="page-section"
            variant="borderless"
            title="Strategy Validation Overview"
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    刷新 overview
                </Button>
            )}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费 GET /api/strategy-validation/overview；用于 validation runtime baseline 总览，不新增
                    route、Dashboard v2 或写侧动作。
                </Paragraph>
                <StrategyValidationOverviewBoundaryBadges overview={overview}/>
                <OverviewBoundaryDriftAlert overview={overview}/>
                <OverviewCounts overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message="Strategy Validation overview 查询失败"
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                overview
                                失败时按不可用处理，不会显示为通过、授权或可执行。{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description="暂无 Strategy Validation overview 响应；固定安全边界仍按 fail-closed 展示。"/>
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
                                message="No evidence / 无证据"
                                description="没有 evidenceAnchors 或 decision=NO_EVIDENCE 时，页面只展示缺证据状态，不补造 evidence。"
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
                            locale={{emptyText: '暂无 nextSteps；不能解释为已经允许交易。'}}
                        />
                        <Table<StrategyValidationEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.checksum ?? 'none'}`}
                            columns={overviewEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1030}}
                            locale={{emptyText: '暂无 evidenceAnchors；不能解释为证据完整。'}}
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
            message: 'Shadow Validation Workflow 暂无 operator items 或缺少 evidence',
            description: 'empty / no evidence 表示没有足够本地事实支撑复核；页面不会补造 operator item，也不会显示为通过。',
        };
    }
    if (shadowWorkflowBlocked(overview)) {
        return {
            level: 'error',
            message: 'Shadow Validation Workflow 被阻断',
            description: 'BLOCKED 只表示诊断阻断，需要处理 blockers；不代表交易状态、风险处置或自动关闭。',
        };
    }
    if (shadowWorkflowNeedsEvidence(overview)) {
        return {
            level: 'warning',
            message: 'Shadow Validation Workflow 需要补证据',
            description: 'NEEDS_EVIDENCE / STALE_EVIDENCE / STALE / PARTIAL 表示材料不足或过期，必须先补齐只读 evidence。',
        };
    }
    if (state === 'EVIDENCE_REVIEW' || decision === 'NEEDS_REVIEW' || decision === 'REJECTED') {
        return {
            level: decision === 'REJECTED' ? 'error' : 'warning',
            message: 'Shadow Validation Workflow 处于 evidence review',
            description: 'EVIDENCE_REVIEW / NEEDS_REVIEW / REJECTED 均要求人工查看证据、警告和下一步；不表示交易授权。',
        };
    }
    if (state === 'READY_FOR_OPERATOR_REVIEW' || decision === 'VALIDATION_READY') {
        return {
            level: 'info',
            message: 'Shadow Validation Workflow 可进入人工复核',
            description: 'READY_FOR_OPERATOR_REVIEW / VALIDATION_READY 只表示验证材料可人工复核，不表示可交易、已批准或 LIVE 可用。',
        };
    }
    if (state === 'CLOSED_RECOMMENDATION') {
        return {
            level: 'info',
            message: 'Shadow Validation Workflow 已形成诊断建议',
            description: 'CLOSED_RECOMMENDATION 只表示诊断建议已形成，不表示自动处置完成或交易链路已放行。',
        };
    }
    return {
        level: 'info',
        message: 'Shadow Validation Workflow 已加载',
        description: '当前结果只用于只读 operator review 诊断，不产生任何交易、运行或持久化副作用。',
    };
}

function ShadowValidationWorkflowBoundaryBadges({overview}: { overview?: ShadowValidationWorkflowOverviewResponse }) {
    const pending = overview ? '' : '；overview 尚未返回时按 fail-closed 展示';
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED（LIVE 关闭）"
                tooltip={`liveDisabled=true；本 workflow 不表示 LIVE 可用${pending}`}
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                tooltip={`realProviderImplemented=false；本 workflow 不调用真实 provider${pending}`}
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                tooltip={`privateTradingImplemented=false；本 workflow 不提供下单、撤单、转账或提现入口${pending}`}
            />
            <BoundaryBadge
                color="warning"
                label="Validation workflow is diagnostic only（验证 workflow 仅诊断）"
                tooltip={`diagnosticOnly=true；operator items 是 derived 诊断条目，不持久化、不执行${pending}`}
            />
            <BoundaryBadge
                color="error"
                label="Not trading authorization（非交易授权）"
                tooltip={`notTradingAuthorization=true；VALIDATION_READY 也不能解释为交易授权${pending}`}
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                tooltip={`aiDhRuntimeIntegrated=false；不表示 AI started 或 DH integrated${pending}`}
            />
        </Space>
    );
}

function ShadowValidationWorkflowBoundaryDriftAlert({overview}: {
    overview?: ShadowValidationWorkflowOverviewResponse
}) {
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
            message="Shadow Validation Workflow boundary flags 与当前安全基线不一致"
            description="页面按 fail-closed 处理该响应；不会把异常 flags 展示成可执行、可交易、可处置或实盘可用。"
        />
    ) : null;
}

function ShadowValidationWorkflowCounts({overview}: { overview?: ShadowValidationWorkflowOverviewResponse }) {
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Blockers（阻断项）</Text>
                <Table<ShadowValidationBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={shadowValidationWorkflowIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 blockers；仍需遵守固定安全边界。'}}
                />
            </div>
            <div>
                <Text strong>Warnings（警告项）</Text>
                <Table<ShadowValidationWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={shadowValidationWorkflowIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 warnings；不能解释为 workflow 已完成。'}}
                />
            </div>
        </Space>
    );
}

function ShadowValidationWorkflowPanel({query}: { query: PanelQueryState<ShadowValidationWorkflowOverviewResponse> }) {
    const overview = query.data;
    const panelState = overview ? resolveShadowValidationWorkflowState(overview) : null;

    return (
        <Card
            className="page-section"
            variant="borderless"
            title="影子验证工作流总览（Shadow Validation Workflow Overview）"
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    刷新总览
                </Button>
            )}
        >
            <Space data-testid="shadow-validation-workflow-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费 GET /api/shadow-validation/workflow/overview；展示 derived operator items、workflowState、
                    validationDecision、evidenceFreshness、evidence anchors 与 traceId，不新增 route、review 动作、交易按钮或写侧请求。
                </Paragraph>
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
                        message="Shadow Validation Workflow overview 查询失败"
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                workflow overview 失败时按不可用处理，不会显示为可复核、授权、自动处置或可执行。
                                {formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty
                        description="暂无 Shadow Validation Workflow overview 响应；固定安全边界仍按 fail-closed 展示。"/>
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
                            <Empty description="暂无 operator items；空态不表示验证完成、建议关闭或可交易。"/>
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
                            locale={{emptyText: '暂无 nextSteps；不能解释为已经完成复核或处置。'}}
                        />
                        <Table<ShadowValidationEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={shadowValidationWorkflowEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: '暂无 evidenceAnchors；不能解释为证据完整。'}}
                        />
                        <Table<ShadowValidationOperatorItem>
                            size="small"
                            rowKey={(record) => record.operatorItemId}
                            columns={shadowValidationWorkflowOperatorColumns}
                            dataSource={overview.operatorItems}
                            pagination={false}
                            scroll={{x: 1550}}
                            locale={{emptyText: '暂无 operatorItems；不能补造复核条目。'}}
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
            message: 'Consistency Evidence overview 暂无 consistency evidence',
            description: 'empty / no consistency evidence 表示本地 Paper vs Shadow 证据不足；页面不会补造 evidence item，也不会创建 consistency report。',
        };
    }
    if (countValue(overview.failedCount) > 0 || comparisonStatus === 'FAILED' || countValue(overview.criticalSeverityCount) > 0 || divergenceSeverity === 'CRITICAL') {
        return {
            level: 'error',
            message: 'Consistency Evidence overview 存在 failed / critical 诊断阻断',
            description: 'FAILED / CRITICAL 只表示诊断优先级或读取计算失败，需要人工排查；不表示自动处置、交易拒绝完成或交易授权。',
        };
    }
    if (countValue(overview.highSeverityCount) > 0 || divergenceSeverity === 'HIGH') {
        return {
            level: 'warning',
            message: 'Consistency Evidence overview 存在 high diagnostic priority',
            description: 'HIGH 只表示诊断优先级高，需要优先复核 Paper vs Shadow 证据，不表示自动处置或交易状态。',
        };
    }
    if (countValue(overview.divergedCount) > 0 || comparisonStatus === 'DIVERGED') {
        return {
            level: 'warning',
            message: 'Consistency Evidence overview 存在 Paper vs Shadow 证据不一致',
            description: 'DIVERGED 只表示本地 consistency evidence 不一致，需要查看 divergenceReasons、limitations 和 anchors，不表示行情方向。',
        };
    }
    if (countValue(overview.partialCount) > 0 || countValue(overview.notComparableCount) > 0 || comparisonStatus === 'PARTIAL' || comparisonStatus === 'NOT_COMPARABLE') {
        return {
            level: 'warning',
            message: 'Consistency Evidence overview 存在 partial / not comparable evidence',
            description: 'PARTIAL / NOT_COMPARABLE 表示证据不完整或不可比较，必须按 fail-closed 展示并保留 nextSteps。',
        };
    }
    if (consistencyEvidenceHasStaleEvidence(overview)) {
        return {
            level: 'warning',
            message: 'Consistency Evidence overview 存在 stale evidence',
            description: 'STALE / MISSING / PARTIAL / UNKNOWN freshness 只描述本地 evidence 新鲜度不足，不能显示为完成或通过。',
        };
    }
    if (consistencyEvidenceHasUnknown(overview)) {
        return {
            level: 'warning',
            message: 'Consistency Evidence overview 存在 unknown 状态',
            description: 'UNKNOWN 状态按 fail-closed 处理；页面不会把未知 comparison / severity / freshness 解释为一致或可继续。',
        };
    }
    if (comparisonStatus === 'CONSISTENT' || countValue(overview.consistentCount) > 0) {
        return {
            level: 'info',
            message: 'Consistency Evidence overview 证据一致（非交易授权）',
            description: 'CONSISTENT 只表示当前本地 Paper vs Shadow evidence 未发现差异，不表示盈利、交易批准、LIVE 可用或自动处置完成。',
        };
    }
    return {
        level: 'info',
        message: 'Consistency Evidence overview 已加载',
        description: '当前结果只用于只读 consistency evidence 诊断，不产生任何交易、运行、报告创建或持久化副作用。',
    };
}

function ConsistencyEvidenceBoundaryBadges({overview}: { overview?: ConsistencyEvidenceOverviewResponse }) {
    const pending = overview ? '' : '；overview 尚未返回时按 fail-closed 展示';
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED（LIVE 关闭）"
                tooltip={`liveDisabled=true；本 consistency evidence 不表示 LIVE 可用${pending}`}
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                tooltip={`realProviderImplemented=false；本 overview 不调用真实 provider${pending}`}
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                tooltip={`privateTradingImplemented=false；本 overview 不提供下单、撤单、转账或提现入口${pending}`}
            />
            <BoundaryBadge
                color="warning"
                label="Consistency evidence is diagnostic only（一致性证据仅诊断）"
                tooltip={`diagnosticOnly=true；evidence items 是 derived 诊断条目，不持久化、不执行${pending}`}
            />
            <BoundaryBadge
                color="error"
                label="Not trading authorization（非交易授权）"
                tooltip={`notTradingAuthorization=true；CONSISTENT 也不能解释为交易授权${pending}`}
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                tooltip={`aiDhRuntimeIntegrated=false；不表示 AI started 或 DH integrated${pending}`}
            />
        </Space>
    );
}

function ConsistencyEvidenceBoundaryDriftAlert({overview}: { overview?: ConsistencyEvidenceOverviewResponse }) {
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
            message="Consistency Evidence boundary flags 与当前安全基线不一致"
            description="页面按 fail-closed 处理该响应；不会把异常 flags、raw metricDelta、收益推断或交易信号展示成可执行、可交易或实盘可用。"
        />
    ) : null;
}

function ConsistencyEvidenceCounts({overview}: { overview?: ConsistencyEvidenceOverviewResponse }) {
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
    if (!item) {
        return <Empty description="暂无 latestEvidenceItem；空态不表示 evidence 完整、可比较或可交易。"/>;
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
                        {String(Boolean(summary?.rawMetricDeltaExposed))}（raw metricDelta 不应暴露）
                    </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="profitConclusionInferred">
                    <Tag color={summary?.profitConclusionInferred ? 'error' : 'default'}>
                        {String(Boolean(summary?.profitConclusionInferred))}（不推断收益结论）
                    </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="tradingSignalInferred">
                    <Tag color={summary?.tradingSignalInferred ? 'error' : 'default'}>
                        {String(Boolean(summary?.tradingSignalInferred))}（不生成交易信号）
                    </Tag>
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
                locale={{emptyText: '暂无 topDeltaMetrics；不能补造 metric delta 或收益结论。'}}
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Blockers（阻断项）</Text>
                <Table<ConsistencyEvidenceBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={consistencyEvidenceIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 blockers；仍需遵守固定安全边界。'}}
                />
            </div>
            <div>
                <Text strong>Warnings（警告项）</Text>
                <Table<ConsistencyEvidenceWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={consistencyEvidenceIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 warnings；不能解释为 consistency evidence 已完成。'}}
                />
            </div>
        </Space>
    );
}

function ConsistencyEvidenceOverviewPanel({query}: { query: PanelQueryState<ConsistencyEvidenceOverviewResponse> }) {
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
            title="一致性证据总览（Consistency Evidence Overview）"
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    刷新证据
                </Button>
            )}
        >
            <Space data-testid="consistency-evidence-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费 GET /api/paper-shadow/consistency/evidence/overview；展示 evidence counts、
                    latestEvidenceItem、severityBuckets、freshnessSummary、metricDeltaSummary、blockers / warnings /
                    nextSteps、evidenceAnchors 与 traceId，不新增 route、review 动作、交易按钮或写侧请求。
                </Paragraph>
                <Alert
                    type="info"
                    showIcon
                    message="文案边界"
                    description="CONSISTENT 不表示盈利或可交易；DIVERGED 只表示 Paper vs Shadow 证据不一致；HIGH / CRITICAL 只表示诊断优先级；metricDelta 只显示诊断差异摘要。"
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
                        message="Consistency Evidence overview 查询失败"
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                consistency evidence overview 失败时按不可用处理，不会显示为一致、授权、自动处置或可执行。
                                {formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description="暂无 Consistency Evidence overview 响应；固定安全边界仍按 fail-closed 展示。"/>
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
                            <Empty description="暂无 consistency evidence；空态不表示证据完整、可比较、已复核或可交易。"/>
                        ) : null}
                        <ConsistencyEvidenceLatestItem item={overview.latestEvidenceItem}/>
                        <div>
                            <Text strong>Evidence summaries（证据摘要）</Text>
                            <Table<ConsistencyEvidenceBucketRow>
                                size="small"
                                rowKey={(record) => record.key}
                                columns={consistencyEvidenceBucketColumns}
                                dataSource={bucketRows}
                                pagination={false}
                                scroll={{x: 560}}
                                locale={{emptyText: '暂无 severityBuckets / freshnessSummary；不能补造桶统计。'}}
                            />
                        </div>
                        <div>
                            <Text strong>metricDeltaSummary（诊断差异摘要）</Text>
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
                            locale={{emptyText: '暂无 nextSteps；不能解释为已经完成复核或处置。'}}
                        />
                        <Table<ConsistencyEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={consistencyEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: '暂无 evidenceAnchors；不能解释为证据完整。'}}
                        />
                        <Table<ConsistencyEvidenceItem>
                            size="small"
                            rowKey={(record) => record.evidenceItemId}
                            columns={consistencyEvidenceItemColumns}
                            dataSource={overview.evidenceItems}
                            pagination={false}
                            scroll={{x: 1660}}
                            locale={{emptyText: '暂无 evidenceItems；不能补造 consistency evidence。'}}
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
            message: 'Incident / Replay Review overview 暂无 review evidence',
            description: 'empty / no review evidence 表示没有足够本地事实支撑复核；页面不会补造 review item，也不会显示为已确认、已升级或已关闭。',
        };
    }
    if (incidentReplayReviewBlocked(overview)) {
        return {
            level: 'error',
            message: 'Incident / Replay Review overview 被阻断',
            description: 'BLOCKED 只表示诊断复核阻断，需要处理 blockers；不代表交易状态变化、风险处置完成或真实 incident 关闭。',
        };
    }
    if (priority === 'critical') {
        return {
            level: 'error',
            message: 'Incident / Replay Review overview 存在 CRITICAL diagnostic priority',
            description: 'CRITICAL 只表示诊断优先级严重，需要人工优先复核；不表示系统自动处置、自动升级、真实 incident 已关闭或交易授权。',
        };
    }
    if (priority === 'high') {
        return {
            level: 'warning',
            message: 'Incident / Replay Review overview 存在 HIGH diagnostic priority',
            description: 'HIGH 只表示诊断优先级高，需要人工查看 review items、blockers、warnings 与 nextSteps；不表示自动处置或交易状态。',
        };
    }
    if (incidentReplayReviewHasStaleEvidence(overview)) {
        return {
            level: 'warning',
            message: 'Incident / Replay Review overview 存在 stale evidence',
            description: 'STALE_EVIDENCE / STALE / MISSING / PARTIAL / UNKNOWN freshness 只描述本地 evidence 新鲜度不足，必须先补齐只读事实来源。',
        };
    }
    if (state === 'EVIDENCE_REVIEW' || state === 'NEEDS_OPERATOR_REVIEW' || decision === 'REVIEW_NEEDED') {
        return {
            level: 'warning',
            message: 'Incident / Replay Review overview 需要人工复核',
            description: 'EVIDENCE_REVIEW / NEEDS_OPERATOR_REVIEW / REVIEW_NEEDED 均只表示需要人工查看诊断事实，不表示系统已处置。',
        };
    }
    if (state === 'ACKNOWLEDGED_RECOMMENDATION' || decision === 'ACKNOWLEDGE_RECOMMENDED') {
        return {
            level: 'info',
            message: 'Incident / Replay Review overview 建议人工确认',
            description: 'ACKNOWLEDGE_RECOMMENDED 只表示建议人工确认诊断事实，不表示系统已确认、自动处置或交易授权。',
        };
    }
    if (state === 'ESCALATED_RECOMMENDATION' || decision === 'ESCALATE_RECOMMENDED') {
        return {
            level: 'warning',
            message: 'Incident / Replay Review overview 建议人工升级复核',
            description: 'ESCALATE_RECOMMENDED 只表示建议人工升级复核，不表示系统已执行升级或触发外部流程。',
        };
    }
    if (state === 'CLOSED_RECOMMENDATION' || decision === 'CLOSEOUT_RECOMMENDED') {
        return {
            level: 'info',
            message: 'Incident / Replay Review overview 已形成诊断闭环建议',
            description: 'CLOSED_RECOMMENDATION / CLOSEOUT_RECOMMENDED 只表示诊断闭环建议，不表示真实 incident 已关闭或自动处置完成。',
        };
    }
    if (state === 'INTAKE') {
        return {
            level: 'info',
            message: 'Incident / Replay Review overview 处于 intake',
            description: 'INTAKE 只表示 derived review item 已进入诊断复核视图，不表示已经形成处置或关闭建议。',
        };
    }
    return {
        level: 'info',
        message: 'Incident / Replay Review overview 已加载',
        description: '当前结果只用于只读 incident / replay review 诊断，不产生 review 持久化、升级、关闭、交易或运行副作用。',
    };
}

function IncidentReplayReviewBoundaryBadges({overview}: { overview?: IncidentReplayReviewOverviewResponse }) {
    const pending = overview ? '' : '；overview 尚未返回时按 fail-closed 展示';
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED（LIVE 关闭）"
                tooltip={`liveDisabled=true；本 review overview 不表示 LIVE 可用${pending}`}
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                tooltip={`realProviderImplemented=false；本 review overview 不调用真实 provider${pending}`}
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                tooltip={`privateTradingImplemented=false；本 review overview 不提供下单、撤单、转账或提现入口${pending}`}
            />
            <BoundaryBadge
                color="warning"
                label="Incident / Replay review is diagnostic only（Incident / Replay review 仅诊断）"
                tooltip={`diagnosticOnly=true；review items 是 derived 诊断条目，不持久化、不自动处置${pending}`}
            />
            <BoundaryBadge
                color="error"
                label="Not trading authorization（非交易授权）"
                tooltip={`notTradingAuthorization=true；ACKNOWLEDGE / ESCALATE / CLOSED recommendation 都不能解释为交易授权${pending}`}
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                tooltip={`aiDhRuntimeIntegrated=false；不表示 AI started 或 DH integrated${pending}`}
            />
        </Space>
    );
}

function IncidentReplayReviewBoundaryDriftAlert({overview}: { overview?: IncidentReplayReviewOverviewResponse }) {
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
            message="Incident / Replay Review boundary flags 与当前安全基线不一致"
            description="页面按 fail-closed 处理该响应；不会把异常 flags 展示成已确认、已升级、已关闭、可交易、可执行或实盘可用。"
        />
    ) : null;
}

function IncidentReplayReviewCounts({overview}: { overview?: IncidentReplayReviewOverviewResponse }) {
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
    if (!item) {
        return <Empty description="暂无 latestReviewItem；空态不表示 review evidence 已完成、已确认、已升级或可交易。"/>;
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Blockers（阻断项）</Text>
                <Table<IncidentReplayReviewBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentReplayReviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 blockers；仍需遵守固定安全边界。'}}
                />
            </div>
            <div>
                <Text strong>Warnings（警告项）</Text>
                <Table<IncidentReplayReviewWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentReplayReviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 warnings；不能解释为 review 已完成、已确认或已关闭。'}}
                />
            </div>
        </Space>
    );
}

function IncidentReplayReviewOverviewPanel({query}: {
    query: PanelQueryState<IncidentReplayReviewOverviewResponse>
}) {
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
            title="事件回放复核总览（Incident / Replay Review Overview）"
        >
            <Space data-testid="incident-replay-review-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费 GET /api/incidents/replay/review/overview；展示 review counts、latestReviewItem、
                    reviewItems、severityBuckets、freshnessSummary、blockers / warnings / nextSteps、
                    evidenceAnchors 与 traceId，不新增 route、review / acknowledge / escalate / closeout 写侧、
                    交易按钮或执行请求。
                </Paragraph>
                <Alert
                    type="info"
                    showIcon
                    message="文案边界"
                    description="ACKNOWLEDGE_RECOMMENDED 只表示建议人工确认诊断事实；ESCALATE_RECOMMENDED 只表示建议人工升级复核；CLOSEOUT_RECOMMENDED / CLOSED_RECOMMENDATION 只表示诊断闭环建议；HIGH / CRITICAL 只表示诊断优先级。"
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
                        message="Incident / Replay Review overview 查询失败"
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                review overview 失败时按不可用处理，不会显示为已确认、已升级、已关闭、授权、
                                自动处置或可执行。{formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description="暂无 Incident / Replay Review overview 响应；固定安全边界仍按 fail-closed 展示。"/>
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
                            <Empty description="暂无 review evidence；空态不表示 incident 已关闭、建议已确认或可交易。"/>
                        ) : null}
                        <IncidentReplayReviewLatestItem item={overview.latestReviewItem}/>
                        <div>
                            <Text strong>Review summaries（复核摘要）</Text>
                            <Table<IncidentReplayReviewBucketRow>
                                size="small"
                                rowKey={(record) => record.key}
                                columns={incidentReplayReviewBucketColumns}
                                dataSource={bucketRows}
                                pagination={false}
                                scroll={{x: 560}}
                                locale={{emptyText: '暂无 severityBuckets / freshnessSummary；不能补造桶统计。'}}
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
                            locale={{emptyText: '暂无 nextSteps；不能解释为已经完成复核、升级、关闭或处置。'}}
                        />
                        <Table<IncidentReplayReviewEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={incidentReplayReviewEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: '暂无 evidenceAnchors；不能解释为证据完整。'}}
                        />
                        <Table<IncidentReplayReviewItem>
                            size="small"
                            rowKey={(record) => record.reviewItemId}
                            columns={incidentReplayReviewItemColumns}
                            dataSource={overview.reviewItems}
                            pagination={false}
                            scroll={{x: 1820}}
                            locale={{emptyText: '暂无 reviewItems；不能补造复核条目。'}}
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
            message: 'Evaluation Artifact Preview checksum failed',
            description: 'checksum invalid / failed 只能解释为 artifact 校验失败，必须 fail-closed；VALID checksum 也不代表策略有效、ML ready 或交易授权。',
        };
    }
    if (evaluationArtifactPreviewIsNoFileBaseline(overview)) {
        return {
            level: 'warning',
            message: '当前未配置 artifact source',
            description: '当前评估产物预览采用 No-file baseline：不读取 artifact 文件或 manifest，不接受路径或上传，不执行 Python，不导入 DB。',
        };
    }
    if (evaluationArtifactPreviewHasChecksumMissing(overview)) {
        return {
            level: 'warning',
            message: 'Evaluation Artifact Preview checksum missing',
            description: 'checksum missing 表示没有足够完整性证据，页面不会把 artifact 显示为有效、可发布或可执行。',
        };
    }
    if (evaluationArtifactPreviewHasFakeFixture(overview)) {
        return {
            level: 'warning',
            message: 'Evaluation Artifact Preview 包含 fake fixture',
            description: 'FAKE_FIXTURE_ONLY 只表示测试 fixture，不是真实策略表现、真实收益或 live execution readiness。',
        };
    }
    if (evaluationArtifactPreviewHasStaleArtifact(overview)) {
        return {
            level: 'warning',
            message: 'Evaluation Artifact Preview 存在 stale artifact',
            description: 'STALE 只表示 artifact freshness 不足，必须补齐受控来源后复核，不能显示为 ready。',
        };
    }
    if (evaluationArtifactPreviewHasUnknown(overview)) {
        return {
            level: 'warning',
            message: 'Evaluation Artifact Preview 存在 unknown fail-closed 状态',
            description: 'UNKNOWN / NOT_CHECKED 说明当前无法确认 schema、checksum 或 metric 覆盖，页面按 fail-closed 处理。',
        };
    }
    return {
        level: 'info',
        message: 'Evaluation Artifact Preview 已加载',
        description: '当前结果只用于 Python offline artifact 诊断预览，不表示 ML ready、live execution ready、交易授权或真实收益。',
    };
}

function EvaluationArtifactPreviewBoundaryBadges({overview}: {
    overview?: PythonEvaluationArtifactPreviewOverviewResponse
}) {
    const pending = overview ? '' : '；overview 尚未返回时按 fail-closed 展示';
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED（LIVE 关闭）"
                tooltip={`liveDisabled=true；artifact preview 不表示 LIVE 可用${pending}`}
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                tooltip={`realProviderImplemented=false；本 overview 不调用真实 provider${pending}`}
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                tooltip={`privateTradingImplemented=false；本 overview 不提供下单、撤单、转账或提现入口${pending}`}
            />
            <BoundaryBadge
                color="warning"
                label="Python artifact preview is diagnostic only（Python artifact preview 仅诊断）"
                tooltip={`diagnosticOnly=true；preview items 是离线诊断材料预览，不导入、不执行、不持久化${pending}`}
            />
            <BoundaryBadge
                color="error"
                label="Not trading authorization（非交易授权）"
                tooltip={`notTradingAuthorization=true；VALID checksum、metric summary 或 artifact preview 都不能解释为交易授权${pending}`}
            />
            <BoundaryBadge
                label="Python ML ready NO（Python ML ready 否）"
                tooltip={`pythonMlReady=false；不表示 Python ML ready${pending}`}
            />
            <BoundaryBadge
                label="Python live execution ready NO（Python live execution ready 否）"
                tooltip={`pythonLiveExecutionReady=false；不表示 Python live execution ready${pending}`}
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                tooltip={`aiDhRuntimeIntegrated=false；不表示 AI started 或 DH integrated${pending}`}
            />
        </Space>
    );
}

function EvaluationArtifactPreviewBoundaryDriftAlert({overview}: {
    overview?: PythonEvaluationArtifactPreviewOverviewResponse
}) {
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
            message="Evaluation Artifact Preview boundary flags 与当前安全基线不一致"
            description="页面按 fail-closed 处理该响应；不会把异常 flags 展示成 ML ready、live execution ready、交易授权、可执行或实盘可用。"
        />
    ) : null;
}

function EvaluationArtifactPreviewCounts({overview}: {
    overview?: PythonEvaluationArtifactPreviewOverviewResponse
}) {
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
    return (
        <Descriptions size="small" bordered column={{xs: 1, sm: 2, md: 3}}>
            <Descriptions.Item label="diagnosticOnly">
                <Tag color={overview?.diagnosticOnly === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.diagnosticOnly))}（只读诊断）
                </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="noSideEffect">
                <Tag color={overview?.noSideEffect === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.noSideEffect))}（无副作用）
                </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="notTradingAuthorization">
                <Tag color={overview?.notTradingAuthorization === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.notTradingAuthorization))}（非交易授权）
                </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="liveDisabled">
                <Tag color={overview?.liveDisabled === false ? 'error' : 'default'}>
                    {String(Boolean(overview?.liveDisabled))}（LIVE 关闭）
                </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="pythonMlReady">
                <Tag color={overview?.pythonMlReady ? 'error' : 'default'}>
                    {String(Boolean(overview?.pythonMlReady))}（Python ML ready NO）
                </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="pythonLiveExecutionReady">
                <Tag color={overview?.pythonLiveExecutionReady ? 'error' : 'default'}>
                    {String(Boolean(overview?.pythonLiveExecutionReady))}（Python live execution ready NO）
                </Tag>
            </Descriptions.Item>
        </Descriptions>
    );
}

function EvaluationArtifactPreviewLatestItem({item}: {
    item?: PythonEvaluationArtifactPreviewItem | null
}) {
    if (!item) {
        return <Empty description="当前未配置 artifact source；No-file baseline 不读取 artifact 文件、不执行 Python、不导入 DB。"/>;
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Blockers（阻断项）</Text>
                <Table<EvaluationArtifactPreviewBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={evaluationArtifactPreviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 960}}
                    locale={{emptyText: '暂无 blockers；仍需遵守固定安全边界。'}}
                />
            </div>
            <div>
                <Text strong>Warnings（警告项）</Text>
                <Table<EvaluationArtifactPreviewWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={evaluationArtifactPreviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 960}}
                    locale={{emptyText: '暂无 warnings；不能解释为 artifact source 已配置或 Python 可执行。'}}
                />
            </div>
        </Space>
    );
}

function EvaluationArtifactPreviewOverviewPanel({query}: {
    query: PanelQueryState<PythonEvaluationArtifactPreviewOverviewResponse>
}) {
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
            title="Python Evaluation Artifact Preview（No-file baseline）"
        >
            <Space data-testid="evaluation-artifact-preview-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费 GET /api/strategy-validation/evaluation-artifacts/preview/overview；展示 No-file
                    baseline、artifact preview counts、schema / checksum / metric coverage、warnings /
                    nextSteps、evidenceAnchors 与 traceId，不新增 route、上传、导入、文件路径输入、Python 执行、
                    review 写侧或交易入口。
                </Paragraph>
                <Alert
                    type="info"
                    showIcon
                    message="文案边界"
                    description="页面颜色只表示诊断状态，success 不表示盈利，danger 不表示下跌；VALID checksum 只表示 payload integrity，不表示策略有效；metricSummary 不表示真实收益；FAKE_FIXTURE_ONLY 是测试 fixture，不是真实策略表现；pythonMlReady=false 与 pythonLiveExecutionReady=false 必须保持可见。"
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
                        message="Evaluation Artifact Preview overview 查询失败"
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                artifact preview overview 失败时按不可用处理，不会显示为 artifact source 已配置、
                                Python 可执行、ML ready、live execution ready、授权或可交易。
                                {formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description="暂无 Evaluation Artifact Preview overview 响应；固定安全边界仍按 fail-closed 展示。"/>
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
                                message="checksum missing"
                                description="checksum 缺失不能显示为 artifact valid、策略有效、ML ready 或交易授权。"
                            />
                        ) : null}
                        {evaluationArtifactPreviewHasChecksumFailed(overview) ? (
                            <Alert
                                type="error"
                                showIcon
                                message="checksum invalid / failed"
                                description="checksum 失败必须 fail-closed；页面不会允许继续上传、导入、执行或交易。"
                            />
                        ) : null}
                        {evaluationArtifactPreviewHasFakeFixture(overview) ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="FAKE_FIXTURE_ONLY"
                                description="FAKE_FIXTURE_ONLY 只表示测试 fixture，不是真实策略表现、真实收益或 live execution readiness。"
                            />
                        ) : null}
                        {evaluationArtifactPreviewHasUnknown(overview) ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="unknown fail-closed"
                                description="UNKNOWN / NOT_CHECKED 表示当前无法确认 source、checksum 或 metric 覆盖；页面按 fail-closed 展示。"
                            />
                        ) : null}
                        <EvaluationArtifactPreviewLatestItem item={overview.latestArtifactPreview}/>
                        <div>
                            <Text strong>Schema / checksum / metric coverage（覆盖摘要）</Text>
                            <Table<EvaluationArtifactPreviewBucketRow>
                                size="small"
                                rowKey={(record) => record.key}
                                columns={evaluationArtifactPreviewBucketColumns}
                                dataSource={bucketRows}
                                pagination={false}
                                scroll={{x: 640}}
                                locale={{emptyText: '暂无 coverage summary；不能补造 schema / checksum / metric 状态。'}}
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
                            locale={{emptyText: '暂无 nextSteps；不能解释为 artifact source 已配置、Python 可执行或已完成。'}}
                        />
                        <Table<EvaluationArtifactPreviewEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.traceId ?? 'none'}`}
                            columns={evaluationArtifactPreviewEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1190}}
                            locale={{emptyText: '暂无 evidenceAnchors；不能解释为证据完整。'}}
                        />
                        <Table<PythonEvaluationArtifactPreviewItem>
                            size="small"
                            rowKey={(record) => record.artifactPreviewId}
                            columns={evaluationArtifactPreviewItemColumns}
                            dataSource={overview.artifactPreviews}
                            pagination={false}
                            scroll={{x: 2300}}
                            locale={{emptyText: 'No-file baseline：当前未配置 artifact source，不读取 artifact 文件、不执行 Python、不导入 DB。'}}
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
                label: 'CRITICAL（严重诊断优先级）',
                message: 'Incident / Replay overview：CRITICAL 诊断优先级',
                description: 'CRITICAL 只表示需要优先人工复核本地诊断证据；页面不会自动处置、不会授权交易、不会启动 replay。',
            };
        case 'HIGH':
            return {
                alertType: 'error',
                color: 'error',
                label: 'HIGH（高诊断优先级）',
                message: 'Incident / Replay overview：HIGH 诊断优先级',
                description: 'HIGH 只表示诊断证据需要尽快复核；不表示自动恢复、自动执行或交易授权。',
            };
        case 'WARNING':
            return {
                alertType: 'warning',
                color: 'warning',
                label: 'WARNING（诊断警告）',
                message: 'Incident / Replay overview：WARNING 诊断警告',
                description: 'WARNING 表示存在需要复核的诊断信号；不表示行情方向、收益或自动处置。',
            };
        case 'NONE':
            return {
                alertType: 'info',
                color: 'default',
                label: 'NONE（无当前诊断优先级）',
                message: 'Incident / Replay overview：暂无诊断优先级',
                description: 'NONE 只表示当前 overview 未给出 incident-like priority；仍需遵守固定安全边界。',
            };
        case 'INFO':
            return {
                alertType: 'info',
                color: 'processing',
                label: 'INFO（诊断信息）',
                message: 'Incident / Replay overview：INFO 诊断信息',
                description: 'INFO 只表示普通诊断信息；不代表通过、收益、交易授权或 LIVE readiness。',
            };
        default:
            return {
                alertType: 'warning',
                color: 'warning',
                label: `${normalized}（未知诊断优先级）`,
                message: 'Incident / Replay overview：未知诊断优先级',
                description: '未知 severity 按 fail-closed 展示，需要人工确认后端事实来源和边界语义。',
            };
    }
}

function IncidentSeverityTag({severity}: { severity?: IncidentReplaySeverity | null }) {
    const presentation = incidentSeverityPresentation(severity);
    return (
        <Tooltip title="severity 只表示诊断优先级，不表示自动处置、交易授权或实盘就绪。">
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
    const pending = overview ? '' : '；overview 尚未返回时按 fail-closed 展示';
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED（LIVE 关闭）"
                tooltip={`liveDisabled=true；Incident / Replay overview 不表示实盘就绪${pending}`}
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                tooltip={`realProviderImplemented=false；本面板不调用真实 provider${pending}`}
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                tooltip={`privateTradingImplemented=false；本面板不提供下单、撤单、转账或提现入口${pending}`}
            />
            <BoundaryBadge
                color="warning"
                label="Incident / Replay is diagnostic only（仅诊断）"
                tooltip={`diagnosticOnly=true；只聚合本地诊断证据，不创建 incident 或启动 replay${pending}`}
            />
            <BoundaryBadge
                color="error"
                label="Not trading authorization（非交易授权）"
                tooltip={`notTradingAuthorization=true；HIGH / CRITICAL 也不能解释为交易授权${pending}`}
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                tooltip={`aiDhRuntimeIntegrated=false；不表示 AI started 或 DH integrated${pending}`}
            />
        </Space>
    );
}

function IncidentReplayBoundaryDriftAlert({overview}: { overview?: IncidentReplayOverviewResponse }) {
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
            message="Incident / Replay boundary flags 与当前安全基线不一致"
            description="页面按 fail-closed 处理该响应；不会把异常 flags 展示成可执行、可交易或实盘就绪。"
        />
    ) : null;
}

function IncidentReplayCounts({overview}: { overview?: IncidentReplayOverviewResponse }) {
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
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <div>
                <Text strong>Blockers</Text>
                <Table<IncidentReplayBlocker>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentOverviewIssueColumns}
                    dataSource={blockers}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 blockers；仍需遵守固定安全边界。'}}
                />
            </div>
            <div>
                <Text strong>Warnings</Text>
                <Table<IncidentReplayWarning>
                    size="small"
                    rowKey={(record) => `${record.code}-${record.severity}-${record.sourceId ?? 'none'}`}
                    columns={incidentOverviewIssueColumns}
                    dataSource={warnings}
                    pagination={false}
                    scroll={{x: 930}}
                    locale={{emptyText: '暂无 warnings；不能解释为诊断链路已完成。'}}
                />
            </div>
        </Space>
    );
}

function IncidentReplayOverviewPanel({query}: { query: PanelQueryState<IncidentReplayOverviewResponse> }) {
    const overview = query.data;
    const severity = overview ? incidentSeverityPresentation(overview.incidentSeverity) : null;
    const sourceUnavailable = overview ? incidentReplayHasSourceUnavailable(overview) : false;
    const partialData = overview ? incidentReplayHasPartialData(overview) : false;

    return (
        <Card
            className="page-section"
            variant="borderless"
            title="Incident / Replay Overview"
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching}
                        onClick={() => query.refetch()}>
                    刷新 Incident / Replay
                </Button>
            )}
        >
            <Space data-testid="incident-replay-overview-panel" direction="vertical" size={12}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费 GET /api/incidents/replay/overview；用于聚合本地 Shadow event、consistency divergence、
                    Paper alert、recovery 与 replay 诊断证据，不创建 incident、不启动 replay、不新增任何交易动作。
                </Paragraph>
                <IncidentReplayBoundaryBadges overview={overview}/>
                <IncidentReplayBoundaryDriftAlert overview={overview}/>
                <IncidentReplayCounts overview={overview}/>
                {query.isLoading ? (
                    <Skeleton active paragraph={{rows: 8}}/>
                ) : query.isError ? (
                    <Alert
                        type="error"
                        showIcon
                        message="Incident / Replay overview 查询失败"
                        description={(
                            <Paragraph style={{marginBottom: 0}}>
                                overview 失败时按 source unavailable 处理，不会显示为通过、授权、自动处置或可执行。
                                {formatApiError(query.error as AppApiError)}
                            </Paragraph>
                        )}
                    />
                ) : !overview ? (
                    <Empty description="暂无 Incident / Replay overview 响应；固定安全边界仍按 fail-closed 展示。"/>
                ) : (
                    <>
                        {sourceUnavailable ? (
                            <Alert
                                type="error"
                                showIcon
                                message="Source unavailable / 事实源不可用"
                                description="overview 返回了不可用或缺失事实源信号；页面只展示已返回证据，不补造 counts、latestEvidence 或 nextSteps。"
                            />
                        ) : partialData ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="Partial data / 部分数据"
                                description="latestEvidence、evidenceAnchors、sourceStatus 或 severity 不完整时，面板仅展示可用事实，不把缺失数据解释为正常。"
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
                            <Empty description="暂无 Incident / Replay evidence；空态不表示事件已解决或可交易。"/>
                        ) : null}
                        <Table<IncidentReplayLatestEvidence>
                            size="small"
                            rowKey={(record) => `${record.evidenceType}-${record.sourceId ?? 'none'}-${record.traceId ?? record.occurredAt ?? 'none'}`}
                            columns={incidentLatestEvidenceColumns}
                            dataSource={overview.latestEvidence}
                            pagination={false}
                            scroll={{x: 1080}}
                            locale={{emptyText: '暂无 latestEvidence；不能补造最新证据。'}}
                        />
                        <IncidentReplayIssueTables blockers={overview.blockers} warnings={overview.warnings}/>
                        <Table<IncidentReplayNextStep>
                            size="small"
                            rowKey={(record) => record.code}
                            columns={incidentNextStepColumns}
                            dataSource={overview.nextSteps}
                            pagination={false}
                            scroll={{x: 1000}}
                            locale={{emptyText: '暂无 nextSteps；不能解释为已经完成处置。'}}
                        />
                        <Table<IncidentReplayEvidenceAnchor>
                            size="small"
                            rowKey={(record) => `${record.sourceType}-${record.sourceId ?? 'none'}-${record.checksum ?? 'none'}`}
                            columns={incidentEvidenceAnchorColumns}
                            dataSource={overview.evidenceAnchors}
                            pagination={false}
                            scroll={{x: 1030}}
                            locale={{emptyText: '暂无 evidenceAnchors；不能解释为证据完整。'}}
                        />
                    </>
                )}
            </Space>
        </Card>
    );
}

function ValidationOperationsBoundaryStrip() {
    return (
        <Space data-testid="validation-operations-boundary-strip" size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED"
                tooltip="LIVE 关闭；本 Workbench 不展示实盘就绪、交易批准或真实交易能力。"
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED"
                tooltip="真实 provider 未实现；本页不调用真实交易所。"
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED"
                tooltip="私有交易能力未实现；不提供下单、撤单、转账、提现或 private endpoint。"
            />
            <BoundaryBadge
                color="warning"
                label="Not trading authorization"
                tooltip="Validation / consistency / review / artifact preview 均不是交易授权。"
            />
            <BoundaryBadge
                label="Python ML ready NO"
                tooltip="Python artifact preview 不表示 Python ML ready。"
            />
            <BoundaryBadge
                label="Python live execution ready NO"
                tooltip="Python artifact preview 不表示 live execution ready。"
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated"
                tooltip="AI 仍 NOT STARTED；DH runtime 仍 NOT INTEGRATED。"
            />
        </Space>
    );
}

function ValidationOperationsTopSummary({queries}: { queries: ValidationOperationsQueryBundle }) {
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
        ],
    );

    return (
        <div data-testid="validation-operations-top-summary">
            <Text strong>Top summary / 验证运营总览</Text>
            <Table<ValidationOperationsSummaryRow>
                size="small"
                rowKey={(record) => record.key}
                columns={validationOperationsSummaryColumns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 1450}}
                locale={{emptyText: '暂无 summary；不能解释为验证运营已完成。'}}
            />
        </div>
    );
}

function ValidationOperationsEvidenceMatrix({queries}: { queries: ValidationOperationsQueryBundle }) {
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
        ],
    );

    return (
        <div data-testid="validation-operations-evidence-matrix">
            <Text strong>Evidence matrix / 证据矩阵</Text>
            <Table<ValidationOperationsEvidenceRow>
                size="small"
                rowKey={(record) => record.key}
                columns={validationOperationsEvidenceColumns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 1150}}
                locale={{emptyText: '暂无 evidence matrix；不能解释为证据完整。'}}
            />
        </div>
    );
}

function ValidationOperationsOperatorQueuePreview({queries}: { queries: ValidationOperationsQueryBundle }) {
    const rows = useMemo(
        () => validationOperationsOperatorQueueRows(
            queries.shadowWorkflow.data,
            queries.incidentReplayReview.data,
        ),
        [queries.shadowWorkflow.data, queries.incidentReplayReview.data],
    );

    return (
        <div data-testid="validation-operations-operator-queue">
            <Text strong>Operator queue preview / 人工复核队列预览</Text>
            <Table<ValidationOperationsOperatorQueueRow>
                size="small"
                rowKey={(record) => record.key}
                columns={validationOperationsOperatorQueueColumns}
                dataSource={rows}
                pagination={false}
                scroll={{x: 1500}}
                locale={{emptyText: '暂无 operator / review items；不能解释为已确认、已升级或已关闭。'}}
            />
        </div>
    );
}

function ValidationOperationsWorkbench({queries}: { queries: ValidationOperationsQueryBundle }) {
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
            title="Validation Operations Workbench"
            extra={(
                <Button size="small" icon={<ReloadOutlined/>} loading={isFetching} onClick={refetchWorkbench}>
                    刷新 Workbench
                </Button>
            )}
        >
            <Space data-testid="validation-operations-workbench" direction="vertical" size={14}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    汇总 Shadow Validation Workflow、Consistency Evidence、Incident / Replay Review 与 Evaluation
                    Artifact Preview 的只读诊断结果；用于人工复核排序、证据链路检查和边界确认，不新增 route、API、DB
                    migration 或交易入口。
                </Paragraph>
                <ValidationOperationsBoundaryStrip/>
                {hasError ? (
                    <Alert
                        type="error"
                        showIcon
                        message="Workbench 存在只读数据加载失败"
                        description="失败主线按 blocked / unknown fail-closed 处理；页面不会把缺失响应解释为通过、已确认或交易授权。"
                    />
                ) : null}
                {isLoading ? <Skeleton active paragraph={{rows: 8}}/> : null}
                {!isLoading && hasPartialData ? (
                    <Alert
                        type="warning"
                        showIcon
                        message="Partial data / 部分数据"
                        description="缺少任一验证运营或影子运行 overview 时，Workbench 只展示已返回事实；不会补造 evidence、review decision 或 artifact readiness。"
                    />
                ) : null}
                <ValidationOperationsTopSummary queries={queries}/>
                <ValidationOperationsEvidenceMatrix queries={queries}/>
                <ValidationOperationsOperatorQueuePreview queries={queries}/>
                <Alert
                    type="info"
                    showIcon
                    message="Detail sections 保留"
                    description="下方保留现有影子运行与验证运营只读 panel，用于查看每条主线的原始 summary、blockers、warnings、nextSteps、traceId 和 evidence anchors。"
                />
            </Space>
        </Card>
    );
}

function ValidationOperationsDetailSections({children}: { children: ReactNode }) {
    return (
        <Space data-testid="validation-operations-detail-sections" direction="vertical" size={16}
               style={{display: 'flex'}}>
            <Alert
                type="info"
                showIcon
                message="Detail sections / 只读详情区"
                description="以下 panel 保留既有只读诊断语义；Workbench summary 用于复核顺序，detail sections 用于证据展开。"
            />
            {children}
        </Space>
    );
}

function StrategyValidationShadowWorkbench({queries}: { queries: WorkbenchQueryBundle }) {
    const strategyOverview = queries.strategyOverview.data;
    const shadowOverview = queries.shadowOverview.data;
    const drilldown = queries.drilldown.data;
    const isLoading = queries.strategyOverview.isLoading || queries.shadowOverview.isLoading || queries.drilldown.isLoading;
    const hasError = queries.strategyOverview.isError || queries.shadowOverview.isError || queries.drilldown.isError;
    const hasPartialData = !strategyOverview || !shadowOverview || !queries.shadowRunId || !drilldown;
    const signalRows = useMemo(
        () => workbenchSignalRows(strategyOverview, shadowOverview, drilldown),
        [strategyOverview, shadowOverview, drilldown],
    );
    const nextStepRows = useMemo(
        () => workbenchNextStepRows(strategyOverview, shadowOverview, drilldown),
        [strategyOverview, shadowOverview, drilldown],
    );
    const evidenceRows = useMemo(
        () => workbenchEvidenceRows(strategyOverview, shadowOverview, drilldown),
        [strategyOverview, shadowOverview, drilldown],
    );

    return (
        <Card
            className="page-section"
            variant="borderless"
            title="Strategy Validation / Shadow Workbench"
            extra={(
                <Space size={8} wrap>
                    {queries.shadowRunId ? (
                        <Link to={`/strategies/shadow-runs/${queries.shadowRunId}`}>
                            <Button size="small">查看 Shadow Run detail</Button>
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
                        刷新 Workbench
                    </Button>
                </Space>
            )}
        >
            <Space data-testid="strategy-validation-shadow-workbench" direction="vertical" size={14}
                   style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    聚合 Strategy Validation overview、Shadow Run overview 与 Paper vs Shadow drilldown 的只读运营视角；不新增
                    route、不触发 runner、不接 Python artifact，也不创建任何交易动作。
                </Paragraph>
                <Space size={[8, 8]} wrap>
                    <BoundaryBadge
                        color="error"
                        label="LIVE DISABLED（LIVE 关闭）"
                        tooltip="LIVE 仍关闭；本 Workbench 不展示 live-ready 或实盘可用结论。"
                    />
                    <BoundaryBadge
                        label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                        tooltip="真实 provider 未实现；本页不调用真实交易所。"
                    />
                    <BoundaryBadge
                        label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                        tooltip="不提供下单、撤单、转账、提现或 private endpoint 能力。"
                    />
                    <BoundaryBadge
                        color="warning"
                        label="Validation is not trading authorization（验证不是交易授权）"
                        tooltip="APPROVED 只表示 validation 层通过，不表示交易授权。"
                    />
                    <BoundaryBadge
                        color="warning"
                        label="Shadow Run is diagnostic only（Shadow Run 仅诊断）"
                        tooltip="Shadow Run facts 仅用于诊断和回放，不代表 Shadow trading enabled。"
                    />
                    <BoundaryBadge
                        label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                        tooltip="AI 仍 NOT STARTED；DH runtime 仍 NOT INTEGRATED。"
                    />
                </Space>

                {hasError ? (
                    <Alert
                        type="error"
                        showIcon
                        message="Workbench 存在只读数据加载失败"
                        description="失败区块按不可用处理；页面保留已返回的 partial data，但不会把缺失数据解释为通过或授权。"
                    />
                ) : null}
                {isLoading ? <Skeleton active paragraph={{rows: 8}}/> : null}
                {!isLoading && hasPartialData ? (
                    <Alert
                        type="warning"
                        showIcon
                        message="Partial data / 部分数据"
                        description="缺少 Strategy overview、Shadow overview、shadowRunId 或 drilldown 时，Workbench 只展示可用事实；不会补造 evidence、comparison 或 nextSteps。"
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
                        <Tooltip title="APPROVED 只表示验证层通过，不表示交易授权。">
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
                        description="暂无 blockers / warnings / nextSteps / evidence anchors；不能解释为证据完整或可执行。"/>
                ) : null}
                <Table<WorkbenchSignalRow>
                    size="small"
                    rowKey={(record) => record.key}
                    columns={workbenchSignalColumns}
                    dataSource={signalRows}
                    pagination={false}
                    scroll={{x: 980}}
                    locale={{emptyText: '暂无 blockers / warnings；仍需遵守固定安全边界。'}}
                />
                <Table<WorkbenchNextStepRow>
                    size="small"
                    rowKey={(record) => record.key}
                    columns={workbenchNextStepColumns}
                    dataSource={nextStepRows}
                    pagination={false}
                    scroll={{x: 1180}}
                    locale={{emptyText: '暂无 nextSteps；不能解释为已经允许交易。'}}
                />
                <Table<WorkbenchEvidenceAnchorRow>
                    size="small"
                    rowKey={(record) => record.key}
                    columns={workbenchEvidenceAnchorColumns}
                    dataSource={evidenceRows}
                    pagination={false}
                    scroll={{x: 1190}}
                    locale={{emptyText: '暂无 evidence anchors；不能补造证据。'}}
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
    const data = query.data;
    return (
        <ResultPanel
            title="Strategy Evaluation Gate"
            subtitle="评估 gate 只说明研究与评估证据是否可进入 Shadow 评审。"
            status={data?.gateStatus}
            submitted={submitted}
            query={query}
            requiredEvidence={data?.requiredEvidence}
            missingEvidence={data?.missingEvidence}
            blockers={data?.blockers}
            warnings={data?.warnings}
            nextSteps={data?.nextSteps}
            boundaryDescription="Evaluation Gate 不代表交易授权，不代表 LIVE 已启用，也不代表策略可实盘运行。"
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
    const data = query.data;
    return (
        <ResultPanel
            title="Paper vs Shadow Comparison"
            subtitle="Paper / Shadow 对照只说明证据是否完整和是否可查看。"
            status={data?.comparisonStatus}
            submitted={submitted}
            query={query}
            requiredEvidence={data?.requiredEvidence}
            missingEvidence={data?.missingEvidence}
            blockers={data?.blockers}
            warnings={data?.warnings}
            nextSteps={data?.nextSteps}
            boundaryDescription="Paper vs Shadow Comparison 不代表交易授权，不代表 Shadow Live 已可执行，也不创建或启动 Shadow run。"
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
                    {data?.comparable ? <Tag color="processing">true（只读可比较）</Tag> :
                        <Tag color="default">false（不可比较）</Tag>}
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
    const data = query.data;
    return (
        <ResultPanel
            title="Shadow Live No-side-effect Preview"
            subtitle="Shadow Live preview 只生成无副作用预览计划，不执行策略或订单。"
            status={data?.previewStatus}
            submitted={submitted}
            query={query}
            requiredEvidence={data?.requiredEvidence}
            missingEvidence={data?.missingEvidence}
            blockers={data?.blockers}
            warnings={data?.warnings}
            nextSteps={data?.nextSteps}
            boundaryDescription="Shadow Live Preview 是 no-side-effect preview：不写库、不外联、不读取真实凭证、不提交真实订单。"
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
    return (
        <div>
            <Text strong>No-side-effect policy</Text>
            <Table<ShadowLiveSideEffectPolicy>
                size="small"
                rowKey={(record) => record.code}
                columns={sideEffectColumns}
                dataSource={policies}
                pagination={false}
                scroll={{x: 760}}
                locale={{emptyText: '暂无 sideEffectPolicy；不能解释为允许执行。'}}
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
    const scope = firstScope(submittedQuery, gate, comparison, preview);
    const artifactPreviewStatus = artifactPreview
        ? evaluationArtifactPreviewIsNoFileBaseline(artifactPreview) ? 'NO_ARTIFACT_SOURCE_CONFIGURED' : 'DIAGNOSTIC_ONLY'
        : 'UNKNOWN';
    const items: LifecycleTraceItem[] = [
        {
            key: 'strategyVersion',
            label: 'Strategy Version',
            value: scope.strategyVersionId,
            status: scope.strategyVersionId ? gate?.gateStatus ?? 'SATISFIED' : 'NOT_AVAILABLE',
            source: 'Strategy Evaluation Gate / query',
            detail: 'strategy version 是本页查询链路的主锚点。',
        },
        {
            key: 'dataset',
            label: 'Dataset',
            value: scope.datasetId,
            status: gate?.datasetQualityStatus ?? comparison?.dataQualityStatus ?? 'NOT_AVAILABLE',
            source: 'Evaluation Gate / Paper Shadow',
            detail: 'dataset 只用于评估证据追踪，不代表行情可交易。',
        },
        {
            key: 'evaluationGate',
            label: 'Evaluation Gate',
            value: scope.evaluationId,
            status: gate?.gateStatus ?? comparison?.evaluationGateStatus ?? 'NOT_AVAILABLE',
            source: 'GateQ-1 GET /api/strategies/evaluation-gate',
            detail: 'Evaluation Gate 只表示可进入 Shadow 评审的只读证据，不代表策略批准或交易授权。',
        },
        {
            key: 'publishTrace',
            label: 'Publish Trace',
            value: scope.publishId,
            status: gate?.publishTraceStatus ?? 'NOT_AVAILABLE',
            source: 'Strategy Evaluation Gate',
            detail: 'publish trace 仅为链路证据，不触发发布写侧。',
        },
        {
            key: 'paperRun',
            label: 'Paper Run',
            value: scope.paperRunId,
            status: comparison?.paperRunStatus ?? gate?.paperEvidenceStatus ?? 'NOT_AVAILABLE',
            source: 'Evaluation Gate / Paper Shadow',
            detail: 'Paper evidence 只表示 SIM/Paper 事实，不启动 Paper run。',
        },
        {
            key: 'paperShadowComparison',
            label: 'Paper / Shadow Comparison',
            value: scope.shadowRunId,
            status: comparison?.comparisonStatus ?? 'NOT_AVAILABLE',
            source: 'GateQ-2 GET /api/strategies/paper-shadow/comparison',
            detail: '只读对照只说明是否可比较；Shadow 缺失、未知或未实现不能显示为成功。',
        },
        {
            key: 'shadowLivePreview',
            label: 'Shadow Live Preview',
            value: scope.shadowRunId,
            status: preview?.previewStatus ?? comparison?.shadowRunStatus ?? 'NOT_AVAILABLE',
            source: 'GateQ-3 GET /api/strategies/shadow-live/preview',
            detail: 'Shadow Live Preview 是 no-side-effect preview，不执行策略、不提交真实订单。',
        },
        {
            key: 'pythonArtifactBindingPreview',
            label: 'Python Artifact Binding Preview',
            value: artifactPreview?.traceId ?? 'NO_FILE_BASELINE',
            status: artifactPreviewStatus,
            source: 'GateT-4 GET /api/strategy-validation/evaluation-artifacts/preview/overview',
            detail: '当前页面只消费 No-file baseline overview；不读取 artifact 文件、不上传、不导入、不执行 Python、不写 Java fact-source。',
        },
    ];

    return (
        <Card className="page-section" variant="borderless" title="生命周期追溯链">
            {!submittedQuery ? (
                <Empty
                    description="提交查询后展示 strategy version -> dataset -> evaluation gate -> publish -> paper run -> Paper / Shadow Comparison -> Shadow Live Preview -> Python Artifact Binding Preview 链路"/>
            ) : (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="info"
                        showIcon
                        message="Trace path"
                        description="strategyVersion -> dataset -> evaluation -> publish -> paper -> shadow -> pythonArtifactBindingPreview。所有节点均为只读展示；缺失、未知、未实现或阻断节点不会显示为成功。"
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
    const rows = useMemo(() => [
        ...evidenceMatrixRows('Evaluation Gate', gate),
        ...evidenceMatrixRows('Paper / Shadow Comparison', comparison),
        ...evidenceMatrixRows('Shadow Live Preview', preview),
        ...evaluationArtifactPreviewMatrixRows(artifactPreview),
    ], [gate, comparison, preview, artifactPreview]);

    return (
        <Card className="page-section" variant="borderless" title="Evidence Matrix / 证据矩阵">
            {!submittedQuery ? (
                <Empty
                    description="提交查询后展示 requiredEvidence / missingEvidence / blockers / warnings / nextSteps"/>
            ) : (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="info"
                        showIcon
                        message="证据矩阵只聚合前端已收到的只读响应"
                        description="requiredEvidence、missingEvidence、blockers、warnings 与 nextSteps 仅用于追溯和评审；缺失 nextSteps 不会被解释为已完成。"
                    />
                    <Table<EvidenceMatrixRow>
                        size="small"
                        rowKey={(record) => record.key}
                        columns={evidenceMatrixColumns}
                        dataSource={rows}
                        pagination={false}
                        scroll={{x: 1080}}
                        locale={{emptyText: '暂无 evidence matrix；不能解释为证据完整。'}}
                    />
                </Space>
            )}
        </Card>
    );
}

function StatusSemantics() {
    return (
        <Card className="page-section" variant="borderless" title="状态解释">
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
    return (
        <Card className="page-section" variant="borderless" title="No-side-effect / authorization boundary">
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Alert
                    type="warning"
                    showIcon
                    message="本页仅用于策略生命周期追溯与只读证据检查"
                    description="Evaluation Gate 不代表交易授权；Paper / Shadow Comparison 不代表交易授权；Shadow Live Preview 是 no-side-effect preview，不提交真实订单；Python artifact binding preview 不代表 artifact 已入库、不代表 ML ready、不代表 live execution ready。"
                />
                <Space size={[8, 8]} wrap>
                    <Tag color="default">只读验证</Tag>
                    <Tag color="error">不代表交易授权</Tag>
                    <Tag color="error">不代表 LIVE 已启用</Tag>
                    {FORBIDDEN_BOUNDARY_ITEMS.map((item) => (
                        <Tag key={item} color="error">{item}</Tag>
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
                    message="缺失态处理"
                    description="UNKNOWN、NOT_AVAILABLE、NOT_IMPLEMENTED、PENDING_FRONTEND_SUPPORT 与 BLOCKED_* 均按非成功态展示，必须结合 blockers 与 nextSteps 处理。"
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

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
import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {Link, useSearchParams} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {
    usePaperShadowConsistencyDrilldown,
    useShadowRunOverview,
} from '@/hooks/useShadowRunQueries';
import {
    usePaperShadowComparisonQuery,
    useShadowLivePreviewQuery,
    useStrategyEvaluationGateQuery,
    useStrategyValidationOverview,
} from '@/hooks/useStrategyValidationQueries';
import type {AppApiError} from '@/types/api';
import type {
    JsonValue,
    PaperShadowConsistencyDrilldownResponse,
    ShadowRunOverviewResponse,
} from '@/types/shadow-runs';
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

const STATUS_PRESENTATION: Record<string, StatusPresentation> = {
    APPROVED: {label: '验证层通过，非交易授权', tone: 'info'},
    REJECTED: {label: '验证层拒绝', tone: 'danger'},
    NEEDS_REVIEW: {label: '需要复核', tone: 'warning'},
    BLOCKED: {label: '阻断', tone: 'danger'},
    NO_EVIDENCE: {label: '无证据', tone: 'neutral'},
    STALE_EVIDENCE: {label: '证据过期或不完整', tone: 'warning'},
    CONSISTENT: {label: '证据一致，非盈利结论', tone: 'info'},
    DIVERGED: {label: '证据偏离', tone: 'warning'},
    NO_REPORT: {label: '无一致性报告', tone: 'neutral'},
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

function normalizeQuery(values: StrategyValidationQuery): StrategyValidationQuery {
    return QUERY_FIELDS.reduce<StrategyValidationQuery>((query, field) => {
        const normalized = values[field]?.trim();
        if (normalized) {
            query[field] = normalized;
        }
        return query;
    }, {});
}

function hasQueryValue(query: StrategyValidationQuery): boolean {
    return QUERY_FIELDS.some((field) => Boolean(query[field]?.trim()));
}

function queryFromSearchParams(searchParams: URLSearchParams): StrategyValidationQuery {
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

function workbenchShadowRunId(
    submittedQuery: StrategyValidationQuery | null,
    strategyOverview?: StrategyValidationOverviewResponse,
    shadowOverview?: ShadowRunOverviewResponse,
): string | null {
    return firstText(
        submittedQuery?.shadowRunId,
        strategyOverview?.latestDecision?.shadowRunId,
        shadowOverview?.latestRun?.shadowRunId,
    );
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

function StatusTag({status}: { status?: string | null }) {
    const presentation = statusPresentation(status);
    return (
        <Tag color={TONE_TO_COLOR[presentation.tone]}>
            {statusText(status)}
        </Tag>
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
                <Descriptions.Item label="latestDecision.traceId">{optionalCode(latestDecision?.traceId)}</Descriptions.Item>
                <Descriptions.Item label="generatedAt">{generatedAtText(latestDecision?.generatedAt)}</Descriptions.Item>
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
                <Button size="small" icon={<ReloadOutlined/>} loading={query.isFetching} onClick={() => query.refetch()}>
                    刷新 overview
                </Button>
            )}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    只读消费 GET /api/strategy-validation/overview；用于 validation runtime baseline 总览，不新增 route、Dashboard v2 或写侧动作。
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
                                overview 失败时按不可用处理，不会显示为通过、授权或可执行。{formatApiError(query.error as AppApiError)}
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
            <Space data-testid="strategy-validation-shadow-workbench" direction="vertical" size={14} style={{display: 'flex'}}>
                <Paragraph type="secondary" style={{marginBottom: 0}}>
                    聚合 Strategy Validation overview、Shadow Run overview 与 Paper vs Shadow drilldown 的只读运营视角；不新增 route、不触发 runner、不接 Python artifact，也不创建任何交易动作。
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
                    <Descriptions.Item label="runningRuns">{numberValue(shadowOverview?.runningRuns)}</Descriptions.Item>
                    <Descriptions.Item label="blockedRuns">{numberValue(shadowOverview?.blockedRuns)}</Descriptions.Item>
                    <Descriptions.Item label="failedRuns">{numberValue(shadowOverview?.failedRuns)}</Descriptions.Item>
                    <Descriptions.Item label="completedRuns">{numberValue(shadowOverview?.completedRuns)}</Descriptions.Item>
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
                        <StatusTag status={drilldown?.comparisonStatus ?? shadowOverview?.latestConsistency?.comparisonStatus}/>
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
                    <Empty description="暂无 blockers / warnings / nextSteps / evidence anchors；不能解释为证据完整或可执行。"/>
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
                           }: {
    submittedQuery: StrategyValidationQuery | null;
    gate?: StrategyEvaluationGateResponse;
    comparison?: PaperShadowComparisonResponse;
    preview?: ShadowLivePreviewResponse;
}) {
    const scope = firstScope(submittedQuery, gate, comparison, preview);
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
            value: 'NOT_CONNECTED',
            status: 'PENDING_FRONTEND_SUPPORT',
            source: 'GateQ-4 POST API 已存在；本页不调用',
            detail: '当前页面未接入 artifact JSON 请求 UI；仅展示只读追溯占位，不上传、不导入、不写 Java fact-source。',
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
                        }: {
    submittedQuery: StrategyValidationQuery | null;
    gate?: StrategyEvaluationGateResponse;
    comparison?: PaperShadowComparisonResponse;
    preview?: ShadowLivePreviewResponse;
}) {
    const rows = useMemo(() => [
        ...evidenceMatrixRows('Evaluation Gate', gate),
        ...evidenceMatrixRows('Paper / Shadow Comparison', comparison),
        ...evidenceMatrixRows('Shadow Live Preview', preview),
        {
            key: 'Python Artifact Binding Preview-front-end-support',
            source: 'Python Artifact Binding Preview',
            category: 'missingEvidence' as const,
            code: 'PENDING_FRONTEND_SUPPORT',
            status: 'PENDING_FRONTEND_SUPPORT',
            message: 'GateQ-4 binding preview API 已存在，但本页未接入 artifact request UI；本轮不补后端、不上传、不导入。',
        },
        {
            key: 'Python Artifact Binding Preview-next-step',
            source: 'Python Artifact Binding Preview',
            category: 'nextSteps' as const,
            code: 'NEXT_STEP_1',
            status: 'ACTION_REQUIRED',
            message: '后续如需 artifact binding 前端能力，必须单独授权只读 request-body preview UI。',
        },
    ], [gate, comparison, preview]);

    return (
        <Card className="page-section" variant="borderless" title="Evidence Matrix / 证据矩阵">
            {!submittedQuery ? (
                <Empty description="提交查询后展示 requiredEvidence / missingEvidence / blockers / warnings / nextSteps"/>
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

export function StrategyValidationPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const initialQuery = useMemo(() => queryFromSearchParams(searchParams), [searchParams]);
    const [submittedQuery, setSubmittedQuery] = useState<StrategyValidationQuery | null>(
        hasQueryValue(initialQuery) ? initialQuery : null,
    );

    const overviewQuery = useStrategyValidationOverview();
    const shadowOverviewQuery = useShadowRunOverview();
    const evaluationGateQuery = useStrategyEvaluationGateQuery(submittedQuery);
    const paperShadowQuery = usePaperShadowComparisonQuery(submittedQuery);
    const shadowLivePreviewQuery = useShadowLivePreviewQuery(submittedQuery);
    const selectedShadowRunId = useMemo(
        () => workbenchShadowRunId(submittedQuery, overviewQuery.data, shadowOverviewQuery.data),
        [submittedQuery, overviewQuery.data, shadowOverviewQuery.data],
    );
    const consistencyDrilldownQuery = usePaperShadowConsistencyDrilldown(selectedShadowRunId);
    const loading = overviewQuery.isFetching
        || shadowOverviewQuery.isFetching
        || consistencyDrilldownQuery.isFetching
        || evaluationGateQuery.isFetching
        || paperShadowQuery.isFetching
        || shadowLivePreviewQuery.isFetching;

    function submitQuery(query: StrategyValidationQuery) {
        setSubmittedQuery(query);
        setSearchParams(query as Record<string, string>);
    }

    function resetQuery() {
        setSubmittedQuery(null);
        setSearchParams({});
    }

    return (
        <Space data-testid="strategy-validation-page" direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" variant="borderless">
                <PageHero
                    title="策略生命周期追溯与 Paper / Shadow 对照"
                    description="只读查看 strategy version、dataset、Evaluation Gate、publish、Paper run、Paper / Shadow Comparison、Shadow Live no-side-effect preview 与 Python Artifact Binding Preview 追溯链。"
                    badge="GateQ-6 · 只读追溯"
                    tip="本页不创建运行、不启动 runner、不修改任何交易状态。"
                />
            </Card>

            <StrategyValidationOverviewPanel query={overviewQuery}/>
            <StrategyValidationShadowWorkbench
                queries={{
                    strategyOverview: overviewQuery,
                    shadowOverview: shadowOverviewQuery,
                    drilldown: consistencyDrilldownQuery,
                    shadowRunId: selectedShadowRunId,
                }}
            />
            <BoundarySummary/>
            <QueryForm initialValues={initialQuery} onSubmit={submitQuery} onReset={resetQuery} loading={loading}/>
            <StatusSemantics/>
            <TraceabilityChain
                submittedQuery={submittedQuery}
                gate={evaluationGateQuery.data}
                comparison={paperShadowQuery.data}
                preview={shadowLivePreviewQuery.data}
            />
            <EvidenceMatrix
                submittedQuery={submittedQuery}
                gate={evaluationGateQuery.data}
                comparison={paperShadowQuery.data}
                preview={shadowLivePreviewQuery.data}
            />
            <EvaluationGatePanel submitted={Boolean(submittedQuery)} query={evaluationGateQuery}/>
            <PaperShadowPanel submitted={Boolean(submittedQuery)} query={paperShadowQuery}/>
            <ShadowLivePreviewPanel submitted={Boolean(submittedQuery)} query={shadowLivePreviewQuery}/>
        </Space>
    );
}

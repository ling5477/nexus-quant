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
    Timeline,
    Typography
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {useSearchParams} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {PageHero} from '@/components/page/PageHero';
import {
    usePaperShadowComparisonQuery,
    useShadowLivePreviewQuery,
    useStrategyEvaluationGateQuery,
} from '@/hooks/useStrategyValidationQueries';
import type {AppApiError} from '@/types/api';
import type {
    PaperShadowComparisonResponse,
    ShadowLivePreviewResponse,
    ShadowLiveSideEffectPolicy,
    StrategyEvaluationGateResponse,
    StrategyValidationEvidence,
    StrategyValidationQuery,
    StrategyValidationReason,
    StrategyValidationScope,
} from '@/types/strategy-validation';
import {formatDateTime} from '@/utils/formatters';

const {Paragraph, Text} = Typography;

type StatusTone = 'success' | 'info' | 'neutral' | 'warning' | 'danger';

interface StatusPresentation {
    label: string;
    tone: StatusTone;
}

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

const STATUS_PRESENTATION: Record<string, StatusPresentation> = {
    READY_FOR_SHADOW_REVIEW: {label: '可进入 Shadow 评审', tone: 'info'},
    READY_FOR_COMPARISON: {label: '可查看只读对照', tone: 'info'},
    READY_FOR_NO_SIDE_EFFECT_PREVIEW: {label: '可生成无副作用预览', tone: 'info'},
    VALID_FOR_BINDING_PREVIEW: {label: '可进入只读绑定预览', tone: 'info'},
    SKELETON_AVAILABLE: {label: '骨架可用', tone: 'info'},
    PREVIEW_ONLY: {label: '仅预览', tone: 'info'},
    NOT_EXECUTED: {label: '未执行', tone: 'neutral'},
    NOT_IMPLEMENTED: {label: '能力未实现', tone: 'warning'},
    UNKNOWN: {label: '未知', tone: 'neutral'},
    NOT_AVAILABLE: {label: '不可用', tone: 'neutral'},
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
};

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
    const items = [
        {
            key: 'strategyVersion',
            value: scope.strategyVersionId,
            status: scope.strategyVersionId ? gate?.gateStatus ?? 'SATISFIED' : 'NOT_AVAILABLE',
            detail: 'strategy version 是本页查询链路的主锚点。',
        },
        {
            key: 'dataset',
            value: scope.datasetId,
            status: gate?.datasetQualityStatus ?? comparison?.dataQualityStatus ?? 'NOT_AVAILABLE',
            detail: 'dataset 只用于评估证据追踪，不代表行情可交易。',
        },
        {
            key: 'evaluation',
            value: scope.evaluationId,
            status: gate?.evaluationStatus ?? comparison?.evaluationGateStatus ?? 'NOT_AVAILABLE',
            detail: 'evaluation 只表示评估事实，不代表策略批准。',
        },
        {
            key: 'publish',
            value: scope.publishId,
            status: gate?.publishTraceStatus ?? 'NOT_AVAILABLE',
            detail: 'publish trace 仅为链路证据，不触发发布写侧。',
        },
        {
            key: 'paper',
            value: scope.paperRunId,
            status: comparison?.paperRunStatus ?? gate?.paperEvidenceStatus ?? 'NOT_AVAILABLE',
            detail: 'Paper evidence 只表示 SIM/Paper 事实，不启动 Paper run。',
        },
        {
            key: 'shadow',
            value: scope.shadowRunId,
            status: comparison?.shadowRunStatus ?? preview?.paperShadowComparisonStatus ?? 'NOT_IMPLEMENTED',
            detail: 'Shadow facts 当前只读；缺失或未实现必须显式展示。',
        },
    ];

    return (
        <Card className="page-section" variant="borderless" title="Traceability chain">
            {!submittedQuery ? (
                <Empty
                    description="提交查询后展示 strategy version -> dataset -> evaluation -> publish -> paper -> shadow 链路"/>
            ) : (
                <Timeline
                    items={items.map((item) => ({
                        color: statusPresentation(item.status).tone === 'danger'
                            ? 'red'
                            : statusPresentation(item.status).tone === 'warning'
                                ? 'orange'
                                : 'blue',
                        children: (
                            <Space direction="vertical" size={4}>
                                <Space size={8} wrap>
                                    <Text code>{item.key}</Text>
                                    {optionalCode(item.value)}
                                    <StatusTag status={item.status}/>
                                </Space>
                                <Text type="secondary">{item.detail}</Text>
                            </Space>
                        ),
                    }))}
                />
            )}
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
                    message="本页仅用于策略验证与只读对照"
                    description="Evaluation Gate 不代表交易授权；Paper vs Shadow Comparison 不代表交易授权；Shadow Live Preview 是 no-side-effect preview，不提交真实订单、不读取真实凭证。"
                />
                <Space size={[8, 8]} wrap>
                    <Tag color="default">只读验证</Tag>
                    <Tag color="error">不代表交易授权</Tag>
                    <Tag color="error">不代表 LIVE 已启用</Tag>
                    <Tag color="error">不提交真实订单</Tag>
                    <Tag color="error">不读取真实凭证</Tag>
                    <Tag color="default">LIVE: DISABLED</Tag>
                    <Tag color="default">real provider: NOT_IMPLEMENTED</Tag>
                    <Tag color="default">private trading adapter: NOT_IMPLEMENTED</Tag>
                    <Tag color="default">real permission probe: NOT_IMPLEMENTED</Tag>
                    <Tag color="default">AI: NOT STARTED</Tag>
                    <Tag color="default">DH runtime: NOT INTEGRATED</Tag>
                </Space>
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

    const evaluationGateQuery = useStrategyEvaluationGateQuery(submittedQuery);
    const paperShadowQuery = usePaperShadowComparisonQuery(submittedQuery);
    const shadowLivePreviewQuery = useShadowLivePreviewQuery(submittedQuery);
    const loading = evaluationGateQuery.isFetching || paperShadowQuery.isFetching || shadowLivePreviewQuery.isFetching;

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
                    title="策略验证与 Paper / Shadow 对照"
                    description="只读查看 strategy version、dataset、evaluation、publish、Paper run、Paper/Shadow 对照与 Shadow Live no-side-effect preview 证据链。"
                    badge="GateQ-5 · 只读验证"
                    tip="本页不创建运行、不启动 runner、不修改任何交易状态。"
                />
            </Card>

            <BoundarySummary/>
            <QueryForm initialValues={initialQuery} onSubmit={submitQuery} onReset={resetQuery} loading={loading}/>
            <TraceabilityChain
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

import {EyeOutlined, ReloadOutlined} from '@ant-design/icons';
import {Button, Card, List, Segmented, Space, Table, Tag, Tooltip, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMemo, useState} from 'react';
import {useNavigate} from 'react-router-dom';

import {
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    NqMetricCard,
    NqPageHeader,
    NqRiskBanner,
    NqStatusTag,
    type NqStatusTone,
} from '@/components/nq';
import {useShadowRunListQuery, useShadowRunOverview} from '@/hooks/useShadowRunQueries';
import type {AppApiError} from '@/types/api';
import type {
    ShadowRunListItemResponse,
    ShadowRunListRequest,
    ShadowRunOverviewBlocker,
    ShadowRunOverviewEvidenceAnchor,
    ShadowRunOverviewNextStep,
    ShadowRunOverviewResponse,
    ShadowRunOverviewWarning,
} from '@/types/shadow-runs';
import {formatDateTime} from '@/utils/formatters';

const {Text} = Typography;

const STATUS_FILTERS = [
    'ALL',
    'CREATED',
    'PRECHECKING',
    'READY',
    'RUNNING',
    'STOP_REQUESTED',
    'STOPPED',
    'COMPLETED',
    'BLOCKED',
    'FAILED',
    'CANCELLED',
];

const SENSITIVE_TEXT_PATTERN = /(api[_-]?key|secret|passphrase|private[_ -]?key|credentialMaterial|realOrderId|realAccountBalance|authorizedForTrading|tradingReady|liveReady|tradeApproved|token)/i;

function asAppApiError(error: unknown): AppApiError | null {
    if (!error || typeof error !== 'object') {
        return null;
    }
    const candidate = error as Partial<AppApiError>;
    return typeof candidate.status === 'number' && typeof candidate.code === 'string'
        ? error as AppApiError
        : null;
}

function statusTone(status: string | null | undefined): NqStatusTone {
    const normalized = status?.toUpperCase() ?? '';
    if (normalized.includes('FAILED') || normalized.includes('BLOCKED') || normalized.includes('REJECTED')) {
        return 'danger';
    }
    if (normalized.includes('WARNING') || normalized.includes('PARTIAL') || normalized.includes('DIVERGED')) {
        return 'warning';
    }
    if (normalized.includes('NOT_') || normalized.includes('UNKNOWN') || normalized.includes('MISSING')) {
        return 'neutral';
    }
    if (normalized.includes('CONSISTENT') || normalized.includes('COMPLETED') || normalized.includes('READY')) {
        return 'success';
    }
    return 'info';
}

function safeText(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '-';
    }
    const text = String(value);
    return SENSITIVE_TEXT_PATTERN.test(text) ? '[filtered sensitive value]' : text;
}

function countValue(value: number | null | undefined): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function BoundaryFlag({label, enabled}: { label: string; enabled: boolean }) {
    return <Tag color={enabled ? 'success' : 'error'}>{label}: {enabled ? 'true' : 'false'}</Tag>;
}

function BoundaryTags({record}: { record: ShadowRunListItemResponse }) {
    return (
        <Space size={[4, 4]} wrap>
            <BoundaryFlag label="No order submission" enabled={record.noOrderSubmission}/>
            <BoundaryFlag label="No credential access" enabled={record.noCredentialAccess}/>
            <BoundaryFlag label="No private endpoint" enabled={record.noPrivateEndpoint}/>
            <BoundaryFlag label="No ledger mutation" enabled={record.noLedgerMutation}/>
            <BoundaryFlag label="No account mutation" enabled={record.noAccountMutation}/>
        </Space>
    );
}

function codeText(value: string | number | null | undefined) {
    const text = safeText(value);
    return text === '-' ? <Text type="secondary">-</Text> : <Text code>{text}</Text>;
}

type OverviewMessage = ShadowRunOverviewBlocker | ShadowRunOverviewWarning;

type OverviewState = {
    level: 'info' | 'warning' | 'danger';
    message: string;
    description: string;
};

function enumExplanation(status: string, category: 'run' | 'comparison' | 'severity'): string {
    const normalized = status.toUpperCase();
    const runStatus: Record<string, string> = {
        CREATED: '已创建，仅表示本地 Shadow Run 事实存在。',
        PRECHECKING: '预检查中，仅用于本地诊断流程。',
        READY: '诊断运行就绪，不表示交易授权。',
        RUNNING: '本地诊断运行中，不表示后台 scheduler 或 LIVE 交易启动。',
        STOP_REQUESTED: '停止请求已记录。',
        STOPPED: '已停止。',
        COMPLETED: '已完成诊断运行，不表示盈利或交易通过。',
        BLOCKED: '已阻断，需要查看 blockers / nextSteps。',
        FAILED: '已失败，需要查看错误和证据锚点。',
        CANCELLED: '已取消。',
    };
    const comparisonStatus: Record<string, string> = {
        CONSISTENT: '一致，仅表示证据层对照一致。',
        DIVERGED: '偏离，需要继续检查 divergence reasons。',
        PARTIAL: '部分可比，不可解释为通过。',
        NOT_COMPARABLE: '不可比，证据不足或边界不满足。',
        FAILED: '对照失败，需要查看 report 和 traceId。',
    };
    const severityStatus: Record<string, string> = {
        NONE: '无偏离。',
        LOW: '低偏离，仅用于诊断排序。',
        MEDIUM: '中等偏离，需要复核。',
        HIGH: '高偏离，需要优先处理。',
        CRITICAL: '严重偏离，需要阻断后续判断。',
        UNKNOWN: '未知，通常表示缺少 consistency report。',
    };
    const dictionary = category === 'run'
        ? runStatus
        : category === 'comparison'
            ? comparisonStatus
            : severityStatus;
    return `${status}：${dictionary[normalized] ?? '后端原始枚举，仅用于只读诊断展示，不表示交易授权。'}`;
}

function StatusWithHint({status, category}: {
    status: string | null | undefined;
    category: 'run' | 'comparison' | 'severity'
}) {
    const text = safeText(status);
    if (text === '-') {
        return <Text type="secondary">-</Text>;
    }
    return (
        <Tooltip title={enumExplanation(text, category)}>
            <span><NqStatusTag status={text} tone={statusTone(text)}/></span>
        </Tooltip>
    );
}

function overviewEmpty(overview: ShadowRunOverviewResponse): boolean {
    return countValue(overview.totalRuns) === 0 && !overview.latestRun && !overview.latestConsistency;
}

function hasStaleEvidence(overview: ShadowRunOverviewResponse): boolean {
    return countValue(overview.staleRuns) > 0
        || overview.warnings.some((warning) => warning.code?.toUpperCase() === 'STALE_EVIDENCE');
}

function hasDivergence(overview: ShadowRunOverviewResponse): boolean {
    const comparisonStatus = overview.latestConsistency?.comparisonStatus?.toUpperCase() ?? '';
    const severity = overview.divergenceSeverity?.toUpperCase() ?? '';
    return comparisonStatus === 'DIVERGED' || ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(severity);
}

function resolveOverviewState(overview: ShadowRunOverviewResponse): OverviewState {
    const latestRunStatus = overview.latestRun?.status?.toUpperCase() ?? '';
    const latestComparisonStatus = overview.latestConsistency?.comparisonStatus?.toUpperCase() ?? '';
    if (countValue(overview.failedRuns) > 0 || latestRunStatus.includes('FAILED') || latestComparisonStatus === 'FAILED') {
        return {
            level: 'danger',
            message: 'Overview 存在失败诊断事实',
            description: 'failed run 或 failed consistency 只表示本地诊断失败，需要检查 traceId / evidence anchors；不代表交易授权变化。',
        };
    }
    if (countValue(overview.blockedRuns) > 0 || latestRunStatus.includes('BLOCKED')) {
        return {
            level: 'warning',
            message: 'Overview 存在 blocked run',
            description: 'blocked 表示本地诊断链路被阻断，需要查看 blockers 和 nextSteps；不是 LIVE 放行或策略批准。',
        };
    }
    if (hasDivergence(overview)) {
        return {
            level: 'warning',
            message: 'Overview 存在 divergence',
            description: 'divergence severity 只用于诊断排序；success / danger 颜色不表示盈利、下跌或交易方向。',
        };
    }
    if (hasStaleEvidence(overview)) {
        return {
            level: 'warning',
            message: 'Overview 存在 stale evidence',
            description: 'stale evidence 表示本地证据过期或缺失，需要重新检查只读 facts；不触发 runner 或交易动作。',
        };
    }
    return {
        level: 'info',
        message: 'Overview 已加载',
        description: '当前摘要仅来自 read-only endpoint；可用于运营诊断，不是 trading authorization。',
    };
}

function BoundaryBadge({label, tooltip, color}: { label: string; tooltip: string; color?: string }) {
    return (
        <Tooltip title={tooltip}>
            <Tag color={color}>{label}</Tag>
        </Tooltip>
    );
}

function OverviewBoundaryBadges({overview}: { overview?: ShadowRunOverviewResponse }) {
    const pending = overview ? '' : '；overview 尚未返回时按 fail-closed 文案展示';
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label="LIVE DISABLED（LIVE 关闭）"
                tooltip={`liveDisabled=true 只表示 LIVE 被关闭，不是交易授权${pending}`}
            />
            <BoundaryBadge
                label="Real provider NOT IMPLEMENTED（真实 provider 未实现）"
                tooltip={`realProviderImplemented=false，前端不得展示真实 provider 可用状态${pending}`}
            />
            <BoundaryBadge
                label="Private trading NOT IMPLEMENTED（私有交易未实现）"
                tooltip={`privateTradingImplemented=false，不存在真实下单、撤单、转账或提现入口${pending}`}
            />
            <BoundaryBadge
                color="warning"
                label="Shadow Run is diagnostic only（仅诊断）"
                tooltip={`diagnosticOnly=true，仅用于本地 Shadow Run 诊断事实查看${pending}`}
            />
            <BoundaryBadge
                color="error"
                label="Not trading authorization（非交易授权）"
                tooltip={`notTradingAuthorization=true，APPROVED 或 CONSISTENT 也不能解释为交易授权${pending}`}
            />
            <BoundaryBadge
                label="AI/DH runtime not integrated（AI/DH runtime 未集成）"
                tooltip={`aiDhRuntimeIntegrated=false，不表示 AI started 或 DH integrated${pending}`}
            />
        </Space>
    );
}

function OverviewMetricStrip({overview, loading}: { overview?: ShadowRunOverviewResponse; loading: boolean }) {
    return (
        <Space size={[12, 12]} wrap>
            <NqMetricCard label="总 run 数" value={countValue(overview?.totalRuns)} loading={loading}
                          footer="read-only local facts"/>
            <NqMetricCard label="运行中 run 数" value={countValue(overview?.runningRuns)} loading={loading}/>
            <NqMetricCard label="阻断 run 数" value={countValue(overview?.blockedRuns)} loading={loading}
                          tone={countValue(overview?.blockedRuns) > 0 ? 'warning' : 'default'}/>
            <NqMetricCard label="失败 run 数" value={countValue(overview?.failedRuns)} loading={loading}
                          tone={countValue(overview?.failedRuns) > 0 ? 'danger' : 'default'}/>
            <NqMetricCard label="完成 run 数" value={countValue(overview?.completedRuns)} loading={loading}/>
            <NqMetricCard label="stale run 数" value={countValue(overview?.staleRuns)} loading={loading}
                          tone={countValue(overview?.staleRuns) > 0 ? 'warning' : 'default'}/>
        </Space>
    );
}

function OverviewMessageList({title, items, emptyText}: {
    title: string;
    items: OverviewMessage[];
    emptyText: string
}) {
    if (items.length === 0) {
        return (
            <Space direction="vertical" size={4}>
                <Text strong>{title}</Text>
                <Text type="secondary">{emptyText}</Text>
            </Space>
        );
    }
    return (
        <section aria-label={title}>
            <Text strong>{title}</Text>
            <List
                size="small"
                dataSource={items.slice(0, 4)}
                renderItem={(item) => (
                    <List.Item>
                        <Space direction="vertical" size={2} style={{display: 'flex'}}>
                            <Space size={[6, 6]} wrap>
                                <NqStatusTag status={safeText(item.severity)} tone={statusTone(item.severity)}/>
                                {codeText(item.code)}
                                <Text type="secondary">{safeText(item.sourceType)}</Text>
                                {item.sourceId ? codeText(item.sourceId) : null}
                            </Space>
                            <Text>{safeText(item.message)}</Text>
                        </Space>
                    </List.Item>
                )}
            />
            {items.length > 4 ? <Text type="secondary">另有 {items.length - 4} 条，仅保留摘要显示。</Text> : null}
        </section>
    );
}

function OverviewNextSteps({items}: { items: ShadowRunOverviewNextStep[] }) {
    if (items.length === 0) {
        return (
            <Space direction="vertical" size={4}>
                <Text strong>Next steps</Text>
                <Text type="secondary">暂无 nextSteps；不能解释为已允许交易。</Text>
            </Space>
        );
    }
    return (
        <section aria-label="Shadow Run overview next steps">
            <Text strong>Next steps</Text>
            <List
                size="small"
                dataSource={items.slice(0, 4)}
                renderItem={(item) => (
                    <List.Item>
                        <Space direction="vertical" size={2} style={{display: 'flex'}}>
                            <Space size={[6, 6]} wrap>
                                {codeText(item.code)}
                                <Tag color={item.blocking ? 'error' : 'default'}>
                                    {item.blocking ? 'blocking' : 'non-blocking'}
                                </Tag>
                                <Text type="secondary">owner: {safeText(item.owner)}</Text>
                            </Space>
                            <Text>{safeText(item.action)}</Text>
                            <Text type="secondary">expectedEvidence: {safeText(item.expectedEvidence)}</Text>
                        </Space>
                    </List.Item>
                )}
            />
            {items.length > 4 ? <Text type="secondary">另有 {items.length - 4} 条，仅保留摘要显示。</Text> : null}
        </section>
    );
}

function OverviewEvidenceSummary({overview}: { overview: ShadowRunOverviewResponse }) {
    const anchors = overview.evidenceAnchors.slice(0, 4);
    return (
        <Space direction="vertical" size={8} style={{display: 'flex'}}>
            <Space size={[12, 8]} wrap>
                <Text>generatedAt: {formatDateTime(overview.generatedAt)}</Text>
                <Text>traceId: {codeText(overview.traceId)}</Text>
            </Space>
            {anchors.length === 0 ? (
                <Text type="secondary">暂无 evidence anchors；不能补造证据。</Text>
            ) : (
                <List<ShadowRunOverviewEvidenceAnchor>
                    size="small"
                    dataSource={anchors}
                    renderItem={(anchor) => (
                        <List.Item>
                            <Space size={[8, 6]} wrap>
                                <Text>{safeText(anchor.sourceType)}</Text>
                                {codeText(anchor.sourceId)}
                                <Text type="secondary">version: {safeText(anchor.sourceVersion)}</Text>
                                <Text type="secondary">time: {formatDateTime(anchor.sourceTimestamp)}</Text>
                                <Text type="secondary">checksum: {safeText(anchor.checksum)}</Text>
                            </Space>
                        </List.Item>
                    )}
                />
            )}
            {overview.evidenceAnchors.length > 4
                ? <Text type="secondary">另有 {overview.evidenceAnchors.length - 4} 个 evidence anchors。</Text>
                : null}
        </Space>
    );
}

function ShadowRunOverviewSummary({
    overview,
    isLoading,
    isFetching,
    isError,
    error,
    onRetry,
}: {
    overview?: ShadowRunOverviewResponse;
    isLoading: boolean;
    isFetching: boolean;
    isError: boolean;
    error: unknown;
    onRetry: () => void;
}) {
    const empty = overview ? overviewEmpty(overview) : false;
    const overviewState = overview ? resolveOverviewState(overview) : null;

    return (
        <Card
            className="page-section"
            variant="borderless"
            title="Overview Summary"
            extra={(
                <Button icon={<ReloadOutlined/>} loading={isFetching} onClick={onRetry}>
                    刷新 overview
                </Button>
            )}
        >
            <Space direction="vertical" size={14} style={{display: 'flex'}}>
                <Text type="secondary">
                    只读消费 GET /api/shadow-runs/overview；用于 Shadow Run 运营诊断，不新增 route、Dashboard v2 或写侧动作。
                </Text>
                <OverviewBoundaryBadges overview={overview}/>
                <OverviewMetricStrip overview={overview} loading={isLoading}/>

                {isLoading ? (
                    <NqLoadingState message="Shadow Run overview loading"/>
                ) : isError ? (
                    <NqErrorState
                        title="Shadow Run overview 加载失败"
                        error={asAppApiError(error)}
                        description="overview 失败时不回退成空数据，也不展示任何可交易结论。"
                        onRetry={onRetry}
                    />
                ) : !overview ? (
                    <NqEmptyState description="暂无 Shadow Run overview 响应；固定安全边界仍按 fail-closed 展示。"/>
                ) : empty ? (
                    <NqEmptyState description="暂无 Shadow Run 运行数据；Overview 仅显示 0 计数与固定安全边界。"/>
                ) : (
                    <>
                        {overviewState ? (
                            <NqRiskBanner
                                level={overviewState.level}
                                message={overviewState.message}
                                description={overviewState.description}
                            />
                        ) : null}
                        <Space size={[12, 12]} wrap>
                            <NqMetricCard
                                label="latestRun.status"
                                value={<StatusWithHint status={overview.latestRun?.status} category="run"/>}
                                footer={overview.latestRun ? codeText(overview.latestRun.shadowRunId) : 'no latest run'}
                            />
                            <NqMetricCard
                                label="latestConsistency"
                                value={(
                                    <StatusWithHint
                                        status={overview.latestConsistency?.comparisonStatus}
                                        category="comparison"
                                    />
                                )}
                                footer={overview.latestConsistency ? codeText(overview.latestConsistency.reportId) : 'no report'}
                            />
                            <NqMetricCard
                                label="divergenceSeverity"
                                value={<StatusWithHint status={overview.divergenceSeverity} category="severity"/>}
                                footer="diagnostic severity only"
                            />
                        </Space>
                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                            <OverviewMessageList
                                title="Blockers"
                                items={overview.blockers}
                                emptyText="暂无 blockers；不能解释为交易放行。"
                            />
                            <OverviewMessageList
                                title="Warnings"
                                items={overview.warnings}
                                emptyText="暂无 warnings；仍需遵守固定安全边界。"
                            />
                            <OverviewNextSteps items={overview.nextSteps}/>
                            <OverviewEvidenceSummary overview={overview}/>
                        </Space>
                    </>
                )}
            </Space>
        </Card>
    );
}

/**
 * GateR-8 / GateS-1 Shadow Run list page.
 *
 * Why:
 * 这是 GateR-7 detail / replay 页面的只读入口，并在 GateS-1 增加 overview summary。
 * 页面只调用 GET overview/list/detail 链路，不提供 start / stop / execute / rerun / approve / trade 操作，
 * 不触发 runner、scheduler、credential、private endpoint 或真实交易。
 */
export function ShadowRunListPage() {
    const navigate = useNavigate();
    const [statusFilter, setStatusFilter] = useState<string>('ALL');
    const queryParams = useMemo<ShadowRunListRequest>(() => ({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        limit: 50,
        offset: 0,
    }), [statusFilter]);
    const overviewQuery = useShadowRunOverview();
    const listQuery = useShadowRunListQuery(queryParams);
    const rows = listQuery.data?.items ?? [];

    const columns = useMemo<ColumnsType<ShadowRunListItemResponse>>(() => [
        {
            title: 'shadowRunId',
            dataIndex: 'id',
            key: 'id',
            width: 280,
            render: (value: string) => codeText(value),
        },
        {
            title: 'status',
            dataIndex: 'status',
            key: 'status',
            width: 150,
            render: (value: string) => <NqStatusTag status={safeText(value)} tone={statusTone(value)}/>,
        },
        {
            title: 'strategyVersionId',
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 190,
            render: (value: string) => codeText(value),
        },
        {
            title: 'datasetId',
            dataIndex: 'datasetId',
            key: 'datasetId',
            width: 260,
            render: (value: string) => codeText(value),
        },
        {
            title: 'paperRunId',
            dataIndex: 'paperRunId',
            key: 'paperRunId',
            width: 170,
            render: (value: string | null) => codeText(value),
        },
        {
            title: 'traceId',
            dataIndex: 'traceId',
            key: 'traceId',
            width: 190,
            render: (value: string) => codeText(value),
        },
        {
            title: 'createdAt',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 190,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: 'counts',
            key: 'counts',
            width: 210,
            render: (_, record) => (
                <Space size={[4, 4]} wrap>
                    <Tag>blockers {countValue(record.blockersCount)}</Tag>
                    <Tag>warnings {countValue(record.warningsCount)}</Tag>
                    <Tag>next {countValue(record.nextStepsCount)}</Tag>
                </Space>
            ),
        },
        {
            title: 'no-side-effect flags',
            key: 'flags',
            width: 360,
            render: (_, record) => <BoundaryTags record={record}/>,
        },
        {
            title: 'detail',
            key: 'detail',
            width: 130,
            fixed: 'right',
            render: (_, record) => (
                <Button
                    type="link"
                    icon={<EyeOutlined/>}
                    onClick={(event) => {
                        event.stopPropagation();
                        navigate(`/strategies/shadow-runs/${record.id}`);
                    }}
                >
                    查看 detail
                </Button>
            ),
        },
    ], [navigate]);

    return (
        <Space data-testid="shadow-run-list-page" direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" variant="borderless">
                <NqPageHeader
                    title="Shadow Run 列表"
                    description="只读查看本地 Shadow Run diagnostic facts，并从列表进入 detail / replay 页面。"
                    badge="GateR-8 · Read-only list"
                    extra={(
                        <Button icon={<ReloadOutlined/>} loading={listQuery.isFetching}
                                onClick={() => listQuery.refetch()}>
                            刷新只读列表
                        </Button>
                    )}
                />
            </Card>

            <ShadowRunOverviewSummary
                overview={overviewQuery.data}
                isLoading={overviewQuery.isLoading}
                isFetching={overviewQuery.isFetching}
                isError={overviewQuery.isError}
                error={overviewQuery.error}
                onRetry={() => overviewQuery.refetch()}
            />

            <NqRiskBanner
                level="warning"
                message="Diagnostic only / No trading authorization"
                description="Shadow Run list 只展示本地诊断事实；不启动 runner，不提交订单，不读取 credential，不调用 private endpoint，不修改 account / ledger / order。"
            />

            <Space size={[12, 12]} wrap>
                <NqMetricCard label="查询窗口" value={`${rows.length} / ${listQuery.data?.total ?? 0}`}
                              footer="bounded local facts"/>
                <NqMetricCard label="LIVE" value={<NqStatusTag status="DISABLED" tone="danger"/>}/>
                <NqMetricCard label="AI" value={<NqStatusTag status="NOT STARTED" tone="neutral"/>}/>
                <NqMetricCard label="DH runtime" value={<NqStatusTag status="NOT INTEGRATED" tone="neutral"/>}/>
            </Space>

            <Card className="page-section" variant="borderless" title="筛选">
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Text type="secondary">status 筛选只影响 GET /api/shadow-runs 查询，不触发任何写侧动作。</Text>
                    <div data-testid="shadow-run-status-filter">
                        <Segmented
                            block
                            options={STATUS_FILTERS}
                            value={statusFilter}
                            onChange={(value) => setStatusFilter(String(value))}
                        />
                    </div>
                </Space>
            </Card>

            {listQuery.isLoading ? (
                <Card className="page-section" variant="borderless">
                    <NqLoadingState message="Shadow Run list loading"/>
                </Card>
            ) : listQuery.isError ? (
                <Card className="page-section" variant="borderless">
                    <NqErrorState
                        title="Shadow Run list 加载失败"
                        error={asAppApiError(listQuery.error)}
                        onRetry={() => listQuery.refetch()}
                    />
                </Card>
            ) : rows.length === 0 ? (
                <Card className="page-section" variant="borderless">
                    <NqEmptyState description="暂无 Shadow Run 列表数据；不能补造本地 facts 或解释为交易阻断已解除。"/>
                </Card>
            ) : (
                <Card className="page-section" variant="borderless" title="Shadow Run facts">
                    <Table<ShadowRunListItemResponse>
                        size="small"
                        rowKey="id"
                        columns={columns}
                        dataSource={rows}
                        pagination={false}
                        scroll={{x: 2030}}
                        onRow={(record) => ({
                            style: {cursor: 'pointer'},
                            onClick: () => navigate(`/strategies/shadow-runs/${record.id}`),
                        })}
                    />
                </Card>
            )}
        </Space>
    );
}

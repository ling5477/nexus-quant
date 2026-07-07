import {EyeOutlined, ReloadOutlined} from '@ant-design/icons';
import {Button, Card, Segmented, Space, Table, Tag, Typography} from 'antd';
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
import {useShadowRunListQuery} from '@/hooks/useShadowRunQueries';
import type {AppApiError} from '@/types/api';
import type {ShadowRunListItemResponse, ShadowRunListRequest} from '@/types/shadow-runs';
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

/**
 * GateR-8 Shadow Run list page.
 *
 * Why:
 * 这是 GateR-7 detail / replay 页面的只读入口。页面只调用 GET list/detail 链路，
 * 不提供 start / stop / execute / rerun / approve / trade 操作，不触发 runner。
 */
export function ShadowRunListPage() {
    const navigate = useNavigate();
    const [statusFilter, setStatusFilter] = useState<string>('ALL');
    const queryParams = useMemo<ShadowRunListRequest>(() => ({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        limit: 50,
        offset: 0,
    }), [statusFilter]);
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

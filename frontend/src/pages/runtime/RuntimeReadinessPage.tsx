import {
    Alert,
    Button,
    Card,
    Col,
    List,
    Row,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {Link} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {NqMetricCard, NqRiskBanner} from '@/components/nq';
import {PageHero} from '@/components/page/PageHero';
import {useAdapterReadinessQuery} from '@/hooks/useAdapterReadinessQuery';
import {DataFreshness, StatusTag, type StatusTone} from '@/nq-design-system';
import type {AdapterReadinessItem} from '@/types/adapter-readiness';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';

const {Paragraph, Text} = Typography;

const REAL_EXCHANGE_VENUES = new Set(['OKX', 'BINANCE']);
const NO_REAL_VENUES = new Set(['NOOP', 'PAPER', 'SIM', 'FAKE', 'STUB', 'FUTURE_REAL']);
const MARKETDATA_READINESS_PATH = `/marketdata?${new URLSearchParams({
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    interval: '1m',
}).toString()}`;
const BLOCKED_RUNTIME_REASONS = [
    'NO_REAL',
    'FAKE',
    'STUB',
    'FUTURE_REAL_DISABLED',
    'LIVE_NOT_AUTHORIZED',
    'PERMISSION_PROBE_DISABLED',
    'CREDENTIAL_UNCONFIGURED',
    'PENDING_BACKEND_SUPPORT',
];

interface RuntimeBlocker {
    key: string;
    area: string;
    status: string;
    source: string;
    impact: string;
    tone: StatusTone;
}

interface VenueSummary {
    venue: string;
    capabilities: number;
    allowed: number;
    liveAuthorized: number;
    statuses: string[];
    reasons: string[];
    tone: StatusTone;
}

function uniqueSorted(values: string[]): string[] {
    return [...new Set(values.filter(Boolean))].sort((left, right) => left.localeCompare(right));
}

function statusTone(status: string): StatusTone {
    switch (status) {
        case 'NO_REAL':
        case 'READY_FOR_PAPER_ONLY':
            return 'info';
        case 'NOT_READY':
        case 'LIVE_NOT_AUTHORIZED':
        case 'UNKNOWN_REQUIRES_REVIEW':
            return 'danger';
        case 'DISABLED_SENTINEL':
        case 'CREDENTIAL_UNCONFIGURED':
        case 'CAPABILITY_NOT_IMPLEMENTED':
        case 'PENDING_BACKEND_SUPPORT':
        case 'FUTURE_REAL_DISABLED':
        case 'PERMISSION_PROBE_DISABLED':
            return 'warning';
        case 'SKIPPED':
            return 'neutral';
        case 'READY':
            // 当前 GateM baseline 不允许 READY 被解释为真实可交易，按高风险信号展示。
            return 'danger';
        default:
            return status.includes('READY') ? 'danger' : 'neutral';
    }
}

function buildVenueSummaries(items: AdapterReadinessItem[]): VenueSummary[] {
    return uniqueSorted(items.map((item) => item.venue)).map((venue) => {
        const rows = items.filter((item) => item.venue === venue);
        const allowed = rows.filter((item) => item.allowed).length;
        const liveAuthorized = rows.filter((item) => item.liveAuthorized).length;
        const statuses = uniqueSorted(rows.map((item) => item.status));
        const reasons = uniqueSorted(rows.flatMap((item) => item.reasons ?? []));
        const isUnexpectedReady = allowed > 0 || liveAuthorized > 0 || statuses.includes('READY');
        const isRealExchange = REAL_EXCHANGE_VENUES.has(venue);

        return {
            venue,
            capabilities: rows.length,
            allowed,
            liveAuthorized,
            statuses,
            reasons,
            tone: isUnexpectedReady || isRealExchange ? 'danger' : 'info',
        };
    });
}

function buildRuntimeBlockers(items: AdapterReadinessItem[]): RuntimeBlocker[] {
    const permissionRows = items.filter((item) => item.capability === 'PERMISSION_PROBE');
    const noRealRows = items.filter((item) => (
        item.status === 'NO_REAL'
        || NO_REAL_VENUES.has(item.venue)
        || (item.reasons ?? []).includes('NO_REAL_DISABLED')
    ));
    const realExchangeRows = items.filter((item) => REAL_EXCHANGE_VENUES.has(item.venue));

    return [
        {
            key: 'live-disabled',
            area: 'LIVE disabled',
            status: 'LIVE_NOT_AUTHORIZED',
            source: 'GateM runtime boundary',
            impact: '不提供 LIVE 入口，不允许真实下单、撤单、转账或提现。',
            tone: 'danger',
        },
        {
            key: 'adapter-no-real',
            area: 'Adapter no-real',
            status: noRealRows.length > 0 ? 'NO_REAL' : 'PENDING_BACKEND_SUPPORT',
            source: 'GET /api/adapters/readiness',
            impact: `NoReal / Fake / Stub / FutureReal 均保持阻断；当前矩阵 no-real rows=${noRealRows.length}。`,
            tone: noRealRows.length > 0 ? 'info' : 'warning',
        },
        {
            key: 'real-exchange',
            area: 'OKX / Binance runtime',
            status: realExchangeRows.length > 0 ? 'NOT_READY' : 'PENDING_BACKEND_SUPPORT',
            source: 'GET /api/adapters/readiness',
            impact: '真实交易所 adapter / RealClient / real provider 未实现，不能作为正式 provider 使用。',
            tone: 'danger',
        },
        {
            key: 'permission-probe',
            area: 'Permission probe',
            status: permissionRows.length > 0 ? 'PERMISSION_PROBE_DISABLED / SKIPPED' : 'PENDING_BACKEND_SUPPORT',
            source: 'Adapter readiness PERMISSION_PROBE row',
            impact: '当前只展示 disabled / skipped 语义，不执行 permission probe POST，也不读取 credential。',
            tone: permissionRows.length > 0 ? 'warning' : 'danger',
        },
        {
            key: 'runtime-flags',
            area: 'Central runtime flags',
            status: 'PENDING_BACKEND_SUPPORT',
            source: 'No aggregate runtime flags API',
            impact: '前端不能从缺失 API 推断 LIVE readiness；统一 runtime flags 等后端聚合支持。',
            tone: 'warning',
        },
        {
            key: 'paper-to-real-aggregate',
            area: 'Paper-to-Real aggregate',
            status: 'PENDING_BACKEND_SUPPORT',
            source: 'No Paper-to-Real aggregate API',
            impact: 'Paper-only readiness 与真实交易授权必须分开展示，当前不提供跨环境聚合通过态。',
            tone: 'warning',
        },
    ];
}

function isReadinessSignalUnexpected(item: AdapterReadinessItem): boolean {
    return item.status === 'READY' || item.allowed || item.liveAuthorized;
}

const venueColumns: ColumnsType<VenueSummary> = [
    {
        title: 'Venue',
        dataIndex: 'venue',
        key: 'venue',
        width: 140,
        render: (venue: string, row) => (
            <Space size={8}>
                <StatusTag label={venue} tone={row.tone} variant="pill"/>
                {REAL_EXCHANGE_VENUES.has(venue) ? <Tag color="error">not authorized</Tag> : null}
            </Space>
        ),
    },
    {
        title: 'Capabilities',
        dataIndex: 'capabilities',
        key: 'capabilities',
        width: 120,
    },
    {
        title: 'Allowed',
        dataIndex: 'allowed',
        key: 'allowed',
        width: 110,
        render: (value: number) => (
            <Tag color={value > 0 ? 'error' : 'default'}>{value}</Tag>
        ),
    },
    {
        title: 'LIVE authorized',
        dataIndex: 'liveAuthorized',
        key: 'liveAuthorized',
        width: 150,
        render: (value: number) => (
            <Tag color={value > 0 ? 'error' : 'default'}>{value}</Tag>
        ),
    },
    {
        title: 'Statuses',
        dataIndex: 'statuses',
        key: 'statuses',
        render: (statuses: string[]) => (
            <Space size={[4, 4]} wrap>
                {statuses.map((status) => (
                    <StatusTag key={status} label={status} tone={statusTone(status)} variant="pill"/>
                ))}
            </Space>
        ),
    },
    {
        title: 'Reasons',
        dataIndex: 'reasons',
        key: 'reasons',
        render: (reasons: string[]) => (
            <Space size={[4, 4]} wrap>
                {reasons.slice(0, 5).map((reason) => (
                    <Tag key={reason} color={BLOCKED_RUNTIME_REASONS.includes(reason) ? 'warning' : 'default'}>
                        {reason}
                    </Tag>
                ))}
                {reasons.length > 5 ? <Tag>+{reasons.length - 5}</Tag> : null}
            </Space>
        ),
    },
];

const blockerColumns: ColumnsType<RuntimeBlocker> = [
    {
        title: 'Runtime blocker',
        dataIndex: 'area',
        key: 'area',
        width: 210,
        render: (area: string, row) => (
            <Space direction="vertical" size={2}>
                <Text strong>{area}</Text>
                <Text type="secondary">{row.source}</Text>
            </Space>
        ),
    },
    {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        width: 220,
        render: (status: string, row) => (
            <StatusTag label={status} tone={row.tone} variant="pill"/>
        ),
    },
    {
        title: 'Impact',
        dataIndex: 'impact',
        key: 'impact',
    },
];

/**
 * RuntimeReadinessPage 是 GateM Runtime UI 5A 的只读运行边界总览。
 *
 * Why:
 * 当前没有 central runtime flags / Paper-to-Real aggregate API，页面只能复用 adapter readiness 只读快照，
 * 并把缺失聚合能力明确展示为 PENDING_BACKEND_SUPPORT。任何 API 失败、未知、READY、allowed 或 liveAuthorized
 * 信号都不能被解释成真实交易授权；本页也不调用任何 POST / write endpoint。
 */
export function RuntimeReadinessPage() {
    const readinessQuery = useAdapterReadinessQuery();
    const items = readinessQuery.data?.items ?? [];
    const venueSummaries = buildVenueSummaries(items);
    const runtimeBlockers = buildRuntimeBlockers(items);
    const unexpectedSignals = items.filter(isReadinessSignalUnexpected);
    const permissionRows = items.filter((item) => item.capability === 'PERMISSION_PROBE');
    const noRealRows = items.filter((item) => item.status === 'NO_REAL' || NO_REAL_VENUES.has(item.venue));

    const adapterMatrixDetail = readinessQuery.isError
        ? 'readiness API unavailable'
        : `${items.length} rows / allowed=${items.filter((item) => item.allowed).length} / liveAuthorized=${items.filter((item) => item.liveAuthorized).length}`;
    const probeStatus = permissionRows.length > 0 ? 'PERMISSION_PROBE_DISABLED / SKIPPED' : 'PENDING_BACKEND_SUPPORT';

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}} data-testid="runtime-readiness-overview">
            <Card className="page-card" variant="borderless">
                <PageHero
                    title="Runtime Readiness Overview"
                    description="只读汇总 GateM 当前运行边界：Paper-only、MarketData readiness 入口、Adapter no-real、LIVE disabled、permission probe disabled / skipped，以及缺失后端聚合能力。"
                    badge="READONLY"
                />
            </Card>

            <NqRiskBanner
                level={unexpectedSignals.length > 0 || readinessQuery.isError ? 'danger' : 'warning'}
                message={unexpectedSignals.length > 0 ? '发现 READY / allowed / liveAuthorized 信号，必须人工复核' : 'Runtime guard summary：Paper-only / fail-closed'}
                description={(
                    <span>
                        LIVE disabled；NoReal / Fake / Stub / FutureReal 均不代表真实交易能力；permission probe 只展示 disabled / skipped 语义。
                        本页只消费 <Text code>GET /api/adapters/readiness</Text>，不调用 permission probe POST、采集、交易或任何 write endpoint。
                    </span>
                )}
            />

            <Row gutter={[16, 16]}>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label="LIVE status"
                        value={<StatusTag label="LIVE disabled" tone="danger" variant="pill"/>}
                        tone="danger"
                        footer="no LIVE UI entry / no real trading"
                    />
                </Col>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label="Paper ready"
                        value={<StatusTag label="READY_FOR_PAPER_ONLY" tone="info" variant="pill"/>}
                        tone="default"
                        footer="Paper-only boundary, not real authorization"
                    />
                </Col>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label="Adapter no-real"
                        value={<StatusTag label={readinessQuery.isError ? 'UNAVAILABLE' : 'NO_REAL'}
                                          tone={readinessQuery.isError ? 'danger' : 'info'} variant="pill"/>}
                        tone={readinessQuery.isError ? 'danger' : 'default'}
                        footer={`${noRealRows.length} no-real rows from adapter readiness`}
                        loading={readinessQuery.isLoading}
                    />
                </Col>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label="Permission probe"
                        value={<StatusTag label={probeStatus} tone={permissionRows.length > 0 ? 'warning' : 'danger'}
                                          variant="pill"/>}
                        tone="warning"
                        footer="skipped / disabled is not a pass state"
                        loading={readinessQuery.isLoading}
                    />
                </Col>
            </Row>

            <Row gutter={[16, 16]}>
                <Col xs={24} xl={14}>
                    <Card
                        className="page-section"
                        variant="borderless"
                        title="Adapter readiness matrix summary"
                        extra={(
                            <Space size={12}>
                                {readinessQuery.data?.generatedAt ? (
                                    <Text
                                        type="secondary">generated {formatDateTime(readinessQuery.data.generatedAt)}</Text>
                                ) : null}
                                <Button onClick={() => readinessQuery.refetch()} loading={readinessQuery.isFetching}>
                                    刷新只读快照
                                </Button>
                            </Space>
                        )}
                    >
                        {readinessQuery.isError ? (
                            <Alert
                                type="error"
                                showIcon
                                message="adapter readiness unavailable"
                                description={(
                                    <Paragraph style={{marginBottom: 0}}>
                                        未能获取 adapter readiness；Runtime Overview 按 fail-closed 处理，不显示任何可用或
                                        LIVE 授权。
                                        <br/>
                                        <Text
                                            type="secondary">{formatApiError(readinessQuery.error as AppApiError)}</Text>
                                    </Paragraph>
                                )}
                            />
                        ) : (
                            <Table<VenueSummary>
                                rowKey="venue"
                                columns={venueColumns}
                                dataSource={venueSummaries}
                                loading={readinessQuery.isLoading || readinessQuery.isFetching}
                                pagination={false}
                                size="small"
                                scroll={{x: 900}}
                                locale={{emptyText: 'No adapter readiness data; runtime remains fail-closed'}}
                            />
                        )}
                    </Card>
                </Col>
                <Col xs={24} xl={10}>
                    <Card
                        className="page-section"
                        variant="borderless"
                        title="MarketData readiness card"
                        extra={<Link to={MARKETDATA_READINESS_PATH}>Open MarketData</Link>}
                    >
                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                            <DataFreshness
                                source="MarketData readiness"
                                state="disabled"
                                detail="PENDING_BACKEND_SUPPORT"
                            />
                            <Alert
                                type="info"
                                showIcon
                                message="MarketData fresh 是 query-scoped DB freshness，不是 live exchange readiness"
                                description="MarketData 页面已支持 /api/marketdata/readiness 的 FRESH / STALE / GAP / NO_DATA / UNKNOWN 展示；本 overview 当前没有全局 source-health aggregate，因此不伪造 READY。"
                            />
                            <Space size={[8, 8]} wrap>
                                <StatusTag label="MarketData fresh" tone="info" variant="pill"/>
                                <StatusTag label="NO_MIGRATION_MVP" tone="warning" variant="pill"/>
                                <StatusTag label="PENDING_BACKEND_SUPPORT" tone="warning" variant="pill"/>
                            </Space>
                            <Button type="primary">
                                <Link to={MARKETDATA_READINESS_PATH}>View MarketData readiness</Link>
                            </Button>
                        </Space>
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]}>
                <Col xs={24} xl={14}>
                    <Card className="page-section" variant="borderless"
                          title="Runtime blockers / unavailable capabilities">
                        <Table<RuntimeBlocker>
                            rowKey="key"
                            columns={blockerColumns}
                            dataSource={runtimeBlockers}
                            pagination={false}
                            size="small"
                            scroll={{x: 880}}
                        />
                    </Card>
                </Col>
                <Col xs={24} xl={10}>
                    <Card className="page-section" variant="borderless" title="Boundary notes">
                        <List
                            size="small"
                            dataSource={[
                                `Adapter matrix detail: ${adapterMatrixDetail}`,
                                'Paper ready means READY_FOR_PAPER_ONLY; it does not authorize LIVE.',
                                'MarketData fresh means local DB freshness for a submitted query; UNKNOWN / API failure is not ready.',
                                'Adapter no-real means NO_REAL / Fake / Stub / FutureReal remain blocked.',
                                'LIVE readiness not implemented; RealClient / real provider / real exchange adapter not implemented.',
                                'No permission probe POST, no ingestion run-once, no order, no cancel, no withdraw, no transfer.',
                            ]}
                            renderItem={(item) => (
                                <List.Item>
                                    <Text>{item}</Text>
                                </List.Item>
                            )}
                        />
                    </Card>
                </Col>
            </Row>
        </Space>
    );
}

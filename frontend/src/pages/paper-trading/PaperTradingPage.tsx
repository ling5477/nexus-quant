import {
    App,
    Button,
    Card,
    Col,
    Descriptions,
    Form,
    Input,
    Modal,
    Row,
    Select,
    Space,
    Tabs,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {
    NqAmountText,
    NqDangerConfirmButton,
    NqDataTable,
    NqDrawdownChart,
    NqEmptyState,
    NqEnvironmentBadge,
    NqEquityCurveChart,
    NqErrorState,
    NqFilterBar,
    NqLoadingState,
    NqMetricCard,
    NqPageHeader,
    NqPercentText,
    NqPriceText,
    NqRiskBanner,
    NqStatusTag,
    nqNumericColumn,
} from '@/components/nq';
import {
    NqAlertPanel,
    NqHeartbeatPanel,
    NqRecoveryPanel,
    NqScheduleFirePanel,
    NqStabilityCheckPanel,
} from '@/components/paper';
import {
    EXCHANGE_OPTIONS,
    INTERVAL_OPTIONS,
    MARKET_TYPE_OPTIONS,
    PAPER_RUN_STATUS_OPTIONS,
    SYMBOL_OPTIONS,
    TRADE_ENV_OPTIONS,
} from '@/constants/filter-options';
import {
    useCreatePaperTradingRunMutation,
    useEmergencyStopMutation,
    useGenerateDailyReportMutation,
    usePaperAlertsQuery,
    usePaperDailyReportsQuery,
    usePaperHeartbeatsQuery,
    usePaperSchedulesQuery,
    usePaperStabilityChecksQuery,
    usePaperTradingDetailQuery,
    usePaperTradingEmergencyStopsQuery,
    usePaperTradingEquityCurveQuery,
    usePaperTradingListQuery,
    usePaperTradingOrdersQuery,
    usePaperTradingPositionCurveQuery,
    usePaperTradingPositionsQuery,
    usePaperTradingReplayQuery,
    usePaperTradingRiskResultsQuery,
    usePaperTradingTradesQuery,
    useRunRiskOnceMutation,
    useStartPaperTradingRunMutation,
    useStopPaperTradingRunMutation,
} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import {
    defaultPaperTradingListFilters,
    type PaperTradingListFilters,
    type PaperTradingRunCreateRequest,
    type PaperTradingRunItem,
} from '@/types/paper-trading';
import {appEnv} from '@/utils/env';
import {formatDateTime, normalizeOptionalText} from '@/utils/formatters';

type PaperRunRow = PaperTradingRunItem;

const DEFAULT_CREATE_VALUES: PaperTradingRunCreateRequest = {
    publishId: '',
    tradeEnv: 'SIM',
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    intervalCode: '1m',
    configSnapshotJson: '',
};

/** 按时间倒序取最新一条，不依赖后端返回顺序。 */
function latestBy<T>(items: T[], getTime: (item: T) => string | null | undefined): T | null {
    return [...items]
        .filter((item) => Boolean(getTime(item)))
        .sort((left, right) => new Date(getTime(right) as string).getTime() - new Date(getTime(left) as string).getTime())[0]
        ?? null;
}

function sumNullableAmounts(...values: Array<string | number | null | undefined>): number | null {
    let total = 0;
    let hasValue = false;

    for (const value of values) {
        if (value === null || value === undefined || value === '') {
            continue;
        }
        const numeric = Number(value);
        if (!Number.isFinite(numeric)) {
            continue;
        }
        total += numeric;
        hasValue = true;
    }

    return hasValue ? total : null;
}

function pnlTone(value: number | null): 'up' | 'down' | 'muted' {
    if (value === null || value === 0) {
        return 'muted';
    }
    return value > 0 ? 'up' : 'down';
}

export function PaperTradingPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<PaperTradingListFilters>();
    const [createForm] = Form.useForm<PaperTradingRunCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<PaperTradingListFilters>(defaultPaperTradingListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedRow, setSelectedRow] = useState<PaperRunRow | null>(null);
    const [createOpen, setCreateOpen] = useState(false);

    const listQuery = usePaperTradingListQuery(
        {
            publishId: submittedFilters.publishId || undefined,
            status: submittedFilters.status || undefined,
        },
        searchVersion,
    );

    const focusRunId = selectedRow?.paperRunId ?? null;
    const detailQuery = usePaperTradingDetailQuery(focusRunId);
    const ordersQuery = usePaperTradingOrdersQuery(focusRunId);
    const tradesQuery = usePaperTradingTradesQuery(focusRunId);
    const positionsQuery = usePaperTradingPositionsQuery(focusRunId);
    const riskResultsQuery = usePaperTradingRiskResultsQuery(focusRunId);
    const equityCurveQuery = usePaperTradingEquityCurveQuery(focusRunId);
    const positionCurveQuery = usePaperTradingPositionCurveQuery(focusRunId);
    const replayQuery = usePaperTradingReplayQuery(focusRunId);
    const emergencyStopsQuery = usePaperTradingEmergencyStopsQuery(focusRunId);
    const dailyReportsQuery = usePaperDailyReportsQuery(focusRunId);

    // 顶部状态条所需读查询；与右侧/中部面板共享 React Query 缓存键，不重复请求。
    const heartbeatsQuery = usePaperHeartbeatsQuery(focusRunId);
    const schedulesQuery = usePaperSchedulesQuery(focusRunId);
    const alertsQuery = usePaperAlertsQuery(focusRunId);
    const stabilityChecksQuery = usePaperStabilityChecksQuery(focusRunId);

    const createMutation = useCreatePaperTradingRunMutation();
    const startMutation = useStartPaperTradingRunMutation();
    const stopMutation = useStopPaperTradingRunMutation();
    const riskOnceMutation = useRunRiskOnceMutation();
    const emergencyStopMutation = useEmergencyStopMutation();
    const generateDailyReportMutation = useGenerateDailyReportMutation();

    const hasSearched = searchVersion > 0;
    const visibleItems = listQuery.data ?? [];

    // 焦点 run 优先用 detailQuery 的最新数据（mutation 后会失效重取），回退到列表快照，
    // 使顶部状态条 / 操作可用性在启停、紧急停机后反映最新运行态。
    const focusRun = detailQuery.data ?? selectedRow;
    const focusStatus = focusRun?.status ?? selectedRow?.status ?? '';

    // 焦点 run 派生状态（顶部状态条 / 中部摘要）
    const latestHeartbeat = latestBy(heartbeatsQuery.data ?? [], (item) => item.heartbeatTime);
    const latestFireTime = latestBy(schedulesQuery.data ?? [], (item) => item.lastFireTime)?.lastFireTime ?? null;
    const openAlertCount = (alertsQuery.data ?? []).filter((alert) => alert.status === 'OPEN').length;
    const latestStability = latestBy(stabilityChecksQuery.data ?? [], (item) => item.checkWindowEnd);
    const latestRisk = latestBy(riskResultsQuery.data ?? [], (item) => item.createdAt);
    const latestDailyReport = [...(dailyReportsQuery.data ?? [])]
        .sort((left, right) => right.reportDate.localeCompare(left.reportDate))[0] ?? null;
    const paperOrders = ordersQuery.data ?? [];
    const paperTrades = tradesQuery.data ?? [];
    const paperPositions = positionsQuery.data ?? [];
    const equitySnapshots = equityCurveQuery.data ?? [];
    const latestOrder = latestBy(paperOrders, (item) => item.updatedAt);
    const latestTrade = latestBy(paperTrades, (item) => item.tradedAt);
    const latestPosition = latestBy(paperPositions, (item) => item.updatedAt);
    const latestEquitySnapshot = latestBy(equitySnapshots, (item) => item.snapshotTime);
    const latestLoopPnl = latestEquitySnapshot
        ? sumNullableAmounts(latestEquitySnapshot.realizedPnl, latestEquitySnapshot.unrealizedPnl)
        : latestPosition
            ? sumNullableAmounts(latestPosition.realizedPnl, latestPosition.unrealizedPnl)
            : sumNullableAmounts(latestDailyReport?.dailyPnl);

    const columns: ColumnsType<PaperRunRow> = [
        {
            title: 'Paper Run',
            dataIndex: 'paperRunId',
            key: 'paperRunId',
            render: (value: string, record) => (
                <Space direction="vertical" size={2} style={{width: '100%'}}>
                    {/* 渲染完整 paperRunId 文本（E2E 以 hasText 全量 id 定位行），视觉溢出交由纯 CSS 省略，
                        不使用 AntD JS ellipsis，避免 DOM 文本被截断破坏 hasText 定位 */}
                    <span className="nq-mono nq-run-id" title={value}>{value}</span>
                    <Space size={6}>
                        <NqStatusTag status={record.status}/>
                        <NqEnvironmentBadge env={record.tradeEnv}/>
                    </Space>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {record.symbol} · {record.intervalCode} · {record.exchangeCode}
                    </Typography.Text>
                    <Typography.Text type="secondary" className="nq-num" style={{fontSize: 11}}>
                        更新 {formatDateTime(record.updatedAt)}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: '操作',
            key: 'action',
            width: 96,
            render: (_, record) => (
                <Space direction="vertical" size={2}>
                    <Button type="link" size="small" style={{paddingInline: 0}} onClick={() => setSelectedRow(record)}>
                        查看详情
                    </Button>
                    <Button
                        type="link"
                        size="small"
                        style={{paddingInline: 0}}
                        disabled={record.status !== 'CREATED'}
                        onClick={() => handleStart(record.paperRunId)}
                    >
                        启动
                    </Button>
                    <Button
                        type="link"
                        size="small"
                        danger
                        style={{paddingInline: 0}}
                        disabled={record.status !== 'RUNNING'}
                        onClick={() => handleStop(record.paperRunId)}
                    >
                        停止
                    </Button>
                </Space>
            ),
        },
    ];

    const handleSearch = (values: PaperTradingListFilters) => {
        setSubmittedFilters({
            publishId: normalizeOptionalText(values.publishId),
            status: normalizeOptionalText(values.status),
        });
        setSearchVersion((v) => v + 1);
    };

    const handleReset = () => {
        queryForm.resetFields();
        setSubmittedFilters(defaultPaperTradingListFilters);
        setSearchVersion(0);
    };

    const handleStart = (paperRunId: string) => {
        startMutation.mutate(paperRunId, {
            onSuccess: (run) => {
                message.success('Paper run 已启动。');
                setSelectedRow(run);
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    const handleStop = (paperRunId: string) => {
        stopMutation.mutate(paperRunId, {
            onSuccess: (run) => {
                message.success('Paper run 已停止。');
                setSelectedRow(run);
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    const handleCreate = (values: PaperTradingRunCreateRequest) => {
        const payload: PaperTradingRunCreateRequest = {
            publishId: values.publishId.trim(),
            tradeEnv: values.tradeEnv?.trim() || 'SIM',
            exchangeCode: values.exchangeCode?.trim() || 'BINANCE',
            marketType: values.marketType?.trim() || 'SPOT',
            symbol: values.symbol?.trim() || 'BTC-USDT',
            intervalCode: values.intervalCode?.trim() || '1m',
            configSnapshotJson: normalizeOptionalText(values.configSnapshotJson) || undefined,
        };
        createMutation.mutate(payload, {
            onSuccess: (run) => {
                message.success('Paper run 已创建。');
                setSelectedRow(run);
                setCreateOpen(false);
                createForm.resetFields();
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => message.error(formatApiError(error as AppApiError)),
        });
    };

    return (
        <>
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Card className="page-card" bordered={false}>
                    <NqPageHeader
                        title="模拟交易"
                        description="Paper Trading 运行控制台：聚焦运行状态、心跳、调度、告警、恢复、稳定性验收与权益/回撤曲线。基于已发布策略版本创建 SIM/Paper run 并固化全链路快照。"
                        badge="Paper Trading Console"
                        tip={(
                            <NqRiskBanner
                                level="info"
                                message="当前为 PAPER（SIM）模拟环境，LIVE 交易未开启。"
                                description="本页所有下单、撤单、紧急停机均只作用于 SIM/Paper Trading，不会触发真实交易所下单或撤单；不存在一键实盘全平能力。"
                            />
                        )}
                    />
                </Card>

                <NqFilterBar
                    actions={(
                        <Space>
                            <Button type="primary" onClick={() => queryForm.submit()}>
                                查询
                            </Button>
                            <Button onClick={handleReset}>
                                重置
                            </Button>
                            <Button type="primary" ghost onClick={() => setCreateOpen(true)}>
                                创建 Paper Run
                            </Button>
                        </Space>
                    )}
                >
                    <Form
                        form={queryForm}
                        layout="vertical"
                        initialValues={defaultPaperTradingListFilters}
                        onFinish={handleSearch}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item label="发布 ID" name="publishId">
                                    <Input placeholder="按发布记录 ID 筛选"/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label="状态" name="status">
                                    <Select allowClear placeholder="全部状态" options={PAPER_RUN_STATUS_OPTIONS}/>
                                </Form.Item>
                            </Col>
                        </Row>
                    </Form>
                </NqFilterBar>

                <Row gutter={[12, 12]} align="top">
                    {/* 左侧：Paper Run 列表（焦点选择入口） */}
                    <Col xs={24} xl={7} xxl={6}>
                        <Card
                            className="page-section"
                            bordered={false}
                            title="Paper Run 列表"
                            styles={{body: {padding: 0}}}
                            extra={hasSearched ? (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>共 {visibleItems.length} 条记录</Typography.Text>
                            ) : null}
                        >
                            {!hasSearched ? (
                                <div style={{padding: 16}}>
                                    <NqEmptyState description="点击查询后加载 Paper Trading run 列表。"/>
                                </div>
                            ) : listQuery.error ? (
                                <div style={{padding: 16}}>
                                    <NqErrorState
                                        title="Paper Trading run 列表查询失败"
                                        error={listQuery.error as AppApiError}
                                        onRetry={() => setSearchVersion((v) => v + 1)}
                                    />
                                </div>
                            ) : (
                                <NqDataTable<PaperRunRow>
                                    rowKey="paperRunId"
                                    columns={columns}
                                    dataSource={visibleItems}
                                    loading={listQuery.isFetching}
                                    showHeader={false}
                                    pagination={{pageSize: 10, showSizeChanger: false, simple: true}}
                                    rowClassName={(record) => (record.paperRunId === focusRunId ? 'nq-row-active' : '')}
                                    // 列表内部滚动：让 Playwright/用户定位某行时滚动表体而非窗口，
                                    // 避免目标行被粘性页头遮挡导致点击被拦截。
                                    scroll={{y: 420}}
                                    locale={{emptyText: '当前筛选条件下没有 Paper Trading run。'}}
                                />
                            )}
                        </Card>
                    </Col>

                    {/* 焦点 run 控制台主体 */}
                    <Col xs={24} xl={17} xxl={18}>
                        {!selectedRow ? (
                            <Card className="page-section" bordered={false}>
                                <NqEmptyState description="从左侧选择一个 Paper Run，查看运行控制台（状态、曲线、告警、恢复、调度、事实表）。"/>
                            </Card>
                        ) : (
                            <section aria-label="Paper Trading 详情">
                                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                    {/* 顶部状态区 */}
                                    <Card className="page-section" bordered={false} styles={{body: {paddingBottom: 12}}}>
                                        <Space size={8} wrap style={{marginBottom: 12}}>
                                            <Typography.Text strong>运行控制台</Typography.Text>
                                            <NqStatusTag status={focusStatus}/>
                                            <NqEnvironmentBadge env={selectedRow.tradeEnv}/>
                                            <NqEnvironmentBadge env={appEnv.envLabel}/>
                                            <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 12}}>
                                                {selectedRow.paperRunId}
                                            </Typography.Text>
                                        </Space>
                                        <div className="nq-status-strip">
                                            <NqMetricCard label="运行状态" value={<NqStatusTag status={focusStatus}/>}/>
                                            <NqMetricCard
                                                label="心跳"
                                                value={latestHeartbeat ? <NqStatusTag status={latestHeartbeat.status} tone={latestHeartbeat.status === 'STOPPED' ? 'danger' : undefined}/> : '-'}
                                                footer={latestHeartbeat ? formatDateTime(latestHeartbeat.heartbeatTime) : '暂无心跳'}
                                                loading={heartbeatsQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="最近调度触发"
                                                value={<span className="nq-num" style={{fontSize: 13}}>{latestFireTime ? formatDateTime(latestFireTime) : '-'}</span>}
                                                footer={latestFireTime ? undefined : '暂无调度触发'}
                                                loading={schedulesQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="未处理告警"
                                                value={String(openAlertCount)}
                                                tone={openAlertCount > 0 ? 'warning' : 'muted'}
                                                loading={alertsQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="稳定性验收"
                                                value={latestStability ? <NqStatusTag status={latestStability.status} tone={latestStability.status === 'PASSED' ? 'success' : latestStability.status === 'PARTIAL' ? 'warning' : 'danger'}/> : '-'}
                                                footer={latestStability ? `窗口至 ${formatDateTime(latestStability.checkWindowEnd)}` : '暂无验收'}
                                                loading={stabilityChecksQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="风控状态"
                                                value={latestRisk ? <NqStatusTag status={latestRisk.status} tone={latestRisk.status === 'PASSED' ? 'success' : latestRisk.status === 'REJECTED' ? 'danger' : 'warning'}/> : '-'}
                                                footer={latestRisk ? latestRisk.checkType : '暂无风控检查'}
                                                loading={riskResultsQuery.isPending}
                                            />
                                            <NqMetricCard label="交易环境" value={<NqEnvironmentBadge env={selectedRow.tradeEnv}/>} footer="LIVE 未开启"/>
                                        </div>
                                        <Space size={8} wrap style={{marginTop: 12}}>
                                            <Button
                                                type="primary"
                                                size="small"
                                                disabled={focusStatus !== 'CREATED'}
                                                loading={startMutation.isPending}
                                                onClick={() => handleStart(selectedRow.paperRunId)}
                                            >
                                                启动 Paper Run
                                            </Button>
                                            <Button
                                                danger
                                                size="small"
                                                disabled={focusStatus !== 'RUNNING'}
                                                loading={stopMutation.isPending}
                                                onClick={() => handleStop(selectedRow.paperRunId)}
                                            >
                                                停止 Paper Run
                                            </Button>
                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                生命周期操作仅作用于当前 SIM/Paper run；LIVE 未开启，不会触发真实交易所。
                                            </Typography.Text>
                                        </Space>
                                        {detailQuery.error ? (
                                            <div style={{marginTop: 12}}>
                                                <NqErrorState title="Paper run 详情加载失败" error={detailQuery.error as AppApiError}/>
                                            </div>
                                        ) : null}
                                    </Card>

                                    <Card
                                        className="page-section"
                                        bordered={false}
                                        title="Paper 执行闭环"
                                        extra={<Typography.Text type="secondary" style={{fontSize: 12}}>订单 → 成交 → 持仓 / PnL → 风控</Typography.Text>}
                                    >
                                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                            <NqRiskBanner
                                                level="info"
                                                message="只读聚合当前 Paper run 的执行事实。"
                                                description="该摘要复用订单、成交、持仓、资金曲线和风控查询结果，不新增交易动作，不触发真实交易所或 LIVE。"
                                            />
                                            <div className="nq-status-strip">
                                                <NqMetricCard
                                                    label="订单事实"
                                                    value={String(paperOrders.length)}
                                                    footer={latestOrder ? `${latestOrder.status} · ${formatDateTime(latestOrder.updatedAt)}` : '暂无订单'}
                                                    loading={ordersQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="成交事实"
                                                    value={String(paperTrades.length)}
                                                    footer={latestTrade ? formatDateTime(latestTrade.tradedAt) : '暂无成交'}
                                                    loading={tradesQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="持仓事实"
                                                    value={String(paperPositions.length)}
                                                    footer={latestPosition ? formatDateTime(latestPosition.updatedAt) : '暂无持仓'}
                                                    loading={positionsQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="净 PnL"
                                                    value={<NqAmountText value={latestLoopPnl} signed colorBySign/>}
                                                    footer={latestEquitySnapshot ? `权益快照 ${formatDateTime(latestEquitySnapshot.snapshotTime)}` : latestPosition ? '持仓实时汇总' : '暂无 PnL'}
                                                    tone={pnlTone(latestLoopPnl)}
                                                    loading={equityCurveQuery.isPending || positionsQuery.isPending || dailyReportsQuery.isPending}
                                                />
                                                <NqMetricCard
                                                    label="风控闭环"
                                                    value={latestRisk ? <NqStatusTag status={latestRisk.status} tone={latestRisk.status === 'PASSED' ? 'success' : latestRisk.status === 'REJECTED' ? 'danger' : 'warning'}/> : '-'}
                                                    footer={latestRisk ? `${latestRisk.checkType} · ${latestRisk.severity}` : '暂无风控检查'}
                                                    loading={riskResultsQuery.isPending}
                                                />
                                            </div>
                                        </Space>
                                    </Card>

                                    <Row gutter={[12, 12]} align="top">
                                        {/* 中间主区域 */}
                                        <Col xs={24} xl={15}>
                                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                <Card className="page-section" bordered={false} title="权益与回撤曲线">
                                                    {equityCurveQuery.isFetching && (equityCurveQuery.data ?? []).length === 0 ? (
                                                        <NqLoadingState/>
                                                    ) : equityCurveQuery.error ? (
                                                        <NqErrorState error={equityCurveQuery.error as AppApiError} onRetry={() => equityCurveQuery.refetch()}/>
                                                    ) : (equityCurveQuery.data ?? []).length === 0 ? (
                                                        <NqEmptyState description="暂无权益曲线数据，运行产生快照后自动绘制。"/>
                                                    ) : (
                                                        <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                            <NqEquityCurveChart data={equityCurveQuery.data ?? []}/>
                                                            <NqDrawdownChart data={equityCurveQuery.data ?? []}/>
                                                        </Space>
                                                    )}
                                                </Card>

                                                <Card
                                                    className="page-section"
                                                    bordered={false}
                                                    title="最新日报摘要"
                                                    extra={(
                                                        <Button
                                                            size="small"
                                                            type="primary"
                                                            ghost
                                                            loading={generateDailyReportMutation.isPending}
                                                            onClick={() => generateDailyReportMutation.mutate(
                                                                {paperRunId: selectedRow.paperRunId, request: {}},
                                                                {
                                                                    onSuccess: () => message.success('日报已生成。'),
                                                                    onError: (err) => message.error(formatApiError(err as AppApiError)),
                                                                },
                                                            )}
                                                        >
                                                            生成今日日报
                                                        </Button>
                                                    )}
                                                >
                                                    {dailyReportsQuery.isFetching && (dailyReportsQuery.data ?? []).length === 0 ? (
                                                        <NqLoadingState/>
                                                    ) : dailyReportsQuery.error ? (
                                                        <NqErrorState error={dailyReportsQuery.error as AppApiError} onRetry={() => dailyReportsQuery.refetch()}/>
                                                    ) : !latestDailyReport ? (
                                                        <NqEmptyState description="当前 Paper run 暂无日报。"/>
                                                    ) : (
                                                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                            <div className="nq-status-strip">
                                                                <NqMetricCard label="总权益" value={<NqAmountText value={latestDailyReport.totalEquity}/>}/>
                                                                <NqMetricCard
                                                                    label="日盈亏"
                                                                    value={<NqAmountText value={latestDailyReport.dailyPnl} signed colorBySign/>}
                                                                    tone={Number(latestDailyReport.dailyPnl ?? 0) > 0 ? 'up' : Number(latestDailyReport.dailyPnl ?? 0) < 0 ? 'down' : 'default'}
                                                                />
                                                                <NqMetricCard label="日收益率" value={<NqPercentText value={latestDailyReport.dailyReturn} ratio colorBySign/>}/>
                                                                <NqMetricCard label="最大回撤" value={<NqPercentText value={latestDailyReport.maxDrawdown} ratio signed={false}/>} tone="warning"/>
                                                            </div>
                                                            <NqDataTable
                                                                rowKey="reportId"
                                                                pagination={false}
                                                                dataSource={dailyReportsQuery.data ?? []}
                                                                scroll={{x: 1100, y: 240}}
                                                                columns={[
                                                                    {title: '日期', dataIndex: 'reportDate', key: 'reportDate', width: 110, className: 'nq-num'},
                                                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'GENERATED' ? 'success' : v === 'PARTIAL' ? 'warning' : 'danger'}/>},
                                                                    nqNumericColumn({title: '总权益', dataIndex: 'totalEquity', key: 'totalEquity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '日盈亏', dataIndex: 'dailyPnl', key: 'dailyPnl', width: 120, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                                                    nqNumericColumn({title: '日收益率', dataIndex: 'dailyReturn', key: 'dailyReturn', width: 100, render: (v) => <NqPercentText value={v as string} ratio colorBySign/>}),
                                                                    nqNumericColumn({title: '最大回撤', dataIndex: 'maxDrawdown', key: 'maxDrawdown', width: 100, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                                                                    nqNumericColumn({title: '订单数', dataIndex: 'orderCount', key: 'orderCount', width: 80}),
                                                                    nqNumericColumn({title: '成交数', dataIndex: 'tradeCount', key: 'tradeCount', width: 80}),
                                                                    nqNumericColumn({title: '告警数', dataIndex: 'alertCount', key: 'alertCount', width: 80}),
                                                                    {title: '生成时间', dataIndex: 'generatedAt', key: 'generatedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </Space>
                                                    )}
                                                </Card>

                                                <NqStabilityCheckPanel paperRunId={selectedRow.paperRunId}/>
                                            </Space>
                                        </Col>

                                        {/* 右侧：告警 / 恢复 / 心跳 / 调度 / 操作 */}
                                        <Col xs={24} xl={9}>
                                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                <Card className="page-section" bordered={false} title="操作区">
                                                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                            紧急停机仅作用于当前 SIM/Paper run，会写入审计事件，不触发真实 LIVE 下单或撤单。
                                                        </Typography.Text>
                                                        <NqDangerConfirmButton
                                                            size="small"
                                                            block
                                                            disabled={focusStatus !== 'RUNNING'}
                                                            loading={emergencyStopMutation.isPending}
                                                            confirmTitle="确认紧急停机"
                                                            confirmContent="此操作将立即停止当前 Paper run。紧急停机只作用于 SIM/Paper Trading，不会触发真实 LIVE 下单或撤单。确认执行？"
                                                            okText="确认停机"
                                                            onConfirm={() => emergencyStopMutation.mutate(
                                                                {
                                                                    paperRunId: selectedRow.paperRunId,
                                                                    request: {triggerType: 'MANUAL', reason: '手动紧急停机', triggeredBy: 'console-user'},
                                                                },
                                                                {
                                                                    onSuccess: () => {
                                                                        message.success('紧急停机已执行。');
                                                                        setSearchVersion((v) => v + 1);
                                                                    },
                                                                    onError: (err) => message.error(formatApiError(err as AppApiError)),
                                                                },
                                                            )}
                                                        >
                                                            紧急停机
                                                        </NqDangerConfirmButton>
                                                        {(emergencyStopsQuery.data ?? []).length > 0 ? (
                                                            <NqDataTable
                                                                rowKey="emergencyStopId"
                                                                pagination={false}
                                                                dataSource={emergencyStopsQuery.data ?? []}
                                                                scroll={{y: 180}}
                                                                columns={[
                                                                    {title: '触发类型', dataIndex: 'triggerType', key: 'triggerType', width: 110},
                                                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'APPLIED' ? 'danger' : v === 'RESOLVED' ? 'success' : 'warning'}/>},
                                                                    {title: '触发时间', dataIndex: 'triggeredAt', key: 'triggeredAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        ) : null}
                                                    </Space>
                                                </Card>

                                                <NqAlertPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqRecoveryPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqHeartbeatPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqScheduleFirePanel paperRunId={selectedRow.paperRunId}/>
                                            </Space>
                                        </Col>
                                    </Row>

                                    {/* 底部事实表 */}
                                    <Card className="page-section" bordered={false} title="运行事实">
                                        <Descriptions bordered column={3} size="small" style={{marginBottom: 12}}>
                                            <Descriptions.Item label="Paper Run ID">
                                                <span className="nq-mono">{selectedRow.paperRunId}</span>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="发布 ID">
                                                <span className="nq-mono">{selectedRow.publishId}</span>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="策略版本 ID">
                                                <span className="nq-mono">{selectedRow.strategyVersionId || '-'}</span>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="Symbol">{selectedRow.symbol}</Descriptions.Item>
                                            <Descriptions.Item label="周期">{selectedRow.intervalCode}</Descriptions.Item>
                                            <Descriptions.Item label="市场类型">{selectedRow.marketType}</Descriptions.Item>
                                            <Descriptions.Item label="启动时间">{formatDateTime(selectedRow.startedAt)}</Descriptions.Item>
                                            <Descriptions.Item label="停止时间">{formatDateTime(selectedRow.stoppedAt)}</Descriptions.Item>
                                            <Descriptions.Item label="创建人">{selectedRow.createdBy}</Descriptions.Item>
                                        </Descriptions>
                                        <Tabs
                                            items={[
                                                {
                                                    key: 'orders',
                                                    label: '订单',
                                                    children: (
                                                        <PaperFactSection
                                                            query={ordersQuery}
                                                            emptyText="当前 Paper run 暂无订单事实。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="paperOrderId"
                                                                pagination={false}
                                                                dataSource={ordersQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '订单 ID', dataIndex: 'paperOrderId', key: 'paperOrderId', className: 'nq-mono'},
                                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                                    {title: '类型', dataIndex: 'orderType', key: 'orderType', width: 80},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '价格', dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
                                                                    {title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'trades',
                                                    label: '成交',
                                                    children: (
                                                        <PaperFactSection
                                                            query={tradesQuery}
                                                            emptyText="当前 Paper run 暂无成交事实。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="paperTradeId"
                                                                pagination={false}
                                                                dataSource={tradesQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '成交 ID', dataIndex: 'paperTradeId', key: 'paperTradeId', className: 'nq-mono'},
                                                                    {title: '订单 ID', dataIndex: 'paperOrderId', key: 'paperOrderId', className: 'nq-mono'},
                                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '价格', dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '手续费', dataIndex: 'fee', key: 'fee', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    {title: '成交时间', dataIndex: 'tradedAt', key: 'tradedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'positions',
                                                    label: '持仓',
                                                    children: (
                                                        <PaperFactSection
                                                            query={positionsQuery}
                                                            emptyText="当前 Paper run 暂无持仓事实。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="paperPositionId"
                                                                pagination={false}
                                                                dataSource={positionsQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 120},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '均价', dataIndex: 'avgPrice', key: 'avgPrice', width: 120, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '已实现盈亏', dataIndex: 'realizedPnl', key: 'realizedPnl', width: 140, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                                                    nqNumericColumn({title: '未实现盈亏', dataIndex: 'unrealizedPnl', key: 'unrealizedPnl', width: 140, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                                                    {title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'snapshots',
                                                    label: '快照',
                                                    children: (
                                                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                            <SnapshotBlock title="Publish Snapshot" content={selectedRow.publishSnapshotJson}/>
                                                            <SnapshotBlock title="Strategy Version Snapshot" content={selectedRow.strategyVersionSnapshotJson}/>
                                                            <SnapshotBlock title="Dataset Snapshot" content={selectedRow.datasetSnapshotJson}/>
                                                            <SnapshotBlock title="Param Snapshot" content={selectedRow.paramSnapshotJson}/>
                                                            <SnapshotBlock title="Config Snapshot" content={selectedRow.configSnapshotJson}/>
                                                        </Space>
                                                    ),
                                                },
                                                {
                                                    key: 'risk-results',
                                                    label: '风控结果',
                                                    children: (
                                                        <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                            <Button
                                                                size="small"
                                                                loading={riskOnceMutation.isPending}
                                                                onClick={() => riskOnceMutation.mutate(selectedRow.paperRunId, {
                                                                    onSuccess: () => message.success('风控检查已执行。'),
                                                                    onError: (err) => message.error(formatApiError(err as AppApiError)),
                                                                })}
                                                            >
                                                                执行风控检查
                                                            </Button>
                                                            <PaperFactSection
                                                                query={riskResultsQuery}
                                                                emptyText="当前 Paper run 暂无风控检查结果。"
                                                            >
                                                                <NqDataTable
                                                                    rowKey="riskResultId"
                                                                    pagination={false}
                                                                    dataSource={riskResultsQuery.data ?? []}
                                                                    scroll={{x: 900}}
                                                                    columns={[
                                                                        {title: '检查类型', dataIndex: 'checkType', key: 'checkType', width: 180},
                                                                        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v} tone={v === 'PASSED' ? 'success' : v === 'REJECTED' ? 'danger' : 'warning'}/>},
                                                                        {title: '严重程度', dataIndex: 'severity', key: 'severity', width: 100},
                                                                        {title: '消息', dataIndex: 'message', key: 'message'},
                                                                        {title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    ]}
                                                                />
                                                            </PaperFactSection>
                                                        </Space>
                                                    ),
                                                },
                                                {
                                                    key: 'equity-curve',
                                                    label: '资金曲线',
                                                    children: (
                                                        <PaperFactSection
                                                            query={equityCurveQuery}
                                                            emptyText="当前 Paper run 暂无资金曲线数据。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="equitySnapshotId"
                                                                pagination={false}
                                                                dataSource={equityCurveQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '时间', dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    nqNumericColumn({title: '总权益', dataIndex: 'totalEquity', key: 'totalEquity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '现金', dataIndex: 'cashBalance', key: 'cashBalance', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '持仓市值', dataIndex: 'positionValue', key: 'positionValue', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '回撤', dataIndex: 'drawdown', key: 'drawdown', width: 100, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                                                                    {title: '来源', dataIndex: 'source', key: 'source', width: 100},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'position-curve',
                                                    label: '持仓曲线',
                                                    children: (
                                                        <PaperFactSection
                                                            query={positionCurveQuery}
                                                            emptyText="当前 Paper run 暂无持仓曲线数据。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="positionSnapshotId"
                                                                pagination={false}
                                                                dataSource={positionCurveQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 120},
                                                                    {title: '时间', dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    nqNumericColumn({title: '均价', dataIndex: 'avgPrice', key: 'avgPrice', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '标记价', dataIndex: 'markPrice', key: 'markPrice', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '市值', dataIndex: 'positionValue', key: 'positionValue', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    {title: '来源', dataIndex: 'source', key: 'source', width: 100},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                                {
                                                    key: 'replay',
                                                    label: '交易复盘',
                                                    children: (
                                                        <PaperFactSection
                                                            query={replayQuery}
                                                            emptyText="当前 Paper run 暂无交易复盘记录。"
                                                        >
                                                            <NqDataTable
                                                                rowKey="replayRecordId"
                                                                pagination={false}
                                                                dataSource={replayQuery.data ?? []}
                                                                scroll={{x: 900}}
                                                                columns={[
                                                                    {title: '时间', dataIndex: 'replayTime', key: 'replayTime', width: 170, render: (v: string) => formatDateTime(v)},
                                                                    {title: '事件类型', dataIndex: 'eventType', key: 'eventType', width: 140},
                                                                    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 120},
                                                                    {title: '方向', dataIndex: 'side', key: 'side', width: 80},
                                                                    nqNumericColumn({title: '价格', dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                                                    nqNumericColumn({title: '数量', dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                                                    {title: '原因', dataIndex: 'reason', key: 'reason'},
                                                                ]}
                                                            />
                                                        </PaperFactSection>
                                                    ),
                                                },
                                            ]}
                                        />
                                    </Card>
                                </Space>
                            </section>
                        )}
                    </Col>
                </Row>
            </Space>

            <Modal
                open={createOpen}
                title="创建 Paper Trading run"
                onCancel={() => setCreateOpen(false)}
                onOk={() => createForm.submit()}
                confirmLoading={createMutation.isPending}
                destroyOnClose
            >
                <Form
                    form={createForm}
                    layout="vertical"
                    initialValues={DEFAULT_CREATE_VALUES}
                    onFinish={handleCreate}
                >
                    <Form.Item
                        label="发布 ID"
                        name="publishId"
                        rules={[{required: true, message: '请输入发布 ID'}]}
                    >
                        <Input placeholder="发布记录 ID（publishId）"/>
                    </Form.Item>
                    <Form.Item label="交易环境" name="tradeEnv" rules={[{required: true}]}>
                        <Select options={TRADE_ENV_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="交易所" name="exchangeCode" rules={[{required: true}]}>
                        <Select options={EXCHANGE_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="市场类型" name="marketType" rules={[{required: true}]}>
                        <Select options={MARKET_TYPE_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="Symbol" name="symbol" rules={[{required: true}]}>
                        <Select showSearch options={SYMBOL_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="周期" name="intervalCode" rules={[{required: true}]}>
                        <Select options={INTERVAL_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label="运行配置快照 JSON（可空）" name="configSnapshotJson">
                        <Input.TextArea rows={3} placeholder='{"feeRate":"0.001","slippageBps":"10"}'/>
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
}

/**
 * PaperFactSection — 事实表三态包装（加载 / 错误 / 空 / 内容）。
 * 统一底部事实表的状态表达，空态文案由调用方按业务口径传入（E2E 依赖原文）。
 */
interface PaperFactSectionProps {
    query: {isFetching: boolean; error: unknown; data?: unknown[]};
    emptyText: string;
    children: React.ReactNode;
}

function PaperFactSection({query, emptyText, children}: PaperFactSectionProps) {
    const data = query.data ?? [];
    if (query.isFetching && data.length === 0) {
        return <NqLoadingState/>;
    }
    if (query.error) {
        return <NqErrorState error={query.error as AppApiError}/>;
    }
    if (data.length === 0) {
        return <NqEmptyState description={emptyText}/>;
    }
    return <>{children}</>;
}

function SnapshotBlock({title, content}: {title: string; content: string | null | undefined}) {
    return (
        <Card size="small" title={title}>
            <Typography.Paragraph
                className="nq-mono"
                copyable={Boolean(content)}
                style={{margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all'}}
            >
                {content || '-'}
            </Typography.Paragraph>
        </Card>
    );
}

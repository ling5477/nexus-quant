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
import {useEffect, useState, type ReactNode} from 'react';
import {Link} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {
    NqAmountText,
    NqDangerConfirmButton,
    NqDataTable,
    NqEmptyState,
    NqEnvironmentBadge,
    NqErrorState,
    NqFilterBar,
    NqLoadingState,
    NqMetricCard,
    NqPageHeader,
    NqPriceText,
    NqRiskBanner,
    NqStatusTag,
    nqNumericColumn,
} from '@/components/nq';
import {NqAlertPanel, NqRecoveryPanel} from '@/components/paper';
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
    usePaperRunSummaryQuery,
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
    type EquityCurveSnapshotItem,
    type PaperRunSummaryResponse,
    type PaperRiskCheckResultItem,
    type PaperTradingListFilters,
    type PaperTradingOrderItem,
    type PaperTradingPositionItem,
    type PaperTradingRunCreateRequest,
    type PaperTradingRunItem,
    type PaperTradingTradeItem,
    type PositionCurveSnapshotItem,
    type TradeReplayRecordItem,
} from '@/types/paper-trading';
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

const EXECUTION_NAV_ITEMS = [
    {
        title: '组合分析',
        description: '跨 Paper run 的组合表现、分组摘要和组合曲线已迁出。',
        action: '查看组合分析',
        to: '/paper-trading/portfolio',
    },
    {
        title: '执行诊断',
        description: '无订单、有单无成交、亏损和风控拦截等诊断已迁出。',
        action: '查看执行诊断',
        to: '/paper-trading/diagnostics',
    },
    {
        title: '策略评估',
        description: 'Strategy Evaluation 与 rules-based Auto Review 已迁出。',
        action: '查看策略评估',
        to: '/paper-trading/reviews',
    },
];

function asRunSummary(raw: unknown): PaperRunSummaryResponse | null {
    return raw && !Array.isArray(raw) && (raw as PaperRunSummaryResponse).counts
        ? (raw as PaperRunSummaryResponse)
        : null;
}

function amountTone(value: string | number | null | undefined): 'up' | 'down' | 'default' {
    const numeric = value === null || value === undefined || value === '' ? null : Number(value);
    if (numeric === null || !Number.isFinite(numeric) || numeric === 0) {
        return 'default';
    }
    return numeric > 0 ? 'up' : 'down';
}

/**
 * PaperTradingRunsPage 是 K5-C4 后的 `/paper-trading/runs` execution-only 页面。
 *
 * Why:
 * K5-C1/C2/C3 已把组合分析、执行诊断、策略评估和自动复盘迁移到独立子路由；runs 继续挂旧完整页会让
 * portfolio / diagnostics / evaluation / review query 在执行入口首屏加载，违背本轮“runs 只做执行层”的边界。
 *
 * What / How:
 * 本页只实例化 run list、run detail、run summary、run fact tabs 与 lifecycle mutations。跨 run 分析能力只以
 * Link/Button 跳转暴露，不在本路由挂载 dashboard，也不触发已迁出的聚合 query key。
 *
 * Edge / Failure Modes:
 * - 未查询列表时显示操作性空态，不自动读取 run list。
 * - summary 失败时只影响执行摘要，不阻塞订单 / 成交 / 持仓等事实 Tab。
 * - 所有操作仍显式标注 SIM/Paper only；LIVE、真实交易所、credential 和 AI/DH runtime 均不触达。
 */
export function PaperTradingRunsPage() {
    const {message} = App.useApp();
    const [queryForm] = Form.useForm<PaperTradingListFilters>();
    const [createForm] = Form.useForm<PaperTradingRunCreateRequest>();
    const [submittedFilters, setSubmittedFilters] = useState<PaperTradingListFilters>(defaultPaperTradingListFilters);
    const [searchVersion, setSearchVersion] = useState(0);
    const [selectedRow, setSelectedRow] = useState<PaperRunRow | null>(null);
    const [createOpen, setCreateOpen] = useState(false);
    // 保持旧 runs 页 factTab 行为：切换 run 时回到 snapshots，明细 tab 按激活项懒加载，避免首屏扇出事实查询。
    const [factTab, setFactTab] = useState('snapshots');

    const listQuery = usePaperTradingListQuery(
        {
            publishId: submittedFilters.publishId || undefined,
            status: submittedFilters.status || undefined,
        },
        searchVersion,
    );

    const focusRunId = selectedRow?.paperRunId ?? null;
    const detailQuery = usePaperTradingDetailQuery(focusRunId);
    const summaryQuery = usePaperRunSummaryQuery(focusRunId);
    const ordersQuery = usePaperTradingOrdersQuery(focusRunId, factTab === 'orders');
    const tradesQuery = usePaperTradingTradesQuery(focusRunId, factTab === 'trades');
    const positionsQuery = usePaperTradingPositionsQuery(focusRunId, factTab === 'positions');
    const riskResultsQuery = usePaperTradingRiskResultsQuery(focusRunId, factTab === 'risk-results');
    const equityCurveQuery = usePaperTradingEquityCurveQuery(focusRunId);
    const positionCurveQuery = usePaperTradingPositionCurveQuery(focusRunId, factTab === 'position-curve');
    const replayQuery = usePaperTradingReplayQuery(focusRunId, factTab === 'replay');
    const emergencyStopsQuery = usePaperTradingEmergencyStopsQuery(focusRunId);

    useEffect(() => {
        setFactTab('snapshots');
    }, [focusRunId]);

    const createMutation = useCreatePaperTradingRunMutation();
    const startMutation = useStartPaperTradingRunMutation();
    const stopMutation = useStopPaperTradingRunMutation();
    const riskOnceMutation = useRunRiskOnceMutation();
    const emergencyStopMutation = useEmergencyStopMutation();

    const hasSearched = searchVersion > 0;
    const visibleItems = listQuery.data ?? [];
    const focusRun = detailQuery.data ?? selectedRow;
    const focusStatus = focusRun?.status ?? selectedRow?.status ?? '';
    const summary = asRunSummary(summaryQuery.data);
    const orderCount = summary?.counts.orderCount ?? null;
    const fillCount = summary?.counts.fillCount ?? summary?.counts.tradeCount ?? null;
    const positionCount = summary?.counts.positionCount ?? null;
    const openAlertCount = summary?.counts.openAlertCount ?? null;
    const netPnl = summary?.resultReview.netPnl ?? null;
    const latestRisk = summary?.latest.riskResult ?? null;

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

    const columns: ColumnsType<PaperRunRow> = [
        {
            title: 'Paper Run',
            dataIndex: 'paperRunId',
            key: 'paperRunId',
            render: (value: string, record) => (
                <Space direction="vertical" size={2} style={{width: '100%'}}>
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

    return (
        <>
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Card className="page-card" variant="borderless">
                    <NqPageHeader
                        title="模拟交易"
                        description="Paper Run 执行入口：创建、启动、停止、恢复与查看单 run 执行事实。组合分析、执行诊断和策略评估已迁出到独立页面。"
                        badge="Runs execution layer"
                        tip={(
                            <NqRiskBanner
                                level="info"
                                message="当前为 PAPER（SIM）模拟环境，LIVE 交易未开启。"
                                description="本页操作只作用于 SIM/Paper run，不触发真实交易所下单、撤单或 credential 访问。"
                            />
                        )}
                    />
                </Card>

                <ExecutionNavigationCard/>

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
                    <Col xs={24} xl={7} xxl={6}>
                        <Card
                            className="page-section"
                            variant="borderless"
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
                                    scroll={{y: 420}}
                                    locale={{emptyText: '当前筛选条件下没有 Paper Trading run。'}}
                                />
                            )}
                        </Card>
                    </Col>

                    <Col xs={24} xl={17} xxl={18}>
                        {!selectedRow ? (
                            <Card className="page-section" variant="borderless">
                                <NqEmptyState description="从左侧选择一个 Paper Run，查看状态、操作、恢复事件与执行事实。"/>
                            </Card>
                        ) : (
                            <section aria-label="Paper Trading 详情">
                                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                    <Card className="page-section" variant="borderless">
                                        <Space size={8} wrap style={{marginBottom: 12}}>
                                            <Typography.Text strong>运行控制台</Typography.Text>
                                            <NqStatusTag status={focusStatus}/>
                                            <NqEnvironmentBadge env={selectedRow.tradeEnv}/>
                                            <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 12}}>
                                                {selectedRow.paperRunId}
                                            </Typography.Text>
                                        </Space>

                                        <div className="nq-status-strip">
                                            <NqMetricCard label="运行状态" value={<NqStatusTag status={focusStatus}/>}/>
                                            <NqMetricCard label="订单事实" value={orderCount === null ? '-' : String(orderCount)} loading={summaryQuery.isPending}/>
                                            <NqMetricCard label="成交事实" value={fillCount === null ? '-' : String(fillCount)} loading={summaryQuery.isPending}/>
                                            <NqMetricCard label="持仓事实" value={positionCount === null ? '-' : String(positionCount)} loading={summaryQuery.isPending}/>
                                            <NqMetricCard
                                                label="净 PnL"
                                                value={<NqAmountText value={netPnl} signed colorBySign/>}
                                                tone={amountTone(netPnl)}
                                                loading={summaryQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="风控闭环"
                                                value={latestRisk ? <NqStatusTag status={latestRisk.status} tone={latestRisk.status === 'PASSED' ? 'success' : latestRisk.status === 'REJECTED' ? 'danger' : 'warning'}/> : '-'}
                                                footer={latestRisk ? `${latestRisk.checkType} · ${latestRisk.severity}` : '暂无风控检查'}
                                                loading={summaryQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label="未处理告警"
                                                value={openAlertCount === null ? '-' : String(openAlertCount)}
                                                tone={openAlertCount && openAlertCount > 0 ? 'warning' : 'muted'}
                                                loading={summaryQuery.isPending}
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
                                        ) : summaryQuery.error ? (
                                            <Typography.Text type="warning" style={{display: 'block', marginTop: 12, fontSize: 12}}>
                                                运行摘要加载失败；订单、成交、持仓等事实 Tab 可继续独立查看。
                                            </Typography.Text>
                                        ) : null}
                                    </Card>

                                    <Row gutter={[12, 12]} align="top">
                                        <Col xs={24} xl={15}>
                                            <RunFactsCard
                                                selectedRow={selectedRow}
                                                factTab={factTab}
                                                setFactTab={setFactTab}
                                                ordersQuery={ordersQuery}
                                                tradesQuery={tradesQuery}
                                                positionsQuery={positionsQuery}
                                                riskResultsQuery={riskResultsQuery}
                                                equityCurveQuery={equityCurveQuery}
                                                positionCurveQuery={positionCurveQuery}
                                                replayQuery={replayQuery}
                                                riskOncePending={riskOnceMutation.isPending}
                                                onRunRiskOnce={() => riskOnceMutation.mutate(selectedRow.paperRunId, {
                                                    onSuccess: () => message.success('风控检查已执行。'),
                                                    onError: (err) => message.error(formatApiError(err as AppApiError)),
                                                })}
                                            />
                                        </Col>
                                        <Col xs={24} xl={9}>
                                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                <Card className="page-section" variant="borderless" title="Run 操作">
                                                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                            紧急停机只作用于当前 SIM/Paper run，会记录停机事件，不触发真实 LIVE 下单或撤单。
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
                                                <NqRecoveryPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqAlertPanel paperRunId={selectedRow.paperRunId}/>
                                            </Space>
                                        </Col>
                                    </Row>
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
 * ExecutionNavigationCard 只提供迁出能力的路由入口。
 *
 * Why:
 * 直接删除入口会让用户误以为分析能力消失；用导航卡明确说明能力已迁出，同时保证本路由不挂载对应 dashboard
 * 与跨 run 聚合 query。
 */
function ExecutionNavigationCard() {
    return (
        <Card className="page-section" variant="borderless" title="分析能力入口">
            <Row gutter={[12, 12]}>
                {EXECUTION_NAV_ITEMS.map((item) => (
                    <Col xs={24} md={8} key={item.to}>
                        <Card size="small" title={item.title}>
                            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                    {item.description}
                                </Typography.Text>
                                <Link to={item.to}>
                                    <Button type="primary" ghost block>{item.action}</Button>
                                </Link>
                            </Space>
                        </Card>
                    </Col>
                ))}
            </Row>
        </Card>
    );
}

interface RunFactsCardProps {
    selectedRow: PaperTradingRunItem;
    factTab: string;
    setFactTab: (tab: string) => void;
    ordersQuery: {isFetching: boolean; error: unknown; data?: PaperTradingOrderItem[]};
    tradesQuery: {isFetching: boolean; error: unknown; data?: PaperTradingTradeItem[]};
    positionsQuery: {isFetching: boolean; error: unknown; data?: PaperTradingPositionItem[]};
    riskResultsQuery: {isFetching: boolean; error: unknown; data?: PaperRiskCheckResultItem[]};
    equityCurveQuery: {isFetching: boolean; error: unknown; data?: EquityCurveSnapshotItem[]};
    positionCurveQuery: {isFetching: boolean; error: unknown; data?: PositionCurveSnapshotItem[]};
    replayQuery: {isFetching: boolean; error: unknown; data?: TradeReplayRecordItem[]};
    riskOncePending: boolean;
    onRunRiskOnce: () => void;
}

/**
 * RunFactsCard 承载单个 Paper Run 的事实表。
 *
 * Boundary:
 * 这里保留 factTab 与订单 / 成交 / 持仓 / 风控 / 曲线 / replay 等 run-local 查询；不读取 portfolio、diagnostics、
 * strategy evaluation 或 auto review 聚合结果。Tab 内容都是当前 run 的执行事实，不做跨 run 分析或投资判断。
 */
function RunFactsCard({
    selectedRow,
    factTab,
    setFactTab,
    ordersQuery,
    tradesQuery,
    positionsQuery,
    riskResultsQuery,
    equityCurveQuery,
    positionCurveQuery,
    replayQuery,
    riskOncePending,
    onRunRiskOnce,
}: RunFactsCardProps) {
    return (
        <Card className="page-section" variant="borderless" title="运行事实">
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
                activeKey={factTab}
                onChange={setFactTab}
                items={[
                    {
                        key: 'orders',
                        label: '订单',
                        children: (
                            <PaperFactSection query={ordersQuery} emptyText="当前 Paper run 暂无订单事实。">
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
                            <PaperFactSection query={tradesQuery} emptyText="当前 Paper run 暂无成交事实。">
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
                            <PaperFactSection query={positionsQuery} emptyText="当前 Paper run 暂无持仓事实。">
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
                                <Button size="small" loading={riskOncePending} onClick={onRunRiskOnce}>
                                    执行风控检查
                                </Button>
                                <PaperFactSection query={riskResultsQuery} emptyText="当前 Paper run 暂无风控检查结果。">
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
                            <PaperFactSection query={equityCurveQuery} emptyText="当前 Paper run 暂无资金曲线数据。">
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
                            <PaperFactSection query={positionCurveQuery} emptyText="当前 Paper run 暂无持仓曲线数据。">
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
                            <PaperFactSection query={replayQuery} emptyText="当前 Paper run 暂无交易复盘记录。">
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
    );
}

interface PaperFactSectionProps {
    query: {isFetching: boolean; error: unknown; data?: unknown[]};
    emptyText: string;
    children: ReactNode;
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

function SnapshotBlock({title, content}: {title: string; content?: string | null}) {
    return (
        <Card size="small" title={title}>
            <Typography.Paragraph className="nq-mono" style={{whiteSpace: 'pre-wrap', marginBottom: 0}}>
                {content || '-'}
            </Typography.Paragraph>
        </Card>
    );
}

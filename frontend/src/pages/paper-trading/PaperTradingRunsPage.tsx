import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
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

import {showApiError} from '@/api/errors';
import {NqAmountText, NqDangerConfirmButton, NqDataTable, NqEmptyState, TradingEnvironmentTag, NqErrorState, NqFilterBar, NqLoadingState, NqMetricCard, NqPageHeader, NqPercentText, NqPriceText, ApplicationRiskAlert, nqNumericColumn} from '@/components/nq';
import {NqAlertPanel, NqHeartbeatPanel, NqRecoveryPanel, NqScheduleFirePanel, NqStabilityCheckPanel} from '@/components/paper';
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
    usePaperDailyReportsQuery,
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
    type PaperRunDailyReportItem,
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
        get title() { return t('pages:portfolioAnalysis'); },
        get description() { return t('pages:crossRunPortfolioPerformanceGroupedSummariesAndPortfolioCurvesAreNowOnASeparatePage'); },
        get action() { return t('pages:viewPortfolioAnalysis'); },
        to: '/paper-trading/portfolio',
    },
    {
        get title() { return t('pages:executionDiagnostics'); },
        get description() { return t('pages:diagnosticsForMissingOrdersUnfilledOrdersLossesAndRiskBlocksAreNowOnASeparatePage'); },
        get action() { return t('pages:viewExecutionDiagnostics'); },
        to: '/paper-trading/diagnostics',
    },
    {
        get title() { return t('pages:strategyEvaluation'); },
        get description() { return t('pages:strategyEvaluationAndRuleBasedReviewsAreNowOnASeparatePage'); },
        get action() { return t('pages:viewStrategyEvaluation'); },
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
    useTranslation('pages');
    const {message} = App.useApp();
    const [queryForm] = useLocalizedForm<PaperTradingListFilters>();
    const [createForm] = useLocalizedForm<PaperTradingRunCreateRequest>();
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
                message.success(t('pages:paperRunStarted'));
                setSelectedRow(run);
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => showApiError(error as AppApiError, message),
        });
    };

    const handleStop = (paperRunId: string) => {
        stopMutation.mutate(paperRunId, {
            onSuccess: (run) => {
                message.success(t('pages:paperRunStopped'));
                setSelectedRow(run);
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => showApiError(error as AppApiError, message),
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
                message.success(t('pages:paperRunCreated'));
                setSelectedRow(run);
                setCreateOpen(false);
                createForm.resetFields();
                setSearchVersion((v) => v + 1);
            },
            onError: (error) => showApiError(error as AppApiError, message),
        });
    };

    const columns: ColumnsType<PaperRunRow> = [
        {
            title: t('pages:paperRun'),
            dataIndex: 'paperRunId',
            key: 'paperRunId',
            render: (value: string, record) => (
                <Space direction="vertical" size={2} style={{width: '100%'}}>
                    <span className="nq-mono nq-run-id" title={value}>{value}</span>
                    <Space size={6}>
                        <StatusTag title="" variant="pill" status={record.status}/>
                        <TradingEnvironmentTag env={record.tradeEnv}/>
                    </Space>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {record.symbol} · {record.intervalCode} · {record.exchangeCode}
                    </Typography.Text>
                    <Typography.Text type="secondary" className="nq-num" style={{fontSize: 11}}>
                        {t('pages:updated')}{formatDateTime(record.updatedAt)}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: t('pages:actions'),
            key: 'action',
            width: 96,
            render: (_, record) => (
                <Space direction="vertical" size={2}>
                    <Button type="link" size="small" style={{paddingInline: 0}} onClick={() => setSelectedRow(record)}>
                        {t('pages:viewDetails')}</Button>
                    <Button
                        type="link"
                        size="small"
                        style={{paddingInline: 0}}
                        disabled={record.status !== 'CREATED'}
                        onClick={() => handleStart(record.paperRunId)}
                    >
                        {t('pages:start')}</Button>
                    <Button
                        type="link"
                        size="small"
                        danger
                        style={{paddingInline: 0}}
                        disabled={record.status !== 'RUNNING'}
                        onClick={() => handleStop(record.paperRunId)}
                    >
                        {t('pages:stop')}</Button>
                </Space>
            ),
        },
    ];

    return (
        <>
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Card className="page-card" variant="borderless">
                    <NqPageHeader
                        title={t('pages:paperTrading')}
                        description={t('pages:createStartStopRecoverAndInspectIndividualPaperRunsPortfolioAnalysisExecutionDiagnosticsAndStrategyE')}
                        badge={t('pages:runExecutionLayer')}
                        tip={(
                            <ApplicationRiskAlert
                                level="info"
                                message={t('pages:theCurrentEnvironmentIsPaperSimLiveTradingIsDisabled')}
                                description={t('pages:actionsAffectSimPaperRunsOnlyTheyDoNotPlaceOrCancelRealExchangeOrdersOrAccessCredentials')}
                            />
                        )}
                    />
                </Card>

                <ExecutionNavigationCard/>

                <NqFilterBar
                    actions={(
                        <Space>
                            <Button type="primary" onClick={() => queryForm.submit()}>
                                {t('pages:search')}</Button>
                            <Button onClick={handleReset}>
                                {t('pages:reset')}</Button>
                            <Button type="primary" ghost onClick={() => setCreateOpen(true)}>
                                {t('pages:createPaperRun')}</Button>
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
                                <Form.Item label={t('pages:publishId')} name="publishId">
                                    <Input placeholder={t('pages:filterByPublishRecordId')}/>
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12} xl={6}>
                                <Form.Item label={t('pages:status')} name="status">
                                    <Select allowClear placeholder={t('pages:allStatuses')} options={PAPER_RUN_STATUS_OPTIONS}/>
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
                            title={t('pages:paperRuns')}
                            styles={{body: {padding: 0}}}
                            extra={hasSearched ? (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:total')}{visibleItems.length} {t('pages:records')}</Typography.Text>
                            ) : null}
                        >
                            {!hasSearched ? (
                                <div style={{padding: 16}}>
                                    <NqEmptyState description={t('pages:searchToLoadPaperTradingRuns')}/>
                                </div>
                            ) : listQuery.error ? (
                                <div style={{padding: 16}}>
                                    <NqErrorState
                                        title={t('pages:failedToQueryPaperTradingRuns')}
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
                                    locale={{emptyText: t('pages:noPaperTradingRunsMatchTheseFilters')}}
                                />
                            )}
                        </Card>
                    </Col>

                    <Col xs={24} xl={17} xxl={18}>
                        {!selectedRow ? (
                            <Card className="page-section" variant="borderless">
                                <NqEmptyState description={t('pages:selectAPaperRunToInspectItsStatusActionsRecoveryEventsAndExecutionFacts')}/>
                            </Card>
                        ) : (
                            <section aria-label={t('pages:paperTradingDetails')}>
                                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                    <Card className="page-section" variant="borderless">
                                        <Space size={8} wrap style={{marginBottom: 12}}>
                                            <Typography.Text strong>{t('pages:runConsole')}</Typography.Text>
                                            <StatusTag title="" variant="pill" status={focusStatus}/>
                                            <TradingEnvironmentTag env={selectedRow.tradeEnv}/>
                                            <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 12}}>
                                                {selectedRow.paperRunId}
                                            </Typography.Text>
                                        </Space>

                                        <div className="nq-status-strip">
                                            <NqMetricCard label={t('pages:runStatus')} value={<StatusTag title="" variant="pill" status={focusStatus}/>}/>
                                            <NqMetricCard label={t('pages:orderFacts')} value={orderCount === null ? '-' : String(orderCount)} loading={summaryQuery.isPending}/>
                                            <NqMetricCard label={t('pages:tradeFacts')} value={fillCount === null ? '-' : String(fillCount)} loading={summaryQuery.isPending}/>
                                            <NqMetricCard label={t('pages:positionFacts')} value={positionCount === null ? '-' : String(positionCount)} loading={summaryQuery.isPending}/>
                                            <NqMetricCard
                                                label={t('pages:netPnl2')}
                                                value={<NqAmountText value={netPnl} signed colorBySign/>}
                                                tone={amountTone(netPnl)}
                                                loading={summaryQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label={t('pages:riskControlLifecycle')}
                                                value={latestRisk ? <StatusTag title="" variant="pill" status={latestRisk.status} tone={latestRisk.status === 'PASSED' ? 'success' : latestRisk.status === 'REJECTED' ? 'danger' : 'warning'}/> : '-'}
                                                footer={latestRisk ? `${latestRisk.checkType} · ${latestRisk.severity}` : t('pages:noRiskChecks')}
                                                loading={summaryQuery.isPending}
                                            />
                                            <NqMetricCard
                                                label={t('pages:unresolvedAlerts')}
                                                value={openAlertCount === null ? '-' : String(openAlertCount)}
                                                tone={openAlertCount && openAlertCount > 0 ? 'warning' : 'muted'}
                                                loading={summaryQuery.isPending}
                                            />
                                            <NqMetricCard label={t('pages:tradingEnvironment')} value={<TradingEnvironmentTag env={selectedRow.tradeEnv}/>} footer={t('pages:liveDisabled2')}/>
                                        </div>

                                        <Space size={8} wrap style={{marginTop: 12}}>
                                            <Button
                                                type="primary"
                                                size="small"
                                                disabled={focusStatus !== 'CREATED'}
                                                loading={startMutation.isPending}
                                                onClick={() => handleStart(selectedRow.paperRunId)}
                                            >
                                                {t('pages:startPaperRun')}</Button>
                                            <Button
                                                danger
                                                size="small"
                                                disabled={focusStatus !== 'RUNNING'}
                                                loading={stopMutation.isPending}
                                                onClick={() => handleStop(selectedRow.paperRunId)}
                                            >
                                                {t('pages:stopPaperRun')}</Button>
                                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                {t('pages:lifecycleActionsAffectThisSimPaperRunOnlyLiveIsDisabledAndNoRealExchangeActionIsTriggered')}</Typography.Text>
                                        </Space>

                                        {detailQuery.error ? (
                                            <div style={{marginTop: 12}}>
                                                <NqErrorState title={t('pages:failedToLoadPaperRunDetails')} error={detailQuery.error as AppApiError}/>
                                            </div>
                                        ) : summaryQuery.error ? (
                                            <Typography.Text type="warning" style={{display: 'block', marginTop: 12, fontSize: 12}}>
                                                {t('pages:theRunSummaryFailedToLoadOrderTradeAndPositionFactTabsRemainIndependentlyAvailable')}</Typography.Text>
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
                                                    onSuccess: () => message.success(t('pages:riskCheckCompleted')),
                                                    onError: (err) => showApiError(err as AppApiError, message),
                                                })}
                                            />
                                        </Col>
                                        <Col xs={24} xl={9}>
                                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                                <Card className="page-section" variant="borderless" title={t('pages:runActions')}>
                                                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                                                            {t('pages:emergencyStopAffectsThisSimPaperRunAndRecordsAStopEventItDoesNotPlaceOrCancelRealLiveOrders')}</Typography.Text>
                                                        <NqDangerConfirmButton
                                                            size="small"
                                                            block
                                                            disabled={focusStatus !== 'RUNNING'}
                                                            loading={emergencyStopMutation.isPending}
                                                            confirmTitle={t('pages:confirmEmergencyStop')}
                                                            confirmContent={t('pages:thisImmediatelyStopsTheCurrentSimPaperRunItDoesNotPlaceOrCancelRealLiveOrdersContinue')}
                                                            okText={t('pages:confirmStop')}
                                                            onConfirm={() => emergencyStopMutation.mutate(
                                                                {
                                                                    paperRunId: selectedRow.paperRunId,
                                                                    request: {triggerType: 'MANUAL', reason: '手动紧急停机', triggeredBy: 'console-user'},
                                                                },
                                                                {
                                                                    onSuccess: () => {
                                                                        message.success(t('pages:emergencyStopCompleted'));
                                                                        setSearchVersion((v) => v + 1);
                                                                    },
                                                                    onError: (err) => showApiError(err as AppApiError, message),
                                                                },
                                                            )}
                                                        >
                                                            {t('pages:emergencyStop')}</NqDangerConfirmButton>
                                                        {(emergencyStopsQuery.data ?? []).length > 0 ? (
                                                            <NqDataTable
                                                                rowKey="emergencyStopId"
                                                                pagination={false}
                                                                dataSource={emergencyStopsQuery.data ?? []}
                                                                scroll={{y: 180}}
                                                                columns={[
                                                                    {title: t('pages:triggerType2'), dataIndex: 'triggerType', key: 'triggerType', width: 110},
                                                                    {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v} tone={v === 'APPLIED' ? 'danger' : v === 'RESOLVED' ? 'success' : 'warning'}/>},
                                                                    {title: t('pages:triggeredAt'), dataIndex: 'triggeredAt', key: 'triggeredAt', width: 170, render: (v: string) => formatDateTime(v)},
                                                                ]}
                                                            />
                                                        ) : null}
                                                    </Space>
                                                </Card>
                                                <NqScheduleFirePanel paperRunId={selectedRow.paperRunId}/>
                                                <NqHeartbeatPanel paperRunId={selectedRow.paperRunId}/>
                                                <RunDailyReportPanel paperRunId={selectedRow.paperRunId}/>
                                                <NqStabilityCheckPanel paperRunId={selectedRow.paperRunId}/>
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
                title={t('pages:createPaperTradingRun')}
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
                        label={t('pages:publishId')}
                        name="publishId"
                        rules={[{required: true, message: t('pages:enterAPublishId')}]}
                    >
                        <Input placeholder={t('pages:publishRecordIdPublishid')}/>
                    </Form.Item>
                    <Form.Item label={t('pages:tradingEnvironment')} name="tradeEnv" rules={[{required: true}]}>
                        <Select options={TRADE_ENV_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label={t('pages:exchange')} name="exchangeCode" rules={[{required: true}]}>
                        <Select options={EXCHANGE_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label={t('pages:marketType')} name="marketType" rules={[{required: true}]}>
                        <Select options={MARKET_TYPE_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label={t('pages:symbol')} name="symbol" rules={[{required: true}]}>
                        <Select showSearch options={SYMBOL_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label={t('pages:interval')} name="intervalCode" rules={[{required: true}]}>
                        <Select options={INTERVAL_OPTIONS}/>
                    </Form.Item>
                    <Form.Item label={t('pages:runConfigurationSnapshotJsonOptional')} name="configSnapshotJson">
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
    useTranslation('pages');
    return (
        <Card className="page-section" variant="borderless" title={t('pages:analysisWorkspaces')}>
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
    useTranslation('pages');
    return (
        <Card className="page-section" variant="borderless" title={t('pages:runFacts')}>
            <Descriptions bordered column={3} size="small" style={{marginBottom: 12}}>
                <Descriptions.Item label={t('pages:paperRunId')}>
                    <span className="nq-mono">{selectedRow.paperRunId}</span>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:publishId')}>
                    <span className="nq-mono">{selectedRow.publishId}</span>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:strategyVersionId')}>
                    <span className="nq-mono">{selectedRow.strategyVersionId || '-'}</span>
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:symbol')}>{selectedRow.symbol}</Descriptions.Item>
                <Descriptions.Item label={t('pages:interval')}>{selectedRow.intervalCode}</Descriptions.Item>
                <Descriptions.Item label={t('pages:marketType')}>{selectedRow.marketType}</Descriptions.Item>
                <Descriptions.Item label={t('pages:startedAt')}>{formatDateTime(selectedRow.startedAt)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:stoppedAt')}>{formatDateTime(selectedRow.stoppedAt)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:createdBy')}>{selectedRow.createdBy}</Descriptions.Item>
            </Descriptions>
            <Tabs
                activeKey={factTab}
                onChange={setFactTab}
                items={[
                    {
                        key: 'orders',
                        label: t('pages:order'),
                        children: (
                            <PaperFactSection query={ordersQuery} emptyText={t('pages:noOrderFactsForThisPaperRun')}>
                                <NqDataTable
                                    rowKey="paperOrderId"
                                    pagination={false}
                                    dataSource={ordersQuery.data ?? []}
                                    scroll={{x: 900}}
                                    columns={[
                                        {title: t('pages:orderId'), dataIndex: 'paperOrderId', key: 'paperOrderId', className: 'nq-mono'},
                                        {title: t('pages:side'), dataIndex: 'side', key: 'side', width: 80},
                                        {title: t('pages:type'), dataIndex: 'orderType', key: 'orderType', width: 80},
                                        nqNumericColumn({title: t('pages:quantity'), dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:price'), dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                        {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v}/>},
                                        {title: t('pages:createdAt'), dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                                    ]}
                                />
                            </PaperFactSection>
                        ),
                    },
                    {
                        key: 'trades',
                        label: t('pages:trade'),
                        children: (
                            <PaperFactSection query={tradesQuery} emptyText={t('pages:noTradeFactsForThisPaperRun')}>
                                <NqDataTable
                                    rowKey="paperTradeId"
                                    pagination={false}
                                    dataSource={tradesQuery.data ?? []}
                                    scroll={{x: 900}}
                                    columns={[
                                        {title: t('pages:tradeId'), dataIndex: 'paperTradeId', key: 'paperTradeId', className: 'nq-mono'},
                                        {title: t('pages:orderId'), dataIndex: 'paperOrderId', key: 'paperOrderId', className: 'nq-mono'},
                                        {title: t('pages:side'), dataIndex: 'side', key: 'side', width: 80},
                                        nqNumericColumn({title: t('pages:quantity'), dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:price'), dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:fee'), dataIndex: 'fee', key: 'fee', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                        {title: t('pages:tradeTime'), dataIndex: 'tradedAt', key: 'tradedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                    ]}
                                />
                            </PaperFactSection>
                        ),
                    },
                    {
                        key: 'positions',
                        label: t('pages:position'),
                        children: (
                            <PaperFactSection query={positionsQuery} emptyText={t('pages:noPositionFactsForThisPaperRun')}>
                                <NqDataTable
                                    rowKey="paperPositionId"
                                    pagination={false}
                                    dataSource={positionsQuery.data ?? []}
                                    scroll={{x: 900}}
                                    columns={[
                                        {title: t('pages:symbol'), dataIndex: 'symbol', key: 'symbol', width: 120},
                                        nqNumericColumn({title: t('pages:quantity'), dataIndex: 'quantity', key: 'quantity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:averagePrice'), dataIndex: 'avgPrice', key: 'avgPrice', width: 120, render: (v) => <NqPriceText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:realizedPnl'), dataIndex: 'realizedPnl', key: 'realizedPnl', width: 140, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                        nqNumericColumn({title: t('pages:unrealizedPnl'), dataIndex: 'unrealizedPnl', key: 'unrealizedPnl', width: 140, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                                        {title: t('pages:updatedAt'), dataIndex: 'updatedAt', key: 'updatedAt', width: 170, render: (v: string) => formatDateTime(v)},
                                    ]}
                                />
                            </PaperFactSection>
                        ),
                    },
                    {
                        key: 'snapshots',
                        label: t('pages:snapshot'),
                        children: (
                            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                                <SnapshotBlock title={t('pages:publishSnapshot')} content={selectedRow.publishSnapshotJson}/>
                                <SnapshotBlock title={t('pages:strategyVersionSnapshot')} content={selectedRow.strategyVersionSnapshotJson}/>
                                <SnapshotBlock title={t('pages:datasetSnapshot')} content={selectedRow.datasetSnapshotJson}/>
                                <SnapshotBlock title={t('pages:parameterSnapshot')} content={selectedRow.paramSnapshotJson}/>
                                <SnapshotBlock title={t('pages:configurationSnapshot')} content={selectedRow.configSnapshotJson}/>
                            </Space>
                        ),
                    },
                    {
                        key: 'risk-results',
                        label: t('pages:riskResults'),
                        children: (
                            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                <Button size="small" loading={riskOncePending} onClick={onRunRiskOnce}>
                                    {t('pages:runRiskCheck')}</Button>
                                <PaperFactSection query={riskResultsQuery} emptyText={t('pages:noRiskCheckResultsForThisPaperRun')}>
                                    <NqDataTable
                                        rowKey="riskResultId"
                                        pagination={false}
                                        dataSource={riskResultsQuery.data ?? []}
                                        scroll={{x: 900}}
                                        columns={[
                                            {title: t('pages:checkType'), dataIndex: 'checkType', key: 'checkType', width: 180},
                                            {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <StatusTag title="" variant="pill" status={v} tone={v === 'PASSED' ? 'success' : v === 'REJECTED' ? 'danger' : 'warning'}/>},
                                            {title: t('pages:severity'), dataIndex: 'severity', key: 'severity', width: 100},
                                            {title: t('pages:message'), dataIndex: 'message', key: 'message'},
                                            {title: t('pages:time'), dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (v: string) => formatDateTime(v)},
                                        ]}
                                    />
                                </PaperFactSection>
                            </Space>
                        ),
                    },
                    {
                        key: 'equity-curve',
                        label: t('pages:equityCurve2'),
                        children: (
                            <PaperFactSection query={equityCurveQuery} emptyText={t('pages:noEquityCurveDataForThisPaperRun')}>
                                <NqDataTable
                                    rowKey="equitySnapshotId"
                                    pagination={false}
                                    dataSource={equityCurveQuery.data ?? []}
                                    scroll={{x: 900}}
                                    columns={[
                                        {title: t('pages:time'), dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170, render: (v: string) => formatDateTime(v)},
                                        nqNumericColumn({title: t('pages:totalEquity'), dataIndex: 'totalEquity', key: 'totalEquity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:cash'), dataIndex: 'cashBalance', key: 'cashBalance', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:positionValue'), dataIndex: 'positionValue', key: 'positionValue', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                                        {title: t('pages:source'), dataIndex: 'source', key: 'source', width: 100},
                                    ]}
                                />
                            </PaperFactSection>
                        ),
                    },
                    {
                        key: 'position-curve',
                        label: t('pages:positionCurve'),
                        children: (
                            <PaperFactSection query={positionCurveQuery} emptyText={t('pages:noPositionCurveDataForThisPaperRun')}>
                                <NqDataTable
                                    rowKey="positionSnapshotId"
                                    pagination={false}
                                    dataSource={positionCurveQuery.data ?? []}
                                    scroll={{x: 900}}
                                    columns={[
                                        {title: t('pages:symbol'), dataIndex: 'symbol', key: 'symbol', width: 120},
                                        {title: t('pages:time'), dataIndex: 'snapshotTime', key: 'snapshotTime', width: 170, render: (v: string) => formatDateTime(v)},
                                        nqNumericColumn({title: t('pages:quantity'), dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:averagePrice'), dataIndex: 'avgPrice', key: 'avgPrice', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:markPrice'), dataIndex: 'markPrice', key: 'markPrice', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                        {title: t('pages:source'), dataIndex: 'source', key: 'source', width: 100},
                                    ]}
                                />
                            </PaperFactSection>
                        ),
                    },
                    {
                        key: 'replay',
                        label: t('pages:tradeReview'),
                        children: (
                            <PaperFactSection query={replayQuery} emptyText={t('pages:noTradeReviewsForThisPaperRun')}>
                                <NqDataTable
                                    rowKey="replayRecordId"
                                    pagination={false}
                                    dataSource={replayQuery.data ?? []}
                                    scroll={{x: 900}}
                                    columns={[
                                        {title: t('pages:time'), dataIndex: 'replayTime', key: 'replayTime', width: 170, render: (v: string) => formatDateTime(v)},
                                        {title: t('pages:eventType'), dataIndex: 'eventType', key: 'eventType', width: 140},
                                        {title: t('pages:symbol'), dataIndex: 'symbol', key: 'symbol', width: 120},
                                        {title: t('pages:side'), dataIndex: 'side', key: 'side', width: 80},
                                        nqNumericColumn({title: t('pages:price'), dataIndex: 'price', key: 'price', width: 100, render: (v) => <NqPriceText value={v as string}/>}),
                                        nqNumericColumn({title: t('pages:quantity'), dataIndex: 'quantity', key: 'quantity', width: 100, render: (v) => <NqAmountText value={v as string}/>}),
                                        {title: t('pages:reason'), dataIndex: 'reason', key: 'reason'},
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

/**
 * RunDailyReportPanel 恢复 K5 `/paper-trading/runs` 的 run-local 日报入口。
 *
 * Why:
 * K5 拆分只迁出跨 run 分析 dashboard；日报仍是单个 Paper Run 的执行事实与验收辅助证据。
 * 如果 runs 页不挂载该 panel，既有 backend-dependent smoke 无法覆盖日报生成链路，也会让用户从执行控制台失去
 * run-local 日报入口。
 *
 * Boundary / Failure Modes:
 * - 复用既有 `usePaperDailyReportsQuery` 与 `useGenerateDailyReportMutation`，不新增 API、query key 或 global store。
 * - 只展示当前 `paperRunId` 的日报列表；生成按钮省略 reportDate，由后端按“今日”幂等生成。
 * - 查询失败只影响本 panel，通过 `NqErrorState` 局部展示，不阻塞订单、风控、调度、心跳或恢复面板。
 * - 日报仅代表 SIM/Paper 执行事实，不构成 LIVE 或真实交易表现。
 */
function RunDailyReportPanel({paperRunId}: {paperRunId: string}) {
    useTranslation('pages');
    const {message} = App.useApp();
    const dailyReportsQuery = usePaperDailyReportsQuery(paperRunId);
    const generateDailyReportMutation = useGenerateDailyReportMutation();
    const data = dailyReportsQuery.data ?? [];

    return (
        <Card
            className="page-section"
            size="small"
            title={t('pages:dailyReport')}
            extra={(
                <Button
                    size="small"
                    type="primary"
                    ghost
                    loading={generateDailyReportMutation.isPending}
                    onClick={() => generateDailyReportMutation.mutate(
                        {paperRunId, request: {}},
                        {
                            onSuccess: () => {
                                message.success(t('pages:dailyReportGenerated'));
                                void dailyReportsQuery.refetch();
                            },
                            onError: (err) => showApiError(err as AppApiError, message),
                        },
                    )}
                >
                    {t('pages:generateTodaySReport')}</Button>
            )}
        >
            <Space direction="vertical" size={8} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {t('pages:dailyReportsSummarizeThisSimPaperRunOnlyNotLiveOrRealTradingPerformance')}</Typography.Text>
                {dailyReportsQuery.isFetching && data.length === 0 ? (
                    <NqLoadingState/>
                ) : dailyReportsQuery.error ? (
                    <NqErrorState error={dailyReportsQuery.error as AppApiError} onRetry={() => dailyReportsQuery.refetch()}/>
                ) : data.length === 0 ? (
                    <NqEmptyState description={t('pages:noDailyReportsForThisPaperRun')}/>
                ) : (
                    <NqDataTable<PaperRunDailyReportItem>
                        rowKey="reportId"
                        pagination={false}
                        dataSource={data}
                        scroll={{x: 900, y: 240}}
                        columns={[
                            {title: t('pages:date'), dataIndex: 'reportDate', key: 'reportDate', width: 120},
                            {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <StatusTag title="" variant="pill" status={v} tone={v === 'GENERATED' ? 'success' : 'warning'}/>},
                            nqNumericColumn({title: t('pages:totalEquity'), dataIndex: 'totalEquity', key: 'totalEquity', width: 120, render: (v) => <NqAmountText value={v as string}/>}),
                            nqNumericColumn({title: t('pages:dailyPnl'), dataIndex: 'dailyPnl', key: 'dailyPnl', width: 120, render: (v) => <NqAmountText value={v as string} signed colorBySign/>}),
                            nqNumericColumn({title: t('pages:dailyReturn2'), dataIndex: 'dailyReturn', key: 'dailyReturn', width: 110, render: (v) => <NqPercentText value={v as string} ratio colorBySign/>}),
                            nqNumericColumn({title: t('pages:maximumDrawdown'), dataIndex: 'maxDrawdown', key: 'maxDrawdown', width: 110, render: (v) => <NqPercentText value={v as string} ratio signed={false}/>}),
                            nqNumericColumn({title: t('pages:order'), dataIndex: 'orderCount', key: 'orderCount', width: 80}),
                            nqNumericColumn({title: t('pages:trade'), dataIndex: 'tradeCount', key: 'tradeCount', width: 80}),
                            nqNumericColumn({title: t('pages:alert'), dataIndex: 'alertCount', key: 'alertCount', width: 80}),
                            {title: t('pages:generatedAt'), dataIndex: 'generatedAt', key: 'generatedAt', width: 170, render: (v: string) => formatDateTime(v)},
                        ]}
                    />
                )}
            </Space>
        </Card>
    );
}

interface PaperFactSectionProps {
    query: {isFetching: boolean; error: unknown; data?: unknown[]};
    emptyText: string;
    children: ReactNode;
}

function PaperFactSection({query, emptyText, children}: PaperFactSectionProps) {
    useTranslation('pages');
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
    useTranslation('pages');
    return (
        <Card size="small" title={title}>
            <Typography.Paragraph className="nq-mono" style={{whiteSpace: 'pre-wrap', marginBottom: 0}}>
                {content || '-'}
            </Typography.Paragraph>
        </Card>
    );
}

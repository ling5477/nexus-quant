import {StatusTag, type StatusTone} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Button, Card, Collapse, Descriptions, Select, Space, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {NqAmountText, NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, NqMetricCard, NqPercentText, NqPortfolioDrawdownChart, NqPortfolioEquityChart, ApplicationRiskAlert, nqNumericColumn} from '@/components/nq';

import {usePaperPortfolioSummaryQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {
    PaperPortfolioCurve,
    PaperPortfolioCurvePoint,
    PaperPortfolioRunRef,
    PaperPortfolioSummaryResponse,
} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

import {toNullableNumber} from './paperFormatters';
import {ClickableMetricCard, renderRunRefTags} from './paperPortfolioShared';

const EMPTY_PORTFOLIO_HIGHLIGHTS = {
    topWinner: null,
    worstDrawdown: null,
    highestRisk: null,
    mostRecent: null,
    noTradeRuns: [],
    riskBlockedRuns: [],
} satisfies PaperPortfolioSummaryResponse['highlights'];

const EMPTY_PORTFOLIO_DATA_QUALITY = {
    missingEquityRuns: [],
    dataInsufficientRuns: [],
    missingBacktestSourceRuns: [],
    missingPublishSourceRuns: [],
} satisfies PaperPortfolioSummaryResponse['dataQuality'];

/**
 * 汇总组合看板里出现过的「风险相关 run 引用」（highlights + dataQuality 去重）。
 * 注意：组合 summary 不下发全量 run 清单，本池为风险相关子集（含 top/worst/highestRisk/mostRecent
 * 与无交易 / 风控拦截 / 数据质量清单），用于回撤排行与阈值分布派生；展示层会显式标注口径，避免误读为全量。
 */
function collectRiskRunPool(portfolio: PaperPortfolioSummaryResponse): PaperPortfolioRunRef[] {
    // 兼容旧版 / 精简 Portfolio summary 响应：缺少清单时按空清单处理，只展示数据不足，不制造风险事实。
    const highlights = portfolio.highlights ?? EMPTY_PORTFOLIO_HIGHLIGHTS;
    const dataQuality = portfolio.dataQuality ?? EMPTY_PORTFOLIO_DATA_QUALITY;
    const byId = new Map<string, PaperPortfolioRunRef>();
    const push = (run: PaperPortfolioRunRef | null | undefined) => {
        if (run && !byId.has(run.paperRunId)) {
            byId.set(run.paperRunId, run);
        }
    };
    push(highlights.topWinner);
    push(highlights.worstDrawdown);
    push(highlights.highestRisk);
    push(highlights.mostRecent);
    highlights.noTradeRuns.forEach(push);
    highlights.riskBlockedRuns.forEach(push);
    dataQuality.missingEquityRuns.forEach(push);
    dataQuality.dataInsufficientRuns.forEach(push);
    dataQuality.missingBacktestSourceRuns.forEach(push);
    dataQuality.missingPublishSourceRuns.forEach(push);
    return Array.from(byId.values());
}

/** 单 run 最大回撤（比例值，<=0）落桶；null 视为数据不足，不参与回撤分桶（不伪造回撤）。 */
const RISK_DRAWDOWN_BUCKETS: ReadonlyArray<{key: string; match: (dd: number) => boolean}> = [
    {key: '0% ~ -5%', match: (dd) => dd > -0.05},
    {key: '-5% ~ -10%', match: (dd) => dd <= -0.05 && dd > -0.1},
    {key: '-10% ~ -20%', match: (dd) => dd <= -0.1 && dd > -0.2},
    {key: '< -20%', match: (dd) => dd <= -0.2},
];

/** 无交易 run 的可能原因（基于组合 summary 可得字段派生，不臆测策略内部行为）。 */
function deriveNoTradeCause(
    run: PaperPortfolioRunRef,
    dataInsufficientIds: Set<string>,
    missingEquityIds: Set<string>,
): {label: string; tone: StatusTone} {
    if (run.riskBlocked) {
        return {label: t('pages:riskBlocked'), tone: 'danger'};
    }
    if (run.status === 'FAILED' || run.status === 'CANCELLED') {
        return {label: t('pages:abnormalEnd'), tone: 'danger'};
    }
    if (run.status === 'CREATED') {
        return {label: t('pages:notStarted'), tone: 'neutral'};
    }
    if (dataInsufficientIds.has(run.paperRunId) || missingEquityIds.has(run.paperRunId)) {
        return {label: t('pages:insufficientData'), tone: 'warning'};
    }
    return {label: t('pages:strategyNotTriggered'), tone: 'info'};
}

/**
 * 无交易 run 的执行进度细分（Loop-18）：基于后端 run 级 noOrder / orderNoFill 标记，
 * 区分「无订单」与「有订单无成交」；旧后端缺该标记时回退到「无成交」泛标签，不臆测。
 */
function deriveExecProgress(run: PaperPortfolioRunRef): {label: string; tone: StatusTone; hint: string} {
    if (run.orderNoFill) {
        return {label: t('pages:ordersWithoutFills'), tone: 'warning', hint: t('pages:matchingOrPriceConditionsNotMetOrInsufficientSimulatedLiquidity')};
    }
    if (run.noOrder) {
        return {label: t('pages:noOrders'), tone: 'info', hint: t('pages:strategyNotTriggeredNotStartedInsufficientData')};
    }
    // 旧后端无 order 拆分字段（noOrder/orderNoFill 均缺失）：泛标签兜底，不伪造拆分。
    return {label: t('pages:noFills'), tone: 'neutral', hint: t('pages:inspectTheRunSOrderAndTradeDetails')};
}

/** 风险 run 表通用列：run / 状态 / 策略版本+发布 / 当前权益 / 总 PnL / 最大回撤 / 未处理告警 / 最近活跃。 */
function riskRunColumns(): ColumnsType<PaperPortfolioRunRef> {
    return [
        {title: t('pages:paperRun'), dataIndex: 'paperRunId', key: 'paperRunId', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <StatusTag title="" variant="pill" status={v}/>},
        {
            title: t('pages:strategyVersionPublish'),
            key: 'lineage',
            width: 200,
            render: (_: unknown, run: PaperPortfolioRunRef) => (
                <Space direction="vertical" size={0}>
                    <span className="nq-mono" style={{fontSize: 11}}>{run.strategyVersionId ?? t('pages:noStrategyVersionBound')}</span>
                    <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 11}}>{run.publishId || t('pages:unknownPublish')}</Typography.Text>
                </Space>
            ),
        },
        nqNumericColumn({title: t('pages:currentEquity'), dataIndex: 'currentEquity', key: 'currentEquity', width: 120, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({
            title: t('pages:totalPnl'),
            dataIndex: 'totalPnl',
            key: 'totalPnl',
            width: 120,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">{t('pages:insufficientData')}</Typography.Text>
                : <NqAmountText value={v as string | number} signed colorBySign/>),
        }),
        nqNumericColumn({
            title: t('pages:maximumDrawdown'),
            dataIndex: 'maxDrawdown',
            key: 'maxDrawdown',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">{t('pages:insufficientData')}</Typography.Text>
                : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: t('pages:unresolvedAlerts'), dataIndex: 'openAlertCount', key: 'openAlertCount', width: 100}),
        {title: t('pages:recentlyActive'), dataIndex: 'lastActivityAt', key: 'lastActivityAt', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

/** 组合曲线采样点表列：时间 / 组合权益 / 组合 PnL / 收益率 / 回撤 / 在册 run / 缺失 run。 */
function portfolioCurveColumns(): ColumnsType<PaperPortfolioCurvePoint> {
    return [
        {title: t('pages:time'), dataIndex: 'timestamp', key: 'timestamp', width: 170, render: (v: string) => formatDateTime(v)},
        nqNumericColumn({title: t('pages:portfolioEquity'), dataIndex: 'totalEquity', key: 'totalEquity', width: 130, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({
            title: t('pages:portfolioPnl'),
            dataIndex: 'totalPnl',
            key: 'totalPnl',
            width: 130,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">{t('pages:insufficientData')}</Typography.Text>
                : <NqAmountText value={v as string | number} signed colorBySign/>),
        }),
        nqNumericColumn({
            title: t('pages:returnRate'),
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">{t('pages:insufficientData')}</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: t('pages:drawdown'),
            dataIndex: 'drawdown',
            key: 'drawdown',
            width: 110,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: t('pages:registeredRuns'), dataIndex: 'sourceRunCount', key: 'sourceRunCount', width: 90}),
        nqNumericColumn({title: t('pages:missingRuns'), dataIndex: 'missingRunCount', key: 'missingRunCount', width: 90}),
    ];
}

/**
 * PortfolioEquityCurveCard —— 组合级 equity / drawdown 时间序列卡（Loop-15）。
 * 优先消费后端 portfolioCurve（真实组合时间序列：当前回撤 / 最大回撤 / 资金峰值 / 最新组合 equity + 采样点）；
 * 不可用（数据不足或旧版本响应缺字段）时回退提示，由上层「回撤分析」继续以单 run 最大回撤口径兜底。
 * 仅代表 SIM/Paper 模拟、简化组合资金合计曲线，不代表真实时间加权组合收益，也不代表 LIVE 或真实交易。
 */
function PortfolioEquityCurveCard({curve}: {curve: PaperPortfolioCurve | null | undefined}) {
    useTranslation('pages');
    const points: PaperPortfolioCurvePoint[] = curve?.points ?? [];
    const hasCurve = Boolean(curve) && points.length > 0;

    return (
        <Card
            size="small"
            title={t('pages:portfolioEquityAndDrawdown')}
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:simPaperOnlyLiveDisabled')}</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                {hasCurve && curve ? (
                    <>
                        <div className="nq-status-strip">
                            <NqMetricCard label={t('pages:latestPortfolioEquity')} value={<NqAmountText value={curve.latestEquity}/>}/>
                            <NqMetricCard label={t('pages:equityPeak')} value={<NqAmountText value={curve.peakEquity}/>}/>
                            <NqMetricCard
                                label={t('pages:currentPortfolioDrawdown')}
                                value={curve.currentDrawdown !== null
                                    ? <NqPercentText value={curve.currentDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="warning"
                                footer={t('pages:portfolioTimeSeriesBasis')}
                            />
                            <NqMetricCard
                                label={t('pages:maximumPortfolioDrawdown')}
                                value={curve.maxDrawdown !== null
                                    ? <NqPercentText value={curve.maxDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="danger"
                            />
                            <NqMetricCard
                                label={t('pages:comparableRuns')}
                                value={String(curve.coverage.comparableRunCount)}
                                footer={t('pages:missingEquityValue1IncompletePointsValue2', {value1: curve.coverage.missingEquityRunCount, value2: curve.coverage.incompletePointCount})}
                            />
                        </div>

                        {/* 组合资金曲线图（复用 Design System ECharts 主题；hover 见每点组合权益/PnL/收益率/在册·缺失 run） */}
                        <div>
                            <Typography.Text strong style={{fontSize: 13}}>{t('pages:portfolioEquityCurve')}</Typography.Text>
                            <NqPortfolioEquityChart points={points}/>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                {t('pages:theSolidLineIsTotalPortfolioEquityTheDashedLineIsComparableRunsInitialCapitalHoverForEquityPnlReturn')}</Typography.Text>
                        </div>

                        {/* 组合回撤曲线图（y 轴反向、回撤向下；hover 见回撤/资金峰值/组合权益） */}
                        <div>
                            <Typography.Text strong style={{fontSize: 13}}>{t('pages:portfolioDrawdownCurve')}</Typography.Text>
                            <NqPortfolioDrawdownChart points={points}/>
                        </div>

                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:coverageComparableRuns')}{curve.coverage.comparableRunCount} {t('pages:missingEquity')}{curve.coverage.missingEquityRunCount}
                            {' '}{t('pages:incompletePoints')}{curve.coverage.incompletePointCount}{t('pages:total2')}{curve.pointCount} {t('pages:samplesSourceruncountCountsRegisteredRunsMissingruncountCountsComparableRunsNotYetStartedAtThatPoint')}</Typography.Text>
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:thisCurveUsesPaperSimulationAndLocalExecutionFactsNotLivePerformanceItShowsTotalPortfolioEquityNotAS')}</Typography.Text>

                        {/* 采样点表保留为可折叠辅助展示，保持数据透明度（默认折叠，避免与图表重复占屏） */}
                        <Collapse
                            size="small"
                            items={[{
                                key: 'curve-points',
                                label: t('pages:portfolioSamplesValue1TotalExpandForTheLatestValue2', {value1: curve.pointCount, value2: Math.min(points.length, 12)}),
                                children: (
                                    <NqDataTable<PaperPortfolioCurvePoint>
                                        rowKey="timestamp"
                                        pagination={false}
                                        dataSource={[...points].slice(-12).reverse()}
                                        columns={portfolioCurveColumns()}
                                        scroll={{x: 900, y: 240}}
                                        locale={{emptyText: t('pages:noPortfolioCurveSamples')}}
                                    />
                                ),
                            }]}
                        />
                    </>
                ) : (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description={t('pages:thePortfolioEquityCurveIsUnavailableDueToInsufficientDataOrAnOlderResponseShowingMaximumDrawdownPerR')}/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>{t('pages:insufficientDataPortfolioTimeSeriesDrawdownIsUnavailable')}</Typography.Text>
                    </Space>
                )}
            </Space>
        </Card>
    );
}

/**
 * PaperRiskDrawdownDashboard —— Paper 风险与回撤驾驶舱（GateJ 后产品化 Loop-14）。
 * 复用 Loop-13 组合 summary 单请求结果，把「风险面」从组合看板中独立出来只读派生：
 * 风险总览、回撤分析（阈值分布 + 单 run 最大回撤排行）、风控与异常清单、无交易 / 数据不足清单、数据质量。
 * 仅代表 SIM/Paper 模拟运行，不读真实交易所账户余额，不代表 LIVE 或真实交易风险；数据不足不伪造回撤。
 */
export function PaperRiskDrawdownDashboard({query}: {query: ReturnType<typeof usePaperPortfolioSummaryQuery>}) {
    useTranslation('pages');
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    return (
      <section aria-label={t('pages:paperRiskAndDrawdownDashboard')}>
        <Card
            className="page-section"
            bordered={false}
            title={t('pages:paperRiskAndDrawdownDashboard')}
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:simPaperOnlyLiveDisabled')}</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <ApplicationRiskAlert
                    level="warning"
                    message={t('pages:inspectHighRiskPaperRunsMaximumDrawdownRiskBlocksMissingTradesAndInsufficientData')}
                    description={t('pages:thisRiskDashboardUsesPaperSimulationAndLocalExecutionFactsOnlyItDoesNotRepresentLiveOrRealTradingRis')}
                />
                {query.error ? (
                    <NqErrorState
                        title={t('pages:failedToLoadPaperRiskAndDrawdown')}
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description={t('pages:createAndExecutePaperRunsToAggregateRiskAndDrawdown')}/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>{t('pages:insufficientDataDrawdownAndRiskValuesAreUnavailable')}</Typography.Text>
                    </Space>
                ) : (
                    <PaperRiskDrawdownBody portfolio={portfolio}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

// ---- Loop-19：风险 Run 清单筛选（合并 highlights/dataQuality 去重后按条件筛选），纯前端只读派生 ----

type RiskRunFilter =
    'all' | 'riskBlocked' | 'noOrder' | 'orderNoFill' | 'hasFill'
    | 'dataInsufficient' | 'terminal' | 'highDrawdown';

const RISK_RUN_FILTER_OPTIONS: ReadonlyArray<{label: string; value: RiskRunFilter}> = [
    {get label() { return t('pages:all'); }, value: 'all'},
    {get label() { return t('pages:riskBlocked'); }, value: 'riskBlocked'},
    {get label() { return t('pages:noOrders'); }, value: 'noOrder'},
    {get label() { return t('pages:ordersWithoutFills'); }, value: 'orderNoFill'},
    {get label() { return t('pages:withFills'); }, value: 'hasFill'},
    {get label() { return t('pages:insufficientData'); }, value: 'dataInsufficient'},
    {get label() { return t('pages:abnormalTerminalState'); }, value: 'terminal'},
    {get label() { return t('pages:highDrawdown'); }, value: 'highDrawdown'},
];

/** 高回撤阈值：单 run 最大回撤 ≤ -10%（与回撤分桶 danger 区间一致）。 */
const RISK_RUN_HIGH_DRAWDOWN_THRESHOLD = -0.1;

/**
 * 风险 Run 清单筛选：noOrder/orderNoFill/hasFill 按后端 run 级标记（旧后端缺失 → 不命中，不伪造）；
 * dataInsufficient 以 dataQuality.dataInsufficientRuns 为准；terminal 取 FAILED/CANCELLED；highDrawdown 取深回撤。
 */
function filterRiskRuns(
    pool: PaperPortfolioRunRef[],
    filter: RiskRunFilter,
    dataInsufficientIds: Set<string>,
): PaperPortfolioRunRef[] {
    switch (filter) {
        case 'riskBlocked': return pool.filter((r) => r.riskBlocked);
        case 'noOrder': return pool.filter((r) => r.noOrder === true);
        case 'orderNoFill': return pool.filter((r) => r.orderNoFill === true);
        case 'hasFill': return pool.filter((r) => r.hasFill === true);
        case 'dataInsufficient': return pool.filter((r) => dataInsufficientIds.has(r.paperRunId));
        case 'terminal': return pool.filter((r) => r.status === 'FAILED' || r.status === 'CANCELLED');
        case 'highDrawdown': return pool.filter((r) => {
            const dd = toNullableNumber(r.maxDrawdown);
            return dd !== null && dd <= RISK_RUN_HIGH_DRAWDOWN_THRESHOLD;
        });
        case 'all':
        default: return pool;
    }
}

/** Run 执行进度标记（通用，含有成交）：旧后端缺 order/fill 标记时回退「无成交」泛标签，不伪造。 */
function runExecTag(run: PaperPortfolioRunRef): {label: string; tone: StatusTone} {
    if (run.hasFill) {
        return {label: t('pages:withFills'), tone: 'success'};
    }
    if (run.orderNoFill) {
        return {label: t('pages:ordersWithoutFills'), tone: 'warning'};
    }
    if (run.noOrder) {
        return {label: t('pages:noOrders'), tone: 'info'};
    }
    return {label: t('pages:noFills'), tone: 'neutral'};
}

function PaperRiskDrawdownBody({portfolio}: {portfolio: PaperPortfolioSummaryResponse}) {
    useTranslation('pages');
    const {overview} = portfolio;
    // Risk dashboard 被独立挂载后必须能消费旧 summary；缺失详情清单时 fail-closed 到空清单。
    const highlights = portfolio.highlights ?? EMPTY_PORTFOLIO_HIGHLIGHTS;
    const dataQuality = portfolio.dataQuality ?? EMPTY_PORTFOLIO_DATA_QUALITY;

    // Loop-19：风险 Run 清单筛选状态（默认全部）。
    const [riskFilter, setRiskFilter] = useState<RiskRunFilter>('all');

    const pool = collectRiskRunPool(portfolio);
    const dataInsufficientIds = new Set(dataQuality.dataInsufficientRuns.map((r) => r.paperRunId));
    const missingEquityIds = new Set(dataQuality.missingEquityRuns.map((r) => r.paperRunId));

    // 回撤排行：池内有最大回撤的 run 按最负优先排序；无回撤的 run 单列「数据不足」，不伪造回撤。
    const drawdownRanked = pool
        .filter((r) => toNullableNumber(r.maxDrawdown) !== null)
        .sort((a, b) => (toNullableNumber(a.maxDrawdown) ?? 0) - (toNullableNumber(b.maxDrawdown) ?? 0));
    const drawdownInsufficient = pool.filter((r) => toNullableNumber(r.maxDrawdown) === null);

    // 回撤阈值分布：仅对有回撤的 run 分桶；数据不足单独计数。
    const bucketCounts = RISK_DRAWDOWN_BUCKETS.map((bucket) => ({
        key: bucket.key,
        count: drawdownRanked.filter((r) => bucket.match(toNullableNumber(r.maxDrawdown) ?? 0)).length,
    }));

    // 异常 / 风险细分清单（均来自组合 summary 已下发的风险相关子集）。
    const openAlertRuns = pool.filter((r) => r.openAlertCount > 0);
    const failedCancelledRuns = pool.filter((r) => r.status === 'FAILED' || r.status === 'CANCELLED');
    const missingPnlRuns = pool.filter((r) => toNullableNumber(r.totalPnl) === null);

    // 高风险 run 数：风控拦截 + 异常终态（按 overview 权威计数合计）。
    const failedCancelledCount = overview.failedCount + overview.cancelledCount;

    // Loop-18：把「无交易」按后端精确口径拆为「无订单」与「有订单无成交」。
    // 旧后端缺该拆分字段时 footer 退化为提示「单 run 查看」，不伪造拆分计数。
    const hasOrderSplit = overview.noOrderRunCount !== undefined && overview.orderNoFillRunCount !== undefined;
    const noTradeSplitFooter = hasOrderSplit
        ? t('pages:noOrdersValue1UnfilledOrdersValue2', {value1: overview.noOrderRunCount, value2: overview.orderNoFillRunCount})
        : t('pages:inspectIndividualRunsForMissingOrdersOrFills');

    // Loop-19：统一风险 Run 清单（合并去重的 pool）按筛选条件展示。
    const riskRunFiltered = filterRiskRuns(pool, riskFilter, dataInsufficientIds);
    const riskFilterLabel = RISK_RUN_FILTER_OPTIONS.find((o) => o.value === riskFilter)?.label ?? t('pages:all');
    // Loop-20：高回撤 run 数（用于 click-to-filter 指标卡，阈值与 filterRiskRuns 保持一致）。
    const highDrawdownCount = pool.filter((r) => {
        const dd = toNullableNumber(r.maxDrawdown);
        return dd !== null && dd <= RISK_RUN_HIGH_DRAWDOWN_THRESHOLD;
    }).length;

    /** 点击指标卡直接切换风险 Run 清单筛选（Loop-20 click-to-filter）。 */
    const handleRiskCardClick = (filter: RiskRunFilter) => setRiskFilter(filter);

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* 1) 风险总览指标（Loop-20：可点击卡片直接联动下方风险 Run 清单筛选） */}
            <div className="nq-status-strip">
                <NqMetricCard
                    label={t('pages:maximumSingleRunDrawdown')}
                    value={overview.worstRunDrawdown !== null
                        ? <NqPercentText value={overview.worstRunDrawdown} ratio signed={false}/>
                        : '-'}
                    tone="warning"
                    footer={highlights.worstDrawdown ? t('pages:runWithLargestCurrentDrawdownValue1', {value1: highlights.worstDrawdown.paperRunId}) : t('pages:basedOnMaximumDrawdownPerRun')}
                />
                <ClickableMetricCard
                    ariaLabel={t('pages:filterRiskBlockedRuns')}
                    testId="risk-filter-card-risk-blocked"
                    isActive={riskFilter === 'riskBlocked'}
                    onClick={() => handleRiskCardClick('riskBlocked')}
                >
                    <NqMetricCard
                        label={t('pages:riskBlockedRuns')}
                        value={String(overview.riskBlockedRunCount)}
                        tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}
                        footer={t('pages:clickToFilter')}
                    />
                </ClickableMetricCard>
                <NqMetricCard
                    label={t('pages:unresolvedAlerts')}
                    value={String(overview.openAlertCount)}
                    tone={overview.openAlertCount > 0 ? 'warning' : 'muted'}
                />
                {hasOrderSplit ? (
                    <>
                        <ClickableMetricCard
                            ariaLabel={t('pages:filterRunsWithoutOrders')}
                            testId="risk-filter-card-no-order"
                            isActive={riskFilter === 'noOrder'}
                            onClick={() => handleRiskCardClick('noOrder')}
                        >
                            <NqMetricCard
                                label={t('pages:noOrders')}
                                value={String(overview.noOrderRunCount ?? 0)}
                                tone={(overview.noOrderRunCount ?? 0) > 0 ? 'warning' : 'muted'}
                                footer={t('pages:clickToFilter')}
                            />
                        </ClickableMetricCard>
                        <ClickableMetricCard
                            ariaLabel={t('pages:filterRunsWithUnfilledOrders')}
                            testId="risk-filter-card-order-no-fill"
                            isActive={riskFilter === 'orderNoFill'}
                            onClick={() => handleRiskCardClick('orderNoFill')}
                        >
                            <NqMetricCard
                                label={t('pages:ordersWithoutFills')}
                                value={String(overview.orderNoFillRunCount ?? 0)}
                                tone={(overview.orderNoFillRunCount ?? 0) > 0 ? 'warning' : 'muted'}
                                footer={t('pages:clickToFilter')}
                            />
                        </ClickableMetricCard>
                        <ClickableMetricCard
                            ariaLabel={t('pages:filterRunsWithFills')}
                            testId="risk-filter-card-has-fill"
                            isActive={riskFilter === 'hasFill'}
                            onClick={() => handleRiskCardClick('hasFill')}
                        >
                            <NqMetricCard
                                label={t('pages:withFills')}
                                value={String(overview.filledRunCount ?? '-')}
                                tone={(overview.filledRunCount ?? 0) > 0 ? 'success' : 'muted'}
                                footer={t('pages:clickToFilter')}
                            />
                        </ClickableMetricCard>
                    </>
                ) : (
                    <NqMetricCard
                        label={t('pages:runsWithoutTrades')}
                        value={String(overview.noTradeRunCount)}
                        tone={overview.noTradeRunCount > 0 ? 'warning' : 'muted'}
                        footer={noTradeSplitFooter}
                    />
                )}
                <ClickableMetricCard
                    ariaLabel={t('pages:filterRunsWithInsufficientData')}
                    testId="risk-filter-card-data-insufficient"
                    isActive={riskFilter === 'dataInsufficient'}
                    onClick={() => handleRiskCardClick('dataInsufficient')}
                >
                    <NqMetricCard
                        label={t('pages:runsWithInsufficientData')}
                        value={String(overview.dataInsufficientRunCount)}
                        tone={overview.dataInsufficientRunCount > 0 ? 'warning' : 'muted'}
                        footer={t('pages:clickToFilter')}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel={t('pages:filterAbnormalTerminalRuns')}
                    testId="risk-filter-card-terminal"
                    isActive={riskFilter === 'terminal'}
                    onClick={() => handleRiskCardClick('terminal')}
                >
                    <NqMetricCard
                        label="FAILED / CANCELLED"
                        value={String(failedCancelledCount)}
                        tone={failedCancelledCount > 0 ? 'danger' : 'muted'}
                        footer={`FAILED ${overview.failedCount} · CANCELLED ${overview.cancelledCount}`}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel={t('pages:filterHighDrawdownRuns')}
                    testId="risk-filter-card-high-drawdown"
                    isActive={riskFilter === 'highDrawdown'}
                    onClick={() => handleRiskCardClick('highDrawdown')}
                >
                    <NqMetricCard
                        label={t('pages:highDrawdownRuns')}
                        value={String(highDrawdownCount)}
                        tone={highDrawdownCount > 0 ? 'danger' : 'muted'}
                        footer={t('pages:drawdown10ClickToFilter')}
                    />
                </ClickableMetricCard>
            </div>

            {/* 1.5) 统一风险 Run 清单（Loop-19）：合并 highlights/dataQuality 去重，按条件筛选快速定位 */}
            <Card
                size="small"
                title={riskFilter !== 'all'
                    ? t('pages:riskRunsFilterValue1Value2Items', {value1: riskFilterLabel, value2: riskRunFiltered.length})
                    : t('pages:riskRunList')}
                extra={riskFilter !== 'all' ? (
                    <Button size="small" type="link" onClick={() => setRiskFilter('all')}>{t('pages:viewAll')}</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label={t('pages:riskRunFilters')}
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:riskFilter')}</Typography.Text>
                        <Select<RiskRunFilter>
                            size="small"
                            value={riskFilter}
                            onChange={setRiskFilter}
                            options={RISK_RUN_FILTER_OPTIONS as Array<{label: string; value: RiskRunFilter}>}
                            style={{width: 150}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            「{riskFilterLabel}{t('pages:matches2')}{riskRunFiltered.length} {t('pages:runs2')}</Typography.Text>
                    </div>
                    {riskRunFiltered.length > 0 ? (
                        <div role="region" aria-label={t('pages:riskRunTable')}>
                            <NqDataTable<PaperPortfolioRunRef>
                                rowKey="paperRunId"
                                pagination={false}
                                dataSource={riskRunFiltered}
                                columns={[
                                    ...riskRunColumns(),
                                    {
                                        title: t('pages:executionProgress'),
                                        key: 'exec',
                                        width: 120,
                                        render: (_: unknown, run: PaperPortfolioRunRef) => {
                                            const t = runExecTag(run);
                                            return <StatusTag title="" variant="pill" status={t.label} tone={t.tone}/>;
                                        },
                                    },
                                ]}
                                scroll={{x: 1220, y: 260}}
                                locale={{emptyText: t('pages:noMatchingRiskRuns')}}
                            />
                        </div>
                    ) : (
                        <NqEmptyState description={t('pages:noRiskRunsMatchTheFilterValue1', {value1: riskFilterLabel})}/>
                    )}
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:riskRunsCombineAndDeduplicateHighlightsAndDataQualityEntriesBeforeFiltering')}</Typography.Text>
                </Space>
            </Card>

            {/* 2) 组合资金曲线与回撤（Loop-15：真实组合时间序列口径，不可用时回退单 run 口径） */}
            <PortfolioEquityCurveCard curve={portfolio.portfolioCurve}/>

            {/* 3) 回撤分析（单 run 最大回撤口径，与上方组合时间序列口径互补） */}
            <Card size="small" title={t('pages:drawdownAnalysis')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div className="nq-status-strip">
                        {bucketCounts.map((bucket) => (
                            <NqMetricCard
                                key={bucket.key}
                                label={bucket.key}
                                value={String(bucket.count)}
                                tone={bucket.count > 0 && (bucket.key === '-10% ~ -20%' || bucket.key === '< -20%') ? 'danger' : 'default'}
                            />
                        ))}
                        <NqMetricCard
                            label={t('pages:insufficientData')}
                            value={String(drawdownInsufficient.length)}
                            tone={drawdownInsufficient.length > 0 ? 'warning' : 'muted'}
                            footer={t('pages:noEquityDrawdownCannotBeCalculated')}
                        />
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:thresholdDistributionsAndRankingsUseMaximumDrawdownPerRunPortfolioTimeSeriesDrawdownAppearsAboveRuns')}</Typography.Text>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={drawdownRanked}
                        columns={riskRunColumns()}
                        scroll={{x: 1100, y: 260}}
                        locale={{emptyText: t('pages:noPaperRunsWithCalculableMaximumDrawdown')}}
                    />
                    {drawdownInsufficient.length > 0 ? (
                        <Descriptions bordered size="small" column={1}>
                            <Descriptions.Item label={t('pages:insufficientDataNoDrawdownValue1', {value1: drawdownInsufficient.length})}>
                                {renderRunRefTags(drawdownInsufficient)}
                            </Descriptions.Item>
                        </Descriptions>
                    ) : null}
                </Space>
            </Card>

            {/* 3) 风控与异常清单 */}
            <Card size="small" title={t('pages:riskBlocksAndExceptions')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:prioritizeRiskBlocksAndUnresolvedAlertsFailedCancelledAreAbnormalTerminalStatesRequiringReview')}</Typography.Text>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={highlights.riskBlockedRuns}
                        columns={riskRunColumns()}
                        scroll={{x: 1100, y: 220}}
                        locale={{emptyText: t('pages:noRiskBlockedPaperRuns')}}
                    />
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={t('pages:runsWithUnresolvedAlertsValue1', {value1: openAlertRuns.length})}>
                            {renderRunRefTags(openAlertRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:failedCancelledRunsValue1Total', {value1: failedCancelledCount})}>
                            {failedCancelledRuns.length > 0 ? renderRunRefTags(failedCancelledRuns) : (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                    {failedCancelledCount > 0 ? t('pages:abnormalTerminalRunsAreOutsideThisRiskSampleSeeThePaperRunListBelow') : t('pages:none')}
                                </Typography.Text>
                            )}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 4) 无交易 / 数据不足清单 */}
            <Card size="small" title={t('pages:noTradesInsufficientData')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={highlights.noTradeRuns}
                        columns={[
                            {title: t('pages:paperRun'), dataIndex: 'paperRunId', key: 'paperRunId', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
                            {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <StatusTag title="" variant="pill" status={v}/>},
                            {
                                title: t('pages:possibleCauses'),
                                key: 'cause',
                                width: 120,
                                render: (_: unknown, run: PaperPortfolioRunRef) => {
                                    const cause = deriveNoTradeCause(run, dataInsufficientIds, missingEquityIds);
                                    return <StatusTag title="" variant="pill" status={cause.label} tone={cause.tone}/>;
                                },
                            },
                            {
                                // Loop-18：执行进度细分（无订单 / 有订单无成交），基于后端 run 级标记，附原因提示。
                                title: t('pages:executionProgress'),
                                key: 'execProgress',
                                width: 160,
                                render: (_: unknown, run: PaperPortfolioRunRef) => {
                                    const prog = deriveExecProgress(run);
                                    return (
                                        <Space direction="vertical" size={0}>
                                            <StatusTag title="" variant="pill" status={prog.label} tone={prog.tone}/>
                                            <Typography.Text type="secondary" style={{fontSize: 11}}>{prog.hint}</Typography.Text>
                                        </Space>
                                    );
                                },
                            },
                            {
                                title: t('pages:strategyVersionPublish'),
                                key: 'lineage',
                                width: 200,
                                render: (_: unknown, run: PaperPortfolioRunRef) => (
                                    <Space direction="vertical" size={0}>
                                        <span className="nq-mono" style={{fontSize: 11}}>{run.strategyVersionId ?? t('pages:noStrategyVersionBound')}</span>
                                        <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 11}}>{run.publishId || t('pages:unknownPublish')}</Typography.Text>
                                    </Space>
                                ),
                            },
                            {title: t('pages:recentlyActive'), dataIndex: 'lastActivityAt', key: 'lastActivityAt', width: 170, render: (v: string | null) => formatDateTime(v)},
                        ]}
                        scroll={{x: 940, y: 220}}
                        locale={{emptyText: t('pages:noPaperRunsWithoutTrades')}}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:runsWithoutTradesAreSplitIntoMissingOrdersAndUnfilledOrdersOlderResponsesWithoutThatDistinctionUseAG')}</Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={t('pages:runsWithInsufficientDataValue1', {value1: dataQuality.dataInsufficientRuns.length})}>
                            {renderRunRefTags(dataQuality.dataInsufficientRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 5) 数据质量分析 */}
            <Card size="small" title={t('pages:riskDataQuality')}>
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:runsMissingEquityInitialCapitalPnlOrSourcesCannotEnterDrawdownAndReturnRiskEvaluationsTheyAreMarkedE')}</Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={t('pages:missingEquitySnapshotValue1', {value1: dataQuality.missingEquityRuns.length})}>
                            {renderRunRefTags(dataQuality.missingEquityRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:missingPnlValue1', {value1: missingPnlRuns.length})}>
                            {renderRunRefTags(missingPnlRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:missingBacktestSourceValue1', {value1: dataQuality.missingBacktestSourceRuns.length})}>
                            {renderRunRefTags(dataQuality.missingBacktestSourceRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:missingPublishSourceValue1', {value1: dataQuality.missingPublishSourceRuns.length})}>
                            {renderRunRefTags(dataQuality.missingPublishSourceRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>
        </Space>
    );
}

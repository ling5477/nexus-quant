import {Button, Card, Collapse, Descriptions, Select, Space, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {
    NqAmountText,
    NqDataTable,
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    NqMetricCard,
    NqPercentText,
    NqPortfolioDrawdownChart,
    NqPortfolioEquityChart,
    NqRiskBanner,
    NqStatusTag,
    nqNumericColumn,
} from '@/components/nq';
import type {NqStatusTone} from '@/components/nq';
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
): {label: string; tone: NqStatusTone} {
    if (run.riskBlocked) {
        return {label: '风控拦截', tone: 'danger'};
    }
    if (run.status === 'FAILED' || run.status === 'CANCELLED') {
        return {label: '异常结束', tone: 'danger'};
    }
    if (run.status === 'CREATED') {
        return {label: '尚未启动', tone: 'neutral'};
    }
    if (dataInsufficientIds.has(run.paperRunId) || missingEquityIds.has(run.paperRunId)) {
        return {label: '数据不足', tone: 'warning'};
    }
    return {label: '策略未触发', tone: 'info'};
}

/**
 * 无交易 run 的执行进度细分（Loop-18）：基于后端 run 级 noOrder / orderNoFill 标记，
 * 区分「无订单」与「有订单无成交」；旧后端缺该标记时回退到「无成交」泛标签，不臆测。
 */
function deriveExecProgress(run: PaperPortfolioRunRef): {label: string; tone: NqStatusTone; hint: string} {
    if (run.orderNoFill) {
        return {label: '有订单无成交', tone: 'warning', hint: '撮合 / 价格条件未满足或流动性模拟不足'};
    }
    if (run.noOrder) {
        return {label: '无订单', tone: 'info', hint: '策略未触发 / 尚未启动 / 数据不足'};
    }
    // 旧后端无 order 拆分字段（noOrder/orderNoFill 均缺失）：泛标签兜底，不伪造拆分。
    return {label: '无成交', tone: 'neutral', hint: '需查看单 run 订单与成交明细'};
}

/** 风险 run 表通用列：run / 状态 / 策略版本+发布 / 当前权益 / 总 PnL / 最大回撤 / 未处理告警 / 最近活跃。 */
function riskRunColumns(): ColumnsType<PaperPortfolioRunRef> {
    return [
        {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <NqStatusTag status={v}/>},
        {
            title: '策略版本 / 发布',
            key: 'lineage',
            width: 200,
            render: (_: unknown, run: PaperPortfolioRunRef) => (
                <Space direction="vertical" size={0}>
                    <span className="nq-mono" style={{fontSize: 11}}>{run.strategyVersionId ?? '(未绑定策略版本)'}</span>
                    <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 11}}>{run.publishId || '(未知发布)'}</Typography.Text>
                </Space>
            ),
        },
        nqNumericColumn({title: '当前权益', dataIndex: 'currentEquity', key: 'currentEquity', width: 120, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({
            title: '总 PnL',
            dataIndex: 'totalPnl',
            key: 'totalPnl',
            width: 120,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqAmountText value={v as string | number} signed colorBySign/>),
        }),
        nqNumericColumn({
            title: '最大回撤',
            dataIndex: 'maxDrawdown',
            key: 'maxDrawdown',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '未处理告警', dataIndex: 'openAlertCount', key: 'openAlertCount', width: 100}),
        {title: '最近活跃', dataIndex: 'lastActivityAt', key: 'lastActivityAt', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

/** 组合曲线采样点表列：时间 / 组合权益 / 组合 PnL / 收益率 / 回撤 / 在册 run / 缺失 run。 */
function portfolioCurveColumns(): ColumnsType<PaperPortfolioCurvePoint> {
    return [
        {title: '时间', dataIndex: 'timestamp', key: 'timestamp', width: 170, render: (v: string) => formatDateTime(v)},
        nqNumericColumn({title: '组合权益', dataIndex: 'totalEquity', key: 'totalEquity', width: 130, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({
            title: '组合 PnL',
            dataIndex: 'totalPnl',
            key: 'totalPnl',
            width: 130,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqAmountText value={v as string | number} signed colorBySign/>),
        }),
        nqNumericColumn({
            title: '收益率',
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: '回撤',
            dataIndex: 'drawdown',
            key: 'drawdown',
            width: 110,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '在册 run', dataIndex: 'sourceRunCount', key: 'sourceRunCount', width: 90}),
        nqNumericColumn({title: '缺失 run', dataIndex: 'missingRunCount', key: 'missingRunCount', width: 90}),
    ];
}

/**
 * PortfolioEquityCurveCard —— 组合级 equity / drawdown 时间序列卡（Loop-15）。
 * 优先消费后端 portfolioCurve（真实组合时间序列：当前回撤 / 最大回撤 / 资金峰值 / 最新组合 equity + 采样点）；
 * 不可用（数据不足或旧版本响应缺字段）时回退提示，由上层「回撤分析」继续以单 run 最大回撤口径兜底。
 * 仅代表 SIM/Paper 模拟、简化组合资金合计曲线，不代表真实时间加权组合收益，也不代表 LIVE 或真实交易。
 */
function PortfolioEquityCurveCard({curve}: {curve: PaperPortfolioCurve | null | undefined}) {
    const points: PaperPortfolioCurvePoint[] = curve?.points ?? [];
    const hasCurve = Boolean(curve) && points.length > 0;

    return (
        <Card
            size="small"
            title="组合资金曲线与回撤"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                {hasCurve && curve ? (
                    <>
                        <div className="nq-status-strip">
                            <NqMetricCard label="最新组合 equity" value={<NqAmountText value={curve.latestEquity}/>}/>
                            <NqMetricCard label="资金峰值" value={<NqAmountText value={curve.peakEquity}/>}/>
                            <NqMetricCard
                                label="组合当前回撤"
                                value={curve.currentDrawdown !== null
                                    ? <NqPercentText value={curve.currentDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="warning"
                                footer="组合时间序列口径"
                            />
                            <NqMetricCard
                                label="组合最大回撤"
                                value={curve.maxDrawdown !== null
                                    ? <NqPercentText value={curve.maxDrawdown} ratio signed={false}/>
                                    : '-'}
                                tone="danger"
                            />
                            <NqMetricCard
                                label="可比 run"
                                value={String(curve.coverage.comparableRunCount)}
                                footer={`缺 equity ${curve.coverage.missingEquityRunCount} · 不完整点 ${curve.coverage.incompletePointCount}`}
                            />
                        </div>

                        {/* 组合资金曲线图（复用 Design System ECharts 主题；hover 见每点组合权益/PnL/收益率/在册·缺失 run） */}
                        <div>
                            <Typography.Text strong style={{fontSize: 13}}>组合资金曲线</Typography.Text>
                            <NqPortfolioEquityChart points={points}/>
                            <Typography.Text type="secondary" style={{fontSize: 12}}>
                                实线为组合资金合计，虚线为可比 run 初始资金合计基线；hover 查看每点组合权益 / PnL / 收益率 / 在册·缺失 run。
                            </Typography.Text>
                        </div>

                        {/* 组合回撤曲线图（y 轴反向、回撤向下；hover 见回撤/资金峰值/组合权益） */}
                        <div>
                            <Typography.Text strong style={{fontSize: 13}}>组合回撤曲线</Typography.Text>
                            <NqPortfolioDrawdownChart points={points}/>
                        </div>

                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            覆盖度：可比 run {curve.coverage.comparableRunCount} · 缺 equity {curve.coverage.missingEquityRunCount}
                            {' '}· 不完整点 {curve.coverage.incompletePointCount}（共 {curve.pointCount} 个采样点）。
                            每个时间点 sourceRunCount 为已在册 run 数，missingRunCount 为尚未起跑的可比 run 数。
                        </Typography.Text>
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            该组合资金曲线仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。
                            该曲线是组合资金合计曲线，不等同于严格时间加权收益。
                        </Typography.Text>

                        {/* 采样点表保留为可折叠辅助展示，保持数据透明度（默认折叠，避免与图表重复占屏） */}
                        <Collapse
                            size="small"
                            items={[{
                                key: 'curve-points',
                                label: `组合曲线采样点（共 ${curve.pointCount}，展开查看最近 ${Math.min(points.length, 12)} 条）`,
                                children: (
                                    <NqDataTable<PaperPortfolioCurvePoint>
                                        rowKey="timestamp"
                                        pagination={false}
                                        dataSource={[...points].slice(-12).reverse()}
                                        columns={portfolioCurveColumns()}
                                        scroll={{x: 900, y: 240}}
                                        locale={{emptyText: '暂无组合曲线采样点。'}}
                                    />
                                ),
                            }]}
                        />
                    </>
                ) : (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="组合 equity curve 暂不可用（数据不足或旧版本响应），回退按单 run 最大回撤统计口径展示。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，不展示组合时间序列回撤</Typography.Text>
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
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    return (
      <section aria-label="Paper 风险与回撤驾驶舱">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 风险与回撤驾驶舱"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="warning"
                    message="聚焦组合内最高风险、最大回撤、风控拦截、无交易与数据不足的 Paper run。"
                    description="该风险看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易风险。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 风险与回撤驾驶舱加载失败"
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="暂无 Paper 风险数据，创建并运行 Paper run 后自动汇总风险与回撤。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，不展示回撤 / 风险数值</Typography.Text>
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
    {label: '全部', value: 'all'},
    {label: '风控拦截', value: 'riskBlocked'},
    {label: '无订单', value: 'noOrder'},
    {label: '有订单无成交', value: 'orderNoFill'},
    {label: '有成交', value: 'hasFill'},
    {label: '数据不足', value: 'dataInsufficient'},
    {label: '异常终态', value: 'terminal'},
    {label: '高回撤', value: 'highDrawdown'},
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
function runExecTag(run: PaperPortfolioRunRef): {label: string; tone: NqStatusTone} {
    if (run.hasFill) {
        return {label: '有成交', tone: 'success'};
    }
    if (run.orderNoFill) {
        return {label: '有订单无成交', tone: 'warning'};
    }
    if (run.noOrder) {
        return {label: '无订单', tone: 'info'};
    }
    return {label: '无成交', tone: 'neutral'};
}

function PaperRiskDrawdownBody({portfolio}: {portfolio: PaperPortfolioSummaryResponse}) {
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
        ? `无订单 ${overview.noOrderRunCount} · 有订单无成交 ${overview.orderNoFillRunCount}`
        : '无订单 / 有订单无成交需查看单 run';

    // Loop-19：统一风险 Run 清单（合并去重的 pool）按筛选条件展示。
    const riskRunFiltered = filterRiskRuns(pool, riskFilter, dataInsufficientIds);
    const riskFilterLabel = RISK_RUN_FILTER_OPTIONS.find((o) => o.value === riskFilter)?.label ?? '全部';
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
                    label="最大单 run 回撤"
                    value={overview.worstRunDrawdown !== null
                        ? <NqPercentText value={overview.worstRunDrawdown} ratio signed={false}/>
                        : '-'}
                    tone="warning"
                    footer={highlights.worstDrawdown ? `当前最大回撤 run：${highlights.worstDrawdown.paperRunId}` : '按单 run 最大回撤统计'}
                />
                <ClickableMetricCard
                    ariaLabel="筛选风控拦截风险 Run"
                    testId="risk-filter-card-risk-blocked"
                    isActive={riskFilter === 'riskBlocked'}
                    onClick={() => handleRiskCardClick('riskBlocked')}
                >
                    <NqMetricCard
                        label="风控拦截 run"
                        value={String(overview.riskBlockedRunCount)}
                        tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}
                        footer="点击筛选"
                    />
                </ClickableMetricCard>
                <NqMetricCard
                    label="未处理告警"
                    value={String(overview.openAlertCount)}
                    tone={overview.openAlertCount > 0 ? 'warning' : 'muted'}
                />
                {hasOrderSplit ? (
                    <>
                        <ClickableMetricCard
                            ariaLabel="筛选无订单风险 Run"
                            testId="risk-filter-card-no-order"
                            isActive={riskFilter === 'noOrder'}
                            onClick={() => handleRiskCardClick('noOrder')}
                        >
                            <NqMetricCard
                                label="无订单"
                                value={String(overview.noOrderRunCount ?? 0)}
                                tone={(overview.noOrderRunCount ?? 0) > 0 ? 'warning' : 'muted'}
                                footer="点击筛选"
                            />
                        </ClickableMetricCard>
                        <ClickableMetricCard
                            ariaLabel="筛选有订单无成交风险 Run"
                            testId="risk-filter-card-order-no-fill"
                            isActive={riskFilter === 'orderNoFill'}
                            onClick={() => handleRiskCardClick('orderNoFill')}
                        >
                            <NqMetricCard
                                label="有订单无成交"
                                value={String(overview.orderNoFillRunCount ?? 0)}
                                tone={(overview.orderNoFillRunCount ?? 0) > 0 ? 'warning' : 'muted'}
                                footer="点击筛选"
                            />
                        </ClickableMetricCard>
                        <ClickableMetricCard
                            ariaLabel="筛选有成交风险 Run"
                            testId="risk-filter-card-has-fill"
                            isActive={riskFilter === 'hasFill'}
                            onClick={() => handleRiskCardClick('hasFill')}
                        >
                            <NqMetricCard
                                label="有成交"
                                value={String(overview.filledRunCount ?? '-')}
                                tone={(overview.filledRunCount ?? 0) > 0 ? 'success' : 'muted'}
                                footer="点击筛选"
                            />
                        </ClickableMetricCard>
                    </>
                ) : (
                    <NqMetricCard
                        label="无交易 run"
                        value={String(overview.noTradeRunCount)}
                        tone={overview.noTradeRunCount > 0 ? 'warning' : 'muted'}
                        footer={noTradeSplitFooter}
                    />
                )}
                <ClickableMetricCard
                    ariaLabel="筛选数据不足风险 Run"
                    testId="risk-filter-card-data-insufficient"
                    isActive={riskFilter === 'dataInsufficient'}
                    onClick={() => handleRiskCardClick('dataInsufficient')}
                >
                    <NqMetricCard
                        label="数据不足 run"
                        value={String(overview.dataInsufficientRunCount)}
                        tone={overview.dataInsufficientRunCount > 0 ? 'warning' : 'muted'}
                        footer="点击筛选"
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="筛选异常终态风险 Run"
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
                    ariaLabel="筛选高回撤风险 Run"
                    testId="risk-filter-card-high-drawdown"
                    isActive={riskFilter === 'highDrawdown'}
                    onClick={() => handleRiskCardClick('highDrawdown')}
                >
                    <NqMetricCard
                        label="高回撤 run"
                        value={String(highDrawdownCount)}
                        tone={highDrawdownCount > 0 ? 'danger' : 'muted'}
                        footer="回撤 ≤ -10%，点击筛选"
                    />
                </ClickableMetricCard>
            </div>

            {/* 1.5) 统一风险 Run 清单（Loop-19）：合并 highlights/dataQuality 去重，按条件筛选快速定位 */}
            <Card
                size="small"
                title={riskFilter !== 'all'
                    ? `风险 Run 清单 · 当前筛选：${riskFilterLabel}（${riskRunFiltered.length} 条）`
                    : '风险 Run 清单'}
                extra={riskFilter !== 'all' ? (
                    <Button size="small" type="link" onClick={() => setRiskFilter('all')}>查看全部</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label="风险 Run 筛选"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>风险筛选</Typography.Text>
                        <Select<RiskRunFilter>
                            size="small"
                            value={riskFilter}
                            onChange={setRiskFilter}
                            options={RISK_RUN_FILTER_OPTIONS as Array<{label: string; value: RiskRunFilter}>}
                            style={{width: 150}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            「{riskFilterLabel}」命中 {riskRunFiltered.length} 个 run
                        </Typography.Text>
                    </div>
                    {riskRunFiltered.length > 0 ? (
                        <div role="region" aria-label="风险 Run 清单表">
                            <NqDataTable<PaperPortfolioRunRef>
                                rowKey="paperRunId"
                                pagination={false}
                                dataSource={riskRunFiltered}
                                columns={[
                                    ...riskRunColumns(),
                                    {
                                        title: '执行进度',
                                        key: 'exec',
                                        width: 120,
                                        render: (_: unknown, run: PaperPortfolioRunRef) => {
                                            const t = runExecTag(run);
                                            return <NqStatusTag status={t.label} tone={t.tone}/>;
                                        },
                                    },
                                ]}
                                scroll={{x: 1220, y: 260}}
                                locale={{emptyText: '暂无匹配的风险 Run。'}}
                            />
                        </div>
                    ) : (
                        <NqEmptyState description={`当前筛选「${riskFilterLabel}」下暂无匹配的风险 Run。`}/>
                    )}
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        风险 Run 清单合并 highlights 与数据质量清单去重后按筛选条件展示。
                    </Typography.Text>
                </Space>
            </Card>

            {/* 2) 组合资金曲线与回撤（Loop-15：真实组合时间序列口径，不可用时回退单 run 口径） */}
            <PortfolioEquityCurveCard curve={portfolio.portfolioCurve}/>

            {/* 3) 回撤分析（单 run 最大回撤口径，与上方组合时间序列口径互补） */}
            <Card size="small" title="回撤分析">
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
                            label="数据不足"
                            value={String(drawdownInsufficient.length)}
                            tone={drawdownInsufficient.length > 0 ? 'warning' : 'muted'}
                            footer="无 equity / 无法计算回撤"
                        />
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        回撤阈值分布与排行按单 run 最大回撤统计；组合层真实时间序列回撤见上方「组合资金曲线与回撤」。
                        单 run 口径与组合曲线口径互补，数据不足的 run 单列「数据不足」，不伪造回撤。
                    </Typography.Text>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={drawdownRanked}
                        columns={riskRunColumns()}
                        scroll={{x: 1100, y: 260}}
                        locale={{emptyText: '暂无可计算最大回撤的 Paper run。'}}
                    />
                    {drawdownInsufficient.length > 0 ? (
                        <Descriptions bordered size="small" column={1}>
                            <Descriptions.Item label={`数据不足（无回撤，${drawdownInsufficient.length}）`}>
                                {renderRunRefTags(drawdownInsufficient)}
                            </Descriptions.Item>
                        </Descriptions>
                    ) : null}
                </Space>
            </Card>

            {/* 3) 风控与异常清单 */}
            <Card size="small" title="风控与异常清单">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        风控拦截与未处理告警优先处理；FAILED / CANCELLED 为异常终态，需复盘运行原因。
                    </Typography.Text>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={highlights.riskBlockedRuns}
                        columns={riskRunColumns()}
                        scroll={{x: 1100, y: 220}}
                        locale={{emptyText: '暂无被风控拦截的 Paper run。'}}
                    />
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`未处理告警 run（${openAlertRuns.length}）`}>
                            {renderRunRefTags(openAlertRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`FAILED / CANCELLED run（共 ${failedCancelledCount}）`}>
                            {failedCancelledRuns.length > 0 ? renderRunRefTags(failedCancelledRuns) : (
                                <Typography.Text type="secondary" style={{fontSize: 12}}>
                                    {failedCancelledCount > 0 ? '异常终态 run 未在风险清单样本中，详见下方 Paper run 列表。' : '无'}
                                </Typography.Text>
                            )}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 4) 无交易 / 数据不足清单 */}
            <Card size="small" title="无交易 / 数据不足清单">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <NqDataTable<PaperPortfolioRunRef>
                        rowKey="paperRunId"
                        pagination={false}
                        dataSource={highlights.noTradeRuns}
                        columns={[
                            {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
                            {title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (v: string) => <NqStatusTag status={v}/>},
                            {
                                title: '可能原因',
                                key: 'cause',
                                width: 120,
                                render: (_: unknown, run: PaperPortfolioRunRef) => {
                                    const cause = deriveNoTradeCause(run, dataInsufficientIds, missingEquityIds);
                                    return <NqStatusTag status={cause.label} tone={cause.tone}/>;
                                },
                            },
                            {
                                // Loop-18：执行进度细分（无订单 / 有订单无成交），基于后端 run 级标记，附原因提示。
                                title: '执行进度',
                                key: 'execProgress',
                                width: 160,
                                render: (_: unknown, run: PaperPortfolioRunRef) => {
                                    const prog = deriveExecProgress(run);
                                    return (
                                        <Space direction="vertical" size={0}>
                                            <NqStatusTag status={prog.label} tone={prog.tone}/>
                                            <Typography.Text type="secondary" style={{fontSize: 11}}>{prog.hint}</Typography.Text>
                                        </Space>
                                    );
                                },
                            },
                            {
                                title: '策略版本 / 发布',
                                key: 'lineage',
                                width: 200,
                                render: (_: unknown, run: PaperPortfolioRunRef) => (
                                    <Space direction="vertical" size={0}>
                                        <span className="nq-mono" style={{fontSize: 11}}>{run.strategyVersionId ?? '(未绑定策略版本)'}</span>
                                        <Typography.Text type="secondary" className="nq-mono" style={{fontSize: 11}}>{run.publishId || '(未知发布)'}</Typography.Text>
                                    </Space>
                                ),
                            },
                            {title: '最近活跃', dataIndex: 'lastActivityAt', key: 'lastActivityAt', width: 170, render: (v: string | null) => formatDateTime(v)},
                        ]}
                        scroll={{x: 940, y: 220}}
                        locale={{emptyText: '暂无无交易的 Paper run。'}}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        无交易已按执行进度细分为「无订单」（策略未触发 / 尚未启动 / 数据不足）与「有订单无成交」（撮合 / 价格条件未满足或流动性模拟不足）；
                        旧后端响应缺该拆分字段时回退为「无成交」泛标签，详细仍可查看单 run。
                    </Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`数据不足 run（${dataQuality.dataInsufficientRuns.length}）`}>
                            {renderRunRefTags(dataQuality.dataInsufficientRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 5) 数据质量分析 */}
            <Card size="small" title="风险数据质量">
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        缺 equity / 初始资金 / PnL / 来源的 run 无法参与回撤与收益风险评估，已明确标注，不以缺省值伪造风险。
                    </Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`缺 equity snapshot（${dataQuality.missingEquityRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingEquityRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 PnL（${missingPnlRuns.length}）`}>
                            {renderRunRefTags(missingPnlRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 backtest 来源（${dataQuality.missingBacktestSourceRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingBacktestSourceRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`缺 publish 来源（${dataQuality.missingPublishSourceRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingPublishSourceRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>
        </Space>
    );
}

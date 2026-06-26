import {Card, Segmented, Select, Space, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState, type ReactNode} from 'react';

import {
    NqAmountText,
    NqDataTable,
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    NqMetricCard,
    NqPercentText,
    NqRiskBanner,
    formatNqNumber,
    nqNumericColumn,
} from '@/components/nq';
import {usePaperPortfolioSummaryQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {
    PaperPortfolioGroup,
    PaperPortfolioRunRef,
    PaperPortfolioSummaryResponse,
} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

import {pnlTone, toNullableNumber} from './paperFormatters';
import {ClickableMetricCard} from './paperPortfolioShared';

/** 策略 / 发布维度排行行：组合 summary 的 group 字段 + 无交易 / 数据不足 / 异常终态计数与风险调整分。 */
interface PaperStrategyRankingRow {
    key: string;
    runCount: number;
    currentEquity: string | number | null;
    totalPnl: string | number | null;
    totalReturn: string | number | null;
    worstDrawdown: string | number | null;
    riskBlockedCount: number;
    openAlertCount: number;
    lastRunTime: string | null;
    noTradeCount: number;
    dataInsufficientCount: number;
    failedCancelledCount: number;
    // Loop-18：把无交易拆为无订单 / 有订单无成交，并显式有成交。无订单 / 有订单无成交在旧后端无法拆分时为 null（显示「-」）；
    // 有成交可由 runCount - noTradeCount 稳定派生，恒为 number。
    noOrderCount: number | null;
    orderNoFillCount: number | null;
    filledRunCount: number;
    score: number | null;
}

/**
 * 风险调整排序分（Paper 内部排序分，非真实投资评级）：
 * score = totalReturn - |maxDrawdown| - riskBlockedCount*0.05 - openAlertCount*0.01 - dataInsufficientCount*0.02。
 * totalReturn 或 maxDrawdown 缺失时返回 null（展示「数据不足」，不伪造排序）。
 */
function riskAdjustedScore(group: PaperPortfolioGroup, dataInsufficientCount: number): number | null {
    const totalReturn = toNullableNumber(group.totalReturn);
    const maxDrawdown = toNullableNumber(group.worstDrawdown);
    if (totalReturn === null || maxDrawdown === null) {
        return null;
    }
    return totalReturn
        - Math.abs(maxDrawdown)
        - group.riskBlockedCount * 0.05
        - group.openAlertCount * 0.01
        - dataInsufficientCount * 0.02;
}

/** 按 strategyVersionId / publishId 维度对 run 引用计数（用于派生每组无交易 / 数据不足 run 数）。 */
function countRunsByKey(runs: PaperPortfolioRunRef[], keyOf: (run: PaperPortfolioRunRef) => string | null): Map<string, number> {
    const map = new Map<string, number>();
    runs.forEach((run) => {
        const key = keyOf(run);
        if (key) {
            map.set(key, (map.get(key) ?? 0) + 1);
        }
    });
    return map;
}

/**
 * 组装某一维度（strategy / publish）的排行行：
 * Loop-17 起优先用后端 group 精确计数（noTradeCount / dataInsufficientCount / failedCount / cancelledCount，
 * 基于完整 bounded runs）；旧后端缺字段时回退到 highlights / dataQuality 截断子集派生（异常终态无法派生时记 0）。
 * 按风险调整分降序（null 分末尾），同分按总 PnL 降序，得到稳定的「风险调整后排行」。
 */
function buildRankingRows(
    groups: PaperPortfolioGroup[],
    noTradeRuns: PaperPortfolioRunRef[],
    dataInsufficientRuns: PaperPortfolioRunRef[],
    keyOf: (run: PaperPortfolioRunRef) => string | null,
): PaperStrategyRankingRow[] {
    const noTradeByKey = countRunsByKey(noTradeRuns, keyOf);
    const dataInsufficientByKey = countRunsByKey(dataInsufficientRuns, keyOf);
    const rows = groups.map((group) => {
        // `?? 派生`：后端字段缺失（undefined）才回退；后端精确 0 会被尊重（0 非 nullish）。
        const noTradeCount = group.noTradeCount ?? (noTradeByKey.get(group.key) ?? 0);
        const dataInsufficientCount = group.dataInsufficientCount ?? (dataInsufficientByKey.get(group.key) ?? 0);
        const failedCancelledCount = (group.failedCount ?? 0) + (group.cancelledCount ?? 0);
        // Loop-18：无订单 / 有订单无成交需后端拆分字段，缺失则为 null（列显示「-」，不臆造拆分）；
        // 有成交可由 runCount - noTradeCount 稳定派生（noTradeCount == 无订单 + 有订单无成交）。
        const noOrderCount = group.noOrderCount ?? null;
        const orderNoFillCount = group.orderNoFillCount ?? null;
        const filledRunCount = group.filledRunCount ?? Math.max(0, group.runCount - noTradeCount);
        return {
            key: group.key,
            runCount: group.runCount,
            currentEquity: group.currentEquity,
            totalPnl: group.totalPnl,
            totalReturn: group.totalReturn,
            worstDrawdown: group.worstDrawdown,
            riskBlockedCount: group.riskBlockedCount,
            openAlertCount: group.openAlertCount,
            lastRunTime: group.lastRunTime,
            noTradeCount,
            dataInsufficientCount,
            failedCancelledCount,
            noOrderCount,
            orderNoFillCount,
            filledRunCount,
            score: riskAdjustedScore(group, dataInsufficientCount),
        };
    });
    return rows.sort((left, right) => {
        const leftScore = left.score === null ? Number.NEGATIVE_INFINITY : left.score;
        const rightScore = right.score === null ? Number.NEGATIVE_INFINITY : right.score;
        if (rightScore !== leftScore) {
            return rightScore - leftScore;
        }
        const leftPnl = toNullableNumber(left.totalPnl) ?? Number.NEGATIVE_INFINITY;
        const rightPnl = toNullableNumber(right.totalPnl) ?? Number.NEGATIVE_INFINITY;
        return rightPnl - leftPnl;
    });
}

/** 取某指标的极值行（preferMax=true 取最大，false 取最小）；指标为 null 的行跳过，避免伪造排名。 */
function topRankingRow(
    rows: PaperStrategyRankingRow[],
    valueOf: (row: PaperStrategyRankingRow) => number | null,
    preferMax = true,
): PaperStrategyRankingRow | null {
    let best: PaperStrategyRankingRow | null = null;
    let bestValue: number | null = null;
    for (const row of rows) {
        const value = valueOf(row);
        if (value === null) {
            continue;
        }
        if (bestValue === null || (preferMax ? value > bestValue : value < bestValue)) {
            best = row;
            bestValue = value;
        }
    }
    return best;
}

/** 风险调整分展示：null → 「数据不足」，否则带符号 4 位小数（明确为排序分，不是收益率）。 */
function renderScore(score: number | null): ReactNode {
    return score === null
        ? <Typography.Text type="secondary">数据不足</Typography.Text>
        : <span className="nq-num">{formatNqNumber(score, {precision: 4, signed: true})}</span>;
}

/** 排行表列：策略表与发布表均展示无交易 / 数据不足 / 异常终态（FAILED+CANCELLED）计数（Loop-17 精确口径）。 */
function rankingColumns(keyTitle: string): ColumnsType<PaperStrategyRankingRow> {
    return [
        {title: keyTitle, dataIndex: 'key', key: 'key', width: 190, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run 数', dataIndex: 'runCount', key: 'runCount', width: 76}),
        nqNumericColumn({title: '当前权益', dataIndex: 'currentEquity', key: 'currentEquity', width: 120, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({title: '总 PnL', dataIndex: 'totalPnl', key: 'totalPnl', width: 120, render: (v) => <NqAmountText value={v as string | number | null} signed colorBySign/>}),
        nqNumericColumn({
            title: '累计收益率',
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 104,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: '最大回撤',
            dataIndex: 'worstDrawdown',
            key: 'worstDrawdown',
            width: 104,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '风控拦截', dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 84}),
        nqNumericColumn({title: '告警', dataIndex: 'openAlertCount', key: 'openAlertCount', width: 72}),
        nqNumericColumn({title: '无交易', dataIndex: 'noTradeCount', key: 'noTradeCount', width: 76}),
        nqNumericColumn({title: '无订单', dataIndex: 'noOrderCount', key: 'noOrderCount', width: 76, render: (v) => (v === null || v === undefined ? '-' : String(v))}),
        nqNumericColumn({title: '有单无成交', dataIndex: 'orderNoFillCount', key: 'orderNoFillCount', width: 96, render: (v) => (v === null || v === undefined ? '-' : String(v))}),
        nqNumericColumn({title: '有成交', dataIndex: 'filledRunCount', key: 'filledRunCount', width: 76}),
        nqNumericColumn({title: '数据不足', dataIndex: 'dataInsufficientCount', key: 'dataInsufficientCount', width: 84}),
        nqNumericColumn({title: '异常终态', dataIndex: 'failedCancelledCount', key: 'failedCancelledCount', width: 84}),
        nqNumericColumn({title: '风险调整分', dataIndex: 'score', key: 'score', width: 110, render: (v) => renderScore(v as number | null)}),
        {title: '最近运行', dataIndex: 'lastRunTime', key: 'lastRunTime', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

// ---- Loop-19：排行控件（排序维度 / 方向 / 数据过滤），纯前端只读派生，不改后端契约 ----

type RankingSortDim =
    'score' | 'totalReturn' | 'totalPnl' | 'worstDrawdown' | 'riskBlocked'
    | 'noOrder' | 'orderNoFill' | 'dataInsufficient' | 'lastRun';
type RankingSortDir = 'desc' | 'asc';
type RankingFilter = 'all' | 'hasReturn' | 'dataInsufficient' | 'riskBlocked' | 'noOrder' | 'orderNoFill' | 'abnormalTerminal';

const RANKING_SORT_OPTIONS: ReadonlyArray<{label: string; value: RankingSortDim}> = [
    {label: '风险调整分', value: 'score'},
    {label: '累计收益率', value: 'totalReturn'},
    {label: '总 PnL', value: 'totalPnl'},
    {label: '最大回撤', value: 'worstDrawdown'},
    {label: '风控拦截', value: 'riskBlocked'},
    {label: '无订单', value: 'noOrder'},
    {label: '有单无成交', value: 'orderNoFill'},
    {label: '数据不足', value: 'dataInsufficient'},
    {label: '最近运行', value: 'lastRun'},
];

const RANKING_FILTER_OPTIONS: ReadonlyArray<{label: string; value: RankingFilter}> = [
    {label: '全部', value: 'all'},
    {label: '仅有收益率', value: 'hasReturn'},
    {label: '仅数据不足', value: 'dataInsufficient'},
    {label: '仅有风控拦截', value: 'riskBlocked'},
    {label: '仅无订单', value: 'noOrder'},
    {label: '仅有单无成交', value: 'orderNoFill'},
    {label: '仅异常终态', value: 'abnormalTerminal'},
];

/**
 * 排序值提取：统一约定「值越大越靠前（降序）」。最大回撤取绝对值（回撤越深值越大），
 * 收益率 / PnL / 风险调整分按原值；无订单 / 有单无成交在旧后端为 null（排末尾，不伪造顺序）；
 * 最近运行取时间戳毫秒。返回 null 表示该维度不可比，恒排末尾。
 */
function rankingSortValue(row: PaperStrategyRankingRow, dim: RankingSortDim): number | null {
    switch (dim) {
        case 'score': return row.score;
        case 'totalReturn': return toNullableNumber(row.totalReturn);
        case 'totalPnl': return toNullableNumber(row.totalPnl);
        case 'worstDrawdown': {
            const dd = toNullableNumber(row.worstDrawdown);
            return dd === null ? null : Math.abs(dd);
        }
        case 'riskBlocked': return row.riskBlockedCount;
        case 'noOrder': return row.noOrderCount;
        case 'orderNoFill': return row.orderNoFillCount;
        case 'dataInsufficient': return row.dataInsufficientCount;
        case 'lastRun': {
            if (!row.lastRunTime) {
                return null;
            }
            const ms = Date.parse(row.lastRunTime);
            return Number.isNaN(ms) ? null : ms;
        }
        default: return null;
    }
}

/** 按维度 + 方向排序；null 值的行恒排末尾（与方向无关，不伪造排名），同值按风险调整分降序兜底。 */
function sortRankingRows(rows: PaperStrategyRankingRow[], dim: RankingSortDim, dir: RankingSortDir): PaperStrategyRankingRow[] {
    const factor = dir === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
        const av = rankingSortValue(a, dim);
        const bv = rankingSortValue(b, dim);
        if (av === null && bv === null) {
            return 0;
        }
        if (av === null) {
            return 1;
        }
        if (bv === null) {
            return -1;
        }
        if (av !== bv) {
            return (av - bv) * factor;
        }
        const as = a.score ?? Number.NEGATIVE_INFINITY;
        const bs = b.score ?? Number.NEGATIVE_INFINITY;
        return bs - as;
    });
}

/** 数据过滤：null/缺失字段按「不满足」处理（旧后端无订单/有单无成交计数为 null → 不计入，不伪造）。 */
function filterRankingRows(rows: PaperStrategyRankingRow[], filter: RankingFilter): PaperStrategyRankingRow[] {
    switch (filter) {
        case 'hasReturn': return rows.filter((r) => toNullableNumber(r.totalReturn) !== null);
        case 'dataInsufficient': return rows.filter((r) => r.dataInsufficientCount > 0);
        case 'riskBlocked': return rows.filter((r) => r.riskBlockedCount > 0);
        case 'noOrder': return rows.filter((r) => (r.noOrderCount ?? 0) > 0);
        case 'orderNoFill': return rows.filter((r) => (r.orderNoFillCount ?? 0) > 0);
        case 'abnormalTerminal': return rows.filter((r) => r.failedCancelledCount > 0);
        case 'all':
        default: return rows;
    }
}

/**
 * PaperStrategyRankingDashboard —— Paper 策略表现排行（GateJ 后产品化 Loop-16）。
 * 复用 Loop-13 组合 summary 单请求结果（strategyGroups / publishGroups + highlights / dataQuality），
 * 从 strategyVersionId / publishId 维度只读派生表现排行与风险调整排序分（Paper 内部排序分，非真实投资评级）。
 * 仅代表 SIM/Paper 模拟，不读真实交易所账户余额，不代表 LIVE 或真实交易；数据不足不伪造排名。
 */
export function PaperStrategyRankingDashboard({query}: {query: ReturnType<typeof usePaperPortfolioSummaryQuery>}) {
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    const hasGroups = Boolean(portfolio)
        && (portfolio!.strategyGroups.length > 0 || portfolio!.publishGroups.length > 0);

    return (
      <section aria-label="Paper 策略表现排行">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 策略表现排行"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="info"
                    message="按 strategyVersionId / publishId 维度横向比较多个 Paper run 的模拟表现。"
                    description="该策略排行仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 策略表现排行加载失败"
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 || !hasGroups ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="暂无可分组的 Paper 策略 / 发布数据，创建并运行 Paper run 后自动汇总排行。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，不做排行</Typography.Text>
                    </Space>
                ) : (
                    <PaperStrategyRankingBody portfolio={portfolio}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperStrategyRankingBody({portfolio}: {portfolio: PaperPortfolioSummaryResponse}) {
    const {strategyGroups, publishGroups, highlights, dataQuality} = portfolio;

    // Loop-19：排行控件状态（默认风险调整分降序、不过滤），同一套控件同时作用于 Strategy / Publish 两张表。
    const [sortDim, setSortDim] = useState<RankingSortDim>('score');
    const [sortDir, setSortDir] = useState<RankingSortDir>('desc');
    const [rankFilter, setRankFilter] = useState<RankingFilter>('all');

    const strategyRows = buildRankingRows(
        strategyGroups, highlights.noTradeRuns, dataQuality.dataInsufficientRuns, (run) => run.strategyVersionId);
    const publishRows = buildRankingRows(
        publishGroups, highlights.noTradeRuns, dataQuality.dataInsufficientRuns, (run) => run.publishId);

    // 控件作用于「展示」：先过滤再排序；榜单概览仍基于完整 strategyRows（保持总览稳定，不随过滤/排序变化）。
    const strategyRowsView = sortRankingRows(filterRankingRows(strategyRows, rankFilter), sortDim, sortDir);
    const publishRowsView = sortRankingRows(filterRankingRows(publishRows, rankFilter), sortDim, sortDir);
    const sortDimLabel = RANKING_SORT_OPTIONS.find((o) => o.value === sortDim)?.label ?? '风险调整分';
    const sortDirLabel = sortDir === 'desc' ? '降序' : '升序';
    const rankFilterLabel = RANKING_FILTER_OPTIONS.find((o) => o.value === rankFilter)?.label ?? '全部';
    const filtered = rankFilter !== 'all';
    const filterEmptyText = '当前筛选条件下暂无匹配的数据。';

    /** 点击榜单概览卡直接切换排行过滤（Loop-20 click-to-filter）。 */
    const handleRankingCardClick = (filter: RankingFilter) => setRankFilter(filter);

    // 榜单概览基于策略维度（口径与表格一致）；无交易 / 数据不足 / 异常终态均用后端 group 精确计数。
    const topReturn = topRankingRow(strategyRows, (row) => toNullableNumber(row.totalReturn), true);
    const topScore = topRankingRow(strategyRows, (row) => row.score, true);
    const worstDrawdown = topRankingRow(strategyRows, (row) => toNullableNumber(row.worstDrawdown), false);
    const mostRiskBlocked = topRankingRow(strategyRows, (row) => row.riskBlockedCount, true);
    const mostNoTrade = topRankingRow(strategyRows, (row) => row.noTradeCount, true);
    const mostDataInsufficient = topRankingRow(strategyRows, (row) => row.dataInsufficientCount, true);
    const mostFailedCancelled = topRankingRow(strategyRows, (row) => row.failedCancelledCount, true);

    const totalGroupRuns = strategyRows.reduce((sum, row) => sum + row.runCount, 0);
    const lowSample = totalGroupRuns < 3;

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* 1) 榜单概览（footer 为对应 strategyVersionId） */}
            <div className="nq-status-strip">
                <NqMetricCard
                    label="收益最高"
                    value={topReturn ? <NqPercentText value={topReturn.totalReturn as string | number} ratio colorBySign/> : '-'}
                    footer={topReturn ? topReturn.key : '暂无可比数据'}
                    tone={topReturn ? pnlTone(toNullableNumber(topReturn.totalReturn)) : 'muted'}
                />
                <NqMetricCard
                    label="风险调整后最高"
                    value={topScore ? <span className="nq-num">{formatNqNumber(topScore.score as number, {precision: 4, signed: true})}</span> : '-'}
                    footer={topScore ? topScore.key : '暂无可比数据'}
                />
                <NqMetricCard
                    label="回撤最大"
                    value={worstDrawdown ? <NqPercentText value={worstDrawdown.worstDrawdown as string | number} ratio signed={false}/> : '-'}
                    footer={worstDrawdown ? worstDrawdown.key : '暂无可比数据'}
                    tone="warning"
                />
                <ClickableMetricCard
                    ariaLabel="过滤仅有风控拦截的策略"
                    testId="ranking-filter-card-risk-blocked"
                    isActive={rankFilter === 'riskBlocked'}
                    onClick={() => handleRankingCardClick('riskBlocked')}
                >
                    <NqMetricCard
                        label="风控拦截最多"
                        value={mostRiskBlocked ? String(mostRiskBlocked.riskBlockedCount) : '0'}
                        footer={mostRiskBlocked && mostRiskBlocked.riskBlockedCount > 0 ? mostRiskBlocked.key : '暂无风控拦截'}
                        tone={mostRiskBlocked && mostRiskBlocked.riskBlockedCount > 0 ? 'danger' : 'muted'}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="过滤仅无订单的策略"
                    testId="ranking-filter-card-no-order"
                    isActive={rankFilter === 'noOrder'}
                    onClick={() => handleRankingCardClick('noOrder')}
                >
                    <NqMetricCard
                        label="无交易最多"
                        value={mostNoTrade ? String(mostNoTrade.noTradeCount) : '0'}
                        footer={mostNoTrade && mostNoTrade.noTradeCount > 0 ? mostNoTrade.key : '暂无无交易'}
                        tone={mostNoTrade && mostNoTrade.noTradeCount > 0 ? 'warning' : 'muted'}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="过滤仅数据不足的策略"
                    testId="ranking-filter-card-data-insufficient"
                    isActive={rankFilter === 'dataInsufficient'}
                    onClick={() => handleRankingCardClick('dataInsufficient')}
                >
                    <NqMetricCard
                        label="数据不足最多"
                        value={mostDataInsufficient ? String(mostDataInsufficient.dataInsufficientCount) : '0'}
                        footer={mostDataInsufficient && mostDataInsufficient.dataInsufficientCount > 0 ? mostDataInsufficient.key : '暂无数据不足'}
                        tone={mostDataInsufficient && mostDataInsufficient.dataInsufficientCount > 0 ? 'warning' : 'muted'}
                    />
                </ClickableMetricCard>
                <ClickableMetricCard
                    ariaLabel="过滤仅异常终态的策略"
                    testId="ranking-filter-card-abnormal-terminal"
                    isActive={rankFilter === 'abnormalTerminal'}
                    onClick={() => handleRankingCardClick('abnormalTerminal')}
                >
                    <NqMetricCard
                        label="异常终态最多"
                        value={mostFailedCancelled ? String(mostFailedCancelled.failedCancelledCount) : '0'}
                        footer={mostFailedCancelled && mostFailedCancelled.failedCancelledCount > 0 ? mostFailedCancelled.key : '暂无异常终态'}
                        tone={mostFailedCancelled && mostFailedCancelled.failedCancelledCount > 0 ? 'danger' : 'muted'}
                    />
                </ClickableMetricCard>
            </div>
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                风险调整分为 Paper 内部排序分，仅用于模拟结果横向比较，不代表真实投资评级。
                {lowSample ? ' 当前可比 run 数较少，排行仅供参考。' : ''}
            </Typography.Text>

            {/* 1.5) 排行控件：排序维度 / 方向 / 数据过滤（同一套控件同时作用于 Strategy / Publish 两张表） */}
            <div
                role="group"
                aria-label="策略排行排序控制"
                style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
            >
                <Typography.Text type="secondary" style={{fontSize: 12}}>排序维度</Typography.Text>
                <Select<RankingSortDim>
                    size="small"
                    value={sortDim}
                    onChange={setSortDim}
                    options={RANKING_SORT_OPTIONS as Array<{label: string; value: RankingSortDim}>}
                    style={{width: 132}}
                    virtual={false}
                />
                <Segmented
                    size="small"
                    value={sortDir}
                    onChange={(v) => setSortDir(v as RankingSortDir)}
                    options={[{label: '降序', value: 'desc'}, {label: '升序', value: 'asc'}]}
                />
                <Typography.Text type="secondary" style={{fontSize: 12}}>数据过滤</Typography.Text>
                <Select<RankingFilter>
                    size="small"
                    value={rankFilter}
                    onChange={setRankFilter}
                    options={RANKING_FILTER_OPTIONS as Array<{label: string; value: RankingFilter}>}
                    style={{width: 160}}
                    virtual={false}
                />
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    当前：{sortDimLabel} · {sortDirLabel}
                    {filtered ? ` · ${rankFilterLabel}（命中 Strategy ${strategyRowsView.length} · Publish ${publishRowsView.length}）` : ''}
                </Typography.Text>
            </div>

            {/* 2) Strategy Version 排行表（受排行控件控制） */}
            <Card size="small" title={`Strategy Version 排行（按${sortDimLabel}·${sortDirLabel}）`}>
                <div role="region" aria-label="策略版本排行表">
                    <NqDataTable<PaperStrategyRankingRow>
                        rowKey="key"
                        pagination={false}
                        dataSource={strategyRowsView}
                        columns={rankingColumns('策略版本')}
                        scroll={{x: 1610, y: 280}}
                        locale={{emptyText: filtered ? filterEmptyText : '暂无可分组的策略版本数据。'}}
                    />
                </div>
            </Card>

            {/* 3) Publish 排行表（受同一套排行控件控制） */}
            <Card size="small" title={`Publish 排行（按${sortDimLabel}·${sortDirLabel}）`}>
                <div role="region" aria-label="发布排行表">
                    <NqDataTable<PaperStrategyRankingRow>
                        rowKey="key"
                        pagination={false}
                        dataSource={publishRowsView}
                        columns={rankingColumns('发布')}
                        scroll={{x: 1610, y: 280}}
                        locale={{emptyText: filtered ? filterEmptyText : '暂无可分组的发布数据。'}}
                    />
                </div>
            </Card>

            {/* 4) 数据质量提示 */}
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                数据质量：累计收益率 / 最大回撤缺失的策略不参与风险调整排序（显示「数据不足」）；
                缺 equity / 缺 publish / 缺 backtest 来源详见上方风险驾驶舱与组合看板的数据质量提示。
            </Typography.Text>
        </Space>
    );
}

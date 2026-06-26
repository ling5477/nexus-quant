import {Button, Card, Descriptions, Segmented, Select, Space, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';

import {
    NqDataTable,
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    NqMetricCard,
    NqPercentText,
    NqRiskBanner,
    NqStatusTag,
    nqNumericColumn,
} from '@/components/nq';
import type {NqStatusTone} from '@/components/nq';
import {usePaperStrategyEvaluationsQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {
    PaperBacktestDeviationLevel,
    PaperPublishEvaluationItem,
    PaperStrategyEvaluationConfidence,
    PaperStrategyEvaluationItem,
    PaperStrategyEvaluationsResponse,
    PaperStrategyRatingLabel,
} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

import {toNullableNumber} from './paperFormatters';

// ---- GateK K3B：Paper 策略评估展示映射、筛选与排序（消费 K3 endpoint，纯前端只读展示）----

export const RATING_LABEL_TEXT: Record<PaperStrategyRatingLabel, string> = {
    STRONG_PAPER_PERFORMER: '稳健表现',
    WATCHLIST: '观察',
    HIGH_RISK: '高风险',
    SAMPLE_INSUFFICIENT: '样本不足',
    DATA_INSUFFICIENT: '数据不足',
    EXECUTION_PROBLEM: '执行问题',
    UNKNOWN: '未知',
};

export const RATING_LABEL_TONE: Record<PaperStrategyRatingLabel, NqStatusTone> = {
    STRONG_PAPER_PERFORMER: 'success',
    WATCHLIST: 'info',
    HIGH_RISK: 'danger',
    SAMPLE_INSUFFICIENT: 'warning',
    DATA_INSUFFICIENT: 'warning',
    EXECUTION_PROBLEM: 'warning',
    UNKNOWN: 'neutral',
};

export const EVAL_CONFIDENCE_TONE: Record<PaperStrategyEvaluationConfidence, NqStatusTone> = {
    HIGH: 'success',
    MEDIUM: 'info',
    LOW: 'neutral',
};

const DEVIATION_LEVEL_TONE: Record<PaperBacktestDeviationLevel, NqStatusTone> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    UNAVAILABLE: 'neutral',
};

type EvalRatingFilter = 'all' | PaperStrategyRatingLabel;
type EvalConfidenceFilter = 'all' | PaperStrategyEvaluationConfidence;
type EvalDeviationFilter = 'all' | PaperBacktestDeviationLevel;
type EvalSortDim =
    'compositeScore' | 'totalReturn' | 'maxDrawdown' | 'winRate'
    | 'sampleScore' | 'riskScore' | 'executionScore' | 'backtestDeviationScore' | 'latestRunTime';
type EvalSortDir = 'desc' | 'asc';

const EVAL_RATING_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalRatingFilter}> = [
    {label: '全部评级', value: 'all'},
    {label: '稳健表现 STRONG_PAPER_PERFORMER', value: 'STRONG_PAPER_PERFORMER'},
    {label: '观察 WATCHLIST', value: 'WATCHLIST'},
    {label: '高风险 HIGH_RISK', value: 'HIGH_RISK'},
    {label: '样本不足 SAMPLE_INSUFFICIENT', value: 'SAMPLE_INSUFFICIENT'},
    {label: '数据不足 DATA_INSUFFICIENT', value: 'DATA_INSUFFICIENT'},
    {label: '执行问题 EXECUTION_PROBLEM', value: 'EXECUTION_PROBLEM'},
    {label: '未知 UNKNOWN', value: 'UNKNOWN'},
];

const EVAL_CONFIDENCE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalConfidenceFilter}> = [
    {label: '全部可信度', value: 'all'},
    {label: 'HIGH', value: 'HIGH'},
    {label: 'MEDIUM', value: 'MEDIUM'},
    {label: 'LOW', value: 'LOW'},
];

const EVAL_DEVIATION_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalDeviationFilter}> = [
    {label: '全部偏差', value: 'all'},
    {label: 'LOW', value: 'LOW'},
    {label: 'MEDIUM', value: 'MEDIUM'},
    {label: 'HIGH', value: 'HIGH'},
    {label: 'UNAVAILABLE', value: 'UNAVAILABLE'},
];

const EVAL_SORT_OPTIONS: ReadonlyArray<{label: string; value: EvalSortDim}> = [
    {label: '综合分', value: 'compositeScore'},
    {label: '收益率', value: 'totalReturn'},
    {label: '最大回撤', value: 'maxDrawdown'},
    {label: '胜率', value: 'winRate'},
    {label: '样本分', value: 'sampleScore'},
    {label: '风险分', value: 'riskScore'},
    {label: '执行分', value: 'executionScore'},
    {label: 'Backtest 偏差分', value: 'backtestDeviationScore'},
    {label: '最近运行', value: 'latestRunTime'},
];

function ratingTag(rating: PaperStrategyRatingLabel) {
    return <NqStatusTag status={RATING_LABEL_TEXT[rating] ?? rating} tone={RATING_LABEL_TONE[rating] ?? 'neutral'}/>;
}

/** 取评估行某排序维度的数值；不可比 / 缺失返回 null（恒排末尾，不伪造）。 */
function evalSortValue(row: PaperStrategyEvaluationItem, dim: EvalSortDim): number | null {
    switch (dim) {
        case 'compositeScore': return row.compositeScore;
        case 'sampleScore': return row.sampleScore;
        case 'riskScore': return row.riskScore;
        case 'executionScore': return row.executionScore;
        case 'backtestDeviationScore': return row.backtestDeviationScore;
        case 'totalReturn': return toNullableNumber(row.totalReturn);
        case 'maxDrawdown': return toNullableNumber(row.maxDrawdown);
        case 'winRate': return toNullableNumber(row.winRate);
        case 'latestRunTime': return row.latestRunTime ? Date.parse(row.latestRunTime) : null;
        default: return null;
    }
}

/** 排序：非空按方向排序，null 恒排末尾。 */
function sortStrategyEvals(rows: PaperStrategyEvaluationItem[], dim: EvalSortDim, dir: EvalSortDir): PaperStrategyEvaluationItem[] {
    const decorated = rows.map((r) => ({r, v: evalSortValue(r, dim)}));
    const nonNull = decorated.filter((x) => x.v !== null) as Array<{r: PaperStrategyEvaluationItem; v: number}>;
    const nulls = decorated.filter((x) => x.v === null);
    nonNull.sort((a, b) => (dir === 'desc' ? b.v - a.v : a.v - b.v));
    return [...nonNull.map((x) => x.r), ...nulls.map((x) => x.r)];
}

/** 分数单元：可空分数（如 backtestDeviationScore）缺失时显示「数据不足」，不伪造 0。 */
function scoreCell(score: number | null) {
    if (score === null || score === undefined) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>数据不足</Typography.Text>;
    }
    return <span className="nq-num">{score}</span>;
}

/**
 * PaperStrategyEvaluationDashboard —— Paper 策略评估（GateK Batch K3B）。
 * 消费 K3 只读 endpoint /paper-trading/strategy-evaluations，把 strategy / publish 评分、ratingLabel、warnings、
 * Paper-vs-Backtest 偏差、compositeScore 展示出来，让用户从「策略排行」升级为「策略评估」。
 * 独立 query：加载 / 错误 / 空 / 兼容回退均限定本区域，不连累其他模块。评分为 Paper 内部启发式分、非真实投资评级、不构成投资建议。
 */
export function PaperStrategyEvaluationDashboard({query}: {query: ReturnType<typeof usePaperStrategyEvaluationsQuery>}) {
    const raw = query.data;
    const evaluation: PaperStrategyEvaluationsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperStrategyEvaluationsResponse).overview
            ? (raw as PaperStrategyEvaluationsResponse)
            : null;

    return (
      <section aria-label="Paper 策略评估">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 策略评估"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · Internal evaluation</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    基于 Paper 表现、执行质量、样本充足性与 Backtest 偏差的内部评估。
                </Typography.Text>
                <NqRiskBanner
                    level="info"
                    message="从 strategyVersionId / publishId 维度评估 Paper 模拟表现、Paper vs Backtest 偏差、样本充足性与风险调整评分。"
                    description="该评分是 Paper 内部启发式评估，不是真实投资评级。该结果不代表 LIVE 或真实交易表现，也不构成投资建议。Backtest 偏差用于比较 Paper 与回测表现差异，缺失数据不会被伪造。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 策略评估加载失败"
                        error={query.error as AppApiError}
                        description="策略评估不可用（旧后端可能尚未提供该接口）；其余 Paper 模块不受影响。"
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !evaluation ? (
                    <NqLoadingState message="加载 Paper 策略评估中..."/>
                ) : !evaluation ? (
                    <NqEmptyState description="暂无 Paper 策略评估数据（接口未返回评估结构）。"/>
                ) : evaluation.overview.strategyCount === 0 ? (
                    <NqEmptyState description="暂无 Paper 策略评估数据，创建并运行 Paper run 后自动生成策略评估。"/>
                ) : (
                    <PaperStrategyEvaluationBody evaluation={evaluation}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperStrategyEvaluationBody({evaluation}: {evaluation: PaperStrategyEvaluationsResponse}) {
    const {overview, strategyEvaluations, publishEvaluations, rankings} = evaluation;

    const [ratingFilter, setRatingFilter] = useState<EvalRatingFilter>('all');
    const [confidenceFilter, setConfidenceFilter] = useState<EvalConfidenceFilter>('all');
    const [deviationFilter, setDeviationFilter] = useState<EvalDeviationFilter>('all');
    const [sortDim, setSortDim] = useState<EvalSortDim>('compositeScore');
    const [sortDir, setSortDir] = useState<EvalSortDir>('desc');

    // 筛选只作用于 Strategy Evaluation 表；deviation 以 backtestDeviation.deviationLevel（缺失视为 UNAVAILABLE）为准。
    const filteredStrategies = strategyEvaluations.filter((s) => {
        const level: PaperBacktestDeviationLevel = s.backtestDeviation?.deviationLevel ?? 'UNAVAILABLE';
        return (ratingFilter === 'all' || s.ratingLabel === ratingFilter)
            && (confidenceFilter === 'all' || s.evaluationConfidence === confidenceFilter)
            && (deviationFilter === 'all' || level === deviationFilter);
    });
    const strategyRowsView = sortStrategyEvals(filteredStrategies, sortDim, sortDir);
    const filtered = ratingFilter !== 'all' || confidenceFilter !== 'all' || deviationFilter !== 'all';
    const sortDimLabel = EVAL_SORT_OPTIONS.find((o) => o.value === sortDim)?.label ?? '综合分';

    const subScoreColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        nqNumericColumn({title: '样本分', dataIndex: 'sampleScore', key: 'sampleScore', width: 80}),
        nqNumericColumn({title: '收益分', dataIndex: 'returnScore', key: 'returnScore', width: 80}),
        nqNumericColumn({title: '风险分', dataIndex: 'riskScore', key: 'riskScore', width: 80}),
        nqNumericColumn({title: '执行分', dataIndex: 'executionScore', key: 'executionScore', width: 80}),
        nqNumericColumn({title: 'Backtest 偏差分', key: 'backtestDeviationScore', width: 130,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => scoreCell(r.backtestDeviationScore)}),
    ];

    const strategyColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run', dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({title: '可比', dataIndex: 'comparableRunCount', key: 'comparableRunCount', width: 70}),
        nqNumericColumn({title: '发布', dataIndex: 'publishCount', key: 'publishCount', width: 70}),
        nqNumericColumn({title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '胜率', key: 'winRate', width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.winRate != null
                ? <NqPercentText value={r.winRate as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        ...subScoreColumns,
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => ratingTag(r.ratingLabel)},
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}/>},
        {title: '主要短板', dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: '警告', key: 'warnings', width: 220,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '最近运行', dataIndex: 'latestRunTime', key: 'latestRunTime', width: 170, render: (v: string | null) => v ? formatDateTime(v) : '-'},
    ];

    const publishColumns: ColumnsType<PaperPublishEvaluationItem> = [
        {title: '发布', dataIndex: 'publishId', key: 'publishId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 150, render: (v: string | null) => v ? <span className="nq-mono">{v}</span> : '-'},
        nqNumericColumn({title: 'Run', dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({title: '可比', dataIndex: 'comparableRunCount', key: 'comparableRunCount', width: 70}),
        nqNumericColumn({title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '胜率', key: 'winRate', width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.winRate != null
                ? <NqPercentText value={r.winRate as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        nqNumericColumn({title: '样本分', dataIndex: 'sampleScore', key: 'sampleScore', width: 80}),
        nqNumericColumn({title: '风险分', dataIndex: 'riskScore', key: 'riskScore', width: 80}),
        nqNumericColumn({title: '执行分', dataIndex: 'executionScore', key: 'executionScore', width: 80}),
        nqNumericColumn({title: 'Backtest 偏差分', key: 'backtestDeviationScore', width: 130,
            render: (_: unknown, r: PaperPublishEvaluationItem) => scoreCell(r.backtestDeviationScore)}),
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => ratingTag(r.ratingLabel)},
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}/>},
        {
            title: '警告', key: 'warnings', width: 200,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '最近运行', dataIndex: 'latestRunTime', key: 'latestRunTime', width: 170, render: (v: string | null) => v ? formatDateTime(v) : '-'},
    ];

    // Paper vs Backtest 偏差表：每个策略一行；无 backtest 时 level=UNAVAILABLE、数值显示「-」。
    const deviationColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Backtest 收益', key: 'backtestReturn', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.backtestReturn != null
                ? <NqPercentText value={r.backtestDeviation.backtestReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: 'Paper 收益', key: 'paperReturn', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.paperReturn != null
                ? <NqPercentText value={r.backtestDeviation.paperReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: '收益偏差', key: 'returnDeviation', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.returnDeviation != null
                ? <NqPercentText value={r.backtestDeviation.returnDeviation as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: 'Backtest 回撤', key: 'backtestMaxDrawdown', width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.backtestMaxDrawdown != null
                ? <NqPercentText value={r.backtestDeviation.backtestMaxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: 'Paper 回撤', key: 'paperMaxDrawdown', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.paperMaxDrawdown != null
                ? <NqPercentText value={r.backtestDeviation.paperMaxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: '回撤偏差', key: 'drawdownDeviation', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.drawdownDeviation != null
                ? <NqPercentText value={r.backtestDeviation.drawdownDeviation as string | number} ratio colorBySign/> : '-'}),
        {title: '偏差等级', key: 'deviationLevel', width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => {
                const level: PaperBacktestDeviationLevel = r.backtestDeviation?.deviationLevel ?? 'UNAVAILABLE';
                return <NqStatusTag status={level} tone={DEVIATION_LEVEL_TONE[level]}/>;
            }},
        {title: '说明', key: 'deviationExplanation', width: 320,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => (
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {r.backtestDeviation?.deviationExplanation ?? 'Backtest 不可用，无法计算偏差。'}
                </Typography.Text>
            )},
    ];

    const rankingItems: Array<{label: string; keys: string[]}> = [
        {label: '综合分最高', keys: rankings.topCompositeStrategies},
        {label: '综合分最低', keys: rankings.worstCompositeStrategies},
        {label: '收益最高', keys: rankings.topReturnStrategies},
        {label: '回撤最大', keys: rankings.worstDrawdownStrategies},
        {label: '样本不足', keys: rankings.sampleInsufficientStrategies},
        {label: '高偏差', keys: rankings.highDeviationStrategies},
        {label: '高风险', keys: rankings.highRiskStrategies},
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 评估总览 */}
            <div className="nq-status-strip">
                <NqMetricCard label="策略数" value={String(overview.strategyCount)} footer="strategyVersionId"/>
                <NqMetricCard label="发布数" value={String(overview.publishCount)}/>
                <NqMetricCard label="纳入评估 run" value={String(overview.evaluatedRunCount)}/>
                <NqMetricCard label="可比 run" value={String(overview.comparableRunCount)}/>
                <NqMetricCard label="样本不足策略" value={String(overview.sampleInsufficientStrategyCount)} tone={overview.sampleInsufficientStrategyCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="盈利策略" value={String(overview.profitableStrategyCount)} tone={overview.profitableStrategyCount > 0 ? 'success' : 'muted'}/>
                <NqMetricCard label="亏损策略" value={String(overview.lossStrategyCount)} tone={overview.lossStrategyCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="高风险策略" value={String(overview.highRiskStrategyCount)} tone={overview.highRiskStrategyCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="高偏差策略" value={String(overview.backtestDeviationStrategyCount)} tone={overview.backtestDeviationStrategyCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="最高综合分" value={overview.topCompositeScore != null ? String(overview.topCompositeScore) : '-'}/>
                <NqMetricCard label="最低综合分" value={overview.worstCompositeScore != null ? String(overview.worstCompositeScore) : '-'}/>
            </div>

            {/* B) Strategy Evaluation 表（受评级 / 可信度 / 偏差筛选 + 排序控件） */}
            <Card
                size="small"
                title={filtered
                    ? `策略评估 · 当前筛选命中 ${strategyRowsView.length} 条`
                    : 'Strategy Version 策略评估'}
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setRatingFilter('all'); setConfidenceFilter('all'); setDeviationFilter('all');}}>查看全部</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label="Paper 策略评估筛选"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>评级</Typography.Text>
                        <Select<EvalRatingFilter>
                            size="small" value={ratingFilter} onChange={setRatingFilter}
                            options={EVAL_RATING_FILTER_OPTIONS as Array<{label: string; value: EvalRatingFilter}>}
                            style={{width: 240}} virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>可信度</Typography.Text>
                        <Select<EvalConfidenceFilter>
                            size="small" value={confidenceFilter} onChange={setConfidenceFilter}
                            options={EVAL_CONFIDENCE_FILTER_OPTIONS as Array<{label: string; value: EvalConfidenceFilter}>}
                            style={{width: 140}} virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>Backtest 偏差</Typography.Text>
                        <Select<EvalDeviationFilter>
                            size="small" value={deviationFilter} onChange={setDeviationFilter}
                            options={EVAL_DEVIATION_FILTER_OPTIONS as Array<{label: string; value: EvalDeviationFilter}>}
                            style={{width: 150}} virtual={false}
                        />
                    </div>
                    <div
                        role="group"
                        aria-label="Paper 策略评估排序"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>排序维度</Typography.Text>
                        <Select<EvalSortDim>
                            size="small" value={sortDim} onChange={setSortDim}
                            options={EVAL_SORT_OPTIONS as Array<{label: string; value: EvalSortDim}>}
                            style={{width: 160}} virtual={false}
                        />
                        <Segmented
                            size="small" value={sortDir}
                            onChange={(v) => setSortDir(v as EvalSortDir)}
                            options={[{label: '降序', value: 'desc'}, {label: '升序', value: 'asc'}]}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            当前：{sortDimLabel} · {sortDir === 'desc' ? '降序' : '升序'} · 命中 {strategyRowsView.length} / {strategyEvaluations.length}（null 分恒排末尾）
                        </Typography.Text>
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        综合分为 0~100 Paper 内部启发式分（样本/收益/风险/执行/Backtest 偏差加权）；缺 Backtest 偏差分时显示「数据不足」，不伪造 0。
                    </Typography.Text>
                    <div role="region" aria-label="Paper 策略评估表">
                        <NqDataTable<PaperStrategyEvaluationItem>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyRowsView}
                            columns={strategyColumns}
                            scroll={{x: 1980, y: 320}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的策略评估。'}}
                        />
                    </div>
                </Space>
            </Card>

            {/* C) Publish Evaluation 表 */}
            <Card size="small" title="Publish 策略评估">
                <div role="region" aria-label="Paper 发布评估表">
                    <NqDataTable<PaperPublishEvaluationItem>
                        rowKey="publishId"
                        pagination={false}
                        dataSource={publishEvaluations}
                        columns={publishColumns}
                        scroll={{x: 1760, y: 280}}
                        locale={{emptyText: '暂无可聚合的发布评估。'}}
                    />
                </div>
            </Card>

            {/* D) Paper vs Backtest 偏差表 */}
            <Card size="small" title="Paper vs Backtest 偏差">
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        Backtest 偏差用于比较 Paper 与回测表现差异，缺失数据显示 UNAVAILABLE，不会被伪造。
                    </Typography.Text>
                    <div role="region" aria-label="Paper 回测偏差表">
                        <NqDataTable<PaperStrategyEvaluationItem>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyEvaluations}
                            columns={deviationColumns}
                            scroll={{x: 1310, y: 260}}
                            locale={{emptyText: '暂无可对照的 Backtest 偏差。'}}
                        />
                    </div>
                </Space>
            </Card>

            {/* E) Rankings */}
            <Card size="small" title="策略评估榜单">
                <div role="region" aria-label="Paper 策略评估榜单">
                    <Descriptions bordered size="small" column={1}>
                        {rankingItems.map((item) => (
                            <Descriptions.Item key={item.label} label={item.label}>
                                {item.keys.length > 0 ? (
                                    <Space size={4} wrap>{item.keys.map((k) => <Tag key={k} className="nq-mono">{k}</Tag>)}</Space>
                                ) : (
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>无</Typography.Text>
                                )}
                            </Descriptions.Item>
                        ))}
                    </Descriptions>
                </div>
            </Card>

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                该评分是 Paper 内部启发式评估，不是真实投资评级；不代表 LIVE 或真实交易表现，也不构成投资建议。
            </Typography.Text>
        </Space>
    );
}

import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
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
    get STRONG_PAPER_PERFORMER() { return t('pages:strongPerformance'); },
    get WATCHLIST() { return t('pages:watchlist'); },
    get HIGH_RISK() { return t('pages:highRisk'); },
    get SAMPLE_INSUFFICIENT() { return t('pages:insufficientSamples'); },
    get DATA_INSUFFICIENT() { return t('pages:insufficientData'); },
    get EXECUTION_PROBLEM() { return t('pages:executionProblems'); },
    get UNKNOWN() { return t('pages:unknown'); },
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
    {get label() { return t('pages:allRatings'); }, value: 'all'},
    {get label() { return t('pages:strongPerformanceStrongPaperPerformer'); }, value: 'STRONG_PAPER_PERFORMER'},
    {get label() { return t('pages:watchlistWatchlist'); }, value: 'WATCHLIST'},
    {get label() { return t('pages:highRiskHighRisk'); }, value: 'HIGH_RISK'},
    {get label() { return t('pages:insufficientSamplesSampleInsufficient'); }, value: 'SAMPLE_INSUFFICIENT'},
    {get label() { return t('pages:insufficientDataDataInsufficient'); }, value: 'DATA_INSUFFICIENT'},
    {get label() { return t('pages:executionProblemExecutionProblem'); }, value: 'EXECUTION_PROBLEM'},
    {get label() { return t('pages:unknownUnknown'); }, value: 'UNKNOWN'},
];

const EVAL_CONFIDENCE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalConfidenceFilter}> = [
    {get label() { return t('pages:allConfidenceLevels'); }, value: 'all'},
    {label: 'HIGH', value: 'HIGH'},
    {label: 'MEDIUM', value: 'MEDIUM'},
    {label: 'LOW', value: 'LOW'},
];

const EVAL_DEVIATION_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalDeviationFilter}> = [
    {get label() { return t('pages:allDeviations'); }, value: 'all'},
    {label: 'LOW', value: 'LOW'},
    {label: 'MEDIUM', value: 'MEDIUM'},
    {label: 'HIGH', value: 'HIGH'},
    {label: 'UNAVAILABLE', value: 'UNAVAILABLE'},
];

const EVAL_SORT_OPTIONS: ReadonlyArray<{label: string; value: EvalSortDim}> = [
    {get label() { return t('pages:overallScore'); }, value: 'compositeScore'},
    {get label() { return t('pages:returnRate'); }, value: 'totalReturn'},
    {get label() { return t('pages:maximumDrawdown'); }, value: 'maxDrawdown'},
    {get label() { return t('pages:winRate'); }, value: 'winRate'},
    {get label() { return t('pages:sampleScore'); }, value: 'sampleScore'},
    {get label() { return t('pages:riskScore'); }, value: 'riskScore'},
    {get label() { return t('pages:executionScore'); }, value: 'executionScore'},
    {get label() { return t('pages:backtestDeviationScore'); }, value: 'backtestDeviationScore'},
    {get label() { return t('pages:latestRun'); }, value: 'latestRunTime'},
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
        return <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:insufficientData')}</Typography.Text>;
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
    useTranslation('pages');
    const raw = query.data;
    const evaluation: PaperStrategyEvaluationsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperStrategyEvaluationsResponse).overview
            ? (raw as PaperStrategyEvaluationsResponse)
            : null;

    return (
      <section aria-label={t('pages:paperStrategyEvaluation')}>
        <Card
            className="page-section"
            bordered={false}
            title={t('pages:paperStrategyEvaluation')}
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:simPaperOnlyInternalEvaluation')}</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {t('pages:internalEvaluationOfPaperPerformanceExecutionQualitySampleSufficiencyAndBacktestDeviation')}</Typography.Text>
                <NqRiskBanner
                    level="info"
                    message={t('pages:evaluatePaperPerformanceDeviationsFromBacktestsSampleSufficiencyAndRiskAdjustedScoresByStrategyversi')}
                    description={t('pages:scoresAreInternalPaperHeuristicsNotInvestmentRatingsLivePerformanceOrInvestmentAdviceBacktestDeviati')}
                />
                {query.error ? (
                    <NqErrorState
                        title={t('pages:failedToLoadPaperStrategyEvaluations')}
                        error={query.error as AppApiError}
                        description={t('pages:strategyEvaluationIsUnavailableOlderBackendsMayLackThisApiOtherPaperModulesAreUnaffected')}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !evaluation ? (
                    <NqLoadingState message={t('pages:loadingPaperStrategyEvaluations')}/>
                ) : !evaluation ? (
                    <NqEmptyState description={t('pages:noStrategyEvaluationsTheResponseContainsNoEvaluationStructure')}/>
                ) : evaluation.overview.strategyCount === 0 ? (
                    <NqEmptyState description={t('pages:createAndExecutePaperRunsToGenerateStrategyEvaluations')}/>
                ) : (
                    <PaperStrategyEvaluationBody evaluation={evaluation}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperStrategyEvaluationBody({evaluation}: {evaluation: PaperStrategyEvaluationsResponse}) {
    useTranslation('pages');
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
    const sortDimLabel = EVAL_SORT_OPTIONS.find((o) => o.value === sortDim)?.label ?? t('pages:overallScore');

    const subScoreColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        nqNumericColumn({title: t('pages:sampleScore'), dataIndex: 'sampleScore', key: 'sampleScore', width: 80}),
        nqNumericColumn({title: t('pages:returnScore'), dataIndex: 'returnScore', key: 'returnScore', width: 80}),
        nqNumericColumn({title: t('pages:riskScore'), dataIndex: 'riskScore', key: 'riskScore', width: 80}),
        nqNumericColumn({title: t('pages:executionScore'), dataIndex: 'executionScore', key: 'executionScore', width: 80}),
        nqNumericColumn({title: t('pages:backtestDeviationScore'), key: 'backtestDeviationScore', width: 130,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => scoreCell(r.backtestDeviationScore)}),
    ];

    const strategyColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {title: t('pages:strategyVersions'), dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: t('pages:run'), dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({title: t('pages:comparable'), dataIndex: 'comparableRunCount', key: 'comparableRunCount', width: 70}),
        nqNumericColumn({title: t('pages:publish2'), dataIndex: 'publishCount', key: 'publishCount', width: 70}),
        nqNumericColumn({title: t('pages:returnRate'), key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: t('pages:maximumDrawdown'), key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: t('pages:winRate'), key: 'winRate', width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.winRate != null
                ? <NqPercentText value={r.winRate as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: t('pages:overallScore'), key: 'compositeScore', width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        ...subScoreColumns,
        {title: t('pages:rating'), key: 'ratingLabel', width: 110, render: (_: unknown, r) => ratingTag(r.ratingLabel)},
        {title: t('pages:confidence'), key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}/>},
        {title: t('pages:mainWeaknesses'), dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: t('pages:warnings'), key: 'warnings', width: 220,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: t('pages:latestRun'), dataIndex: 'latestRunTime', key: 'latestRunTime', width: 170, render: (v: string | null) => v ? formatDateTime(v) : '-'},
    ];

    const publishColumns: ColumnsType<PaperPublishEvaluationItem> = [
        {title: t('pages:publish2'), dataIndex: 'publishId', key: 'publishId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: t('pages:strategyVersions'), dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 150, render: (v: string | null) => v ? <span className="nq-mono">{v}</span> : '-'},
        nqNumericColumn({title: t('pages:run'), dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({title: t('pages:comparable'), dataIndex: 'comparableRunCount', key: 'comparableRunCount', width: 70}),
        nqNumericColumn({title: t('pages:returnRate'), key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: t('pages:maximumDrawdown'), key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: t('pages:winRate'), key: 'winRate', width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) => r.winRate != null
                ? <NqPercentText value={r.winRate as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: t('pages:overallScore'), key: 'compositeScore', width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        nqNumericColumn({title: t('pages:sampleScore'), dataIndex: 'sampleScore', key: 'sampleScore', width: 80}),
        nqNumericColumn({title: t('pages:riskScore'), dataIndex: 'riskScore', key: 'riskScore', width: 80}),
        nqNumericColumn({title: t('pages:executionScore'), dataIndex: 'executionScore', key: 'executionScore', width: 80}),
        nqNumericColumn({title: t('pages:backtestDeviationScore'), key: 'backtestDeviationScore', width: 130,
            render: (_: unknown, r: PaperPublishEvaluationItem) => scoreCell(r.backtestDeviationScore)}),
        {title: t('pages:rating'), key: 'ratingLabel', width: 110, render: (_: unknown, r) => ratingTag(r.ratingLabel)},
        {title: t('pages:confidence'), key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}/>},
        {
            title: t('pages:warnings'), key: 'warnings', width: 200,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: t('pages:latestRun'), dataIndex: 'latestRunTime', key: 'latestRunTime', width: 170, render: (v: string | null) => v ? formatDateTime(v) : '-'},
    ];

    // Paper vs Backtest 偏差表：每个策略一行；无 backtest 时 level=UNAVAILABLE、数值显示「-」。
    const deviationColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {title: t('pages:strategyVersions'), dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: t('pages:backtestReturn'), key: 'backtestReturn', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.backtestReturn != null
                ? <NqPercentText value={r.backtestDeviation.backtestReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: t('pages:paperReturn'), key: 'paperReturn', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.paperReturn != null
                ? <NqPercentText value={r.backtestDeviation.paperReturn as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: t('pages:returnDeviation'), key: 'returnDeviation', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.returnDeviation != null
                ? <NqPercentText value={r.backtestDeviation.returnDeviation as string | number} ratio colorBySign/> : '-'}),
        nqNumericColumn({title: t('pages:backtestDrawdown'), key: 'backtestMaxDrawdown', width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.backtestMaxDrawdown != null
                ? <NqPercentText value={r.backtestDeviation.backtestMaxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: t('pages:paperDrawdown'), key: 'paperMaxDrawdown', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.paperMaxDrawdown != null
                ? <NqPercentText value={r.backtestDeviation.paperMaxDrawdown as string | number} ratio signed={false}/> : '-'}),
        nqNumericColumn({title: t('pages:drawdownDeviation'), key: 'drawdownDeviation', width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => r.backtestDeviation?.drawdownDeviation != null
                ? <NqPercentText value={r.backtestDeviation.drawdownDeviation as string | number} ratio colorBySign/> : '-'}),
        {title: t('pages:deviationLevel'), key: 'deviationLevel', width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => {
                const level: PaperBacktestDeviationLevel = r.backtestDeviation?.deviationLevel ?? 'UNAVAILABLE';
                return <NqStatusTag status={level} tone={DEVIATION_LEVEL_TONE[level]}/>;
            }},
        {title: t('pages:explanation2'), key: 'deviationExplanation', width: 320,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => (
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {r.backtestDeviation?.deviationExplanation ?? t('pages:backtestUnavailableDeviationCannotBeCalculated')}
                </Typography.Text>
            )},
    ];

    const rankingItems: Array<{label: string; keys: string[]}> = [
        {label: t('pages:highestOverallScore'), keys: rankings.topCompositeStrategies},
        {label: t('pages:lowestOverallScore'), keys: rankings.worstCompositeStrategies},
        {label: t('pages:highestReturn'), keys: rankings.topReturnStrategies},
        {label: t('pages:largestDrawdown'), keys: rankings.worstDrawdownStrategies},
        {label: t('pages:insufficientSamples'), keys: rankings.sampleInsufficientStrategies},
        {label: t('pages:highDeviation'), keys: rankings.highDeviationStrategies},
        {label: t('pages:highRisk'), keys: rankings.highRiskStrategies},
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 评估总览 */}
            <div className="nq-status-strip">
                <NqMetricCard label={t('pages:strategyCount')} value={String(overview.strategyCount)} footer="strategyVersionId"/>
                <NqMetricCard label={t('pages:publishCount')} value={String(overview.publishCount)}/>
                <NqMetricCard label={t('pages:runsIncludedInEvaluation')} value={String(overview.evaluatedRunCount)}/>
                <NqMetricCard label={t('pages:comparableRuns')} value={String(overview.comparableRunCount)}/>
                <NqMetricCard label={t('pages:strategiesWithInsufficientSamples')} value={String(overview.sampleInsufficientStrategyCount)} tone={overview.sampleInsufficientStrategyCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:profitableStrategies')} value={String(overview.profitableStrategyCount)} tone={overview.profitableStrategyCount > 0 ? 'success' : 'muted'}/>
                <NqMetricCard label={t('pages:losingStrategies')} value={String(overview.lossStrategyCount)} tone={overview.lossStrategyCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:highRiskStrategies')} value={String(overview.highRiskStrategyCount)} tone={overview.highRiskStrategyCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label={t('pages:highDeviationStrategies')} value={String(overview.backtestDeviationStrategyCount)} tone={overview.backtestDeviationStrategyCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label={t('pages:highestOverallScore2')} value={overview.topCompositeScore != null ? String(overview.topCompositeScore) : '-'}/>
                <NqMetricCard label={t('pages:lowestOverallScore2')} value={overview.worstCompositeScore != null ? String(overview.worstCompositeScore) : '-'}/>
            </div>

            {/* B) Strategy Evaluation 表（受评级 / 可信度 / 偏差筛选 + 排序控件） */}
            <Card
                size="small"
                title={filtered
                    ? t('pages:strategyEvaluationsValue1MatchingItems', {value1: strategyRowsView.length})
                    : t('pages:strategyVersionEvaluation')}
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setRatingFilter('all'); setConfidenceFilter('all'); setDeviationFilter('all');}}>{t('pages:viewAll')}</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label={t('pages:paperStrategyEvaluationFilters')}
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:rating')}</Typography.Text>
                        <Select<EvalRatingFilter>
                            size="small" value={ratingFilter} onChange={setRatingFilter}
                            options={EVAL_RATING_FILTER_OPTIONS as Array<{label: string; value: EvalRatingFilter}>}
                            style={{width: 240}} virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:confidence')}</Typography.Text>
                        <Select<EvalConfidenceFilter>
                            size="small" value={confidenceFilter} onChange={setConfidenceFilter}
                            options={EVAL_CONFIDENCE_FILTER_OPTIONS as Array<{label: string; value: EvalConfidenceFilter}>}
                            style={{width: 140}} virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:backtestDeviation')}</Typography.Text>
                        <Select<EvalDeviationFilter>
                            size="small" value={deviationFilter} onChange={setDeviationFilter}
                            options={EVAL_DEVIATION_FILTER_OPTIONS as Array<{label: string; value: EvalDeviationFilter}>}
                            style={{width: 150}} virtual={false}
                        />
                    </div>
                    <div
                        role="group"
                        aria-label={t('pages:paperStrategyEvaluationSorting')}
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:sortBy')}</Typography.Text>
                        <Select<EvalSortDim>
                            size="small" value={sortDim} onChange={setSortDim}
                            options={EVAL_SORT_OPTIONS as Array<{label: string; value: EvalSortDim}>}
                            style={{width: 160}} virtual={false}
                        />
                        <Segmented
                            size="small" value={sortDir}
                            onChange={(v) => setSortDir(v as EvalSortDir)}
                            options={[{label: t('pages:descending'), value: 'desc'}, {label: t('pages:ascending'), value: 'asc'}]}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:current')}{sortDimLabel} · {sortDir === 'desc' ? t('pages:descending') : t('pages:ascending')} {t('pages:matches3')}{strategyRowsView.length} / {strategyEvaluations.length}{t('pages:nullScoresAlwaysAppearLast')}</Typography.Text>
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:theOverallScoreIsA0100InternalPaperHeuristicWeightedBySamplesReturnRiskExecutionAndBacktestDeviation')}</Typography.Text>
                    <div role="region" aria-label={t('pages:paperStrategyEvaluationTable')}>
                        <NqDataTable<PaperStrategyEvaluationItem>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyRowsView}
                            columns={strategyColumns}
                            scroll={{x: 1980, y: 320}}
                            locale={{emptyText: t('pages:noStrategyEvaluationsMatchTheseFilters')}}
                        />
                    </div>
                </Space>
            </Card>

            {/* C) Publish Evaluation 表 */}
            <Card size="small" title={t('pages:publishStrategyEvaluation')}>
                <div role="region" aria-label={t('pages:paperPublishEvaluationTable')}>
                    <NqDataTable<PaperPublishEvaluationItem>
                        rowKey="publishId"
                        pagination={false}
                        dataSource={publishEvaluations}
                        columns={publishColumns}
                        scroll={{x: 1760, y: 280}}
                        locale={{emptyText: t('pages:noPublishEvaluationsToAggregate')}}
                    />
                </div>
            </Card>

            {/* D) Paper vs Backtest 偏差表 */}
            <Card size="small" title={t('pages:paperVersusBacktestDeviation')}>
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:backtestDeviationComparesPaperAndBacktestPerformanceMissingDataRemainsUnavailableAndIsNotFabricated')}</Typography.Text>
                    <div role="region" aria-label={t('pages:paperBacktestDeviationTable')}>
                        <NqDataTable<PaperStrategyEvaluationItem>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyEvaluations}
                            columns={deviationColumns}
                            scroll={{x: 1310, y: 260}}
                            locale={{emptyText: t('pages:noComparableBacktestDeviation')}}
                        />
                    </div>
                </Space>
            </Card>

            {/* E) Rankings */}
            <Card size="small" title={t('pages:strategyEvaluationRankings')}>
                <div role="region" aria-label={t('pages:paperStrategyEvaluationRankings')}>
                    <Descriptions bordered size="small" column={1}>
                        {rankingItems.map((item) => (
                            <Descriptions.Item key={item.label} label={item.label}>
                                {item.keys.length > 0 ? (
                                    <Space size={4} wrap>{item.keys.map((k) => <Tag key={k} className="nq-mono">{k}</Tag>)}</Space>
                                ) : (
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:none')}</Typography.Text>
                                )}
                            </Descriptions.Item>
                        ))}
                    </Descriptions>
                </div>
            </Card>

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {t('pages:scoresAreInternalPaperHeuristicsNotInvestmentRatingsLivePerformanceOrInvestmentAdvice')}</Typography.Text>
        </Space>
    );
}

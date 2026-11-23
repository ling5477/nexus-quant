import {paperRuleText} from '@/features/paper-trading/paper-rule-text';
import {PaperRuleSourceDetails} from '@/features/paper-trading/components/PaperRuleSourceDetails';
import {StatusTag, type StatusTone} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {localizedPaperLabel, localizedPaperOptions} from './paperAnalysisOptions';
import {Button, Card, Descriptions, Segmented, Select, Space, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {
    LegacyAnalysisObjectLink,
    useLegacyFilter,
    useResetLegacyFilters,
} from './LegacyAnalysisControls';

import {
    NqDataTable,
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    NqMetricCard,
    NqPercentText,
    ApplicationRiskAlert,
    nqNumericColumn,
} from '@/components/nq';

import type {PaperAnalysisReadState} from '@/features/paper-trading/hooks/useLegacyPaperAnalysis';
import type {AppApiError} from '@/types/api';
import type {
    PaperBacktestDeviationLevel,
    PaperPublishEvaluationItem,
    PaperStrategyEvaluationConfidence,
    PaperStrategyEvaluationItem,
    PaperStrategyEvaluationsResponse,
    PaperStrategyRatingLabel,
} from '@/features/paper-trading/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

import {sortLegacyEvaluations} from '@/features/paper-trading/legacy-analysis';

export const RATING_LABEL_TEXT: Record<PaperStrategyRatingLabel, string> = {
    get STRONG_PAPER_PERFORMER() {
        return t('pages:strongPerformance');
    },
    get WATCHLIST() {
        return t('pages:watchlist');
    },
    get HIGH_RISK() {
        return t('pages:highRisk');
    },
    get SAMPLE_INSUFFICIENT() {
        return t('pages:insufficientSamples');
    },
    get DATA_INSUFFICIENT() {
        return t('pages:insufficientData');
    },
    get EXECUTION_PROBLEM() {
        return t('pages:executionProblems');
    },
    get UNKNOWN() {
        return t('pages:unknown');
    },
};

export const RATING_LABEL_TONE: Record<PaperStrategyRatingLabel, StatusTone> = {
    STRONG_PAPER_PERFORMER: 'success',
    WATCHLIST: 'info',
    HIGH_RISK: 'danger',
    SAMPLE_INSUFFICIENT: 'warning',
    DATA_INSUFFICIENT: 'warning',
    EXECUTION_PROBLEM: 'warning',
    UNKNOWN: 'neutral',
};

export const EVAL_CONFIDENCE_TONE: Record<PaperStrategyEvaluationConfidence, StatusTone> = {
    HIGH: 'success',
    MEDIUM: 'info',
    LOW: 'neutral',
};

const DEVIATION_LEVEL_TONE: Record<PaperBacktestDeviationLevel, StatusTone> = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    UNAVAILABLE: 'neutral',
};

type EvalRatingFilter = 'all' | PaperStrategyRatingLabel;
type EvalConfidenceFilter = 'all' | PaperStrategyEvaluationConfidence;
type EvalDeviationFilter = 'all' | PaperBacktestDeviationLevel;
type EvalSortDim =
    | 'compositeScore'
    | 'totalReturn'
    | 'maxDrawdown'
    | 'winRate'
    | 'sampleScore'
    | 'riskScore'
    | 'executionScore'
    | 'backtestDeviationScore'
    | 'latestRunTime';
type EvalSortDir = 'desc' | 'asc';

const EVAL_RATING_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalRatingFilter}> = [
    {
        get label() {
            return t('pages:allRatings');
        },
        value: 'all',
    },
    {
        get label() {
            return t('pages:strongPerformanceStrongPaperPerformer');
        },
        value: 'STRONG_PAPER_PERFORMER',
    },
    {
        get label() {
            return t('pages:watchlistWatchlist');
        },
        value: 'WATCHLIST',
    },
    {
        get label() {
            return t('pages:highRiskHighRisk');
        },
        value: 'HIGH_RISK',
    },
    {
        get label() {
            return t('pages:insufficientSamplesSampleInsufficient');
        },
        value: 'SAMPLE_INSUFFICIENT',
    },
    {
        get label() {
            return t('pages:insufficientDataDataInsufficient');
        },
        value: 'DATA_INSUFFICIENT',
    },
    {
        get label() {
            return t('pages:executionProblemExecutionProblem');
        },
        value: 'EXECUTION_PROBLEM',
    },
    {
        get label() {
            return t('pages:unknownUnknown');
        },
        value: 'UNKNOWN',
    },
];

const EVAL_CONFIDENCE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalConfidenceFilter}> =
    [
        {
            get label() {
                return t('pages:allConfidenceLevels');
            },
            value: 'all',
        },
        {
            get label() {
                return localizedPaperLabel('HIGH');
            },
            value: 'HIGH',
        },
        {
            get label() {
                return localizedPaperLabel('MEDIUM');
            },
            value: 'MEDIUM',
        },
        {
            get label() {
                return localizedPaperLabel('LOW');
            },
            value: 'LOW',
        },
    ];

const EVAL_DEVIATION_FILTER_OPTIONS: ReadonlyArray<{label: string; value: EvalDeviationFilter}> = [
    {
        get label() {
            return t('pages:allDeviations');
        },
        value: 'all',
    },
    {
        get label() {
            return localizedPaperLabel('LOW');
        },
        value: 'LOW',
    },
    {
        get label() {
            return localizedPaperLabel('MEDIUM');
        },
        value: 'MEDIUM',
    },
    {
        get label() {
            return localizedPaperLabel('HIGH');
        },
        value: 'HIGH',
    },
    {
        get label() {
            return localizedPaperLabel('UNAVAILABLE');
        },
        value: 'UNAVAILABLE',
    },
];

const EVAL_SORT_OPTIONS: ReadonlyArray<{label: string; value: EvalSortDim}> = [
    {
        get label() {
            return t('pages:overallScore');
        },
        value: 'compositeScore',
    },
    {
        get label() {
            return t('pages:returnRate');
        },
        value: 'totalReturn',
    },
    {
        get label() {
            return t('pages:maximumDrawdown');
        },
        value: 'maxDrawdown',
    },
    {
        get label() {
            return t('pages:winRate');
        },
        value: 'winRate',
    },
    {
        get label() {
            return t('pages:sampleScore');
        },
        value: 'sampleScore',
    },
    {
        get label() {
            return t('pages:riskScore');
        },
        value: 'riskScore',
    },
    {
        get label() {
            return t('pages:executionScore');
        },
        value: 'executionScore',
    },
    {
        get label() {
            return t('pages:backtestDeviationScore');
        },
        value: 'backtestDeviationScore',
    },
    {
        get label() {
            return t('pages:latestRun');
        },
        value: 'latestRunTime',
    },
];

function ratingTag(rating: PaperStrategyRatingLabel) {
    return (
        <StatusTag
            title={rating}
            variant="pill"
            status={rating}
            label={RATING_LABEL_TEXT[rating] ?? localizedPaperLabel(rating)}
            tone={RATING_LABEL_TONE[rating] ?? 'neutral'}
        />
    );
}

/** 分数单元：可空分数（如 backtestDeviationScore）缺失时显示「数据不足」，不伪造 0。 */
function scoreCell(score: number | null) {
    if (score === null || score === undefined) {
        return (
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {t('pages:insufficientData')}
            </Typography.Text>
        );
    }
    return <span className="nq-num">{score}</span>;
}

/** 历史Paper同源快照的只读展示；筛选与明细状态保存在URL，规则评分不代表LIVE或投资建议。 */
export function PaperStrategyEvaluationDashboard({
    query,
}: {
    query: PaperAnalysisReadState<PaperStrategyEvaluationsResponse>;
}) {
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
                variant="borderless"
                title={t('pages:paperStrategyEvaluation')}
                extra={
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:simPaperOnlyInternalEvaluation')}
                    </Typography.Text>
                }
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t(
                            'pages:internalEvaluationOfPaperPerformanceExecutionQualitySampleSufficiencyAndBacktestDeviation',
                        )}
                    </Typography.Text>
                    <ApplicationRiskAlert
                        level="info"
                        message={t(
                            'pages:evaluatePaperPerformanceDeviationsFromBacktestsSampleSufficiencyAndRiskAdjustedScoresByStrategyversi',
                        )}
                        description={t(
                            'pages:scoresAreInternalPaperHeuristicsNotInvestmentRatingsLivePerformanceOrInvestmentAdviceBacktestDeviati',
                        )}
                    />
                    {query.error ? (
                        <NqErrorState
                            title={t('pages:failedToLoadPaperStrategyEvaluations')}
                            error={query.error as AppApiError}
                            description={t(
                                'pages:strategyEvaluationIsUnavailableOlderBackendsMayLackThisApiOtherPaperModulesAreUnaffected',
                            )}
                            onRetry={() => query.refetch()}
                        />
                    ) : query.isFetching && !evaluation ? (
                        <NqLoadingState message={t('pages:loadingPaperStrategyEvaluations')} />
                    ) : !evaluation ? (
                        <NqEmptyState
                            description={t(
                                'pages:noStrategyEvaluationsTheResponseContainsNoEvaluationStructure',
                            )}
                        />
                    ) : evaluation.overview.strategyCount === 0 ? (
                        <NqEmptyState
                            description={t(
                                'pages:createAndExecutePaperRunsToGenerateStrategyEvaluations',
                            )}
                        />
                    ) : (
                        <>
                            <PaperStrategyEvaluationBody evaluation={evaluation} />
                            <PaperRuleSourceDetails value={evaluation} />
                        </>
                    )}
                </Space>
            </Card>
        </section>
    );
}

function PaperStrategyEvaluationBody({evaluation}: {evaluation: PaperStrategyEvaluationsResponse}) {
    useTranslation('pages');
    const {overview, strategyEvaluations, publishEvaluations, rankings} = evaluation;

    const [ratingFilter, setRatingFilter] = useLegacyFilter<EvalRatingFilter>(
        'legacyRating',
        'all',
    );
    const [confidenceFilter, setConfidenceFilter] = useLegacyFilter<EvalConfidenceFilter>(
        'legacyConfidence',
        'all',
    );
    const [deviationFilter, setDeviationFilter] = useLegacyFilter<EvalDeviationFilter>(
        'legacyDeviation',
        'all',
    );
    const [sortDim, setSortDim] = useLegacyFilter<EvalSortDim>('legacySort', 'compositeScore');
    const [sortDir, setSortDir] = useLegacyFilter<EvalSortDir>('legacyDirection', 'desc');
    const resetFilters = useResetLegacyFilters([
        'legacyRating',
        'legacyConfidence',
        'legacyDeviation',
    ]);

    // 筛选只作用于 Strategy Evaluation 表；deviation 以 backtestDeviation.deviationLevel（缺失视为 UNAVAILABLE）为准。
    const filteredStrategies = strategyEvaluations.filter((s) => {
        const level: PaperBacktestDeviationLevel =
            s.backtestDeviation?.deviationLevel ?? 'UNAVAILABLE';
        return (
            (ratingFilter === 'all' || s.ratingLabel === ratingFilter) &&
            (confidenceFilter === 'all' || s.evaluationConfidence === confidenceFilter) &&
            (deviationFilter === 'all' || level === deviationFilter)
        );
    });
    const strategyRowsView = sortLegacyEvaluations(filteredStrategies, sortDim, sortDir);
    const filtered =
        ratingFilter !== 'all' || confidenceFilter !== 'all' || deviationFilter !== 'all';
    const sortDimLabel =
        EVAL_SORT_OPTIONS.find((o) => o.value === sortDim)?.label ?? t('pages:overallScore');

    const subScoreColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        nqNumericColumn({
            title: t('pages:sampleScore'),
            dataIndex: 'sampleScore',
            key: 'sampleScore',
            width: 80,
        }),
        nqNumericColumn({
            title: t('pages:returnScore'),
            dataIndex: 'returnScore',
            key: 'returnScore',
            width: 80,
        }),
        nqNumericColumn({
            title: t('pages:riskScore'),
            dataIndex: 'riskScore',
            key: 'riskScore',
            width: 80,
        }),
        nqNumericColumn({
            title: t('pages:executionScore'),
            dataIndex: 'executionScore',
            key: 'executionScore',
            width: 80,
        }),
        nqNumericColumn({
            title: t('pages:backtestDeviationScore'),
            key: 'backtestDeviationScore',
            width: 130,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                scoreCell(r.backtestDeviationScore),
        }),
    ];

    const strategyColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {
            title: t('pages:strategyVersions'),
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 160,
            render: (v: string) => <LegacyAnalysisObjectLink kind="strategy" id={v} />,
        },
        nqNumericColumn({title: t('pages:run'), dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({
            title: t('pages:comparable'),
            dataIndex: 'comparableRunCount',
            key: 'comparableRunCount',
            width: 70,
        }),
        nqNumericColumn({
            title: t('pages:publish2'),
            dataIndex: 'publishCount',
            key: 'publishCount',
            width: 70,
        }),
        nqNumericColumn({
            title: t('pages:returnRate'),
            key: 'totalReturn',
            width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.totalReturn != null ? (
                    <NqPercentText value={r.totalReturn as string | number} ratio colorBySign />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:maximumDrawdown'),
            key: 'maxDrawdown',
            width: 100,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.maxDrawdown != null ? (
                    <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false} />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:winRate'),
            key: 'winRate',
            width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.winRate != null ? (
                    <NqPercentText value={r.winRate as string | number} ratio signed={false} />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:overallScore'),
            key: 'compositeScore',
            width: 90,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => (
                <span className="nq-num">
                    <strong>{r.compositeScore}</strong>
                </span>
            ),
        }),
        ...subScoreColumns,
        {
            title: t('pages:rating'),
            key: 'ratingLabel',
            width: 110,
            render: (_: unknown, r) => ratingTag(r.ratingLabel),
        },
        {
            title: t('pages:confidence'),
            key: 'evaluationConfidence',
            width: 100,
            render: (_: unknown, r) => (
                <StatusTag
                    title={r.evaluationConfidence}
                    variant="pill"
                    status={r.evaluationConfidence}
                    label={localizedPaperLabel(r.evaluationConfidence)}
                    tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}
                />
            ),
        },
        {
            title: t('pages:mainWeaknesses'),
            dataIndex: 'primaryWeakness',
            key: 'primaryWeakness',
            width: 130,
            render: (v: string) => (
                <Typography.Text type="secondary" style={{fontSize: 12}} title={v}>
                    {localizedPaperLabel(v)}
                </Typography.Text>
            ),
        },
        {
            title: t('pages:warnings'),
            key: 'warnings',
            width: 220,
            render: (_: unknown, r) =>
                r.warnings.length > 0 ? (
                    <Space size={4} wrap>
                        {r.warnings.map((w) => (
                            <Tag key={w} color="warning" title={w}>
                                {localizedPaperLabel(w)}
                            </Tag>
                        ))}
                    </Space>
                ) : (
                    <Typography.Text type="secondary">-</Typography.Text>
                ),
        },
        {
            title: t('pages:latestRun'),
            dataIndex: 'latestRunTime',
            key: 'latestRunTime',
            width: 170,
            render: (v: string | null) => (v ? formatDateTime(v) : '-'),
        },
    ];

    const publishColumns: ColumnsType<PaperPublishEvaluationItem> = [
        {
            title: t('pages:publish2'),
            dataIndex: 'publishId',
            key: 'publishId',
            width: 160,
            render: (v: string) => <LegacyAnalysisObjectLink kind="publish" id={v} />,
        },
        {
            title: t('pages:strategyVersions'),
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 150,
            render: (v: string | null) =>
                v ? <LegacyAnalysisObjectLink kind="strategy" id={v} /> : '-',
        },
        nqNumericColumn({title: t('pages:run'), dataIndex: 'runCount', key: 'runCount', width: 70}),
        nqNumericColumn({
            title: t('pages:comparable'),
            dataIndex: 'comparableRunCount',
            key: 'comparableRunCount',
            width: 70,
        }),
        nqNumericColumn({
            title: t('pages:returnRate'),
            key: 'totalReturn',
            width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) =>
                r.totalReturn != null ? (
                    <NqPercentText value={r.totalReturn as string | number} ratio colorBySign />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:maximumDrawdown'),
            key: 'maxDrawdown',
            width: 100,
            render: (_: unknown, r: PaperPublishEvaluationItem) =>
                r.maxDrawdown != null ? (
                    <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false} />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:winRate'),
            key: 'winRate',
            width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) =>
                r.winRate != null ? (
                    <NqPercentText value={r.winRate as string | number} ratio signed={false} />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:overallScore'),
            key: 'compositeScore',
            width: 90,
            render: (_: unknown, r: PaperPublishEvaluationItem) => (
                <span className="nq-num">
                    <strong>{r.compositeScore}</strong>
                </span>
            ),
        }),
        nqNumericColumn({
            title: t('pages:sampleScore'),
            dataIndex: 'sampleScore',
            key: 'sampleScore',
            width: 80,
        }),
        nqNumericColumn({
            title: t('pages:riskScore'),
            dataIndex: 'riskScore',
            key: 'riskScore',
            width: 80,
        }),
        nqNumericColumn({
            title: t('pages:executionScore'),
            dataIndex: 'executionScore',
            key: 'executionScore',
            width: 80,
        }),
        nqNumericColumn({
            title: t('pages:backtestDeviationScore'),
            key: 'backtestDeviationScore',
            width: 130,
            render: (_: unknown, r: PaperPublishEvaluationItem) =>
                scoreCell(r.backtestDeviationScore),
        }),
        {
            title: t('pages:rating'),
            key: 'ratingLabel',
            width: 110,
            render: (_: unknown, r) => ratingTag(r.ratingLabel),
        },
        {
            title: t('pages:confidence'),
            key: 'evaluationConfidence',
            width: 100,
            render: (_: unknown, r) => (
                <StatusTag
                    title={r.evaluationConfidence}
                    variant="pill"
                    status={r.evaluationConfidence}
                    label={localizedPaperLabel(r.evaluationConfidence)}
                    tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence]}
                />
            ),
        },
        {
            title: t('pages:warnings'),
            key: 'warnings',
            width: 200,
            render: (_: unknown, r) =>
                r.warnings.length > 0 ? (
                    <Space size={4} wrap>
                        {r.warnings.map((w) => (
                            <Tag key={w} color="warning" title={w}>
                                {localizedPaperLabel(w)}
                            </Tag>
                        ))}
                    </Space>
                ) : (
                    <Typography.Text type="secondary">-</Typography.Text>
                ),
        },
        {
            title: t('pages:latestRun'),
            dataIndex: 'latestRunTime',
            key: 'latestRunTime',
            width: 170,
            render: (v: string | null) => (v ? formatDateTime(v) : '-'),
        },
    ];

    // Paper vs Backtest 偏差表：每个策略一行；无 backtest 时 level=UNAVAILABLE、数值显示「-」。
    const deviationColumns: ColumnsType<PaperStrategyEvaluationItem> = [
        {
            title: t('pages:strategyVersions'),
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 160,
            render: (v: string) => <LegacyAnalysisObjectLink kind="strategy" id={v} />,
        },
        nqNumericColumn({
            title: t('pages:backtestReturn'),
            key: 'backtestReturn',
            width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.backtestDeviation?.backtestReturn != null ? (
                    <NqPercentText
                        value={r.backtestDeviation.backtestReturn as string | number}
                        ratio
                        colorBySign
                    />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:paperReturn'),
            key: 'paperReturn',
            width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.backtestDeviation?.paperReturn != null ? (
                    <NqPercentText
                        value={r.backtestDeviation.paperReturn as string | number}
                        ratio
                        colorBySign
                    />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:returnDeviation'),
            key: 'returnDeviation',
            width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.backtestDeviation?.returnDeviation != null ? (
                    <NqPercentText
                        value={r.backtestDeviation.returnDeviation as string | number}
                        ratio
                        colorBySign
                    />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:backtestDrawdown'),
            key: 'backtestMaxDrawdown',
            width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.backtestDeviation?.backtestMaxDrawdown != null ? (
                    <NqPercentText
                        value={r.backtestDeviation.backtestMaxDrawdown as string | number}
                        ratio
                        signed={false}
                    />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:paperDrawdown'),
            key: 'paperMaxDrawdown',
            width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.backtestDeviation?.paperMaxDrawdown != null ? (
                    <NqPercentText
                        value={r.backtestDeviation.paperMaxDrawdown as string | number}
                        ratio
                        signed={false}
                    />
                ) : (
                    '-'
                ),
        }),
        nqNumericColumn({
            title: t('pages:drawdownDeviation'),
            key: 'drawdownDeviation',
            width: 110,
            render: (_: unknown, r: PaperStrategyEvaluationItem) =>
                r.backtestDeviation?.drawdownDeviation != null ? (
                    <NqPercentText
                        value={r.backtestDeviation.drawdownDeviation as string | number}
                        ratio
                        colorBySign
                    />
                ) : (
                    '-'
                ),
        }),
        {
            title: t('pages:deviationLevel'),
            key: 'deviationLevel',
            width: 120,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => {
                const level: PaperBacktestDeviationLevel =
                    r.backtestDeviation?.deviationLevel ?? 'UNAVAILABLE';
                return (
                    <StatusTag
                        title={level}
                        variant="pill"
                        status={level}
                        label={localizedPaperLabel(level)}
                        tone={DEVIATION_LEVEL_TONE[level]}
                    />
                );
            },
        },
        {
            title: t('pages:explanation2'),
            key: 'deviationExplanation',
            width: 320,
            render: (_: unknown, r: PaperStrategyEvaluationItem) => (
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {paperRuleText(
                        r.backtestDeviation?.deviationExplanation ??
                            t('pages:backtestUnavailableDeviationCannotBeCalculated'),
                    )}
                </Typography.Text>
            ),
        },
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
                <NqMetricCard
                    label={t('pages:strategyCount')}
                    value={String(overview.strategyCount)}
                    footer="strategyVersionId"
                />
                <NqMetricCard
                    label={t('pages:publishCount')}
                    value={String(overview.publishCount)}
                />
                <NqMetricCard
                    label={t('pages:runsIncludedInEvaluation')}
                    value={String(overview.evaluatedRunCount)}
                />
                <NqMetricCard
                    label={t('pages:comparableRuns')}
                    value={String(overview.comparableRunCount)}
                />
                <NqMetricCard
                    label={t('pages:strategiesWithInsufficientSamples')}
                    value={String(overview.sampleInsufficientStrategyCount)}
                    tone={overview.sampleInsufficientStrategyCount > 0 ? 'warning' : 'muted'}
                />
                <NqMetricCard
                    label={t('pages:profitableStrategies')}
                    value={
                        overview.profitableStrategyCount === null
                            ? t('pages:legacyPortfolio.unitMissingShort')
                            : String(overview.profitableStrategyCount)
                    }
                    tone={(overview.profitableStrategyCount ?? 0) > 0 ? 'success' : 'muted'}
                />
                <NqMetricCard
                    label={t('pages:losingStrategies')}
                    value={
                        overview.lossStrategyCount === null
                            ? t('pages:legacyPortfolio.unitMissingShort')
                            : String(overview.lossStrategyCount)
                    }
                    tone={(overview.lossStrategyCount ?? 0) > 0 ? 'warning' : 'muted'}
                />
                <NqMetricCard
                    label={t('pages:highRiskStrategies')}
                    value={String(overview.highRiskStrategyCount)}
                    tone={overview.highRiskStrategyCount > 0 ? 'danger' : 'muted'}
                />
                <NqMetricCard
                    label={t('pages:highDeviationStrategies')}
                    value={String(overview.backtestDeviationStrategyCount)}
                    tone={overview.backtestDeviationStrategyCount > 0 ? 'danger' : 'muted'}
                />
                <NqMetricCard
                    label={t('pages:highestOverallScore2')}
                    value={
                        overview.topCompositeScore != null
                            ? String(overview.topCompositeScore)
                            : '-'
                    }
                />
                <NqMetricCard
                    label={t('pages:lowestOverallScore2')}
                    value={
                        overview.worstCompositeScore != null
                            ? String(overview.worstCompositeScore)
                            : '-'
                    }
                />
            </div>

            {/* B) Strategy Evaluation 表（受评级 / 可信度 / 偏差筛选 + 排序控件） */}
            <Card
                size="small"
                title={
                    filtered
                        ? t('pages:strategyEvaluationsValue1MatchingItems', {
                              value1: strategyRowsView.length,
                          })
                        : t('pages:strategyVersionEvaluation')
                }
                extra={
                    filtered ? (
                        <Button size="small" type="link" onClick={resetFilters}>
                            {t('pages:viewAll')}
                        </Button>
                    ) : null
                }
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label={t('pages:paperStrategyEvaluationFilters')}
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:rating')}
                        </Typography.Text>
                        <Select<EvalRatingFilter>
                            aria-label={t('pages:rating')}
                            size="small"
                            value={ratingFilter}
                            onChange={setRatingFilter}
                            options={localizedPaperOptions(EVAL_RATING_FILTER_OPTIONS)}
                            style={{width: 240}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:confidence')}
                        </Typography.Text>
                        <Select<EvalConfidenceFilter>
                            aria-label={t('pages:confidence')}
                            size="small"
                            value={confidenceFilter}
                            onChange={setConfidenceFilter}
                            options={localizedPaperOptions(EVAL_CONFIDENCE_FILTER_OPTIONS)}
                            style={{width: 140}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:backtestDeviation')}
                        </Typography.Text>
                        <Select<EvalDeviationFilter>
                            aria-label={t('pages:backtestDeviation')}
                            size="small"
                            value={deviationFilter}
                            onChange={setDeviationFilter}
                            options={localizedPaperOptions(EVAL_DEVIATION_FILTER_OPTIONS)}
                            style={{width: 150}}
                            virtual={false}
                        />
                    </div>
                    <div
                        role="group"
                        aria-label={t('pages:paperStrategyEvaluationSorting')}
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:sortBy')}
                        </Typography.Text>
                        <Select<EvalSortDim>
                            aria-label={t('pages:sortBy')}
                            size="small"
                            value={sortDim}
                            onChange={setSortDim}
                            options={localizedPaperOptions(EVAL_SORT_OPTIONS)}
                            style={{width: 160}}
                            virtual={false}
                        />
                        <Segmented
                            aria-label={t('pages:legacyAnalysis.sortDirection')}
                            size="small"
                            value={sortDir}
                            onChange={(v) => setSortDir(v as EvalSortDir)}
                            options={[
                                {label: t('pages:descending'), value: 'desc'},
                                {label: t('pages:ascending'), value: 'asc'},
                            ]}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>
                            {t('pages:current')}
                            {sortDimLabel} ·{' '}
                            {sortDir === 'desc' ? t('pages:descending') : t('pages:ascending')}{' '}
                            {t('pages:matches3')}
                            {strategyRowsView.length} / {strategyEvaluations.length}
                            {t('pages:nullScoresAlwaysAppearLast')}
                        </Typography.Text>
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t(
                            'pages:theOverallScoreIsA0100InternalPaperHeuristicWeightedBySamplesReturnRiskExecutionAndBacktestDeviation',
                        )}
                    </Typography.Text>
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
                        {t(
                            'pages:backtestDeviationComparesPaperAndBacktestPerformanceMissingDataRemainsUnavailableAndIsNotFabricated',
                        )}
                    </Typography.Text>
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
                                    <Space size={4} wrap>
                                        {item.keys.map((k) => (
                                            <Tag key={k}>
                                                <LegacyAnalysisObjectLink kind="strategy" id={k} />
                                            </Tag>
                                        ))}
                                    </Space>
                                ) : (
                                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                                        {t('pages:none')}
                                    </Typography.Text>
                                )}
                            </Descriptions.Item>
                        ))}
                    </Descriptions>
                </div>
            </Card>

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {t(
                    'pages:scoresAreInternalPaperHeuristicsNotInvestmentRatingsLivePerformanceOrInvestmentAdvice',
                )}
            </Typography.Text>
        </Space>
    );
}

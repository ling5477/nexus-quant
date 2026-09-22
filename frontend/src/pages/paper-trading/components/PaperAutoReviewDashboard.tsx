import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Button, Card, Descriptions, Select, Space, Tag, Typography} from 'antd';
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
import {usePaperAutoReviewsQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {
    PaperAutoReviewSeverity,
    PaperAutoReviewsResponse,
    PaperExecutionCause,
    PaperExecutionCauseConfidence,
    PaperIssueCluster,
    PaperPublishAutoReview,
    PaperRunAutoReview,
    PaperStrategyAutoReview,
    PaperStrategyEvaluationConfidence,
    PaperStrategyRatingLabel,
} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

import {
    EXECUTION_CAUSE_LABEL,
    EXECUTION_CAUSE_TONE,
    EXECUTION_CONFIDENCE_TONE,
    EXECUTION_SEVERITY_TONE,
} from './PaperExecutionDiagnosticsDashboard';
import {EVAL_CONFIDENCE_TONE, RATING_LABEL_TEXT, RATING_LABEL_TONE} from './PaperStrategyEvaluationDashboard';

// ---- GateK K4B：Paper 规则化自动复盘展示映射与筛选（消费 K4 endpoint，纯前端只读展示）----

/** 聚类专属 cause（执行 / 评估维度聚类）补充中文名；run primaryCause 复用执行诊断 cause 映射。 */
const AUTO_REVIEW_EXTRA_CAUSE_LABEL: Record<string, string> = {
    get BACKTEST_DEVIATION_HIGH() { return t('pages:highBacktestDeviation'); },
    get SAMPLE_INSUFFICIENT() { return t('pages:insufficientSamples'); },
};

function autoReviewCauseLabel(cause: string): string {
    return EXECUTION_CAUSE_LABEL[cause as PaperExecutionCause]
        ?? AUTO_REVIEW_EXTRA_CAUSE_LABEL[cause] ?? cause;
}

function autoReviewCauseTone(cause: string): NqStatusTone {
    return EXECUTION_CAUSE_TONE[cause as PaperExecutionCause]
        ?? (cause === 'BACKTEST_DEVIATION_HIGH' ? 'danger' : cause === 'SAMPLE_INSUFFICIENT' ? 'warning' : 'neutral');
}

/** cause 标签（中文名 + 语义色），缺省回退原始枚举值，不伪造。 */
function autoReviewCauseTag(cause: string) {
    return <NqStatusTag status={autoReviewCauseLabel(cause)} tone={autoReviewCauseTone(cause)}/>;
}

/** ratingLabel 标签（复用策略评估评级中文名与语义色）。 */
function autoReviewRatingTag(rating: string) {
    return (
        <NqStatusTag
            status={RATING_LABEL_TEXT[rating as PaperStrategyRatingLabel] ?? rating}
            tone={RATING_LABEL_TONE[rating as PaperStrategyRatingLabel] ?? 'neutral'}
        />
    );
}

type AutoReviewSeverityFilter = 'all' | PaperAutoReviewSeverity;
type AutoReviewDimensionFilter = 'all' | 'run' | 'strategy' | 'publish' | 'cluster';
type AutoReviewCauseFilter = string;

const AUTO_REVIEW_SEVERITY_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewSeverityFilter}> = [
    {get label() { return t('pages:allSeverities'); }, value: 'all'},
    {label: 'CRITICAL', value: 'CRITICAL'},
    {label: 'WARNING', value: 'WARNING'},
    {label: 'INFO', value: 'INFO'},
];

const AUTO_REVIEW_DIMENSION_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewDimensionFilter}> = [
    {get label() { return t('pages:allDimensions'); }, value: 'all'},
    {get label() { return t('pages:run'); }, value: 'run'},
    {get label() { return t('pages:strategy'); }, value: 'strategy'},
    {get label() { return t('pages:publish2'); }, value: 'publish'},
    {get label() { return t('pages:cluster'); }, value: 'cluster'},
];

const AUTO_REVIEW_CAUSE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewCauseFilter}> = [
    {get label() { return t('pages:allReasons'); }, value: 'all'},
    {get label() { return t('pages:noOrdersNoOrder'); }, value: 'NO_ORDER'},
    {get label() { return t('pages:unfilledOrdersOrderNoFill'); }, value: 'ORDER_NO_FILL'},
    {get label() { return t('pages:tradingLossFilledLoss'); }, value: 'FILLED_LOSS'},
    {get label() { return t('pages:riskBlockedRiskBlocked'); }, value: 'RISK_BLOCKED'},
    {get label() { return t('pages:insufficientDataDataInsufficient'); }, value: 'DATA_INSUFFICIENT'},
    {get label() { return t('pages:highDrawdownHighDrawdown'); }, value: 'HIGH_DRAWDOWN'},
    {get label() { return t('pages:failedTerminalStateFailedRun'); }, value: 'FAILED_RUN'},
    {get label() { return t('pages:highBacktestDeviationBacktestDeviationHigh'); }, value: 'BACKTEST_DEVIATION_HIGH'},
    {get label() { return t('pages:insufficientSamplesSampleInsufficient'); }, value: 'SAMPLE_INSUFFICIENT'},
    {get label() { return t('pages:healthyHealthy'); }, value: 'HEALTHY'},
];

/** 字符串清单渲染为 tag 列表；空时显示给定空文案（如 suggestedActions 的「暂无建议动作」）。 */
function autoReviewTagList(items: string[] | undefined, emptyText: string, color?: string) {
    if (!items || items.length === 0) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>{emptyText}</Typography.Text>;
    }
    return <Space size={4} wrap>{items.map((t, i) => <Tag key={`${t}-${i}`} color={color}>{t}</Tag>)}</Space>;
}

/** 字符串清单渲染为紧凑 bullet 列表；空时显示给定空文案。 */
function autoReviewBullets(items: string[] | undefined, emptyText: string) {
    if (!items || items.length === 0) {
        return <Typography.Text type="secondary" style={{fontSize: 12}}>{emptyText}</Typography.Text>;
    }
    return (
        <ul style={{margin: 0, paddingLeft: 18}}>
            {items.map((t, i) => <li key={`${t}-${i}`}><Typography.Text style={{fontSize: 12}}>{t}</Typography.Text></li>)}
        </ul>
    );
}

/**
 * PaperAutoReviewDashboard —— Paper 规则化自动复盘（GateK Batch K4B）。
 * 消费 K4 只读 endpoint /paper-trading/auto-reviews，把组合复盘、重点 run 复盘、策略 / 发布复盘与问题聚类展示出来，
 * 让用户从「诊断 + 评分」升级为「可读复盘」。复盘由规则引擎生成，不接 AI / DH runtime。
 * 独立 query：加载 / 错误 / 空 / 兼容回退均限定本区域，不连累组合看板、诊断、评估与排行。
 * 仅 Paper-only 规则化复盘，不代表 LIVE 或真实交易表现，也不构成投资建议。
 */
export function PaperAutoReviewDashboard({query}: {query: ReturnType<typeof usePaperAutoReviewsQuery>}) {
    useTranslation('pages');
    const raw = query.data;
    const review: PaperAutoReviewsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperAutoReviewsResponse).overview
            ? (raw as PaperAutoReviewsResponse)
            : null;
    const empty = review
        && review.overview.totalRuns === 0
        && review.overview.strategyReviewedCount === 0
        && review.overview.publishReviewedCount === 0;

    return (
      <section aria-label={t('pages:paperAutomatedReview')}>
        <Card
            className="page-section"
            bordered={false}
            title={t('pages:paperAutomatedReview')}
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:simPaperOnlyRuleBasedReview')}</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {t('pages:ruleBasedReviewOfPaperExecutionFactsDiagnosticsAndStrategyEvaluations')}</Typography.Text>
                <NqRiskBanner
                    level="info"
                    message={t('pages:ruleBasedSummariesAndIssueGroupsDerivedFromExecutionDiagnosticsAndStrategyEvaluationsForPortfoliosFo')}
                    description={t('pages:reviewsUseOnlySimulatedPaperRunsDiagnosticsAndEvaluationsARuleEngineGeneratesThemWithoutAiDhRuntimeT')}
                />
                {query.error ? (
                    <NqErrorState
                        title={t('pages:failedToLoadPaperReviews')}
                        error={query.error as AppApiError}
                        description={t('pages:automatedReviewsAreUnavailableOlderBackendsMayLackThisApiOtherPaperModulesAreUnaffected')}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !review ? (
                    <NqLoadingState message={t('pages:loadingPaperReviews')}/>
                ) : !review ? (
                    <NqEmptyState description={t('pages:noPaperReviewDataTheResponseContainsNoReviewStructure')}/>
                ) : empty ? (
                    <NqEmptyState description={t('pages:createAndExecuteAPaperRunToGenerateRuleBasedReviews')}/>
                ) : (
                    <PaperAutoReviewBody review={review}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperAutoReviewBody({review}: {review: PaperAutoReviewsResponse}) {
    useTranslation('pages');
    const overview = review.overview;
    const portfolioReview = review.portfolioReview;
    const runReviews = review.runReviews ?? [];
    const strategyReviews = review.strategyReviews ?? [];
    const publishReviews = review.publishReviews ?? [];
    const issueClusters = review.issueClusters ?? [];

    const [severityFilter, setSeverityFilter] = useState<AutoReviewSeverityFilter>('all');
    const [causeFilter, setCauseFilter] = useState<AutoReviewCauseFilter>('all');
    const [dimensionFilter, setDimensionFilter] = useState<AutoReviewDimensionFilter>('all');

    // 筛选优先作用于 Run Reviews（按 primaryCause / severity）与 Issue Clusters（按 cause / severity）。
    const filteredRuns = runReviews.filter((r) =>
        (severityFilter === 'all' || r.severity === severityFilter)
        && (causeFilter === 'all' || r.primaryCause === causeFilter));
    const filteredClusters = issueClusters.filter((c) =>
        (severityFilter === 'all' || c.severity === severityFilter)
        && (causeFilter === 'all' || c.cause === causeFilter));
    const filtered = severityFilter !== 'all' || causeFilter !== 'all';

    const showRuns = dimensionFilter === 'all' || dimensionFilter === 'run';
    const showStrategies = dimensionFilter === 'all' || dimensionFilter === 'strategy';
    const showPublishes = dimensionFilter === 'all' || dimensionFilter === 'publish';
    const showClusters = dimensionFilter === 'all' || dimensionFilter === 'cluster';

    const runColumns: ColumnsType<PaperRunAutoReview> = [
        {title: t('pages:paperRun'), dataIndex: 'paperRunId', key: 'paperRunId', width: 150, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
        {title: t('pages:primaryCause'), key: 'primaryCause', width: 120, render: (_: unknown, r) => autoReviewCauseTag(r.primaryCause)},
        {title: t('pages:severity2'), key: 'severity', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.severity} tone={EXECUTION_SEVERITY_TONE[r.severity]}/>},
        {title: t('pages:confidence'), key: 'confidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.confidence} tone={EXECUTION_CONFIDENCE_TONE[r.confidence as PaperExecutionCauseConfidence] ?? 'neutral'}/>},
        nqNumericColumn({
            title: t('pages:returnRate'), key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperRunAutoReview) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-',
        }),
        nqNumericColumn({
            title: t('pages:maximumDrawdown'), key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperRunAutoReview) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-',
        }),
        {
            title: t('pages:review'), key: 'review', width: 320,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: t('pages:keyFacts'), key: 'keyFacts', width: 220, render: (_: unknown, r) => autoReviewTagList(r.keyFacts, '-')},
        {title: t('pages:possibleCauses'), key: 'likelyReasons', width: 240, render: (_: unknown, r) => autoReviewBullets(r.likelyReasons, '-')},
        {title: t('pages:recommendedDiagnosticActions'), key: 'suggestedActions', width: 240, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, t('pages:noRecommendedActions'), 'blue')},
        {title: t('pages:tags'), key: 'tags', width: 180, render: (_: unknown, r) => autoReviewTagList(r.tags, '-')},
    ];

    const strategyReviewColumns: ColumnsType<PaperStrategyAutoReview> = [
        {title: t('pages:strategyVersions'), dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: t('pages:rating'), key: 'ratingLabel', width: 110, render: (_: unknown, r) => autoReviewRatingTag(r.ratingLabel)},
        nqNumericColumn({title: t('pages:overallScore'), key: 'compositeScore', width: 90, render: (_: unknown, r: PaperStrategyAutoReview) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        {title: t('pages:confidence'), key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence as PaperStrategyEvaluationConfidence] ?? 'neutral'}/>},
        {title: t('pages:mainWeaknesses'), dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: t('pages:review'), key: 'review', width: 300,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: t('pages:strengths'), key: 'strengths', width: 220, render: (_: unknown, r) => autoReviewTagList(r.strengths, t('pages:noNotableStrengths'), 'green')},
        {title: t('pages:weaknesses'), key: 'weaknesses', width: 240, render: (_: unknown, r) => autoReviewBullets(r.weaknesses, '-')},
        {
            title: t('pages:warnings'), key: 'warnings', width: 200,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: t('pages:recommendedDiagnosticActions'), key: 'suggestedActions', width: 240, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, t('pages:noRecommendedActions'), 'blue')},
    ];

    const publishReviewColumns: ColumnsType<PaperPublishAutoReview> = [
        {title: t('pages:publish2'), dataIndex: 'publishId', key: 'publishId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: t('pages:strategyVersions'), dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 150, render: (v: string | null) => v ? <span className="nq-mono">{v}</span> : '-'},
        {title: t('pages:rating'), key: 'ratingLabel', width: 110, render: (_: unknown, r) => autoReviewRatingTag(r.ratingLabel)},
        nqNumericColumn({title: t('pages:overallScore'), key: 'compositeScore', width: 90, render: (_: unknown, r: PaperPublishAutoReview) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        {title: t('pages:confidence'), key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence as PaperStrategyEvaluationConfidence] ?? 'neutral'}/>},
        {title: t('pages:mainWeaknesses'), dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: t('pages:review'), key: 'review', width: 300,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: t('pages:strengths'), key: 'strengths', width: 200, render: (_: unknown, r) => autoReviewTagList(r.strengths, t('pages:noNotableStrengths'), 'green')},
        {title: t('pages:weaknesses'), key: 'weaknesses', width: 220, render: (_: unknown, r) => autoReviewBullets(r.weaknesses, '-')},
        {
            title: t('pages:warnings'), key: 'warnings', width: 180,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: t('pages:recommendedDiagnosticActions'), key: 'suggestedActions', width: 220, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, t('pages:noRecommendedActions'), 'blue')},
    ];

    const clusterColumns: ColumnsType<PaperIssueCluster> = [
        {title: t('pages:clusters'), dataIndex: 'clusterKey', key: 'clusterKey', width: 200, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: t('pages:reason'), key: 'cause', width: 140, render: (_: unknown, c) => autoReviewCauseTag(c.cause)},
        {title: t('pages:severity2'), key: 'severity', width: 110, render: (_: unknown, c) => <NqStatusTag status={c.severity} tone={EXECUTION_SEVERITY_TONE[c.severity]}/>},
        nqNumericColumn({title: t('pages:quantity'), dataIndex: 'count', key: 'count', width: 80}),
        {
            title: t('pages:affectedRunsStrategiesPublishes'), key: 'affected', width: 280,
            render: (_: unknown, c) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:run')}{c.affectedRunIds.length} {t('pages:strategies')}{c.affectedStrategyVersionIds.length} {t('pages:publishes')}{c.affectedPublishIds.length}
                    </Typography.Text>
                    {c.affectedRunIds.length > 0
                        ? <Space size={4} wrap>{c.affectedRunIds.map((id) => <Tag key={id} className="nq-mono">{id}</Tag>)}</Space>
                        : c.affectedStrategyVersionIds.length > 0
                            ? <Space size={4} wrap>{c.affectedStrategyVersionIds.map((id) => <Tag key={id} className="nq-mono">{id}</Tag>)}</Space>
                            : null}
                </Space>
            ),
        },
        {title: t('pages:summary'), dataIndex: 'summary', key: 'summary', width: 300, render: (v: string) => <Typography.Text style={{fontSize: 12}}>{v}</Typography.Text>},
        {title: t('pages:recommendedDiagnosticActions'), dataIndex: 'suggestedAction', key: 'suggestedAction', width: 260, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 复盘总览 */}
            <div className="nq-status-strip">
                <NqMetricCard label={t('pages:runsIncludedInReviews')} value={String(overview.totalRuns)} footer={t('pages:boundedPaperRun')}/>
                <NqMetricCard label={t('pages:reviewedRuns')} value={String(overview.reviewedRunCount)}/>
                <NqMetricCard label={t('pages:runsWithIssues')} value={String(overview.issueRunCount)} tone={overview.issueRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:healthyRuns')} value={String(overview.healthyRunCount)} tone={overview.healthyRunCount > 0 ? 'success' : 'muted'}/>
                <NqMetricCard label={t('pages:criticalIssues')} value={String(overview.criticalIssueCount)} tone={overview.criticalIssueCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label={t('pages:warningIssues')} value={String(overview.warningIssueCount)} tone={overview.warningIssueCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:reviewedStrategies')} value={String(overview.strategyReviewedCount)}/>
                <NqMetricCard label={t('pages:reviewedPublishes')} value={String(overview.publishReviewedCount)}/>
                <NqMetricCard label={t('pages:mostConcentratedIssue')} value={overview.topIssueCause != null ? autoReviewCauseLabel(overview.topIssueCause) : '-'}/>
                <NqMetricCard label={t('pages:mostCommonWeakness')} value={overview.topWeakness ?? '-'}/>
                <NqMetricCard label={t('pages:generatedAt')} value={overview.generatedAt ? formatDateTime(overview.generatedAt) : '-'}/>
            </div>

            {/* B) Portfolio Review 摘要区 */}
            <Card size="small" title={t('pages:portfolioReviewSummary')}>
                {portfolioReview ? (
                    <div role="region" aria-label={t('pages:paperPortfolioAutomatedReviewSummary')}>
                        <Space direction="vertical" size={8} style={{display: 'flex'}}>
                            <Typography.Text strong style={{fontSize: 14}}>{portfolioReview.headline}</Typography.Text>
                            <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 0}}>{portfolioReview.summary}</Typography.Paragraph>
                            <Descriptions bordered size="small" column={1}>
                                <Descriptions.Item label={t('pages:keyFindings')}>{autoReviewBullets(portfolioReview.keyFindings, t('pages:none'))}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:riskHighlights')}>{autoReviewTagList(portfolioReview.riskHighlights, t('pages:none'), 'red')}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:executionHighlights')}>{autoReviewTagList(portfolioReview.executionHighlights, t('pages:none'), 'orange')}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:strategyHighlights')}>{autoReviewTagList(portfolioReview.strategyHighlights, t('pages:none'), 'geekblue')}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:backtestDeviation')}>{autoReviewTagList(portfolioReview.backtestDeviationHighlights, t('pages:none'), 'purple')}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:recommendedDiagnosticActions')}>{autoReviewTagList(portfolioReview.suggestedNextActions, t('pages:noRecommendedActions'), 'blue')}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:reviewLimitations')}>{autoReviewBullets(portfolioReview.limitations, t('pages:none'))}</Descriptions.Item>
                            </Descriptions>
                        </Space>
                    </div>
                ) : (
                    <NqEmptyState description={t('pages:noPortfolioReviewSummary')}/>
                )}
            </Card>

            {/* 复盘筛选：severity / cause 影响 Run Reviews 与 Issue Clusters；dimension 控制展示维度。 */}
            <Card
                size="small"
                title={t('pages:reviewFilters')}
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setSeverityFilter('all'); setCauseFilter('all'); setDimensionFilter('all');}}>{t('pages:viewAll')}</Button>
                ) : null}
            >
                <div
                    role="group"
                    aria-label={t('pages:paperAutomatedReviewFilters')}
                    style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                >
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:severity2')}</Typography.Text>
                    <Select<AutoReviewSeverityFilter>
                        size="small" value={severityFilter} onChange={setSeverityFilter}
                        options={AUTO_REVIEW_SEVERITY_FILTER_OPTIONS as Array<{label: string; value: AutoReviewSeverityFilter}>}
                        style={{width: 150}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:reason')}</Typography.Text>
                    <Select<AutoReviewCauseFilter>
                        size="small" value={causeFilter} onChange={setCauseFilter}
                        options={AUTO_REVIEW_CAUSE_FILTER_OPTIONS as Array<{label: string; value: AutoReviewCauseFilter}>}
                        style={{width: 280}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:displayDimension')}</Typography.Text>
                    <Select<AutoReviewDimensionFilter>
                        size="small" value={dimensionFilter} onChange={setDimensionFilter}
                        options={AUTO_REVIEW_DIMENSION_FILTER_OPTIONS as Array<{label: string; value: AutoReviewDimensionFilter}>}
                        style={{width: 150}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:matchingRuns')}{filteredRuns.length} / {runReviews.length} {t('pages:clusters2')}{filteredClusters.length} / {issueClusters.length}
                    </Typography.Text>
                </div>
            </Card>

            {/* C) Issue Clusters（受 severity / cause 筛选） */}
            {showClusters ? (
                <Card size="small" title={filtered ? t('pages:issueClustersValue1MatchingItems', {value1: filteredClusters.length}) : t('pages:issueClusters')}>
                    <div role="region" aria-label={t('pages:paperReviewIssueClusters')}>
                        <NqDataTable<PaperIssueCluster>
                            rowKey="clusterKey"
                            pagination={false}
                            dataSource={filteredClusters}
                            columns={clusterColumns}
                            scroll={{x: 1290, y: 320}}
                            locale={{emptyText: t('pages:noIssueClustersMatchTheseFilters')}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* D) Run Reviews（受 severity / cause 筛选） */}
            {showRuns ? (
                <Card size="small" title={filtered ? t('pages:focusedRunReviewsValue1MatchingItems', {value1: filteredRuns.length}) : t('pages:focusedRunReviews')}>
                    <div role="region" aria-label={t('pages:paperAutomatedRunReviewTable')}>
                        <NqDataTable<PaperRunAutoReview>
                            rowKey="paperRunId"
                            pagination={false}
                            dataSource={filteredRuns}
                            columns={runColumns}
                            scroll={{x: 2020, y: 360}}
                            locale={{emptyText: t('pages:noRunReviewsMatchTheseFilters')}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* E) Strategy Reviews */}
            {showStrategies ? (
                <Card size="small" title={t('pages:strategyReviews')}>
                    <div role="region" aria-label={t('pages:paperAutomatedStrategyReviewTable')}>
                        <NqDataTable<PaperStrategyAutoReview>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyReviews}
                            columns={strategyReviewColumns}
                            scroll={{x: 1900, y: 320}}
                            locale={{emptyText: t('pages:noStrategyVersionsAvailableForReview')}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* F) Publish Reviews */}
            {showPublishes ? (
                <Card size="small" title={t('pages:publishReviews')}>
                    <div role="region" aria-label={t('pages:paperAutomatedPublishReviewTable')}>
                        <NqDataTable<PaperPublishAutoReview>
                            rowKey="publishId"
                            pagination={false}
                            dataSource={publishReviews}
                            columns={publishReviewColumns}
                            scroll={{x: 1960, y: 320}}
                            locale={{emptyText: t('pages:noPublishesAvailableForReview')}}
                        />
                    </div>
                </Card>
            ) : null}

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {t('pages:aRuleEngineGeneratesPaperReviewsWithoutAiDhRuntimeTheyAreNotLivePerformanceOrInvestmentAdviceRecomme')}</Typography.Text>
        </Space>
    );
}

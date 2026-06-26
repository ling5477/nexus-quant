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
    BACKTEST_DEVIATION_HIGH: 'Backtest 偏差大',
    SAMPLE_INSUFFICIENT: '样本不足',
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
    {label: '全部严重度', value: 'all'},
    {label: 'CRITICAL', value: 'CRITICAL'},
    {label: 'WARNING', value: 'WARNING'},
    {label: 'INFO', value: 'INFO'},
];

const AUTO_REVIEW_DIMENSION_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewDimensionFilter}> = [
    {label: '全部维度', value: 'all'},
    {label: 'Run', value: 'run'},
    {label: 'Strategy', value: 'strategy'},
    {label: 'Publish', value: 'publish'},
    {label: 'Cluster', value: 'cluster'},
];

const AUTO_REVIEW_CAUSE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: AutoReviewCauseFilter}> = [
    {label: '全部原因', value: 'all'},
    {label: '无订单 NO_ORDER', value: 'NO_ORDER'},
    {label: '有订单无成交 ORDER_NO_FILL', value: 'ORDER_NO_FILL'},
    {label: '成交亏损 FILLED_LOSS', value: 'FILLED_LOSS'},
    {label: '风控拦截 RISK_BLOCKED', value: 'RISK_BLOCKED'},
    {label: '数据不足 DATA_INSUFFICIENT', value: 'DATA_INSUFFICIENT'},
    {label: '高回撤 HIGH_DRAWDOWN', value: 'HIGH_DRAWDOWN'},
    {label: '异常终态 FAILED_RUN', value: 'FAILED_RUN'},
    {label: 'Backtest 偏差大 BACKTEST_DEVIATION_HIGH', value: 'BACKTEST_DEVIATION_HIGH'},
    {label: '样本不足 SAMPLE_INSUFFICIENT', value: 'SAMPLE_INSUFFICIENT'},
    {label: '健康 HEALTHY', value: 'HEALTHY'},
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
      <section aria-label="Paper 自动复盘">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 自动复盘"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · Rules-based review</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    基于 Paper 执行事实、诊断与策略评估的规则化复盘摘要。
                </Typography.Text>
                <NqRiskBanner
                    level="info"
                    message="把执行诊断与策略评估结果规则化归纳为组合 / 重点 run / 策略 / 发布的可读复盘摘要与问题聚类。"
                    description="该复盘仅基于 Paper 模拟运行、诊断与策略评估结果。复盘由规则引擎生成，不接 AI / DH runtime。内容不代表 LIVE 或真实交易表现，也不构成投资建议。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 自动复盘加载失败"
                        error={query.error as AppApiError}
                        description="自动复盘不可用（旧后端可能尚未提供该接口）；其余 Paper 模块不受影响。"
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !review ? (
                    <NqLoadingState message="加载 Paper 自动复盘中..."/>
                ) : !review ? (
                    <NqEmptyState description="暂无 Paper 自动复盘数据（接口未返回复盘结构）。"/>
                ) : empty ? (
                    <NqEmptyState description="暂无 Paper 自动复盘数据，创建并运行 Paper run 后自动生成规则化复盘。"/>
                ) : (
                    <PaperAutoReviewBody review={review}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperAutoReviewBody({review}: {review: PaperAutoReviewsResponse}) {
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
        {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 150, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
        {title: '主因', key: 'primaryCause', width: 120, render: (_: unknown, r) => autoReviewCauseTag(r.primaryCause)},
        {title: '严重度', key: 'severity', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.severity} tone={EXECUTION_SEVERITY_TONE[r.severity]}/>},
        {title: '可信度', key: 'confidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.confidence} tone={EXECUTION_CONFIDENCE_TONE[r.confidence as PaperExecutionCauseConfidence] ?? 'neutral'}/>},
        nqNumericColumn({
            title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperRunAutoReview) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-',
        }),
        nqNumericColumn({
            title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperRunAutoReview) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-',
        }),
        {
            title: '复盘', key: 'review', width: 320,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: '关键事实', key: 'keyFacts', width: 220, render: (_: unknown, r) => autoReviewTagList(r.keyFacts, '-')},
        {title: '可能原因', key: 'likelyReasons', width: 240, render: (_: unknown, r) => autoReviewBullets(r.likelyReasons, '-')},
        {title: '建议排查动作', key: 'suggestedActions', width: 240, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, '暂无建议动作', 'blue')},
        {title: '标签', key: 'tags', width: 180, render: (_: unknown, r) => autoReviewTagList(r.tags, '-')},
    ];

    const strategyReviewColumns: ColumnsType<PaperStrategyAutoReview> = [
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => autoReviewRatingTag(r.ratingLabel)},
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90, render: (_: unknown, r: PaperStrategyAutoReview) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence as PaperStrategyEvaluationConfidence] ?? 'neutral'}/>},
        {title: '主要短板', dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: '复盘', key: 'review', width: 300,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: '优势', key: 'strengths', width: 220, render: (_: unknown, r) => autoReviewTagList(r.strengths, '暂无突出优势', 'green')},
        {title: '短板', key: 'weaknesses', width: 240, render: (_: unknown, r) => autoReviewBullets(r.weaknesses, '-')},
        {
            title: '警告', key: 'warnings', width: 200,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '建议排查动作', key: 'suggestedActions', width: 240, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, '暂无建议动作', 'blue')},
    ];

    const publishReviewColumns: ColumnsType<PaperPublishAutoReview> = [
        {title: '发布', dataIndex: 'publishId', key: 'publishId', width: 160, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '策略版本', dataIndex: 'strategyVersionId', key: 'strategyVersionId', width: 150, render: (v: string | null) => v ? <span className="nq-mono">{v}</span> : '-'},
        {title: '评级', key: 'ratingLabel', width: 110, render: (_: unknown, r) => autoReviewRatingTag(r.ratingLabel)},
        nqNumericColumn({title: '综合分', key: 'compositeScore', width: 90, render: (_: unknown, r: PaperPublishAutoReview) => <span className="nq-num"><strong>{r.compositeScore}</strong></span>}),
        {title: '可信度', key: 'evaluationConfidence', width: 100, render: (_: unknown, r) => <NqStatusTag status={r.evaluationConfidence} tone={EVAL_CONFIDENCE_TONE[r.evaluationConfidence as PaperStrategyEvaluationConfidence] ?? 'neutral'}/>},
        {title: '主要短板', dataIndex: 'primaryWeakness', key: 'primaryWeakness', width: 130, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
        {
            title: '复盘', key: 'review', width: 300,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text strong style={{fontSize: 12}}>{r.reviewHeadline}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{r.reviewSummary}</Typography.Text>
                </Space>
            ),
        },
        {title: '优势', key: 'strengths', width: 200, render: (_: unknown, r) => autoReviewTagList(r.strengths, '暂无突出优势', 'green')},
        {title: '短板', key: 'weaknesses', width: 220, render: (_: unknown, r) => autoReviewBullets(r.weaknesses, '-')},
        {
            title: '警告', key: 'warnings', width: 180,
            render: (_: unknown, r) => r.warnings.length > 0
                ? <Space size={4} wrap>{r.warnings.map((w) => <Tag key={w} color="warning">{w}</Tag>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '建议排查动作', key: 'suggestedActions', width: 220, render: (_: unknown, r) => autoReviewTagList(r.suggestedActions, '暂无建议动作', 'blue')},
    ];

    const clusterColumns: ColumnsType<PaperIssueCluster> = [
        {title: '聚类', dataIndex: 'clusterKey', key: 'clusterKey', width: 200, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '原因', key: 'cause', width: 140, render: (_: unknown, c) => autoReviewCauseTag(c.cause)},
        {title: '严重度', key: 'severity', width: 110, render: (_: unknown, c) => <NqStatusTag status={c.severity} tone={EXECUTION_SEVERITY_TONE[c.severity]}/>},
        nqNumericColumn({title: '数量', dataIndex: 'count', key: 'count', width: 80}),
        {
            title: '受影响 Run / 策略 / 发布', key: 'affected', width: 280,
            render: (_: unknown, c) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        Run {c.affectedRunIds.length} · 策略 {c.affectedStrategyVersionIds.length} · 发布 {c.affectedPublishIds.length}
                    </Typography.Text>
                    {c.affectedRunIds.length > 0
                        ? <Space size={4} wrap>{c.affectedRunIds.map((id) => <Tag key={id} className="nq-mono">{id}</Tag>)}</Space>
                        : c.affectedStrategyVersionIds.length > 0
                            ? <Space size={4} wrap>{c.affectedStrategyVersionIds.map((id) => <Tag key={id} className="nq-mono">{id}</Tag>)}</Space>
                            : null}
                </Space>
            ),
        },
        {title: '摘要', dataIndex: 'summary', key: 'summary', width: 300, render: (v: string) => <Typography.Text style={{fontSize: 12}}>{v}</Typography.Text>},
        {title: '建议排查动作', dataIndex: 'suggestedAction', key: 'suggestedAction', width: 260, render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 复盘总览 */}
            <div className="nq-status-strip">
                <NqMetricCard label="纳入复盘 run" value={String(overview.totalRuns)} footer="bounded Paper run"/>
                <NqMetricCard label="已复盘 run" value={String(overview.reviewedRunCount)}/>
                <NqMetricCard label="问题 run" value={String(overview.issueRunCount)} tone={overview.issueRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="健康 run" value={String(overview.healthyRunCount)} tone={overview.healthyRunCount > 0 ? 'success' : 'muted'}/>
                <NqMetricCard label="关键问题" value={String(overview.criticalIssueCount)} tone={overview.criticalIssueCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="警告问题" value={String(overview.warningIssueCount)} tone={overview.warningIssueCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="已复盘策略" value={String(overview.strategyReviewedCount)}/>
                <NqMetricCard label="已复盘发布" value={String(overview.publishReviewedCount)}/>
                <NqMetricCard label="最集中问题" value={overview.topIssueCause != null ? autoReviewCauseLabel(overview.topIssueCause) : '-'}/>
                <NqMetricCard label="最常见短板" value={overview.topWeakness ?? '-'}/>
                <NqMetricCard label="生成时间" value={overview.generatedAt ? formatDateTime(overview.generatedAt) : '-'}/>
            </div>

            {/* B) Portfolio Review 摘要区 */}
            <Card size="small" title="组合复盘摘要">
                {portfolioReview ? (
                    <div role="region" aria-label="Paper 自动复盘组合摘要">
                        <Space direction="vertical" size={8} style={{display: 'flex'}}>
                            <Typography.Text strong style={{fontSize: 14}}>{portfolioReview.headline}</Typography.Text>
                            <Typography.Paragraph type="secondary" style={{fontSize: 12, marginBottom: 0}}>{portfolioReview.summary}</Typography.Paragraph>
                            <Descriptions bordered size="small" column={1}>
                                <Descriptions.Item label="关键发现">{autoReviewBullets(portfolioReview.keyFindings, '无')}</Descriptions.Item>
                                <Descriptions.Item label="风险亮点">{autoReviewTagList(portfolioReview.riskHighlights, '无', 'red')}</Descriptions.Item>
                                <Descriptions.Item label="执行亮点">{autoReviewTagList(portfolioReview.executionHighlights, '无', 'orange')}</Descriptions.Item>
                                <Descriptions.Item label="策略亮点">{autoReviewTagList(portfolioReview.strategyHighlights, '无', 'geekblue')}</Descriptions.Item>
                                <Descriptions.Item label="Backtest 偏差">{autoReviewTagList(portfolioReview.backtestDeviationHighlights, '无', 'purple')}</Descriptions.Item>
                                <Descriptions.Item label="建议排查动作">{autoReviewTagList(portfolioReview.suggestedNextActions, '暂无建议动作', 'blue')}</Descriptions.Item>
                                <Descriptions.Item label="复盘局限">{autoReviewBullets(portfolioReview.limitations, '无')}</Descriptions.Item>
                            </Descriptions>
                        </Space>
                    </div>
                ) : (
                    <NqEmptyState description="暂无组合复盘摘要。"/>
                )}
            </Card>

            {/* 复盘筛选：severity / cause 影响 Run Reviews 与 Issue Clusters；dimension 控制展示维度。 */}
            <Card
                size="small"
                title="复盘筛选"
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setSeverityFilter('all'); setCauseFilter('all'); setDimensionFilter('all');}}>查看全部</Button>
                ) : null}
            >
                <div
                    role="group"
                    aria-label="Paper 自动复盘筛选"
                    style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                >
                    <Typography.Text type="secondary" style={{fontSize: 12}}>严重度</Typography.Text>
                    <Select<AutoReviewSeverityFilter>
                        size="small" value={severityFilter} onChange={setSeverityFilter}
                        options={AUTO_REVIEW_SEVERITY_FILTER_OPTIONS as Array<{label: string; value: AutoReviewSeverityFilter}>}
                        style={{width: 150}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>原因</Typography.Text>
                    <Select<AutoReviewCauseFilter>
                        size="small" value={causeFilter} onChange={setCauseFilter}
                        options={AUTO_REVIEW_CAUSE_FILTER_OPTIONS as Array<{label: string; value: AutoReviewCauseFilter}>}
                        style={{width: 280}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>展示维度</Typography.Text>
                    <Select<AutoReviewDimensionFilter>
                        size="small" value={dimensionFilter} onChange={setDimensionFilter}
                        options={AUTO_REVIEW_DIMENSION_FILTER_OPTIONS as Array<{label: string; value: AutoReviewDimensionFilter}>}
                        style={{width: 150}} virtual={false}
                    />
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        命中 Run {filteredRuns.length} / {runReviews.length} · 聚类 {filteredClusters.length} / {issueClusters.length}
                    </Typography.Text>
                </div>
            </Card>

            {/* C) Issue Clusters（受 severity / cause 筛选） */}
            {showClusters ? (
                <Card size="small" title={filtered ? `问题聚类 · 当前筛选命中 ${filteredClusters.length} 条` : '问题聚类'}>
                    <div role="region" aria-label="Paper 自动复盘问题聚类">
                        <NqDataTable<PaperIssueCluster>
                            rowKey="clusterKey"
                            pagination={false}
                            dataSource={filteredClusters}
                            columns={clusterColumns}
                            scroll={{x: 1290, y: 320}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的问题聚类。'}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* D) Run Reviews（受 severity / cause 筛选） */}
            {showRuns ? (
                <Card size="small" title={filtered ? `重点 Run 复盘 · 当前筛选命中 ${filteredRuns.length} 条` : '重点 Run 复盘'}>
                    <div role="region" aria-label="Paper 自动复盘 Run 表">
                        <NqDataTable<PaperRunAutoReview>
                            rowKey="paperRunId"
                            pagination={false}
                            dataSource={filteredRuns}
                            columns={runColumns}
                            scroll={{x: 2020, y: 360}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的 Run 复盘。'}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* E) Strategy Reviews */}
            {showStrategies ? (
                <Card size="small" title="策略复盘">
                    <div role="region" aria-label="Paper 自动复盘 Strategy 表">
                        <NqDataTable<PaperStrategyAutoReview>
                            rowKey="strategyVersionId"
                            pagination={false}
                            dataSource={strategyReviews}
                            columns={strategyReviewColumns}
                            scroll={{x: 1900, y: 320}}
                            locale={{emptyText: '暂无可复盘的策略版本。'}}
                        />
                    </div>
                </Card>
            ) : null}

            {/* F) Publish Reviews */}
            {showPublishes ? (
                <Card size="small" title="发布复盘">
                    <div role="region" aria-label="Paper 自动复盘 Publish 表">
                        <NqDataTable<PaperPublishAutoReview>
                            rowKey="publishId"
                            pagination={false}
                            dataSource={publishReviews}
                            columns={publishReviewColumns}
                            scroll={{x: 1960, y: 320}}
                            locale={{emptyText: '暂无可复盘的发布。'}}
                        />
                    </div>
                </Card>
            ) : null}

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                该复盘由规则引擎生成，不接 AI / DH runtime；仅 Paper 模拟口径，不代表 LIVE 或真实交易表现，也不构成投资建议。
                建议动作均为工程排查动作（检查数据 / 触发条件 / 撮合参数 / 风控阈值 / 增加样本 / 复核 Backtest 偏差）。
            </Typography.Text>
        </Space>
    );
}

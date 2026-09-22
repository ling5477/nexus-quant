import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Button, Card, Select, Space, Typography} from 'antd';
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
import {usePaperExecutionDiagnosticsQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {
    PaperExecutionCause,
    PaperExecutionCauseConfidence,
    PaperExecutionDiagnosticCauseDistribution,
    PaperExecutionDiagnosticsResponse,
    PaperExecutionGroupDiagnostic,
    PaperExecutionRunDiagnostic,
    PaperExecutionSeverity,
} from '@/types/paper-trading';

// ---- GateK K2：Paper 执行诊断展示映射与筛选（消费 K1 endpoint，纯前端只读展示）----

/** cause 中文展示名（保留枚举值用于筛选与审计，展示层映射为业务可读名）。 */
export const EXECUTION_CAUSE_LABEL: Record<PaperExecutionCause, string> = {
    get NO_ORDER() { return t('pages:noOrders'); },
    get ORDER_NO_FILL() { return t('pages:ordersWithoutFills'); },
    get FILLED_LOSS() { return t('pages:tradingLoss'); },
    get RISK_BLOCKED() { return t('pages:riskBlocked'); },
    get DATA_INSUFFICIENT() { return t('pages:insufficientData'); },
    get HIGH_DRAWDOWN() { return t('pages:highDrawdown'); },
    get FAILED_RUN() { return t('pages:abnormalTerminalState'); },
    get RUNNING_NO_RESULT() { return t('pages:runningWithoutResults'); },
    get HEALTHY() { return t('pages:healthy'); },
    get UNKNOWN() { return t('pages:unattributed'); },
};

export const EXECUTION_CAUSE_TONE: Record<PaperExecutionCause, NqStatusTone> = {
    NO_ORDER: 'warning',
    ORDER_NO_FILL: 'warning',
    FILLED_LOSS: 'warning',
    RISK_BLOCKED: 'danger',
    DATA_INSUFFICIENT: 'warning',
    HIGH_DRAWDOWN: 'danger',
    FAILED_RUN: 'danger',
    RUNNING_NO_RESULT: 'info',
    HEALTHY: 'success',
    UNKNOWN: 'neutral',
};

export const EXECUTION_SEVERITY_TONE: Record<PaperExecutionSeverity, NqStatusTone> = {
    INFO: 'neutral',
    WARNING: 'warning',
    CRITICAL: 'danger',
};

export const EXECUTION_CONFIDENCE_TONE: Record<PaperExecutionCauseConfidence, NqStatusTone> = {
    HIGH: 'success',
    MEDIUM: 'info',
    LOW: 'neutral',
};

type ExecutionCauseFilter = 'all' | PaperExecutionCause;
type ExecutionSeverityFilter = 'all' | PaperExecutionSeverity;

/** 诊断 cause 筛选项（全部 + 各归因；顺序与后端 primaryCause 优先级一致，最紧急在前）。 */
const EXECUTION_CAUSE_FILTER_OPTIONS: ReadonlyArray<{label: string; value: ExecutionCauseFilter}> = [
    {get label() { return t('pages:allReasons'); }, value: 'all'},
    {get label() { return t('pages:failedTerminalStateFailedRun'); }, value: 'FAILED_RUN'},
    {get label() { return t('pages:insufficientDataDataInsufficient'); }, value: 'DATA_INSUFFICIENT'},
    {get label() { return t('pages:riskBlockedRiskBlocked'); }, value: 'RISK_BLOCKED'},
    {get label() { return t('pages:unfilledOrdersOrderNoFill'); }, value: 'ORDER_NO_FILL'},
    {get label() { return t('pages:noOrdersNoOrder'); }, value: 'NO_ORDER'},
    {get label() { return t('pages:tradingLossFilledLoss'); }, value: 'FILLED_LOSS'},
    {get label() { return t('pages:highDrawdownHighDrawdown'); }, value: 'HIGH_DRAWDOWN'},
    {get label() { return t('pages:runHasNoResultRunningNoResult'); }, value: 'RUNNING_NO_RESULT'},
    {get label() { return t('pages:healthyHealthy'); }, value: 'HEALTHY'},
];

const EXECUTION_SEVERITY_FILTER_OPTIONS: ReadonlyArray<{label: string; value: ExecutionSeverityFilter}> = [
    {get label() { return t('pages:allSeverities'); }, value: 'all'},
    {label: 'CRITICAL', value: 'CRITICAL'},
    {label: 'WARNING', value: 'WARNING'},
    {label: 'INFO', value: 'INFO'},
];

/** cause 标签（中文名 + 语义色），缺省回退原始枚举值，不伪造。 */
function executionCauseTag(cause: PaperExecutionCause) {
    return <NqStatusTag status={EXECUTION_CAUSE_LABEL[cause] ?? cause} tone={EXECUTION_CAUSE_TONE[cause] ?? 'neutral'}/>;
}

/**
 * PaperExecutionDiagnosticsDashboard —— Paper 执行诊断（GateK Batch K2）。
 * 消费 K1 只读 endpoint /paper-trading/execution-diagnostics，把规则化归因（cause / severity / confidence /
 * explanation / suggestedAction）展示出来，让用户从「事实筛选」升级为「原因诊断」。
 * 独立 query：加载 / 错误 / 空 / 兼容回退均限定在本区域，不连累组合看板、风险驾驶舱与策略排行。
 * 仅 Paper-only 规则化归因，不是 AI 投资建议，也不构成真实交易建议。
 */
export function PaperExecutionDiagnosticsDashboard({query}: {query: ReturnType<typeof usePaperExecutionDiagnosticsQuery>}) {
    useTranslation('pages');
    const raw = query.data;
    const diagnostics: PaperExecutionDiagnosticsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperExecutionDiagnosticsResponse).overview
            ? (raw as PaperExecutionDiagnosticsResponse)
            : null;

    return (
      <section aria-label={t('pages:paperExecutionDiagnostics')}>
        <Card
            className="page-section"
            bordered={false}
            title={t('pages:paperExecutionDiagnostics')}
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:simPaperOnlyRuleBasedDiagnostics')}</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    {t('pages:ruleBasedAttributionOfPaperExecutionFactsNotLiveOrRealTradingAdvice')}</Typography.Text>
                <NqRiskBanner
                    level="info"
                    message={t('pages:ruleBasedAttributionOfMissingOrdersUnfilledOrdersTradingLossesRiskBlocksAndInsufficientDataForEachPa')}
                    description={t('pages:diagnosticsUseSimulatedPaperRunsAndLocalExecutionFactsOnlyTheyAreRuleBasedExplanationsNotLivePerform')}
                />
                {query.error ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqErrorState
                            title={t('pages:failedToLoadPaperExecutionDiagnostics')}
                            error={query.error as AppApiError}
                            description={t('pages:executionDiagnosticsAreUnavailableOlderBackendsMayLackThisApiOtherPaperModulesAreUnaffected')}
                            onRetry={() => query.refetch()}
                        />
                    </Space>
                ) : query.isFetching && !diagnostics ? (
                    <NqLoadingState message={t('pages:loadingPaperExecutionDiagnostics')}/>
                ) : !diagnostics ? (
                    <NqEmptyState description={t('pages:noExecutionDiagnosticsTheResponseContainsNoDiagnosticStructure')}/>
                ) : diagnostics.overview.totalRuns === 0 ? (
                    <NqEmptyState description={t('pages:createAndExecuteAPaperRunToGenerateExecutionAttribution')}/>
                ) : (
                    <PaperExecutionDiagnosticsBody diagnostics={diagnostics}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperExecutionDiagnosticsBody({diagnostics}: {diagnostics: PaperExecutionDiagnosticsResponse}) {
    useTranslation('pages');
    const {overview, causeDistribution, runDiagnostics, strategyDiagnostics, publishDiagnostics} = diagnostics;

    const [causeFilter, setCauseFilter] = useState<ExecutionCauseFilter>('all');
    const [severityFilter, setSeverityFilter] = useState<ExecutionSeverityFilter>('all');

    // 筛选只作用于 Run Diagnostics 表（cause 按 primaryCause；severity 按 run severity）；分组表保持完整。
    const filteredRuns = runDiagnostics.filter((r) =>
        (causeFilter === 'all' || r.primaryCause === causeFilter)
        && (severityFilter === 'all' || r.severity === severityFilter));
    const filtered = causeFilter !== 'all' || severityFilter !== 'all';

    const runColumns: ColumnsType<PaperExecutionRunDiagnostic> = [
        {title: t('pages:paperRun'), dataIndex: 'paperRunId', key: 'paperRunId', width: 150, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
        {title: t('pages:primaryCause'), key: 'primaryCause', width: 120, render: (_: unknown, r) => executionCauseTag(r.primaryCause)},
        {
            title: t('pages:secondaryCauses'), key: 'secondaryCauses', width: 200,
            render: (_: unknown, r) => r.secondaryCauses.length > 0
                ? <Space size={4} wrap>{r.secondaryCauses.map((c) => <span key={c}>{executionCauseTag(c)}</span>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: t('pages:severity2'), key: 'severity', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.severity} tone={EXECUTION_SEVERITY_TONE[r.severity]}/>},
        {title: t('pages:confidence'), key: 'causeConfidence', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.causeConfidence} tone={EXECUTION_CONFIDENCE_TONE[r.causeConfidence]}/>},
        nqNumericColumn({title: t('pages:order'), dataIndex: 'orderCount', key: 'orderCount', width: 70}),
        nqNumericColumn({title: t('pages:trade'), dataIndex: 'tradeCount', key: 'tradeCount', width: 70}),
        nqNumericColumn({
            title: t('pages:returnRate'), key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperExecutionRunDiagnostic) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-',
        }),
        nqNumericColumn({
            title: t('pages:maximumDrawdown'), key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperExecutionRunDiagnostic) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-',
        }),
        {
            title: t('pages:diagnosticExplanationRecommendation'), key: 'explanation', width: 340,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text style={{fontSize: 12}}>{r.explanation}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:recommendation')}{r.suggestedAction}</Typography.Text>
                </Space>
            ),
        },
    ];

    const groupColumns = (dimensionTitle: string): ColumnsType<PaperExecutionGroupDiagnostic> => [
        {title: dimensionTitle, dataIndex: 'key', key: 'key', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: t('pages:runCount'), dataIndex: 'runCount', key: 'runCount', width: 80}),
        {title: t('pages:primaryCause'), key: 'primaryCause', width: 120, render: (_: unknown, g) => executionCauseTag(g.primaryCause)},
        {
            title: t('pages:topCause'), key: 'topCauses', width: 220,
            render: (_: unknown, g) => g.topCauses.length > 0
                ? <Space size={4} wrap>{g.topCauses.map((c) => <span key={c}>{executionCauseTag(c)}</span>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: t('pages:severity2'), key: 'severity', width: 100, render: (_: unknown, g) => <NqStatusTag status={g.severity} tone={EXECUTION_SEVERITY_TONE[g.severity]}/>},
        {title: t('pages:confidence'), key: 'causeConfidence', width: 100, render: (_: unknown, g) => <NqStatusTag status={g.causeConfidence} tone={EXECUTION_CONFIDENCE_TONE[g.causeConfidence]}/>},
        nqNumericColumn({title: t('pages:noOrders'), dataIndex: 'noOrderCount', key: 'noOrderCount', width: 80}),
        nqNumericColumn({title: t('pages:ordersWithoutFills2'), dataIndex: 'orderNoFillCount', key: 'orderNoFillCount', width: 100}),
        nqNumericColumn({title: t('pages:tradingLoss'), dataIndex: 'filledLossCount', key: 'filledLossCount', width: 90}),
        nqNumericColumn({title: t('pages:riskBlocked'), dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 90}),
        nqNumericColumn({title: t('pages:insufficientData'), dataIndex: 'dataInsufficientCount', key: 'dataInsufficientCount', width: 90}),
        nqNumericColumn({title: t('pages:highDrawdown'), dataIndex: 'highDrawdownCount', key: 'highDrawdownCount', width: 80}),
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 诊断总览（按事实独立计数，桶可重叠） */}
            <div className="nq-status-strip">
                <NqMetricCard label={t('pages:runsIncludedInDiagnostics')} value={String(overview.totalRuns)} footer={t('pages:boundedPaperRun')}/>
                <NqMetricCard label={t('pages:noOrders')} value={String(overview.noOrderRunCount)} tone={overview.noOrderRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:ordersWithoutFills')} value={String(overview.orderNoFillRunCount)} tone={overview.orderNoFillRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:tradingLoss')} value={String(overview.filledLossRunCount)} tone={overview.filledLossRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:riskBlocked')} value={String(overview.riskBlockedRunCount)} tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label={t('pages:insufficientData')} value={String(overview.dataInsufficientRunCount)} tone={overview.dataInsufficientRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label={t('pages:highDrawdown')} value={String(overview.highDrawdownRunCount)} tone={overview.highDrawdownRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label={t('pages:abnormalTerminalState')} value={String(overview.failedRunCount)} tone={overview.failedRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label={t('pages:running')} value={String(overview.runningRunCount)} tone={overview.runningRunCount > 0 ? 'success' : 'muted'}/>
            </div>

            {/* B) Cause Distribution（主因分布） */}
            <Card size="small" title={t('pages:primaryCauseDistributionByRunPrimarycause')}>
                <div role="region" aria-label={t('pages:paperDiagnosticPrimaryCauseDistribution')}>
                    <NqDataTable<PaperExecutionDiagnosticCauseDistribution>
                        rowKey="cause"
                        pagination={false}
                        dataSource={causeDistribution}
                        columns={[
                            {title: t('pages:reason'), key: 'cause', width: 140, render: (_: unknown, d) => executionCauseTag(d.cause)},
                            nqNumericColumn({title: t('pages:runCount'), dataIndex: 'count', key: 'count', width: 90}),
                            {title: t('pages:severity2'), key: 'severity', width: 110, render: (_: unknown, d) => <NqStatusTag status={d.severity} tone={EXECUTION_SEVERITY_TONE[d.severity]}/>},
                            {title: t('pages:representativeConfidence'), key: 'confidence', width: 120, render: (_: unknown, d) => <NqStatusTag status={d.confidence} tone={EXECUTION_CONFIDENCE_TONE[d.confidence]}/>},
                            {title: t('pages:explanation2'), dataIndex: 'description', key: 'description', render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
                        ]}
                        scroll={{x: 720}}
                        locale={{emptyText: t('pages:noPrimaryCauseDistribution')}}
                    />
                </div>
            </Card>

            {/* C) Run Diagnostics（单 run 诊断，受 cause / severity 筛选） */}
            <Card
                size="small"
                title={filtered
                    ? t('pages:runExecutionDiagnosticsValue1MatchingItems', {value1: filteredRuns.length})
                    : t('pages:runExecutionDiagnostics')}
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setCauseFilter('all'); setSeverityFilter('all');}}>{t('pages:viewAll')}</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label={t('pages:paperExecutionDiagnosticFilters')}
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:causeFilter')}</Typography.Text>
                        <Select<ExecutionCauseFilter>
                            size="small"
                            value={causeFilter}
                            onChange={setCauseFilter}
                            options={EXECUTION_CAUSE_FILTER_OPTIONS as Array<{label: string; value: ExecutionCauseFilter}>}
                            style={{width: 230}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:severityFilter')}</Typography.Text>
                        <Select<ExecutionSeverityFilter>
                            size="small"
                            value={severityFilter}
                            onChange={setSeverityFilter}
                            options={EXECUTION_SEVERITY_FILTER_OPTIONS as Array<{label: string; value: ExecutionSeverityFilter}>}
                            style={{width: 150}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:matches')}{filteredRuns.length} / {runDiagnostics.length} {t('pages:runs2')}</Typography.Text>
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:confidenceDescribesDirectOrInferredEvidenceNotRealTradingConclusionsHighMeansExplicitFactsMediumInfe')}</Typography.Text>
                    <div role="region" aria-label={t('pages:paperExecutionDiagnosticRunTable')}>
                        <NqDataTable<PaperExecutionRunDiagnostic>
                            rowKey="paperRunId"
                            pagination={false}
                            dataSource={filteredRuns}
                            columns={runColumns}
                            scroll={{x: 1560, y: 320}}
                            locale={{emptyText: t('pages:noRunDiagnosticsMatchTheseFilters')}}
                        />
                    </div>
                </Space>
            </Card>

            {/* D) Strategy Diagnostics（strategyVersionId 维度聚合） */}
            <Card size="small" title={t('pages:strategyVersionExecutionDiagnosticSummary')}>
                <div role="region" aria-label={t('pages:paperExecutionDiagnosticStrategyTable')}>
                    <NqDataTable<PaperExecutionGroupDiagnostic>
                        rowKey="key"
                        pagination={false}
                        dataSource={strategyDiagnostics}
                        columns={groupColumns(t('pages:strategyVersions'))}
                        scroll={{x: 1360, y: 280}}
                        locale={{emptyText: t('pages:noStrategyVersionDiagnosticsAvailableToAggregate')}}
                    />
                </div>
            </Card>

            {/* E) Publish Diagnostics（publishId 维度聚合） */}
            <Card size="small" title={t('pages:publishExecutionDiagnosticSummary')}>
                <div role="region" aria-label={t('pages:paperExecutionDiagnosticPublishTable')}>
                    <NqDataTable<PaperExecutionGroupDiagnostic>
                        rowKey="key"
                        pagination={false}
                        dataSource={publishDiagnostics}
                        columns={groupColumns(t('pages:publish2'))}
                        scroll={{x: 1360, y: 280}}
                        locale={{emptyText: t('pages:noPublishDiagnosticsAvailableToAggregate')}}
                    />
                </div>
            </Card>

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {t('pages:diagnosticCountsAreIndependentARunMayAppearInMultipleGroupsPrimaryAndSecondaryCausesFollowRulePriori')}</Typography.Text>
        </Space>
    );
}

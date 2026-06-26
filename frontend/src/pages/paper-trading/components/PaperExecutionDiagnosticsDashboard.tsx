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
    NO_ORDER: '无订单',
    ORDER_NO_FILL: '有订单无成交',
    FILLED_LOSS: '成交亏损',
    RISK_BLOCKED: '风控拦截',
    DATA_INSUFFICIENT: '数据不足',
    HIGH_DRAWDOWN: '高回撤',
    FAILED_RUN: '异常终态',
    RUNNING_NO_RESULT: '运行未出结果',
    HEALTHY: '健康',
    UNKNOWN: '未归因',
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
    {label: '全部原因', value: 'all'},
    {label: '异常终态 FAILED_RUN', value: 'FAILED_RUN'},
    {label: '数据不足 DATA_INSUFFICIENT', value: 'DATA_INSUFFICIENT'},
    {label: '风控拦截 RISK_BLOCKED', value: 'RISK_BLOCKED'},
    {label: '有订单无成交 ORDER_NO_FILL', value: 'ORDER_NO_FILL'},
    {label: '无订单 NO_ORDER', value: 'NO_ORDER'},
    {label: '成交亏损 FILLED_LOSS', value: 'FILLED_LOSS'},
    {label: '高回撤 HIGH_DRAWDOWN', value: 'HIGH_DRAWDOWN'},
    {label: '运行未出结果 RUNNING_NO_RESULT', value: 'RUNNING_NO_RESULT'},
    {label: '健康 HEALTHY', value: 'HEALTHY'},
];

const EXECUTION_SEVERITY_FILTER_OPTIONS: ReadonlyArray<{label: string; value: ExecutionSeverityFilter}> = [
    {label: '全部严重度', value: 'all'},
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
    const raw = query.data;
    const diagnostics: PaperExecutionDiagnosticsResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperExecutionDiagnosticsResponse).overview
            ? (raw as PaperExecutionDiagnosticsResponse)
            : null;

    return (
      <section aria-label="Paper 执行诊断">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 执行诊断"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · Rules-based diagnostics</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <Typography.Text type="secondary" style={{fontSize: 12}}>
                    基于 Paper 执行事实的规则化归因，不代表 LIVE 或真实交易建议。
                </Typography.Text>
                <NqRiskBanner
                    level="info"
                    message="对每个 Paper run 做规则化执行归因：为什么无订单 / 有订单无成交 / 成交亏损 / 风控拦截 / 数据不足。"
                    description="该诊断仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。诊断结果为规则化归因，不是 AI 投资建议，也不构成真实交易建议。"
                />
                {query.error ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqErrorState
                            title="Paper 执行诊断加载失败"
                            error={query.error as AppApiError}
                            description="执行诊断不可用（旧后端可能尚未提供该接口）；其余 Paper 模块不受影响。"
                            onRetry={() => query.refetch()}
                        />
                    </Space>
                ) : query.isFetching && !diagnostics ? (
                    <NqLoadingState message="加载 Paper 执行诊断中..."/>
                ) : !diagnostics ? (
                    <NqEmptyState description="暂无 Paper 执行诊断数据（接口未返回诊断结构）。"/>
                ) : diagnostics.overview.totalRuns === 0 ? (
                    <NqEmptyState description="暂无 Paper 执行诊断数据，创建并运行 Paper run 后自动生成执行归因。"/>
                ) : (
                    <PaperExecutionDiagnosticsBody diagnostics={diagnostics}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperExecutionDiagnosticsBody({diagnostics}: {diagnostics: PaperExecutionDiagnosticsResponse}) {
    const {overview, causeDistribution, runDiagnostics, strategyDiagnostics, publishDiagnostics} = diagnostics;

    const [causeFilter, setCauseFilter] = useState<ExecutionCauseFilter>('all');
    const [severityFilter, setSeverityFilter] = useState<ExecutionSeverityFilter>('all');

    // 筛选只作用于 Run Diagnostics 表（cause 按 primaryCause；severity 按 run severity）；分组表保持完整。
    const filteredRuns = runDiagnostics.filter((r) =>
        (causeFilter === 'all' || r.primaryCause === causeFilter)
        && (severityFilter === 'all' || r.severity === severityFilter));
    const filtered = causeFilter !== 'all' || severityFilter !== 'all';

    const runColumns: ColumnsType<PaperExecutionRunDiagnostic> = [
        {title: 'Paper Run', dataIndex: 'paperRunId', key: 'paperRunId', width: 150, render: (v: string) => <span className="nq-mono">{v}</span>},
        {title: '状态', dataIndex: 'status', key: 'status', width: 100, render: (v: string) => <NqStatusTag status={v}/>},
        {title: '主因', key: 'primaryCause', width: 120, render: (_: unknown, r) => executionCauseTag(r.primaryCause)},
        {
            title: '辅助原因', key: 'secondaryCauses', width: 200,
            render: (_: unknown, r) => r.secondaryCauses.length > 0
                ? <Space size={4} wrap>{r.secondaryCauses.map((c) => <span key={c}>{executionCauseTag(c)}</span>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '严重度', key: 'severity', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.severity} tone={EXECUTION_SEVERITY_TONE[r.severity]}/>},
        {title: '可信度', key: 'causeConfidence', width: 110, render: (_: unknown, r) => <NqStatusTag status={r.causeConfidence} tone={EXECUTION_CONFIDENCE_TONE[r.causeConfidence]}/>},
        nqNumericColumn({title: '订单', dataIndex: 'orderCount', key: 'orderCount', width: 70}),
        nqNumericColumn({title: '成交', dataIndex: 'tradeCount', key: 'tradeCount', width: 70}),
        nqNumericColumn({
            title: '收益率', key: 'totalReturn', width: 100,
            render: (_: unknown, r: PaperExecutionRunDiagnostic) => r.totalReturn != null
                ? <NqPercentText value={r.totalReturn as string | number} ratio colorBySign/> : '-',
        }),
        nqNumericColumn({
            title: '最大回撤', key: 'maxDrawdown', width: 100,
            render: (_: unknown, r: PaperExecutionRunDiagnostic) => r.maxDrawdown != null
                ? <NqPercentText value={r.maxDrawdown as string | number} ratio signed={false}/> : '-',
        }),
        {
            title: '诊断说明 / 建议', key: 'explanation', width: 340,
            render: (_: unknown, r) => (
                <Space direction="vertical" size={2} style={{display: 'flex'}}>
                    <Typography.Text style={{fontSize: 12}}>{r.explanation}</Typography.Text>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>建议：{r.suggestedAction}</Typography.Text>
                </Space>
            ),
        },
    ];

    const groupColumns = (dimensionTitle: string): ColumnsType<PaperExecutionGroupDiagnostic> => [
        {title: dimensionTitle, dataIndex: 'key', key: 'key', width: 180, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run 数', dataIndex: 'runCount', key: 'runCount', width: 80}),
        {title: '主因', key: 'primaryCause', width: 120, render: (_: unknown, g) => executionCauseTag(g.primaryCause)},
        {
            title: 'Top 原因', key: 'topCauses', width: 220,
            render: (_: unknown, g) => g.topCauses.length > 0
                ? <Space size={4} wrap>{g.topCauses.map((c) => <span key={c}>{executionCauseTag(c)}</span>)}</Space>
                : <Typography.Text type="secondary">-</Typography.Text>,
        },
        {title: '严重度', key: 'severity', width: 100, render: (_: unknown, g) => <NqStatusTag status={g.severity} tone={EXECUTION_SEVERITY_TONE[g.severity]}/>},
        {title: '可信度', key: 'causeConfidence', width: 100, render: (_: unknown, g) => <NqStatusTag status={g.causeConfidence} tone={EXECUTION_CONFIDENCE_TONE[g.causeConfidence]}/>},
        nqNumericColumn({title: '无订单', dataIndex: 'noOrderCount', key: 'noOrderCount', width: 80}),
        nqNumericColumn({title: '有单无成交', dataIndex: 'orderNoFillCount', key: 'orderNoFillCount', width: 100}),
        nqNumericColumn({title: '成交亏损', dataIndex: 'filledLossCount', key: 'filledLossCount', width: 90}),
        nqNumericColumn({title: '风控拦截', dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 90}),
        nqNumericColumn({title: '数据不足', dataIndex: 'dataInsufficientCount', key: 'dataInsufficientCount', width: 90}),
        nqNumericColumn({title: '高回撤', dataIndex: 'highDrawdownCount', key: 'highDrawdownCount', width: 80}),
    ];

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* A) 诊断总览（按事实独立计数，桶可重叠） */}
            <div className="nq-status-strip">
                <NqMetricCard label="纳入诊断 run" value={String(overview.totalRuns)} footer="bounded Paper run"/>
                <NqMetricCard label="无订单" value={String(overview.noOrderRunCount)} tone={overview.noOrderRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="有订单无成交" value={String(overview.orderNoFillRunCount)} tone={overview.orderNoFillRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="成交亏损" value={String(overview.filledLossRunCount)} tone={overview.filledLossRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="风控拦截" value={String(overview.riskBlockedRunCount)} tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="数据不足" value={String(overview.dataInsufficientRunCount)} tone={overview.dataInsufficientRunCount > 0 ? 'warning' : 'muted'}/>
                <NqMetricCard label="高回撤" value={String(overview.highDrawdownRunCount)} tone={overview.highDrawdownRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="异常终态" value={String(overview.failedRunCount)} tone={overview.failedRunCount > 0 ? 'danger' : 'muted'}/>
                <NqMetricCard label="运行中" value={String(overview.runningRunCount)} tone={overview.runningRunCount > 0 ? 'success' : 'muted'}/>
            </div>

            {/* B) Cause Distribution（主因分布） */}
            <Card size="small" title="主因分布（按 run primaryCause 聚合）">
                <div role="region" aria-label="Paper 执行诊断主因分布表">
                    <NqDataTable<PaperExecutionDiagnosticCauseDistribution>
                        rowKey="cause"
                        pagination={false}
                        dataSource={causeDistribution}
                        columns={[
                            {title: '原因', key: 'cause', width: 140, render: (_: unknown, d) => executionCauseTag(d.cause)},
                            nqNumericColumn({title: 'Run 数', dataIndex: 'count', key: 'count', width: 90}),
                            {title: '严重度', key: 'severity', width: 110, render: (_: unknown, d) => <NqStatusTag status={d.severity} tone={EXECUTION_SEVERITY_TONE[d.severity]}/>},
                            {title: '代表可信度', key: 'confidence', width: 120, render: (_: unknown, d) => <NqStatusTag status={d.confidence} tone={EXECUTION_CONFIDENCE_TONE[d.confidence]}/>},
                            {title: '说明', dataIndex: 'description', key: 'description', render: (v: string) => <Typography.Text type="secondary" style={{fontSize: 12}}>{v}</Typography.Text>},
                        ]}
                        scroll={{x: 720}}
                        locale={{emptyText: '暂无主因分布。'}}
                    />
                </div>
            </Card>

            {/* C) Run Diagnostics（单 run 诊断，受 cause / severity 筛选） */}
            <Card
                size="small"
                title={filtered
                    ? `Run 执行诊断 · 当前筛选命中 ${filteredRuns.length} 条`
                    : 'Run 执行诊断'}
                extra={filtered ? (
                    <Button size="small" type="link" onClick={() => {setCauseFilter('all'); setSeverityFilter('all');}}>查看全部</Button>
                ) : null}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div
                        role="group"
                        aria-label="Paper 执行诊断筛选"
                        style={{display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center'}}
                    >
                        <Typography.Text type="secondary" style={{fontSize: 12}}>原因筛选</Typography.Text>
                        <Select<ExecutionCauseFilter>
                            size="small"
                            value={causeFilter}
                            onChange={setCauseFilter}
                            options={EXECUTION_CAUSE_FILTER_OPTIONS as Array<{label: string; value: ExecutionCauseFilter}>}
                            style={{width: 230}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>严重度筛选</Typography.Text>
                        <Select<ExecutionSeverityFilter>
                            size="small"
                            value={severityFilter}
                            onChange={setSeverityFilter}
                            options={EXECUTION_SEVERITY_FILTER_OPTIONS as Array<{label: string; value: ExecutionSeverityFilter}>}
                            style={{width: 150}}
                            virtual={false}
                        />
                        <Typography.Text type="secondary" style={{fontSize: 12}}>命中 {filteredRuns.length} / {runDiagnostics.length} 个 run</Typography.Text>
                    </div>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        confidence 表示该诊断原因由事实直接判断或推断得到，不代表真实交易结论。HIGH=明确事实，MEDIUM=推断归因，LOW=信息不足。
                    </Typography.Text>
                    <div role="region" aria-label="Paper 执行诊断 Run 表">
                        <NqDataTable<PaperExecutionRunDiagnostic>
                            rowKey="paperRunId"
                            pagination={false}
                            dataSource={filteredRuns}
                            columns={runColumns}
                            scroll={{x: 1560, y: 320}}
                            locale={{emptyText: '当前筛选条件下暂无匹配的 Run 诊断。'}}
                        />
                    </div>
                </Space>
            </Card>

            {/* D) Strategy Diagnostics（strategyVersionId 维度聚合） */}
            <Card size="small" title="Strategy Version 执行诊断聚合">
                <div role="region" aria-label="Paper 执行诊断 Strategy 表">
                    <NqDataTable<PaperExecutionGroupDiagnostic>
                        rowKey="key"
                        pagination={false}
                        dataSource={strategyDiagnostics}
                        columns={groupColumns('策略版本')}
                        scroll={{x: 1360, y: 280}}
                        locale={{emptyText: '暂无可聚合的策略版本执行诊断。'}}
                    />
                </div>
            </Card>

            {/* E) Publish Diagnostics（publishId 维度聚合） */}
            <Card size="small" title="Publish 执行诊断聚合">
                <div role="region" aria-label="Paper 执行诊断 Publish 表">
                    <NqDataTable<PaperExecutionGroupDiagnostic>
                        rowKey="key"
                        pagination={false}
                        dataSource={publishDiagnostics}
                        columns={groupColumns('发布')}
                        scroll={{x: 1360, y: 280}}
                        locale={{emptyText: '暂无可聚合的发布执行诊断。'}}
                    />
                </div>
            </Card>

            <Typography.Text type="secondary" style={{fontSize: 12}}>
                诊断总览按事实独立计数（一个 run 可同时计入多个桶）；主因 / 辅助原因按规则优先级归因。仅 Paper 模拟口径，不构成真实投资建议。
            </Typography.Text>
        </Space>
    );
}

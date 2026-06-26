import {Card, Descriptions, Space, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';

import {
    NqAmountText,
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
import {usePaperPortfolioSummaryQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperPortfolioGroup, PaperPortfolioSummaryResponse} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

import {pnlTone, toNullableNumber} from './paperFormatters';
import {renderRunRefTags} from './paperPortfolioShared';

function portfolioGroupColumns(keyTitle: string): ColumnsType<PaperPortfolioGroup> {
    return [
        {title: keyTitle, dataIndex: 'key', key: 'key', width: 200, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: 'Run 数', dataIndex: 'runCount', key: 'runCount', width: 80}),
        nqNumericColumn({title: '当前权益', dataIndex: 'currentEquity', key: 'currentEquity', width: 130, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({title: '总 PnL', dataIndex: 'totalPnl', key: 'totalPnl', width: 130, render: (v) => <NqAmountText value={v as string | number | null} signed colorBySign/>}),
        nqNumericColumn({
            title: '累计收益率',
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">数据不足</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: '最大回撤',
            dataIndex: 'worstDrawdown',
            key: 'worstDrawdown',
            width: 110,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: '风控拦截', dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 90}),
        nqNumericColumn({title: '未处理告警', dataIndex: 'openAlertCount', key: 'openAlertCount', width: 100}),
        {title: '最近运行', dataIndex: 'lastRunTime', key: 'lastRunTime', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

/**
 * PaperPortfolioDashboard —— Paper 组合看板（GateJ 后产品化 Loop-13）。
 * 只读消费后端 /paper-trading/portfolio/summary 单请求聚合结果：组合总览、策略/发布排行、Run 排行与数据质量。
 * 仅代表 SIM/Paper 模拟运行表现，不代表 LIVE 或真实交易；数据不足时不伪造收益率。
 */
export function PaperPortfolioDashboard({query}: {query: ReturnType<typeof usePaperPortfolioSummaryQuery>}) {
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    return (
      <section aria-label="Paper 组合看板">
        <Card
            className="page-section"
            bordered={false}
            title="Paper 组合看板"
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>SIM/Paper only · LIVE 未开启</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="info"
                    message="跨多个 Paper run 只读汇总组合表现。"
                    description="该组合看板仅基于 Paper 模拟运行与本地执行事实，不代表 LIVE 或真实交易表现。"
                />
                {query.error ? (
                    <NqErrorState
                        title="Paper 组合看板加载失败"
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description="暂无 Paper run，创建并运行后自动汇总组合表现。"/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>数据不足，无法计算组合收益率</Typography.Text>
                    </Space>
                ) : (
                    <PaperPortfolioDashboardBody portfolio={portfolio}/>
                )}
            </Space>
        </Card>
      </section>
    );
}

function PaperPortfolioDashboardBody({portfolio}: {portfolio: PaperPortfolioSummaryResponse}) {
    const {overview, strategyGroups, publishGroups, highlights, dataQuality} = portfolio;
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* 组合总览指标 */}
            <div className="nq-status-strip">
                <NqMetricCard label="Paper run 总数" value={String(overview.totalRuns)} footer={`可比 ${overview.returnEligibleRunCount} 个`}/>
                <NqMetricCard label="当前总资产" value={<NqAmountText value={overview.totalCurrentEquity}/>}/>
                <NqMetricCard
                    label="总 PnL"
                    value={<NqAmountText value={overview.totalPnl} signed colorBySign/>}
                    tone={pnlTone(toNullableNumber(overview.totalPnl))}
                />
                <NqMetricCard
                    label="累计收益率"
                    value={overview.totalReturn !== null
                        ? <NqPercentText value={overview.totalReturn} ratio colorBySign/>
                        : <Typography.Text type="secondary">数据不足</Typography.Text>}
                />
                <NqMetricCard
                    label="最大回撤"
                    value={overview.worstRunDrawdown !== null
                        ? <NqPercentText value={overview.worstRunDrawdown} ratio signed={false}/>
                        : '-'}
                    tone="warning"
                    footer="按单 run 最大回撤统计"
                />
                <NqMetricCard label="初始总资金" value={<NqAmountText value={overview.totalInitialEquity}/>}/>
                <NqMetricCard label="RUNNING 数量" value={String(overview.runningCount)}/>
                <NqMetricCard
                    label="风控拦截"
                    value={String(overview.riskBlockedRunCount)}
                    tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}
                />
                <NqMetricCard
                    label="未处理告警"
                    value={String(overview.openAlertCount)}
                    tone={overview.openAlertCount > 0 ? 'warning' : 'muted'}
                />
            </div>
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                状态分布：RUNNING {overview.runningCount} · STOPPED {overview.stoppedCount} · FAILED {overview.failedCount}
                {' '}· CANCELLED {overview.cancelledCount} · CREATED {overview.createdCount}
            </Typography.Text>

            {/* 策略 / 发布维度排行 */}
            <Card size="small" title="Strategy Version 收益排行">
                <NqDataTable<PaperPortfolioGroup>
                    rowKey="key"
                    pagination={false}
                    dataSource={strategyGroups}
                    columns={portfolioGroupColumns('策略版本')}
                    scroll={{x: 1020, y: 240}}
                    locale={{emptyText: '暂无可分组的策略版本数据。'}}
                />
            </Card>
            <Card size="small" title="Publish 收益排行">
                <NqDataTable<PaperPortfolioGroup>
                    rowKey="key"
                    pagination={false}
                    dataSource={publishGroups}
                    columns={portfolioGroupColumns('发布')}
                    scroll={{x: 1020, y: 240}}
                    locale={{emptyText: '暂无可分组的发布数据。'}}
                />
            </Card>

            {/* Run 排行 / 风险清单 */}
            <Card size="small" title="Run 排行 / 风险清单">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div className="nq-status-strip">
                        <NqMetricCard
                            label="收益最高"
                            value={highlights.topWinner ? <NqAmountText value={highlights.topWinner.totalPnl} signed colorBySign/> : '-'}
                            footer={highlights.topWinner ? highlights.topWinner.paperRunId : '暂无数据'}
                            tone={highlights.topWinner ? pnlTone(toNullableNumber(highlights.topWinner.totalPnl)) : 'muted'}
                        />
                        <NqMetricCard
                            label="回撤最大"
                            value={highlights.worstDrawdown && highlights.worstDrawdown.maxDrawdown !== null
                                ? <NqPercentText value={highlights.worstDrawdown.maxDrawdown} ratio signed={false}/>
                                : '-'}
                            footer={highlights.worstDrawdown ? highlights.worstDrawdown.paperRunId : '暂无数据'}
                            tone="warning"
                        />
                        <NqMetricCard
                            label="风险最高"
                            value={highlights.highestRisk
                                ? <NqStatusTag status={highlights.highestRisk.riskBlocked ? '风控拦截' : '告警'} tone={highlights.highestRisk.riskBlocked ? 'danger' : 'warning'}/>
                                : '-'}
                            footer={highlights.highestRisk ? highlights.highestRisk.paperRunId : '暂无数据'}
                        />
                        <NqMetricCard
                            label="最近活跃"
                            value={highlights.mostRecent
                                ? <span className="nq-num" style={{fontSize: 13}}>{formatDateTime(highlights.mostRecent.lastActivityAt)}</span>
                                : '-'}
                            footer={highlights.mostRecent ? highlights.mostRecent.paperRunId : '暂无数据'}
                        />
                    </div>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label="风控拦截 run">{renderRunRefTags(highlights.riskBlockedRuns)}</Descriptions.Item>
                        <Descriptions.Item label="无交易 run">{renderRunRefTags(highlights.noTradeRuns)}</Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 数据质量提示 */}
            <Card size="small" title="数据质量提示">
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        数据不足的 run 不参与组合收益率计算，避免伪造收益。
                    </Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={`缺 equity（${dataQuality.missingEquityRuns.length}）`}>
                            {renderRunRefTags(dataQuality.missingEquityRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={`数据不足（${dataQuality.dataInsufficientRuns.length}）`}>
                            {renderRunRefTags(dataQuality.dataInsufficientRuns)}
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

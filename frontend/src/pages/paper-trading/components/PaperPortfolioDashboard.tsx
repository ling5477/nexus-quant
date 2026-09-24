import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Card, Descriptions, Space, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';

import {NqAmountText, NqDataTable, NqEmptyState, NqErrorState, NqLoadingState, NqMetricCard, NqPercentText, NqRiskBanner, nqNumericColumn} from '@/components/nq';
import {usePaperPortfolioSummaryQuery} from '@/hooks/usePaperTradingQuery';
import type {AppApiError} from '@/types/api';
import type {PaperPortfolioGroup, PaperPortfolioSummaryResponse} from '@/types/paper-trading';
import {formatDateTime} from '@/utils/formatters';

import {pnlTone, toNullableNumber} from './paperFormatters';
import {renderRunRefTags} from './paperPortfolioShared';

function portfolioGroupColumns(keyTitle: string): ColumnsType<PaperPortfolioGroup> {
    return [
        {title: keyTitle, dataIndex: 'key', key: 'key', width: 200, render: (v: string) => <span className="nq-mono">{v}</span>},
        nqNumericColumn({title: t('pages:runCount'), dataIndex: 'runCount', key: 'runCount', width: 80}),
        nqNumericColumn({title: t('pages:currentEquity'), dataIndex: 'currentEquity', key: 'currentEquity', width: 130, render: (v) => <NqAmountText value={v as string | number | null}/>}),
        nqNumericColumn({title: t('pages:totalPnl'), dataIndex: 'totalPnl', key: 'totalPnl', width: 130, render: (v) => <NqAmountText value={v as string | number | null} signed colorBySign/>}),
        nqNumericColumn({
            title: t('pages:cumulativeReturn'),
            dataIndex: 'totalReturn',
            key: 'totalReturn',
            width: 110,
            render: (v) => (v === null || v === undefined
                ? <Typography.Text type="secondary">{t('pages:insufficientData')}</Typography.Text>
                : <NqPercentText value={v as string | number} ratio colorBySign/>),
        }),
        nqNumericColumn({
            title: t('pages:maximumDrawdown'),
            dataIndex: 'worstDrawdown',
            key: 'worstDrawdown',
            width: 110,
            render: (v) => (v === null || v === undefined ? '-' : <NqPercentText value={v as string | number} ratio signed={false}/>),
        }),
        nqNumericColumn({title: t('pages:riskBlocked'), dataIndex: 'riskBlockedCount', key: 'riskBlockedCount', width: 90}),
        nqNumericColumn({title: t('pages:unresolvedAlerts'), dataIndex: 'openAlertCount', key: 'openAlertCount', width: 100}),
        {title: t('pages:latestRun'), dataIndex: 'lastRunTime', key: 'lastRunTime', width: 170, render: (v: string | null) => formatDateTime(v)},
    ];
}

/**
 * PaperPortfolioDashboard —— Paper 组合看板（GateJ 后产品化 Loop-13）。
 * 只读消费后端 /paper-trading/portfolio/summary 单请求聚合结果：组合总览、策略/发布排行、Run 排行与数据质量。
 * 仅代表 SIM/Paper 模拟运行表现，不代表 LIVE 或真实交易；数据不足时不伪造收益率。
 */
export function PaperPortfolioDashboard({query}: {query: ReturnType<typeof usePaperPortfolioSummaryQuery>}) {
    useTranslation('pages');
    const raw = query.data;
    const portfolio: PaperPortfolioSummaryResponse | null =
        raw && !Array.isArray(raw) && (raw as PaperPortfolioSummaryResponse).overview
            ? (raw as PaperPortfolioSummaryResponse)
            : null;

    return (
      <section aria-label={t('pages:paperPortfolioDashboard')}>
        <Card
            className="page-section"
            bordered={false}
            title={t('pages:paperPortfolioDashboard')}
            extra={<Typography.Text type="secondary" style={{fontSize: 12}}>{t('pages:simPaperOnlyLiveDisabled')}</Typography.Text>}
        >
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="info"
                    message={t('pages:readOnlyPortfolioPerformanceAcrossPaperRuns')}
                    description={t('pages:thisPortfolioDashboardUsesPaperSimulationAndLocalExecutionFactsOnlyItDoesNotRepresentLiveOrRealTradi')}
                />
                {query.error ? (
                    <NqErrorState
                        title={t('pages:failedToLoadThePaperPortfolio')}
                        error={query.error as AppApiError}
                        onRetry={() => query.refetch()}
                    />
                ) : query.isFetching && !portfolio ? (
                    <NqLoadingState/>
                ) : !portfolio || portfolio.overview.totalRuns === 0 ? (
                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                        <NqEmptyState description={t('pages:createAndExecutePaperRunsToAggregatePortfolioPerformance')}/>
                        <Typography.Text type="warning" style={{fontSize: 12}}>{t('pages:insufficientDataToCalculatePortfolioReturn')}</Typography.Text>
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
    useTranslation('pages');
    const {overview, strategyGroups, publishGroups, highlights, dataQuality} = portfolio;
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            {/* 组合总览指标 */}
            <div className="nq-status-strip">
                <NqMetricCard label={t('pages:totalPaperRuns2')} value={String(overview.totalRuns)} footer={t('pages:value1Comparable', {value1: overview.returnEligibleRunCount})}/>
                <NqMetricCard label={t('pages:currentTotalAssets')} value={<NqAmountText value={overview.totalCurrentEquity}/>}/>
                <NqMetricCard
                    label={t('pages:totalPnl')}
                    value={<NqAmountText value={overview.totalPnl} signed colorBySign/>}
                    tone={pnlTone(toNullableNumber(overview.totalPnl))}
                />
                <NqMetricCard
                    label={t('pages:cumulativeReturn')}
                    value={overview.totalReturn !== null
                        ? <NqPercentText value={overview.totalReturn} ratio colorBySign/>
                        : <Typography.Text type="secondary">{t('pages:insufficientData')}</Typography.Text>}
                />
                <NqMetricCard
                    label={t('pages:maximumDrawdown')}
                    value={overview.worstRunDrawdown !== null
                        ? <NqPercentText value={overview.worstRunDrawdown} ratio signed={false}/>
                        : '-'}
                    tone="warning"
                    footer={t('pages:basedOnMaximumDrawdownPerRun')}
                />
                <NqMetricCard label={t('pages:totalInitialCapital')} value={<NqAmountText value={overview.totalInitialEquity}/>}/>
                <NqMetricCard label={t('pages:runningCount')} value={String(overview.runningCount)}/>
                <NqMetricCard
                    label={t('pages:riskBlocked')}
                    value={String(overview.riskBlockedRunCount)}
                    tone={overview.riskBlockedRunCount > 0 ? 'danger' : 'muted'}
                />
                <NqMetricCard
                    label={t('pages:unresolvedAlerts')}
                    value={String(overview.openAlertCount)}
                    tone={overview.openAlertCount > 0 ? 'warning' : 'muted'}
                />
            </div>
            <Typography.Text type="secondary" style={{fontSize: 12}}>
                {t('pages:statusDistributionRunning')}{overview.runningCount} · STOPPED {overview.stoppedCount} · FAILED {overview.failedCount}
                {' '}· CANCELLED {overview.cancelledCount} · CREATED {overview.createdCount}
            </Typography.Text>

            {/* 策略 / 发布维度排行 */}
            <Card size="small" title={t('pages:strategyVersionReturnRanking')}>
                <NqDataTable<PaperPortfolioGroup>
                    rowKey="key"
                    pagination={false}
                    dataSource={strategyGroups}
                    columns={portfolioGroupColumns(t('pages:strategyVersions'))}
                    scroll={{x: 1020, y: 240}}
                    locale={{emptyText: t('pages:noStrategyVersionDataAvailableToGroup')}}
                />
            </Card>
            <Card size="small" title={t('pages:publishReturnRanking')}>
                <NqDataTable<PaperPortfolioGroup>
                    rowKey="key"
                    pagination={false}
                    dataSource={publishGroups}
                    columns={portfolioGroupColumns(t('pages:publish2'))}
                    scroll={{x: 1020, y: 240}}
                    locale={{emptyText: t('pages:noPublishDataAvailableToGroup')}}
                />
            </Card>

            {/* Run 排行 / 风险清单 */}
            <Card size="small" title={t('pages:runRankingsRiskList')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <div className="nq-status-strip">
                        <NqMetricCard
                            label={t('pages:highestReturn')}
                            value={highlights.topWinner ? <NqAmountText value={highlights.topWinner.totalPnl} signed colorBySign/> : '-'}
                            footer={highlights.topWinner ? highlights.topWinner.paperRunId : t('pages:noData')}
                            tone={highlights.topWinner ? pnlTone(toNullableNumber(highlights.topWinner.totalPnl)) : 'muted'}
                        />
                        <NqMetricCard
                            label={t('pages:largestDrawdown')}
                            value={highlights.worstDrawdown && highlights.worstDrawdown.maxDrawdown !== null
                                ? <NqPercentText value={highlights.worstDrawdown.maxDrawdown} ratio signed={false}/>
                                : '-'}
                            footer={highlights.worstDrawdown ? highlights.worstDrawdown.paperRunId : t('pages:noData')}
                            tone="warning"
                        />
                        <NqMetricCard
                            label={t('pages:highestRisk')}
                            value={highlights.highestRisk
                                ? <StatusTag title="" variant="pill" status={highlights.highestRisk.riskBlocked ? t('pages:riskBlocked') : t('pages:alert')} tone={highlights.highestRisk.riskBlocked ? 'danger' : 'warning'}/>
                                : '-'}
                            footer={highlights.highestRisk ? highlights.highestRisk.paperRunId : t('pages:noData')}
                        />
                        <NqMetricCard
                            label={t('pages:recentlyActive')}
                            value={highlights.mostRecent
                                ? <span className="nq-num" style={{fontSize: 13}}>{formatDateTime(highlights.mostRecent.lastActivityAt)}</span>
                                : '-'}
                            footer={highlights.mostRecent ? highlights.mostRecent.paperRunId : t('pages:noData')}
                        />
                    </div>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={t('pages:riskBlockedRuns')}>{renderRunRefTags(highlights.riskBlockedRuns)}</Descriptions.Item>
                        <Descriptions.Item label={t('pages:runsWithoutTrades')}>{renderRunRefTags(highlights.noTradeRuns)}</Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>

            {/* 数据质量提示 */}
            <Card size="small" title={t('pages:dataQualityNotes')}>
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        {t('pages:runsWithInsufficientDataAreExcludedFromPortfolioReturnsToAvoidFabricatedResults')}</Typography.Text>
                    <Descriptions bordered size="small" column={1}>
                        <Descriptions.Item label={t('pages:missingEquityValue1', {value1: dataQuality.missingEquityRuns.length})}>
                            {renderRunRefTags(dataQuality.missingEquityRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:insufficientDataValue1', {value1: dataQuality.dataInsufficientRuns.length})}>
                            {renderRunRefTags(dataQuality.dataInsufficientRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:missingBacktestSourceValue1', {value1: dataQuality.missingBacktestSourceRuns.length})}>
                            {renderRunRefTags(dataQuality.missingBacktestSourceRuns)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:missingPublishSourceValue1', {value1: dataQuality.missingPublishSourceRuns.length})}>
                            {renderRunRefTags(dataQuality.missingPublishSourceRuns)}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </Card>
        </Space>
    );
}

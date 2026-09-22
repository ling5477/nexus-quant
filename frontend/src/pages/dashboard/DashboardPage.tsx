import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {ArrowRightOutlined} from '@ant-design/icons';
import {Button, Card, Col, List, Row, Space, Tag, Typography} from 'antd';
import {useMemo} from 'react';
import {Link, useNavigate} from 'react-router-dom';

import {NqEmptyState, NqErrorState, NqMetricCard, NqPageHeader, NqPercentText, NqRiskBanner, NqStatusTag, formatNqNumber} from '@/components/nq';
import {
    usePaperAlertsQuery,
    usePaperDailyReportsQuery,
    usePaperHeartbeatsQuery,
    usePaperRecoveryEventsQuery,
    usePaperTradingListQuery,
    usePaperTradingRiskResultsQuery,
} from '@/hooks/usePaperTradingQuery';
import {appNavItems} from '@/router/navigation';
import {StatusTag} from '@/nq-design-system/status/StatusTag';
import type {AppApiError} from '@/types/api';
import type {PaperTradingRunItem} from '@/types/paper-trading';
import {appEnv} from '@/utils/env';
import {formatDateTime} from '@/utils/formatters';
import './DashboardPage.css';
import {hasCurrentFocusEvidence} from './dashboard-evidence';

/**
 * DashboardPage — 安全总览。
 *
 * 目标：打开系统就能回答“现在是否安全”。
 * 数据边界：只复用既有 paper-trading 查询接口；除 run 列表外，其余指标
 * 聚焦在一个“焦点 run”（最近活跃的 RUNNING run）上，避免对全部 run 做 N+1 轮询。
 * 当前产品口径：仅 Paper Trading，LIVE 能力 disabled。
 */
export function DashboardPage() {
    const {i18n: pageI18n} = useTranslation('pages');
    const navigate = useNavigate();

    // searchVersion 固定为 1：总览页打开即加载，不需要手动触发查询
    const runsQuery = usePaperTradingListQuery({}, 1);
    const runs = useMemo(() => runsQuery.data ?? [], [runsQuery.data]);

    const focusRun = useMemo<PaperTradingRunItem | null>(() => {
        if (runs.length === 0) {
            return null;
        }
        const byUpdatedDesc = (left: PaperTradingRunItem, right: PaperTradingRunItem) =>
            new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
        const running = runs.filter((run) => run.status === 'RUNNING').sort(byUpdatedDesc);

        return running[0] ?? [...runs].sort(byUpdatedDesc)[0] ?? null;
    }, [runs]);

    const focusRunId = focusRun?.paperRunId ?? null;
    const dailyReportsQuery = usePaperDailyReportsQuery(focusRunId);
    const alertsQuery = usePaperAlertsQuery(focusRunId);
    const heartbeatsQuery = usePaperHeartbeatsQuery(focusRunId);
    const riskResultsQuery = usePaperTradingRiskResultsQuery(focusRunId);
    const recoveryEventsQuery = usePaperRecoveryEventsQuery(focusRunId);

    const runningCount = runs.filter((run) => run.status === 'RUNNING').length;
    const failedCount = runs.filter((run) => run.status === 'FAILED').length;
    const runningStrategyCount = new Set(
        runs.filter((run) => run.status === 'RUNNING' && run.strategyVersionId).map((run) => run.strategyVersionId),
    ).size;

    const openAlerts = (alertsQuery.data ?? []).filter((alert) => alert.status === 'OPEN');
    const openCriticalCount = openAlerts.filter((alert) => alert.severity === 'CRITICAL').length;
    // 不依赖后端返回顺序，取“最新”前先按时间显式排序
    const latestHeartbeat = useMemo(() => [...(heartbeatsQuery.data ?? [])]
        .sort((left, right) => new Date(right.heartbeatTime).getTime() - new Date(left.heartbeatTime).getTime())[0] ?? null, [heartbeatsQuery.data]);
    const latestRiskResult = useMemo(() => [...(riskResultsQuery.data ?? [])]
        .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())[0] ?? null, [riskResultsQuery.data]);
    const latestDailyReport = useMemo(() => [...(dailyReportsQuery.data ?? [])]
        .sort((left, right) => right.reportDate.localeCompare(left.reportDate))[0] ?? null, [dailyReportsQuery.data]);

    const recentEvents = useMemo(() => {
        const alertEvents = (alertsQuery.data ?? []).slice(0, 5).map((alert) => ({
            key: `alert-${alert.alertId}`,
            kind: t('pages:alert'),
            status: alert.status,
            tone: alert.status === 'OPEN' ? ('danger' as const) : undefined,
            title: alert.title,
            time: alert.createdAt,
        }));
        const recoveryEvents = (recoveryEventsQuery.data ?? []).slice(0, 5).map((event) => ({
            key: `recovery-${event.recoveryEventId}`,
            kind: t('pages:recovery'),
            status: event.status,
            tone: undefined,
            title: event.recoveryType + (event.reason ? `：${event.reason}` : ''),
            time: event.startedAt,
        }));

        return [...alertEvents, ...recoveryEvents]
            .sort((left, right) => new Date(right.time).getTime() - new Date(left.time).getTime())
            .slice(0, 8);
    }, [alertsQuery.data, recoveryEventsQuery.data, pageI18n.resolvedLanguage]);

    const focusEvidenceAvailable = hasCurrentFocusEvidence(alertsQuery, heartbeatsQuery, latestHeartbeat?.status);
    const runsAvailable = runsQuery.isSuccess;
    // 已知异常优先；证据不全不报健康，完整焦点证据也不代表全局健康或实时授权。
    const banner = useMemo(() => {
        if (runsQuery.error) {
            return {
                level: 'danger' as const,
                message: t('pages:unableToRetrievePaperTradingStatus'),
                description: t('pages:theRunQueryFailedAndSystemSafetyIsUnknownCheckTheApiAndBackendServiceFirst'),
            };
        }
        if (failedCount > 0 || openCriticalCount > 0) {
            return {
                level: 'danger' as const,
                message: failedCount > 0 ? t('pages:failedPaperRuns', {count: failedCount}) : t('pages:unresolvedCriticalAlerts'),
                description: t('pages:reviewFailedRunsAndAlertsOnThePaperTradingPageBeforeContinuing'),
            };
        }
        if (openAlerts.length > 0 || latestHeartbeat?.status === 'LAGGING' || latestHeartbeat?.status === 'STOPPED') {
            return {
                level: 'warning' as const,
                message: openAlerts.length > 0 ? t('pages:unresolvedAlertCount', {count: openAlerts.length}) : t('pages:focusedRunHeartbeatIsAbnormal'),
                description: t('pages:theSystemIsRunningButRiskSignalsRequireManualReview'),
            };
        }
        if (runningCount > 0) {
            if (!focusEvidenceAvailable || runsQuery.isFetching) {
                return {
                    level: 'warning' as const,
                    message: t('dashboardEvidence.unknown'),
                    description: t('dashboardEvidence.incomplete'),
                };
            }
            return {
                level: 'info' as const,
                message: t('dashboardEvidence.running'),
                description: t('dashboardEvidence.scoped'),
            };
        }

        if (!runsAvailable || runsQuery.isFetching) {
            return {level: 'info' as const, message: t('dashboardEvidence.unknown'), description: t('dashboardEvidence.incomplete')};
        }

        return {
            level: 'info' as const,
            message: t('pages:noActivePaperRun'),
            description: t('pages:theSystemIsIdleCreateAndStartAPaperRunOnThePaperTradingPage'),
        };
    }, [failedCount, latestHeartbeat, openAlerts.length, openCriticalCount, runningCount, runsQuery.error, runsQuery.isFetching, runsAvailable, focusEvidenceAvailable, pageI18n.resolvedLanguage]);

    const focusLoading = Boolean(focusRunId) && dailyReportsQuery.isPending;

    return (
        <div className="nq-dashboard">
            <Card className="page-card nq-dashboard__heading" bordered={false}>
                <NqPageHeader
                    title={t('pages:dashboard')}
                    description={t('pages:safetyOverviewOfSystemHealthTheCurrentEnvironmentPaperTradingStatusAndRiskSignals')}
                    badge={<Tag color="processing">{appEnv.envLabel}</Tag>}
                    tip={(
                        <NqRiskBanner
                            level={banner.level}
                            message={banner.message}
                            description={t('pages:dashboardEnvironmentBoundary', {description: banner.description, environment: appEnv.envLabel})}
                        />
                    )}
                />
                {(runsQuery.error || heartbeatsQuery.error) && (
                    <NqErrorState title={t('queryFailed')} error={(runsQuery.error ?? heartbeatsQuery.error) as AppApiError}/>
                )}
            </Card>

            <div className="nq-status-strip nq-dashboard__metrics">
                <NqMetricCard
                    label={t('pages:totalPaperRuns')}
                    value={runsAvailable ? formatNqNumber(runs.length, {precision: 0}) : '-'}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label="RUNNING"
                    value={runsAvailable ? formatNqNumber(runningCount, {precision: 0}) : '-'}
                    tone={runningCount > 0 ? 'success' : 'muted'}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label="FAILED"
                    value={runsAvailable ? formatNqNumber(failedCount, {precision: 0}) : '-'}
                    tone={failedCount > 0 ? 'danger' : 'muted'}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label={t('pages:runningStrategies')}
                    value={runsAvailable ? formatNqNumber(runningStrategyCount, {precision: 0}) : '-'}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label={t('pages:unresolvedAlerts')}
                    value={focusRunId && alertsQuery.isSuccess ? formatNqNumber(openAlerts.length, {precision: 0}) : '-'}
                    tone={openAlerts.length > 0 ? 'warning' : 'muted'}
                    footer={focusRunId ? t('pages:focusedRunScope') : t('pages:noPaperRuns')}
                    loading={Boolean(focusRunId) && alertsQuery.isPending}
                />
                <NqMetricCard
                    label={t('pages:heartbeatStatus')}
                    value={heartbeatsQuery.isSuccess && latestHeartbeat ? <NqStatusTag status={latestHeartbeat.status}/> : '-'}
                    footer={latestHeartbeat ? formatDateTime(latestHeartbeat.heartbeatTime) : t('pages:focusedRunScope')}
                    loading={Boolean(focusRunId) && heartbeatsQuery.isPending}
                />
            </div>

            <Card
                data-testid="dashboard-runtime-readiness-card"
                className="page-section nq-dashboard__runtime"
                bordered={false}
                title={t('pages:runtimeReadiness')}
                extra={<NqStatusTag status="LIVE_DISABLED" tone="danger"/>}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <NqRiskBanner
                        level="warning"
                        message={t('pages:runtimeGuardedLiveDisabled')}
                        description={t('pages:theDashboardSummarizesRuntimeBoundariesAndHasNoTradingExecutionEntryPaperReadyDbFreshAndPermissionPr')}
                    />
                    <div className="nq-status-strip">
                        <NqMetricCard
                            label="LIVE"
                            value={<StatusTag status="Disabled" label={t('pages:disabled')} tone="danger" title="" variant="pill"/>}
                            tone="danger"
                        />
                        <NqMetricCard
                            label={t('pages:realProvider')}
                            value={<StatusTag status="Not implemented" label={t('pages:notImplemented')} tone="warning" title="" variant="pill"/>}
                            tone="warning"
                        />
                        <NqMetricCard
                            label={t('pages:paper')}
                            value={<StatusTag status="Simulated only" label={t('pages:simulationOnly')} tone="info" title="" variant="pill"/>}
                            tone="muted"
                        />
                        <NqMetricCard
                            label={t('pages:permissionProbe')}
                            value={<StatusTag status="Skipped / NoReal" label={t('pages:skippedNoReal')} tone="neutral" title="" variant="pill"/>}
                            tone="muted"
                        />
                    </div>
                    <Typography.Text type="secondary">
                        {t('pages:norealFakeStubFuturerealAreNotLiveReadyASkippedOrDisabledPermissionProbeIsNotVerified')}</Typography.Text>
                    <Space size={12} wrap>
                        <Link to="/runtime/readiness">{t('pages:viewRuntimeReadiness')}</Link>
                        <Link to="/marketdata">{t('pages:viewMarketDataReadiness')}</Link>
                    </Space>
                </Space>
            </Card>

            <Row className="nq-dashboard__activity" gutter={[12, 12]}>
                <Col span={24}>
                    <Card
                        className="page-section"
                        bordered={false}
                        title={t('pages:focusedPaperRunPerformance')}
                        extra={focusRun ? (
                            <Typography.Text type="secondary" className="nq-mono">
                                {focusRun.paperRunId}
                            </Typography.Text>
                        ) : null}
                    >
                        {dailyReportsQuery.error ? (
                            <NqErrorState title={t('queryFailed')} error={dailyReportsQuery.error as AppApiError}/>
                        ) : !focusRun ? (
                            <NqEmptyState description={t('pages:createAndStartAPaperRunToSeeItsLatestDailyMetricsHere')}/>
                        ) : !latestDailyReport && !focusLoading ? (
                            <NqEmptyState description={t('pages:noDailyReportForTheFocusedRunGenerateOneFromPaperTradingDetails')}/>
                        ) : (
                            <div className="nq-status-strip">
                                <NqMetricCard
                                    label={t('pages:totalEquity')}
                                    value={formatNqNumber(latestDailyReport?.totalEquity, {precision: 2})}
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label={t('pages:todaySPnl')}
                                    value={formatNqNumber(latestDailyReport?.dailyPnl, {precision: 2, signed: true})}
                                    tone={Number(latestDailyReport?.dailyPnl ?? 0) > 0 ? 'up' : Number(latestDailyReport?.dailyPnl ?? 0) < 0 ? 'down' : 'default'}
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label={t('pages:dailyReturn')}
                                    value={<NqPercentText value={latestDailyReport?.dailyReturn} ratio colorBySign/>}
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label={t('pages:maximumDrawdown')}
                                    value={<NqPercentText value={latestDailyReport?.maxDrawdown} ratio signed={false}/>}
                                    tone="warning"
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label={t('pages:riskStatus')}
                                    value={latestRiskResult ? <NqStatusTag status={latestRiskResult.status}/> : '-'}
                                    footer={latestRiskResult ? latestRiskResult.checkType : t('pages:noRiskCheckResults')}
                                    loading={Boolean(focusRunId) && riskResultsQuery.isPending}
                                />
                            </div>
                        )}
                        {latestDailyReport ? (
                            <Typography.Paragraph type="secondary" style={{margin: '12px 0 0', fontSize: 12}}>
                                {t('pages:dataSource')}{latestDailyReport.reportDate} {t('pages:dailyReportGeneratedAt')}{formatDateTime(latestDailyReport.generatedAt)}{t('pages:sentenceClose')}</Typography.Paragraph>
                        ) : null}
                    </Card>
                </Col>
                <Col span={24}>
                    <Card className="page-section" bordered={false} title={t('pages:recentEvents')}>
                        {!focusRunId ? (
                            <NqEmptyState description={t('pages:noPaperRunsTheEventFeedIsEmpty')}/>
                        ) : alertsQuery.error || recoveryEventsQuery.error ? (
                            <NqErrorState title={t('pages:failedToQueryEvents')} error={(alertsQuery.error ?? recoveryEventsQuery.error) as AppApiError}/>
                        ) : recentEvents.length === 0 ? (
                            <NqEmptyState description={t('pages:noAlertsOrRecoveryEventsForTheFocusedRun')}/>
                        ) : (
                            <List
                                size="small"
                                dataSource={recentEvents}
                                renderItem={(item) => (
                                    <List.Item>
                                        <Space size={8} style={{width: '100%', justifyContent: 'space-between'}}>
                                            <Space size={8}>
                                                <Tag>{item.kind}</Tag>
                                                <NqStatusTag status={item.status} tone={item.tone}/>
                                                <Typography.Text>{item.title}</Typography.Text>
                                            </Space>
                                            <Typography.Text type="secondary" className="nq-num" style={{fontSize: 12}}>
                                                {formatDateTime(item.time)}
                                            </Typography.Text>
                                        </Space>
                                    </List.Item>
                                )}
                            />
                        )}
                    </Card>
                </Col>
            </Row>

            <Card className="page-section nq-dashboard__workspaces" bordered={false} title={t('pages:workspaces')}>
                <Row gutter={[12, 12]}>
                    {appNavItems
                        .filter((item) => item.key !== 'dashboard')
                        .map((item) => (
                            <Col xs={24} md={12} xl={6} key={item.key}>
                                <Card hoverable size="small" onClick={() => navigate(item.path)}>
                                    <Space direction="vertical" size={4} style={{width: '100%'}}>
                                        <Space align="center" size={8}>
                                            {item.icon}
                                            <Typography.Text strong>{item.label}</Typography.Text>
                                        </Space>
                                        <Typography.Paragraph
                                            type="secondary"
                                            style={{margin: 0, fontSize: 12}}
                                            ellipsis={{rows: 2}}
                                        >
                                            {item.description}
                                        </Typography.Paragraph>
                                        <Button type="link" size="small" icon={<ArrowRightOutlined/>} style={{paddingInline: 0}}>
                                            {t('pages:openPage')}</Button>
                                    </Space>
                                </Card>
                            </Col>
                        ))}
                </Row>
            </Card>
        </div>
    );
}

import {ArrowRightOutlined} from '@ant-design/icons';
import {Button, Card, Col, List, Row, Space, Tag, Typography} from 'antd';
import {useMemo} from 'react';
import {useNavigate} from 'react-router-dom';

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
import type {AppApiError} from '@/types/api';
import type {PaperTradingRunItem} from '@/types/paper-trading';
import {appEnv} from '@/utils/env';
import {formatDateTime} from '@/utils/formatters';

/**
 * DashboardPage — 安全总览。
 *
 * 目标：打开系统就能回答“现在是否安全”。
 * 数据边界：只复用既有 paper-trading 查询接口；除 run 列表外，其余指标
 * 聚焦在一个“焦点 run”（最近活跃的 RUNNING run）上，避免对全部 run 做 N+1 轮询。
 * 当前阶段口径：GateJ completed，仅 Paper Trading，LIVE 能力 disabled。
 */
export function DashboardPage() {
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
            kind: '告警',
            status: alert.status,
            tone: alert.status === 'OPEN' ? ('danger' as const) : undefined,
            title: alert.title,
            time: alert.createdAt,
        }));
        const recoveryEvents = (recoveryEventsQuery.data ?? []).slice(0, 5).map((event) => ({
            key: `recovery-${event.recoveryEventId}`,
            kind: '恢复',
            status: event.status,
            tone: undefined,
            title: event.recoveryType + (event.reason ? `：${event.reason}` : ''),
            time: event.startedAt,
        }));

        return [...alertEvents, ...recoveryEvents]
            .sort((left, right) => new Date(right.time).getTime() - new Date(left.time).getTime())
            .slice(0, 8);
    }, [alertsQuery.data, recoveryEventsQuery.data]);

    // 安全横幅级别：失败 run / CRITICAL 告警 > 未处理告警 / 心跳滞后 > 正常运行 > 无 run
    const banner = useMemo(() => {
        if (runsQuery.error) {
            return {
                level: 'danger' as const,
                message: '无法获取 Paper Trading 运行状态',
                description: '运行列表查询失败，系统安全状态未知，请优先排查 API 与后端服务。',
            };
        }
        if (failedCount > 0 || openCriticalCount > 0) {
            return {
                level: 'danger' as const,
                message: failedCount > 0 ? `存在 ${failedCount} 个 FAILED Paper Run` : '存在未处理的 CRITICAL 告警',
                description: '请进入模拟交易页面处理失败运行与告警后再继续观察。',
            };
        }
        if (openAlerts.length > 0 || latestHeartbeat?.status === 'LAGGING' || latestHeartbeat?.status === 'STOPPED') {
            return {
                level: 'warning' as const,
                message: openAlerts.length > 0 ? `存在 ${openAlerts.length} 条未处理告警` : '焦点 run 心跳异常',
                description: '系统仍在运行，但存在需要人工确认的风险信号。',
            };
        }
        if (runningCount > 0) {
            return {
                level: 'success' as const,
                message: 'Paper Trading 运行正常',
                description: '当前无 FAILED run、无未处理告警，心跳正常。',
            };
        }

        return {
            level: 'info' as const,
            message: '当前没有运行中的 Paper Run',
            description: '系统处于待机状态，可在模拟交易页面创建并启动 Paper Run。',
        };
    }, [failedCount, latestHeartbeat, openAlerts.length, openCriticalCount, runningCount, runsQuery.error]);

    const focusLoading = Boolean(focusRunId) && dailyReportsQuery.isPending;

    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <NqPageHeader
                    title="控制台总览"
                    description="安全总览：系统健康、当前环境、Paper Trading 运行状态与风险信号。"
                    badge={<Tag color="processing">{appEnv.envLabel}</Tag>}
                    tip={(
                        <NqRiskBanner
                            level={banner.level}
                            message={banner.message}
                            description={`${banner.description} 环境：${appEnv.envLabel} · LIVE 交易未开启 · 阶段：GateJ completed（Paper Trading）。`}
                        />
                    )}
                />
            </Card>

            <div className="nq-status-strip">
                <NqMetricCard
                    label="Paper Run 总数"
                    value={runsQuery.isPending ? '-' : formatNqNumber(runs.length, {precision: 0})}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label="RUNNING"
                    value={formatNqNumber(runningCount, {precision: 0})}
                    tone={runningCount > 0 ? 'success' : 'muted'}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label="FAILED"
                    value={formatNqNumber(failedCount, {precision: 0})}
                    tone={failedCount > 0 ? 'danger' : 'muted'}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label="运行策略数量"
                    value={formatNqNumber(runningStrategyCount, {precision: 0})}
                    loading={runsQuery.isPending}
                />
                <NqMetricCard
                    label="未处理告警"
                    value={focusRunId ? formatNqNumber(openAlerts.length, {precision: 0}) : '-'}
                    tone={openAlerts.length > 0 ? 'warning' : 'muted'}
                    footer={focusRunId ? '焦点 run 范围' : '暂无 Paper Run'}
                    loading={Boolean(focusRunId) && alertsQuery.isPending}
                />
                <NqMetricCard
                    label="心跳状态"
                    value={latestHeartbeat ? <NqStatusTag status={latestHeartbeat.status}/> : '-'}
                    footer={latestHeartbeat ? formatDateTime(latestHeartbeat.heartbeatTime) : '焦点 run 范围'}
                    loading={Boolean(focusRunId) && heartbeatsQuery.isPending}
                />
            </div>

            <Row gutter={[12, 12]}>
                <Col xs={24} xl={14}>
                    <Card
                        className="page-section"
                        bordered={false}
                        title="焦点 Paper Run 绩效"
                        extra={focusRun ? (
                            <Typography.Text type="secondary" className="nq-mono">
                                {focusRun.paperRunId}
                            </Typography.Text>
                        ) : null}
                    >
                        {!focusRun ? (
                            <NqEmptyState description="暂无 Paper Run，创建并启动后这里展示最新日报指标。"/>
                        ) : !latestDailyReport && !focusLoading ? (
                            <NqEmptyState description="焦点 run 暂无日报数据，可在模拟交易详情页生成日报。"/>
                        ) : (
                            <div className="nq-status-strip">
                                <NqMetricCard
                                    label="总权益"
                                    value={formatNqNumber(latestDailyReport?.totalEquity, {precision: 2})}
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label="今日盈亏"
                                    value={formatNqNumber(latestDailyReport?.dailyPnl, {precision: 2, signed: true})}
                                    tone={Number(latestDailyReport?.dailyPnl ?? 0) > 0 ? 'up' : Number(latestDailyReport?.dailyPnl ?? 0) < 0 ? 'down' : 'default'}
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label="日收益率"
                                    value={<NqPercentText value={latestDailyReport?.dailyReturn} ratio colorBySign/>}
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label="最大回撤"
                                    value={<NqPercentText value={latestDailyReport?.maxDrawdown} ratio signed={false}/>}
                                    tone="warning"
                                    loading={focusLoading}
                                />
                                <NqMetricCard
                                    label="风控状态"
                                    value={latestRiskResult ? <NqStatusTag status={latestRiskResult.status}/> : '-'}
                                    footer={latestRiskResult ? latestRiskResult.checkType : '暂无风控检查结果'}
                                    loading={Boolean(focusRunId) && riskResultsQuery.isPending}
                                />
                            </div>
                        )}
                        {latestDailyReport ? (
                            <Typography.Paragraph type="secondary" style={{margin: '12px 0 0', fontSize: 12}}>
                                数据来源：{latestDailyReport.reportDate} 日报（生成于 {formatDateTime(latestDailyReport.generatedAt)}）。
                            </Typography.Paragraph>
                        ) : null}
                    </Card>
                </Col>
                <Col xs={24} xl={10}>
                    <Card className="page-section" bordered={false} title="最近事件">
                        {!focusRunId ? (
                            <NqEmptyState description="暂无 Paper Run，事件流为空。"/>
                        ) : alertsQuery.error ? (
                            <NqErrorState title="事件查询失败" error={alertsQuery.error as AppApiError}/>
                        ) : recentEvents.length === 0 ? (
                            <NqEmptyState description="焦点 run 暂无告警与恢复事件。"/>
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

            <Card className="page-section" bordered={false} title="业务入口">
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
                                            进入页面
                                        </Button>
                                    </Space>
                                </Card>
                            </Col>
                        ))}
                </Row>
            </Card>
        </Space>
    );
}

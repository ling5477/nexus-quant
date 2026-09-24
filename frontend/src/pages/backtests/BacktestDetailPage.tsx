import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Alert, Button, Card, Col, Descriptions, Empty, Row, Space, Spin, Typography} from 'antd';
import {useEffect, useMemo, type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useNavigate, useParams} from 'react-router-dom';

import {backtestsApi} from '@/features/backtests/backtestsApi';
import {evaluationsApi} from '@/api/evaluations';
import {formatApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {backtestsQueryKeys, evaluationsQueryKeys} from '@/api/query-keys';
import {BacktestCurveChart, type BacktestCurvePoint} from '@/features/backtests/BacktestCurveChart';
import {useLiveQuery} from '@/hooks/useLiveQuery';
import {
    ChangeCell,
    DataFreshness,
    MoneyCell,
    NumberCell,
    PercentCell,
    StatusCell,
    applyNqCssVars,
    nqTableClassName,
} from '@/nq-design-system';
import type {AppApiError} from '@/types/api';
import type {BacktestEvaluationListItem} from '@/types/evaluations';
import {formatDateTime} from '@/utils/formatters';

import '@/nq-design-system/table/nq-table.css';

/**
 * BacktestDetailPage — 回测详情可视化。
 *
 * 数据来源(全部真实 API,缺则显式 unavailable,不编造):
 * - 配置/快照:GET /backtest-configs/{id}(策略版本 / 参数 / 数据集 / 配置快照 JSON)。
 * - 关键指标 + 交易/风险摘要:GET /evaluations?backtestConfigId + GET /evaluations/{id}。
 * - 数据集快照:GET /marketdata/datasets(按 config.datasetId 匹配 typed 字段)。
 * 权益/回撤时间序列:GET /api/backtest-runs/{runId}/pnl-snapshots(表 sim_pnl_snapshots,真实序列)。
 *   runId 取自所选 evaluation.backtestRunId(保证曲线与指标同一 run);equity 直接映射,
 *   drawdown 客户端派生(equity − 运行峰值,≤0,口径同后端 DrawdownCalculator);无 run / 无快照显式 unavailable,不编造。
 * 实时性:回测为静态结果,使用 useLiveQuery 仅做 manual refresh + freshness,不轮询(pollingIntervalMs=0)。
 */

/** 选取要展示的评估:优先 SUCCEEDED,再按 evaluatedAt 倒序取最新。 */
function pickEvaluation(list: BacktestEvaluationListItem[] | undefined): BacktestEvaluationListItem | null {
    if (!list || list.length === 0) {
        return null;
    }
    const sorted = [...list].sort((a, b) => (b.evaluatedAt ?? '').localeCompare(a.evaluatedAt ?? ''));
    return sorted.find((item) => (item.evaluationStatus ?? '').toUpperCase() === 'SUCCEEDED') ?? sorted[0];
}

const UNAVAILABLE = '—';

function MetricCard({label, children}: {label: string; children: ReactNode}) {
    useTranslation('pages');
    return (
        <Card size="small" variant="outlined" style={{height: '100%'}}>
            <div style={{fontSize: 12, color: 'var(--nq-text-tertiary)', marginBottom: 4}}>{label}</div>
            <div style={{fontSize: 18, fontWeight: 600}}>{children}</div>
        </Card>
    );
}

export function BacktestDetailPage() {
    useTranslation('pages');
    const navigate = useNavigate();
    const {backtestConfigId} = useParams<{backtestConfigId: string}>();
    const configId = backtestConfigId ?? '';

    // 进入页面注入 v2 CSS 变量(additive 的 --nq-*,与 v1 的 --nq-color-* 不冲突),供表格列组件读色/等宽。
    useEffect(() => {
        applyNqCssVars();
    }, []);

    const configQuery = useQuery({
        queryKey: backtestsQueryKeys.detail(configId),
        queryFn: () => backtestsApi.detail(configId),
        enabled: Boolean(configId),
    });
    const config = configQuery.data;

    const evaluationsQuery = useQuery({
        queryKey: evaluationsQueryKeys.list({backtestConfigId: configId}, 1),
        queryFn: () => evaluationsApi.list({backtestConfigId: configId}),
        enabled: Boolean(configId),
    });
    const selectedEvaluation = pickEvaluation(evaluationsQuery.data);
    const evalReportId = selectedEvaluation?.evalReportId ?? null;

    // 评估明细用 useLiveQuery:仅 manual refresh + freshness,不轮询静态回测结果。
    const evalLive = useLiveQuery({
        queryKey: evaluationsQueryKeys.detail(evalReportId ?? ''),
        queryFn: () => evaluationsApi.detail(evalReportId ?? ''),
        enabled: Boolean(evalReportId),
        pollingIntervalMs: 0,
    });
    const evaluation = evalLive.data;

    const datasetsQuery = useQuery({
        queryKey: ['marketdata-datasets'],
        queryFn: marketdataApi.listDatasets,
        enabled: Boolean(config?.datasetId),
    });
    const dataset = (datasetsQuery.data ?? []).find((item) => item.datasetId === config?.datasetId) ?? null;

    // 权益/回撤序列:取自所选 evaluation 的 backtestRunId,保证与指标同一 run;静态结果只手动刷新、不轮询。
    const runId = selectedEvaluation?.backtestRunId ?? null;
    const pnlLive = useLiveQuery({
        queryKey: backtestsQueryKeys.pnlSnapshots(runId ?? ''),
        queryFn: () => backtestsApi.pnlSnapshots(runId ?? ''),
        enabled: Boolean(runId),
        pollingIntervalMs: 0,
    });
    const snapshots = pnlLive.data;

    // equity 曲线:snapshotTime + equity 直接映射(过滤 null)。
    const equityPoints = useMemo<BacktestCurvePoint[] | null>(() => {
        if (!snapshots || snapshots.length === 0) {
            return null;
        }
        const points = snapshots
            .filter((item) => item.equity != null)
            .map((item) => ({t: item.snapshotTime, v: Number(item.equity)}));
        return points.length > 0 ? points : null;
    }, [snapshots]);

    // drawdown 曲线:客户端派生 equity − 运行峰值(≤0,向下);口径同后端 DrawdownCalculator 的 peak − equity 取负。
    const drawdownPoints = useMemo<BacktestCurvePoint[] | null>(() => {
        if (!snapshots || snapshots.length === 0) {
            return null;
        }
        let peak = Number.NEGATIVE_INFINITY;
        const points: BacktestCurvePoint[] = [];
        for (const item of snapshots) {
            if (item.equity == null) {
                continue;
            }
            const equityValue = Number(item.equity);
            if (equityValue > peak) {
                peak = equityValue;
            }
            points.push({t: item.snapshotTime, v: equityValue - peak});
        }
        return points.length > 0 ? points : null;
    }, [snapshots]);

    // 无 runId / 错误 / 空快照的明确原因(不编造曲线)。
    const curveUnavailable = !runId
        ? t('pages:theSelectedEvaluationHasNoBacktestrunidSoItsEquitySeriesCannotBeLocated')
        : pnlLive.status === 'error'
            ? pnlLive.errorReason ?? t('pages:failedToLoadEquitySnapshots')
            : t('pages:thisBacktestRunHasNoEquitySnapshotsSimPnlSnapshotsIsEmpty');

    const refreshAll = () => {
        void configQuery.refetch();
        void evaluationsQuery.refetch();
        evalLive.refresh();
        void datasetsQuery.refetch();
        pnlLive.refresh();
    };

    if (!configId) {
        return <Alert type="error" showIcon message={t('pages:theBacktestconfigidRouteParameterIsMissing')}/>;
    }

    const freshnessDetail = evaluation?.evaluatedAt
        ? t('pages:evaluatedAtTime', {time: formatDateTime(evaluation.evaluatedAt)})
        : evalLive.status === 'error'
            ? evalLive.errorReason ?? t('pages:loadingFailed')
            : evalReportId
                ? t('pages:loading2')
                : t('pages:noEvaluations');

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            {/* 页头:标识 + 状态 + 数据新鲜度 + 操作 */}
            <Card className="page-card" variant="borderless">
                <Row align="middle" justify="space-between" gutter={[16, 12]}>
                    <Col>
                        <Space direction="vertical" size={2}>
                            <Typography.Title level={4} style={{margin: 0}}>
                                {t('pages:backtestVisualization')}</Typography.Title>
                            <Typography.Text type="secondary">
                                {config?.name ? `${config.name} · ` : ''}
                                <Typography.Text code copyable={{text: configId}}>{configId}</Typography.Text>
                            </Typography.Text>
                        </Space>
                    </Col>
                    <Col>
                        <Space size={12} wrap>
                            <DataFreshness source={t('pages:backtestEvaluation')} state={evalLive.freshnessState} detail={freshnessDetail} inline/>
                            {selectedEvaluation && <StatusCell status={selectedEvaluation.evaluationStatus}/>}
                            <Button onClick={() => navigate('/backtests')}>{t('pages:backToList')}</Button>
                            <Button type="primary" onClick={refreshAll} loading={evalLive.isFetching || configQuery.isFetching}>
                                {t('pages:refresh')}</Button>
                        </Space>
                    </Col>
                </Row>
            </Card>

            {configQuery.isLoading ? (
                <Card className="page-section" variant="borderless">
                    <Spin/>
                </Card>
            ) : configQuery.error ? (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:failedToLoadBacktestConfiguration')}
                    description={formatApiError(configQuery.error as AppApiError)}
                    action={<Button size="small" onClick={() => configQuery.refetch()}>{t('pages:retry')}</Button>}
                />
            ) : (
                <>
                    {/* 关键指标摘要 */}
                    <Card className="page-section" variant="borderless" title={t('pages:keyMetrics')}>
                        {evaluationsQuery.isLoading ? (
                            <Spin/>
                        ) : evaluationsQuery.error ? (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:failedToLoadEvaluations')}
                                description={formatApiError(evaluationsQuery.error as AppApiError)}
                            />
                        ) : !selectedEvaluation ? (
                            <Empty description={t('pages:runABacktestAndEvaluateItBeforeViewingMetrics')}/>
                        ) : (
                            <Row gutter={[12, 12]}>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:totalReturn')}>
                                        {evaluation?.totalReturn != null
                                            ? <ChangeCell value={evaluation.totalReturn} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:totalReturnRate')}>
                                        {evaluation?.totalReturnRate != null
                                            ? <ChangeCell value={evaluation.totalReturnRate} percent ratio/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:maximumDrawdown')}>
                                        {evaluation?.maxDrawdownRate != null
                                            ? <PercentCell value={evaluation.maxDrawdownRate} ratio signed={false} colorBySign={false}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:sharpeRatio')}>
                                        {evaluation?.sharpeRatio != null
                                            ? <NumberCell value={evaluation.sharpeRatio} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:winRate')}>
                                        {evaluation?.winRate != null
                                            ? <PercentCell value={evaluation.winRate} ratio signed={false}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:tradeCount')}>
                                        {evaluation?.tradeCount != null
                                            ? <NumberCell value={evaluation.tradeCount} precision={0}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:netPnl')}>
                                        {evaluation?.netPnl != null
                                            ? <ChangeCell value={evaluation.netPnl} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label={t('pages:closingEquity')}>
                                        {evaluation?.finalEquity != null
                                            ? <MoneyCell value={evaluation.finalEquity} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                            </Row>
                        )}
                        <Typography.Text type="secondary" style={{display: 'block', marginTop: 8, fontSize: 12}}>
                            {t('pages:returnDrawdownAndWinRatiosAreDisplayedAsBackendRatios100BackendValuesMustUseTheSameRatioConvention')}</Typography.Text>
                    </Card>

                    {/* 权益 / 回撤曲线:真实来源 GET /api/backtest-runs/{runId}/pnl-snapshots;回撤客户端派生 */}
                    <Card
                        className="page-section"
                        variant="borderless"
                        title={t('pages:equityAndDrawdownCurves')}
                        extra={runId ? (
                            <DataFreshness
                                source={t('pages:equitySeries')}
                                state={pnlLive.freshnessState}
                                detail={
                                    pnlLive.status === 'error'
                                        ? pnlLive.errorReason ?? t('pages:loadingFailed')
                                        : snapshots
                                            ? t('pages:seriesPointsLatency', {count: snapshots.length, latency: pnlLive.latencyMs ?? '-'})
                                            : t('pages:loading2')
                                }
                                inline
                            />
                        ) : null}
                    >
                        <Row gutter={[16, 16]}>
                            <Col xs={24} xl={12}>
                                <div style={{fontSize: 12, color: 'var(--nq-text-tertiary)', marginBottom: 4}}>
                                    {t('pages:equityCurve')}</div>
                                <BacktestCurveChart points={equityPoints} kind="equity" unavailableText={curveUnavailable}/>
                            </Col>
                            <Col xs={24} xl={12}>
                                <div style={{fontSize: 12, color: 'var(--nq-text-tertiary)', marginBottom: 4}}>
                                    {t('pages:drawdownCurveEquityMinusRunningPeak0')}</div>
                                <BacktestCurveChart points={drawdownPoints} kind="drawdown" unavailableText={curveUnavailable}/>
                            </Col>
                        </Row>
                        <Typography.Text type="secondary" style={{display: 'block', marginTop: 8, fontSize: 12}}>
                            {t('pages:sourceGetApiBacktestRuns')}{'{runId}'}{t('pages:pnlSnapshotsSimPnlSnapshotsOrderedBySnapshottimeAscendingDrawdownIsDerivedAsEquityMinusTheRunningPea')}</Typography.Text>
                    </Card>

                    {/* 交易 / 风险摘要(聚合,复用表格列组件) */}
                    <Card className="page-section" variant="borderless" title={t('pages:tradeAndRiskSummary')}>
                        {!evaluation ? (
                            <Empty description={t('pages:noEvaluationDetails')}/>
                        ) : (
                            <table className={nqTableClassName('standard')} aria-label={t('pages:tradeRiskSummary')}>
                                <thead>
                                    <tr>
                                        <th>{t('pages:metric')}</th>
                                        <th className="nq-ds-col-num">{t('pages:value')}</th>
                                        <th>{t('pages:metric')}</th>
                                        <th className="nq-ds-col-num">{t('pages:value')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>{t('pages:orderCount')}</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.orderCount} precision={0}/></td>
                                        <td>{t('pages:tradeCount2')}</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.tradeCount} precision={0}/></td>
                                    </tr>
                                    <tr>
                                        <td>{t('pages:winningTrades')}</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.winningTradeCount} precision={0}/></td>
                                        <td>{t('pages:losingTrades')}</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.losingTradeCount} precision={0}/></td>
                                    </tr>
                                    <tr>
                                        <td>{t('pages:breakEvenTrades')}</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.flatTradeCount} precision={0}/></td>
                                        <td>{t('pages:profitLossRatio')}</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.profitLossRatio} precision={2}/></td>
                                    </tr>
                                    <tr>
                                        <td>{t('pages:realizedPnl')}</td>
                                        <td className="nq-ds-col-num"><ChangeCell value={evaluation.realizedPnl} precision={2}/></td>
                                        <td>{t('pages:unrealizedPnl')}</td>
                                        <td className="nq-ds-col-num"><ChangeCell value={evaluation.unrealizedPnl} precision={2}/></td>
                                    </tr>
                                    <tr>
                                        <td>{t('pages:totalFees')}</td>
                                        <td className="nq-ds-col-num"><MoneyCell value={evaluation.totalFee} precision={2}/></td>
                                        <td>{t('pages:totalSlippage')}</td>
                                        <td className="nq-ds-col-num"><MoneyCell value={evaluation.totalSlippage} precision={2}/></td>
                                    </tr>
                                </tbody>
                            </table>
                        )}
                        {evaluation?.failureMessage && (
                            <Alert
                                style={{marginTop: 12}}
                                type="warning"
                                showIcon
                                message={t('pages:evaluationFailure', {code: evaluation.failureCode ?? ''})}
                                description={<details><summary>{t('pages:failureDiagnostics')}</summary>{evaluation.failureMessage}</details>}
                            />
                        )}
                    </Card>

                    {/* 数据集快照 */}
                    <Card className="page-section" variant="borderless" title={t('pages:datasetSnapshot')}>
                        {!config?.datasetId ? (
                            <Empty description={t('pages:noDatasetIsBoundToThisBacktestConfiguration')}/>
                        ) : datasetsQuery.isLoading ? (
                            <Spin/>
                        ) : !dataset ? (
                            <Alert
                                type="warning"
                                showIcon
                                message={t('pages:theBoundDatasetWasNotFoundInTheCatalog')}
                                description={`Dataset ID: ${config.datasetId}`}
                            />
                        ) : (
                            <Descriptions bordered column={2} size="small">
                                <Descriptions.Item label={t('pages:instrument')}>{dataset.exchangeCode} {dataset.symbol}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:interval')}>{dataset.interval}</Descriptions.Item>
                                <Descriptions.Item label={t('pages:range')} span={2}>
                                    {formatDateTime(dataset.startTime)} ~ {formatDateTime(dataset.endTime)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:barCount')}>
                                    <NumberCell value={dataset.barCount} precision={0}/>
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:gapCount')}>
                                    <NumberCell value={dataset.gapCount} precision={0}/>
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:qualityStatus')}><StatusCell status={dataset.qualityStatus}/></Descriptions.Item>
                                <Descriptions.Item label={t('pages:datasetStatus')}><StatusCell status={dataset.status}/></Descriptions.Item>
                            </Descriptions>
                        )}
                    </Card>

                    {/* 参数 / 策略 / 配置快照 */}
                    <Card className="page-section" variant="borderless" title={t('pages:parameterAndStrategySnapshots')}>
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label={t('pages:strategyVersionId')} span={2}>
                                {config?.strategyVersionId
                                    ? <Typography.Text copyable>{config.strategyVersionId}</Typography.Text>
                                    : t('pages:notBound')}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:backtestRange')} span={2}>
                                {config ? `${formatDateTime(config.startTime)} ~ ${formatDateTime(config.endTime)}` : UNAVAILABLE}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:initialCapital')}>
                                {config?.initialCapital != null
                                    ? <MoneyCell value={config.initialCapital} precision={2}/>
                                    : UNAVAILABLE}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:createdAt')}>
                                {config ? formatDateTime(config.createdAt) : UNAVAILABLE}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:parameterSnapshotParamsnapshotjson')} span={2}>
                                <JsonSnapshot value={config?.paramSnapshotJson}/>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:strategyVersionSnapshotStrategyversionsnapshotjson')} span={2}>
                                <JsonSnapshot value={config?.strategyVersionSnapshotJson}/>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:configurationSnapshotConfigsnapshotjson')} span={2}>
                                <JsonSnapshot value={config?.configSnapshotJson}/>
                            </Descriptions.Item>
                        </Descriptions>
                    </Card>
                </>
            )}
        </Space>
    );
}

/** JSON 快照美化展示:能 parse 则缩进展示,否则原样;空则 unavailable。 */
function JsonSnapshot({value}: {value: string | null | undefined}) {
    useTranslation('pages');
    const pretty = useMemo(() => {
        if (!value) {
            return null;
        }
        try {
            return JSON.stringify(JSON.parse(value), null, 2);
        } catch {
            return value;
        }
    }, [value]);

    if (!pretty) {
        return <Typography.Text type="secondary">{t('pages:noSnapshots')}</Typography.Text>;
    }

    return (
        <pre
            style={{
                margin: 0,
                maxHeight: 220,
                overflow: 'auto',
                fontFamily: 'var(--nq-font-mono)',
                fontSize: 12,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
            }}
        >
            {pretty}
        </pre>
    );
}

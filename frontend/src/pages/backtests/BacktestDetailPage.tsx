import {Alert, Button, Card, Col, Descriptions, Empty, Row, Space, Spin, Typography} from 'antd';
import {useEffect, useMemo, type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useNavigate, useParams} from 'react-router-dom';

import {backtestsApi} from '@/api/backtests';
import {evaluationsApi} from '@/api/evaluations';
import {formatApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {backtestsQueryKeys, evaluationsQueryKeys} from '@/api/query-keys';
import {BacktestCurveChart, type BacktestCurvePoint} from '@/components/backtest/BacktestCurveChart';
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
 * BacktestDetailPage — 回测详情可视化(B1)。
 *
 * 数据来源(全部真实 API,缺则显式 unavailable,不编造):
 * - 配置/快照:GET /backtest-configs/{id}(策略版本 / 参数 / 数据集 / 配置快照 JSON)。
 * - 关键指标 + 交易/风险摘要:GET /evaluations?backtestConfigId + GET /evaluations/{id}。
 * - 数据集快照:GET /marketdata/datasets(按 config.datasetId 匹配 typed 字段)。
 * 权益/回撤时间序列:后端无专用端点,仅从 evaluation report/metrics JSON 防御式解析;无则显示 unavailable。
 * 实时性:回测为静态结果,使用 useLiveQuery 仅做 manual refresh + freshness,不轮询(pollingIntervalMs=0)。
 */

/** 从 evaluation JSON 中防御式解析时间序列;只渲染真实数组,解析不到返回 null(不编造)。 */
function extractSeries(jsonStr: string | null | undefined, keys: readonly string[]): BacktestCurvePoint[] | null {
    if (!jsonStr) {
        return null;
    }

    let parsed: unknown;
    try {
        parsed = JSON.parse(jsonStr);
    } catch {
        return null;
    }

    if (!parsed || typeof parsed !== 'object') {
        return null;
    }

    const root = parsed as Record<string, unknown>;
    for (const key of keys) {
        const arr = root[key];
        if (Array.isArray(arr) && arr.length > 0) {
            const points = arr
                .map((item, index) => normalizePoint(item, index))
                .filter((point): point is BacktestCurvePoint => point !== null);
            if (points.length > 0) {
                return points;
            }
        }
    }
    return null;
}

function normalizePoint(item: unknown, index: number): BacktestCurvePoint | null {
    if (typeof item === 'number') {
        return {t: String(index), v: item};
    }
    if (item && typeof item === 'object') {
        const obj = item as Record<string, unknown>;
        const value = obj.v ?? obj.value ?? obj.equity ?? obj.drawdown ?? obj.y;
        const time = obj.t ?? obj.time ?? obj.timestamp ?? obj.date ?? obj.x ?? index;
        if (typeof value === 'number') {
            return {t: String(time), v: value};
        }
    }
    return null;
}

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
    return (
        <Card size="small" variant="outlined" style={{height: '100%'}}>
            <div style={{fontSize: 12, color: 'var(--nq-text-tertiary)', marginBottom: 4}}>{label}</div>
            <div style={{fontSize: 18, fontWeight: 600}}>{children}</div>
        </Card>
    );
}

export function BacktestDetailPage() {
    const navigate = useNavigate();
    const {backtestConfigId} = useParams<{backtestConfigId: string}>();
    const configId = backtestConfigId ?? '';

    // 进入页面注入 v2 CSS 变量(additive 的 --nq-*,与 v1 的 --nq-color-* 不冲突),供 B0.2 列组件读色/等宽。
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

    const equityPoints = useMemo(
        () =>
            extractSeries(evaluation?.reportJson, ['equityCurve', 'equity', 'equitySeries'])
            ?? extractSeries(evaluation?.metricsJson, ['equityCurve', 'equity', 'equitySeries']),
        [evaluation],
    );
    const drawdownPoints = useMemo(
        () =>
            extractSeries(evaluation?.reportJson, ['drawdownCurve', 'drawdown', 'drawdownSeries'])
            ?? extractSeries(evaluation?.metricsJson, ['drawdownCurve', 'drawdown', 'drawdownSeries']),
        [evaluation],
    );

    const refreshAll = () => {
        void configQuery.refetch();
        void evaluationsQuery.refetch();
        evalLive.refresh();
        void datasetsQuery.refetch();
    };

    if (!configId) {
        return <Alert type="error" showIcon message="缺少 backtestConfigId 路由参数。"/>;
    }

    const freshnessDetail = evaluation?.evaluatedAt
        ? `评估于 ${formatDateTime(evaluation.evaluatedAt)}`
        : evalLive.status === 'error'
            ? evalLive.errorReason ?? '加载失败'
            : evalReportId
                ? '加载中'
                : '尚无评估';

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            {/* 页头:标识 + 状态 + 数据新鲜度 + 操作 */}
            <Card className="page-card" variant="borderless">
                <Row align="middle" justify="space-between" gutter={[16, 12]}>
                    <Col>
                        <Space direction="vertical" size={2}>
                            <Typography.Title level={4} style={{margin: 0}}>
                                回测详情可视化
                            </Typography.Title>
                            <Typography.Text type="secondary">
                                {config?.name ? `${config.name} · ` : ''}
                                <Typography.Text code copyable={{text: configId}}>{configId}</Typography.Text>
                            </Typography.Text>
                        </Space>
                    </Col>
                    <Col>
                        <Space size={12} wrap>
                            <DataFreshness source="回测评估" state={evalLive.freshnessState} detail={freshnessDetail} inline/>
                            {selectedEvaluation && <StatusCell status={selectedEvaluation.evaluationStatus}/>}
                            <Button onClick={() => navigate('/backtests')}>返回列表</Button>
                            <Button type="primary" onClick={refreshAll} loading={evalLive.isFetching || configQuery.isFetching}>
                                刷新
                            </Button>
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
                    message="回测配置加载失败"
                    description={formatApiError(configQuery.error as AppApiError)}
                    action={<Button size="small" onClick={() => configQuery.refetch()}>重试</Button>}
                />
            ) : (
                <>
                    {/* 关键指标摘要 */}
                    <Card className="page-section" variant="borderless" title="关键指标摘要">
                        {evaluationsQuery.isLoading ? (
                            <Spin/>
                        ) : evaluationsQuery.error ? (
                            <Alert
                                type="error"
                                showIcon
                                message="评估列表加载失败"
                                description={formatApiError(evaluationsQuery.error as AppApiError)}
                            />
                        ) : !selectedEvaluation ? (
                            <Empty description="尚无评估结果。请先运行回测并执行评估后再查看指标。"/>
                        ) : (
                            <Row gutter={[12, 12]}>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="总收益">
                                        {evaluation?.totalReturn != null
                                            ? <ChangeCell value={evaluation.totalReturn} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="总收益率">
                                        {evaluation?.totalReturnRate != null
                                            ? <ChangeCell value={evaluation.totalReturnRate} percent ratio/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="最大回撤">
                                        {evaluation?.maxDrawdownRate != null
                                            ? <PercentCell value={evaluation.maxDrawdownRate} ratio signed={false} colorBySign={false}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="夏普比率">
                                        {evaluation?.sharpeRatio != null
                                            ? <NumberCell value={evaluation.sharpeRatio} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="胜率">
                                        {evaluation?.winRate != null
                                            ? <PercentCell value={evaluation.winRate} ratio signed={false}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="成交笔数">
                                        {evaluation?.tradeCount != null
                                            ? <NumberCell value={evaluation.tradeCount} precision={0}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="净盈亏">
                                        {evaluation?.netPnl != null
                                            ? <ChangeCell value={evaluation.netPnl} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                                <Col xs={12} md={8} xl={6}>
                                    <MetricCard label="期末权益">
                                        {evaluation?.finalEquity != null
                                            ? <MoneyCell value={evaluation.finalEquity} precision={2}/>
                                            : UNAVAILABLE}
                                    </MetricCard>
                                </Col>
                            </Row>
                        )}
                        <Typography.Text type="secondary" style={{display: 'block', marginTop: 8, fontSize: 12}}>
                            比率字段(收益率 / 回撤率 / 胜率)按后端比例值 ×100 展示;若后端已是百分比口径需后端对齐。
                        </Typography.Text>
                    </Card>

                    {/* 权益 / 回撤曲线(后端无时间序列端点时显式 unavailable) */}
                    <Row gutter={[16, 16]}>
                        <Col xs={24} xl={12}>
                            <Card className="page-section" variant="borderless" title="权益曲线">
                                <BacktestCurveChart
                                    points={equityPoints}
                                    kind="equity"
                                    unavailableText="后端暂未提供回测权益时间序列(仅有聚合指标)。"
                                />
                            </Card>
                        </Col>
                        <Col xs={24} xl={12}>
                            <Card className="page-section" variant="borderless" title="回撤曲线">
                                <BacktestCurveChart
                                    points={drawdownPoints}
                                    kind="drawdown"
                                    unavailableText="后端暂未提供回测回撤时间序列(仅有聚合指标)。"
                                />
                            </Card>
                        </Col>
                    </Row>

                    {/* 交易 / 风险摘要(聚合,复用 B0.2 列组件) */}
                    <Card className="page-section" variant="borderless" title="交易 / 风险摘要">
                        {!evaluation ? (
                            <Empty description="尚无评估明细。"/>
                        ) : (
                            <table className={nqTableClassName('standard')} aria-label="交易风险摘要">
                                <thead>
                                    <tr>
                                        <th>指标</th>
                                        <th className="nq-ds-col-num">值</th>
                                        <th>指标</th>
                                        <th className="nq-ds-col-num">值</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>订单数</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.orderCount} precision={0}/></td>
                                        <td>成交数</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.tradeCount} precision={0}/></td>
                                    </tr>
                                    <tr>
                                        <td>盈利笔数</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.winningTradeCount} precision={0}/></td>
                                        <td>亏损笔数</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.losingTradeCount} precision={0}/></td>
                                    </tr>
                                    <tr>
                                        <td>持平笔数</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.flatTradeCount} precision={0}/></td>
                                        <td>盈亏比</td>
                                        <td className="nq-ds-col-num"><NumberCell value={evaluation.profitLossRatio} precision={2}/></td>
                                    </tr>
                                    <tr>
                                        <td>已实现盈亏</td>
                                        <td className="nq-ds-col-num"><ChangeCell value={evaluation.realizedPnl} precision={2}/></td>
                                        <td>未实现盈亏</td>
                                        <td className="nq-ds-col-num"><ChangeCell value={evaluation.unrealizedPnl} precision={2}/></td>
                                    </tr>
                                    <tr>
                                        <td>总手续费</td>
                                        <td className="nq-ds-col-num"><MoneyCell value={evaluation.totalFee} precision={2}/></td>
                                        <td>总滑点</td>
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
                                message={`评估失败:${evaluation.failureCode ?? ''}`}
                                description={evaluation.failureMessage}
                            />
                        )}
                    </Card>

                    {/* 数据集快照 */}
                    <Card className="page-section" variant="borderless" title="数据集快照">
                        {!config?.datasetId ? (
                            <Empty description="该回测配置尚未绑定数据集。"/>
                        ) : datasetsQuery.isLoading ? (
                            <Spin/>
                        ) : !dataset ? (
                            <Alert
                                type="warning"
                                showIcon
                                message="未在数据集目录中找到绑定的数据集"
                                description={`Dataset ID: ${config.datasetId}`}
                            />
                        ) : (
                            <Descriptions bordered column={2} size="small">
                                <Descriptions.Item label="标的">{dataset.exchangeCode} {dataset.symbol}</Descriptions.Item>
                                <Descriptions.Item label="周期">{dataset.interval}</Descriptions.Item>
                                <Descriptions.Item label="区间" span={2}>
                                    {formatDateTime(dataset.startTime)} ~ {formatDateTime(dataset.endTime)}
                                </Descriptions.Item>
                                <Descriptions.Item label="Bar 数量">
                                    <NumberCell value={dataset.barCount} precision={0}/>
                                </Descriptions.Item>
                                <Descriptions.Item label="缺口数量">
                                    <NumberCell value={dataset.gapCount} precision={0}/>
                                </Descriptions.Item>
                                <Descriptions.Item label="质量状态"><StatusCell status={dataset.qualityStatus}/></Descriptions.Item>
                                <Descriptions.Item label="数据集状态"><StatusCell status={dataset.status}/></Descriptions.Item>
                            </Descriptions>
                        )}
                    </Card>

                    {/* 参数 / 策略 / 配置快照 */}
                    <Card className="page-section" variant="borderless" title="参数 / 策略快照">
                        <Descriptions bordered column={2} size="small">
                            <Descriptions.Item label="策略版本 ID" span={2}>
                                {config?.strategyVersionId
                                    ? <Typography.Text copyable>{config.strategyVersionId}</Typography.Text>
                                    : '未绑定'}
                            </Descriptions.Item>
                            <Descriptions.Item label="回测区间" span={2}>
                                {config ? `${formatDateTime(config.startTime)} ~ ${formatDateTime(config.endTime)}` : UNAVAILABLE}
                            </Descriptions.Item>
                            <Descriptions.Item label="初始资金">
                                {config?.initialCapital != null
                                    ? <MoneyCell value={config.initialCapital} precision={2}/>
                                    : UNAVAILABLE}
                            </Descriptions.Item>
                            <Descriptions.Item label="创建时间">
                                {config ? formatDateTime(config.createdAt) : UNAVAILABLE}
                            </Descriptions.Item>
                            <Descriptions.Item label="参数快照(paramSnapshotJson)" span={2}>
                                <JsonSnapshot value={config?.paramSnapshotJson}/>
                            </Descriptions.Item>
                            <Descriptions.Item label="策略版本快照(strategyVersionSnapshotJson)" span={2}>
                                <JsonSnapshot value={config?.strategyVersionSnapshotJson}/>
                            </Descriptions.Item>
                            <Descriptions.Item label="配置快照(configSnapshotJson)" span={2}>
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
        return <Typography.Text type="secondary">暂无快照</Typography.Text>;
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

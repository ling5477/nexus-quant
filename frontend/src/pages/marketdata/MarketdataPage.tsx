import {Alert, Button, Card, DatePicker, Descriptions, Form, Input, Select, Space, Table, Tag, Typography, message} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useEffect, useMemo, useState, type ReactNode} from 'react';

import {formatApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {PageHero} from '@/components/page/PageHero';
import {EXCHANGE_OPTIONS, INTERVAL_OPTIONS, MARKET_TYPE_OPTIONS, SYMBOL_OPTIONS} from '@/constants/filter-options';
import {DataFreshness, NqKlineChart, NqVolumeChart, applyNqCssVars, type FreshnessState, type NqKlineBar} from '@/nq-design-system';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {
    CreateMarketdataIngestionJobRequest,
    CreateMarketdataDatasetRequest,
    MarketdataBar,
    MarketdataBarsQuery,
    MarketdataDataset,
    MarketdataIngestionJob,
    MarketdataIngestionRun,
} from '@/types/marketdata';
import {formatDateTime, formatNumber} from '@/utils/formatters';

const columns: ColumnsType<MarketdataBar> = [
    {title: 'Exchange', dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: 'Market', dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 140},
    {title: 'Interval', dataIndex: 'interval', key: 'interval', width: 100},
    {title: 'Open Time', dataIndex: 'openTime', key: 'openTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Close Time', dataIndex: 'closeTime', key: 'closeTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Open', dataIndex: 'openPrice', key: 'openPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'High', dataIndex: 'highPrice', key: 'highPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Low', dataIndex: 'lowPrice', key: 'lowPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Close', dataIndex: 'closePrice', key: 'closePrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Volume', dataIndex: 'volume', key: 'volume', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Quote Volume', dataIndex: 'quoteVolume', key: 'quoteVolume', width: 140, render: (value?: number | null) => value == null ? '-' : formatNumber(value, 8)},
    {title: 'Quality', dataIndex: 'qualityStatus', key: 'qualityStatus', width: 130, render: (value: string) => <Tag color={value === 'OK' ? 'green' : 'orange'}>{value}</Tag>},
];

const GAP_QUALITY_STATUSES = new Set(['GAP_DETECTED', 'MISSING_BAR', 'INCOMPLETE', 'DEGRADED']);

type MarketdataDateValue = string | null | undefined | {
    toISOString?: () => string;
    toDate?: () => Date;
};

type MarketdataBarsFormValues = Omit<MarketdataBarsQuery, 'startTime' | 'endTime'> & {
    startTime?: MarketdataDateValue;
    endTime?: MarketdataDateValue;
};

type CreateMarketdataIngestionJobFormValues = Omit<CreateMarketdataIngestionJobRequest, 'startTime' | 'endTime'> & {
    startTime?: MarketdataDateValue;
    endTime?: MarketdataDateValue;
};

type CreateMarketdataDatasetFormValues = Omit<CreateMarketdataDatasetRequest, 'startTime' | 'endTime'> & {
    startTime?: MarketdataDateValue;
    endTime?: MarketdataDateValue;
};

function toIsoString(value: MarketdataDateValue): string {
    if (!value) {
        return '';
    }
    if (typeof value === 'string') {
        return value;
    }
    if (typeof value.toISOString === 'function') {
        return value.toISOString();
    }
    if (typeof value.toDate === 'function') {
        return value.toDate().toISOString();
    }
    return String(value);
}

function normalizeBarsQuery(values: MarketdataBarsFormValues): MarketdataBarsQuery {
    return {
        ...values,
        startTime: toIsoString(values.startTime),
        endTime: toIsoString(values.endTime),
    };
}

function normalizeIngestionJob(values: CreateMarketdataIngestionJobFormValues): CreateMarketdataIngestionJobRequest {
    return {
        ...values,
        startTime: toIsoString(values.startTime),
        endTime: toIsoString(values.endTime),
    };
}

function normalizeDataset(values: CreateMarketdataDatasetFormValues): CreateMarketdataDatasetRequest {
    return {
        ...values,
        startTime: toIsoString(values.startTime),
        endTime: toIsoString(values.endTime),
    };
}

function toNqKlineBar(bar: MarketdataBar): NqKlineBar {
    return {
        time: bar.openTime,
        open: Number(bar.openPrice),
        high: Number(bar.highPrice),
        low: Number(bar.lowPrice),
        close: Number(bar.closePrice),
        volume: Number(bar.volume),
        qualityStatus: bar.qualityStatus ?? null,
    };
}

function intervalToMs(interval: string | undefined): number | null {
    if (!interval) {
        return null;
    }

    const match = /^(\d+)([mhd])$/i.exec(interval.trim());
    if (!match) {
        return null;
    }

    const amount = Number(match[1]);
    const unit = match[2].toLowerCase();

    if (!Number.isFinite(amount) || amount <= 0) {
        return null;
    }

    if (unit === 'm') {
        return amount * 60_000;
    }
    if (unit === 'h') {
        return amount * 60 * 60_000;
    }
    return amount * 24 * 60 * 60_000;
}

function timestampMs(value: string | undefined | null): number | null {
    if (!value) {
        return null;
    }

    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
}

interface BarsQualitySummary {
    statuses: string[];
    gapCount: number;
    hasQualityStatus: boolean;
}

function summarizeBarsQuality(bars: readonly MarketdataBar[]): BarsQualitySummary {
    const statuses = Array.from(new Set(
        bars
            .map((bar) => bar.qualityStatus)
            .filter((value): value is string => Boolean(value)),
    ));

    return {
        statuses,
        gapCount: bars.filter((bar) => GAP_QUALITY_STATUSES.has((bar.qualityStatus ?? '').toUpperCase())).length,
        hasQualityStatus: statuses.length > 0,
    };
}

interface BarsFreshnessSummary {
    state: FreshnessState;
    detail: string;
    stale: boolean;
}

function summarizeBarsFreshness(
    submittedQuery: MarketdataBarsQuery | null,
    bars: readonly MarketdataBar[],
    quality: BarsQualitySummary,
    error: unknown,
    loading: boolean,
): BarsFreshnessSummary {
    if (error) {
        return {state: 'error', detail: 'bars query failed', stale: false};
    }
    if (!submittedQuery) {
        return {state: 'disabled', detail: 'not queried', stale: false};
    }
    if (loading) {
        return {state: 'delayed', detail: 'loading', stale: false};
    }
    if (bars.length === 0) {
        return {state: 'no_data', detail: '0 bars', stale: false};
    }
    if (quality.gapCount > 0) {
        return {state: 'degraded', detail: `${quality.gapCount} gap bars`, stale: false};
    }

    const lastBar = bars[bars.length - 1];
    const lastCloseMs = timestampMs(lastBar?.closeTime ?? lastBar?.openTime);
    const requestedEndMs = timestampMs(submittedQuery.endTime);
    const intervalMs = intervalToMs(submittedQuery.interval);
    const isStale = Boolean(
        lastCloseMs !== null
        && requestedEndMs !== null
        && intervalMs !== null
        && lastCloseMs + intervalMs < requestedEndMs,
    );

    if (isStale) {
        return {
            state: 'stale',
            detail: `last ${formatDateTime(lastBar.closeTime ?? lastBar.openTime)}`,
            stale: true,
        };
    }

    return {
        state: 'fresh',
        detail: `last ${formatDateTime(lastBar.closeTime ?? lastBar.openTime)}`,
        stale: false,
    };
}

function QualityTags({quality}: {quality: BarsQualitySummary}) {
    if (!quality.hasQualityStatus) {
        return <Tag>qualityStatus unavailable</Tag>;
    }

    return (
        <Space size={4} wrap>
            {quality.statuses.map((status) => (
                <Tag key={status} color={status === 'OK' ? 'green' : 'orange'}>
                    {status}
                </Tag>
            ))}
        </Space>
    );
}

function MetricText({children}: {children: ReactNode}) {
    return <Typography.Text style={{fontFamily: 'var(--nq-font-mono)'}}>{children}</Typography.Text>;
}

const jobColumns = (
    onRunOnce: (jobId: string) => void,
    pendingJobId: string | null,
): ColumnsType<MarketdataIngestionJob> => [
    {title: 'Job ID', dataIndex: 'jobId', key: 'jobId', width: 260, ellipsis: true},
    {title: 'Exchange', dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: 'Market', dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 130},
    {title: 'Interval', dataIndex: 'interval', key: 'interval', width: 100},
    {title: 'Status', dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag>},
    {title: 'Start', dataIndex: 'startTime', key: 'startTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'End', dataIndex: 'endTime', key: 'endTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Updated', dataIndex: 'updatedAt', key: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value)},
    {
        title: 'Action',
        key: 'action',
        fixed: 'right',
        width: 130,
        render: (_, record) => (
            <Button size="small" loading={pendingJobId === record.jobId} onClick={() => onRunOnce(record.jobId)}>
                Run once
            </Button>
        ),
    },
];

const runColumns: ColumnsType<MarketdataIngestionRun> = [
    {title: 'Run ID', dataIndex: 'runId', key: 'runId', width: 260, ellipsis: true},
    {title: 'Status', dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag>},
    {title: 'Fetched', dataIndex: 'fetchedBars', key: 'fetchedBars', width: 100},
    {title: 'Inserted', dataIndex: 'insertedBars', key: 'insertedBars', width: 100},
    {title: 'Updated', dataIndex: 'updatedBars', key: 'updatedBars', width: 100},
    {title: 'Skipped', dataIndex: 'skippedBars', key: 'skippedBars', width: 100},
    {title: 'Started', dataIndex: 'startedAt', key: 'startedAt', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Finished', dataIndex: 'finishedAt', key: 'finishedAt', width: 180, render: (value?: string | null) => value ? formatDateTime(value) : '-'},
    {title: 'Error', dataIndex: 'errorMessage', key: 'errorMessage', width: 280, ellipsis: true, render: (value?: string | null) => value || '-'},
];

const datasetColumns = (
    onRefreshQuality: (datasetId: string) => void,
    pendingDatasetId: string | null,
): ColumnsType<MarketdataDataset> => [
    {title: 'Dataset ID', dataIndex: 'datasetId', key: 'datasetId', width: 260, ellipsis: true},
    {title: 'Name', dataIndex: 'datasetName', key: 'datasetName', width: 180},
    {title: 'Exchange', dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: 'Market', dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 130},
    {title: 'Interval', dataIndex: 'interval', key: 'interval', width: 100},
    {title: 'Status', dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'READY' ? 'green' : value === 'INVALID' ? 'red' : 'blue'}>{value}</Tag>},
    {title: 'Quality', dataIndex: 'qualityStatus', key: 'qualityStatus', width: 140, render: (value: string) => <Tag color={value === 'OK' ? 'green' : value === 'GAP_DETECTED' ? 'orange' : 'red'}>{value}</Tag>},
    {title: 'Bars', dataIndex: 'barCount', key: 'barCount', width: 100},
    {title: 'Gaps', dataIndex: 'gapCount', key: 'gapCount', width: 100},
    {title: 'Start', dataIndex: 'startTime', key: 'startTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'End', dataIndex: 'endTime', key: 'endTime', width: 180, render: (value: string) => formatDateTime(value)},
    {
        title: 'Action',
        key: 'action',
        fixed: 'right',
        width: 150,
        render: (_, record) => (
            <Button
                size="small"
                loading={pendingDatasetId === record.datasetId}
                onClick={() => onRefreshQuality(record.datasetId)}
            >
                Refresh quality
            </Button>
        ),
    },
];

export function MarketdataPage() {
    const [form] = Form.useForm<MarketdataBarsFormValues>();
    const [jobForm] = Form.useForm<CreateMarketdataIngestionJobFormValues>();
    const [datasetForm] = Form.useForm<CreateMarketdataDatasetFormValues>();
    const [messageApi, contextHolder] = message.useMessage();
    const queryClient = useQueryClient();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const [submittedQuery, setSubmittedQuery] = useState<MarketdataBarsQuery | null>(null);
    const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
    const [pendingJobId, setPendingJobId] = useState<string | null>(null);
    const [pendingDatasetId, setPendingDatasetId] = useState<string | null>(null);

    // Chart foundation(B0.4) 使用 additive v2 CSS vars；页级注入不改全局 AppProviders。
    useEffect(() => {
        applyNqCssVars();
    }, []);

    const barsQuery = useQuery({
        queryKey: ['marketdata-bars', submittedQuery],
        queryFn: () => marketdataApi.listBars(submittedQuery as MarketdataBarsQuery),
        enabled: submittedQuery !== null,
    });
    const jobsQuery = useQuery({
        queryKey: ['marketdata-ingestion-jobs'],
        queryFn: marketdataApi.listIngestionJobs,
    });
    const runsQuery = useQuery({
        queryKey: ['marketdata-ingestion-runs', selectedJobId],
        queryFn: () => marketdataApi.listIngestionRuns(selectedJobId as string),
        enabled: selectedJobId !== null,
    });
    const datasetsQuery = useQuery({
        queryKey: ['marketdata-datasets'],
        queryFn: marketdataApi.listDatasets,
    });
    const createJobMutation = useMutation({
        mutationFn: marketdataApi.createIngestionJob,
        onSuccess: async (job) => {
            setSelectedJobId(job.jobId);
            messageApi.success('Marketdata ingestion job created');
            await queryClient.invalidateQueries({queryKey: ['marketdata-ingestion-jobs']});
        },
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
    });
    const runOnceMutation = useMutation({
        mutationFn: marketdataApi.runIngestionJobOnce,
        onMutate: (jobId) => setPendingJobId(jobId),
        onSuccess: async (run) => {
            setSelectedJobId(run.jobId);
            messageApi.info(`Run finished: ${run.status}`);
            await queryClient.invalidateQueries({queryKey: ['marketdata-ingestion-jobs']});
            await queryClient.invalidateQueries({queryKey: ['marketdata-ingestion-runs', run.jobId]});
            await queryClient.invalidateQueries({queryKey: ['marketdata-bars']});
        },
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
        onSettled: () => setPendingJobId(null),
    });
    const createDatasetMutation = useMutation({
        mutationFn: marketdataApi.createDataset,
        onSuccess: async (dataset) => {
            messageApi.success(`Dataset created: ${dataset.qualityStatus}`);
            await queryClient.invalidateQueries({queryKey: ['marketdata-datasets']});
        },
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
    });
    const refreshDatasetMutation = useMutation({
        mutationFn: marketdataApi.refreshDatasetQuality,
        onMutate: (datasetId) => setPendingDatasetId(datasetId),
        onSuccess: async (dataset) => {
            messageApi.info(`Dataset quality: ${dataset.qualityStatus}`);
            await queryClient.invalidateQueries({queryKey: ['marketdata-datasets']});
        },
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
        onSettled: () => setPendingDatasetId(null),
    });
    const bars = barsQuery.data ?? [];
    const chartBars = useMemo(() => bars.map(toNqKlineBar), [bars]);
    const barsQuality = useMemo(() => summarizeBarsQuality(bars), [bars]);
    const barsFreshness = useMemo(
        () => summarizeBarsFreshness(submittedQuery, bars, barsQuality, barsQuery.error, barsQuery.isLoading),
        [bars, barsQuality, barsQuery.error, barsQuery.isLoading, submittedQuery],
    );
    const lastBar = bars.length > 0 ? bars[bars.length - 1] : null;
    const chartError = barsQuery.error ? formatApiError(barsQuery.error as AppApiError) : null;
    const chartEmptyText = submittedQuery
        ? '当前查询没有返回 OHLCV bars'
        : '提交查询后展示 K 线主图';
    const chartSourceLabel = submittedQuery
        ? `${submittedQuery.exchangeCode} ${submittedQuery.symbol} ${submittedQuery.interval}`
        : 'Marketdata bars';

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            {contextHolder}
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="Marketdata"
                    description="SPOT 历史 OHLCV 查询、接入任务和 Dataset 管理入口，固定使用当前 freeze 可验收的交易所、交易对和周期范围。"
                    badge="Marketdata"
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="查询条件"
                extra={<Button type="primary" onClick={() => form.submit()}>查询</Button>}
            >
                <Form<MarketdataBarsFormValues>
                    form={form}
                    layout="vertical"
                    initialValues={{
                        exchangeCode: contextExchangeCode ?? 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                        page: 0,
                        size: 100,
                    }}
                    onFinish={(values) => setSubmittedQuery(normalizeBarsQuery(values))}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime" rules={[{required: true, message: '请选择开始时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime" rules={[{required: true, message: '请选择结束时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                <Typography.Text type="secondary">默认交易所来自当前账户上下文：{contextExchangeCode ?? '未选择'}</Typography.Text>
            </Card>
            <Card className="page-section" bordered={false} title="K 线 readiness 视图">
                <div data-testid="marketdata-kline-readiness-view" style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                    <Descriptions
                        size="small"
                        column={{xs: 1, sm: 2, md: 3}}
                        items={[
                            {key: 'exchange', label: 'Exchange', children: <MetricText>{submittedQuery?.exchangeCode ?? '-'}</MetricText>},
                            {key: 'symbol', label: 'Instrument', children: <MetricText>{submittedQuery?.symbol ?? '-'}</MetricText>},
                            {key: 'interval', label: 'Timeframe', children: <MetricText>{submittedQuery?.interval ?? '-'}</MetricText>},
                            {key: 'barCount', label: 'Bar count', children: <MetricText>{bars.length}</MetricText>},
                            {key: 'lastBar', label: 'Last bar time', children: <MetricText>{lastBar ? formatDateTime(lastBar.closeTime ?? lastBar.openTime) : '-'}</MetricText>},
                            {key: 'quality', label: 'Data quality', children: <QualityTags quality={barsQuality}/>},
                        ]}
                    />
                    <Space size={12} wrap>
                        <DataFreshness
                            source={chartSourceLabel}
                            state={barsFreshness.state}
                            detail={barsFreshness.detail}
                            inline
                        />
                        {barsQuality.gapCount > 0 ? (
                            <Tag color="orange">gap / qualityStatus: {barsQuality.gapCount}</Tag>
                        ) : null}
                        {!barsQuality.hasQualityStatus && bars.length > 0 ? (
                            <Tag>qualityStatus missing: non-blocking</Tag>
                        ) : null}
                    </Space>
                    {barsFreshness.state === 'stale' ? (
                        <Alert
                            type="warning"
                            showIcon
                            message="Marketdata bars stale"
                            description="最后一根 K 线未覆盖查询结束时间；本视图只展示已有历史 bars，不做实时刷新或 WebSocket 补齐。"
                        />
                    ) : null}
                    {barsQuality.gapCount > 0 ? (
                        <Alert
                            type="warning"
                            showIcon
                            message="Marketdata bars quality degraded"
                            description="后端返回了非 OK qualityStatus；图表继续展示已有 bars，gap 修复仍由 MarketData ingestion / dataset quality 流程处理。"
                        />
                    ) : null}
                    {!barsQuality.hasQualityStatus && bars.length > 0 ? (
                        <Alert
                            type="info"
                            showIcon
                            message="qualityStatus unavailable"
                            description="当前 bars payload 未携带 qualityStatus；这是非阻断提示，不会伪造 gap 状态。"
                        />
                    ) : null}
                    <div
                        style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                            gap: 16,
                        }}
                    >
                        <NqKlineChart
                            bars={chartBars}
                            loading={barsQuery.isLoading}
                            error={chartError}
                            stale={barsFreshness.stale}
                            staleDetail={barsFreshness.detail}
                            sourceLabel={chartSourceLabel}
                            title="OHLCV K-line"
                            emptyText={chartEmptyText}
                            height={320}
                        />
                        <NqVolumeChart
                            bars={chartBars}
                            loading={barsQuery.isLoading}
                            error={chartError}
                            sourceLabel={chartSourceLabel}
                            title="Volume"
                            emptyText={submittedQuery ? '当前查询没有返回成交量 bars' : '提交查询后展示成交量'}
                            height={180}
                        />
                    </div>
                    <Typography.Text type="secondary">
                        本视图复用现有 /api/marketdata/bars 与 marketdataApi.listBars()；不接 WebSocket、不接真实交易所私有流、不做买卖点或指标系统。
                    </Typography.Text>
                </div>
            </Card>
            <Card className="page-section" bordered={false} title="Bars 结果">
                {barsQuery.error ? (
                    <Alert type="error" showIcon message="Marketdata bars 查询失败" description={formatApiError(barsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey={(record) => `${record.exchangeCode}-${record.marketType}-${record.symbol}-${record.interval}-${record.openTime}`}
                        columns={columns}
                        dataSource={barsQuery.data ?? []}
                        loading={barsQuery.isLoading || barsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1400}}
                    />
                )}
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="接入任务"
                extra={<Button type="primary" loading={createJobMutation.isPending} onClick={() => jobForm.submit()}>创建任务</Button>}
            >
                <Form<CreateMarketdataIngestionJobFormValues>
                    form={jobForm}
                    layout="vertical"
                    initialValues={{
                        exchangeCode: 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                    }}
                    onFinish={(values) => createJobMutation.mutate(normalizeIngestionJob(values))}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime" rules={[{required: true, message: '请选择开始时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime" rules={[{required: true, message: '请选择结束时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                {jobsQuery.error ? (
                    <Alert type="error" showIcon message="Marketdata ingestion jobs 查询失败" description={formatApiError(jobsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey="jobId"
                        columns={jobColumns((jobId) => runOnceMutation.mutate(jobId), pendingJobId)}
                        dataSource={jobsQuery.data ?? []}
                        loading={jobsQuery.isLoading || jobsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1600}}
                        onRow={(record) => ({
                            onClick: () => setSelectedJobId(record.jobId),
                        })}
                    />
                )}
            </Card>
            <Card className="page-section" bordered={false} title="运行结果">
                {selectedJobId ? (
                    runsQuery.error ? (
                        <Alert type="error" showIcon message="Marketdata ingestion runs 查询失败" description={formatApiError(runsQuery.error as AppApiError)} />
                    ) : (
                        <Table
                            rowKey="runId"
                            columns={runColumns}
                            dataSource={runsQuery.data ?? []}
                            loading={runsQuery.isLoading || runsQuery.isFetching}
                            pagination={{pageSize: 5, showSizeChanger: false}}
                            scroll={{x: 1500}}
                        />
                    )
                ) : (
                    <Alert type="info" showIcon message="请选择或创建一个接入任务查看运行结果" />
                )}
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="Datasets"
                extra={(
                    <Button
                        type="primary"
                        loading={createDatasetMutation.isPending}
                        onClick={() => datasetForm.submit()}
                    >
                        创建 Dataset
                    </Button>
                )}
            >
                <Form<CreateMarketdataDatasetFormValues>
                    form={datasetForm}
                    layout="vertical"
                    initialValues={{
                        datasetName: `BINANCE-BTC-USDT-1m-${Date.now()}`,
                        exchangeCode: 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                    }}
                    onFinish={(values) => createDatasetMutation.mutate({
                        ...normalizeDataset(values),
                        datasetName: values.datasetName || `BINANCE-BTC-USDT-1m-${Date.now()}`,
                    })}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label="Dataset Name" name="datasetName">
                            <Input style={{width: 260}} />
                        </Form.Item>
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime" rules={[{required: true, message: '请选择开始时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime" rules={[{required: true, message: '请选择结束时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                {datasetsQuery.error ? (
                    <Alert type="error" showIcon message="Marketdata datasets 查询失败" description={formatApiError(datasetsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey="datasetId"
                        columns={datasetColumns((datasetId) => refreshDatasetMutation.mutate(datasetId), pendingDatasetId)}
                        dataSource={datasetsQuery.data ?? []}
                        loading={datasetsQuery.isLoading || datasetsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1900}}
                        locale={{emptyText: '暂无 marketdata dataset，可先创建数据集。'}}
                    />
                )}
            </Card>
        </Space>
    );
}

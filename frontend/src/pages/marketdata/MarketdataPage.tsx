import {Alert, Button, Card, Form, Input, Select, Space, Table, Tag, Typography, message} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {PageHero} from '@/components/page/PageHero';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {
    CreateMarketdataIngestionJobRequest,
    MarketdataBar,
    MarketdataBarsQuery,
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

export function MarketdataPage() {
    const [form] = Form.useForm<MarketdataBarsQuery>();
    const [jobForm] = Form.useForm<CreateMarketdataIngestionJobRequest>();
    const [messageApi, contextHolder] = message.useMessage();
    const queryClient = useQueryClient();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const [submittedQuery, setSubmittedQuery] = useState<MarketdataBarsQuery | null>(null);
    const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
    const [pendingJobId, setPendingJobId] = useState<string | null>(null);

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

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            {contextHolder}
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="Marketdata"
                    description="SPOT 历史 OHLCV 查询与接入任务入口。GateH-2 固定 OKX / BINANCE、BTC-USDT / ETH-USDT / SOL-USDT 与 1m 到 1d 周期。"
                    badge="GateH-2"
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="查询条件"
                extra={<Button type="primary" onClick={() => form.submit()}>查询</Button>}
            >
                <Form<MarketdataBarsQuery>
                    form={form}
                    layout="vertical"
                    initialValues={{
                        exchangeCode: contextExchangeCode ?? 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                        startTime: '2025-01-01T00:00:00Z',
                        endTime: '2025-01-01T00:05:59Z',
                        page: 0,
                        size: 100,
                    }}
                    onFinish={(values) => setSubmittedQuery(values)}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={[{label: 'BINANCE', value: 'BINANCE'}, {label: 'OKX', value: 'OKX'}]} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={[{label: 'SPOT', value: 'SPOT'}]} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select style={{width: 160}} options={[
                                {label: 'BTC-USDT', value: 'BTC-USDT'},
                                {label: 'ETH-USDT', value: 'ETH-USDT'},
                                {label: 'SOL-USDT', value: 'SOL-USDT'},
                            ]} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={['1m', '5m', '15m', '1h', '4h', '1d'].map((value) => ({label: value, value}))} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime">
                            <Input style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime">
                            <Input style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                <Typography.Text type="secondary">默认交易所来自当前账户上下文：{contextExchangeCode ?? '未选择'}</Typography.Text>
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
                <Form<CreateMarketdataIngestionJobRequest>
                    form={jobForm}
                    layout="vertical"
                    initialValues={{
                        exchangeCode: 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                        startTime: '2025-01-01T00:00:00Z',
                        endTime: '2025-01-01T00:05:59Z',
                    }}
                    onFinish={(values) => createJobMutation.mutate(values)}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={[{label: 'BINANCE', value: 'BINANCE'}, {label: 'OKX', value: 'OKX'}]} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={[{label: 'SPOT', value: 'SPOT'}]} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select style={{width: 160}} options={[
                                {label: 'BTC-USDT', value: 'BTC-USDT'},
                                {label: 'ETH-USDT', value: 'ETH-USDT'},
                                {label: 'SOL-USDT', value: 'SOL-USDT'},
                            ]} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={['1m', '5m', '15m', '1h', '4h', '1d'].map((value) => ({label: value, value}))} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime">
                            <Input style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime">
                            <Input style={{width: 220}} />
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
        </Space>
    );
}

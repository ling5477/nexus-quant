import {Alert, Button, Card, Form, Input, Select, Space, Table, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useQuery} from '@tanstack/react-query';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {PageHero} from '@/components/page/PageHero';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {MarketdataBar, MarketdataBarsQuery} from '@/types/marketdata';
import {formatDateTime, formatNumber} from '@/utils/formatters';

const columns: ColumnsType<MarketdataBar> = [
    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 140},
    {title: 'Interval', dataIndex: 'interval', key: 'interval', width: 100},
    {title: 'Open Time', dataIndex: 'openTime', key: 'openTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Close Time', dataIndex: 'closeTime', key: 'closeTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Open', dataIndex: 'openPrice', key: 'openPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'High', dataIndex: 'highPrice', key: 'highPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Low', dataIndex: 'lowPrice', key: 'lowPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Close', dataIndex: 'closePrice', key: 'closePrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Volume', dataIndex: 'volume', key: 'volume', width: 120, render: (value: number) => formatNumber(value, 8)},
];

export function MarketdataPage() {
    const [form] = Form.useForm<MarketdataBarsQuery>();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const [submittedQuery, setSubmittedQuery] = useState<MarketdataBarsQuery | null>(null);

    const barsQuery = useQuery({
        queryKey: ['marketdata-bars', submittedQuery],
        queryFn: () => marketdataApi.listBars(submittedQuery as MarketdataBarsQuery),
        enabled: submittedQuery !== null,
    });

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="Marketdata"
                    description="正式行情查询入口。当前先提供 bars 查询工作台，后续多币种研究与 selector 都围绕这里的正式数据范围工作。"
                    badge="GateH-PRE / PRE-2"
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
                        symbol: 'BTCUSDT',
                        interval: '1m',
                        startTime: '2025-01-01T00:00:00Z',
                        endTime: '2025-01-01T00:05:59Z',
                    }}
                    onFinish={(values) => setSubmittedQuery(values)}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={[{label: 'BINANCE', value: 'BINANCE'}, {label: 'OKX', value: 'OKX'}]} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Input style={{width: 180}} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={[{label: '1m', value: '1m'}]} />
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
                        rowKey={(record) => `${record.symbol}-${record.openTime}`}
                        columns={columns}
                        dataSource={barsQuery.data ?? []}
                        loading={barsQuery.isLoading || barsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1400}}
                    />
                )}
            </Card>
        </Space>
    );
}

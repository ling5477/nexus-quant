import {Alert, App, Button, Card, Form, Select, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery} from '@tanstack/react-query';
import {useState} from 'react';

import {formatApiError} from '@/api/errors';
import {instrumentsApi} from '@/api/instruments';
import {PageHero} from '@/components/page/PageHero';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {InstrumentCatalogItem} from '@/types/instruments';
import {formatDateTime, formatNumber} from '@/utils/formatters';

interface InstrumentFilterValues {
    exchangeCode?: string;
}

const columns: ColumnsType<InstrumentCatalogItem> = [
    {title: '交易所', dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: '内部 Symbol', dataIndex: 'internalSymbol', key: 'internalSymbol', width: 160},
    {title: '原生 Symbol', dataIndex: 'exchangeSymbol', key: 'exchangeSymbol', width: 160},
    {title: 'Base', dataIndex: 'baseAsset', key: 'baseAsset', width: 100},
    {title: 'Quote', dataIndex: 'quoteAsset', key: 'quoteAsset', width: 100},
    {title: '状态', dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'TRADING' || value === 'LIVE' ? 'success' : 'default'}>{value}</Tag>},
    {title: 'Tick', dataIndex: 'tickSize', key: 'tickSize', width: 120, render: (value: number | null) => formatNumber(value, 8)},
    {title: 'Step', dataIndex: 'stepSize', key: 'stepSize', width: 120, render: (value: number | null) => formatNumber(value, 8)},
    {title: '最小数量', dataIndex: 'minQuantity', key: 'minQuantity', width: 140, render: (value: number | null) => formatNumber(value, 8)},
    {title: '来源', dataIndex: 'source', key: 'source', width: 220},
    {title: '同步时间', dataIndex: 'syncedAt', key: 'syncedAt', width: 180, render: (value: string) => formatDateTime(value)},
];

/**
 * 把后端受控 catalog sync 错误转换为页面提示。
 * Why:
 * GateJ-FREEZE 允许 Catalog 为空，但不允许 Binance 451 这类外部限制以 internal server error 呈现；
 * 这里保留 trace/error 兜底，同时把已知受控 409 显示为操作员能理解的 freeze 提示。
 */
function formatInstrumentCatalogSyncError(error: AppApiError): string {
    if (error.status === 409 && error.message.includes('禁用外部交易所同步')) {
        return '当前环境禁用外部交易所同步，Catalog 可以为空，不影响 freeze 验收。';
    }
    if (error.status === 409 && error.message.includes('instrument catalog 同步暂不可用')) {
        return '外部交易所同步暂不可用，请使用当前 Catalog 查询结果或稍后重试。';
    }
    return formatApiError(error);
}

export function InstrumentsPage() {
    const {message} = App.useApp();
    const [form] = Form.useForm<InstrumentFilterValues>();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const [exchangeCode, setExchangeCode] = useState<string | undefined>(contextExchangeCode ?? undefined);

    const instrumentsQuery = useQuery({
        queryKey: ['instruments', exchangeCode ?? 'ALL'],
        queryFn: () => instrumentsApi.list(exchangeCode),
    });

    const syncMutation = useMutation({
        mutationFn: instrumentsApi.sync,
        onSuccess: async (result) => {
            message.success(`同步完成：读取 ${result.rowsRead} 条，新增 ${result.rowsInserted} 条，更新 ${result.rowsUpdated} 条。`);
            await instrumentsQuery.refetch();
        },
        onError: (error) => message.warning(formatInstrumentCatalogSyncError(error as AppApiError)),
    });

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="Instruments"
                    description="正式 instrument/symbol catalog。后续交易对 selector、precision 校验和多币种工作流都以这里为主数据入口。"
                    badge="Catalog"
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="筛选与同步"
                extra={
                    <Space>
                        <Button type="primary" onClick={() => form.submit()}>
                            查询
                        </Button>
                        <Button onClick={() => syncMutation.mutate(exchangeCode)} loading={syncMutation.isPending}>
                            同步 Catalog
                        </Button>
                    </Space>
                }
            >
                <Form<InstrumentFilterValues>
                    form={form}
                    layout="vertical"
                    initialValues={{exchangeCode: contextExchangeCode ?? undefined}}
                    onFinish={(values) => setExchangeCode(values.exchangeCode || undefined)}
                >
                    <Form.Item label="交易所" name="exchangeCode">
                        <Select allowClear options={[{label: 'BINANCE', value: 'BINANCE'}, {label: 'OKX', value: 'OKX'}]} />
                    </Form.Item>
                </Form>
                <Typography.Text type="secondary">
                    当前账户上下文默认交易所：{contextExchangeCode ?? '未选择'}
                </Typography.Text>
            </Card>
            <Card className="page-section" bordered={false} title="Catalog 列表">
                {instrumentsQuery.error ? (
                    <Alert type="error" showIcon message="Instrument catalog 加载失败" description={formatApiError(instrumentsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey="instrumentId"
                        columns={columns}
                        dataSource={instrumentsQuery.data ?? []}
                        loading={instrumentsQuery.isLoading || instrumentsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1600}}
                    />
                )}
            </Card>
        </Space>
    );
}

import {
    Alert,
    Button,
    Card,
    Collapse,
    Descriptions,
    Grid,
    List,
    Pagination,
    Space,
    Table,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState, type ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {NqAmountText} from '@/components/nq';
import {formatApiError} from '@/api/errors';
import {useRunOrdersQuery, useRunTradesQuery} from '@/features/runs/hooks/useRunListQuery';
import type {StrategyRunOrderItem, StrategyRunTradeItem} from '@/features/runs/types/runs';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';

const identity = (value: string | null) =>
    value ? <Typography.Text copyable>{value}</Typography.Text> : '—';
const amount = (value: string | null) => <NqAmountText exact value={value} />;

/** 手机保留可决策的金额与状态，追踪字段可展开；不能仅留下横向表格的首个ID列。 */
export function MobileRecord({
    id,
    fields,
    details,
    detailLabel,
}: {
    id: string;
    fields: [string, ReactNode][];
    details: [string, ReactNode][];
    detailLabel: string;
}) {
    return (
        <List.Item data-run-record-id={id} style={{display: 'block'}}>
            <Card size="small" title={identity(id)}>
                <Descriptions
                    size="small"
                    column={1}
                    items={fields.map(([label, children]) => ({key: label, label, children}))}
                />
                <Collapse
                    size="small"
                    ghost
                    items={[
                        {
                            key: 'details',
                            label: detailLabel,
                            children: (
                                <Descriptions
                                    size="small"
                                    column={1}
                                    items={details.map(([label, children]) => ({
                                        key: label,
                                        label,
                                        children,
                                    }))}
                                />
                            ),
                        },
                    ]}
                />
            </Card>
        </List.Item>
    );
}

/** 运行目前至多关联一个canonical Order，仍使用有界查询而不依赖旧聚合数组。 */
export function RunOrdersTable({runId, active}: {runId: string; active: boolean}) {
    const {t} = useTranslation('pages');
    const screens = Grid.useBreakpoint();
    const [page, setPage] = useState(1);
    const query = useRunOrdersQuery(runId, {limit: 10, offset: (page - 1) * 10}, active);
    const columns: ColumnsType<StrategyRunOrderItem> = [
        {title: t('orderId'), dataIndex: 'orderId', width: 180, render: identity},
        {title: t('orderStatus'), dataIndex: 'orderStatus', width: 140},
        {title: t('symbol'), dataIndex: 'symbol', width: 130},
        {title: t('side'), dataIndex: 'side', width: 90},
        {title: t('orderType'), dataIndex: 'orderType', width: 100},
        {title: t('price'), dataIndex: 'price', width: 260, align: 'right', render: amount},
        {title: t('quantity'), dataIndex: 'quantity', width: 180, align: 'right', render: amount},
        {title: t('createdAt'), dataIndex: 'createdAt', width: 180, render: formatDateTime},
        {title: t('updatedAt'), dataIndex: 'updatedAt', width: 180, render: formatDateTime},
        {title: t('clientOrderId'), dataIndex: 'clientOrderId', width: 200, render: identity},
        {
            title: t('runExchangeOrderId'),
            dataIndex: 'exchangeOrderId',
            width: 200,
            render: identity,
        },
    ];
    if (query.isError)
        return (
            <Alert
                type="error"
                showIcon
                message={t('runRelatedReadFailed')}
                description={formatApiError(query.error as AppApiError)}
                action={<Button onClick={() => void query.refetch()}>{t('retry')}</Button>}
            />
        );
    if (!screens.md)
        return (
            <Space direction="vertical" style={{display: 'flex'}}>
                <List
                    dataSource={query.data?.items ?? []}
                    loading={query.isFetching}
                    locale={{emptyText: t('runNoOrders')}}
                    renderItem={(row) => (
                        <MobileRecord
                            id={row.orderId}
                            detailLabel={t('viewDetails')}
                            fields={[
                                [t('orderStatus'), row.orderStatus],
                                [t('symbol'), row.symbol],
                                [t('side'), row.side],
                                [t('orderType'), row.orderType],
                                [t('price'), amount(row.price)],
                                [t('quantity'), amount(row.quantity)],
                            ]}
                            details={[
                                [t('clientOrderId'), identity(row.clientOrderId)],
                                [t('runExchangeOrderId'), identity(row.exchangeOrderId)],
                                [t('createdAt'), formatDateTime(row.createdAt)],
                                [t('updatedAt'), formatDateTime(row.updatedAt)],
                            ]}
                        />
                    )}
                />
                <Pagination
                    size="small"
                    current={page}
                    pageSize={10}
                    total={query.data?.total ?? 0}
                    showSizeChanger={false}
                    hideOnSinglePage
                    onChange={setPage}
                />
            </Space>
        );
    return (
        <Table<StrategyRunOrderItem>
            size="small"
            rowKey="orderId"
            columns={columns}
            dataSource={query.data?.items ?? []}
            loading={query.isFetching}
            scroll={{x: 1840}}
            pagination={{
                current: page,
                pageSize: 10,
                total: query.data?.total ?? 0,
                showSizeChanger: false,
                hideOnSinglePage: true,
                onChange: setPage,
            }}
            locale={{emptyText: t('runNoOrders')}}
        />
    );
}

/** 每次只加载一页成交；原币手续费保留符号、零及null，不据当前费率补算。 */
export function RunTradesTable({runId, active}: {runId: string; active: boolean}) {
    const {t} = useTranslation('pages');
    const screens = Grid.useBreakpoint();
    const [page, setPage] = useState(1);
    const query = useRunTradesQuery(runId, {limit: 10, offset: (page - 1) * 10}, active);
    const columns: ColumnsType<StrategyRunTradeItem> = [
        {title: t('tradeId'), dataIndex: 'tradeId', width: 180, render: identity},
        {title: t('orderId'), dataIndex: 'orderId', width: 180, render: identity},
        {title: t('symbol'), dataIndex: 'symbol', width: 120},
        {title: t('price'), dataIndex: 'price', width: 260, align: 'right', render: amount},
        {title: t('quantity'), dataIndex: 'quantity', width: 180, align: 'right', render: amount},
        {title: t('fee'), dataIndex: 'fee', width: 180, align: 'right', render: amount},
        {
            title: t('feeCurrency'),
            dataIndex: 'feeCurrency',
            width: 120,
            render: (value: string | null) => value ?? '—',
        },
        {title: t('runTradeTime'), dataIndex: 'tradeTs', width: 180, render: formatDateTime},
        {title: t('exchangeTradeId'), dataIndex: 'exchangeTradeId', width: 200, render: identity},
        {
            title: t('runExchangeOrderId'),
            dataIndex: 'exchangeOrderId',
            width: 200,
            render: identity,
        },
    ];
    if (query.isError)
        return (
            <Alert
                type="error"
                showIcon
                message={t('runRelatedReadFailed')}
                description={formatApiError(query.error as AppApiError)}
                action={<Button onClick={() => void query.refetch()}>{t('retry')}</Button>}
            />
        );
    return (
        <Space direction="vertical" style={{display: 'flex'}}>
            {!screens.md ? (
                <>
                    <List
                        dataSource={query.data?.items ?? []}
                        loading={query.isFetching}
                        locale={{emptyText: t('runNoTrades')}}
                        renderItem={(row) => (
                            <MobileRecord
                                id={row.tradeId}
                                detailLabel={t('viewDetails')}
                                fields={[
                                    [t('symbol'), row.symbol],
                                    [t('price'), amount(row.price)],
                                    [t('quantity'), amount(row.quantity)],
                                    [t('fee'), amount(row.fee)],
                                    [t('feeCurrency'), row.feeCurrency ?? '—'],
                                    [t('runTradeTime'), formatDateTime(row.tradeTs)],
                                ]}
                                details={[
                                    [t('orderId'), identity(row.orderId)],
                                    [t('exchangeTradeId'), identity(row.exchangeTradeId)],
                                    [t('runExchangeOrderId'), identity(row.exchangeOrderId)],
                                ]}
                            />
                        )}
                    />
                    <Pagination
                        size="small"
                        current={page}
                        pageSize={10}
                        total={query.data?.total ?? 0}
                        showSizeChanger={false}
                        hideOnSinglePage
                        onChange={setPage}
                    />
                </>
            ) : (
                <Table<StrategyRunTradeItem>
                    size="small"
                    rowKey="tradeId"
                    columns={columns}
                    dataSource={query.data?.items ?? []}
                    loading={query.isFetching}
                    scroll={{x: 1800}}
                    pagination={{
                        current: page,
                        pageSize: 10,
                        total: query.data?.total ?? 0,
                        showSizeChanger: false,
                        hideOnSinglePage: true,
                        onChange: setPage,
                    }}
                    locale={{emptyText: t('runNoTrades')}}
                />
            )}
            <Typography.Text type="secondary">{t('runFeeMeaning')}</Typography.Text>
        </Space>
    );
}

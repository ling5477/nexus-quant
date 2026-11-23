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
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useRunBusinessEventsQuery} from '@/features/runs/hooks/useRunListQuery';
import type {StrategyRunBusinessEventItem} from '@/features/runs/types/runs';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';

const identity = (value: string | null) =>
    value ? (
        <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
            {value}
        </Typography.Text>
    ) : (
        '—'
    );

/** 仅展示正式身份可确定归属的历史事件；不把命令记录、回执或账户快照说成运行已成功。 */
export function StrategyRunBusinessEventsTable({
    runId,
    active,
    onOrders,
    onTrades,
}: {
    runId: string;
    active: boolean;
    onOrders: () => void;
    onTrades: () => void;
}) {
    const {t} = useTranslation('pages');
    const screens = Grid.useBreakpoint();
    const [page, setPage] = useState(1);
    const query = useRunBusinessEventsQuery(runId, {limit: 10, offset: (page - 1) * 10}, active);
    const eventName = (row: StrategyRunBusinessEventItem) =>
        t(`runEvents.type.${row.eventType}`, {defaultValue: t('runEvents.unknownType')});
    const meaning = (row: StrategyRunBusinessEventItem) =>
        row.eventType.endsWith('Command')
            ? t('runEvents.commandMeaning')
            : row.association === 'ORDER_TRIGGERED_POSITION'
              ? t('runEvents.positionMeaning')
              : null;
    const state = (row: StrategyRunBusinessEventItem) => {
        const entries = [
            [t('runEvents.status'), row.status],
            [t('runEvents.decision'), row.decision],
            [t('runEvents.result'), row.result],
            [t('runEvents.rejectCode'), row.rejectCode],
        ].filter((entry): entry is [string, string] => entry[1] !== null);
        return entries.length ? (
            <Space wrap size={[4, 4]}>
                {entries.map(([label, value]) => (
                    <Tag
                        key={label}
                        style={{maxWidth: '100%', whiteSpace: 'normal', overflowWrap: 'anywhere'}}
                        color={
                            ['REJECT', 'FAILED', 'REJECTED', 'RISK_REJECTED'].includes(value)
                                ? 'error'
                                : 'default'
                        }
                    >
                        {label}: {t(`runEvents.state.${value}`, {defaultValue: value})}
                    </Tag>
                ))}
            </Space>
        ) : (
            <Typography.Text type="secondary">{t('runEvents.noState')}</Typography.Text>
        );
    };
    const details = (row: StrategyRunBusinessEventItem) => (
        <Descriptions
            size="small"
            column={{xs: 1, md: 2}}
            items={[
                {key: 'event', label: t('runEvents.id'), children: identity(row.eventId)},
                {key: 'order', label: t('orderId'), children: identity(row.orderId)},
                {key: 'trade', label: t('tradeId'), children: identity(row.tradeId)},
                {key: 'account', label: t('account'), children: identity(row.accountId)},
                {
                    key: 'occurred',
                    label: t('runEvents.occurredAt'),
                    children: formatDateTime(row.occurredAt),
                },
                {
                    key: 'published',
                    label: t('runEvents.publishedAt'),
                    children: formatDateTime(row.publishedAt),
                },
                {
                    key: 'recorded',
                    label: t('runEvents.recordedAt'),
                    children: formatDateTime(row.recordedAt),
                },
                {key: 'source', label: t('runEvents.source'), children: identity(row.source)},
                {key: 'type', label: t('runEvents.typeCode'), children: row.eventType},
                {key: 'topic', label: t('runEvents.topic'), children: row.topic},
                {
                    key: 'association',
                    label: t('runEvents.associationTitle'),
                    children: t(`runEvents.association.${row.association}`, {
                        defaultValue: row.association,
                    }),
                },
                {key: 'trace', label: t('runTraceId'), children: identity(row.traceId)},
            ]}
        />
    );
    const actions = (row: StrategyRunBusinessEventItem) => (
        <Space wrap size={4}>
            <Button type="link" size="small" onClick={onOrders}>
                {t('runEvents.browseOrders')}
            </Button>
            {row.tradeId && (
                <Button type="link" size="small" onClick={onTrades}>
                    {t('runEvents.browseTrades')}
                </Button>
            )}
        </Space>
    );
    const columns: ColumnsType<StrategyRunBusinessEventItem> = [
        {
            title: t('runEvents.occurredAt'),
            dataIndex: 'occurredAt',
            width: 190,
            render: formatDateTime,
        },
        {
            title: t('runEvents.event'),
            key: 'event',
            width: 240,
            render: (_, row) => (
                <Space direction="vertical" size={2}>
                    <Typography.Text>{eventName(row)}</Typography.Text>
                    {meaning(row) && (
                        <Typography.Text type="secondary">{meaning(row)}</Typography.Text>
                    )}
                </Space>
            ),
        },
        {
            title: t('runEvents.historicalResult'),
            key: 'state',
            width: 230,
            render: (_, row) => state(row),
        },
        {title: t('actions'), key: 'actions', width: 160, render: (_, row) => actions(row)},
    ];
    if (query.isError)
        return (
            <Alert
                type="error"
                showIcon
                message={t('runEvents.readFailed')}
                description={
                    (query.error as AppApiError).status === 404
                        ? t('runEvents.notVisible')
                        : formatApiError(query.error as AppApiError)
                }
                action={<Button onClick={() => void query.refetch()}>{t('retry')}</Button>}
            />
        );
    return (
        <Space direction="vertical" size={12} style={{display: 'flex'}}>
            <Typography.Text type="secondary">{t('runEvents.scopeMeaning')}</Typography.Text>
            <Space wrap style={{justifyContent: 'space-between', width: '100%'}}>
                <Typography.Text>
                    {query.data ? t('runRelationTotal', {total: query.data.total}) : '—'}
                </Typography.Text>
                <Typography.Text type="secondary">{t('runEvents.orderMeaning')}</Typography.Text>
            </Space>
            {!screens.md ? (
                <>
                    <List
                        loading={query.isFetching}
                        rowKey="eventId"
                        dataSource={query.data?.items ?? []}
                        locale={{emptyText: t('runEvents.empty')}}
                        renderItem={(row) => (
                            <List.Item data-business-event-id={row.eventId}>
                                <Card size="small" style={{width: '100%'}} title={eventName(row)}>
                                    <Space direction="vertical" size={8} style={{display: 'flex'}}>
                                        <Typography.Text>
                                            {t('runEvents.occurredAt')}:{' '}
                                            {formatDateTime(row.occurredAt)}
                                        </Typography.Text>
                                        {state(row)}
                                        {meaning(row) && (
                                            <Typography.Text type="secondary">
                                                {meaning(row)}
                                            </Typography.Text>
                                        )}
                                        {actions(row)}
                                        <Collapse
                                            size="small"
                                            items={[
                                                {
                                                    key: 'details',
                                                    label: t('runEvents.details'),
                                                    children: details(row),
                                                },
                                            ]}
                                        />
                                    </Space>
                                </Card>
                            </List.Item>
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
                <Table<StrategyRunBusinessEventItem>
                    rowKey="eventId"
                    size="small"
                    columns={columns}
                    dataSource={query.data?.items ?? []}
                    loading={query.isFetching}
                    scroll={{x: 820}}
                    expandable={{expandedRowRender: details, columnTitle: t('runEvents.details')}}
                    pagination={{
                        current: page,
                        pageSize: 10,
                        total: query.data?.total ?? 0,
                        showSizeChanger: false,
                        hideOnSinglePage: true,
                        onChange: setPage,
                    }}
                    locale={{emptyText: t('runEvents.empty')}}
                />
            )}
        </Space>
    );
}

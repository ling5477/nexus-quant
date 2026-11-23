import {
    Alert,
    Button,
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
import {NqAmountText} from '@/components/nq';
import {formatApiError} from '@/api/errors';
import {
    useRunRiskEventsQuery,
    useRunLedgerEntriesQuery,
} from '@/features/runs/hooks/useRunListQuery';
import type {
    StrategyRunRiskEventItem,
    StrategyRunLedgerEntryItem,
} from '@/features/runs/types/runs';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {MobileRecord} from './StrategyRunRelatedTables';

const identity = (value: string | null) =>
    value ? (
        <Typography.Text copyable style={{wordBreak: 'break-all'}}>
            {value}
        </Typography.Text>
    ) : (
        '—'
    );
const amount = (value: string | null) => <NqAmountText exact signed value={value} />;

/** 只认同一成交的四种完整正式键；旧键含额外片段时不能据后缀猜测现金或对手用途。 */
export function runLedgerPostingRole(row: StrategyRunLedgerEntryItem): string {
    if (row.refType !== 'TRADE') return 'UNKNOWN';
    switch (row.idempotencyKey) {
        case `${row.tradeId}:LEDGER:1`:
            return 'CASH_PRINCIPAL';
        case `${row.tradeId}:LEDGER:2`:
            return 'CONTRA_PRINCIPAL';
        case `${row.tradeId}:LEDGER:FEE_1`:
            return 'CASH_FEE';
        case `${row.tradeId}:LEDGER:FEE_2`:
            return 'CONTRA_FEE';
        default:
            return 'UNKNOWN';
    }
}

/** 仅订单级结果，空记录不产生“运行无风险”结论。 */
export function RunRiskEventsTable({runId, active}: {runId: string; active: boolean}) {
    const {t} = useTranslation('pages');
    const screens = Grid.useBreakpoint();
    const [page, setPage] = useState(1);
    const query = useRunRiskEventsQuery(runId, {limit: 10, offset: (page - 1) * 10}, active);
    const decision = (value: string) => (
        <Tag color={value === 'REJECT' ? 'error' : value === 'ALLOW' ? 'success' : 'warning'}>
            {value}
        </Tag>
    );
    const columns: ColumnsType<StrategyRunRiskEventItem> = [
        {title: t('runRiskEventId'), dataIndex: 'riskEventId', width: 180, render: identity},
        {title: t('runRiskDecision'), dataIndex: 'decision', width: 110, render: decision},
        {
            title: t('runRiskReason'),
            dataIndex: 'reason',
            width: 280,
            render: (value: string | null) => (
                <Typography.Text style={{whiteSpace: 'pre-wrap', wordBreak: 'break-word'}}>
                    {value ?? '—'}
                </Typography.Text>
            ),
        },
        {
            title: t('runRiskSeverity'),
            dataIndex: 'severity',
            width: 100,
            render: (value: string | null) => value ?? '—',
        },
        {title: t('orderId'), dataIndex: 'orderId', width: 180, render: identity},
        {title: t('runRiskRule'), dataIndex: 'ruleId', width: 180, render: identity},
        {title: t('createdAt'), dataIndex: 'createdAt', width: 180, render: formatDateTime},
        {title: t('runTraceId'), dataIndex: 'traceId', width: 180, render: identity},
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
            <Typography.Text type="secondary">{t('runRiskScopeMeaning')}</Typography.Text>
            <Typography.Text>
                {query.data ? t('runRelationTotal', {total: query.data.total}) : '—'}
            </Typography.Text>
            {!screens.md ? (
                <>
                    <List
                        rowKey="riskEventId"
                        dataSource={query.data?.items ?? []}
                        loading={query.isFetching}
                        locale={{emptyText: t('runNoOrderRisk')}}
                        renderItem={(row) => (
                            <MobileRecord
                                id={row.riskEventId}
                                detailLabel={t('viewDetails')}
                                fields={[
                                    [t('runRiskDecision'), decision(row.decision)],
                                    [
                                        t('runRiskReason'),
                                        <Typography.Text
                                            style={{
                                                whiteSpace: 'pre-wrap',
                                                wordBreak: 'break-word',
                                            }}
                                        >
                                            {row.reason ?? '—'}
                                        </Typography.Text>,
                                    ],
                                    [t('runRiskSeverity'), row.severity ?? '—'],
                                    [t('createdAt'), formatDateTime(row.createdAt)],
                                ]}
                                details={[
                                    [t('orderId'), identity(row.orderId)],
                                    [t('runRiskRule'), identity(row.ruleId)],
                                    [t('runTraceId'), identity(row.traceId)],
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
                <Table<StrategyRunRiskEventItem>
                    size="small"
                    rowKey="riskEventId"
                    columns={columns}
                    dataSource={query.data?.items ?? []}
                    loading={query.isFetching}
                    scroll={{x: 1390}}
                    pagination={{
                        current: page,
                        pageSize: 10,
                        total: query.data?.total ?? 0,
                        showSizeChanger: false,
                        hideOnSinglePage: true,
                        onChange: setPage,
                    }}
                    locale={{emptyText: t('runNoOrderRisk')}}
                />
            )}
        </Space>
    );
}

/** 展示原币双边分录，不合计现金，也不把原始balanceAfter重命名为当前余额。 */
export function RunLedgerEntriesTable({runId, active}: {runId: string; active: boolean}) {
    const {t} = useTranslation('pages');
    const screens = Grid.useBreakpoint();
    const [page, setPage] = useState(1);
    const query = useRunLedgerEntriesQuery(runId, {limit: 10, offset: (page - 1) * 10}, active);
    const role = (row: StrategyRunLedgerEntryItem) => t(`ledgerRole_${runLedgerPostingRole(row)}`);
    const details = (row: StrategyRunLedgerEntryItem) =>
        [
            [t('orderId'), identity(row.orderId)],
            [t('account'), identity(row.accountId)],
            [t('runLedgerRefType'), row.refType],
            [t('runLedgerRecordedBalance'), amount(row.balanceAfter)],
            [t('runLedgerIdempotency'), identity(row.idempotencyKey)],
            [t('runTraceId'), identity(row.traceId)],
        ] as const;
    const columns: ColumnsType<StrategyRunLedgerEntryItem> = [
        {title: t('runLedgerEntryId'), dataIndex: 'entryId', width: 180, render: identity},
        {title: t('runLedgerRole'), key: 'role', width: 170, render: (_, row) => role(row)},
        {title: t('runLedgerCurrency'), dataIndex: 'currency', width: 100},
        {
            title: t('runLedgerDelta'),
            dataIndex: 'delta',
            width: 250,
            align: 'right',
            render: amount,
        },
        {title: t('runLedgerDirection'), dataIndex: 'direction', width: 110},
        {title: t('tradeId'), dataIndex: 'tradeId', width: 200, render: identity},
        {title: t('runTradeTime'), dataIndex: 'occurredAt', width: 180, render: formatDateTime},
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
            <Typography.Text type="secondary">{t('runLedgerScopeMeaning')}</Typography.Text>
            <Typography.Text>
                {query.data ? t('runRelationTotal', {total: query.data.total}) : '—'}
            </Typography.Text>
            {!screens.md ? (
                <>
                    <List
                        rowKey="entryId"
                        dataSource={query.data?.items ?? []}
                        loading={query.isFetching}
                        locale={{emptyText: t('runNoTradeLedger')}}
                        renderItem={(row) => (
                            <MobileRecord
                                id={row.entryId}
                                detailLabel={t('viewDetails')}
                                fields={[
                                    [t('runLedgerRole'), role(row)],
                                    [t('runLedgerCurrency'), row.currency],
                                    [t('runLedgerDelta'), amount(row.delta)],
                                    [t('runLedgerDirection'), row.direction],
                                    [t('tradeId'), identity(row.tradeId)],
                                    [t('runTradeTime'), formatDateTime(row.occurredAt)],
                                ]}
                                details={details(row).map(([label, value]) => [label, value])}
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
                <Table<StrategyRunLedgerEntryItem>
                    size="small"
                    rowKey="entryId"
                    columns={columns}
                    dataSource={query.data?.items ?? []}
                    loading={query.isFetching}
                    scroll={{x: 1190}}
                    pagination={{
                        current: page,
                        pageSize: 10,
                        total: query.data?.total ?? 0,
                        showSizeChanger: false,
                        hideOnSinglePage: true,
                        onChange: setPage,
                    }}
                    expandable={{
                        expandedRowRender: (row) => (
                            <Descriptions
                                size="small"
                                column={1}
                                items={details(row).map(([label, children]) => ({
                                    key: label,
                                    label,
                                    children,
                                }))}
                            />
                        ),
                    }}
                    locale={{emptyText: t('runNoTradeLedger')}}
                />
            )}
        </Space>
    );
}

import {Alert, Button, Collapse, Empty, Table, Typography} from 'antd';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import {formatDateTimeMilliseconds} from '@/utils/formatters';
import {decimalDisplay} from '@/utils/decimal-display';
import type {AppApiError} from '@/types/api';
import type {BacktestSimTrade} from './backtestsTypes';
import {backtestsApi} from './backtestsApi';
import {feePaymentStatusKey} from './fee-source';

/** 按所选运行只读正式研究成交；展开才加载，owner/run进入缓存身份，旧缺失原币资料保持明确缺失。 */
export function BacktestTradesPanel({runId}: {runId: string}) {
    const {t} = useTranslation('pages');
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [expanded, setExpanded] = useState(false);
    const trades = useQuery({
        queryKey: ['backtest-sim-trades', owner, runId],
        queryFn: ({signal}) => backtestsApi.simTrades(runId, signal),
        enabled: expanded && Boolean(owner),
        retry: false,
    });
    const pnl = useQuery({
        queryKey: ['backtest-discount-pnl', owner, runId],
        queryFn: () => backtestsApi.pnlSnapshots(runId),
        enabled: expanded && Boolean(owner),
        retry: false,
    });
    const discountPnl = (pnl.data ?? []).filter((row) => row.discountAssetCurrency != null);
    const columns = [
        {
            title: t('feeModel.tradeTime'),
            dataIndex: 'tradedAt',
            key: 'time',
            width: 210,
            render: (value: string) => formatDateTimeMilliseconds(value),
        },
        {
            title: t('pages:researchEventOrder.executionSequence'),
            dataIndex: 'executionSequence',
            key: 'executionSequence',
            width: 110,
            // 只显示持久化原顺序；时间相同时不能由 UI 重新按方向或随机身份排序。
            render: (value: string | null | undefined) => value ?? '—',
        },
        {
            title: t('feeModel.tradeSide'),
            dataIndex: 'side',
            key: 'side',
            width: 85,
            render: (value: string) =>
                value === 'BUY' ? t('feeModel.buy') : value === 'SELL' ? t('feeModel.sell') : value,
        },
        {
            title: t('feeModel.grossQuantity'),
            dataIndex: 'quantity',
            key: 'quantity',
            align: 'right' as const,
            render: (value: string) => decimalDisplay(value, 8),
        },
        {
            title: t('feeModel.fillPrice'),
            dataIndex: 'tradePrice',
            key: 'price',
            align: 'right' as const,
            render: (value: string) => decimalDisplay(value, 8),
        },
        {
            title: t('feeModel.nativeFee'),
            key: 'native',
            align: 'right' as const,
            render: (_: unknown, row: BacktestSimTrade) =>
                row.feeNativeAmount == null ? (
                    <Typography.Text type="secondary">
                        {t('feeModel.nativeNotProvided')}
                    </Typography.Text>
                ) : (
                    decimalDisplay(row.feeNativeAmount, 8, 0, true) + ' ' + row.feeCurrency
                ),
        },
        {
            title: t('feeModel.quoteFeeValue'),
            key: 'quote',
            align: 'right' as const,
            render: (_: unknown, row: BacktestSimTrade) =>
                decimalDisplay(row.feeAmount, 8, 0, true) + ' ' + row.symbol.split('-')[1],
        },
        {
            title: t('discountFee.paymentResult'),
            dataIndex: 'feePaymentStatus',
            key: 'payment',
            width: 160,
            render: (value: unknown) =>
                feePaymentStatusKey(value) ? t(feePaymentStatusKey(value)!) : String(value ?? '—'),
        },
    ];
    return (
        <Collapse
            size="small"
            onChange={(keys) => setExpanded(keys.length > 0)}
            items={[
                {
                    key: 'trades',
                    label: t('feeModel.tradeDetails'),
                    children: (
                        <>
                            <Typography.Paragraph type="secondary">
                                {t('feeModel.quoteFeeMeaning')}
                            </Typography.Paragraph>
                            {trades.isError ? (
                                <Alert
                                    type="error"
                                    message={formatApiError(trades.error as AppApiError)}
                                    action={
                                        <Button onClick={() => void trades.refetch()}>
                                            {t('retry')}
                                        </Button>
                                    }
                                />
                            ) : (
                                <Table<BacktestSimTrade>
                                    size="small"
                                    rowKey="simTradeId"
                                    columns={columns}
                                    dataSource={trades.data ?? []}
                                    loading={trades.isFetching}
                                    expandable={{
                                        rowExpandable: (row) => Boolean(row.feePriceSourceSha256),
                                        expandedRowRender: (row) => (
                                            <Collapse
                                                size="small"
                                                items={[
                                                    {
                                                        key: 'price',
                                                        label: t('discountFee.conversionDetails'),
                                                        children: (
                                                            <>
                                                                <Typography.Paragraph>
                                                                    {t(
                                                                        'discountFee.conversionPrice',
                                                                    )}
                                                                    :{' '}
                                                                    {decimalDisplay(
                                                                        row.feeConversionPrice,
                                                                        8,
                                                                        0,
                                                                        true,
                                                                    )}{' '}
                                                                    USDT
                                                                </Typography.Paragraph>
                                                                <Typography.Paragraph>
                                                                    {t('discountFee.observedAt')}:{' '}
                                                                    {formatDateTimeMilliseconds(
                                                                        row.feePriceObservedAt,
                                                                    )}{' '}
                                                                    · {t('discountFee.visibleAt')}:{' '}
                                                                    {formatDateTimeMilliseconds(
                                                                        row.feePriceVisibleAt,
                                                                    )}
                                                                </Typography.Paragraph>
                                                                <Typography.Paragraph
                                                                    copyable
                                                                    style={{
                                                                        overflowWrap: 'anywhere',
                                                                    }}
                                                                >
                                                                    {row.feePriceSourceSha256}
                                                                </Typography.Paragraph>
                                                                <Typography.Paragraph
                                                                    copyable
                                                                    style={{
                                                                        overflowWrap: 'anywhere',
                                                                    }}
                                                                >
                                                                    {row.feePriceSourceJson}
                                                                </Typography.Paragraph>
                                                            </>
                                                        ),
                                                    },
                                                ]}
                                            />
                                        ),
                                    }}
                                    pagination={{pageSize: 10, showSizeChanger: false}}
                                    scroll={{x: 1100}}
                                    locale={{
                                        emptyText: <Empty description={t('feeModel.noTrades')} />,
                                    }}
                                />
                            )}
                            {pnl.isError && (
                                <Alert
                                    type="error"
                                    message={formatApiError(pnl.error as AppApiError)}
                                    action={
                                        <Button onClick={() => void pnl.refetch()}>
                                            {t('retry')}
                                        </Button>
                                    }
                                />
                            )}
                            {discountPnl.length > 0 && (
                                <>
                                    <Typography.Paragraph strong style={{marginTop: 12}}>
                                        {t('discountFee.balanceHistory')}
                                    </Typography.Paragraph>
                                    <Table
                                        size="small"
                                        rowKey="simPnlSnapshotId"
                                        dataSource={discountPnl}
                                        pagination={{pageSize: 10, showSizeChanger: false}}
                                        scroll={{x: 850}}
                                        columns={[
                                            {
                                                title: t('feeModel.tradeTime'),
                                                dataIndex: 'snapshotTime',
                                                render: (value: string) =>
                                                    formatDateTimeMilliseconds(value),
                                            },
                                            {
                                                title: t(
                                                    'pages:researchEventOrder.valuationSequence',
                                                ),
                                                dataIndex: 'valuationSequence',
                                                render: (value: string | null | undefined) =>
                                                    value ?? '—',
                                            },
                                            {
                                                title: t('discountFee.balance'),
                                                render: (
                                                    _: unknown,
                                                    row: (typeof discountPnl)[number],
                                                ) =>
                                                    decimalDisplay(
                                                        row.discountAssetBalance,
                                                        8,
                                                        0,
                                                        true,
                                                    ) +
                                                    ' ' +
                                                    row.discountAssetCurrency,
                                            },
                                            {
                                                title: t('discountFee.quoteValue'),
                                                dataIndex: 'discountAssetQuoteValue',
                                                render: (value: string | null) =>
                                                    decimalDisplay(value, 8, 0, true) + ' USDT',
                                            },
                                            {
                                                title: t('discountFee.conversionPrice'),
                                                dataIndex: 'discountConversionPrice',
                                                render: (value: string | null) =>
                                                    decimalDisplay(value, 8, 0, true) + ' USDT',
                                            },
                                            {
                                                title: t('discountFee.visibleAt'),
                                                dataIndex: 'discountPriceVisibleAt',
                                                render: (value: string | null) =>
                                                    formatDateTimeMilliseconds(value),
                                            },
                                        ]}
                                    />
                                </>
                            )}
                        </>
                    ),
                },
            ]}
        />
    );
}

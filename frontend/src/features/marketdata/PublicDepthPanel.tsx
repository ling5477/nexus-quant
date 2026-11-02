import {
    Alert,
    Button,
    Card,
    Descriptions,
    Empty,
    Select,
    Space,
    Spin,
    Table,
    Tabs,
    Tag,
    Typography,
} from 'antd';
import {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useQuery} from '@tanstack/react-query';
import {useSearchParams} from 'react-router-dom';
import {marketdataQueryKeys} from '@/api/query-keys';
import {formatApiError} from '@/api/errors';
import {NqAmountText} from '@/components/nq/NqNumericText';
import {useEChart} from '@/nq-design-system/charts/useEChart';
import {echarts} from '@/nq-design-system/charts/echarts-core';
import {buildNqLineChartBaseOption} from '@/nq-design-system/theme/nqChartTheme';
import {nqColor} from '@/theme/tokens';
import {formatDateTime} from '@/utils/formatters';
import {formatNqNumber} from '@/nq-design-system/format/nqFormat';
import type {InstrumentCatalogItem} from '@/features/instruments/types/instruments';
import {isActiveSpot} from '@/features/preferences/components/WatchlistEditor';
import type {AppApiError} from '@/types/api';
import {publicDepthApi, type PublicDepthSnapshot, type DepthLevel} from './public-depth';

const supported = new Set(['BTC-USDT', 'ETH-USDT', 'SOL-USDT']);

/** 当前公开现货快照独立于历史K线区间；断连只保留带明确过期标记的真实旧快照。 */
export function PublicDepthPanel({
    instruments,
    initialSymbol,
    catalogError,
    active,
}: {
    instruments: InstrumentCatalogItem[];
    initialSymbol?: string;
    catalogError?: AppApiError | null;
    active: boolean;
}) {
    const {t} = useTranslation('pages');
    const [params, setParams] = useSearchParams();
    const symbol = params.get('depthSymbol') ?? initialSymbol ?? null;
    const valid = Boolean(symbol && supported.has(symbol));
    const options = instruments
        .filter(
            (item) =>
                item.exchangeCode === 'OKX' &&
                isActiveSpot(item) &&
                supported.has(item.internalSymbol),
        )
        .map((item) => ({
            value: item.internalSymbol,
            label: `${item.internalSymbol} · OKX · SPOT`,
        }));
    const query = useQuery({
        queryKey: marketdataQueryKeys.depth(symbol ?? ''),
        queryFn: ({signal}) => publicDepthApi.read(symbol!, signal),
        enabled: valid && active,
        retry: false,
        refetchInterval: active ? 10_000 : false,
        refetchIntervalInBackground: false,
    });
    const snapshot = query.data?.symbol === symbol ? query.data : null;
    const usable = Boolean(snapshot && snapshot.bids.length && snapshot.asks.length);
    const [now, setNow] = useState(Date.now);
    useEffect(() => {
        if (!active || !snapshot?.receivedAt) return;
        // 新请求尚未返回或页面重新可见时，旧快照仍会按实际接收时间过期。
        const timer = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(timer);
    }, [snapshot?.receivedAt, active]);
    const stale =
        query.isError ||
        snapshot?.status === 'STALE' ||
        Boolean(
            snapshot?.receivedAt &&
                Math.max(now, Date.now()) - Date.parse(snapshot.receivedAt) > 15_000,
        );
    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:depth.title')}
            extra={
                <Button
                    disabled={!valid}
                    loading={query.isFetching}
                    onClick={() => void query.refetch()}
                >
                    {t('pages:depth.refresh')}
                </Button>
            }
        >
            <Space direction="vertical" size={12} style={{width: '100%', minWidth: 0}}>
                <Select
                    aria-label={t('pages:depth.symbol')}
                    style={{width: '100%', maxWidth: 420}}
                    value={symbol ?? undefined}
                    options={options}
                    placeholder={t('pages:depth.chooseSymbol')}
                    onChange={(value) => {
                        const next = new URLSearchParams(params);
                        next.set('depthSymbol', value);
                        setParams(next);
                    }}
                />
                {catalogError && <Alert type="error" message={formatApiError(catalogError)} />}
                <Typography.Text type="secondary">{t('pages:depth.scope')}</Typography.Text>
                {symbol && !valid && (
                    <Alert type="warning" message={t('pages:depth.unsupported')} />
                )}
                {query.isError && (
                    <Alert
                        type={usable ? 'warning' : 'error'}
                        showIcon
                        message={formatApiError(query.error as AppApiError)}
                        description={usable ? t('pages:depth.lastKnown') : undefined}
                    />
                )}
                {!symbol && <Empty description={t('pages:depth.chooseSymbol')} />}
                {valid && query.isPending && (
                    <Spin tip={t('pages:loading')}>
                        <div style={{minHeight: 100}} />
                    </Spin>
                )}
                {snapshot && (
                    <>
                        <Descriptions size="small" column={{xs: 1, md: 3}}>
                            <Descriptions.Item label={t('pages:depth.status')}>
                                <Tag
                                    color={
                                        stale
                                            ? 'orange'
                                            : snapshot.status === 'AVAILABLE'
                                              ? 'blue'
                                              : 'default'
                                    }
                                >
                                    {t(`pages:depth.status_${stale ? 'STALE' : snapshot.status}`)}
                                </Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:depth.sourceTime')}>
                                {formatDateTime(snapshot.sourceTime)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:depth.receivedAt')}>
                                {formatDateTime(snapshot.receivedAt)}
                            </Descriptions.Item>
                        </Descriptions>
                        {snapshot.errorCode && !query.isError && (
                            <Alert type="warning" showIcon message={t('pages:depth.lastKnown')} />
                        )}
                        {usable ? (
                            <>
                                <Space aria-label={t('pages:depth.chartLabel')}>
                                    <Tag color={nqColor.up}>{t('pages:depth.bids')}</Tag>
                                    <Tag color={nqColor.down}>{t('pages:depth.asks')}</Tag>
                                </Space>
                                <DepthChart snapshot={snapshot} />
                                <Typography.Text type="secondary">
                                    {t('pages:depth.chartMeaning', {
                                        base: symbol?.split('-')[0],
                                        quote: symbol?.split('-')[1],
                                    })}
                                </Typography.Text>
                                <Tabs
                                    items={(['bids', 'asks'] as const).map((side) => ({
                                        key: side,
                                        label: t(`pages:depth.${side}`),
                                        children: (
                                            <DepthTable data={snapshot[side]} symbol={symbol!} />
                                        ),
                                    }))}
                                />
                            </>
                        ) : (
                            <Empty description={t(`pages:depth.status_${snapshot.status}`)} />
                        )}
                        <Typography.Text type="secondary">
                            {snapshot.source} ·{' '}
                            {t('pages:depth.cadence', {levels: snapshot.maxLevels})}
                        </Typography.Text>
                    </>
                )}
            </Space>
        </Card>
    );
}

function DepthChart({snapshot}: {snapshot: PublicDepthSnapshot}) {
    const {t, i18n} = useTranslation('pages');
    const option = useMemo(() => {
        const base = buildNqLineChartBaseOption();
        const seriesRows = [[...snapshot.bids].reverse(), snapshot.asks];
        const escape = (text: string) =>
            text.replace(
                /[&<>"']/g,
                (char) =>
                    ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[char]!,
            );
        return {
            ...base,
            grid: {...base.grid, top: 30, bottom: 42},
            xAxis: {
                ...base.xAxis,
                type: 'value',
                name: snapshot.symbol.split('-')[1],
                nameLocation: 'middle',
                nameGap: 26,
                scale: true,
                splitNumber: 3,
                axisLabel: {...base.xAxis.axisLabel, hideOverlap: true},
            },
            yAxis: {...base.yAxis, name: snapshot.symbol.split('-')[0], min: 0},
            tooltip: {
                ...base.tooltip,
                trigger: 'item',
                backgroundColor: 'rgba(5,17,29,.72)',
                borderWidth: 0,
                confine: true,
                hideDelay: 0,
                transitionDuration: 0,
                formatter: (point: {seriesIndex: number; dataIndex: number}) => {
                    const row = seriesRows[point.seriesIndex]?.[point.dataIndex];
                    return row
                        ? `${escape(t(point.seriesIndex === 0 ? 'pages:depth.bids' : 'pages:depth.asks'))}<br/>${escape(formatNqNumber(row.price, {exact: true}))} · ${escape(formatNqNumber(row.cumulativeQuantity, {exact: true}))}`
                        : '';
                },
            },
            series: seriesRows.map((rows, index) => ({
                name: t(index === 0 ? 'pages:depth.bids' : 'pages:depth.asks'),
                type: 'line',
                step: index === 0 ? 'start' : 'end',
                smooth: false,
                showSymbol: true,
                symbolSize: 4,
                data: rows.map((row) => [Number(row.price), Number(row.cumulativeQuantity)]),
                lineStyle: {width: 1.5, color: index === 0 ? nqColor.up : nqColor.down},
                itemStyle: {color: index === 0 ? nqColor.up : nqColor.down},
                areaStyle: {opacity: 0.08, color: index === 0 ? nqColor.up : nqColor.down},
            })),
        };
    }, [snapshot, t, i18n.resolvedLanguage]);
    const ref = useEChart(option);
    return (
        <div
            role="img"
            aria-label={t('pages:depth.chartLabel')}
            ref={ref}
            className="nq-chart"
            style={{height: 280}}
            onMouseLeave={() => {
                if (ref.current)
                    echarts.getInstanceByDom(ref.current)?.dispatchAction({type: 'hideTip'});
            }}
        />
    );
}

function DepthTable({data, symbol}: {data: DepthLevel[]; symbol: string}) {
    const {t} = useTranslation('pages');
    return (
        <Table
            size="small"
            rowKey="price"
            dataSource={data}
            pagination={false}
            scroll={{x: 560}}
            columns={[
                {
                    title: t('pages:depth.price', {currency: symbol.split('-')[1]}),
                    dataIndex: 'price',
                    align: 'right',
                    render: (value: string) => <NqAmountText exact value={value} />,
                },
                {
                    title: t('pages:depth.quantity', {currency: symbol.split('-')[0]}),
                    dataIndex: 'quantity',
                    align: 'right',
                    render: (value: string) => <NqAmountText exact value={value} />,
                },
                {
                    title: t('pages:depth.cumulative', {currency: symbol.split('-')[0]}),
                    dataIndex: 'cumulativeQuantity',
                    align: 'right',
                    render: (value: string) => <NqAmountText exact value={value} />,
                },
                {title: t('pages:depth.orderCount'), dataIndex: 'orderCount', align: 'right'},
            ]}
        />
    );
}

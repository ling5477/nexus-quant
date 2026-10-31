import {useMemo, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Button, Card, Select, Space, Tag, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import dayjs from 'dayjs';
import {marketdataApi} from '@/api/marketdata';
import {instrumentQueryKeys} from '@/api/query-keys';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {isActiveSpot} from '@/features/preferences/components/WatchlistEditor';
import {NqKlineChart, NqVolumeChart, type NqKlineBar} from '@/nq-design-system/charts';
import {NqErrorState} from '@/components/nq';
import type {AppApiError} from '@/types/api';
import {decimalDisplay} from '@/utils/decimal-display';
import {formatDateTime} from '@/utils/formatters';

const intervals = ['15m', '1h', '4h', '1d'] as const;
const durations = {'15m': 900, '1h': 3600, '4h': 14400, '1d': 86400};

/** 总览只读取正式目录与本地行情；不发起采集，不以顶部账户或示例交易对补造市场身份。 */
export function DashboardMarketPanel() {
    const {t} = useTranslation('product');
    const {query: preferences} = useUserPreferences();
    const catalog = useQuery({queryKey: instrumentQueryKeys.list(), queryFn: () => instrumentsApi.list()});
    const [selected, setSelected] = useState<string | undefined>();
    const [interval, setInterval] = useState<(typeof intervals)[number]>('1h');
    const [anchor, setAnchor] = useState(() => dayjs().startOf('hour'));
    const [visibleRange, setVisibleRange] = useState<{from: number; to: number} | null>(null);
    const instruments = (catalog.data ?? []).filter(isActiveSpot);
    const defaultMarket = preferences.data?.defaultMarket;
    const key = selected ?? (defaultMarket ? defaultMarket.exchangeCode + ':' + defaultMarket.symbol : undefined);
    const market = instruments.find((item) => item.exchangeCode + ':' + item.internalSymbol === key);
    const request = useMemo(() => market ? {
        exchangeCode: market.exchangeCode, marketType: 'SPOT', symbol: market.internalSymbol,
        interval, startTime: anchor.subtract(durations[interval] * 200, 'second').toISOString(),
        endTime: anchor.toISOString(), page: 0, size: 200,
    } : null, [market?.exchangeCode, market?.internalSymbol, interval, anchor]);
    const query = useQuery({
        queryKey: ['dashboard-market', request],
        queryFn: () => marketdataApi.listBars(request!),
        enabled: Boolean(request), retry: false,
    });
    const bars = useMemo<NqKlineBar[]>(() => (query.data ?? []).map((bar) => ({
        time: bar.openTime, open: Number(bar.openPrice), high: Number(bar.highPrice),
        low: Number(bar.lowPrice), close: Number(bar.closePrice), volume: Number(bar.volume),
        closeTime: bar.closeTime, availableAt: bar.availableAt, qualityStatus: bar.qualityStatus,
        exact: {open: String(bar.openPrice), high: String(bar.highPrice), low: String(bar.lowPrice), close: String(bar.closePrice), volume: String(bar.volume)},
    })), [query.data]);
    const latest = query.data?.at(-1);
    const detailLink = request ? '/marketdata?' + new URLSearchParams({
        ...request, page: String(request.page), size: String(request.size), section: 'market',
    }) : '/marketdata';
    const syncRange = (next: {from: number; to: number} | null) => setVisibleRange((current) =>
        current?.from === next?.from && current?.to === next?.to ? current : next);
    return (
        <Card className="page-section nq-dashboard__market" title={t('marketTitle')}
            extra={<Link to={detailLink}>{t('openMarket')} →</Link>}>
            <div className="overview-market-toolbar">
                <Select aria-label={t('chooseMarket')} placeholder={t('chooseMarket')} value={market ? key : undefined}
                    showSearch optionFilterProp="label" loading={catalog.isPending} onChange={(value) => {setSelected(value); setVisibleRange(null);}}
                    options={instruments.map((item) => ({value: item.exchangeCode + ':' + item.internalSymbol, label: item.internalSymbol + ' · ' + item.exchangeCode}))} />
                <Select aria-label={t('interval')} value={interval} options={intervals.map((value) => ({value, label: value}))}
                    onChange={(value) => {setInterval(value); setVisibleRange(null);}} />
                <Button disabled={!market} loading={Boolean(market) && query.isFetching}
                    onClick={() => {setAnchor(dayjs().startOf('hour')); void query.refetch();}}>{t('read')}</Button>
            </div>
            <div className="overview-price-line">
                <Typography.Text className="overview-price nq-num">{latest ? decimalDisplay(String(latest.closePrice), 8) : '—'}</Typography.Text>
                <Tag>{market?.quoteAsset ?? '—'}</Tag>
                <Space wrap size={12}>
                    {(['open', 'high', 'low', 'close'] as const).map((name) => <span key={name}>{t(name)} <b className="nq-num">{latest ? String(latest[`${name}Price`]) : '—'}</b></span>)}
                </Space>
            </div>
            {(catalog.error || preferences.error) && <NqErrorState title={t('marketTitle')} error={(catalog.error ?? preferences.error) as AppApiError} />}
            <NqKlineChart bars={bars} height={330} loading={Boolean(request) && query.isPending}
                error={query.error} title={market?.internalSymbol ?? t('marketTitle')}
                intervalSeconds={durations[interval]} movingAverageWindow={20}
                visibleRange={visibleRange} onVisibleRangeChange={syncRange}
                emptyText={instruments.length === 0 && catalog.isSuccess ? t('marketUnavailable') : t('noMarket')}
                sourceLabel={t('marketSource')} />
            <NqVolumeChart bars={bars} height={132} loading={Boolean(request) && query.isPending}
                error={query.error} title={t('volume') + ' · ' + (market?.baseAsset ?? '—')}
                visibleRange={visibleRange} onVisibleRangeChange={syncRange} sourceLabel={t('marketSource')}
                emptyText={t('noMarket')} />
            <Typography.Text type="secondary" className="overview-market-source">
                {t('marketBoundary')}{latest ? ' · ' + formatDateTime(latest.closeTime) : ''}
            </Typography.Text>
        </Card>
    );
}

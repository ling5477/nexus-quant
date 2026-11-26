import {useMemo, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Button, Card, Select, Space, Typography} from 'antd';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {instrumentQueryKeys} from '@/api/query-keys';
import {instrumentsApi} from '@/features/instruments/api/instruments';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {isActiveSpot} from '@/features/preferences/components/WatchlistEditor';
import {NqKlineChart, NqVolumeChart} from '@/nq-design-system/charts';
import {NqErrorState} from '@/components/nq';
import type {AppApiError} from '@/types/api';
import {usePublicMarketSnapshot} from '@/features/marketdata/hooks/usePublicMarketSnapshot';
import {PublicMarketSnapshotStatus} from '@/features/marketdata/PublicMarketSnapshotStatus';
import {supportsPublicSnapshot} from '@/features/marketdata/public-market-snapshot';

const intervals = ['15m', '1h', '4h', '1d'] as const;
const durations = {'15m': 900, '1h': 3600, '4h': 14400, '1d': 86400};

/** 总览只使用正式目录、历史底图与只读展示快照。 */
export function DashboardMarketPanel() {
    const {t} = useTranslation('product');
    const {query: preferences} = useUserPreferences();
    const catalog = useQuery({queryKey: instrumentQueryKeys.list(), queryFn: () => instrumentsApi.list()});
    const [selected, setSelected] = useState<string | undefined>();
    const [interval, setInterval] = useState<(typeof intervals)[number]>('1h');
    const [visibleRange, setVisibleRange] = useState<{from: number; to: number} | null>(null);
    const instruments = (catalog.data ?? []).filter(isActiveSpot);
    const defaultMarket = preferences.data?.defaultMarket;
    const key = selected ?? (defaultMarket ? defaultMarket.exchangeCode + ':' + defaultMarket.symbol : undefined);
    const market = instruments.find((item) => item.exchangeCode + ':' + item.internalSymbol === key);
    const selection = useMemo(() => market ? {
        exchangeCode: market.exchangeCode, marketType: 'SPOT', symbol: market.internalSymbol, interval,
    } : null, [market, interval]);
    const snapshot = usePublicMarketSnapshot(selection, true);
    const bars = snapshot.chart.bars;
    const latest = bars.at(-1);
    const loading = !bars.length && (snapshot.history.isLoading || snapshot.snapshot.isLoading);
    const chartError = !bars.length ? snapshot.history.error : null;
    const sourceLabel = t(snapshot.supported ? 'snapshotMarketSource' : 'marketSource');
    const detailLink = selection ? '/marketdata?' + new URLSearchParams({
        ...selection, section: 'market', ...(supportsPublicSnapshot(selection) ? {mode: 'latest'} : {}),
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
                <Button disabled={!market || !snapshot.ready} loading={snapshot.history.isFetching || snapshot.snapshot.isFetching}
                    onClick={snapshot.refresh}>{t('read')}</Button>
            </div>
            <div className="overview-price-line">
                <PublicMarketSnapshotStatus market={snapshot} quoteAsset={market?.quoteAsset} priceClassName="overview-price nq-num" />
                <Space wrap size={12}>
                    {(['open', 'high', 'low', 'close'] as const).map((name) => <span key={name}>{t(name)} <b className="nq-num">{latest?.exact?.[name] ?? '—'}</b></span>)}
                </Space>
            </div>
            {(catalog.error || preferences.error) && <NqErrorState title={t('marketTitle')} error={(catalog.error ?? preferences.error) as AppApiError} />}
            <NqKlineChart bars={bars} height={330} loading={loading}
                error={chartError} title={market?.internalSymbol ?? t('marketTitle')}
                intervalSeconds={durations[interval]} movingAverageWindow={20}
                visibleRange={visibleRange} onVisibleRangeChange={syncRange}
                emptyText={instruments.length === 0 && catalog.isSuccess ? t('marketUnavailable') : t('noMarket')}
                sourceLabel={sourceLabel} />
            <NqVolumeChart bars={bars} height={132} loading={loading}
                error={chartError} title={t('volume') + ' · ' + (market?.baseAsset ?? '—')}
                visibleRange={visibleRange} onVisibleRangeChange={syncRange} sourceLabel={sourceLabel}
                emptyText={t('noMarket')} />
            <Typography.Text type="secondary" className="overview-market-source">
                {t('marketBoundary')}
            </Typography.Text>
        </Card>
    );
}

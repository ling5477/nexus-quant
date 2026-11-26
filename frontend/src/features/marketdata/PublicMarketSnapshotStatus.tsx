import {Space, Tag, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import type {AppApiError} from '@/types/api';
import {formatNqNumber} from '@/nq-design-system/format/nqFormat';
import {formatDateTime} from '@/utils/formatters';
import type {usePublicMarketSnapshot} from './hooks/usePublicMarketSnapshot';
import {InvalidPublicMarketSnapshot} from './public-market-snapshot';

interface Props {
    market: ReturnType<typeof usePublicMarketSnapshot>;
    quoteAsset?: string;
    priceClassName?: string;
}

export function PublicMarketSnapshotStatus({market, quoteAsset, priceClassName = 'nq-num'}: Props) {
    const {t} = useTranslation();
    if (!market.active) return null;
    const {snapshot, history, view, chart} = market;
    const ticker = view?.ticker;
    const candleObservedAt = view?.bars.at(-1)?.observedAt;
    const tail = chart.bars.at(-1);
    const error = snapshot.error;
    const httpStatus = (error as AppApiError | null)?.status;
    const errorKey = error instanceof InvalidPublicMarketSnapshot ? 'INVALID_RESPONSE'
        : httpStatus === 400 ? 'unsupported'
        : httpStatus === 403 ? 'DENIED'
        : httpStatus === 429 ? 'RATE_LIMITED'
        : error ? 'SOURCE_UNAVAILABLE' : null;
    const tickerState = !market.supported ? 'unsupported' : !market.online ? 'offline'
        : errorKey ?? (view?.latest.tickerStatus === 'AVAILABLE'
            ? market.staleTicker ? 'stale' : 'fresh'
            : view?.latest.tickerStatus ?? 'waiting');
    const candleState = !market.supported ? null : !market.online ? 'offline'
        : errorKey ?? (view?.latest.barsStatus === 'AVAILABLE'
            ? market.staleCandles ? 'stale' : 'fresh'
            : view?.latest.barsStatus ?? 'waiting');

    return (
        <Space wrap size={[10, 4]} role="status" aria-label={t('markets.snapshot.title')} data-testid="public-market-snapshot">
            <Typography.Text type="secondary">{t('markets.snapshot.lastPrice')}</Typography.Text>
            <Typography.Text strong className={priceClassName} data-testid="public-market-last-price">
                {ticker ? formatNqNumber(ticker.lastPrice, {exact: true}) : '—'}
            </Typography.Text>
            {quoteAsset && <Tag>{quoteAsset}</Tag>}
            <Tag color={tickerState === 'fresh' ? 'success' : tickerState === 'waiting' ? 'default' : 'warning'}>
                {t(`markets.snapshot.${tickerState}`)}
            </Tag>
            {ticker?.lastPriceObservedAt && (
                <Typography.Text type="secondary" title={ticker.lastPriceSource}>
                    {t('markets.snapshot.tradeTime', {time: formatDateTime(ticker.lastPriceObservedAt)})}
                    {' · '}{t('markets.snapshot.receivedAt', {time: formatDateTime(ticker.lastPriceReceivedAt)})}
                </Typography.Text>
            )}
            {tail?.status && (
                <Tag color={tail.status === 'FORMING' ? 'processing' : 'default'} title={tail.source ?? undefined}>
                    {tail.status === 'FORMING' ? t('markets.forming') : t('markets.snapshot.closed')}
                </Tag>
            )}
            {candleState && (
                <Typography.Text type="secondary" title={view?.candles?.source}>
                    {t('markets.snapshot.candles', {status: t(`markets.snapshot.${candleState}`)})}
                    {candleObservedAt && ' · ' + t('markets.snapshot.receivedAt', {
                        time: formatDateTime(candleObservedAt),
                    })}
                </Typography.Text>
            )}
            {snapshot.isFetching && <Tag>{t('markets.snapshot.refreshing')}</Tag>}
            {history.isError && <Tag color="warning">{t('markets.snapshot.historyError')}</Tag>}
            {chart.unconfirmedHistoryCount > 0 && (
                <Tag color="warning">{t('markets.snapshot.unconfirmedHistory', {count: chart.unconfirmedHistoryCount})}</Tag>
            )}
            {chart.conflictCount > 0 && (
                <Tag color="warning">{t('markets.snapshot.conflict', {count: chart.conflictCount})}</Tag>
            )}
        </Space>
    );
}

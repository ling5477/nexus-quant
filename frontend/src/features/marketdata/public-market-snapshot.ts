import {formatNqNumber} from '@/nq-design-system/format/nqFormat';
import type {NqKlineBar} from '@/nq-design-system/charts';
import type {
    MarketdataBar, MarketdataBarsQuery, PublicMarketBar, PublicMarketSelection, PublicMarketSnapshot,
} from '@/types/marketdata';
import {intervalSeconds} from '@/utils/time-range';

export const MARKET_POLL_MS = 30_000;
export const MARKET_FRESH_MS = 90_000;
const intervals = new Set(['1m', '5m', '15m', '1h', '4h', '1d']);
const values = ['openPrice', 'highPrice', 'lowPrice', 'closePrice', 'volume'] as const;
const sourceStatuses = new Set([
    'AVAILABLE', 'DISABLED', 'DENIED', 'RATE_LIMITED', 'TIMEOUT', 'SOURCE_UNAVAILABLE',
    'INVALID_RESPONSE', 'INCOMPLETE_RESPONSE', 'BUSY',
]);
const freshnessStates = new Set(['FRESH', 'STALE', 'PARTIAL', 'UNAVAILABLE']);

export class InvalidPublicMarketSnapshot extends Error {}

export function supportsPublicSnapshot(selection: PublicMarketSelection): boolean {
    return selection.exchangeCode === 'OKX' && selection.marketType === 'SPOT'
        && /^[A-Z0-9]{1,24}-[A-Z0-9]{1,24}$/.test(selection.symbol)
        && intervals.has(selection.interval);
}

function sameMarket(a: PublicMarketSelection, b: PublicMarketSelection): boolean {
    return a.exchangeCode === b.exchangeCode && a.marketType === b.marketType
        && a.symbol === b.symbol && a.interval === b.interval;
}

const validTime = (value: unknown): value is string =>
    typeof value === 'string' && Number.isFinite(Date.parse(value));
const validDecimal = (value: unknown): value is string =>
    typeof value === 'string' && value.length <= 64 && /^\d+(?:\.\d+)?$/.test(value);

export function validateSnapshot(value: PublicMarketSnapshot, selection: PublicMarketSelection) {
    if (!value || !sameMarket(value, selection)
        || value.source !== 'OKX_PUBLIC_CANDLES' || value.lastPriceSource !== 'OKX_PUBLIC_TICKER'
        || !sourceStatuses.has(value.tickerStatus) || !sourceStatuses.has(value.barsStatus)
        || ![value.freshness, value.tickerFreshness, value.barsFreshness].every((part) => freshnessStates.has(part))
        || !Array.isArray(value.bars) || value.bars.length > 100
        || (value.observedAt !== null && !validTime(value.observedAt))
        || (value.retryAt !== null && !validTime(value.retryAt))) throw new InvalidPublicMarketSnapshot();

    const tickerValid = value.tickerStatus === 'AVAILABLE'
        ? validDecimal(value.lastPrice) && /[1-9]/.test(value.lastPrice)
            && validTime(value.lastPriceObservedAt) && validTime(value.lastPriceReceivedAt)
            && ['FRESH', 'STALE'].includes(value.tickerFreshness)
        : value.lastPrice === null && value.lastPriceObservedAt === null && value.lastPriceReceivedAt === null
            && value.tickerFreshness === 'UNAVAILABLE';
    const candlesValid = value.barsStatus === 'AVAILABLE'
        ? validTime(value.barsObservedAt) && value.bars.length > 0 && ['FRESH', 'STALE'].includes(value.barsFreshness)
        : value.barsObservedAt === null && !value.bars.length && value.barsFreshness === 'UNAVAILABLE';
    const duration = (intervalSeconds(selection.interval) ?? 0) * 1000;
    if (!tickerValid || !candlesValid || ((value.lastPrice !== null || value.bars.length > 0) && !value.observedAt)
        || !value.bars.every((bar) => bar != null && validTime(bar.openTime) && validTime(bar.closeTime)
            && Date.parse(bar.closeTime) + 1 - Date.parse(bar.openTime) === duration
            && (bar.status === 'FORMING' || bar.status === 'CLOSED')
            && values.every((field) => validDecimal(bar[field])))) throw new InvalidPublicMarketSnapshot();
    return value;
}

export interface MarketSnapshotView {
    latest: PublicMarketSnapshot;
    ticker: PublicMarketSnapshot | undefined;
    candles: PublicMarketSnapshot | undefined;
    bars: PublicMarketBar[];
}

/** 仅在同一选择的展示缓存内保留已确认状态，失败保留最后一次成功事实。 */
export function retainSnapshot(latest: PublicMarketSnapshot, previous?: MarketSnapshotView): MarketSnapshotView {
    const before = previous && sameMarket(latest, previous.latest) ? previous : undefined;
    const ticker = latest.lastPriceObservedAt &&
        (!before?.ticker?.lastPriceObservedAt ||
            Date.parse(latest.lastPriceObservedAt) >= Date.parse(before.ticker.lastPriceObservedAt))
        ? latest : before?.ticker;
    if (latest.barsStatus !== 'AVAILABLE') return {latest, ticker, candles: before?.candles, bars: before?.bars ?? []};

    const byTime = new Map<number, PublicMarketBar>();
    for (const bar of before?.bars ?? []) {
        if (bar.status === 'CLOSED') byTime.set(Date.parse(bar.openTime), bar);
    }
    for (const bar of latest.bars) {
        const key = Date.parse(bar.openTime);
        if (bar.status === 'FORMING' && byTime.get(key)?.status === 'CLOSED') continue;
        byTime.set(key, bar);
    }
    const times = [...byTime.keys()].sort((a, b) => a - b);
    const bars = times.slice(-100).flatMap((time) => {
        const bar = byTime.get(time)!;
        return bar.status === 'CLOSED' || time === times.at(-1) ? [bar] : [];
    });
    return {latest, ticker, candles: latest, bars};
}

function chartBar(bar: MarketdataBar | PublicMarketBar): NqKlineBar {
    return {
        time: bar.openTime, closeTime: bar.closeTime,
        open: Number(bar.openPrice), high: Number(bar.highPrice), low: Number(bar.lowPrice),
        close: Number(bar.closePrice), volume: Number(bar.volume),
        exact: {
            open: String(bar.openPrice), high: String(bar.highPrice), low: String(bar.lowPrice),
            close: String(bar.closePrice), volume: String(bar.volume),
        },
    };
}

export function storedChartBar(bar: MarketdataBar): NqKlineBar {
    return {...chartBar(bar), availableAt: bar.availableAt, qualityStatus: bar.qualityStatus};
}

const exactDecimal = (value: string | number) =>
    formatNqNumber(value, {exact: true, thousands: false});

export interface MarketChart {
    bars: NqKlineBar[];
    conflictCount: number;
}

/** 历史只在身份相同且质量合格时接管；保留冲突的两侧来源用于查看对应 K 线。 */
export function mergeMarketChart(
    selection: PublicMarketSelection | null, stored: readonly MarketdataBar[],
    view: MarketSnapshotView | undefined, now: number,
): MarketChart {
    const times = new Map<number, NqKlineBar>();
    const snapshot = view && selection && sameMarket(view.latest, selection) ? view : undefined;
    for (const bar of snapshot?.bars ?? []) {
        times.set(Date.parse(bar.openTime), {...chartBar(bar), source: snapshot?.latest.source, status: bar.status});
    }
    let conflictCount = 0;
    const snapshotBars = new Map((snapshot?.bars ?? []).map((bar) => [Date.parse(bar.openTime), bar]));
    for (const bar of stored) {
        const time = Date.parse(bar.openTime);
        if (!selection || !sameMarket(bar, selection) || !Number.isFinite(time)) continue;
        const publicBar = snapshotBars.get(time);
        const trusted = bar.qualityStatus === 'OK' && validTime(bar.closeTime)
            && Date.parse(bar.closeTime) <= now && Boolean(bar.source) && bar.source !== 'UNKNOWN';
        if (publicBar && !trusted) continue;
        const conflict = publicBar?.status === 'CLOSED' &&
            (Date.parse(bar.closeTime) !== Date.parse(publicBar.closeTime)
                || values.some((field) => exactDecimal(bar[field]) !== exactDecimal(publicBar[field])));
        if (conflict) conflictCount++;
        times.set(time, {
            ...storedChartBar(bar), source: bar.source, status: trusted ? 'CLOSED' : undefined,
            comparedSource: conflict ? snapshot?.latest.source : undefined,
        });
    }
    return {bars: [...times.entries()].sort(([a], [b]) => a - b).map(([, bar]) => bar), conflictCount};
}

/** 最近历史按实际分钟/小时/日边界补读，单次最多 200 桶。 */
export function recentStoredBars(selection: PublicMarketSelection, now: number): MarketdataBarsQuery {
    const duration = (intervalSeconds(selection.interval) ?? 3600) * 1000;
    const offset = selection.exchangeCode === 'OKX' && selection.interval === '1d' ? 28_800_000 : 0;
    const bucketStart = Math.floor((now + offset) / duration) * duration - offset;
    return {
        ...selection,
        startTime: new Date(bucketStart - duration * 200).toISOString(),
        endTime: new Date(bucketStart - 1).toISOString(),
        page: 0, size: 200,
    };
}

export function snapshotExpiry(view?: MarketSnapshotView): {ticker: number; candles: number} {
    const ticker = Math.min(
        Date.parse(view?.ticker?.lastPriceObservedAt ?? ''),
        Date.parse(view?.ticker?.lastPriceReceivedAt ?? ''),
    ) + MARKET_FRESH_MS;
    const latest = view?.candles;
    const tail = view?.bars.at(-1);
    const close = Date.parse(tail?.closeTime ?? '');
    const candleExpiry = tail?.status === 'FORMING' ? close
        : close + (intervalSeconds(latest?.interval) ?? 0) * 1000 + MARKET_FRESH_MS;
    return {ticker, candles: Math.min(Date.parse(latest?.barsObservedAt ?? '') + MARKET_FRESH_MS, candleExpiry)};
}

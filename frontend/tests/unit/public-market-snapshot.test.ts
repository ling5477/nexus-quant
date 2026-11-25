import {describe, expect, it} from 'vitest';
import type {MarketdataBar, PublicMarketBar, PublicMarketSnapshot} from '@/types/marketdata';
import {
    InvalidPublicMarketSnapshot, mergeMarketChart, recentStoredBars, retainSnapshot, snapshotExpiry,
    supportsPublicSnapshot, validateSnapshot,
} from '@/features/marketdata/public-market-snapshot';

const market = {exchangeCode: 'OKX', marketType: 'SPOT', symbol: 'BTC-USDT', interval: '1h'};
const observedAt = '2026-11-25T01:00:20Z';
const now = Date.parse(observedAt);
const tail: PublicMarketBar = {
    openTime: '2026-11-25T00:00:00Z', closeTime: '2026-11-25T00:59:59.999Z',
    openPrice: '100', highPrice: '107', lowPrice: '95', closePrice: '100.000000000000000001',
    volume: '250', status: 'CLOSED',
};
const prior: PublicMarketBar = {
    ...tail, openTime: '2026-11-24T23:00:00Z', closeTime: '2026-11-24T23:59:59.999Z',
};
const snapshot = (overrides: Partial<PublicMarketSnapshot> = {}): PublicMarketSnapshot => ({
    ...market,
    lastPrice: '110.123456789012345678',
    lastPriceObservedAt: '2026-11-25T01:00:10Z',
    lastPriceReceivedAt: observedAt,
    lastPriceSource: 'OKX_PUBLIC_TICKER',
    bars: [tail], source: 'OKX_PUBLIC_CANDLES', barsObservedAt: observedAt, observedAt,
    tickerStatus: 'AVAILABLE', barsStatus: 'AVAILABLE',
    freshness: 'FRESH', tickerFreshness: 'FRESH', barsFreshness: 'FRESH', retryAt: null,
    ...overrides,
});
const stored = (bar: PublicMarketBar, overrides: Partial<MarketdataBar> = {}): MarketdataBar => ({
    ...market, ...bar, qualityStatus: 'OK', source: 'OKX_PUBLIC_HISTORY', ...overrides,
});

describe('公开行情快照展示', () => {
    it('按时间桶去重、原位更新、接收明确闭合且拒绝状态回退', () => {
        const forming = {...tail, status: 'FORMING'} as const;
        const first = retainSnapshot(snapshot({bars: [forming, prior, {...forming, closePrice: '103'}]}));
        expect(first.bars.map((bar) => [bar.openTime, bar.closePrice, bar.status])).toEqual([
            [prior.openTime, prior.closePrice, 'CLOSED'],
            [forming.openTime, '103', 'FORMING'],
        ]);
        const closed = retainSnapshot(snapshot({bars: [tail, forming]}), first);
        expect(closed.bars.at(-1)).toEqual(tail);
        expect(retainSnapshot(snapshot({bars: [forming]}), closed).bars.at(-1)).toEqual(tail);
    });

    it('可信历史接管同一桶，并在精确十进制有差异时保留两个来源', () => {
        const view = retainSnapshot(snapshot({bars: [tail, prior]}));
        const merged = mergeMarketChart(market, [
            stored(tail, {closePrice: '100.000000000000000002'}),
            stored(prior, {closePrice: '1.00000000000000000001e2'}),
        ], view, now);
        expect(merged.bars).toHaveLength(2);
        expect(merged.conflictCount).toBe(1);
        expect(merged.bars.at(-1)).toMatchObject({
            status: 'CLOSED', source: 'OKX_PUBLIC_HISTORY', comparedSource: 'OKX_PUBLIC_CANDLES',
            exact: {close: '100.000000000000000002'},
        });
    });

    it('未证实的历史保留公开事实，间隔身份隔离，缺桶保持缺失', () => {
        const view = retainSnapshot(snapshot());
        const untrusted = stored(tail, {qualityStatus: 'UNKNOWN', closePrice: '102'});
        const otherInterval = stored(prior, {interval: '4h'});
        const merged = mergeMarketChart(market, [otherInterval, untrusted], view, now);
        expect(merged.bars).toHaveLength(1);
        expect(merged.bars[0].source).toBe('OKX_PUBLIC_CANDLES');
        expect(mergeMarketChart({...market, interval: '4h'}, [], view, now).bars).toEqual([]);
    });

    it('部分失败保留最后成功观测，缺失 ticker 不从 K 线补价', () => {
        const failure = snapshot({
            lastPrice: null, lastPriceObservedAt: null, lastPriceReceivedAt: null,
            tickerStatus: 'TIMEOUT', tickerFreshness: 'UNAVAILABLE', freshness: 'PARTIAL',
        });
        expect(retainSnapshot(failure).ticker).toBeUndefined();
        const view = retainSnapshot(failure, retainSnapshot(snapshot()));
        expect(view.latest.tickerStatus).toBe('TIMEOUT');
        expect(view.ticker?.lastPrice).toBe('110.123456789012345678');
        expect(view.ticker?.lastPriceReceivedAt).toBe(observedAt);
        expect(snapshotExpiry(view).ticker).toBe(Date.parse('2026-11-25T01:01:40Z'));
    });

    it('形成中到达边界只变过期，保持来源原状态', () => {
        const forming = {...tail, status: 'FORMING'} as const;
        const view = retainSnapshot(snapshot({bars: [forming]}));
        expect(snapshotExpiry(view).candles).toBe(Date.parse(tail.closeTime));
        expect(view.bars[0].status).toBe('FORMING');
    });

    it('拒绝身份不符、缺字段及浮点化价格的响应', () => {
        expect(validateSnapshot(snapshot(), market).lastPrice).toBe('110.123456789012345678');
        expect(() => validateSnapshot(snapshot({symbol: 'ETH-USDT'}), market)).toThrow(InvalidPublicMarketSnapshot);
        expect(() => validateSnapshot(snapshot({lastPriceReceivedAt: null}), market)).toThrow(InvalidPublicMarketSnapshot);
        const numeric = {...snapshot(), lastPrice: 110.123} as unknown as PublicMarketSnapshot;
        expect(() => validateSnapshot(numeric, market)).toThrow(InvalidPublicMarketSnapshot);
    });

    it('只启用 OKX SPOT 已知周期，并按 OKX 日线边界计算有界历史窗口', () => {
        expect(supportsPublicSnapshot(market)).toBe(true);
        expect(supportsPublicSnapshot({...market, exchangeCode: 'BINANCE'})).toBe(false);
        expect(supportsPublicSnapshot({...market, marketType: 'SWAP'})).toBe(false);
        expect(supportsPublicSnapshot({...market, interval: '1s'})).toBe(false);
        const request = recentStoredBars({...market, interval: '1d'}, now);
        expect(request.size).toBe(200);
        expect(request.endTime).toBe('2026-11-24T15:59:59.999Z');
    });
});

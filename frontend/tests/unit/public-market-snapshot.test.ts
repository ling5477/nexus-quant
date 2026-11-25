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
    ...market, ...bar, qualityStatus: 'OK', source: 'EXCHANGE_HISTORICAL', venueCloseConfirmed: true, ...overrides,
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
        expect(closed.bars.at(-1)).toMatchObject(tail);
        expect(retainSnapshot(snapshot({bars: [forming]}), closed).bars.at(-1)).toMatchObject(tail);
    });

    it('乱序及缺尾的成功响应保留原形成中观测，夹具重叠时显示差异', () => {
        const forming = {...tail, status: 'FORMING', closePrice: '104'} as const;
        const current = retainSnapshot(snapshot({bars: [prior, forming]}));
        const older = retainSnapshot(snapshot({
            bars: [prior, {...forming, closePrice: '101'}],
            barsObservedAt: '2026-11-25T00:58:10Z',
        }), current);
        const incomplete = retainSnapshot(snapshot({
            bars: [prior], barsObservedAt: '2026-11-25T01:00:50Z',
        }), older);
        expect(incomplete.bars).toHaveLength(2);
        expect(incomplete.bars.at(-1)).toMatchObject({...forming, observedAt});
        expect(snapshotExpiry(incomplete).candles).toBe(Date.parse(forming.closeTime));
        const chart = mergeMarketChart(market, [
            stored(tail, {source: 'STORED_OKX_FIXTURE', closePrice: '105'}),
        ], incomplete, now);
        expect(chart.unconfirmedHistoryCount).toBe(1);
        expect(chart.bars).toHaveLength(2);
        expect(chart.bars.at(-1)).toMatchObject({
            status: 'FORMING', source: 'OKX_PUBLIC_CANDLES', comparedSource: 'STORED_OKX_FIXTURE',
            exact: {close: '104'},
        });
    });

    it('同桶更新遵循该桶观测时间，较新的确认才能由形成中推进为闭合', () => {
        const forming = {...tail, status: 'FORMING'} as const;
        const initial = retainSnapshot(snapshot({bars: [forming]}));
        const updated = retainSnapshot(snapshot({
            bars: [{...forming, closePrice: '104'}], barsObservedAt: '2026-11-25T01:00:50Z',
        }), initial);
        expect(updated.bars.at(-1)?.closePrice).toBe('104');
        expect(retainSnapshot(snapshot({bars: [tail]}), updated).bars.at(-1)?.status).toBe('FORMING');
        const closed = retainSnapshot(snapshot({
            bars: [tail], barsObservedAt: '2026-11-25T01:01:00Z',
        }), updated);
        expect(closed.bars.at(-1)?.status).toBe('CLOSED');
        expect(retainSnapshot(snapshot({
            bars: [forming], barsObservedAt: '2026-11-25T01:01:10Z',
        }), closed).bars.at(-1)?.observedAt).toBe('2026-11-25T01:01:00Z');
        expect(retainSnapshot(snapshot({symbol: 'ETH-USDT', bars: [prior]}), closed).bars).toHaveLength(1);
    });

    it('较新时间桶出现后仍保留旧形成中事实及其原始过期点', () => {
        const forming = {...tail, status: 'FORMING'} as const;
        const next = {
            ...forming,
            openTime: '2026-11-25T01:00:00Z',
            closeTime: '2026-11-25T01:59:59.999Z',
        };
        const previous = retainSnapshot(snapshot({bars: [forming], barsObservedAt: '2026-11-25T00:59:45Z'}));
        const view = retainSnapshot(snapshot({bars: [next]}), previous);
        expect(view.bars.map((bar) => bar.openTime)).toEqual([forming.openTime, next.openTime]);
        expect(mergeMarketChart(market, [], view, now).bars[0]).toMatchObject({
            status: 'FORMING', formingExpiresAt: Date.parse(forming.closeTime),
        });
        expect(snapshotExpiry(view).candles).toBe(now + 90_000);
    });

    it('缺失尾桶的批次不能给之前读到的闭合行情续期', () => {
        const initial = retainSnapshot(snapshot({bars: [tail, prior]}));
        const newer = retainSnapshot(snapshot({
            bars: [prior], barsObservedAt: '2026-11-25T01:02:00Z',
        }), initial);
        expect(snapshotExpiry(newer).candles).toBe(now + 90_000);
    });

    it('历史缺少来源或原始收盘证明时不能取得闭合展示权', () => {
        const publicBar = {...tail, status: 'FORMING'} as const;
        const view = retainSnapshot(snapshot({bars: [publicBar]}));
        for (const bar of [
            stored(tail, {source: 'UNKNOWN'}),
            stored(tail, {venueCloseConfirmed: undefined}),
            stored(tail, {venueCloseConfirmed: false}),
        ]) {
            const merged = mergeMarketChart(market, [bar], view, now);
            expect(merged.bars.at(-1)?.status).toBe('FORMING');
            expect(merged.unconfirmedHistoryCount).toBe(1);
            expect(mergeMarketChart(market, [bar], undefined, now).bars.at(-1)?.status).toBeUndefined();
        }
        expect(mergeMarketChart(market, [stored(tail)], view, now).bars.at(-1)?.status).toBe('CLOSED');
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
            status: 'CLOSED', source: 'EXCHANGE_HISTORICAL', comparedSource: 'OKX_PUBLIC_CANDLES',
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

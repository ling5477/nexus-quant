import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
    clearPriceEventPending,
    loadPriceEventPending,
    persistPriceEventPending,
    previewPriceEventDocument,
    priceEventPendingKey,
    validatePriceEventResponse,
} from '../../src/features/marketdata/price-event-source';
import type {
    MarketdataBar,
    PriceEventSourceRecord,
    PriceEventSourceSubmission,
} from '../../src/types/marketdata';

/** 公开来源夹具验证精度、跨对象恢复及槽并发；不代表真实供应方或真实浏览器核验。 */
const document = {
    exchangeCode: 'OKX',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    interval: '1m',
    openTime: '2026-10-01T00:00:00Z',
    closeTime: '2026-10-01T00:00:59.999Z',
    openPrice: '100',
    highPrice: '110',
    lowPrice: '90',
    closePrice: '105',
    openPriceAt: '2026-10-01T00:00:00.123456Z',
    highPriceAt: '2026-10-01T00:00:05Z',
    lowPriceAt: '2026-10-01T00:00:17Z',
    closePriceAt: '2026-10-01T00:00:58.123456Z',
};
const request: PriceEventSourceSubmission = {
    sourceId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    barId: '9007199254740993',
    expectedBarSha256: 'a'.repeat(64),
    sourceName: 'source-fixture.json',
    documentJson: JSON.stringify(document),
};
const stored = new Map<string, string>();
const key = priceEventPendingKey(1);
beforeEach(() => {
    stored.clear();
    let chain = Promise.resolve();
    vi.stubGlobal('navigator', {
        locks: {
            request: (_key: string, fn: () => unknown) => {
                const result = chain.then(fn);
                chain = result.then(
                    () => undefined,
                    () => undefined,
                );
                return result;
            },
        },
    });
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => stored.get(key) ?? null,
        setItem: (key: string, value: string) => stored.set(key, value),
        removeItem: (key: string) => stored.delete(key),
    });
});
afterEach(() => vi.unstubAllGlobals());

describe('explicit price occurrence sources', () => {
    it('retains large numeric price lexemes and six fractional time digits', () => {
        const input = JSON.stringify(document).replace('"100"', '12345678901234567890.12345678');
        expect(previewPriceEventDocument(input).openPrice).toBe('12345678901234567890.12345678');
        expect(previewPriceEventDocument(input).openPriceAt).toBe(document.openPriceAt);
    });
    it('rejects missing times, extra material, arrays, oversized input and unsupported precision', () => {
        for (const input of [
            {...document, highPriceAt: null},
            {...document, extra: 'unexpected'},
            [],
            {...document, highPriceAt: '2026-10-01T00:00:05.1234567Z'},
        ])
            expect(() => previewPriceEventDocument(JSON.stringify(input))).toThrow();
        expect(() => previewPriceEventDocument(' '.repeat(32769))).toThrow();
    });
    it('restores the original scope and rejects rebinding, extra cache fields and invalid identities', () => {
        expect(loadPriceEventPending(JSON.stringify(request))).toEqual(request);
        expect(
            loadPriceEventPending(JSON.stringify(request), {
                barId: '2',
                barIdentitySha256: 'a'.repeat(64),
            } as MarketdataBar),
        ).toBeNull();
        for (const patch of [
            {extra: 'unexpected'},
            {sourceId: '-'.repeat(36)},
            {sourceName: 'C:\\source.json'},
            {barId: 1},
        ])
            expect(loadPriceEventPending(JSON.stringify({...request, ...patch}))).toBeNull();
        expect(priceEventPendingKey(2)).not.toBe(key);
    });
    it('lets only one concurrent request occupy the slot', async () => {
        const results = await Promise.allSettled([
            persistPriceEventPending(key, request),
            persistPriceEventPending(key, {
                ...request,
                sourceId: 'bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee',
            }),
        ]);
        expect(results.map((result) => result.status)).toEqual(['fulfilled', 'rejected']);
        expect(loadPriceEventPending(stored.get(key)!)).toEqual(request);
    });
    it('preserves a new request and corrupted cache when an older result arrives', async () => {
        const newer = {...request, sourceId: 'bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee'};
        await persistPriceEventPending(key, newer);
        expect(await clearPriceEventPending(key, request)).toBe(false);
        expect(loadPriceEventPending(stored.get(key)!)).toEqual(newer);
        expect(await clearPriceEventPending(key, newer)).toBe(true);
        expect(stored.has(key)).toBe(false);
        stored.set(key, 'corrupted');
        expect(await clearPriceEventPending(key, request)).toBe(false);
        await expect(persistPriceEventPending(key, request)).rejects.toThrow(
            'PENDING_SOURCE_ALREADY_EXISTS',
        );
        expect(stored.get(key)).toBe('corrupted');
    });
    it('fails closed if locking or storage is unavailable', async () => {
        vi.stubGlobal('navigator', {});
        await expect(persistPriceEventPending(key, request)).rejects.toThrow();
        expect(stored.size).toBe(0);
    });
    it('validates equivalent exact responses and rejects wrong versions or microseconds', () => {
        const response = {
            ...request,
            barIdentitySha256: request.expectedBarSha256,
            documentSha256: 'b'.repeat(64),
            documentJson: JSON.stringify({
                ...document,
                openPrice: '100.00000000',
                openPriceAt: '2026-10-01T08:00:00.123456+08:00',
            }),
            openPriceAt: document.openPriceAt,
            highPriceAt: document.highPriceAt,
            lowPriceAt: document.lowPriceAt,
            closePriceAt: document.closePriceAt,
        } as unknown as PriceEventSourceRecord;
        expect(validatePriceEventResponse(request, response)).toEqual(response);
        for (const patch of [
            {barId: '2'},
            {barIdentitySha256: 'c'.repeat(64)},
            {openPriceAt: '2026-10-01T00:00:00.123457Z'},
            {documentJson: JSON.stringify({...document, highPrice: '110.00000001'})},
        ])
            expect(() => validatePriceEventResponse(request, {...response, ...patch})).toThrow();
    });
});

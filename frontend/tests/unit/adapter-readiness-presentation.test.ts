import {describe, expect, it} from 'vitest';
import {
    adapterCapabilityKey,
    adapterStatusView,
    adapterVenueRows,
} from '../../src/pages/adapters/adapterReadinessPresentation';
import type {AdapterReadinessItem} from '../../src/types/adapter-readiness';

/** 展示变换不拥有授权；验证未知能力保留、精确venue筛选及原始禁止状态不变。 */
describe('adapter capability presentation', () => {
    const rows: AdapterReadinessItem[] = ['OKX', 'BINANCE', 'NOOP', 'PAPER', 'SIM'].flatMap(
        (venue) =>
            ['PUBLIC_MARKETDATA', 'PLACE_ORDER', 'CANCEL_ORDER', 'FUTURE_CAPABILITY'].map(
                (capability) => ({
                    venue,
                    capability,
                    status: venue === 'OKX' || venue === 'BINANCE' ? 'NOT_READY' : 'NO_REAL',
                    allowed: false,
                    liveAuthorized: false,
                    reasons: ['LIVE_DISABLED'],
                    message: 'bounded policy fixture',
                }),
            ),
    );
    it('preserves every row and raw permission field without mutating the caller', () => {
        const all = adapterVenueRows(rows, 'ALL');
        expect(all).toHaveLength(20);
        expect(all).not.toBe(rows);
        expect(
            all.every(
                (item, index) =>
                    item === rows[index] && item.allowed === false && item.liveAuthorized === false,
            ),
        ).toBe(true);
    });
    it('filters exact venue identities and retains future capabilities', () => {
        const binance = adapterVenueRows(rows, 'BINANCE');
        expect(binance).toHaveLength(4);
        expect(binance.every((row) => row.venue === 'BINANCE')).toBe(true);
        expect(binance.some((row) => row.capability === 'FUTURE_CAPABILITY')).toBe(true);
        expect(adapterVenueRows(rows, 'binance')).toEqual([]);
    });
    it('keeps unknown states visibly unconfirmed rather than presenting ready', () => {
        for (const value of ['NEW_STATUS', 'constructor', '__proto__', ''])
            expect(adapterStatusView(value)).toEqual({
                key: 'adapterCapabilities.states.unknown',
                color: 'red',
            });
        expect(adapterStatusView('NOT_READY').color).toBe('red');
        expect(adapterStatusView('NO_REAL').key).toBe('adapterCapabilities.states.noReal');
    });
    it('distinguishes subscription and order capabilities while retaining unknown codes for details', () => {
        expect(adapterCapabilityKey('SUBSCRIBE_ORDERBOOK')).toBe(
            'adapterCapabilities.capabilities.SUBSCRIBE_ORDERBOOK',
        );
        expect(adapterCapabilityKey('CANCEL_ORDER')).toBe(
            'adapterCapabilities.capabilities.CANCEL_ORDER',
        );
        expect(adapterCapabilityKey('FUTURE_CAPABILITY')).toBe(
            'adapterCapabilities.capabilities.unknown',
        );
    });
});

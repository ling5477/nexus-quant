import {describe, expect, it} from 'vitest';
import {
    discountAvailable,
    eligibleDiscountDatasets,
    executionObject,
    feePaymentPolicySpec,
    feeSelectionError,
    feeSourceMode,
    validDiscountFraction,
} from '@/features/backtests/fee-source';
import {
    parseFrozenSimFeeCharge,
    parseSimAnalysisResponse,
    parseSimPerformanceResponse,
} from '@/features/paper-trading/analysis-response';
import {backtestCurvePointFromPnl} from '@/features/backtests/BacktestCurveChart';
import type {AccountFeeObservation} from '@/features/accounts/api/fees';
import type {MarketdataDataset} from '@/types/marketdata';
import type {SimPnlSnapshotItem} from '@/features/backtests/backtestsTypes';

/** 费用政策和封存来源必须保持完整；缺价与真实零余额分别消费，不用浮点判断预算边界。 */
describe('优惠资产配置与金融事实消费', () => {
    it('preserves exact valuation identity and server event order at the same visible time', () => {
        const rows = [
            {
                snapshotTime: '2026-01-01T00:03:59Z',
                equity: '100',
                valuationSequence: '9007199254740993',
            },
            {
                snapshotTime: '2026-01-01T00:03:59Z',
                equity: '99',
                valuationSequence: '9007199254740994',
            },
        ] as SimPnlSnapshotItem[];
        const points = rows.map(backtestCurvePointFromPnl);
        expect(points.map((point) => point.t)).toEqual([
            rows[0].snapshotTime,
            rows[1].snapshotTime,
        ]);
        expect(points.map((point) => point.valuationSequence)).toEqual([
            '9007199254740993',
            '9007199254740994',
        ]);
        expect(points.map((point) => point.exact)).toEqual(['100', '99']);
        expect(
            backtestCurvePointFromPnl({
                snapshotTime: rows[0].snapshotTime,
                equity: '100',
            } as SimPnlSnapshotItem).valuationSequence,
        ).toBeUndefined();
    });
    const base = {
        costSource: 'CURRENT_ACCOUNT_OBSERVATION',
        feeObservationId: 'obs',
        feeAccountId: '7',
        feeTradeEnv: 'SIM',
        feeSymbol: 'BTC-USDT',
        feeExchangeCode: 'BINANCE',
        feeAssumptionVersion: 'ACCOUNT_FEE_MODEL_V2',
        feePaymentPolicy: 'DISCOUNT_ASSET_IF_FUNDED',
        discountAssetBudgetFraction: '0.00000001',
        discountPriceDatasetId: '12345678-1234-1234-1234-123456789012',
        quantityStep: '0.0001',
        ruleSha256: 'stable',
    };
    it('accepts exact positive fractions below one and rejects rounded boundary candidates', () => {
        for (const value of ['0.00000001', '0.1', '0.99999999'])
            expect(validDiscountFraction(value)).toBe(true);
        for (const value of ['0', '0.00000000', '1', '1.0', '0.999999999', '1e-8', 0.1, null])
            expect(validDiscountFraction(value)).toBe(false);
        expect(
            executionObject('{"discountAssetBudgetFraction":0.00000001}')
                .discountAssetBudgetFraction,
        ).toBe('0.00000001');
    });
    it('keeps market rules while clearing all old policy-specific and sealed identities', () => {
        const next = feePaymentPolicySpec(
            {
                ...base,
                discountPriceSnapshotId: 'private',
                discountPriceSnapshotSha256: 'a',
                feeSourceSnapshotJson: 'old',
                feeSourceOwnerUserId: '7',
            },
            'RECEIVED_ASSET',
        );
        expect(next.quantityStep).toBe('0.0001');
        expect(next.ruleSha256).toBe('stable');
        expect(next.feeObservationId).toBe('obs');
        for (const key of [
            'discountAssetBudgetFraction',
            'discountPriceDatasetId',
            'discountPriceSnapshotId',
            'discountPriceSnapshotSha256',
            'feeSourceSnapshotJson',
            'feeSourceOwnerUserId',
        ])
            expect(next[key]).toBeUndefined();
        expect(feeSourceMode(base, 'EXPERIMENT_ASSUMPTION').discountPriceDatasetId).toBeUndefined();
    });
    it('requires a dataset reference and refuses client frozen snapshot fields', () => {
        expect(feeSelectionError(JSON.stringify(base), 'BTC-USDT', 'BINANCE')).toBeNull();
        expect(
            feeSelectionError(
                JSON.stringify({...base, discountPriceDatasetId: ''}),
                'BTC-USDT',
                'BINANCE',
            ),
        ).toBe('discountFee.datasetRequired');
        expect(
            feeSelectionError(
                JSON.stringify({...base, discountPriceSnapshotId: 'forged'}),
                'BTC-USDT',
                'BINANCE',
            ),
        ).toBe('discountFee.serverFieldsForbidden');
        expect(
            feeSelectionError(
                JSON.stringify({...base, feeSourceOwnerUserId: '7'}),
                'BTC-USDT',
                'BINANCE',
            ),
        ).toBe('discountFee.serverFieldsForbidden');
        expect(
            feeSelectionError(
                JSON.stringify({...base, feePaymentPolicy: 'RECEIVED_ASSET'}),
                'BTC-USDT',
                'BINANCE',
            ),
        ).toBe('discountFee.policyFieldsInvalid');
    });
    it('shows funded policy only when both actual observed discount conditions are valid', () => {
        const zero = {maker: '0', taker: '0', buyer: '0', seller: '0'};
        const row = {
            exchangeCode: 'BINANCE',
            commissionTerms: {
                nativeSymbol: 'BTCUSDT',
                standard: zero,
                tax: zero,
                special: zero,
                discount: {
                    enabledForAccount: true,
                    enabledForSymbol: true,
                    asset: 'BNB',
                    multiplier: '0.25',
                },
                responseSha256: 'a'.repeat(64),
            },
        } as AccountFeeObservation;
        expect(discountAvailable(row)).toBe(true);
        expect(
            discountAvailable({
                ...row,
                commissionTerms: {
                    ...row.commissionTerms!,
                    discount: {...row.commissionTerms!.discount, enabledForSymbol: false},
                },
            }),
        ).toBe(false);
        expect(discountAvailable({...row, exchangeCode: 'OKX'})).toBe(false);
    });
    it('uses only eligible formal same-exchange one-minute BNB sources', () => {
        const row = {
            datasetId: 'dataset',
            exchangeCode: 'BINANCE',
            marketType: 'SPOT',
            symbol: 'BNB-USDT',
            interval: '1m',
            status: 'READY',
            qualityStatus: 'OK',
            gapCount: 0,
            source: 'marketdata_bars',
        } as MarketdataDataset;
        const bad = [
            {...row, exchangeCode: 'OKX'},
            {...row, symbol: 'BTC-USDT'},
            {...row, interval: '1h'},
            {...row, status: 'INVALID'},
            {...row, gapCount: 1},
            {...row, source: 'public-capture'},
        ];
        expect(eligibleDiscountDatasets([row, ...bad], 'BINANCE')).toEqual([row]);
    });
    it('preserves native reserves and missing valuations through exact analysis parsing', () => {
        const result = parseSimAnalysisResponse(
            '{"facts":{"equity":null,"pnl":null,"discountAsset":{"status":"PRICE_UNAVAILABLE","balance":123456789012345.12345678,"reserved":0.00000001,"available":123456789012345.12345677,"quoteValue":null}}}',
        ) as unknown as {
            facts: {
                equity: null;
                pnl: null;
                discountAsset: {
                    balance: string;
                    reserved: string;
                    available: string;
                    quoteValue: null;
                };
            };
        };
        expect(result.facts.equity).toBeNull();
        expect(result.facts.discountAsset.quoteValue).toBeNull();
        expect(result.facts.discountAsset.balance).toBe('123456789012345.12345678');
        expect(result.facts.discountAsset.reserved).toBe('0.00000001');
    });
    it('parses payment conversion apart from performance valuation with exact decimals', () => {
        const charge = parseFrozenSimFeeCharge(
            '{"currency":"BNB","amount":-0.00000001,"quoteValue":-0.00000123,"paymentStatus":"DISCOUNT_ASSET_USED","positiveReserve":0,"quotePerAsset":123.45678901,"visibleAt":"2026-01-01T00:01:00.000001Z"}',
        );
        expect(charge?.amount).toBe('-0.00000001');
        expect(charge?.positiveReserve).toBe('0');
        const performance = parseSimPerformanceResponse(
            '{"samples":[{"discountAssetBalance":0.00000001,"discountAssetQuoteValue":0.00000123,"discountConversionPrice":123.456789012345678}]}',
        ) as unknown as {samples: Array<{discountConversionPrice: string}>};
        expect(performance.samples[0].discountConversionPrice).toBe('123.456789012345678');
        expect(
            parseFrozenSimFeeCharge(
                '{"currency":"BNB","amount":"1","quoteValue":"2","positiveReserve":"-1","paymentStatus":"DISCOUNT_ASSET_USED"}',
            ),
        ).toBeNull();
    });
    it('keeps a missing equity as a chart gap while retaining real third-asset quantities', () => {
        const row = {
            snapshotTime: '2026-01-01T00:00:00Z',
            equity: null,
            discountAssetCurrency: 'BNB',
            discountAssetBalance: '0.00000001',
            discountAssetQuoteValue: null,
        } as SimPnlSnapshotItem;
        const point = backtestCurvePointFromPnl(row);
        expect(point.v).toBeNull();
        expect(point.exact).toBeUndefined();
        expect(point.discountAssetBalance).toBe('0.00000001');
        expect(point.discountAssetQuoteValue).toBeNull();
    });
});

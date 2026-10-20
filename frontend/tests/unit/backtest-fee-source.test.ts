import {expect, it} from 'vitest';
import {
    executionObject,
    feeSourceMode,
    observedFeeSpec,
    feeSelectionError,
    receivedCommissionRates,
} from '../../src/features/backtests/fee-source';
import type {AccountFeeObservation} from '../../src/features/accounts/api/fees';

/** 来源切换不能污染已冻结身份，精确返佣不得经过浮点或覆盖公开规则。 */
it('clears sealed identity when switching while preserving rule and slippage contracts', () => {
    const next = feeSourceMode(
        {
            feeSourceOwnerUserId: '3',
            feeSourceSnapshotJson: 'private',
            feeSourceSnapshotSha256: 'a',
            feeObservationId: 'old',
            feeRate: '-0.000000000001',
            feeAssumptionVersion: 'old',
            ruleSha256: 'rule',
            slippageBps: '1',
            custom: 'retained',
        },
        'EXPERIMENT_ASSUMPTION',
    );
    expect(next).toEqual({
        costSource: 'EXPERIMENT_ASSUMPTION',
        ruleSha256: 'rule',
        slippageBps: '1',
        custom: 'retained',
    });
});

it('keeps native direction and payment references without a scalar and rejects cross-exchange selection', () => {
    const zero = {maker: '0', taker: '0', buyer: '0', seller: '0'};
    const row = {
        observationId: 'native',
        exchangeAccountId: 5,
        tradeEnv: 'LIVE',
        exchangeCode: 'BINANCE',
        symbol: 'BTC-USDT',
        commissionTerms: {
            nativeSymbol: 'BTCUSDT',
            standard: {...zero, taker: '0.001', buyer: '0.0002', seller: '0.0003'},
            tax: {...zero, taker: '0.00001'},
            special: {...zero, taker: '0.00002'},
            discount: {
                enabledForAccount: true,
                enabledForSymbol: true,
                asset: 'BNB',
                multiplier: '0.25',
            },
            responseSha256: 'a'.repeat(64),
        },
    } as AccountFeeObservation;
    const spec = observedFeeSpec(
        {feeRate: '0.1', slippageBps: '2', feeSourceSnapshotJson: 'forged'},
        row,
    );
    expect(spec.feeRate).toBeUndefined();
    expect(spec.feeSourceSnapshotJson).toBeUndefined();
    expect(spec.slippageBps).toBe('2');
    expect(receivedCommissionRates(row)).toEqual({buy: '0.001230000000', sell: '0.001330000000'});
    expect(feeSelectionError(JSON.stringify(spec), 'BTC-USDT', 'BINANCE')).toBeNull();
    expect(feeSelectionError(JSON.stringify(spec), 'BTC-USDT', 'OKX')).toBe(
        'feeModel.selectRequired',
    );
    expect(feeSelectionError(JSON.stringify({...spec, feeRate: '0'}), 'BTC-USDT', 'BINANCE')).toBe(
        'feeModel.selectRequired',
    );
    expect(
        feeSelectionError(
            JSON.stringify({...spec, feePaymentPolicy: 'DISCOUNT_ASSET_IF_FUNDED'}),
            'BTC-USDT',
            'BINANCE',
        ),
    ).toBe('discountFee.fractionInvalid');
    expect(feeSourceMode(spec, 'EXPERIMENT_ASSUMPTION').feePaymentPolicy).toBeUndefined();
    expect(feeSourceMode(spec, 'EXPERIMENT_ASSUMPTION').feeExchangeCode).toBeUndefined();
});
it('preserves the smallest signed directional amount and refuses incomplete component previews', () => {
    const zero = {maker: '0', taker: '0', buyer: '0', seller: '0'};
    const row = {
        commissionTerms: {
            nativeSymbol: 'BTCUSDT',
            standard: {...zero, taker: '-0.000000000001'},
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
    expect(receivedCommissionRates(row)).toEqual({buy: '-0.000000000001', sell: '-0.000000000001'});
    expect(
        receivedCommissionRates({
            ...row,
            commissionTerms: {
                ...row.commissionTerms!,
                standard: {...zero, taker: 0 as unknown as string},
            },
        }),
    ).toBeNull();
});
it('preserves minimum signed costs and submits references instead of client sealed facts', () => {
    const row = {
        observationId: 'original',
        exchangeAccountId: 4,
        tradeEnv: 'SIM',
        symbol: 'BTC-USDT',
        takerCostRate: '-0.000000000001',
    } as AccountFeeObservation;
    const next = observedFeeSpec({ruleSha256: 'rule', feeSourceOwnerUserId: '9'}, row);
    expect(next.feeRate).toBe('-0.000000000001');
    expect(next.feeAccountId).toBe('4');
    expect(next.feeSourceOwnerUserId).toBeUndefined();
    expect(next.ruleSha256).toBe('rule');
    expect(feeSelectionError(JSON.stringify(next), 'BTC-USDT')).toBeNull();
    expect(feeSelectionError(JSON.stringify(next), 'ETH-USDT')).toBe('feeModel.selectRequired');
    expect(feeSelectionError(JSON.stringify(next), undefined)).toBe('feeModel.selectRequired');
});
it('rejects incomplete or broken source inputs without replacing them with a default', () => {
    expect(feeSelectionError('{', 'BTC-USDT')).toBe('feeModel.invalidJson');
    expect(() => executionObject('[]')).toThrow();
    expect(feeSelectionError('{"costSource":"CURRENT_ACCOUNT_OBSERVATION"}', 'BTC-USDT')).toBe(
        'feeModel.selectRequired',
    );
    expect(executionObject('{"feeRate":0.000000000001}').feeRate).toBe('0.000000000001');
});

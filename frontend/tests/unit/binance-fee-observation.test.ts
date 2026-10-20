import {expect, it} from 'vitest';
import {
    validCommissionTerms,
    validateFeeReceipt,
    type AccountFeeObservation,
    type PendingFeeSync,
    type SpotCommissionTerms,
} from '../../src/features/accounts/api/fees';
import {formatAccountFeePercent} from '../../src/features/accounts/fee-display';
import {observedFeeSpec} from '../../src/features/backtests/fee-source';

const identity = '06c639e1-a738-4e2d-9f82-19a455555bf6';
const pending: PendingFeeSync = {
    ownerUserId: 1,
    accountId: 5,
    credentialId: 7,
    symbol: 'BTC-USDT',
    operationId: identity,
    phase: 'UNKNOWN',
};
const rates = {
    maker: '-0.000000000001',
    taker: '0.001000000000',
    buyer: '0.000000000000',
    seller: '0.000000000000',
};
const terms: SpotCommissionTerms = {
    nativeSymbol: 'BTCUSDT',
    standard: rates,
    tax: {...rates, maker: '0.000000000000'},
    special: rates,
    discount: {
        enabledForAccount: true,
        enabledForSymbol: true,
        asset: 'BNB',
        multiplier: '0.250000000000',
    },
    responseSha256: 'a'.repeat(64),
};
const receipt: AccountFeeObservation = {
    observationId: identity,
    operationId: identity,
    exchangeAccountId: 5,
    credentialId: 7,
    exchangeCode: 'BINANCE',
    tradeEnv: 'LIVE',
    symbol: 'BTC-USDT',
    source: 'BINANCE_PRIVATE_SPOT_COMMISSION',
    rateConvention: 'BINANCE_POSITIVE_COST_V1',
    makerProviderRate: null,
    takerProviderRate: null,
    makerCostRate: null,
    takerCostRate: null,
    tier: null,
    providerAt: null,
    providerTimeBasis: 'NOT_PROVIDED',
    commissionTerms: terms,
    observedAt: '2026-10-14T00:00:00.123456Z',
    expiresAt: '2026-10-14T00:05:00.123456Z',
    sourceSha256: 'b'.repeat(64),
    status: 'SUCCEEDED',
    scope: 'CURRENT_ACCOUNT_AT_OBSERVATION',
};

/** 新来源不能被旧标量或优惠开关伪装；接收完整事实不等于批准运行消费。 */
it('accepts complete native terms with genuinely missing tier and provider time', () => {
    expect(validCommissionTerms(terms)).toBe(true);
    expect(() => validateFeeReceipt(pending, receipt)).not.toThrow();
    expect(formatAccountFeePercent(null)).toBe('—');
    expect(formatAccountFeePercent('0')).toBe('0%');
    expect(formatAccountFeePercent('-0.000000000001')).toBe('-0.0000000001%');
});
it('rejects missing, unknown, numeric and overprecision components', () => {
    for (const changed of [
        {standard: {...rates, buyer: undefined}},
        {tax: {...rates, unexpected: '0'}},
        {special: {...rates, maker: 0}},
        {discount: {...terms.discount, multiplier: '-0.25'}},
        {discount: {...terms.discount, balanceSufficient: true}},
        {standard: {...rates, maker: '-0.0000000000001'}},
    ]) {
        expect(validCommissionTerms({...terms, ...changed})).toBe(false);
    }
});
it('rejects wrong source, native symbol, missing conditions and fake scalar or tier', () => {
    for (const changed of [
        {exchangeCode: 'OKX'},
        {rateConvention: 'OKX_NEGATIVE_COST_V1'},
        {makerCostRate: '0'},
        {tier: 'VIP1'},
        {providerTimeBasis: 'HTTP_RESPONSE_DATE'},
        {commissionTerms: null},
        {commissionTerms: {...terms, nativeSymbol: 'ETHUSDT'}},
    ]) {
        expect(() =>
            validateFeeReceipt(pending, {...receipt, ...changed} as AccountFeeObservation),
        ).toThrow('FEE_SYNC_RESPONSE_INVALID');
    }
});
it('selects the explicit V2 received-asset model without a scalar or client-sealed facts', () => {
    const spec = observedFeeSpec({feeRate: '0.01', feeSourceSnapshotJson: 'forged'}, receipt);
    expect(spec.feeAssumptionVersion).toBe('ACCOUNT_FEE_MODEL_V2');
    expect(spec.feeExchangeCode).toBe('BINANCE');
    expect(spec.feePaymentPolicy).toBe('RECEIVED_ASSET');
    expect(spec.feeRate).toBeUndefined();
    expect(spec.feeSourceSnapshotJson).toBeUndefined();
});

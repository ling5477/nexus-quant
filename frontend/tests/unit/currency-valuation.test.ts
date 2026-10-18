import {describe, expect, it} from 'vitest';
import {
    accountValuationCurrency,
    validateCurrencyRegistry,
    type CurrencyRegistryView,
} from '@/features/preferences/currencies';
import {parseAccountValuationSource} from '@/features/accounts/api/performance';

const registry = (): CurrencyRegistryView => ({
    defaultValuationCurrency: 'USDT',
    defaultDisplayCurrency: 'USDT',
    currencies: [
        {
            code: 'USDT',
            name: 'Tether',
            kind: 'STABLECOIN',
            valuationSupported: true,
            displayConversionSupported: false,
            displayOnly: false,
            status: 'SOURCE_REQUIRED',
        },
        {
            code: 'CNY',
            name: 'Chinese Yuan',
            kind: 'FIAT',
            valuationSupported: false,
            displayConversionSupported: false,
            displayOnly: true,
            status: 'NOT_ENABLED',
        },
    ],
});

describe('币种定义与实际能力', () => {
    it('估值原币、乘积、换算率和源价保留原十进制，不把接近1的汇率显示成固定等价', () => {
        const raw =
            '{"snapshotId":9223372036854775807,"valuationMaxAgeSeconds":7200,"valuationDetails":{"assets":[{"currency":"USDC","amount":1234567890123456.123456789012345678,"value":1234567890123456.12345678901234567890,"conversionRate":0.999999999999999999,"price":{"rate":2.345678901234567890}}]}}';
        expect(parseAccountValuationSource(raw)).toEqual({
            snapshotId: '9223372036854775807',
            valuationMaxAgeSeconds: 7200,
            valuationDetails: {
                assets: [
                    {
                        currency: 'USDC',
                        amount: '1234567890123456.123456789012345678',
                        value: '1234567890123456.12345678901234567890',
                        conversionRate: '0.999999999999999999',
                        price: {rate: '2.345678901234567890'},
                    },
                ],
            },
        });
        expect(JSON.parse(raw).valuationDetails.assets[0].conversionRate).toBe(1);
    });
    it('保留显式预留与展示用途，不生成虚假的换算支持', () => {
        const result = validateCurrencyRegistry(registry());
        expect(result.currencies[1].displayOnly).toBe(true);
        expect(result.currencies.every((c) => !c.displayConversionSupported)).toBe(true);
    });
    it('重复代码、缺失能力字段及不支持的默认币种不能被静默补齐', () => {
        const duplicate = registry();
        duplicate.currencies.push(duplicate.currencies[0]);
        expect(() => validateCurrencyRegistry(duplicate)).toThrow();
        const missing = registry();
        delete (missing.currencies[0] as Partial<CurrencyRegistryView['currencies'][number]>)
            .valuationSupported;
        expect(() => validateCurrencyRegistry(missing)).toThrow();
        const wrong = registry();
        wrong.defaultValuationCurrency = 'CNY';
        expect(() => validateCurrencyRegistry(wrong)).toThrow();
    });
    it('新旧正式账户字段可兼容，但不接受字段冲突或非USDT假折算', () => {
        expect(accountValuationCurrency({quoteCurrency: 'USDT'})).toBe('USDT');
        expect(accountValuationCurrency({quoteCurrency: 'USDT', valuationCurrency: 'USDT'})).toBe(
            'USDT',
        );
        expect(() =>
            accountValuationCurrency({quoteCurrency: 'USDT', valuationCurrency: 'USD'}),
        ).toThrow();
        expect(() => accountValuationCurrency({quoteCurrency: 'CNY'})).toThrow();
    });
});

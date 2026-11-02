import {apiClient} from '@/api/client';

/** 目录只陈述定义和实际能力；displayCurrency不会改变任何账务或权益原值。 */
export interface CurrencyRegistryView {
    defaultValuationCurrency: string;
    defaultDisplayCurrency: string;
    currencies: Array<{
        code: string;
        kind: 'STABLECOIN' | 'DIGITAL_ASSET' | 'FIAT';
        name: string;
        valuationSupported: boolean;
        displayConversionSupported: boolean;
        displayOnly: boolean;
        status: 'SOURCE_REQUIRED' | 'NOT_ENABLED';
    }>;
}

/** 不把缺失能力字段当false或自行补齐币种，防止异常响应变成看似已确认的支持范围。 */
export function validateCurrencyRegistry(value: CurrencyRegistryView): CurrencyRegistryView {
    const validCode = (code: unknown) => typeof code === 'string' && /^[A-Z0-9]{2,32}$/.test(code);
    if (
        !value ||
        !validCode(value.defaultValuationCurrency) ||
        !validCode(value.defaultDisplayCurrency) ||
        !Array.isArray(value.currencies) ||
        value.currencies.length === 0 ||
        value.currencies.length > 1000 ||
        value.currencies.some(
            (c) =>
                !validCode(c.code) ||
                typeof c.name !== 'string' ||
                !['STABLECOIN', 'DIGITAL_ASSET', 'FIAT'].includes(c.kind) ||
                typeof c.valuationSupported !== 'boolean' ||
                typeof c.displayConversionSupported !== 'boolean' ||
                typeof c.displayOnly !== 'boolean' ||
                !['SOURCE_REQUIRED', 'NOT_ENABLED'].includes(c.status),
        ) ||
        new Set(value.currencies.map((c) => c.code)).size !== value.currencies.length ||
        !value.currencies.some(
            (c) => c.code === value.defaultValuationCurrency && c.valuationSupported,
        )
    ) {
        throw Error('CURRENCY_REGISTRY_RESPONSE_MISMATCH');
    }
    return value;
}

export async function readCurrencyRegistry(signal: AbortSignal): Promise<CurrencyRegistryView> {
    return validateCurrencyRegistry(
        (await apiClient.get<CurrencyRegistryView>('/reference/currencies', {signal})).data,
    );
}

/** 兼容已发布账户接口的旧命名；只有该接口的显式计价单位可兼容，不从交易对或旧Paper推断。 */
export function accountValuationCurrency(summary: {
    quoteCurrency: string;
    valuationCurrency?: string;
}): string {
    if (
        summary.valuationCurrency !== undefined &&
        summary.valuationCurrency !== summary.quoteCurrency
    )
        throw Error('ACCOUNT_VALUATION_CURRENCY_RESPONSE_MISMATCH');
    const currency = summary.valuationCurrency ?? summary.quoteCurrency;
    if (currency !== 'USDT') throw Error('ACCOUNT_VALUATION_CURRENCY_RESPONSE_MISMATCH');
    return currency;
}

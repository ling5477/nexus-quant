import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import type {AccountPerformance} from '../types/performance';
import {accountValuationCurrency} from '@/features/preferences/currencies';

const decimals = new Set([
    'equity',
    'cumulativeNetFlow',
    'initialEquity',
    'finalEquity',
    'netFlow',
    'pnl',
    'returnRatio',
    'maxDrawdown',
    'maxDrawdownRatio',
    'value',
    'riskFreeRate',
    'winRate',
    'netProfit',
    'netLoss',
    'profitLossRatio',
]);

/** 估值来源的原币金额、精确乘积和换算价格不能在展开明细时先经过JavaScript浮点。 */
export function parseAccountValuationSource(text: string): unknown {
    return parseExactJson(
        text,
        new Set([
            'balance',
            'available',
            'frozen',
            'price',
            'delta',
            'snapshotId',
            'barId',
            'legacyAccountId',
            'fundingEntryCount',
            'amount',
            'value',
            'conversionRate',
            'rate',
        ]),
    );
}
/** 只读指定账户的真实历史；十进制字段保留原词法值，取消请求不触发采样或资产变更。 */
export async function readAccountPerformance(
    accountId: number,
    from: string,
    to: string,
    signal: AbortSignal,
): Promise<AccountPerformance> {
    const {data} = await apiClient.get<AccountPerformance>(
        `/exchange-accounts/${accountId}/performance`,
        {
            params: {from, to, valuationCurrency: 'USDT'},
            signal,
            transformResponse: [(raw: string) => parseExactJson(raw, decimals)],
        },
    );
    accountValuationCurrency(data.summary);
    if (
        data.summary.exchangeAccountId !== accountId ||
        data.summary.samples.some(
            (sample) =>
                sample.exchangeAccountId !== accountId ||
                accountValuationCurrency(sample) !== 'USDT',
        )
    ) {
        throw Error('ACCOUNT_PERFORMANCE_RESPONSE_SCOPE_MISMATCH');
    }
    return data;
}

import {parseExactJson} from '@/api/exact-json';
import type {AccountFactPage} from './types/trading-workbench';

const economicFields = new Set([
    'price',
    'quantity',
    'fee',
    'availableQuantity',
    'avgPrice',
    'balance',
    'available',
    'frozen',
]);

/** 普通交易同样保留原始十进制词法值，不能先转浮点后再补字符串；null、零与返佣符号分别保留。 */
export function parseTradingWorkbenchResponse(raw: string): unknown {
    return parseExactJson(raw, economicFields);
}

/** 身份或窗口错配不能当空态或另一账户的资产；十进制解析后仍保留原文本。 */
export function validateAccountFactPage<T extends {accountId: number; symbol: string}>(
    value: AccountFactPage<T>, request: {accountId: number; page: number; size: number; symbol?: string},
    source: 'LOCAL_CANONICAL_TRADES' | 'LOCAL_CANONICAL_POSITION_PROJECTION',
): AccountFactPage<T> {
    if (!value || value.exchangeAccountId !== request.accountId || value.source !== source ||
        !Number.isSafeInteger(value.tradingAccountId) || value.tradingAccountId <= 0 ||
        !['SIM','LIVE'].includes(value.tradeEnv) || !value.venue ||
        value.page !== request.page || value.size !== request.size || !Number.isSafeInteger(value.total) || value.total < 0 ||
        !Array.isArray(value.items) || value.items.length > request.size ||
        value.items.some(row => row.accountId !== value.tradingAccountId || (request.symbol && row.symbol !== request.symbol))) {
        throw new Error('Account fact identity or page mismatch');
    }
    return value;
}

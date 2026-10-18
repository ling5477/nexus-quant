import {parseExactJson} from '@/api/exact-json';

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

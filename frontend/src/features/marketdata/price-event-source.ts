import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';
import type {
    MarketdataBar,
    PriceEventSourceRecord,
    PriceEventSourceSubmission,
} from '@/types/marketdata';

const fields = [
    'exchangeCode',
    'marketType',
    'symbol',
    'interval',
    'openTime',
    'closeTime',
    'openPrice',
    'highPrice',
    'lowPrice',
    'closePrice',
    'openPriceAt',
    'highPriceAt',
    'lowPriceAt',
    'closePriceAt',
] as const;
const prices = new Set(['openPrice', 'highPrice', 'lowPrice', 'closePrice']);
const iso = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/;

/** 本地只做来源文件形状预检；精确范围、价格、时序与版本匹配仍由正式服务校验。 */
export function previewPriceEventDocument(text: string): Record<(typeof fields)[number], string> {
    if (new TextEncoder().encode(text).length > 32768)
        throw Error('INVALID_PRICE_EVENT_SOURCE_DOCUMENT');
    const parsed = parseExactJson(text, prices) as Record<string, unknown>;
    if (
        !parsed ||
        Array.isArray(parsed) ||
        typeof parsed !== 'object' ||
        Object.keys(parsed).length !== fields.length ||
        fields.some((key) => typeof parsed[key] !== 'string')
    )
        throw Error('INVALID_PRICE_EVENT_SOURCE_DOCUMENT');
    if (
        fields.some((key) =>
            prices.has(key)
                ? !/^\+?\d+(?:\.\d+)?$/.test(parsed[key] as string)
                : key.endsWith('At') || key.endsWith('Time')
                  ? !iso.test(parsed[key] as string)
                  : false,
        )
    )
        throw Error('INVALID_PRICE_EVENT_SOURCE_DOCUMENT');
    return parsed as Record<(typeof fields)[number], string>;
}

/** 仅保存一个当前用户的有界待确认请求；包含公开行情记录，不能保存未知字段或凭据。 */
export function priceEventPendingKey(userId: number): string {
    return `nq:price-event-source:pending:${userId}`;
}
/** 跨标签页串行占用一个待确认槽，不能覆盖另一请求或损坏的旧缓存。锁只保护存储，不跨网络持有。 */
export async function persistPriceEventPending(
    key: string,
    request: PriceEventSourceSubmission,
): Promise<void> {
    if (!navigator.locks) throw Error('PENDING_SOURCE_STORAGE_UNAVAILABLE');
    await navigator.locks.request(key, () => {
        if (localStorage.getItem(key) !== null) throw Error('PENDING_SOURCE_ALREADY_EXISTS');
        localStorage.setItem(key, JSON.stringify(request));
    });
}
/** 晚到结果只能清理捕获的同一请求；另一标签页的新槽及损坏记录一律保留。 */
export async function clearPriceEventPending(
    key: string,
    request: PriceEventSourceSubmission,
): Promise<boolean> {
    if (!navigator.locks) throw Error('PENDING_SOURCE_STORAGE_UNAVAILABLE');
    return navigator.locks.request(key, () => {
        const stored = loadPriceEventPending(localStorage.getItem(key));
        if (
            !stored ||
            !(
                ['sourceId', 'barId', 'expectedBarSha256', 'sourceName', 'documentJson'] as const
            ).every((field) => stored[field] === request[field])
        )
            return false;
        localStorage.removeItem(key);
        return true;
    });
}
/** 恢复只接受已知的公开行情字段，不透传缓存中的额外内容或替换当前所选版本。 */
export function loadPriceEventPending(
    text: string | null,
    bar?: MarketdataBar,
): PriceEventSourceSubmission | null {
    if (!text || text.length > 100000) return null;
    try {
        const pending = JSON.parse(text) as PriceEventSourceSubmission;
        if (
            !pending ||
            Array.isArray(pending) ||
            Object.keys(pending).length !== 5 ||
            typeof pending.barId !== 'string' ||
            !/^[1-9]\d{0,18}$/.test(pending.barId) ||
            typeof pending.expectedBarSha256 !== 'string' ||
            !/^[a-f0-9]{64}$/.test(pending.expectedBarSha256) ||
            (bar &&
                (pending.barId !== bar.barId ||
                    pending.expectedBarSha256 !== bar.barIdentitySha256)) ||
            typeof pending.sourceId !== 'string' ||
            !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(
                pending.sourceId,
            ) ||
            typeof pending.sourceName !== 'string' ||
            !pending.sourceName.trim() ||
            pending.sourceName.length > 128 ||
            /[/\\]/.test(pending.sourceName) ||
            typeof pending.documentJson !== 'string'
        )
            return null;
        previewPriceEventDocument(pending.documentJson);
        return pending;
    } catch {
        return null;
    }
}
/** 成功响应须仍对应捕获范围与完整四价时点；格式损坏或错对象按未知处理，不能清除待确认身份。 */
export function validatePriceEventResponse(
    request: PriceEventSourceSubmission,
    result: PriceEventSourceRecord,
): PriceEventSourceRecord {
    if (
        !result ||
        result.barId !== request.barId ||
        result.barIdentitySha256 !== request.expectedBarSha256 ||
        result.sourceName !== request.sourceName ||
        typeof result.sourceId !== 'string' ||
        !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(result.sourceId) ||
        typeof result.documentSha256 !== 'string' ||
        !/^[a-f0-9]{64}$/.test(result.documentSha256)
    )
        throw Error('PRICE_EVENT_SOURCE_RESPONSE_INVALID');
    const expected = previewPriceEventDocument(request.documentJson),
        actual = previewPriceEventDocument(result.documentJson);
    const decimal = (value: string) =>
        value
            .replace(/^\+/, '')
            .replace(/^0+(?=\d)/, '')
            .replace(/(\.\d*?)0+$/, '$1')
            .replace(/\.$/, '');
    // ISO时点按微秒比较；Date只参与整数毫秒转换，额外三位不得被浮点解析丢弃。
    const time = (value: string) => {
        const millis = Date.parse(value);
        if (!Number.isFinite(millis)) throw Error('PRICE_EVENT_SOURCE_RESPONSE_INVALID');
        const fraction = (value.match(/\.(\d+)/)?.[1] ?? '').padEnd(6, '0');
        return BigInt(millis) * 1000n + BigInt(fraction.slice(3, 6));
    };
    for (const field of fields) {
        const matches = prices.has(field)
            ? decimal(expected[field]) === decimal(actual[field])
            : field.endsWith('At') || field.endsWith('Time')
              ? time(expected[field]) === time(actual[field])
              : expected[field] === actual[field];
        if (!matches) throw Error('PRICE_EVENT_SOURCE_RESPONSE_INVALID');
    }
    for (const field of ['openPriceAt', 'highPriceAt', 'lowPriceAt', 'closePriceAt'] as const)
        if (
            typeof result[field] !== 'string' ||
            !iso.test(result[field]) ||
            time(result[field]) !== time(actual[field])
        )
            throw Error('PRICE_EVENT_SOURCE_RESPONSE_INVALID');
    return result;
}
export const priceEventSourceApi = {
    async submit(request: PriceEventSourceSubmission): Promise<PriceEventSourceRecord> {
        return validatePriceEventResponse(
            request,
            (
                await apiClient.post<PriceEventSourceRecord>(
                    '/marketdata/price-event-sources',
                    request,
                    {transformResponse: [(raw: string) => parseExactJson(raw, new Set(['barId']))]},
                )
            ).data,
        );
    },
    async verify(request: PriceEventSourceSubmission): Promise<{
        status: 'NOT_FOUND' | 'CONFLICT' | 'MATCH_FOUND';
        source: PriceEventSourceRecord | null;
    }> {
        const result = (
            await apiClient.post<{
                status: 'NOT_FOUND' | 'CONFLICT' | 'MATCH_FOUND';
                source: PriceEventSourceRecord | null;
            }>('/marketdata/price-event-sources/verification', request, {
                transformResponse: [(raw: string) => parseExactJson(raw, new Set(['barId']))],
            })
        ).data;
        if (!result || !['NOT_FOUND', 'CONFLICT', 'MATCH_FOUND'].includes(result.status))
            throw Error('PRICE_EVENT_SOURCE_RESPONSE_INVALID');
        if (result.status === 'MATCH_FOUND') validatePriceEventResponse(request, result.source!);
        else if (result.source !== null) throw Error('PRICE_EVENT_SOURCE_RESPONSE_INVALID');
        return result;
    },
};

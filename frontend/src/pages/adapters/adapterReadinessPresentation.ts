import type {AdapterReadinessItem} from '@/types/adapter-readiness';

const capabilities = new Set([
    'PUBLIC_MARKETDATA',
    'SUBSCRIBE_BARS',
    'SUBSCRIBE_TRADES',
    'SUBSCRIBE_ORDERBOOK',
    'PLACE_ORDER',
    'CANCEL_ORDER',
    'QUERY_ORDER',
    'ACCOUNT_BALANCE',
    'PERMISSION_PROBE',
]);
const statuses: Record<string, {key: string; color: string}> = {
    READY: {key: 'ready', color: 'green'},
    NOT_READY: {key: 'notReady', color: 'red'},
    NO_REAL: {key: 'noReal', color: 'default'},
    FAKE: {key: 'testOnly', color: 'default'},
    STUB: {key: 'testOnly', color: 'default'},
    READY_FOR_PAPER_ONLY: {key: 'paperOnly', color: 'default'},
    DISABLED_SENTINEL: {key: 'endpointDisabled', color: 'orange'},
    CREDENTIAL_UNCONFIGURED: {key: 'credentialsMissing', color: 'orange'},
    LIVE_NOT_AUTHORIZED: {key: 'liveUnauthorized', color: 'red'},
    FUTURE_REAL_DISABLED: {key: 'notImplemented', color: 'orange'},
    PERMISSION_PROBE_DISABLED: {key: 'probeDisabled', color: 'orange'},
    NOT_IMPLEMENTED: {key: 'notImplemented', color: 'orange'},
    CAPABILITY_NOT_IMPLEMENTED: {key: 'notImplemented', color: 'orange'},
    UNKNOWN_REQUIRES_REVIEW: {key: 'unknown', color: 'red'},
};

/** 只翻译已知能力；未来或未知能力仍保留原始记录，在详情显示代码，不静默丢行。 */
export function adapterCapabilityKey(capability: string): string {
    return capabilities.has(capability)
        ? `adapterCapabilities.capabilities.${capability}`
        : 'adapterCapabilities.capabilities.unknown';
}

/** 状态标签不替代允许执行或LIVE授权字段；未知状态必须明显保留为待核查。 */
export function adapterStatusView(status: string): {key: string; color: string} {
    const value = Object.hasOwn(statuses, status)
        ? statuses[status]
        : statuses.UNKNOWN_REQUIRES_REVIEW;
    return {key: `adapterCapabilities.states.${value.key}`, color: value.color};
}

/** 筛选仅改变当前呈现，不合并venue身份、不删掉未实现/未知能力或触发外部连接。 */
export function adapterVenueRows(
    items: readonly AdapterReadinessItem[],
    venue: string,
): AdapterReadinessItem[] {
    return items.filter((item) => venue === 'ALL' || item.venue === venue);
}

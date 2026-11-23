import type {UserNotification} from './api/notifications';

/** 保留服务端Instant亚毫秒精度，同毫秒内的新事件不能被误判为已读。 */
export function notificationInstant(value: string): bigint | null {
    const match = /^(.*:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/.exec(value);
    if (!match) return null;
    const whole = Date.parse(match[1] + match[3]);
    if (!Number.isFinite(whole)) return null;
    return BigInt(whole) * 1000000n + BigInt((match[2] ?? '').padEnd(9, '0'));
}
export function notificationIsUnread(occurredAt: string, readAt: string | null): boolean | null {
    const event = notificationInstant(occurredAt);
    const read = readAt === null ? null : notificationInstant(readAt);
    if (event === null || (readAt !== null && read === null)) return null;
    return read === null || event > read;
}
export function notificationRunPath(paperRunId: string, alertId?: string): string {
    const query = new URLSearchParams({paperRunId});
    if (alertId) query.set('alertId', alertId);
    return '/paper-trading/runs?' + query.toString();
}

/** 来源决定正式深链接；打开通知只读取原对象，不导入本地待执行请求或启动任何任务。 */
export function notificationSourcePath(item: UserNotification): string {
    if (item.kind === 'ALERT' || item.kind === 'RECOVERY') {
        if (!item.paperRunId) throw new Error('Notification run identity missing');
        return notificationRunPath(
            item.paperRunId,
            item.kind === 'ALERT' && item.id.startsWith('alert:') ? item.id.slice(6) : undefined,
        );
    }
    if (!item.sourceId) throw new Error('Notification source identity missing');
    if (item.kind === 'SHADOW_EVENT')
        return `/strategies/shadow-runs/${encodeURIComponent(item.sourceId)}?view=events`;
    const query = new URLSearchParams({
        [item.kind === 'SYSTEM_OPERATION' ? 'operationId' : 'jobKey']: item.sourceId,
    });
    return '/system/scheduler?' + query.toString();
}

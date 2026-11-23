import {apiClient} from '@/api/client';
import {notificationInstant} from '../read-state';

/** 原业务摘要保留各自身份；只读Shadow和系统任务不能伪装为SIM运行。 */
export interface UserNotification {
    id: string;
    kind: 'ALERT' | 'RECOVERY' | 'SHADOW_EVENT' | 'SYSTEM_OPERATION' | 'SYSTEM_HEALTH';
    paperRunId: string | null;
    runKind: 'LEGACY_PAPER' | 'CANONICAL_SIM' | null;
    tradeEnv: string | null;
    title: string;
    severity: string;
    status: string;
    occurredAt: string;
    sourceType?: 'PAPER_RUN' | 'SHADOW_RUN' | 'SCHEDULER_OPERATION' | 'SCHEDULER_JOB';
    sourceId?: string;
}
export interface NotificationPage {
    items: UserNotification[];
    offset: number;
    hasMore: boolean;
    windowLimited: boolean;
    checkedAt: string;
    scope: 'OWNED_SIM_RUN_EVENTS' | 'AUTHORIZED_USER_EVENTS';
}
/** 结构错误必须进入读取失败分支，不能让通知入口崩溃全站，也不能补空列表伪装为没有通知。 */
export function parseNotificationPage(value: unknown, offset: number): NotificationPage {
    const invalid = () =>
        Object.assign(new Error('Invalid notification response'), {
            status: 0,
            code: 'NOTIFICATION_RESPONSE_INVALID',
            method: 'GET',
            path: '/user/notifications',
            fieldErrors: [],
        });
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalid();
    const page = value as Record<string, unknown>;
    if (
        !Number.isSafeInteger(offset) ||
        offset < 0 ||
        offset > 950 ||
        offset % 50 !== 0 ||
        page.offset !== offset ||
        !Array.isArray(page.items) ||
        page.items.length > 50 ||
        typeof page.hasMore !== 'boolean' ||
        typeof page.windowLimited !== 'boolean' ||
        !['OWNED_SIM_RUN_EVENTS', 'AUTHORIZED_USER_EVENTS'].includes(String(page.scope)) ||
        typeof page.checkedAt !== 'string' ||
        notificationInstant(page.checkedAt) === null
    )
        throw invalid();
    const ids = new Set<string>();
    for (const value of page.items) {
        if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalid();
        const item = value as Record<string, unknown>;
        const text = (field: string, limit: number) =>
            typeof item[field] === 'string' &&
            item[field].length > 0 &&
            item[field].length <= limit;
        if (
            !text('id', 256) ||
            !text('title', 1024) ||
            !text('severity', 128) ||
            !text('status', 128) ||
            !['ALERT', 'RECOVERY', 'SHADOW_EVENT', 'SYSTEM_OPERATION', 'SYSTEM_HEALTH'].includes(
                String(item.kind),
            ) ||
            typeof item.occurredAt !== 'string' ||
            notificationInstant(item.occurredAt) === null ||
            ids.has(item.id as string)
        )
            throw invalid();
        if (item.kind === 'ALERT' || item.kind === 'RECOVERY') {
            if (
                !text('paperRunId', 128) ||
                !['LEGACY_PAPER', 'CANONICAL_SIM'].includes(String(item.runKind)) ||
                item.tradeEnv !== 'SIM' ||
                (item.sourceType !== undefined && item.sourceType !== 'PAPER_RUN') ||
                (item.sourceId !== undefined && item.sourceId !== item.paperRunId)
            )
                throw invalid();
        } else {
            const expected =
                item.kind === 'SHADOW_EVENT'
                    ? 'SHADOW_RUN'
                    : item.kind === 'SYSTEM_OPERATION'
                      ? 'SCHEDULER_OPERATION'
                      : 'SCHEDULER_JOB';
            if (
                item.paperRunId !== null ||
                item.runKind !== null ||
                item.tradeEnv !== null ||
                item.sourceType !== expected ||
                !text('sourceId', 128) ||
                (expected === 'SCHEDULER_JOB'
                    ? !/^[A-Z][A-Z0-9_]{1,47}$/.test(String(item.sourceId))
                    : !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(String(item.sourceId)))
            )
                throw invalid();
        }
        ids.add(item.id as string);
    }
    return value as NotificationPage;
}
/** 只读最多1000条通知窗口；hasMore与windowLimited区分可翻页和窗口截断。 */
export const notificationsApi = {
    async read(offset: number, signal?: AbortSignal): Promise<NotificationPage> {
        const {data} = await apiClient.get<unknown>('/user/notifications', {
            params: {offset},
            signal,
        });
        return parseNotificationPage(data, offset);
    },
};

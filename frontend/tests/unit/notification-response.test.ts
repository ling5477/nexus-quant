import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '@/api/client';
import {notificationsApi, parseNotificationPage} from '@/features/notifications/api/notifications';

const page = {
    items: [],
    offset: 0,
    hasMore: false,
    windowLimited: false,
    checkedAt: '2026-10-15T00:00:00.123456789Z',
    scope: 'OWNED_SIM_RUN_EVENTS',
};
const item = {
    id: 'alert:one',
    kind: 'ALERT',
    paperRunId: 'ptr-notification',
    runKind: 'CANONICAL_SIM',
    tradeEnv: 'SIM',
    title: 'Alert',
    severity: 'FUTURE_SEVERITY',
    status: 'OPEN',
    occurredAt: '2026-10-15T00:00:00.123456788Z',
};
afterEach(() => vi.restoreAllMocks());
describe('notification response boundary', () => {
    it('accepts explicit Shadow and system identities without inventing Paper or SIM context', () => {
        const shadow = {
            ...item,
            id: 'shadow:event',
            kind: 'SHADOW_EVENT',
            paperRunId: null,
            runKind: null,
            tradeEnv: null,
            sourceType: 'SHADOW_RUN',
            sourceId: 'd5f6b170-1ed6-4dc5-8d51-4781b4324fae',
        };
        const operation = {
            ...shadow,
            id: 'scheduler:request',
            kind: 'SYSTEM_OPERATION',
            sourceType: 'SCHEDULER_OPERATION',
        };
        const health = {
            ...shadow,
            id: 'job:PAPER_MATCHING:1',
            kind: 'SYSTEM_HEALTH',
            sourceType: 'SCHEDULER_JOB',
            sourceId: 'PAPER_MATCHING',
        };
        expect(
            parseNotificationPage(
                {...page, scope: 'AUTHORIZED_USER_EVENTS', items: [shadow, operation, health]},
                0,
            ).items,
        ).toHaveLength(3);
        for (const value of [
            {...shadow, paperRunId: 'fake-paper'},
            {...shadow, tradeEnv: 'SIM'},
            {...operation, sourceType: 'SHADOW_RUN'},
            {...health, sourceId: '../task'},
            {...shadow, sourceId: 'not-a-uuid'},
            {...item, sourceType: 'PAPER_RUN', sourceId: 'different-run'},
        ])
            expect(() => parseNotificationPage({...page, items: [value]}, 0)).toThrow(
                'Invalid notification response',
            );
    });
    it('keeps the real empty page and original nanosecond event time', () => {
        expect(parseNotificationPage(page, 0)).toEqual(page);
        expect(parseNotificationPage({...page, items: [item]}, 0).items[0].occurredAt).toBe(
            item.occurredAt,
        );
    });
    it('rejects malformed, wrong-scope, wrong-page, oversized and invalid event payloads rather than replacing them with empty data', () => {
        for (const value of [
            [],
            null,
            {},
            {...page, items: null},
            {...page, offset: 50},
            {...page, hasMore: null},
            {...page, checkedAt: 'invalid'},
            {...page, scope: 'OTHER'},
            {...page, items: [item, item]},
            {...page, items: [{...item, paperRunId: null}]},
            {...page, items: [{...item, tradeEnv: 'LIVE'}]},
            {...page, items: [{...item, occurredAt: 'invalid'}]},
            {
                ...page,
                items: Array.from({length: 51}, (_, index) => ({...item, id: `alert:${index}`})),
            },
        ]) {
            expect(() => parseNotificationPage(value, 0)).toThrowError(
                'Invalid notification response',
            );
        }
    });
    it('converts a malformed successful GET into an explicit read failure', async () => {
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: []});
        await expect(notificationsApi.read(0)).rejects.toMatchObject({
            code: 'NOTIFICATION_RESPONSE_INVALID',
            method: 'GET',
        });
    });
});

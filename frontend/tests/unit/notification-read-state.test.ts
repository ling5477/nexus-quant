import {describe, expect, it} from 'vitest';
import {
    notificationInstant,
    notificationIsUnread,
    notificationRunPath,
    notificationSourcePath,
} from '../../src/features/notifications/read-state';
import type {UserNotification} from '@/features/notifications/api/notifications';

describe('通知阅读边界', () => {
    it('不同来源定位原对象，系统回执链接不会变成执行请求', () => {
        const base = {
            id: 'event',
            title: 'CREATED',
            severity: 'INFO',
            status: 'CREATED',
            occurredAt: '2026-10-17T00:00:00Z',
            paperRunId: null,
            runKind: null,
            tradeEnv: null,
            sourceId: 'd5f6b170-1ed6-4dc5-8d51-4781b4324fae',
        };
        expect(
            notificationSourcePath({...base, kind: 'SHADOW_EVENT', sourceType: 'SHADOW_RUN'}),
        ).toBe('/strategies/shadow-runs/' + base.sourceId + '?view=events');
        expect(
            notificationSourcePath({
                ...base,
                kind: 'SYSTEM_OPERATION',
                sourceType: 'SCHEDULER_OPERATION',
            }),
        ).toBe('/system/scheduler?operationId=' + base.sourceId);
        expect(
            notificationSourcePath({
                ...base,
                kind: 'SYSTEM_HEALTH',
                sourceType: 'SCHEDULER_JOB',
                sourceId: 'PAPER_MATCHING',
            }),
        ).toBe('/system/scheduler?jobKey=PAPER_MATCHING');
        expect(() =>
            notificationSourcePath({...base, kind: 'RECOVERY'} as UserNotification),
        ).toThrow('identity missing');
    });
    it('保留同毫秒内的先后顺序和等价时区', () => {
        expect(
            notificationIsUnread('2026-10-13T01:00:00.123456Z', '2026-10-13T01:00:00.123455Z'),
        ).toBe(true);
        expect(
            notificationIsUnread('2026-10-13T01:00:00.123Z', '2026-10-13T01:00:00.123000Z'),
        ).toBe(false);
        expect(notificationInstant('2026-10-13T09:00:00.123456+08:00')).toBe(
            notificationInstant('2026-10-13T01:00:00.123456Z'),
        );
    });
    it('未知边界不伪装为已读，旧事件与新事件独立判断', () => {
        expect(notificationIsUnread('invalid', null)).toBe(null);
        expect(notificationIsUnread('2026-10-13T01:00:00Z', 'invalid')).toBe(null);
        expect(notificationIsUnread('2026-10-13T01:00:00Z', null)).toBe(true);
        expect(notificationIsUnread('2026-10-13T01:00:00Z', '2026-10-13T01:00:01Z')).toBe(false);
    });
    it('来源深链接编码精确对象身份', () => {
        const url = new URL(notificationRunPath('run+a &b', 'alert+/?&一'), 'http://localhost');
        expect(url.pathname).toBe('/paper-trading/runs');
        expect(url.searchParams.get('paperRunId')).toBe('run+a &b');
        expect(url.searchParams.get('alertId')).toBe('alert+/?&一');
        expect(
            new URL(notificationRunPath('ptr-only'), 'http://localhost').searchParams.has(
                'alertId',
            ),
        ).toBe(false);
    });
});

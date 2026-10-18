import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {useAuthStore} from '../../src/store/auth-store';
import type {CurrentUser} from '../../src/types/auth';
import {
    dailyScheduleWindow,
    normalizeScheduleCreation,
    type ScheduleCreationInput,
} from '../../src/features/schedules/creation-input';
import {
    confirmScheduleCreation,
    readScheduleCreation,
    scheduleCreation,
    type PendingScheduleCreation,
    type ScheduleCreationReceipt,
} from '../../src/features/schedules/creation';

const stored = new Map<string, string>();
const locks = new Set<string>();
const storageKey = 'nq:schedule-creation:v1:17';
const user: CurrentUser = {
    userId: 17,
    username: 'schedule-owner',
    roles: ['OPERATOR'],
    authenticated: true,
    mustChangePassword: false,
    defaultExchangeAccountId: null,
    defaultExchangeCode: null,
    defaultTradeEnv: null,
    defaultAccountAlias: null,
};
const input: ScheduleCreationInput = {
    strategyId: 'str-schedule',
    scheduleType: 'CRON',
    cronExpr: '0 * * * * *',
    timezone: 'Asia/Shanghai',
    enabled: false,
    windowConfig: '{"exact":0.12345678901234567890123456789}',
    dedupScope: 'SCHEDULE_WINDOW',
};
const id = '22222222-2222-4222-8222-222222222222';
const receipt = (creationId = id): ScheduleCreationReceipt => ({
    creationId,
    status: 'COMPLETED',
    requestVersion: 1,
    request: {...input},
    result: {
        scheduleJobId: 'sch-33333333-3333-4333-8333-333333333333',
        ...input,
        status: 'DISABLED',
        exchangeCode: 'BINANCE',
        accountId: '9007199254740993',
        tradeEnv: 'SIM',
        lastTriggeredAt: null,
        createdAt: '2026-10-16T01:00:00.123456Z',
        updatedAt: '2026-10-16T01:00:00.123456Z',
    },
    traceId: 'trace-schedule-creation',
    completedAt: '2026-10-16T01:00:00.123456Z',
});

beforeEach(() => {
    stored.clear();
    locks.clear();
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('crypto', {randomUUID: () => id});
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => stored.get(key) ?? null,
        setItem: (key: string, value: string) => stored.set(key, value),
        removeItem: (key: string) => stored.delete(key),
    });
    vi.stubGlobal('navigator', {
        locks: {
            request: async (
                name: string,
                _options: unknown,
                callback: (lock: unknown) => Promise<unknown>,
            ) => {
                if (locks.has(name)) return callback(null);
                locks.add(name);
                try {
                    return await callback({name});
                } finally {
                    locks.delete(name);
                }
            },
        },
    });
    useAuthStore.setState({currentUser: user});
});
afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    useAuthStore.setState({currentUser: null});
});
const pending = (): PendingScheduleCreation => {
    const state = readScheduleCreation(17);
    if (state.kind !== 'saved') throw new Error('expected durable original request');
    return state.pending;
};
async function lostResponse() {
    vi.spyOn(apiClient, 'post').mockRejectedValue({status: 0, code: 'NETWORK_ERROR'});
    await scheduleCreation.submit(input).catch(() => undefined);
    return pending();
}

describe('durable schedule creation', () => {
    it('saves the exact input before POST, retains unknown across re-entry and forbids another ID', async () => {
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            const state = JSON.parse(stored.get(storageKey)!);
            expect(state.request.windowConfig).toBe(input.windowConfig);
            expect(state.creationId).toBe(id);
            throw {status: 0, code: 'NETWORK_ERROR'};
        });
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({code: 'NETWORK_ERROR'});
        expect(pending().phase).toBe('UNKNOWN');
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_PENDING',
        });
        expect(post).toHaveBeenCalledTimes(1);
        expect(readScheduleCreation(18)).toEqual({kind: 'none'});
    });
    it('does not send when saving, readback, secure coordination or identity generation fails', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('localStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('denied');
            },
        });
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_STORAGE_UNAVAILABLE',
        });
        vi.stubGlobal('localStorage', {getItem: () => null, setItem: () => undefined});
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_STORAGE_UNAVAILABLE',
        });
        vi.stubGlobal('navigator', {});
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_COORDINATION_UNAVAILABLE',
        });
        expect(post).not.toHaveBeenCalled();
    });
    it('treats GET404 as unknown and only explicitly resumes the original complete request', async () => {
        const original = await lostResponse();
        await expect(scheduleCreation.resume(original)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_LOOKUP_REQUIRED',
        });
        const post = vi.spyOn(apiClient, 'post').mockResolvedValue({data: receipt()});
        post.mockClear();
        const get = vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404, code: 'NOT_FOUND'});
        await expect(scheduleCreation.lookup(original)).rejects.toMatchObject({status: 404});
        expect(post).not.toHaveBeenCalled();
        expect(get).toHaveBeenCalledWith(`/strategy-schedule-creations/${id}`);
        expect(pending().request).toEqual(input);
        await scheduleCreation.resume(original);
        expect(post).toHaveBeenCalledWith('/strategy-schedule-creations', {
            creationId: id,
            request: input,
        });
        expect(pending().phase).toBe('COMPLETED');
        expect(pending().receipt?.result.accountId).toBe('9007199254740993');
    });
    it('retains a completed receipt until explicit acknowledgement, without another POST', async () => {
        vi.spyOn(apiClient, 'post').mockResolvedValue({data: receipt()});
        await scheduleCreation.submit(input);
        expect(pending().receipt).toEqual(receipt());
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_PENDING',
        });
        await scheduleCreation.acknowledge(pending());
        expect(readScheduleCreation(17)).toEqual({kind: 'none'});
    });
    it('keeps all conflicts and recovery rejections unresolved; initial definite validation rejection can release the ID', async () => {
        const original = await lostResponse();
        vi.spyOn(apiClient, 'post').mockRejectedValue({
            status: 409,
            code: 'STRATEGY_SCHEDULE_CREATION_ID_CONFLICT',
        });
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 404});
        await scheduleCreation.lookup(original).catch(() => undefined);
        await scheduleCreation.resume(original).catch(() => undefined);
        expect(pending().creationId).toBe(id);
        await scheduleCreation.lookup(original).catch(() => undefined);
        vi.spyOn(apiClient, 'post').mockRejectedValue({status: 400});
        await scheduleCreation.resume(original).catch(() => undefined);
        expect(pending().phase).toBe('UNKNOWN');
        stored.clear();
        await scheduleCreation.submit(input).catch(() => undefined);
        expect(readScheduleCreation(17)).toEqual({kind: 'none'});
    });
    it('refuses cross-owner and viewer recovery without issuing HTTP', async () => {
        const original = await lostResponse();
        const get = vi.spyOn(apiClient, 'get');
        useAuthStore.setState({currentUser: {...user, userId: 18}});
        await expect(scheduleCreation.lookup(original)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_SCOPE_CHANGED',
        });
        useAuthStore.setState({currentUser: {...user, roles: ['VIEWER']}});
        await expect(scheduleCreation.lookup(original)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_SCOPE_UNAVAILABLE',
        });
        expect(get).not.toHaveBeenCalled();
        expect(stored.has(storageKey)).toBe(true);
    });
    it('keeps the original unresolved if the login identity changes while a response arrives', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            useAuthStore.setState({currentUser: {...user, userId: 18}});
            return {data: receipt()};
        });
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_SCOPE_CHANGED',
        });
        useAuthStore.setState({currentUser: user});
        expect(pending().phase).toBe('UNKNOWN');
        expect(stored.has('nq:schedule-creation:v1:18')).toBe(false);
    });
    it('blocks simultaneous submissions across the same browser owner lock', async () => {
        let release!: () => void;
        let entered!: () => void;
        const started = new Promise<void>((resolve) => {
            entered = resolve;
        });
        const done = new Promise<void>((resolve) => {
            release = resolve;
        });
        const post = vi.spyOn(apiClient, 'post').mockImplementation(async () => {
            entered();
            await done;
            return {data: receipt()};
        });
        const first = scheduleCreation.submit(input);
        await started;
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_PENDING',
        });
        release();
        await first;
        expect(post).toHaveBeenCalledTimes(1);
    });
    it('rejects wrong IDs, changed original inputs, rounded accounts and incomplete receipts', async () => {
        const original = await lostResponse();
        const valid = receipt();
        const values = [
            {...valid, creationId: 'other'},
            {...valid, requestVersion: 2},
            {...valid, request: {...input, windowConfig: input.windowConfig.replace(':', ': ')}},
            {...valid, result: {...valid.result, accountId: 9007199254740992}},
            {...valid, result: {...valid.result, enabled: true}},
            {...valid, result: {...valid.result, strategyId: 'other'}},
            {...valid, completedAt: 'invalid'},
        ];
        for (const value of values)
            expect(() =>
                confirmScheduleCreation(original, value as ScheduleCreationReceipt),
            ).toThrow();
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({data: values[0]});
        await expect(scheduleCreation.lookup(original)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_RESPONSE_INVALID',
        });
        expect(get).toHaveBeenCalledTimes(1);
        expect(pending().phase).toBe('UNKNOWN');
    });
    it('does not treat damaged or foreign stored input as an empty slot', async () => {
        stored.set(storageKey, '{broken');
        const post = vi.spyOn(apiClient, 'post');
        expect(readScheduleCreation(17)).toEqual({kind: 'unavailable'});
        await expect(scheduleCreation.submit(input)).rejects.toMatchObject({
            code: 'SCHEDULE_CREATION_PENDING',
        });
        stored.set(
            storageKey,
            JSON.stringify({
                ownerUserId: 18,
                creationId: id,
                request: input,
                phase: 'UNKNOWN',
                recordedAt: Date.now(),
            }),
        );
        expect(readScheduleCreation(17)).toEqual({kind: 'unavailable'});
        expect(post).not.toHaveBeenCalled();
    });
});

describe('schedule time and raw numeric input', () => {
    it('retains raw decimal precision and JSON spacing while trimming only the boundary', () => {
        expect(
            normalizeScheduleCreation({
                ...input,
                strategyId: ' str-schedule ',
                windowConfig: `  ${input.windowConfig}  `,
            }),
        ).toEqual(input);
        const raw = '{ "exact" : 0.12345678901234567890123456789 }';
        expect(normalizeScheduleCreation({...input, windowConfig: raw}).windowConfig).toBe(raw);
        for (const invalid of [
            '[]',
            'null',
            '"text"',
            '{bad',
            JSON.stringify({x: '中'.repeat(3000)}),
        ])
            expect(() => normalizeScheduleCreation({...input, windowConfig: invalid})).toThrow();
        expect(() =>
            normalizeScheduleCreation({...input, scheduleType: 'MANUAL' as 'CRON'}),
        ).toThrow();
    });
    it('supports seconds, cross-midnight, all-day and explicit weekdays without applying date range ordering', () => {
        expect(JSON.parse(dailyScheduleWindow('23:59:58', '00:00:02', ['MONDAY']))).toEqual({
            enabled: true,
            startTime: '23:59:58',
            endTime: '00:00:02',
            daysOfWeek: ['MONDAY'],
        });
        expect(JSON.parse(dailyScheduleWindow('00:00:00', '00:00:00', [])).endTime).toBe(
            '00:00:00',
        );
        for (const times of [
            ['24:00:00', '00:00:00'],
            ['12:00', '13:00:00'],
            ['12:00:60', '13:00:00'],
        ])
            expect(() => dailyScheduleWindow(times[0], times[1], [])).toThrow();
        expect(() => dailyScheduleWindow('00:00:00', '01:00:00', ['MONDAY', 'MONDAY'])).toThrow();
    });
});

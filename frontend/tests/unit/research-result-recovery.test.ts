import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
    clearResultAction,
    originalResult,
    isResultActionInFlight,
    queryResultAction,
    readResultAction,
    submitResultAction,
} from '@/features/research-result-recovery';
const fixture = vi.hoisted(() => ({user: {userId: 17, roles: ['ADMIN']}}));
const cache = vi.hoisted(() => ({
    setQueryData: vi.fn(),
    invalidateQueries: vi.fn(),
    mutation: {} as {onSuccess: () => void},
}));
vi.mock('@tanstack/react-query', () => ({
    useQueryClient: () => cache,
    useMutation: (options: typeof cache.mutation) => {
        cache.mutation = options;
        return {};
    },
}));
import {useEvaluateMutation} from '@/pages/evaluations/useEvaluationsListQuery';
import {confirmOriginalReviewEvent, parseReviewOperationTarget} from '@/features/validation/api/validation-review';
vi.mock('@/store/auth-store', () => ({
    useAuthStore: {getState: () => ({currentUser: fixture.user})},
}));
let data: Map<string, string>;
let held: Set<string>;
beforeEach(() => {
    data = new Map();
    held = new Set();
    fixture.user = {userId: 17, roles: ['ADMIN']};
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => data.get(key) ?? null,
        setItem: (key: string, value: string) => data.set(key, value),
        removeItem: (key: string) => data.delete(key),
    });
    vi.stubGlobal('navigator', {
        locks: {
            request: async (
                name: string,
                options: {ifAvailable: boolean},
                work: (lock: unknown) => Promise<unknown>,
            ) => {
                expect(options.ifAvailable).toBe(true);
                if (held.has(name)) return work(null);
                held.add(name);
                try {
                    return await work({name});
                } finally {
                    held.delete(name);
                }
            },
        },
    });
});
afterEach(() => vi.unstubAllGlobals());
describe('原操作范围保留与禁止盲重发', () => {
    it('同步在登记前明确被禁用时解除本次请求，不把业务拒绝留成UNKNOWN', async () => {
        const write = vi.fn(async (id: string) => {
            throw Object.assign(new Error('INSTRUMENT_CATALOG_SYNC_DISABLED'), {
                status: 409,
                code: 'STATE_CONFLICT',
                method: 'POST',
                path: `/api/instruments/sync-requests/${id}`,
            });
        });
        await expect(submitResultAction('INSTRUMENT_SYNC', 'OKX', write)).rejects.toThrow(
            'INSTRUMENT_CATALOG_SYNC_DISABLED',
        );
        expect(write).toHaveBeenCalledTimes(1);
        expect(readResultAction(17, 'INSTRUMENT_SYNC')).toBeNull();
    });
    it.each([
        {status: 0},
        {code: 'OTHER_CONFLICT'},
        {message: 'OTHER_REASON'},
        {method: 'GET'},
        {path: '/api/instruments/sync-requests/other-request'},
    ])('没有原POST明确拒绝的完整依据时仍禁止重发：%o', async (difference) => {
        await expect(
            submitResultAction('INSTRUMENT_SYNC', 'OKX', async (id) => {
                throw Object.assign(new Error('INSTRUMENT_CATALOG_SYNC_DISABLED'), {
                    status: 409,
                    code: 'STATE_CONFLICT',
                    method: 'POST',
                    path: `/api/instruments/sync-requests/${id}`,
                    ...difference,
                });
            }),
        ).rejects.toBeDefined();
        expect(readResultAction(17, 'INSTRUMENT_SYNC')).toMatchObject({target: 'OKX'});
    });
    it('在发送前持久化原范围，正常响应后清除，不保存认证材料', async () => {
        await submitResultAction(
            'PUBLISH',
            'run-original',
            async () => {
                expect(readResultAction(17, 'PUBLISH')).toMatchObject({
                    owner: 17,
                    target: 'run-original',
                    strategyVersionId: 'version-frozen',
                });
                expect([...data.values()].join()).not.toMatch(/token|password|credential/i);
                return {publishStatus: 'FAILED'};
            },
            'version-frozen',
        );
        expect(readResultAction(17, 'PUBLISH')).toBeNull();
    });
    it.each([0, 408, 429, 500, 502, 503, 504])(
        '状态%s保留UNKNOWN，查询失败及重复点击不能再发送',
        async (status) => {
            const write = vi.fn(async () => {
                throw {status};
            });
            await expect(submitResultAction('INSTRUMENT_SYNC', 'OKX', write)).rejects.toMatchObject(
                {status},
            );
            expect(readResultAction(17, 'INSTRUMENT_SYNC')).toMatchObject({target: 'OKX'});
            await expect(submitResultAction('INSTRUMENT_SYNC', 'BINANCE', write)).rejects.toThrow();
            expect(write).toHaveBeenCalledTimes(1);
            const pending = readResultAction(17, 'INSTRUMENT_SYNC');
            if (!pending || pending === 'UNAVAILABLE') throw Error('missing');
            await expect(
                queryResultAction(pending, async () => {
                    throw {status: 404};
                }),
            ).rejects.toMatchObject({status: 404});
            expect(readResultAction(17, 'INSTRUMENT_SYNC')).toEqual(pending);
        },
    );
    it.each([400, 401, 403, 404, 409, 422])('明确拒绝%s清除本次登记', async (status) => {
        await expect(
            submitResultAction('PUBLISH', 'run-original', async () => {
                throw {status};
            }),
        ).rejects.toMatchObject({status});
        expect(readResultAction(17, 'PUBLISH')).toBeNull();
    });
    it('持锁期间重复点击不排队执行第二次，恢复GET也不能抢先确认', async () => {
        let release!: () => void;
        const gate = new Promise<void>((resolve) => {
            release = resolve;
        });
        const first = submitResultAction('PUBLISH', 'run-original', () => gate, 'version');
        const pending = readResultAction(17, 'PUBLISH');
        if (!pending || pending === 'UNAVAILABLE') throw Error('missing');
        const write = vi.fn();
        const read = vi.fn();
        await expect(
            submitResultAction('PUBLISH', 'run-original', write, 'version'),
        ).rejects.toThrow();
        await expect(queryResultAction(pending, read)).rejects.toThrow();
        expect(write).not.toHaveBeenCalled();
        expect(read).not.toHaveBeenCalled();
        release();
        await first;
    });
    it('刷新可重读同一记录，切换主体不能读取、查询或清除别人的记录', async () => {
        await expect(
            submitResultAction('EVALUATE', 'run-original', async () => {
                throw {status: 0};
            }),
        ).rejects.toBeDefined();
        const pending = readResultAction(17, 'EVALUATE');
        if (!pending || pending === 'UNAVAILABLE') throw Error('missing');
        fixture.user.userId = 18;
        expect(readResultAction(18, 'EVALUATE')).toBeNull();
        await expect(queryResultAction(pending, vi.fn())).rejects.toThrow();
        expect(() => clearResultAction(pending)).toThrow();
        fixture.user.userId = 17;
        expect(readResultAction(17, 'EVALUATE')).toEqual(pending);
    });
    it('当前结果查询不自动清除评估未知状态', async () => {
        await expect(
            submitResultAction('EVALUATE', 'run-original', async () => {
                throw {status: 0};
            }),
        ).rejects.toBeDefined();
        const pending = readResultAction(17, 'EVALUATE');
        if (!pending || pending === 'UNAVAILABLE') throw Error('missing');
        await queryResultAction(pending, async () => ({
            evalReportId: 'current-report',
            backtestRunId: 'run-original',
        }));
        expect(readResultAction(17, 'EVALUATE')).toEqual(pending);
        expect(isResultActionInFlight(pending)).toBe(false);
    });
    it('损坏记录或不可用存储均阻止发送，不能作为没有待恢复请求', async () => {
        data.set('nq:result-action:v1:17:EVALUATE', '{broken');
        expect(readResultAction(17, 'EVALUATE')).toBe('UNAVAILABLE');
        const write = vi.fn();
        await expect(submitResultAction('EVALUATE', 'run-original', write)).rejects.toThrow();
        expect(write).not.toHaveBeenCalled();
        vi.stubGlobal('localStorage', {
            getItem: () => {
                throw Error('disabled');
            },
        });
        await expect(submitResultAction('PUBLISH', 'run-original', write)).rejects.toThrow();
        expect(write).not.toHaveBeenCalled();
    });
    it('只读角色或跨标签锁不可用时零写入', async () => {
        fixture.user.roles = ['VIEWER'];
        const write = vi.fn();
        await expect(submitResultAction('EVALUATE', 'run-original', write)).rejects.toThrow();
        fixture.user.roles = ['ADMIN'];
        vi.stubGlobal('navigator', {});
        await expect(submitResultAction('EVALUATE', 'run-original', write)).rejects.toThrow();
        expect(write).not.toHaveBeenCalled();
    });
});

it('评估mutation跨主体回调仅失效正式查询，不主动写入新主体详情缓存', () => {
    useEvaluateMutation();
    fixture.user.userId = 18;
    cache.mutation.onSuccess();
    expect(cache.setQueryData).not.toHaveBeenCalled();
    expect(cache.invalidateQueries).toHaveBeenCalled();
});

it.each(['EVALUATE', 'INSTRUMENT_SYNC'] as const)(
    '已登记后409不得清除%s原UUID或通过新UUID盲重发',
    async (kind) => {
        await expect(
            submitResultAction(kind, 'original', async () => {
                throw {status: 409};
            }),
        ).rejects.toBeDefined();
        const pending = readResultAction(17, kind);
        expect(pending).toMatchObject({target: 'original', requestId: expect.any(String)});
        const write = vi.fn();
        await expect(submitResultAction(kind, 'original', write)).rejects.toThrow();
        expect(write).not.toHaveBeenCalled();
    },
);

it('原结果必须是原UUID与原范围的正式终态，PENDING不能当作成功', () => {
    const requestId = 'fe888e37-6ad7-49f2-a4ea-f7a4860f44ee';
    const result = {evalReportId: 'eval-original', backtestRunId: 'run-original'};
    const receipt = {
        requestId,
        target: 'run-original',
        state: 'SUCCEEDED' as const,
        createdAt: '2026-10-17T00:00:00Z',
        completedAt: '2026-10-17T00:00:01Z',
        result,
    };
    expect(originalResult(receipt, requestId, 'run-original')).toEqual(result);
    for (const invalid of [
        {...receipt, state: 'PENDING' as const},
        {...receipt, requestId: 'another'},
        {...receipt, target: 'other'},
        {...receipt, completedAt: null},
    ])
        expect(() => originalResult(invalid, requestId, 'run-original')).toThrow();
});

it('复核响应丢失及查询404保留原身份，备注不进入恢复存储且新提交被阻止', async () => {
    const target = 'd1e904ec-6326-4b20-bd8c-41c77e28d97f:acknowledge:0';
    await expect(
        submitResultAction('VALIDATION_REVIEW', target, async () => {
            throw {status: 0};
        }),
    ).rejects.toBeDefined();
    const pending = readResultAction(17, 'VALIDATION_REVIEW');
    expect(pending).toMatchObject({owner: 17, target, requestId: expect.any(String)});
    if (!pending || pending === 'UNAVAILABLE') throw new Error('original identity missing');
    await expect(
        queryResultAction(pending, async () => {
            throw {status: 404};
        }),
    ).rejects.toBeDefined();
    expect(readResultAction(17, 'VALIDATION_REVIEW')).toEqual(pending);
    expect(Object.keys(pending).sort()).toEqual([
        'kind',
        'owner',
        'recordedAt',
        'requestId',
        'target',
    ]);
    const write = vi.fn();
    await expect(submitResultAction('VALIDATION_REVIEW', target, write)).rejects.toThrow();
    expect(write).not.toHaveBeenCalled();
});

it.each([400, 403, 404, 422, 409])(
    '复核原POST明确拒绝%s才释放身份，查询同状态不冒充拒绝',
    async (status) => {
        const target = 'd1e904ec-6326-4b20-bd8c-41c77e28d97f:acknowledge:0';
        await expect(
            submitResultAction('VALIDATION_REVIEW', target, async () => {
                throw {
                    status,
                    method: 'POST',
                    code: 'REVIEW_CASE_VERSION_CONFLICT',
                    path: '/api/validation-review-cases/d1e904ec-6326-4b20-bd8c-41c77e28d97f/acknowledge',
                };
            }),
        ).rejects.toBeDefined();
        expect(readResultAction(17, 'VALIDATION_REVIEW')).toBeNull();
        await expect(
            submitResultAction('VALIDATION_REVIEW', target, async () => {
                throw {
                    status,
                    method: 'GET',
                    code: 'REVIEW_CASE_VERSION_CONFLICT',
                    path: '/api/validation-review-cases/d1e904ec-6326-4b20-bd8c-41c77e28d97f/action-result',
                };
            }),
        ).rejects.toBeDefined();
        expect(readResultAction(17, 'VALIDATION_REVIEW')).toMatchObject({target});
    },
);

it('复核原事件必须匹配对象、操作者、动作及原版本，后续状态不是原结果', () => {
    const caseId = 'd1e904ec-6326-4b20-bd8c-41c77e28d97f';
    const target = `${caseId}:acknowledge:0`;
    const event = {
        id: 'f2b4dc59-9d05-4e31-856a-8200d86522e1',
        caseId,
        actorId: 17,
        eventType: 'ACKNOWLEDGED',
        fromState: 'OPEN',
        toState: 'ACKNOWLEDGED',
        caseVersion: 1,
        createdAt: '2026-10-18T00:00:00Z',
    } as const;
    expect(confirmOriginalReviewEvent(event, target, 17)).toEqual(event);
    for (const change of [
        {caseId: '38e8dbcc-cf0c-4fd1-b6cb-90f27f99a920'},
        {actorId: 18},
        {caseVersion: 2},
        {eventType: 'RESOLVED', toState: 'RESOLVED'},
        {createdAt: 'unknown'},
        {id: ''},
    ])
        expect(() => confirmOriginalReviewEvent({...event, ...change}, target, 17)).toThrow();
    for (const invalid of [
        `${caseId}:unknown:0`,
        `${caseId}:acknowledge:01`,
        `${caseId}:acknowledge:9007199254740992`,
    ])
        expect(() => parseReviewOperationTarget(invalid)).toThrow();
});

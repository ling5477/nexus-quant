import {beforeEach, afterEach, describe, it, expect, vi} from 'vitest';
import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import {paperTradingApi} from '@/features/paper-trading/api/paper-trading';
import {
    createRecoverably,
    pendingCreation,
    queryCreation,
    retryCreation,
    acknowledgeCreation,
    creationStillCurrent,
    creationResultMatches,
} from '@/features/research/creation-operations';
const storage = new Map<string, string>();
const user = {userId: 1, username: 'owner', roles: ['ADMIN'], authenticated: true};
beforeEach(() => {
    storage.clear();
    vi.restoreAllMocks();
    useAuthStore.setState({currentUser: user as never});
    vi.stubGlobal('localStorage', {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => storage.set(key, value),
        removeItem: (key: string) => storage.delete(key),
    });
    vi.stubGlobal('window', {dispatchEvent: vi.fn()});
    vi.stubGlobal('navigator', {
        locks: {request: async (_key: string, work: () => Promise<unknown>) => work()},
    });
});
afterEach(() => vi.unstubAllGlobals());
const input = {
    sourceStrategyId: 'str-original',
    name: 'Original',
    parameterSchema: '{}',
    parameterDefaults: '{}',
    datasetSpec: '{}',
};
const unknown = Object.assign(new Error('timeout'), {status: 0});
async function lost() {
    vi.spyOn(apiClient, 'post').mockRejectedValueOnce(unknown);
    await expect(createRecoverably('RESEARCH', input, vi.fn())).rejects.toMatchObject({
        code: 'RESEARCH_CREATION_UNKNOWN',
    });
    return pendingCreation('RESEARCH')!;
}
function receipt(original: ReturnType<typeof pendingCreation>) {
    return {...original, objectId: 'rcf-original', lookupKey: 'rcf-original'};
}
describe('research creation original identity', () => {
    it('历史Paper创建也冻结原输入，丢响应后先查询同一请求而不生成第二个运行', async () => {
        const paper = {
            publishId: 'synthetic-paper-publish',
            tradeEnv: 'SIM',
            exchangeCode: 'OKX',
            marketType: 'SPOT',
            symbol: 'BTC-USDT',
            intervalCode: '1h',
        };
        vi.spyOn(apiClient, 'post').mockRejectedValueOnce(unknown);
        await expect(paperTradingApi.create(paper)).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_UNKNOWN',
        });
        const original = pendingCreation('PAPER_RUN')!;
        expect(original.input).toEqual(paper);
        expect(apiClient.post).toHaveBeenCalledWith(
            `/research-creation-operations/${original.operationId}`,
            {kind: 'PAPER_RUN', parentKey: '', input: paper},
        );
        await expect(paperTradingApi.create({...paper, symbol: 'ETH-USDT'})).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_UNKNOWN',
        });
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {
                ...original,
                objectId: 'ptr-synthetic-original',
                lookupKey: 'ptr-synthetic-original',
            },
        });
        expect((await retryCreation(original)).objectId).toBe('ptr-synthetic-original');
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('isolated SIM creation persists its original budget and UUID before a lost response', async () => {
        vi.spyOn(apiClient, 'post').mockRejectedValueOnce(unknown);
        await expect(
            paperTradingApi.createStrategySim('pub-original', '100.00000001'),
        ).rejects.toMatchObject({code: 'RESEARCH_CREATION_UNKNOWN'});
        const original = pendingCreation('SIM_RUN')!;
        expect(original.input).toEqual({publishId: 'pub-original', budget: '100.00000001'});
        expect(apiClient.post).toHaveBeenCalledWith(
            `/research-creation-operations/${original.operationId}`,
            {kind: 'SIM_RUN', parentKey: '', input: original.input},
        );
        await expect(paperTradingApi.createStrategySim('pub-other', '1000')).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_UNKNOWN',
        });
        expect(apiClient.post).toHaveBeenCalledTimes(1);
        vi.spyOn(apiClient, 'get').mockResolvedValueOnce({data: receipt(original)});
        expect((await retryCreation(original)).operationId).toBe(original.operationId);
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('saves before POST and keeps the same input and identity after timeout', async () => {
        const original = await lost();
        expect(original.input).toEqual(input);
        expect(apiClient.post).toHaveBeenCalledWith(
            `/research-creation-operations/${original.operationId}`,
            expect.objectContaining({input}),
        );
        await expect(
            createRecoverably('RESEARCH', {...input, name: 'Replacement'}, vi.fn()),
        ).rejects.toMatchObject({code: 'RESEARCH_CREATION_UNKNOWN'});
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('queries the original result and does not repeat POST when it already exists', async () => {
        const original = await lost();
        vi.spyOn(apiClient, 'get').mockResolvedValue({data: receipt(original)});
        expect((await retryCreation(original)).objectId).toBe('rcf-original');
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('404 retry sends the saved identity and frozen input', async () => {
        const original = await lost();
        vi.spyOn(apiClient, 'get').mockRejectedValueOnce({status: 404});
        vi.mocked(apiClient.post).mockResolvedValueOnce({data: receipt(original)});
        await retryCreation(original);
        expect(apiClient.post).toHaveBeenLastCalledWith(
            `/research-creation-operations/${original.operationId}`,
            expect.objectContaining({input}),
        );
        expect(pendingCreation('RESEARCH')?.operationId).toBe(original.operationId);
    });
    it('does not retry POST when result query fails or is forbidden', async () => {
        const original = await lost();
        vi.spyOn(apiClient, 'get').mockRejectedValue({status: 403});
        await expect(retryCreation(original)).rejects.toMatchObject({status: 403});
        expect(apiClient.post).toHaveBeenCalledTimes(1);
    });
    it('rejects another object kind or operation in a returned receipt', async () => {
        const original = await lost();
        vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {...receipt(original), operationId: crypto.randomUUID()},
        });
        await expect(queryCreation(original)).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_UNKNOWN',
        });
    });
    it('keeps committed identity when canonical detail read fails with 404', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async () => ({
            data: receipt(pendingCreation('RESEARCH')),
        }));
        await expect(
            createRecoverably('RESEARCH', input, () => Promise.reject({status: 404})),
        ).rejects.toMatchObject({code: 'RESEARCH_CREATION_UNKNOWN'});
        expect(pendingCreation('RESEARCH')).not.toBeNull();
    });
    it('only clears definite no-write rejection, never 409 or 5xx', async () => {
        const post = vi.spyOn(apiClient, 'post').mockRejectedValueOnce({status: 400});
        await expect(createRecoverably('RESEARCH', input, vi.fn())).rejects.toMatchObject({
            status: 400,
        });
        expect(pendingCreation('RESEARCH')).toBeNull();
        post.mockRejectedValueOnce({status: 409});
        await expect(createRecoverably('RESEARCH', input, vi.fn())).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_UNKNOWN',
        });
        expect(pendingCreation('RESEARCH')).not.toBeNull();
    });
    it('retains successful identity across reread and requires explicit acknowledgement', async () => {
        vi.spyOn(apiClient, 'post').mockImplementation(async () => ({
            data: receipt(pendingCreation('RESEARCH')),
        }));
        expect(
            await createRecoverably('RESEARCH', input, async () => ({id: 'rcf-original'})),
        ).toEqual({id: 'rcf-original'});
        const original = pendingCreation('RESEARCH')!;
        expect(original).not.toBeNull();
        acknowledgeCreation(original);
        expect(pendingCreation('RESEARCH')).toBeNull();
    });
    it('separates stored requests by authenticated owner', async () => {
        const original = await lost();
        useAuthStore.setState({currentUser: {...user, userId: 2} as never});
        expect(pendingCreation('RESEARCH')).toBeNull();
        await expect(retryCreation(original)).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_UNKNOWN',
        });
    });
    it('never confirms a later UNKNOWN operation using an earlier tab result', async () => {
        const first = await lost();
        const proof = {operationId: first.operationId, ownerId: 1};
        expect(creationResultMatches(first, 1, proof)).toBe(true);
        acknowledgeCreation(first);
        vi.mocked(apiClient.post).mockRejectedValueOnce(unknown);
        await expect(createRecoverably('RESEARCH', input, vi.fn())).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_UNKNOWN',
        });
        const second = pendingCreation('RESEARCH')!;
        expect(creationStillCurrent(first, 1)).toBe(false);
        expect(creationResultMatches(second, 1, proof)).toBe(false);
        useAuthStore.setState({currentUser: {...user, userId: 2} as never});
        expect(
            creationResultMatches(second, 2, {operationId: second.operationId, ownerId: 1}),
        ).toBe(false);
    });
    it('does not POST under a new owner after waiting for the previous owner lock', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('navigator', {
            locks: {
                request: async (_key: string, work: () => Promise<unknown>) => {
                    useAuthStore.setState({currentUser: {...user, userId: 2} as never});
                    return work();
                },
            },
        });
        await expect(createRecoverably('RESEARCH', input, vi.fn())).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_SCOPE_CHANGED',
        });
        expect(post).not.toHaveBeenCalled();
        expect(pendingCreation('RESEARCH')).toBeNull();
    });
    it('storage failure prevents every POST', async () => {
        const post = vi.spyOn(apiClient, 'post');
        vi.stubGlobal('localStorage', {
            getItem: () => null,
            setItem: () => {
                throw new Error('disabled');
            },
        });
        await expect(createRecoverably('RESEARCH', input, vi.fn())).rejects.toMatchObject({
            code: 'RESEARCH_CREATION_STORAGE',
        });
        expect(post).not.toHaveBeenCalled();
    });
});

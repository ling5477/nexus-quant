import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {
    CreateMarketdataIngestionJobRequest,
    MarketdataIngestionJob,
    MarketdataIngestionRun,
} from '@/types/marketdata';

export type IngestionRequest =
    | {kind: 'CREATE'; jobId: string; input: CreateMarketdataIngestionJobRequest}
    | {kind: 'RUN'; jobId: string; runId: string};
export interface PendingIngestion {
    ownerUserId: number;
    ownerUsername: string;
    request: IngestionRequest;
    phase: 'SUBMITTING' | 'UNKNOWN' | 'CONFIRMED';
    recordedAt: number;
    result?: MarketdataIngestionJob | MarketdataIngestionRun;
}
export type IngestionStorage =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'saved'; pending: PendingIngestion};

export const ingestionChanged = 'nq:ingestion-operation-changed';
const inFlight = new Set<string>();
let missing: string | null = null;
const key = (owner: number) => `nq:ingestion-operation:v1:${owner}`;
export const validIngestionId = (value: unknown): value is string =>
    typeof value === 'string' &&
    /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(value) &&
    value !== '00000000-0000-0000-0000-000000000000';
const identity = (p: PendingIngestion) =>
    `${p.ownerUserId}:${p.request.kind}:${p.request.jobId}:${p.request.kind === 'RUN' ? p.request.runId : ''}`;
const problem = (code: string): AppApiError =>
    Object.assign(new Error(`INGESTION_${code}`), {
        status: 0,
        code: `INGESTION_${code}`,
        fieldErrors: [],
    });
const notify = () => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(ingestionChanged));
};
const fields = [
    'exchangeCode',
    'marketType',
    'symbol',
    'interval',
    'startTime',
    'endTime',
] as const;
const statuses = ['CREATED', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED'];
const date = (value: unknown): value is string =>
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value) &&
    Number.isFinite(Date.parse(value));

/** 只开放正式历史适配器已实现的品种；目录里存在不等于该采集接口支持。 */
export function ingestionSymbols(exchange: string): string[] {
    return exchange === 'BINANCE'
        ? ['BTC-USDT', 'ETH-USDT', 'SOL-USDT', 'BNB-USDT']
        : exchange === 'OKX'
          ? ['BTC-USDT', 'ETH-USDT', 'SOL-USDT']
          : [];
}

export function normalizeIngestionInput(
    value: CreateMarketdataIngestionJobRequest,
): CreateMarketdataIngestionJobRequest {
    if (
        !value ||
        !fields.every((field) => typeof value[field] === 'string') ||
        Object.keys(value).some((field) => !fields.includes(field as (typeof fields)[number]))
    )
        throw problem('INPUT_INVALID');
    const input = {
        exchangeCode: value.exchangeCode?.trim().toUpperCase(),
        marketType: value.marketType?.trim().toUpperCase(),
        symbol: value.symbol?.trim().toUpperCase(),
        interval: value.interval?.trim().toLowerCase(),
        startTime: value.startTime,
        endTime: value.endTime,
    };
    if (
        input.marketType !== 'SPOT' ||
        !ingestionSymbols(input.exchangeCode).includes(input.symbol) ||
        !['1m', '5m', '15m', '1h', '4h', '1d'].includes(input.interval) ||
        !date(input.startTime) ||
        !date(input.endTime) ||
        Date.parse(input.endTime) <= Date.parse(input.startTime)
    )
        throw problem('INPUT_INVALID');
    return {
        ...input,
        startTime: new Date(input.startTime).toISOString(),
        endTime: new Date(input.endTime).toISOString(),
    };
}

export function validateIngestionJob(
    value: MarketdataIngestionJob,
    id?: string,
): MarketdataIngestionJob {
    if (
        !value ||
        !validIngestionId(value.jobId) ||
        (id && value.jobId !== id) ||
        !statuses.includes(value.status) ||
        !date(value.startTime) ||
        !date(value.endTime) ||
        !date(value.createdAt) ||
        !date(value.updatedAt) ||
        !value.createdBy ||
        value.source !== 'EXCHANGE_HISTORICAL' ||
        !value.exchangeCode ||
        !value.symbol ||
        !value.marketType ||
        !value.interval
    )
        throw problem('RESPONSE_INVALID');
    return value;
}

export function validateIngestionRun(
    value: MarketdataIngestionRun,
    jobId: string,
    runId?: string,
): MarketdataIngestionRun {
    if (
        !value ||
        value.jobId !== jobId ||
        !validIngestionId(value.runId) ||
        (runId && value.runId !== runId) ||
        !statuses.includes(value.status) ||
        !date(value.startedAt) ||
        !date(value.requestedStartTime) ||
        !date(value.requestedEndTime) ||
        ![value.fetchedBars, value.insertedBars, value.updatedBars, value.skippedBars].every(
            (number) => Number.isSafeInteger(number) && number >= 0,
        ) ||
        (value.finishedAt != null && !date(value.finishedAt)) ||
        (value.actualStartTime != null && !date(value.actualStartTime)) ||
        (value.actualEndTime != null && !date(value.actualEndTime))
    )
        throw problem('RESPONSE_INVALID');
    return value;
}

/** 回执只确认同一个原请求；任务当前状态不冒充不可变的创建时状态。 */
export function confirmIngestion(
    pending: PendingIngestion,
    value: MarketdataIngestionJob | MarketdataIngestionRun,
) {
    const request = pending.request;
    if (request.kind === 'CREATE') {
        const result = validateIngestionJob(value as MarketdataIngestionJob, request.jobId);
        if (
            result.createdBy !== pending.ownerUsername ||
            !fields.every((field) =>
                field === 'startTime' || field === 'endTime'
                    ? Date.parse(result[field]) === Date.parse(request.input[field])
                    : result[field] === request.input[field],
            )
        )
            throw problem('RESPONSE_INVALID');
        return result;
    }
    const result = validateIngestionRun(
        value as MarketdataIngestionRun,
        request.jobId,
        request.runId,
    );
    if (!['SUCCEEDED', 'PARTIAL', 'FAILED'].includes(result.status) || !date(result.finishedAt))
        throw problem('RESPONSE_INVALID');
    return result;
}

/** 刷新只恢复原输入，不自动发写请求；损坏或不可读的存储不能被当成没有待确认请求。 */
export function readIngestion(owner?: number): IngestionStorage {
    if (!owner || !Number.isSafeInteger(owner) || owner <= 0) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 16384) return {kind: 'unavailable'};
        const pending = JSON.parse(raw) as PendingIngestion;
        const request = pending?.request;
        if (
            pending?.ownerUserId !== owner ||
            typeof pending.ownerUsername !== 'string' ||
            !pending.ownerUsername ||
            !request ||
            !validIngestionId(request.jobId) ||
            !['CREATE', 'RUN'].includes(request.kind) ||
            !['SUBMITTING', 'UNKNOWN', 'CONFIRMED'].includes(pending.phase) ||
            !Number.isSafeInteger(pending.recordedAt) ||
            pending.recordedAt <= 0
        )
            return {kind: 'unavailable'};
        if (request.kind === 'CREATE') {
            const normalized = normalizeIngestionInput(request.input);
            if (!fields.every((field) => normalized[field] === request.input[field]))
                return {kind: 'unavailable'};
        } else if (!validIngestionId(request.runId)) return {kind: 'unavailable'};
        if (pending.phase === 'CONFIRMED') confirmIngestion(pending, pending.result!);
        return {
            kind: 'saved',
            pending: {
                ...pending,
                phase:
                    pending.phase === 'CONFIRMED'
                        ? 'CONFIRMED'
                        : inFlight.has(identity(pending))
                          ? 'SUBMITTING'
                          : 'UNKNOWN',
            },
        };
    } catch {
        return {kind: 'unavailable'};
    }
}

function scope(pending?: PendingIngestion, write = true) {
    const user = useAuthStore.getState().currentUser;
    if (
        !user?.authenticated ||
        !Number.isSafeInteger(user.userId) ||
        user.userId <= 0 ||
        (write &&
            !user.roles.some((role) =>
                ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
            ))
    )
        throw problem('READ_ONLY');
    if (pending && (user.userId !== pending.ownerUserId || user.username !== pending.ownerUsername))
        throw problem('SCOPE_CHANGED');
    return user;
}
function original(pending: PendingIngestion) {
    scope(pending, false);
    const stored = readIngestion(pending.ownerUserId);
    if (
        stored.kind !== 'saved' ||
        identity(stored.pending) !== identity(pending) ||
        JSON.stringify(stored.pending.request) !== JSON.stringify(pending.request)
    )
        throw problem('STORAGE_UNAVAILABLE');
    return stored.pending;
}
function retain(pending: PendingIngestion) {
    scope(pending, false);
    try {
        const raw = JSON.stringify(pending);
        localStorage.setItem(key(pending.ownerUserId), raw);
        if (localStorage.getItem(key(pending.ownerUserId)) !== raw)
            throw new Error('storage readback mismatch');
        notify();
    } catch {
        throw problem('STORAGE_UNAVAILABLE');
    }
}
async function coordinated<T>(owner: number, work: () => Promise<T>) {
    if (typeof navigator === 'undefined' || !navigator.locks?.request)
        throw problem('COORDINATION_UNAVAILABLE');
    return navigator.locks.request(
        key(owner),
        {mode: 'exclusive', ifAvailable: true},
        async (lock) => {
            if (!lock) throw problem('PENDING');
            return work();
        },
    );
}
function finish(pending: PendingIngestion, value: MarketdataIngestionJob | MarketdataIngestionRun) {
    original(pending);
    const result = confirmIngestion(pending, value);
    retain({...pending, phase: 'CONFIRMED', result});
    return result;
}
const jobPath = (id: string) => `/marketdata/ingestion-jobs/${encodeURIComponent(id)}`;
const runPath = (jobId: string, runId: string) =>
    `${jobPath(jobId)}/runs/${encodeURIComponent(runId)}`;

async function execute(pending: PendingIngestion, recovery: boolean) {
    scope(pending);
    original(pending);
    inFlight.add(identity(pending));
    try {
        retain({...pending, phase: 'SUBMITTING'});
        const request = pending.request;
        const {data} =
            request.kind === 'CREATE'
                ? await apiClient.post(jobPath(request.jobId), request.input)
                : await apiClient.post(runPath(request.jobId, request.runId));
        return finish(pending, data);
    } catch (failure) {
        const status = (failure as AppApiError).status;
        // 恢复中的拒绝不能证明原写入没发生；初次明确校验/权限拒绝才释放编号，409始终保留。
        if (
            !recovery &&
            [400, 403, 422].includes(status) &&
            useAuthStore.getState().currentUser?.userId === pending.ownerUserId
        ) {
            original(pending);
            localStorage.removeItem(key(pending.ownerUserId));
        } else {
            try {
                retain({...pending, phase: 'UNKNOWN'});
            } catch {
                /* 原输入已保存，禁止换号。 */
            }
        }
        throw failure;
    } finally {
        inFlight.delete(identity(pending));
        notify();
    }
}

async function submit(request: (id: string) => IngestionRequest) {
    const user = scope();
    return coordinated(user.userId, async () => {
        if (readIngestion(user.userId).kind !== 'none') throw problem('PENDING');
        if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')
            throw problem('IDENTITY_UNAVAILABLE');
        if (scope().userId !== user.userId) throw problem('SCOPE_CHANGED');
        const id = crypto.randomUUID();
        if (!validIngestionId(id)) throw problem('IDENTITY_UNAVAILABLE');
        const pending: PendingIngestion = {
            ownerUserId: user.userId,
            ownerUsername: user.username,
            request: request(id),
            phase: 'SUBMITTING',
            recordedAt: Date.now(),
        };
        missing = null;
        retain(pending);
        return execute(pending, false);
    });
}

export const ingestionOperations = {
    create: (input: CreateMarketdataIngestionJobRequest) => {
        const normalized = normalizeIngestionInput(input);
        return submit((jobId) => ({kind: 'CREATE', jobId, input: normalized}));
    },
    run: (jobId: string) => {
        if (!validIngestionId(jobId)) return Promise.reject(problem('INPUT_INVALID'));
        return submit((runId) => ({kind: 'RUN', jobId, runId}));
    },
    lookup: (pending: PendingIngestion) =>
        coordinated(pending.ownerUserId, async () => {
            original(pending);
            missing = null;
            try {
                const request = pending.request;
                const {data} = await apiClient.get(
                    request.kind === 'CREATE'
                        ? `${jobPath(request.jobId)}/creation-result`
                        : runPath(request.jobId, request.runId),
                );
                return finish(pending, data);
            } catch (failure) {
                if (
                    (failure as AppApiError).status === 404 &&
                    useAuthStore.getState().currentUser?.userId === pending.ownerUserId
                )
                    missing = identity(pending);
                throw failure;
            }
        }),
    resume: (pending: PendingIngestion) =>
        coordinated(pending.ownerUserId, async () => {
            if (original(pending).phase === 'CONFIRMED' || missing !== identity(pending))
                throw problem('LOOKUP_REQUIRED');
            missing = null;
            return execute(pending, true);
        }),
    acknowledge: (pending: PendingIngestion) =>
        coordinated(pending.ownerUserId, async () => {
            if (original(pending).phase !== 'CONFIRMED') throw problem('LOOKUP_REQUIRED');
            localStorage.removeItem(key(pending.ownerUserId));
            if (localStorage.getItem(key(pending.ownerUserId)) !== null)
                throw problem('STORAGE_UNAVAILABLE');
            notify();
        }),
};

/** 读取有上限并携带取消信号；任务与运行窗口不能冒充全库总数。 */
export const ingestionReads = {
    async jobs(signal?: AbortSignal) {
        const {data} = await apiClient.get<MarketdataIngestionJob[]>('/marketdata/ingestion-jobs', {
            signal,
        });
        if (
            !Array.isArray(data) ||
            data.length > 100 ||
            new Set(data.map((row) => row?.jobId)).size !== data.length
        )
            throw problem('RESPONSE_INVALID');
        return data.map((row) => validateIngestionJob(row));
    },
    async job(id: string, signal?: AbortSignal) {
        const {data} = await apiClient.get<MarketdataIngestionJob>(jobPath(id), {signal});
        return validateIngestionJob(data, id);
    },
    async runs(id: string, signal?: AbortSignal) {
        const {data} = await apiClient.get<MarketdataIngestionRun[]>(`${jobPath(id)}/runs`, {
            signal,
        });
        if (
            !Array.isArray(data) ||
            data.length > 100 ||
            new Set(data.map((row) => row?.runId)).size !== data.length
        )
            throw problem('RESPONSE_INVALID');
        return data.map((row) => validateIngestionRun(row, id));
    },
    async run(jobId: string, runId: string, signal?: AbortSignal) {
        const {data} = await apiClient.get<MarketdataIngestionRun>(runPath(jobId, runId), {signal});
        return validateIngestionRun(data, jobId, runId);
    },
};

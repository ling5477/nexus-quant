import {marketdataApi} from '@/api/marketdata';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';

/** 默认名称只取当前完整选择；用户输入独立保留，目录缺项时不猜测交易对。 */
export function datasetNameForSelection(
    selection: {exchangeCode?: string; symbol?: string; interval?: string},
    suffix: string,
    manualName?: string,
): string {
    if (manualName !== undefined) return manualName;
    const {exchangeCode, symbol, interval} = selection;
    return exchangeCode && symbol && interval ? `${exchangeCode}-${symbol}-${interval}-${suffix}` : '';
}
import type {
    CreateMarketdataDatasetRequest,
    DatasetOperation,
    DatasetOperationRequest,
} from '@/types/marketdata';

export interface PendingDatasetOperation {
    ownerUserId: number;
    ownerUsername: string;
    request: DatasetOperationRequest;
    phase: 'SUBMITTING' | 'UNKNOWN' | 'CONFIRMED';
    recordedAt: number;
}
export type DatasetOperationStorage =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'saved'; pending: PendingDatasetOperation};
export const datasetOperationChanged = 'nq:dataset-operation-changed';
export const validDatasetOperationId = (id: unknown): id is string =>
    typeof id === 'string' &&
    /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/.test(id) &&
    id !== '00000000-0000-0000-0000-000000000000';
const key = (owner: number) => `nq:dataset-operation:v1:${owner}`;
const inFlight = new Set<string>();
let proof: {owner: number; id: string; token: string; kind: 'TERMINAL' | 'CONTINUE'} | null = null;
const problem = (code: string): AppApiError =>
    Object.assign(new Error('DATASET_OPERATION_' + code), {
        status: 0,
        code: 'DATASET_OPERATION_' + code,
        fieldErrors: [],
    });
const object = (v: unknown): v is Record<string, unknown> =>
    Boolean(v && typeof v === 'object' && !Array.isArray(v));
const text = (v: unknown, limit = 512): v is string =>
    typeof v === 'string' &&
    Boolean(v.trim()) &&
    v.length <= limit &&
    !/[\u0000-\u001f\u007f-\u009f]/.test(v);
const count = (v: unknown) => Number.isSafeInteger(v) && Number(v) >= 0;
const notify = () => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(datasetOperationChanged));
};

/** 保留微秒并验证原始日历，不能用毫秒级Date.parse把不同原输入合并；非零亚微秒不能在PG往返。 */
export function datasetInstant(value: unknown): {value: string; micros: bigint} {
    if (typeof value !== 'string') throw problem('INPUT_INVALID');
    const m =
        /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|([+-])(\d{2}):(\d{2}))$/.exec(
            value,
        );
    if (!m || /[1-9]/.test((m[2] ?? '').slice(6))) throw problem('INPUT_INVALID');
    const local = new Date(m[1] + '.000Z');
    const hour = Number(m[5] ?? 0),
        minute = Number(m[6] ?? 0);
    const base = Date.parse(m[1] + m[3]);
    if (
        !Number.isFinite(base) ||
        !Number.isFinite(local.getTime()) ||
        local.toISOString().slice(0, 19) !== m[1] ||
        hour > 18 ||
        minute > 59 ||
        (hour === 18 && minute !== 0)
    )
        throw problem('INPUT_INVALID');
    const fraction = Number((m[2] ?? '').padEnd(6, '0').slice(0, 6));
    const utc = new Date(base).toISOString().slice(0, 19);
    if (!/^\d{4}-/.test(utc)) throw problem('INPUT_INVALID');
    const digits =
        fraction === 0
            ? ''
            : '.' +
              (fraction % 1000 === 0
                  ? String(fraction / 1000).padStart(3, '0')
                  : String(fraction).padStart(6, '0'));
    return {value: utc + digits + 'Z', micros: BigInt(base) * 1000n + BigInt(fraction)};
}
const instant = (value: unknown) => {
    try {
        return datasetInstant(value);
    } catch {
        throw problem('RESPONSE_INVALID');
    }
};

export function normalizeDatasetInput(
    raw: CreateMarketdataDatasetRequest,
): CreateMarketdataDatasetRequest {
    const fields = [
        'datasetName',
        'exchangeCode',
        'marketType',
        'symbol',
        'interval',
        'startTime',
        'endTime',
    ];
    if (
        !object(raw) ||
        Object.keys(raw).length !== fields.length ||
        fields.some((field) => !text(raw[field]))
    )
        throw problem('INPUT_INVALID');
    const datasetName = raw.datasetName.trim(),
        exchangeCode = raw.exchangeCode.trim().toUpperCase(),
        marketType = raw.marketType.trim().toUpperCase(),
        symbol = raw.symbol.trim().toUpperCase(),
        interval = raw.interval.trim();
    const start = datasetInstant(raw.startTime),
        end = datasetInstant(raw.endTime);
    if (
        datasetName.length > 255 ||
        !['BINANCE', 'OKX'].includes(exchangeCode) ||
        marketType !== 'SPOT' ||
        !['BTC-USDT', 'ETH-USDT', 'SOL-USDT', 'BNB-USDT'].includes(symbol) ||
        !['1m', '5m', '15m', '1h', '4h', '1d'].includes(interval) ||
        start.micros >= end.micros
    )
        throw problem('INPUT_INVALID');
    return {
        datasetName,
        exchangeCode,
        marketType,
        symbol,
        interval,
        startTime: start.value,
        endTime: end.value,
    };
}
function normalizeRequest(raw: DatasetOperationRequest): DatasetOperationRequest {
    if (
        !object(raw) ||
        !validDatasetOperationId(raw.operationId) ||
        !validDatasetOperationId(raw.datasetId)
    )
        throw problem('INPUT_INVALID');
    if (raw.kind === 'CREATE' && raw.operationId === raw.datasetId)
        return {
            kind: 'CREATE',
            operationId: raw.operationId,
            datasetId: raw.datasetId,
            input: normalizeDatasetInput(raw.input),
        };
    if (raw.kind === 'REFRESH' && object(raw.input) && Object.keys(raw.input).length === 0)
        return {kind: 'REFRESH', operationId: raw.operationId, datasetId: raw.datasetId, input: {}};
    throw problem('INPUT_INVALID');
}
function jsonObject(raw: unknown) {
    if (typeof raw !== 'string' || raw.length > 262144) throw problem('RESPONSE_INVALID');
    try {
        const parsed: unknown = JSON.parse(raw);
        if (object(parsed)) return parsed;
    } catch {
        /* 下方统一拒绝不完整回执。 */
    }
    throw problem('RESPONSE_INVALID');
}

/** 成功只表示本次计算已提交；回执绑定原次覆盖，不能用数据集最新详情替代，也不代表质量合格。 */
export function validateDatasetOperation(
    value: unknown,
    id: string,
    original?: DatasetOperationRequest,
): DatasetOperation {
    const r = value as DatasetOperation;
    if (
        !object(r) ||
        r.operationId !== id ||
        !validDatasetOperationId(id) ||
        !['PENDING', 'SUCCEEDED', 'FAILED'].includes(r.state)
    )
        throw problem('RESPONSE_INVALID');
    let request: DatasetOperationRequest;
    try {
        request = normalizeRequest({
            kind: r.kind,
            operationId: r.operationId,
            datasetId: r.datasetId,
            input: r.input,
        } as DatasetOperationRequest);
    } catch {
        throw problem('RESPONSE_INVALID');
    }
    if (original && JSON.stringify(request) !== JSON.stringify(normalizeRequest(original)))
        throw problem('RESPONSE_INVALID');
    const created = instant(r.createdAt);
    if (r.state === 'PENDING') {
        if (r.completedAt !== null || r.result !== null || r.failureCode !== null)
            throw problem('RESPONSE_INVALID');
        return r;
    }
    const finished = instant(r.completedAt);
    if (finished.micros < created.micros) throw problem('RESPONSE_INVALID');
    if (r.state === 'FAILED') {
        if (
            r.result !== null ||
            ![
                'DATASET_ID_OCCUPIED',
                'DATASET_NOT_FOUND',
                'PUBLIC_CAPTURE_DATASET_IMMUTABLE',
            ].includes(r.failureCode ?? '') ||
            (request.kind === 'CREATE'
                ? r.failureCode !== 'DATASET_ID_OCCUPIED'
                : r.failureCode === 'DATASET_ID_OCCUPIED')
        )
            throw problem('RESPONSE_INVALID');
        return r;
    }
    if (
        !object(r.result) ||
        !object(r.result.dataset) ||
        !object(r.result.coverage) ||
        r.failureCode !== null
    )
        throw problem('RESPONSE_INVALID');
    const d = r.result.dataset,
        c = r.result.coverage;
    if (
        d.datasetId !== request.datasetId ||
        c.datasetId !== request.datasetId ||
        c.coverageId !== id ||
        !text(d.datasetName, 255) ||
        !text(d.createdBy) ||
        !text(d.exchangeCode) ||
        !text(d.marketType) ||
        !text(d.symbol) ||
        !text(d.interval) ||
        d.source !== 'marketdata_bars' ||
        !['READY', 'INVALID'].includes(d.status) ||
        !['OK', 'GAP_DETECTED', 'INCOMPLETE', 'INVALID'].includes(d.qualityStatus) ||
        d.qualityStatus !== c.qualityStatus ||
        (d.qualityStatus === 'OK' ? d.status !== 'READY' : d.status !== 'INVALID') ||
        ![
            d.barCount,
            d.gapCount,
            c.expectedBars,
            c.actualBars,
            c.missingBars,
            c.duplicateBars,
            c.invalidBars,
        ].every(count) ||
        d.barCount !== c.actualBars ||
        d.gapCount !== c.missingBars ||
        c.missingBars !== Math.max(0, c.expectedBars - c.actualBars) ||
        c.invalidBars > c.actualBars
    )
        throw problem('RESPONSE_INVALID');
    const start = instant(d.startTime),
        end = instant(d.endTime),
        updated = instant(d.updatedAt);
    if (
        start.micros >= end.micros ||
        instant(c.rangeStartTime).micros !== start.micros ||
        instant(c.rangeEndTime).micros !== end.micros ||
        instant(c.createdAt).micros !== updated.micros ||
        instant(d.createdAt).micros > updated.micros ||
        updated.micros > finished.micros
    )
        throw problem('RESPONSE_INVALID');
    jsonObject(c.summaryJson);
    const recordedInput = jsonObject(d.requestJson);
    if (request.kind === 'CREATE') {
        let snapshot: CreateMarketdataDatasetRequest;
        try {
            snapshot = normalizeDatasetInput(
                recordedInput as unknown as CreateMarketdataDatasetRequest,
            );
        } catch {
            throw problem('RESPONSE_INVALID');
        }
        if (
            JSON.stringify(snapshot) !== JSON.stringify(request.input) ||
            ['datasetName', 'exchangeCode', 'marketType', 'symbol', 'interval'].some(
                (field) =>
                    d[field as keyof typeof d] !==
                    request.input[field as keyof CreateMarketdataDatasetRequest],
            ) ||
            start.micros !== instant(request.input.startTime).micros ||
            end.micros !== instant(request.input.endTime).micros
        )
            throw problem('RESPONSE_INVALID');
    }
    return r;
}

export function readDatasetOperation(owner?: number): DatasetOperationStorage {
    if (!owner || !Number.isSafeInteger(owner) || owner <= 0) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 8192) return {kind: 'unavailable'};
        const p = JSON.parse(raw) as PendingDatasetOperation;
        if (
            !object(p) ||
            p.ownerUserId !== owner ||
            !text(p.ownerUsername, 128) ||
            !['SUBMITTING', 'UNKNOWN', 'CONFIRMED'].includes(p.phase) ||
            !Number.isSafeInteger(p.recordedAt) ||
            p.recordedAt <= 0
        )
            return {kind: 'unavailable'};
        const request = normalizeRequest(p.request);
        if (JSON.stringify(request) !== JSON.stringify(p.request)) return {kind: 'unavailable'};
        return {
            kind: 'saved',
            pending: {
                ...p,
                request,
                phase:
                    p.phase === 'CONFIRMED'
                        ? 'CONFIRMED'
                        : inFlight.has(request.operationId)
                          ? 'SUBMITTING'
                          : 'UNKNOWN',
            },
        };
    } catch {
        return {kind: 'unavailable'};
    }
}
function session(write = false) {
    const {currentUser: user, accessToken: token} = useAuthStore.getState();
    if (
        !user?.authenticated ||
        !token ||
        !Number.isSafeInteger(user.userId) ||
        user.userId <= 0 ||
        user.mustChangePassword
    )
        throw problem('SCOPE_CHANGED');
    if (
        write &&
        !user.roles.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        )
    )
        throw problem('READ_ONLY');
    return {user, token};
}
function sameSession(owner: number, token: string) {
    const current = session();
    if (current.user.userId !== owner || current.token !== token) throw problem('SCOPE_CHANGED');
    return current;
}
function original(p: PendingDatasetOperation) {
    const saved = readDatasetOperation(p.ownerUserId);
    if (
        session().user.userId !== p.ownerUserId ||
        saved.kind !== 'saved' ||
        JSON.stringify(saved.pending.request) !== JSON.stringify(p.request)
    )
        throw problem('STORAGE_UNAVAILABLE');
    return saved.pending;
}
function retain(p: PendingDatasetOperation) {
    if (session().user.userId !== p.ownerUserId) throw problem('SCOPE_CHANGED');
    try {
        const raw = JSON.stringify(p);
        localStorage.setItem(key(p.ownerUserId), raw);
        if (localStorage.getItem(key(p.ownerUserId)) !== raw) throw problem('STORAGE_UNAVAILABLE');
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
function remember(result: DatasetOperation, owner: number, token: string) {
    sameSession(owner, token);
    const saved = readDatasetOperation(owner);
    if (saved.kind === 'saved' && saved.pending.request.operationId === result.operationId) {
        validateDatasetOperation(result, result.operationId, saved.pending.request);
        proof = {
            owner,
            token,
            id: result.operationId,
            kind: result.state === 'PENDING' ? 'CONTINUE' : 'TERMINAL',
        };
    }
}
export const datasetOperationReads = {
    async one(id: string, signal?: AbortSignal): Promise<DatasetOperation> {
        if (!validDatasetOperationId(id)) throw problem('INPUT_INVALID');
        const {user, token} = session();
        proof = null;
        try {
            const data = await marketdataApi.getDatasetOperation(id, signal);
            sameSession(user.userId, token);
            const result = validateDatasetOperation(data, id);
            remember(result, user.userId, token);
            return result;
        } catch (error) {
            const saved = readDatasetOperation(user.userId);
            if (
                (error as AppApiError).status === 404 &&
                saved.kind === 'saved' &&
                saved.pending.request.operationId === id
            ) {
                sameSession(user.userId, token);
                proof = {owner: user.userId, token, id, kind: 'CONTINUE'};
            }
            throw error;
        }
    },
};
async function execute(p: PendingDatasetOperation): Promise<DatasetOperation> {
    const {user, token} = session(true);
    original(p);
    inFlight.add(p.request.operationId);
    proof = null;
    try {
        retain({...p, phase: 'SUBMITTING'});
        const data = await marketdataApi.submitDatasetOperation(p.request);
        sameSession(user.userId, token);
        original(p);
        const result = validateDatasetOperation(data, p.request.operationId, p.request);
        remember(result, user.userId, token);
        try {
            retain({...p, phase: result.state === 'PENDING' ? 'UNKNOWN' : 'CONFIRMED'});
        } catch {
            /* 首次保存的原请求仍保留，正式回执可重新查询。 */
        }
        return result;
    } catch (error) {
        try {
            original(p);
            retain({...p, phase: 'UNKNOWN'});
        } catch {
            /* 不能因换用户或存储故障删除原编号。 */
        }
        throw error;
    } finally {
        inFlight.delete(p.request.operationId);
        notify();
    }
}
async function prepare(
    kind: 'CREATE' | 'REFRESH',
    input: CreateMarketdataDatasetRequest | null,
    datasetId: string | null,
    prepared?: (id: string) => void,
) {
    const {user, token} = session(true);
    const normalized = kind === 'CREATE' ? normalizeDatasetInput(input!) : {};
    if (kind === 'REFRESH' && !validDatasetOperationId(datasetId)) throw problem('INPUT_INVALID');
    return coordinated(user.userId, async () => {
        sameSession(user.userId, token);
        const saved = readDatasetOperation(user.userId);
        if (saved.kind === 'unavailable') throw problem('STORAGE_UNAVAILABLE');
        if (saved.kind !== 'none') throw problem('PENDING');
        if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')
            throw problem('IDENTITY_UNAVAILABLE');
        const id = crypto.randomUUID();
        if (!validDatasetOperationId(id)) throw problem('IDENTITY_UNAVAILABLE');
        const request = normalizeRequest({
            kind,
            operationId: id,
            datasetId: datasetId ?? id,
            input: normalized,
        } as DatasetOperationRequest);
        const p: PendingDatasetOperation = {
            ownerUserId: user.userId,
            ownerUsername: user.username,
            request,
            phase: 'SUBMITTING',
            recordedAt: Date.now(),
        };
        retain(p);
        prepared?.(id);
        return execute(p);
    });
}
function confirmed(owner: number, token: string, kind: 'CONTINUE' | 'TERMINAL') {
    const saved = readDatasetOperation(owner);
    if (
        saved.kind !== 'saved' ||
        !proof ||
        proof.owner !== owner ||
        proof.token !== token ||
        proof.id !== saved.pending.request.operationId ||
        proof.kind !== kind
    )
        throw problem('LOOKUP_REQUIRED');
    return original(saved.pending);
}
export const datasetOperations = {
    create: (input: CreateMarketdataDatasetRequest, prepared?: (id: string) => void) =>
        prepare('CREATE', input, null, prepared),
    refresh: (datasetId: string, prepared?: (id: string) => void) =>
        prepare('REFRESH', null, datasetId, prepared),
    async resume() {
        const {user, token} = session(true);
        return coordinated(user.userId, async () => {
            sameSession(user.userId, token);
            return execute(confirmed(user.userId, token, 'CONTINUE'));
        });
    },
    async acknowledge() {
        const {user, token} = session();
        return coordinated(user.userId, async () => {
            sameSession(user.userId, token);
            confirmed(user.userId, token, 'TERMINAL');
            try {
                localStorage.removeItem(key(user.userId));
                if (localStorage.getItem(key(user.userId)) !== null)
                    throw problem('STORAGE_UNAVAILABLE');
            } catch {
                throw problem('STORAGE_UNAVAILABLE');
            }
            proof = null;
            notify();
        });
    },
};

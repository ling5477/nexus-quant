import {apiClient} from '@/api/client';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {PublicCaptureOperation} from '@/types/marketdata';

export interface CaptureInput {
    start: string;
    end: string;
}
export interface PendingCapture {
    ownerUserId: number;
    ownerUsername: string;
    captureId: string;
    input: CaptureInput;
    phase: 'SUBMITTING' | 'UNKNOWN' | 'CONFIRMED';
    recordedAt: number;
}
export type CaptureStorage =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'saved'; pending: PendingCapture};
export const captureChanged = 'nq:public-capture-operation-changed';
export const validCaptureId = (id: unknown): id is string =>
    typeof id === 'string' &&
    /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(id) &&
    id !== '00000000-0000-0000-0000-000000000000';
const key = (owner: number) => `nq:public-capture:v1:${owner}`;
const inFlight = new Set<string>();
let proof: {owner: number; id: string; token: string; kind: 'TERMINAL' | 'CONTINUE'} | null = null;
const problem = (code: string): AppApiError =>
    Object.assign(new Error('PUBLIC_CAPTURE_' + code), {
        status: 0,
        code: 'PUBLIC_CAPTURE_' + code,
        fieldErrors: [],
    });
const notify = () => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(captureChanged));
};
/** 校验原始日历字段，避免浏览器把不存在的日期自动顺延后当成用户原输入。 */
function date(value: unknown): value is string {
    if (typeof value !== 'string') return false;
    const parts = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.exec(
        value,
    );
    if (!parts || !Number.isFinite(Date.parse(value))) return false;
    const local = new Date(parts[1] + '.000Z');
    return Number.isFinite(local.getTime()) && local.toISOString().slice(0, 19) === parts[1];
}

/** 捕获沿用UTC整小时、1—100小时及已关闭窗口约束；恢复原输入时不重新用本机当前时间判废历史请求。 */
export function normalizeCaptureInput(value: CaptureInput, closed = true): CaptureInput {
    if (
        !value ||
        Object.keys(value).some((field) => !['start', 'end'].includes(field)) ||
        !date(value.start) ||
        !date(value.end) ||
        [value.start, value.end].some((time) => /[1-9]/.test(time.match(/\.(\d+)/)?.[1] ?? ''))
    )
        throw problem('INPUT_INVALID');
    const start = Date.parse(value.start),
        end = Date.parse(value.end);
    if (
        start % 3600000 !== 0 ||
        end % 3600000 !== 0 ||
        end - start < 3600000 ||
        end - start > 100 * 3600000 ||
        (closed && end > Date.now() - 3600000)
    )
        throw problem('INPUT_INVALID');
    return {start: new Date(start).toISOString(), end: new Date(end).toISOString()};
}

/** 只接受与原编号、原窗口相符的完整正式回执；本地记录不能提供结果权威。 */
export function validateCaptureResult(
    value: unknown,
    id: string,
    input?: CaptureInput,
): PublicCaptureOperation {
    const result = value as PublicCaptureOperation;
    if (
        !result ||
        result.captureId !== id ||
        !validCaptureId(id) ||
        !['PENDING', 'SUCCEEDED', 'FAILED'].includes(result.state) ||
        !date(result.createdAt)
    )
        throw problem('RESPONSE_INVALID');
    let window: CaptureInput;
    try {
        window = normalizeCaptureInput({start: result.start, end: result.end}, false);
    } catch {
        throw problem('RESPONSE_INVALID');
    }
    if (
        input &&
        (Date.parse(input.start) !== Date.parse(window.start) ||
            Date.parse(input.end) !== Date.parse(window.end))
    )
        throw problem('RESPONSE_INVALID');
    if (result.state === 'PENDING') {
        if (result.result !== null || result.failureCode !== null || result.completedAt !== null)
            throw problem('RESPONSE_INVALID');
    } else if (!date(result.completedAt)) throw problem('RESPONSE_INVALID');
    if (
        result.state === 'FAILED' &&
        (result.result !== null ||
            typeof result.failureCode !== 'string' ||
            !/^PUBLIC_[A-Z0-9_]{1,73}$/.test(result.failureCode))
    )
        throw problem('RESPONSE_INVALID');
    if (result.state === 'SUCCEEDED') {
        const capture = result.result;
        if (
            !capture ||
            result.failureCode !== null ||
            capture.datasetId !== id ||
            capture.venue !== 'OKX' ||
            capture.marketType !== 'SPOT' ||
            capture.instrument !== 'BTC-USDT' ||
            capture.interval !== '1h' ||
            !date(capture.start) ||
            !date(capture.end) ||
            Date.parse(capture.start) !== Date.parse(window.start) ||
            Date.parse(capture.end) !== Date.parse(window.end) ||
            !Number.isSafeInteger(capture.barCount) ||
            capture.barCount !== (Date.parse(window.end) - Date.parse(window.start)) / 3600000 ||
            !date(capture.observedAt) ||
            !date(capture.ruleObservedAt) ||
            ![
                'rawSha256',
                'normalizedSha256',
                'consumedSha256',
                'ruleRawSha256',
                'ruleSha256',
            ].every((field) =>
                /^[a-f0-9]{64}$/.test(capture[field as keyof typeof capture] as string),
            ) ||
            capture.replayVisibilityVersion !== 'CLOSED_HOURLY_BOUNDARY_V1' ||
            capture.replayVisibilitySource !== 'EXPERIMENT_ASSUMPTION' ||
            capture.rulePolicy !== 'CURRENTLY_OBSERVED_PUBLIC_RULES'
        )
            throw problem('RESPONSE_INVALID');
    }
    return result;
}

/** 刷新只恢复原编号；读不到或损坏的存储不能当作没有待确认请求。 */
export function readCapture(owner?: number): CaptureStorage {
    if (!owner || !Number.isSafeInteger(owner) || owner <= 0) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(key(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 4096) return {kind: 'unavailable'};
        const pending = JSON.parse(raw) as PendingCapture;
        if (
            !pending ||
            pending.ownerUserId !== owner ||
            typeof pending.ownerUsername !== 'string' ||
            !pending.ownerUsername ||
            !validCaptureId(pending.captureId) ||
            !['SUBMITTING', 'UNKNOWN', 'CONFIRMED'].includes(pending.phase) ||
            !Number.isSafeInteger(pending.recordedAt) ||
            pending.recordedAt <= 0
        )
            return {kind: 'unavailable'};
        const input = normalizeCaptureInput(pending.input, false);
        if (input.start !== pending.input.start || input.end !== pending.input.end)
            return {kind: 'unavailable'};
        return {
            kind: 'saved',
            pending: {
                ...pending,
                phase:
                    pending.phase === 'CONFIRMED'
                        ? 'CONFIRMED'
                        : inFlight.has(pending.captureId)
                          ? 'SUBMITTING'
                          : 'UNKNOWN',
            },
        };
    } catch {
        return {kind: 'unavailable'};
    }
}

function session(write = false) {
    const authState = useAuthStore.getState();
    const user = authState.currentUser;
    const token = selectSessionIdentity(authState);
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
function original(pending: PendingCapture) {
    const {user} = session();
    const saved = readCapture(pending.ownerUserId);
    if (
        user.userId !== pending.ownerUserId ||
        saved.kind !== 'saved' ||
        saved.pending.captureId !== pending.captureId ||
        JSON.stringify(saved.pending.input) !== JSON.stringify(pending.input)
    )
        throw problem('STORAGE_UNAVAILABLE');
    return saved.pending;
}
function retain(pending: PendingCapture) {
    if (session().user.userId !== pending.ownerUserId) throw problem('SCOPE_CHANGED');
    try {
        const raw = JSON.stringify(pending);
        localStorage.setItem(key(pending.ownerUserId), raw);
        if (localStorage.getItem(key(pending.ownerUserId)) !== raw)
            throw problem('STORAGE_UNAVAILABLE');
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
const path = (id: string) => '/marketdata/public-captures/' + encodeURIComponent(id);
function remember(result: PublicCaptureOperation, owner: number, token: string) {
    sameSession(owner, token);
    const saved = readCapture(owner);
    if (saved.kind === 'saved' && saved.pending.captureId === result.captureId) {
        validateCaptureResult(result, result.captureId, saved.pending.input);
        proof = {
            owner,
            id: result.captureId,
            token,
            kind: result.state === 'PENDING' ? 'CONTINUE' : 'TERMINAL',
        };
    }
}

export const captureReads = {
    async one(id: string, signal?: AbortSignal): Promise<PublicCaptureOperation> {
        if (!validCaptureId(id)) throw problem('INPUT_INVALID');
        const {user, token} = session();
        proof = null;
        try {
            const {data} = await apiClient.get(path(id), {signal});
            sameSession(user.userId, token);
            const result = validateCaptureResult(data, id);
            remember(result, user.userId, token);
            return result;
        } catch (failure) {
            const saved = readCapture(user.userId);
            if (
                (failure as AppApiError).status === 404 &&
                saved.kind === 'saved' &&
                saved.pending.captureId === id
            ) {
                sameSession(user.userId, token);
                proof = {owner: user.userId, token, id, kind: 'CONTINUE'};
            }
            throw failure;
        }
    },
};

async function execute(pending: PendingCapture): Promise<PublicCaptureOperation> {
    const {user, token} = session(true);
    original(pending);
    inFlight.add(pending.captureId);
    proof = null;
    try {
        retain({...pending, phase: 'SUBMITTING'});
        const {data} = await apiClient.post(path(pending.captureId), pending.input);
        sameSession(user.userId, token);
        original(pending);
        const result = validateCaptureResult(data, pending.captureId, pending.input);
        remember(result, user.userId, token);
        // 原结果以服务端为准；本地只保存阶段，不把大块回执写入存储而丢失原请求身份。
        try {
            retain({...pending, phase: result.state === 'PENDING' ? 'UNKNOWN' : 'CONFIRMED'});
        } catch {
            /* 原编号已保存，原结果仍可查询和确认。 */
        }
        return result;
    } catch (failure) {
        try {
            original(pending);
            retain({...pending, phase: 'UNKNOWN'});
        } catch {
            /* 换用户或存储失败仍不删除旧原编号。 */
        }
        throw failure;
    } finally {
        inFlight.delete(pending.captureId);
        notify();
    }
}

export const captureOperations = {
    async create(value: CaptureInput, onPrepared?: (id: string) => void) {
        const {user, token} = session(true);
        const input = normalizeCaptureInput(value);
        return coordinated(user.userId, async () => {
            sameSession(user.userId, token);
            const saved = readCapture(user.userId);
            if (saved.kind === 'unavailable') throw problem('STORAGE_UNAVAILABLE');
            if (saved.kind !== 'none') throw problem('PENDING');
            if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')
                throw problem('IDENTITY_UNAVAILABLE');
            const id = crypto.randomUUID();
            if (!validCaptureId(id)) throw problem('IDENTITY_UNAVAILABLE');
            const pending: PendingCapture = {
                ownerUserId: user.userId,
                ownerUsername: user.username,
                captureId: id,
                input,
                phase: 'SUBMITTING',
                recordedAt: Date.now(),
            };
            retain(pending);
            onPrepared?.(id);
            return execute(pending);
        });
    },
    async resume() {
        const {user, token} = session(true);
        return coordinated(user.userId, async () => {
            sameSession(user.userId, token);
            const saved = readCapture(user.userId);
            if (
                saved.kind !== 'saved' ||
                !proof ||
                proof.owner !== user.userId ||
                proof.token !== token ||
                proof.id !== saved.pending.captureId ||
                proof.kind !== 'CONTINUE'
            )
                throw problem('LOOKUP_REQUIRED');
            return execute(original(saved.pending));
        });
    },
    async acknowledge() {
        const {user, token} = session();
        return coordinated(user.userId, async () => {
            sameSession(user.userId, token);
            const saved = readCapture(user.userId);
            if (
                saved.kind !== 'saved' ||
                !proof ||
                proof.owner !== user.userId ||
                proof.token !== token ||
                proof.id !== saved.pending.captureId ||
                proof.kind !== 'TERMINAL'
            )
                throw problem('LOOKUP_REQUIRED');
            original(saved.pending);
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

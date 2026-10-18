import {apiClient} from '@/api/client';
import {useAuthStore} from '@/store/auth-store';
import {notificationInstant} from '@/features/notifications/read-state';
import type {AppApiError} from '@/types/api';
import type {PaperRunAlertItem} from './types/paper-trading';

export type PaperAlertCommand =
    | {
          type: 'CREATE';
          alertType: string;
          severity: string;
          title: string;
          message?: string | null;
          source?: string | null;
          eventSnapshotJson?: string | null;
      }
    | {type: 'ACK' | 'RESOLVE'; alertId: string};
export interface PendingPaperAlertOperation {
    owner: number;
    operationId: string;
    paperRunId: string;
    command: PaperAlertCommand;
    recordedAt: number;
    phase: 'SUBMITTING' | 'UNKNOWN';
}
export interface PaperAlertReceipt {
    operationId: string;
    paperRunId: string;
    type: PaperAlertCommand['type'];
    status: 'COMPLETED';
    inputSha256: string;
    result: PaperRunAlertItem;
    completedAt: string;
}
export type PaperAlertStorage =
    | {kind: 'none'}
    | {kind: 'unavailable'}
    | {kind: 'pending'; pending: PendingPaperAlertOperation};
export const paperAlertOperationChanged = 'nq:paper-alert-operation-changed';
const storageKey = (owner: number) => `nq:paper-alert-operation:v1:${owner}`;
const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/;
const identifier = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;
const inFlight = new Set<string>();
let missing: {token: string; original: string} | null = null;
const failure = (code: string): AppApiError =>
    Object.assign(new Error(code), {name: 'AppApiError', status: 0, code, fieldErrors: []});
const original = (pending: PendingPaperAlertOperation) =>
    JSON.stringify([
        pending.owner,
        pending.operationId,
        pending.paperRunId,
        pending.command,
        pending.recordedAt,
    ]);
const notify = () => window.dispatchEvent(new Event(paperAlertOperationChanged));

export function canOperatePaperAlerts(): boolean {
    const user = useAuthStore.getState().currentUser;
    return Boolean(
        user?.authenticated && user.roles.some((role) => ['ADMIN', 'OPERATOR'].includes(role)),
    );
}
function session(owner?: number) {
    const auth = useAuthStore.getState();
    if (
        !auth.currentUser?.authenticated ||
        !auth.accessToken ||
        !Number.isSafeInteger(auth.currentUser.userId) ||
        auth.currentUser.userId <= 0 ||
        (owner !== undefined && auth.currentUser.userId !== owner)
    )
        throw failure('PAPER_ALERT_SESSION_CHANGED');
    return {owner: auth.currentUser.userId, token: auth.accessToken};
}
function unchanged(scope: ReturnType<typeof session>) {
    const current = session(scope.owner);
    if (current.token !== scope.token) throw failure('PAPER_ALERT_SESSION_CHANGED');
}
function text(value: unknown, limit: number, required = false): boolean {
    if (value == null) return !required;
    if (
        typeof value !== 'string' ||
        value.length > limit ||
        (required && !value.trim()) ||
        value.includes('\0')
    )
        return false;
    for (let index = 0; index < value.length; index++) {
        const code = value.charCodeAt(index);
        if (code >= 0xd800 && code <= 0xdbff) {
            const low = value.charCodeAt(++index);
            if (!(low >= 0xdc00 && low <= 0xdfff)) return false;
        } else if (code >= 0xdc00 && code <= 0xdfff) return false;
    }
    return true;
}
export function validPaperAlertCommand(value: unknown): value is PaperAlertCommand {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
    const row = value as Record<string, unknown>;
    if (row.type === 'CREATE') {
        if (
            !Object.keys(row).every((key) =>
                [
                    'type',
                    'alertType',
                    'severity',
                    'title',
                    'message',
                    'source',
                    'eventSnapshotJson',
                ].includes(key),
            ) ||
            !text(row.alertType, 64, true) ||
            !['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(String(row.severity)) ||
            !text(row.title, 255, true) ||
            !text(row.message, 2048) ||
            !text(row.source, 64) ||
            !text(row.eventSnapshotJson, 8192)
        )
            return false;
        try {
            if (row.eventSnapshotJson != null) JSON.parse(row.eventSnapshotJson as string);
        } catch {
            return false;
        }
        return true;
    }
    return (
        (row.type === 'ACK' || row.type === 'RESOLVE') &&
        typeof row.alertId === 'string' &&
        identifier.test(row.alertId) &&
        Object.keys(row).every((key) => ['type', 'alertId'].includes(key))
    );
}

/** 刷新遗留的提交中只能视为未知；损坏/不可读存储不能降级成无待确认操作。 */
export function readPaperAlertOperation(owner?: number): PaperAlertStorage {
    if (!owner || !Number.isSafeInteger(owner) || owner <= 0) return {kind: 'none'};
    try {
        const raw = localStorage.getItem(storageKey(owner));
        if (raw === null) return {kind: 'none'};
        if (raw.length > 131072) return {kind: 'unavailable'};
        const value = JSON.parse(raw) as PendingPaperAlertOperation;
        if (
            !value ||
            value.owner !== owner ||
            !uuid.test(value.operationId) ||
            typeof value.paperRunId !== 'string' ||
            !identifier.test(value.paperRunId) ||
            !validPaperAlertCommand(value.command) ||
            !Number.isSafeInteger(value.recordedAt) ||
            value.recordedAt <= 0 ||
            !['SUBMITTING', 'UNKNOWN'].includes(value.phase) ||
            Object.keys(value).some(
                (key) =>
                    ![
                        'owner',
                        'operationId',
                        'paperRunId',
                        'command',
                        'recordedAt',
                        'phase',
                    ].includes(key),
            )
        )
            return {kind: 'unavailable'};
        return {
            kind: 'pending',
            pending:
                value.phase === 'SUBMITTING' && !inFlight.has(value.operationId)
                    ? {...value, phase: 'UNKNOWN'}
                    : value,
        };
    } catch {
        return {kind: 'unavailable'};
    }
}
function retain(pending: PendingPaperAlertOperation) {
    try {
        const raw = JSON.stringify(pending);
        if (raw.length > 131072) throw failure('PAPER_ALERT_STORAGE_UNAVAILABLE');
        localStorage.setItem(storageKey(pending.owner), raw);
        if (localStorage.getItem(storageKey(pending.owner)) !== raw)
            throw failure('PAPER_ALERT_STORAGE_UNAVAILABLE');
    } catch {
        throw failure('PAPER_ALERT_STORAGE_UNAVAILABLE');
    }
    notify();
}
function confirm(pending: PendingPaperAlertOperation) {
    const stored = readPaperAlertOperation(pending.owner);
    if (stored.kind !== 'pending' || original(stored.pending) !== original(pending))
        throw failure('PAPER_ALERT_PENDING_CHANGED');
    localStorage.removeItem(storageKey(pending.owner));
    if (localStorage.getItem(storageKey(pending.owner)) !== null)
        throw failure('PAPER_ALERT_STORAGE_UNAVAILABLE');
    missing = null;
    notify();
}

/** 与Java使用同一长度前缀和完整输入，回执必须同时匹配原UUID、父运行与输入指纹。 */
export async function paperAlertFingerprint(pending: PendingPaperAlertOperation): Promise<string> {
    const command = pending.command;
    const values = [
        'paper-alert-operation.v1',
        String(pending.owner),
        pending.paperRunId,
        command.type,
        command.type === 'CREATE' ? null : command.alertId,
        command.type === 'CREATE' ? command.alertType : null,
        command.type === 'CREATE' ? command.severity : null,
        command.type === 'CREATE' ? command.title : null,
        command.type === 'CREATE' ? (command.message ?? null) : null,
        command.type === 'CREATE' ? (command.source ?? null) : null,
        command.type === 'CREATE' ? (command.eventSnapshotJson ?? null) : null,
    ];
    const parts = values.map((value) => {
        const bytes = value === null ? new Uint8Array() : new TextEncoder().encode(value);
        const part = new Uint8Array(4 + bytes.length);
        new DataView(part.buffer).setInt32(0, value === null ? -1 : bytes.length);
        part.set(bytes, 4);
        return part;
    });
    const bytes = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0));
    let offset = 0;
    for (const part of parts) {
        bytes.set(part, offset);
        offset += part.length;
    }
    return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)))
        .map((value) => value.toString(16).padStart(2, '0'))
        .join('');
}
/** 读取边界拒绝串运行或缺字段，不把损坏响应当空列表或当前告警。 */
export function parsePaperAlert(
    value: unknown,
    paperRunId: string,
    alertId?: string,
): PaperRunAlertItem {
    const result = value as PaperRunAlertItem;
    const instant = (value: unknown, nullable = false) =>
        (nullable && value === null) ||
        (typeof value === 'string' && notificationInstant(value) !== null);
    if (
        !result ||
        typeof result !== 'object' ||
        ![
            'alertId',
            'paperRunId',
            'alertType',
            'severity',
            'status',
            'title',
            'message',
            'source',
            'eventSnapshotJson',
            'acknowledgedBy',
            'acknowledgedAt',
            'resolvedAt',
            'createdAt',
            'updatedAt',
        ].every((key) => Object.hasOwn(result, key)) ||
        result.paperRunId !== paperRunId ||
        typeof result.alertId !== 'string' ||
        !identifier.test(result.alertId) ||
        (alertId !== undefined && result.alertId !== alertId) ||
        !text(result.status, 32, true) ||
        !text(result.alertType, 64, true) ||
        !text(result.title, 255, true) ||
        !text(result.severity, 128, true) ||
        !instant(result.createdAt) ||
        !instant(result.updatedAt) ||
        !instant(result.acknowledgedAt, true) ||
        !instant(result.resolvedAt, true)
    )
        throw failure('PAPER_ALERT_RESPONSE_INVALID');
    const nullableText = (value: unknown, limit: number) =>
        value === null || (typeof value === 'string' && text(value, limit));
    if (
        !nullableText(result.message, 2048) ||
        !nullableText(result.source, 64) ||
        !nullableText(result.eventSnapshotJson, 65536) ||
        !nullableText(result.acknowledgedBy, 128) ||
        (result.acknowledgedAt === null) !== (result.acknowledgedBy === null)
    )
        throw failure('PAPER_ALERT_RESPONSE_INVALID');
    if (result.eventSnapshotJson !== null) {
        try {
            JSON.parse(result.eventSnapshotJson);
        } catch {
            throw failure('PAPER_ALERT_RESPONSE_INVALID');
        }
    }
    return result;
}
async function receipt(pending: PendingPaperAlertOperation, value: PaperAlertReceipt) {
    const result = parsePaperAlert(
        value?.result,
        pending.paperRunId,
        pending.command.type === 'CREATE' ? undefined : pending.command.alertId,
    );
    if (
        value?.operationId !== pending.operationId ||
        value.paperRunId !== pending.paperRunId ||
        value.type !== pending.command.type ||
        value.status !== 'COMPLETED' ||
        value.inputSha256 !== (await paperAlertFingerprint(pending)) ||
        typeof value.completedAt !== 'string' ||
        notificationInstant(value.completedAt) === null ||
        result.status !==
            {CREATE: 'OPEN', ACK: 'ACKED', RESOLVE: 'RESOLVED'}[pending.command.type] ||
        (pending.command.type === 'ACK' &&
            (result.acknowledgedAt === null || !result.acknowledgedBy)) ||
        (pending.command.type === 'RESOLVE'
            ? result.resolvedAt === null
            : result.resolvedAt !== null)
    )
        throw failure('PAPER_ALERT_RESPONSE_INVALID');
    if (pending.command.type === 'CREATE') {
        const command = pending.command;
        if (
            result.alertType !== command.alertType ||
            result.severity !== command.severity ||
            result.title !== command.title ||
            // JSONB会重排快照与补空格；完整原输入由SHA绑定，不比较数据库序列化字节。
            result.message !== (command.message ?? null) ||
            result.source !== (command.source ?? null) ||
            result.acknowledgedAt !== null
        )
            throw failure('PAPER_ALERT_RESPONSE_INVALID');
    }
    return value;
}
async function writeLocked<T>(owner: number, action: () => Promise<T>): Promise<T> {
    if (!navigator.locks) throw failure('PAPER_ALERT_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(storageKey(owner), {ifAvailable: true}, (lock) => {
        if (!lock) throw failure('PAPER_ALERT_BUSY');
        return action();
    });
}
const path = (pending: PendingPaperAlertOperation) =>
    `/paper-trading/runs/${encodeURIComponent(pending.paperRunId)}/alert-operations/${pending.operationId}`;
async function execute(
    pending: PendingPaperAlertOperation,
    scope: ReturnType<typeof session>,
    recovery: boolean,
) {
    unchanged(scope);
    if (!canOperatePaperAlerts()) throw failure('PAPER_ALERT_PERMISSION_REQUIRED');
    inFlight.add(pending.operationId);
    try {
        retain({...pending, phase: 'SUBMITTING'});
        const response = await apiClient.post<PaperAlertReceipt>(path(pending), pending.command, {
            timeout: 15000,
        });
        const result = await receipt(pending, response.data);
        unchanged(scope);
        confirm(pending);
        return result;
    } catch (error) {
        const problem = error as AppApiError;
        const current = useAuthStore.getState();
        const sameSession =
            current.currentUser?.userId === scope.owner && current.accessToken === scope.token;
        if (!recovery && sameSession && [400, 401, 403, 404, 422].includes(problem.status))
            confirm(pending);
        else if (problem.code !== 'PAPER_ALERT_STORAGE_UNAVAILABLE')
            retain({...pending, phase: 'UNKNOWN'});
        throw error;
    } finally {
        inFlight.delete(pending.operationId);
        notify();
    }
}

export const paperAlertOperations = {
    async submit(paperRunId: string, command: PaperAlertCommand): Promise<PaperAlertReceipt> {
        const scope = session();
        if (!identifier.test(paperRunId) || !validPaperAlertCommand(command))
            throw failure('PAPER_ALERT_INPUT_INVALID');
        return writeLocked(scope.owner, async () => {
            unchanged(scope);
            if (readPaperAlertOperation(scope.owner).kind !== 'none')
                throw failure('PAPER_ALERT_PENDING');
            missing = null;
            // 保存独立输入快照，调用者后续修改表单对象不能改变正在发送的原命令。
            return execute(
                {
                    owner: scope.owner,
                    paperRunId,
                    command: structuredClone(command),
                    operationId: crypto.randomUUID(),
                    recordedAt: Date.now(),
                    phase: 'SUBMITTING',
                },
                scope,
                false,
            );
        });
    },
    async lookup(
        pending: PendingPaperAlertOperation,
        signal?: AbortSignal,
    ): Promise<PaperAlertReceipt> {
        const scope = session(pending.owner);
        missing = null;
        if (!navigator.locks) throw failure('PAPER_ALERT_COORDINATION_UNAVAILABLE');
        const control = new AbortController();
        const abort = () => control.abort();
        signal?.addEventListener('abort', abort, {once: true});
        if (signal?.aborted) control.abort();
        const timer = setTimeout(abort, 15000);
        try {
            return await navigator.locks.request(
                storageKey(scope.owner),
                {signal: control.signal},
                async () => {
                    unchanged(scope);
                    const stored = readPaperAlertOperation(scope.owner);
                    if (stored.kind !== 'pending' || original(stored.pending) !== original(pending))
                        throw failure('PAPER_ALERT_PENDING_CHANGED');
                    const response = await apiClient.get<PaperAlertReceipt>(path(pending), {
                        signal: control.signal,
                        timeout: 15000,
                    });
                    const result = await receipt(pending, response.data);
                    unchanged(scope);
                    confirm(pending);
                    return result;
                },
            );
        } catch (error) {
            unchanged(scope);
            if ((error as AppApiError).status === 404)
                missing = {token: scope.token, original: original(pending)};
            if (control.signal.aborted && !signal?.aborted)
                throw failure('PAPER_ALERT_READ_TIMEOUT');
            throw error;
        } finally {
            clearTimeout(timer);
            signal?.removeEventListener('abort', abort);
        }
    },
    async resume(pending: PendingPaperAlertOperation): Promise<PaperAlertReceipt> {
        const scope = session(pending.owner);
        return writeLocked(scope.owner, async () => {
            const stored = readPaperAlertOperation(scope.owner);
            if (
                !missing ||
                missing.token !== scope.token ||
                missing.original !== original(pending) ||
                stored.kind !== 'pending' ||
                original(stored.pending) !== original(pending)
            )
                throw failure('PAPER_ALERT_LOOKUP_REQUIRED');
            missing = null;
            return execute(pending, scope, true);
        });
    },
};

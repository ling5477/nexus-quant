import {apiClient} from '@/api/client';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';

export type MaintenanceKind = 'RECONCILE' | 'RECOVERY';
export interface MaintenanceRequest {
    kind: MaintenanceKind;
    venue: 'OKX' | 'BINANCE';
    limit?: number;
}
export interface MaintenanceReceipt extends Omit<MaintenanceRequest, 'limit'> {
    operationId: string;
    limit: number | null;
    state: 'STARTED' | 'UNKNOWN' | 'COMPLETED';
    traceId: string;
    detail: string | null;
    createdAt: string;
    completedAt: string | null;
}
export interface PendingMaintenance {
    owner: number;
    operationId: string;
    request: MaintenanceRequest;
    rejected?: boolean;
}
const key = (owner: number) => `nq:trading-maintenance:v1:${owner}`;
export const maintenanceChanged = 'nq-maintenance-operation-changed';
const problem = (code: string) => Object.assign(new Error(code), {code});
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** 损坏的本地记录不得被当成没有待定操作，避免刷新后换编号重发。 */
export function readMaintenance(owner?: number): PendingMaintenance | null {
    if (!owner) return null;
    let raw: string | null;
    try {
        raw = localStorage.getItem(key(owner));
    } catch {
        throw problem('MAINTENANCE_STORAGE_UNAVAILABLE');
    }
    if (!raw) return null;
    try {
        const value = JSON.parse(raw) as PendingMaintenance;
        if (
            value.owner !== owner ||
            !uuid.test(value.operationId) ||
            !validMaintenance(value.request)
        )
            throw new Error();
        return value;
    } catch {
        throw problem('MAINTENANCE_STORAGE_INVALID');
    }
}
export function validMaintenance(value: MaintenanceRequest): boolean {
    return Boolean(
        value &&
            ['OKX', 'BINANCE'].includes(value.venue) &&
            ((value.kind === 'RECONCILE' &&
                Number.isInteger(value.limit) &&
                value.limit! >= 1 &&
                value.limit! <= 1000) ||
                (value.kind === 'RECOVERY' && value.limit == null)),
    );
}
function save(value: PendingMaintenance) {
    try {
        localStorage.setItem(key(value.owner), JSON.stringify(value));
        if (localStorage.getItem(key(value.owner)) !== JSON.stringify(value)) throw new Error();
    } catch {
        throw problem('MAINTENANCE_STORAGE_UNAVAILABLE');
    }
    window.dispatchEvent(new Event(maintenanceChanged));
}
function scope(owner: number, token: string | null) {
    const current = useAuthStore.getState();
    if (
        current.currentUser?.userId !== owner ||
        selectSessionIdentity(current) !== token ||
        !token ||
        !current.currentUser.roles.some((role) =>
            ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
        )
    )
        throw problem('MAINTENANCE_SCOPE_CHANGED');
}
function verify(value: MaintenanceReceipt, original: PendingMaintenance) {
    if (
        !value ||
        value.operationId !== original.operationId ||
        value.kind !== original.request.kind ||
        value.venue !== original.request.venue ||
        value.limit !== (original.request.limit ?? null) ||
        !['STARTED', 'UNKNOWN', 'COMPLETED'].includes(value.state) ||
        typeof value.traceId !== 'string' ||
        !value.traceId ||
        !Number.isFinite(Date.parse(value.createdAt)) ||
        (value.state === 'COMPLETED'
            ? typeof value.detail !== 'string' ||
              !value.detail ||
              !value.completedAt ||
              !Number.isFinite(Date.parse(value.completedAt))
            : value.detail !== null || value.completedAt !== null)
    )
        throw problem('MAINTENANCE_RESPONSE_INVALID');
    return value;
}
async function locked<T>(owner: number, work: () => Promise<T>) {
    if (!navigator.locks) throw problem('MAINTENANCE_COORDINATION_UNAVAILABLE');
    return navigator.locks.request(key(owner), {ifAvailable: true}, (lock) => {
        if (!lock) throw problem('MAINTENANCE_BUSY');
        return work();
    });
}

/** 跨标签页只允许一个原身份；写入本地身份失败时禁止发起服务端命令。 */
export async function submitMaintenance(owner: number, request: MaintenanceRequest) {
    const token = selectSessionIdentity(useAuthStore.getState());
    return locked(owner, async () => {
        scope(owner, token);
        if (readMaintenance(owner)) throw problem('MAINTENANCE_PENDING');
        if (!validMaintenance(request)) throw problem('MAINTENANCE_INPUT_INVALID');
        const original: PendingMaintenance = {owner, operationId: crypto.randomUUID(), request};
        save(original);
        try {
            const {data} = await apiClient.post<MaintenanceReceipt>(
                request.kind === 'RECONCILE'
                    ? '/trading/reconciliation/run-once'
                    : '/trading/recovery/run-once',
                {
                    venue: request.venue,
                    ...(request.kind === 'RECONCILE' ? {limit: request.limit} : {}),
                },
                {headers: {'Idempotency-Key': original.operationId}},
            );
            scope(owner, token);
            return verify(data, original);
        } catch (failure) {
            // 仅正式鉴权及输入校验拒绝确认未进入服务；超时、409和500一律保留原身份。
            const status = (failure as {status?: number}).status;
            if ([400, 401, 403, 422].includes(status ?? 0)) save({...original, rejected: true});
            throw failure;
        }
    });
}

export async function lookupMaintenance(original: PendingMaintenance) {
    const token = selectSessionIdentity(useAuthStore.getState());
    scope(original.owner, token);
    const {data} = await apiClient.get<MaintenanceReceipt>(
        `/trading/maintenance/operations/${original.operationId}`,
    );
    scope(original.owner, token);
    return verify(data, original);
}

/** 只在正式原结果已确认或提交前置拒绝后收起记录；404和查询失败都不允许解锁。 */
export async function acknowledgeMaintenance(original: PendingMaintenance) {
    const token = selectSessionIdentity(useAuthStore.getState());
    return locked(original.owner, async () => {
        scope(original.owner, token);
        const current = readMaintenance(original.owner);
        if (!current || current.operationId !== original.operationId)
            throw problem('MAINTENANCE_PENDING');
        if (!current.rejected && (await lookupMaintenance(current)).state !== 'COMPLETED')
            throw problem('MAINTENANCE_PENDING');
        scope(original.owner, token);
        localStorage.removeItem(key(original.owner));
        window.dispatchEvent(new Event(maintenanceChanged));
    });
}

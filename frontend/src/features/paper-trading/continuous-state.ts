import type {ContinuousSimStatus} from './types/paper-trading';

function isUtcInstant(value: unknown): value is string {
    if (typeof value !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z$/.test(value))
        return false;
    const instant = Date.parse(value);
    return Number.isFinite(instant) && new Date(instant).toISOString().slice(0, 19) === value.slice(0, 19);
}

/** 固定 UTC 整点的原始值可用于到期后的回执核对，因此此处不与客户端当前时间比较。 */
export function isUtcHour(value: unknown): value is string {
    return isUtcInstant(value) && /:00:00(?:\.0{1,9})?Z$/.test(value);
}

function validBoundedStatus(value: unknown): boolean {
    if (value == null) return true;
    if (typeof value !== 'object' || Array.isArray(value)) return false;
    const row = value as Record<string, unknown>;
    const text = (v: unknown) => typeof v === 'string' && v.length > 0 && v.length <= 128;
    return isUtcHour(row.endAt) && text(row.lifecycleStatus) && text(row.finalValuationStatus)
        && (row.manualEndAt == null || isUtcInstant(row.manualEndAt))
        && (row.terminatedAt == null || isUtcInstant(row.terminatedAt))
        && (row.lastValuationBoundary === null || isUtcHour(row.lastValuationBoundary))
        && (row.pendingOrders === null || (typeof row.pendingOrders === 'number'
            && Number.isSafeInteger(row.pendingOrders) && row.pendingOrders >= 0))
        && (row.reason === null || text(row.reason))
        && typeof row.updatedAt === 'string' && Number.isFinite(Date.parse(row.updatedAt))
        && text(row.publishId) && text(row.strategyVersionId);
}

/** 只有空成功响应表示没有游标；结构损坏和其他运行的数据必须作为读取错误处理。 */
export function parseContinuousSimStatus(
    value: unknown,
    runId: string,
): ContinuousSimStatus | null {
    if (value == null || value === '') return null;
    if (typeof value !== 'object' || Array.isArray(value))
        throw new Error('Invalid continuous status');
    const row = value as Record<string, unknown>;
    const text = (key: string, nullable = false) =>
        (nullable && row[key] === null) ||
        (typeof row[key] === 'string' && row[key].length > 0 && row[key].length <= 128);
    const instant = (key: string, nullable = false) =>
        (nullable && row[key] === null) ||
        (typeof row[key] === 'string' && Number.isFinite(Date.parse(row[key])));
    if (
        row.paperRunId !== runId ||
        !text('status') ||
        !instant('startedAt') ||
        !instant('lastPollAt', true) ||
        !instant('lastSuccessfulPollAt', true) ||
        !instant('lastProcessedBar') ||
        !instant('nextExpectedBar') ||
        !instant('lastObservedBar') ||
        !instant('gapStartBar', true) ||
        !text('lastDecisionId', true) ||
        !text('lastDecisionStatus', true) ||
        !text('blockReason', true) ||
        !validBoundedStatus(row.bounded) ||
        !Number.isInteger(row.consecutivePollFailures) ||
        (row.consecutivePollFailures as number) < 0 ||
        (row.consecutivePollFailures as number) > 2_147_483_647
    )
        throw new Error('Invalid continuous status identity or facts');
    return value as ContinuousSimStatus;
}

/** 非法或串运行响应不参与动作判断；未知状态保留原值，不能猜成正在运行或已停止。 */
export function continuousStateMatches(
    value: ContinuousSimStatus | null | undefined,
    runId: string,
): boolean {
    return value == null || value.paperRunId === runId;
}

export function continuousStatusKey(status: string): 'running' | 'stalled' | 'stopped' | 'unknown' {
    return status === 'RUNNING'
        ? 'running'
        : status === 'STALLED'
          ? 'stalled'
          : status === 'STOPPED'
            ? 'stopped'
            : 'unknown';
}

/** 这里只解释正式状态；仍由服务端检查来源、容量和版本，未知或父运行终态时关闭恢复入口。 */
export function continuousAction(
    status: ContinuousSimStatus,
    parentStatus: string,
): 'pause' | 'resume' | 'end' | null {
    if (parentStatus !== 'RUNNING') return null;
    if (status.bounded) {
        if (status.status === 'STOPPED') return null;
        return ['RUNNING', 'ADMISSION_CLOSED', 'SETTLING', 'BLOCKED'].includes(status.bounded.lifecycleStatus)
            ? 'end' : null;
    }
    if (status.status === 'RUNNING' || status.status === 'STALLED') return 'pause';
    return status.status === 'STOPPED' && status.blockReason !== 'DATA_REVISION_DETECTED'
        ? 'resume'
        : null;
}

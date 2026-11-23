import type {ScheduledJobControl} from '@/features/scheduler/types';
import type {ContinuousSimStatus} from './types/paper-trading';

export const continuousJobKeys = ['CONTINUOUS_SIM_POLL', 'PAPER_MATCHING'] as const;

/** 只消费代码目录内两项依赖；缺行保留未知，损坏或重复行作为读取失败。 */
export function continuousJobs(rows: ScheduledJobControl[]) {
    if (!Array.isArray(rows) || rows.length > 100) throw new Error('INVALID_SCHEDULER_RESPONSE');
    const result = new Map<string, ScheduledJobControl>();
    for (const row of rows) {
        if (!continuousJobKeys.includes(row?.jobKey as typeof continuousJobKeys[number])) continue;
        if (result.has(row.jobKey) || typeof row.enabled !== 'boolean'
            || !Number.isSafeInteger(row.fixedDelayMs) || row.fixedDelayMs < 1000 || row.fixedDelayMs > 86400000
            || !Number.isSafeInteger(row.consecutiveFailures) || row.consecutiveFailures < 0
            || typeof row.lastStatus !== 'string'
            || [row.lastStartedAt, row.lastFinishedAt, row.nextRunAt].some(at => at !== null && !Number.isFinite(Date.parse(at))))
            throw new Error('INVALID_SCHEDULER_RESPONSE');
        result.set(row.jobKey, row);
    }
    return result;
}

/** 新鲜度仅提示当前读取时刻的运营检查，不重算服务端状态或改变恢复资格。 */
export function continuousPollFreshness(status: ContinuousSimStatus | null, delay: number | null, now: number) {
    if (status?.status === 'STOPPED') return 'PAUSED';
    const last = status?.lastPollAt ? Date.parse(status.lastPollAt) : NaN;
    if (!status?.lastSuccessfulPollAt || !Number.isFinite(last) || !Number.isFinite(now)
        || last > now + 60000 || delay === null || !Number.isSafeInteger(delay) || delay < 30000 || delay > 86400000)
        return 'UNKNOWN';
    return now - last > 2 * delay + 60000 ? 'STALE' : 'RECENT';
}

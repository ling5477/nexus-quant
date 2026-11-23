import {apiClient} from '@/api/client';

export interface JvmResourceSample {
    measuredAt: string;
    processCpuPercent: number | null;
    availableProcessors: number;
    heapUsedBytes: number | null;
    heapCommittedBytes: number | null;
    heapMaxBytes: number | null;
    nonHeapUsedBytes: number | null;
    liveThreads: number | null;
    gcCollections: number | null;
    gcTimeMillis: number | null;
    uptimeMillis: number | null;
}
export interface JvmResourceSnapshot {
    instanceId: string;
    scope: 'JVM_PROCESS';
    status: 'AVAILABLE' | 'PARTIAL' | 'STALE' | 'WARMING_UP';
    checkedAt: string;
    windowStart: string | null;
    lastSampleAt: string | null;
    sampleSeconds: number;
    maxSamples: number;
    samples: JvmResourceSample[];
}

export const resourceApi = {
    async read(signal?: AbortSignal): Promise<JvmResourceSnapshot> {
        return (await apiClient.get<JvmResourceSnapshot>('/runtime/resources', {signal})).data;
    },
};

/** 指标的缺失保留为空，避免Number(null)把未测量值变成零负载。 */
export function bytesToMiB(value: number | null | undefined): number | null {
    return value == null || !Number.isFinite(value) || value < 0 ? null : value / 1_048_576;
}

export function resourcesAreStale(snapshot: JvmResourceSnapshot, now: number): boolean {
    return (
        snapshot.status === 'STALE' ||
        Boolean(
            snapshot.lastSampleAt &&
                (!Number.isFinite(Date.parse(snapshot.lastSampleAt)) ||
                    now - Date.parse(snapshot.lastSampleAt) > 45_000),
        )
    );
}

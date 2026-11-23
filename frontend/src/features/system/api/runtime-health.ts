import {apiClient} from '@/api/client';

export interface RuntimeHealth {status: string; checkedAt: string}

/** 只读取原最小健康快照，非UP及响应损坏都不能冒充依赖正常。 */
export async function readRuntimeHealth(signal?: AbortSignal): Promise<RuntimeHealth> {
    const {data} = await apiClient.get<RuntimeHealth>('/system/health', {signal});
    if (typeof data?.status !== 'string' || !Number.isFinite(Date.parse(data.checkedAt)))
        throw new Error('INVALID_RUNTIME_HEALTH');
    return data;
}

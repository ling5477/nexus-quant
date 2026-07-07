import {apiClient} from '@/api/client';
import type {
    ShadowConsistencyReportResponse,
    ShadowRunDetailResponse,
    ShadowRunEventResponse,
    ShadowRunSnapshotResponse,
} from '@/types/shadow-runs';

export async function getShadowRunDetail(id: string): Promise<ShadowRunDetailResponse> {
    const {data} = await apiClient.get<ShadowRunDetailResponse>(`/shadow-runs/${id}`);
    return data;
}

export async function getShadowRunEvents(id: string): Promise<ShadowRunEventResponse[]> {
    const {data} = await apiClient.get<ShadowRunEventResponse[]>(`/shadow-runs/${id}/events`);
    return data;
}

export async function getShadowRunSnapshots(id: string): Promise<ShadowRunSnapshotResponse[]> {
    const {data} = await apiClient.get<ShadowRunSnapshotResponse[]>(`/shadow-runs/${id}/snapshots`);
    return data;
}

export async function getShadowRunLatestConsistencyReport(id: string): Promise<ShadowConsistencyReportResponse> {
    const {data} = await apiClient.get<ShadowConsistencyReportResponse>(`/shadow-runs/${id}/consistency-report/latest`);
    return data;
}

/**
 * GateR-7 Shadow Run API client。
 *
 * Why:
 * 只消费 GateR-6 已存在的 read-only GET API；不创建、不启动、不停止、不重跑 Shadow Run，
 * 不读取 credential，不调用 private endpoint，不触发 runner 或真实交易。
 */
export const shadowRunsApi = {
    getShadowRunDetail,
    getShadowRunEvents,
    getShadowRunSnapshots,
    getShadowRunLatestConsistencyReport,
};

import {apiClient} from '@/api/client';
import type {
    PaperShadowConsistencyDrilldownResponse,
    ShadowConsistencyReportResponse,
    ShadowRunDetailResponse,
    ShadowRunEventResponse,
    ShadowRunListRequest,
    ShadowRunListResponse,
    ShadowRunOverviewResponse,
    ShadowRunSnapshotResponse,
} from '@/types/shadow-runs';

function listParams(params: ShadowRunListRequest): Record<string, string | number> {
    const result: Record<string, string | number> = {};
    if (params.status) {
        result.status = params.status;
    }
    if (params.strategyVersionId) {
        result.strategyVersionId = params.strategyVersionId;
    }
    if (params.datasetId) {
        result.datasetId = params.datasetId;
    }
    if (params.paperRunId) {
        result.paperRunId = params.paperRunId;
    }
    if (params.limit !== undefined) {
        result.limit = params.limit;
    }
    if (params.offset !== undefined) {
        result.offset = params.offset;
    }
    return result;
}

export async function listShadowRuns(params: ShadowRunListRequest = {}): Promise<ShadowRunListResponse> {
    const {data} = await apiClient.get<ShadowRunListResponse>('/shadow-runs', {params: listParams(params)});
    return data;
}

export async function getShadowRunOverview(): Promise<ShadowRunOverviewResponse> {
    const {data} = await apiClient.get<ShadowRunOverviewResponse>('/shadow-runs/overview');
    return data;
}

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

export async function getPaperShadowConsistencyDrilldown(shadowRunId: string): Promise<PaperShadowConsistencyDrilldownResponse> {
    const {data} = await apiClient.get<PaperShadowConsistencyDrilldownResponse>(
        '/paper-shadow/consistency/drilldown',
        {params: {shadowRunId}},
    );
    return data;
}

/**
 * GateR-7 Shadow Run API client。
 *
 * Why:
 * 只消费 GateR/GateS 已存在的 read-only GET API；不创建、不启动、不停止、不重跑 Shadow Run，
 * 不读取 credential，不调用 private endpoint，不触发 runner、scheduler 或真实交易。
 */
export const shadowRunsApi = {
    listShadowRuns,
    getShadowRunOverview,
    getShadowRunDetail,
    getShadowRunEvents,
    getShadowRunSnapshots,
    getShadowRunLatestConsistencyReport,
    getPaperShadowConsistencyDrilldown,
};

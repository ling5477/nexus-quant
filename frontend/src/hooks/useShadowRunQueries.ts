import {useQuery} from '@tanstack/react-query';

import {shadowRunsQueryKeys} from '@/api/query-keys';
import {shadowRunsApi} from '@/api/shadow-runs';

/**
 * GateR-7 Shadow Run read-only 查询 hooks。
 *
 * Why:
 * 这些 query 只读取本地 diagnostic facts。关闭自动 retry 可以让 404 / 500 明确进入
 * not found / error 状态，避免 UI 把不可用误显示成加载中的可执行能力。
 */
export function useShadowRunDetailQuery(shadowRunId: string | null) {
    return useQuery({
        queryKey: shadowRunsQueryKeys.detail(shadowRunId ?? ''),
        queryFn: () => shadowRunsApi.getShadowRunDetail(shadowRunId ?? ''),
        enabled: Boolean(shadowRunId),
        retry: false,
    });
}

export function useShadowRunEventsQuery(shadowRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: shadowRunsQueryKeys.events(shadowRunId ?? ''),
        queryFn: () => shadowRunsApi.getShadowRunEvents(shadowRunId ?? ''),
        enabled: Boolean(shadowRunId) && enabled,
        retry: false,
    });
}

export function useShadowRunSnapshotsQuery(shadowRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: shadowRunsQueryKeys.snapshots(shadowRunId ?? ''),
        queryFn: () => shadowRunsApi.getShadowRunSnapshots(shadowRunId ?? ''),
        enabled: Boolean(shadowRunId) && enabled,
        retry: false,
    });
}

export function useShadowRunLatestConsistencyReportQuery(shadowRunId: string | null, enabled = true) {
    return useQuery({
        queryKey: shadowRunsQueryKeys.latestConsistencyReport(shadowRunId ?? ''),
        queryFn: () => shadowRunsApi.getShadowRunLatestConsistencyReport(shadowRunId ?? ''),
        enabled: Boolean(shadowRunId) && enabled,
        retry: false,
    });
}

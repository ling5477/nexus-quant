import {useQuery} from '@tanstack/react-query';

import {incidentReplayApi} from '@/api/incident-replay';
import {incidentReplayQueryKeys} from '@/api/query-keys';

/**
 * GateS-6 Incident / Replay overview 查询 hook。
 *
 * Why:
 * overview 是 read-only / diagnostic-only / no-side-effect 诊断面板；关闭自动 retry，避免错误态被延迟隐藏
 * 或被误读成后台轮询/真实 incident runtime。
 */
export function useIncidentReplayOverview() {
    return useQuery({
        queryKey: incidentReplayQueryKeys.overview(),
        queryFn: () => incidentReplayApi.getIncidentReplayOverview(),
        retry: false,
    });
}

import {apiClient} from '@/api/client';
import type {IncidentReplayOverviewResponse} from '@/types/incident-replay';

export async function getIncidentReplayOverview(): Promise<IncidentReplayOverviewResponse> {
    const {data} = await apiClient.get<IncidentReplayOverviewResponse>('/incidents/replay/overview');
    return data;
}

/**
 * GateS-6 Incident / Replay API client。
 *
 * Why:
 * 该 client 只消费已存在的 GET-only overview endpoint；不创建 incident、不确认 alert、不启动 replay、
 * 不触发 runner / scheduler，也不引入任何交易动作。
 */
export const incidentReplayApi = {
    getIncidentReplayOverview,
};

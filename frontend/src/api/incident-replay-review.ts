import {apiClient} from '@/api/client';
import type {IncidentReplayReviewOverviewResponse} from '@/types/incident-replay-review';

/**
 * 读取 GateT-3 Incident / Replay Review overview。
 *
 * Why:
 * 该 client 只发起 GET 请求，复用统一 Axios 实例，不提供 review / acknowledge /
 * escalate / closeout / approve / reject、start / stop / execute / trade、
 * placeOrder / cancelOrder / withdraw / transfer 或任何 private exchange 请求，
 * 避免前端形成写侧或交易能力。
 */
export async function getIncidentReplayReviewOverview(): Promise<IncidentReplayReviewOverviewResponse> {
    const {data} = await apiClient.get<IncidentReplayReviewOverviewResponse>(
        '/incidents/replay/review/overview',
    );
    return data;
}

export const incidentReplayReviewApi = {
    getIncidentReplayReviewOverview,
};

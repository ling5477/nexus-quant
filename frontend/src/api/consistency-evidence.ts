import {apiClient} from '@/api/client';
import type {ConsistencyEvidenceOverviewResponse} from '@/types/consistency-evidence';

/**
 * 读取 GateT-2 Consistency Evidence overview。
 *
 * Why:
 * 该 client 只发起 GET 请求，复用统一 Axios 实例，不提供 create report / review / acknowledge /
 * approve / reject、start / stop / execute / trade、placeOrder / cancelOrder / withdraw / transfer
 * 或任何 private exchange 请求，避免前端形成写侧或交易能力。
 */
export async function getConsistencyEvidenceOverview(): Promise<ConsistencyEvidenceOverviewResponse> {
    const {data} = await apiClient.get<ConsistencyEvidenceOverviewResponse>(
        '/paper-shadow/consistency/evidence/overview',
    );
    return data;
}

export const consistencyEvidenceApi = {
    getConsistencyEvidenceOverview,
};

import {apiClient} from '@/api/client';
import {parseExactJson} from '@/api/exact-json';

interface Balance {
    balance: string;
    available: string;
    frozen: string;
    tradeEnv: string | null;
    basis: string | null;
    scope: string | null;
}

interface Holding {
    qty: string;
    available: string;
    frozen: string;
    avgPrice: string;
}

interface Differences {
    actualCash: Balance | null;
    expectedCash: Balance;
    actualAsset: Balance | null;
    expectedAsset: Balance | null;
    actualPosition: Holding | null;
    expectedPosition: Holding | null;
    cashConsistent: boolean;
    positionConsistent: boolean;
}

export interface ProjectionPreview {
    accountId: string;
    tenantId: string;
    endOperationId: string | null;
    ledgerWatermark: {postedThrough: string; sha256: string};
    projectionVersion: string;
    differences: Differences;
    recoveryOperationId: string | null;
}

export interface ProjectionRecoveryRequest {
    operationId: string;
    endOperationId: string;
    ledgerWatermark: string;
    projectionVersion: string;
}

export interface FinalizationRequest {
    operationId: string;
    endOperationId: string;
    recoveryOperationId: string;
}

interface ProjectionOperation {
    operationId: string;
    endOperationId: string;
    finalization: {lifecycleStatus: 'MANUALLY_STOPPED'; finalValuationStatus: 'VERIFIED'} | null;
}

const path = (run: string) =>
    `/paper-trading/strategy-sim/runs/${encodeURIComponent(run)}/projection-recovery`;

export const projectionRecoveryApi = {
    async preview(run: string, signal: AbortSignal): Promise<ProjectionPreview> {
        const {data} = await apiClient.get<ProjectionPreview>(path(run), {
            signal,
            transformResponse: [(text: string) => parseExactJson(text, new Set(['accountId', 'postedThrough']))],
        });
        return data;
    },
    async recover(run: string, request: ProjectionRecoveryRequest): Promise<ProjectionOperation> {
        const {data} = await apiClient.post<ProjectionOperation>(path(run), request);
        return data;
    },
    async recheck(run: string, request: FinalizationRequest): Promise<ProjectionOperation> {
        const {data} = await apiClient.post<ProjectionOperation>(`${path(run)}/finalization`, request);
        return data;
    },
};

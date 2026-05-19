import {apiClient} from '@/api/client';
import type {BacktestEvaluationDetailItem, BacktestEvaluationListItem} from '@/types/evaluations';

export interface EvaluationListRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
}

export const evaluationsApi = {
    async list(request: EvaluationListRequest): Promise<BacktestEvaluationListItem[]> {
        const {data} = await apiClient.get<BacktestEvaluationListItem[]>('/evaluations', {
            params: request,
        });
        return data;
    },
    async detail(evaluationId: string): Promise<BacktestEvaluationDetailItem> {
        const {data} = await apiClient.get<BacktestEvaluationDetailItem>(`/evaluations/${evaluationId}`);
        return data;
    },
    async evaluate(runId: string): Promise<BacktestEvaluationDetailItem> {
        const {data} = await apiClient.post<BacktestEvaluationDetailItem>(`/backtest-runs/${runId}/evaluate`);
        return data;
    },
};

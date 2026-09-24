import {apiClient} from '@/api/client';
import type {
    PaperShadowComparisonResponse,
    ShadowLivePreviewResponse,
    StrategyEvaluationGateResponse,
    StrategyValidationOverviewResponse,
    StrategyValidationQuery,
} from '@/features/validation/types/strategy-validation';

type StrategyValidationParams = Partial<Record<keyof StrategyValidationQuery, string>>;

function toQueryParams(query: StrategyValidationQuery): StrategyValidationParams {
    return Object.entries(query).reduce<StrategyValidationParams>((params, [key, value]) => {
        const normalized = value?.trim();
        if (normalized) {
            params[key as keyof StrategyValidationQuery] = normalized;
        }
        return params;
    }, {});
}

export async function getStrategyValidationOverview(): Promise<StrategyValidationOverviewResponse> {
    const {data} = await apiClient.get<StrategyValidationOverviewResponse>('/strategy-validation/overview');
    return data;
}

/**
 * 策略验证只读 API client。
 *
 * Why:
 * 该 client 只消费策略评估准入 / Paper/Shadow 对照 / Shadow Live 只读预览既有 GET API；不新增写侧调用，不创建 Paper/Shadow run，
 * 不读取 credential，不调用 private endpoint，也不把任何 readiness 解释成交易授权。
 */
export const strategyValidationApi = {
    getStrategyValidationOverview,

    async getEvaluationGate(query: StrategyValidationQuery): Promise<StrategyEvaluationGateResponse> {
        const {data} = await apiClient.get<StrategyEvaluationGateResponse>('/strategies/evaluation-gate', {
            params: toQueryParams(query),
        });
        return data;
    },

    async getPaperShadowComparison(query: StrategyValidationQuery): Promise<PaperShadowComparisonResponse> {
        const {data} = await apiClient.get<PaperShadowComparisonResponse>('/strategies/paper-shadow/comparison', {
            params: toQueryParams(query),
        });
        return data;
    },

    async getShadowLivePreview(query: StrategyValidationQuery): Promise<ShadowLivePreviewResponse> {
        const {data} = await apiClient.get<ShadowLivePreviewResponse>('/strategies/shadow-live/preview', {
            params: toQueryParams(query),
        });
        return data;
    },
};

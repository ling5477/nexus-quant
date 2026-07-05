import {useQuery} from '@tanstack/react-query';

import {strategyValidationQueryKeys} from '@/api/query-keys';
import {strategyValidationApi} from '@/api/strategy-validation';
import type {StrategyValidationQuery} from '@/types/strategy-validation';

/**
 * GateQ-5 页面查询 hooks。
 *
 * Why:
 * 三个查询都属于安全边界读模型；失败时页面必须 fail-closed 展示 error / unavailable，
 * 因此关闭自动 retry，避免反复请求让 smoke 误判为真实联调或把错误态延迟隐藏。
 */
export function useStrategyEvaluationGateQuery(query: StrategyValidationQuery | null) {
    return useQuery({
        queryKey: strategyValidationQueryKeys.evaluationGate(query),
        queryFn: () => strategyValidationApi.getEvaluationGate(query ?? {}),
        enabled: Boolean(query),
        retry: false,
    });
}

export function usePaperShadowComparisonQuery(query: StrategyValidationQuery | null) {
    return useQuery({
        queryKey: strategyValidationQueryKeys.paperShadowComparison(query),
        queryFn: () => strategyValidationApi.getPaperShadowComparison(query ?? {}),
        enabled: Boolean(query),
        retry: false,
    });
}

export function useShadowLivePreviewQuery(query: StrategyValidationQuery | null) {
    return useQuery({
        queryKey: strategyValidationQueryKeys.shadowLivePreview(query),
        queryFn: () => strategyValidationApi.getShadowLivePreview(query ?? {}),
        enabled: Boolean(query),
        retry: false,
    });
}

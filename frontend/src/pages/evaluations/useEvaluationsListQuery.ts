import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {evaluationsApi} from '@/api/evaluations';
import {evaluationsQueryKeys} from '@/api/query-keys';

interface UseEvaluationsListQueryRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
}

export function useEvaluationsListQuery(request: UseEvaluationsListQueryRequest, searchVersion: number) {
    return useQuery({
        queryKey: evaluationsQueryKeys.list(request, searchVersion),
        queryFn: () => evaluationsApi.list(request),
        enabled: searchVersion > 0,
    });
}

export function useEvaluationDetailQuery(evaluationId: string | null) {
    return useQuery({
        queryKey: evaluationsQueryKeys.detail(evaluationId ?? ''),
        queryFn: () => evaluationsApi.detail(evaluationId ?? ''),
        enabled: Boolean(evaluationId),
    });
}

export function useEvaluateMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (runId: string) => evaluationsApi.evaluate(runId),
        onSuccess: (report, runId) => {
            // 重新评估可能生成新报告 ID；先缓存新报告，避免刷新已经被替换的旧详情。
            queryClient.setQueryData(evaluationsQueryKeys.detail(report.evalReportId), report);
            queryClient.setQueryData(evaluationsQueryKeys.forRun(runId), report);
            queryClient.invalidateQueries({queryKey: [...evaluationsQueryKeys.all, 'list']});
        },
    });
}

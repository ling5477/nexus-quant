import {useQuery} from '@tanstack/react-query';

import {evaluationArtifactPreviewApi} from '@/api/evaluation-artifact-preview';
import {evaluationArtifactPreviewQueryKeys} from '@/api/query-keys';

/**
 * GateT-4 Evaluation Artifact Preview read-only query hook.
 *
 * Why:
 * Evaluation artifact preview 是 No-file baseline 诊断面板的只读事实源。关闭 retry 可以让
 * error 状态立即显式展示，避免自动重试把后端不可用误表现为 artifact source 已配置、
 * Python 已可执行、ML ready 或 live execution ready。
 */
export function useEvaluationArtifactPreviewOverview() {
    return useQuery({
        queryKey: evaluationArtifactPreviewQueryKeys.overview(),
        queryFn: () => evaluationArtifactPreviewApi.getEvaluationArtifactPreviewOverview(),
        retry: false,
    });
}

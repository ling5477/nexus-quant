import {useQuery} from '@tanstack/react-query';

import {shadowValidationWorkflowQueryKeys} from '@/api/query-keys';
import {shadowValidationWorkflowApi} from '@/features/validation/api/shadow-validation-workflow';

/**
 * Shadow 验证工作流只读查询 hook.
 *
 * Why:
 * Workflow overview 是人工复核诊断面板的只读事实源。关闭 retry 可以让 error 状态立即显式展示，
 * 避免自动重试把后端不可用误表现为正在联调或可继续操作。
 */
export function useShadowValidationWorkflowOverview() {
    return useQuery({
        queryKey: shadowValidationWorkflowQueryKeys.overview(),
        queryFn: () => shadowValidationWorkflowApi.getShadowValidationWorkflowOverview(),
        retry: false,
    });
}

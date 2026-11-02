import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {validationReviewQueryKeys} from '@/api/query-keys';
import {
    validationReviewApi,
    reviewOperationTarget,
    parseReviewOperationTarget,
} from '@/features/validation/api/validation-review';
import {submitResultAction} from '@/features/research-result-recovery';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {
    ValidationReviewLifecycleCommand,
    ValidationReviewListRequest,
} from '@/features/validation/types/validation-review';

/** 查询服务端稳定排序、bounded pagination 的 review queue；失败不自动 retry。 */
export function useValidationReviewListQuery(request: ValidationReviewListRequest) {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    return useQuery({
        queryKey: [...validationReviewQueryKeys.list(request), owner],
        queryFn: () => validationReviewApi.list(request),
        retry: false,
        enabled: Boolean(owner),
    });
}

/** 查询 URL 选中 case；空 ID 时不发请求，404 保持为显式不存在状态。 */
export function useValidationReviewDetailQuery(caseId: string | null) {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    return useQuery({
        queryKey: [...validationReviewQueryKeys.detail(caseId ?? ''), owner],
        queryFn: () => validationReviewApi.detail(caseId ?? ''),
        enabled: Boolean(caseId && owner),
        retry: false,
    });
}

/** 查询服务端返回顺序的最多 100 条 lifecycle events，不补造分页或完整历史。 */
export function useValidationReviewEventsQuery(caseId: string | null) {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    return useQuery({
        queryKey: [...validationReviewQueryKeys.events(caseId ?? ''), owner],
        queryFn: () => validationReviewApi.events(caseId ?? ''),
        enabled: Boolean(caseId && owner),
        retry: false,
    });
}

/**
 * 执行四类 allowlisted lifecycle mutation。
 *
 * Why：mutation 禁止自动 retry，避免同一 payload 被浏览器隐式重复提交；成功后统一失效 queue、
 * detail、events。409/422 额外立即 refetch queue/detail/events，让三处都回到服务端最新状态。
 */
export function useValidationReviewLifecycleMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (command: Omit<ValidationReviewLifecycleCommand, 'idempotencyKey'>) =>
            submitResultAction(
                'VALIDATION_REVIEW',
                reviewOperationTarget(command),
                async (idempotencyKey) => {
                    const result = await validationReviewApi.transition({
                        ...command,
                        idempotencyKey,
                    });
                    const target = parseReviewOperationTarget(reviewOperationTarget(command));
                    if (
                        result.id !== command.caseId ||
                        result.state !== target.targetState ||
                        result.version !== command.payload.expectedVersion + 1 ||
                        result.diagnosticOnly !== true ||
                        result.notTradingAuthorization !== true ||
                        result.liveDisabled !== true ||
                        result.noSideEffect !== true
                    )
                        throw new Error('Unconfirmed original review result');
                    return result;
                },
            ),
        retry: false,
        onSuccess: async (_, command) => {
            await Promise.all([
                queryClient.invalidateQueries({queryKey: validationReviewQueryKeys.lists()}),
                queryClient.invalidateQueries({
                    queryKey: validationReviewQueryKeys.detail(command.caseId),
                }),
                queryClient.invalidateQueries({
                    queryKey: validationReviewQueryKeys.events(command.caseId),
                }),
            ]);
        },
        onError: async (error, command) => {
            const apiError = error as AppApiError;
            if (apiError.status !== 409 && apiError.status !== 422) {
                return;
            }
            await Promise.all([
                queryClient.refetchQueries({queryKey: validationReviewQueryKeys.lists()}),
                queryClient.refetchQueries({
                    queryKey: validationReviewQueryKeys.detail(command.caseId),
                }),
                queryClient.refetchQueries({
                    queryKey: validationReviewQueryKeys.events(command.caseId),
                }),
            ]);
        },
    });
}

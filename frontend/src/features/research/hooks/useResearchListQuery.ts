import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {researchApi} from '@/features/research/api/research';
import {researchQueryKeys} from '@/api/query-keys';
import type {
    ResearchConfigCreateRequest,
    ResearchPageRequest,
} from '@/features/research/types/research';
import {useAuthStore} from '@/store/auth-store';

export function useResearchListQuery(request: ResearchPageRequest, searchVersion: number) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: researchQueryKeys.page(owner, request, searchVersion),
        queryFn: ({signal}) => researchApi.page(request, signal),
        enabled: searchVersion > 0 && owner !== null,
        // 页码/筛选/用户进入身份；过期读取可取消，离开的页面不累积完整列表缓存。
        gcTime: 0,
    });
}

export function useResearchDetailQuery(configId: string | null) {
    return useQuery({
        queryKey: researchQueryKeys.detail(configId ?? ''),
        queryFn: () => researchApi.detail(configId ?? ''),
        enabled: Boolean(configId),
    });
}

/** 选择值和关联对象按当前用户单独读取，不能依赖列表第一页或跨用户缓存。 */
export function useResearchSelectionDetailQuery(configId: string | null, active = true) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: [...researchQueryKeys.all, 'selection-detail', owner, active ? configId : null],
        queryFn: ({signal}) => researchApi.detail(configId!, signal),
        enabled: active && Boolean(configId) && owner !== null,
        gcTime: 0,
    });
}

export function useCreateResearchMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (request: ResearchConfigCreateRequest) => researchApi.create(request),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: researchQueryKeys.all});
        },
    });
}

/** 原配置身份随命令传入；成功或响应未知后都重读原对象，不创建替代配置。 */
export function useArchiveResearchMutation() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({configId, reason}: {configId: string; reason?: string}) =>
            researchApi.archive(configId, reason),
        // 缓存重读不能延长确认框的提交Promise；失败后必须立即允许取消或原对象重试。
        onSettled: () => {
            void queryClient.invalidateQueries({queryKey: researchQueryKeys.all});
        },
    });
}

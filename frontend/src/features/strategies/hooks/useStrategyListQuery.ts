import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {strategyQueryKeys} from '@/api/query-keys';
import {strategiesApi} from '@/features/strategies/api/strategies';
import {
    defaultStrategyListFilters,
    type StrategyPageRequest,
    type StrategyStatusUpdateRequest,
    type StrategyVersionCreateRequest,
    type StrategyVersionPageRequest,
} from '@/features/strategies/types/strategies';
import {useAuthStore} from '@/store/auth-store';

export function useStrategyListQuery(request: StrategyPageRequest, searchVersion: number) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: strategyQueryKeys.page(owner, request, searchVersion),
        queryFn: ({signal}) => strategiesApi.page(request, signal),
        enabled: searchVersion > 0 && owner !== null,
        // 离开窗口取消读取并释放结果，不累积整个策略目录或跨主体复用。
        gcTime: 0,
    });
}

/** 关联关系提供的是strategyId，不能为了取得编码而先加载全部定义。 */
export function useStrategyByIdQuery(strategyId: string | null, active = true) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    const request: StrategyPageRequest = {
        ...defaultStrategyListFilters,
        strategyId: active ? (strategyId ?? '') : '',
        limit: 1,
        offset: 0,
    };
    return useQuery({
        queryKey: strategyQueryKeys.page(owner, request, 0),
        queryFn: ({signal}) => strategiesApi.page(request, signal),
        enabled: active && Boolean(strategyId) && owner !== null,
        select: (page) => page.items[0] ?? null,
        gcTime: 0,
    });
}

export function useStrategyDetailQuery(strategyCode: string | null) {
    return useQuery({
        queryKey: strategyQueryKeys.detail(strategyCode ?? ''),
        queryFn: () => strategiesApi.detail(strategyCode ?? ''),
        enabled: Boolean(strategyCode),
    });
}

export function useUpdateStrategyStatusMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({
            strategyCode,
            request,
        }: {
            strategyCode: string;
            request: StrategyStatusUpdateRequest;
        }) => strategiesApi.updateStatus(strategyCode, request),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({queryKey: strategyQueryKeys.all});
            queryClient.invalidateQueries({
                queryKey: strategyQueryKeys.detail(variables.strategyCode),
            });
        },
    });
}

/** 关闭时切换缓存身份以即时取消读取；enabled本身不卸下旧请求观察者。 */
export function useStrategyVersionPageQuery(
    strategyCode: string,
    request: StrategyVersionPageRequest,
    active: boolean,
    revision = 0,
) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: strategyQueryKeys.versionPage(
            owner,
            strategyCode,
            request,
            active ? revision : -1,
        ),
        queryFn: ({signal}) => strategiesApi.pageVersions(strategyCode, request, signal),
        enabled: active && Boolean(strategyCode) && owner !== null,
        gcTime: 0,
    });
}

/** 已绑定或深链定位的版本直接按身份读取，不受当前目录页与筛选影响。 */
export function useStrategyVersionDetailQuery(
    strategyCode: string | null | undefined,
    versionId: string | null | undefined,
    active = true,
) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: strategyQueryKeys.scopedVersionDetail(
            owner,
            active ? (strategyCode ?? null) : null,
            active ? (versionId ?? null) : null,
        ),
        queryFn: ({signal}) => strategiesApi.versionDetail(strategyCode!, versionId!, signal),
        enabled: active && Boolean(strategyCode && versionId) && owner !== null,
        gcTime: 0,
    });
}

export function useCreateStrategyVersionMutation() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({
            strategyCode,
            request,
        }: {
            strategyCode: string;
            request: StrategyVersionCreateRequest;
        }) => strategiesApi.createVersion(strategyCode, request),
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({
                queryKey: strategyQueryKeys.versions(variables.strategyCode),
            });
            queryClient.invalidateQueries({
                queryKey: strategyQueryKeys.detail(variables.strategyCode),
            });
        },
    });
}

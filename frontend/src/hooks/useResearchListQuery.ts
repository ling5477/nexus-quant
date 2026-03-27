import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';

import {researchApi} from '@/api/research';
import {researchQueryKeys} from '@/api/query-keys';
import type {ResearchConfigCreateRequest} from '@/types/research';

export function useResearchListQuery(sourceStrategyId: string, searchVersion: number) {
    return useQuery({
        queryKey: researchQueryKeys.list(sourceStrategyId, searchVersion),
        queryFn: () => researchApi.list(sourceStrategyId || undefined),
        enabled: searchVersion > 0,
    });
}

export function useResearchDetailQuery(configId: string | null) {
    return useQuery({
        queryKey: researchQueryKeys.detail(configId ?? ''),
        queryFn: () => researchApi.detail(configId ?? ''),
        enabled: Boolean(configId),
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

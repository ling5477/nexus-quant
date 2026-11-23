import {useQuery} from '@tanstack/react-query';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import {readLegacyPaperAnalysis} from '../api/legacy-analysis';

/** 三类历史视图共享同一用户的服务端快照；离开来源会取消读取，不能沿用其他用户缓存。 */
export function useLegacyPaperAnalysis() {
    const userId = useAuthStore((state) => state.currentUser?.userId);
    return useQuery({
        queryKey: paperTradingQueryKeys.legacyAnalysis(userId),
        queryFn: ({signal}) => readLegacyPaperAnalysis(signal),
        enabled: Boolean(userId),
        retry: false,
        staleTime: 30_000,
        gcTime: 60_000,
    });
}

/** 展示组件只依赖读取状态，不假造完整TanStack结果，也不自行发起不同窗口的聚合请求。 */
export interface PaperAnalysisReadState<T> {
    data: T | undefined;
    error: unknown;
    isFetching: boolean;
    refetch: () => unknown;
}

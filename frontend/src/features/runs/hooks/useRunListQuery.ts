import {useQuery} from '@tanstack/react-query';

import {runQueryKeys} from '@/api/query-keys';
import {runsApi} from '@/features/runs/api/runs';
import type {StrategyRunPageRequest, StrategyRunRelatedRequest} from '@/features/runs/types/runs';
import {useAuthStore} from '@/store/auth-store';

export function useRunListQuery(request: StrategyRunPageRequest, searchVersion: number) {
    const hasQuery = Boolean(request.strategyId || request.scheduleId);
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);

    return useQuery({
        queryKey: runQueryKeys.page(owner, request, searchVersion),
        queryFn: ({signal}) => runsApi.page(request, signal),
        enabled: hasQuery && searchVersion > 0 && owner !== null,
        // 重置、换页或切换主体时卸下旧观察者并取消读取，不保留整份运行历史。
        gcTime: 0,
    });
}

export function useRunOverviewQuery(runId: string) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: runQueryKeys.overview(owner, runId),
        queryFn: ({signal}) => runsApi.overview(runId, signal),
        enabled: owner !== null,
        gcTime: 0,
    });
}

/** 非活动页签切换身份并即时取消请求，不能等待下次打开才覆盖旧响应。 */
export function useRunOrdersQuery(
    runId: string,
    request: StrategyRunRelatedRequest,
    active: boolean,
) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: runQueryKeys.related(owner, active ? runId : null, 'orders', request),
        queryFn: ({signal}) => runsApi.orders(runId, request, signal),
        enabled: active && owner !== null,
        gcTime: 0,
    });
}

export function useRunTradesQuery(
    runId: string,
    request: StrategyRunRelatedRequest,
    active: boolean,
) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: runQueryKeys.related(owner, active ? runId : null, 'trades', request),
        queryFn: ({signal}) => runsApi.trades(runId, request, signal),
        enabled: active && owner !== null,
        gcTime: 0,
    });
}

/** 非活动页签取消读取，显式区分不同关系与主体，不缓存整份历史。 */
export function useRunRiskEventsQuery(
    runId: string,
    request: StrategyRunRelatedRequest,
    active: boolean,
) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: runQueryKeys.related(owner, active ? runId : null, 'risk-events', request),
        queryFn: ({signal}) => runsApi.riskEvents(runId, request, signal),
        enabled: active && owner !== null,
        gcTime: 0,
    });
}

/** 非活动页签取消读取，显式区分不同关系与主体，不缓存整份历史。 */
export function useRunLedgerEntriesQuery(
    runId: string,
    request: StrategyRunRelatedRequest,
    active: boolean,
) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: runQueryKeys.related(owner, active ? runId : null, 'ledger-entries', request),
        queryFn: ({signal}) => runsApi.ledgerEntries(runId, request, signal),
        enabled: active && owner !== null,
        gcTime: 0,
    });
}

/** 非活动事件页签立即取消并释放原读取，缓存包含主体、运行和页窗口。 */
export function useRunBusinessEventsQuery(
    runId: string,
    request: StrategyRunRelatedRequest,
    active: boolean,
) {
    const owner = useAuthStore((state) => state.currentUser?.userId ?? null);
    return useQuery({
        queryKey: runQueryKeys.related(owner, active ? runId : null, 'business-events', request),
        queryFn: ({signal}) => runsApi.businessEvents(runId, request, signal),
        enabled: active && owner !== null,
        gcTime: 0,
    });
}

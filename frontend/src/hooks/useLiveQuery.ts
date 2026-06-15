import {useCallback, useEffect, useRef, useState} from 'react';
import {useQuery, type QueryKey} from '@tanstack/react-query';

import type {FreshnessState} from '@/nq-design-system';

/**
 * useLiveQuery — NQ Console 实时数据获取抽象(B0.3)。
 *
 * 职责:在 TanStack Query 之上统一 polling / manual refresh / enabled,并把查询状态归一化为
 * fresh / stale / error / disabled / loading,输出 lastUpdatedAt / latencyMs / errorReason,
 * 与 DataFreshness 等状态组件打通。
 *
 * 关键约束(GateK 前):
 * 1) 仅 polling + 手动刷新,**不接 WebSocket / SSE**;传输层抽象在此,页面不感知实现;
 *    后期切 socket 时只改本 hook,调用方不变。
 * 2) 默认窗口失焦暂停轮询(pauseOnHidden),省资源。
 * 3) 失败不静默:errorReason 暴露给上层显式展示,不吞错误。queryFn 应抛出已脱敏的错误。
 */

export type LiveStatus = 'loading' | 'fresh' | 'stale' | 'error' | 'disabled';

export interface UseLiveQueryOptions<T> {
    queryKey: QueryKey;
    queryFn: () => Promise<T>;
    /** 轮询间隔(ms);0 或省略表示只手动刷新,不轮询。 */
    pollingIntervalMs?: number;
    /** 超过该时长未更新视为 stale;默认 pollingIntervalMs*2(无轮询时 30s)。 */
    staleAfterMs?: number;
    /** 是否启用;false 时归一化为 disabled,不发请求、不轮询。默认 true。 */
    enabled?: boolean;
    /** 窗口失焦时是否暂停轮询。默认 true(省资源)。 */
    pauseOnHidden?: boolean;
}

export interface UseLiveQueryResult<T> {
    data: T | undefined;
    /** 归一化状态。 */
    status: LiveStatus;
    /** 映射到 DataFreshness 的状态,便于直接驱动状态组件。 */
    freshnessState: FreshnessState;
    /** 最近一次成功更新时间(epoch ms);从未成功为 null。 */
    lastUpdatedAt: number | null;
    /** 最近一次请求往返耗时(ms);从未请求为 null。 */
    latencyMs: number | null;
    /** 错误原因(已尽量脱敏);无错误为 null。 */
    errorReason: string | null;
    /** 是否正在请求中(首载或刷新)。 */
    isFetching: boolean;
    enabled: boolean;
    /** 手动刷新。 */
    refresh: () => void;
}

const STATUS_TO_FRESHNESS: Record<LiveStatus, FreshnessState> = {
    loading: 'no_data',
    fresh: 'fresh',
    stale: 'stale',
    error: 'error',
    disabled: 'disabled',
};

/** 把归一化 LiveStatus 映射为 DataFreshness 的 FreshnessState。 */
export function liveStatusToFreshness(status: LiveStatus): FreshnessState {
    return STATUS_TO_FRESHNESS[status];
}

function now(): number {
    return typeof performance !== 'undefined' ? performance.now() : Date.now();
}

/** 错误脱敏:优先按 HTTP 状态归类,避免把内部栈/路径透出到 UI。 */
function normalizeErrorReason(error: unknown): string {
    const appError = error as {status?: number; message?: string};

    if (typeof appError.status === 'number') {
        if (appError.status === 401 || appError.status === 403) {
            return '无访问权限或登录已过期';
        }
        if (appError.status >= 500) {
            return '服务暂时不可用';
        }
        if (appError.status === 0) {
            return '网络异常';
        }
        return `请求失败 (${appError.status})`;
    }

    return appError.message ? String(appError.message) : '请求失败';
}

export function useLiveQuery<T>(options: UseLiveQueryOptions<T>): UseLiveQueryResult<T> {
    const {queryKey, queryFn, pollingIntervalMs = 0, enabled = true, pauseOnHidden = true} = options;
    const staleAfterMs = options.staleAfterMs ?? (pollingIntervalMs > 0 ? pollingIntervalMs * 2 : 30_000);

    // 记录单次请求往返耗时(成功/失败都记录),用于 latencyMs。
    const latencyRef = useRef<number | null>(null);
    const measuredQueryFn = useCallback(async () => {
        const start = now();
        try {
            const result = await queryFn();
            latencyRef.current = Math.round(now() - start);
            return result;
        } catch (error) {
            latencyRef.current = Math.round(now() - start);
            throw error;
        }
    }, [queryFn]);

    const query = useQuery({
        queryKey,
        queryFn: measuredQueryFn,
        enabled,
        // enabled 且设了间隔才轮询;否则只手动刷新。
        refetchInterval: enabled && pollingIntervalMs > 0 ? pollingIntervalMs : false,
        // pauseOnHidden=true → 不在后台轮询(TanStack 默认);false → 后台也轮询。
        refetchIntervalInBackground: !pauseOnHidden,
    });

    // 轻量 tick:仅在 enabled 且已有数据时每秒触发一次 re-render,
    // 使 fresh→stale 随时间推移与相对时间显示实时更新。
    const [, setTick] = useState(0);
    const hasData = query.dataUpdatedAt > 0;
    useEffect(() => {
        if (!enabled || !hasData) {
            return;
        }
        const id = window.setInterval(() => setTick((tick) => (tick + 1) % 1_000_000), 1_000);
        return () => window.clearInterval(id);
    }, [enabled, hasData]);

    const lastUpdatedAt = query.dataUpdatedAt > 0 ? query.dataUpdatedAt : null;

    // 状态归一化(每次 render 重算,配合 tick 让 stale 过渡生效)。
    let status: LiveStatus;
    if (!enabled) {
        status = 'disabled';
    } else if (query.isError) {
        status = 'error';
    } else if (lastUpdatedAt === null) {
        status = 'loading';
    } else {
        status = Date.now() - lastUpdatedAt > staleAfterMs ? 'stale' : 'fresh';
    }

    const errorReason = query.isError && query.error ? normalizeErrorReason(query.error) : null;

    const refresh = useCallback(() => {
        void query.refetch();
    }, [query]);

    return {
        data: query.data,
        status,
        freshnessState: liveStatusToFreshness(status),
        lastUpdatedAt,
        latencyMs: latencyRef.current,
        errorReason,
        isFetching: query.isFetching,
        enabled,
        refresh,
    };
}

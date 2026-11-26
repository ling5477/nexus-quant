import {useEffect, useMemo, useReducer, useSyncExternalStore} from 'react';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {marketdataApi} from '@/api/marketdata';
import {marketdataQueryKeys} from '@/api/query-keys';
import {selectSessionIdentity, useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {MarketdataBar, PublicMarketSelection} from '@/types/marketdata';
import {
    MARKET_POLL_MS, mergeMarketChart, recentStoredBars, retainSnapshot, snapshotExpiry,
    supportsPublicSnapshot, validateSnapshot, type MarketSnapshotView,
} from '../public-market-snapshot';

function subscribeBrowser(notify: () => void) {
    document.addEventListener('visibilitychange', notify);
    window.addEventListener('online', notify);
    window.addEventListener('offline', notify);
    return () => {
        document.removeEventListener('visibilitychange', notify);
        window.removeEventListener('online', notify);
        window.removeEventListener('offline', notify);
    };
}

const isVisible = () => document.visibilityState === 'visible';
const isOnline = () => navigator.onLine;

function permanentFailure(view: MarketSnapshotView | undefined, error: unknown) {
    const status = (error as AppApiError | null)?.status;
    const stopped = (part?: string) => part === 'DENIED' || part === 'DISABLED';
    return status === 400 || status === 403
        || (stopped(view?.latest.tickerStatus) && stopped(view?.latest.barsStatus));
}

function cooldown(view?: MarketSnapshotView): number {
    return Math.max(0, (Date.parse(view?.latest.retryAt ?? '') || 0) - Date.now());
}

const emptyHistory: MarketdataBar[] = [];

/** 两页共用身份、缓存、轮询和暂停条件；退出选择时由查询层取消底层 Axios 请求。 */
export function usePublicMarketSnapshot(selection: PublicMarketSelection | null, following: boolean) {
    const session = useAuthStore(selectSessionIdentity);
    const visible = useSyncExternalStore(subscribeBrowser, isVisible);
    const online = useSyncExternalStore(subscribeBrowser, isOnline);
    const ready = visible && online;
    const active = following && selection && session ? selection : null;
    const supported = Boolean(active && supportsPublicSnapshot(active));
    const client = useQueryClient();
    const key = marketdataQueryKeys.publicSnapshot(session, supported ? active : null);
    const snapshot = useQuery({
        queryKey: key,
        queryFn: async ({signal}) => {
            const previous = client.getQueryData<MarketSnapshotView>(key);
            if (previous && cooldown(previous) > 0) return previous;
            const next = validateSnapshot(await marketdataApi.getPublicSnapshot(active!, signal), active!);
            signal.throwIfAborted();
            return retainSnapshot(next, client.getQueryData<MarketSnapshotView>(key));
        },
        enabled: (query) => supported && ready && !permanentFailure(query.state.data, query.state.error),
        retry: false,
        staleTime: 0,
        gcTime: 120_000,
        refetchOnMount: (query) => Date.now() - query.state.dataUpdatedAt >= 5_000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        refetchIntervalInBackground: false,
        refetchInterval: (query) => {
            if (!supported || !ready || permanentFailure(query.state.data, query.state.error)) return false;
            return Math.min(2_147_000_000, Math.max(MARKET_POLL_MS, cooldown(query.state.data)));
        },
    });

    const history = useQuery({
        queryKey: [...marketdataQueryKeys.barsAll(), 'recent', session, active] as const,
        queryFn: ({signal}) => marketdataApi.listBars(recentStoredBars(active!, Date.now()), signal),
        enabled: Boolean(active) && ready,
        retry: false,
        gcTime: 120_000,
        staleTime: 5_000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
        refetchIntervalInBackground: false,
        // 本地历史的准入与公开快照的故障及限流独立。
        refetchInterval: ready && supported ? MARKET_POLL_MS : false,
    });

    const [, updateFreshness] = useReducer((revision: number) => revision + 1, 0);
    const rejection = (snapshot.error as AppApiError | null)?.status;
    const view = rejection === 400 || rejection === 403 ? undefined : snapshot.data;
    const now = Date.now();
    const expiry = snapshotExpiry(view);
    const nextExpiry = Math.min(...[expiry.ticker, expiry.candles].filter((time) => time >= now));
    useEffect(() => {
        if (!Number.isFinite(nextExpiry)) return;
        const timer = window.setTimeout(updateFreshness, Math.min(2_147_000_000, Math.max(1, nextExpiry - Date.now() + 1)));
        return () => window.clearTimeout(timer);
    }, [nextExpiry]);

    const stored = history.data ?? emptyHistory;
    const chart = useMemo(() => mergeMarketChart(active, stored, view, Date.now()), [active, stored, view]);
    // 查询缓存会分别共享两个对象分支，是否保留较新的旧观测由来源时间决定。
    const olderTicker = Date.parse(view?.latest.lastPriceObservedAt ?? '')
        < Date.parse(view?.ticker?.lastPriceObservedAt ?? '');
    const staleTicker = expiry.ticker < now || view?.ticker?.tickerFreshness === 'STALE' || olderTicker;
    const candleObservation = view?.bars.at(-1)?.observedAt;
    const staleCandles = expiry.candles < now || (candleObservation === view?.candles?.barsObservedAt
        && view?.candles?.barsFreshness === 'STALE');
    const cooling = cooldown(view) > 0;
    return {
        active, supported, online, ready, history, snapshot, view, chart, staleTicker, staleCandles,
        refresh() {
            if (!active || !isOnline() || !isVisible()) return;
            void history.refetch({cancelRefetch: false});
            if (supported && !cooling) void snapshot.refetch({cancelRefetch: false});
        },
    };
}

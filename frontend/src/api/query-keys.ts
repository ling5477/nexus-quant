export const authQueryKeys = {
    all: ['auth'] as const,
    currentUser: (accessToken?: string | null) => [...authQueryKeys.all, 'current-user', accessToken ?? 'anonymous'] as const,
};

export const accountQueryKeys = {
    all: ['exchange-accounts'] as const,
    list: (accessToken?: string | null) => [...accountQueryKeys.all, 'list', accessToken ?? 'anonymous'] as const,
};

export const strategyQueryKeys = {
    all: ['strategies'] as const,
    list: (searchVersion: number) => [...strategyQueryKeys.all, 'list', searchVersion] as const,
    detail: (strategyCode: string) => [...strategyQueryKeys.all, 'detail', strategyCode] as const,
};

export const scheduleQueryKeys = {
    all: ['strategy-schedules'] as const,
    list: (strategyId: string, searchVersion: number) => [...scheduleQueryKeys.all, 'list', strategyId, searchVersion] as const,
    detail: (scheduleId: string) => [...scheduleQueryKeys.all, 'detail', scheduleId] as const,
};

export const runQueryKeys = {
    all: ['strategy-runs'] as const,
    list: (request: { strategyId?: string; scheduleId?: string }, searchVersion: number) => [
        ...runQueryKeys.all,
        'list',
        request.strategyId ?? '',
        request.scheduleId ?? '',
        searchVersion,
    ] as const,
    detail: (runId: string) => [...runQueryKeys.all, 'detail', runId] as const,
};

export const researchQueryKeys = {
    all: ['research-configs'] as const,
    list: (sourceStrategyId: string, searchVersion: number) => [...researchQueryKeys.all, 'list', sourceStrategyId, searchVersion] as const,
    detail: (configId: string) => [...researchQueryKeys.all, 'detail', configId] as const,
};

export const backtestsQueryKeys = {
    all: ['backtest-configs'] as const,
    list: (researchConfigId: string, searchVersion: number) => [...backtestsQueryKeys.all, 'list', researchConfigId, searchVersion] as const,
    detail: (configId: string) => [...backtestsQueryKeys.all, 'detail', configId] as const,
};

export const evaluationsQueryKeys = {
    all: ['evaluation-runs'] as const,
    list: (request: { researchConfigId?: string; backtestConfigId?: string }, searchVersion: number) => [
        ...evaluationsQueryKeys.all,
        'list',
        request.researchConfigId ?? '',
        request.backtestConfigId ?? '',
        searchVersion,
    ] as const,
    detail: (runId: string) => [...evaluationsQueryKeys.all, 'detail', runId] as const,
};

export const publishesQueryKeys = {
    all: ['publish-runs'] as const,
    list: (request: { researchConfigId?: string; backtestConfigId?: string }, searchVersion: number) => [
        ...publishesQueryKeys.all,
        'list',
        request.researchConfigId ?? '',
        request.backtestConfigId ?? '',
        searchVersion,
    ] as const,
    detail: (runId: string) => [...publishesQueryKeys.all, 'detail', runId] as const,
};

export const tradeValidationQueryKeys = {
    all: ['trade-validation'] as const,
    lookup: (request: { orderId: string; accountId?: number; symbol?: string }, searchVersion: number) => [
        ...tradeValidationQueryKeys.all,
        'lookup',
        request.orderId,
        request.accountId ?? '',
        request.symbol ?? '',
        searchVersion,
    ] as const,
};

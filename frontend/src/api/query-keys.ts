export const authQueryKeys = {
    all: ['auth'] as const,
    currentUser: (accessToken?: string | null) => [...authQueryKeys.all, 'current-user', accessToken ?? 'anonymous'] as const,
};

export const adapterReadinessQueryKeys = {
    all: ['adapter-readiness'] as const,
    status: () => [...adapterReadinessQueryKeys.all, 'status'] as const,
};

export const operationalReadinessQueryKeys = {
    all: ['operational-readiness'] as const,
    status: () => [...operationalReadinessQueryKeys.all, 'status'] as const,
};

export const marketdataQueryKeys = {
    all: ['marketdata'] as const,
    barsAll: () => [...marketdataQueryKeys.all, 'bars'] as const,
    bars: (query: unknown) => [...marketdataQueryKeys.barsAll(), query] as const,
    readinessAll: () => [...marketdataQueryKeys.all, 'readiness'] as const,
    readiness: (query: unknown) => [...marketdataQueryKeys.readinessAll(), query] as const,
    qualityOverviewAll: () => [...marketdataQueryKeys.all, 'quality-overview'] as const,
    qualityOverview: (query: unknown) => [...marketdataQueryKeys.qualityOverviewAll(), query] as const,
    ingestionJobs: () => [...marketdataQueryKeys.all, 'ingestion-jobs'] as const,
    ingestionRuns: (jobId: string | null) => [...marketdataQueryKeys.all, 'ingestion-runs', jobId ?? 'none'] as const,
    datasets: () => [...marketdataQueryKeys.all, 'datasets'] as const,
};

export const accountQueryKeys = {
    all: ['exchange-accounts'] as const,
    list: (accessToken?: string | null) => [...accountQueryKeys.all, 'list', accessToken ?? 'anonymous'] as const,
    detail: (accountId: number) => [...accountQueryKeys.all, 'detail', accountId] as const,
    activeCredential: (accountId: number) => [...accountQueryKeys.all, 'active-credential', accountId] as const,
};

export const strategyQueryKeys = {
    all: ['strategies'] as const,
    list: (searchVersion: number) => [...strategyQueryKeys.all, 'list', searchVersion] as const,
    detail: (strategyCode: string) => [...strategyQueryKeys.all, 'detail', strategyCode] as const,
    versions: (strategyCode: string) => [...strategyQueryKeys.all, 'versions', strategyCode] as const,
    versionDetail: (strategyCode: string, versionId: string) => [
        ...strategyQueryKeys.all,
        'version-detail',
        strategyCode,
        versionId,
    ] as const,
};

export const strategyValidationQueryKeys = {
    all: ['strategy-validation'] as const,
    evaluationGate: (query: unknown) => [...strategyValidationQueryKeys.all, 'evaluation-gate', query ?? 'idle'] as const,
    paperShadowComparison: (query: unknown) => [...strategyValidationQueryKeys.all, 'paper-shadow-comparison', query ?? 'idle'] as const,
    shadowLivePreview: (query: unknown) => [...strategyValidationQueryKeys.all, 'shadow-live-preview', query ?? 'idle'] as const,
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
    pnlSnapshots: (runId: string) => [...backtestsQueryKeys.all, 'pnl-snapshots', runId] as const,
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
    detail: (evaluationId: string) => [...evaluationsQueryKeys.all, 'detail', evaluationId] as const,
};

export const publishesQueryKeys = {
    all: ['publish-runs'] as const,
    list: (request: {
        researchConfigId?: string;
        backtestConfigId?: string;
        strategyVersionId?: string
    }, searchVersion: number) => [
        ...publishesQueryKeys.all,
        'list',
        request.researchConfigId ?? '',
        request.backtestConfigId ?? '',
        request.strategyVersionId ?? '',
        searchVersion,
    ] as const,
    detail: (publishId: string) => [...publishesQueryKeys.all, 'detail', publishId] as const,
};

export const tradingWorkbenchQueryKeys = {
    all: ['trading-workbench'] as const,
    orders: (request: {
        accountId?: number;
        orderId?: string;
        venue?: string;
        symbol?: string;
        status?: string;
        environment?: string;
        page?: number;
        size?: number
    }, searchVersion: number) => [
        ...tradingWorkbenchQueryKeys.all,
        'orders',
        request.accountId ?? '',
        request.orderId ?? '',
        request.venue ?? '',
        request.symbol ?? '',
        request.status ?? '',
        request.environment ?? '',
        request.page ?? 0,
        request.size ?? 20,
        searchVersion,
    ] as const,
    lookup: (request: { orderId: string; accountId?: number; symbol?: string }, searchVersion: number) => [
        ...tradingWorkbenchQueryKeys.all,
        'lookup',
        request.orderId,
        request.accountId ?? '',
        request.symbol ?? '',
        searchVersion,
    ] as const,
};

export const paperTradingQueryKeys = {
    all: ['paper-trading-runs'] as const,
    list: (request: { publishId?: string; status?: string }, searchVersion: number) => [
        ...paperTradingQueryKeys.all,
        'list',
        request.publishId ?? '',
        request.status ?? '',
        searchVersion,
    ] as const,
    detail: (paperRunId: string) => [...paperTradingQueryKeys.all, 'detail', paperRunId] as const,
    summary: (paperRunId: string) => [...paperTradingQueryKeys.all, 'summary', paperRunId] as const,
    portfolioSummary: () => [...paperTradingQueryKeys.all, 'portfolio-summary'] as const,
    executionDiagnostics: () => [...paperTradingQueryKeys.all, 'execution-diagnostics'] as const,
    strategyEvaluations: () => [...paperTradingQueryKeys.all, 'strategy-evaluations'] as const,
    autoReviews: () => [...paperTradingQueryKeys.all, 'auto-reviews'] as const,
    orders: (paperRunId: string) => [...paperTradingQueryKeys.all, 'orders', paperRunId] as const,
    trades: (paperRunId: string) => [...paperTradingQueryKeys.all, 'trades', paperRunId] as const,
    positions: (paperRunId: string) => [...paperTradingQueryKeys.all, 'positions', paperRunId] as const,
    riskResults: (paperRunId: string) => [...paperTradingQueryKeys.all, 'risk-results', paperRunId] as const,
    equityCurve: (paperRunId: string) => [...paperTradingQueryKeys.all, 'equity-curve', paperRunId] as const,
    positionCurve: (paperRunId: string) => [...paperTradingQueryKeys.all, 'position-curve', paperRunId] as const,
    replay: (paperRunId: string) => [...paperTradingQueryKeys.all, 'replay', paperRunId] as const,
    emergencyStops: (paperRunId: string) => [...paperTradingQueryKeys.all, 'emergency-stops', paperRunId] as const,
    schedules: (paperRunId: string) => [...paperTradingQueryKeys.all, 'schedules', paperRunId] as const,
    fires: (scheduleId: string) => [...paperTradingQueryKeys.all, 'fires', scheduleId] as const,
    heartbeats: (paperRunId: string) => [...paperTradingQueryKeys.all, 'heartbeats', paperRunId] as const,
    dailyReports: (paperRunId: string) => [...paperTradingQueryKeys.all, 'daily-reports', paperRunId] as const,
    alerts: (paperRunId: string, status?: string, severity?: string) => [
        ...paperTradingQueryKeys.all,
        'alerts',
        paperRunId,
        status ?? '',
        severity ?? '',
    ] as const,
    recoveryEvents: (paperRunId: string, recoveryType?: string, status?: string) => [
        ...paperTradingQueryKeys.all,
        'recovery-events',
        paperRunId,
        recoveryType ?? '',
        status ?? '',
    ] as const,
    stabilityChecks: (paperRunId: string, status?: string) => [
        ...paperTradingQueryKeys.all,
        'stability-checks',
        paperRunId,
        status ?? '',
    ] as const,
};

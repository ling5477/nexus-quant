import type {StrategyVersionPageRequest} from '@/features/strategies/types/strategies';

export const experimentQueryKeys = {
    all: ['strategy-experiments'] as const,
    list: (owner: number | undefined, offset: number) => [...experimentQueryKeys.all, owner, 'list', offset] as const,
    detail: (owner: number | undefined, id: string | null) => [...experimentQueryKeys.all, owner, 'detail', id] as const,
};

export const authQueryKeys = {
    all: ['auth'] as const,
    currentUser: (accessToken?: string | null) =>
        [...authQueryKeys.all, 'current-user', accessToken ?? 'anonymous'] as const,
};

export const adapterReadinessQueryKeys = {
    all: ['adapter-readiness'] as const,
    status: () => [...adapterReadinessQueryKeys.all, 'status'] as const,
};

export const operationalReadinessQueryKeys = {
    all: ['operational-readiness'] as const,
    status: () => [...operationalReadinessQueryKeys.all, 'status'] as const,
};

export const schedulerQueryKeys = {
    all: ['scheduler-jobs'] as const,
    list: () => [...schedulerQueryKeys.all, 'list'] as const,
};

export const marketdataQueryKeys = {
    depth: (symbol: string) => ['marketdata', 'public-depth', 'OKX', 'SPOT', symbol] as const,
    all: ['marketdata'] as const,
    publicCapture: (owner: number | undefined, id: string | null) =>
        [...marketdataQueryKeys.all, 'public-capture-result', owner, id] as const,
    datasetOperation: (owner: number | undefined, id: string | null) =>
        [...marketdataQueryKeys.all, 'dataset-operation-result', owner, id] as const,
    barsAll: () => [...marketdataQueryKeys.all, 'bars'] as const,
    bars: (query: unknown) => [...marketdataQueryKeys.barsAll(), query] as const,
    readinessAll: () => [...marketdataQueryKeys.all, 'readiness'] as const,
    readiness: (query: unknown) => [...marketdataQueryKeys.readinessAll(), query] as const,
    qualityOverviewAll: () => [...marketdataQueryKeys.all, 'quality-overview'] as const,
    qualityOverview: (query: unknown) =>
        [...marketdataQueryKeys.qualityOverviewAll(), query] as const,
    datasets: () => [...marketdataQueryKeys.all, 'datasets'] as const,
    datasetPage: (owner: number | null, request: unknown, revision: number) =>
        [...marketdataQueryKeys.datasets(), 'page', owner, request, revision] as const,
    datasetDetail: (owner: number | null, datasetId: string | null) =>
        [...marketdataQueryKeys.datasets(), 'detail', owner, datasetId] as const,
};

export const instrumentQueryKeys = {
    all: ['instruments'] as const,
    list: (exchangeCode?: string) => [...instrumentQueryKeys.all, exchangeCode ?? 'ALL'] as const,
};

export const accountQueryKeys = {
    all: ['exchange-accounts'] as const,
    list: (accessToken?: string | null) =>
        [...accountQueryKeys.all, 'list', accessToken ?? 'anonymous'] as const,
    detail: (accountId: number) => [...accountQueryKeys.all, 'detail', accountId] as const,
    valuationCurrency: (
        accessToken: string | null,
        accountId: number | null,
        from: string,
        to: string,
    ) =>
        [
            ...accountQueryKeys.all,
            'valuation-currency',
            accessToken ?? 'anonymous',
            accountId,
            from,
            to,
        ] as const,
    activeCredential: (accountId: number) =>
        [...accountQueryKeys.all, 'active-credential', accountId] as const,
    credentialHistory: (owner: number | undefined, accountId: number, before?: number) =>
        [
            ...accountQueryKeys.all,
            'credential-history',
            owner,
            accountId,
            before ?? 'latest',
        ] as const,
};

export const strategyQueryKeys = {
    all: ['strategies'] as const,
    page: (
        ownerUserId: number | null,
        request: {
            strategyId?: string;
            strategyCode: string;
            strategyName?: string;
            strategyType: string;
            exchangeCode: string;
            tradeEnv: string;
            enabled: string;
            limit: number;
            offset: number;
        },
        searchVersion: number,
    ) =>
        [
            ...strategyQueryKeys.all,
            'page',
            ownerUserId,
            request.strategyId ?? '',
            request.strategyCode,
            request.strategyName ?? '',
            request.strategyType,
            request.exchangeCode,
            request.tradeEnv,
            request.enabled,
            request.limit,
            request.offset,
            searchVersion,
        ] as const,
    list: (searchVersion: number) => [...strategyQueryKeys.all, 'list', searchVersion] as const,
    detail: (strategyCode: string) => [...strategyQueryKeys.all, 'detail', strategyCode] as const,
    versions: (strategyCode: string) =>
        [...strategyQueryKeys.all, 'versions', strategyCode] as const,
    versionPage: (
        ownerUserId: number | null,
        strategyCode: string,
        request: StrategyVersionPageRequest,
        revision: number,
    ) =>
        [
            ...strategyQueryKeys.versions(strategyCode),
            'page',
            ownerUserId,
            request.strategyVersionId ?? '',
            request.versionName ?? '',
            request.version ?? null,
            request.status ?? '',
            request.limit,
            request.offset,
            revision,
        ] as const,
    scopedVersionDetail: (
        ownerUserId: number | null,
        strategyCode: string | null,
        versionId: string | null,
    ) =>
        [...strategyQueryKeys.all, 'version-detail', ownerUserId, strategyCode, versionId] as const,
    versionDetail: (strategyCode: string, versionId: string) =>
        [...strategyQueryKeys.all, 'version-detail', strategyCode, versionId] as const,
};

export const strategyValidationQueryKeys = {
    all: ['strategy-validation'] as const,
    overview: () => [...strategyValidationQueryKeys.all, 'overview'] as const,
    evaluationGate: (query: unknown) =>
        [...strategyValidationQueryKeys.all, 'evaluation-gate', query ?? 'idle'] as const,
    paperShadowComparison: (query: unknown) =>
        [...strategyValidationQueryKeys.all, 'paper-shadow-comparison', query ?? 'idle'] as const,
    shadowLivePreview: (query: unknown) =>
        [...strategyValidationQueryKeys.all, 'shadow-live-preview', query ?? 'idle'] as const,
};

/** Strategy Release 查询按 publishRecordId 隔离缓存，不接受 path/digest 等客户端事实。 */
export const strategyReleaseQueryKeys = {
    all: ['strategy-releases'] as const,
    admissionPreview: (publishRecordId: string | null) =>
        [
            ...strategyReleaseQueryKeys.all,
            'shadow-admission-preview',
            publishRecordId ?? 'idle',
        ] as const,
};

export const shadowValidationWorkflowQueryKeys = {
    all: ['shadow-validation'] as const,
    workflow: () => [...shadowValidationWorkflowQueryKeys.all, 'workflow'] as const,
    overview: () => [...shadowValidationWorkflowQueryKeys.workflow(), 'overview'] as const,
};

export const consistencyEvidenceQueryKeys = {
    all: ['paper-shadow', 'consistency', 'evidence'] as const,
    overview: () => [...consistencyEvidenceQueryKeys.all, 'overview'] as const,
};

export const incidentReplayQueryKeys = {
    all: ['incidents', 'replay'] as const,
    overview: () => [...incidentReplayQueryKeys.all, 'overview'] as const,
};

export const incidentReplayReviewQueryKeys = {
    all: ['incidents', 'replay', 'review'] as const,
    overview: () => [...incidentReplayReviewQueryKeys.all, 'overview'] as const,
};

export const evaluationArtifactPreviewQueryKeys = {
    all: ['strategy-validation', 'evaluation-artifacts', 'preview'] as const,
    overview: () => [...evaluationArtifactPreviewQueryKeys.all, 'overview'] as const,
};

export const validationOperationsRuntimeEvidenceQueryKeys = {
    all: ['validation-operations', 'runtime-evidence'] as const,
    overview: () => [...validationOperationsRuntimeEvidenceQueryKeys.all, 'overview'] as const,
};

export const validationReviewQueryKeys = {
    all: ['validation-review-cases'] as const,
    lists: () => [...validationReviewQueryKeys.all, 'list'] as const,
    list: (request: unknown) => [...validationReviewQueryKeys.lists(), request] as const,
    detail: (caseId: string) => [...validationReviewQueryKeys.all, 'detail', caseId] as const,
    events: (caseId: string) => [...validationReviewQueryKeys.all, 'events', caseId] as const,
};

export const paperShadowQueryKeys = {
    all: ['paper-shadow'] as const,
    consistencyDrilldown: (shadowRunId: string) =>
        [...paperShadowQueryKeys.all, 'consistency-drilldown', shadowRunId] as const,
};

export const shadowRunsQueryKeys = {
    all: ['shadow-runs'] as const,
    overview: () => [...shadowRunsQueryKeys.all, 'overview'] as const,
    list: (request: {
        status?: string | null;
        strategyVersionId?: string | null;
        datasetId?: string | null;
        paperRunId?: string | null;
        limit?: number;
        offset?: number;
    }) =>
        [
            ...shadowRunsQueryKeys.all,
            'list',
            request.status ?? '',
            request.strategyVersionId ?? '',
            request.datasetId ?? '',
            request.paperRunId ?? '',
            request.limit ?? 50,
            request.offset ?? 0,
        ] as const,
    detail: (shadowRunId: string) => [...shadowRunsQueryKeys.all, 'detail', shadowRunId] as const,
    events: (shadowRunId: string) => [...shadowRunsQueryKeys.all, 'events', shadowRunId] as const,
    snapshots: (shadowRunId: string) =>
        [...shadowRunsQueryKeys.all, 'snapshots', shadowRunId] as const,
    latestConsistencyReport: (shadowRunId: string) =>
        [...shadowRunsQueryKeys.all, 'latest-consistency-report', shadowRunId] as const,
};

export const scheduleQueryKeys = {
    all: ['strategy-schedules'] as const,
    page: (
        owner: number | null,
        request: {
            strategyId: string;
            scheduleId: string;
            status: string;
            scheduleType: string;
            limit: number;
            offset: number;
        },
        revision: number,
    ) =>
        [
            ...scheduleQueryKeys.all,
            'page',
            owner,
            request.strategyId,
            request.scheduleId,
            request.status,
            request.scheduleType,
            request.limit,
            request.offset,
            revision,
        ] as const,
    detail: (owner: number | null, scheduleId: string) =>
        [...scheduleQueryKeys.all, 'detail', owner, scheduleId] as const,
};

export const runQueryKeys = {
    all: ['strategy-runs'] as const,
    page: (
        owner: number | null,
        request: {
            strategyId: string;
            scheduleId: string;
            status: string;
            triggerType: string;
            limit: number;
            offset: number;
        },
        revision: number,
    ) =>
        [
            ...runQueryKeys.all,
            'page',
            owner,
            request.strategyId,
            request.scheduleId,
            request.status,
            request.triggerType,
            request.limit,
            request.offset,
            revision,
        ] as const,
    run: (owner: number | null, runId: string | null) =>
        [...runQueryKeys.all, 'run', owner, runId] as const,
    overview: (owner: number | null, runId: string | null) =>
        [...runQueryKeys.run(owner, runId), 'overview'] as const,
    related: (
        owner: number | null,
        runId: string | null,
        kind: 'orders' | 'trades' | 'risk-events' | 'ledger-entries' | 'business-events',
        request: {limit: number; offset: number},
    ) => [...runQueryKeys.run(owner, runId), kind, request.limit, request.offset] as const,
};

export const researchQueryKeys = {
    all: ['research-configs'] as const,
    page: (
        ownerUserId: number | null,
        request: {
            sourceStrategyId: string;
            researchConfigId: string;
            name: string;
            limit: number;
            offset: number;
        },
        searchVersion: number,
    ) =>
        [
            ...researchQueryKeys.all,
            'page',
            ownerUserId,
            request.sourceStrategyId,
            request.researchConfigId,
            request.name,
            request.limit,
            request.offset,
            searchVersion,
        ] as const,
    list: (sourceStrategyId: string, searchVersion: number) =>
        [...researchQueryKeys.all, 'list', sourceStrategyId, searchVersion] as const,
    detail: (configId: string) => [...researchQueryKeys.all, 'detail', configId] as const,
};

export const backtestsQueryKeys = {
    all: ['backtest-configs'] as const,
    page: (
        ownerUserId: number | null,
        request: {
            researchConfigId: string;
            backtestConfigId: string;
            name: string;
            limit: number;
            offset: number;
        },
        searchVersion: number,
    ) =>
        [
            ...backtestsQueryKeys.all,
            'page',
            ownerUserId,
            request.researchConfigId,
            request.backtestConfigId,
            request.name,
            request.limit,
            request.offset,
            searchVersion,
        ] as const,
    list: (researchConfigId: string, searchVersion: number) =>
        [...backtestsQueryKeys.all, 'list', researchConfigId, searchVersion] as const,
    detail: (configId: string) => [...backtestsQueryKeys.all, 'detail', configId] as const,
    pnlSnapshots: (runId: string) => [...backtestsQueryKeys.all, 'pnl-snapshots', runId] as const,
};

export const evaluationsQueryKeys = {
    all: ['evaluation-runs'] as const,
    page: (
        owner: number | null,
        request: {
            researchConfigId: string;
            backtestConfigId: string;
            backtestRunId: string;
            evaluationStatus: string;
            limit: number;
            offset: number;
        },
        version: number,
    ) =>
        [
            ...evaluationsQueryKeys.all,
            'page',
            owner,
            request.researchConfigId,
            request.backtestConfigId,
            request.backtestRunId,
            request.evaluationStatus,
            request.limit,
            request.offset,
            version,
        ] as const,
    forRun: (runId: string) => [...evaluationsQueryKeys.all, 'run', runId] as const,
    list: (
        request: {researchConfigId?: string; backtestConfigId?: string},
        searchVersion: number,
    ) =>
        [
            ...evaluationsQueryKeys.all,
            'list',
            request.researchConfigId ?? '',
            request.backtestConfigId ?? '',
            searchVersion,
        ] as const,
    detail: (evaluationId: string) =>
        [...evaluationsQueryKeys.all, 'detail', evaluationId] as const,
};

export const publishesQueryKeys = {
    all: ['publish-runs'] as const,
    page: (
        owner: number | null,
        request: {
            researchConfigId: string;
            backtestConfigId: string;
            sourceStrategyId: string;
            strategyVersionId: string;
            publishStatus: string;
            limit: number;
            offset: number;
        },
        version: number,
    ) =>
        [
            ...publishesQueryKeys.all,
            'page',
            owner,
            request.researchConfigId,
            request.backtestConfigId,
            request.sourceStrategyId,
            request.strategyVersionId,
            request.publishStatus,
            request.limit,
            request.offset,
            version,
        ] as const,
    forRun: (ownerUserId: number | null, runId: string) =>
        [...publishesQueryKeys.all, 'run', ownerUserId, runId] as const,
    list: (
        request: {
            researchConfigId?: string;
            backtestConfigId?: string;
            strategyVersionId?: string;
        },
        searchVersion: number,
    ) =>
        [
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
    accountFacts: (
        owner: number | undefined,
        kind: 'orders' | 'trades' | 'positions',
        request: {accountId: number; page: number; size: number; symbol?: string; venue?: string; environment?: string} | null,
    ) => ['trading-workbench', 'account-facts', owner ?? '', kind, request] as const,
    cancelTarget: (
        owner: number | undefined,
        account: number | null,
        orderId: string | undefined,
    ) => ['trading-workbench', 'cancel-target', owner ?? '', account ?? '', orderId ?? ''] as const,
    readiness: (userId: number | undefined, accountId: number, exchangeCode: string) =>
        ['trading-workbench', 'readiness', userId, accountId, exchangeCode, 'SPOT'] as const,
    orders: (
        request: {
            accountId?: number;
            orderId?: string;
            venue?: string;
            symbol?: string;
            status?: string;
            environment?: string;
            page?: number;
            size?: number;
        },
        searchVersion: number,
    ) =>
        [
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
    lookup: (
        request: {orderId: string; accountId?: number; symbol?: string},
        searchVersion: number,
    ) =>
        [
            ...tradingWorkbenchQueryKeys.all,
            'lookup',
            request.orderId,
            request.accountId ?? '',
            request.symbol ?? '',
            searchVersion,
        ] as const,
};

export const paperTradingQueryKeys = {
    legacyAnalysis: (userId: number | undefined) =>
        ['paper-trading-runs', 'legacy-analysis', userId] as const,
    analysisCatalog: (userId: number | undefined, offset: number) =>
        ['paper-trading-runs', 'analysis-catalog', userId, offset] as const,
    analysis: (userId: number | undefined, paperRunId: string) =>
        ['paper-trading-runs', 'analysis', userId, paperRunId] as const,
    performance: (
        userId: number | undefined,
        paperRunId: string,
        from: string | null,
        to: string | null,
    ) => ['paper-trading-runs', 'performance', userId, paperRunId, from, to] as const,
    canonicalOrder: (paperRunId: string, orderId: string) =>
        ['paper-trading-runs', 'canonical-order', paperRunId, orderId] as const,
    canonicalFacts: (id: string) => ['paper-trading-runs', 'canonical-facts', id] as const,
    decisions: (id: string) => ['paper-trading-runs', 'decisions', id] as const,
    continuous: (id: string) => ['paper-trading-runs', 'continuous', id] as const,
    continuousCapabilities: ['paper-trading-runs', 'continuous-capabilities'] as const,
    all: ['paper-trading-runs'] as const,
    list: (request: {publishId?: string; status?: string}, searchVersion: number) =>
        [
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
    positions: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'positions', paperRunId] as const,
    riskResults: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'risk-results', paperRunId] as const,
    equityCurve: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'equity-curve', paperRunId] as const,
    positionCurve: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'position-curve', paperRunId] as const,
    replay: (paperRunId: string) => [...paperTradingQueryKeys.all, 'replay', paperRunId] as const,
    emergencyStops: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'emergency-stops', paperRunId] as const,
    schedules: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'schedules', paperRunId] as const,
    fires: (scheduleId: string) => [...paperTradingQueryKeys.all, 'fires', scheduleId] as const,
    heartbeats: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'heartbeats', paperRunId] as const,
    dailyReports: (paperRunId: string) =>
        [...paperTradingQueryKeys.all, 'daily-reports', paperRunId] as const,
    alerts: (paperRunId: string, status?: string, severity?: string) =>
        [...paperTradingQueryKeys.all, 'alerts', paperRunId, status ?? '', severity ?? ''] as const,
    recoveryEvents: (paperRunId: string, recoveryType?: string, status?: string) =>
        [
            ...paperTradingQueryKeys.all,
            'recovery-events',
            paperRunId,
            recoveryType ?? '',
            status ?? '',
        ] as const,
    stabilityChecks: (paperRunId: string, status?: string) =>
        [...paperTradingQueryKeys.all, 'stability-checks', paperRunId, status ?? ''] as const,
};
export const newsQueryKeys = {
    all: ['public-news'] as const,
    feed: () => ['public-news', 'coindesk-rss'] as const,
};
export const resourceQueryKeys = {
    all: ['jvm-resources'] as const,
    history: () => ['jvm-resources', 'history'] as const,
};

export const systemVersionQueryKeys = {
    all: ['system-version'] as const,
    current: (accessToken: string | null) =>
        [...systemVersionQueryKeys.all, accessToken ?? 'anonymous'] as const,
};

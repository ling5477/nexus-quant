export interface MarketdataBar {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    openTime: string;
    closeTime: string;
    openPrice: number;
    highPrice: number;
    lowPrice: number;
    closePrice: number;
    volume: number;
    quoteVolume?: number | null;
    tradeCount?: number | null;
    qualityStatus?: string | null;
}

export interface MarketdataBarsQuery {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    startTime: string;
    endTime: string;
    page?: number;
    size?: number;
}

export interface MarketdataReadinessQuery {
    exchangeCode: string;
    marketType?: string;
    symbol?: string;
    instrumentId?: string;
    interval: string;
    from?: string;
    to?: string;
}

export interface MarketdataQualityStatusSummary {
    okCount: number;
    gapSignalCount: number;
    invalidCount: number;
    unknownQualityCount: number;
    statuses: Record<string, number>;
}

export type MarketdataReadinessStatus =
    | 'FRESH'
    | 'STALE'
    | 'VERY_STALE'
    | 'GAP'
    | 'ERROR'
    | 'DISABLED'
    | 'UNKNOWN'
    | 'NO_DATA';

export type MarketdataReadinessSourceStatus =
    | 'ENABLED'
    | 'DISABLED'
    | 'DEGRADED'
    | 'ERROR'
    | 'RATE_LIMITED';

export type MarketdataReadinessSourceHealth =
    | 'HEALTHY'
    | 'DEGRADED'
    | 'RATE_LIMITED'
    | 'TIMEOUT'
    | 'ERROR'
    | 'UNKNOWN';

export type MarketdataReadinessGapStatus =
    | 'NONE'
    | 'PARTIAL'
    | 'GAP'
    | 'UNKNOWN';

export type MarketdataReadinessDataOrigin =
    | 'LOCAL_DB'
    | 'FIXTURE'
    | 'FAKE_SERVER'
    | 'PUBLIC_CANDIDATE'
    | 'UNKNOWN';

export type MarketdataReadinessErrorCategory =
    | 'NONE'
    | 'DISABLED'
    | 'POLICY_DENIED'
    | 'RATE_LIMITED'
    | 'TIMEOUT'
    | 'TEMPORARY_FAILURE'
    | 'INVALID_RESPONSE'
    | 'STALE'
    | 'GAP'
    | 'TRANSPORT_ERROR'
    | 'UNKNOWN';

export interface MarketdataReadinessSummary {
    exchangeCode: string;
    exchange: string;
    marketType: string;
    instrumentId: string;
    symbol: string;
    interval: string;
    timeframe: string;
    sourceCode: string;
    dataOrigin: MarketdataReadinessDataOrigin | string;
    status: MarketdataReadinessStatus | string;
    sourceStatus: MarketdataReadinessSourceStatus | string;
    freshnessStatus: MarketdataReadinessStatus | string;
    sourceHealthStatus: MarketdataReadinessStatus | string;
    sourceHealth: MarketdataReadinessSourceHealth | string;
    sourceHealthReason: string;
    gapStatus: MarketdataReadinessGapStatus | string;
    qualityStatusSummary: MarketdataQualityStatusSummary;
    barCount: number;
    firstBarTime?: string | null;
    lastBarTime?: string | null;
    expectedBarCount?: number | null;
    gapCount?: number | null;
    missingFrom?: string | null;
    missingTo?: string | null;
    unknownQualityCount: number;
    lastSuccessAt?: string | null;
    lastFailureAt?: string | null;
    lastObservedAt?: string | null;
    latencyMs?: number | null;
    errorRate?: number | null;
    errorCategory: MarketdataReadinessErrorCategory | string;
    staleAfterSeconds?: number | null;
    degradedReason?: string | null;
    disabledReason?: string | null;
    traceId?: string | null;
    requestId?: string | null;
    backendSupportLevel: string;
    generatedAt: string;
    updatedAt: string;
}

export type MarketdataSandboxSourceType =
    | 'LOCAL_DB'
    | 'FIXTURE'
    | 'FAKE_SERVER'
    | 'NO_EGRESS_SANDBOX'
    | 'PUBLIC_SANDBOX_CANDIDATE';

export type MarketdataSandboxReadiness =
    | 'FRESH'
    | 'STALE'
    | 'GAP'
    | 'ERROR'
    | 'DISABLED'
    | 'PENDING_BACKEND_SUPPORT';

export type MarketdataSandboxCapability =
    | 'bars'
    | 'instrument metadata'
    | 'ticker'
    | 'exchange status';

export interface CreateMarketdataIngestionJobRequest {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    startTime: string;
    endTime: string;
}

export interface MarketdataIngestionJob {
    jobId: string;
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    startTime: string;
    endTime: string;
    status: string;
    source: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

export interface MarketdataIngestionRun {
    jobId: string;
    runId: string;
    status: string;
    fetchedBars: number;
    insertedBars: number;
    updatedBars: number;
    skippedBars: number;
    startedAt: string;
    finishedAt?: string | null;
    requestedStartTime: string;
    requestedEndTime: string;
    actualStartTime?: string | null;
    actualEndTime?: string | null;
    errorMessage?: string | null;
}

export interface CreateMarketdataDatasetRequest {
    datasetName: string;
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    startTime: string;
    endTime: string;
}

export interface MarketdataDataset {
    datasetId: string;
    datasetName: string;
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    startTime: string;
    endTime: string;
    status: string;
    qualityStatus: string;
    barCount: number;
    gapCount: number;
    source: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
    requestJson: string;
}

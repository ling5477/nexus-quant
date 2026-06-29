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

export interface MarketdataReadinessSummary {
    exchangeCode: string;
    marketType: string;
    instrumentId: string;
    symbol: string;
    interval: string;
    status: string;
    freshnessStatus: string;
    sourceHealthStatus: string;
    sourceHealthReason: string;
    qualityStatusSummary: MarketdataQualityStatusSummary;
    barCount: number;
    firstBarTime?: string | null;
    lastBarTime?: string | null;
    expectedBarCount?: number | null;
    gapCount?: number | null;
    unknownQualityCount: number;
    lastSuccessAt?: string | null;
    lastFailureAt?: string | null;
    backendSupportLevel: string;
    generatedAt: string;
}

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

export interface MarketdataBar {
    exchangeCode: string;
    marketType: string;
    symbol: string;
    interval: string;
    openTime: string;
    closeTime: string;
    openPrice: string | number;
    highPrice: string | number;
    lowPrice: string | number;
    closePrice: string | number;
    volume: string | number;
    quoteVolume?: string | number | null;
    tradeCount?: number | null;
    qualityStatus?: string | null;
    source?: string | null;
    availableAt?: string | null;
    barId?: string | null;
    barIdentitySha256?: string | null;
    priceEventTimes?: PriceEventTimes;
}

/** 原始边界、可见时间和四价发生时点是不同事实；缺来源不能补成区间起止。 */
export interface PriceEventTimes {
    status: 'NOT_PROVIDED' | 'SOURCE_PROVIDED';
    sourceId: string | null;
    sourceName: string | null;
    documentSha256: string | null;
    openPriceAt: string | null;
    highPriceAt: string | null;
    lowPriceAt: string | null;
    closePriceAt: string | null;
    recordedAt: string | null;
}
export interface PriceEventSourceSubmission {
    sourceId: string;
    barId: string;
    expectedBarSha256: string;
    sourceName: string;
    documentJson: string;
}
export interface PriceEventSourceRecord {
    sourceId: string;
    barId: string;
    barIdentitySha256: string;
    sourceName: string;
    documentSha256: string;
    documentJson: string;
    barSnapshotJson: string;
    openPriceAt: string;
    highPriceAt: string;
    lowPriceAt: string;
    closePriceAt: string;
    createdByUserId: number;
    createdAt: string;
}

export interface PublicMarketCapture {
    datasetId: string;
    venue: 'OKX';
    marketType: 'SPOT';
    instrument: 'BTC-USDT';
    interval: '1h';
    start: string;
    end: string;
    barCount: number;
    observedAt: string;
    rawSha256: string;
    normalizedSha256: string;
    consumedSha256: string;
    replayVisibilityVersion: string;
    replayVisibilitySource: string;
    ruleObservedAt: string;
    ruleRawSha256: string;
    ruleSha256: string;
    rulePolicy: string;
}

/** 原请求回执独立于来源开关；PENDING只代表已登记，不能显示成捕获成功。 */
export interface PublicCaptureOperation {
    captureId: string;
    state: 'PENDING' | 'SUCCEEDED' | 'FAILED';
    start: string;
    end: string;
    createdAt: string;
    completedAt: string | null;
    result: PublicMarketCapture | null;
    failureCode: string | null;
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

export interface MarketdataQualityOverviewQuery {
    exchangeCode?: string;
    exchange?: string;
    marketType?: string;
    symbol?: string;
    interval?: string;
    sourceType?: string;
    dataOrigin?: string;
    datasetId?: string;
    from?: string;
    to?: string;
}

interface MarketdataQualityOverviewScope {
    exchangeCode?: string | null;
    marketType?: string | null;
    symbol?: string | null;
    interval?: string | null;
    sourceType?: string | null;
    dataOrigin?: string | null;
    datasetId?: string | null;
    from?: string | null;
    to?: string | null;
}

export interface MarketdataQualityMetric {
    value?: number | null;
    status: 'AVAILABLE' | 'UNKNOWN' | 'NOT_AVAILABLE' | string;
    reason?: string | null;
}

interface MarketdataQualityDataOriginSummary {
    requestedDataOrigin?: string | null;
    effectiveDataOrigin: string;
    localDbBars: number;
    fixtureBars: number;
    unknownOriginBars: number;
    supportLevel: string;
}

interface MarketdataQualityDatasetCoverageSummary {
    datasetCount: number;
    expectedBars?: number | null;
    actualBars?: number | null;
    missingBars?: number | null;
    duplicateBars?: number | null;
    invalidBars?: number | null;
    latestDatasetId?: string | null;
    latestCoverageAt?: string | null;
}

export interface MarketdataQualityIssue {
    code: string;
    severity: string;
    count: number;
    message: string;
}

export interface MarketdataQualityOverview {
    scope: MarketdataQualityOverviewScope;
    totalBars: number;
    expectedBars?: number | null;
    gapCount?: number | null;
    duplicateCount: MarketdataQualityMetric;
    outOfOrderCount: MarketdataQualityMetric;
    staleCount: MarketdataQualityMetric;
    latestBarTime?: string | null;
    earliestBarTime?: string | null;
    lastSuccessAt?: string | null;
    lastFailureAt?: string | null;
    lastIngestionRunId?: string | null;
    sourceHealth: string;
    freshnessStatus: string;
    qualityStatus: string;
    dataOriginSummary: MarketdataQualityDataOriginSummary;
    datasetCoverageSummary: MarketdataQualityDatasetCoverageSummary;
    topIssues: MarketdataQualityIssue[];
    generatedAt: string;
}

interface MarketdataQualityStatusSummary {
    okCount: number;
    gapSignalCount: number;
    invalidCount: number;
    unknownQualityCount: number;
    statuses: Record<string, number>;
}

type MarketdataReadinessStatus =
    | 'FRESH'
    | 'STALE'
    | 'VERY_STALE'
    | 'GAP'
    | 'ERROR'
    | 'DISABLED'
    | 'UNKNOWN'
    | 'NO_DATA';

type MarketdataReadinessSourceStatus =
    | 'ENABLED'
    | 'DISABLED'
    | 'DEGRADED'
    | 'ERROR'
    | 'RATE_LIMITED';

type MarketdataReadinessSourceHealth =
    | 'HEALTHY'
    | 'DEGRADED'
    | 'RATE_LIMITED'
    | 'TIMEOUT'
    | 'ERROR'
    | 'UNKNOWN';

type MarketdataReadinessGapStatus = 'NONE' | 'PARTIAL' | 'GAP' | 'UNKNOWN';

type MarketdataReadinessDataOrigin =
    | 'LOCAL_DB'
    | 'FIXTURE'
    | 'FAKE_SERVER'
    | 'PUBLIC_CANDIDATE'
    | 'UNKNOWN';

type MarketdataReadinessErrorCategory =
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

/** 目录过滤不代表绑定许可；执行仍由正式数据质量及冻结合同校验。 */
export interface DatasetPageRequest {
    datasetId?: string;
    datasetName?: string;
    exchangeCode?: string;
    marketType?: string;
    symbol?: string;
    interval?: string;
    status?: string;
    qualityStatus?: string;
    source?: string;
    maximumGapCount?: number;
    limit: number;
    offset: number;
}

export interface DatasetPage {
    items: MarketdataDataset[];
    total: number;
    limit: number;
    offset: number;
}

/** 原次覆盖记录独立于数据集的可变最新质量；计数必须完整且保持安全整数。 */
export interface DatasetCoverage {
    coverageId: string;
    datasetId: string;
    rangeStartTime: string;
    rangeEndTime: string;
    expectedBars: number;
    actualBars: number;
    missingBars: number;
    duplicateBars: number;
    invalidBars: number;
    qualityStatus: string;
    summaryJson: string;
    createdAt: string;
}

export type DatasetOperationRequest =
    | {
          kind: 'CREATE';
          operationId: string;
          datasetId: string;
          input: CreateMarketdataDatasetRequest;
      }
    | {kind: 'REFRESH'; operationId: string; datasetId: string; input: Record<string, never>};

export interface DatasetOperation {
    operationId: string;
    kind: 'CREATE' | 'REFRESH';
    datasetId: string;
    state: 'PENDING' | 'SUCCEEDED' | 'FAILED';
    input: CreateMarketdataDatasetRequest | Record<string, never>;
    createdAt: string;
    completedAt: string | null;
    result: {dataset: MarketdataDataset; coverage: DatasetCoverage} | null;
    failureCode:
        | 'DATASET_ID_OCCUPIED'
        | 'DATASET_NOT_FOUND'
        | 'PUBLIC_CAPTURE_DATASET_IMMUTABLE'
        | null;
}

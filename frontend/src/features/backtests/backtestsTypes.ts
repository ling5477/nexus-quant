export interface BacktestConfigListItem {
    backtestConfigId: string;
    researchConfigId: string;
    name: string;
    description: string;
    startTime: string;
    endTime: string;
    initialCapital: number | string | null;
    executionSpec: string;
    evaluationSpec: string;
    strategyVersionId?: string | null;
    strategyVersionSnapshotJson?: string | null;
    paramSnapshotJson?: string | null;
    configSnapshotJson?: string | null;
    datasetId?: string | null;
    datasetSnapshotJson?: string | null;
    configSnapshot: string;
    createdAt: string;
    updatedAt: string;
    /** 旧接口可能未携带生命周期元数据，缺失不能推断为可归档。 */
    status?: string;
    archivedAt?: string | null;
    archivedBy?: string | null;
    archiveReason?: string | null;
}

export interface BacktestsListFilters {
    researchConfigId: string;
    backtestConfigId: string;
    name: string;
}

export interface BacktestPageRequest extends BacktestsListFilters {
    limit: number;
    offset: number;
}

/** 总数由正式接口返回，保留配置中的原始金额字符串，不能从当前页推算。 */
export interface BacktestConfigPage {
    items: BacktestConfigListItem[];
    total: number;
    limit: number;
    offset: number;
}

export const defaultBacktestsListFilters: BacktestsListFilters = {
    researchConfigId: '',
    backtestConfigId: '',
    name: '',
};

export interface BacktestConfigCreateRequest {
    researchConfigId: string;
    name: string;
    description?: string;
    startTime: string;
    endTime: string;
    initialCapital: number;
    executionSpec: string;
    evaluationSpec: string;
}

export interface BacktestDatasetBindingRequest {
    datasetId: string;
}

export interface BacktestStrategyVersionBindingRequest {
    strategyVersionId: string;
}

export interface BacktestRunCreateRequest {
    backtestConfigId: string;
}

/**
 * SimPnlSnapshotItem — 回测运行的权益/PnL 快照(对应后端 GET /api/backtest-runs/{runId}/pnl-snapshots)。
 * 金额在 JSON 解析前保留精确十进制；时间为真实事件时点，相同时间按服务器的原估值顺序展示。
 */
export interface SimPnlSnapshotItem {
    simPnlSnapshotId: string;
    backtestRunId: string;
    snapshotTime: string;
    cashBalance: number | string | null;
    positionMarketValue: number | string | null;
    realizedPnl: number | string | null;
    unrealizedPnl: number | string | null;
    totalFee: number | string | null;
    totalSlippage: number | string | null;
    equity: number | string | null;
    netPnl: number | string | null;
    createdAt: string;
    /** 优惠币余额和估值分别保留，缺价为空，不当作零资产。 */
    discountAssetCurrency?: string | null;
    discountAssetBalance?: string | null;
    discountAssetQuoteValue?: string | null;
    discountConversionPrice?: string | null;
    discountPriceObservedAt?: string | null;
    discountPriceVisibleAt?: string | null;
    discountPriceSourceSha256?: string | null;
    discountPriceSourceJson?: string | null;
    /** 原估值顺序由服务端提供；旧资料缺失时不以数组位置补造。 */
    valuationSequence?: string | null;
}

export interface BacktestRunDetailItem {
    evaluationStatus?: string | null;
    publishStatus?: string | null;
    failureCode?: string | null;
    backtestRunId: string;
    backtestConfigId: string;
    researchConfigId: string;
    sourceStrategyId: string;
    strategyVersionId: string | null;
    strategyVersionSnapshotJson: string | null;
    paramSnapshotJson: string | null;
    configSnapshotJson: string | null;
    datasetSnapshotJson: string | null;
    status: string;
    requestedAt: string;
    startedAt: string | null;
    finishedAt: string | null;
}

/** 原币费用与quote估值分别返回；旧成交缺少支付资料时保持null，不推测历史币种。 */
export interface BacktestSimTrade {
    simTradeId: string;
    simOrderId: string;
    backtestRunId: string;
    symbol: string;
    side: string;
    quantity: string;
    tradePrice: string;
    feeAmount: string;
    slippageAmount: string;
    tradedAt: string;
    feeCurrency: string | null;
    feeNativeAmount: string | null;
    feePaymentStatus: string | null;
    feeValuationScale: number | null;
    feeConversionPrice?: string | null;
    feePriceObservedAt?: string | null;
    feePriceVisibleAt?: string | null;
    feePriceSourceSha256?: string | null;
    feePriceSourceJson?: string | null;
    /** 原执行顺序使用字符串，保持超过 JavaScript 安全整数范围时的精确身份。 */
    executionSequence?: string | null;
}

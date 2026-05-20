export interface PaperTradingRunItem {
    paperRunId: string;
    publishId: string;
    strategyVersionId: string | null;
    status: string;
    tradeEnv: string;
    exchangeCode: string;
    marketType: string;
    symbol: string;
    intervalCode: string;
    startedAt: string | null;
    stoppedAt: string | null;
    publishSnapshotJson: string | null;
    strategyVersionSnapshotJson: string | null;
    datasetSnapshotJson: string | null;
    paramSnapshotJson: string | null;
    configSnapshotJson: string | null;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
}

export interface PaperTradingOrderItem {
    paperOrderId: string;
    paperRunId: string;
    symbol: string;
    side: string;
    orderType: string;
    quantity: string | number | null;
    price: string | number | null;
    status: string;
    reason: string | null;
    rawSignalJson: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface PaperTradingTradeItem {
    paperTradeId: string;
    paperOrderId: string;
    paperRunId: string;
    symbol: string;
    side: string;
    quantity: string | number | null;
    price: string | number | null;
    fee: string | number | null;
    tradedAt: string;
    createdAt: string;
}

export interface PaperTradingPositionItem {
    paperPositionId: string;
    paperRunId: string;
    symbol: string;
    quantity: string | number | null;
    avgPrice: string | number | null;
    unrealizedPnl: string | number | null;
    realizedPnl: string | number | null;
    updatedAt: string;
    createdAt: string;
}

export interface PaperTradingRunCreateRequest {
    publishId: string;
    tradeEnv: string;
    exchangeCode: string;
    marketType: string;
    symbol: string;
    intervalCode: string;
    configSnapshotJson?: string;
}

export interface PaperTradingListFilters {
    publishId: string;
    status: string;
}

export const defaultPaperTradingListFilters: PaperTradingListFilters = {
    publishId: '',
    status: '',
};

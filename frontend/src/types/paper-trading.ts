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

export interface PaperRiskCheckResultItem {
    riskResultId: string;
    paperRunId: string;
    checkType: string;
    status: string;
    severity: string;
    message: string | null;
    inputSnapshotJson: string | null;
    resultSnapshotJson: string | null;
    createdAt: string;
}

export interface EquityCurveSnapshotItem {
    equitySnapshotId: string;
    paperRunId: string;
    snapshotTime: string;
    totalEquity: string | number;
    cashBalance: string | number;
    positionValue: string | number;
    unrealizedPnl: string | number;
    realizedPnl: string | number;
    drawdown: string | number;
    source: string;
    createdAt: string;
}

export interface PositionCurveSnapshotItem {
    positionSnapshotId: string;
    paperRunId: string;
    symbol: string;
    snapshotTime: string;
    quantity: string | number;
    avgPrice: string | number;
    markPrice: string | number;
    positionValue: string | number;
    unrealizedPnl: string | number;
    realizedPnl: string | number;
    source: string;
    createdAt: string;
}

export interface TradeReplayRecordItem {
    replayRecordId: string;
    paperRunId: string;
    paperOrderId: string | null;
    paperTradeId: string | null;
    replayTime: string;
    eventType: string;
    symbol: string;
    side: string | null;
    price: string | number | null;
    quantity: string | number | null;
    reason: string | null;
    decisionSnapshotJson: string | null;
    riskSnapshotJson: string | null;
    marketSnapshotJson: string | null;
    createdAt: string;
}

export interface EmergencyStopEventItem {
    emergencyStopId: string;
    paperRunId: string;
    triggerType: string;
    status: string;
    reason: string | null;
    triggeredBy: string | null;
    triggeredAt: string;
    resolvedAt: string | null;
    requestJson: string | null;
    resultJson: string | null;
    createdAt: string;
}

export interface EmergencyStopRequest {
    triggerType: string;
    reason: string;
    triggeredBy?: string;
}

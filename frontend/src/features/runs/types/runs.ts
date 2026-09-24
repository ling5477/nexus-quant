export interface StrategyRunSummaryItem {
    strategyId: string;
    scheduleJobId: string | null;
    strategyRunId: string;
    requestId: string | null;
    triggerType: string;
    status: string;
    exchangeCode: string;
    accountId: number | null;
    tradeEnv: string;
    startedAt: string;
    finishedAt: string | null;
    errorMessage: string | null;
}

export interface StrategyRunOrderItem {
    orderId: string;
    symbol: string;
    side: string;
    orderType: string;
    price: string | null;
    quantity: string;
    status: string;
    createdAt: string;
}

export interface StrategyRunTradeItem {
    tradeId: string;
    orderId: string;
    symbol: string;
    side: string;
    price: string;
    quantity: string;
    fee: string | null;
    occurredAt: string;
}

export interface StrategyRunDetailItem {
    strategyId: string;
    scheduleJobId: string | null;
    strategyRunId: string;
    requestId: string | null;
    triggerType: string;
    status: string;
    exchangeCode: string;
    accountId: number | null;
    tradeEnv: string;
    startedAt: string;
    finishedAt: string | null;
    errorMessage: string | null;
    orders: StrategyRunOrderItem[];
    trades: StrategyRunTradeItem[];
    ledgerSummary: string | null;
    riskSummary: string | null;
    eventSummary: string | null;
}

export interface StrategyRunListFilters {
    strategyId: string;
    scheduleId: string;
    status: string;
    triggerType: string;
}

export const defaultStrategyRunListFilters: StrategyRunListFilters = {
    strategyId: '',
    scheduleId: '',
    status: '',
    triggerType: '',
};

export interface OrderView {
    orderId: string;
    accountId: number;
    venue: string;
    symbol: string;
    clientOrderId: string;
    externalOrderId: string | null;
    price: number | null;
    quantity: number;
    status: string;
    traceId: string;
}

export interface TradeView {
    tradeId: string;
    orderId: string;
    accountId: number;
    venue: string;
    symbol: string;
    externalOrderId: string | null;
    exchangeTradeId: string | null;
    price: number;
    quantity: number;
    fee: number | null;
    feeCurrency: string | null;
    tradeTs: string;
    traceId: string;
}

export interface PositionView {
    accountId: number;
    venue: string;
    symbol: string;
    quantity: number;
    availableQuantity: number;
    avgPrice: number;
    traceId: string;
}

export interface AccountBalanceView {
    currency: string;
    balance: number;
    available: number;
    frozen: number;
    snapshotTs: string;
    traceId: string;
}

export interface AccountView {
    accountId: number;
    venue: string;
    balances: AccountBalanceView[];
    traceId: string;
}

export interface TradeValidationLookupRequest {
    orderId: string;
    accountId?: number;
    symbol?: string;
}

export interface TradeValidationLookupResult {
    order: OrderView;
    latestTrade: TradeView | null;
    account: AccountView | null;
    position: PositionView | null;
}

export interface OrderSubmitRequest {
    accountId: number;
    strategyRunId?: string;
    venue: string;
    clientOrderId: string;
    symbol: string;
    side: string;
    orderType: string;
    price?: number;
    quantity: number;
}

export interface OrderCancelRequestBody {
    orderId?: string;
    accountId?: number;
    clientOrderId?: string;
    reason: string;
}

export interface ReconcileRunOnceRequest {
    venue?: string;
    limit?: number;
}

export interface RecoveryRunOnceRequest {
    venue?: string;
}

export interface OperationTriggerResponse {
    action: string;
    traceId: string;
    detail: string;
}

/** 经济字段以十进制文本跨过浏览器边界；账户身份与分页计数仍保持整数，缺失费用保留null。 */
export interface OrderView {
    orderId: string;
    accountId: number;
    venue: string;
    symbol: string;
    clientOrderId: string;
    externalOrderId: string | null;
    side: string;
    type: string;
    price: string | null;
    quantity: string;
    status: string;
    tradeEnv: string;
    createdAt: string | null;
    updatedAt: string | null;
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
    price: string;
    quantity: string;
    fee: string | null;
    feeCurrency: string | null;
    tradeTs: string;
    traceId: string;
}

export interface PositionView {
    accountId: number;
    venue: string;
    symbol: string;
    quantity: string;
    availableQuantity: string;
    avgPrice: string;
    traceId: string;
}

export interface AccountBalanceView {
    currency: string;
    balance: string;
    available: string;
    frozen: string;
    snapshotTs: string;
    traceId: string;
}

export interface AccountView {
    accountId: number;
    venue: string;
    balances: AccountBalanceView[];
    traceId: string;
}

export interface TradingWorkbenchLookupRequest {
    orderId: string;
    accountId?: number;
    symbol?: string;
}

export interface TradingOrderListRequest {
    accountId: number;
    orderId?: string;
    venue?: string;
    symbol?: string;
    status?: string;
    environment?: string;
    page?: number;
    size?: number;
}

export interface TradingOrderListResponse {
    items: OrderView[];
    page: number;
    size: number;
    total: number;
}

export interface AccountFactPage<T> {
    exchangeAccountId: number;
    tradingAccountId: number;
    venue: string;
    tradeEnv: string;
    source: string;
    items: T[];
    page: number;
    size: number;
    total: number;
}

export interface TradingWorkbenchLookupResult {
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
    price?: string;
    quantity: string;
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

/** 正式只读诊断仅含本地账户/凭据元数据和阻断说明，不返回交易许可或密钥。 */
export interface TradingPreflightRequest {
    accountId: number;
    exchangeCode: string;
    marketType: 'SPOT';
}

export interface TradingPreflightReadiness {
    accountId: number | null;
    exchangeCode: string;
    accountStatus: string;
    dataQualityStatus: string;
    riskPreflightStatus: string;
    blockers: Array<{code: string; severity: string; message: string}>;
    warnings: Array<{code: string; severity: string; message: string}>;
    requiredNextSteps: string[];
    generatedAt: string;
}

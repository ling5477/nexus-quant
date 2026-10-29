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

/** 概览不附带子数组；总数与不可关联能力由服务端明确返回。 */
export interface StrategyRunOverview {
    run: StrategyRunSummaryItem;
    orderCount: number;
    tradeCount: number;
    traceId: string;
    unavailableRelationships: string[];
    relationshipScopes?: {risk?: string; ledger?: string; events?: string};
}

export interface StrategyRunOrderItem {
    orderId: string;
    clientOrderId: string;
    exchangeOrderId: string | null;
    orderStatus: string;
    symbol: string;
    side: string;
    orderType: string;
    price: string | null;
    quantity: string;
    createdAt: string;
    updatedAt: string;
}

/** 金额、数量与费用是原始十进制文本，不允许经过number再格式化。 */
export interface StrategyRunTradeItem {
    tradeId: string;
    orderId: string;
    exchangeTradeId: string | null;
    exchangeOrderId: string | null;
    symbol: string;
    price: string;
    quantity: string;
    fee: string | null;
    feeCurrency: string | null;
    tradeTs: string;
}

export interface StrategyRunRelatedRequest {
    limit: number;
    offset: number;
}
export interface StrategyRunRelatedPage<T> {
    strategyRunId: string;
    items: T[];
    total: number;
    limit: number;
    offset: number;
}

export interface StrategyRunListFilters {
    strategyId: string;
    scheduleId: string;
    status: string;
    triggerType: string;
}

export interface StrategyRunPageRequest extends StrategyRunListFilters {
    limit: number;
    offset: number;
}

/** 总数由服务端按相同范围和筛选返回，不能从当前页长度推算。 */
export interface StrategyRunPage {
    items: StrategyRunSummaryItem[];
    total: number;
    limit: number;
    offset: number;
}

export const defaultStrategyRunListFilters: StrategyRunListFilters = {
    strategyId: '',
    scheduleId: '',
    status: '',
    triggerType: '',
};

/** 仅本运行Order级风控，不能代表账户与全局风险的完整集合。 */
export interface StrategyRunRiskEventItem {
    riskEventId: string;
    orderId: string;
    ruleId: string | null;
    decision: string;
    reason: string | null;
    severity: string | null;
    traceId: string;
    createdAt: string;
}

/** 原币分录与账户身份均保留文本；balanceAfter不作为当前资产使用。 */
export interface StrategyRunLedgerEntryItem {
    entryId: string;
    tradeId: string;
    orderId: string;
    accountId: string;
    currency: string;
    delta: string;
    balanceAfter: string | null;
    direction: string;
    refType: string;
    idempotencyKey: string | null;
    traceId: string;
    occurredAt: string;
}

/** 事件状态只是历史快照；三个时间不互相回填，账户身份不经浮点转换。 */
export interface StrategyRunBusinessEventItem {
    eventId: string;
    orderId: string;
    tradeId: string | null;
    accountId: string;
    eventType: string;
    topic: string;
    source: string | null;
    association: string;
    status: string | null;
    decision: string | null;
    result: string | null;
    rejectCode: string | null;
    occurredAt: string | null;
    publishedAt: string | null;
    recordedAt: string;
    traceId: string;
}

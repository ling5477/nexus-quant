import {apiClient} from '@/api/client';
import {validateResultPage} from '@/api/result-page';
import type {
    StrategyRunBusinessEventItem,
    StrategyRunRiskEventItem,
    StrategyRunLedgerEntryItem,
    StrategyRunOverview,
    StrategyRunOrderItem,
    StrategyRunTradeItem,
    StrategyRunRelatedRequest,
    StrategyRunRelatedPage,
    StrategyRunSummaryItem,
    StrategyRunPageRequest,
    StrategyRunPage,
} from '@/features/runs/types/runs';

const decimal = (value: unknown, nullable = false) =>
    (nullable && value === null) || (typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value));
const text = (value: unknown): value is string =>
    typeof value === 'string' && Boolean(value.trim());
const timestamp = (value: unknown) =>
    typeof value === 'string' && Number.isFinite(Date.parse(value));

/** 所属run、窗口和字段合同均须匹配，另一对象或已浮点化的金额不能进入有效明细。 */
function relatedPage<T>(
    value: unknown,
    runId: string,
    request: StrategyRunRelatedRequest,
    identity: (row: Record<string, unknown>) => string | null,
): StrategyRunRelatedPage<T> {
    if (
        !value ||
        typeof value !== 'object' ||
        (value as Record<string, unknown>).strategyRunId !== runId
    )
        throw new Error('Invalid run related scope');
    const page = validateResultPage<T>(value, request, (row) =>
        row && typeof row === 'object' ? identity(row as Record<string, unknown>) : null,
    );
    return {...page, strategyRunId: runId};
}

export const runsApi = {
    async page(request: StrategyRunPageRequest, signal?: AbortSignal): Promise<StrategyRunPage> {
        const {data} = await apiClient.get<unknown>('/strategy-runs/page', {
            params: request,
            signal,
        });
        // 另一范围的记录、错页或残缺状态不能在界面中变成有效运行历史。
        return validateResultPage<StrategyRunSummaryItem>(data, request, (value) => {
            if (!value || typeof value !== 'object') return null;
            const row = value as Record<string, unknown>;
            if (
                typeof row.strategyRunId !== 'string' ||
                !row.strategyRunId.trim() ||
                typeof row.strategyId !== 'string' ||
                !row.strategyId.trim() ||
                !['CREATED', 'DISPATCHING', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(
                    String(row.status),
                ) ||
                !['MANUAL', 'SCHEDULED', 'SCHEDULER', 'RECOVERY'].includes(
                    String(row.triggerType),
                ) ||
                typeof row.startedAt !== 'string' ||
                !Number.isFinite(Date.parse(row.startedAt)) ||
                (request.strategyId.trim() && row.strategyId !== request.strategyId.trim()) ||
                (request.scheduleId.trim() && row.scheduleJobId !== request.scheduleId.trim()) ||
                (request.status && row.status !== request.status) ||
                (request.triggerType && row.triggerType !== request.triggerType)
            )
                return null;
            return row.strategyRunId;
        });
    },
    async overview(runId: string, signal?: AbortSignal): Promise<StrategyRunOverview> {
        const {data} = await apiClient.get<StrategyRunOverview>(
            `/strategy-runs/${encodeURIComponent(runId)}/overview`,
            {signal},
        );
        if (
            !data ||
            data.run?.strategyRunId !== runId ||
            !text(data.run.strategyId) ||
            !['CREATED', 'DISPATCHING', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(
                data.run.status,
            ) ||
            !['MANUAL', 'SCHEDULED', 'SCHEDULER', 'RECOVERY'].includes(data.run.triggerType) ||
            !timestamp(data.run.startedAt) ||
            !Number.isSafeInteger(data.orderCount) ||
            data.orderCount < 0 ||
            !Number.isSafeInteger(data.tradeCount) ||
            data.tradeCount < 0 ||
            !Array.isArray(data.unavailableRelationships) ||
            !data.unavailableRelationships.every(text)
        )
            throw new Error('Invalid run overview');
        return data;
    },
    async orders(
        runId: string,
        request: StrategyRunRelatedRequest,
        signal?: AbortSignal,
    ): Promise<StrategyRunRelatedPage<StrategyRunOrderItem>> {
        const {data} = await apiClient.get<unknown>(
            `/strategy-runs/${encodeURIComponent(runId)}/orders/page`,
            {params: request, signal},
        );
        return relatedPage(data, runId, request, (row) =>
            text(row.orderId) &&
            text(row.clientOrderId) &&
            text(row.orderStatus) &&
            text(row.symbol) &&
            ['BUY', 'SELL'].includes(String(row.side)) &&
            ['LIMIT', 'MARKET'].includes(String(row.orderType)) &&
            decimal(row.price, true) &&
            decimal(row.quantity) &&
            timestamp(row.createdAt) &&
            timestamp(row.updatedAt)
                ? row.orderId
                : null,
        );
    },
    async trades(
        runId: string,
        request: StrategyRunRelatedRequest,
        signal?: AbortSignal,
    ): Promise<StrategyRunRelatedPage<StrategyRunTradeItem>> {
        const {data} = await apiClient.get<unknown>(
            `/strategy-runs/${encodeURIComponent(runId)}/trades/page`,
            {params: request, signal},
        );
        return relatedPage(data, runId, request, (row) =>
            text(row.tradeId) &&
            text(row.orderId) &&
            text(row.symbol) &&
            decimal(row.price) &&
            decimal(row.quantity) &&
            decimal(row.fee, true) &&
            (row.feeCurrency === null || text(row.feeCurrency)) &&
            timestamp(row.tradeTs)
                ? row.tradeId
                : null,
        );
    },
    async businessEvents(
        runId: string,
        request: StrategyRunRelatedRequest,
        signal?: AbortSignal,
    ): Promise<StrategyRunRelatedPage<StrategyRunBusinessEventItem>> {
        const {data} = await apiClient.get<unknown>(
            `/strategy-runs/${encodeURIComponent(runId)}/business-events/page`,
            {params: request, signal},
        );
        return relatedPage(data, runId, request, (row) =>
            text(row.eventId) &&
            text(row.orderId) &&
            (row.tradeId === null || text(row.tradeId)) &&
            typeof row.accountId === 'string' &&
            /^[1-9]\d*$/.test(row.accountId) &&
            text(row.eventType) &&
            text(row.topic) &&
            text(row.association) &&
            ['source', 'status', 'decision', 'result', 'rejectCode'].every(
                (field) => row[field] === null || typeof row[field] === 'string',
            ) &&
            (row.occurredAt === null || timestamp(row.occurredAt)) &&
            (row.publishedAt === null || timestamp(row.publishedAt)) &&
            timestamp(row.recordedAt) &&
            text(row.traceId)
                ? row.eventId
                : null,
        );
    },
    async riskEvents(
        runId: string,
        request: StrategyRunRelatedRequest,
        signal?: AbortSignal,
    ): Promise<StrategyRunRelatedPage<StrategyRunRiskEventItem>> {
        const {data} = await apiClient.get<unknown>(
            `/strategy-runs/${encodeURIComponent(runId)}/risk-events/page`,
            {params: request, signal},
        );
        return relatedPage(data, runId, request, (row) =>
            text(row.riskEventId) &&
            text(row.orderId) &&
            text(row.decision) &&
            (row.ruleId === null || typeof row.ruleId === 'string') &&
            (row.reason === null || typeof row.reason === 'string') &&
            (row.severity === null || typeof row.severity === 'string') &&
            text(row.traceId) &&
            timestamp(row.createdAt)
                ? row.riskEventId
                : null,
        );
    },
    async ledgerEntries(
        runId: string,
        request: StrategyRunRelatedRequest,
        signal?: AbortSignal,
    ): Promise<StrategyRunRelatedPage<StrategyRunLedgerEntryItem>> {
        const {data} = await apiClient.get<unknown>(
            `/strategy-runs/${encodeURIComponent(runId)}/ledger-entries/page`,
            {params: request, signal},
        );
        return relatedPage(data, runId, request, (row) =>
            text(row.entryId) &&
            text(row.tradeId) &&
            text(row.orderId) &&
            typeof row.accountId === 'string' &&
            /^[1-9]\d*$/.test(row.accountId) &&
            text(row.currency) &&
            decimal(row.delta) &&
            decimal(row.balanceAfter, true) &&
            ['DEBIT', 'CREDIT'].includes(String(row.direction)) &&
            row.refType === 'TRADE' &&
            (row.idempotencyKey === null || typeof row.idempotencyKey === 'string') &&
            text(row.traceId) &&
            timestamp(row.occurredAt)
                ? row.entryId
                : null,
        );
    },
};

import type {OperationTriggerResponse} from './types/trading-workbench';

/** 与正式OrderStatus合同一致；不把不存在的CREATED作为可提交筛选条件。 */
export const tradingOrderStatuses = [
    'NEW',
    'RISK_PASSED',
    'RISK_REJECTED',
    'SENT',
    'ACCEPTED',
    'VALIDATED',
    'SUBMITTING',
    'ACKED',
    'PARTIALLY_FILLED',
    'FILLED',
    'CANCEL_REQUESTED',
    'CANCEL_REJECTED',
    'CANCELLED',
    'REJECTED',
    'FAILED',
] as const;

export interface TradingOperationFeedback {
    tone: 'info' | 'success' | 'warning' | 'error';
    key: 'received' | 'rejected' | 'failed' | 'filled' | 'partial' | 'pending';
    status: string | null;
}

/** 回执返回不代表成交成功；只消费唯一、已知的正式状态，未知或歧义结果保持待核对。 */
export function tradingOperationFeedback(
    result: OperationTriggerResponse,
): TradingOperationFeedback {
    const fields = [...result.detail.matchAll(/(?:^|,)\s*status=([^,]*)/g)];
    const candidate = fields.length === 1 ? fields[0][1].trim() : null;
    const status =
        candidate && tradingOrderStatuses.some((value) => value === candidate) ? candidate : null;
    if (result.action !== 'placeOrder') return {tone: 'info', key: 'received', status};
    if (status === 'RISK_REJECTED' || status === 'REJECTED')
        return {tone: 'warning', key: 'rejected', status};
    if (status === 'FAILED') return {tone: 'error', key: 'failed', status};
    if (status === 'FILLED') return {tone: 'success', key: 'filled', status};
    if (status === 'PARTIALLY_FILLED') return {tone: 'info', key: 'partial', status};
    return {tone: 'info', key: 'pending', status};
}

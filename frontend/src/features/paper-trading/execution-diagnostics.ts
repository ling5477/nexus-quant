import type {SimAnalysis} from './api/analysis';

export type SimDiagnosticTarget = 'orders' | 'decisions' | 'risk' | 'run';
export type SimDiagnosticFindingKey =
    | 'invalidCounts'
    | 'failedRun'
    | 'notStarted'
    | 'noDecisions'
    | 'noOrders'
    | 'ordersWithoutTrades'
    | 'riskRejected'
    | 'failedOrders'
    | 'riskRecorded'
    | 'tradesRecorded'
    | 'decisionWindowLimited';

export interface SimDiagnosticFinding {
    key: SimDiagnosticFindingKey;
    tone: 'error' | 'warning' | 'info';
    target: SimDiagnosticTarget;
    count?: number;
}

export interface SimExecutionDiagnosticView {
    counts: {orders: number; trades: number; decisions: number; riskEvents: number} | null;
    findings: SimDiagnosticFinding[];
}

/**
 * 只解释同一实例的完整服务端计数，不能从最多500条的明细窗口判断零订单、零成交或整体健康。
 * 风险记录数不等于拒绝数；只有明确的RISK_REJECTED订单状态才形成拒绝提示。
 */
export function simExecutionDiagnosticView(input: {
    run: Pick<SimAnalysis['run'], 'status'>;
    metrics: SimAnalysis['metrics'];
}): SimExecutionDiagnosticView {
    const {metrics, run} = input;
    const validCount = (value: number) => Number.isSafeInteger(value) && value >= 0;
    const orders = metrics.orderStatusCounts.reduce((sum, row) => sum + row.count, 0);
    if (
        ![orders, metrics.tradeCount, metrics.decisionCount, metrics.riskEventCount].every(
            validCount,
        ) ||
        !metrics.orderStatusCounts.every((row) => validCount(row.count))
    ) {
        // 非法或超出JS精确整数范围的计数保留不可确认，禁止回退成0并据此生成归因。
        return {
            counts: null,
            findings: [{key: 'invalidCounts', tone: 'warning', target: 'run'}],
        };
    }
    const findings: SimDiagnosticFinding[] = [];
    const statusCount = (...statuses: string[]) =>
        metrics.orderStatusCounts
            .filter((row) => statuses.includes(row.status))
            .reduce((sum, row) => sum + row.count, 0);
    const add = (
        key: SimDiagnosticFindingKey,
        tone: SimDiagnosticFinding['tone'],
        target: SimDiagnosticTarget,
        count?: number,
    ) => findings.push({key, tone, target, count});

    if (run.status === 'FAILED') add('failedRun', 'error', 'run');
    if (run.status === 'CREATED' && orders === 0 && metrics.decisionCount === 0) {
        add('notStarted', 'info', 'run');
    } else {
        if (metrics.decisionCount === 0) add('noDecisions', 'info', 'decisions');
        if (orders === 0 && metrics.tradeCount === 0) add('noOrders', 'info', 'decisions');
    }
    if (orders > 0 && metrics.tradeCount === 0)
        add('ordersWithoutTrades', 'warning', 'orders', orders);
    const rejected = statusCount('RISK_REJECTED');
    if (rejected > 0) add('riskRejected', 'error', 'risk', rejected);
    const failed = statusCount('FAILED', 'REJECTED');
    if (failed > 0) add('failedOrders', 'warning', 'orders', failed);
    if (metrics.riskEventCount > 0) add('riskRecorded', 'info', 'risk', metrics.riskEventCount);
    if (metrics.tradeCount > 0) add('tradesRecorded', 'info', 'orders', metrics.tradeCount);
    if (metrics.decisionWindowLimited)
        add('decisionWindowLimited', 'info', 'decisions', metrics.decisionCount);

    return {
        counts: {
            orders,
            trades: metrics.tradeCount,
            decisions: metrics.decisionCount,
            riskEvents: metrics.riskEventCount,
        },
        findings,
    };
}

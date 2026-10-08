import {t} from '@/i18n';

const LEVEL_KEYS: Record<string, string> = {
    CRITICAL: 'paperAnalysisCritical', WARNING: 'paperAnalysisWarning', INFO: 'paperAnalysisInfo',
    HIGH: 'paperAnalysisHigh', MEDIUM: 'paperAnalysisMedium', LOW: 'paperAnalysisLow',
    UNAVAILABLE: 'paperAnalysisUnavailable',
    CREATED: 'paperRunCreatedStatus', RUNNING: 'paperRunRunningStatus', STOPPED: 'paperRunStoppedStatus',
    FAILED: 'paperRunFailedStatus', CANCELLED: 'paperRunCancelledStatus', CANCELED: 'paperRunCancelledStatus',
    APPLIED: 'paperRunAppliedStatus', RESOLVED: 'paperRunResolvedStatus', PASSED: 'paperRunPassedStatus',
    REJECTED: 'paperRunRejectedStatus', GENERATED: 'paperRunGeneratedStatus', UNKNOWN: 'unknown',
    RETURN: 'paperAnalysisReturnDimension', RISK: 'paperAnalysisRiskDimension', EXECUTION: 'paperAnalysisExecutionDimension',
    SAMPLE: 'paperAnalysisSampleDimension', BACKTEST_DEVIATION: 'paperAnalysisDeviationDimension',
    DATA_INSUFFICIENT: 'insufficientData', SAMPLE_INSUFFICIENT: 'insufficientSamples',
    EXECUTION_NO_FILL: 'paperAnalysisExecutionNoFill', EXECUTION_NO_ORDER: 'noOrders', NO_ORDER: 'noOrders',
    EXECUTION_ORDER_NO_FILL: 'ordersWithoutFills', ORDER_NO_FILL: 'ordersWithoutFills',
    EXECUTION_FILLED_LOSS: 'tradingLoss', FILLED_LOSS: 'tradingLoss', HIGH_DRAWDOWN: 'highDrawdown',
    RISK_BLOCKED_PRESENT: 'riskBlocked', RISK_BLOCKED: 'riskBlocked', FAILED_RUN_PRESENT: 'abnormalTerminalState',
    FAILED_RUN: 'abnormalTerminalState', BACKTEST_DATA_UNAVAILABLE: 'paperAnalysisBacktestUnavailable',
    BACKTEST_PAPER_DEVIATION_HIGH: 'highBacktestDeviation', BACKTEST_DEVIATION_HIGH: 'highBacktestDeviation',
    HEALTHY: 'paperAnalysisHealthy', RUNNING_NO_RESULT: 'runningWithoutResults',
    FILLED: 'paperRunFilledStatus', NEW: 'paperRunNewStatus', PARTIALLY_FILLED: 'paperRunPartialFillStatus',
};

/** 仅翻译展示枚举；未知值保留未知语义，原始编号由调用方作为诊断 title 保留。 */
export function localizedPaperLabel(value: string | null | undefined): string {
    if (!value) return '—';
    const key = LEVEL_KEYS[value];
    return key ? t(`pages:${key}`) : t('pages:paperAnalysisUnknownValue');
}

/** 每次渲染生成当前语言的选项快照，避免选择器缓存模块级 getter 数组。 */
export function localizedPaperOptions<T extends string>(options: ReadonlyArray<{label: string; value: T}>): Array<{label: string; value: T}> {
    return options.map(({label, value}) => ({label: LEVEL_KEYS[label] ? t(`pages:${LEVEL_KEYS[label]}`) : label, value}));
}

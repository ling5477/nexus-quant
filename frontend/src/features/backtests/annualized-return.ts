import {formatRatioPercent} from '@/utils/formatters';

const reasonKeys: Record<string, string> = {
    OUT_OF_RANGE: 'pages:annualizedReturnOutOfRange',
    INVALID_PERIOD: 'pages:annualizedReturnInvalidPeriod',
    NON_POSITIVE_GROWTH: 'pages:annualizedReturnNonPositiveGrowth',
};

/** 只消费正式评估的值和原因；旧记录/损坏JSON不猜测为溢出，也不将缺失值补成零。 */
export function annualizedReturnPresentation(
    value: number | string | null | undefined,
    metricsJson?: string | null,
) {
    const formatted = formatRatioPercent(value);
    if (formatted !== '—') return {value: formatted, reasonKey: null};
    let reasonKey = 'pages:annualizedReturnReasonUnavailable';
    if (metricsJson) {
        try {
            const metrics: unknown = JSON.parse(metricsJson);
            if (
                metrics &&
                typeof metrics === 'object' &&
                !Array.isArray(metrics) &&
                'annualizedReturnStatus' in metrics &&
                typeof metrics.annualizedReturnStatus === 'string'
            ) {
                if (Object.hasOwn(reasonKeys, metrics.annualizedReturnStatus))
                    reasonKey = reasonKeys[metrics.annualizedReturnStatus];
            }
        } catch {
            // 不影响其余已取得的报告指标；无法解析的旧元数据维持“原因未提供”。
        }
    }
    return {value: '—', reasonKey};
}

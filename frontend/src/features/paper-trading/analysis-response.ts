import {parseExactJson} from '@/api/exact-json';
import type {SimAnalysis, SimPerformance} from './api/analysis';
import type {StrategySimDecision, FrozenSimFeeCharge} from './types/paper-trading';

const decimalFields = new Set([
    'initialBudget',
    'cash',
    'positionQuantity',
    'markPrice',
    'equity',
    'pnl',
    'qty',
    'price',
    'fee',
    'delta',
    'avg_price',
    'quantity',
    'executionPrice',
    'feeRate',
    'slippageBps',
    'targetExposure',
    'budgetFraction',
    'initialQuantity',
    'initialQuoteValue',
    'initialCash',
    'balance',
    'reserved',
    'available',
    'quoteValue',
    'conversionPrice',
    'fee_quote_value',
    'fee_conversion_price',
    'discountAssetBalance',
    'discountAssetQuoteValue',
    'discountConversionPrice',
]);

/** 金额及模型输入保留十进制词法值，计数与业务版本仍为整数。 */
export function parseSimAnalysisResponse(text: string): SimAnalysis {
    return parseExactJson(text, decimalFields) as SimAnalysis;
}

/** 单条推进和决策列表使用同一十进制消费规则，原币模型的JSON字符串及空费率保持原样。 */
export function parseSimDecisionResponse(
    text: string,
): StrategySimDecision | StrategySimDecision[] {
    return parseExactJson(text, decimalFields) as StrategySimDecision | StrategySimDecision[];
}

/** 高级详情仅解释原收费选择；字段缺失或无效时保留不可确认，不猜币种、比例或预留。 */
export function parseFrozenSimFeeCharge(raw?: string | null): FrozenSimFeeCharge | null {
    if (!raw) return null;
    try {
        const parsed = parseExactJson(
            raw,
            new Set([
                'amount',
                'quoteValue',
                'positiveReserve',
                'quotePerAsset',
                'availableAfterReserves',
            ]),
        ) as Record<string, unknown>;
        if (
            !parsed ||
            Array.isArray(parsed) ||
            typeof parsed.currency !== 'string' ||
            !/^[A-Z0-9]{1,20}$/.test(parsed.currency) ||
            typeof parsed.paymentStatus !== 'string' ||
            !['amount', 'quoteValue', 'positiveReserve'].every(
                (key) =>
                    typeof parsed[key] === 'string' &&
                    /^-?\d+(\.\d+)?$/.test(parsed[key] as string),
            ) ||
            String(parsed.positiveReserve).startsWith('-')
        )
            return null;
        return parsed as unknown as FrozenSimFeeCharge;
    } catch {
        return null;
    }
}

/** 曲线坐标允许绘图时转换，统计和明细始终保留接口原十进制词法值。 */
export function parseSimPerformanceResponse(text: string): SimPerformance {
    return parseExactJson(
        text,
        new Set([
            ...decimalFields,
            'initialEquity',
            'finalEquity',
            'returnRatio',
            'maxDrawdown',
            'maxDrawdownRatio',
            'value',
            'riskFreeRate',
            'winRate',
            'netProfit',
            'netLoss',
            'profitLossRatio',
        ]),
    ) as SimPerformance;
}

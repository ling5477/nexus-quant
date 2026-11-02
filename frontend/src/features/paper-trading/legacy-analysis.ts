import {parseExactJson} from '@/api/exact-json';
import type {
    PaperAutoReviewsResponse,
    PaperExecutionDiagnosticsResponse,
    PaperStrategyEvaluationItem,
    PaperStrategyEvaluationsResponse,
    PaperPortfolioSummaryResponse,
    PaperPortfolioRunRef,
} from './types/paper-trading';

/** 三类历史分析来自同一授权窗口；生成时间表示读取时间，不代表经济事实发生时间。 */
export interface LegacyPaperAnalysis {
    source: 'LEGACY_PAPER';
    valuationStatus: 'UNIT_NOT_RECORDED';
    maxRuns: 200;
    includedRuns: number;
    hasMore: boolean;
    generatedAt: string;
    portfolio: PaperPortfolioSummaryResponse;
    runs: PaperPortfolioRunRef[];
    diagnostics: PaperExecutionDiagnosticsResponse;
    evaluations: PaperStrategyEvaluationsResponse;
    reviews: PaperAutoReviewsResponse;
}

const decimalFields = new Set([
    'currentEquity',
    'initialEquity',
    'totalPnl',
    'totalReturn',
    'maxDrawdown',
    'winRate',
    'averageReturn',
    'averageDrawdown',
    'backtestReturn',
    'paperReturn',
    'returnDeviation',
    'backtestMaxDrawdown',
    'paperMaxDrawdown',
    'drawdownDeviation',
    'totalInitialEquity',
    'totalCurrentEquity',
    'worstRunDrawdown',
    'worstDrawdown',
    'totalEquity',
    'peakEquity',
    'latestEquity',
    'currentDrawdown',
    'drawdown',
]);

function record(value: unknown): Record<string, unknown> {
    if (!value || typeof value !== 'object' || Array.isArray(value))
        throw new Error('Invalid legacy Paper analysis structure');
    return value as Record<string, unknown>;
}

/** 拒绝混源、窗口口径冲突和缺失结构；协议错误不能被画成零运行的成功空态。 */
export function parseLegacyPaperAnalysis(text: string): LegacyPaperAnalysis {
    const value = record(parseExactJson(text, decimalFields));
    const diagnostics = record(value.diagnostics);
    const evaluations = record(value.evaluations);
    const reviews = record(value.reviews);
    const portfolio = record(value.portfolio);
    const included = value.includedRuns;
    if (
        value.source !== 'LEGACY_PAPER' ||
        value.valuationStatus !== 'UNIT_NOT_RECORDED' ||
        value.maxRuns !== 200 ||
        typeof included !== 'number' ||
        !Number.isInteger(included) ||
        included < 0 ||
        included > 200 ||
        typeof value.hasMore !== 'boolean' ||
        (value.hasMore && included !== 200) ||
        typeof value.generatedAt !== 'string' ||
        !Number.isFinite(Date.parse(value.generatedAt)) ||
        record(diagnostics.overview).totalRuns !== included ||
        record(evaluations.overview).evaluatedRunCount !== included ||
        record(reviews.overview).totalRuns !== included ||
        record(portfolio.overview).totalRuns !== included ||
        !Array.isArray(value.runs) ||
        value.runs.length !== included
    )
        throw new Error('Inconsistent legacy Paper analysis window');
    for (const [section, fields] of [
        [
            diagnostics,
            ['causeDistribution', 'runDiagnostics', 'strategyDiagnostics', 'publishDiagnostics'],
        ],
        [evaluations, ['strategyEvaluations', 'publishEvaluations']],
        [reviews, ['runReviews', 'strategyReviews', 'publishReviews', 'issueClusters']],
    ] as const) {
        for (const field of fields)
            if (!Array.isArray(section[field]) || section[field].length > 200)
                throw new Error('Unbounded legacy Paper analysis rows');
    }
    if ((diagnostics.runDiagnostics as unknown[]).length !== included)
        throw new Error('Incomplete legacy Paper diagnostic window');
    record(evaluations.rankings);
    record(reviews.portfolioReview);
    for (const key of ['strategyGroups', 'publishGroups']) {
        if (!Array.isArray(portfolio[key]) || portfolio[key].length > 200)
            throw new Error('Invalid legacy portfolio groups');
    }
    // 无币种的金额合计属于协议错误，不能依赖前端文案掩盖后端错误结果。
    for (const [section, fields] of [
        [
            record(portfolio.overview),
            ['totalInitialEquity', 'totalCurrentEquity', 'totalPnl', 'totalReturn'],
        ],
        ...(['strategyGroups', 'publishGroups'] as const).flatMap((key) =>
            (portfolio[key] as unknown[]).map(
                (row) => [record(row), ['currentEquity', 'totalPnl', 'totalReturn']] as const,
            ),
        ),
        ...(evaluations.strategyEvaluations as unknown[]).map(
            (row) =>
                [
                    record(row),
                    ['currentEquity', 'initialEquity', 'totalPnl', 'totalReturn'],
                ] as const,
        ),
        ...(evaluations.publishEvaluations as unknown[]).map(
            (row) => [record(row), ['totalPnl', 'totalReturn']] as const,
        ),
    ] as const) {
        if (fields.some((field) => section[field] !== null && section[field] !== undefined))
            throw new Error(
                'Unrecorded valuation unit cannot produce cross-run monetary aggregates',
            );
    }
    if (
        (record(portfolio.portfolioCurve).points as unknown[]).length ||
        record(portfolio.highlights).topWinner
    )
        throw new Error(
            'Unrecorded valuation unit cannot produce an amount-ranked winner or combined capital curve',
        );
    const runIds = new Set((value.runs as unknown[]).map((run) => record(run).paperRunId));
    if (
        runIds.size !== included ||
        !(diagnostics.runDiagnostics as unknown[]).every((run) =>
            runIds.has(record(run).paperRunId),
        )
    )
        throw new Error('Inconsistent legacy portfolio run identities');
    const curve = record(portfolio.portfolioCurve);
    if (!Array.isArray(curve.points) || curve.points.length > 500)
        throw new Error('Invalid legacy portfolio curve window');
    record(portfolio.highlights);
    record(portfolio.dataQuality);
    return value as unknown as LegacyPaperAnalysis;
}

export const legacyFilterDefinitions = {
    legacyCause: [
        'all',
        'NO_ORDER',
        'ORDER_NO_FILL',
        'FILLED_LOSS',
        'RISK_BLOCKED',
        'DATA_INSUFFICIENT',
        'HIGH_DRAWDOWN',
        'FAILED_RUN',
        'RUNNING_NO_RESULT',
        'HEALTHY',
        'UNKNOWN',
    ],
    legacySeverity: ['all', 'INFO', 'WARNING', 'CRITICAL'],
    legacyRating: [
        'all',
        'STRONG_PAPER_PERFORMER',
        'WATCHLIST',
        'HIGH_RISK',
        'SAMPLE_INSUFFICIENT',
        'DATA_INSUFFICIENT',
        'EXECUTION_PROBLEM',
        'UNKNOWN',
    ],
    legacyConfidence: ['all', 'HIGH', 'MEDIUM', 'LOW'],
    legacyDeviation: ['all', 'LOW', 'MEDIUM', 'HIGH', 'UNAVAILABLE'],
    legacySort: [
        'compositeScore',
        'totalReturn',
        'maxDrawdown',
        'winRate',
        'sampleScore',
        'riskScore',
        'executionScore',
        'backtestDeviationScore',
        'latestRunTime',
    ],
    legacyDirection: ['desc', 'asc'],
    legacyReviewSeverity: ['all', 'INFO', 'WARNING', 'CRITICAL'],
    legacyReviewCause: [
        'all',
        'NO_ORDER',
        'ORDER_NO_FILL',
        'FILLED_LOSS',
        'RISK_BLOCKED',
        'DATA_INSUFFICIENT',
        'HIGH_DRAWDOWN',
        'FAILED_RUN',
        'RUNNING_NO_RESULT',
        'HEALTHY',
        'UNKNOWN',
        'BACKTEST_DEVIATION_HIGH',
        'SAMPLE_INSUFFICIENT',
    ],
    legacyDimension: ['all', 'run', 'strategy', 'publish', 'cluster'],
    legacyRisk: [
        'all',
        'riskBlocked',
        'noOrder',
        'orderNoFill',
        'hasFill',
        'dataInsufficient',
        'terminal',
        'highDrawdown',
    ],
    legacyRankSort: [
        'score',
        'totalReturn',
        'totalPnl',
        'worstDrawdown',
        'riskBlocked',
        'noOrder',
        'orderNoFill',
        'dataInsufficient',
        'lastRun',
    ],
    legacyRankDirection: ['desc', 'asc'],
    legacyRankFilter: [
        'all',
        'hasReturn',
        'dataInsufficient',
        'riskBlocked',
        'noOrder',
        'orderNoFill',
        'abnormalTerminal',
    ],
} as const;
export type LegacyFilterKey = keyof typeof legacyFilterDefinitions;
export type LegacyEvaluationSort = (typeof legacyFilterDefinitions.legacySort)[number];

/** 多项重置只生成一次新URL，保留来源和其他页面状态，避免连续导航互相覆盖。 */
export function updateLegacyParams(
    current: URLSearchParams,
    changes: Record<string, string | null>,
) {
    const next = new URLSearchParams(current);
    for (const [key, value] of Object.entries(changes)) {
        if (value === null) next.delete(key);
        else next.set(key, value);
    }
    return next;
}

export function invalidLegacyFilters(params: URLSearchParams): string[] {
    return Object.entries(legacyFilterDefinitions)
        .filter(
            ([key, choices]) =>
                params.has(key) && !(choices as readonly string[]).includes(params.get(key)!),
        )
        .map(([key]) => key);
}

interface DecimalOrder {
    negative: boolean;
    digits: string;
    point: number;
}

function decimalOrder(value: string | number | null | undefined): DecimalOrder | null {
    if (value == null || (typeof value === 'number' && !Number.isFinite(value))) return null;
    const text = String(value);
    if (text.length > 512) return null;
    const match = /^([+-]?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(text);
    if (!match) return null;
    const exponent = Number(match[4] ?? 0);
    if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 1000) return null;
    const digits = (match[2] + (match[3] ?? '')).replace(/^0+/, '');
    if (!digits) return {negative: false, digits: '0', point: 0};
    return {
        negative: match[1] === '-',
        digits: digits.replace(/0+$/, ''),
        point: digits.length - (match[3]?.length ?? 0) + exponent,
    };
}

/** 十进制按符号、数量级和有效位比较；不经浮点，缺失或越界值在升降序中都排末尾。 */
export function compareLegacyDecimals(
    left: string | number | null | undefined,
    right: string | number | null | undefined,
    direction: 'asc' | 'desc',
): number {
    const a = decimalOrder(left),
        b = decimalOrder(right);
    if (!a || !b) return a ? -1 : b ? 1 : 0;
    let result = 0;
    if (a.negative !== b.negative) result = a.negative ? -1 : 1;
    else {
        const aZero = a.digits === '0',
            bZero = b.digits === '0';
        if (aZero || bZero) result = aZero === bZero ? 0 : aZero ? -1 : 1;
        else if (a.point !== b.point) result = a.point < b.point ? -1 : 1;
        else {
            const size = Math.max(a.digits.length, b.digits.length);
            const first = a.digits.padEnd(size, '0'),
                second = b.digits.padEnd(size, '0');
            result = first < second ? -1 : first > second ? 1 : 0;
        }
        if (a.negative) result *= -1;
    }
    return result === 0 ? 0 : direction === 'asc' ? result : -result;
}

export function sortLegacyEvaluations(
    rows: PaperStrategyEvaluationItem[],
    dimension: LegacyEvaluationSort,
    direction: 'asc' | 'desc',
) {
    const value = (row: PaperStrategyEvaluationItem) =>
        dimension === 'latestRunTime'
            ? row.latestRunTime
                ? Date.parse(row.latestRunTime)
                : null
            : row[dimension];
    return [...rows].sort((a, b) => compareLegacyDecimals(value(a), value(b), direction));
}

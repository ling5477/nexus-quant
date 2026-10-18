import {afterEach, describe, expect, it, vi} from 'vitest';
import {AxiosError, type AxiosResponse} from 'axios';
import {apiClient} from '../../src/api/client';
import {readLegacyPaperAnalysis} from '../../src/features/paper-trading/api/legacy-analysis';
import {
    compareLegacyDecimals,
    invalidLegacyFilters,
    parseLegacyPaperAnalysis,
    sortLegacyEvaluations,
    updateLegacyParams,
} from '../../src/features/paper-trading/legacy-analysis';
import type {PaperStrategyEvaluationItem} from '../../src/features/paper-trading/types/paper-trading';

const session = vi.hoisted(() => ({
    accessToken: 'synthetic-legacy-session',
    tokenType: 'Bearer',
}));
vi.mock('../../src/store/auth-store', () => ({useAuthStore: {getState: () => session}}));
const originalAdapter = apiClient.defaults.adapter;
afterEach(() => {
    apiClient.defaults.adapter = originalAdapter;
    session.accessToken = 'synthetic-legacy-session';
});

/** 使用真实Axios响应转换和会话拦截器；只替换传输，不绕过错误归一化。 */
function respond(status: number, body: string, changeSession = false) {
    apiClient.defaults.adapter = async (config) => {
        const response: AxiosResponse = {
            data: body,
            status,
            statusText: '',
            headers: {},
            config,
        };
        if (changeSession) session.accessToken = 'synthetic-next-session';
        if (status >= 400)
            throw new AxiosError(
                'synthetic HTTP error',
                AxiosError.ERR_BAD_RESPONSE,
                config,
                undefined,
                response,
            );
        return response;
    };
}

describe('历史分析HTTP与严格数据校验边界', () => {
    it('503错误保留状态、错误码和追踪号，不误报会话取消', async () => {
        respond(503, JSON.stringify({code: 'SERVICE_UNAVAILABLE', traceId: 'synthetic-legacy-trace'}));
        await expect(readLegacyPaperAnalysis()).rejects.toMatchObject({
            status: 503,
            code: 'SERVICE_UNAVAILABLE',
            traceId: 'synthetic-legacy-trace',
            method: 'GET',
        });
    });
    it('非JSON网关失败仍保留HTTP状态', async () => {
        respond(502, '<html>synthetic gateway failure</html>');
        await expect(readLegacyPaperAnalysis()).rejects.toMatchObject({
            status: 502,
            code: 'UNKNOWN_ERROR',
        });
    });
    it('成功响应仍执行严格合同校验，残缺数据不能作为成功空态', async () => {
        respond(200, '{}');
        await expect(readLegacyPaperAnalysis()).rejects.toThrow(
            'Invalid legacy Paper analysis structure',
        );
        respond(200, JSON.stringify(snapshot()));
        await expect(readLegacyPaperAnalysis()).resolves.toMatchObject({
            includedRuns: 0,
            source: 'LEGACY_PAPER',
        });
    });
    it('真实会话变化仍拒收旧响应', async () => {
        respond(200, JSON.stringify(snapshot()), true);
        await expect(readLegacyPaperAnalysis()).rejects.toMatchObject({code: 'ERR_CANCELED'});
    });
});

function snapshot() {
    return {
        source: 'LEGACY_PAPER',
        valuationStatus: 'UNIT_NOT_RECORDED',
        maxRuns: 200,
        includedRuns: 0,
        hasMore: false,
        generatedAt: '2026-10-16T00:01:02Z',
        portfolio: {
            overview: {totalRuns: 0},
            strategyGroups: [],
            publishGroups: [],
            highlights: {},
            dataQuality: {},
            portfolioCurve: {points: []},
        },
        runs: [],
        diagnostics: {
            overview: {totalRuns: 0},
            runDiagnostics: [],
            causeDistribution: [],
            strategyDiagnostics: [],
            publishDiagnostics: [],
        },
        evaluations: {
            overview: {evaluatedRunCount: 0},
            strategyEvaluations: [],
            publishEvaluations: [],
            rankings: {},
        },
        reviews: {
            overview: {totalRuns: 0},
            portfolioReview: {},
            runReviews: [],
            strategyReviews: [],
            publishReviews: [],
            issueClusters: [],
        },
    };
}

describe('legacy Paper read boundary', () => {
    it('accepts a genuine empty historical source without inventing canonical facts', () => {
        expect(parseLegacyPaperAnalysis(JSON.stringify(snapshot())).includedRuns).toBe(0);
    });
    it('keeps decimal facts exact while counts and heuristic scores remain numbers', () => {
        const text = JSON.stringify(snapshot())
            .replace(
                '"runDiagnostics":[]',
                '"runDiagnostics":[{"paperRunId":"test","totalReturn":0.10000000000000000001,"maxDrawdown":-0.00000000000000000001,"orderCount":3,"tradeCount":2}]',
            )
            .replace('"includedRuns":0', '"includedRuns":1')
            .replace('"runs":[]', '"runs":[{"paperRunId":"test"}]')
            .replaceAll('"totalRuns":0', '"totalRuns":1')
            .replace('"evaluatedRunCount":0', '"evaluatedRunCount":1')
            .replace(
                '"strategyEvaluations":[]',
                '"strategyEvaluations":[{"compositeScore":20,"averageReturn":1e-30}]',
            );
        const result = parseLegacyPaperAnalysis(text);
        expect(result.diagnostics.runDiagnostics[0].totalReturn).toBe('0.10000000000000000001');
        expect(result.diagnostics.runDiagnostics[0].maxDrawdown).toBe('-0.00000000000000000001');
        expect(result.diagnostics.runDiagnostics[0].orderCount).toBe(3);
        expect(result.evaluations.strategyEvaluations[0].averageReturn).toBe('1e-30');
        expect(result.evaluations.strategyEvaluations[0].compositeScore).toBe(20);
    });
    it('rejects unknown units returning fabricated totals, combined curves or an absolute-amount winner', () => {
        const base = snapshot();
        for (const field of [
            'totalInitialEquity',
            'totalCurrentEquity',
            'totalPnl',
            'totalReturn',
        ]) {
            const value = structuredClone(base);
            Object.assign(value.portfolio.overview, {[field]: '610'});
            expect(() => parseLegacyPaperAnalysis(JSON.stringify(value))).toThrow(/valuation unit/);
        }
        const unsupported = {...base, valuationStatus: 'ASSUMED_USDT'};
        expect(() => parseLegacyPaperAnalysis(JSON.stringify(unsupported))).toThrow();
        Object.assign(base.portfolio.highlights, {topWinner: {paperRunId: 'unproven'}});
        expect(() => parseLegacyPaperAnalysis(JSON.stringify(base))).toThrow(/valuation unit/);
    });
    it('rejects mixed sources and inconsistent windows instead of showing a successful empty screen', () => {
        const mixed = snapshot();
        mixed.source = 'CANONICAL_SIM';
        expect(() => parseLegacyPaperAnalysis(JSON.stringify(mixed))).toThrow();
        const partial = snapshot();
        partial.reviews.overview.totalRuns = 1;
        expect(() => parseLegacyPaperAnalysis(JSON.stringify(partial))).toThrow();
        const missing = snapshot();
        delete (missing.evaluations as Partial<typeof missing.evaluations>).rankings;
        expect(() => parseLegacyPaperAnalysis(JSON.stringify(missing))).toThrow();
    });
    it('rejects invalid count, truncation and unbounded detail claims', () => {
        for (const count of [-1, 201, 0.5]) {
            const value = snapshot();
            value.includedRuns = count;
            expect(() => parseLegacyPaperAnalysis(JSON.stringify(value))).toThrow();
        }
        const invalid = snapshot();
        invalid.hasMore = true;
        expect(() => parseLegacyPaperAnalysis(JSON.stringify(invalid))).toThrow();
        const rows = snapshot();
        (rows.diagnostics as {runDiagnostics: unknown[]}).runDiagnostics = Array(201).fill({});
        expect(() => parseLegacyPaperAnalysis(JSON.stringify(rows))).toThrow();
    });
});

describe('legacy analysis navigation and exact sorting', () => {
    it('resets multiple filters atomically and preserves source and unrelated view state', () => {
        const current = new URLSearchParams(
            'analysisSource=legacy&legacyCause=FAILED_RUN&legacySeverity=CRITICAL&legacyView=reviews',
        );
        const next = updateLegacyParams(current, {legacyCause: null, legacySeverity: null});
        expect(next.toString()).toBe('analysisSource=legacy&legacyView=reviews');
        expect(current.get('legacyCause')).toBe('FAILED_RUN');
    });
    it('flags malformed filter values without silently interpreting them as another selection', () => {
        expect(
            invalidLegacyFilters(
                new URLSearchParams('legacyCause=invalid&legacyDirection=&legacySeverity=WARNING'),
            ),
        ).toEqual(['legacyCause', 'legacyDirection']);
        expect(
            invalidLegacyFilters(new URLSearchParams('legacyCause=UNKNOWN&legacyDirection=asc')),
        ).toEqual([]);
    });
    it('orders positive and negative high precision values that collapse under IEEE754', () => {
        const values = [
            '0.10000000000000000002',
            '-0.10000000000000000001',
            '0.10000000000000000001',
            '-0.10000000000000000002',
        ];
        expect([...values].sort((a, b) => compareLegacyDecimals(a, b, 'asc'))).toEqual([
            '-0.10000000000000000002',
            '-0.10000000000000000001',
            '0.10000000000000000001',
            '0.10000000000000000002',
        ]);
    });
    it('compares scientific notation, leading zeros, negative zero and different scales', () => {
        expect(compareLegacyDecimals('0001.2300e-3', '0.00123', 'asc')).toBe(0);
        expect(compareLegacyDecimals('-0.00', '0e20', 'desc')).toBe(0);
        expect(compareLegacyDecimals('1e-100', '0', 'asc')).toBeGreaterThan(0);
        expect(compareLegacyDecimals('-1e20', '-1e-20', 'asc')).toBeLessThan(0);
    });
    it('keeps missing, malformed and excessive values last in either direction', () => {
        for (const dir of ['asc', 'desc'] as const) {
            expect(compareLegacyDecimals(null, '-5', dir)).toBeGreaterThan(0);
            expect(compareLegacyDecimals('invalid', '0', dir)).toBeGreaterThan(0);
            expect(compareLegacyDecimals('1e9999999', '0', dir)).toBeGreaterThan(0);
            expect(compareLegacyDecimals('1'.repeat(513), '0', dir)).toBeGreaterThan(0);
        }
    });
    it('sorts a copied evaluation list and keeps missing results last', () => {
        const rows = [
            {strategyVersionId: 'low', totalReturn: '0.10000000000000000001'},
            {strategyVersionId: 'unknown', totalReturn: null},
            {strategyVersionId: 'high', totalReturn: '0.10000000000000000002'},
        ] as PaperStrategyEvaluationItem[];
        expect(
            sortLegacyEvaluations(rows, 'totalReturn', 'desc').map((r) => r.strategyVersionId),
        ).toEqual(['high', 'low', 'unknown']);
        expect(rows.map((r) => r.strategyVersionId)).toEqual(['low', 'unknown', 'high']);
    });
});

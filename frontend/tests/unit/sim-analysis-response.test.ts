import {describe, expect, it} from 'vitest';
import {
    parseSimAnalysisResponse,
    parseSimPerformanceResponse,
    parseSimDecisionResponse,
} from '../../src/features/paper-trading/analysis-response';
import {formatNqNumber} from '../../src/nq-design-system/format/nqFormat';

describe('canonical SIM analysis precision', () => {
    it('single and list decisions preserve exact decimals and absent scalar fees with native identity', () => {
        const text =
            '{"decisionId":"native-decision","quantity":9007199254740993.12345678,"executionPrice":100.00000001,"feeRate":null,"feeModelJson":"{\\"feeExchangeCode\\":\\"BINANCE\\"}","executionOpenTime":null}';
        const single = parseSimDecisionResponse(text);
        expect(Array.isArray(single)).toBe(false);
        if (Array.isArray(single)) throw new Error('unexpected decision list');
        expect(single.quantity).toBe('9007199254740993.12345678');
        expect(single.executionPrice).toBe('100.00000001');
        expect(single.feeRate).toBeNull();
        expect(single.feeModelJson).toBe('{"feeExchangeCode":"BINANCE"}');
        expect(single.executionOpenTime).toBeNull();
        expect(parseSimDecisionResponse(`[${text}]`)).toEqual([single]);
    });
    it('keeps history and statistics decimal words while preserving unavailable results and counts', () => {
        const result = parseSimPerformanceResponse(
            '{"paperRunId":"exact-run","pnl":-0.00000001,"maxDrawdown":0.00000002,"sharpe":{"value":1.234567890123456789,"riskFreeRate":0,"returnCount":2,"samplingSeconds":900},"outcomes":{"winRate":0.5,"netProfit":9007199254740993.12345678,"profitLossRatio":null,"winningCount":1},"tradesAfterCoverage":2,"samples":[{"equity":9007199254740993.12345678,"markPrice":81817.10000001}]}',
        );
        expect(result.sharpe.value).toBe('1.234567890123456789');
        expect(result.sharpe.returnCount).toBe(2);
        expect(result.outcomes.netProfit).toBe('9007199254740993.12345678');
        expect(result.outcomes.profitLossRatio).toBeNull();
        expect(result.samples[0].equity).toBe('9007199254740993.12345678');
        expect(result.pnl).toBe('-0.00000001');
        expect(result.tradesAfterCoverage).toBe(2);
    });
    it('preserves high precision money while keeping version and complete counts numeric', () => {
        const result = parseSimAnalysisResponse(
            '{"run":{"version":3,"canonicalAccountId":5},"facts":{"initialBudget":9007199254740993.12345678,"cash":99.74643775,"equity":99.74643775,"pnl":-0.25356225},"metrics":{"tradeCount":2,"orderStatusCounts":[{"status":"FILLED","count":501}]},"decisions":[{"quantity":0.00122035,"executionPrice":81817.1,"feeRate":0.001}]}',
        );
        expect(result.facts.initialBudget).toBe('9007199254740993.12345678');
        expect(result.facts.pnl).toBe('-0.25356225');
        expect(result.decisions[0].quantity).toBe('0.00122035');
        expect(result.run.version).toBe(3);
        expect(result.metrics.orderStatusCounts[0].count).toBe(501);
    });
    it('preserves zero, missing marks and signed rebates without mixing currencies', () => {
        const result = parseSimAnalysisResponse(
            '{"facts":{"cash":0,"markAsOf":null,"pnl":null},"metrics":{"fees":[{"currency":"USDT","totalFee":"-0.00000001","tradeCount":1,"measuredFeeCount":1},{"currency":"BTC","totalFee":null,"tradeCount":1,"measuredFeeCount":0}]}}',
        );
        expect(result.facts.cash).toBe('0');
        expect(result.facts.markAsOf).toBeNull();
        expect(result.facts.pnl).toBeNull();
        expect(result.metrics.fees.map((fee) => fee.totalFee)).toEqual(['-0.00000001', null]);
        expect(result.metrics.fees.map((fee) => fee.currency)).toEqual(['USDT', 'BTC']);
    });
    it('expands server decimal exponents exactly, including very small fees and large values', () => {
        expect(formatNqNumber('0E-8', {exact: true})).toBe('0');
        expect(formatNqNumber('-1.234567890123456789E-8', {exact: true})).toBe(
            '-0.00000001234567890123456789',
        );
        expect(formatNqNumber('9.00719925474099312345678E+18', {exact: true})).toBe(
            '9,007,199,254,740,993,123.45678',
        );
        expect(formatNqNumber('1e1000000', {exact: true})).toBe('1e1000000');
    });
});

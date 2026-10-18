import {describe, expect, it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import i18n from '@/i18n';
import {SimExecutionCode} from '@/features/paper-trading/components/SimExecutionDiagnostics';
import {simExecutionDiagnosticView} from '@/features/paper-trading/execution-diagnostics';

const input = (
    patch: Partial<Parameters<typeof simExecutionDiagnosticView>[0]['metrics']> = {},
    status = 'RUNNING',
) => ({
    run: {status},
    metrics: {
        orderStatusCounts: [],
        tradeCount: 0,
        riskEventCount: 0,
        decisionCount: 0,
        decisionWindowLimited: false,
        fees: [],
        ...patch,
    },
});

describe('Canonical SIM execution diagnostic evidence', () => {
    it('translates known decision meaning while retaining raw and unknown codes', async () => {
        const original = i18n.resolvedLanguage ?? 'zh-CN';
        try {
            for (const [locale, label] of [
                ['zh-CN', '已达到目标仓位'],
                ['en-US', 'Target position already reached'],
            ]) {
                await i18n.changeLanguage(locale);
                const html = renderToStaticMarkup(
                    createElement(SimExecutionCode, {kind: 'reason', value: 'ALREADY_AT_TARGET'}),
                );
                expect(html).toContain(label);
                expect(html).toContain('title="ALREADY_AT_TARGET"');
                expect(html).toContain('aria-label=');
                const unknown = renderToStaticMarkup(
                    createElement(SimExecutionCode, {kind: 'reason', value: 'FUTURE_REASON'}),
                );
                expect(unknown).toContain('FUTURE_REASON');
            }
        } finally {
            await i18n.changeLanguage(original);
        }
    });
    it('uses complete server counts even when no order or trade details are supplied', () => {
        const view = simExecutionDiagnosticView(
            input({
                orderStatusCounts: [{status: 'FILLED', count: 1200}],
                tradeCount: 1350,
                decisionCount: 2400,
                decisionWindowLimited: true,
            }),
        );
        expect(view.counts).toEqual({orders: 1200, trades: 1350, decisions: 2400, riskEvents: 0});
        expect(view.findings.map((row) => row.key)).toEqual([
            'tradesRecorded',
            'decisionWindowLimited',
        ]);
        expect(view.findings.find((row) => row.key === 'tradesRecorded')?.count).toBe(1350);
    });

    it('keeps a created empty instance as a normal initial state', () => {
        const view = simExecutionDiagnosticView(input({}, 'CREATED'));
        expect(view.findings).toEqual([
            {key: 'notStarted', tone: 'info', target: 'run', count: undefined},
        ]);
        expect(view.counts?.orders).toBe(0);
    });

    it('does not turn risk event counts, including approvals, into rejection counts', () => {
        const view = simExecutionDiagnosticView(input({riskEventCount: 42, decisionCount: 1}));
        expect(view.findings.some((row) => row.key === 'riskRecorded' && row.count === 42)).toBe(
            true,
        );
        expect(view.findings.some((row) => row.key === 'riskRejected')).toBe(false);
    });

    it('distinguishes risk rejection, order rejection and cancellation rejection', () => {
        const view = simExecutionDiagnosticView(
            input({
                orderStatusCounts: [
                    {status: 'RISK_REJECTED', count: 2},
                    {status: 'REJECTED', count: 3},
                    {status: 'FAILED', count: 4},
                    {status: 'CANCEL_REJECTED', count: 8},
                ],
                decisionCount: 20,
            }),
        );
        expect(view.findings.find((row) => row.key === 'riskRejected')?.count).toBe(2);
        expect(view.findings.find((row) => row.key === 'failedOrders')?.count).toBe(7);
        expect(view.findings.find((row) => row.key === 'ordersWithoutTrades')?.count).toBe(17);
    });

    it('does not assert failure for waiting, cancelled or unknown order statuses', () => {
        const view = simExecutionDiagnosticView(
            input({
                orderStatusCounts: [
                    {status: 'ACCEPTED', count: 1},
                    {status: 'CANCELLED', count: 1},
                    {status: 'FUTURE_UNKNOWN', count: 1},
                ],
                decisionCount: 3,
            }),
        );
        expect(view.findings.map((row) => row.key)).toEqual(['ordersWithoutTrades']);
    });

    it('does not turn fills or signed fee observations into profitability or health findings', () => {
        const view = simExecutionDiagnosticView(
            input({
                orderStatusCounts: [{status: 'FILLED', count: 1}],
                tradeCount: 1,
                decisionCount: 1,
                fees: [
                    {
                        currency: 'BNB',
                        totalFee: '-0.000000000000000001',
                        tradeCount: 1,
                        measuredFeeCount: 1,
                    },
                ],
            }),
        );
        expect(view.findings.map((row) => row.key)).toEqual(['tradesRecorded']);
    });

    it('keeps lifecycle failure visible alongside actual fills', () => {
        const view = simExecutionDiagnosticView(
            input(
                {
                    orderStatusCounts: [{status: 'FILLED', count: 1}],
                    tradeCount: 1,
                    decisionCount: 1,
                },
                'FAILED',
            ),
        );
        expect(view.findings.map((row) => row.key)).toEqual(['failedRun', 'tradesRecorded']);
    });

    it.each([-1, 0.5, Number.NaN, Number.POSITIVE_INFINITY, Number.MAX_SAFE_INTEGER + 1])(
        'does not replace an invalid count %s with a false zero',
        (value) => {
            const view = simExecutionDiagnosticView(input({tradeCount: value}));
            expect(view.counts).toBeNull();
            expect(view.findings.map((row) => row.key)).toEqual(['invalidCounts']);
        },
    );

    it('rejects unsafe sums and negative members even when the total appears valid', () => {
        for (const counts of [
            [Number.MAX_SAFE_INTEGER, 1],
            [-1, 2],
        ]) {
            const view = simExecutionDiagnosticView(
                input({orderStatusCounts: counts.map((count) => ({status: 'NEW', count}))}),
            );
            expect(view.counts).toBeNull();
            expect(view.findings.map((row) => row.key)).toEqual(['invalidCounts']);
        }
    });
});

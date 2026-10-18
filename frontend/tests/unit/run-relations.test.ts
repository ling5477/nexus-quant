import {afterEach, describe, expect, it, vi} from 'vitest';
import {apiClient} from '../../src/api/client';
import {runQueryKeys} from '../../src/api/query-keys';
import {runsApi} from '../../src/features/runs/api/runs';
import {runLedgerPostingRole} from '../../src/features/runs/components/StrategyRunRelationTables';

const request = {limit: 1, offset: 0};
const ledger = {
    entryId: 'entry',
    tradeId: 'trade',
    orderId: 'order',
    accountId: '9007199254740993',
    currency: 'USDT',
    delta: '-9007199254740993.12345678',
    balanceAfter: null,
    direction: 'DEBIT',
    refType: 'TRADE',
    idempotencyKey: 'trade:LEDGER:1',
    traceId: 'trace',
    occurredAt: '2026-10-01T00:00:00Z',
};
const risk = {
    riskEventId: 'risk',
    orderId: 'order',
    ruleId: null,
    decision: 'REJECT',
    reason: null,
    severity: null,
    traceId: 'trace',
    createdAt: ledger.occurredAt,
};
afterEach(() => vi.restoreAllMocks());

describe('typed run risk and trade ledger relations', () => {
    it('uses an exact encoded scope, bounded windows and cancellation for both relationships', async () => {
        const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
            data: {strategyRunId: 'run/1', items: [ledger], total: 1, ...request},
        });
        const signal = new AbortController().signal;
        expect((await runsApi.ledgerEntries('run/1', request, signal)).items[0]).toEqual(ledger);
        expect(get).toHaveBeenCalledWith('/strategy-runs/run%2F1/ledger-entries/page', {
            params: request,
            signal,
        });
        get.mockResolvedValue({
            data: {strategyRunId: 'run/1', items: [risk], total: 1, ...request},
        });
        expect((await runsApi.riskEvents('run/1', request, signal)).items[0]).toEqual(risk);
        expect(get).toHaveBeenLastCalledWith('/strategy-runs/run%2F1/risk-events/page', {
            params: request,
            signal,
        });
    });
    it('rejects another run, wrong reference types and already-rounded monetary or account facts', async () => {
        for (const delta of [
            {strategyRunId: 'other'},
            {items: [{...ledger, refType: 'ORDER'}]},
            {items: [{...ledger, delta: -9007199254740994}]},
            {items: [{...ledger, accountId: 9007199254740994}]},
            {items: []},
        ]) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({
                data: {strategyRunId: 'run', items: [ledger], total: 1, ...request, ...delta},
            });
            await expect(runsApi.ledgerEntries('run', request)).rejects.toThrow();
        }
    });
    it('preserves zero, negative and positive changes without summing posting legs', async () => {
        for (const delta of ['0.00000000', '-0.00000001', '0.00000001']) {
            vi.spyOn(apiClient, 'get').mockResolvedValue({
                data: {strategyRunId: 'run', items: [{...ledger, delta}], total: 1, ...request},
            });
            expect((await runsApi.ledgerEntries('run', request)).items[0].delta).toBe(delta);
        }
    });
    it('classifies only the formal key for the same trade and preserves unknown roles', () => {
        expect(runLedgerPostingRole(ledger)).toBe('CASH_PRINCIPAL');
        for (const [suffix, role] of [
            ['2', 'CONTRA_PRINCIPAL'],
            ['FEE_1', 'CASH_FEE'],
            ['FEE_2', 'CONTRA_FEE'],
        ])
            expect(
                runLedgerPostingRole({...ledger, idempotencyKey: `trade:LEDGER:${suffix}`}),
            ).toBe(role);
        expect(runLedgerPostingRole({...ledger, idempotencyKey: 'other:LEDGER:FEE_1'})).toBe(
            'UNKNOWN',
        );
        expect(runLedgerPostingRole({...ledger, idempotencyKey: null})).toBe('UNKNOWN');
        expect(runLedgerPostingRole({...ledger, refType: 'SIM_FUNDING_CASH'})).toBe('UNKNOWN');
    });
    it('keeps noncanonical nested or extended keys unknown even when the suffix resembles a cash leg', () => {
        for (const suffix of ['1', '2', 'FEE_1', 'FEE_2']) {
            for (const key of [
                `trade:LEDGER:legacy:LEDGER:${suffix}`,
                `trade:LEDGER:legacy:${suffix}`,
                `trade:LEDGER:${suffix}:extra`,
                `trade:LEDGER:${suffix} `,
            ])
                expect(runLedgerPostingRole({...ledger, idempotencyKey: key})).toBe('UNKNOWN');
        }
    });
    it('separates relation kinds, owners, window and inactive identity', () => {
        const key = runQueryKeys.related(1, 'run', 'risk-events', request);
        for (const other of [
            runQueryKeys.related(1, 'run', 'ledger-entries', request),
            runQueryKeys.related(2, 'run', 'risk-events', request),
            runQueryKeys.related(1, null, 'risk-events', request),
            runQueryKeys.related(1, 'run', 'risk-events', {...request, offset: 1}),
        ])
            expect(other).not.toEqual(key);
    });
});

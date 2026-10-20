import {describe, expect, it} from 'vitest';
import type {BacktestRunDetailItem} from '@/features/backtests/backtestsTypes';
import {frozenBacktestFacts, boundVersionStrategyCode} from '@/features/backtests/binding-identity';

describe('bound version identity', () => {
    it('uses the frozen execution parent even when it differs from the research source', () => {
        expect(
            boundVersionStrategyCode(
                'sv-execution',
                JSON.stringify({
                    strategyVersionId: 'sv-execution',
                    strategyCode: 'execution-parent',
                }),
            ),
        ).toBe('execution-parent');
    });
    it.each([
        undefined,
        '{}',
        '[]',
        'null',
        '{',
        '{"strategyVersionId":"sv-other","strategyCode":"parent"}',
        '{"strategyVersionId":"sv-execution","strategyCode":" "}',
        '{"strategyVersionId":"sv-execution","strategyCode":42}',
    ])(
        'does not invent a parent for missing, corrupted or mismatched snapshots: %s',
        (snapshot) => {
            expect(boundVersionStrategyCode('sv-execution', snapshot)).toBeNull();
        },
    );
});

const frozenRun: BacktestRunDetailItem = {
    backtestRunId: 'run-old',
    backtestConfigId: 'config-editable',
    researchConfigId: 'research',
    sourceStrategyId: 'strategy',
    strategyVersionId: 'version-old',
    strategyVersionSnapshotJson: '{"strategyVersionId":"version-old"}',
    paramSnapshotJson: '{"window":3}',
    configSnapshotJson:
        '{"startTime":"2026-09-20T00:00:00Z","endTime":"2026-09-23T00:00:00Z","initialCapital":9007199254740993.12345678}',
    datasetSnapshotJson:
        '{"datasetId":"dataset-old","barCount":72,"gapCount":0,"qualityStatus":"OK"}',
    status: 'SUCCEEDED',
    requestedAt: '2026-10-17T01:00:00Z',
    startedAt: null,
    finishedAt: null,
};

describe('frozen backtest display facts', () => {
    it('keeps the run version, original dataset and exact capital without a mutable configuration input', () => {
        const facts = frozenBacktestFacts(frozenRun, 'config-editable', 'run-old');
        expect(facts?.strategyVersionId).toBe('version-old');
        expect(facts?.datasetId).toBe('dataset-old');
        expect(facts?.initialCapital).toBe('9007199254740993.12345678');
        expect(facts?.dataset?.barCount).toBe('72');
        expect(facts?.dataset?.gapCount).toBe('0');
    });
    it('rejects a different parent, run identity or missing run', () => {
        expect(frozenBacktestFacts(frozenRun, 'other-config', 'run-old')).toBeNull();
        expect(frozenBacktestFacts(frozenRun, 'config-editable', 'other-run')).toBeNull();
        expect(frozenBacktestFacts(undefined, 'config-editable', 'run-old')).toBeNull();
    });
    it('leaves malformed frozen JSON unknown rather than inventing current draft fields', () => {
        const facts = frozenBacktestFacts(
            {...frozenRun, configSnapshotJson: '{', datasetSnapshotJson: '[]'},
            'config-editable',
            'run-old',
        );
        expect(facts?.initialCapital).toBeNull();
        expect(facts?.startTime).toBeNull();
        expect(facts?.dataset).toBeNull();
        expect(facts?.strategyVersionId).toBe('version-old');
    });
    it('keeps absent dataset dimensions and counts unknown, without a zero or venue default', () => {
        const facts = frozenBacktestFacts(
            {...frozenRun, datasetSnapshotJson: '{"datasetId":"dataset-old"}'},
            'config-editable',
            'run-old',
        );
        expect(facts?.dataset?.barCount).toBeNull();
        expect(facts?.dataset?.exchangeCode).toBeNull();
    });
});

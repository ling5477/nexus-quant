import {describe, expect, it} from 'vitest';
import {simAnalysisNavigationSearch} from '../../src/features/paper-trading/analysis-navigation';

describe('SIM analysis navigation context', () => {
    it('preserves the exact instance and second-level interval across sibling pages', () => {
        const value = new URLSearchParams(
            simAnalysisNavigationSearch(
                '?paperRunId=run-1&simFrom=2026-10-08T16%3A00%3A00.000Z&simTo=2026-10-08T18%3A00%3A01.000Z',
            ),
        );
        expect(value.get('paperRunId')).toBe('run-1');
        expect(value.get('simTo')).toBe('2026-10-08T18:00:01.000Z');
        expect(value.get('simFrom')).toBe('2026-10-08T16:00:00.000Z');
    });
    it('drops page-specific detail and unrelated filters', () => {
        expect(
            simAnalysisNavigationSearch(
                '?paperRunId=run-1&tab=ledger&orderId=other-order&status=RUNNING',
            ),
        ).toBe('?paperRunId=run-1');
    });
    it('keeps an explicitly invalid empty interval so the destination can reject it', () => {
        expect(simAnalysisNavigationSearch('?paperRunId=run-1&simFrom=&simTo=invalid')).toBe(
            '?paperRunId=run-1&simFrom=&simTo=invalid',
        );
    });
    it('does not invent an instance or interval for empty navigation', () => {
        expect(simAnalysisNavigationSearch('?tab=orders')).toBe('');
    });
    it('preserves fixed observation cutoff, frozen review and research lineage without page-local state', () => {
        const result = new URLSearchParams(simAnalysisNavigationSearch('?paperRunId=run-1&simFrom=2026-10-01T00:00:00Z&simTo=2026-10-02T00:00:00Z&simAsOf=2026-10-03T00:00:00Z&observationId=observation&reviewId=review&validationId=validation&experimentId=experiment&selectedTrialId=trial&strategyVersionId=version&publishId=publication&orderId=other&tab=risk'));
        expect(Object.fromEntries(result)).toEqual({paperRunId: 'run-1', simFrom: '2026-10-01T00:00:00Z', simTo: '2026-10-02T00:00:00Z',
            simAsOf: '2026-10-03T00:00:00Z', observationId: 'observation', reviewId: 'review', validationId: 'validation', experimentId: 'experiment',
            selectedTrialId: 'trial', strategyVersionId: 'version', publishId: 'publication'});
    });
    it('keeps explicit legacy source and view but drops page-local filters and grouping', () => {
        expect(
            simAnalysisNavigationSearch(
                '?analysisSource=legacy&legacyView=evaluations&legacyCause=FAILED_RUN&legacyVersion=v1',
            ),
        ).toBe('?analysisSource=legacy&legacyView=evaluations');
    });
    it('preserves an invalid source so the destination can explain it', () => {
        expect(simAnalysisNavigationSearch('?analysisSource=invalid')).toBe(
            '?analysisSource=invalid',
        );
    });
    it('preserves a historical portfolio tab but drops its per-table filters', () => {
        expect(
            simAnalysisNavigationSearch(
                '?analysisSource=legacy&legacyPortfolioView=ranking&legacyRankSort=totalPnl&legacyRisk=highDrawdown',
            ),
        ).toBe('?analysisSource=legacy&legacyPortfolioView=ranking');
    });
});

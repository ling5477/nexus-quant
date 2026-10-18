import {describe, expect, it} from 'vitest';
import {validateDatasetPage} from '@/api/marketdata';
import type {MarketdataDataset} from '@/types/marketdata';

const request = {limit: 20, offset: 520};
const row: MarketdataDataset = {
    datasetId: '00000000-0000-0000-0000-000000000001',
    datasetName: 'Original final dataset',
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BNB-USDT',
    interval: '1m',
    status: 'INVALID',
    qualityStatus: 'INCOMPLETE',
    barCount: 0,
    gapCount: 2,
    source: 'marketdata_bars',
    createdBy: 'synthetic',
    startTime: '2026-10-01T00:00:00Z',
    endTime: '2026-10-01T00:02:00Z',
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    requestJson: '{}',
};

describe('formal dataset page integrity', () => {
    it('retains the original final row and its actual invalid quality state', () => {
        const page = {items: [row], total: 521, ...request};
        expect(validateDatasetPage(page, request)).toBe(page);
        expect(validateDatasetPage(page, request).items[0].qualityStatus).toBe('INCOMPLETE');
    });
    it('rejects truncated, wrong-window and duplicate-identity responses', () => {
        for (const page of [
            {items: [], total: 521, ...request},
            {items: [row], total: 521, ...request, offset: 0},
            {items: [row, row], total: 522, ...request},
            {items: [], total: -1, ...request},
        ])
            expect(() => validateDatasetPage(page, request)).toThrow();
    });
    it('rejects a different selected identity or missing provenance without fabricating an option', () => {
        expect(() =>
            validateDatasetPage(
                {items: [row], total: 1, limit: 1, offset: 0},
                {datasetId: 'different', limit: 1, offset: 0},
            ),
        ).toThrow();
        expect(() =>
            validateDatasetPage(
                {items: [{...row, source: undefined}], total: 521, ...request},
                request,
            ),
        ).toThrow();
    });
});

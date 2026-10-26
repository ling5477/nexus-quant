import {describe, expect, it} from 'vitest';
import {researchWindow, isResearchJob, datasetFromJobLink, researchFromDatasetLink} from '../../src/features/marketdata/research-dataset';
import {frozenResearchInput} from '../../src/features/backtests/FrozenResearchInputSummary';
import type {MarketdataIngestionJob} from '../../src/types/marketdata';
import zh from '../../src/i18n/locales/zh-CN/pages.json';
import en from '../../src/i18n/locales/en-US/pages.json';

describe('bounded research dataset entry', () => {
    const now = Date.parse('2026-10-26T12:30:00Z');
    it.each(['BTC-USDT', 'ETH-USDT'])('keeps all 720 hours and exclusive UTC end for %s', (symbol) => {
        const input = researchWindow(symbol, '2026-09-26', '2026-10-26', now);
        expect(input).toEqual({exchangeCode: 'OKX', marketType: 'SPOT', symbol, interval: '1h',
            startTime: '2026-09-26T00:00:00.000Z', endTime: '2026-10-25T23:59:59.999Z'});
        expect((Date.parse(input.endTime) + 1 - Date.parse(input.startTime)) / 3_600_000).toBe(720);
    });
    it.each([
        ['SOL-USDT', '2026-09-26', '2026-10-26'],
        ['BTC-USDT', '2026-09-25', '2026-10-26'],
        ['BTC-USDT', '2026-10-26', '2026-10-27'],
        ['BTC-USDT', '2026-10-26', '2026-10-26'],
        ['BTC-USDT', '2026-02-30', '2026-03-01'],
        ['BTC-USDT', '2026-10-25T08:00:00+08:00', '2026-10-26'],
    ])('rejects unsupported or non-complete UTC window %s %s %s', (symbol, start, end) => {
        expect(() => researchWindow(symbol, start, end, now)).toThrow('RESEARCH_WINDOW_INVALID');
    });
    it('recognizes original compatible job scope without narrowing the generic ingestion API', () => {
        const job = {...researchWindow('ETH-USDT', '2026-09-26', '2026-10-26', now), jobId: '123'} as MarketdataIngestionJob;
        expect(isResearchJob(job)).toBe(true);
        expect(isResearchJob({...job, interval: '1m'})).toBe(false);
        expect(isResearchJob({...job, endTime: '2026-10-26T00:00:00Z'})).toBe(false);
        expect(datasetFromJobLink(job)).toBe('/marketdata?section=preparation&preparationTab=datasets&datasetFromJob=123');
        expect(researchFromDatasetLink('a/b')).toBe('/research?create=true&datasetId=a%2Fb');
    });
    it('shows frozen count and digest from the selected snapshot and preserves unknown', () => {
        const input = {version: 'HISTORICAL_RESEARCH_INPUT_V1', sha256: 'a'.repeat(64),
            frozenAt: '2026-10-26T01:00:00Z', source: 'EXCHANGE_HISTORICAL', bars: Array(720).fill({}),
            replayVisibilityVersion: 'CLOSED_HOURLY_BOUNDARY_V1', replayVisibilitySource: 'EXPERIMENT_ASSUMPTION'};
        expect(frozenResearchInput(JSON.stringify({frozenInput: input}))).toMatchObject({invalid: false, count: 720, sha256: 'a'.repeat(64)});
        expect(frozenResearchInput('{}')).toBeNull();
        expect(frozenResearchInput('{')).toEqual({invalid: true});
        expect(frozenResearchInput(JSON.stringify({frozenInput: {...input, sha256: ''}}))).toEqual({invalid: true});
    });
    it('has matching Chinese and English product messages', () => {
        expect(Object.keys(zh.researchDataset).sort()).toEqual(Object.keys(en.researchDataset).sort());
        expect(Object.values(zh.researchDataset).every(Boolean)).toBe(true);
        expect(Object.values(en.researchDataset).every(Boolean)).toBe(true);
    });
});

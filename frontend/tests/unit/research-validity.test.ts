import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {expect, it} from 'vitest';
import {ResearchValidityDetails, researchPercent} from '@/pages/evaluations/EvaluationsPage';
import type {ResearchValidity} from '@/types/evaluations';

it('unknown and non-finite research returns remain missing while measured zero is visible', () => {
    expect(researchPercent(null)).toBe('—');
    expect(researchPercent(undefined)).toBe('—');
    expect(researchPercent(Number.NaN)).toBe('—');
    expect(researchPercent(0)).toBe('0.0000%');
    expect(researchPercent(-0.123)).toBe('-12.3000%');
});

it('old reports render the explicit missing state and chronological disclosure', () => {
    const html = renderToStaticMarkup(createElement(ResearchValidityDetails, {}));
    expect(html).toContain('不可用');
    expect(html).toContain('不是随机交叉验证');
    expect(html).toContain('样本外 (OOS)');
    expect(html).not.toMatch(/>0%</);
});

it('insufficient data leaves OOS and benchmark returns missing', () => {
    const value: ResearchValidity = {validationStatus: 'INSUFFICIENT_DATA', reason: 'TWO_NONEMPTY_WINDOWS_REQUIRED',
        splitPolicy: 'CHRONOLOGICAL_CLOSED_BARS_70_30_FLOOR_V1', segmentEquityPolicy: 'CONTINUOUS_RUN_PREVIOUS_BAR_EQUITY',
        identity: null, assumptions: null, full: null, inSample: null, outOfSample: null, benchmark: null,
        strategyVsBenchmarkDifference: null};
    const html = renderToStaticMarkup(createElement(ResearchValidityDetails, {value}));
    expect(html).toContain('数据不足');
    expect(html).toContain('—');
    expect(html).not.toMatch(/>0%</);
});

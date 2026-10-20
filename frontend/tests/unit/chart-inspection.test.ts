import {describe, expect, it} from 'vitest';
import {chartUtcTime} from '@/nq-design-system/charts/NqKlineChart';
import {toHistogramData} from '@/nq-design-system/charts/chartData';
import {formatRatioPercent} from '@/utils/formatters';

describe('行情检查与金融比率展示', () => {
    it('显示完整UTC时点，不受本地时区影响', () => {
        expect(chartUtcTime('2026-10-05T08:00:00Z')).toBe('2026-10-05 08:00:00 UTC');
        expect(chartUtcTime({year: 2026, month: 10, day: 5})).toBe('2026-10-05 00:00:00 UTC');
        expect(chartUtcTime('invalid')).toBe('—');
    });
    it('缺失成交量不产生零值柱，合法零成交量保留', () => {
        const base = {time: 1, open: 1, high: 2, low: 1, close: 2};
        expect(toHistogramData([{...base, volume: null}], 'INTL_CRYPTO')).toEqual([]);
        expect(toHistogramData([{...base, volume: 0}], 'INTL_CRYPTO')[0].value).toBe(0);
    });
    it('比例只转换一次，并保留小额损益方向与缺值', () => {
        expect(formatRatioPercent(-0.0007224)).toBe('-0.0722%');
        expect(formatRatioPercent(0)).toBe('0.0000%');
        expect(formatRatioPercent(null)).toBe('—');
        expect(formatRatioPercent(Number.NaN)).toBe('—');
    });
});

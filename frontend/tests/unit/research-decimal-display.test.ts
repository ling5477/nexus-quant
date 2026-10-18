import {describe, it, expect} from 'vitest';
import {parseExactJson} from '@/api/exact-json';
import {formatNumber, formatRatioPercent} from '@/utils/formatters';

describe('研究金额经过正式JSON与展示链仍保留十进制精度', () => {
    it('大于浮点安全范围的初资与负费用按声明精度舍入', () => {
        const parsed = parseExactJson(
            '{"initialCapital":123456789012345.123456789,"fee":-0.00000001999,"orderCount":46}',
            new Set(['initialCapital', 'fee']),
        ) as {initialCapital: string; fee: string; orderCount: number};
        expect(formatNumber(parsed.initialCapital, 8)).toBe('123,456,789,012,345.12345679');
        expect(formatNumber(parsed.fee, 8)).toBe('-0.00000002');
        expect(parsed.orderCount).toBe(46);
    });
    it('比例按十进制换算百分比，零与缺失分别显示', () => {
        expect(formatRatioPercent('0.1234567890123456789')).toBe('12.3457%');
        expect(formatRatioPercent('-0.1234567890123456789')).toBe('-12.3457%');
        expect(formatRatioPercent('0')).toBe('0.0000%');
        expect(formatRatioPercent(null)).toBe('—');
    });
    it('无效来源不会伪装为零', () => {
        expect(formatNumber('not-available')).toBe('-');
        expect(formatRatioPercent('not-available')).toBe('—');
    });
});

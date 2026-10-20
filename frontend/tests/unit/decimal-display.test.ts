import {describe, expect, it} from 'vitest';
import {decimalDisplay} from '@/utils/decimal-display';
describe('精确账户展示', () => {
    it('大金额保留整数及尾部精度', () =>
        expect(decimalDisplay('123456789012345678901234567890.123456785', 8)).toBe(
            '123,456,789,012,345,678,901,234,567,890.12345679',
        ));
    it('负费用与缺失不变成零', () => {
        expect(decimalDisplay('-0.000000019', 8)).toBe('-0.00000002');
        expect(decimalDisplay(null)).toBe('—');
    });
    it('比例乘100和科学计数法不经浮点', () => {
        expect(decimalDisplay('0.012345', 4, 2, true)).toBe('+1.2345');
        expect(decimalDisplay('1.23456789012345678e+18', 8)).toBe('1,234,567,890,123,456,780');
    });
    it('实际零不显示负号', () => {
        expect(decimalDisplay('-0.000000000', 8)).toBe('0');
        expect(decimalDisplay('0E-16')).toBe('0');
    });
});

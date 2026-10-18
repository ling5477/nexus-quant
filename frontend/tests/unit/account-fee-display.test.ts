import {expect, it} from 'vitest';
import {formatAccountFeePercent} from '../../src/features/accounts/fee-display';

/** 主费率字段不能把最小非零值显示为零；断言真实收费/返佣口径，而非仅检查字符串精度配置。 */
it('distinguishes the smallest observable charge and rebate from a genuinely zero rate', () => {
    expect(formatAccountFeePercent('0.000000000001')).toBe('+0.0000000001%');
    expect(formatAccountFeePercent('-0.000000000001')).toBe('-0.0000000001%');
    expect(formatAccountFeePercent('0.000000000000')).toBe('0%');
});
it('preserves every quoted digit while converting rates into percentages', () => {
    expect(formatAccountFeePercent('0.123456789012')).toBe('+12.3456789012%');
    expect(formatAccountFeePercent('-0.123456789012')).toBe('-12.3456789012%');
    expect(formatAccountFeePercent('0.001000000000')).toBe('+0.1%');
    expect(formatAccountFeePercent('-0.000200000000')).toBe('-0.02%');
});
it('keeps zero in scientific notation distinct from signed nonzero values', () => {
    expect(formatAccountFeePercent('0E-12')).toBe('0%');
    expect(formatAccountFeePercent('-1E-12')).toBe('-0.0000000001%');
});

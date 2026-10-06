import {describe, expect, it} from 'vitest';
import {formatNqNumber} from '../../src/nq-design-system/format/nqFormat';

describe('canonical economic precision', () => {
    it('preserves BTC and decimal amounts without binary conversion or fixed scale', () => {
        expect(formatNqNumber('0.00119354', {exact: true})).toBe('0.00119354');
        expect(formatNqNumber('12345678901234567890.123456789', {exact: true})).toBe('12,345,678,901,234,567,890.123456789');
        expect(formatNqNumber('-0.00000001', {exact: true})).toBe('-0.00000001');
        expect(formatNqNumber('100.00000000', {exact: true})).toBe('100');
    });
    it('keeps unknown and absent values distinct from actual zero', () => {
        for (const value of [null, undefined, '']) expect(formatNqNumber(value, {exact: true})).toBe('-');
        expect(formatNqNumber('UNKNOWN', {exact: true})).toBe('UNKNOWN');
        expect(formatNqNumber('NOT_AVAILABLE', {exact: true})).toBe('NOT_AVAILABLE');
        expect(formatNqNumber('0', {exact: true})).toBe('0');
    });
});

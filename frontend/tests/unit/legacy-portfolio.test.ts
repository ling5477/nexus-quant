import {describe, expect, it} from 'vitest';
import {
    exactLegacyRiskScore,
    legacyAbsDecimal,
    legacyDecimalAvailable,
    legacyDrawdownBucket,
    topLegacyRow,
} from '../../src/features/paper-trading/legacy-portfolio';

describe('historical sample ranking arithmetic', () => {
    it('preserves tiny positive and negative score differences without floating point', () => {
        expect(exactLegacyRiskScore('0.300000000000000001', '-0.2', 1, 3, 1)).toBe(
            '0.000000000000000001',
        );
        expect(exactLegacyRiskScore('0.299999999999999999', '-0.2', 1, 3, 1)).toBe(
            '-0.000000000000000001',
        );
    });
    it('retains the existing count penalties and absolute drawdown semantics', () => {
        expect(exactLegacyRiskScore('0.9', '-0.1', 2, 5, 3)).toBe('0.59');
        expect(exactLegacyRiskScore('9e-1', '1e-1', 2, 5, 3)).toBe('0.59');
        expect(exactLegacyRiskScore('0', '0', 0, 0, 0)).toBe('0');
    });
    it('does not substitute zeros for unavailable inputs or invalid counts', () => {
        expect(exactLegacyRiskScore(null, '-0.1', 0, 0, 0)).toBeNull();
        expect(exactLegacyRiskScore('1', 'bad', 0, 0, 0)).toBeNull();
        expect(exactLegacyRiskScore('1', '-0.1', -1, 0, 0)).toBeNull();
        expect(exactLegacyRiskScore('1', '-0.1', 1, 0.5, 0)).toBeNull();
    });
    it('compares exact threshold boundaries and rejects a positive drawdown', () => {
        expect(legacyDrawdownBucket('-0.04999999999999999999')).toBe(0);
        expect(legacyDrawdownBucket('-0.05')).toBe(1);
        expect(legacyDrawdownBucket('-0.09999999999999999999')).toBe(1);
        expect(legacyDrawdownBucket('-0.1')).toBe(2);
        expect(legacyDrawdownBucket('-0.2')).toBe(3);
        expect(legacyDrawdownBucket(null)).toBeNull();
        expect(legacyDrawdownBucket('0.1')).toBeNull();
    });
    it('uses the actual extreme identity and leaves missing samples unranked', () => {
        const rows = [
            {id: 'a', value: '0.10000000000000000001'},
            {id: 'b', value: '0.10000000000000000002'},
            {id: 'missing', value: null},
        ];
        expect(topLegacyRow(rows, (row) => row.value)?.id).toBe('b');
        expect(topLegacyRow(rows, (row) => row.value, false)?.id).toBe('a');
        expect(topLegacyRow([{value: null}], (row) => row.value)).toBeNull();
        expect(legacyAbsDecimal('-1e-18')).toBe('1e-18');
        expect(legacyDecimalAvailable('invalid')).toBe(false);
    });
});

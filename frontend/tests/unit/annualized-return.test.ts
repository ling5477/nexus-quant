import {describe, expect, it} from 'vitest';
import {annualizedReturnPresentation} from '@/features/backtests/annualized-return';

describe('年化缺失只使用正式报告依据', () => {
    it('可用的零收益与非零收益继续显示数值', () => {
        expect(annualizedReturnPresentation('0', '{"annualizedReturnStatus":"AVAILABLE"}')).toEqual(
            {value: '0.0000%', reasonKey: null},
        );
        expect(annualizedReturnPresentation('0.2')).toEqual({value: '20.0000%', reasonKey: null});
    });
    it('超范围不显示零、截顶值或猜测数值', () => {
        expect(
            annualizedReturnPresentation(null, '{"annualizedReturnStatus":"OUT_OF_RANGE"}'),
        ).toEqual({value: '—', reasonKey: 'pages:annualizedReturnOutOfRange'});
    });
    it('不同不可计算原因有各自文案', () => {
        expect(
            annualizedReturnPresentation(null, '{"annualizedReturnStatus":"INVALID_PERIOD"}')
                .reasonKey,
        ).toBe('pages:annualizedReturnInvalidPeriod');
        expect(
            annualizedReturnPresentation(null, '{"annualizedReturnStatus":"NON_POSITIVE_GROWTH"}')
                .reasonKey,
        ).toBe('pages:annualizedReturnNonPositiveGrowth');
    });
    it('旧记录、未知状态、损坏JSON保留缺少原因', () => {
        for (const metrics of [
            null,
            '{}',
            'not-json',
            '[]',
            '{"annualizedReturnStatus":"FUTURE_STATUS"}',
            '{"annualizedReturnStatus":"toString"}',
            '{"annualizedReturnStatus":2}',
        ]) {
            expect(annualizedReturnPresentation(null, metrics).reasonKey).toBe(
                'pages:annualizedReturnReasonUnavailable',
            );
        }
    });
});

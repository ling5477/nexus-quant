import {describe, expect, it} from 'vitest';
import {candleChange, movingAverage} from '@/nq-design-system/charts/marketIndicators';
import {endAfterStartRule, instantMillis} from '@/utils/time-range';
import type {NqKlineBar} from '@/nq-design-system/charts/types';

const bar = (time: number, close: number, qualityStatus = 'OK'): NqKlineBar => ({
    time,
    close,
    open: close,
    high: close,
    low: close,
    qualityStatus,
});
describe('正式OHLCV指标边界', () => {
    it('只有完整连续窗口产生MA，缺段后重新预热', () => {
        expect(
            movingAverage(
                [bar(60, 2), bar(120, 4), bar(180, 6), bar(300, 8), bar(360, 10), bar(420, 12)],
                3,
                60,
                1e9,
            ),
        ).toEqual([
            {time: 60},
            {time: 120},
            {time: 180, value: 4},
            {time: 300},
            {time: 360},
            {time: 420, value: 10},
        ]);
    });
    it('异常与未收盘bar不加入均值，合法零成交量不影响收盘价指标', () => {
        expect(
            movingAverage(
                [
                    bar(60, 2),
                    {...bar(120, 4), volume: 0},
                    bar(180, 6, 'GAP_DETECTED'),
                    bar(240, 8),
                    bar(300, 10),
                ],
                2,
                60,
                310_000,
            ),
        ).toEqual([{time: 60}, {time: 120, value: 3}, {time: 180}, {time: 240}, {time: 300}]);
        expect(
            movingAverage(
                [bar(60, 2), {...bar(120, 4), closeTime: '1970-01-01T00:10:00Z'}],
                2,
                60,
                500_000,
            ),
        ).toEqual([{time: 60}, {time: 120}]);
    });
    it('本根涨跌只使用同根开收，缺失开盘不当成零', () => {
        expect(candleChange(100, 105)).toBe(5);
        expect(candleChange(100, 95)).toBe(-5);
        expect(candleChange(0, 100)).toBeNull();
        expect(candleChange(Number.NaN, 100)).toBeNull();
        expect(movingAverage([bar(60, 1)], 201, 60)).toEqual([]);
    });
    it('时间范围比较包含秒、时区偏移和毫秒，逆序或相等不能提交', async () => {
        const rule = endAfterStartRule('start')({
            getFieldValue: () => '2026-10-12T09:00:00.123+08:00',
        });
        await expect(rule.validator(null, '2026-10-12T01:00:00.123Z')).rejects.toThrow();
        await expect(rule.validator(null, '2026-10-12T01:00:00.122Z')).rejects.toThrow();
        await expect(rule.validator(null, '2026-10-12T01:00:00.124Z')).resolves.toBeUndefined();
        expect(instantMillis(null)).toBeNull();
        expect(instantMillis('invalid')).toBeNull();
    });
});

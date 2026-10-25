import type {LineData, Time, WhitespaceData} from 'lightweight-charts';
import {toChartTime} from './chartData';
import type {NqKlineBar} from './types';

/** MA只使用连续、已收盘且未被标记异常的收盘价；缺段后重新预热。 */
export function movingAverage(
    bars: readonly NqKlineBar[],
    window: number,
    stepSeconds: number,
    now = Date.now(),
): Array<LineData | WhitespaceData> {
    if (!Number.isInteger(window) || window < 2 || window > 200 || !(stepSeconds > 0)) return [];
    const queue: number[] = [],
        result: Array<LineData | WhitespaceData> = [];
    let previous: number | null = null;
    for (const bar of bars) {
        const time = toChartTime(bar.time);
        if (typeof time !== 'number') continue;
        const openMs = time * 1000;
        const closed = bar.closeTime
            ? Date.parse(bar.closeTime) < now
            : openMs + stepSeconds * 1000 <= now;
        const valid =
            Number.isFinite(bar.close) &&
            bar.close > 0 &&
            closed &&
            (!bar.availableAt || Date.parse(bar.availableAt) <= now) &&
            (!bar.qualityStatus || ['OK', 'GOOD'].includes(bar.qualityStatus));
        if ((previous !== null && time - previous !== stepSeconds) || !valid) queue.length = 0;
        previous = time;
        if (valid) queue.push(bar.close);
        if (queue.length > window) queue.shift();
        result.push(
            queue.length === window
                ? {time: time as Time, value: queue.reduce((sum, value) => sum + value, 0) / window}
                : {time},
        );
    }
    return result;
}

export function candleChange(open: number, close: number): number | null {
    return Number.isFinite(open) && open > 0 && Number.isFinite(close)
        ? ((close - open) / open) * 100
        : null;
}

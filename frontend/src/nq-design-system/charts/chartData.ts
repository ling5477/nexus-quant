import type { CandlestickData, HistogramData, Time, UTCTimestamp } from 'lightweight-charts';

import { marketColors, type MarketConvention } from '../tokens/nq-tokens';
import type { NqKlineBar } from './types';

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function chartTimeSortValue(time: Time): number {
  if (typeof time === 'number') {
    return time;
  }

  if (typeof time === 'string') {
    const parsed = Date.parse(time);
    return Number.isFinite(parsed) ? parsed / 1000 : 0;
  }

  return Date.UTC(time.year, time.month - 1, time.day) / 1000;
}

export function toChartTime(value: NqKlineBar['time']): Time | null {
  if (isFiniteNumber(value)) {
    return Math.floor(value) as UTCTimestamp;
  }

  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    if (Number.isFinite(parsed)) {
      return Math.floor(parsed / 1000) as UTCTimestamp;
    }

    return value;
  }

  return value;
}

export function toCandlestickData(bars: readonly NqKlineBar[]): CandlestickData[] {
  return bars
    .map((bar) => {
      const time = toChartTime(bar.time);
      if (
        time == null
        || !isFiniteNumber(bar.open)
        || !isFiniteNumber(bar.high)
        || !isFiniteNumber(bar.low)
        || !isFiniteNumber(bar.close)
      ) {
        return null;
      }

      return {
        time,
        open: bar.open,
        high: bar.high,
        low: bar.low,
        close: bar.close,
      };
    })
    .filter((item): item is CandlestickData => item !== null)
    .sort((a, b) => chartTimeSortValue(a.time) - chartTimeSortValue(b.time));
}

export function toHistogramData(
  bars: readonly NqKlineBar[],
  convention: MarketConvention,
): HistogramData[] {
  const colors = marketColors(convention);

  return bars
    .map((bar) => {
      const time = toChartTime(bar.time);
      const volume = bar.volume;
      if (time == null || !isFiniteNumber(volume)) {
        return null;
      }

      const item: HistogramData = {
        time,
        value: volume,
        color: bar.close >= bar.open ? colors.up : colors.down,
      };

      return item;
    })
    .filter((item): item is HistogramData => item !== null)
    .sort((a, b) => chartTimeSortValue(a.time) - chartTimeSortValue(b.time));
}

export function chartErrorText(error: string | Error | null | undefined): string | null {
  if (!error) {
    return null;
  }

  return typeof error === 'string' ? error : error.message;
}

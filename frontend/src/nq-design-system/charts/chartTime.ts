import type {Time} from 'lightweight-charts';
import i18n from '@/i18n';
import {toChartTime} from './chartData';
import type {NqKlineBar} from './types';

export function chartTimeDate(time: NqKlineBar['time']): Date | null {
    const value = toChartTime(time);
    const date =
        typeof value === 'number'
            ? new Date(value * 1000)
            : typeof value === 'string'
              ? new Date(value)
              : value
                ? new Date(Date.UTC(value.year, value.month - 1, value.day))
                : null;
    return date && Number.isFinite(date.getTime()) ? date : null;
}

/** 只改变显示，不平移UTC事实或交易所分桶；与列表采用相同自动时区。 */
export function formatChartTime(time: Time, compact = false): string {
    const date = chartTimeDate(time);
    return date
        ? new Intl.DateTimeFormat(
              i18n.resolvedLanguage ?? 'zh-CN',
              compact
                  ? {
                        month: '2-digit',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                        hour12: false,
                    }
                  : {
                        year: 'numeric',
                        month: '2-digit',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit',
                        hour12: false,
                    },
          ).format(date)
        : '—';
}

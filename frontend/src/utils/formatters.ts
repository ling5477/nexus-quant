import i18n from '@/i18n';
import {decimalDisplay} from './decimal-display';
export function normalizeOptionalText(value: string | undefined): string {
    return value?.trim() ?? '';
}

export function containsIgnoreCase(
    value: string | number | null | undefined,
    keyword: string,
): boolean {
    if (!keyword) {
        return true;
    }

    return String(value ?? '')
        .toLowerCase()
        .includes(keyword.toLowerCase());
}

export function matchesBooleanFilter(value: boolean, filterValue: string): boolean {
    if (!filterValue || filterValue === 'all') {
        return true;
    }

    return filterValue === 'true' ? value : !value;
}

export function formatDateTime(value: string | null | undefined): string {
    if (!value) {
        return '-';
    }

    return new Date(value).toLocaleString(i18n.resolvedLanguage ?? 'zh-CN', {
        hour12: false,
    });
}

/** 回测允许毫秒边界，详情不能把同一秒内不同的起止Instant显示为相等。 */
export function formatDateTimeMilliseconds(value: string | null | undefined): string {
    if (!value || !Number.isFinite(Date.parse(value))) return '-';
    return new Date(value).toLocaleString(i18n.resolvedLanguage ?? 'zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        fractionalSecondDigits: 3,
        hour12: false,
    });
}

export function formatNumber(
    value: number | string | null | undefined,
    maximumFractionDigits = 4,
): string {
    if (value === null || value === undefined || value === '') {
        return '-';
    }
    if (typeof value === 'string')
        return /^[+-]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value)
            ? decimalDisplay(value, maximumFractionDigits)
            : '-';

    return Number(value).toLocaleString(i18n.resolvedLanguage ?? 'zh-CN', {
        maximumFractionDigits,
    });
}

/** 只格式化后端明确的比例值，空值与非有限值不作为零收益。 */
export function formatRatioPercent(value: number | string | null | undefined): string {
    if (typeof value === 'string') {
        if (!/^[+-]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value)) return '—';
        const rendered = decimalDisplay(value, 4, 2),
            [integer, fraction = ''] = rendered.split('.');
        return `${integer}.${fraction.padEnd(4, '0')}%`;
    }
    return value == null || !Number.isFinite(value)
        ? '—'
        : `${(value * 100).toLocaleString(i18n.resolvedLanguage ?? 'zh-CN', {minimumFractionDigits: 4, maximumFractionDigits: 4})}%`;
}

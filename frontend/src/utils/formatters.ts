import i18n from '@/i18n';
export function normalizeOptionalText(value: string | undefined): string {
    return value?.trim() ?? '';
}

export function containsIgnoreCase(value: string | number | null | undefined, keyword: string): boolean {
    if (!keyword) {
        return true;
    }

    return String(value ?? '').toLowerCase().includes(keyword.toLowerCase());
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

export function formatNumber(value: number | string | null | undefined, maximumFractionDigits = 4): string {
    if (value === null || value === undefined || value === '') {
        return '-';
    }

    return Number(value).toLocaleString(i18n.resolvedLanguage ?? 'zh-CN', {
        maximumFractionDigits,
    });
}

/** 只格式化后端明确的比例值，空值与非有限值不作为零收益。 */
export function formatRatioPercent(value: number | null | undefined): string {
    return value == null || !Number.isFinite(value) ? '—' : `${(value * 100).toLocaleString(i18n.resolvedLanguage ?? 'zh-CN', {minimumFractionDigits: 4, maximumFractionDigits: 4})}%`;
}

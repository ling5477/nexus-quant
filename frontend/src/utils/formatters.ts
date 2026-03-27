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

    return new Date(value).toLocaleString('zh-CN', {
        hour12: false,
    });
}

export function formatNumber(value: number | string | null | undefined, maximumFractionDigits = 4): string {
    if (value === null || value === undefined || value === '') {
        return '-';
    }

    return Number(value).toLocaleString('zh-CN', {
        maximumFractionDigits,
    });
}

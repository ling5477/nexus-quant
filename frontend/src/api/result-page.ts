export interface ResultPage<T> {
    items: T[];
    total: number;
    limit: number;
    offset: number;
}

/** 错页、截断结果或重复业务身份必须进入错误态，不能把它们当作真实空结果。 */
export function validateResultPage<T>(
    value: unknown,
    request: {limit: number; offset: number},
    identity: (row: unknown) => string | null,
): ResultPage<T> {
    if (!value || typeof value !== 'object') throw new Error('Invalid result page');
    const page = value as Partial<ResultPage<unknown>>;
    if (
        !Number.isInteger(request.limit) ||
        request.limit < 1 ||
        request.limit > 100 ||
        !Number.isInteger(request.offset) ||
        request.offset < 0 ||
        request.offset > 2147483647 ||
        !Array.isArray(page.items) ||
        !Number.isSafeInteger(page.total) ||
        page.total! < 0 ||
        page.limit !== request.limit ||
        page.offset !== request.offset ||
        page.items.length !== Math.min(request.limit, Math.max(0, page.total! - request.offset))
    ) {
        throw new Error('Invalid result page window');
    }
    const seen = new Set<string>();
    for (const row of page.items) {
        const id = identity(row);
        if (!id || seen.has(id)) throw new Error('Invalid result page identity');
        seen.add(id);
    }
    return page as ResultPage<T>;
}

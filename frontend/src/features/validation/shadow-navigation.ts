/** 列表与详情共享只读查询上下文；分页上限遵循后端非负 int 偏移与每页50条合同。 */
export const SHADOW_RUN_STATUSES = [
    'ALL',
    'CREATED',
    'PRECHECKING',
    'READY',
    'RUNNING',
    'STOP_REQUESTED',
    'STOPPED',
    'COMPLETED',
    'BLOCKED',
    'FAILED',
    'CANCELLED',
] as const;

export const SHADOW_LIST_PAGE_SIZE = 50;
const MAX_PAGE = Math.floor(2_147_483_647 / SHADOW_LIST_PAGE_SIZE) + 1;
export const SHADOW_LIST_PATH = '/strategies/shadow-runs';

/** 无效书签明确报错并禁止列表请求，不能悄悄读成另一个状态或第一页。 */
export function shadowListLocation(search: string) {
    const params = new URLSearchParams(search);
    const status = params.get('status') ?? 'ALL';
    const text = params.get('page') ?? '1';
    const page = /^\d+$/.test(text) ? Number(text) : NaN;
    const error = !SHADOW_RUN_STATUSES.some((value) => value === status)
        ? 'status'
        : !Number.isSafeInteger(page) || page < 1 || page > MAX_PAGE
          ? 'page'
          : null;
    return {status, page: error ? 1 : page, error};
}

export interface ShadowListReturn {
    search: string;
    scrollY: number;
    focusId: string;
}

/** 导航状态不拥有业务权限，只接收本列表的有界查询和UUID焦点，不接受外部返回地址。 */
export function shadowListReturn(value: unknown): ShadowListReturn | null {
    if (!value || typeof value !== 'object') return null;
    const candidate = value as Partial<ShadowListReturn>;
    if (
        typeof candidate.search !== 'string' ||
        candidate.search.length > 2048 ||
        (candidate.search !== '' && !candidate.search.startsWith('?')) ||
        typeof candidate.scrollY !== 'number' ||
        !Number.isFinite(candidate.scrollY) ||
        candidate.scrollY < 0 ||
        candidate.scrollY > 10_000_000 ||
        typeof candidate.focusId !== 'string' ||
        !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(candidate.focusId)
    )
        return null;
    const params = new URLSearchParams(candidate.search);
    const safe = new URLSearchParams();
    for (const key of ['status', 'page']) {
        const text = params.get(key);
        if (text !== null) safe.set(key, text);
    }
    const search = safe.size ? '?' + safe.toString() : '';
    return {search, scrollY: candidate.scrollY, focusId: candidate.focusId};
}

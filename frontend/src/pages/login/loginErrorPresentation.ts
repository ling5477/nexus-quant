import type {AppApiError} from '@/types/api';

/** 登录请求与已登录会话使用不同提示；不从后端自由文本推断账号是否存在。 */
export function loginErrorMessageKey(error: Partial<AppApiError>, credentialRequest: boolean): string {
    if (error.status === 401) return credentialRequest ? 'errors:login.credentials' : 'errors:login.expired';
    if (error.status === 403) return 'errors:login.unavailableAccount';
    if (error.status === 429) return 'errors:login.rateLimited';
    if (error.status === 0 || error.code === 'NETWORK_ERROR') return 'errors:login.network';
    if (error.status && error.status >= 500) return 'errors:login.service';
    return 'errors:login.failed';
}

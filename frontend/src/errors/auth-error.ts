import type {AppApiError} from '@/types/api';

const AUTH_ERROR_KEY = 'nq.auth-error';
export function saveAuthError(error: AppApiError): void {
    try {
        // 只跨跳转保存展示必需身份，不保存凭证、字段值或后端诊断消息。
        sessionStorage.setItem(AUTH_ERROR_KEY, JSON.stringify({
            status: error.status, code: error.code, errorKey: error.errorKey, errorId: error.errorId, traceId: error.traceId,
        }));
    } catch {
        // 存储不可用不阻断会话清理与安全跳转。
    }
}

export function readAuthError(): Partial<AppApiError> | null {
    try {
        const raw = sessionStorage.getItem(AUTH_ERROR_KEY);
        const value: unknown = raw ? JSON.parse(raw) : null;
        if (!value || typeof value !== 'object') return null;
        const error = value as Record<string, unknown>;
        return {
            status: 401, code: typeof error.code === 'string' ? error.code : 'UNAUTHORIZED',
            errorId: typeof error.errorId === 'string' ? error.errorId : undefined,
            errorKey: typeof error.errorKey === 'string' ? error.errorKey : undefined,
            traceId: typeof error.traceId === 'string' ? error.traceId : undefined,
        };
    } catch {
        return null;
    }
}

export function clearAuthError(): void {
    try { sessionStorage.removeItem(AUTH_ERROR_KEY); } catch { /* 存储不可用不影响登录。 */ }
}

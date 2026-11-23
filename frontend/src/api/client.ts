import axios, {AxiosError, CanceledError} from 'axios';

import {normalizeApiError} from '@/api/errors';
import {emitAppError} from '@/utils/error-events';
import {useAuthStore} from '@/store/auth-store';
import {appEnv} from '@/utils/env';
import {saveAuthError} from '@/errors/auth-error';
import {responseBelongsToSession} from './session-scope';

declare module 'axios' {
    interface AxiosRequestConfig { authSessionId?: string | null; }
}

function redirectToLogin(reason: 'expired' | 'unauthorized'): void {
    if (typeof window === 'undefined') {
        return;
    }

    const currentPath = `${window.location.pathname}${window.location.search}`;
    const redirectQuery =
        currentPath && currentPath !== '/login'
            ? `?redirect=${encodeURIComponent(currentPath)}&reason=${reason}`
            : `?reason=${reason}`;

    window.location.assign(`/login${redirectQuery}`);
}

/**
 * 统一 Axios 实例负责追加 Bearer token 与标准化错误。
 * Why:
 * 401 必须统一清理登录态并回到 `/login`，403/500 也需要通过统一出口触发提示，
 * 否则每个页面都会重复实现一套相同行为并逐步漂移。
 */
export const apiClient = axios.create({
    baseURL: appEnv.apiBaseUrl,
    timeout: 15000,
    headers: {
        'X-Requested-With': 'XMLHttpRequest',
    },
});

apiClient.interceptors.request.use(
    (config) => {
        const {accessToken, tokenType, sessionId} = useAuthStore.getState();
        config.authSessionId = sessionId;

        if (accessToken && !config.headers.Authorization) {
            config.headers.Authorization = `${tokenType ?? 'Bearer'} ${accessToken}`;
        }

        return config;
    },
    undefined,
    {synchronous: true},
);

function currentSession(config?: {url?: string; headers?: {Authorization?: unknown}; authSessionId?: string | null}): boolean {
    const {accessToken, tokenType, sessionId} = useAuthStore.getState();
    return responseBelongsToSession(
        config?.headers?.Authorization,
        accessToken,
        tokenType ?? 'Bearer',
        config?.url,
        config?.authSessionId, sessionId,
    );
}

apiClient.interceptors.response.use(
    (response) => {
        if (!currentSession(response.config))
            return Promise.reject(
                new CanceledError('Session changed while the request was pending.'),
            );
        return response;
    },
    (error: AxiosError) => {
        if (!currentSession(error.config))
            return Promise.reject(
                new CanceledError('Session changed while the request was pending.'),
            );
        const normalizedError = normalizeApiError(error);
        const requestUrl = error.config?.url ?? '';
        const isLoginRequest = requestUrl.includes('/auth/login');

        if (normalizedError.status === 401 && !isLoginRequest) {
            // 同会话刚续期时，旧令牌的迟到401不能注销新令牌；不自动重放原业务写入。
            const state = useAuthStore.getState();
            if (String(error.config?.headers?.Authorization) !== `${state.tokenType ?? 'Bearer'} ${state.accessToken}`)
                return Promise.reject(new CanceledError('Superseded access token.'));
            saveAuthError(normalizedError);
            useAuthStore.getState().clearAuth('expired');
            redirectToLogin('expired');
        } else if (
            !isLoginRequest &&
            (normalizedError.status === 403 || normalizedError.status >= 500)
        ) {
            emitAppError(normalizedError);
        }

        return Promise.reject(normalizedError);
    },
);

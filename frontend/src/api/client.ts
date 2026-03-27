import axios, {AxiosError} from 'axios';

import {normalizeApiError} from '@/api/errors';
import {emitAppError} from '@/utils/error-events';
import {useAuthStore} from '@/store/auth-store';
import {appEnv} from '@/utils/env';

function redirectToLogin(reason: 'expired' | 'unauthorized'): void {
    if (typeof window === 'undefined') {
        return;
    }

    const currentPath = `${window.location.pathname}${window.location.search}`;
    const redirectQuery = currentPath && currentPath !== '/login'
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

apiClient.interceptors.request.use((config) => {
    const {accessToken, tokenType} = useAuthStore.getState();

    if (accessToken && !config.headers.Authorization) {
        config.headers.Authorization = `${tokenType ?? 'Bearer'} ${accessToken}`;
    }

    return config;
});

apiClient.interceptors.response.use(
    (response) => response,
    (error: AxiosError) => {
        const normalizedError = normalizeApiError(error);
        const requestUrl = error.config?.url ?? '';
        const isLoginRequest = requestUrl.includes('/auth/login');

        if (normalizedError.status === 401 && !isLoginRequest) {
            useAuthStore.getState().clearAuth('expired');
            redirectToLogin('expired');
        } else if (normalizedError.status === 403 || normalizedError.status >= 500) {
            emitAppError(normalizedError);
        }

        return Promise.reject(normalizedError);
    },
);

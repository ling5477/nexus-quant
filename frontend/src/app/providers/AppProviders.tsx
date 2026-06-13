import {App as AntApp, ConfigProvider, notification} from 'antd';
import type {PropsWithChildren} from 'react';
import {startTransition, useEffect, useEffectEvent} from 'react';
import {QueryClient, QueryClientProvider, useQuery} from '@tanstack/react-query';

import {authApi} from '@/api/auth';
import {authQueryKeys} from '@/api/query-keys';
import {nqAntdTheme} from '@/theme/antd-theme';
import type {AppApiError} from '@/types/api';
import {subscribeAppError} from '@/utils/error-events';
import {useAuthStore} from '@/store/auth-store';
import {appEnv} from '@/utils/env';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: (failureCount, error) => {
                const appError = error as AppApiError;
                return appError.status !== 401 && appError.status !== 403 && failureCount < 1;
            },
            refetchOnWindowFocus: false,
            staleTime: 60_000,
        },
    },
});

function AppErrorBridge() {
    const [api, contextHolder] = notification.useNotification();
    const onAppError = useEffectEvent((error: AppApiError) => {
        const title = error.status === 403 ? '权限不足' : error.status >= 500 ? '服务异常' : '请求失败';
        const description = error.traceId ? `${error.message}（traceId: ${error.traceId}）` : error.message;

        api.error({
            message: title,
            description,
            placement: 'topRight',
        });
    });

    useEffect(() => subscribeAppError(onAppError), [onAppError]);

    return contextHolder;
}

function AuthBootstrap({children}: PropsWithChildren) {
    const accessToken = useAuthStore((state) => state.accessToken);
    const setBootstrapStatus = useAuthStore((state) => state.setBootstrapStatus);
    const updateCurrentUser = useAuthStore((state) => state.updateCurrentUser);
    const clearAuth = useAuthStore((state) => state.clearAuth);

    const currentUserQuery = useQuery({
        queryKey: authQueryKeys.currentUser(accessToken),
        queryFn: authApi.getCurrentUser,
        enabled: Boolean(accessToken),
    });

    useEffect(() => {
        if (!accessToken) {
            setBootstrapStatus('ready');
            return;
        }

        setBootstrapStatus(currentUserQuery.isPending ? 'loading' : 'ready');
    }, [accessToken, currentUserQuery.isPending, setBootstrapStatus]);

    useEffect(() => {
        if (!currentUserQuery.data) {
            return;
        }

        startTransition(() => {
            updateCurrentUser(currentUserQuery.data);
            setBootstrapStatus('ready');
        });
    }, [currentUserQuery.data, setBootstrapStatus, updateCurrentUser]);

    useEffect(() => {
        if (!currentUserQuery.error) {
            return;
        }

        const error = currentUserQuery.error as AppApiError;

        if (error.status === 401) {
            clearAuth('expired');
            return;
        }

        setBootstrapStatus('ready');
    }, [clearAuth, currentUserQuery.error, setBootstrapStatus]);

    return children;
}

export function AppProviders({children}: PropsWithChildren) {
    useEffect(() => {
        document.title = appEnv.appTitle;
    }, []);

    return (
        <ConfigProvider theme={nqAntdTheme}>
            <AntApp>
                <QueryClientProvider client={queryClient}>
                    <AppErrorBridge/>
                    <AuthBootstrap>{children}</AuthBootstrap>
                </QueryClientProvider>
            </AntApp>
        </ConfigProvider>
    );
}

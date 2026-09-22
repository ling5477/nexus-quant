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
import {useTranslation} from 'react-i18next';
import zhCN from 'antd/es/locale/zh_CN';
import enUS from 'antd/es/locale/en_US';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/en';
import '@/i18n';
import {describeApiError, formatApiError} from '@/api/errors';
import {shouldRetryQuery, mutationRetry} from '@/errors/retry-policy';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: shouldRetryQuery,
            refetchOnWindowFocus: false,
            staleTime: 60_000,
        },
        mutations: {retry: mutationRetry},
    },
});

function AppErrorBridge() {
    useTranslation('errors');
    const [api, contextHolder] = notification.useNotification();
    const onAppError = useEffectEvent((error: AppApiError) => {
        const view = describeApiError(error);
        api[view.catalog.severity]({
            message: <ErrorNotificationText error={error} title/>,
            description: <ErrorNotificationText error={error}/>,
            placement: 'topRight',
        });
    });

    useEffect(() => subscribeAppError(onAppError), [onAppError]);

    return contextHolder;
}

function ErrorNotificationText({error, title = false}: {error: AppApiError; title?: boolean}) {
    useTranslation('errors');
    return title ? describeApiError(error).title : formatApiError(error);
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
    const {i18n} = useTranslation();
    const chinese = i18n.resolvedLanguage !== 'en-US';
    dayjs.locale(chinese ? 'zh-cn' : 'en');
    useEffect(() => {
        document.title = appEnv.appTitle;
        document.documentElement.lang = chinese ? 'zh-CN' : 'en-US';
    }, [chinese]);

    return (
        <ConfigProvider theme={nqAntdTheme} locale={chinese ? zhCN : enUS}>
            <AntApp>
                <QueryClientProvider client={queryClient}>
                    <AppErrorBridge/>
                    <AuthBootstrap>{children}</AuthBootstrap>
                </QueryClientProvider>
            </AntApp>
        </ConfigProvider>
    );
}

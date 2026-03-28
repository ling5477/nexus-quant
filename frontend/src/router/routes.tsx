import {Navigate, createBrowserRouter} from 'react-router-dom';

import {ConsoleLayout} from '@/layouts/ConsoleLayout';
import {DashboardPage} from '@/pages/dashboard/DashboardPage';
import {LoginPage} from '@/pages/login/LoginPage';
import {NotFoundPage} from '@/pages/not-found/NotFoundPage';
import {StrategiesPage} from '@/pages/strategies/StrategiesPage';
import {SchedulesPage} from '@/pages/schedules/SchedulesPage';
import {RunsPage} from '@/pages/runs/RunsPage';
import {ResearchPage} from '@/pages/research/ResearchPage';
import {BacktestsPage} from '@/pages/backtests/BacktestsPage';
import {AccountsPage} from '@/pages/accounts/AccountsPage';
import {EvaluationsPage} from '@/pages/evaluations/EvaluationsPage';
import {PublishesPage} from '@/pages/publishes/PublishesPage';
import {TradeValidationPage} from '@/pages/trade-validation/TradeValidationPage';
import {RequireAuth} from '@/router/RequireAuth';
import {appNavItems} from '@/router/navigation';
import type {RouteHandle} from '@/types/navigation';

function createHandle(menuKey: string): RouteHandle {
    const matched = appNavItems.find((item) => item.key === menuKey);

    if (!matched) {
        return {
            title: 'NexusQuant',
            breadcrumb: 'NexusQuant',
        };
    }

    return {
        title: matched.title,
        breadcrumb: matched.label,
        menuKey: matched.key,
    };
}

export const appRouter = createBrowserRouter([
    {
        path: '/login',
        element: <LoginPage/>,
        handle: {
            title: '登录',
            breadcrumb: '登录',
        } satisfies RouteHandle,
    },
    {
        element: <RequireAuth/>,
        children: [
            {
                path: '/',
                element: <ConsoleLayout/>,
                children: [
                    {
                        index: true,
                        element: <Navigate to="/dashboard" replace/>,
                    },
                    {
                        path: 'dashboard',
                        element: <DashboardPage/>,
                        handle: createHandle('dashboard'),
                    },
                    {
                        path: 'strategies',
                        element: <StrategiesPage/>,
                        handle: createHandle('strategies'),
                    },
                    {
                        path: 'schedules',
                        element: <SchedulesPage/>,
                        handle: createHandle('schedules'),
                    },
                    {
                        path: 'runs',
                        element: <RunsPage/>,
                        handle: createHandle('runs'),
                    },
                    {
                        path: 'accounts',
                        element: <AccountsPage/>,
                        handle: createHandle('accounts'),
                    },
                    {
                        path: 'research',
                        element: <ResearchPage/>,
                        handle: createHandle('research'),
                    },
                    {
                        path: 'backtests',
                        element: <BacktestsPage/>,
                        handle: createHandle('backtests'),
                    },
                    {
                        path: 'evaluations',
                        element: <EvaluationsPage/>,
                        handle: createHandle('evaluations'),
                    },
                    {
                        path: 'publishes',
                        element: <PublishesPage/>,
                        handle: createHandle('publishes'),
                    },
                    {
                        path: 'trade-validation',
                        element: <TradeValidationPage/>,
                        handle: createHandle('trade-validation'),
                    },
                ],
            },
        ],
    },
    {
        path: '*',
        element: <NotFoundPage/>,
        handle: {
            title: '未找到页面',
            breadcrumb: '404',
        } satisfies RouteHandle,
    },
]);

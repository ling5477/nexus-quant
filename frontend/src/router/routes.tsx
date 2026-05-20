import {Navigate, createBrowserRouter} from 'react-router-dom';

import {ConsoleLayout} from '@/layouts/ConsoleLayout';
import {AccountsPage} from '@/pages/accounts/AccountsPage';
import {BacktestsPage} from '@/pages/backtests/BacktestsPage';
import {DashboardPage} from '@/pages/dashboard/DashboardPage';
import {EvaluationsPage} from '@/pages/evaluations/EvaluationsPage';
import {InstrumentsPage} from '@/pages/instruments/InstrumentsPage';
import {LoginPage} from '@/pages/login/LoginPage';
import {MarketdataPage} from '@/pages/marketdata/MarketdataPage';
import {NotFoundPage} from '@/pages/not-found/NotFoundPage';
import {PaperTradingPage} from '@/pages/paper-trading/PaperTradingPage';
import {PublishesPage} from '@/pages/publishes/PublishesPage';
import {ResearchPage} from '@/pages/research/ResearchPage';
import {RunsPage} from '@/pages/runs/RunsPage';
import {SchedulesPage} from '@/pages/schedules/SchedulesPage';
import {StrategiesPage} from '@/pages/strategies/StrategiesPage';
import {TradingWorkbenchPage} from '@/pages/trading/TradingWorkbenchPage';
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
                        path: 'accounts',
                        element: <AccountsPage/>,
                        handle: createHandle('accounts'),
                    },
                    {
                        path: 'trading',
                        element: <TradingWorkbenchPage/>,
                        handle: createHandle('trading'),
                    },
                    {
                        // Why:
                        // `/trade-validation` 是历史路由 alias，不是正式入口；正式入口固定为 `/trading`。
                        // 退役计划：GateH-PLAN 后确认旧书签和历史 e2e 均迁移完成，再删除该无状态重定向。
                        path: 'trade-validation',
                        element: <TradingWorkbenchPage legacyAlias/>,
                        handle: createHandle('trading'),
                    },
                    {
                        path: 'instruments',
                        element: <InstrumentsPage/>,
                        handle: createHandle('instruments'),
                    },
                    {
                        path: 'marketdata',
                        element: <MarketdataPage/>,
                        handle: createHandle('marketdata'),
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
                        path: 'paper-trading',
                        element: <PaperTradingPage/>,
                        handle: createHandle('paper-trading'),
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

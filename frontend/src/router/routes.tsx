import {Navigate, createBrowserRouter} from 'react-router-dom';

import {ConsoleLayout} from '@/layouts/ConsoleLayout';
import {AccountsPage} from '@/pages/accounts/AccountsPage';
import {AdapterReadinessPage} from '@/pages/adapters/AdapterReadinessPage';
import {BacktestDetailPage} from '@/pages/backtests/BacktestDetailPage';
import {BacktestsPage} from '@/pages/backtests/BacktestsPage';
import {DashboardPage} from '@/pages/dashboard/DashboardPage';
import {DesignSystemDemoPage} from '@/pages/dev/DesignSystemDemoPage';
import {EvaluationsPage} from '@/pages/evaluations/EvaluationsPage';
import {AuthFailurePage} from '@/pages/exceptions/AuthFailurePage';
import {ForbiddenPage} from '@/pages/exceptions/ForbiddenPage';
import {SystemErrorPage} from '@/pages/exceptions/SystemErrorPage';
import {WelcomePage} from '@/pages/exceptions/WelcomePage';
import {InstrumentsPage} from '@/pages/instruments/InstrumentsPage';
import {LoginPage} from '@/pages/login/LoginPage';
import {MarketdataPage} from '@/pages/marketdata/MarketdataPage';
import {NotFoundPage} from '@/pages/not-found/NotFoundPage';
import {PaperDiagnosticsPage} from '@/pages/paper-trading/PaperDiagnosticsPage';
import {PaperPortfolioPage} from '@/pages/paper-trading/PaperPortfolioPage';
import {PaperReviewsPage} from '@/pages/paper-trading/PaperReviewsPage';
import {PaperTradingRouteShell} from '@/pages/paper-trading/PaperTradingRouteShell';
import {PaperTradingRunsPage} from '@/pages/paper-trading/PaperTradingRunsPage';
import {PublishesPage} from '@/pages/publishes/PublishesPage';
import {ResearchPage} from '@/pages/research/ResearchPage';
import {RuntimeReadinessPage} from '@/pages/runtime/RuntimeReadinessPage';
import {RunsPage} from '@/pages/runs/RunsPage';
import {ShadowRunDetailPage} from '@/pages/shadow-runs/ShadowRunDetailPage';
import {SchedulesPage} from '@/pages/schedules/SchedulesPage';
import {StrategiesPage} from '@/pages/strategies/StrategiesPage';
import {StrategyValidationPage} from '@/pages/strategies/StrategyValidationPage';
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
                        // 退役计划：确认旧书签和历史 e2e 均迁移完成后，再删除该无状态重定向。
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
                        path: 'adapter-readiness',
                        element: <AdapterReadinessPage/>,
                        handle: createHandle('adapter-readiness'),
                    },
                    {
                        path: 'runtime/readiness',
                        element: <RuntimeReadinessPage/>,
                        handle: createHandle('runtime-readiness'),
                    },
                    {
                        path: 'strategies/validation',
                        element: <StrategyValidationPage/>,
                        handle: createHandle('strategy-validation'),
                    },
                    {
                        path: 'strategies/shadow-runs/:shadowRunId',
                        element: <ShadowRunDetailPage/>,
                        handle: createHandle('strategy-validation'),
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
                        // B1:回测详情可视化(权益/回撤/指标/快照),复用 backtests 菜单高亮。
                        path: 'backtests/:backtestConfigId',
                        element: <BacktestDetailPage/>,
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
                        element: <PaperTradingRouteShell/>,
                        handle: createHandle('paper-trading'),
                        children: [
                            {
                                index: true,
                                element: <Navigate to="runs" replace/>,
                                handle: createHandle('paper-trading'),
                            },
                            {
                                path: 'runs',
                                element: <PaperTradingRunsPage/>,
                                handle: createHandle('paper-trading'),
                            },
                            {
                                path: 'portfolio',
                                element: <PaperPortfolioPage/>,
                                handle: createHandle('paper-trading'),
                            },
                            {
                                path: 'diagnostics',
                                element: <PaperDiagnosticsPage/>,
                                handle: createHandle('paper-trading'),
                            },
                            {
                                path: 'reviews',
                                element: <PaperReviewsPage/>,
                                handle: createHandle('paper-trading'),
                            },
                        ],
                    },
                ],
            },
        ],
    },
    {
        // Why:
        // B0(Design Tokens v2)自检路由,非业务页面、不在侧导航中,自带 v2 ConfigProvider 作用域,
        // 不依赖登录/后端,便于本地与 build 后核对 v2 设计系统。后续做 v2 全局采用切片时可下线。
        path: '/dev/design-system',
        element: <DesignSystemDemoPage/>,
        handle: {
            title: 'Design System v2 自检',
            breadcrumb: 'Design System v2',
        } satisfies RouteHandle,
    },
    {
        // Why:
        // B0.1 异常页(v2),AppShell/RequireAuth 之外的公开展示路由,自带 v2 作用域、不依赖后端。
        // 这里只承载异常的"原因 + 下一步"表现层;真实触发(会话过期跳转、403、错误边界、空态检测)
        // 接入这些页面属于后续切片,不在本批改动鉴权/错误处理逻辑。
        path: '/exception/auth',
        element: <AuthFailurePage/>,
        handle: {
            title: '鉴权失败',
            breadcrumb: '鉴权失败',
        } satisfies RouteHandle,
    },
    {
        path: '/exception/forbidden',
        element: <ForbiddenPage/>,
        handle: {
            title: '无访问权限',
            breadcrumb: '无访问权限',
        } satisfies RouteHandle,
    },
    {
        path: '/exception/error',
        element: <SystemErrorPage/>,
        handle: {
            title: '系统错误',
            breadcrumb: '系统错误',
        } satisfies RouteHandle,
    },
    {
        path: '/exception/welcome',
        element: <WelcomePage/>,
        handle: {
            title: '系统待初始化',
            breadcrumb: '系统待初始化',
        } satisfies RouteHandle,
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

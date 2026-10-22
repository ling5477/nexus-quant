import {Navigate, createBrowserRouter} from 'react-router-dom';
import {lazy, type ReactNode} from 'react';
import type {RouteObject} from 'react-router-dom';

import {ConsoleLayout} from '@/layouts/ConsoleLayout';
import {AuthFailurePage} from '@/pages/exceptions/AuthFailurePage';
import {ForbiddenPage} from '@/pages/exceptions/ForbiddenPage';
import {SystemErrorPage} from '@/pages/exceptions/SystemErrorPage';
import {WelcomePage} from '@/pages/exceptions/WelcomePage';
import {ForcePasswordChange} from '@/pages/login/ForcePasswordChange';
import {LoginPage} from '@/pages/login/LoginPage';
import {NotFoundPage} from '@/pages/not-found/NotFoundPage';
import {PaperTradingRouteShell} from '@/pages/paper-trading/PaperTradingRouteShell';
import {RequireAuth} from '@/router/RequireAuth';
import {appNavItems} from '@/router/navigation';
import type {RouteHandle} from '@/types/navigation';
import {t} from '@/i18n';

// 业务页面按路由加载；登录和认证异常路径保持即时可用，不提前装载全站图表与表单。
const AccountsPage = lazy(() =>
    import('@/pages/accounts/AccountsPage').then((module) => ({default: module.AccountsPage})),
);
const AdapterReadinessPage = lazy(() =>
    import('@/pages/adapters/AdapterReadinessPage').then((module) => ({
        default: module.AdapterReadinessPage,
    })),
);
const BacktestDetailPage = lazy(() =>
    import('@/pages/backtests/BacktestDetailPage').then((module) => ({
        default: module.BacktestDetailPage,
    })),
);
const BacktestsPage = lazy(() =>
    import('@/pages/backtests/BacktestsPage').then((module) => ({default: module.BacktestsPage})),
);
const DashboardPage = lazy(() =>
    import('@/pages/dashboard/DashboardPage').then((module) => ({default: module.DashboardPage})),
);
const EvaluationsPage = lazy(() =>
    import('@/pages/evaluations/EvaluationsPage').then((module) => ({
        default: module.EvaluationsPage,
    })),
);
const InstrumentsPage = lazy(() =>
    import('@/pages/instruments/InstrumentsPage').then((module) => ({
        default: module.InstrumentsPage,
    })),
);
const MarketdataPage = lazy(() =>
    import('@/pages/marketdata/MarketdataPage').then((module) => ({
        default: module.MarketdataPage,
    })),
);
const PaperDiagnosticsPage = lazy(() =>
    import('@/pages/paper-trading/PaperDiagnosticsPage').then((module) => ({
        default: module.PaperDiagnosticsPage,
    })),
);
const PaperPortfolioPage = lazy(() =>
    import('@/pages/paper-trading/PaperPortfolioPage').then((module) => ({
        default: module.PaperPortfolioPage,
    })),
);
const PaperReviewsPage = lazy(() =>
    import('@/pages/paper-trading/PaperReviewsPage').then((module) => ({
        default: module.PaperReviewsPage,
    })),
);
const PaperTradingRunsPage = lazy(() =>
    import('@/pages/paper-trading/PaperTradingRunsPage').then((module) => ({
        default: module.PaperTradingRunsPage,
    })),
);
const PublishesPage = lazy(() =>
    import('@/pages/publishes/PublishesPage').then((module) => ({default: module.PublishesPage})),
);
const ResearchPage = lazy(() =>
    import('@/pages/research/ResearchPage').then((module) => ({default: module.ResearchPage})),
);
const RuntimeReadinessPage = lazy(() =>
    import('@/pages/runtime/RuntimeReadinessPage').then((module) => ({
        default: module.RuntimeReadinessPage,
    })),
);
const SchedulerJobsPage = lazy(() =>
    import('@/pages/scheduler/SchedulerJobsPage').then((module) => ({
        default: module.SchedulerJobsPage,
    })),
);
const RunsPage = lazy(() =>
    import('@/pages/runs/RunsPage').then((module) => ({default: module.RunsPage})),
);
const ShadowRunDetailPage = lazy(() =>
    import('@/pages/shadow-runs/ShadowRunDetailPage').then((module) => ({
        default: module.ShadowRunDetailPage,
    })),
);
const ShadowRunListPage = lazy(() =>
    import('@/pages/shadow-runs/ShadowRunListPage').then((module) => ({
        default: module.ShadowRunListPage,
    })),
);
const SchedulesPage = lazy(() =>
    import('@/pages/schedules/SchedulesPage').then((module) => ({default: module.SchedulesPage})),
);
const SettingsPage = lazy(() =>
    import('@/pages/settings/SettingsPage').then((module) => ({default: module.SettingsPage})),
);
const StrategiesPage = lazy(() =>
    import('@/pages/strategies/StrategiesPage').then((module) => ({
        default: module.StrategiesPage,
    })),
);
const StrategyValidationPage = lazy(() =>
    import('@/pages/strategies/StrategyValidationPage').then((module) => ({
        default: module.StrategyValidationPage,
    })),
);
const TradingWorkbenchPage = lazy(() =>
    import('@/pages/trading/TradingWorkbenchPage').then((module) => ({
        default: module.TradingWorkbenchPage,
    })),
);

function createHandle(menuKey: string): RouteHandle {
    const matched = appNavItems.find((item) => item.key === menuKey);

    if (!matched) {
        return {
            title: 'NexusQuant',
            breadcrumb: 'NexusQuant',
        };
    }

    return {
        get title() {
            return matched.title;
        },
        get breadcrumb() {
            return matched.label;
        },
        menuKey: matched.key,
    };
}

export interface DistributionRoutes {
    publicRoutes?: RouteObject[];
    consoleRoutes?: RouteObject[];
    home?: ReactNode;
    simSetup?: RouteObject;
}

/** 发行入口只传入自身可达页面；共享路由不依赖注册或平台管理模块。 */
export function createAppRouter(distribution: DistributionRoutes = {}) {
    return createBrowserRouter([
    ...(distribution.publicRoutes ?? []),
    {
        path: '/login',
        element: <LoginPage />,
        handle: {
            get title() {
                return t('auth.submit');
            },
            get breadcrumb() {
                return t('auth.submit');
            },
        } satisfies RouteHandle,
    },
    {
        element: <RequireAuth />,
        children: [
            {path: '/change-password', element: <ForcePasswordChange />},
            {
                path: '/',
                element: <ConsoleLayout />,
                errorElement: <SystemErrorPage />,
                children: [
                    ...(distribution.consoleRoutes ?? []),
                    {
                        index: true,
                        element: distribution.home ?? <Navigate to="/dashboard" replace />,
                    },
                    {
                        path: 'dashboard',
                        element: <DashboardPage />,
                        handle: createHandle('dashboard'),
                    },
                    {path: 'settings', element: <SettingsPage />, handle: createHandle('settings')},
                    {
                        path: 'accounts',
                        element: <AccountsPage />,
                        handle: createHandle('accounts'),
                    },
                    {
                        path: 'trading',
                        element: <TradingWorkbenchPage />,
                        handle: createHandle('trading'),
                    },
                    {
                        // Why:
                        // `/trade-validation` 是历史路由 alias，不是正式入口；正式入口固定为 `/trading`。
                        // 退役计划：确认旧书签和历史 e2e 均迁移完成后，再删除该无状态重定向。
                        path: 'trade-validation',
                        element: <TradingWorkbenchPage legacyAlias />,
                        handle: createHandle('trading'),
                    },
                    {
                        path: 'instruments',
                        element: <InstrumentsPage />,
                        handle: createHandle('instruments'),
                    },
                    {
                        path: 'marketdata',
                        element: <MarketdataPage />,
                        handle: createHandle('marketdata'),
                    },
                    {
                        path: 'adapter-readiness',
                        element: <AdapterReadinessPage />,
                        handle: createHandle('adapter-readiness'),
                    },
                    {
                        path: 'runtime/readiness',
                        element: <RuntimeReadinessPage />,
                        handle: createHandle('runtime-readiness'),
                    },
                    {
                        path: 'system/scheduler',
                        element: <SchedulerJobsPage />,
                        handle: createHandle('scheduler-jobs'),
                    },
                    {
                        path: 'strategies/validation',
                        element: <StrategyValidationPage />,
                        handle: createHandle('strategy-validation'),
                    },
                    {
                        path: 'strategies/shadow-runs',
                        element: <ShadowRunListPage />,
                        handle: createHandle('shadow-runs'),
                    },
                    {
                        path: 'strategies/shadow-runs/:shadowRunId',
                        element: <ShadowRunDetailPage />,
                        handle: createHandle('shadow-runs'),
                    },
                    {
                        path: 'strategies',
                        element: <StrategiesPage />,
                        handle: createHandle('strategies'),
                    },
                    {
                        path: 'schedules',
                        element: <SchedulesPage />,
                        handle: createHandle('schedules'),
                    },
                    {
                        path: 'runs',
                        element: <RunsPage />,
                        handle: createHandle('runs'),
                    },
                    {
                        path: 'research',
                        element: <ResearchPage />,
                        handle: createHandle('research'),
                    },
                    {
                        path: 'backtests',
                        element: <BacktestsPage />,
                        handle: createHandle('backtests'),
                    },
                    {
                        // 回测详情可视化(权益/回撤/指标/快照),复用 backtests 菜单高亮。
                        path: 'backtests/:backtestConfigId',
                        element: <BacktestDetailPage />,
                        handle: createHandle('backtests'),
                    },
                    {
                        path: 'evaluations',
                        element: <EvaluationsPage />,
                        handle: createHandle('evaluations'),
                    },
                    {
                        path: 'publishes',
                        element: <PublishesPage />,
                        handle: createHandle('publishes'),
                    },
                    {
                        path: 'paper-trading',
                        element: <PaperTradingRouteShell />,
                        handle: createHandle('paper-trading'),
                        children: [
                            {
                                index: true,
                                element: <Navigate to="runs" replace />,
                                handle: createHandle('paper-trading'),
                            },
                            {
                                path: 'runs',
                                ...(distribution.simSetup ?? {element: <PaperTradingRunsPage />}),
                                handle: createHandle('paper-trading'),
                            },
                            {
                                path: 'portfolio',
                                element: <PaperPortfolioPage />,
                                handle: createHandle('paper-portfolio'),
                            },
                            {
                                path: 'diagnostics',
                                element: <PaperDiagnosticsPage />,
                                handle: createHandle('paper-diagnostics'),
                            },
                            {
                                path: 'reviews',
                                element: <PaperReviewsPage />,
                                handle: createHandle('paper-reviews'),
                            },
                        ],
                    },
                ],
            },
        ],
    },
    {
        // Why:
        // 异常页(v2),AppShell/RequireAuth 之外的公开展示路由,自带 v2 作用域、不依赖后端。
        // 这里只承载异常的"原因 + 下一步"表现层;真实触发(会话过期跳转、403、错误边界、空态检测)
        // 接入这些页面属于后续切片,不在本批改动鉴权/错误处理逻辑。
        path: '/exception/auth',
        element: <AuthFailurePage />,
        handle: {
            get title() {
                return t('exception.authFailure');
            },
            get breadcrumb() {
                return t('exception.authFailure');
            },
        } satisfies RouteHandle,
    },
    {
        path: '/exception/forbidden',
        element: <ForbiddenPage />,
        handle: {
            get title() {
                return t('exception.forbidden');
            },
            get breadcrumb() {
                return t('exception.forbidden');
            },
        } satisfies RouteHandle,
    },
    {
        path: '/exception/error',
        element: <SystemErrorPage />,
        handle: {
            get title() {
                return t('exception.system');
            },
            get breadcrumb() {
                return t('exception.system');
            },
        } satisfies RouteHandle,
    },
    {
        path: '/exception/welcome',
        element: <WelcomePage />,
        handle: {
            get title() {
                return t('exception.welcome');
            },
            get breadcrumb() {
                return t('exception.welcome');
            },
        } satisfies RouteHandle,
    },
    {
        path: '*',
        element: <NotFoundPage />,
        handle: {
            get title() {
                return t('exception.notFound');
            },
            breadcrumb: '404',
        } satisfies RouteHandle,
    },
]);

}

import {Breadcrumb, Spin, Typography} from 'antd';
import {Suspense, useEffect, useState} from 'react';
import {Outlet, useMatches, useLocation} from 'react-router-dom';
import {WorkspaceNavigation, workspaceFamily} from '@/components/layout/WorkspaceNavigation';

import {AppHeader} from '@/components/layout/AppHeader';
import {AppSiderMenu} from '@/components/layout/AppSiderMenu';
import type {RouteHandle} from '@/types/navigation';
import {useTranslation} from 'react-i18next';
import {appNavItems} from '@/router/navigation';
import {AppShell} from '@/nq-design-system/shell/AppShell';
import {BrandLockup} from '@/nq-design-system/brand/BrandLockup';
import {LoginDisclosures} from '@/components/legal/LoginDisclosures';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {setLocale} from '@/i18n';
import {SessionLifecycle} from '@/features/session/SessionLifecycle';
import {WhatsNew} from '@/features/system/components/VersionHistory';

export function ConsoleLayout() {
    const {pathname} = useLocation();
    const {t} = useTranslation();
    const {query: preferences} = useUserPreferences();
    useEffect(() => {
        if (preferences.data) void setLocale(preferences.data.locale);
    }, [preferences.data?.locale]);
    const [collapsed, setCollapsed] = useState(
        () => window.matchMedia('(max-width: 767px)').matches,
    );
    const matches = useMatches();
    const breadcrumbItems = matches
        .map((match) => match.handle as RouteHandle | undefined)
        .filter((handle): handle is RouteHandle => Boolean(handle?.breadcrumb))
        .map((handle) => ({
            title:
                appNavItems.find((item) => item.key === handle.menuKey)?.label ?? handle.breadcrumb,
        }));

    return (
        <AppShell
            collapsed={collapsed}
            onCollapsedChange={setCollapsed}
            brand={<BrandLockup compact={collapsed} caption={t('shell.console')} />}
            nav={
                <AppSiderMenu
                    collapsed={collapsed}
                    onNavigate={() => {
                        if (window.matchMedia('(max-width: 767px)').matches) setCollapsed(true);
                    }}
                />
            }
            header={
                <AppHeader
                    collapsed={collapsed}
                    onToggleCollapsed={() => setCollapsed((value) => !value)}
                />
            }
            pageHeader={<Breadcrumb className="app-shell__breadcrumb" items={breadcrumbItems} />}
        >
            <LoginDisclosures />
            <SessionLifecycle />
            <WhatsNew />
            <Suspense
                fallback={
                    <div
                        role="status"
                        aria-busy="true"
                        style={{padding: 32, display: 'flex', gap: 12, alignItems: 'center'}}
                    >
                        <Spin />
                        <Typography.Text>{t('loading')}</Typography.Text>
                    </div>
                }
            >
                <div className="product-workspace" data-family={workspaceFamily(pathname)} data-page={pathname}>
                    <WorkspaceNavigation />
                    <Outlet />
                </div>
            </Suspense>
        </AppShell>
    );
}

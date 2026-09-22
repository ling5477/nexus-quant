import {Breadcrumb} from 'antd';
import {useState} from 'react';
import {Outlet, useMatches} from 'react-router-dom';

import {AppHeader} from '@/components/layout/AppHeader';
import {AppSiderMenu} from '@/components/layout/AppSiderMenu';
import type {RouteHandle} from '@/types/navigation';
import {useTranslation} from 'react-i18next';
import {appNavItems} from '@/router/navigation';
import {AppShell} from '@/nq-design-system/shell/AppShell';
import {BrandLockup} from '@/nq-design-system/brand/BrandLockup';

export function ConsoleLayout() {
    const {t} = useTranslation();
    const [collapsed, setCollapsed] = useState(false);
    const matches = useMatches();
    const breadcrumbItems = matches
        .map((match) => match.handle as RouteHandle | undefined)
        .filter((handle): handle is RouteHandle => Boolean(handle?.breadcrumb))
        .map((handle) => ({
            title: appNavItems.find((item) => item.key === handle.menuKey)?.label ?? handle.breadcrumb,
        }));

    return (
        <AppShell
            collapsed={collapsed}
            onCollapsedChange={setCollapsed}
            brand={<BrandLockup compact={collapsed} caption={t('shell.console')}/>}
            nav={<AppSiderMenu collapsed={collapsed} onNavigate={() => {
                if (window.matchMedia('(max-width: 767px)').matches) setCollapsed(true);
            }}/>}
            header={<AppHeader collapsed={collapsed} onToggleCollapsed={() => setCollapsed((value) => !value)}/>}
            pageHeader={<Breadcrumb className="app-shell__breadcrumb" items={breadcrumbItems}/>}
        >
            <Outlet/>
        </AppShell>
    );
}

import {Breadcrumb, Layout} from 'antd';
import {useState} from 'react';
import {Outlet, useMatches} from 'react-router-dom';

import {AppHeader} from '@/components/layout/AppHeader';
import {AppSiderMenu} from '@/components/layout/AppSiderMenu';
import type {RouteHandle} from '@/types/navigation';

const {Sider, Content} = Layout;

export function ConsoleLayout() {
    const [collapsed, setCollapsed] = useState(false);
    const matches = useMatches();
    const breadcrumbItems = matches
        .map((match) => match.handle as RouteHandle | undefined)
        .filter((handle): handle is RouteHandle => Boolean(handle?.breadcrumb))
        .map((handle) => ({
            title: handle.breadcrumb,
        }));

    return (
        <Layout className="app-shell">
            <Sider
                className="app-shell__sider"
                width={260}
                breakpoint="lg"
                collapsible
                collapsed={collapsed}
                collapsedWidth={88}
                trigger={null}
                onBreakpoint={(broken) => {
                    if (broken) {
                        setCollapsed(true);
                    }
                }}
            >
                <AppSiderMenu collapsed={collapsed}/>
            </Sider>
            <Layout>
                <AppHeader collapsed={collapsed} onToggleCollapsed={() => setCollapsed((value) => !value)}/>
                <Content className="app-shell__content">
                    <div className="app-shell__main">
                        <Breadcrumb className="app-shell__breadcrumb" items={breadcrumbItems}/>
                        <Outlet/>
                    </div>
                </Content>
            </Layout>
        </Layout>
    );
}

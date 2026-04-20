import {Menu, Typography} from 'antd';
import {useLocation, useNavigate} from 'react-router-dom';

import {appNavItems, resolveMenuKey} from '@/router/navigation';
import type {AppNavItem} from '@/types/navigation';

interface AppSiderMenuProps {
    collapsed: boolean;
}

export function AppSiderMenu({collapsed}: AppSiderMenuProps) {
    const location = useLocation();
    const navigate = useNavigate();
    const groupedItems = Array.from(
        appNavItems.reduce((map, item) => {
            const section = item.section ?? '未分组';
            const current = map.get(section) ?? [];
            current.push(item);
            map.set(section, current);
            return map;
        }, new Map<string, AppNavItem[]>()),
    );

    return (
        <>
            <div className="app-shell__logo">
                <span className="app-shell__logo-mark">NQ</span>
                {!collapsed ? (
                    <div className="app-shell__brand">
                        <Typography.Text strong style={{color: '#f8fbff'}}>
                            NexusQuant
                        </Typography.Text>
                        <Typography.Text type="secondary" style={{color: 'rgba(248, 251, 255, 0.68)'}}>
                            Trading Console
                        </Typography.Text>
                    </div>
                ) : null}
            </div>
            <Menu
                mode="inline"
                theme="dark"
                className="app-shell__menu"
                selectedKeys={[resolveMenuKey(location.pathname)]}
                items={groupedItems.map(([section, items]) => ({
                    type: 'group' as const,
                    label: collapsed ? undefined : section,
                    children: items.map((item) => ({
                        key: item.key,
                        icon: item.icon,
                        label: item.label,
                    })),
                }))}
                onClick={({key}) => {
                    const matched = appNavItems.find((item) => item.key === key);
                    if (matched) {
                        navigate(matched.path);
                    }
                }}
            />
        </>
    );
}

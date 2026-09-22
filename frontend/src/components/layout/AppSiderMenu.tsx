import {Menu} from 'antd';
import {useLocation, useNavigate} from 'react-router-dom';

import {appNavItems, resolveMenuKey} from '@/router/navigation';
import type {AppNavItem} from '@/types/navigation';
import {useTranslation} from 'react-i18next';

interface AppSiderMenuProps {
    collapsed: boolean;
    onNavigate?: () => void;
}

export function AppSiderMenu({collapsed, onNavigate}: AppSiderMenuProps) {
    const {t} = useTranslation();
    const location = useLocation();
    const navigate = useNavigate();
    const groupedItems = Array.from(
        appNavItems.reduce((map, item) => {
            const section = item.section ?? t('shell.ungrouped');
            const current = map.get(section) ?? [];
            current.push(item);
            map.set(section, current);
            return map;
        }, new Map<string, AppNavItem[]>()),
    );

    return (
        <>
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
                        onNavigate?.();
                    }
                }}
            />
        </>
    );
}

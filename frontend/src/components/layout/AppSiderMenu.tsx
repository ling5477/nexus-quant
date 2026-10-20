import {Menu} from 'antd';
import {
    FundOutlined,
    ExperimentOutlined,
    DeploymentUnitOutlined,
    RadarChartOutlined,
} from '@ant-design/icons';
import {useEffect, useState} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';

import {appNavItems, resolveMenuKey} from '@/router/navigation';
import {useTranslation} from 'react-i18next';

interface AppSiderMenuProps {
    collapsed: boolean;
    onNavigate?: () => void;
}

export function AppSiderMenu({collapsed, onNavigate}: AppSiderMenuProps) {
    const {t} = useTranslation();
    const location = useLocation();
    const navigate = useNavigate();
    const selectedKey = resolveMenuKey(location.pathname);
    const groups = [
        {
            key: 'market',
            label: t('workspace.navigation.market'),
            icon: <FundOutlined />,
            keys: ['marketdata', 'instruments'],
        },
        {
            key: 'research',
            label: t('workspace.navigation.research'),
            icon: <ExperimentOutlined />,
            keys: [
                'strategies',
                'research',
                'backtests',
                'evaluations',
                'publishes',
                'strategy-validation',
            ],
        },
        {
            key: 'simulation',
            label: t('workspace.navigation.simulation'),
            icon: <DeploymentUnitOutlined />,
            keys: [
                'trading',
                'paper-trading',
                'paper-portfolio',
                'paper-reviews',
                'paper-diagnostics',
            ],
        },
        {
            key: 'monitoring',
            label: t('workspace.navigation.monitoring'),
            icon: <RadarChartOutlined />,
            keys: [
                'schedules',
                'runs',
                'scheduler-jobs',
                'adapter-readiness',
                'runtime-readiness',
                'shadow-runs',
            ],
        },
    ];
    const activeGroup = groups.find((group) => group.keys.includes(selectedKey))?.key;
    const [openKeys, setOpenKeys] = useState<string[]>(activeGroup ? [activeGroup] : []);
    useEffect(() => {
        if (activeGroup) setOpenKeys([activeGroup]);
    }, [activeGroup]);
    const leaf = (key: string) => {
        const item = appNavItems.find((entry) => entry.key === key);
        return item ? {key: item.key, icon: item.icon, label: item.label} : null;
    };
    const items = [
        leaf('dashboard'),
        leaf('accounts'),
        ...groups.map((group) => ({
            key: group.key + '-group',
            icon: group.icon,
            label: group.label,
            children: group.keys.map(leaf).filter((item) => item !== null),
        })),
        leaf('settings'),
    ].filter((item) => item !== null);

    return (
        <>
            <Menu
                mode="inline"
                theme="dark"
                className="app-shell__menu"
                selectedKeys={[selectedKey]}
                openKeys={collapsed ? undefined : openKeys.map((key) => key + '-group')}
                onOpenChange={(keys) => setOpenKeys(keys.map((key) => key.replace(/-group$/, '')))}
                items={items}
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

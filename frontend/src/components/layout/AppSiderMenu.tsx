import {Typography} from 'antd';
import {Menu} from 'antd';
import {useLocation, useNavigate} from 'react-router-dom';

import {appNavItems, resolveMenuKey} from '@/router/navigation';

interface AppSiderMenuProps {
    collapsed: boolean;
}

export function AppSiderMenu({collapsed}: AppSiderMenuProps) {
    const location = useLocation();
    const navigate = useNavigate();

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
                items={appNavItems.map((item) => ({
                    key: item.key,
                    icon: item.icon,
                    label: item.label,
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

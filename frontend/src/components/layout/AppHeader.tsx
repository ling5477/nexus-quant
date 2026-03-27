import {
    LogoutOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
} from '@ant-design/icons';
import {Button, Space, Tag, Typography} from 'antd';
import {useNavigate} from 'react-router-dom';
import {useQueryClient} from '@tanstack/react-query';

import {authQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import {appEnv} from '@/utils/env';

interface AppHeaderProps {
    collapsed: boolean;
    onToggleCollapsed: () => void;
}

export function AppHeader({collapsed, onToggleCollapsed}: AppHeaderProps) {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const currentUser = useAuthStore((state) => state.currentUser);
    const clearAuth = useAuthStore((state) => state.clearAuth);

    const handleLogout = () => {
        clearAuth('manual');
        queryClient.removeQueries({queryKey: authQueryKeys.all});
        navigate('/login', {replace: true});
    };

    return (
        <header className="app-shell__header">
            <div className="app-shell__header-left">
                <Button
                    type="text"
                    icon={collapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>}
                    onClick={onToggleCollapsed}
                    aria-label={collapsed ? '展开菜单' : '收起菜单'}
                />
                <div>
                    <Typography.Text strong>{appEnv.appTitle}</Typography.Text>
                    <br/>
                    <Typography.Text type="secondary">GateG-1 Front Skeleton</Typography.Text>
                </div>
            </div>
            <div className="app-shell__header-right">
                <Tag color="cyan">{appEnv.envLabel}</Tag>
                <Space size={8} wrap>
                    {currentUser?.roles.map((role) => (
                        <Tag key={role}>{role}</Tag>
                    ))}
                </Space>
                <Typography.Text>{currentUser?.username ?? 'anonymous'}</Typography.Text>
                <Button icon={<LogoutOutlined/>} onClick={handleLogout}>
                    退出登录
                </Button>
            </div>
        </header>
    );
}

import {
    DownOutlined,
    LogoutOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
} from '@ant-design/icons';
import {Button, Dropdown, Space, Tag, Typography} from 'antd';
import {useEffect} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {useQueryClient} from '@tanstack/react-query';

import {accountsApi} from '@/api/accounts';
import {authQueryKeys} from '@/api/query-keys';
import {accountQueryKeys} from '@/api/query-keys';
import type {ExchangeAccountSummary} from '@/types/accounts';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import {appEnv} from '@/utils/env';

interface AppHeaderProps {
    collapsed: boolean;
    onToggleCollapsed: () => void;
}

export function AppHeader({collapsed, onToggleCollapsed}: AppHeaderProps) {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const accessToken = useAuthStore((state) => state.accessToken);
    const currentUser = useAuthStore((state) => state.currentUser);
    const clearAuth = useAuthStore((state) => state.clearAuth);
    const clearAccountContext = useAccountContextStore((state) => state.clearAccountContext);
    const selectedExchangeAccountId = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const accountAlias = useAccountContextStore((state) => state.accountAlias);
    const exchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const tradeEnv = useAccountContextStore((state) => state.tradeEnv);
    const setSelectedAccount = useAccountContextStore((state) => state.setSelectedAccount);

    const accountsQuery = useQuery({
        queryKey: accountQueryKeys.list(accessToken),
        queryFn: accountsApi.list,
        enabled: Boolean(accessToken),
    });

    const selectedAccount = (accountsQuery.data ?? []).find((item) => item.exchangeAccountId === selectedExchangeAccountId)
        ?? (accountsQuery.data ?? []).find((item) => item.exchangeAccountId === currentUser?.defaultExchangeAccountId)
        ?? (accountsQuery.data ?? []).find((item) => item.isDefault);

    useEffect(() => {
        if (!selectedAccount) {
            return;
        }
        if (selectedExchangeAccountId === selectedAccount.exchangeAccountId) {
            return;
        }
        setSelectedAccount(selectedAccount);
    }, [selectedAccount, selectedExchangeAccountId, setSelectedAccount]);

    const handleLogout = () => {
        clearAuth('manual');
        clearAccountContext();
        queryClient.removeQueries({queryKey: authQueryKeys.all});
        navigate('/login', {replace: true});
    };

    const accountItems = (accountsQuery.data ?? []).map((item: ExchangeAccountSummary) => ({
        key: String(item.exchangeAccountId),
        label: `${item.exchangeCode} / ${item.tradeEnv} / ${item.accountAlias}`,
        onClick: () => setSelectedAccount(item),
    }));

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
                    <Typography.Text type="secondary">RC1 Account Context</Typography.Text>
                </div>
            </div>
            <div className="app-shell__header-right">
                <Tag color="cyan">{appEnv.envLabel}</Tag>
                <Dropdown menu={{items: accountItems}} trigger={['click']} disabled={accountItems.length === 0}>
                    <Button>
                        {selectedExchangeAccountId ? `${exchangeCode} / ${tradeEnv} / ${accountAlias}` : '选择账户上下文'} <DownOutlined/>
                    </Button>
                </Dropdown>
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

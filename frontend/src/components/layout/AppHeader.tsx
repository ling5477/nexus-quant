import {
    DownOutlined,
    LogoutOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
} from '@ant-design/icons';
import {Button, Dropdown, Space, Tag, Typography} from 'antd';
import {useEffect} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQuery, useQueryClient} from '@tanstack/react-query';

import {accountsApi} from '@/api/accounts';
import {accountQueryKeys, authQueryKeys} from '@/api/query-keys';
import type {ExchangeAccountSummary} from '@/types/accounts';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import {appEnv} from '@/utils/env';
import {useTranslation} from 'react-i18next';
import {LanguageSelect} from '@/i18n/LanguageSelect';

interface AppHeaderProps {
    collapsed: boolean;
    onToggleCollapsed: () => void;
}

export function AppHeader({collapsed, onToggleCollapsed}: AppHeaderProps) {
    const {t} = useTranslation();
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

    const defaultAccount = (accountsQuery.data ?? []).find((item) => item.exchangeAccountId === currentUser?.defaultExchangeAccountId)
        ?? (accountsQuery.data ?? []).find((item) => item.isDefault)
        ?? null;

    useEffect(() => {
        if (selectedExchangeAccountId !== null) {
            return;
        }
        if (defaultAccount) {
            setSelectedAccount(defaultAccount);
            return;
        }
        if (!currentUser?.defaultExchangeAccountId) {
            clearAccountContext();
        }
    }, [clearAccountContext, currentUser?.defaultExchangeAccountId, defaultAccount, selectedExchangeAccountId, setSelectedAccount]);

    const handleLogout = () => {
        clearAuth('manual');
        clearAccountContext();
        queryClient.removeQueries({queryKey: authQueryKeys.all});
        navigate('/login', {replace: true});
    };

    const accountItems = (accountsQuery.data ?? []).map((item: ExchangeAccountSummary) => ({
        key: String(item.exchangeAccountId),
        label: `${item.exchangeCode} / ${item.tradeEnv} / ${item.accountAlias}${item.isDefault ? t('shell.default') : ''}`,
    }));

    const accountLabel = selectedExchangeAccountId && exchangeCode && tradeEnv
        ? `${exchangeCode} / ${tradeEnv} / ${accountAlias ?? '-'}（exchangeAccountId=${selectedExchangeAccountId}）`
        : currentUser?.defaultExchangeAccountId && currentUser.defaultExchangeCode && currentUser.defaultTradeEnv
            ? `${currentUser.defaultExchangeCode} / ${currentUser.defaultTradeEnv} / ${currentUser.defaultAccountAlias ?? '-'}（exchangeAccountId=${currentUser.defaultExchangeAccountId}）`
            : t('shell.chooseAccount');

    return (
        <header className="app-shell__header">
            <div className="app-shell__header-left">
                <Button
                    type="text"
                    icon={collapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>}
                    onClick={onToggleCollapsed}
                    aria-label={collapsed ? t('shell.expand') : t('shell.collapse')}
                />
                <div>
                    <Typography.Text strong>{appEnv.appTitle}</Typography.Text>
                    <br/>
                    {/* 副标题保持中性描述，不声明 Gate 阶段，避免阶段推进后文案过期 */}
                    <Typography.Text type="secondary" style={{fontSize: 12}}>{t('shell.subtitle')}</Typography.Text>
                </div>
            </div>
            <div className="app-shell__header-right">
                <LanguageSelect/>
                <Tag color="cyan">{appEnv.envLabel}</Tag>
                <Dropdown
                    menu={{
                        items: accountItems,
                        onClick: ({key}) => {
                            const matched = (accountsQuery.data ?? []).find((item) => String(item.exchangeAccountId) === key);
                            if (matched) {
                                setSelectedAccount(matched);
                            }
                        },
                    }}
                    trigger={['click']}
                    disabled={accountItems.length === 0}
                >
                    <Button>
                        {accountLabel} <DownOutlined/>
                    </Button>
                </Dropdown>
                <Button onClick={() => navigate('/accounts')}>
                    {t('shell.accounts')}
                </Button>
                <Space size={8} wrap>
                    {currentUser?.roles.map((role) => (
                        <Tag key={role}>{role}</Tag>
                    ))}
                </Space>
                <Typography.Text>{currentUser?.username ?? t('shell.anonymous')}</Typography.Text>
                <Button icon={<LogoutOutlined/>} onClick={handleLogout}>
                    {t('shell.logout')}
                </Button>
            </div>
        </header>
    );
}

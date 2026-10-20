import {
    DownOutlined,
    LogoutOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
} from '@ant-design/icons';
import {App, Avatar, Button, Dropdown, Space, Tag, Tooltip, Typography} from 'antd';
import {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQuery, useQueryClient} from '@tanstack/react-query';

import {accountsApi} from '@/features/accounts/api/accounts';
import {accountQueryKeys} from '@/api/query-keys';
import type {ExchangeAccountSummary} from '@/features/accounts/types/accounts';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {authApi} from '@/api/auth';
import {useAccountContextStore} from '@/store/account-context-store';
import {useTranslation} from 'react-i18next';
import {LanguageSelect} from '@/i18n/LanguageSelect';
import {ExchangeIcon} from '@/nq-design-system/brand/ExchangeBadge';
import {resolveDefaultAccount} from '@/features/accounts/default-account';
import {ObjectSearch} from './ObjectSearch';
import {NotificationCenter} from '@/features/notifications/NotificationCenter';

interface AppHeaderProps {
    collapsed: boolean;
    onToggleCollapsed: () => void;
}

export function AppHeader({collapsed, onToggleCollapsed}: AppHeaderProps) {
    const {t} = useTranslation();
    const navigate = useNavigate();
    const {message} = App.useApp();
    const queryClient = useQueryClient();
    const accessToken = useAuthStore(selectSessionIdentity);
    const currentUser = useAuthStore((state) => state.currentUser);
    const clearAuth = useAuthStore((state) => state.clearAuth);
    const clearAccountContext = useAccountContextStore((state) => state.clearAccountContext);
    const selectedExchangeAccountId = useAccountContextStore(
        (state) => state.selectedExchangeAccountId,
    );
    const accountAlias = useAccountContextStore((state) => state.accountAlias);
    const exchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const tradeEnv = useAccountContextStore((state) => state.tradeEnv);
    const setSelectedAccount = useAccountContextStore((state) => state.setSelectedAccount);
    const [requiresAccountChoice, setRequiresAccountChoice] = useState(false);

    const accountsQuery = useQuery({
        queryKey: accountQueryKeys.list(accessToken),
        queryFn: accountsApi.list,
        enabled: Boolean(accessToken),
    });

    const defaultAccount = resolveDefaultAccount(
        currentUser?.defaultExchangeAccountId,
        accountsQuery.data ?? [],
    );

    useEffect(() => {
        if (selectedExchangeAccountId !== null) {
            if (
                accountsQuery.data &&
                !accountsQuery.data.some(
                    (account) =>
                        account.exchangeAccountId === selectedExchangeAccountId &&
                        account.status === 'ACTIVE',
                )
            ) {
                clearAccountContext();
                setRequiresAccountChoice(true);
            } else if (accountsQuery.data) setRequiresAccountChoice(false);
            return;
        }
        if (defaultAccount && !requiresAccountChoice) {
            setSelectedAccount(defaultAccount);
            return;
        }
        if (!currentUser?.defaultExchangeAccountId) {
            clearAccountContext();
        }
    }, [
        accountsQuery.data,
        clearAccountContext,
        currentUser?.defaultExchangeAccountId,
        defaultAccount,
        requiresAccountChoice,
        selectedExchangeAccountId,
        setSelectedAccount,
    ]);

    const handleLogout = async () => {
        // 服务端先撤销当前登录会话；撤销失败时保留错误并允许重试，不能假报已安全退出。
        try { await authApi.logout(); } catch {
            void message.error(t('sessionLifetime.logoutFailed'));
            return;
        }
        clearAuth('manual');
        clearAccountContext();
        // 业务查询并非都以认证前缀命名，退出必须取消并清理整个身份范围缓存。
        await queryClient.cancelQueries();
        queryClient.clear();
        navigate('/login', {replace: true});
    };

    const accountItems = (accountsQuery.data ?? []).map((item: ExchangeAccountSummary) => ({
        key: String(item.exchangeAccountId),
        icon: <ExchangeIcon code={item.exchangeCode} />,
        disabled: item.status !== 'ACTIVE',
        label: `${item.accountAlias} · ${item.exchangeCode} · ${item.tradeEnv}${item.isDefault ? t('shell.default') : ''}`,
    }));

    const accountLabel = requiresAccountChoice
        ? t('preferences.accountUnavailable')
        : selectedExchangeAccountId && exchangeCode && tradeEnv
          ? `${accountAlias ?? '-'} · ${exchangeCode} · ${tradeEnv}`
          : t('shell.chooseAccount');

    return (
        <header className="app-shell__header">
            <div className="app-shell__header-left">
                <Button
                    type="text"
                    icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                    onClick={onToggleCollapsed}
                    aria-label={collapsed ? t('shell.expand') : t('shell.collapse')}
                />
                <ObjectSearch />
            </div>
            <div className="app-shell__header-right">
                <NotificationCenter />
                <span className="app-shell__timezone" title={t('workspace.timezoneAutomatic')}>
                    {Intl.DateTimeFormat().resolvedOptions().timeZone} · {t('workspace.automatic')}
                </span>
                <LanguageSelect />
                {/* 环境来自所选正式账户；显示上下文不代表具有LIVE调用许可。 */}
                {(tradeEnv === 'SIM' || tradeEnv === 'LIVE') && (
                    <Tag
                        color={tradeEnv === 'LIVE' ? 'red' : 'cyan'}
                        title={t('workspace.accountEnvironmentScope')}
                    >
                        {tradeEnv}
                    </Tag>
                )}
                <Dropdown
                    menu={{
                        items: accountItems,
                        onClick: ({key}) => {
                            const matched = (accountsQuery.data ?? []).find(
                                (item) => String(item.exchangeAccountId) === key,
                            );
                            if (matched?.status === 'ACTIVE') {
                                setRequiresAccountChoice(false);
                                setSelectedAccount(matched);
                            }
                        },
                    }}
                    trigger={['click']}
                    disabled={accountItems.length === 0}
                >
                    <Button
                        className="app-shell__account-selector"
                        title={accountLabel}
                        aria-label={accountLabel}
                    >
                        <ExchangeIcon
                            code={
                                selectedExchangeAccountId && exchangeCode && tradeEnv
                                    ? exchangeCode
                                    : currentUser?.defaultExchangeCode
                            }
                        />
                        <span className="app-shell__account-label">{accountLabel}</span>{' '}
                        <DownOutlined />
                    </Button>
                </Dropdown>
                <Button onClick={() => navigate('/accounts')}>{t('shell.accounts')}</Button>
                <Space className="app-shell__roles" size={8} wrap>
                    {currentUser?.roles.map((role) => (
                        <Tag key={role}>{role}</Tag>
                    ))}
                </Space>
                <span className="app-shell__user" title={currentUser?.roles.join(' · ')}>
                    <Avatar size={28}>
                        {currentUser?.username?.slice(0, 1).toUpperCase() ?? '?'}
                    </Avatar>
                    <Typography.Text className="app-shell__username" title={currentUser?.username}>
                        {currentUser?.username ?? t('shell.anonymous')}
                    </Typography.Text>
                </span>
                <Tooltip title={t('shell.logout')}>
                    <Button
                        type="text"
                        icon={<LogoutOutlined />}
                        aria-label={t('shell.logout')}
                        onClick={handleLogout}
                    />
                </Tooltip>
            </div>
        </header>
    );
}

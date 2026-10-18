import {Alert, Button, Card, Checkbox, Select, Space, Typography} from 'antd';
import {useEffect, useState} from 'react';
import {useQuery, useMutation, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useUserPreferences} from '@/features/preferences/hooks/useUserPreferences';
import {accountsApi} from '@/features/accounts/api/accounts';
import {accountQueryKeys, authQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {LegalDocuments} from '@/components/legal/LegalDocuments';
import {setLocale, type AppLocale} from '@/i18n';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {WatchlistEditor} from '@/features/preferences/components/WatchlistEditor';
import {
    AccountCommandRecovery,
    usePendingAccountCommand,
} from '@/features/accounts/components/AccountCommandRecovery';
import {hasAccountCommandRecord} from '@/features/accounts/api/commands';
import {DefaultAssetCurrency} from '@/features/preferences/components/DefaultAssetCurrency';
import {SystemVersionPanel} from '@/features/system/components/SystemVersionPanel';

/** 个人偏好与默认账户分别走各自正式接口；时区自动跟随浏览器，默认账户使用稳定命令恢复。 */
export function SettingsPage() {
    const {t} = useTranslation();
    const {query, save} = useUserPreferences();
    const token = useAuthStore((state) => state.accessToken);
    const user = useAuthStore((state) => state.currentUser);
    const pendingAccount = usePendingAccountCommand();
    const client = useQueryClient();
    const [hide, setHide] = useState(false),
        [locale, chooseLocale] = useState<AppLocale>('zh-CN');
    const [accountId, chooseAccount] = useState<number | null>(
        user?.defaultExchangeAccountId ?? null,
    );
    const accounts = useQuery({
        queryKey: accountQueryKeys.list(token),
        queryFn: accountsApi.list,
        enabled: Boolean(token),
    });
    useEffect(() => {
        if (query.data) {
            setHide(query.data.hideLoginDisclosures);
            chooseLocale(query.data.locale);
        }
    }, [query.data]);
    useEffect(() => {
        chooseAccount(user?.defaultExchangeAccountId ?? null);
    }, [user?.defaultExchangeAccountId]);
    const setDefault = useMutation({
        mutationFn: (id: number) => accountsApi.setDefault(id),
        onSuccess: async () => {
            await Promise.all([
                client.invalidateQueries({queryKey: accountQueryKeys.all}),
                client.invalidateQueries({queryKey: authQueryKeys.currentUser(token)}),
            ]);
        },
    });
    return (
        <NqPageScaffold>
            <NqPageHeader title={t('preferences.settings')} />
            <AccountCommandRecovery
                onConfirmed={async () => {
                    setDefault.reset();
                    await Promise.all([
                        client.invalidateQueries({queryKey: accountQueryKeys.all}),
                        client.invalidateQueries({queryKey: authQueryKeys.currentUser(token)}),
                    ]);
                }}
            />
            <div className="settings-grid">
                <Card title={t('preferences.personal')} loading={query.isPending}>
                    {query.error && (
                        <Alert
                            type="error"
                            showIcon
                            message={formatApiError(query.error)}
                            action={
                                <Button onClick={() => void query.refetch()}>
                                    {t('preferences.reload')}
                                </Button>
                            }
                        />
                    )}
                    <Space direction="vertical" size={16} style={{width: '100%'}}>
                        <label>
                            {t('preferences.language')}{' '}
                            <Select
                                aria-label={t('preferences.language')}
                                value={locale}
                                onChange={chooseLocale}
                                options={[
                                    {value: 'zh-CN', label: '简体中文'},
                                    {value: 'en-US', label: 'English'},
                                ]}
                            />
                        </label>
                        <Checkbox
                            checked={hide}
                            disabled={!query.data}
                            onChange={(event) => setHide(event.target.checked)}
                        >
                            {t('preferences.hideNextLogin')}
                        </Checkbox>
                        <Typography.Text type="secondary">
                            {t('preferences.timezone')}：
                            {Intl.DateTimeFormat().resolvedOptions().timeZone} ·{' '}
                            {t('workspace.automatic')}
                        </Typography.Text>
                        {save.error && <Alert type="error" message={formatApiError(save.error)} />}
                        <Button
                            type="primary"
                            disabled={!query.data}
                            loading={save.isPending}
                            onClick={() =>
                                save.mutate(
                                    {hideLoginDisclosures: hide, locale},
                                    {
                                        onSuccess: () => {
                                            void setLocale(locale);
                                        },
                                    },
                                )
                            }
                        >
                            {t('preferences.save')}
                        </Button>
                        {save.isSuccess && (
                            <Typography.Text className="nq-text-up" role="status">
                                {t('preferences.saved')}
                            </Typography.Text>
                        )}
                    </Space>
                </Card>
                <Card title={t('preferences.defaultAccount')} loading={accounts.isPending}>
                    {accounts.error && (
                        <Alert
                            type="error"
                            message={formatApiError(accounts.error as AppApiError)}
                        />
                    )}
                    <Space direction="vertical" size={16} style={{width: '100%'}}>
                        <Typography.Text type="secondary">
                            {t('preferences.defaultAccountScope')}
                        </Typography.Text>
                        <Select
                            aria-label={t('preferences.defaultAccount')}
                            value={accountId}
                            onChange={chooseAccount}
                            placeholder={t('shell.chooseAccount')}
                            style={{width: '100%'}}
                            options={(accounts.data ?? []).map((account) => ({
                                value: account.exchangeAccountId,
                                label: `${account.accountAlias} · ${account.exchangeCode} · ${account.tradeEnv}`,
                                disabled: account.status !== 'ACTIVE',
                            }))}
                        />
                        {setDefault.error && (
                            <Alert
                                type="error"
                                message={formatApiError(setDefault.error as AppApiError)}
                            />
                        )}
                        <Button
                            disabled={
                                !accountId ||
                                accountId === user?.defaultExchangeAccountId ||
                                Boolean(pendingAccount) ||
                                hasAccountCommandRecord(user?.userId)
                            }
                            loading={setDefault.isPending}
                            onClick={() => {
                                if (accountId) setDefault.mutate(accountId);
                            }}
                        >
                            {t('preferences.setDefaultAccount')}
                        </Button>
                        {setDefault.isSuccess && (
                            <Typography.Text role="status">
                                {t('preferences.saved')}
                            </Typography.Text>
                        )}
                        <DefaultAssetCurrency
                            accountId={user?.defaultExchangeAccountId ?? null}
                            loading={accounts.isPending}
                            active={Boolean(
                                accounts.data?.some(
                                    (account) =>
                                        account.exchangeAccountId ===
                                            user?.defaultExchangeAccountId &&
                                        account.status === 'ACTIVE',
                                ),
                            )}
                        />
                    </Space>
                </Card>
            </div>
            <Card title={t('markets.watchlist')}>
                <WatchlistEditor />
            </Card>
            <SystemVersionPanel />
            <Card id="legal" title={t('preferences.legal')}>
                <LegalDocuments />
            </Card>
        </NqPageScaffold>
    );
}

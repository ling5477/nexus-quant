import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    App,
    Alert,
    Button,
    Card,
    Descriptions,
    Drawer,
    Empty,
    Form,
    Input,
    Select,
    Space,
    Spin,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';

import {accountsApi} from '@/features/accounts/api/accounts';
import {formatApiError, showApiError} from '@/api/errors';
import {accountQueryKeys, authQueryKeys} from '@/api/query-keys';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {ExchangeBadge} from '@/nq-design-system/brand/ExchangeBadge';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {ExchangeAccountSummary} from '@/features/accounts/types/accounts';
import type {AppApiError} from '@/types/api';
import {
    CredentialWorkspace,
    usePendingCredentialOperation,
} from '@/features/accounts/components/CredentialWorkspace';
import {hasCredentialOperationRecord} from '@/features/accounts/api/credential-operations';
import {
    AccountCommandRecovery,
    usePendingAccountCommand,
} from '@/features/accounts/components/AccountCommandRecovery';
import {readPendingAccountCommand, hasAccountCommandRecord} from '@/features/accounts/api/commands';
import {AccountFeeWorkspace} from '@/features/accounts/components/AccountFeeWorkspace';

interface AccountFormValues {
    exchangeCode: string;
    tradeEnv: string;
    accountAlias: string;
    externalAccountRef?: string;
}

export function AccountsPage() {
    useTranslation('pages');
    const {message, modal} = App.useApp();
    const queryClient = useQueryClient();
    const accessToken = useAuthStore((state) => state.accessToken);
    const currentUser = useAuthStore((state) => state.currentUser);
    const pendingAccountCommand = usePendingAccountCommand();
    const pendingCredentialOperation = usePendingCredentialOperation();
    const selectedExchangeAccountId = useAccountContextStore(
        (state) => state.selectedExchangeAccountId,
    );
    const [accountForm] = useLocalizedForm<AccountFormValues>();
    const [accountDrawerMode, setAccountDrawerMode] = useState<'create' | 'edit' | 'detail' | null>(
        null,
    );
    const [editingAccountId, setEditingAccountId] = useState<number | null>(null);
    const credentialTrigger = useRef<HTMLElement | null>(null);
    const [credentialAccount, setCredentialAccount] = useState<ExchangeAccountSummary | null>(null);
    const [feeAccount, setFeeAccount] = useState<ExchangeAccountSummary | null>(null);
    const [params, setParams] = useSearchParams();
    const deepAccountId = params.get('accountId');
    const deepAccountInvalid =
        deepAccountId !== null &&
        (!/^\d+$/.test(deepAccountId) ||
            !Number.isSafeInteger(Number(deepAccountId)) ||
            Number(deepAccountId) <= 0);
    const closeAccount = () => {
        setAccountDrawerMode(null);
        if (params.has('accountId')) {
            const next = new URLSearchParams(params);
            next.delete('accountId');
            setParams(next);
        }
    };
    useEffect(() => {
        if (
            deepAccountId &&
            /^\d+$/.test(deepAccountId) &&
            Number.isSafeInteger(Number(deepAccountId)) &&
            Number(deepAccountId) > 0
        ) {
            setEditingAccountId(Number(deepAccountId));
            setAccountDrawerMode('detail');
        } else if (deepAccountId !== null) {
            setEditingAccountId(null);
            setAccountDrawerMode(null);
        }
    }, [deepAccountId]);

    const accountsQuery = useQuery({
        queryKey: accountQueryKeys.list(accessToken),
        queryFn: accountsApi.list,
        enabled: Boolean(accessToken),
    });

    const accountDetailQuery = useQuery({
        queryKey: editingAccountId
            ? accountQueryKeys.detail(editingAccountId)
            : ['exchange-accounts', 'detail', 'idle'],
        queryFn: ({signal}) => accountsApi.detail(editingAccountId!, signal),
        enabled:
            (accountDrawerMode === 'edit' || accountDrawerMode === 'detail') &&
            editingAccountId !== null,
    });

    const invalidateAccounts = async (accountId?: number) => {
        await Promise.all([
            queryClient.invalidateQueries({queryKey: accountQueryKeys.all}),
            queryClient.invalidateQueries({queryKey: authQueryKeys.currentUser(accessToken)}),
            accountId
                ? queryClient.invalidateQueries({queryKey: accountQueryKeys.detail(accountId)})
                : Promise.resolve(),
            accountId
                ? queryClient.invalidateQueries({
                      queryKey: accountQueryKeys.activeCredential(accountId),
                  })
                : Promise.resolve(),
        ]);
    };

    const accountCommandError = (error: unknown) => {
        showApiError(error as AppApiError, message);
        if (readPendingAccountCommand(currentUser?.userId)?.phase === 'UNKNOWN') {
            closeAccount();
            accountForm.resetFields();
        }
    };

    const createAccountMutation = useMutation({
        mutationFn: accountsApi.create,
        onSuccess: async (result) => {
            message.success(t('pages:accountCreated'));
            const next = new URLSearchParams(params);
            next.set('accountId', String(result.exchangeAccountId));
            setParams(next);
            setEditingAccountId(result.exchangeAccountId);
            setAccountDrawerMode('detail');
            await invalidateAccounts();
        },
        onError: accountCommandError,
    });

    const updateAccountMutation = useMutation({
        mutationFn: ({
            accountId,
            payload,
        }: {
            accountId: number;
            payload: {accountAlias: string; externalAccountRef?: string | null};
        }) => accountsApi.update(accountId, payload),
        onSuccess: async (_, variables) => {
            message.success(t('pages:accountUpdated'));
            closeAccount();
            await invalidateAccounts(variables.accountId);
        },
        onError: accountCommandError,
    });

    const enableMutation = useMutation({
        mutationFn: accountsApi.enable,
        onSuccess: async (result) => {
            message.success(t('pages:accountEnabled'));
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: accountCommandError,
    });

    const disableMutation = useMutation({
        mutationFn: accountsApi.disable,
        onSuccess: async (result) => {
            message.success(t('pages:accountDisabled'));
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: accountCommandError,
    });

    const setDefaultMutation = useMutation({
        mutationFn: accountsApi.setDefault,
        onSuccess: async (result) => {
            message.success(t('pages:defaultAccountUpdated'));
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: accountCommandError,
    });

    useEffect(() => {
        if (accountDrawerMode === 'create') {
            accountForm.setFieldsValue({
                exchangeCode: currentUser?.defaultExchangeCode ?? 'OKX',
                tradeEnv: currentUser?.defaultTradeEnv ?? 'SIM',
                accountAlias: '',
                externalAccountRef: undefined,
            });
        }
    }, [
        accountDrawerMode,
        accountForm,
        currentUser?.defaultExchangeCode,
        currentUser?.defaultTradeEnv,
    ]);

    useEffect(() => {
        if (accountDrawerMode !== 'edit' || !accountDetailQuery.data) {
            return;
        }
        accountForm.setFieldsValue({
            exchangeCode: accountDetailQuery.data.exchangeCode,
            tradeEnv: accountDetailQuery.data.tradeEnv,
            accountAlias: accountDetailQuery.data.accountAlias,
            externalAccountRef: accountDetailQuery.data.externalAccountRef ?? undefined,
        });
    }, [accountDetailQuery.data, accountDrawerMode, accountForm]);

    const accountSubmitting =
        createAccountMutation.isPending ||
        updateAccountMutation.isPending ||
        enableMutation.isPending ||
        disableMutation.isPending ||
        setDefaultMutation.isPending;
    const accountWritePending =
        accountSubmitting ||
        Boolean(pendingAccountCommand) ||
        hasAccountCommandRecord(currentUser?.userId);
    const existingAccount = accountDrawerMode === 'edit' || accountDrawerMode === 'detail';

    const columns: ColumnsType<ExchangeAccountSummary> = [
        {
            title: t('pages:accountAlias'),
            dataIndex: 'accountAlias',
            key: 'accountAlias',
            width: 240,
        },
        {
            title: t('pages:exchange'),
            dataIndex: 'exchangeCode',
            key: 'exchangeCode',
            width: 120,
            render: (value: string) => <ExchangeBadge code={value} />,
        },
        {
            title: t('pages:environment'),
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 100,
            render: (value: string) => <Tag color={value === 'LIVE' ? 'red' : 'blue'}>{value}</Tag>,
        },
        {
            title: t('pages:accountId'),
            dataIndex: 'exchangeAccountId',
            key: 'exchangeAccountId',
            width: 100,
        },
        {
            title: t('pages:defaultAccount'),
            dataIndex: 'isDefault',
            key: 'isDefault',
            width: 120,
            render: (value: boolean) =>
                value ? <Tag color="success">{t('pages:default')}</Tag> : '-',
        },
        {
            title: t('pages:status'),
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => (
                <Tag color={value === 'ACTIVE' ? 'blue' : 'default'}>
                    {accountStatusLabel(value)}
                </Tag>
            ),
        },
        {
            title: t('pages:actions'),
            key: 'action',
            width: 520,
            render: (_, record) => (
                <Space wrap>
                    <Button
                        type="link"
                        disabled={accountSubmitting}
                        onClick={() => {
                            const next = new URLSearchParams(params);
                            next.set('accountId', String(record.exchangeAccountId));
                            setParams(next);
                        }}
                    >
                        {t('pages:accountWorkspace.detailTitle')}
                    </Button>
                    <Button
                        type="link"
                        onClick={() => {
                            setEditingAccountId(record.exchangeAccountId);
                            setAccountDrawerMode('edit');
                        }}
                        disabled={accountWritePending}
                    >
                        {t('pages:edit')}
                    </Button>
                    <Button
                        type="link"
                        onClick={(event) => {
                            credentialTrigger.current = event.currentTarget;
                            setCredentialAccount(record);
                        }}
                    >
                        {t('pages:credentials')}
                    </Button>
                    <Button type="link" onClick={() => setFeeAccount(record)}>
                        {t('pages:accountFees.title')}
                    </Button>
                    <Button
                        type="link"
                        disabled={
                            accountWritePending || record.isDefault || record.status !== 'ACTIVE'
                        }
                        onClick={() => setDefaultMutation.mutate(record.exchangeAccountId)}
                    >
                        {t('pages:setAsDefault')}
                    </Button>
                    {record.status === 'ACTIVE' ? (
                        <Button
                            type="link"
                            danger
                            disabled={accountWritePending}
                            onClick={() =>
                                modal.confirm({
                                    title: t('pages:accountWorkspace.disableTitle', {
                                        account: record.accountAlias,
                                    }),
                                    content: t('pages:accountWorkspace.disableScope', {
                                        exchange: record.exchangeCode,
                                        environment: record.tradeEnv,
                                    }),
                                    okText: t('pages:disable'),
                                    cancelText: t('pages:cancel'),
                                    okButtonProps: {danger: true},
                                    onOk: () =>
                                        disableMutation.mutateAsync(record.exchangeAccountId),
                                })
                            }
                        >
                            {t('pages:disable')}
                        </Button>
                    ) : (
                        <Button
                            type="link"
                            disabled={accountWritePending}
                            onClick={() => enableMutation.mutate(record.exchangeAccountId)}
                        >
                            {t('pages:enable')}
                        </Button>
                    )}
                </Space>
            ),
        },
    ];

    const currentContextLabel = currentUser?.defaultExchangeAccountId
        ? `${currentUser.defaultAccountAlias} · ${currentUser.defaultExchangeCode} · ${currentUser.defaultTradeEnv}`
        : t('pages:noDefaultAccountContext');

    return (
        <NqPageScaffold>
            {deepAccountInvalid && (
                <Alert type="error" message={t('objectSearch.invalidIdentity')} />
            )}
            <Card className="page-card" bordered={false}>
                <NqPageHeader
                    title={t('pages:accountsAndCredentials')}
                    description={t(
                        'pages:createAccountsSelectADefaultAccountRotateCredentialsAndValidateTheirStructure',
                    )}
                    extra={
                        <Button
                            type="primary"
                            disabled={accountWritePending}
                            onClick={() => {
                                setEditingAccountId(null);
                                setAccountDrawerMode('create');
                            }}
                        >
                            {t('pages:createAccount')}
                        </Button>
                    }
                />
            </Card>
            <AccountCommandRecovery
                onConfirmed={async (receipt) => {
                    await invalidateAccounts(receipt.result.exchangeAccountId);
                    const next = new URLSearchParams(params);
                    next.set('accountId', String(receipt.result.exchangeAccountId));
                    setParams(next);
                    setEditingAccountId(receipt.result.exchangeAccountId);
                    setAccountDrawerMode('detail');
                }}
            />
            {hasCredentialOperationRecord(currentUser?.userId) && (
                <Alert
                    type="warning"
                    showIcon
                    message={t('pages:credentialManager.unknown')}
                    description={
                        pendingCredentialOperation
                            ? t('pages:credentialManager.pendingAccount', {
                                  account: pendingCredentialOperation.accountId,
                              })
                            : t('pages:credentialManager.unreadable')
                    }
                    action={
                        pendingCredentialOperation && (
                            <Button
                                onClick={async (event) => {
                                    credentialTrigger.current = event.currentTarget;
                                    try {
                                        setCredentialAccount(
                                            await accountsApi.detail(
                                                pendingCredentialOperation.accountId,
                                            ),
                                        );
                                    } catch (error) {
                                        showApiError(error as AppApiError, message);
                                    }
                                }}
                            >
                                {t('pages:credentialManager.openRecovery')}
                            </Button>
                        )
                    }
                />
            )}
            <Card className="page-section" bordered={false} title={t('pages:currentContext')}>
                {currentUser?.defaultExchangeAccountId ? (
                    <Space direction="vertical" size={4}>
                        <Typography.Text>
                            {t('pages:currentDefaultAccountContext')}
                            {currentContextLabel}
                        </Typography.Text>
                        <Typography.Text type="secondary">
                            {t('pages:accountWorkspace.temporaryViewing')}：
                            {accountsQuery.data?.find(
                                (account) =>
                                    account.exchangeAccountId === selectedExchangeAccountId,
                            )?.accountAlias ?? t('pages:notSynchronized')}
                        </Typography.Text>
                    </Space>
                ) : (
                    <Alert
                        type="info"
                        showIcon
                        message={t(
                            'pages:noDefaultAccountIsSetSelectingOneRefreshesTheHeaderAndTradingWorkbenchFromTheBackendSourceOfTruth',
                        )}
                    />
                )}
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title={t('pages:accounts')}
                extra={
                    <Button
                        loading={accountsQuery.isFetching}
                        onClick={() => void accountsQuery.refetch()}
                    >
                        {t('preferences.reload')}
                    </Button>
                }
            >
                {accountsQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingAccounts')} />
                ) : accountsQuery.error ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:failedToLoadAccounts')}
                        description={formatApiError(accountsQuery.error as AppApiError)}
                    />
                ) : (accountsQuery.data?.length ?? 0) === 0 ? (
                    <Empty description={t('pages:noExchangeAccountIsLinkedToTheCurrentUser')} />
                ) : (
                    <Table
                        rowKey="exchangeAccountId"
                        columns={columns}
                        dataSource={accountsQuery.data}
                        scroll={{x: 1100}}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                    />
                )}
            </Card>

            <Drawer
                open={accountDrawerMode !== null}
                width="min(560px, 100vw)"
                title={
                    accountDrawerMode === 'create'
                        ? t('pages:createAccount')
                        : accountDrawerMode === 'detail'
                          ? t('pages:accountWorkspace.detailTitle')
                          : t('pages:editAccount')
                }
                onClose={() => {
                    if (!accountSubmitting) closeAccount();
                }}
                maskClosable={!accountSubmitting}
                closable={!accountSubmitting}
                destroyOnClose
            >
                {existingAccount && accountDetailQuery.isPending ? (
                    <Spin />
                ) : existingAccount && accountDetailQuery.error ? (
                    <Alert
                        type="error"
                        message={formatApiError(accountDetailQuery.error as AppApiError)}
                        action={
                            <Button onClick={() => void accountDetailQuery.refetch()}>
                                {t('preferences.reload')}
                            </Button>
                        }
                    />
                ) : (
                    <Space direction="vertical" size={20} style={{width: '100%'}}>
                        {existingAccount && accountDetailQuery.data && (
                            <Descriptions bordered size="small" column={1}>
                                <Descriptions.Item label={t('pages:accountId')}>
                                    {accountDetailQuery.data.exchangeAccountId}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:accountAlias')}>
                                    {accountDetailQuery.data.accountAlias}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:exchange')}>
                                    <ExchangeBadge code={accountDetailQuery.data.exchangeCode} />
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:environment')}>
                                    <Tag
                                        color={
                                            accountDetailQuery.data.tradeEnv === 'LIVE'
                                                ? 'red'
                                                : 'blue'
                                        }
                                    >
                                        {accountDetailQuery.data.tradeEnv}
                                    </Tag>
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:status')}>
                                    {accountStatusLabel(accountDetailQuery.data.status)}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:defaultAccount')}>
                                    {accountDetailQuery.data.isDefault ? t('pages:default') : '—'}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:externalAccountReference')}>
                                    {accountDetailQuery.data.externalAccountRef ?? '—'}
                                </Descriptions.Item>
                                <Descriptions.Item label={t('pages:compatibleLegacyaccountid')}>
                                    {accountDetailQuery.data.legacyAccountId ?? '—'}
                                </Descriptions.Item>
                            </Descriptions>
                        )}
                        {accountDrawerMode === 'detail' ? (
                            <Button
                                disabled={accountWritePending}
                                onClick={() => setAccountDrawerMode('edit')}
                            >
                                {t('pages:editAccount')}
                            </Button>
                        ) : (
                            <Form
                                form={accountForm}
                                layout="vertical"
                                onFinish={(values) => {
                                    if (accountDrawerMode === 'create') {
                                        createAccountMutation.mutate({
                                            exchangeCode: values.exchangeCode,
                                            tradeEnv: values.tradeEnv,
                                            accountAlias: values.accountAlias,
                                            externalAccountRef: values.externalAccountRef || null,
                                        });
                                        return;
                                    }
                                    if (editingAccountId !== null) {
                                        updateAccountMutation.mutate({
                                            accountId: editingAccountId,
                                            payload: {
                                                accountAlias: values.accountAlias,
                                                externalAccountRef:
                                                    values.externalAccountRef || null,
                                            },
                                        });
                                    }
                                }}
                            >
                                <Form.Item
                                    label={t('pages:exchange')}
                                    name="exchangeCode"
                                    rules={[{required: true, message: t('pages:selectAnExchange')}]}
                                >
                                    <Select
                                        disabled={accountDrawerMode === 'edit'}
                                        options={[
                                            {label: 'OKX', value: 'OKX'},
                                            {label: 'BINANCE', value: 'BINANCE'},
                                        ]}
                                    />
                                </Form.Item>
                                <Form.Item
                                    label={t('pages:environment')}
                                    name="tradeEnv"
                                    rules={[
                                        {required: true, message: t('pages:selectAnEnvironment')},
                                    ]}
                                >
                                    <Select
                                        disabled={accountDrawerMode === 'edit'}
                                        options={[
                                            {label: 'SIM', value: 'SIM'},
                                            {label: 'LIVE', value: 'LIVE'},
                                        ]}
                                    />
                                </Form.Item>
                                <Form.Item
                                    label={t('pages:accountAlias')}
                                    name="accountAlias"
                                    rules={[
                                        {
                                            required: true,
                                            whitespace: true,
                                            message: t('pages:enterAnAccountAlias'),
                                        },
                                    ]}
                                >
                                    <Input maxLength={64} />
                                </Form.Item>
                                <Form.Item
                                    label={t('pages:externalAccountReference')}
                                    name="externalAccountRef"
                                >
                                    <Input maxLength={128} placeholder={t('pages:optional')} />
                                </Form.Item>
                                <Space>
                                    <Button
                                        type="primary"
                                        htmlType="submit"
                                        loading={
                                            createAccountMutation.isPending ||
                                            updateAccountMutation.isPending
                                        }
                                    >
                                        {t('pages:save')}
                                    </Button>
                                    <Button disabled={accountSubmitting} onClick={closeAccount}>
                                        {t('pages:cancel')}
                                    </Button>
                                </Space>
                            </Form>
                        )}
                    </Space>
                )}
            </Drawer>

            {credentialAccount && (
                <CredentialWorkspace
                    key={credentialAccount.exchangeAccountId}
                    account={credentialAccount}
                    onClose={() => {
                        setCredentialAccount(null);
                        // 工作区立即卸载会跳过组件库的关闭回调；在提交后的下一帧恢复原入口。
                        requestAnimationFrame(() => {
                            if (credentialTrigger.current?.isConnected) {
                                credentialTrigger.current.focus({preventScroll: true});
                            }
                        });
                    }}
                />
            )}
            {feeAccount && (
                <AccountFeeWorkspace
                    key={`${currentUser?.userId}:${feeAccount.exchangeAccountId}`}
                    account={feeAccount}
                    onClose={() => setFeeAccount(null)}
                />
            )}
        </NqPageScaffold>
    );
}

function accountStatusLabel(status: string) {
    if (status === 'ACTIVE') return t('pages:accountWorkspace.active');
    if (status === 'DISABLED') return t('pages:accountWorkspace.disabled');
    return status;
}

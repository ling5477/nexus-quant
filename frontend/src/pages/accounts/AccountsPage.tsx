import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {App, Alert, Button, Card, Descriptions, Drawer, Empty, Form, Input, Select, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useEffect, useState} from 'react';

import {accountsApi} from '@/features/accounts/api/accounts';
import {formatApiError, showApiError} from '@/api/errors';
import {accountQueryKeys, authQueryKeys} from '@/api/query-keys';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {ExchangeBadge} from '@/nq-design-system/brand/ExchangeBadge';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {
    ExchangeAccountCredentialSummary,
    ExchangeAccountCredentialUpsertRequest,
    ExchangeAccountSummary,
    ExchangeCredentialType,
} from '@/features/accounts/types/accounts';
import type {AppApiError} from '@/types/api';

interface AccountFormValues {
    exchangeCode: string;
    tradeEnv: string;
    accountAlias: string;
    externalAccountRef?: string;
}

interface CredentialFormValues {
    credentialType: ExchangeCredentialType;
    apiKey: string;
    secretKey?: string;
    passphrase?: string;
    privateKeyPem?: string;
}

export function AccountsPage() {
    useTranslation('pages');
    const {message} = App.useApp();
    const queryClient = useQueryClient();
    const accessToken = useAuthStore((state) => state.accessToken);
    const currentUser = useAuthStore((state) => state.currentUser);
    const selectedExchangeAccountId = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const [accountForm] = useLocalizedForm<AccountFormValues>();
    const [credentialForm] = useLocalizedForm<CredentialFormValues>();
    const [accountDrawerMode, setAccountDrawerMode] = useState<'create' | 'edit' | null>(null);
    const [editingAccountId, setEditingAccountId] = useState<number | null>(null);
    const [credentialAccount, setCredentialAccount] = useState<ExchangeAccountSummary | null>(null);

    const accountsQuery = useQuery({
        queryKey: accountQueryKeys.list(accessToken),
        queryFn: accountsApi.list,
        enabled: Boolean(accessToken),
    });

    const accountDetailQuery = useQuery({
        queryKey: editingAccountId ? accountQueryKeys.detail(editingAccountId) : ['exchange-accounts', 'detail', 'idle'],
        queryFn: () => accountsApi.detail(editingAccountId!),
        enabled: accountDrawerMode === 'edit' && editingAccountId !== null,
    });

    const activeCredentialQuery = useQuery({
        queryKey: credentialAccount ? accountQueryKeys.activeCredential(credentialAccount.exchangeAccountId) : ['exchange-accounts', 'active-credential', 'idle'],
        queryFn: () => accountsApi.getActiveCredential(credentialAccount!.exchangeAccountId),
        enabled: credentialAccount !== null,
    });

    const invalidateAccounts = async (accountId?: number) => {
        await Promise.all([
            queryClient.invalidateQueries({queryKey: accountQueryKeys.all}),
            queryClient.invalidateQueries({queryKey: authQueryKeys.currentUser(accessToken)}),
            accountId ? queryClient.invalidateQueries({queryKey: accountQueryKeys.detail(accountId)}) : Promise.resolve(),
            accountId ? queryClient.invalidateQueries({queryKey: accountQueryKeys.activeCredential(accountId)}) : Promise.resolve(),
        ]);
    };

    const createAccountMutation = useMutation({
        mutationFn: accountsApi.create,
        onSuccess: async () => {
            message.success(t('pages:accountCreated'));
            setAccountDrawerMode(null);
            await invalidateAccounts();
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const updateAccountMutation = useMutation({
        mutationFn: ({accountId, payload}: { accountId: number; payload: { accountAlias: string; externalAccountRef?: string | null } }) => accountsApi.update(accountId, payload),
        onSuccess: async (_, variables) => {
            message.success(t('pages:accountUpdated'));
            setAccountDrawerMode(null);
            await invalidateAccounts(variables.accountId);
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const enableMutation = useMutation({
        mutationFn: accountsApi.enable,
        onSuccess: async (result) => {
            message.success(t('pages:accountEnabled'));
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const disableMutation = useMutation({
        mutationFn: accountsApi.disable,
        onSuccess: async (result) => {
            message.success(t('pages:accountDisabled'));
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const setDefaultMutation = useMutation({
        mutationFn: accountsApi.setDefault,
        onSuccess: async (result) => {
            message.success(t('pages:defaultAccountUpdated'));
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const upsertCredentialMutation = useMutation({
        mutationFn: ({accountId, payload}: { accountId: number; payload: ExchangeAccountCredentialUpsertRequest }) => accountsApi.upsertCredential(accountId, payload),
        onSuccess: async (_, variables) => {
            message.success(t('pages:credentialsSavedAndAwaitingValidation'));
            await invalidateAccounts(variables.accountId);
        },
        onError: (error) => showApiError(error as AppApiError, message),
    });

    const verifyCredentialMutation = useMutation({
        mutationFn: accountsApi.verifyCredential,
        onSuccess: async (result) => {
            message.success(t('pages:connectionTestStructuralValidationCompleted'));
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => showApiError(error as AppApiError, message),
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
    }, [accountDrawerMode, accountForm, currentUser?.defaultExchangeCode, currentUser?.defaultTradeEnv]);

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

    useEffect(() => {
        if (!credentialAccount) {
            return;
        }
        const activeCredential = activeCredentialQuery.data?.activeCredential;
        credentialForm.setFieldsValue({
            credentialType: activeCredential?.credentialType ?? defaultCredentialType(credentialAccount.exchangeCode),
            apiKey: '',
            secretKey: undefined,
            passphrase: undefined,
            privateKeyPem: undefined,
        });
    }, [activeCredentialQuery.data, credentialAccount, credentialForm]);

    const credentialType = Form.useWatch('credentialType', credentialForm) as ExchangeCredentialType | undefined;

    const columns: ColumnsType<ExchangeAccountSummary> = [
        {
            title: t('pages:accountId'),
            dataIndex: 'exchangeAccountId',
            key: 'exchangeAccountId',
            width: 120,
        },
        {
            title: t('pages:exchange'),
            dataIndex: 'exchangeCode',
            key: 'exchangeCode',
            width: 120,
            render: (value: string) => <ExchangeBadge code={value}/>,
        },
        {
            title: t('pages:environment'),
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 100,
            render: (value: string) => <Tag color={value === 'LIVE' ? 'red' : 'blue'}>{value}</Tag>,
        },
        {
            title: t('pages:accountAlias'),
            dataIndex: 'accountAlias',
            key: 'accountAlias',
            width: 180,
        },
        {
            title: t('pages:compatibleLegacyaccountid'),
            dataIndex: 'legacyAccountId',
            key: 'legacyAccountId',
            width: 160,
            render: (value: number | null) => value ?? '-',
        },
        {
            title: t('pages:defaultAccount'),
            dataIndex: 'isDefault',
            key: 'isDefault',
            width: 120,
            render: (value: boolean) => value ? <Tag color="success">{t('pages:default')}</Tag> : '-',
        },
        {
            title: t('pages:status'),
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => <Tag color={value === 'ACTIVE' ? 'blue' : 'default'}>{value}</Tag>,
        },
        {
            title: t('pages:actions'),
            key: 'action',
            width: 320,
            render: (_, record) => (
                <Space wrap>
                    <Button type="link" onClick={() => {
                        setEditingAccountId(record.exchangeAccountId);
                        setAccountDrawerMode('edit');
                    }}>
                        {t('pages:edit')}</Button>
                    <Button type="link" onClick={() => setCredentialAccount(record)}>
                        {t('pages:credentials')}</Button>
                    <Button type="link" disabled={record.isDefault || record.status !== 'ACTIVE'} onClick={() => setDefaultMutation.mutate(record.exchangeAccountId)}>
                        {t('pages:setAsDefault')}</Button>
                    {record.status === 'ACTIVE' ? (
                        <Button type="link" danger onClick={() => disableMutation.mutate(record.exchangeAccountId)}>
                            {t('pages:disable')}</Button>
                    ) : (
                        <Button type="link" onClick={() => enableMutation.mutate(record.exchangeAccountId)}>
                            {t('pages:enable')}</Button>
                    )}
                </Space>
            ),
        },
    ];

    const currentContextLabel = currentUser?.defaultExchangeAccountId
        ? `${currentUser.defaultExchangeCode} / ${currentUser.defaultTradeEnv} / ${currentUser.defaultAccountAlias}（exchangeAccountId=${currentUser.defaultExchangeAccountId}）`
        : t('pages:noDefaultAccountContext');

    return (
        <NqPageScaffold>
            <Card className="page-card" bordered={false}>
                <NqPageHeader
                    title={t('pages:accountsAndCredentials')}
                    description={t('pages:createAccountsSelectADefaultAccountRotateCredentialsAndValidateTheirStructure')}
                    badge="RC1-4"
                    extra={<Button type="primary" onClick={() => {
                        setEditingAccountId(null);
                        setAccountDrawerMode('create');
                    }}>{t('pages:createAccount')}</Button>}
                />
            </Card>
            <Card className="page-section" bordered={false} title={t('pages:currentContext')}>
                {currentUser?.defaultExchangeAccountId ? (
                    <Space direction="vertical" size={4}>
                        <Typography.Text>{t('pages:currentDefaultAccountContext')}{currentContextLabel}</Typography.Text>
                        <Typography.Text type="secondary">{t('pages:currentAccountContextStoreSelection')}{selectedExchangeAccountId ?? t('pages:notSynchronized')}</Typography.Text>
                    </Space>
                ) : (
                    <Alert type="info" showIcon message={t('pages:noDefaultAccountIsSetSelectingOneRefreshesTheHeaderAndTradingWorkbenchFromTheBackendSourceOfTruth')}/>
                )}
            </Card>
            <Card className="page-section" bordered={false} title={t('pages:accounts')}>
                {accountsQuery.isLoading ? (
                    <Alert type="info" showIcon message={t('pages:loadingAccounts')}/>
                ) : accountsQuery.error ? (
                    <Alert type="error" showIcon message={t('pages:failedToLoadAccounts')} description={formatApiError(accountsQuery.error as AppApiError)}/>
                ) : (accountsQuery.data?.length ?? 0) === 0 ? (
                    <Empty description={t('pages:noExchangeAccountIsLinkedToTheCurrentUser')}/>
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
                width={560}
                title={accountDrawerMode === 'create' ? t('pages:createAccount') : t('pages:editAccount')}
                onClose={() => setAccountDrawerMode(null)}
                destroyOnClose
            >
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
                                    externalAccountRef: values.externalAccountRef || null,
                                },
                            });
                        }
                    }}
                >
                    <Form.Item label={t('pages:exchange')} name="exchangeCode" rules={[{required: true, message: t('pages:selectAnExchange')}]}>
                        <Select disabled={accountDrawerMode === 'edit'} options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                    </Form.Item>
                    <Form.Item label={t('pages:environment')} name="tradeEnv" rules={[{required: true, message: t('pages:selectAnEnvironment')}]}>
                        <Select disabled={accountDrawerMode === 'edit'} options={[{label: 'SIM', value: 'SIM'}, {label: 'LIVE', value: 'LIVE'}]}/>
                    </Form.Item>
                    <Form.Item label={t('pages:accountAlias')} name="accountAlias" rules={[{required: true, message: t('pages:enterAnAccountAlias')}]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label={t('pages:externalAccountReference')} name="externalAccountRef">
                        <Input placeholder={t('pages:optional')} />
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={createAccountMutation.isPending || updateAccountMutation.isPending}>
                            {t('pages:save')}</Button>
                        <Button onClick={() => setAccountDrawerMode(null)}>{t('pages:cancel')}</Button>
                    </Space>
                </Form>
            </Drawer>

            <Drawer
                open={credentialAccount !== null}
                width={620}
                title={credentialAccount ? t('pages:credentialsForAccount', {account: credentialAccount.accountAlias}) : t('pages:manageCredentials')}
                onClose={() => setCredentialAccount(null)}
                destroyOnClose
            >
                {credentialAccount && (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered size="small" column={1} title={t('pages:currentActiveCredentials')}>
                            <Descriptions.Item label={t('pages:account')}>{credentialAccount.exchangeCode} / {credentialAccount.tradeEnv} / {credentialAccount.accountAlias}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:currentSummary')}>
                                {activeCredentialQuery.isLoading ? t('pages:loading') : activeCredentialQuery.data?.activeCredential ? `${activeCredentialQuery.data.activeCredential.credentialType} / ${activeCredentialQuery.data.activeCredential.maskedAccessKey}` : t('pages:noActiveCredentials')}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:validationStatus')}>
                                {activeCredentialQuery.data?.activeCredential ? renderVerificationStatus(activeCredentialQuery.data.activeCredential) : t('pages:notConfigured')}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:latestValidationResult')}>
                                {activeCredentialQuery.data?.activeCredential?.lastVerificationError ?? t('pages:none')}
                            </Descriptions.Item>
                        </Descriptions>
                        <Form
                            form={credentialForm}
                            layout="vertical"
                            onFinish={(values) => {
                                upsertCredentialMutation.mutate({
                                    accountId: credentialAccount.exchangeAccountId,
                                    payload: {
                                        credentialType: values.credentialType,
                                        apiKey: values.apiKey,
                                        secretKey: values.secretKey || null,
                                        passphrase: values.passphrase || null,
                                        privateKeyPem: values.privateKeyPem || null,
                                    },
                                });
                            }}
                        >
                            <Form.Item label={t('pages:credentialType')} name="credentialType" rules={[{required: true, message: t('pages:selectACredentialType')}]}>
                                <Select options={credentialTypeOptions(credentialAccount.exchangeCode)} />
                            </Form.Item>
                            <Form.Item label={t('pages:apiKey')} name="apiKey" rules={[{required: true, message: t('pages:enterAnApiKey')}]}>
                                <Input />
                            </Form.Item>
                            {(credentialType === 'OKX_API_V5' || credentialType === 'BINANCE_HMAC') ? (
                                <Form.Item label={t('pages:secretKey')} name="secretKey" rules={[{required: true, message: t('pages:enterASecretKey')}]}>
                                    <Input.Password />
                                </Form.Item>
                            ) : null}
                            {credentialType === 'OKX_API_V5' ? (
                                <Form.Item label={t('pages:passphrase')} name="passphrase" rules={[{required: true, message: t('pages:enterAPassphrase')}]}>
                                    <Input.Password />
                                </Form.Item>
                            ) : null}
                            {credentialType === 'BINANCE_ED25519' ? (
                                <Form.Item label={t('pages:privateKeyPem')} name="privateKeyPem" rules={[{required: true, message: t('pages:enterThePrivateKeyPem')}]}>
                                    <Input.TextArea rows={6} />
                                </Form.Item>
                            ) : null}
                            <Space>
                                <Button type="primary" htmlType="submit" loading={upsertCredentialMutation.isPending}>
                                    {t('pages:saveCredentials')}</Button>
                                <Button
                                    onClick={() => verifyCredentialMutation.mutate(credentialAccount.exchangeAccountId)}
                                    loading={verifyCredentialMutation.isPending}
                                    disabled={!activeCredentialQuery.data?.activeCredential}
                                >
                                    {t('pages:testConnectionStructuralValidation')}</Button>
                            </Space>
                        </Form>
                    </Space>
                )}
            </Drawer>
        </NqPageScaffold>
    );
}

function defaultCredentialType(exchangeCode: string): ExchangeCredentialType {
    return exchangeCode === 'BINANCE' ? 'BINANCE_HMAC' : 'OKX_API_V5';
}

function credentialTypeOptions(exchangeCode: string) {
    if (exchangeCode === 'BINANCE') {
        return [
            {label: 'BINANCE_HMAC', value: 'BINANCE_HMAC'},
            {label: 'BINANCE_ED25519', value: 'BINANCE_ED25519'},
        ];
    }
    return [{label: 'OKX_API_V5', value: 'OKX_API_V5'}];
}

function renderVerificationStatus(activeCredential: ExchangeAccountCredentialSummary) {
    const color = activeCredential.verificationStatus === 'VERIFIED'
        ? 'success'
        : activeCredential.verificationStatus === 'FAILED'
            ? 'error'
            : activeCredential.verificationStatus === 'REVOKED'
                ? 'default'
                : 'processing';
    const label = activeCredential.verificationStatus === 'VERIFIED'
        ? t('pages:validated')
        : activeCredential.verificationStatus === 'FAILED'
            ? t('pages:validationFailed')
            : activeCredential.verificationStatus === 'REVOKED'
                ? t('pages:expired')
                : t('pages:awaitingValidation');
    return <Tag color={color}>{label}</Tag>;
}

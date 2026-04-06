import {App, Alert, Button, Card, Descriptions, Drawer, Empty, Form, Input, Select, Space, Table, Tag, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useEffect, useState} from 'react';

import {accountsApi} from '@/api/accounts';
import {formatApiError} from '@/api/errors';
import {accountQueryKeys, authQueryKeys} from '@/api/query-keys';
import {PageHero} from '@/components/page/PageHero';
import {useAuthStore} from '@/store/auth-store';
import {useAccountContextStore} from '@/store/account-context-store';
import type {
    ExchangeAccountCredentialSummary,
    ExchangeAccountCredentialUpsertRequest,
    ExchangeAccountSummary,
    ExchangeCredentialType,
} from '@/types/accounts';
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
    const {message} = App.useApp();
    const queryClient = useQueryClient();
    const accessToken = useAuthStore((state) => state.accessToken);
    const currentUser = useAuthStore((state) => state.currentUser);
    const selectedExchangeAccountId = useAccountContextStore((state) => state.selectedExchangeAccountId);
    const [accountForm] = Form.useForm<AccountFormValues>();
    const [credentialForm] = Form.useForm<CredentialFormValues>();
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
            message.success('账户已创建');
            setAccountDrawerMode(null);
            await invalidateAccounts();
        },
        onError: (error) => message.error(formatApiError(error as AppApiError)),
    });

    const updateAccountMutation = useMutation({
        mutationFn: ({accountId, payload}: { accountId: number; payload: { accountAlias: string; externalAccountRef?: string | null } }) => accountsApi.update(accountId, payload),
        onSuccess: async (_, variables) => {
            message.success('账户已更新');
            setAccountDrawerMode(null);
            await invalidateAccounts(variables.accountId);
        },
        onError: (error) => message.error(formatApiError(error as AppApiError)),
    });

    const enableMutation = useMutation({
        mutationFn: accountsApi.enable,
        onSuccess: async (result) => {
            message.success('账户已启用');
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => message.error(formatApiError(error as AppApiError)),
    });

    const disableMutation = useMutation({
        mutationFn: accountsApi.disable,
        onSuccess: async (result) => {
            message.success('账户已停用');
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => message.error(formatApiError(error as AppApiError)),
    });

    const setDefaultMutation = useMutation({
        mutationFn: accountsApi.setDefault,
        onSuccess: async (result) => {
            message.success('默认账户已更新');
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => message.error(formatApiError(error as AppApiError)),
    });

    const upsertCredentialMutation = useMutation({
        mutationFn: ({accountId, payload}: { accountId: number; payload: ExchangeAccountCredentialUpsertRequest }) => accountsApi.upsertCredential(accountId, payload),
        onSuccess: async (_, variables) => {
            message.success('凭证已写入，当前状态为待校验');
            await invalidateAccounts(variables.accountId);
        },
        onError: (error) => message.error(formatApiError(error as AppApiError)),
    });

    const verifyCredentialMutation = useMutation({
        mutationFn: accountsApi.verifyCredential,
        onSuccess: async (result) => {
            message.success('测试连接（结构性校验）已完成');
            await invalidateAccounts(result.exchangeAccountId);
        },
        onError: (error) => message.error(formatApiError(error as AppApiError)),
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
            title: 'Account ID',
            dataIndex: 'exchangeAccountId',
            key: 'exchangeAccountId',
            width: 120,
        },
        {
            title: '交易所',
            dataIndex: 'exchangeCode',
            key: 'exchangeCode',
            width: 120,
        },
        {
            title: '环境',
            dataIndex: 'tradeEnv',
            key: 'tradeEnv',
            width: 100,
            render: (value: string) => <Tag color={value === 'LIVE' ? 'red' : 'blue'}>{value}</Tag>,
        },
        {
            title: '账户别名',
            dataIndex: 'accountAlias',
            key: 'accountAlias',
            width: 180,
        },
        {
            title: '兼容 legacyAccountId',
            dataIndex: 'legacyAccountId',
            key: 'legacyAccountId',
            width: 160,
            render: (value: number | null) => value ?? '-',
        },
        {
            title: '默认账户',
            dataIndex: 'isDefault',
            key: 'isDefault',
            width: 120,
            render: (value: boolean) => value ? <Tag color="success">默认</Tag> : '-',
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (value: string) => <Tag color={value === 'ACTIVE' ? 'blue' : 'default'}>{value}</Tag>,
        },
        {
            title: '操作',
            key: 'action',
            width: 320,
            render: (_, record) => (
                <Space wrap>
                    <Button type="link" onClick={() => {
                        setEditingAccountId(record.exchangeAccountId);
                        setAccountDrawerMode('edit');
                    }}>
                        编辑
                    </Button>
                    <Button type="link" onClick={() => setCredentialAccount(record)}>
                        凭证
                    </Button>
                    <Button type="link" disabled={record.isDefault || record.status !== 'ACTIVE'} onClick={() => setDefaultMutation.mutate(record.exchangeAccountId)}>
                        设为默认
                    </Button>
                    {record.status === 'ACTIVE' ? (
                        <Button type="link" danger onClick={() => disableMutation.mutate(record.exchangeAccountId)}>
                            停用
                        </Button>
                    ) : (
                        <Button type="link" onClick={() => enableMutation.mutate(record.exchangeAccountId)}>
                            启用
                        </Button>
                    )}
                </Space>
            ),
        },
    ];

    const currentContextLabel = currentUser?.defaultExchangeAccountId
        ? `${currentUser.defaultExchangeCode} / ${currentUser.defaultTradeEnv} / ${currentUser.defaultAccountAlias}（exchangeAccountId=${currentUser.defaultExchangeAccountId}）`
        : '当前没有默认账户上下文';

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" bordered={false} extra={<Button type="primary" onClick={() => {
                setEditingAccountId(null);
                setAccountDrawerMode('create');
            }}>新建账户</Button>}>
                <PageHero
                    title="账户与凭证管理"
                    description="补齐账户创建、默认账户切换、凭证轮换与结构性校验的最小写侧闭环。"
                    badge="RC1-4"
                />
            </Card>
            <Card className="page-section" bordered={false} title="当前上下文">
                {currentUser?.defaultExchangeAccountId ? (
                    <Space direction="vertical" size={4}>
                        <Typography.Text>当前默认账户上下文：{currentContextLabel}</Typography.Text>
                        <Typography.Text type="secondary">account-context-store 当前选中：{selectedExchangeAccountId ?? '未同步'}</Typography.Text>
                    </Space>
                ) : (
                    <Alert type="info" showIcon message="当前未设置默认账户；设为默认账户后 header 与 trade-validation 会跟随后端真源刷新。"/>
                )}
            </Card>
            <Card className="page-section" bordered={false} title="账户列表">
                {accountsQuery.isLoading ? (
                    <Alert type="info" showIcon message="正在加载账户列表..."/>
                ) : accountsQuery.error ? (
                    <Alert type="error" showIcon message="账户列表加载失败" description={formatApiError(accountsQuery.error as AppApiError)}/>
                ) : (accountsQuery.data?.length ?? 0) === 0 ? (
                    <Empty description="当前用户尚未绑定任何 exchange account。"/>
                ) : (
                    <Table
                        rowKey="exchangeAccountId"
                        columns={columns}
                        dataSource={accountsQuery.data}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                    />
                )}
            </Card>

            <Drawer
                open={accountDrawerMode !== null}
                width={560}
                title={accountDrawerMode === 'create' ? '新建账户' : '编辑账户'}
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
                    <Form.Item label="交易所" name="exchangeCode" rules={[{required: true, message: '请选择交易所'}]}>
                        <Select disabled={accountDrawerMode === 'edit'} options={[{label: 'OKX', value: 'OKX'}, {label: 'BINANCE', value: 'BINANCE'}]}/>
                    </Form.Item>
                    <Form.Item label="环境" name="tradeEnv" rules={[{required: true, message: '请选择环境'}]}>
                        <Select disabled={accountDrawerMode === 'edit'} options={[{label: 'SIM', value: 'SIM'}, {label: 'LIVE', value: 'LIVE'}]}/>
                    </Form.Item>
                    <Form.Item label="账户别名" name="accountAlias" rules={[{required: true, message: '请输入账户别名'}]}>
                        <Input />
                    </Form.Item>
                    <Form.Item label="外部账户引用" name="externalAccountRef">
                        <Input placeholder="可空" />
                    </Form.Item>
                    <Space>
                        <Button type="primary" htmlType="submit" loading={createAccountMutation.isPending || updateAccountMutation.isPending}>
                            保存
                        </Button>
                        <Button onClick={() => setAccountDrawerMode(null)}>取消</Button>
                    </Space>
                </Form>
            </Drawer>

            <Drawer
                open={credentialAccount !== null}
                width={620}
                title={credentialAccount ? `凭证管理：${credentialAccount.accountAlias}` : '凭证管理'}
                onClose={() => setCredentialAccount(null)}
                destroyOnClose
            >
                {credentialAccount && (
                    <Space direction="vertical" size={16} style={{display: 'flex'}}>
                        <Descriptions bordered size="small" column={1} title="当前 active 凭证">
                            <Descriptions.Item label="账户">{credentialAccount.exchangeCode} / {credentialAccount.tradeEnv} / {credentialAccount.accountAlias}</Descriptions.Item>
                            <Descriptions.Item label="当前摘要">
                                {activeCredentialQuery.isLoading ? '正在加载...' : activeCredentialQuery.data?.activeCredential ? `${activeCredentialQuery.data.activeCredential.credentialType} / ${activeCredentialQuery.data.activeCredential.maskedAccessKey}` : '当前无 active 凭证'}
                            </Descriptions.Item>
                            <Descriptions.Item label="校验状态">
                                {activeCredentialQuery.data?.activeCredential ? renderVerificationStatus(activeCredentialQuery.data.activeCredential) : '未配置'}
                            </Descriptions.Item>
                            <Descriptions.Item label="最近校验结果">
                                {activeCredentialQuery.data?.activeCredential?.lastVerificationError ?? '无'}
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
                            <Form.Item label="凭证类型" name="credentialType" rules={[{required: true, message: '请选择凭证类型'}]}>
                                <Select options={credentialTypeOptions(credentialAccount.exchangeCode)} />
                            </Form.Item>
                            <Form.Item label="API Key" name="apiKey" rules={[{required: true, message: '请输入 API Key'}]}>
                                <Input />
                            </Form.Item>
                            {(credentialType === 'OKX_API_V5' || credentialType === 'BINANCE_HMAC') ? (
                                <Form.Item label="Secret Key" name="secretKey" rules={[{required: true, message: '请输入 Secret Key'}]}>
                                    <Input.Password />
                                </Form.Item>
                            ) : null}
                            {credentialType === 'OKX_API_V5' ? (
                                <Form.Item label="Passphrase" name="passphrase" rules={[{required: true, message: '请输入 Passphrase'}]}>
                                    <Input.Password />
                                </Form.Item>
                            ) : null}
                            {credentialType === 'BINANCE_ED25519' ? (
                                <Form.Item label="Private Key PEM" name="privateKeyPem" rules={[{required: true, message: '请输入 Private Key PEM'}]}>
                                    <Input.TextArea rows={6} />
                                </Form.Item>
                            ) : null}
                            <Space>
                                <Button type="primary" htmlType="submit" loading={upsertCredentialMutation.isPending}>
                                    保存凭证
                                </Button>
                                <Button
                                    onClick={() => verifyCredentialMutation.mutate(credentialAccount.exchangeAccountId)}
                                    loading={verifyCredentialMutation.isPending}
                                    disabled={!activeCredentialQuery.data?.activeCredential}
                                >
                                    测试连接（结构性校验）
                                </Button>
                            </Space>
                        </Form>
                    </Space>
                )}
            </Drawer>
        </Space>
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
        ? '已校验'
        : activeCredential.verificationStatus === 'FAILED'
            ? '校验失败'
            : activeCredential.verificationStatus === 'REVOKED'
                ? '已失效'
                : '待校验';
    return <Tag color={color}>{label}</Tag>;
}

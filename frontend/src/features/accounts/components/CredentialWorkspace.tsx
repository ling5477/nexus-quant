import {useEffect, useState} from 'react';
import {
    Alert,
    App,
    Button,
    Collapse,
    Descriptions,
    Drawer,
    Dropdown,
    Empty,
    Form,
    Input,
    Select,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import {formatDateTime} from '@/utils/formatters';
import {accountQueryKeys} from '@/api/query-keys';
import type {AppApiError} from '@/types/api';
import type {
    ExchangeAccountCredentialSummary,
    ExchangeAccountSummary,
    ExchangeCredentialType,
} from '../types/accounts';
import {
    credentialOperationChanged,
    credentialOperationsApi,
    hasCredentialOperationRecord,
    readPendingCredentialOperation,
    type CredentialOperationRequest,
    type CredentialOperationType,
    type PendingCredentialOperation,
} from '../api/credential-operations';

export function usePendingCredentialOperation() {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [pending, setPending] = useState(() => readPendingCredentialOperation(owner));
    useEffect(() => {
        const update = () => setPending(readPendingCredentialOperation(owner));
        update();
        window.addEventListener(credentialOperationChanged, update);
        return () => window.removeEventListener(credentialOperationChanged, update);
    }, [owner]);
    return pending?.ownerUserId === owner ? pending : null;
}

type OperationForm = {
    type: CredentialOperationType;
    credentialType: ExchangeCredentialType;
    credentialId?: number;
    recovery?: PendingCredentialOperation;
};
type FormValues = {
    credentialType: ExchangeCredentialType;
    apiKey?: string;
    secretKey?: string;
    passphrase?: string;
    privateKeyPem?: string;
    reason?: string;
};

/** 敏感输入只存在当前表单与一次请求中，完成/失败/关闭/换身份均清空，不进入mutation缓存。 */
export function CredentialWorkspace({
    account,
    onClose,
}: {
    account: ExchangeAccountSummary;
    onClose: () => void;
}) {
    const {t} = useTranslation('pages');
    const {modal, message} = App.useApp();
    const queryClient = useQueryClient();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const pending = usePendingCredentialOperation();
    const [form] = useLocalizedForm<FormValues>();
    const [before, setBefore] = useState<number>();
    const [operation, setOperation] = useState<OperationForm | null>(null);
    const [selected, setSelected] = useState<ExchangeAccountCredentialSummary | null>(null);
    const [busy, setBusy] = useState(false);
    const [lookupBusy, setLookupBusy] = useState(false);
    const [notRecorded, setNotRecorded] = useState(false);
    const [error, setError] = useState<AppApiError | null>(null);
    const type = Form.useWatch('credentialType', form) as ExchangeCredentialType | undefined;
    const history = useQuery({
        queryKey: accountQueryKeys.credentialHistory(owner, account.exchangeAccountId, before),
        queryFn: ({signal}) =>
            credentialOperationsApi.history(account.exchangeAccountId, before, signal),
    });
    const blocked = busy || Boolean(pending) || hasCredentialOperationRecord(owner);
    const scopedPending = pending?.accountId === account.exchangeAccountId ? pending : null;
    useEffect(() => {
        form.resetFields();
        setOperation(null);
        setSelected(null);
        setNotRecorded(false);
        setError(null);
    }, [owner, account.exchangeAccountId, form]);
    useEffect(() => {
        setNotRecorded(false);
    }, [pending?.operationId]);
    const refresh = async () => {
        setBefore(undefined);
        setSelected(null);
        await queryClient.invalidateQueries({queryKey: accountQueryKeys.all});
    };
    const begin = (
        type: CredentialOperationType,
        row?: ExchangeAccountCredentialSummary,
        recovery?: PendingCredentialOperation,
    ) => {
        form.resetFields();
        setError(null);
        const credentialType =
            recovery?.credentialType ??
            row?.credentialType ??
            (account.exchangeCode === 'BINANCE' ? 'BINANCE_HMAC' : 'OKX_API_V5');
        setOperation({
            type,
            credentialType,
            credentialId: recovery?.credentialId ?? row?.credentialId,
            recovery,
        });
        form.setFieldsValue({credentialType});
    };
    const lookup = async () => {
        if (!scopedPending || lookupBusy || busy) return;
        setLookupBusy(true);
        setError(null);
        setNotRecorded(false);
        try {
            const receipt = await credentialOperationsApi.result(scopedPending);
            credentialOperationsApi.confirm(scopedPending);
            setOperation(null);
            form.resetFields();
            message.success(
                t('pages:credentialManager.confirmed', {version: receipt.result.credentialId}),
            );
            await refresh();
        } catch (failure) {
            if ((failure as AppApiError).status === 404) setNotRecorded(true);
            else setError(failure as AppApiError);
        } finally {
            setLookupBusy(false);
        }
    };
    const submit = async (values: FormValues) => {
        if (!operation || busy || lookupBusy || (!operation.recovery && blocked)) return;
        const material = operation.type === 'CREATE' || operation.type === 'ROTATE';
        const request: CredentialOperationRequest = {
            type: operation.type,
            credentialType: values.credentialType,
            credentialId: operation.credentialId,
            ...(material
                ? {
                      apiKey: values.apiKey,
                      secretKey:
                          values.credentialType !== 'BINANCE_ED25519' ? values.secretKey : null,
                      passphrase: values.credentialType === 'OKX_API_V5' ? values.passphrase : null,
                      privateKeyPem:
                          values.credentialType === 'BINANCE_ED25519' ? values.privateKeyPem : null,
                  }
                : {}),
            ...(!['CREATE', 'VERIFY'].includes(operation.type)
                ? {reason: values.reason || null}
                : {}),
        };
        let confirmed = false;
        setBusy(true);
        setError(null);
        try {
            const receipt = operation.recovery
                ? await credentialOperationsApi.recover(operation.recovery, request)
                : await credentialOperationsApi.submit(account.exchangeAccountId, request);
            confirmed = true;
            setOperation(null);
            message.success(
                t('pages:credentialManager.confirmed', {version: receipt.result.credentialId}),
            );
            await refresh();
        } catch (failure) {
            setError(failure as AppApiError);
        } finally {
            form.resetFields();
            if (!confirmed) form.setFieldsValue({credentialType: request.credentialType});
            setBusy(false);
        }
    };
    const confirmSubmit = async () => {
        if (busy || lookupBusy || (blocked && !operation?.recovery)) return;
        await form.validateFields();
        if (!operation) return;
        modal.confirm({
            title: t('pages:credentialManager.confirmTitle', {
                operation: t(`pages:credentialManager.op_${operation.type}`),
            }),
            content: `${account.accountAlias} · ${account.exchangeCode} / ${account.tradeEnv} · ${operation.credentialId ?? t('pages:credentialManager.newVersion')}\n${t(`pages:credentialManager.effect_${operation.type}`)}`,
            okText: t(
                operation.recovery
                    ? 'pages:credentialManager.recover'
                    : 'pages:credentialManager.submit',
            ),
            cancelText: t('pages:cancel'),
            onOk: async () => {
                await submit(await form.validateFields());
            },
        });
    };
    const columns: ColumnsType<ExchangeAccountCredentialSummary> = [
        {
            title: t('pages:credentialManager.version'),
            key: 'version',
            width: 210,
            render: (_, row) => (
                <Space direction="vertical" size={2}>
                    <Button type="link" size="small" onClick={() => setSelected(row)}>
                        #{row.credentialId} · {row.maskedAccessKey ?? '—'}
                    </Button>
                    <Typography.Text type="secondary">
                        {t(`pages:credentialManager.type_${row.credentialType}`)}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: t('pages:credentialManager.state'),
            dataIndex: 'credentialStatus',
            width: 120,
            render: (value) => (
                <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>
                    {t(`pages:credentialManager.state_${value}`, {defaultValue: value})}
                </Tag>
            ),
        },
        {
            title: t('pages:credentialManager.validation'),
            width: 160,
            render: (_, row) => (
                <Space direction="vertical" size={2}>
                    <Typography.Text>
                        {t(`pages:credentialManager.validation_${row.verificationStatus}`, {
                            defaultValue: row.verificationStatus,
                        })}
                    </Typography.Text>
                    <Typography.Text type="secondary">
                        {formatDateTime(row.lastVerifiedAt)}
                    </Typography.Text>
                </Space>
            ),
        },
        {
            title: t('pages:actions'),
            key: 'actions',
            width: 190,
            render: (_, row) => {
                const active = row.isActive && row.credentialStatus === 'ACTIVE';
                const items = [
                    ...(active
                        ? [
                              {key: 'ROTATE', label: t('pages:credentialManager.op_ROTATE')},
                              {key: 'DISABLE', label: t('pages:credentialManager.op_DISABLE')},
                          ]
                        : []),
                    ...(row.credentialStatus === 'DISABLED'
                        ? [{key: 'ENABLE', label: t('pages:credentialManager.op_ENABLE')}]
                        : []),
                    ...(['ACTIVE', 'DISABLED'].includes(row.credentialStatus)
                        ? [{key: 'EXPIRE', label: t('pages:credentialManager.op_EXPIRE')}]
                        : []),
                    ...(row.credentialStatus !== 'REVOKED'
                        ? [
                              {
                                  key: 'REVOKE',
                                  danger: true,
                                  label: t('pages:credentialManager.op_REVOKE'),
                              },
                          ]
                        : []),
                ];
                return (
                    <Space size={4}>
                        {active && (
                            <Button
                                size="small"
                                disabled={blocked || history.isError}
                                onClick={() => begin('VERIFY', row)}
                            >
                                {t('pages:credentialManager.op_VERIFY')}
                            </Button>
                        )}
                        {items.length > 0 && (
                            <Dropdown
                                menu={{
                                    items,
                                    onClick: ({key}) => begin(key as CredentialOperationType, row),
                                }}
                                trigger={['click']}
                                disabled={blocked || history.isError}
                            >
                                <Button size="small">{t('pages:credentialManager.more')} ▾</Button>
                            </Dropdown>
                        )}
                    </Space>
                );
            },
        },
    ];
    const material = operation?.type === 'CREATE' || operation?.type === 'ROTATE';
    return (
        <Drawer
            open
            width="min(860px, 100vw)"
            title={t('pages:credentialsForAccount', {account: account.accountAlias})}
            onClose={() => {
                if (!busy) {
                    form.resetFields();
                    onClose();
                }
            }}
            closable={!busy}
            maskClosable={!busy}
            destroyOnClose
        >
            <Space direction="vertical" size={16} style={{width: '100%'}}>
                <Typography.Text>
                    {account.exchangeCode} / {account.tradeEnv} · {account.accountAlias}
                </Typography.Text>
                <Typography.Text type="secondary">
                    {t('pages:credentialManager.scope')}
                </Typography.Text>
                {error && <Alert type="error" showIcon message={formatApiError(error)} />}
                {hasCredentialOperationRecord(owner) && !pending && (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:credentialManager.unreadable')}
                    />
                )}
                {pending && !scopedPending && (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:credentialManager.otherPending', {
                            account: pending.accountId,
                        })}
                    />
                )}
                {scopedPending?.phase === 'UNKNOWN' && (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:credentialManager.unknown')}
                        description={
                            <Space direction="vertical" style={{width: '100%'}}>
                                <Typography.Text>
                                    {t('pages:credentialManager.unknownMeaning')}
                                </Typography.Text>
                                <Typography.Text>
                                    {t(`pages:credentialManager.op_${scopedPending.type}`)} · #
                                    {scopedPending.credentialId ?? '—'} ·{' '}
                                    {scopedPending.operationId}
                                </Typography.Text>
                                {notRecorded && (
                                    <Typography.Text>
                                        {t(
                                            scopedPending.type === 'VERIFY'
                                                ? 'pages:credentialManager.notRecordedTarget'
                                                : 'pages:credentialManager.notRecorded',
                                        )}
                                    </Typography.Text>
                                )}
                                <Space wrap>
                                    <Button
                                        loading={lookupBusy}
                                        disabled={busy || lookupBusy}
                                        onClick={() => void lookup()}
                                    >
                                        {t('pages:credentialManager.lookup')}
                                    </Button>
                                    <Button
                                        disabled={!notRecorded || busy || lookupBusy}
                                        onClick={() =>
                                            begin(scopedPending.type, undefined, scopedPending)
                                        }
                                    >
                                        {t(
                                            scopedPending.type === 'VERIFY'
                                                ? 'pages:credentialManager.originalTarget'
                                                : 'pages:credentialManager.originalInput',
                                        )}
                                    </Button>
                                </Space>
                            </Space>
                        }
                    />
                )}
                <Space wrap>
                    <Typography.Text strong>{t('pages:credentialManager.history')}</Typography.Text>
                    <Button
                        size="small"
                        loading={history.isFetching}
                        onClick={() => void history.refetch()}
                    >
                        {t('common:preferences.reload')}
                    </Button>
                    <Button
                        type="primary"
                        size="small"
                        disabled={blocked || !history.data || history.isError}
                        onClick={() => begin('CREATE')}
                    >
                        {t('pages:credentialManager.op_CREATE')}
                    </Button>
                </Space>
                {history.isError && (
                    <Alert
                        type="error"
                        showIcon
                        message={formatApiError(history.error as AppApiError)}
                    />
                )}
                <Table
                    size="small"
                    rowKey="credentialId"
                    columns={columns}
                    dataSource={history.data?.items ?? []}
                    loading={history.isPending}
                    pagination={false}
                    scroll={{x: 720}}
                    locale={{
                        emptyText: (
                            <Empty
                                description={
                                    history.isPending
                                        ? t('pages:loading')
                                        : history.isError
                                          ? t('pages:accountWorkspace.readUnavailable')
                                          : t('pages:credentialManager.empty')
                                }
                            />
                        ),
                    }}
                />
                <Space wrap>
                    <Typography.Text type="secondary">
                        {t('pages:credentialManager.pageScope')}
                    </Typography.Text>
                    {before && (
                        <Button size="small" onClick={() => setBefore(undefined)}>
                            {t('pages:credentialManager.latest')}
                        </Button>
                    )}
                    <Button
                        size="small"
                        disabled={!history.data?.hasMore || history.isFetching}
                        onClick={() => setBefore(history.data?.nextBefore ?? undefined)}
                    >
                        {t('pages:credentialManager.older')}
                    </Button>
                </Space>
                {selected && (
                    <Collapse
                        items={[
                            {
                                key: 'details',
                                label: t('pages:credentialManager.details', {
                                    version: selected.credentialId,
                                }),
                                children: (
                                    <Descriptions size="small" bordered column={1}>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.version')}
                                        >
                                            #{selected.credentialId} · {selected.credentialType}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.rotatedFrom')}
                                        >
                                            {selected.rotatedFromCredentialId ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.rotatedAt')}
                                        >
                                            {formatDateTime(selected.rotatedAt)}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.revokedAt')}
                                        >
                                            {formatDateTime(selected.revokedAt)}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.validationError')}
                                        >
                                            {selected.lastVerificationError ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.permissionStatus')}
                                        >
                                            {t(
                                                `pages:credentialManager.probe_${selected.permissionProbeStatus}`,
                                                {defaultValue: selected.permissionProbeStatus},
                                            )}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.permissionScope')}
                                        >
                                            {selected.permissionScope ??
                                                t('pages:credentialManager.notProbed')}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.permissionTime')}
                                        >
                                            {formatDateTime(selected.lastPermissionProbeAt)}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.permissionError')}
                                        >
                                            {selected.lastPermissionProbeError ?? '—'}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.ipStatus')}
                                        >
                                            {t(
                                                `pages:credentialManager.ip_${selected.ipAllowlistProbeStatus}`,
                                                {defaultValue: selected.ipAllowlistProbeStatus},
                                            )}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:credentialManager.failedAuthCount')}
                                        >
                                            {selected.failedAuthCount}
                                        </Descriptions.Item>
                                        <Descriptions.Item
                                            label={t('pages:accountWorkspace.updatedAt')}
                                        >
                                            {formatDateTime(selected.updatedAt)}
                                        </Descriptions.Item>
                                    </Descriptions>
                                ),
                            },
                        ]}
                        defaultActiveKey={['details']}
                    />
                )}
                {operation && (
                    <Form
                        form={form}
                        layout="vertical"
                        preserve={false}
                        onFinish={() => void confirmSubmit()}
                        disabled={busy || lookupBusy}
                    >
                        <Typography.Title level={5}>
                            {t(`pages:credentialManager.op_${operation.type}`)}{' '}
                            {operation.credentialId && `· #${operation.credentialId}`}
                        </Typography.Title>
                        <Typography.Paragraph type="secondary">
                            {t(`pages:credentialManager.effect_${operation.type}`)}
                        </Typography.Paragraph>
                        {operation.recovery && (
                            <Alert
                                type="warning"
                                showIcon
                                message={t(
                                    operation.type === 'VERIFY'
                                        ? 'pages:credentialManager.originalTargetMeaning'
                                        : 'pages:credentialManager.originalInputMeaning',
                                )}
                            />
                        )}
                        <Form.Item
                            name="credentialType"
                            label={t('pages:credentialType')}
                            rules={[{required: true}]}
                        >
                            <Select
                                disabled={
                                    operation.type !== 'CREATE' || Boolean(operation.recovery)
                                }
                                onChange={() =>
                                    form.resetFields([
                                        'apiKey',
                                        'secretKey',
                                        'passphrase',
                                        'privateKeyPem',
                                    ])
                                }
                                options={(account.exchangeCode === 'BINANCE'
                                    ? ['BINANCE_HMAC', 'BINANCE_ED25519']
                                    : ['OKX_API_V5']
                                ).map((value) => ({
                                    value,
                                    label: t(`pages:credentialManager.type_${value}`),
                                }))}
                            />
                        </Form.Item>
                        {material && (
                            <>
                                <Form.Item
                                    name="apiKey"
                                    label={t('pages:apiKey')}
                                    rules={[{required: true}]}
                                >
                                    <Input.Password maxLength={512} autoComplete="off" />
                                </Form.Item>
                                {type !== 'BINANCE_ED25519' && (
                                    <Form.Item
                                        name="secretKey"
                                        label={t('pages:secretKey')}
                                        rules={[{required: true}]}
                                    >
                                        <Input.Password
                                            maxLength={4096}
                                            autoComplete="new-password"
                                        />
                                    </Form.Item>
                                )}
                                {type === 'OKX_API_V5' && (
                                    <Form.Item
                                        name="passphrase"
                                        label={t('pages:passphrase')}
                                        rules={[{required: true}]}
                                    >
                                        <Input.Password
                                            maxLength={512}
                                            autoComplete="new-password"
                                        />
                                    </Form.Item>
                                )}
                                {type === 'BINANCE_ED25519' && (
                                    <Form.Item
                                        name="privateKeyPem"
                                        label={t('pages:privateKeyPem')}
                                        rules={[{required: true}]}
                                    >
                                        <Input.TextArea
                                            rows={5}
                                            maxLength={16384}
                                            autoComplete="off"
                                        />
                                    </Form.Item>
                                )}
                            </>
                        )}
                        {!['CREATE', 'VERIFY'].includes(operation.type) && (
                            <Form.Item
                                name="reason"
                                label={t('pages:credentialManager.reason')}
                                rules={[{required: ['ENABLE', 'ROTATE'].includes(operation.type)}]}
                            >
                                <Input.TextArea
                                    maxLength={1024}
                                    rows={2}
                                    placeholder={t('pages:credentialManager.reasonHint')}
                                />
                            </Form.Item>
                        )}
                        <Space wrap>
                            <Button
                                type="primary"
                                danger={operation.type === 'REVOKE'}
                                htmlType="submit"
                                loading={busy}
                                disabled={lookupBusy || (blocked && !operation.recovery)}
                            >
                                {t(
                                    operation.recovery
                                        ? 'pages:credentialManager.recover'
                                        : 'pages:credentialManager.submit',
                                )}
                            </Button>
                            <Button
                                disabled={busy}
                                onClick={() => {
                                    form.resetFields();
                                    setOperation(null);
                                }}
                            >
                                {t('pages:cancel')}
                            </Button>
                        </Space>
                    </Form>
                )}
            </Space>
        </Drawer>
    );
}

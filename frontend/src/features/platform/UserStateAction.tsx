import {App, Alert, Button, Form, Input, Modal, Space, Typography} from 'antd';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {ApiErrorNotice} from '@/errors/ApiErrorNotice';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {clearPendingUserOperation, readPendingUserOperation, savePendingUserOperation, type PendingUserOperation} from './pending-operation';
import {platformApi, type TenantItem, type UserOperationInput} from './api';

/** 未知网络结果保留同一请求身份与内容；不生成新 operationId 盲重放。 */
export function UserStateAction({user}: {user: TenantItem}) {
    const {t} = useTranslation('platform');
    const {message, modal} = App.useApp();
    const client = useQueryClient();
    const identity = useAuthStore(selectSessionIdentity) ?? '';
    const pending = useRef<PendingUserOperation | null>(null);
    const [open, setOpen] = useState(false);
    const [request, setRequest] = useState<UserOperationInput | null>(null);
    const [form] = Form.useForm<{reason: string}>();
    const [targetEnabled, setTargetEnabled] = useState(!user.enabled);
    const mutation = useMutation({
        mutationFn: (operation: PendingUserOperation) => platformApi.setEnabled(user.userId, operation.enabled, operation.input),
        retry: false,
        onSuccess: async (result) => {
            await client.invalidateQueries({queryKey: ['platform']});
            void message.success(t('operationSucceeded'));
            modal.success({title: t('operationSucceeded'), content: <Space direction="vertical">
                <Typography.Text>{t('operationId')}: {result.operationId}</Typography.Text>
                <Typography.Text>{t('changedAt')}: {formatDateTime(result.createdAt)}</Typography.Text>
                <Typography.Text>{targetEnabled ? t('enableDescription') : t('disableDescription')}</Typography.Text>
            </Space>});
            clearPendingUserOperation(identity, user.userId); pending.current = null;
            setOpen(false); setRequest(null); form.resetFields();
        },
    });
    return <>
        <Button disabled={!user.tenantId || !user.emailVerified} title={!user.tenantId || !user.emailVerified ? t('initializationRequired') : undefined} danger={user.enabled} size="small" onClick={() => {
            const prior = pending.current ?? readPendingUserOperation(identity, user.userId);
            pending.current = prior; setTargetEnabled(prior?.enabled ?? !user.enabled); setOpen(true);
            setRequest(prior?.input ?? null); mutation.reset(); form.setFieldsValue({reason: prior?.input.reason ?? ''});
        }}>{t(user.enabled ? 'disable' : 'enable')}</Button>
        <Modal open={open} title={t(targetEnabled ? 'confirmEnable' : 'confirmDisable')}
            closable={!mutation.isPending} maskClosable={false}
            onCancel={() => {if (!mutation.isPending) setOpen(false);}}
            footer={null} destroyOnHidden>
            <Typography.Paragraph>{user.username}</Typography.Paragraph>
            <Alert type={targetEnabled ? 'info' : 'warning'} showIcon
                message={t(targetEnabled ? 'enableDescription' : 'disableDescription')} />
            {mutation.error && <ApiErrorNotice error={mutation.error as AppApiError} />}
            {request && <Alert type="warning" showIcon message={t('operationUnknown')} />}
            <Form form={form} layout="vertical" onFinish={({reason}) => {
                const operation = pending.current ?? {userId: user.userId, enabled: targetEnabled,
                    input: {operationId: crypto.randomUUID(), reason: reason.trim()}};
                pending.current = operation; savePendingUserOperation(identity, operation);
                setRequest(operation.input); mutation.mutate(operation);
            }}>
                <Form.Item name="reason" label={t('reason')} rules={[{required: true, whitespace: true, message: t('reasonRequired')}, {max: 500}]}>
                    <Input.TextArea rows={3} maxLength={500} disabled={Boolean(request)} />
                </Form.Item>
                {request && <Typography.Paragraph copyable>{request.operationId}</Typography.Paragraph>}
                <Space>
                    <Button type="primary" danger={!targetEnabled} htmlType="submit" loading={mutation.isPending}>
                        {t(request ? 'retry' : 'confirm')}
                    </Button>
                    <Button disabled={mutation.isPending} onClick={() => setOpen(false)}>{t('cancel')}</Button>
                </Space>
            </Form>
        </Modal>
    </>;
}

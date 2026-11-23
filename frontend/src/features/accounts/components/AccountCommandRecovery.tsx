import {useEffect, useState} from 'react';
import {Alert, App, Button, Descriptions, Space, Typography} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import {formatDateTime} from '@/utils/formatters';
import type {AppApiError} from '@/types/api';
import {
    accountCommandChanged,
    accountCommandsApi,
    hasAccountCommandRecord,
    readPendingAccountCommand,
    type AccountCommandResponse,
} from '../api/commands';

export function usePendingAccountCommand() {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [pending, setPending] = useState(() => readPendingAccountCommand(owner));
    useEffect(() => {
        const update = () => setPending(readPendingAccountCommand(owner));
        update();
        window.addEventListener(accountCommandChanged, update);
        return () => window.removeEventListener(accountCommandChanged, update);
    }, [owner]);
    return pending?.ownerUserId === owner ? pending : null;
}

/** 结果未知时先查原命令；查询不到仍不宣布失败，恢复也沿用原身份和输入。 */
export function AccountCommandRecovery({
    onConfirmed,
}: {
    onConfirmed: (result: AccountCommandResponse) => Promise<void>;
}) {
    const {t} = useTranslation('pages');
    const {modal, message} = App.useApp();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const pending = usePendingAccountCommand();
    const confirmed = async (result: AccountCommandResponse) => {
        if (
            !pending ||
            result.commandId !== pending.commandId ||
            result.type !== pending.request.type
        )
            throw new Error('ACCOUNT_COMMAND_RESPONSE_INVALID');
        accountCommandsApi.confirm(pending);
        message.success(t('pages:accountRecovery.confirmed'));
        await onConfirmed(result);
    };
    const lookup = useMutation({
        mutationFn: (id: string) => accountCommandsApi.result(id),
        onSuccess: confirmed,
    });
    const recover = useMutation({
        mutationFn: accountCommandsApi.recover,
        onSuccess: async (result) => {
            message.success(t('pages:accountRecovery.confirmed'));
            await onConfirmed(result);
        },
    });
    useEffect(() => {
        lookup.reset();
        recover.reset();
    }, [pending?.commandId, lookup.reset, recover.reset]);
    const notRecorded = lookup.isError && (lookup.error as AppApiError).status === 404;
    const busy = lookup.isPending || recover.isPending;
    if (!pending)
        return hasAccountCommandRecord(owner) ? (
            <Alert type="error" showIcon message={t('pages:accountRecovery.unreadable')} />
        ) : null;
    if (pending.phase !== 'UNKNOWN') return null;
    return (
        <Alert
            type="warning"
            showIcon
            message={t('pages:accountRecovery.title')}
            description={
                <Space direction="vertical" size={12} style={{width: '100%'}}>
                    <Typography.Text>{t('pages:accountRecovery.meaning')}</Typography.Text>
                    <Descriptions size="small" column={{xs: 1, md: 2}}>
                        <Descriptions.Item label={t('pages:accountRecovery.operation')}>
                            {t(`pages:accountRecovery.operation_${pending.request.type}`)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountRecovery.account')}>
                            {pending.request.accountAlias ?? pending.request.accountId ?? '—'}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:accountRecovery.identity')}>
                            <Typography.Text copyable>{pending.commandId}</Typography.Text>
                        </Descriptions.Item>
                    </Descriptions>
                    {notRecorded && (
                        <Typography.Text>{t('pages:accountRecovery.notRecorded')}</Typography.Text>
                    )}
                    {lookup.isError && !notRecorded && (
                        <Typography.Text type="danger">
                            {formatApiError(lookup.error as AppApiError)}
                        </Typography.Text>
                    )}
                    {recover.isError && (
                        <Typography.Text type="danger">
                            {formatApiError(recover.error as AppApiError)}
                        </Typography.Text>
                    )}
                    {lookup.data && (
                        <Typography.Text>{formatDateTime(lookup.data.completedAt)}</Typography.Text>
                    )}
                    <Space wrap>
                        <Button
                            loading={lookup.isPending}
                            disabled={busy}
                            onClick={() => lookup.mutate(pending.commandId)}
                        >
                            {t('pages:accountRecovery.lookup')}
                        </Button>
                        <Button
                            disabled={!notRecorded || busy}
                            loading={recover.isPending}
                            onClick={() =>
                                modal.confirm({
                                    title: t('pages:accountRecovery.recoverTitle'),
                                    content: t('pages:accountRecovery.recoverMeaning'),
                                    okText: t('pages:accountRecovery.recover'),
                                    cancelText: t('pages:cancel'),
                                    onOk: () => recover.mutateAsync(pending),
                                })
                            }
                        >
                            {t('pages:accountRecovery.recover')}
                        </Button>
                    </Space>
                </Space>
            }
        />
    );
}

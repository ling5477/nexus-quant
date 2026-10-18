import {useEffect, useState} from 'react';
import {Alert, App, Button, Descriptions, Space, Typography} from 'antd';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router-dom';
import {formatApiError} from '@/api/errors';
import {paperTradingQueryKeys} from '@/api/query-keys';
import {useAuthStore} from '@/store/auth-store';
import {notificationRunPath} from '@/features/notifications/read-state';
import type {AppApiError} from '@/types/api';
import {
    canOperatePaperAlerts,
    paperAlertOperationChanged,
    paperAlertOperations,
    readPaperAlertOperation,
    type PaperAlertReceipt,
} from '../paper-alert-operations';

export function usePendingPaperAlertOperation() {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [value, setValue] = useState(() => ({owner, state: readPaperAlertOperation(owner)}));
    useEffect(() => {
        const refresh = () => setValue({owner, state: readPaperAlertOperation(owner)});
        refresh();
        window.addEventListener(paperAlertOperationChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(paperAlertOperationChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    return value.owner === owner ? value.state : readPaperAlertOperation(owner);
}

export function paperAlertErrorMessage(error: unknown, translate: (key: string) => string) {
    const problem = error as AppApiError;
    const key: Record<string, string> = {
        PAPER_ALERT_SESSION_CHANGED: 'sessionChanged',
        PAPER_ALERT_STORAGE_UNAVAILABLE: 'storageUnavailable',
        PAPER_ALERT_PENDING_CHANGED: 'pendingChanged',
        PAPER_ALERT_PENDING: 'pendingExists',
        PAPER_ALERT_RESPONSE_INVALID: 'responseInvalid',
        PAPER_ALERT_COORDINATION_UNAVAILABLE: 'coordinationUnavailable',
        PAPER_ALERT_BUSY: 'busy',
        PAPER_ALERT_PERMISSION_REQUIRED: 'permission',
        PAPER_ALERT_LOOKUP_REQUIRED: 'lookupRequired',
        PAPER_ALERT_READ_TIMEOUT: 'readTimeout',
        PAPER_ALERT_INPUT_INVALID: 'inputInvalid',
    };
    return key[problem.code]
        ? translate(`paperAlerts.${key[problem.code]}`)
        : formatApiError(problem);
}

/** 原回执只触发当前事实重读，绝不能用历史OPEN或ACKED快照覆盖后来已解决的告警。 */
export function PaperAlertOperationRecovery() {
    const {t} = useTranslation('pages');
    const {modal, message} = App.useApp();
    const queryClient = useQueryClient();
    const stored = usePendingPaperAlertOperation();
    const user = useAuthStore((state) => state.currentUser);
    const token = useAuthStore((state) => state.accessToken);
    const pending = stored.kind === 'pending' ? stored.pending : null;
    const [session, setSession] = useState({owner: user?.userId, token, revision: 0});
    useEffect(() => {
        setSession((previous) =>
            previous.owner === user?.userId && previous.token === token
                ? previous
                : {owner: user?.userId, token, revision: previous.revision + 1},
        );
    }, [user?.userId, token]);
    const onResult = async (_result: PaperAlertReceipt) => {
        await Promise.all([
            queryClient.invalidateQueries({queryKey: paperTradingQueryKeys.all}),
            queryClient.invalidateQueries({queryKey: ['user-notifications']}),
        ]);
        message.success(t('paperAlerts.originalConfirmed'));
    };
    const lookup = useQuery({
        queryKey: [
            'paper-alert-original-result',
            user?.userId,
            pending?.operationId,
            pending?.recordedAt,
            pending ? JSON.stringify(pending.command) : '',
            session.revision,
        ],
        queryFn: async ({signal}) => {
            const result = await paperAlertOperations.lookup(pending!, signal);
            await onResult(result);
            return result;
        },
        enabled: Boolean(
            token &&
                user?.authenticated &&
                session.owner === user?.userId &&
                session.token === token &&
                pending?.phase === 'UNKNOWN',
        ),
        retry: false,
        staleTime: Infinity,
        gcTime: 0,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
    });
    const resume = useMutation({
        mutationFn: paperAlertOperations.resume,
        retry: false,
        onSuccess: onResult,
    });
    useEffect(() => {
        resume.reset();
    }, [user?.userId, token, pending?.operationId, resume.reset]);
    if (stored.kind === 'unavailable')
        return <Alert type="error" showIcon message={t('paperAlerts.storageUnavailable')} />;
    if (!pending) return null;
    const missing = lookup.isError && (lookup.error as AppApiError).status === 404;
    const busy = lookup.isFetching || resume.isPending || pending.phase === 'SUBMITTING';
    return (
        <Alert
            data-testid="paper-alert-operation-recovery"
            type="warning"
            showIcon
            message={t(
                pending.phase === 'SUBMITTING' ? 'paperAlerts.submitting' : 'paperAlerts.unknown',
            )}
            description={
                <Space direction="vertical" size={10} style={{width: '100%'}}>
                    <Typography.Text>{t('paperAlerts.recoveryMeaning')}</Typography.Text>
                    <Descriptions size="small" layout="vertical" column={{xs: 1, md: 2}}>
                        <Descriptions.Item label={t('paperAlerts.action')}>
                            {t(`paperAlerts.${pending.command.type}`)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('paperRunId')}>
                            <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                {pending.paperRunId}
                            </Typography.Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                            label={t(
                                pending.command.type === 'CREATE' ? 'title' : 'paperAlerts.alertId',
                            )}
                        >
                            <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                {pending.command.type === 'CREATE'
                                    ? pending.command.title
                                    : pending.command.alertId}
                            </Typography.Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('paperAlerts.identity')}>
                            <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                                {pending.operationId}
                            </Typography.Text>
                        </Descriptions.Item>
                    </Descriptions>
                    <Link
                        to={notificationRunPath(
                            pending.paperRunId,
                            pending.command.type === 'CREATE' ? undefined : pending.command.alertId,
                        )}
                    >
                        {t('paperAlerts.openSource')}
                    </Link>
                    {lookup.isFetching && (
                        <Typography.Text>{t('paperAlerts.reading')}</Typography.Text>
                    )}
                    {missing && <Typography.Text>{t('paperAlerts.missing')}</Typography.Text>}
                    {lookup.isError && !missing && (
                        <Typography.Text type="danger">
                            {paperAlertErrorMessage(lookup.error, t)}
                        </Typography.Text>
                    )}
                    {resume.isError && (
                        <Typography.Text type="danger">
                            {paperAlertErrorMessage(resume.error, t)}
                        </Typography.Text>
                    )}
                    {!canOperatePaperAlerts() && (
                        <Typography.Text>{t('paperAlerts.permission')}</Typography.Text>
                    )}
                    <Space wrap>
                        <Button
                            loading={lookup.isFetching}
                            disabled={busy || !token || !user?.authenticated}
                            onClick={() => void lookup.refetch()}
                        >
                            {t('paperAlerts.lookup')}
                        </Button>
                        <Button
                            type="primary"
                            loading={resume.isPending}
                            disabled={busy || !missing || !canOperatePaperAlerts()}
                            onClick={() =>
                                modal.confirm({
                                    title: t('paperAlerts.continueTitle'),
                                    content: t('paperAlerts.continueMeaning'),
                                    okText: t('paperAlerts.continue'),
                                    cancelText: t('cancel'),
                                    onOk: async () => {
                                        try {
                                            await resume.mutateAsync(pending);
                                        } catch {
                                            /* 弹窗关闭后保留原编号和错误；不把关闭视为业务成功。 */
                                        }
                                    },
                                })
                            }
                        >
                            {t('paperAlerts.continue')}
                        </Button>
                    </Space>
                </Space>
            }
        />
    );
}

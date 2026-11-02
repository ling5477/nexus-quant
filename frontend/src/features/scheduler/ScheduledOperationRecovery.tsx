import {useEffect, useState} from 'react';
import {Alert, App, Button, Card, Descriptions, Space, Typography} from 'antd';
import {useMutation, useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useSearchParams} from 'react-router-dom';
import {apiClient} from '@/api/client';
import {formatDateTime} from '@/utils/formatters';
import type {ScheduledJobControl} from './types';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {
    hasScheduledOperation,
    readScheduledOperation,
    scheduledOperationChanged,
    scheduledOperations,
    type ScheduledOperationResponse,
} from './operations';

export function usePendingScheduledOperation() {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [value, setValue] = useState(() => readScheduledOperation(owner));
    useEffect(() => {
        const refresh = () => setValue(readScheduledOperation(owner));
        refresh();
        window.addEventListener(scheduledOperationChanged, refresh);
        return () => window.removeEventListener(scheduledOperationChanged, refresh);
    }, [owner]);
    return value?.ownerUserId === owner ? value : null;
}

/** 结果未知时保留原任务与参数；只有未记录或尚未领取时允许用户确认继续原请求。 */
export function ScheduledOperationRecovery({
    jobName,
    onResult,
}: {
    jobName: (key: string) => string;
    onResult: (value: ScheduledOperationResponse) => Promise<void>;
}) {
    const {t} = useTranslation('pages');
    const {modal} = App.useApp();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const canManage = roles.some((role) => ['ADMIN', 'ROLE_ADMIN'].includes(role.toUpperCase()));
    const pending = usePendingScheduledOperation();
    const lookup = useMutation({mutationFn: scheduledOperations.lookup, onSuccess: onResult});
    const resume = useMutation({mutationFn: scheduledOperations.resume, onSuccess: onResult});
    useEffect(() => {
        lookup.reset();
        resume.reset();
    }, [pending?.request.operationId, lookup.reset, resume.reset]);
    if (!pending)
        return hasScheduledOperation(owner) ? (
            <Alert type="error" showIcon message={t('schedulerOperations.unreadable')} />
        ) : null;
    if (pending.phase === 'SUBMITTING') return null;
    const notRecorded = lookup.isError && (lookup.error as AppApiError).status === 404;
    const canResume = notRecorded || lookup.data?.status === 'STARTING';
    const busy = lookup.isPending || resume.isPending;
    return (
        <Alert
            type="warning"
            showIcon
            message={t('schedulerOperations.unconfirmed')}
            description={
                <Space direction="vertical" size={10} style={{width: '100%'}}>
                    <Typography.Text>{t('schedulerOperations.meaning')}</Typography.Text>
                    <Descriptions size="small" column={{xs: 1, md: 2}}>
                        <Descriptions.Item label={t('schedulerOperations.job')}>
                            {jobName(pending.request.jobKey)}
                        </Descriptions.Item>
                        <Descriptions.Item label={t('schedulerOperations.action')}>
                            {t(`schedulerOperations.${pending.request.type}`)}
                        </Descriptions.Item>
                        {pending.request.fixedDelayMs !== undefined && (
                            <Descriptions.Item label={t('schedulerFixedDelay')}>
                                {pending.request.fixedDelayMs.toLocaleString()} ms
                            </Descriptions.Item>
                        )}
                        {pending.request.enabled !== undefined && (
                            <Descriptions.Item label={t('schedulerEnabled')}>
                                {t(
                                    pending.request.enabled
                                        ? 'schedulerOperations.enable'
                                        : 'schedulerOperations.disable',
                                )}
                            </Descriptions.Item>
                        )}
                        <Descriptions.Item label={t('schedulerOperations.identity')}>
                            <Typography.Text copyable>
                                {pending.request.operationId}
                            </Typography.Text>
                        </Descriptions.Item>
                    </Descriptions>
                    {notRecorded && (
                        <Typography.Text>{t('schedulerOperations.notRecorded')}</Typography.Text>
                    )}
                    {lookup.data && (
                        <Typography.Text>
                            {t(`schedulerOperations.status_${lookup.data.status}`)}
                        </Typography.Text>
                    )}
                    {lookup.isError && !notRecorded && (
                        <Typography.Text type="danger">
                            {formatApiError(lookup.error as AppApiError)}
                        </Typography.Text>
                    )}
                    {resume.isError && (
                        <Typography.Text type="danger">
                            {formatApiError(resume.error as AppApiError)}
                        </Typography.Text>
                    )}
                    <Space wrap>
                        <Button
                            loading={lookup.isPending}
                            disabled={busy || !canManage}
                            onClick={() => lookup.mutate(pending)}
                        >
                            {t('schedulerOperations.lookup')}
                        </Button>
                        <Button
                            loading={resume.isPending}
                            disabled={!canResume || busy || !canManage}
                            onClick={() =>
                                modal.confirm({
                                    title: t('schedulerOperations.resumeTitle'),
                                    content: t('schedulerOperations.resumeMeaning'),
                                    okText: t('schedulerOperations.resume'),
                                    cancelText: t('cancel'),
                                    onOk: () => resume.mutateAsync(pending),
                                })
                            }
                        >
                            {t('schedulerOperations.resume')}
                        </Button>
                    </Space>
                </Space>
            }
        />
    );
}

/** 通知深链接只读原回执或任务状态，不向恢复存储写入身份，更不自动继续或重新执行原操作。 */
export function ScheduledNotificationSource({jobName}: {jobName: (key: string) => string}) {
    const {t} = useTranslation();
    const [params, setParams] = useSearchParams();
    const user = useAuthStore((state) => state.currentUser);
    const operationId = params.get('operationId');
    const jobKey = params.get('jobKey');
    const present = operationId !== null || jobKey !== null;
    const valid =
        present &&
        !(operationId !== null && jobKey !== null) &&
        params.getAll('operationId').length <= 1 &&
        params.getAll('jobKey').length <= 1 &&
        (operationId !== null
            ? /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(operationId)
            : /^[A-Z][A-Z0-9_]{1,47}$/.test(jobKey!));
    const allowed = Boolean(
        user?.roles.some((role) => ['ADMIN', 'ROLE_ADMIN'].includes(role.toUpperCase())),
    );
    const query = useQuery({
        queryKey: ['scheduler-notification-source', user?.userId, operationId, jobKey],
        enabled: allowed && valid,
        retry: false,
        queryFn: async () => {
            if (operationId) {
                const {data} = await apiClient.get<ScheduledOperationResponse>(
                    `/scheduler/operations/${operationId}`,
                );
                if (data.operationId !== operationId)
                    throw Error('Scheduler receipt identity mismatch');
                return data;
            }
            const {data} = await apiClient.get<ScheduledJobControl>(
                `/scheduler/jobs/${encodeURIComponent(jobKey!)}`,
            );
            if (data.jobKey !== jobKey) throw Error('Scheduler job identity mismatch');
            return data;
        },
    });
    if (!present) return null;
    const value = allowed && valid && !query.isError ? query.data : undefined;
    const operation = value && 'operationId' in value ? value : null;
    const job = value && !('operationId' in value) ? value : null;
    return (
        <Card
            size="small"
            title={t('notifications.source')}
            extra={
                <Button
                    onClick={() => {
                        const next = new URLSearchParams(params);
                        next.delete('operationId');
                        next.delete('jobKey');
                        setParams(next);
                    }}
                >
                    {t('notifications.sourceClose')}
                </Button>
            }
        >
            <Typography.Paragraph type="secondary">
                {t('notifications.sourceReadonly')}
            </Typography.Paragraph>
            {!allowed ? (
                <Alert type="warning" message={t('notifications.sourceDenied')} />
            ) : !valid ? (
                <Alert type="error" message={t('notifications.invalidSource')} />
            ) : query.isError ? (
                <Alert
                    type="error"
                    message={formatApiError(query.error)}
                    action={<Button onClick={() => void query.refetch()}>{t('retry')}</Button>}
                />
            ) : (
                <Space direction="vertical" style={{width: '100%'}}>
                    <Button loading={query.isFetching} onClick={() => void query.refetch()}>
                        {t('refresh')}
                    </Button>
                    {value && (
                        <Descriptions size="small" column={{xs: 1, sm: 2}}>
                            <Descriptions.Item label={t('pages:schedulerOperations.job')}>
                                {jobName(value.jobKey)}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('notifications.sourceStatus')}>
                                {t(`notifications.status_${operation?.status ?? job?.lastStatus}`, {
                                    defaultValue: operation?.status ?? job?.lastStatus,
                                })}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('pages:schedulerOperations.identity')}>
                                <Typography.Text copyable>
                                    {operation?.operationId ?? job?.jobKey}
                                </Typography.Text>
                            </Descriptions.Item>
                            <Descriptions.Item label={t('notifications.sourceResult')}>
                                {t(`notifications.status_${operation?.result ?? job?.lastStatus}`, {
                                    defaultValue: operation?.result ?? job?.lastStatus ?? '—',
                                })}
                            </Descriptions.Item>
                            <Descriptions.Item label={t('notifications.sourceTime')}>
                                {formatDateTime(
                                    operation?.completedAt ??
                                        operation?.startedAt ??
                                        operation?.createdAt ??
                                        job?.lastFinishedAt,
                                )}
                            </Descriptions.Item>
                        </Descriptions>
                    )}
                </Space>
            )}
        </Card>
    );
}

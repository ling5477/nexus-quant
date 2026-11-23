import {useEffect, useState} from 'react';
import {Alert, App, Button, Descriptions, Space, Typography} from 'antd';
import {useMutation, useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {formatApiError} from '@/api/errors';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import {
    canOperateContinuous,
    continuousOperationChanged,
    continuousOperationErrorKey,
    continuousOperations,
    readContinuousOperation,
    type ConfirmedContinuousOperation,
} from '../continuous-operations';

export function usePendingContinuousOperation() {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [value, setValue] = useState(() => ({owner, state: readContinuousOperation(owner)}));
    useEffect(() => {
        const refresh = () => setValue({owner, state: readContinuousOperation(owner)});
        refresh();
        window.addEventListener(continuousOperationChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(continuousOperationChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    return value.owner === owner ? value.state : readContinuousOperation(owner);
}

/** 重开只发一次原结果GET；404继续未知，只有明确再次确认才允许发送同一编号和原输入。 */
export function ContinuousOperationRecovery({
    canSubmit,
    onResult,
}: {
    canSubmit: boolean | null;
    onResult: (result: ConfirmedContinuousOperation) => void | Promise<void>;
}) {
    const {t} = useTranslation('pages');
    const {modal} = App.useApp();
    const stored = usePendingContinuousOperation();
    const user = useAuthStore((state) => state.currentUser);
    const token = useAuthStore(selectSessionIdentity);
    const pending = stored.kind === 'pending' ? stored.pending : null;
    const allowed = canOperateContinuous();
    const [readSession, setReadSession] = useState({owner: user?.userId, token, revision: 0});
    useEffect(() => {
        setReadSession((previous) =>
            previous.owner === user?.userId && previous.token === token
                ? previous
                : {owner: user?.userId, token, revision: previous.revision + 1},
        );
    }, [user?.userId, token]);
    const currentSession = readSession.owner === user?.userId && readSession.token === token;
    const lookup = useQuery({
        queryKey: [
            'continuous-sim-original-result',
            user?.userId,
            pending?.request.operationId,
            pending ? JSON.stringify(pending.request) : '',
            pending?.recordedAt,
            readSession.revision,
        ],
        queryFn: async ({signal}) => {
            const result = await continuousOperations.lookup(pending!, signal);
            await onResult(result);
            return result;
        },
        enabled: Boolean(
            currentSession && token && user?.authenticated && pending?.phase === 'UNKNOWN',
        ),
        retry: false,
        staleTime: Infinity,
        gcTime: 0,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
    });
    const resume = useMutation({
        mutationFn: continuousOperations.resume,
        retry: false,
        onSuccess: onResult,
    });
    useEffect(() => {
        resume.reset();
    }, [user?.userId, pending?.request.operationId, token, resume.reset]);
    if (stored.kind === 'unavailable')
        return (
            <Alert type="error" showIcon message={t('continuousOperations.storageUnavailable')} />
        );
    if (!pending) return null;
    const missing = lookup.isError && (lookup.error as AppApiError).status === 404;
    const busy = lookup.isFetching || resume.isPending || pending.phase === 'SUBMITTING';
    const describe = (failure: unknown) => {
        const problem = failure as AppApiError;
        const key = continuousOperationErrorKey(problem.code);
        return key ? t(key) : formatApiError(problem);
    };
    return (
        <Alert
            data-testid="continuous-operation-recovery"
            type="warning"
            showIcon
            message={t(
                pending.phase === 'SUBMITTING'
                    ? 'continuousOperations.submitting'
                    : 'continuousOperations.unknown',
            )}
            description={
                <Space direction="vertical" size={10} style={{width: '100%'}}>
                    <Typography.Text>{t('continuousOperations.meaning')}</Typography.Text>
                    <Descriptions size="small" layout="vertical" column={{xs: 1, md: 2}}>
                        <Descriptions.Item label={t('continuousOperations.action')}>
                            {t(`continuousOperations.${pending.request.type}`)}
                        </Descriptions.Item>
                        <Descriptions.Item
                            label={t(
                                pending.request.type === 'CREATE' ? 'publishId' : 'paperRunId',
                            )}
                        >
                            <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                {pending.request.type === 'CREATE'
                                    ? pending.request.publishId
                                    : pending.request.paperRunId}
                            </Typography.Text>
                        </Descriptions.Item>
                        {pending.request.type === 'CREATE' && (
                            <Descriptions.Item label={t('strategySimBudget')}>
                                {pending.request.budget}
                            </Descriptions.Item>
                        )}
                        <Descriptions.Item label={t('continuousOperations.identity')}>
                            <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                                {pending.request.operationId}
                            </Typography.Text>
                        </Descriptions.Item>
                    </Descriptions>
                    {missing && (
                        <Typography.Text>{t('continuousOperations.missing')}</Typography.Text>
                    )}
                    {lookup.isFetching && (
                        <Typography.Text>{t('continuousOperations.reading')}</Typography.Text>
                    )}
                    {lookup.isError && !missing && (
                        <Typography.Text type="danger">{describe(lookup.error)}</Typography.Text>
                    )}
                    {resume.isError && (
                        <Typography.Text type="danger">{describe(resume.error)}</Typography.Text>
                    )}
                    {!allowed && (
                        <Typography.Text>{t('continuousOperations.permission')}</Typography.Text>
                    )}
                    {canSubmit !== true && (
                        <Typography.Text>
                            {t(
                                canSubmit === false
                                    ? 'continuousOperations.executionOff'
                                    : 'continuousOperations.executionUnknown',
                            )}
                        </Typography.Text>
                    )}
                    <Space wrap>
                        <Button
                            loading={lookup.isFetching}
                            disabled={busy || !token || !user?.authenticated}
                            onClick={() => void lookup.refetch()}
                        >
                            {t('continuousOperations.lookup')}
                        </Button>
                        <Button
                            type="primary"
                            loading={resume.isPending}
                            disabled={busy || !missing || !allowed || canSubmit !== true}
                            onClick={() =>
                                modal.confirm({
                                    title: t('continuousOperations.confirmTitle'),
                                    content: t('continuousOperations.confirmMeaning'),
                                    okText: t('continuousOperations.continue'),
                                    cancelText: t('cancel'),
                                    onOk: async () => {
                                        try {
                                            await resume.mutateAsync(pending);
                                        } catch {
                                            /* 关闭确认弹窗后展示保留编号的待确认面板；关闭不代表业务成功。 */
                                        }
                                    },
                                })
                            }
                        >
                            {t('continuousOperations.continue')}
                        </Button>
                    </Space>
                </Space>
            }
        />
    );
}

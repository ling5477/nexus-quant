import {useEffect, useState} from 'react';
import {Alert, App, Button, Descriptions, Space, Typography} from 'antd';
import {useMutation} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuthStore} from '@/store/auth-store';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {
    readShadowCreation,
    shadowCreation,
    shadowCreationChanged,
    shadowCreationErrorKey,
    type ConfirmedShadowCreation,
} from './shadow-creation';

/** 原主体重新登录或刷新后只恢复浏览器记录，不自动发送创建请求。 */
export function usePendingShadowCreation() {
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const [value, setValue] = useState(() => ({owner, state: readShadowCreation(owner)}));
    useEffect(() => {
        const refresh = () => setValue({owner, state: readShadowCreation(owner)});
        refresh();
        window.addEventListener(shadowCreationChanged, refresh);
        window.addEventListener('storage', refresh);
        return () => {
            window.removeEventListener(shadowCreationChanged, refresh);
            window.removeEventListener('storage', refresh);
        };
    }, [owner]);
    return value.owner === owner ? value.state : readShadowCreation(owner);
}

/** GET 的404继续保留未知，用户再次确认才复用原编号和原发布重试；不能生成新命令代替恢复。 */
export function ShadowCreationRecovery({
    onResult,
}: {
    onResult: (value: ConfirmedShadowCreation) => void;
}) {
    const {t} = useTranslation('pages');
    const {modal} = App.useApp();
    const stored = usePendingShadowCreation();
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const allowed = roles.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
    );
    const pending = stored.kind === 'pending' ? stored.pending : null;
    const lookup = useMutation({
        mutationFn: shadowCreation.lookup,
        retry: false,
        onSuccess: onResult,
    });
    const resume = useMutation({
        mutationFn: shadowCreation.resume,
        retry: false,
        onSuccess: onResult,
    });
    useEffect(() => {
        lookup.reset();
        resume.reset();
    }, [owner, pending?.commandIdentity, lookup.reset, resume.reset]);
    if (stored.kind === 'unavailable')
        return <Alert type="error" showIcon message={t('shadowCreation.storageUnavailable')} />;
    if (!pending || pending.phase === 'SUBMITTING') return null;
    const missing = lookup.isError && (lookup.error as AppApiError).status === 404;
    const busy = lookup.isPending || resume.isPending;
    const describeError = (value: unknown) => {
        const failure = value as AppApiError;
        const key = shadowCreationErrorKey(failure.code);
        return key ? t(key) : formatApiError(failure);
    };
    return (
        <Alert
            data-testid="shadow-creation-recovery"
            type="warning"
            showIcon
            message={t('shadowCreation.unknown')}
            description={
                <Space direction="vertical" size={10} style={{width: '100%'}}>
                    <Typography.Text>{t('shadowCreation.meaning')}</Typography.Text>
                    <Descriptions size="small" column={{xs: 1, md: 2}}>
                        <Descriptions.Item label={t('publishId')}>
                            <Typography.Text style={{overflowWrap: 'anywhere'}}>
                                {pending.publishRecordId}
                            </Typography.Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('shadowCreation.identity')}>
                            <Typography.Text copyable style={{overflowWrap: 'anywhere'}}>
                                {pending.commandIdentity}
                            </Typography.Text>
                        </Descriptions.Item>
                    </Descriptions>
                    {missing && <Typography.Text>{t('shadowCreation.missing')}</Typography.Text>}
                    {lookup.isError && !missing && (
                        <Typography.Text type="danger">
                            {describeError(lookup.error)}
                        </Typography.Text>
                    )}
                    {resume.isError && (
                        <Typography.Text type="danger">
                            {describeError(resume.error)}
                        </Typography.Text>
                    )}
                    {!allowed && (
                        <Typography.Text>{t('shadowCreation.permission')}</Typography.Text>
                    )}
                    <Space wrap>
                        <Button
                            loading={lookup.isPending}
                            disabled={busy || !allowed}
                            onClick={() => lookup.mutate(pending)}
                        >
                            {t('shadowCreation.lookup')}
                        </Button>
                        <Button
                            loading={resume.isPending}
                            disabled={!missing || busy || !allowed}
                            onClick={() =>
                                modal.confirm({
                                    title: t('reconfirmTheSameCreationCommand'),
                                    content: t('shadowCreation.resumeMeaning'),
                                    okText: t('confirmRetry'),
                                    cancelText: t('cancel'),
                                    onOk: () => resume.mutateAsync(pending),
                                })
                            }
                        >
                            {t('retryTheSameCreationCommand')}
                        </Button>
                    </Space>
                </Space>
            }
        />
    );
}

import {canWriteResearch} from '@/utils/distribution';
import {useAuthStore} from '@/store/auth-store';
import {Alert, App, Button, Input, Modal, Space, Typography} from 'antd';
import {useTranslation} from 'react-i18next';
import {useRef, useState} from 'react';
import {formatApiError} from '@/api/errors';
import type {AppApiError} from '@/types/api';
import {formatDateTime} from '@/utils/formatters';

interface Props {
    label: string;
    configurationId: string;
    configuration: {
        name: string;
        status?: string;
        archivedAt?: string | null;
        archivedBy?: string | null;
        archiveReason?: string | null;
    };
    pending: boolean;
    onArchive: (reason?: string) => Promise<unknown>;
}
/** 同一确认动作只保留一个在途请求；拒绝后释放门控，允许原输入显式重试。 */
export function createArchiveSubmission() {
    let active: Promise<unknown> | null = null;
    return {
        run(submit: () => Promise<unknown>): Promise<unknown> {
            if (active) return active;
            active = Promise.resolve()
                .then(submit)
                .then(
                    (value) => {
                        active = null;
                        return value;
                    },
                    (error) => {
                        active = null;
                        throw error;
                    },
                );
            return active;
        },
    };
}
/** 受控确认层保留原原因与错误；缓存更新不拥有确认框loading，失败后可取消或重试。 */
export function ConfigurationArchiveAction({
    label,
    configurationId,
    configuration,
    pending,
    onArchive,
}: Props) {
    const {t} = useTranslation('pages');
    const canWrite = useAuthStore(
        (state) =>
            canWriteResearch(state.currentUser?.roles),
    );
    const {message} = App.useApp();
    const [open, setOpen] = useState(false);
    const [reason, setReason] = useState('');
    const [error, setError] = useState<AppApiError | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const target = useRef<string | null>(null);
    const request = useRef(createArchiveSubmission());
    const confirm = () => {
        target.current = configurationId;
        setReason('');
        setError(null);
        setOpen(true);
    };
    const archive = async () => {
        if (target.current !== configurationId || !canWrite || configuration.status !== 'ACTIVE')
            return;
        setSubmitting(true);
        setError(null);
        try {
            await request.current.run(() => onArchive(reason.trim() || undefined));
            message.success(t('configurationArchiveCompleted'));
            setOpen(false);
        } catch (failure) {
            setError(failure as AppApiError);
        } finally {
            setSubmitting(false);
        }
    };
    return (
        <Space direction="vertical" style={{width: '100%'}}>
            <Space wrap>
                <Button
                    danger
                    disabled={
                        !canWrite || configuration.status !== 'ACTIVE' || pending || submitting
                    }
                    loading={pending || submitting}
                    onClick={confirm}
                >
                    {label}
                </Button>
                <Typography.Text type="secondary">
                    {configuration.status === 'ARCHIVED'
                        ? t('configurationArchived')
                        : configuration.status === 'ACTIVE'
                          ? t('configurationAvailable')
                          : t('configurationStatusUnknown')}
                </Typography.Text>
            </Space>
            <Modal
                open={open}
                title={label}
                onCancel={() => {
                    if (!submitting) setOpen(false);
                }}
                onOk={() => void archive()}
                confirmLoading={submitting}
                okText={label}
                cancelText={t('cancel')}
                okButtonProps={{
                    danger: true,
                    disabled: !canWrite || configuration.status !== 'ACTIVE',
                }}
                cancelButtonProps={{disabled: submitting}}
                closable={!submitting}
                keyboard={!submitting}
                maskClosable={false}
            >
                <Space direction="vertical" style={{width: '100%'}}>
                    <Typography.Text strong>{configuration.name}</Typography.Text>
                    <Typography.Text copyable>{configurationId}</Typography.Text>
                    <Typography.Paragraph>{t('configurationArchiveMeaning')}</Typography.Paragraph>
                    {error && <Alert type="error" showIcon message={formatApiError(error)} />}
                    <Input.TextArea
                        aria-label={t('configurationArchiveReason')}
                        placeholder={t('configurationArchiveReason')}
                        maxLength={1024}
                        rows={3}
                        value={reason}
                        disabled={submitting}
                        onChange={(event) => setReason(event.target.value)}
                    />
                </Space>
            </Modal>
            {configuration.status === 'ARCHIVED' && (
                <Alert
                    type="info"
                    showIcon
                    message={t('configurationArchived')}
                    description={
                        <Space direction="vertical">
                            <Typography.Text>
                                {configuration.archivedAt
                                    ? formatDateTime(configuration.archivedAt)
                                    : '—'}{' '}
                                · {configuration.archivedBy ?? '—'}
                            </Typography.Text>
                            <Typography.Text>{configuration.archiveReason || '—'}</Typography.Text>
                            <Typography.Text>{t('configurationArchiveMeaning')}</Typography.Text>
                        </Space>
                    }
                />
            )}
        </Space>
    );
}

import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {App, Alert, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Space, Timeline, Typography} from 'antd';
import {useEffect, useMemo, useState} from 'react';
import {describeApiError, formatApiError} from '@/api/errors';

import {NqStatusTag} from '@/components/nq';
import {
    useValidationReviewDetailQuery,
    useValidationReviewEventsQuery,
    useValidationReviewLifecycleMutation,
} from '@/hooks/useValidationReviewQueries';
import type {AppApiError} from '@/types/api';
import type {
    ValidationReviewAction,
    ValidationReviewLifecycleRequest,
    ValidationReviewState,
} from '@/types/validation-review';
import {formatDateTime} from '@/utils/formatters';

const {Paragraph, Text} = Typography;

const ACTION_LABELS: Record<ValidationReviewAction, string> = {
    get acknowledge() { return t('pages:acknowledgeReview'); },
    get escalate() { return t('pages:escalate'); },
    get resolve() { return t('pages:markResolved'); },
    get close() { return t('pages:closeCase'); },
};

const ACTIONS_BY_STATE: Record<ValidationReviewState, ValidationReviewAction[]> = {
    OPEN: ['acknowledge', 'escalate'],
    ACKNOWLEDGED: ['escalate', 'resolve'],
    ESCALATED: ['resolve'],
    RESOLVED: ['close'],
    CLOSED: [],
};

interface ValidationReviewCaseDrawerProps {
    caseId: string | null;
    onClose: () => void;
}

/** 每次用户确认提交生成一个新 key；mutation 不 retry，因此同一请求过程只使用该稳定值。 */
function createIdempotencyKey(): string {
    if (typeof globalThis.crypto?.randomUUID !== 'function') {
        throw new Error('secure UUID generation is unavailable');
    }
    const key = globalThis.crypto.randomUUID();
    if (!key) {
        throw new Error('secure UUID generation returned an empty key');
    }
    return key;
}

/**
 * Case detail Drawer 展示后端 allowlisted 字段、最多 100 条 events 与真实状态机动作。
 * 不展示 raw metadata、credential、stack trace 或服务端未公开的诊断锚点。
 */
export function ValidationReviewCaseDrawer({caseId, onClose}: ValidationReviewCaseDrawerProps) {
    useTranslation('pages');
    const {message} = App.useApp();
    const [form] = useLocalizedForm<ValidationReviewLifecycleRequest>();
    const [action, setAction] = useState<ValidationReviewAction | null>(null);
    const [actionForbidden, setActionForbidden] = useState(false);
    const [actionNotice, setActionNotice] = useState<{key: string} | {error: AppApiError} | null>(null);
    const detailQuery = useValidationReviewDetailQuery(caseId);
    const eventsQuery = useValidationReviewEventsQuery(caseId);
    const mutation = useValidationReviewLifecycleMutation();

    useEffect(() => {
        setActionForbidden(false);
        setActionNotice(null);
        setAction(null);
        form.resetFields();
    }, [caseId, form]);

    const allowedActions = useMemo(
        () => detailQuery.data ? (ACTIONS_BY_STATE[detailQuery.data.state] ?? []) : [],
        [detailQuery.data],
    );
    const detailError = detailQuery.error as AppApiError | null;
    const eventsError = eventsQuery.error as AppApiError | null;

    function submitAction(values: ValidationReviewLifecycleRequest) {
        if (!caseId || !action || !detailQuery.data) return;
        if (!allowedActions.includes(action) || !Number.isSafeInteger(detailQuery.data.version) || detailQuery.data.version < 0) {
            setActionNotice({key: 'pages:theCaseStateOrVersionCannotBeSubmittedNoRequestWasSentRefreshTheLatestDetails'});
            return;
        }
        let idempotencyKey: string;
        try {
            idempotencyKey = createIdempotencyKey();
        } catch {
            setActionNotice({key: 'pages:unableToGenerateASafeIdempotencyKeyTheRequestWasNotSent'});
            return;
        }
        mutation.mutate({
            caseId,
            action,
            idempotencyKey,
            payload: {expectedVersion: detailQuery.data.version, reason: values.reason.trim()},
        }, {
            onSuccess: () => {
                message.success(t('pages:reviewActionSubmitted', {action: ACTION_LABELS[action]}));
                setAction(null);
                setActionNotice(null);
                form.resetFields();
            },
            onError: (error) => {
                const apiError = error as AppApiError;
                if (apiError.status === 403) setActionForbidden(true);
                setActionNotice({error: apiError});
            },
        });
    }

    return (
        <>
            <Drawer
                open={Boolean(caseId)}
                width={760}
                title={t('pages:validationReviewCase')}
                onClose={onClose}
                destroyOnClose={false}
                data-testid="validation-review-case-drawer"
            >
                <Space direction="vertical" size={16} style={{display: 'flex'}}>
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:diagnosticReviewNoTradingAuthorization')}
                        description={t('pages:lifecycleStatusRecordsLocalManualReviewProgressOnlyItDoesNotStartLiveShadowTradingOrTradingActions')}
                    />
                    {detailError ? (
                        <Alert type="error" showIcon message={describeApiError(detailError).title} description={formatApiError(detailError)}/>
                    ) : null}
                    {detailQuery.isLoading ? <Text type="secondary">{t('pages:loadingCaseDetails')}</Text> : null}
                    {detailQuery.data ? (
                        <Descriptions bordered size="small" column={2}>
                            <Descriptions.Item label={t('pages:caseId')} span={2}><Text code copyable>{detailQuery.data.id}</Text></Descriptions.Item>
                            <Descriptions.Item label={t('pages:state')}><NqStatusTag status={detailQuery.data.state}/></Descriptions.Item>
                            <Descriptions.Item label={t('pages:severity2')}><NqStatusTag status={detailQuery.data.severity}/></Descriptions.Item>
                            <Descriptions.Item label={t('pages:owner')}>{detailQuery.data.ownerId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:version')}>{detailQuery.data.version}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:evidenceType')}>{detailQuery.data.evidenceType}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:evidenceSource')}>{detailQuery.data.evidenceSource}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:title')} span={2}>{detailQuery.data.title}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:summary')} span={2}>{detailQuery.data.summary}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:created')}>{formatDateTime(detailQuery.data.createdAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:updated2')}>{formatDateTime(detailQuery.data.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:retentionUntil')} span={2}>{formatDateTime(detailQuery.data.retentionUntil)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:diagnosticMetadata')} span={2}>
                                {t('pages:theSafeBackendDtoDoesNotExposeTraceSchemaChecksumOrEvidenceAnchorsTheFrontendDoesNotInferOrFabricate')}</Descriptions.Item>
                        </Descriptions>
                    ) : null}

                    {actionNotice ? <Alert type="warning" showIcon message={'error' in actionNotice ? formatApiError(actionNotice.error) : t(actionNotice.key)}/> : null}
                    {detailQuery.data ? (
                        <Space wrap data-testid="validation-review-actions">
                            {allowedActions.map((item) => (
                                <Button
                                    key={item}
                                    danger={item === 'close'}
                                    disabled={actionForbidden || mutation.isPending}
                                    onClick={() => {
                                        setAction(item);
                                        form.setFieldsValue({expectedVersion: detailQuery.data?.version, reason: ''});
                                    }}
                                >
                                    {ACTION_LABELS[item]}
                                </Button>
                            ))}
                            {allowedActions.length === 0 ? <Text type="secondary">{t('pages:noActionsAreAvailableInTheCurrentState')}</Text> : null}
                        </Space>
                    ) : null}

                    <div data-testid="validation-review-event-timeline">
                        <Text strong>{t('pages:lifecycleEventsUpTo100')}</Text>
                        {eventsError ? (
                            <Alert type="error" showIcon message={describeApiError(eventsError).title} description={formatApiError(eventsError)}/>
                        ) : eventsQuery.isLoading ? (
                            <Paragraph type="secondary">{t('pages:loadingEvents')}</Paragraph>
                        ) : (eventsQuery.data ?? []).length === 0 ? (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('pages:noLifecycleEvents')}/>
                        ) : (
                            <Timeline
                                items={(eventsQuery.data ?? []).map((event) => ({
                                    children: (
                                        <Space direction="vertical" size={0}>
                                            <Text strong>{event.eventType}</Text>
                                            <Text>{event.fromState} → {event.toState}</Text>
                                            <Text type="secondary">actor {event.actorId} · version {event.caseVersion} · {formatDateTime(event.createdAt)}</Text>
                                        </Space>
                                    ),
                                }))}
                            />
                        )}
                    </div>
                </Space>
            </Drawer>

            <Modal
                open={Boolean(action)}
                title={action && caseId ? t('pages:confirmReviewAction', {action: ACTION_LABELS[action], caseId}) : t('pages:lifecycleAction')}
                okText={t('pages:confirmAndSubmit')}
                cancelText={t('pages:cancel')}
                confirmLoading={mutation.isPending}
                okButtonProps={{disabled: actionForbidden || mutation.isPending}}
                zIndex={1100}
                onCancel={() => !mutation.isPending && setAction(null)}
                onOk={() => form.submit()}
                destroyOnHidden
                forceRender
            >
                <Alert type="warning" showIcon message={t('pages:thisActionUpdatesLocalReviewStatusOnlyItGrantsNoTradingAuthorization')} style={{marginBottom: 16}}/>
                <Form form={form} layout="vertical" onFinish={submitAction}>
                    <Form.Item name="reason" label={t('pages:reviewReason')} rules={[
                        {required: true, whitespace: true, message: t('pages:enterAReviewReason')},
                        {max: 1000, message: t('pages:theReviewReasonMustNotExceed1000Characters')},
                    ]}>
                        <Input.TextArea rows={4} placeholder={t('pages:enterSanitizedManualReviewNotesOnly')}/>
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
}

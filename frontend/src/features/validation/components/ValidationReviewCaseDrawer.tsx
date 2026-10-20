import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n/index';
import {App, Alert, Button, Descriptions, Drawer, Empty, Form, Input, Modal, Space, Timeline, Typography} from 'antd';
import {useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import {describeApiError, formatApiError} from '@/api/errors';

import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {ResearchResultRecovery, usePendingResultAction} from '@/features/ResearchResultRecovery';
import {readResultAction} from '@/features/research-result-recovery';
import {useAuthStore, selectSessionIdentity} from '@/store/auth-store';
import {
    useValidationReviewDetailQuery,
    useValidationReviewEventsQuery,
    useValidationReviewLifecycleMutation,
} from '@/features/validation/hooks/useValidationReviewQueries';
import type {AppApiError} from '@/types/api';
import type {
    ValidationReviewAction,
    ValidationReviewLifecycleRequest,
    ValidationReviewState,
} from '@/features/validation/types/validation-review';
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
    const currentUser = useAuthStore((state) => state.currentUser);
    const accessToken = useAuthStore(selectSessionIdentity);
    const actionScope = useRef<object | null>(null);
    const canOperate = currentUser?.roles.some((role) =>
        ['ADMIN', 'OPERATOR', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role.toUpperCase()),
    ) ?? false;
    // 切换对象、身份或会话后，旧回调不得覆盖新上下文；卸载也使原回调失效。
    useLayoutEffect(() => {
        const scope = {};
        actionScope.current = scope;
        return () => {
            if (actionScope.current === scope) actionScope.current = null;
        };
    }, [caseId, currentUser?.userId, accessToken, canOperate]);

    const pending = usePendingResultAction('VALIDATION_REVIEW');
    const blocked = pending !== null;
    // 读取失败或对象不匹配时隐藏旧快照，避免旧版本继续授权按钮。
    const details = !detailQuery.isError && detailQuery.data?.id === caseId ? detailQuery.data : null;

    useEffect(() => {
        setActionForbidden(false);
        setActionNotice(null);
        setAction(null);
        form.resetFields();
    }, [caseId, form, currentUser?.userId, accessToken, canOperate]);

    const allowedActions = useMemo(
        () => details ? (ACTIONS_BY_STATE[details.state] ?? []) : [],
        [details],
    );
    const detailError = detailQuery.error as AppApiError | null;
    const eventsError = eventsQuery.error as AppApiError | null;

    function submitAction(values: ValidationReviewLifecycleRequest) {
        if (!caseId || !action || !details || !canOperate || blocked) return;
        if (!allowedActions.includes(action) || !Number.isSafeInteger(details.version) || details.version < 0) {
            setActionNotice({key: 'pages:theCaseStateOrVersionCannotBeSubmittedNoRequestWasSentRefreshTheLatestDetails'});
            return;
        }
        const submittedScope = actionScope.current;
        mutation.mutate({
            caseId,
            action,
            payload: {expectedVersion: details.version, reason: values.reason.trim()},
        }, {
            onSuccess: () => {
                if (!submittedScope || actionScope.current !== submittedScope) return;
                message.success(t('pages:reviewActionSubmitted', {action: ACTION_LABELS[action]}));
                setAction(null);
                setActionNotice(null);
                form.resetFields();
            },
            onError: (error) => {
                if (!submittedScope || actionScope.current !== submittedScope) return;
                const apiError = error as AppApiError;
                if (apiError.status === 403) setActionForbidden(true);
                setActionNotice({error: apiError});
                // 未知结果只保留原请求查询，关闭确认框，不能再次生成身份提交。
                if (readResultAction(currentUser?.userId, 'VALIDATION_REVIEW') !== null) {
                    setAction(null);
                }
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
                    {details ? (
                        <Descriptions bordered size="small" column={{xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2}}>
                            <Descriptions.Item label={t('pages:caseId')} span="filled"><Text copyable><code style={{whiteSpace: 'normal', overflowWrap: 'anywhere'}}>{details.id}</code></Text></Descriptions.Item>
                            <Descriptions.Item label={t('pages:state')}><StatusTag status={details.state} title="" variant="pill"/></Descriptions.Item>
                            <Descriptions.Item label={t('pages:severity2')}><StatusTag status={details.severity} title="" variant="pill"/></Descriptions.Item>
                            <Descriptions.Item label={t('pages:owner')}>{details.ownerId}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:version')}>{details.version}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:evidenceType')}>{details.evidenceType}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:evidenceSource')}>{details.evidenceSource}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:title')} span="filled">{details.title}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:summary')} span="filled">{details.summary}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:created')}>{formatDateTime(details.createdAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:updated2')}>{formatDateTime(details.updatedAt)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:retentionUntil')} span="filled">{formatDateTime(details.retentionUntil)}</Descriptions.Item>
                            <Descriptions.Item label={t('pages:diagnosticMetadata')} span="filled">
                                {t('pages:theSafeBackendDtoDoesNotExposeTraceSchemaChecksumOrEvidenceAnchorsTheFrontendDoesNotInferOrFabricate')}</Descriptions.Item>
                        </Descriptions>
                    ) : null}

                    <ResearchResultRecovery kind="VALIDATION_REVIEW" />
                    {actionNotice ? <Alert type="warning" showIcon message={'error' in actionNotice ? formatApiError(actionNotice.error) : t(actionNotice.key)}/> : null}
                    {details ? (
                        <Space wrap data-testid="validation-review-actions">
                            {allowedActions.map((item) => (
                                <Button
                                    key={item}
                                    danger={item === 'close'}
                                    disabled={
                                        !canOperate || blocked || detailQuery.isFetching ||
                                        actionForbidden || mutation.isPending
                                    }
                                    onClick={() => {
                                        setAction(item);
                                        form.setFieldsValue({expectedVersion: details?.version, reason: ''});
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
                okButtonProps={{
                    disabled: !canOperate || blocked || detailQuery.isFetching ||
                        actionForbidden || mutation.isPending,
                }}
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

import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {PlusOutlined, RedoOutlined, ReloadOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Descriptions, Modal, Skeleton, Space, Typography} from 'antd';
import {useEffect, useState} from 'react';

import {describeApiError, formatApiError} from '@/api/errors';
import {useStrategyReleaseShadowRunMaterialization} from '@/features/validation/hooks/useStrategyReleaseQueries';
import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {
    StrategyReleaseAdmissionPreviewResponse,
    StrategyReleaseShadowRunMaterializationResponse,
} from '@/features/validation/types/strategy-releases';

const {Text} = Typography;

interface AdmissionPreviewQueryState {
    data?: StrategyReleaseAdmissionPreviewResponse;
    isLoading: boolean;
    isFetching: boolean;
    isError: boolean;
    error: unknown;
    refetch: () => unknown;
}

const REASON_TEXT: Record<string, string> = {
    get ELIGIBLE_FOR_CREATION_PLAN_ONLY() { return t('pages:controlledCreationOfAnUnstartedCreatedShadowRunIsAllowed'); },
    get ARTIFACT_LOCATION_UNBOUND() { return t('pages:legacyPublishHasNoServerSideArtifactLocation'); },
    get ARTIFACT_ROOT_NOT_CONFIGURED() { return t('pages:trustedServerArtifactRootIsNotConfigured'); },
    get ARTIFACT_LOCATION_UNSAFE() { return t('pages:serverArtifactLocationFailedSafetyValidation'); },
    get ARTIFACT_MANIFEST_INVALID() { return t('pages:invalidArtifactManifest'); },
    get ARTIFACT_RELEASE_IDENTITY_MISMATCH() { return t('pages:artifactIdentityDoesNotMatchThePublishRecord'); },
    get RELEASE_REJECTED() { return t('pages:strategyReleaseRejected'); },
    get ARTIFACT_NOT_VERIFIED() { return t('pages:artifactNotYetValidated'); },
    get RELEASE_BINDING_REQUIRED() { return t('pages:validatedReleaseBindingMissing'); },
    get VALIDATION_EVIDENCE_MISSING() { return t('pages:validationEvidenceMissing'); },
    get VALIDATION_EVIDENCE_STALE() { return t('pages:validationEvidenceStale'); },
    get VALIDATION_NOT_APPROVED() { return t('pages:validationDecisionNotApproved'); },
    get SHADOW_WINDOW_MISSING() { return t('pages:shadowObservationWindowMissing'); },
    get SHADOW_WINDOW_INVALID() { return t('pages:shadowObservationWindowInvalid'); },
    get AUTHORIZATION_BOUNDARY_MISSING() { return t('pages:readOnlyAuthorizationBoundaryMissing'); },
    get AUTHORIZATION_BOUNDARY_INVALID() { return t('pages:authorizationBoundaryDoesNotPermitPreview'); },
    get SIDE_EFFECT_POLICY_MISSING() { return t('pages:noSideEffectPolicyMissing'); },
    get NO_ORDER_SUBMISSION_REQUIRED() { return t('pages:orderSubmissionMustBeProhibited'); },
    get NO_CREDENTIAL_ACCESS_REQUIRED() { return t('pages:credentialAccessMustBeProhibited'); },
    get NO_PRIVATE_ENDPOINT_REQUIRED() { return t('pages:privateEndpointAccessMustBeProhibited'); },
    get NO_LEDGER_MUTATION_REQUIRED() { return t('pages:ledgerWritesMustBeProhibited'); },
    get NO_ACCOUNT_MUTATION_REQUIRED() { return t('pages:accountWritesMustBeProhibited'); },
    get NO_EXTERNAL_PRIVATE_IO_REQUIRED() { return t('pages:externalPrivateIOMustBeProhibited'); },
};

function statusLabel(status: string | null | undefined): string {
    switch (status?.toUpperCase()) {
        case 'VERIFIED': return t('pages:verified');
        case 'APPROVED': return t('pages:validationPassed');
        case 'ELIGIBLE': return t('pages:shadowAdmissionAllowed');
        case 'BLOCKED': return t('pages:blocked');
        case 'REJECTED': return t('pages:rejected');
        case 'RELEASE_BOUND': return t('pages:releaseBound');
        case 'LEGACY_UNBOUND':
        case 'LEGACY_PUBLISH_ONLY': return t('pages:legacyUnbound');
        case 'NO_EVIDENCE': return t('pages:noValidationEvidence');
        case 'STALE_EVIDENCE': return t('pages:validationEvidenceStale2');
        case 'NEEDS_REVIEW': return t('pages:reviewRequired');
        default: return t('pages:unavailable');
    }
}

function unavailable(value: string | null | undefined): string {
    return value?.trim() || t('pages:notProvided');
}

function createCommandIdentity(): string | null {
    if (typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function') {
        return null;
    }
    return `shadow-materialization-${crypto.randomUUID()}`;
}

type ConfirmationMode = 'new' | 'retry' | null;
type MaterializationNotice = {
    type: 'success' | 'warning' | 'error';
    messageKey: string;
    descriptionKey: string;
    values?: Record<string, string>;
} | {error: AppApiError} | null;

/**
 * 现有 Strategy Validation workspace 内的最小 Shadow admission preview 区块。
 *
 * <p>仅在 ELIGIBLE 且当前用户具备 OPERATOR/ADMIN 时提供 CREATE-only materialization；
 * 不提供启动、执行、重绑、上传或交易动作。
 */
export function StrategyReleaseAdmissionPreviewPanel({
    publishRecordId,
    query,
}: {
    publishRecordId: string | null;
    query: AdmissionPreviewQueryState;
}) {
    useTranslation('pages');
    const preview = query.data;
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const canMaterialize = roles.some((role) => ['OPERATOR', 'ADMIN'].includes(role.toUpperCase()));
    const materialization = useStrategyReleaseShadowRunMaterialization();
    const [confirmationMode, setConfirmationMode] = useState<ConfirmationMode>(null);
    const [activeCommandIdentity, setActiveCommandIdentity] = useState<string | null>(null);
    const [result, setResult] = useState<StrategyReleaseShadowRunMaterializationResponse | null>(null);
    const [notice, setNotice] = useState<MaterializationNotice>(null);
    const noticeView = notice && ('error' in notice ? {
        type: describeApiError(notice.error).catalog.severity,
        message: describeApiError(notice.error).title,
        description: formatApiError(notice.error),
    } : {
        type: notice.type,
        message: t(notice.messageKey),
        description: t(notice.descriptionKey, notice.values ?? {}),
    });
    const legacyUnbound = preview?.bindingMode === 'LEGACY_UNBOUND'
        || preview?.bindingMode === 'LEGACY_PUBLISH_ONLY';
    const eligible = preview?.admissionDecision === 'ELIGIBLE';

    useEffect(() => {
        setConfirmationMode(null);
        setActiveCommandIdentity(null);
        setResult(null);
        setNotice(null);
        materialization.reset();
        // publish 变化代表新的 release command scope；旧 command identity 绝不能跨 publish 复用。
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [publishRecordId]);

    const submitConfirmedCommand = () => {
        if (!publishRecordId || !eligible || !canMaterialize) {
            setConfirmationMode(null);
            return;
        }
        const commandIdentity = confirmationMode === 'retry'
            ? activeCommandIdentity
            : createCommandIdentity();
        if (!commandIdentity) {
            setNotice({
                type: 'error',
                messageKey: 'pages:cannotCreateShadowRun',
                descriptionKey: 'pages:unableToGenerateASafeIdempotencyKeyTheRequestWasNotSent',
            });
            setConfirmationMode(null);
            return;
        }
        setActiveCommandIdentity(commandIdentity);
        setConfirmationMode(null);
        setNotice(null);
        materialization.mutate(
            {publishRecordId, idempotencyKey: commandIdentity},
            {
                onSuccess: (created) => {
                    setResult(created);
                    setNotice({
                        type: 'success',
                        messageKey: created.idempotentReplay ? 'pages:theSameCreationCommandWasSafelyReplayed' : 'pages:shadowRunCreated',
                        descriptionKey: 'pages:createdShadowBoundary',
                        values: {status: created.status},
                    });
                    query.refetch();
                },
                onError: (error) => {
                    const apiError = error as AppApiError;
                    setNotice({error: apiError});
                    if (apiError.code === 'ADMISSION_STALE') {
                        query.refetch();
                    }
                },
            },
        );
    };

    return (
        <Card
            data-testid="strategy-release-admission-preview"
            title={t('pages:shadowAdmissionPreview')}
            extra={publishRecordId ? (
                <Button
                    size="small"
                    icon={<ReloadOutlined/>}
                    loading={query.isFetching}
                    onClick={() => query.refetch()}
                >
                    {t('pages:refreshPreview')}</Button>
            ) : null}
        >
            {!publishRecordId ? (
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:enterAPublishIdAndSearch')}
                    description={t('pages:theServerResolvesReleaseArtifactAndValidationFactsUsingOnlyThePublishId')}
                />
            ) : query.isLoading ? (
                <Skeleton data-testid="strategy-release-admission-loading" active paragraph={{rows: 4}}/>
            ) : query.isError ? (
                <Alert
                    type="error"
                    showIcon
                    message={describeApiError(query.error as AppApiError).title}
                    description={formatApiError(query.error as AppApiError)}
                />
            ) : preview ? (
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type={preview.admissionDecision === 'ELIGIBLE' ? 'success' : 'warning'}
                        showIcon
                        message={legacyUnbound
                            ? t('pages:legacyUnbound')
                            : preview.admissionDecision === 'ELIGIBLE'
                                ? t('pages:anUnstartedShadowRunMayBeCreated')
                                : t('pages:admissionBlocked')}
                        description={preview.admissionDecision === 'ELIGIBLE'
                            ? t('pages:admissionAllowsControlledCreationOfACreatedShadowRunCreationDoesNotStartExecutionPlaceOrdersOrGrantT')
                            : t('pages:reviewBlockersAndProvenanceThisResultTriggersNoCreationStartupExecutionOrTradingAction')}
                    />
                    {noticeView ? (
                        <Alert
                            data-testid="shadow-materialization-notice"
                            type={noticeView.type}
                            showIcon
                            message={noticeView.message}
                            description={noticeView.description}
                        />
                    ) : null}
                    {result ? (
                        <Alert
                            data-testid="shadow-materialization-result"
                            type="info"
                            showIcon
                            message={`Shadow Run：${result.status}`}
                            description={(
                                <Space direction="vertical" size={2}>
                                    <Text code>{result.shadowRunId}</Text>
                                    <Text type="secondary">
                                        {result.idempotentReplay ? t('pages:theSameCommandWasReplayedNoNewCreatedEventWasAdded') : t('pages:releaseBoundFactCreated')}
                                    </Text>
                                </Space>
                            )}
                        />
                    ) : null}
                    <Descriptions size="small" bordered column={{xs: 1, sm: 2, lg: 3}}>
                        <Descriptions.Item label={t('pages:artifactValidation')}>
                            <StatusTag
                                status={preview.artifactVerificationStatus}
                                label={statusLabel(preview.artifactVerificationStatus)}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:releaseBinding')}>
                            <StatusTag
                                status={legacyUnbound ? 'BLOCKED' : preview.bindingMode}
                                label={statusLabel(preview.bindingMode)}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:validationDecision')}>
                            <StatusTag
                                status={preview.validationDecision}
                                label={statusLabel(preview.validationDecision)}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:shadowAdmissionDecision')}>
                            <StatusTag
                                status={preview.admissionDecision}
                                label={statusLabel(preview.admissionDecision)}
                                tone={preview.admissionDecision === 'ELIGIBLE' ? 'success' : undefined}
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:publishId')}>
                            <Text code copyable>{preview.publishRecordId}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:releaseAnchor')}>
                            <Text code copyable>{preview.releaseAnchorId}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:strategyVersion')}>
                            <Text code>{unavailable(preview.strategyVersionId)}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="dataset">
                            <Text code>{unavailable(preview.datasetId)}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label="evaluation">
                            <Text code>{unavailable(preview.evaluationId)}</Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:artifactDigest')} span={3}>
                            <Text code>{unavailable(preview.artifactDigest)}</Text>
                        </Descriptions.Item>
                    </Descriptions>
                    <div>
                        <Text strong>{t('pages:blockersExplanation')}</Text>
                        <Space direction="vertical" size={4} style={{display: 'flex', marginTop: 8}}>
                            {preview.reasonCodes.map((reason) => (
                                <Text key={reason} type={reason === 'ELIGIBLE_FOR_CREATION_PLAN_ONLY' ? 'secondary' : 'danger'}>
                                    {reason}：{REASON_TEXT[reason] ?? t('pages:unknownReasonTreatedAsBlocked')}
                                </Text>
                            ))}
                        </Space>
                    </div>
                    {eligible && canMaterialize ? (
                        <Space wrap>
                            <Button
                                type="primary"
                                icon={<PlusOutlined/>}
                                loading={materialization.isPending}
                                onClick={() => setConfirmationMode('new')}
                            >
                                {activeCommandIdentity ? t('pages:createANewShadowRun') : t('pages:createShadowRun')}
                            </Button>
                            {activeCommandIdentity ? (
                                <Button
                                    icon={<RedoOutlined/>}
                                    disabled={materialization.isPending}
                                    onClick={() => setConfirmationMode('retry')}
                                >
                                    {t('pages:retryTheSameCreationCommand')}</Button>
                            ) : null}
                        </Space>
                    ) : null}
                </Space>
            ) : (
                <Alert type="warning" showIcon message={t('pages:admissionPreviewUnavailable')}/>
            )}
            <Modal
                title={confirmationMode === 'retry' ? t('pages:reconfirmTheSameCreationCommand') : t('pages:confirmShadowRunCreation')}
                open={confirmationMode !== null}
                okText={confirmationMode === 'retry' ? t('pages:confirmRetry') : t('pages:confirmCreation')}
                cancelText={t('pages:cancel')}
                confirmLoading={materialization.isPending}
                onOk={submitConfirmedCommand}
                onCancel={() => setConfirmationMode(null)}
            >
                <Alert
                    type="warning"
                    showIcon
                    message={t('pages:createACreatedShadowRunOnly')}
                    description={t('pages:thisActionDoesNotStartARunnerOrSchedulerPlaceOrdersAccessTradingCredentialsOrGrantTradingAuthorizati')}
                />
            </Modal>
        </Card>
    );
}

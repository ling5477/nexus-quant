import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {PlusOutlined, ReloadOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Descriptions, Modal, Skeleton, Space, Typography} from 'antd';
import {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';

import {describeApiError, formatApiError} from '@/api/errors';
import {useStrategyReleaseShadowRunMaterialization} from '@/features/validation/hooks/useStrategyReleaseQueries';
import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useAuthStore} from '@/store/auth-store';
import type {AppApiError} from '@/types/api';
import type {StrategyReleaseAdmissionPreviewResponse} from '@/features/validation/types/strategy-releases';
import {ShadowCreationRecovery, usePendingShadowCreation} from './ShadowCreationRecovery';
import {shadowCreationErrorKey, type ConfirmedShadowCreation} from './shadow-creation';
import {SHADOW_LIST_PATH} from './shadow-navigation';

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
    get ELIGIBLE_FOR_CREATION_PLAN_ONLY() {
        return t('pages:controlledCreationOfAnUnstartedCreatedShadowRunIsAllowed');
    },
    get ARTIFACT_LOCATION_UNBOUND() {
        return t('pages:legacyPublishHasNoServerSideArtifactLocation');
    },
    get ARTIFACT_ROOT_NOT_CONFIGURED() {
        return t('pages:trustedServerArtifactRootIsNotConfigured');
    },
    get ARTIFACT_LOCATION_UNSAFE() {
        return t('pages:serverArtifactLocationFailedSafetyValidation');
    },
    get ARTIFACT_MANIFEST_INVALID() {
        return t('pages:invalidArtifactManifest');
    },
    get ARTIFACT_RELEASE_IDENTITY_MISMATCH() {
        return t('pages:artifactIdentityDoesNotMatchThePublishRecord');
    },
    get RELEASE_REJECTED() {
        return t('pages:strategyReleaseRejected');
    },
    get ARTIFACT_NOT_VERIFIED() {
        return t('pages:artifactNotYetValidated');
    },
    get RELEASE_BINDING_REQUIRED() {
        return t('pages:validatedReleaseBindingMissing');
    },
    get VALIDATION_EVIDENCE_MISSING() {
        return t('pages:validationEvidenceMissing');
    },
    get VALIDATION_EVIDENCE_STALE() {
        return t('pages:validationEvidenceStale');
    },
    get VALIDATION_NOT_APPROVED() {
        return t('pages:validationDecisionNotApproved');
    },
    get SHADOW_WINDOW_MISSING() {
        return t('pages:shadowObservationWindowMissing');
    },
    get SHADOW_WINDOW_INVALID() {
        return t('pages:shadowObservationWindowInvalid');
    },
    get AUTHORIZATION_BOUNDARY_MISSING() {
        return t('pages:readOnlyAuthorizationBoundaryMissing');
    },
    get AUTHORIZATION_BOUNDARY_INVALID() {
        return t('pages:authorizationBoundaryDoesNotPermitPreview');
    },
    get SIDE_EFFECT_POLICY_MISSING() {
        return t('pages:noSideEffectPolicyMissing');
    },
    get NO_ORDER_SUBMISSION_REQUIRED() {
        return t('pages:orderSubmissionMustBeProhibited');
    },
    get NO_CREDENTIAL_ACCESS_REQUIRED() {
        return t('pages:credentialAccessMustBeProhibited');
    },
    get NO_PRIVATE_ENDPOINT_REQUIRED() {
        return t('pages:privateEndpointAccessMustBeProhibited');
    },
    get NO_LEDGER_MUTATION_REQUIRED() {
        return t('pages:ledgerWritesMustBeProhibited');
    },
    get NO_ACCOUNT_MUTATION_REQUIRED() {
        return t('pages:accountWritesMustBeProhibited');
    },
    get NO_EXTERNAL_PRIVATE_IO_REQUIRED() {
        return t('pages:externalPrivateIOMustBeProhibited');
    },
};

function statusLabel(status: string | null | undefined): string {
    switch (status?.toUpperCase()) {
        case 'VERIFIED':
            return t('pages:verified');
        case 'APPROVED':
            return t('pages:validationPassed');
        case 'ELIGIBLE':
            return t('pages:shadowAdmissionAllowed');
        case 'BLOCKED':
            return t('pages:blocked');
        case 'REJECTED':
            return t('pages:rejected');
        case 'RELEASE_BOUND':
            return t('pages:releaseBound');
        case 'LEGACY_UNBOUND':
        case 'LEGACY_PUBLISH_ONLY':
            return t('pages:legacyUnbound');
        case 'NO_EVIDENCE':
            return t('pages:noValidationEvidence');
        case 'STALE_EVIDENCE':
            return t('pages:validationEvidenceStale2');
        case 'NEEDS_REVIEW':
            return t('pages:reviewRequired');
        default:
            return t('pages:unavailable');
    }
}

function unavailable(value: string | null | undefined): string {
    return value?.trim() || t('pages:notProvided');
}

type ConfirmationMode = 'new' | null;
type MaterializationNotice =
    | {
          type: 'success' | 'warning' | 'error';
          messageKey: string;
          descriptionKey: string;
          values?: Record<string, string>;
      }
    | {error: AppApiError}
    | null;

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
    const owner = useAuthStore((state) => state.currentUser?.userId);
    const pendingState = usePendingShadowCreation();
    const roles = useAuthStore((state) => state.currentUser?.roles ?? []);
    const canMaterialize = roles.some((role) =>
        ['OPERATOR', 'ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR'].includes(role),
    );
    const materialization = useStrategyReleaseShadowRunMaterialization();
    const [confirmationMode, setConfirmationMode] = useState<ConfirmationMode>(null);
    const [result, setResult] = useState<ConfirmedShadowCreation | null>(null);
    const [notice, setNotice] = useState<MaterializationNotice>(null);
    const localErrorKey =
        notice && 'error' in notice ? shadowCreationErrorKey(notice.error.code) : null;
    const noticeView =
        notice &&
        ('error' in notice
            ? localErrorKey
                ? {
                      type: 'warning' as const,
                      message: t('pages:shadowCreation.operationUnavailable'),
                      description: t(`pages:${localErrorKey}`),
                  }
                : {
                      type: describeApiError(notice.error).catalog.severity,
                      message: describeApiError(notice.error).title,
                      description: formatApiError(notice.error),
                  }
            : {
                  type: notice.type,
                  message: t(notice.messageKey),
                  description: t(notice.descriptionKey, notice.values ?? {}),
              });
    const legacyUnbound =
        preview?.bindingMode === 'LEGACY_UNBOUND' || preview?.bindingMode === 'LEGACY_PUBLISH_ONLY';
    const eligible =
        preview?.admissionDecision === 'ELIGIBLE' && preview.publishRecordId === publishRecordId;

    useEffect(() => {
        setConfirmationMode(null);
        setResult(null);
        setNotice(null);
        materialization.reset();
        // 切换发布或主体只清理本页显示，持久原命令仍归原主体所有，不能随页面切换丢弃。
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [publishRecordId, owner]);

    const showConfirmed = (created: ConfirmedShadowCreation) => {
        if (useAuthStore.getState().currentUser?.userId !== owner) return;
        setResult(created);
        setNotice({
            type: 'success',
            messageKey:
                created.source === 'GET'
                    ? 'pages:shadowCreation.confirmedByQuery'
                    : created.idempotentReplay
                      ? 'pages:theSameCreationCommandWasSafelyReplayed'
                      : 'pages:shadowRunCreated',
            descriptionKey: 'pages:shadowCreation.confirmedMeaning',
            values: {status: created.runStatus},
        });
        if (created.publishRecordId === publishRecordId) query.refetch();
    };

    const submitConfirmedCommand = () => {
        if (!publishRecordId || !eligible || !canMaterialize || pendingState.kind !== 'none') {
            setConfirmationMode(null);
            return;
        }
        setConfirmationMode(null);
        setNotice(null);
        materialization.mutate(
            {publishRecordId},
            {
                onSuccess: (created) => {
                    showConfirmed(created);
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
            extra={
                publishRecordId ? (
                    <Button
                        size="small"
                        icon={<ReloadOutlined />}
                        loading={query.isFetching}
                        onClick={() => query.refetch()}
                    >
                        {t('pages:refreshPreview')}
                    </Button>
                ) : null
            }
        >
            <Space direction="vertical" size={12} style={{display: 'flex', marginBottom: 12}}>
                <ShadowCreationRecovery onResult={showConfirmed} />
                {noticeView && (
                    <Alert
                        data-testid="shadow-materialization-notice"
                        type={noticeView.type}
                        showIcon
                        message={noticeView.message}
                        description={noticeView.description}
                    />
                )}
                {result && (
                    <Alert
                        data-testid="shadow-materialization-result"
                        type="info"
                        showIcon
                        message={t('pages:shadowCreation.confirmed')}
                        description={
                            <Space direction="vertical" size={4}>
                                <Text>
                                    {t('pages:shadowCreation.creationState')}：
                                    {result.creationStatus}
                                </Text>
                                <Text>
                                    {t('pages:shadowCreation.currentState')}：{result.runStatus}
                                </Text>
                                {/* 创建和查询结果共用正式路由基址，避免已确认对象进入未注册页面。 */}
                                <Link to={`${SHADOW_LIST_PATH}/${result.shadowRunId}`}>
                                    {result.shadowRunId}
                                </Link>
                                <Text type="secondary">
                                    {result.source === 'GET'
                                        ? t('pages:shadowCreation.queryOnly')
                                        : result.idempotentReplay
                                          ? t(
                                                'pages:theSameCommandWasReplayedNoNewCreatedEventWasAdded',
                                            )
                                          : t('pages:releaseBoundFactCreated')}
                                </Text>
                            </Space>
                        }
                    />
                )}
            </Space>
            {!publishRecordId ? (
                <Alert
                    type="info"
                    showIcon
                    message={t('pages:enterAPublishIdAndSearch')}
                    description={t(
                        'pages:theServerResolvesReleaseArtifactAndValidationFactsUsingOnlyThePublishId',
                    )}
                />
            ) : query.isLoading ? (
                <Skeleton
                    data-testid="strategy-release-admission-loading"
                    active
                    paragraph={{rows: 4}}
                />
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
                        message={
                            legacyUnbound
                                ? t('pages:legacyUnbound')
                                : preview.admissionDecision === 'ELIGIBLE'
                                  ? t('pages:anUnstartedShadowRunMayBeCreated')
                                  : t('pages:admissionBlocked')
                        }
                        description={
                            preview.admissionDecision === 'ELIGIBLE'
                                ? t(
                                      'pages:admissionAllowsControlledCreationOfACreatedShadowRunCreationDoesNotStartExecutionPlaceOrdersOrGrantT',
                                  )
                                : t(
                                      'pages:reviewBlockersAndProvenanceThisResultTriggersNoCreationStartupExecutionOrTradingAction',
                                  )
                        }
                    />
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
                                tone={
                                    preview.admissionDecision === 'ELIGIBLE' ? 'success' : undefined
                                }
                                variant="pill"
                            />
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:publishId')}>
                            <Text code copyable>
                                {preview.publishRecordId}
                            </Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={t('pages:releaseAnchor')}>
                            <Text code copyable>
                                {preview.releaseAnchorId}
                            </Text>
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
                        <Space
                            direction="vertical"
                            size={4}
                            style={{display: 'flex', marginTop: 8}}
                        >
                            {preview.reasonCodes.map((reason) => (
                                <Text
                                    key={reason}
                                    type={
                                        reason === 'ELIGIBLE_FOR_CREATION_PLAN_ONLY'
                                            ? 'secondary'
                                            : 'danger'
                                    }
                                >
                                    {reason}：
                                    {REASON_TEXT[reason] ??
                                        t('pages:unknownReasonTreatedAsBlocked')}
                                </Text>
                            ))}
                        </Space>
                    </div>
                    {eligible && canMaterialize ? (
                        <Space wrap>
                            <Button
                                type="primary"
                                icon={<PlusOutlined />}
                                loading={materialization.isPending}
                                disabled={pendingState.kind !== 'none' || !owner}
                                onClick={() => setConfirmationMode('new')}
                            >
                                {result
                                    ? t('pages:createANewShadowRun')
                                    : t('pages:createShadowRun')}
                            </Button>
                        </Space>
                    ) : null}
                </Space>
            ) : (
                <Alert type="warning" showIcon message={t('pages:admissionPreviewUnavailable')} />
            )}
            <Modal
                title={t('pages:confirmShadowRunCreation')}
                open={confirmationMode !== null}
                okText={t('pages:confirmCreation')}
                cancelText={t('pages:cancel')}
                confirmLoading={materialization.isPending}
                onOk={submitConfirmedCommand}
                onCancel={() => setConfirmationMode(null)}
            >
                <Alert
                    type="warning"
                    showIcon
                    message={t('pages:createACreatedShadowRunOnly')}
                    description={t(
                        'pages:thisActionDoesNotStartARunnerOrSchedulerPlaceOrdersAccessTradingCredentialsOrGrantTradingAuthorizati',
                    )}
                />
            </Modal>
        </Card>
    );
}

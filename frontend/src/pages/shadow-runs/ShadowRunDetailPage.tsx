import {StatusTag, type StatusTone} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {ArrowLeftOutlined, ReloadOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Col, Descriptions, Empty, Row, Space, Table, Tag, Timeline, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useMemo, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';

import {NqEmptyState, NqErrorState, NqLoadingState, NqPageHeader, NqRiskBanner} from '@/components/nq';
import {
    usePaperShadowConsistencyDrilldown,
    useShadowRunDetailQuery,
    useShadowRunEventsQuery,
    useShadowRunLatestConsistencyReportQuery,
    useShadowRunSnapshotsQuery,
} from '@/hooks/useShadowRunQueries';
import type {AppApiError} from '@/types/api';
import type {
    JsonObject,
    JsonValue,
    PaperShadowConsistencyBlocker,
    PaperShadowConsistencyDrilldownResponse,
    PaperShadowConsistencyEvidenceAnchor,
    PaperShadowConsistencyNextStep,
    PaperShadowConsistencyWarning,
    ShadowConsistencyReportResponse,
    ShadowRunDetailResponse,
    ShadowRunEventResponse,
    ShadowRunSnapshotResponse,
} from '@/types/shadow-runs';
import {formatDateTime} from '@/utils/formatters';

const {Text, Paragraph} = Typography;

const SENSITIVE_FIELD_NAME_PATTERN = /^(apiKey|api_key|secret|token|cookie|passphrase|privateKey|credential|credentialMaterial|encrypted_payload|encryptedPayload|decrypted_payload|decryptedPayload|rawSignature|rawRequest|rawResponse|rawHeaders|fullQueryString|privatePayload|privateEndpoint|privateEndpointPayload|rawPrivate|rawPrivateRequest|rawPrivateResponse|realOrderId|realAccountBalance|realPosition|authorizedForTrading|tradingReady|liveReady|tradeApproved|orderExecutionCommand|privateAdapterReference)$/i;
const SENSITIVE_TEXT_PATTERN = /(api[_-]?key|secret|passphrase|private[_ -]?key|credentialMaterial|encrypted[_ -]?payload|decrypted[_ -]?payload|rawSignature|rawPrivate|private endpoint|realOrderId|realAccountBalance|authorizedForTrading|tradingReady|liveReady|tradeApproved)/i;

function asAppApiError(error: unknown): AppApiError | null {
    if (!error || typeof error !== 'object') {
        return null;
    }
    const candidate = error as Partial<AppApiError>;
    return typeof candidate.status === 'number' && typeof candidate.code === 'string'
        ? error as AppApiError
        : null;
}

function isNotFound(error: unknown): boolean {
    return asAppApiError(error)?.status === 404;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function sanitizeJsonValue(value: unknown): JsonValue | undefined {
    if (value === null || value === undefined) {
        return null;
    }
    if (typeof value === 'string') {
        return SENSITIVE_TEXT_PATTERN.test(value) ? '[filtered sensitive value]' : value;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
        return value;
    }
    if (Array.isArray(value)) {
        return value
            .map((item) => sanitizeJsonValue(item))
            .filter((item): item is JsonValue => item !== undefined);
    }
    if (isRecord(value)) {
        return Object.entries(value).reduce<JsonObject>((result, [key, item]) => {
            if (SENSITIVE_FIELD_NAME_PATTERN.test(key)) {
                return result;
            }
            const sanitized = sanitizeJsonValue(item);
            if (sanitized !== undefined) {
                result[key] = sanitized;
            }
            return result;
        }, {});
    }
    return String(value);
}

function safeJsonText(value: unknown): string | null {
    const sanitized = sanitizeJsonValue(value);
    if (sanitized === undefined || sanitized === null) {
        return null;
    }
    const text = typeof sanitized === 'string' ? sanitized : JSON.stringify(sanitized, null, 2);
    if (!text || text === '[]' || text === '{}') {
        return null;
    }
    return text;
}

function OptionalCode({value}: { value: string | number | null | undefined }) {
    useTranslation('pages');
    if (value === null || value === undefined || value === '') {
        return <Text type="secondary">-</Text>;
    }
    const text = String(value);
    return <Text code copyable={{text}}>{text}</Text>;
}

function SafeText({value}: { value: string | null | undefined }) {
    useTranslation('pages');
    if (!value) {
        return <Text type="secondary">-</Text>;
    }
    if (SENSITIVE_TEXT_PATTERN.test(value)) {
        return <Text type="secondary">[filtered sensitive value]</Text>;
    }
    return <Text>{value}</Text>;
}

function SafeJsonBlock({value, emptyText}: { value: unknown; emptyText: string }) {
    useTranslation('pages');
    const pretty = useMemo(() => safeJsonText(value), [value]);

    if (!pretty) {
        return <Text type="secondary">{emptyText}</Text>;
    }

    return (
        <pre
            style={{
                margin: 0,
                maxHeight: 260,
                overflow: 'auto',
                fontFamily: 'var(--nq-font-mono)',
                fontSize: 12,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
            }}
        >
            {pretty}
        </pre>
    );
}

function statusTone(status: string | null | undefined): StatusTone {
    const normalized = status?.toUpperCase() ?? '';
    if (normalized.includes('FAILED') || normalized.includes('BLOCKED') || normalized.includes('REJECTED')) {
        return 'danger';
    }
    if (normalized.includes('WARNING') || normalized.includes('PARTIAL') || normalized.includes('DIVERGED')) {
        return 'warning';
    }
    if (normalized.includes('NOT_') || normalized.includes('UNKNOWN') || normalized.includes('MISSING')) {
        return 'neutral';
    }
    if (normalized.includes('CONSISTENT') || normalized.includes('COMPLETED') || normalized.includes('READY')) {
        return 'success';
    }
    return 'info';
}

function timelineColor(status: string | null | undefined): string {
    const tone = statusTone(status);
    if (tone === 'danger') {
        return 'red';
    }
    if (tone === 'warning') {
        return 'orange';
    }
    if (tone === 'success') {
        return 'green';
    }
    return 'blue';
}

function sortedEvents(events: ShadowRunEventResponse[]): ShadowRunEventResponse[] {
    return [...events].sort((left, right) => left.createdAt.localeCompare(right.createdAt));
}

function snapshotKey(snapshot: ShadowRunSnapshotResponse): string {
    return `${snapshot.snapshotType}:${snapshot.sequenceNo}:${snapshot.checksum}`;
}

function sortedSnapshots(snapshots: ShadowRunSnapshotResponse[]): ShadowRunSnapshotResponse[] {
    return [...snapshots].sort((left, right) => (
        left.snapshotType.localeCompare(right.snapshotType) || left.sequenceNo - right.sequenceNo
    ));
}

type DrilldownBoundaryMessage = PaperShadowConsistencyBlocker | PaperShadowConsistencyWarning;

type DrilldownStateKey = 'no-report' | 'failed' | 'blocked' | 'diverged' | 'stale' | 'normal';

interface DrilldownStateMeta {
    key: DrilldownStateKey;
    label: string;
    tone: StatusTone;
    alertType: 'info' | 'warning' | 'error';
    description: string;
}

function normalizedStatus(value: string | null | undefined): string {
    return value?.toUpperCase() ?? '';
}

function hasAnyWarningCode(
    drilldown: PaperShadowConsistencyDrilldownResponse,
    patterns: RegExp[],
): boolean {
    return drilldown.warnings.some((warning) => patterns.some((pattern) => pattern.test(warning.code)));
}

function isUsableDrilldownResponse(
    value: PaperShadowConsistencyDrilldownResponse | undefined,
): value is PaperShadowConsistencyDrilldownResponse {
    return Boolean(value?.shadowRun?.shadowRunId && value.snapshotSummary && value.eventSummary);
}

function resolveDrilldownState(drilldown: PaperShadowConsistencyDrilldownResponse): DrilldownStateMeta {
    const comparisonStatus = normalizedStatus(drilldown.comparisonStatus);
    const runStatus = normalizedStatus(drilldown.shadowRun.status);
    const divergenceSeverity = normalizedStatus(drilldown.divergenceSeverity);

    if (!drilldown.latestConsistency || comparisonStatus === 'NO_REPORT') {
        return {
            key: 'no-report',
            label: t('pages:noReportNoConsistencyReport'),
            tone: 'neutral',
            alertType: 'warning',
            description: t('pages:thisShadowRunHasNoLatestConsistencyReportAMissingReportDoesNotMeanTheComparisonPassed'),
        };
    }
    if (comparisonStatus === 'FAILED' || runStatus.includes('FAILED')) {
        return {
            key: 'failed',
            label: t('pages:failedLocalEvidenceFailure'),
            tone: 'danger',
            alertType: 'error',
            description: t('pages:localDiagnosticEvidenceOrComparisonFailedReviewTraceidMetricdeltaAndEvidenceAnchors'),
        };
    }
    if (runStatus.includes('BLOCKED')) {
        return {
            key: 'blocked',
            label: t('pages:blockedShadowRunBlocked'),
            tone: 'danger',
            alertType: 'error',
            description: t('pages:theShadowRunIsBlockedThisDoesNotChangeTradingAuthorizationOrAllowExecutionActions'),
        };
    }
    if (comparisonStatus === 'DIVERGED' || divergenceSeverity === 'HIGH' || divergenceSeverity === 'CRITICAL') {
        return {
            key: 'diverged',
            label: t('pages:divergedEvidenceDiffers'),
            tone: 'warning',
            alertType: 'warning',
            description: t('pages:divergenceMeansLocalPaperAndShadowEvidenceDiffersColorsDoNotIndicateProfitLossOrMarketDirection'),
        };
    }
    if (
        comparisonStatus === 'STALE_EVIDENCE'
        || divergenceSeverity === 'UNKNOWN'
        || hasAnyWarningCode(drilldown, [/STALE/i, /INCOMPLETE/i])
    ) {
        return {
            key: 'stale',
            label: t('pages:staleEvidenceIncompleteOrStaleEvidence'),
            tone: 'warning',
            alertType: 'warning',
            description: t('pages:incompleteEvidenceRequiresDiagnosticReviewSnapshotsAndEventsMustNotBeFabricatedAndTheComparisonHasNo'),
        };
    }
    return {
        key: 'normal',
        label: t('pages:normalReadOnlyDiagnosticsAvailable'),
        tone: 'info',
        alertType: 'info',
        description: t('pages:drilldownDataIsReadableComparisonstatusDescribesEvidenceOnlyNotTradingAdmission'),
    };
}

function BoundaryFlag({label, enabled}: { label: string; enabled: boolean }) {
    useTranslation('pages');
    return <Tag color={enabled ? 'success' : 'error'}>{label}: {enabled ? 'true' : 'false'}</Tag>;
}

function BoundarySummary({detail}: { detail?: ShadowRunDetailResponse }) {
    useTranslation('pages');
    const flags = detail?.sideEffectFlags;
    const diagnosticOnly = detail?.authorizationBoundary === 'DIAGNOSTIC_ONLY';

    return (
        <Card className="page-section" variant="borderless">
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="warning"
                    message={t('pages:diagnosticsOnlyNoTradingAuthorization')}
                    description={t('pages:shadowDetailAndReplayShowLocalDiagnosticFactsOnlyAConsistencyReportIsNotApprovalOrLiveReadinessAndDo')}
                />
                <Space size={[8, 8]} wrap>
                    <Tag color="error">{t('pages:liveDisabled3')}</Tag>
                    <Tag color={diagnosticOnly ? 'success' : 'error'}>{t('pages:diagnosticsOnly')}</Tag>
                    <Tag color="default">AI: NOT STARTED</Tag>
                    <Tag color="default">DH runtime: NOT INTEGRATED</Tag>
                    <Tag color="default">RealClient: NOT IMPLEMENTED</Tag>
                    {flags ? (
                        <>
                            <BoundaryFlag label={t('pages:noOrderSubmission')} enabled={flags.noOrderSubmission}/>
                            <BoundaryFlag label={t('pages:noCredentialAccess')} enabled={flags.noCredentialAccess}/>
                            <BoundaryFlag label={t('pages:noPrivateEndpoint')} enabled={flags.noPrivateEndpoint}/>
                            <BoundaryFlag label={t('pages:noLedgerMutation')} enabled={flags.noLedgerMutation}/>
                            <BoundaryFlag label={t('pages:noAccountMutation')} enabled={flags.noAccountMutation}/>
                            <BoundaryFlag label={t('pages:noExternalPrivateIO')} enabled={flags.noExternalPrivateIo}/>
                        </>
                    ) : (
                        <>
                            <Tag color="error">{t('pages:noOrderSubmissionUnavailable')}</Tag>
                            <Tag color="error">{t('pages:noCredentialAccessUnavailable')}</Tag>
                            <Tag color="error">{t('pages:noPrivateEndpointUnavailable')}</Tag>
                            <Tag color="error">{t('pages:noLedgerMutationUnavailable')}</Tag>
                            <Tag color="error">{t('pages:noAccountMutationUnavailable')}</Tag>
                        </>
                    )}
                </Space>
            </Space>
        </Card>
    );
}

function DrilldownBoundaryBadges({drilldown}: { drilldown?: PaperShadowConsistencyDrilldownResponse }) {
    useTranslation('pages');
    return (
        <Space size={[8, 8]} wrap>
            <Tag color={drilldown?.liveDisabled === false ? 'warning' : 'error'}>LIVE DISABLED</Tag>
            <Tag color={drilldown?.realProviderImplemented ? 'warning' : 'default'}>{t('pages:realProviderNotImplemented3')}</Tag>
            <Tag color={drilldown?.privateTradingImplemented ? 'warning' : 'default'}>{t('pages:privateTradingNotImplemented2')}</Tag>
            <Tag color={drilldown?.diagnosticOnly === false ? 'warning' : 'blue'}>{t('pages:shadowRunIsDiagnosticOnly3')}</Tag>
            <Tag color={drilldown?.notTradingAuthorization === false ? 'warning' : 'volcano'}>{t('pages:notTradingAuthorization3')}</Tag>
            <Tag color={drilldown?.aiDhRuntimeIntegrated ? 'warning' : 'default'}>{t('pages:aiDhRuntimeNotIntegrated2')}</Tag>
        </Space>
    );
}

function DrilldownMessageTable({
                                   title,
                                   items,
                                   emptyText,
                               }: {
    title: string;
    items: DrilldownBoundaryMessage[];
    emptyText: string;
}) {
    const {i18n: pageI18n} = useTranslation('pages');
    const columns = useMemo<ColumnsType<DrilldownBoundaryMessage>>(() => [
        {
            title: 'code',
            dataIndex: 'code',
            key: 'code',
            width: 230,
            render: (value: string) => <Text code>{value}</Text>,
        },
        {
            title: 'severity',
            dataIndex: 'severity',
            key: 'severity',
            width: 120,
            render: (value: string) => <StatusTag title="" variant="pill" status={value} tone={statusTone(value)}/>,
        },
        {
            title: 'message',
            dataIndex: 'message',
            key: 'message',
            render: (value: string) => <SafeText value={value}/>,
        },
        {
            title: 'source',
            key: 'source',
            width: 260,
            render: (_, record) => (
                <Space direction="vertical" size={2}>
                    <Text code>{record.sourceType}</Text>
                    <OptionalCode value={record.sourceId}/>
                </Space>
            ),
        },
    ], [pageI18n.resolvedLanguage]);

    return (
        <section aria-label={title}>
            <Typography.Title level={5}>{title}</Typography.Title>
            {items.length === 0 ? (
                <Empty description={emptyText}/>
            ) : (
                <Table<DrilldownBoundaryMessage>
                    size="small"
                    rowKey={(record) => `${record.code}:${record.sourceType}:${record.sourceId ?? ''}`}
                    columns={columns}
                    dataSource={items}
                    pagination={false}
                    scroll={{x: 900}}
                />
            )}
        </section>
    );
}

function DrilldownNextStepsTable({items}: { items: PaperShadowConsistencyNextStep[] }) {
    const {i18n: pageI18n} = useTranslation('pages');
    const columns = useMemo<ColumnsType<PaperShadowConsistencyNextStep>>(() => [
        {
            title: 'code',
            dataIndex: 'code',
            key: 'code',
            width: 280,
            render: (value: string) => <Text code>{value}</Text>,
        },
        {
            title: 'owner',
            dataIndex: 'owner',
            key: 'owner',
            width: 120,
        },
        {
            title: 'action',
            dataIndex: 'action',
            key: 'action',
            render: (value: string) => <SafeText value={value}/>,
        },
        {
            title: 'expectedEvidence',
            dataIndex: 'expectedEvidence',
            key: 'expectedEvidence',
            render: (value: string) => <SafeText value={value}/>,
        },
        {
            title: 'blocking',
            dataIndex: 'blocking',
            key: 'blocking',
            width: 110,
            render: (value: boolean) => <Tag color={value ? 'error' : 'default'}>{value ? 'true' : 'false'}</Tag>,
        },
    ], [pageI18n.resolvedLanguage]);

    return (
        <section aria-label={t('pages:paperShadowDrilldownNextSteps')}>
            <Typography.Title level={5}>{t('pages:nextSteps')}</Typography.Title>
            {items.length === 0 ? (
                <Empty description={t('pages:noNextstepsExecutionActionsMustNotBeInvented')}/>
            ) : (
                <Table<PaperShadowConsistencyNextStep>
                    size="small"
                    rowKey={(record) => record.code}
                    columns={columns}
                    dataSource={items}
                    pagination={false}
                    scroll={{x: 1120}}
                />
            )}
        </section>
    );
}

function DrilldownEvidenceAnchorsTable({items}: { items: PaperShadowConsistencyEvidenceAnchor[] }) {
    const {i18n: pageI18n} = useTranslation('pages');
    const columns = useMemo<ColumnsType<PaperShadowConsistencyEvidenceAnchor>>(() => [
        {
            title: 'sourceType',
            dataIndex: 'sourceType',
            key: 'sourceType',
            width: 210,
            render: (value: string) => <Text code>{value}</Text>,
        },
        {
            title: 'sourceId',
            dataIndex: 'sourceId',
            key: 'sourceId',
            width: 260,
            render: (value: string) => <OptionalCode value={value}/>,
        },
        {
            title: 'sourceVersion',
            dataIndex: 'sourceVersion',
            key: 'sourceVersion',
            width: 180,
            render: (value: string | null) => <OptionalCode value={value}/>,
        },
        {
            title: 'sourceTimestamp',
            dataIndex: 'sourceTimestamp',
            key: 'sourceTimestamp',
            width: 190,
            render: (value: string | null) => formatDateTime(value),
        },
        {
            title: 'checksum',
            dataIndex: 'checksum',
            key: 'checksum',
            width: 220,
            render: (value: string | null) => <OptionalCode value={value}/>,
        },
    ], [pageI18n.resolvedLanguage]);

    return (
        <section aria-label={t('pages:paperShadowDrilldownEvidenceAnchors')}>
            <Typography.Title level={5}>{t('pages:evidenceAnchors2')}</Typography.Title>
            {items.length === 0 ? (
                <Empty description={t('pages:noEvidenceAnchorsAnchorsMustNotBeFabricated')}/>
            ) : (
                <Table<PaperShadowConsistencyEvidenceAnchor>
                    size="small"
                    rowKey={(record) => `${record.sourceType}:${record.sourceId}:${record.checksum ?? ''}`}
                    columns={columns}
                    dataSource={items}
                    pagination={false}
                    scroll={{x: 1120}}
                />
            )}
        </section>
    );
}

export function PaperShadowConsistencyDrilldownPanel({
                                                         drilldown,
                                                         loading,
                                                         error,
                                                         onRetry,
                                                         shadowRunId,
                                                     }: {
    drilldown?: PaperShadowConsistencyDrilldownResponse;
    loading: boolean;
    error: unknown;
    onRetry: () => void;
    shadowRunId: string;
}) {
    useTranslation('pages');
    const usableDrilldown = isUsableDrilldownResponse(drilldown) ? drilldown : undefined;
    const state = usableDrilldown ? resolveDrilldownState(usableDrilldown) : null;

    if (isNotFound(error)) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:paperVersusShadowConsistencyDrilldown')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqEmptyState description={t('pages:drilldownMissingShadowRunNotFoundOrDrilldownUnavailableValue1', {value1: shadowRunId})}/>
                </Space>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:paperVersusShadowConsistencyDrilldown')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqErrorState title={t('pages:failedToLoadConsistencyDrilldown')} error={asAppApiError(error)} onRetry={onRetry}/>
                </Space>
            </Card>
        );
    }
    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:paperVersusShadowConsistencyDrilldown')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqLoadingState message={t('pages:loadingConsistencyDrilldown')}/>
                </Space>
            </Card>
        );
    }
    if (!usableDrilldown || !state) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:paperVersusShadowConsistencyDrilldown')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqEmptyState description={t('pages:noDrilldownResponseThisDoesNotMeanAReportWasGeneratedOrTheComparisonPassed')}/>
                </Space>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title={t('pages:paperVersusShadowConsistencyDrilldown')}>
            <section aria-label={t('pages:paperShadowConsistencyDrilldownPanel')}>
                <Space direction="vertical" size={14} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges drilldown={usableDrilldown}/>
                    <Alert
                        type={state.alertType}
                        showIcon
                        message={state.label}
                        description={t('pages:value1NottradingauthorizationValue2ThisSectionGrantsNoTradingAuthorizationAndEstablishesNoRealTradin', {value1: state.description, value2: String(usableDrilldown.notTradingAuthorization)})}
                    />
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:colorAndStatusMeanings')}
                        description={t('pages:successWarningAndDangerDescribeDiagnosticEvidenceLevelsOnlyNotReturnsMarketDirectionOrTradingAuthori')}
                    />
                    <Descriptions size="small" bordered column={1}>
                        <Descriptions.Item label="comparisonStatus">
                            <StatusTag title="" variant="pill" status={usableDrilldown.comparisonStatus} tone={state.tone}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="divergenceSeverity">
                            <StatusTag title="" variant="pill" status={usableDrilldown.divergenceSeverity}
                                         tone={statusTone(usableDrilldown.divergenceSeverity)}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="generatedAt">{formatDateTime(usableDrilldown.generatedAt)}</Descriptions.Item>
                        <Descriptions.Item label="latestConsistency.generatedAt">
                            {formatDateTime(usableDrilldown.latestConsistency?.generatedAt)}
                        </Descriptions.Item>
                        <Descriptions.Item label="traceId"><OptionalCode value={usableDrilldown.traceId}/></Descriptions.Item>
                        <Descriptions.Item label="shadowRun.status">
                            <StatusTag title="" variant="pill" status={usableDrilldown.shadowRun.status}
                                         tone={statusTone(usableDrilldown.shadowRun.status)}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="shadowRunId">
                            <OptionalCode value={usableDrilldown.shadowRun.shadowRunId}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="paperRunId">
                            <OptionalCode value={usableDrilldown.shadowRun.paperRunId}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="authorizationBoundary">
                            <StatusTag title="" variant="pill" status={usableDrilldown.shadowRun.authorizationBoundary}
                                         tone={statusTone(usableDrilldown.shadowRun.authorizationBoundary)}/>
                        </Descriptions.Item>
                    </Descriptions>

                    <Row gutter={[12, 12]}>
                        <Col xs={24} lg={8}>
                            <Descriptions size="small" bordered column={1} title={t('pages:snapshotSummary')}>
                                <Descriptions.Item label="totalSnapshots">
                                    {usableDrilldown.snapshotSummary.totalSnapshots}
                                </Descriptions.Item>
                                <Descriptions.Item label="inputMarketdata">
                                    {usableDrilldown.snapshotSummary.inputMarketdataSnapshots}
                                </Descriptions.Item>
                                <Descriptions.Item label="strategyDecision">
                                    {usableDrilldown.snapshotSummary.strategyDecisionSnapshots}
                                </Descriptions.Item>
                                <Descriptions.Item label="riskPreflight">
                                    {usableDrilldown.snapshotSummary.riskPreflightSnapshots}
                                </Descriptions.Item>
                                <Descriptions.Item label="orderIntentPreview">
                                    {usableDrilldown.snapshotSummary.orderIntentPreviewSnapshots}
                                </Descriptions.Item>
                                <Descriptions.Item label="latestSnapshotAt">
                                    {formatDateTime(usableDrilldown.snapshotSummary.latestSnapshotAt)}
                                </Descriptions.Item>
                                <Descriptions.Item label="latestSnapshotTypes">
                                    {usableDrilldown.snapshotSummary.latestSnapshotTypes.length > 0
                                        ? usableDrilldown.snapshotSummary.latestSnapshotTypes.join(', ')
                                        : '-'}
                                </Descriptions.Item>
                            </Descriptions>
                        </Col>
                        <Col xs={24} lg={8}>
                            <Descriptions size="small" bordered column={1} title={t('pages:eventSummary')}>
                                <Descriptions.Item label="totalEvents">
                                    {usableDrilldown.eventSummary.totalEvents}
                                </Descriptions.Item>
                                <Descriptions.Item label="latestEventAt">
                                    {formatDateTime(usableDrilldown.eventSummary.latestEventAt)}
                                </Descriptions.Item>
                                <Descriptions.Item label="latestEventType">
                                    <OptionalCode value={usableDrilldown.eventSummary.latestEventType}/>
                                </Descriptions.Item>
                                <Descriptions.Item label="latestReasonCode">
                                    <OptionalCode value={usableDrilldown.eventSummary.latestReasonCode}/>
                                </Descriptions.Item>
                            </Descriptions>
                        </Col>
                        <Col xs={24} lg={8}>
                            <Descriptions size="small" bordered column={1} title={t('pages:latestReport')}>
                                <Descriptions.Item label="reportId">
                                    <OptionalCode value={usableDrilldown.latestConsistency?.reportId}/>
                                </Descriptions.Item>
                                <Descriptions.Item label="comparisonStatus">
                                    <OptionalCode value={usableDrilldown.latestConsistency?.comparisonStatus}/>
                                </Descriptions.Item>
                                <Descriptions.Item label="traceId">
                                    <OptionalCode value={usableDrilldown.latestConsistency?.traceId}/>
                                </Descriptions.Item>
                            </Descriptions>
                        </Col>
                    </Row>

                    <Row gutter={[12, 12]}>
                        <Col xs={24} lg={8}>
                            <section aria-label={t('pages:paperShadowDrilldownMetricdelta')}>
                                <Typography.Title level={5}>metricDelta</Typography.Title>
                                <SafeJsonBlock value={usableDrilldown.metricDelta} emptyText={t('pages:metricdeltaIsEmpty')}/>
                            </section>
                        </Col>
                        <Col xs={24} lg={8}>
                            <section aria-label={t('pages:paperShadowDrilldownDivergenceReasons')}>
                                <Typography.Title level={5}>divergenceReasons</Typography.Title>
                                <SafeJsonBlock value={usableDrilldown.divergenceReasons}
                                               emptyText={t('pages:divergencereasonsIsEmpty')}/>
                            </section>
                        </Col>
                        <Col xs={24} lg={8}>
                            <section aria-label={t('pages:paperShadowDrilldownLimitations')}>
                                <Typography.Title level={5}>limitations</Typography.Title>
                                <SafeJsonBlock value={usableDrilldown.limitations} emptyText={t('pages:limitationsIsEmpty')}/>
                            </section>
                        </Col>
                    </Row>

                    <DrilldownMessageTable
                        title={t('pages:blockers2')}
                        items={usableDrilldown.blockers}
                        emptyText={t('pages:noBlockersThisStillDoesNotAuthorizeTrading')}
                    />
                    <DrilldownMessageTable
                        title={t('pages:warnings3')}
                        items={usableDrilldown.warnings}
                        emptyText={t('pages:noWarnings')}
                    />
                    <DrilldownNextStepsTable items={usableDrilldown.nextSteps}/>
                    <DrilldownEvidenceAnchorsTable items={usableDrilldown.evidenceAnchors}/>
                </Space>
            </section>
        </Card>
    );
}

function ShadowRunDetailPanel({detail}: { detail: ShadowRunDetailResponse }) {
    useTranslation('pages');
    return (
        <Card className="page-section" variant="borderless" title={t('pages:shadowRunInformation')}>
            <Space direction="vertical" size={14} style={{display: 'flex'}}>
                <Descriptions size="small" bordered column={1}>
                    <Descriptions.Item label="shadowRunId"><OptionalCode
                        value={detail.id}/></Descriptions.Item>
                    <Descriptions.Item label="status">
                        <StatusTag title="" variant="pill" status={detail.status} tone={statusTone(detail.status)}/>
                    </Descriptions.Item>
                    <Descriptions.Item label="authorizationBoundary">
                        <StatusTag title="" variant="pill" status={detail.authorizationBoundary}
                                     tone={statusTone(detail.authorizationBoundary)}/>
                    </Descriptions.Item>
                    <Descriptions.Item label="strategyVersionId"><OptionalCode
                        value={detail.strategyVersionId}/></Descriptions.Item>
                    <Descriptions.Item label="datasetId"><OptionalCode value={detail.datasetId}/></Descriptions.Item>
                    <Descriptions.Item label="evaluationId"><OptionalCode
                        value={detail.evaluationId}/></Descriptions.Item>
                    <Descriptions.Item label="publishId"><OptionalCode value={detail.publishId}/></Descriptions.Item>
                    <Descriptions.Item label="paperRunId"><OptionalCode value={detail.paperRunId}/></Descriptions.Item>
                    <Descriptions.Item label="traceId"><OptionalCode value={detail.traceId}/></Descriptions.Item>
                    <Descriptions.Item label="requestId"><OptionalCode value={detail.requestId}/></Descriptions.Item>
                    <Descriptions.Item label="window">
                        {formatDateTime(detail.windowStart)} ~ {formatDateTime(detail.windowEnd)}
                    </Descriptions.Item>
                    <Descriptions.Item label="createdAt">{formatDateTime(detail.createdAt)}</Descriptions.Item>
                    <Descriptions.Item label="updatedAt">{formatDateTime(detail.updatedAt)}</Descriptions.Item>
                    <Descriptions.Item label="startedAt">{formatDateTime(detail.startedAt)}</Descriptions.Item>
                    <Descriptions.Item label="completedAt">{formatDateTime(detail.completedAt)}</Descriptions.Item>
                    <Descriptions.Item label="stoppedAt">{formatDateTime(detail.stoppedAt)}</Descriptions.Item>
                </Descriptions>

                <Row gutter={[12, 12]}>
                    <Col xs={24} lg={8}>
                        <section aria-label={t('pages:shadowRunBlockers')}>
                            <Typography.Title level={5}>{t('pages:blockers2')}</Typography.Title>
                            <SafeJsonBlock value={detail.blockers} emptyText={t('pages:noBlockersThisDoesNotAuthorizeTrading')}/>
                        </section>
                    </Col>
                    <Col xs={24} lg={8}>
                        <section aria-label={t('pages:shadowRunWarnings')}>
                            <Typography.Title level={5}>{t('pages:warnings3')}</Typography.Title>
                            <SafeJsonBlock value={detail.warnings} emptyText={t('pages:noWarnings')}/>
                        </section>
                    </Col>
                    <Col xs={24} lg={8}>
                        <section aria-label={t('pages:shadowRunNextSteps')}>
                            <Typography.Title level={5}>{t('pages:nextSteps')}</Typography.Title>
                            <SafeJsonBlock value={detail.nextSteps} emptyText={t('pages:noNextsteps')}/>
                        </section>
                    </Col>
                </Row>
            </Space>
        </Card>
    );
}

export function ShadowRunEventTimeline({
                                           events,
                                           loading,
                                           error,
                                           onRetry,
                                       }: {
    events: ShadowRunEventResponse[];
    loading: boolean;
    error: unknown;
    onRetry: () => void;
}) {
    useTranslation('pages');
    const orderedEvents = useMemo(() => sortedEvents(events), [events]);

    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:eventTimeline')}>
                <NqLoadingState message={t('pages:loadingEventTimeline')}/>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:eventTimeline')}>
                <NqErrorState title={t('pages:failedToLoadEventTimeline')} error={asAppApiError(error)} onRetry={onRetry}/>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title={t('pages:eventTimeline')}>
            <section aria-label={t('pages:shadowRunEventTimeline')}>
                {orderedEvents.length === 0 ? (
                    <Empty description={t('pages:noEventsLifecycleEventsMustNotBeFabricated')}/>
                ) : (
                    <Timeline
                        items={orderedEvents.map((event) => ({
                            color: timelineColor(event.eventType),
                            children: (
                                <Space direction="vertical" size={4}>
                                    <Space size={8} wrap>
                                        <StatusTag title="" variant="pill" status={event.eventType} tone={statusTone(event.eventType)}/>
                                        <Text type="secondary">{formatDateTime(event.createdAt)}</Text>
                                        <Text code>{event.reasonCode ?? '-'}</Text>
                                    </Space>
                                    <Paragraph style={{marginBottom: 0}}>
                                        {event.message ?? t('pages:noEventDescription')}
                                    </Paragraph>
                                    <Text type="secondary">
                                        {event.fromStatus ?? '-'} -&gt; {event.toStatus ?? '-'} ·
                                        traceId {event.traceId ?? '-'}
                                    </Text>
                                    <SafeJsonBlock value={event.metadata} emptyText={t('pages:metadataIsEmpty')}/>
                                </Space>
                            ),
                        }))}
                    />
                )}
            </section>
        </Card>
    );
}

export function ShadowRunSnapshotPanel({
                                           snapshots,
                                           loading,
                                           error,
                                           onRetry,
                                       }: {
    snapshots: ShadowRunSnapshotResponse[];
    loading: boolean;
    error: unknown;
    onRetry: () => void;
}) {
    const {i18n: pageI18n} = useTranslation('pages');
    const orderedSnapshots = useMemo(() => sortedSnapshots(snapshots), [snapshots]);
    const [selectedSnapshotKey, setSelectedSnapshotKey] = useState<string | null>(null);
    const selectedSnapshot = orderedSnapshots.find((snapshot) => snapshotKey(snapshot) === selectedSnapshotKey)
        ?? orderedSnapshots[0]
        ?? null;

    useEffect(() => {
        if (orderedSnapshots.length === 0) {
            setSelectedSnapshotKey(null);
            return;
        }
        if (!selectedSnapshotKey || !orderedSnapshots.some((snapshot) => snapshotKey(snapshot) === selectedSnapshotKey)) {
            setSelectedSnapshotKey(snapshotKey(orderedSnapshots[0]));
        }
    }, [orderedSnapshots, selectedSnapshotKey]);

    const columns = useMemo<ColumnsType<ShadowRunSnapshotResponse>>(() => [
        {
            title: 'snapshotType',
            dataIndex: 'snapshotType',
            key: 'snapshotType',
            width: 220,
            render: (value: string) => <StatusTag title="" variant="pill" status={value} tone={statusTone(value)}/>,
        },
        {
            title: 'sequenceNo',
            dataIndex: 'sequenceNo',
            key: 'sequenceNo',
            width: 120,
            sorter: (left, right) => left.sequenceNo - right.sequenceNo,
        },
        {
            title: 'schemaVersion',
            dataIndex: 'schemaVersion',
            key: 'schemaVersion',
            width: 260,
            render: (value: string) => <Text code>{value}</Text>,
        },
        {
            title: 'checksum',
            dataIndex: 'checksum',
            key: 'checksum',
            width: 220,
            render: (value: string) => <Text code>{value}</Text>,
        },
        {
            title: 'capturedAt',
            dataIndex: 'capturedAt',
            key: 'capturedAt',
            width: 190,
            render: (value: string) => formatDateTime(value),
        },
    ], [pageI18n.resolvedLanguage]);

    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:snapshotListAndDetails')}>
                <NqLoadingState message={t('pages:loadingSnapshots')}/>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:snapshotListAndDetails')}>
                <NqErrorState title={t('pages:failedToLoadSnapshots')} error={asAppApiError(error)} onRetry={onRetry}/>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title={t('pages:snapshotListAndDetails')}>
            <section aria-label={t('pages:shadowRunSnapshotPanel')}>
                {orderedSnapshots.length === 0 ? (
                    <Empty description={t('pages:noSnapshotsReplayEvidenceMustNotBeFabricated')}/>
                ) : (
                    <Space direction="vertical" size={12} style={{display: 'flex'}}>
                        <Alert
                            type="info"
                            showIcon
                            message={t('pages:snapshotsAreOrderedBySnapshottypeAndSequenceno')}
                            description={t('pages:payloadsAreRenderedSafelySensitiveKeysAndValuesAreFilteredByTheFrontend')}
                        />
                        <Table<ShadowRunSnapshotResponse>
                            size="small"
                            rowKey={snapshotKey}
                            columns={columns}
                            dataSource={orderedSnapshots}
                            pagination={false}
                            scroll={{x: 1020}}
                            onRow={(record) => ({
                                onClick: () => setSelectedSnapshotKey(snapshotKey(record)),
                            })}
                        />
                        {selectedSnapshot ? (
                            <Descriptions size="small" bordered column={1}>
                                <Descriptions.Item label="snapshotType">
                                    <StatusTag title="" variant="pill" status={selectedSnapshot.snapshotType}
                                                 tone={statusTone(selectedSnapshot.snapshotType)}/>
                                </Descriptions.Item>
                                <Descriptions.Item label="sequenceNo">{selectedSnapshot.sequenceNo}</Descriptions.Item>
                                <Descriptions.Item label="source"><OptionalCode
                                    value={selectedSnapshot.source}/></Descriptions.Item>
                                <Descriptions.Item label="traceId"><OptionalCode
                                    value={selectedSnapshot.traceId}/></Descriptions.Item>
                                <Descriptions.Item label="payload">
                                    <SafeJsonBlock value={selectedSnapshot.payload} emptyText={t('pages:payloadIsEmpty')}/>
                                </Descriptions.Item>
                            </Descriptions>
                        ) : null}
                    </Space>
                )}
            </section>
        </Card>
    );
}

export function ShadowConsistencyReportPanel({
                                                 report,
                                                 loading,
                                                 error,
                                                 onRetry,
                                             }: {
    report?: ShadowConsistencyReportResponse;
    loading: boolean;
    error: unknown;
    onRetry: () => void;
}) {
    useTranslation('pages');
    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:latestConsistencyReport')}>
                <NqLoadingState message={t('pages:loadingConsistencyReport')}/>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:latestConsistencyReport')}>
                {isNotFound(error) ? (
                    <NqEmptyState
                        description={t('pages:theLatestConsistencyReportHasNotBeenGenerated')}/>
                ) : (
                    <NqErrorState title={t('pages:failedToLoadTheConsistencyReport')} error={asAppApiError(error)} onRetry={onRetry}/>
                )}
            </Card>
        );
    }
    if (!report) {
        return (
            <Card className="page-section" variant="borderless" title={t('pages:latestConsistencyReport')}>
                <NqEmptyState description={t('pages:noLatestConsistencyReportThisDoesNotMeanTheComparisonPassed')}/>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title={t('pages:latestConsistencyReport')}>
            <section aria-label={t('pages:shadowConsistencyReportPanel')}>
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:comparisonstatusIsADiagnosticResultOnly')}
                        description={t('pages:aConsistencyReportIsNotApprovalTradingAuthorizationOrLiveReadiness')}
                    />
                    <Descriptions size="small" bordered column={1}>
                        <Descriptions.Item label="reportId"><OptionalCode value={report.id}/></Descriptions.Item>
                        <Descriptions.Item label="shadowRunId"><OptionalCode
                            value={report.shadowRunId}/></Descriptions.Item>
                        <Descriptions.Item label="paperRunId"><OptionalCode
                            value={report.paperRunId}/></Descriptions.Item>
                        <Descriptions.Item label="comparisonStatus">
                            <StatusTag title="" variant="pill" status={report.comparisonStatus} tone={statusTone(report.comparisonStatus)}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="generatedAt">{formatDateTime(report.generatedAt)}</Descriptions.Item>
                        <Descriptions.Item label="traceId"><OptionalCode value={report.traceId}/></Descriptions.Item>
                        <Descriptions.Item label="metricDelta">
                            <SafeJsonBlock value={report.metricDelta} emptyText={t('pages:metricdeltaIsEmpty')}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="divergenceReasons">
                            <SafeJsonBlock value={report.divergenceReasons} emptyText={t('pages:divergencereasonsIsEmpty')}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="limitations">
                            <SafeJsonBlock value={report.limitations} emptyText={t('pages:limitationsIsEmpty')}/>
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </section>
        </Card>
    );
}

export function ShadowRunDetailPage() {
    useTranslation('pages');
    const navigate = useNavigate();
    const {shadowRunId} = useParams<{ shadowRunId: string }>();
    const normalizedShadowRunId = shadowRunId?.trim() || null;

    const detailQuery = useShadowRunDetailQuery(normalizedShadowRunId);
    const factsEnabled = Boolean(normalizedShadowRunId) && detailQuery.isSuccess;
    const eventsQuery = useShadowRunEventsQuery(normalizedShadowRunId, factsEnabled);
    const snapshotsQuery = useShadowRunSnapshotsQuery(normalizedShadowRunId, factsEnabled);
    const reportQuery = useShadowRunLatestConsistencyReportQuery(normalizedShadowRunId, factsEnabled);
    const drilldownQuery = usePaperShadowConsistencyDrilldown(normalizedShadowRunId);
    const fetching = detailQuery.isFetching
        || eventsQuery.isFetching
        || snapshotsQuery.isFetching
        || reportQuery.isFetching
        || drilldownQuery.isFetching;

    const refreshAll = () => {
        void detailQuery.refetch();
        void drilldownQuery.refetch();
        if (factsEnabled) {
            void eventsQuery.refetch();
            void snapshotsQuery.refetch();
            void reportQuery.refetch();
        }
    };

    if (!normalizedShadowRunId) {
        return <Alert type="error" showIcon message={t('pages:theShadowrunidRouteParameterIsMissing')}/>;
    }

    return (
        <Space data-testid="shadow-run-detail-page" direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" variant="borderless">
                <NqPageHeader
                    title={t('pages:shadowRunDetailsReplay')}
                    description={t('pages:readOnlyShadowRunInformationPaperVersusShadowConsistencyDrilldownEventTimelineSnapshotsAndLatestCons')}
                    badge={t('pages:shadowRunDetailsReadOnlyDiagnostics')}
                    extra={(
                        <Space size={8} wrap>
                            <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate('/strategies/shadow-runs')}>
                                {t('pages:backToShadowRuns')}</Button>
                            <Button icon={<ReloadOutlined/>} onClick={refreshAll} loading={fetching}>
                                {t('pages:refreshReadOnlyData')}</Button>
                        </Space>
                    )}
                />
            </Card>

            <BoundarySummary detail={detailQuery.data}/>

            {detailQuery.isLoading ? (
                <Card className="page-section" variant="borderless">
                    <NqLoadingState message={t('pages:loadingShadowRunDetails')}/>
                </Card>
            ) : detailQuery.isError ? (
                <Card className="page-section" variant="borderless">
                    {isNotFound(detailQuery.error) ? (
                        <NqEmptyState
                            description={t('pages:shadowRunNotFoundValue1', {value1: normalizedShadowRunId})}/>
                    ) : (
                        <NqErrorState
                            title={t('pages:failedToLoadShadowRunDetails')}
                            error={asAppApiError(detailQuery.error)}
                            onRetry={() => detailQuery.refetch()}
                        />
                    )}
                </Card>
            ) : detailQuery.data ? (
                <ShadowRunDetailPanel detail={detailQuery.data}/>
            ) : (
                <Card className="page-section" variant="borderless">
                    <NqEmptyState description={t('pages:noShadowRunDetails')}/>
                </Card>
            )}

            <PaperShadowConsistencyDrilldownPanel
                shadowRunId={normalizedShadowRunId}
                drilldown={drilldownQuery.data}
                loading={drilldownQuery.isLoading}
                error={drilldownQuery.error}
                onRetry={() => drilldownQuery.refetch()}
            />

            {detailQuery.data ? (
                <>
                    <ShadowRunEventTimeline
                        events={eventsQuery.data ?? []}
                        loading={eventsQuery.isLoading}
                        error={eventsQuery.error}
                        onRetry={() => eventsQuery.refetch()}
                    />
                    <ShadowRunSnapshotPanel
                        snapshots={snapshotsQuery.data ?? []}
                        loading={snapshotsQuery.isLoading}
                        error={snapshotsQuery.error}
                        onRetry={() => snapshotsQuery.refetch()}
                    />
                    <ShadowConsistencyReportPanel
                        report={reportQuery.data}
                        loading={reportQuery.isLoading}
                        error={reportQuery.error}
                        onRetry={() => reportQuery.refetch()}
                    />
                </>
            ) : null}
        </Space>
    );
}

import {ArrowLeftOutlined, ReloadOutlined} from '@ant-design/icons';
import {Alert, Button, Card, Col, Descriptions, Empty, Row, Space, Table, Tag, Timeline, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useEffect, useMemo, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';

import {
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    NqPageHeader,
    NqRiskBanner,
    NqStatusTag,
    type NqStatusTone,
} from '@/components/nq';
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
    if (value === null || value === undefined || value === '') {
        return <Text type="secondary">-</Text>;
    }
    const text = String(value);
    return <Text code copyable={{text}}>{text}</Text>;
}

function SafeText({value}: { value: string | null | undefined }) {
    if (!value) {
        return <Text type="secondary">-</Text>;
    }
    if (SENSITIVE_TEXT_PATTERN.test(value)) {
        return <Text type="secondary">[filtered sensitive value]</Text>;
    }
    return <Text>{value}</Text>;
}

function SafeJsonBlock({value, emptyText}: { value: unknown; emptyText: string }) {
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

function statusTone(status: string | null | undefined): NqStatusTone {
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
    tone: NqStatusTone;
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
            label: 'NO_REPORT（无一致性报告）',
            tone: 'neutral',
            alertType: 'warning',
            description: '当前 Shadow Run 尚无 latest consistency report；不能把缺失报告解释为 comparison 通过。',
        };
    }
    if (comparisonStatus === 'FAILED' || runStatus.includes('FAILED')) {
        return {
            key: 'failed',
            label: 'FAILED（本地证据失败）',
            tone: 'danger',
            alertType: 'error',
            description: '失败只表示本地诊断证据或 comparison 失败，需要继续复核 traceId、metricDelta 与 evidence anchors。',
        };
    }
    if (runStatus.includes('BLOCKED')) {
        return {
            key: 'blocked',
            label: 'BLOCKED（Shadow Run 阻断）',
            tone: 'danger',
            alertType: 'error',
            description: 'Shadow Run 当前处于阻断状态；该状态不表达交易授权变化，也不允许触发执行动作。',
        };
    }
    if (comparisonStatus === 'DIVERGED' || divergenceSeverity === 'HIGH' || divergenceSeverity === 'CRITICAL') {
        return {
            key: 'diverged',
            label: 'DIVERGED（证据存在偏离）',
            tone: 'warning',
            alertType: 'warning',
            description: '偏离只表达 Paper 与 Shadow 本地证据不一致；颜色不表示盈利、亏损、上涨或下跌。',
        };
    }
    if (
        comparisonStatus === 'STALE_EVIDENCE'
        || divergenceSeverity === 'UNKNOWN'
        || hasAnyWarningCode(drilldown, [/STALE/i, /INCOMPLETE/i])
    ) {
        return {
            key: 'stale',
            label: 'STALE_EVIDENCE（证据不完整或过期）',
            tone: 'warning',
            alertType: 'warning',
            description: '证据不完整时只能进入诊断复核，不能补造 snapshot/event，也不能视为 comparison 已通过。',
        };
    }
    return {
        key: 'normal',
        label: 'NORMAL（只读诊断可查看）',
        tone: 'info',
        alertType: 'info',
        description: '当前仅表示 drilldown 数据可读；comparisonStatus 仍只表达证据状态，不表达交易准入。',
    };
}

function BoundaryFlag({label, enabled}: { label: string; enabled: boolean }) {
    return <Tag color={enabled ? 'success' : 'error'}>{label}: {enabled ? 'true' : 'false'}</Tag>;
}

function BoundarySummary({detail}: { detail?: ShadowRunDetailResponse }) {
    const flags = detail?.sideEffectFlags;
    const diagnosticOnly = detail?.authorizationBoundary === 'DIAGNOSTIC_ONLY';

    return (
        <Card className="page-section" variant="borderless">
            <Space direction="vertical" size={12} style={{display: 'flex'}}>
                <NqRiskBanner
                    level="warning"
                    message="Diagnostic only / no trading authorization"
                    description="Shadow Run detail / replay 只展示本地诊断事实；consistency report 不是 approval，不代表实盘就绪，不允许据此下单、撤单、转账或提现。"
                />
                <Space size={[8, 8]} wrap>
                    <Tag color="error">LIVE disabled</Tag>
                    <Tag color={diagnosticOnly ? 'success' : 'error'}>Diagnostic only</Tag>
                    <Tag color="default">AI: NOT STARTED</Tag>
                    <Tag color="default">DH runtime: NOT INTEGRATED</Tag>
                    <Tag color="default">RealClient: NOT IMPLEMENTED</Tag>
                    {flags ? (
                        <>
                            <BoundaryFlag label="No order submission" enabled={flags.noOrderSubmission}/>
                            <BoundaryFlag label="No credential access" enabled={flags.noCredentialAccess}/>
                            <BoundaryFlag label="No private endpoint" enabled={flags.noPrivateEndpoint}/>
                            <BoundaryFlag label="No ledger mutation" enabled={flags.noLedgerMutation}/>
                            <BoundaryFlag label="No account mutation" enabled={flags.noAccountMutation}/>
                            <BoundaryFlag label="No external private I/O" enabled={flags.noExternalPrivateIo}/>
                        </>
                    ) : (
                        <>
                            <Tag color="error">No order submission: unavailable</Tag>
                            <Tag color="error">No credential access: unavailable</Tag>
                            <Tag color="error">No private endpoint: unavailable</Tag>
                            <Tag color="error">No ledger mutation: unavailable</Tag>
                            <Tag color="error">No account mutation: unavailable</Tag>
                        </>
                    )}
                </Space>
            </Space>
        </Card>
    );
}

function DrilldownBoundaryBadges({drilldown}: { drilldown?: PaperShadowConsistencyDrilldownResponse }) {
    return (
        <Space size={[8, 8]} wrap>
            <Tag color={drilldown?.liveDisabled === false ? 'warning' : 'error'}>LIVE DISABLED</Tag>
            <Tag color={drilldown?.realProviderImplemented ? 'warning' : 'default'}>Real provider NOT IMPLEMENTED</Tag>
            <Tag color={drilldown?.privateTradingImplemented ? 'warning' : 'default'}>Private trading NOT IMPLEMENTED</Tag>
            <Tag color={drilldown?.diagnosticOnly === false ? 'warning' : 'blue'}>Shadow Run is diagnostic only</Tag>
            <Tag color={drilldown?.notTradingAuthorization === false ? 'warning' : 'volcano'}>Not trading authorization</Tag>
            <Tag color={drilldown?.aiDhRuntimeIntegrated ? 'warning' : 'default'}>AI/DH runtime not integrated</Tag>
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
            render: (value: string) => <NqStatusTag status={value} tone={statusTone(value)}/>,
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
    ], []);

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
    ], []);

    return (
        <section aria-label="Paper Shadow drilldown next steps">
            <Typography.Title level={5}>NextSteps</Typography.Title>
            {items.length === 0 ? (
                <Empty description="暂无 nextSteps；不能自行补造执行动作。"/>
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
    ], []);

    return (
        <section aria-label="Paper Shadow drilldown evidence anchors">
            <Typography.Title level={5}>Evidence anchors</Typography.Title>
            {items.length === 0 ? (
                <Empty description="暂无 evidence anchors；不能补造证据锚点。"/>
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
    const usableDrilldown = isUsableDrilldownResponse(drilldown) ? drilldown : undefined;
    const state = usableDrilldown ? resolveDrilldownState(usableDrilldown) : null;

    if (isNotFound(error)) {
        return (
            <Card className="page-section" variant="borderless" title="Paper vs Shadow Consistency Drilldown">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqEmptyState description={`drilldown missing / Shadow Run 不存在或 drilldown 不可用：${shadowRunId}`}/>
                </Space>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title="Paper vs Shadow Consistency Drilldown">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqErrorState title="Consistency drilldown 加载失败" error={asAppApiError(error)} onRetry={onRetry}/>
                </Space>
            </Card>
        );
    }
    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title="Paper vs Shadow Consistency Drilldown">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqLoadingState message="Consistency drilldown loading"/>
                </Space>
            </Card>
        );
    }
    if (!usableDrilldown || !state) {
        return (
            <Card className="page-section" variant="borderless" title="Paper vs Shadow Consistency Drilldown">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges/>
                    <NqEmptyState description="暂无可用 drilldown response；不能解释为 report 已生成或 comparison 已通过。"/>
                </Space>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title="Paper vs Shadow Consistency Drilldown">
            <section aria-label="Paper Shadow consistency drilldown panel">
                <Space direction="vertical" size={14} style={{display: 'flex'}}>
                    <DrilldownBoundaryBadges drilldown={usableDrilldown}/>
                    <Alert
                        type={state.alertType}
                        showIcon
                        message={state.label}
                        description={`${state.description} notTradingAuthorization=${String(usableDrilldown.notTradingAuthorization)}，本区块不是 trading authorization，也不是实盘就绪依据。`}
                    />
                    <Alert
                        type="info"
                        showIcon
                        message="颜色与状态说明"
                        description="success / warning / danger 只用于诊断证据层级；不表示盈利、亏损、上涨、下跌、可交易或交易放行。comparisonStatus 只表达证据状态。"
                    />
                    <Descriptions size="small" bordered column={1}>
                        <Descriptions.Item label="comparisonStatus">
                            <NqStatusTag status={usableDrilldown.comparisonStatus} tone={state.tone}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="divergenceSeverity">
                            <NqStatusTag status={usableDrilldown.divergenceSeverity}
                                         tone={statusTone(usableDrilldown.divergenceSeverity)}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="generatedAt">{formatDateTime(usableDrilldown.generatedAt)}</Descriptions.Item>
                        <Descriptions.Item label="latestConsistency.generatedAt">
                            {formatDateTime(usableDrilldown.latestConsistency?.generatedAt)}
                        </Descriptions.Item>
                        <Descriptions.Item label="traceId"><OptionalCode value={usableDrilldown.traceId}/></Descriptions.Item>
                        <Descriptions.Item label="shadowRun.status">
                            <NqStatusTag status={usableDrilldown.shadowRun.status}
                                         tone={statusTone(usableDrilldown.shadowRun.status)}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="shadowRunId">
                            <OptionalCode value={usableDrilldown.shadowRun.shadowRunId}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="paperRunId">
                            <OptionalCode value={usableDrilldown.shadowRun.paperRunId}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="authorizationBoundary">
                            <NqStatusTag status={usableDrilldown.shadowRun.authorizationBoundary}
                                         tone={statusTone(usableDrilldown.shadowRun.authorizationBoundary)}/>
                        </Descriptions.Item>
                    </Descriptions>

                    <Row gutter={[12, 12]}>
                        <Col xs={24} lg={8}>
                            <Descriptions size="small" bordered column={1} title="Snapshot summary">
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
                            <Descriptions size="small" bordered column={1} title="Event summary">
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
                            <Descriptions size="small" bordered column={1} title="Latest report">
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
                            <section aria-label="Paper Shadow drilldown metricDelta">
                                <Typography.Title level={5}>metricDelta</Typography.Title>
                                <SafeJsonBlock value={usableDrilldown.metricDelta} emptyText="metricDelta 为空。"/>
                            </section>
                        </Col>
                        <Col xs={24} lg={8}>
                            <section aria-label="Paper Shadow drilldown divergence reasons">
                                <Typography.Title level={5}>divergenceReasons</Typography.Title>
                                <SafeJsonBlock value={usableDrilldown.divergenceReasons}
                                               emptyText="divergenceReasons 为空。"/>
                            </section>
                        </Col>
                        <Col xs={24} lg={8}>
                            <section aria-label="Paper Shadow drilldown limitations">
                                <Typography.Title level={5}>limitations</Typography.Title>
                                <SafeJsonBlock value={usableDrilldown.limitations} emptyText="limitations 为空。"/>
                            </section>
                        </Col>
                    </Row>

                    <DrilldownMessageTable
                        title="Blockers"
                        items={usableDrilldown.blockers}
                        emptyText="暂无 blockers；仍不代表交易放行。"
                    />
                    <DrilldownMessageTable
                        title="Warnings"
                        items={usableDrilldown.warnings}
                        emptyText="暂无 warnings。"
                    />
                    <DrilldownNextStepsTable items={usableDrilldown.nextSteps}/>
                    <DrilldownEvidenceAnchorsTable items={usableDrilldown.evidenceAnchors}/>
                </Space>
            </section>
        </Card>
    );
}

function ShadowRunDetailPanel({detail}: { detail: ShadowRunDetailResponse }) {
    return (
        <Card className="page-section" variant="borderless" title="Shadow Run 基本信息">
            <Space direction="vertical" size={14} style={{display: 'flex'}}>
                <Descriptions size="small" bordered column={1}>
                    <Descriptions.Item label="shadowRunId"><OptionalCode
                        value={detail.id}/></Descriptions.Item>
                    <Descriptions.Item label="status">
                        <NqStatusTag status={detail.status} tone={statusTone(detail.status)}/>
                    </Descriptions.Item>
                    <Descriptions.Item label="authorizationBoundary">
                        <NqStatusTag status={detail.authorizationBoundary}
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
                        <section aria-label="Shadow Run blockers">
                            <Typography.Title level={5}>Blockers</Typography.Title>
                            <SafeJsonBlock value={detail.blockers} emptyText="暂无 blockers；不能解释为交易放行。"/>
                        </section>
                    </Col>
                    <Col xs={24} lg={8}>
                        <section aria-label="Shadow Run warnings">
                            <Typography.Title level={5}>Warnings</Typography.Title>
                            <SafeJsonBlock value={detail.warnings} emptyText="暂无 warnings。"/>
                        </section>
                    </Col>
                    <Col xs={24} lg={8}>
                        <section aria-label="Shadow Run next steps">
                            <Typography.Title level={5}>NextSteps</Typography.Title>
                            <SafeJsonBlock value={detail.nextSteps} emptyText="暂无 nextSteps。"/>
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
    const orderedEvents = useMemo(() => sortedEvents(events), [events]);

    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title="Events 时间线">
                <NqLoadingState message="Events timeline loading"/>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title="Events 时间线">
                <NqErrorState title="Events timeline 加载失败" error={asAppApiError(error)} onRetry={onRetry}/>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title="Events 时间线">
            <section aria-label="Shadow Run events timeline">
                {orderedEvents.length === 0 ? (
                    <Empty description="暂无 events；不能补造生命周期事件。"/>
                ) : (
                    <Timeline
                        items={orderedEvents.map((event) => ({
                            color: timelineColor(event.eventType),
                            children: (
                                <Space direction="vertical" size={4}>
                                    <Space size={8} wrap>
                                        <NqStatusTag status={event.eventType} tone={statusTone(event.eventType)}/>
                                        <Text type="secondary">{formatDateTime(event.createdAt)}</Text>
                                        <Text code>{event.reasonCode ?? '-'}</Text>
                                    </Space>
                                    <Paragraph style={{marginBottom: 0}}>
                                        {event.message ?? '无事件说明。'}
                                    </Paragraph>
                                    <Text type="secondary">
                                        {event.fromStatus ?? '-'} -&gt; {event.toStatus ?? '-'} ·
                                        traceId {event.traceId ?? '-'}
                                    </Text>
                                    <SafeJsonBlock value={event.metadata} emptyText="metadata 为空。"/>
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
            render: (value: string) => <NqStatusTag status={value} tone={statusTone(value)}/>,
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
    ], []);

    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title="Snapshots 列表与详情">
                <NqLoadingState message="Snapshots loading"/>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title="Snapshots 列表与详情">
                <NqErrorState title="Snapshots 加载失败" error={asAppApiError(error)} onRetry={onRetry}/>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title="Snapshots 列表与详情">
            <section aria-label="Shadow Run snapshots panel">
                {orderedSnapshots.length === 0 ? (
                    <Empty description="暂无 snapshots；不能补造 replay evidence。"/>
                ) : (
                    <Space direction="vertical" size={12} style={{display: 'flex'}}>
                        <Alert
                            type="info"
                            showIcon
                            message="Snapshots 按 snapshotType + sequenceNo 排序"
                            description="payload 只做安全渲染；敏感 key/value 会被前端兜底过滤。"
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
                                    <NqStatusTag status={selectedSnapshot.snapshotType}
                                                 tone={statusTone(selectedSnapshot.snapshotType)}/>
                                </Descriptions.Item>
                                <Descriptions.Item label="sequenceNo">{selectedSnapshot.sequenceNo}</Descriptions.Item>
                                <Descriptions.Item label="source"><OptionalCode
                                    value={selectedSnapshot.source}/></Descriptions.Item>
                                <Descriptions.Item label="traceId"><OptionalCode
                                    value={selectedSnapshot.traceId}/></Descriptions.Item>
                                <Descriptions.Item label="payload">
                                    <SafeJsonBlock value={selectedSnapshot.payload} emptyText="payload 为空。"/>
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
    if (loading) {
        return (
            <Card className="page-section" variant="borderless" title="Latest consistency report">
                <NqLoadingState message="Consistency report loading"/>
            </Card>
        );
    }
    if (error) {
        return (
            <Card className="page-section" variant="borderless" title="Latest consistency report">
                {isNotFound(error) ? (
                    <NqEmptyState
                        description="latest consistency report not found / 尚未生成 latest consistency report。"/>
                ) : (
                    <NqErrorState title="Consistency report 加载失败" error={asAppApiError(error)} onRetry={onRetry}/>
                )}
            </Card>
        );
    }
    if (!report) {
        return (
            <Card className="page-section" variant="borderless" title="Latest consistency report">
                <NqEmptyState description="暂无 latest consistency report；不能解释为 comparison 已通过。"/>
            </Card>
        );
    }

    return (
        <Card className="page-section" variant="borderless" title="Latest consistency report">
            <section aria-label="Shadow consistency report panel">
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="warning"
                        showIcon
                        message="comparisonStatus 仅为诊断结果"
                        description="Consistency report 不是 approval，不代表 trading authorization，不代表实盘就绪。"
                    />
                    <Descriptions size="small" bordered column={1}>
                        <Descriptions.Item label="reportId"><OptionalCode value={report.id}/></Descriptions.Item>
                        <Descriptions.Item label="shadowRunId"><OptionalCode
                            value={report.shadowRunId}/></Descriptions.Item>
                        <Descriptions.Item label="paperRunId"><OptionalCode
                            value={report.paperRunId}/></Descriptions.Item>
                        <Descriptions.Item label="comparisonStatus">
                            <NqStatusTag status={report.comparisonStatus} tone={statusTone(report.comparisonStatus)}/>
                        </Descriptions.Item>
                        <Descriptions.Item label="generatedAt">{formatDateTime(report.generatedAt)}</Descriptions.Item>
                        <Descriptions.Item label="traceId"><OptionalCode value={report.traceId}/></Descriptions.Item>
                        <Descriptions.Item label="metricDelta">
                            <SafeJsonBlock value={report.metricDelta} emptyText="metricDelta 为空。"/>
                        </Descriptions.Item>
                        <Descriptions.Item label="divergenceReasons">
                            <SafeJsonBlock value={report.divergenceReasons} emptyText="divergenceReasons 为空。"/>
                        </Descriptions.Item>
                        <Descriptions.Item label="limitations">
                            <SafeJsonBlock value={report.limitations} emptyText="limitations 为空。"/>
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            </section>
        </Card>
    );
}

export function ShadowRunDetailPage() {
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
        return <Alert type="error" showIcon message="缺少 shadowRunId 路由参数。"/>;
    }

    return (
        <Space data-testid="shadow-run-detail-page" direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" variant="borderless">
                <NqPageHeader
                    title="Shadow Run detail / replay"
                    description="只读查看 Shadow Run 基本信息、Paper vs Shadow consistency drilldown、events 时间线、snapshots 和 latest consistency report。"
                    badge="GateR-7 / GateS-2 · Read-only"
                    extra={(
                        <Space size={8} wrap>
                            <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate('/strategies/shadow-runs')}>
                                返回 Shadow Run 列表
                            </Button>
                            <Button icon={<ReloadOutlined/>} onClick={refreshAll} loading={fetching}>
                                刷新只读数据
                            </Button>
                        </Space>
                    )}
                />
            </Card>

            <BoundarySummary detail={detailQuery.data}/>

            {detailQuery.isLoading ? (
                <Card className="page-section" variant="borderless">
                    <NqLoadingState message="Shadow Run detail loading"/>
                </Card>
            ) : detailQuery.isError ? (
                <Card className="page-section" variant="borderless">
                    {isNotFound(detailQuery.error) ? (
                        <NqEmptyState
                            description={`Shadow Run not found / Shadow Run 不存在：${normalizedShadowRunId}`}/>
                    ) : (
                        <NqErrorState
                            title="Shadow Run detail 加载失败"
                            error={asAppApiError(detailQuery.error)}
                            onRetry={() => detailQuery.refetch()}
                        />
                    )}
                </Card>
            ) : detailQuery.data ? (
                <ShadowRunDetailPanel detail={detailQuery.data}/>
            ) : (
                <Card className="page-section" variant="borderless">
                    <NqEmptyState description="暂无 Shadow Run detail。"/>
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

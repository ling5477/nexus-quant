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
    useShadowRunDetailQuery,
    useShadowRunEventsQuery,
    useShadowRunLatestConsistencyReportQuery,
    useShadowRunSnapshotsQuery,
} from '@/hooks/useShadowRunQueries';
import type {AppApiError} from '@/types/api';
import type {
    JsonObject,
    JsonValue,
    ShadowConsistencyReportResponse,
    ShadowRunDetailResponse,
    ShadowRunEventResponse,
    ShadowRunSnapshotResponse,
} from '@/types/shadow-runs';
import {formatDateTime} from '@/utils/formatters';

const {Text, Paragraph} = Typography;

const SENSITIVE_FIELD_NAME_PATTERN = /^(apiKey|api_key|secret|token|cookie|passphrase|privateKey|credential|credentialMaterial|encrypted_payload|decrypted_payload|rawRequest|rawResponse|rawHeaders|fullQueryString|privatePayload|privateEndpointPayload|rawPrivateRequest|rawPrivateResponse|realOrderId|realAccountBalance|realPosition|authorizedForTrading|tradingReady|liveReady|tradeApproved|orderExecutionCommand|privateAdapterReference)$/i;
const SENSITIVE_TEXT_PATTERN = /(api[_-]?key|secret|passphrase|private[_ -]?key|credentialMaterial|encrypted_payload|decrypted_payload|realOrderId|realAccountBalance|authorizedForTrading|tradingReady|liveReady|tradeApproved)/i;

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
                    description="Shadow Run detail / replay 只展示本地诊断事实；consistency report 不是 approval，不代表 LIVE ready，不允许据此下单、撤单、转账或提现。"
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
                        description="Consistency report 不是 approval，不代表 trading authorization，不代表 LIVE ready。"
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
    const fetching = detailQuery.isFetching || eventsQuery.isFetching || snapshotsQuery.isFetching || reportQuery.isFetching;

    const refreshAll = () => {
        void detailQuery.refetch();
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
                    description="只读查看 Shadow Run 基本信息、no-side-effect flags、events 时间线、snapshots 和 latest consistency report。"
                    badge="GateR-7 · Read-only"
                    extra={(
                        <Space size={8} wrap>
                            <Button icon={<ArrowLeftOutlined/>} onClick={() => navigate('/strategies/validation')}>
                                返回策略验证
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
                <>
                    <ShadowRunDetailPanel detail={detailQuery.data}/>
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
            ) : (
                <Card className="page-section" variant="borderless">
                    <NqEmptyState description="暂无 Shadow Run detail。"/>
                </Card>
            )}
        </Space>
    );
}

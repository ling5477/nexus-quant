import {StatusTag, type StatusTone} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {EyeOutlined, ReloadOutlined} from '@ant-design/icons';
import {Button, Card, List, Segmented, Space, Table, Tag, Tooltip, Typography} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMemo, useState} from 'react';
import {useNavigate} from 'react-router-dom';

import {NqEmptyState, NqErrorState, NqLoadingState, NqMetricCard, NqPageHeader, ApplicationRiskAlert} from '@/components/nq';
import {useShadowRunListQuery, useShadowRunOverview} from '@/hooks/useShadowRunQueries';
import {DataFreshness, type FreshnessState} from '@/nq-design-system/status/DataFreshness';
import type {AppApiError} from '@/types/api';
import type {ReadModelEvidenceMetadata} from '@/types/read-model-evidence';
import type {
    ShadowRunListItemResponse,
    ShadowRunListRequest,
    ShadowRunOverviewBlocker,
    ShadowRunOverviewEvidenceAnchor,
    ShadowRunOverviewNextStep,
    ShadowRunOverviewResponse,
    ShadowRunOverviewWarning,
} from '@/types/shadow-runs';
import {formatDateTime} from '@/utils/formatters';

const {Text} = Typography;

const STATUS_FILTERS = [
    'ALL',
    'CREATED',
    'PRECHECKING',
    'READY',
    'RUNNING',
    'STOP_REQUESTED',
    'STOPPED',
    'COMPLETED',
    'BLOCKED',
    'FAILED',
    'CANCELLED',
];

const SENSITIVE_TEXT_PATTERN = /(api[_-]?key|secret|passphrase|private[_ -]?key|credentialMaterial|realOrderId|realAccountBalance|authorizedForTrading|tradingReady|liveReady|tradeApproved|token)/i;

function asAppApiError(error: unknown): AppApiError | null {
    if (!error || typeof error !== 'object') {
        return null;
    }
    const candidate = error as Partial<AppApiError>;
    return typeof candidate.status === 'number' && typeof candidate.code === 'string'
        ? error as AppApiError
        : null;
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

function safeText(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '-';
    }
    const text = String(value);
    return SENSITIVE_TEXT_PATTERN.test(text) ? '[filtered sensitive value]' : text;
}

function countValue(value: number | null | undefined): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function BoundaryFlag({label, enabled}: { label: string; enabled: boolean }) {
    useTranslation('pages');
    return <Tag color={enabled ? 'success' : 'error'}>{label}: {enabled ? 'true' : 'false'}</Tag>;
}

function BoundaryTags({record}: { record: ShadowRunListItemResponse }) {
    useTranslation('pages');
    return (
        <Space size={[4, 4]} wrap>
            <BoundaryFlag label={t('pages:noOrderSubmission')} enabled={record.noOrderSubmission}/>
            <BoundaryFlag label={t('pages:noCredentialAccess')} enabled={record.noCredentialAccess}/>
            <BoundaryFlag label={t('pages:noPrivateEndpoint')} enabled={record.noPrivateEndpoint}/>
            <BoundaryFlag label={t('pages:noLedgerMutation')} enabled={record.noLedgerMutation}/>
            <BoundaryFlag label={t('pages:noAccountMutation')} enabled={record.noAccountMutation}/>
        </Space>
    );
}

function codeText(value: string | number | null | undefined) {
    const text = safeText(value);
    return text === '-' ? <Text type="secondary">-</Text> : <Text code>{text}</Text>;
}

function evidenceFreshnessState(metadata: ReadModelEvidenceMetadata | null | undefined): FreshnessState {
    const availability = metadata?.availability?.toUpperCase() ?? 'UNKNOWN';
    const freshness = metadata?.freshnessStatus?.toUpperCase() ?? 'UNKNOWN';
    if (availability === 'UNAVAILABLE') {
        return 'error';
    }
    if (availability === 'PARTIAL') {
        return 'degraded';
    }
    if (freshness === 'FRESH') {
        return 'fresh';
    }
    if (freshness === 'STALE') {
        return 'stale';
    }
    return 'no_data';
}

function EvidenceMetadataSummary({metadata}: { metadata?: ReadModelEvidenceMetadata | null }) {
    useTranslation('pages');
    const source = metadata?.source?.trim() || 'UNKNOWN_SOURCE';
    const availability = metadata?.availability?.toUpperCase() || 'UNKNOWN';
    const freshness = metadata?.freshnessStatus?.toUpperCase() || 'UNKNOWN';
    const freshnessText = freshness === 'FRESH'
        ? t('pages:fresh')
        : freshness === 'STALE' ? t('pages:stale') : t('pages:freshnessUnknown');

    return (
        <Space data-testid="shadow-run-evidence-metadata" direction="vertical" size={6} style={{display: 'flex'}}>
            <DataFreshness
                source={t('pages:dataSourceValue1', {value1: source})}
                state={evidenceFreshnessState(metadata)}
                detail={metadata?.ageSeconds == null ? freshnessText : `${freshnessText}；age ${metadata.ageSeconds}s`}
            />
            <Space size={[8, 6]} wrap>
                <Tag color={availability === 'AVAILABLE' && freshness === 'FRESH'
                    ? 'success'
                    : availability === 'PARTIAL' || freshness === 'STALE' ? 'warning' : availability === 'UNAVAILABLE' ? 'error' : 'default'}>
                    {t('pages:availability')}{availability}
                </Tag>
                <Text>{t('pages:freshness')}{freshness}（{freshnessText}）</Text>
                <Text>{t('pages:lastComputed')}{metadata?.lastCalculatedAt ? formatDateTime(metadata.lastCalculatedAt) : t('pages:notProvided')}</Text>
            </Space>
        </Space>
    );
}

type OverviewMessage = ShadowRunOverviewBlocker | ShadowRunOverviewWarning;

type OverviewState = {
    level: 'info' | 'warning' | 'danger';
    message: string;
    description: string;
};

function enumExplanation(status: string, category: 'run' | 'comparison' | 'severity'): string {
    const normalized = status.toUpperCase();
    const runStatus: Record<string, string> = {
        CREATED: t('pages:createdOnlyALocalShadowRunFactExists'),
        PRECHECKING: t('pages:preflightInProgressForLocalDiagnosticsOnly'),
        READY: t('pages:diagnosticRunReadyNoTradingAuthorization'),
        RUNNING: t('pages:localDiagnosticsRunningThisDoesNotStartASchedulerOrLiveTrading'),
        STOP_REQUESTED: t('pages:stopRequestRecorded'),
        STOPPED: t('pages:stopped'),
        COMPLETED: t('pages:diagnosticRunCompletedNoClaimOfProfitOrTradingApproval'),
        BLOCKED: t('pages:blockedReviewBlockersAndNextsteps'),
        FAILED: t('pages:failedReviewErrorsAndEvidenceAnchors'),
        CANCELLED: t('pages:cancelled'),
    };
    const comparisonStatus: Record<string, string> = {
        CONSISTENT: t('pages:consistentAtTheEvidenceComparisonLevelOnly'),
        DIVERGED: t('pages:divergedInspectDivergenceReasons'),
        PARTIAL: t('pages:partiallyComparableThisDoesNotMeanPassed'),
        NOT_COMPARABLE: t('pages:notComparableDueToInsufficientEvidenceOrUnmetBoundaries'),
        FAILED: t('pages:comparisonFailedInspectTheReportAndTraceid'),
    };
    const severityStatus: Record<string, string> = {
        NONE: t('pages:noDivergence'),
        LOW: t('pages:lowDivergenceDiagnosticPrioritizationOnly'),
        MEDIUM: t('pages:mediumDivergenceReviewRequired'),
        HIGH: t('pages:highDivergencePrioritizeReview'),
        CRITICAL: t('pages:criticalDivergenceBlockSubsequentConclusions'),
        UNKNOWN: t('pages:unknownUsuallyNoConsistencyReportIsAvailable'),
    };
    const dictionary = category === 'run'
        ? runStatus
        : category === 'comparison'
            ? comparisonStatus
            : severityStatus;
    return `${status}：${dictionary[normalized] ?? t('pages:rawBackendEnumForReadOnlyDiagnosticsNoTradingAuthorization')}`;
}

function StatusWithHint({status, category}: {
    status: string | null | undefined;
    category: 'run' | 'comparison' | 'severity'
}) {
    useTranslation('pages');
    const text = safeText(status);
    if (text === '-') {
        return <Text type="secondary">-</Text>;
    }
    return (
        <Tooltip title={enumExplanation(text, category)}>
            <span><StatusTag title="" variant="pill" status={text} tone={statusTone(text)}/></span>
        </Tooltip>
    );
}

function overviewEmpty(overview: ShadowRunOverviewResponse): boolean {
    return countValue(overview.totalRuns) === 0 && !overview.latestRun && !overview.latestConsistency;
}

function hasStaleEvidence(overview: ShadowRunOverviewResponse): boolean {
    return countValue(overview.staleRuns) > 0
        || overview.warnings.some((warning) => warning.code?.toUpperCase() === 'STALE_EVIDENCE');
}

function hasDivergence(overview: ShadowRunOverviewResponse): boolean {
    const comparisonStatus = overview.latestConsistency?.comparisonStatus?.toUpperCase() ?? '';
    const severity = overview.divergenceSeverity?.toUpperCase() ?? '';
    return comparisonStatus === 'DIVERGED' || ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(severity);
}

function resolveOverviewState(overview: ShadowRunOverviewResponse): OverviewState {
    const latestRunStatus = overview.latestRun?.status?.toUpperCase() ?? '';
    const latestComparisonStatus = overview.latestConsistency?.comparisonStatus?.toUpperCase() ?? '';
    if (countValue(overview.failedRuns) > 0 || latestRunStatus.includes('FAILED') || latestComparisonStatus === 'FAILED') {
        return {
            level: 'danger',
            message: t('pages:overviewContainsDiagnosticFailures'),
            description: t('pages:failedRunsOrConsistencyChecksAreLocalDiagnosticFailuresInspectTraceidAndEvidenceAnchorsTradingAuthor'),
        };
    }
    if (countValue(overview.blockedRuns) > 0 || latestRunStatus.includes('BLOCKED')) {
        return {
            level: 'warning',
            message: t('pages:overviewContainsBlockedRuns'),
            description: t('pages:blockedMeansTheLocalDiagnosticChainIsBlockedReviewBlockersAndNextstepsThisIsNotLiveOrStrategyApprova'),
        };
    }
    if (hasDivergence(overview)) {
        return {
            level: 'warning',
            message: t('pages:overviewContainsDivergence'),
            description: t('pages:divergenceSeverityPrioritizesDiagnosticsOnlySuccessAndDangerColorsDoNotIndicateReturnsOrMarketDirect'),
        };
    }
    if (hasStaleEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:overviewContainsStaleEvidence'),
            description: t('pages:localEvidenceIsStaleOrMissingRecheckReadOnlyFactsNoRunnerOrTradingActionIsTriggered'),
        };
    }
    return {
        level: 'info',
        message: t('pages:overviewLoaded'),
        description: t('pages:thisReadOnlySummarySupportsOperationalDiagnosticsNotTradingAuthorization'),
    };
}

function BoundaryBadge({label, tooltip, color}: { label: string; tooltip: string; color?: string }) {
    useTranslation('pages');
    return (
        <Tooltip title={tooltip}>
            <Tag color={color}>{label}</Tag>
        </Tooltip>
    );
}

function OverviewBoundaryBadges({overview}: { overview?: ShadowRunOverviewResponse }) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataIsShownFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t('pages:livedisabledTrueMeansLiveIsDisabledNotTradingAuthorizationValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t('pages:realproviderimplementedFalseRealProvidersMustNotBeShownAsAvailableValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t('pages:privatetradingimplementedFalseNoRealOrdersCancellationsTransfersOrWithdrawalsValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:shadowRunIsDiagnosticOnly')}
                tooltip={t('pages:diagnosticonlyTrueLocalShadowRunDiagnosticFactsOnlyValue1', {value1: pending})}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t('pages:nottradingauthorizationTrueApprovedOrConsistentDoNotGrantTradingAuthorizationValue1', {value1: pending})}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t('pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue1', {value1: pending})}
            />
        </Space>
    );
}

function OverviewMetricStrip({overview, loading}: { overview?: ShadowRunOverviewResponse; loading: boolean }) {
    useTranslation('pages');
    return (
        <Space size={[12, 12]} wrap>
            <NqMetricCard label={t('pages:totalRuns')} value={countValue(overview?.totalRuns)} loading={loading}
                          footer={t('pages:readOnlyLocalFacts')}/>
            <NqMetricCard label={t('pages:runningRuns')} value={countValue(overview?.runningRuns)} loading={loading}/>
            <NqMetricCard label={t('pages:blockedRuns')} value={countValue(overview?.blockedRuns)} loading={loading}
                          tone={countValue(overview?.blockedRuns) > 0 ? 'warning' : 'default'}/>
            <NqMetricCard label={t('pages:failedRuns')} value={countValue(overview?.failedRuns)} loading={loading}
                          tone={countValue(overview?.failedRuns) > 0 ? 'danger' : 'default'}/>
            <NqMetricCard label={t('pages:completedRuns')} value={countValue(overview?.completedRuns)} loading={loading}/>
            <NqMetricCard label={t('pages:staleRuns')} value={countValue(overview?.staleRuns)} loading={loading}
                          tone={countValue(overview?.staleRuns) > 0 ? 'warning' : 'default'}/>
        </Space>
    );
}

function OverviewMessageList({title, items, emptyText}: {
    title: string;
    items: OverviewMessage[];
    emptyText: string
}) {
    useTranslation('pages');
    if (items.length === 0) {
        return (
            <Space direction="vertical" size={4}>
                <Text strong>{title}</Text>
                <Text type="secondary">{emptyText}</Text>
            </Space>
        );
    }
    return (
        <section aria-label={title}>
            <Text strong>{title}</Text>
            <List
                size="small"
                dataSource={items.slice(0, 4)}
                renderItem={(item) => (
                    <List.Item>
                        <Space direction="vertical" size={2} style={{display: 'flex'}}>
                            <Space size={[6, 6]} wrap>
                                <StatusTag title="" variant="pill" status={safeText(item.severity)} tone={statusTone(item.severity)}/>
                                {codeText(item.code)}
                                <Text type="secondary">{safeText(item.sourceType)}</Text>
                                {item.sourceId ? codeText(item.sourceId) : null}
                            </Space>
                            <Text>{safeText(item.message)}</Text>
                        </Space>
                    </List.Item>
                )}
            />
            {items.length > 4 ? <Text type="secondary">{t('pages:additional')}{items.length - 4} {t('pages:itemsSummaryViewOnly')}</Text> : null}
        </section>
    );
}

function OverviewNextSteps({items}: { items: ShadowRunOverviewNextStep[] }) {
    useTranslation('pages');
    if (items.length === 0) {
        return (
            <Space direction="vertical" size={4}>
                <Text strong>{t('pages:nextSteps')}</Text>
                <Text type="secondary">{t('pages:noNextstepsThisDoesNotPermitTrading')}</Text>
            </Space>
        );
    }
    return (
        <section aria-label={t('pages:shadowRunOverviewNextSteps')}>
            <Text strong>{t('pages:nextSteps')}</Text>
            <List
                size="small"
                dataSource={items.slice(0, 4)}
                renderItem={(item) => (
                    <List.Item>
                        <Space direction="vertical" size={2} style={{display: 'flex'}}>
                            <Space size={[6, 6]} wrap>
                                {codeText(item.code)}
                                <Tag color={item.blocking ? 'error' : 'default'}>
                                    {item.blocking ? 'blocking' : 'non-blocking'}
                                </Tag>
                                <Text type="secondary">owner: {safeText(item.owner)}</Text>
                            </Space>
                            <Text>{safeText(item.action)}</Text>
                            <Text type="secondary">expectedEvidence: {safeText(item.expectedEvidence)}</Text>
                        </Space>
                    </List.Item>
                )}
            />
            {items.length > 4 ? <Text type="secondary">{t('pages:additional')}{items.length - 4} {t('pages:itemsSummaryViewOnly')}</Text> : null}
        </section>
    );
}

function OverviewEvidenceSummary({overview}: { overview: ShadowRunOverviewResponse }) {
    useTranslation('pages');
    const anchors = overview.evidenceAnchors.slice(0, 4);
    return (
        <Space direction="vertical" size={8} style={{display: 'flex'}}>
            <Space size={[12, 8]} wrap>
                <Text>generatedAt: {formatDateTime(overview.generatedAt)}</Text>
                <Text>traceId: {codeText(overview.traceId)}</Text>
            </Space>
            {anchors.length === 0 ? (
                <Text type="secondary">{t('pages:noEvidenceAnchorsEvidenceMustNotBeFabricated')}</Text>
            ) : (
                <List<ShadowRunOverviewEvidenceAnchor>
                    size="small"
                    dataSource={anchors}
                    renderItem={(anchor) => (
                        <List.Item>
                            <Space size={[8, 6]} wrap>
                                <Text>{safeText(anchor.sourceType)}</Text>
                                {codeText(anchor.sourceId)}
                                <Text type="secondary">version: {safeText(anchor.sourceVersion)}</Text>
                                <Text type="secondary">time: {formatDateTime(anchor.sourceTimestamp)}</Text>
                                <Text type="secondary">checksum: {safeText(anchor.checksum)}</Text>
                            </Space>
                        </List.Item>
                    )}
                />
            )}
            {overview.evidenceAnchors.length > 4
                ? <Text type="secondary">{t('pages:additional')}{overview.evidenceAnchors.length - 4} {t('pages:evidenceAnchors')}</Text>
                : null}
        </Space>
    );
}

function ShadowRunOverviewSummary({
    overview,
    isLoading,
    isFetching,
    isError,
    error,
    onRetry,
}: {
    overview?: ShadowRunOverviewResponse;
    isLoading: boolean;
    isFetching: boolean;
    isError: boolean;
    error: unknown;
    onRetry: () => void;
}) {
    useTranslation('pages');
    const empty = overview ? overviewEmpty(overview) : false;
    const overviewState = overview ? resolveOverviewState(overview) : null;

    return (
        <Card
            className="page-section"
            variant="borderless"
            title={t('pages:overviewSummary')}
            extra={(
                <Button icon={<ReloadOutlined/>} loading={isFetching} onClick={onRetry}>
                    {t('pages:refreshOverview')}</Button>
            )}
        >
            <Space direction="vertical" size={14} style={{display: 'flex'}}>
                <Text type="secondary">
                    {t('pages:readOnlyGetApiShadowRunsOverviewForShadowRunDiagnosticsWithoutNewRoutesDashboardV2OrWriteActions')}</Text>
                <OverviewBoundaryBadges overview={overview}/>
                <EvidenceMetadataSummary metadata={overview?.evidenceMetadata}/>
                <OverviewMetricStrip overview={overview} loading={isLoading}/>

                {isLoading ? (
                    <NqLoadingState message={t('pages:loadingShadowRunOverview')}/>
                ) : isError ? (
                    <NqErrorState
                        title={t('pages:failedToLoadShadowRunOverview')}
                        error={asAppApiError(error)}
                        description={t('pages:aFailedOverviewIsNotShownAsEmptyDataOrATradableState')}
                        onRetry={onRetry}
                    />
                ) : !overview ? (
                    <NqEmptyState description={t('pages:noShadowRunOverviewResponseFixedSafetyBoundariesRemainFailClosed')}/>
                ) : empty ? (
                    <NqEmptyState description={t('pages:noShadowRunsTheOverviewShowsZeroCountsAndFixedSafetyBoundariesOnly')}/>
                ) : (
                    <>
                        {overviewState ? (
                            <ApplicationRiskAlert
                                level={overviewState.level}
                                message={overviewState.message}
                                description={overviewState.description}
                            />
                        ) : null}
                        <Space size={[12, 12]} wrap>
                            <NqMetricCard
                                label="latestRun.status"
                                value={<StatusWithHint status={overview.latestRun?.status} category="run"/>}
                                footer={overview.latestRun ? codeText(overview.latestRun.shadowRunId) : t('pages:noLatestRun')}
                            />
                            <NqMetricCard
                                label="latestConsistency"
                                value={(
                                    <StatusWithHint
                                        status={overview.latestConsistency?.comparisonStatus}
                                        category="comparison"
                                    />
                                )}
                                footer={overview.latestConsistency ? codeText(overview.latestConsistency.reportId) : t('pages:noReport')}
                            />
                            <NqMetricCard
                                label="divergenceSeverity"
                                value={<StatusWithHint status={overview.divergenceSeverity} category="severity"/>}
                                footer={t('pages:diagnosticSeverityOnly')}
                            />
                        </Space>
                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                            <OverviewMessageList
                                title={t('pages:blockers2')}
                                items={overview.blockers}
                                emptyText={t('pages:noBlockersThisDoesNotAuthorizeTrading')}
                            />
                            <OverviewMessageList
                                title={t('pages:warnings3')}
                                items={overview.warnings}
                                emptyText={t('pages:noWarningsFixedSafetyBoundariesStillApply')}
                            />
                            <OverviewNextSteps items={overview.nextSteps}/>
                            <OverviewEvidenceSummary overview={overview}/>
                        </Space>
                    </>
                )}
            </Space>
        </Card>
    );
}

/**
 * GateR-8 / GateS-1 Shadow Run list page.
 *
 * Why:
 * 这是 GateR-7 detail / replay 页面的只读入口，并在 GateS-1 增加 overview summary。
 * 页面只调用 GET overview/list/detail 链路，不提供 start / stop / execute / rerun / approve / trade 操作，
 * 不触发 runner、scheduler、credential、private endpoint 或真实交易。
 */
export function ShadowRunListPage() {
    const {i18n: pageI18n} = useTranslation('pages');
    const navigate = useNavigate();
    const [statusFilter, setStatusFilter] = useState<string>('ALL');
    const queryParams = useMemo<ShadowRunListRequest>(() => ({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        limit: 50,
        offset: 0,
    }), [statusFilter]);
    const overviewQuery = useShadowRunOverview();
    const listQuery = useShadowRunListQuery(queryParams);
    const rows = listQuery.data?.items ?? [];

    const columns = useMemo<ColumnsType<ShadowRunListItemResponse>>(() => [
        {
            title: 'shadowRunId',
            dataIndex: 'id',
            key: 'id',
            width: 280,
            render: (value: string) => codeText(value),
        },
        {
            title: 'status',
            dataIndex: 'status',
            key: 'status',
            width: 150,
            render: (value: string) => <StatusTag title="" variant="pill" status={safeText(value)} tone={statusTone(value)}/>,
        },
        {
            title: 'strategyVersionId',
            dataIndex: 'strategyVersionId',
            key: 'strategyVersionId',
            width: 190,
            render: (value: string) => codeText(value),
        },
        {
            title: 'datasetId',
            dataIndex: 'datasetId',
            key: 'datasetId',
            width: 260,
            render: (value: string) => codeText(value),
        },
        {
            title: 'paperRunId',
            dataIndex: 'paperRunId',
            key: 'paperRunId',
            width: 170,
            render: (value: string | null) => codeText(value),
        },
        {
            title: 'traceId',
            dataIndex: 'traceId',
            key: 'traceId',
            width: 190,
            render: (value: string) => codeText(value),
        },
        {
            title: 'createdAt',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 190,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: 'counts',
            key: 'counts',
            width: 210,
            render: (_, record) => (
                <Space size={[4, 4]} wrap>
                    <Tag>blockers {countValue(record.blockersCount)}</Tag>
                    <Tag>warnings {countValue(record.warningsCount)}</Tag>
                    <Tag>next {countValue(record.nextStepsCount)}</Tag>
                </Space>
            ),
        },
        {
            title: t('pages:noSideEffectFlags'),
            key: 'flags',
            width: 360,
            render: (_, record) => <BoundaryTags record={record}/>,
        },
        {
            title: 'detail',
            key: 'detail',
            width: 130,
            fixed: 'right',
            render: (_, record) => (
                <Button
                    type="link"
                    icon={<EyeOutlined/>}
                    onClick={(event) => {
                        event.stopPropagation();
                        navigate(`/strategies/shadow-runs/${record.id}`);
                    }}
                >
                    {t('pages:viewDetails2')}</Button>
            ),
        },
    ], [navigate, pageI18n.resolvedLanguage]);

    return (
        <Space data-testid="shadow-run-list-page" direction="vertical" size={16} style={{display: 'flex'}}>
            <Card className="page-card" variant="borderless">
                <NqPageHeader
                    title={t('pages:shadowRuns')}
                    description={t('pages:viewLocalShadowDiagnosticsInReadOnlyModeAndOpenDetailsOrReplayFromTheList')}
                    badge={t('pages:shadowRunsReadOnlyList')}
                    extra={(
                        <Button icon={<ReloadOutlined/>} loading={listQuery.isFetching}
                                onClick={() => listQuery.refetch()}>
                            {t('pages:refreshReadOnlyList')}</Button>
                    )}
                />
            </Card>

            <ShadowRunOverviewSummary
                overview={overviewQuery.data}
                isLoading={overviewQuery.isLoading}
                isFetching={overviewQuery.isFetching}
                isError={overviewQuery.isError}
                error={overviewQuery.error}
                onRetry={() => overviewQuery.refetch()}
            />

            <ApplicationRiskAlert
                level="warning"
                message={t('pages:diagnosticsOnlyNoTradingAuthorization')}
                description={t('pages:thisListShowsLocalDiagnosticsOnlyItDoesNotStartRunnersSubmitOrdersReadCredentialsCallPrivateEndpoint')}
            />

            <Space size={[12, 12]} wrap>
                <NqMetricCard label={t('pages:queryWindow')} value={`${rows.length} / ${listQuery.data?.total ?? 0}`}
                              footer={t('pages:boundedLocalFacts')}/>
                <NqMetricCard label="LIVE" value={<StatusTag title="" variant="pill" status="DISABLED" tone="danger"/>}/>
                <NqMetricCard label="AI" value={<StatusTag title="" variant="pill" status="NOT STARTED" tone="neutral"/>}/>
                <NqMetricCard label={t('pages:dhRuntime')} value={<StatusTag title="" variant="pill" status="NOT INTEGRATED" tone="neutral"/>}/>
            </Space>

            <Card className="page-section" variant="borderless" title={t('pages:filter')}>
                <Space direction="vertical" size={8} style={{display: 'flex'}}>
                    <Text type="secondary">{t('pages:theStatusFilterAffectsGetApiShadowRunsOnlyAndTriggersNoWrites')}</Text>
                    <div data-testid="shadow-run-status-filter">
                        <Segmented
                            block
                            options={STATUS_FILTERS}
                            value={statusFilter}
                            onChange={(value) => setStatusFilter(String(value))}
                        />
                    </div>
                </Space>
            </Card>

            {listQuery.isLoading ? (
                <Card className="page-section" variant="borderless">
                    <NqLoadingState message={t('pages:loadingShadowRuns')}/>
                </Card>
            ) : listQuery.isError ? (
                <Card className="page-section" variant="borderless">
                    <NqErrorState
                        title={t('pages:failedToLoadShadowRuns')}
                        error={asAppApiError(listQuery.error)}
                        onRetry={() => listQuery.refetch()}
                    />
                </Card>
            ) : rows.length === 0 ? (
                <Card className="page-section" variant="borderless">
                    <NqEmptyState description={t('pages:noShadowRunListDataLocalFactsMustNotBeFabricatedOrInterpretedAsRemovalOfTradingBlocks')}/>
                </Card>
            ) : (
                <Card className="page-section" variant="borderless" title={t('pages:shadowRunFacts')}>
                    <Table<ShadowRunListItemResponse>
                        size="small"
                        rowKey="id"
                        columns={columns}
                        dataSource={rows}
                        pagination={false}
                        scroll={{x: 2030}}
                        onRow={(record) => ({
                            style: {cursor: 'pointer'},
                            onClick: () => navigate(`/strategies/shadow-runs/${record.id}`),
                        })}
                    />
                </Card>
            )}
        </Space>
    );
}

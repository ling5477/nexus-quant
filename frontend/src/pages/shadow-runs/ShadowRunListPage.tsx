import {StatusTag} from '@/nq-design-system/status/StatusTag';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {EyeOutlined, ReloadOutlined} from '@ant-design/icons';
import {
    Alert,
    Button,
    Card,
    Descriptions,
    Grid,
    List,
    Pagination,
    Select,
    Space,
    Table,
    Tag,
    Tooltip,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useCallback, useEffect, useMemo} from 'react';
import {useLocation, useNavigate, useSearchParams} from 'react-router-dom';
import {
    SHADOW_LIST_PAGE_SIZE,
    SHADOW_LIST_PATH,
    SHADOW_RUN_STATUSES,
    shadowListLocation,
    shadowListReturn,
} from '@/features/validation/shadow-navigation';
import './ShadowRunListPage.css';

import {
    NqEmptyState,
    NqErrorState,
    NqLoadingState,
    NqMetricCard,
    NqPageHeader,
    ApplicationRiskAlert,
} from '@/components/nq';
import {
    useShadowRunListQuery,
    useShadowRunOverview,
} from '@/features/validation/hooks/useShadowRunQueries';
import {DataFreshness, type FreshnessState} from '@/nq-design-system/status/DataFreshness';
import type {AppApiError} from '@/types/api';
import type {ReadModelEvidenceMetadata} from '@/features/validation/types/read-model-evidence';
import type {
    ShadowRunListItemResponse,
    ShadowRunListRequest,
    ShadowRunOverviewBlocker,
    ShadowRunOverviewEvidenceAnchor,
    ShadowRunOverviewNextStep,
    ShadowRunOverviewResponse,
    ShadowRunOverviewWarning,
} from '@/features/validation/types/shadow-runs';
import {formatDateTime} from '@/utils/formatters';
import {statusTone} from './statusTone';

const {Text} = Typography;

const SENSITIVE_TEXT_PATTERN =
    /(api[_-]?key|secret|passphrase|private[_ -]?key|credentialMaterial|realOrderId|realAccountBalance|authorizedForTrading|tradingReady|liveReady|tradeApproved|token)/i;

function asAppApiError(error: unknown): AppApiError | null {
    if (!error || typeof error !== 'object') {
        return null;
    }
    const candidate = error as Partial<AppApiError>;
    return typeof candidate.status === 'number' && typeof candidate.code === 'string'
        ? (error as AppApiError)
        : null;
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

function BoundaryFlag({label, enabled}: {label: string; enabled: boolean}) {
    useTranslation('pages');
    return (
        <Tag color={enabled ? 'success' : 'error'}>
            {label}: {enabled ? 'true' : 'false'}
        </Tag>
    );
}

function BoundaryTags({record}: {record: ShadowRunListItemResponse}) {
    useTranslation('pages');
    return (
        <Space size={[4, 4]} wrap>
            <BoundaryFlag label={t('pages:noOrderSubmission')} enabled={record.noOrderSubmission} />
            <BoundaryFlag
                label={t('pages:noCredentialAccess')}
                enabled={record.noCredentialAccess}
            />
            <BoundaryFlag label={t('pages:noPrivateEndpoint')} enabled={record.noPrivateEndpoint} />
            <BoundaryFlag label={t('pages:noLedgerMutation')} enabled={record.noLedgerMutation} />
            <BoundaryFlag label={t('pages:noAccountMutation')} enabled={record.noAccountMutation} />
        </Space>
    );
}

function codeText(value: string | number | null | undefined) {
    const text = safeText(value);
    // 复制与展示使用同一脱敏值，不能通过复制按钮绕过敏感文本过滤。
    return text === '-' ? (
        <Text type="secondary">-</Text>
    ) : (
        <Text code copyable={{text}}>
            {text}
        </Text>
    );
}

function evidenceFreshnessState(
    metadata: ReadModelEvidenceMetadata | null | undefined,
): FreshnessState {
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

function EvidenceMetadataSummary({metadata}: {metadata?: ReadModelEvidenceMetadata | null}) {
    useTranslation('pages');
    const source = metadata?.source?.trim() || 'UNKNOWN_SOURCE';
    const availability = metadata?.availability?.toUpperCase() || 'UNKNOWN';
    const freshness = metadata?.freshnessStatus?.toUpperCase() || 'UNKNOWN';
    const freshnessText =
        freshness === 'FRESH'
            ? t('pages:fresh')
            : freshness === 'STALE'
              ? t('pages:stale')
              : t('pages:freshnessUnknown');

    return (
        <Space
            data-testid="shadow-run-evidence-metadata"
            direction="vertical"
            size={6}
            style={{display: 'flex'}}
        >
            <DataFreshness
                source={t('pages:dataSourceValue1', {value1: source})}
                state={evidenceFreshnessState(metadata)}
                detail={
                    metadata?.ageSeconds == null
                        ? freshnessText
                        : `${freshnessText}；age ${metadata.ageSeconds}s`
                }
            />
            <Space size={[8, 6]} wrap>
                <Tag
                    color={
                        availability === 'AVAILABLE' && freshness === 'FRESH'
                            ? 'success'
                            : availability === 'PARTIAL' || freshness === 'STALE'
                              ? 'warning'
                              : availability === 'UNAVAILABLE'
                                ? 'error'
                                : 'default'
                    }
                >
                    {t('pages:availability')}
                    {availability}
                </Tag>
                <Text>
                    {t('pages:freshness')}
                    {freshness}（{freshnessText}）
                </Text>
                <Text>
                    {t('pages:lastComputed')}
                    {metadata?.lastCalculatedAt
                        ? formatDateTime(metadata.lastCalculatedAt)
                        : t('pages:notProvided')}
                </Text>
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
    const dictionary =
        category === 'run'
            ? runStatus
            : category === 'comparison'
              ? comparisonStatus
              : severityStatus;
    return `${status}：${dictionary[normalized] ?? t('pages:rawBackendEnumForReadOnlyDiagnosticsNoTradingAuthorization')}`;
}

function StatusWithHint({
    status,
    category,
}: {
    status: string | null | undefined;
    category: 'run' | 'comparison' | 'severity';
}) {
    useTranslation('pages');
    const text = safeText(status);
    if (text === '-') {
        return <Text type="secondary">-</Text>;
    }
    return (
        <Tooltip title={enumExplanation(text, category)}>
            <span>
                <StatusTag title="" variant="pill" status={text} tone={statusTone(text)} />
            </span>
        </Tooltip>
    );
}

function overviewEmpty(overview: ShadowRunOverviewResponse): boolean {
    return (
        countValue(overview.totalRuns) === 0 && !overview.latestRun && !overview.latestConsistency
    );
}

function hasStaleEvidence(overview: ShadowRunOverviewResponse): boolean {
    return (
        countValue(overview.staleRuns) > 0 ||
        overview.warnings.some((warning) => warning.code?.toUpperCase() === 'STALE_EVIDENCE')
    );
}

function hasDivergence(overview: ShadowRunOverviewResponse): boolean {
    const comparisonStatus = overview.latestConsistency?.comparisonStatus?.toUpperCase() ?? '';
    const severity = overview.divergenceSeverity?.toUpperCase() ?? '';
    return (
        comparisonStatus === 'DIVERGED' || ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(severity)
    );
}

function resolveOverviewState(overview: ShadowRunOverviewResponse): OverviewState {
    const latestRunStatus = overview.latestRun?.status?.toUpperCase() ?? '';
    const latestComparisonStatus =
        overview.latestConsistency?.comparisonStatus?.toUpperCase() ?? '';
    if (
        countValue(overview.failedRuns) > 0 ||
        latestRunStatus.includes('FAILED') ||
        latestComparisonStatus === 'FAILED'
    ) {
        return {
            level: 'danger',
            message: t('pages:overviewContainsDiagnosticFailures'),
            description: t(
                'pages:failedRunsOrConsistencyChecksAreLocalDiagnosticFailuresInspectTraceidAndEvidenceAnchorsTradingAuthor',
            ),
        };
    }
    if (countValue(overview.blockedRuns) > 0 || latestRunStatus.includes('BLOCKED')) {
        return {
            level: 'warning',
            message: t('pages:overviewContainsBlockedRuns'),
            description: t(
                'pages:blockedMeansTheLocalDiagnosticChainIsBlockedReviewBlockersAndNextstepsThisIsNotLiveOrStrategyApprova',
            ),
        };
    }
    if (hasDivergence(overview)) {
        return {
            level: 'warning',
            message: t('pages:overviewContainsDivergence'),
            description: t(
                'pages:divergenceSeverityPrioritizesDiagnosticsOnlySuccessAndDangerColorsDoNotIndicateReturnsOrMarketDirect',
            ),
        };
    }
    if (hasStaleEvidence(overview)) {
        return {
            level: 'warning',
            message: t('pages:overviewContainsStaleEvidence'),
            description: t(
                'pages:localEvidenceIsStaleOrMissingRecheckReadOnlyFactsNoRunnerOrTradingActionIsTriggered',
            ),
        };
    }
    return {
        level: 'info',
        message: t('pages:overviewLoaded'),
        description: t(
            'pages:thisReadOnlySummarySupportsOperationalDiagnosticsNotTradingAuthorization',
        ),
    };
}

function BoundaryBadge({label, tooltip, color}: {label: string; tooltip: string; color?: string}) {
    useTranslation('pages');
    return (
        <Tooltip title={tooltip}>
            <Tag color={color}>{label}</Tag>
        </Tooltip>
    );
}

function OverviewBoundaryBadges({overview}: {overview?: ShadowRunOverviewResponse}) {
    useTranslation('pages');
    const pending = overview ? '' : t('pages:unavailableOverviewDataIsShownFailClosed');
    return (
        <Space size={[8, 8]} wrap>
            <BoundaryBadge
                color="error"
                label={t('pages:liveDisabled')}
                tooltip={t(
                    'pages:livedisabledTrueMeansLiveIsDisabledNotTradingAuthorizationValue1',
                    {value1: pending},
                )}
            />
            <BoundaryBadge
                label={t('pages:realProviderNotImplemented')}
                tooltip={t(
                    'pages:realproviderimplementedFalseRealProvidersMustNotBeShownAsAvailableValue1',
                    {value1: pending},
                )}
            />
            <BoundaryBadge
                label={t('pages:privateTradingNotImplemented')}
                tooltip={t(
                    'pages:privatetradingimplementedFalseNoRealOrdersCancellationsTransfersOrWithdrawalsValue1',
                    {value1: pending},
                )}
            />
            <BoundaryBadge
                color="warning"
                label={t('pages:shadowRunIsDiagnosticOnly')}
                tooltip={t('pages:diagnosticonlyTrueLocalShadowRunDiagnosticFactsOnlyValue1', {
                    value1: pending,
                })}
            />
            <BoundaryBadge
                color="error"
                label={t('pages:notTradingAuthorization')}
                tooltip={t(
                    'pages:nottradingauthorizationTrueApprovedOrConsistentDoNotGrantTradingAuthorizationValue1',
                    {value1: pending},
                )}
            />
            <BoundaryBadge
                label={t('pages:aiDhRuntimeNotIntegrated')}
                tooltip={t(
                    'pages:aidhruntimeintegratedFalseNeitherAiStartedNorDhIntegratedValue1',
                    {value1: pending},
                )}
            />
        </Space>
    );
}

function OverviewMetricStrip({
    overview,
    loading,
}: {
    overview?: ShadowRunOverviewResponse;
    loading: boolean;
}) {
    useTranslation('pages');
    return (
        <Space size={[12, 12]} wrap>
            <NqMetricCard
                label={t('pages:totalRuns')}
                value={countValue(overview?.totalRuns)}
                loading={loading}
                footer={t('pages:readOnlyLocalFacts')}
            />
            <NqMetricCard
                label={t('pages:runningRuns')}
                value={countValue(overview?.runningRuns)}
                loading={loading}
            />
            <NqMetricCard
                label={t('pages:blockedRuns')}
                value={countValue(overview?.blockedRuns)}
                loading={loading}
                tone={countValue(overview?.blockedRuns) > 0 ? 'warning' : 'default'}
            />
            <NqMetricCard
                label={t('pages:failedRuns')}
                value={countValue(overview?.failedRuns)}
                loading={loading}
                tone={countValue(overview?.failedRuns) > 0 ? 'danger' : 'default'}
            />
            <NqMetricCard
                label={t('pages:completedRuns')}
                value={countValue(overview?.completedRuns)}
                loading={loading}
            />
            <NqMetricCard
                label={t('pages:staleRuns')}
                value={countValue(overview?.staleRuns)}
                loading={loading}
                tone={countValue(overview?.staleRuns) > 0 ? 'warning' : 'default'}
            />
        </Space>
    );
}

function OverviewMessageList({
    title,
    items,
    emptyText,
}: {
    title: string;
    items: OverviewMessage[];
    emptyText: string;
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
                                <StatusTag
                                    title=""
                                    variant="pill"
                                    status={safeText(item.severity)}
                                    tone={statusTone(item.severity)}
                                />
                                {codeText(item.code)}
                                <Text type="secondary">{safeText(item.sourceType)}</Text>
                                {item.sourceId ? codeText(item.sourceId) : null}
                            </Space>
                            <Text>{safeText(item.message)}</Text>
                        </Space>
                    </List.Item>
                )}
            />
            {items.length > 4 ? (
                <Text type="secondary">
                    {t('pages:additional')}
                    {items.length - 4} {t('pages:itemsSummaryViewOnly')}
                </Text>
            ) : null}
        </section>
    );
}

function OverviewNextSteps({items}: {items: ShadowRunOverviewNextStep[]}) {
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
                            <Text type="secondary">
                                expectedEvidence: {safeText(item.expectedEvidence)}
                            </Text>
                        </Space>
                    </List.Item>
                )}
            />
            {items.length > 4 ? (
                <Text type="secondary">
                    {t('pages:additional')}
                    {items.length - 4} {t('pages:itemsSummaryViewOnly')}
                </Text>
            ) : null}
        </section>
    );
}

function OverviewEvidenceSummary({overview}: {overview: ShadowRunOverviewResponse}) {
    useTranslation('pages');
    const anchors = overview.evidenceAnchors.slice(0, 4);
    return (
        <Space direction="vertical" size={8} style={{display: 'flex'}}>
            <Space size={[12, 8]} wrap>
                <Text>generatedAt: {formatDateTime(overview.generatedAt)}</Text>
                <Text>traceId: {codeText(overview.traceId)}</Text>
            </Space>
            {anchors.length === 0 ? (
                <Text type="secondary">
                    {t('pages:noEvidenceAnchorsEvidenceMustNotBeFabricated')}
                </Text>
            ) : (
                <List<ShadowRunOverviewEvidenceAnchor>
                    size="small"
                    dataSource={anchors}
                    renderItem={(anchor) => (
                        <List.Item>
                            <Space size={[8, 6]} wrap>
                                <Text>{safeText(anchor.sourceType)}</Text>
                                {codeText(anchor.sourceId)}
                                <Text type="secondary">
                                    version: {safeText(anchor.sourceVersion)}
                                </Text>
                                <Text type="secondary">
                                    time: {formatDateTime(anchor.sourceTimestamp)}
                                </Text>
                                <Text type="secondary">checksum: {safeText(anchor.checksum)}</Text>
                            </Space>
                        </List.Item>
                    )}
                />
            )}
            {overview.evidenceAnchors.length > 4 ? (
                <Text type="secondary">
                    {t('pages:additional')}
                    {overview.evidenceAnchors.length - 4} {t('pages:evidenceAnchors')}
                </Text>
            ) : null}
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
            extra={
                <Button icon={<ReloadOutlined />} loading={isFetching} onClick={onRetry}>
                    {t('pages:refreshOverview')}
                </Button>
            }
        >
            <Space direction="vertical" size={14} style={{display: 'flex'}}>
                <Text type="secondary">
                    {t(
                        'pages:readOnlyGetApiShadowRunsOverviewForShadowRunDiagnosticsWithoutNewRoutesDashboardV2OrWriteActions',
                    )}
                </Text>
                <OverviewBoundaryBadges overview={overview} />
                <EvidenceMetadataSummary metadata={overview?.evidenceMetadata} />
                <OverviewMetricStrip overview={overview} loading={isLoading} />

                {isLoading ? (
                    <NqLoadingState message={t('pages:loadingShadowRunOverview')} />
                ) : isError ? (
                    <NqErrorState
                        title={t('pages:failedToLoadShadowRunOverview')}
                        error={asAppApiError(error)}
                        description={t(
                            'pages:aFailedOverviewIsNotShownAsEmptyDataOrATradableState',
                        )}
                        onRetry={onRetry}
                    />
                ) : !overview ? (
                    <NqEmptyState
                        description={t(
                            'pages:noShadowRunOverviewResponseFixedSafetyBoundariesRemainFailClosed',
                        )}
                    />
                ) : empty ? (
                    <NqEmptyState
                        description={t(
                            'pages:noShadowRunsTheOverviewShowsZeroCountsAndFixedSafetyBoundariesOnly',
                        )}
                    />
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
                                value={
                                    <StatusWithHint
                                        status={overview.latestRun?.status}
                                        category="run"
                                    />
                                }
                                footer={
                                    overview.latestRun
                                        ? codeText(overview.latestRun.shadowRunId)
                                        : t('pages:noLatestRun')
                                }
                            />
                            <NqMetricCard
                                label="latestConsistency"
                                value={
                                    <StatusWithHint
                                        status={overview.latestConsistency?.comparisonStatus}
                                        category="comparison"
                                    />
                                }
                                footer={
                                    overview.latestConsistency
                                        ? codeText(overview.latestConsistency.reportId)
                                        : t('pages:noReport')
                                }
                            />
                            <NqMetricCard
                                label="divergenceSeverity"
                                value={
                                    <StatusWithHint
                                        status={overview.divergenceSeverity}
                                        category="severity"
                                    />
                                }
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
                            <OverviewNextSteps items={overview.nextSteps} />
                            <OverviewEvidenceSummary overview={overview} />
                        </Space>
                    </>
                )}
            </Space>
        </Card>
    );
}

/**
 * Shadow Run 列表与总览页面.
 *
 * Why:
 * 这是影子运行 detail / replay 页面的只读入口，并在 Shadow Run 总览增加 overview summary。
 * 页面只调用 GET overview/list/detail 链路，不提供 start / stop / execute / rerun / approve / trade 操作，
 * 不触发 runner、scheduler、credential、private endpoint 或真实交易。
 */
export function ShadowRunListPage() {
    const {t} = useTranslation('pages');
    const navigate = useNavigate();
    const location = useLocation();
    const [search, setSearch] = useSearchParams();
    const compact = Grid.useBreakpoint().xs === true;
    const {status: statusFilter, page, error: locationError} = shadowListLocation(location.search);
    const queryParams = useMemo<ShadowRunListRequest>(
        () => ({
            status: statusFilter === 'ALL' ? undefined : statusFilter,
            limit: SHADOW_LIST_PAGE_SIZE,
            offset: (page - 1) * SHADOW_LIST_PAGE_SIZE,
        }),
        [statusFilter, page],
    );
    const overviewQuery = useShadowRunOverview();
    const listQuery = useShadowRunListQuery(queryParams, !locationError);
    const rows = locationError ? [] : (listQuery.data?.items ?? []);
    // 正式读取成功但尚无诊断事实时，UNAVAILABLE表示没有证据；原始元数据仍可展开，不误报为接口失败。
    const noEvidence =
        overviewQuery.data?.totalRuns === 0 &&
        overviewQuery.data.latestRun === null &&
        overviewQuery.data.latestConsistency === null &&
        overviewQuery.data.evidenceMetadata?.source === 'LOCAL_DB_SHADOW_FACTS' &&
        overviewQuery.data.evidenceMetadata.availability === 'UNAVAILABLE' &&
        overviewQuery.data.evidenceMetadata.lastCalculatedAt === null &&
        overviewQuery.data.evidenceMetadata.staleReason === 'LAST_CALCULATED_AT_MISSING';

    const changeQuery = (status: string, nextPage: number) => {
        const next = new URLSearchParams(search);
        if (status === 'ALL') next.delete('status');
        else next.set('status', status);
        if (nextPage === 1) next.delete('page');
        else next.set('page', String(nextPage));
        setSearch(next);
    };
    const openRecord = useCallback(
        (record: ShadowRunListItemResponse) => {
            const returned = {search: location.search, scrollY: window.scrollY, focusId: record.id};
            // 同时保存列表历史项和详情返回状态，显式返回与浏览器后退都保持原筛选、页码与焦点。
            navigate(location.pathname + location.search, {
                replace: true,
                state: {shadowListReturn: returned},
            });
            navigate(`${SHADOW_LIST_PATH}/${record.id}`, {state: {shadowList: returned}});
        },
        [navigate, location.pathname, location.search],
    );

    useEffect(() => {
        const returned = shadowListReturn(location.state?.shadowListReturn);
        if (!returned || listQuery.isFetching || !listQuery.isSuccess || locationError) return;
        const frame = requestAnimationFrame(() => {
            document
                .getElementById(`shadow-run-view-${returned.focusId}`)
                ?.focus({preventScroll: true});
            window.scrollTo({top: returned.scrollY});
        });
        return () => cancelAnimationFrame(frame);
    }, [location.state, listQuery.isFetching, listQuery.isSuccess, locationError]);

    const stateTag = (record: ShadowRunListItemResponse) => (
        <StatusTag
            title=""
            variant="pill"
            status={safeText(record.status)}
            label={t(`pages:shadowList.states.${record.status}`, {
                defaultValue: safeText(record.status),
            })}
            tone={statusTone(record.status)}
        />
    );
    const technical = (record: ShadowRunListItemResponse) => (
        <details className="shadow-list-technical">
            <summary>{t('pages:shadowList.sourceDetails')}</summary>
            <Descriptions size="small" column={1}>
                <Descriptions.Item label={t('pages:shadowList.rawStatus')}>
                    {codeText(record.status)}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:datasetId')}>
                    {codeText(record.datasetId)}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:paperRunId')}>
                    {codeText(record.paperRunId)}
                </Descriptions.Item>
                <Descriptions.Item label="traceId">{codeText(record.traceId)}</Descriptions.Item>
                <Descriptions.Item label={t('pages:shadowList.boundary')}>
                    {codeText(record.authorizationBoundary)}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:shadowList.updatedAt')}>
                    {formatDateTime(record.updatedAt)}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:shadowList.startedAt')}>
                    {formatDateTime(record.startedAt)}
                </Descriptions.Item>
                <Descriptions.Item label={t('pages:shadowList.completedAt')}>
                    {formatDateTime(record.completedAt)}
                </Descriptions.Item>
            </Descriptions>
            <BoundaryTags record={record} />
        </details>
    );
    const reviewCounts = (record: ShadowRunListItemResponse) => (
        <Space size={[4, 4]} wrap>
            <Tag color={record.blockersCount > 0 ? 'error' : 'default'}>
                {t('pages:shadowList.blockers', {count: record.blockersCount})}
            </Tag>
            <Tag color={record.warningsCount > 0 ? 'warning' : 'default'}>
                {t('pages:shadowList.warnings', {count: record.warningsCount})}
            </Tag>
            <Tag>{t('pages:shadowList.nextSteps', {count: record.nextStepsCount})}</Tag>
        </Space>
    );
    const viewButton = (record: ShadowRunListItemResponse) => (
        <Button
            type="link"
            id={`shadow-run-view-${record.id}`}
            icon={<EyeOutlined />}
            onClick={(event) => {
                event.stopPropagation();
                openRecord(record);
            }}
        >
            {t('pages:shadowList.view')}
        </Button>
    );
    const columns: ColumnsType<ShadowRunListItemResponse> = [
        {
            title: t('pages:shadowList.record'),
            dataIndex: 'id',
            width: 280,
            fixed: 'left',
            render: (_, record) => (
                <div className="shadow-list-record">
                    {codeText(record.id)}
                    {stateTag(record)}
                    {technical(record)}
                </div>
            ),
        },
        {
            title: t('pages:strategyVersion'),
            dataIndex: 'strategyVersionId',
            width: 210,
            render: codeText,
        },
        {
            title: t('pages:shadowList.review'),
            width: 240,
            render: (_, record) => reviewCounts(record),
        },
        {
            title: t('pages:createdAt'),
            dataIndex: 'createdAt',
            width: 190,
            render: (value: string) => formatDateTime(value),
        },
        {
            title: t('pages:shadowList.action'),
            width: 120,
            fixed: 'right',
            render: (_, record) => viewButton(record),
        },
    ];
    const metric = (key: 'totalRuns' | 'runningRuns' | 'blockedRuns' | 'failedRuns') => {
        const value = overviewQuery.isError ? undefined : overviewQuery.data?.[key];
        return typeof value === 'number' && Number.isFinite(value) ? value : t('pages:notProvided');
    };

    return (
        <Space
            className="shadow-run-list-page"
            data-testid="shadow-run-list-page"
            direction="vertical"
            size={16}
        >
            <NqPageHeader
                title={t('pages:shadowRuns')}
                description={t('pages:shadowList.intro')}
                badge={t('pages:diagnosticsOnlyNoTradingAuthorization')}
                extra={
                    <Button
                        icon={<ReloadOutlined />}
                        loading={listQuery.isFetching}
                        disabled={Boolean(locationError)}
                        onClick={() => void listQuery.refetch()}
                    >
                        {t('pages:refreshReadOnlyList')}
                    </Button>
                }
            />
            <div className="shadow-list-metrics">
                <NqMetricCard label={t('pages:totalRuns')} value={metric('totalRuns')} />
                <NqMetricCard label={t('pages:runningRuns')} value={metric('runningRuns')} />
                <NqMetricCard label={t('pages:blockedRuns')} value={metric('blockedRuns')} />
                <NqMetricCard label={t('pages:failedRuns')} value={metric('failedRuns')} />
            </div>
            <DataFreshness
                source={t('pages:shadowList.source')}
                state={
                    overviewQuery.isError
                        ? 'error'
                        : noEvidence
                          ? 'no_data'
                          : evidenceFreshnessState(overviewQuery.data?.evidenceMetadata)
                }
                detail={noEvidence ? t('pages:shadowList.noEvidence') : undefined}
            />
            {overviewQuery.data &&
            (hasDivergence(overviewQuery.data) ||
                hasStaleEvidence(overviewQuery.data) ||
                overviewQuery.data.failedRuns > 0 ||
                overviewQuery.data.blockedRuns > 0) ? (
                <ApplicationRiskAlert
                    level={resolveOverviewState(overviewQuery.data).level}
                    message={resolveOverviewState(overviewQuery.data).message}
                />
            ) : null}
            <Card size="small">
                <div className="shadow-list-filter" data-testid="shadow-run-status-filter">
                    <label htmlFor="shadow-list-status">{t('pages:shadowList.status')}</label>
                    <Select
                        id="shadow-list-status"
                        // 状态枚举数量固定且有界，键盘与读屏可访问全部选项，不依赖当前虚拟窗口。
                        virtual={false}
                        value={statusFilter}
                        options={SHADOW_RUN_STATUSES.map((value) => ({
                            value,
                            label: t(`pages:shadowList.states.${value}`),
                        }))}
                        onChange={(value: string) => changeQuery(value, 1)}
                    />
                    <Text type="secondary">
                        {!locationError && !listQuery.isError && listQuery.data
                            ? t('pages:shadowList.window', {
                                  shown: rows.length,
                                  total: listQuery.data.total,
                              })
                            : t('pages:notProvided')}
                    </Text>
                </div>
            </Card>
            {locationError ? (
                <Alert
                    type="error"
                    showIcon
                    message={t('pages:shadowList.invalidQuery')}
                    action={
                        <Button onClick={() => changeQuery('ALL', 1)}>
                            {t('pages:shadowList.reset')}
                        </Button>
                    }
                />
            ) : null}
            {!locationError && listQuery.isError ? (
                <NqErrorState
                    title={t('pages:failedToLoadShadowRuns')}
                    error={asAppApiError(listQuery.error)}
                    onRetry={() => void listQuery.refetch()}
                />
            ) : null}
            {!locationError && listQuery.isLoading ? (
                <NqLoadingState message={t('pages:loadingShadowRuns')} />
            ) : null}
            {!locationError && !listQuery.isLoading && !listQuery.isError && rows.length === 0 ? (
                <Card size="small">
                    <NqEmptyState description={t('pages:shadowList.empty')} />
                </Card>
            ) : null}
            {!locationError && rows.length > 0 ? (
                <Card size="small" title={t('pages:shadowRunFacts')}>
                    {compact ? (
                        <List
                            dataSource={rows}
                            split={false}
                            renderItem={(record) => (
                                <List.Item>
                                    <div className="shadow-list-record" style={{width: '100%'}}>
                                        <div
                                            className="shadow-list-mobile-header"
                                            style={{width: '100%'}}
                                        >
                                            {stateTag(record)}
                                            {viewButton(record)}
                                        </div>
                                        {codeText(record.id)}
                                        <Text>
                                            {t('pages:strategyVersion')}：
                                            {codeText(record.strategyVersionId)}
                                        </Text>
                                        {reviewCounts(record)}
                                        <Text type="secondary">
                                            {t('pages:createdAt')}：
                                            {formatDateTime(record.createdAt)}
                                        </Text>
                                        {technical(record)}
                                    </div>
                                </List.Item>
                            )}
                        />
                    ) : (
                        <Table<ShadowRunListItemResponse>
                            size="small"
                            rowKey="id"
                            columns={columns}
                            dataSource={rows}
                            pagination={false}
                            scroll={{x: 1040}}
                            onRow={(record) => ({
                                style: {cursor: 'pointer'},
                                onClick: (event) => {
                                    // 展开来源和复制标识属于行内操作，不能因冒泡误跳详情。
                                    if (
                                        (event.target as HTMLElement).closest(
                                            'button,a,summary,details,.ant-typography-copy',
                                        )
                                    )
                                        return;
                                    openRecord(record);
                                },
                            })}
                        />
                    )}
                </Card>
            ) : null}
            {!locationError && listQuery.data && listQuery.data.total > 0 ? (
                <div className="shadow-list-pagination">
                    <Pagination
                        current={page}
                        pageSize={SHADOW_LIST_PAGE_SIZE}
                        total={listQuery.data.total}
                        showSizeChanger={false}
                        responsive
                        disabled={listQuery.isFetching}
                        onChange={(nextPage) => changeQuery(statusFilter, nextPage)}
                    />
                </div>
            ) : null}
            <details className="shadow-list-overview">
                <summary>{t('pages:shadowList.overviewDetails')}</summary>
                <ShadowRunOverviewSummary
                    overview={overviewQuery.data}
                    isLoading={overviewQuery.isLoading}
                    isFetching={overviewQuery.isFetching}
                    isError={overviewQuery.isError}
                    error={overviewQuery.error}
                    onRetry={() => void overviewQuery.refetch()}
                />
            </details>
        </Space>
    );
}

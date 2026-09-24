import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {
    Alert,
    Button,
    Card,
    Col,
    List,
    Row,
    Space,
    Table,
    Tag,
    Typography,
} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {operationalReadinessApi} from '@/features/runtime/api/operational-readiness';
import {operationalReadinessQueryKeys} from '@/api/query-keys';
import {NqMetricCard, ApplicationRiskAlert} from '@/components/nq';
import {PageHero} from '@/components/page/PageHero';
import {useAdapterReadinessQuery} from '@/hooks/useAdapterReadinessQuery';
import {DataFreshness, StatusTag, type StatusTone} from '@/nq-design-system';
import type {AdapterReadinessItem} from '@/types/adapter-readiness';
import type {AppApiError} from '@/types/api';
import type {
    OperationalReadinessResponse,
    OperationalReadinessStatusResponse,
} from '@/features/runtime/types/operational-readiness';
import {formatDateTime} from '@/utils/formatters';

const {Paragraph, Text} = Typography;

const REAL_EXCHANGE_VENUES = new Set(['OKX', 'BINANCE']);
const NO_REAL_VENUES = new Set(['NOOP', 'PAPER', 'SIM', 'FAKE', 'STUB', 'FUTURE_REAL']);
const MARKETDATA_READINESS_PATH = `/marketdata?${new URLSearchParams({
    exchangeCode: 'BINANCE',
    marketType: 'SPOT',
    symbol: 'BTC-USDT',
    interval: '1m',
}).toString()}`;
const DASHBOARD_RUNTIME_SUMMARY_PATH = '/dashboard';
const BLOCKED_RUNTIME_REASONS = [
    'NO_REAL',
    'FAKE',
    'STUB',
    'FUTURE_REAL_DISABLED',
    'LIVE_NOT_AUTHORIZED',
    'PERMISSION_PROBE_DISABLED',
    'CREDENTIAL_UNCONFIGURED',
    'PENDING_BACKEND_SUPPORT',
];

interface RuntimeBlocker {
    key: string;
    area: string;
    status: string;
    source: string;
    impact: string;
    tone: StatusTone;
}

interface RuntimeReleaseMatrixRow {
    key: string;
    area: string;
    status: string;
    meaning: string;
    boundary: string;
    source: string;
}

interface OperationalReadinessItem {
    key: string;
    area: string;
    status: string;
    source: string;
    reasonCode: string;
    reason: string;
    safeState: string;
    tone: StatusTone;
}

interface FakeDryRunOperationsRow {
    key: string;
    area: string;
    status: string;
    detail: string;
    tone: StatusTone;
}

interface VenueSummary {
    venue: string;
    capabilities: number;
    allowed: number;
    liveAuthorized: number;
    statuses: string[];
    reasons: string[];
    tone: StatusTone;
}

type OperationalReadinessStatusKey = Exclude<
    keyof OperationalReadinessResponse,
    'generatedAt' | 'fakeDryRunOperations'
>;

function uniqueSorted(values: string[]): string[] {
    return [...new Set(values.filter(Boolean))].sort((left, right) => left.localeCompare(right));
}

function statusTone(status: string): StatusTone {
    switch (status) {
        case 'DISABLED':
        case 'UNAVAILABLE':
        case 'NO_REAL':
        case 'READY_FOR_PAPER_ONLY':
            return status === 'READY_FOR_PAPER_ONLY' ? 'info' : 'warning';
        case 'NOT_READY':
        case 'NOT_STARTED':
        case 'NOT_INTEGRATED':
        case 'NOT_IMPLEMENTED':
        case 'LIVE_NOT_AUTHORIZED':
        case 'UNKNOWN_REQUIRES_REVIEW':
            return 'danger';
        case 'NOT_EXPOSED':
        case 'SAFE_BY_DEFAULT':
        case 'SAFE_SUMMARY_ONLY':
        case 'DIAGNOSTIC_ONLY':
        case 'READ_ONLY':
            return 'info';
        case 'DISABLED_SENTINEL':
        case 'CREDENTIAL_UNCONFIGURED':
        case 'CAPABILITY_NOT_IMPLEMENTED':
        case 'PENDING_BACKEND_SUPPORT':
        case 'FUTURE_REAL_DISABLED':
        case 'PERMISSION_PROBE_DISABLED':
            return 'warning';
        case 'SKIPPED':
            return 'neutral';
        case 'READY':
            // 当前 GateM baseline 不允许 READY 被解释为真实可交易，按高风险信号展示。
            return 'danger';
        default:
            return status.includes('READY') ? 'danger' : 'neutral';
    }
}

const OPERATIONAL_STATUS_FIELDS: Array<{
    key: OperationalReadinessStatusKey;
    area: string;
    source: string;
}> = [
    {key: 'liveStatus', get area() { return t('pages:liveStatus'); }, source: 'GET /api/runtime/operational-readiness'},
    {key: 'aiStatus', get area() { return t('pages:aiStatus'); }, source: 'GET /api/runtime/operational-readiness'},
    {key: 'dhRuntimeStatus', get area() { return t('pages:dhRuntimeStatus'); }, source: 'GET /api/runtime/operational-readiness'},
    {key: 'realProviderStatus', get area() { return t('pages:realProviderStatus'); }, source: 'GET /api/runtime/operational-readiness'},
    {
        key: 'credentialExposureStatus',
        get area() { return t('pages:credentialExposureStatus'); },
        source: 'GET /api/runtime/operational-readiness'
    },
    {
        key: 'externalExchangeCallStatus',
        get area() { return t('pages:externalExchangeCallStatus'); },
        source: 'GET /api/runtime/operational-readiness'
    },
    {key: 'permissionProbeStatus', get area() { return t('pages:permissionProbeStatus'); }, source: 'GET /api/runtime/operational-readiness'},
    {key: 'startupBoundaryStatus', get area() { return t('pages:startupBoundaryStatus'); }, source: 'GET /api/runtime/operational-readiness'},
    {key: 'profileBoundaryStatus', get area() { return t('pages:profileBoundaryStatus'); }, source: 'GET /api/runtime/operational-readiness'},
    {
        key: 'configDiagnosticsStatus',
        get area() { return t('pages:configurationDiagnosticsStatus'); },
        source: 'GET /api/runtime/operational-readiness'
    },
    {key: 'logDiagnosticsStatus', get area() { return t('pages:logDiagnosticsStatus'); }, source: 'GET /api/runtime/operational-readiness'},
];

function isOperationalReadinessStatusResponse(value: unknown): value is OperationalReadinessStatusResponse {
    if (!value || typeof value !== 'object') {
        return false;
    }

    const candidate = value as Record<string, unknown>;
    return typeof candidate.status === 'string'
        && typeof candidate.ready === 'boolean'
        && typeof candidate.reasonCode === 'string'
        && typeof candidate.reason === 'string';
}

function isOperationalReadinessResponse(value: unknown): value is OperationalReadinessResponse {
    if (!value || typeof value !== 'object') {
        return false;
    }

    const candidate = value as Partial<Record<keyof OperationalReadinessResponse, unknown>>;
    return typeof candidate.generatedAt === 'string'
        && OPERATIONAL_STATUS_FIELDS.every(({key}) => isOperationalReadinessStatusResponse(candidate[key]));
}

function unavailableOperationalStatus(): OperationalReadinessStatusResponse {
    return {
        status: 'UNAVAILABLE',
        ready: false,
        reasonCode: 'PENDING_BACKEND_SUPPORT',
        reason: t('pages:operationalReadinessIsUnavailableTheRuntimeUiRemainsFailClosedWithNoCapabilityTreatedAsAvailable'),
    };
}

function buildOperationalReadinessItems(summary?: OperationalReadinessResponse): OperationalReadinessItem[] {
    const unavailable = unavailableOperationalStatus();

    return OPERATIONAL_STATUS_FIELDS.map(({key, area, source}) => {
        const status = summary?.[key] ?? unavailable;
        const normalizedStatus = status.ready ? 'REVIEW_REQUIRED' : status.status;

        return {
            key: String(key),
            area,
            status: normalizedStatus,
            source: summary ? source : t('pages:operationalReadinessSummaryUnavailable'),
            reasonCode: status.reasonCode || 'PENDING_BACKEND_SUPPORT',
            reason: status.reason || unavailable.reason,
            safeState: status.ready ? 'MANUAL_REVIEW_REQUIRED' : 'BLOCKED',
            tone: status.ready ? 'danger' : statusTone(status.status),
        };
    });
}

function buildVenueSummaries(items: AdapterReadinessItem[]): VenueSummary[] {
    return uniqueSorted(items.map((item) => item.venue)).map((venue) => {
        const rows = items.filter((item) => item.venue === venue);
        const allowed = rows.filter((item) => item.allowed).length;
        const liveAuthorized = rows.filter((item) => item.liveAuthorized).length;
        const statuses = uniqueSorted(rows.map((item) => item.status));
        const reasons = uniqueSorted(rows.flatMap((item) => item.reasons ?? []));
        const isUnexpectedReady = allowed > 0 || liveAuthorized > 0 || statuses.includes('READY');
        const isRealExchange = REAL_EXCHANGE_VENUES.has(venue);

        return {
            venue,
            capabilities: rows.length,
            allowed,
            liveAuthorized,
            statuses,
            reasons,
            tone: isUnexpectedReady || isRealExchange ? 'danger' : 'info',
        };
    });
}

function buildRuntimeBlockers(items: AdapterReadinessItem[]): RuntimeBlocker[] {
    const permissionRows = items.filter((item) => item.capability === 'PERMISSION_PROBE');
    const noRealRows = items.filter((item) => (
        item.status === 'NO_REAL'
        || NO_REAL_VENUES.has(item.venue)
        || (item.reasons ?? []).includes('NO_REAL_DISABLED')
    ));
    const realExchangeRows = items.filter((item) => REAL_EXCHANGE_VENUES.has(item.venue));

    return [
        {
            key: 'live-disabled',
            area: t('pages:liveDisabled3'),
            status: 'LIVE_NOT_AUTHORIZED',
            source: t('pages:gatemRuntimeBoundary'),
            impact: t('pages:noLiveEntryIsProvidedRealOrdersCancellationsTransfersAndWithdrawalsAreProhibited'),
            tone: 'danger',
        },
        {
            key: 'adapter-no-real',
            area: t('pages:adapterNoReal'),
            status: noRealRows.length > 0 ? 'NO_REAL' : 'PENDING_BACKEND_SUPPORT',
            source: 'GET /api/adapters/readiness',
            impact: t('pages:norealFakeStubFuturerealRemainBlockedNoRealRowsValue1', {value1: noRealRows.length}),
            tone: noRealRows.length > 0 ? 'info' : 'warning',
        },
        {
            key: 'real-exchange',
            area: t('pages:okxBinanceRuntime'),
            status: realExchangeRows.length > 0 ? 'NOT_READY' : 'PENDING_BACKEND_SUPPORT',
            source: 'GET /api/adapters/readiness',
            impact: t('pages:realExchangeAdaptersRealclientAndRealProvidersAreNotImplementedAndCannotServeAsCanonicalProviders'),
            tone: 'danger',
        },
        {
            key: 'permission-probe',
            area: t('pages:permissionProbe'),
            status: permissionRows.length > 0 ? 'PERMISSION_PROBE_DISABLED / SKIPPED' : 'PENDING_BACKEND_SUPPORT',
            source: t('pages:adapterReadinessPermissionProbeRow'),
            impact: t('pages:onlyDisabledSkippedStatesAreDisplayedNoPermissionProbePostOrCredentialReadsOccur'),
            tone: permissionRows.length > 0 ? 'warning' : 'danger',
        },
        {
            key: 'runtime-flags',
            area: t('pages:centralRuntimeFlags'),
            status: 'PENDING_BACKEND_SUPPORT',
            source: t('pages:noAggregateRuntimeFlagsApi'),
            impact: t('pages:theFrontendCannotInferLiveReadinessFromMissingApisUnifiedRuntimeFlagsAwaitBackendAggregation'),
            tone: 'warning',
        },
        {
            key: 'paper-to-real-aggregate',
            area: t('pages:paperToRealAggregate'),
            status: 'PENDING_BACKEND_SUPPORT',
            source: t('pages:noPaperToRealAggregateApi'),
            impact: t('pages:paperReadinessAndRealTradingAuthorizationRemainSeparateNoCrossEnvironmentAggregatePassIsOffered'),
            tone: 'warning',
        },
    ];
}

function isReadinessSignalUnexpected(item: AdapterReadinessItem): boolean {
    return item.status === 'READY' || item.allowed || item.liveAuthorized;
}

const venueColumns: ColumnsType<VenueSummary> = [
    {
        get title() { return t('pages:venue'); },
        dataIndex: 'venue',
        key: 'venue',
        width: 140,
        render: (venue: string, row) => (
            <Space size={8}>
                <StatusTag label={venue} tone={row.tone} variant="pill"/>
                {REAL_EXCHANGE_VENUES.has(venue) ? <Tag color="error">{t('pages:notAuthorized')}</Tag> : null}
            </Space>
        ),
    },
    {
        get title() { return t('pages:capabilities'); },
        dataIndex: 'capabilities',
        key: 'capabilities',
        width: 120,
    },
    {
        get title() { return t('pages:allowed2'); },
        dataIndex: 'allowed',
        key: 'allowed',
        width: 110,
        render: (value: number) => (
            <Tag color={value > 0 ? 'error' : 'default'}>{value}</Tag>
        ),
    },
    {
        get title() { return t('pages:liveAuthorizationCount'); },
        dataIndex: 'liveAuthorized',
        key: 'liveAuthorized',
        width: 150,
        render: (value: number) => (
            <Tag color={value > 0 ? 'error' : 'default'}>{value}</Tag>
        ),
    },
    {
        get title() { return t('pages:statuses'); },
        dataIndex: 'statuses',
        key: 'statuses',
        render: (statuses: string[]) => (
            <Space size={[4, 4]} wrap>
                {statuses.map((status) => (
                    <StatusTag key={status} label={status} tone={statusTone(status)} variant="pill"/>
                ))}
            </Space>
        ),
    },
    {
        get title() { return t('pages:reasons2'); },
        dataIndex: 'reasons',
        key: 'reasons',
        render: (reasons: string[]) => (
            <Space size={[4, 4]} wrap>
                {reasons.slice(0, 5).map((reason) => (
                    <Tag key={reason} color={BLOCKED_RUNTIME_REASONS.includes(reason) ? 'warning' : 'default'}>
                        {reason}
                    </Tag>
                ))}
                {reasons.length > 5 ? <Tag>+{reasons.length - 5}</Tag> : null}
            </Space>
        ),
    },
];

const blockerColumns: ColumnsType<RuntimeBlocker> = [
    {
        get title() { return t('pages:runtimeBlocker'); },
        dataIndex: 'area',
        key: 'area',
        width: 210,
        render: (area: string, row) => (
            <Space direction="vertical" size={2}>
                <Text strong>{area}</Text>
                <Text type="secondary">{row.source}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 220,
        render: (status: string, row) => (
            <StatusTag label={status} tone={row.tone} variant="pill"/>
        ),
    },
    {
        get title() { return t('pages:impact'); },
        dataIndex: 'impact',
        key: 'impact',
    },
];

/**
 * Runtime release matrix 是 GateP Batch 3 的前端静态放行解释层。
 *
 * Why:
 * 当前没有任何 API 能把 Data Quality、public marketdata、permission probe、private trading、LIVE、AI、
 * DH runtime 合并为“可交易”结论，因此矩阵必须显式 fail-closed：只展示诊断/只读/未实现/未启动状态，
 * 不派生 tradingReady、liveReady 或 authorization 文案。
 */
const runtimeReleaseMatrixRows: RuntimeReleaseMatrixRow[] = [
    {
        key: 'data-quality',
        get area() { return t('pages:dataQuality'); },
        status: 'DIAGNOSTIC_ONLY',
        get meaning() { return t('pages:marketDataDiagnosticsAndGapVisibilityOnly'); },
        get boundary() { return t('pages:passingDataQualityDoesNotGrantTradingAuthorization'); },
        source: 'GET /api/marketdata/quality/overview',
    },
    {
        key: 'public-marketdata',
        get area() { return t('pages:publicMarketData'); },
        status: 'READ_ONLY',
        get meaning() { return t('pages:publicMarketDataIsReadOnlyItDoesNotEstablishPrivateEndpointSignedRequestOrProviderTradingReadiness'); },
        get boundary() { return t('pages:publicMarketDataReadinessDoesNotMeanLiveAvailability'); },
        get source() { return t('pages:readOnlyMarketDataApis'); },
    },
    {
        key: 'permission-probe',
        get area() { return t('pages:permissionProbe'); },
        status: 'NOT_IMPLEMENTED / READ_ONLY_STATUS',
        get meaning() { return t('pages:onlyUnimplementedDisabledOrSkippedStatesAreShownThisPageDoesNotPerformProbePostRequests'); },
        get boundary() { return t('pages:skippedDisabledDoesNotMeanPermissionVerificationPassed'); },
        get source() { return t('pages:operationalAdapterReadinessSummary'); },
    },
    {
        key: 'private-trading',
        get area() { return t('pages:privateTrading'); },
        status: 'NOT_IMPLEMENTED',
        get meaning() { return t('pages:realOrdersCancellationsTransfersWithdrawalsAndPrivateTradingAdaptersAreNotImplemented'); },
        get boundary() { return t('pages:noWriteEndpointOrTradingAuthorizationEntryIsProvided'); },
        get source() { return t('pages:gatepBoundary'); },
    },
    {
        key: 'live',
        area: 'LIVE',
        status: 'DISABLED',
        get meaning() { return t('pages:liveIsDisabled'); },
        get boundary() { return t('pages:realLiveOrdersAndFundOperationsAreProhibited'); },
        get source() { return t('pages:currentFactSource'); },
    },
    {
        key: 'ai',
        area: 'AI',
        status: 'NOT_STARTED',
        get meaning() { return t('pages:aiRuntimeSignalsAndAutomatedTradingHaveNotStarted'); },
        get boundary() { return t('pages:dataQualityAndRuntimeStatesAreNotConvertedIntoAiAdviceOrTradingActions'); },
        get source() { return t('pages:currentFactSource'); },
    },
    {
        key: 'dh-runtime',
        get area() { return t('pages:dhRuntime'); },
        status: 'NOT_INTEGRATED',
        get meaning() { return t('pages:dhRuntimeIsNotIntegratedIntoNq'); },
        get boundary() { return t('pages:dhCannotStartPaperRunsModifyNqTradingStateOrAccessCredentials'); },
        get source() { return t('pages:currentFactSource'); },
    },
];

const runtimeReleaseMatrixColumns: ColumnsType<RuntimeReleaseMatrixRow> = [
    {
        get title() { return t('pages:matrixItem'); },
        dataIndex: 'area',
        key: 'area',
        width: 190,
        render: (area: string, row) => (
            <Space direction="vertical" size={2}>
                <Text strong>{area}</Text>
                <Text type="secondary">{row.source}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 240,
        render: (status: string) => <StatusTag label={status} tone={statusTone(status)} variant="pill"/>,
    },
    {
        get title() { return t('pages:meaning'); },
        dataIndex: 'meaning',
        key: 'meaning',
    },
    {
        get title() { return t('pages:boundary'); },
        dataIndex: 'boundary',
        key: 'boundary',
    },
];

const operationalColumns: ColumnsType<OperationalReadinessItem> = [
    {
        get title() { return t('pages:operationalArea'); },
        dataIndex: 'area',
        key: 'area',
        width: 210,
        render: (area: string, row) => (
            <Space direction="vertical" size={2}>
                <Text strong>{area}</Text>
                <Text type="secondary">{row.source}</Text>
            </Space>
        ),
    },
    {
        get title() { return t('pages:status'); },
        dataIndex: 'status',
        key: 'status',
        width: 220,
        render: (status: string, row) => (
            <StatusTag label={status} tone={row.tone} variant="pill"/>
        ),
    },
    {
        get title() { return t('pages:safeState'); },
        dataIndex: 'safeState',
        key: 'safeState',
        width: 150,
        render: (safeState: string, row) => (
            <StatusTag label={safeState} tone={row.tone} variant="pill"/>
        ),
    },
    {
        get title() { return t('pages:reasonCode'); },
        dataIndex: 'reasonCode',
        key: 'reasonCode',
        width: 230,
        render: (reasonCode: string) => <Tag color="warning">{reasonCode}</Tag>,
    },
    {
        get title() { return t('pages:safeReason'); },
        dataIndex: 'reason',
        key: 'reason',
    },
];

const fakeDryRunOperationsColumns: ColumnsType<FakeDryRunOperationsRow> = [
    {get title() { return t('pages:operationalFact'); }, dataIndex: 'area', key: 'area', width: 210},
    {
        get title() { return t('pages:status'); }, dataIndex: 'status', key: 'status', width: 230,
        render: (status: string, row) => <StatusTag label={status} tone={row.tone} variant="pill"/>,
    },
    {get title() { return t('pages:sanitizedDetail'); }, dataIndex: 'detail', key: 'detail'},
];

/**
 * RuntimeReadinessPage 是 GateM Runtime UI 5A 的只读运行边界总览。
 *
 * Why:
 * 当前没有 central runtime flags / Paper-to-Real aggregate API，页面只能复用 adapter readiness 只读快照，
 * 并把缺失聚合能力明确展示为 PENDING_BACKEND_SUPPORT。任何 API 失败、未知、READY、allowed 或 liveAuthorized
 * 信号都不能被解释成真实交易授权；本页也不调用任何 POST / write endpoint。
 */
export function RuntimeReadinessPage() {
    useTranslation('pages');
    const readinessQuery = useAdapterReadinessQuery();
    const operationalReadinessQuery = useQuery({
        queryKey: operationalReadinessQueryKeys.status(),
        queryFn: operationalReadinessApi.getReadiness,
        retry: false,
    });
    const items = readinessQuery.data?.items ?? [];
    const venueSummaries = buildVenueSummaries(items);
    const runtimeBlockers = buildRuntimeBlockers(items);
    const operationalReadinessSummary = isOperationalReadinessResponse(operationalReadinessQuery.data)
        ? operationalReadinessQuery.data
        : undefined;
    const operationalReadinessUnavailable = operationalReadinessQuery.isError
        || (operationalReadinessQuery.isSuccess && !operationalReadinessSummary);
    const operationalReadinessItems = buildOperationalReadinessItems(operationalReadinessSummary);
    const fakeOperations = operationalReadinessSummary?.fakeDryRunOperations;
    const fakeDryRunRows: FakeDryRunOperationsRow[] = fakeOperations ? [
        {key: 'mode', area: t('pages:executionMode'), status: fakeOperations.mode, detail: t('pages:disposableLocalFakeDryRunsOnlyNoProductionStartAuthorization'), tone: 'info'},
        {key: 'kill', area: t('pages:killSwitch'), status: fakeOperations.killState, detail: `observed ${formatDateTime(fakeOperations.observedAt)}`, tone: fakeOperations.killState === 'ENGAGED' ? 'danger' : 'warning'},
        {key: 'session', area: t('pages:sessionApproval'), status: fakeOperations.sessionState, detail: `${fakeOperations.sessionId} / approval=${fakeOperations.approvalState}`, tone: statusTone(fakeOperations.sessionState)},
        {key: 'risk', area: t('pages:riskBinding'), status: fakeOperations.riskDigest === '-' ? 'NOT_OBSERVED' : 'DIGEST_BOUND', detail: fakeOperations.riskDigest, tone: 'warning'},
        {key: 'worker', area: t('pages:workerHealth'), status: fakeOperations.workerHealth, detail: `worker=${fakeOperations.workerIdentity}`, tone: statusTone(fakeOperations.workerHealth)},
        {key: 'release', area: t('pages:releaseIdentity'), status: fakeOperations.releaseIdentity, detail: `digest=${fakeOperations.releaseDigest}`, tone: 'warning'},
        {key: 'intent', area: t('pages:intentReceipt'), status: fakeOperations.intentState, detail: `${fakeOperations.intentId} / receipt=${fakeOperations.receiptState}`, tone: ['UNKNOWN', 'FAILED'].includes(fakeOperations.intentState) ? 'danger' : statusTone(fakeOperations.intentState)},
    ] : [];
    const unexpectedSignals = items.filter(isReadinessSignalUnexpected);
    const permissionRows = items.filter((item) => item.capability === 'PERMISSION_PROBE');
    const noRealRows = items.filter((item) => item.status === 'NO_REAL' || NO_REAL_VENUES.has(item.venue));

    const adapterMatrixDetail = readinessQuery.isError
        ? t('pages:readinessApiUnavailable')
        : `${items.length} rows / allowed=${items.filter((item) => item.allowed).length} / liveAuthorized=${items.filter((item) => item.liveAuthorized).length}`;
    const probeStatus = permissionRows.length > 0 ? 'PERMISSION_PROBE_DISABLED / SKIPPED' : 'PENDING_BACKEND_SUPPORT';

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}} data-testid="runtime-readiness-overview">
            <Card className="page-card" variant="borderless">
                <PageHero
                    title={t('pages:runtimeReadinessOverview')}
                    description={t('pages:readOnlyGatemBoundariesPaperOnlyMarketDataReadinessNoRealAdaptersLiveDisabledPermissionProbesDisable')}
                    badge="READONLY"
                />
            </Card>

            <ApplicationRiskAlert
                level={unexpectedSignals.length > 0 || readinessQuery.isError ? 'danger' : 'warning'}
                message={unexpectedSignals.length > 0 ? t('pages:readyAllowedLiveauthorizedDetectedManualReviewRequired') : t('pages:runtimeGuardSummaryPaperOnlyFailClosed')}
                description={(
                    <span>
                        {t('pages:liveIsDisabledNorealFakeStubFuturerealDoNotProvideRealTradingCapabilitiesPermissionProbesShowDisable')}<Text code>GET /api/adapters/readiness</Text>{t('pages:andDoesNotCallPermissionProbePostIngestionTradingOrAnyWriteEndpoint')}</span>
                )}
            />

            <Card
                className="page-section"
                variant="borderless"
                title={t('pages:runtimeReleaseMatrix')}
                data-testid="runtime-release-matrix"
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:releaseMatrixRemainsFailClosed')}
                        description={t('pages:dataQualityPublicMarketDataPermissionProbesPrivateTradingLiveAiAndDhRuntimeAreSeparateCapabilitiesPa')}
                    />
                    <Table<RuntimeReleaseMatrixRow>
                        rowKey="key"
                        columns={runtimeReleaseMatrixColumns}
                        dataSource={runtimeReleaseMatrixRows}
                        pagination={false}
                        size="small"
                        scroll={{x: 980}}
                    />
                </Space>
            </Card>

            <Card
                className="page-section"
                variant="borderless"
                title={t('pages:fakeOnlyDryRunOperations')}
                data-testid="fake-dry-run-operations"
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:fakeOnlyDryRunLiveDisabled')}
                        description={t('pages:readOnlyKillSessionApprovalRiskIntentAndReceiptFactsMissingDurableWorkerOrReleaseFactsRemainNotObser')}
                    />
                    <Space size={[8, 8]} wrap>
                        <StatusTag label="LIVE DISABLED" tone="danger" variant="pill"/>
                        <StatusTag label={fakeOperations?.killState ?? 'UNKNOWN'} tone="danger" variant="pill"/>
                        <StatusTag label="tradingAuthorization=false" tone="info" variant="pill"/>
                        <StatusTag label="productionStartAuthorization=false" tone="info" variant="pill"/>
                    </Space>
                    <Table<FakeDryRunOperationsRow>
                        rowKey="key"
                        columns={fakeDryRunOperationsColumns}
                        dataSource={fakeDryRunRows}
                        pagination={false}
                        size="small"
                        scroll={{x: 900}}
                        locale={{emptyText: t('pages:operationalSnapshotUnavailableRuntimeRemainsFailClosed')}}
                    />
                </Space>
            </Card>

            <Row gutter={[16, 16]}>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label={t('pages:liveStatus')}
                        value={<StatusTag label={t('pages:liveDisabled3')} tone="danger" variant="pill"/>}
                        tone="danger"
                        footer={t('pages:noLiveUiEntryNoRealTrading')}
                    />
                </Col>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label={t('pages:paperReady')}
                        value={<StatusTag label="READY_FOR_PAPER_ONLY" tone="info" variant="pill"/>}
                        tone="default"
                        footer={t('pages:paperOnlyBoundaryNotRealAuthorization')}
                    />
                </Col>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label={t('pages:adapterNoReal')}
                        value={<StatusTag label={readinessQuery.isError ? 'UNAVAILABLE' : 'NO_REAL'}
                                          tone={readinessQuery.isError ? 'danger' : 'info'} variant="pill"/>}
                        tone={readinessQuery.isError ? 'danger' : 'default'}
                        footer={`${noRealRows.length} no-real rows from adapter readiness`}
                        loading={readinessQuery.isLoading}
                    />
                </Col>
                <Col xs={24} sm={12} xl={6}>
                    <NqMetricCard
                        label={t('pages:permissionProbe')}
                        value={<StatusTag label={probeStatus} tone={permissionRows.length > 0 ? 'warning' : 'danger'}
                                          variant="pill"/>}
                        tone="warning"
                        footer={t('pages:skippedDisabledIsNotAPassState')}
                        loading={readinessQuery.isLoading}
                    />
                </Col>
            </Row>

            <Card
                className="page-section"
                variant="borderless"
                title={t('pages:operationalReadiness')}
                data-testid="operational-readiness-overview"
                extra={(
                    <Space size={12} wrap>
                        {operationalReadinessSummary?.generatedAt ? (
                            <Text type="secondary">
                                generated {formatDateTime(operationalReadinessSummary.generatedAt)}
                            </Text>
                        ) : null}
                        <Button
                            onClick={() => operationalReadinessQuery.refetch()}
                            loading={operationalReadinessQuery.isFetching}
                        >
                            {t('pages:refreshOperationalSummary')}</Button>
                        <Link to={MARKETDATA_READINESS_PATH}>{t('pages:viewMarketDataReadiness')}</Link>
                        <Link to={DASHBOARD_RUNTIME_SUMMARY_PATH}>{t('pages:viewDashboardRuntimeSummary')}</Link>
                    </Space>
                )}
            >
                <Space direction="vertical" size={12} style={{display: 'flex'}}>
                    <Alert
                        type={operationalReadinessUnavailable ? 'error' : 'warning'}
                        showIcon
                        message={operationalReadinessUnavailable
                            ? t('pages:operationalReadinessSummaryUnavailable')
                            : t('pages:operationalReadinessSummaryIsFailClosed')}
                        description={operationalReadinessUnavailable
                            ? t('pages:unavailablePendingBackendSupportTheSafeBackendSummaryIsUnavailableOrIncompleteNoCapabilitiesAreShown')
                            : t('pages:actuatorHealthIsProcessHealthOnlyNotLiveAuthorizationRuntimeUiDoesNotEstablishRealProviderReadinessP')}
                    />
                    <Table<OperationalReadinessItem>
                        rowKey="key"
                        columns={operationalColumns}
                        dataSource={operationalReadinessItems}
                        loading={operationalReadinessQuery.isLoading || operationalReadinessQuery.isFetching}
                        pagination={false}
                        size="small"
                        scroll={{x: 1180}}
                    />
                </Space>
            </Card>

            <Row gutter={[16, 16]}>
                <Col xs={24} xl={14}>
                    <Card
                        className="page-section"
                        variant="borderless"
                        title={t('pages:adapterReadinessMatrixSummary')}
                        extra={(
                            <Space size={12}>
                                {readinessQuery.data?.generatedAt ? (
                                    <Text
                                        type="secondary">generated {formatDateTime(readinessQuery.data.generatedAt)}</Text>
                                ) : null}
                                <Button onClick={() => readinessQuery.refetch()} loading={readinessQuery.isFetching}>
                                    {t('pages:refreshReadOnlySnapshot')}</Button>
                            </Space>
                        )}
                    >
                        {readinessQuery.isError ? (
                            <Alert
                                type="error"
                                showIcon
                                message={t('pages:adapterReadinessUnavailable')}
                                description={(
                                    <Paragraph style={{marginBottom: 0}}>
                                        {t('pages:adapterReadinessCouldNotBeRetrievedTheOverviewRemainsFailClosedWithNoAvailableCapabilityOrLiveAuthor')}<br/>
                                        <Text
                                            type="secondary">{formatApiError(readinessQuery.error as AppApiError)}</Text>
                                    </Paragraph>
                                )}
                            />
                        ) : (
                            <Table<VenueSummary>
                                rowKey="venue"
                                columns={venueColumns}
                                dataSource={venueSummaries}
                                loading={readinessQuery.isLoading || readinessQuery.isFetching}
                                pagination={false}
                                size="small"
                                scroll={{x: 900}}
                                locale={{emptyText: t('pages:noAdapterReadinessDataRuntimeRemainsFailClosed')}}
                            />
                        )}
                    </Card>
                </Col>
                <Col xs={24} xl={10}>
                    <Card
                        className="page-section"
                        variant="borderless"
                        title={t('pages:marketDataReadiness')}
                        extra={<Link to={MARKETDATA_READINESS_PATH}>{t('pages:openMarketData')}</Link>}
                    >
                        <Space direction="vertical" size={12} style={{display: 'flex'}}>
                            <DataFreshness
                                source={t('pages:marketDataReadiness')}
                                state="disabled"
                                detail="PENDING_BACKEND_SUPPORT"
                            />
                            <Alert
                                type="info"
                                showIcon
                                message={t('pages:marketDataFreshnessIsScopedToTheDatabaseQueryItDoesNotEstablishLiveExchangeReadiness')}
                                description={t('pages:theMarketDataPageDisplaysFreshStaleGapNoDataUnknownFromApiMarketdataReadinessNoGlobalSourceHealthAgg')}
                            />
                            <Space size={[8, 8]} wrap>
                                <StatusTag label={t('pages:marketDataFresh')} tone="info" variant="pill"/>
                                <StatusTag label="NO_MIGRATION_MVP" tone="warning" variant="pill"/>
                                <StatusTag label="PENDING_BACKEND_SUPPORT" tone="warning" variant="pill"/>
                            </Space>
                            <Button type="primary">
                                <Link to={MARKETDATA_READINESS_PATH}>{t('pages:viewMarketDataReadiness')}</Link>
                            </Button>
                        </Space>
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]}>
                <Col xs={24} xl={14}>
                    <Card className="page-section" variant="borderless"
                          title={t('pages:runtimeBlockersAndUnavailableCapabilities')}>
                        <Table<RuntimeBlocker>
                            rowKey="key"
                            columns={blockerColumns}
                            dataSource={runtimeBlockers}
                            pagination={false}
                            size="small"
                            scroll={{x: 880}}
                        />
                    </Card>
                </Col>
                <Col xs={24} xl={10}>
                    <Card className="page-section" variant="borderless" title={t('pages:boundaryNotes')}>
                        <List
                            size="small"
                            dataSource={[
                                `Adapter matrix detail: ${adapterMatrixDetail}`,
                                t('pages:paperReadyMeansReadyForPaperOnlyItDoesNotAuthorizeLive'),
                                t('pages:marketDataFreshnessAppliesToTheSubmittedLocalDatabaseQueryUnknownApiFailureIsNotReady'),
                                t('pages:adapterNoRealMeansNoRealFakeStubFuturerealRemainBlocked'),
                                t('pages:liveReadinessRealclientRealProvidersAndRealExchangeAdaptersAreNotImplemented'),
                                t('pages:noPermissionProbePostIngestionRunOnceOrdersCancellationsWithdrawalsOrTransfers'),
                            ]}
                            renderItem={(item) => (
                                <List.Item>
                                    <Text>{item}</Text>
                                </List.Item>
                            )}
                        />
                    </Card>
                </Col>
            </Row>
        </Space>
    );
}

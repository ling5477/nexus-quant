import {useLocalizedForm} from '@/i18n/useLocalizedForm';
import {useTranslation} from 'react-i18next';
import {t} from '@/i18n';
import {Alert, Button, Card, DatePicker, Descriptions, Form, Input, Select, Space, Table, Tag, Typography, message} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {useSearchParams} from 'react-router-dom';

import {formatApiError, showApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {marketdataQueryKeys} from '@/api/query-keys';
import {NqPageHeader} from '@/components/nq/NqPageHeader';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {ExchangeBadge} from '@/nq-design-system/brand/ExchangeBadge';
import {EXCHANGE_OPTIONS, INTERVAL_OPTIONS, MARKET_TYPE_OPTIONS, SYMBOL_OPTIONS} from '@/constants/filter-options';
import {DataFreshness, NqKlineChart, NqVolumeChart, applyNqCssVars, type FreshnessState, type NqKlineBar} from '@/nq-design-system';
import {useAccountContextStore} from '@/store/account-context-store';
import type {AppApiError} from '@/types/api';
import type {
    CreateMarketdataIngestionJobRequest,
    CreateMarketdataDatasetRequest,
    MarketdataBar,
    MarketdataBarsQuery,
    MarketdataDataset,
    MarketdataIngestionJob,
    MarketdataIngestionRun,
    MarketdataQualityIssue,
    MarketdataQualityMetric,
    MarketdataQualityOverview,
    MarketdataQualityOverviewQuery,
    MarketdataReadinessQuery,
    MarketdataReadinessSummary,
    MarketdataSandboxCapability,
    MarketdataSandboxReadiness,
    MarketdataSandboxSourceType,
} from '@/types/marketdata';
import {formatDateTime, formatNumber} from '@/utils/formatters';

const columns: ColumnsType<MarketdataBar> = [
    {get title() { return t('pages:exchange'); }, dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {get title() { return t('pages:market'); }, dataIndex: 'marketType', key: 'marketType', width: 100},
    {get title() { return t('pages:symbol'); }, dataIndex: 'symbol', key: 'symbol', width: 140},
    {get title() { return t('pages:interval'); }, dataIndex: 'interval', key: 'interval', width: 100},
    {get title() { return t('pages:openTime'); }, dataIndex: 'openTime', key: 'openTime', width: 180, render: (value: string) => formatDateTime(value)},
    {get title() { return t('pages:closeTime'); }, dataIndex: 'closeTime', key: 'closeTime', width: 180, render: (value: string) => formatDateTime(value)},
    {get title() { return t('pages:open'); }, dataIndex: 'openPrice', key: 'openPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:high'); }, dataIndex: 'highPrice', key: 'highPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:low'); }, dataIndex: 'lowPrice', key: 'lowPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:close2'); }, dataIndex: 'closePrice', key: 'closePrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:volume'); }, dataIndex: 'volume', key: 'volume', width: 120, render: (value: number) => formatNumber(value, 8)},
    {get title() { return t('pages:quoteVolume'); }, dataIndex: 'quoteVolume', key: 'quoteVolume', width: 140, render: (value?: number | null) => value == null ? '-' : formatNumber(value, 8)},
    {
        get title() { return t('pages:quality'); },
        dataIndex: 'qualityStatus',
        key: 'qualityStatus',
        width: 130,
        render: (value?: string | null) => value
            ? <Tag color={value === 'OK' ? 'green' : 'orange'}>{value}</Tag>
            : <Tag>{t('pages:unavailable')}</Tag>,
    },
];

const GAP_QUALITY_STATUSES = new Set(['GAP_DETECTED', 'MISSING_BAR', 'INCOMPLETE', 'DEGRADED']);
const OK_QUALITY_STATUSES = new Set(['OK', 'GOOD']);

type QualityReadinessStatus = 'GOOD' | 'WARN' | 'STALE' | 'GAP' | 'ERROR' | 'UNKNOWN';

const stableFactPendingText = () => t('pages:noStableFacts');

const READINESS_STATUS_COLOR: Record<string, string> = {
    ENABLED: 'blue',
    FRESH: 'green',
    STALE: 'orange',
    VERY_STALE: 'red',
    GAP: 'orange',
    ERROR: 'red',
    DISABLED: 'default',
    HEALTHY: 'green',
    DEGRADED: 'orange',
    RATE_LIMITED: 'gold',
    TIMEOUT: 'orange',
    NONE: 'green',
    PARTIAL: 'gold',
    PENDING_BACKEND_SUPPORT: 'default',
    UNKNOWN: 'default',
    NO_DATA: 'default',
    GOOD: 'green',
    WARN: 'gold',
};

const READINESS_FRESHNESS_STATE: Record<string, FreshnessState> = {
    FRESH: 'fresh',
    STALE: 'stale',
    VERY_STALE: 'stale',
    GAP: 'degraded',
    ERROR: 'error',
    DISABLED: 'disabled',
    PENDING_BACKEND_SUPPORT: 'delayed',
    UNKNOWN: 'delayed',
    NO_DATA: 'no_data',
};

const SANDBOX_READINESS_VALUES = new Set<MarketdataSandboxReadiness>([
    'FRESH',
    'STALE',
    'GAP',
    'ERROR',
    'DISABLED',
    'PENDING_BACKEND_SUPPORT',
]);

type MarketdataDateValue = string | null | undefined | {
    toISOString?: () => string;
    toDate?: () => Date;
};

type MarketdataBarsFormValues = Omit<MarketdataBarsQuery, 'startTime' | 'endTime'> & {
    startTime?: MarketdataDateValue;
    endTime?: MarketdataDateValue;
};

type CreateMarketdataIngestionJobFormValues = Omit<CreateMarketdataIngestionJobRequest, 'startTime' | 'endTime'> & {
    startTime?: MarketdataDateValue;
    endTime?: MarketdataDateValue;
};

type CreateMarketdataDatasetFormValues = Omit<CreateMarketdataDatasetRequest, 'startTime' | 'endTime'> & {
    startTime?: MarketdataDateValue;
    endTime?: MarketdataDateValue;
};

type MarketdataRuntimeDeepLinkValues = Partial<Pick<MarketdataBarsQuery, 'exchangeCode' | 'marketType' | 'symbol' | 'interval'>>;

const EXCHANGE_OPTION_VALUES = new Set(EXCHANGE_OPTIONS.map((option) => option.value));
const MARKET_TYPE_OPTION_VALUES = new Set(MARKET_TYPE_OPTIONS.map((option) => option.value));
const SYMBOL_OPTION_VALUES = new Set(SYMBOL_OPTIONS.map((option) => option.value));
const INTERVAL_OPTION_VALUES = new Set(INTERVAL_OPTIONS.map((option) => option.value));

function safeQueryParam(searchParams: URLSearchParams, key: string, allowedValues: ReadonlySet<string>): string | undefined {
    const value = searchParams.get(key)?.trim();
    if (!value || !allowedValues.has(value)) {
        return undefined;
    }
    return value;
}

function readRuntimeDeepLinkValues(searchParams: URLSearchParams): MarketdataRuntimeDeepLinkValues {
    const values: MarketdataRuntimeDeepLinkValues = {};
    const exchangeCode = safeQueryParam(searchParams, 'exchangeCode', EXCHANGE_OPTION_VALUES);
    const marketType = safeQueryParam(searchParams, 'marketType', MARKET_TYPE_OPTION_VALUES);
    const symbol = safeQueryParam(searchParams, 'symbol', SYMBOL_OPTION_VALUES);
    const interval = safeQueryParam(searchParams, 'interval', INTERVAL_OPTION_VALUES);

    if (exchangeCode) {
        values.exchangeCode = exchangeCode;
    }
    if (marketType) {
        values.marketType = marketType;
    }
    if (symbol) {
        values.symbol = symbol;
    }
    if (interval) {
        values.interval = interval;
    }
    return values;
}

function hasRuntimeDeepLinkValues(values: MarketdataRuntimeDeepLinkValues): boolean {
    return Boolean(values.exchangeCode || values.marketType || values.symbol || values.interval);
}

function toIsoString(value: MarketdataDateValue): string {
    if (!value) {
        return '';
    }
    if (typeof value === 'string') {
        return value;
    }
    if (typeof value.toISOString === 'function') {
        return value.toISOString();
    }
    if (typeof value.toDate === 'function') {
        return value.toDate().toISOString();
    }
    return String(value);
}

function normalizeBarsQuery(values: MarketdataBarsFormValues): MarketdataBarsQuery {
    return {
        ...values,
        startTime: toIsoString(values.startTime),
        endTime: toIsoString(values.endTime),
    };
}

function toReadinessQuery(query: MarketdataBarsQuery): MarketdataReadinessQuery {
    return {
        exchangeCode: query.exchangeCode,
        marketType: query.marketType,
        symbol: query.symbol,
        interval: query.interval,
        from: query.startTime,
        to: query.endTime,
    };
}

function toQualityOverviewQuery(query: MarketdataBarsQuery): MarketdataQualityOverviewQuery {
    return {
        exchangeCode: query.exchangeCode,
        marketType: query.marketType,
        symbol: query.symbol,
        interval: query.interval,
        from: query.startTime,
        to: query.endTime,
    };
}

function normalizeIngestionJob(values: CreateMarketdataIngestionJobFormValues): CreateMarketdataIngestionJobRequest {
    return {
        ...values,
        startTime: toIsoString(values.startTime),
        endTime: toIsoString(values.endTime),
    };
}

function normalizeDataset(values: CreateMarketdataDatasetFormValues): CreateMarketdataDatasetRequest {
    return {
        ...values,
        startTime: toIsoString(values.startTime),
        endTime: toIsoString(values.endTime),
    };
}

function toNqKlineBar(bar: MarketdataBar): NqKlineBar {
    return {
        time: bar.openTime,
        open: Number(bar.openPrice),
        high: Number(bar.highPrice),
        low: Number(bar.lowPrice),
        close: Number(bar.closePrice),
        volume: Number(bar.volume),
        qualityStatus: bar.qualityStatus ?? null,
    };
}

function intervalToMs(interval: string | undefined): number | null {
    if (!interval) {
        return null;
    }

    const match = /^(\d+)([mhd])$/i.exec(interval.trim());
    if (!match) {
        return null;
    }

    const amount = Number(match[1]);
    const unit = match[2].toLowerCase();

    if (!Number.isFinite(amount) || amount <= 0) {
        return null;
    }

    if (unit === 'm') {
        return amount * 60_000;
    }
    if (unit === 'h') {
        return amount * 60 * 60_000;
    }
    return amount * 24 * 60 * 60_000;
}

function timestampMs(value: string | undefined | null): number | null {
    if (!value) {
        return null;
    }

    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
}

interface BarsQualitySummary {
    statuses: string[];
    statusCounts: Array<{status: string; count: number}>;
    gapCount: number;
    qualityGapCount: number;
    sequenceGapCount: number | null;
    unknownQualityCount: number;
    nonOkQualityCount: number;
    hasQualityStatus: boolean;
    gapDetectionUnavailable: boolean;
}

function countSequenceGaps(bars: readonly MarketdataBar[], interval: string | undefined): number | null {
    if (bars.length < 2) {
        return 0;
    }

    const intervalMs = intervalToMs(interval);
    if (intervalMs === null) {
        return null;
    }

    let gapCount = 0;
    const sortedOpenTimes = bars
        .map((bar) => timestampMs(bar.openTime))
        .filter((value): value is number => value !== null)
        .sort((left, right) => left - right);

    if (sortedOpenTimes.length !== bars.length) {
        return null;
    }

    for (let index = 1; index < sortedOpenTimes.length; index += 1) {
        const diff = sortedOpenTimes[index] - sortedOpenTimes[index - 1];
        if (diff > intervalMs * 1.5) {
            gapCount += Math.max(1, Math.round(diff / intervalMs) - 1);
        }
    }

    return gapCount;
}

function summarizeBarsQuality(bars: readonly MarketdataBar[], interval: string | undefined): BarsQualitySummary {
    const statusCounter = new Map<string, number>();
    let unknownQualityCount = 0;

    bars.forEach((bar) => {
        const status = bar.qualityStatus?.trim();
        if (!status) {
            unknownQualityCount += 1;
            return;
        }

        const normalized = status.toUpperCase();
        statusCounter.set(normalized, (statusCounter.get(normalized) ?? 0) + 1);
    });

    const statusCounts = Array.from(statusCounter.entries()).map(([status, count]) => ({status, count}));
    const sequenceGapCount = countSequenceGaps(bars, interval);
    const qualityGapCount = statusCounts.reduce(
        (total, item) => total + (GAP_QUALITY_STATUSES.has(item.status) ? item.count : 0),
        0,
    );
    const nonOkQualityCount = statusCounts.reduce(
        (total, item) => total + (OK_QUALITY_STATUSES.has(item.status) ? 0 : item.count),
        0,
    );

    return {
        statuses: statusCounts.map((item) => item.status),
        statusCounts,
        gapCount: qualityGapCount + (sequenceGapCount ?? 0),
        qualityGapCount,
        sequenceGapCount,
        unknownQualityCount,
        nonOkQualityCount,
        hasQualityStatus: statusCounts.length > 0,
        gapDetectionUnavailable: bars.length > 0 && !statusCounts.length && sequenceGapCount === null,
    };
}

interface BarsFreshnessSummary {
    state: FreshnessState;
    detail: string;
    stale: boolean;
}

function summarizeBarsFreshness(
    submittedQuery: MarketdataBarsQuery | null,
    bars: readonly MarketdataBar[],
    quality: BarsQualitySummary,
    error: unknown,
    loading: boolean,
): BarsFreshnessSummary {
    if (error) {
        return {state: 'error', detail: t('pages:barQueryFailed'), stale: false};
    }
    if (!submittedQuery) {
        return {state: 'disabled', detail: t('pages:notQueried'), stale: false};
    }
    if (loading) {
        return {state: 'delayed', detail: 'loading', stale: false};
    }
    if (bars.length === 0) {
        return {state: 'no_data', detail: '0 bars', stale: false};
    }
    if (quality.gapCount > 0) {
        return {state: 'degraded', detail: `${quality.gapCount} gap bars`, stale: false};
    }

    const lastBar = bars[bars.length - 1];
    const lastCloseMs = timestampMs(lastBar?.closeTime ?? lastBar?.openTime);
    const requestedEndMs = timestampMs(submittedQuery.endTime);
    const intervalMs = intervalToMs(submittedQuery.interval);
    const isStale = Boolean(
        lastCloseMs !== null
        && requestedEndMs !== null
        && intervalMs !== null
        && lastCloseMs + intervalMs < requestedEndMs,
    );

    if (isStale) {
        return {
            state: 'stale',
            detail: `last ${formatDateTime(lastBar.closeTime ?? lastBar.openTime)}`,
            stale: true,
        };
    }

    return {
        state: 'fresh',
        detail: `last ${formatDateTime(lastBar.closeTime ?? lastBar.openTime)}`,
        stale: false,
    };
}

interface DataQualityReadinessSummary {
    status: QualityReadinessStatus;
    title: string;
    detail: string;
    sourceHealth: 'UNAVAILABLE';
    sourceHealthDetail: string;
}

function summarizeDataQualityReadiness(
    submittedQuery: MarketdataBarsQuery | null,
    bars: readonly MarketdataBar[],
    quality: BarsQualitySummary,
    freshness: BarsFreshnessSummary,
    error: unknown,
    loading: boolean,
): DataQualityReadinessSummary {
    if (error) {
        return {
            status: 'ERROR',
            title: 'ERROR',
            detail: t('pages:barQueryFailedDataQualityCannotBeTrustedForThisRequest'),
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
        };
    }
    if (!submittedQuery) {
        return {
            status: 'UNKNOWN',
            title: 'UNKNOWN',
            detail: t('pages:queryBarsToEvaluateCurrentDataQuality'),
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
        };
    }
    if (loading) {
        return {
            status: 'UNKNOWN',
            title: 'UNKNOWN',
            detail: t('pages:barQueryIsStillLoading'),
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
        };
    }
    if (bars.length === 0) {
        return {
            status: 'UNKNOWN',
            title: 'UNKNOWN',
            detail: t('pages:noBarsReturnedForTheSelectedWindow'),
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
        };
    }
    if (quality.gapCount > 0) {
        return {
            status: 'GAP',
            title: 'GAP',
            detail: `${quality.gapCount} gap signal(s) detected from qualityStatus or interval sequence`,
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
        };
    }
    if (freshness.stale) {
        return {
            status: 'STALE',
            title: 'STALE',
            detail: t('pages:theLastBarDoesNotCoverTheQueryEnd'),
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
        };
    }
    if (quality.unknownQualityCount > 0 || quality.nonOkQualityCount > 0 || quality.gapDetectionUnavailable) {
        return {
            status: 'WARN',
            title: 'WARN',
            detail: t('pages:qualitystatusIsIncompleteOrContainsNonOkValues'),
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
        };
    }

    return {
        status: 'GOOD',
        title: 'GOOD',
        detail: t('pages:barsArePresentSequentialAndQualitystatusIsOk'),
        sourceHealth: 'UNAVAILABLE',
        sourceHealthDetail: t('pages:readinessApiUnavailableUsingBarDerivedFallback'),
    };
}

interface SandboxCapabilityStatus {
    capability: MarketdataSandboxCapability;
    readiness: MarketdataSandboxReadiness;
    reason: string;
}

interface SandboxSourceDisplaySummary {
    sourceType: MarketdataSandboxSourceType;
    readiness: MarketdataSandboxReadiness;
    venue: string;
    reasonCode: string;
    reasonText: string;
    checkedAt: string;
    noEgress: 'PENDING_BACKEND_SUPPORT';
    sourceLabel: string;
    capabilities: SandboxCapabilityStatus[];
}

function mapBackendSandboxReadiness(status: string | undefined | null): MarketdataSandboxReadiness {
    const normalized = status?.trim().toUpperCase();
    if (normalized && SANDBOX_READINESS_VALUES.has(normalized as MarketdataSandboxReadiness)) {
        return normalized as MarketdataSandboxReadiness;
    }
    return 'PENDING_BACKEND_SUPPORT';
}

function mapFallbackSandboxReadiness(summary: DataQualityReadinessSummary): MarketdataSandboxReadiness {
    if (summary.status === 'GOOD') {
        return 'FRESH';
    }
    if (summary.status === 'STALE' || summary.status === 'GAP' || summary.status === 'ERROR') {
        return summary.status;
    }
    return 'PENDING_BACKEND_SUPPORT';
}

function summarizeSandboxSourceDisplay(
    submittedQuery: MarketdataBarsQuery | null,
    backendReadiness: MarketdataReadinessSummary | null,
    dataQualityReadiness: DataQualityReadinessSummary,
    readinessLoading: boolean,
    chartError: string | null,
): SandboxSourceDisplaySummary {
    const readiness = backendReadiness
        ? mapBackendSandboxReadiness(backendReadiness.status)
        : readinessLoading
            ? 'PENDING_BACKEND_SUPPORT'
            : chartError
                ? 'ERROR'
                : mapFallbackSandboxReadiness(dataQualityReadiness);
    const reasonCode = backendReadiness?.sourceHealthStatus
        ?? (readinessLoading ? 'PENDING_BACKEND_SUPPORT' : readiness);
    const reasonText = backendReadiness?.sourceHealthReason
        ?? (readinessLoading
            ? t('pages:waitingForTheReadinessApiThisUiDoesNotRequestExternalExchanges')
            : t('pages:value1ThisUiShowsLocalBarsReadinessResultsOnly', {value1: dataQualityReadiness.detail}));
    const barsCapabilityReadiness = submittedQuery ? readiness : 'PENDING_BACKEND_SUPPORT';

    return {
        sourceType: 'LOCAL_DB',
        readiness,
        venue: submittedQuery?.exchangeCode ?? '-',
        reasonCode,
        reasonText,
        checkedAt: backendReadiness?.generatedAt ?? 'PENDING_BACKEND_SUPPORT',
        noEgress: 'PENDING_BACKEND_SUPPORT',
        sourceLabel: submittedQuery
            ? t('pages:localDatabaseMarketDataReadiness')
            : t('pages:localDatabaseReadinessAwaitsAQuery'),
        capabilities: [
            {
                capability: 'bars',
                readiness: barsCapabilityReadiness,
                reason: submittedQuery
                    ? t('pages:theBarsReadinessApisReturnLocalMarketDataFacts')
                    : t('pages:searchToViewTheBarSourceStatus'),
            },
            {
                capability: 'instrument metadata',
                readiness: 'PENDING_BACKEND_SUPPORT',
                reason: t('pages:metadataSourceDiagnosticsAreNotExposedByThisApi'),
            },
            {
                capability: 'ticker',
                readiness: 'PENDING_BACKEND_SUPPORT',
                reason: t('pages:tickerSourceDiagnosticsAreNotExposedByThisApi'),
            },
            {
                capability: 'exchange status',
                readiness: 'PENDING_BACKEND_SUPPORT',
                reason: t('pages:exchangeStatusSourceDiagnosticsAreNotExposedByThisApi'),
            },
        ],
    };
}

function SandboxSourceDisplay({summary}: {summary: SandboxSourceDisplaySummary}) {
    useTranslation('pages');
    return (
        <div
            data-testid="marketdata-sandbox-source-display"
            style={{
                border: '1px solid var(--nq-color-border)',
                borderRadius: 8,
                padding: 12,
                background: 'var(--nq-color-surface)',
            }}
        >
            <Space direction="vertical" size={10} style={{display: 'flex'}}>
                <Space size={8} wrap>
                    <Typography.Text strong>{t('pages:sandboxSource')}</Typography.Text>
                    <Tag color="blue">{t('pages:sandbox')}</Tag>
                    <Tag>{t('pages:noEgress')}</Tag>
                    <Tag>{t('pages:publicCandidate')}</Tag>
                    <Tag color={readinessStatusColor(summary.readiness)}>{summary.readiness}</Tag>
                </Space>
                <Descriptions
                    size="small"
                    column={{xs: 1, sm: 2, md: 3}}
                    items={[
                        {key: 'sourceType', label: 'sourceType', children: <MetricText>{summary.sourceType}</MetricText>},
                        {key: 'venue', label: 'venue', children: <MetricText>{summary.venue}</MetricText>},
                        {key: 'sourceLabel', label: 'sourceLabel', children: <MetricText>{summary.sourceLabel}</MetricText>},
                        {key: 'reasonCode', label: 'reasonCode', children: <MetricText>{summary.reasonCode}</MetricText>},
                        {key: 'checkedAt', label: 'checkedAt', children: <MetricText>{summary.checkedAt}</MetricText>},
                        {key: 'noEgress', label: 'noEgress', children: <MetricText>{summary.noEgress}</MetricText>},
                    ]}
                />
                <Typography.Text type="secondary">
                    {summary.reasonText}
                </Typography.Text>
                <Space size={6} wrap>
                    {summary.capabilities.map((item) => (
                        <Tag key={item.capability} color={readinessStatusColor(item.readiness)} title={item.reason}>
                            {item.capability}: {item.readiness}
                        </Tag>
                    ))}
                </Space>
                <Typography.Text type="secondary">
                    {t('pages:thisSectionUsesBarsReadinessAndTheLocalQueryContextOnlyMissingSandboxSourceFieldsRemainPendingBacken')}</Typography.Text>
            </Space>
        </div>
    );
}

function QualityTags({quality}: {quality: BarsQualitySummary}) {
    useTranslation('pages');
    if (!quality.hasQualityStatus) {
        return <Tag>{t('pages:qualitystatusUnavailable')}</Tag>;
    }

    return (
        <Space size={4} wrap>
            {quality.statusCounts.map(({status, count}) => (
                <Tag key={status} color={OK_QUALITY_STATUSES.has(status) ? 'green' : 'orange'}>
                    {status}: {count}
                </Tag>
            ))}
        </Space>
    );
}

function readinessStatusColor(status?: string | null): string {
    return status ? (READINESS_STATUS_COLOR[status] ?? 'default') : 'default';
}

function readinessFreshnessState(status?: string | null): FreshnessState {
    return status ? (READINESS_FRESHNESS_STATE[status] ?? 'degraded') : 'disabled';
}

function countText(value?: number | null): string {
    return value == null ? stableFactPendingText() : String(value);
}

function dateText(value?: string | null): string {
    return value ? formatDateTime(value) : stableFactPendingText();
}

function optionalText(value?: string | null): string {
    return value && value.trim() ? value : stableFactPendingText();
}

function numberText(value?: number | null, suffix = ''): string {
    return value == null ? stableFactPendingText() : `${formatNumber(value, 2)}${suffix}`;
}

function percentText(value?: number | null): string {
    return value == null ? stableFactPendingText() : `${formatNumber(value * 100, 2)}%`;
}

function readinessSourceHealth(readiness: MarketdataReadinessSummary): string {
    return readiness.sourceHealth ?? readiness.sourceHealthStatus ?? 'UNKNOWN';
}

function readinessUpdatedAt(readiness: MarketdataReadinessSummary): string | null {
    return readiness.updatedAt ?? readiness.generatedAt ?? null;
}

function MarketDataStatusBadge({status}: {status?: string | null}) {
    useTranslation('pages');
    return <Tag color={readinessStatusColor(status)}>{status ?? 'UNKNOWN'}</Tag>;
}

function MarketDataOriginBadge({origin}: {origin?: string | null}) {
    useTranslation('pages');
    const color = origin === 'LOCAL_DB'
        ? 'blue'
        : origin === 'FIXTURE' || origin === 'FAKE_SERVER'
            ? 'gold'
            : origin === 'PUBLIC_CANDIDATE'
                ? 'purple'
                : 'default';
    return <Tag color={color}>{origin ?? 'UNKNOWN'}</Tag>;
}

interface MarketDataSourceHealthRow {
    key: string;
    sourceCode: string;
    dataOrigin: string;
    exchange: string;
    symbol: string;
    timeframe: string;
    sourceStatus: string;
    sourceHealth: string;
    freshnessStatus: string;
    gapStatus: string;
    errorCategory: string;
    updatedAt: string;
    reason: string;
}

const sourceHealthColumns: ColumnsType<MarketDataSourceHealthRow> = [
    {get title() { return t('pages:dataSource2'); }, dataIndex: 'sourceCode', key: 'sourceCode', width: 180, render: (value: string) => <MetricText>{value}</MetricText>},
    {get title() { return t('pages:dataOrigin'); }, dataIndex: 'dataOrigin', key: 'dataOrigin', width: 150, render: (value: string) => <MarketDataOriginBadge origin={value} />},
    {get title() { return t('pages:exchange'); }, dataIndex: 'exchange', key: 'exchange', width: 120},
    {get title() { return t('pages:tradingPair'); }, dataIndex: 'symbol', key: 'symbol', width: 140},
    {get title() { return t('pages:interval'); }, dataIndex: 'timeframe', key: 'timeframe', width: 100},
    {get title() { return t('pages:sourceStatus'); }, dataIndex: 'sourceStatus', key: 'sourceStatus', width: 130, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {get title() { return t('pages:healthStatus'); }, dataIndex: 'sourceHealth', key: 'sourceHealth', width: 130, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {get title() { return t('pages:freshness2'); }, dataIndex: 'freshnessStatus', key: 'freshnessStatus', width: 130, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {get title() { return t('pages:gaps'); }, dataIndex: 'gapStatus', key: 'gapStatus', width: 120, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {get title() { return t('pages:errorCategory'); }, dataIndex: 'errorCategory', key: 'errorCategory', width: 170, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {get title() { return t('pages:updatedAt'); }, dataIndex: 'updatedAt', key: 'updatedAt', width: 190, render: (value: string) => <MetricText>{value}</MetricText>},
    {get title() { return t('pages:reason'); }, dataIndex: 'reason', key: 'reason', width: 360, ellipsis: true},
];

function sourceHealthRows(
    readiness: MarketdataReadinessSummary | null,
    submittedQuery: MarketdataBarsQuery | null,
): MarketDataSourceHealthRow[] {
    if (!readiness) {
        return [{
            key: 'pending-readiness',
            sourceCode: submittedQuery
                ? `${submittedQuery.exchangeCode}:${submittedQuery.symbol}:${submittedQuery.interval}`
                : stableFactPendingText(),
            dataOrigin: 'UNKNOWN',
            exchange: submittedQuery?.exchangeCode ?? stableFactPendingText(),
            symbol: submittedQuery?.symbol ?? stableFactPendingText(),
            timeframe: submittedQuery?.interval ?? stableFactPendingText(),
            sourceStatus: 'UNKNOWN',
            sourceHealth: 'UNKNOWN',
            freshnessStatus: submittedQuery ? 'UNKNOWN' : 'NO_DATA',
            gapStatus: 'UNKNOWN',
            errorCategory: 'UNKNOWN',
            updatedAt: stableFactPendingText(),
            reason: submittedQuery
                ? t('pages:waitingForLocalDatabaseDiagnosticsFromApiMarketdataReadinessNoExternalExchangeRequestsAreMade')
                : t('pages:searchToViewReadOnlyReadinessDiagnostics'),
        }];
    }

    return [{
        key: readiness.sourceCode ?? `${readiness.exchangeCode}:${readiness.symbol}:${readiness.interval}`,
        sourceCode: readiness.sourceCode ?? stableFactPendingText(),
        dataOrigin: readiness.dataOrigin ?? 'UNKNOWN',
        exchange: readiness.exchangeCode ?? readiness.exchange ?? stableFactPendingText(),
        symbol: readiness.symbol ?? stableFactPendingText(),
        timeframe: readiness.interval ?? readiness.timeframe ?? stableFactPendingText(),
        sourceStatus: readiness.sourceStatus ?? 'UNKNOWN',
        sourceHealth: readinessSourceHealth(readiness),
        freshnessStatus: readiness.freshnessStatus ?? 'UNKNOWN',
        gapStatus: readiness.gapStatus ?? 'UNKNOWN',
        errorCategory: readiness.errorCategory ?? 'UNKNOWN',
        updatedAt: dateText(readinessUpdatedAt(readiness)),
        reason: readiness.disabledReason
            ?? readiness.degradedReason
            ?? readiness.sourceHealthReason
            ?? stableFactPendingText(),
    }];
}

function MarketDataSourceHealthTable({
    readiness,
    submittedQuery,
    loading,
}: {
    readiness: MarketdataReadinessSummary | null;
    submittedQuery: MarketdataBarsQuery | null;
    loading: boolean;
}) {
    useTranslation('pages');
    return (
        <Table<MarketDataSourceHealthRow>
            size="small"
            rowKey="key"
            columns={sourceHealthColumns}
            dataSource={sourceHealthRows(readiness, submittedQuery)}
            pagination={false}
            loading={loading}
            scroll={{x: 1800}}
        />
    );
}

function MarketDataQualityNotice() {
    useTranslation('pages');
    return (
        <Alert
            type="warning"
            showIcon
            message={t('pages:readOnlyMarketDataQualityDiagnostics')}
            description={(
                <Space direction="vertical" size={2}>
                    <span>{t('pages:thisPageShowsMarketDataQualityDiagnosticsOnly')}</span>
                    <span>{t('pages:goodDataQualityDoesNotPermitTrading')}</span>
                    <span>{t('pages:publicMarketDataReadinessDoesNotGrantTradingAuthorization')}</span>
                    <span>{t('pages:liveIsDisabledPrivateTradingPermissionProbesAndRealProvidersAreNotImplemented')}</span>
                </Space>
            )}
        />
    );
}

function isMarketdataQualityStatusSummary(value: unknown): value is MarketdataReadinessSummary['qualityStatusSummary'] {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return false;
    }

    const candidate = value as Partial<MarketdataReadinessSummary['qualityStatusSummary']>;
    return typeof candidate.okCount === 'number'
        && typeof candidate.gapSignalCount === 'number'
        && typeof candidate.invalidCount === 'number'
        && typeof candidate.unknownQualityCount === 'number'
        && Boolean(candidate.statuses)
        && typeof candidate.statuses === 'object'
        && !Array.isArray(candidate.statuses);
}

function isMarketdataReadinessSummary(value: unknown): value is MarketdataReadinessSummary {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return false;
    }

    const candidate = value as Partial<MarketdataReadinessSummary>;
    return typeof candidate.status === 'string'
        && typeof candidate.freshnessStatus === 'string'
        && typeof candidate.sourceHealthStatus === 'string'
        && typeof candidate.sourceHealthReason === 'string'
        && typeof candidate.backendSupportLevel === 'string'
        && typeof candidate.generatedAt === 'string'
        && typeof candidate.barCount === 'number'
        && typeof candidate.unknownQualityCount === 'number'
        && isMarketdataQualityStatusSummary(candidate.qualityStatusSummary);
}

function isQualityMetric(value: unknown): value is MarketdataQualityMetric {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return false;
    }

    const candidate = value as Partial<MarketdataQualityMetric>;
    return typeof candidate.status === 'string'
        && (candidate.value === undefined || candidate.value === null || typeof candidate.value === 'number');
}

function isMarketdataQualityIssue(value: unknown): value is MarketdataQualityIssue {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return false;
    }

    const candidate = value as Partial<MarketdataQualityIssue>;
    return typeof candidate.code === 'string'
        && typeof candidate.severity === 'string'
        && typeof candidate.count === 'number'
        && typeof candidate.message === 'string';
}

function isMarketdataQualityOverview(value: unknown): value is MarketdataQualityOverview {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return false;
    }

    const candidate = value as Partial<MarketdataQualityOverview>;
    return Boolean(candidate.scope)
        && typeof candidate.totalBars === 'number'
        && isQualityMetric(candidate.duplicateCount)
        && isQualityMetric(candidate.outOfOrderCount)
        && isQualityMetric(candidate.staleCount)
        && typeof candidate.sourceHealth === 'string'
        && typeof candidate.freshnessStatus === 'string'
        && typeof candidate.qualityStatus === 'string'
        && Boolean(candidate.dataOriginSummary)
        && typeof candidate.dataOriginSummary?.effectiveDataOrigin === 'string'
        && typeof candidate.dataOriginSummary?.supportLevel === 'string'
        && Boolean(candidate.datasetCoverageSummary)
        && typeof candidate.datasetCoverageSummary?.datasetCount === 'number'
        && Array.isArray(candidate.topIssues)
        && candidate.topIssues.every(isMarketdataQualityIssue)
        && typeof candidate.generatedAt === 'string';
}

function metricStatusColor(status?: string | null): string {
    if (status === 'AVAILABLE') {
        return 'green';
    }
    if (status === 'UNKNOWN' || status === 'NOT_AVAILABLE') {
        return 'gold';
    }
    return readinessStatusColor(status);
}

function metricValueText(metric?: MarketdataQualityMetric | null): string {
    if (!metric) {
        return stableFactPendingText();
    }
    if (metric.status === 'AVAILABLE' && metric.value != null) {
        return String(metric.value);
    }
    return metric.status;
}

function overviewCountText(value?: number | null): string {
    return value == null ? stableFactPendingText() : String(value);
}

function ScopeValue({value}: {value?: string | null}) {
    useTranslation('pages');
    return <MetricText>{value && value.trim() ? value : stableFactPendingText()}</MetricText>;
}

function ReadinessQualityTags({readiness}: {readiness: MarketdataReadinessSummary}) {
    useTranslation('pages');
    const entries = Object.entries(readiness.qualityStatusSummary?.statuses ?? {});

    if (entries.length === 0) {
        return <Tag>{t('pages:qualitystatusUnavailable')}</Tag>;
    }

    return (
        <Space size={4} wrap>
            {entries.map(([status, count]) => (
                <Tag key={status} color={OK_QUALITY_STATUSES.has(status) ? 'green' : 'orange'}>
                    {status}: {count}
                </Tag>
            ))}
        </Space>
    );
}

function MetricText({children}: {children: ReactNode}) {
    useTranslation('pages');
    return <Typography.Text style={{fontFamily: 'var(--nq-font-mono)'}}>{children}</Typography.Text>;
}

function MetricTile({label, value, detail}: {label: string; value: ReactNode; detail?: ReactNode}) {
    useTranslation('pages');
    return (
        <div
            style={{
                border: '1px solid var(--nq-color-border)',
                borderRadius: 8,
                padding: 12,
                minHeight: 92,
                background: 'var(--nq-color-surface)',
            }}
        >
            <Typography.Text type="secondary" style={{display: 'block', fontSize: 12}}>
                {label}
            </Typography.Text>
            <div style={{marginTop: 8, fontSize: 20, fontWeight: 600, lineHeight: 1.2}}>
                {value}
            </div>
            {detail ? (
                <Typography.Text type="secondary" style={{display: 'block', marginTop: 6, fontSize: 12}}>
                    {detail}
                </Typography.Text>
            ) : null}
        </div>
    );
}

function issueSeverityColor(severity: string): string {
    const normalized = severity.toUpperCase();
    if (normalized === 'CRITICAL' || normalized === 'ERROR') {
        return 'red';
    }
    if (normalized === 'WARN' || normalized === 'WARNING') {
        return 'gold';
    }
    if (normalized === 'INFO') {
        return 'blue';
    }
    return 'default';
}

const qualityIssueColumns: ColumnsType<MarketdataQualityIssue> = [
    {
        get title() { return t('pages:issueCode'); },
        dataIndex: 'code',
        key: 'code',
        width: 190,
        render: (value: string) => <MetricText>{value}</MetricText>,
    },
    {
        get title() { return t('pages:severity2'); },
        dataIndex: 'severity',
        key: 'severity',
        width: 130,
        render: (value: string) => <Tag color={issueSeverityColor(value)}>{value}</Tag>,
    },
    {
        get title() { return t('pages:count'); },
        dataIndex: 'count',
        key: 'count',
        width: 100,
        render: (value: number) => <MetricText>{value}</MetricText>,
    },
    {
        get title() { return t('pages:message'); },
        dataIndex: 'message',
        key: 'message',
    },
];

function QualityMetricTile({label, metric}: {label: string; metric?: MarketdataQualityMetric | null}) {
    useTranslation('pages');
    return (
        <MetricTile
            label={label}
            value={<MetricText>{metricValueText(metric)}</MetricText>}
            detail={(
                <Space direction="vertical" size={2}>
                    <Tag color={metricStatusColor(metric?.status)}>{metric?.status ?? 'UNKNOWN'}</Tag>
                    <span>{metric?.reason ?? t('pages:missingStableLocalFactsAreNotInferredAsZero')}</span>
                </Space>
            )}
        />
    );
}

/**
 * MarketdataQualityCenterPanel 消费数据质量诊断只读 overview API。
 *
 * Why:
 * 该区块把 Data Quality Center 与交易授权完全拆开：所有 UNKNOWN / NOT_AVAILABLE / NO_DATA / INCOMPLETE
 * 都必须原样可见，且页面只发起 GET 只读请求，不触发 ingestion、permission probe、private trading 或外部交易所调用。
 */
function MarketdataQualityCenterPanel({
    overview,
    submittedQuery,
    loading,
    fetching,
    error,
    unavailable,
}: {
    overview: MarketdataQualityOverview | null;
    submittedQuery: MarketdataBarsQuery | null;
    loading: boolean;
    fetching: boolean;
    error: unknown;
    unavailable: boolean;
}) {
    useTranslation('pages');
    const errorText = error ? formatApiError(error as AppApiError) : null;

    return (
        <Card className="page-section" bordered={false} title={t('pages:dataQualityCenter')}>
            <div data-testid="marketdata-data-quality-center" style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                <Alert
                    type="warning"
                    showIcon
                    message={t('pages:dataQualityDiagnosticsOnly')}
                    description={t('pages:dataQualityDiagnosticsDoNotGrantTradingAuthorizationOrEstablishLivePrivateTradingPermissionProbeOrRe')}
                />
                {!submittedQuery ? (
                    <Alert
                        type="info"
                        showIcon
                        message={t('pages:waitingForQueryFilters')}
                        description={t('pages:searchCallsGetApiMarketdataQualityOverviewInReadOnlyModeItDoesNotTriggerIngestionQualityRefreshPermi')}
                    />
                ) : null}
                {errorText ? (
                    <Alert
                        type="error"
                        showIcon
                        message={t('pages:dataQualityCenterUnavailable')}
                        description={t('pages:overviewApiFailedValue1BarDataIsNotUsedToFabricateAPassingOverview', {value1: errorText})}
                    />
                ) : null}
                {unavailable ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={t('pages:dataQualityResponseIncomplete')}
                        description={t('pages:requiredOverviewFieldsAreMissingThePageRemainsFailClosedAndDoesNotInterpretMissingFieldsAsZeroOrRead')}
                    />
                ) : null}

                <Descriptions
                    size="small"
                    column={{xs: 1, sm: 2, md: 3}}
                    items={[
                        {key: 'scopeExchange', label: 'scope.exchangeCode', children: <ScopeValue value={overview?.scope.exchangeCode ?? submittedQuery?.exchangeCode} />},
                        {key: 'scopeMarket', label: 'scope.marketType', children: <ScopeValue value={overview?.scope.marketType ?? submittedQuery?.marketType} />},
                        {key: 'scopeSymbol', label: 'scope.symbol', children: <ScopeValue value={overview?.scope.symbol ?? submittedQuery?.symbol} />},
                        {key: 'scopeInterval', label: 'scope.interval', children: <ScopeValue value={overview?.scope.interval ?? submittedQuery?.interval} />},
                        {key: 'scopeSourceType', label: 'scope.sourceType', children: <ScopeValue value={overview?.scope.sourceType} />},
                        {key: 'scopeDataOrigin', label: 'scope.dataOrigin', children: <ScopeValue value={overview?.scope.dataOrigin} />},
                        {key: 'scopeDatasetId', label: 'scope.datasetId', children: <ScopeValue value={overview?.scope.datasetId} />},
                        {key: 'scopeFrom', label: 'scope.from', children: <MetricText>{overview?.scope.from ? formatDateTime(overview.scope.from) : (submittedQuery ? formatDateTime(submittedQuery.startTime) : stableFactPendingText())}</MetricText>},
                        {key: 'scopeTo', label: 'scope.to', children: <MetricText>{overview?.scope.to ? formatDateTime(overview.scope.to) : (submittedQuery ? formatDateTime(submittedQuery.endTime) : stableFactPendingText())}</MetricText>},
                        {key: 'generatedAt', label: 'generatedAt', children: <MetricText>{overview ? formatDateTime(overview.generatedAt) : stableFactPendingText()}</MetricText>},
                    ]}
                />

                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
                        gap: 12,
                    }}
                >
                    <MetricTile
                        label="totalBars"
                        value={<MetricText>{overview?.totalBars ?? (loading || fetching ? 'LOADING' : stableFactPendingText())}</MetricText>}
                        detail={t('pages:localBarAggregateCountZeroAppearsOnlyWhenExplicitlyReturnedByTheBackend')}
                    />
                    <MetricTile
                        label="expectedBars"
                        value={<MetricText>{overviewCountText(overview?.expectedBars)}</MetricText>}
                        detail={t('pages:nullMeansNoStableExpectedCountFactIsAvailable')}
                    />
                    <MetricTile
                        label="gapCount"
                        value={<MetricText>{overviewCountText(overview?.gapCount)}</MetricText>}
                        detail={t('pages:nullDoesNotMeanNoGapsMissingFactsRemainUnknown')}
                    />
                    <QualityMetricTile label="duplicateCount" metric={overview?.duplicateCount} />
                    <QualityMetricTile label="outOfOrderCount" metric={overview?.outOfOrderCount} />
                    <QualityMetricTile label="staleCount" metric={overview?.staleCount} />
                    <MetricTile
                        label="latestBarTime"
                        value={<MetricText>{overview ? dateText(overview.latestBarTime) : stableFactPendingText()}</MetricText>}
                        detail={overview?.latestBarTime ?? t('pages:noDataUnknownRemainEmptyStates')}
                    />
                    <MetricTile
                        label="earliestBarTime"
                        value={<MetricText>{overview ? dateText(overview.earliestBarTime) : stableFactPendingText()}</MetricText>}
                        detail={overview?.earliestBarTime ?? t('pages:noDataUnknownRemainEmptyStates')}
                    />
                    <MetricTile
                        label="lastSuccessAt"
                        value={<MetricText>{overview ? dateText(overview.lastSuccessAt) : stableFactPendingText()}</MetricText>}
                        detail={overview?.lastSuccessAt ?? t('pages:noIngestionSuccessFacts')}
                    />
                    <MetricTile
                        label="lastFailureAt"
                        value={<MetricText>{overview ? dateText(overview.lastFailureAt) : stableFactPendingText()}</MetricText>}
                        detail={overview?.lastFailureAt ?? t('pages:noIngestionFailureFacts')}
                    />
                    <MetricTile
                        label="lastIngestionRunId"
                        value={<MetricText>{overview?.lastIngestionRunId ?? stableFactPendingText()}</MetricText>}
                        detail={t('pages:onlyRunIdsAreDisplayedNeverRawRequestsHeadersOrCredentials')}
                    />
                    <MetricTile
                        label="sourceHealth"
                        value={<MarketDataStatusBadge status={overview?.sourceHealth ?? (submittedQuery ? 'UNKNOWN' : 'NO_DATA')} />}
                        detail={t('pages:sourcehealthDiagnosesDataSourceHealthNotProviderTradingPermissions')}
                    />
                    <MetricTile
                        label="freshnessStatus"
                        value={<MarketDataStatusBadge status={overview?.freshnessStatus ?? (submittedQuery ? 'UNKNOWN' : 'NO_DATA')} />}
                        detail={t('pages:noDataUnknownExplicitlyDoNotIndicateAPass')}
                    />
                    <MetricTile
                        label="qualityStatus"
                        value={<MarketDataStatusBadge status={overview?.qualityStatus ?? (submittedQuery ? 'UNKNOWN' : 'NO_DATA')} />}
                        detail={t('pages:incompleteInvalidGapDetectedRemainVisible')}
                    />
                </div>

                <Descriptions
                    title="dataOriginSummary"
                    size="small"
                    column={{xs: 1, sm: 2, md: 3}}
                    items={[
                        {key: 'requestedDataOrigin', label: 'requestedDataOrigin', children: <ScopeValue value={overview?.dataOriginSummary.requestedDataOrigin} />},
                        {key: 'effectiveDataOrigin', label: 'effectiveDataOrigin', children: <MarketDataOriginBadge origin={overview?.dataOriginSummary.effectiveDataOrigin ?? 'UNKNOWN'} />},
                        {key: 'localDbBars', label: 'localDbBars', children: <MetricText>{overview?.dataOriginSummary.localDbBars ?? stableFactPendingText()}</MetricText>},
                        {key: 'fixtureBars', label: 'fixtureBars', children: <MetricText>{overview?.dataOriginSummary.fixtureBars ?? stableFactPendingText()}</MetricText>},
                        {key: 'unknownOriginBars', label: 'unknownOriginBars', children: <MetricText>{overview?.dataOriginSummary.unknownOriginBars ?? stableFactPendingText()}</MetricText>},
                        {key: 'supportLevel', label: 'supportLevel', children: <MetricText>{overview?.dataOriginSummary.supportLevel ?? stableFactPendingText()}</MetricText>},
                    ]}
                />
                <Typography.Text type="secondary">
                    {t('pages:effectivedataoriginLocalDbMeansLocalFactsAreAggregatedReadOnlyPublicOutboundInARequestOrHistoricalDe')}</Typography.Text>

                <Descriptions
                    title="datasetCoverageSummary"
                    size="small"
                    column={{xs: 1, sm: 2, md: 4}}
                    items={[
                        {key: 'datasetCount', label: 'datasetCount', children: <MetricText>{overview?.datasetCoverageSummary.datasetCount ?? stableFactPendingText()}</MetricText>},
                        {key: 'coverageExpected', label: 'expectedBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.expectedBars)}</MetricText>},
                        {key: 'actualBars', label: 'actualBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.actualBars)}</MetricText>},
                        {key: 'missingBars', label: 'missingBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.missingBars)}</MetricText>},
                        {key: 'duplicateBars', label: 'duplicateBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.duplicateBars)}</MetricText>},
                        {key: 'invalidBars', label: 'invalidBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.invalidBars)}</MetricText>},
                        {key: 'latestDatasetId', label: 'latestDatasetId', children: <ScopeValue value={overview?.datasetCoverageSummary.latestDatasetId} />},
                        {key: 'latestCoverageAt', label: 'latestCoverageAt', children: <MetricText>{overview ? dateText(overview.datasetCoverageSummary.latestCoverageAt) : stableFactPendingText()}</MetricText>},
                    ]}
                />

                <Table<MarketdataQualityIssue>
                    rowKey={(record) => `${record.code}-${record.severity}-${record.message}`}
                    columns={qualityIssueColumns}
                    dataSource={overview?.topIssues ?? []}
                    loading={loading || fetching}
                    pagination={false}
                    size="small"
                    scroll={{x: 760}}
                    locale={{emptyText: t('pages:noTopissuesWereReturnedThisDoesNotGrantTradingAuthorization')}}
                />
            </div>
        </Card>
    );
}

const jobColumns = (
    onRunOnce: (jobId: string) => void,
    pendingJobId: string | null,
): ColumnsType<MarketdataIngestionJob> => [
    {title: t('pages:jobId'), dataIndex: 'jobId', key: 'jobId', width: 260, ellipsis: true},
    {title: t('pages:exchange'), dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: t('pages:market'), dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: t('pages:symbol'), dataIndex: 'symbol', key: 'symbol', width: 130},
    {title: t('pages:interval'), dataIndex: 'interval', key: 'interval', width: 100},
    {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag>},
    {title: t('pages:start2'), dataIndex: 'startTime', key: 'startTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: t('pages:end'), dataIndex: 'endTime', key: 'endTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: t('pages:updated2'), dataIndex: 'updatedAt', key: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value)},
    {
        title: t('pages:action2'),
        key: 'action',
        fixed: 'right',
        width: 130,
        render: (_, record) => (
            <Button size="small" loading={pendingJobId === record.jobId} onClick={() => onRunOnce(record.jobId)}>
                {t('pages:runOnce')}</Button>
        ),
    },
];

const runColumns: ColumnsType<MarketdataIngestionRun> = [
    {get title() { return t('pages:runId'); }, dataIndex: 'runId', key: 'runId', width: 260, ellipsis: true},
    {get title() { return t('pages:status'); }, dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag>},
    {get title() { return t('pages:fetched'); }, dataIndex: 'fetchedBars', key: 'fetchedBars', width: 100},
    {get title() { return t('pages:inserted'); }, dataIndex: 'insertedBars', key: 'insertedBars', width: 100},
    {get title() { return t('pages:updated2'); }, dataIndex: 'updatedBars', key: 'updatedBars', width: 100},
    {get title() { return t('pages:skipped'); }, dataIndex: 'skippedBars', key: 'skippedBars', width: 100},
    {get title() { return t('pages:started'); }, dataIndex: 'startedAt', key: 'startedAt', width: 180, render: (value: string) => formatDateTime(value)},
    {get title() { return t('pages:finished'); }, dataIndex: 'finishedAt', key: 'finishedAt', width: 180, render: (value?: string | null) => value ? formatDateTime(value) : '-'},
    {get title() { return t('pages:error'); }, dataIndex: 'errorMessage', key: 'errorMessage', width: 280, ellipsis: true, render: (value?: string | null) => value || '-'},
];

const datasetColumns = (
    onRefreshQuality: (datasetId: string) => void,
    pendingDatasetId: string | null,
): ColumnsType<MarketdataDataset> => [
    {title: t('pages:datasetId'), dataIndex: 'datasetId', key: 'datasetId', width: 260, ellipsis: true},
    {title: t('pages:name'), dataIndex: 'datasetName', key: 'datasetName', width: 180},
    {title: t('pages:exchange'), dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: t('pages:market'), dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: t('pages:symbol'), dataIndex: 'symbol', key: 'symbol', width: 130},
    {title: t('pages:interval'), dataIndex: 'interval', key: 'interval', width: 100},
    {title: t('pages:status'), dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'READY' ? 'green' : value === 'INVALID' ? 'red' : 'blue'}>{value}</Tag>},
    {title: t('pages:quality'), dataIndex: 'qualityStatus', key: 'qualityStatus', width: 140, render: (value: string) => <Tag color={value === 'OK' ? 'green' : value === 'GAP_DETECTED' ? 'orange' : 'red'}>{value}</Tag>},
    {title: t('pages:bars'), dataIndex: 'barCount', key: 'barCount', width: 100},
    {title: t('pages:gaps'), dataIndex: 'gapCount', key: 'gapCount', width: 100},
    {title: t('pages:start2'), dataIndex: 'startTime', key: 'startTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: t('pages:end'), dataIndex: 'endTime', key: 'endTime', width: 180, render: (value: string) => formatDateTime(value)},
    {
        title: t('pages:action2'),
        key: 'action',
        fixed: 'right',
        width: 150,
        render: (_, record) => (
            <Button
                size="small"
                loading={pendingDatasetId === record.datasetId}
                onClick={() => onRefreshQuality(record.datasetId)}
            >
                {t('pages:refreshQuality')}</Button>
        ),
    },
];

export function MarketdataPage() {
    const {i18n: pageI18n} = useTranslation('pages');
    const [form] = useLocalizedForm<MarketdataBarsFormValues>();
    const [jobForm] = useLocalizedForm<CreateMarketdataIngestionJobFormValues>();
    const [datasetForm] = useLocalizedForm<CreateMarketdataDatasetFormValues>();
    const [searchParams] = useSearchParams();
    const [messageApi, contextHolder] = message.useMessage();
    const queryClient = useQueryClient();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const [submittedQuery, setSubmittedQuery] = useState<MarketdataBarsQuery | null>(null);
    const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
    const [pendingJobId, setPendingJobId] = useState<string | null>(null);
    const [pendingDatasetId, setPendingDatasetId] = useState<string | null>(null);

    // Chart foundation 使用 additive v2 CSS vars；页级注入不改全局 AppProviders。
    useEffect(() => {
        applyNqCssVars();
    }, []);

    const runtimeDeepLinkValues = useMemo(() => readRuntimeDeepLinkValues(searchParams), [searchParams]);
    const hasRuntimeDeepLink = hasRuntimeDeepLinkValues(runtimeDeepLinkValues);

    useEffect(() => {
        if (!hasRuntimeDeepLink) {
            return;
        }
        form.setFieldsValue(runtimeDeepLinkValues);
    }, [form, hasRuntimeDeepLink, runtimeDeepLinkValues]);

    const barsQuery = useQuery({
        queryKey: marketdataQueryKeys.bars(submittedQuery),
        queryFn: () => marketdataApi.listBars(submittedQuery as MarketdataBarsQuery),
        enabled: submittedQuery !== null,
    });
    const readinessQueryInput = useMemo(
        () => submittedQuery ? toReadinessQuery(submittedQuery) : null,
        [submittedQuery],
    );
    const readinessQuery = useQuery({
        queryKey: marketdataQueryKeys.readiness(readinessQueryInput),
        queryFn: () => marketdataApi.getReadiness(readinessQueryInput as MarketdataReadinessQuery),
        enabled: readinessQueryInput !== null,
        retry: false,
    });
    const qualityOverviewQueryInput = useMemo(
        () => submittedQuery ? toQualityOverviewQuery(submittedQuery) : null,
        [submittedQuery],
    );
    const qualityOverviewQuery = useQuery({
        queryKey: marketdataQueryKeys.qualityOverview(qualityOverviewQueryInput),
        queryFn: () => marketdataApi.getQualityOverview(qualityOverviewQueryInput as MarketdataQualityOverviewQuery),
        enabled: qualityOverviewQueryInput !== null,
        retry: false,
    });
    const jobsQuery = useQuery({
        queryKey: marketdataQueryKeys.ingestionJobs(),
        queryFn: marketdataApi.listIngestionJobs,
    });
    const runsQuery = useQuery({
        queryKey: marketdataQueryKeys.ingestionRuns(selectedJobId),
        queryFn: () => marketdataApi.listIngestionRuns(selectedJobId as string),
        enabled: selectedJobId !== null,
    });
    const datasetsQuery = useQuery({
        queryKey: marketdataQueryKeys.datasets(),
        queryFn: marketdataApi.listDatasets,
    });
    const createJobMutation = useMutation({
        mutationFn: marketdataApi.createIngestionJob,
        onSuccess: async (job) => {
            setSelectedJobId(job.jobId);
            messageApi.success(t('pages:marketDataIngestionJobCreated'));
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.ingestionJobs()});
        },
        onError: (error) => showApiError(error as AppApiError, messageApi),
    });
    const runOnceMutation = useMutation({
        mutationFn: marketdataApi.runIngestionJobOnce,
        onMutate: (jobId) => setPendingJobId(jobId),
        onSuccess: async (run) => {
            setSelectedJobId(run.jobId);
            messageApi.info(`Run finished: ${run.status}`);
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.ingestionJobs()});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.ingestionRuns(run.jobId)});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.barsAll()});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.readinessAll()});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.qualityOverviewAll()});
        },
        onError: (error) => showApiError(error as AppApiError, messageApi),
        onSettled: () => setPendingJobId(null),
    });
    const createDatasetMutation = useMutation({
        mutationFn: marketdataApi.createDataset,
        onSuccess: async (dataset) => {
            messageApi.success(`Dataset created: ${dataset.qualityStatus}`);
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.datasets()});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.qualityOverviewAll()});
        },
        onError: (error) => showApiError(error as AppApiError, messageApi),
    });
    const refreshDatasetMutation = useMutation({
        mutationFn: marketdataApi.refreshDatasetQuality,
        onMutate: (datasetId) => setPendingDatasetId(datasetId),
        onSuccess: async (dataset) => {
            messageApi.info(`Dataset quality: ${dataset.qualityStatus}`);
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.datasets()});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.qualityOverviewAll()});
        },
        onError: (error) => showApiError(error as AppApiError, messageApi),
        onSettled: () => setPendingDatasetId(null),
    });
    const bars = barsQuery.data ?? [];
    const backendReadiness = useMemo(
        () => isMarketdataReadinessSummary(readinessQuery.data) ? readinessQuery.data : null,
        [readinessQuery.data],
    );
    const qualityOverview = useMemo(
        () => isMarketdataQualityOverview(qualityOverviewQuery.data) ? qualityOverviewQuery.data : null,
        [qualityOverviewQuery.data],
    );
    const readinessLoading = submittedQuery !== null && (readinessQuery.isLoading || readinessQuery.isFetching);
    const qualityOverviewLoading = submittedQuery !== null && qualityOverviewQuery.isLoading;
    const qualityOverviewUnavailable = submittedQuery !== null
        && !qualityOverviewQuery.isLoading
        && !qualityOverviewQuery.isError
        && qualityOverviewQuery.data !== undefined
        && qualityOverview === null;
    const readinessUnavailable = submittedQuery !== null && !readinessLoading && backendReadiness === null;
    const readinessError = readinessQuery.error ? formatApiError(readinessQuery.error as AppApiError) : null;
    const chartBars = useMemo(() => bars.map(toNqKlineBar), [bars]);
    const barsQuality = useMemo(() => summarizeBarsQuality(bars, submittedQuery?.interval), [bars, submittedQuery?.interval]);
    const barsFreshness = useMemo(
        () => summarizeBarsFreshness(submittedQuery, bars, barsQuality, barsQuery.error, barsQuery.isLoading),
        [bars, barsQuality, barsQuery.error, barsQuery.isLoading, submittedQuery, pageI18n.resolvedLanguage],
    );
    const dataQualityReadiness = useMemo(
        () => summarizeDataQualityReadiness(
            submittedQuery,
            bars,
            barsQuality,
            barsFreshness,
            barsQuery.error,
            barsQuery.isLoading,
        ),
        [bars, barsFreshness, barsQuality, barsQuery.error, barsQuery.isLoading, submittedQuery, pageI18n.resolvedLanguage],
    );
    const firstBar = bars.length > 0 ? bars[0] : null;
    const lastBar = bars.length > 0 ? bars[bars.length - 1] : null;
    const chartError = barsQuery.error ? formatApiError(barsQuery.error as AppApiError) : null;
    const sandboxSourceDisplay = useMemo(
        () => summarizeSandboxSourceDisplay(
            submittedQuery,
            backendReadiness,
            dataQualityReadiness,
            readinessLoading,
            chartError,
        ),
        [backendReadiness, chartError, dataQualityReadiness, readinessLoading, submittedQuery, pageI18n.resolvedLanguage],
    );
    const chartEmptyText = submittedQuery
        ? t('pages:noOhlcvBarsReturnedForThisQuery')
        : t('pages:searchToViewCandlesticks');
    const chartSourceLabel = submittedQuery
        ? `${submittedQuery.exchangeCode} ${submittedQuery.symbol} ${submittedQuery.interval}`
        : t('pages:marketDataBars');

    return (
        <NqPageScaffold>
            {contextHolder}
            <Card className="page-card" bordered={false}>
                <NqPageHeader
                    title={t('pages:marketData')}
                    description={t('pages:queryHistoricalSpotOhlcvManageIngestionJobsAndDatasetsWithinTheExchangesSymbolsAndIntervalsAcceptedF')}
                    badge={t('pages:marketData')}
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title={t('pages:queryFilters')}
                extra={<Button type="primary" onClick={() => form.submit()}>{t('pages:search')}</Button>}
            >
                <Form<MarketdataBarsFormValues>
                    form={form}
                    layout="vertical"
                    initialValues={{
                        exchangeCode: contextExchangeCode ?? 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                        page: 0,
                        size: 100,
                    }}
                    onFinish={(values) => setSubmittedQuery(normalizeBarsQuery(values))}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label={t('pages:exchange')} name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:market')} name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:tradingPair')} name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:interval')} name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:startTime')} name="startTime" rules={[{required: true, message: t('pages:selectAStartTime')}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label={t('pages:endTime')} name="endTime" rules={[{required: true, message: t('pages:selectAnEndTime')}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                <Typography.Text type="secondary">{t('pages:defaultExchangeFromTheCurrentAccountContext')}{contextExchangeCode ?? t('pages:notSelected')}</Typography.Text>
                {hasRuntimeDeepLink ? (
                    <Alert
                        data-testid="marketdata-runtime-deep-link"
                        type="info"
                        showIcon
                        style={{marginTop: 12}}
                        message={t('pages:runtimeReadinessContextApplied')}
                        description={t('pages:exchangecodeMarkettypeSymbolIntervalArePrefilledFromRuntimeReadinessNoIngestionOrWriteEndpointIsTrig')}
                    />
                ) : null}
            </Card>
            <Card className="page-section" bordered={false} title={t('pages:candlestickReadinessView')}>
                <div data-testid="marketdata-kline-readiness-view" style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                    <Descriptions
                        size="small"
                        column={{xs: 1, sm: 2, md: 3}}
                        items={[
                            {key: 'exchange', label: t('pages:exchange'), children: <ExchangeBadge code={submittedQuery?.exchangeCode}/>},
                            {key: 'symbol', label: t('pages:instrument2'), children: <MetricText>{submittedQuery?.symbol ?? '-'}</MetricText>},
                            {key: 'interval', label: t('pages:timeframe'), children: <MetricText>{submittedQuery?.interval ?? '-'}</MetricText>},
                            {key: 'barCount', label: t('pages:barCount2'), children: <MetricText>{bars.length}</MetricText>},
                            {key: 'lastBar', label: t('pages:lastBarTime'), children: <MetricText>{lastBar ? formatDateTime(lastBar.closeTime ?? lastBar.openTime) : '-'}</MetricText>},
                            {key: 'quality', label: t('pages:dataQuality'), children: <QualityTags quality={barsQuality}/>},
                        ]}
                    />
                    <Space size={12} wrap>
                        <DataFreshness
                            source={chartSourceLabel}
                            state={barsFreshness.state}
                            detail={barsFreshness.detail}
                            inline
                        />
                        {barsQuality.gapCount > 0 ? (
                            <Tag color="orange">{t('pages:gapQualitystatus')}{barsQuality.gapCount}</Tag>
                        ) : null}
                        {!barsQuality.hasQualityStatus && bars.length > 0 ? (
                            <Tag>{t('pages:qualitystatusMissingNonBlocking')}</Tag>
                        ) : null}
                    </Space>
                    {barsFreshness.state === 'stale' ? (
                        <Alert
                            type="warning"
                            showIcon
                            message={t('pages:marketDataBarsAreStale')}
                            description={t('pages:theLastBarDoesNotCoverTheEndOfTheQueryThisViewShowsHistoricalBarsWithoutLiveRefreshOrWebsocketBackfi')}
                        />
                    ) : null}
                    {barsQuality.gapCount > 0 ? (
                        <Alert
                            type="warning"
                            showIcon
                            message={t('pages:marketDataQualityDegraded')}
                            description={t('pages:theBackendReturnedANonOkQualitystatusExistingBarsRemainVisibleIngestionAndDatasetQualityWorkflowsHan')}
                        />
                    ) : null}
                    {!barsQuality.hasQualityStatus && bars.length > 0 ? (
                        <Alert
                            type="info"
                            showIcon
                            message={t('pages:qualitystatusUnavailable')}
                            description={t('pages:theBarsResponseHasNoQualitystatusThisAdvisoryDoesNotFabricateAGapStatus')}
                        />
                    ) : null}
                    <div
                        style={{
                            display: 'grid',
                            gridTemplateColumns: 'minmax(0, 1fr)',
                            gap: 16,
                        }}
                    >
                        <NqKlineChart
                            bars={chartBars}
                            loading={barsQuery.isLoading}
                            error={chartError}
                            stale={barsFreshness.stale}
                            staleDetail={barsFreshness.detail}
                            sourceLabel={chartSourceLabel}
                            title={t('pages:ohlcvCandlesticks')}
                            emptyText={chartEmptyText}
                            height={320}
                        />
                        <NqVolumeChart
                            bars={chartBars}
                            loading={barsQuery.isLoading}
                            error={chartError}
                            sourceLabel={chartSourceLabel}
                            title={t('pages:volume')}
                            emptyText={submittedQuery ? t('pages:noVolumeBarsReturnedForThisQuery') : t('pages:searchToViewVolume')}
                            height={180}
                        />
                    </div>
                    <Typography.Text type="secondary">
                        {t('pages:thisViewUsesApiMarketdataBarsAndMarketdataapiListbarsItHasNoWebsocketPrivateExchangeFeedTradingSigna')}</Typography.Text>
                </div>
            </Card>
            <MarketdataQualityCenterPanel
                overview={qualityOverview}
                submittedQuery={submittedQuery}
                loading={qualityOverviewLoading}
                fetching={qualityOverviewQuery.isFetching}
                error={qualityOverviewQuery.error}
                unavailable={qualityOverviewUnavailable}
            />
            <Card className="page-section" bordered={false} title={t('pages:dataQualityAndReadiness')}>
                <div data-testid="marketdata-quality-readiness-view" style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                    <MarketDataQualityNotice />
                    <Descriptions
                        size="small"
                        column={{xs: 1, sm: 2, md: 3}}
                        items={[
                            {key: 'queryExchange', label: t('pages:exchange'), children: <MetricText>{submittedQuery?.exchangeCode ?? '-'}</MetricText>},
                            {key: 'queryInstrument', label: t('pages:instrumentSymbol'), children: <MetricText>{submittedQuery?.symbol ?? '-'}</MetricText>},
                            {key: 'queryInterval', label: t('pages:intervalTimeframe'), children: <MetricText>{submittedQuery?.interval ?? '-'}</MetricText>},
                            {key: 'queryStart', label: t('pages:queryStart'), children: <MetricText>{submittedQuery ? formatDateTime(submittedQuery.startTime) : '-'}</MetricText>},
                            {key: 'queryEnd', label: t('pages:queryEnd'), children: <MetricText>{submittedQuery ? formatDateTime(submittedQuery.endTime) : '-'}</MetricText>},
                            {
                                key: 'readinessStatus',
                                label: t('pages:readinessStatus'),
                                children: (
                                    <Tag color={readinessStatusColor(backendReadiness?.status ?? dataQualityReadiness.status)}>
                                        {backendReadiness?.status ?? dataQualityReadiness.title}
                                    </Tag>
                                ),
                            },
                            {
                                key: 'sourceHealthStatus',
                                label: t('pages:sourceHealthStatus'),
                                children: (
                                    <MarketDataStatusBadge status={backendReadiness ? readinessSourceHealth(backendReadiness) : (readinessLoading ? 'LOADING' : 'UNAVAILABLE')} />
                                ),
                            },
                            {
                                key: 'sourceStatus',
                                label: t('pages:sourceStatus2'),
                                children: (
                                    <MarketDataStatusBadge status={backendReadiness?.sourceStatus ?? (readinessLoading ? 'LOADING' : 'UNKNOWN')} />
                                ),
                            },
                            {
                                key: 'dataOrigin',
                                label: t('pages:dataOrigin'),
                                children: (
                                    <MarketDataOriginBadge origin={backendReadiness?.dataOrigin ?? 'UNKNOWN'} />
                                ),
                            },
                            {
                                key: 'gapStatus',
                                label: t('pages:gapStatus'),
                                children: (
                                    <MarketDataStatusBadge status={backendReadiness?.gapStatus ?? 'UNKNOWN'} />
                                ),
                            },
                            {
                                key: 'backendSupportLevel',
                                label: t('pages:backendSupport'),
                                children: <MetricText>{backendReadiness?.backendSupportLevel ?? (submittedQuery ? 'UNAVAILABLE' : '-')}</MetricText>,
                            },
                        ]}
                    />
                    <SandboxSourceDisplay summary={sandboxSourceDisplay} />
                    <MarketDataSourceHealthTable
                        readiness={backendReadiness}
                        submittedQuery={submittedQuery}
                        loading={readinessLoading}
                    />
                    <div
                        style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                            gap: 12,
                        }}
                    >
                        <MetricTile
                            label={t('pages:barsLoaded')}
                            value={<MetricText>{backendReadiness?.barCount ?? bars.length}</MetricText>}
                            detail={backendReadiness ? t('pages:fromApiMarketdataReadiness') : (submittedQuery ? t('pages:fromApiMarketdataBarsFallback') : t('pages:pendingQuery'))}
                        />
                        <MetricTile
                            label={t('pages:firstBarTime')}
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.firstBarTime) : (firstBar ? formatDateTime(firstBar.openTime) : '-')}</MetricText>}
                            detail={backendReadiness ? (backendReadiness.firstBarTime ?? t('pages:noData2')) : (firstBar ? firstBar.openTime : t('pages:noData2'))}
                        />
                        <MetricTile
                            label={t('pages:lastBarTime')}
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastBarTime) : (lastBar ? formatDateTime(lastBar.closeTime ?? lastBar.openTime) : '-')}</MetricText>}
                            detail={backendReadiness ? (backendReadiness.lastBarTime ?? t('pages:noData2')) : (lastBar ? (lastBar.closeTime ?? lastBar.openTime) : t('pages:noData2'))}
                        />
                        <MetricTile
                            label={t('pages:latestClose')}
                            value={<MetricText>{lastBar ? formatNumber(lastBar.closePrice, 8) : '-'}</MetricText>}
                            detail={lastBar ? t('pages:lastReturnedBar') : t('pages:noData2')}
                        />
                        <MetricTile
                            label={t('pages:latestVolume')}
                            value={<MetricText>{lastBar ? formatNumber(lastBar.volume, 8) : '-'}</MetricText>}
                            detail={lastBar ? t('pages:lastReturnedBar') : t('pages:noData2')}
                        />
                        <MetricTile
                            label={t('pages:freshness2')}
                            value={(
                                <DataFreshness
                                    source={backendReadiness ? t('pages:backendReadiness') : 'bars'}
                                    state={backendReadiness ? readinessFreshnessState(backendReadiness.freshnessStatus) : barsFreshness.state}
                                    detail={backendReadiness?.freshnessStatus ?? barsFreshness.detail}
                                    inline
                                />
                            )}
                            detail={backendReadiness ? t('pages:fromApiMarketdataReadiness') : (barsFreshness.stale ? t('pages:staleByQueryIntervalEstimate') : t('pages:frontendEstimate'))}
                        />
                        <MetricTile
                            label={t('pages:qualityStatus')}
                            value={backendReadiness ? <ReadinessQualityTags readiness={backendReadiness}/> : <QualityTags quality={barsQuality}/>}
                            detail={backendReadiness ? `ok=${backendReadiness.qualityStatusSummary.okCount}, gap=${backendReadiness.qualityStatusSummary.gapSignalCount}, invalid=${backendReadiness.qualityStatusSummary.invalidCount}` : (barsQuality.hasQualityStatus ? t('pages:aggregatedFromBarData') : t('pages:unavailableReadinessUnavailable'))}
                        />
                        <MetricTile
                            label={t('pages:gapCount')}
                            value={<MetricText>{backendReadiness ? countText(backendReadiness.gapCount) : (barsQuality.gapDetectionUnavailable ? '-' : barsQuality.gapCount)}</MetricText>}
                            detail={backendReadiness ? `expected=${countText(backendReadiness.expectedBarCount)}` : (barsQuality.gapDetectionUnavailable ? t('pages:gapDetectionUnavailable') : `quality=${barsQuality.qualityGapCount}, sequence=${barsQuality.sequenceGapCount ?? '-'}`)}
                        />
                        <MetricTile
                            label={t('pages:gapStatus')}
                            value={<MarketDataStatusBadge status={backendReadiness?.gapStatus ?? 'UNKNOWN'} />}
                            detail={backendReadiness ? `missingFrom=${dateText(backendReadiness.missingFrom)}, missingTo=${dateText(backendReadiness.missingTo)}` : stableFactPendingText()}
                        />
                        <MetricTile
                            label={t('pages:unknownQualityCount')}
                            value={<MetricText>{backendReadiness?.unknownQualityCount ?? barsQuality.unknownQualityCount}</MetricText>}
                            detail={backendReadiness ? t('pages:fromBackendQualitystatussummary') : (barsQuality.unknownQualityCount > 0 ? t('pages:qualitystatusMissingOnReturnedBars') : 'none')}
                        />
                        <MetricTile
                            label={t('pages:sourceHealth')}
                            value={(
                                <MarketDataStatusBadge status={backendReadiness ? readinessSourceHealth(backendReadiness) : (readinessLoading ? 'LOADING' : 'UNAVAILABLE')} />
                            )}
                            detail={backendReadiness?.sourceHealthReason ?? (readinessLoading ? t('pages:loadingApiMarketdataReadiness') : dataQualityReadiness.sourceHealthDetail)}
                        />
                        <MetricTile
                            label={t('pages:sourceStatus2')}
                            value={<MarketDataStatusBadge status={backendReadiness?.sourceStatus ?? 'UNKNOWN'} />}
                            detail={t('pages:sourcestatusDescribesDiagnosticsOnlyNotProviderOrTradingAuthorization')}
                        />
                        <MetricTile
                            label={t('pages:dataOrigin')}
                            value={<MarketDataOriginBadge origin={backendReadiness?.dataOrigin ?? 'UNKNOWN'} />}
                            detail={t('pages:localDbFixtureFakeServerPublicCandidateAreDiagnosticOriginsNotProofOfRealExternalConnections')}
                        />
                        <MetricTile
                            label={t('pages:errorCategory')}
                            value={<MarketDataStatusBadge status={backendReadiness?.errorCategory ?? 'UNKNOWN'} />}
                            detail={backendReadiness ? optionalText(backendReadiness.degradedReason ?? backendReadiness.disabledReason) : stableFactPendingText()}
                        />
                        <MetricTile
                            label={t('pages:errorRate')}
                            value={<MetricText>{backendReadiness ? percentText(backendReadiness.errorRate) : stableFactPendingText()}</MetricText>}
                            detail={t('pages:nullMeansNoStableFactItIsNotDisplayedAs0')}
                        />
                        <MetricTile
                            label={t('pages:latency')}
                            value={<MetricText>{backendReadiness ? numberText(backendReadiness.latencyMs, ' ms') : stableFactPendingText()}</MetricText>}
                            detail={t('pages:nullMeansNoStableFactLatencyThresholdsAreNotHardcoded')}
                        />
                        <MetricTile
                            label={t('pages:missingFrom')}
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.missingFrom) : stableFactPendingText()}</MetricText>}
                            detail={t('pages:gapStartNullDoesNotMeanNoGap')}
                        />
                        <MetricTile
                            label={t('pages:missingTo')}
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.missingTo) : stableFactPendingText()}</MetricText>}
                            detail={t('pages:gapEndNullDoesNotMeanNoGap')}
                        />
                        <MetricTile
                            label={t('pages:lastObserved')}
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastObservedAt) : stableFactPendingText()}</MetricText>}
                            detail={t('pages:latestObservedLocalMarketDataOrIngestionFact')}
                        />
                        <MetricTile
                            label={t('pages:updatedAt')}
                            value={<MetricText>{backendReadiness ? dateText(readinessUpdatedAt(backendReadiness)) : stableFactPendingText()}</MetricText>}
                            detail={backendReadiness ? t('pages:usesUpdatedatWithGeneratedatAsACompatibleFallback') : stableFactPendingText()}
                        />
                        <MetricTile
                            label={t('pages:staleThreshold')}
                            value={<MetricText>{backendReadiness ? numberText(backendReadiness.staleAfterSeconds, ' s') : stableFactPendingText()}</MetricText>}
                            detail={t('pages:theStaleThresholdComesFromBackendReadOnlyFactsTheFrontendDoesNotDeriveBusinessThresholds')}
                        />
                        <MetricTile
                            label={t('pages:degradedReason')}
                            value={<MetricText>{backendReadiness ? optionalText(backendReadiness.degradedReason) : stableFactPendingText()}</MetricText>}
                            detail={t('pages:onlySanitizedDiagnosticReasonsAreShown')}
                        />
                        <MetricTile
                            label={t('pages:disabledReason')}
                            value={<MetricText>{backendReadiness ? optionalText(backendReadiness.disabledReason) : stableFactPendingText()}</MetricText>}
                            detail={t('pages:onlySanitizedDisabledReasonsAreShown')}
                        />
                        <MetricTile
                            label={t('pages:traceRequest')}
                            value={<MetricText>{backendReadiness ? `${optionalText(backendReadiness.traceId)} / ${optionalText(backendReadiness.requestId)}` : stableFactPendingText()}</MetricText>}
                            detail={t('pages:traceidRequestidMayBeEmptyCredentialsHeadersAndRawPayloadsMustNotBeDisplayed')}
                        />
                        <MetricTile
                            label={t('pages:backendSupport')}
                            value={<MetricText>{backendReadiness?.backendSupportLevel ?? (submittedQuery ? 'UNAVAILABLE' : '-')}</MetricText>}
                            detail={backendReadiness ? `generated ${formatDateTime(backendReadiness.generatedAt)}` : t('pages:readinessApiFallback')}
                        />
                        <MetricTile
                            label={t('pages:lastSuccess')}
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastSuccessAt) : '-'}</MetricText>}
                            detail={backendReadiness?.lastSuccessAt ?? stableFactPendingText()}
                        />
                        <MetricTile
                            label={t('pages:lastFailure')}
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastFailureAt) : '-'}</MetricText>}
                            detail={backendReadiness?.lastFailureAt ?? stableFactPendingText()}
                        />
                    </div>
                    <Space size={8} wrap>
                        <Tag color={readinessStatusColor(backendReadiness?.status ?? dataQualityReadiness.status)}>
                            {backendReadiness?.status ?? dataQualityReadiness.status}
                        </Tag>
                        {backendReadiness ? (
                            <>
                                <Tag color={readinessStatusColor(backendReadiness.freshnessStatus)}>
                                    {t('pages:freshness')}{backendReadiness.freshnessStatus}
                                </Tag>
                                <Tag color={readinessStatusColor(readinessSourceHealth(backendReadiness))}>
                                    {t('pages:sourceHealth2')}{readinessSourceHealth(backendReadiness)}
                                </Tag>
                                <Tag color={readinessStatusColor(backendReadiness.gapStatus)}>
                                    {t('pages:gap')}{backendReadiness.gapStatus ?? 'UNKNOWN'}
                                </Tag>
                                <Tag color={readinessStatusColor(backendReadiness.sourceStatus)}>
                                    {t('pages:sourceStatus3')}{backendReadiness.sourceStatus ?? 'UNKNOWN'}
                                </Tag>
                                <Tag>{t('pages:dataOrigin2')}{backendReadiness.dataOrigin ?? 'UNKNOWN'}</Tag>
                                <Tag>{t('pages:backendSupport2')}{backendReadiness.backendSupportLevel}</Tag>
                            </>
                        ) : (
                            <Tag>{t('pages:sourceHealth2')}{readinessLoading ? 'LOADING' : 'UNAVAILABLE'}</Tag>
                        )}
                        {barsQuality.gapDetectionUnavailable ? (
                            <Tag>{t('pages:gapDetectionUnavailable')}</Tag>
                        ) : null}
                    </Space>
                    {chartError ? (
                        <Alert
                            type="error"
                            showIcon
                            message={t('pages:dataQualityUnavailable')}
                            description={t('pages:theBarsQueryFailedDataReadinessCannotBeInferredFromAFailedResponse')}
                        />
                    ) : null}
                    {!chartError && submittedQuery && bars.length === 0 ? (
                        <Alert
                            type="info"
                            showIcon
                            message={t('pages:noBarsReturned')}
                            description={t('pages:noBarsWereReturnedForThisWindowFreshnessGapsAndQualityRemainNoDataUnavailable')}
                        />
                    ) : null}
                    {barsQuality.gapDetectionUnavailable ? (
                        <Alert
                            type="info"
                            showIcon
                            message={t('pages:gapDetectionUnavailable')}
                            description={t('pages:qualitystatusAndSufficientIntervalOrTimeDataAreMissingThePageDoesNotFabricateGap0OrSourceHealthOk')}
                        />
                    ) : null}
                    {readinessUnavailable ? (
                        <Alert
                            type="warning"
                            showIcon
                            message={t('pages:marketDataSourceHealthUnavailable')}
                            description={readinessError
                                ? `readiness API failed: ${readinessError}; using bars-derived fallback only.`
                                : t('pages:readinessApiReturnedNoUsableSummaryOnlyBarDerivedFallbackIsAvailable')}
                        />
                    ) : null}
                    {backendReadiness ? (
                        <Alert
                            type={backendReadiness.status === 'FRESH' ? 'success' : backendReadiness.status === 'ERROR' ? 'error' : 'warning'}
                            showIcon
                            message={`MarketData readiness: ${backendReadiness.status}`}
                            description={t('pages:value1BackendSupportValue2ThisIsMarketDataDiagnosticsOnlyNotTradingAuthorization', {value1: backendReadiness.sourceHealthReason, value2: backendReadiness.backendSupportLevel})}
                        />
                    ) : (
                        <Alert
                            type={dataQualityReadiness.status === 'GOOD' ? 'success' : dataQualityReadiness.status === 'ERROR' ? 'error' : 'warning'}
                            showIcon
                            message={`MarketData readiness: ${dataQualityReadiness.title}`}
                            description={`${dataQualityReadiness.detail}. Source health is ${readinessLoading ? 'loading' : dataQualityReadiness.sourceHealthDetail}.`}
                        />
                    )}
                </div>
            </Card>
            <Card className="page-section" bordered={false} title={t('pages:barResults')}>
                {barsQuery.error ? (
                    <Alert type="error" showIcon message={t('pages:failedToQueryMarketDataBars')} description={formatApiError(barsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey={(record) => `${record.exchangeCode}-${record.marketType}-${record.symbol}-${record.interval}-${record.openTime}`}
                        columns={columns}
                        dataSource={barsQuery.data ?? []}
                        loading={barsQuery.isLoading || barsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1400}}
                    />
                )}
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title={t('pages:ingestionJobs')}
                extra={<Button type="primary" loading={createJobMutation.isPending} onClick={() => jobForm.submit()}>{t('pages:createJob')}</Button>}
            >
                <Form<CreateMarketdataIngestionJobFormValues>
                    form={jobForm}
                    layout="vertical"
                    initialValues={{
                        exchangeCode: 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                    }}
                    onFinish={(values) => createJobMutation.mutate(normalizeIngestionJob(values))}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label={t('pages:exchange')} name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:market')} name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:tradingPair')} name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:interval')} name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:startTime')} name="startTime" rules={[{required: true, message: t('pages:selectAStartTime')}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label={t('pages:endTime')} name="endTime" rules={[{required: true, message: t('pages:selectAnEndTime')}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                {jobsQuery.error ? (
                    <Alert type="error" showIcon message={t('pages:failedToQueryIngestionJobs')} description={formatApiError(jobsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey="jobId"
                        columns={jobColumns((jobId) => runOnceMutation.mutate(jobId), pendingJobId)}
                        dataSource={jobsQuery.data ?? []}
                        loading={jobsQuery.isLoading || jobsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1600}}
                        onRow={(record) => ({
                            onClick: () => setSelectedJobId(record.jobId),
                        })}
                    />
                )}
            </Card>
            <Card className="page-section" bordered={false} title={t('pages:runResults')}>
                {selectedJobId ? (
                    runsQuery.error ? (
                        <Alert type="error" showIcon message={t('pages:failedToQueryIngestionRuns')} description={formatApiError(runsQuery.error as AppApiError)} />
                    ) : (
                        <Table
                            rowKey="runId"
                            columns={runColumns}
                            dataSource={runsQuery.data ?? []}
                            loading={runsQuery.isLoading || runsQuery.isFetching}
                            pagination={{pageSize: 5, showSizeChanger: false}}
                            scroll={{x: 1500}}
                        />
                    )
                ) : (
                    <Alert type="info" showIcon message={t('pages:selectOrCreateAnIngestionJobToViewRunResults')} />
                )}
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title={t('pages:datasets')}
                extra={(
                    <Button
                        type="primary"
                        loading={createDatasetMutation.isPending}
                        onClick={() => datasetForm.submit()}
                    >
                        {t('pages:createDataset')}</Button>
                )}
            >
                <Form<CreateMarketdataDatasetFormValues>
                    form={datasetForm}
                    layout="vertical"
                    initialValues={{
                        datasetName: `BINANCE-BTC-USDT-1m-${Date.now()}`,
                        exchangeCode: 'BINANCE',
                        marketType: 'SPOT',
                        symbol: 'BTC-USDT',
                        interval: '1m',
                    }}
                    onFinish={(values) => createDatasetMutation.mutate({
                        ...normalizeDataset(values),
                        datasetName: values.datasetName || `BINANCE-BTC-USDT-1m-${Date.now()}`,
                    })}
                >
                    <Space align="start" size={16} wrap>
                        <Form.Item label={t('pages:datasetName')} name="datasetName">
                            <Input style={{width: 260}} />
                        </Form.Item>
                        <Form.Item label={t('pages:exchange')} name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:market')} name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:tradingPair')} name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:interval')} name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label={t('pages:startTime')} name="startTime" rules={[{required: true, message: t('pages:selectAStartTime')}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label={t('pages:endTime')} name="endTime" rules={[{required: true, message: t('pages:selectAnEndTime')}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                {datasetsQuery.error ? (
                    <Alert type="error" showIcon message={t('pages:failedToQueryMarketDataDatasets')} description={formatApiError(datasetsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey="datasetId"
                        columns={datasetColumns((datasetId) => refreshDatasetMutation.mutate(datasetId), pendingDatasetId)}
                        dataSource={datasetsQuery.data ?? []}
                        loading={datasetsQuery.isLoading || datasetsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1900}}
                        locale={{emptyText: t('pages:noMarketDataDatasetsCreateOneFirst')}}
                    />
                )}
            </Card>
        </NqPageScaffold>
    );
}

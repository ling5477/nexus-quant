import {Alert, Button, Card, DatePicker, Descriptions, Form, Input, Select, Space, Table, Tag, Typography, message} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useEffect, useMemo, useState, type ReactNode} from 'react';
import {useSearchParams} from 'react-router-dom';

import {formatApiError} from '@/api/errors';
import {marketdataApi} from '@/api/marketdata';
import {marketdataQueryKeys} from '@/api/query-keys';
import {PageHero} from '@/components/page/PageHero';
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
    {title: 'Exchange', dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: 'Market', dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 140},
    {title: 'Interval', dataIndex: 'interval', key: 'interval', width: 100},
    {title: 'Open Time', dataIndex: 'openTime', key: 'openTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Close Time', dataIndex: 'closeTime', key: 'closeTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Open', dataIndex: 'openPrice', key: 'openPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'High', dataIndex: 'highPrice', key: 'highPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Low', dataIndex: 'lowPrice', key: 'lowPrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Close', dataIndex: 'closePrice', key: 'closePrice', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Volume', dataIndex: 'volume', key: 'volume', width: 120, render: (value: number) => formatNumber(value, 8)},
    {title: 'Quote Volume', dataIndex: 'quoteVolume', key: 'quoteVolume', width: 140, render: (value?: number | null) => value == null ? '-' : formatNumber(value, 8)},
    {
        title: 'Quality',
        dataIndex: 'qualityStatus',
        key: 'qualityStatus',
        width: 130,
        render: (value?: string | null) => value
            ? <Tag color={value === 'OK' ? 'green' : 'orange'}>{value}</Tag>
            : <Tag>unavailable</Tag>,
    },
];

const GAP_QUALITY_STATUSES = new Set(['GAP_DETECTED', 'MISSING_BAR', 'INCOMPLETE', 'DEGRADED']);
const OK_QUALITY_STATUSES = new Set(['OK', 'GOOD']);

type QualityReadinessStatus = 'GOOD' | 'WARN' | 'STALE' | 'GAP' | 'ERROR' | 'UNKNOWN';

const STABLE_FACT_PENDING_TEXT = '暂无稳定事实';

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
        return {state: 'error', detail: 'bars query failed', stale: false};
    }
    if (!submittedQuery) {
        return {state: 'disabled', detail: 'not queried', stale: false};
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
            detail: 'bars query failed; data quality cannot be trusted for this request',
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: 'readiness API unavailable; using bars fallback',
        };
    }
    if (!submittedQuery) {
        return {
            status: 'UNKNOWN',
            title: 'UNKNOWN',
            detail: 'submit a bars query to evaluate current data quality',
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: 'readiness API unavailable; using bars fallback',
        };
    }
    if (loading) {
        return {
            status: 'UNKNOWN',
            title: 'UNKNOWN',
            detail: 'bars query is still loading',
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: 'readiness API unavailable; using bars fallback',
        };
    }
    if (bars.length === 0) {
        return {
            status: 'UNKNOWN',
            title: 'UNKNOWN',
            detail: 'no bars returned for the submitted window',
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: 'readiness API unavailable; using bars fallback',
        };
    }
    if (quality.gapCount > 0) {
        return {
            status: 'GAP',
            title: 'GAP',
            detail: `${quality.gapCount} gap signal(s) detected from qualityStatus or interval sequence`,
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: 'readiness API unavailable; using bars fallback',
        };
    }
    if (freshness.stale) {
        return {
            status: 'STALE',
            title: 'STALE',
            detail: 'last bar does not cover the submitted query end window',
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: 'readiness API unavailable; using bars fallback',
        };
    }
    if (quality.unknownQualityCount > 0 || quality.nonOkQualityCount > 0 || quality.gapDetectionUnavailable) {
        return {
            status: 'WARN',
            title: 'WARN',
            detail: 'qualityStatus is incomplete or contains non-OK values',
            sourceHealth: 'UNAVAILABLE',
            sourceHealthDetail: 'readiness API unavailable; using bars fallback',
        };
    }

    return {
        status: 'GOOD',
        title: 'GOOD',
        detail: 'bars are present, sequential and qualityStatus is OK',
        sourceHealth: 'UNAVAILABLE',
        sourceHealthDetail: 'readiness API unavailable; using bars fallback',
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
            ? '等待现有 readiness API 返回；当前 UI 不发起外部交易所请求。'
            : `${dataQualityReadiness.detail}；当前 UI 只展示本地 bars/readiness 结果。`);
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
            ? 'Local DB marketdata readiness'
            : 'Local DB readiness pending query',
        capabilities: [
            {
                capability: 'bars',
                readiness: barsCapabilityReadiness,
                reason: submittedQuery
                    ? '现有 bars/readiness API 返回本地行情事实。'
                    : '提交查询后展示 bars source 状态。',
            },
            {
                capability: 'instrument metadata',
                readiness: 'PENDING_BACKEND_SUPPORT',
                reason: '当前接口未暴露 metadata source diagnostic。',
            },
            {
                capability: 'ticker',
                readiness: 'PENDING_BACKEND_SUPPORT',
                reason: '当前接口未暴露 ticker source diagnostic。',
            },
            {
                capability: 'exchange status',
                readiness: 'PENDING_BACKEND_SUPPORT',
                reason: '当前接口未暴露 exchange status source diagnostic。',
            },
        ],
    };
}

function SandboxSourceDisplay({summary}: {summary: SandboxSourceDisplaySummary}) {
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
                    <Typography.Text strong>Sandbox Source</Typography.Text>
                    <Tag color="blue">Sandbox</Tag>
                    <Tag>No-egress</Tag>
                    <Tag>Public candidate</Tag>
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
                    当前区块只消费现有 bars/readiness 和本地查询上下文；缺失的 sandbox source 字段显示 PENDING_BACKEND_SUPPORT（等待后端支持），不代表交易授权。
                </Typography.Text>
            </Space>
        </div>
    );
}

function QualityTags({quality}: {quality: BarsQualitySummary}) {
    if (!quality.hasQualityStatus) {
        return <Tag>qualityStatus unavailable</Tag>;
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
    return value == null ? STABLE_FACT_PENDING_TEXT : String(value);
}

function dateText(value?: string | null): string {
    return value ? formatDateTime(value) : STABLE_FACT_PENDING_TEXT;
}

function optionalText(value?: string | null): string {
    return value && value.trim() ? value : STABLE_FACT_PENDING_TEXT;
}

function numberText(value?: number | null, suffix = ''): string {
    return value == null ? STABLE_FACT_PENDING_TEXT : `${formatNumber(value, 2)}${suffix}`;
}

function percentText(value?: number | null): string {
    return value == null ? STABLE_FACT_PENDING_TEXT : `${formatNumber(value * 100, 2)}%`;
}

function readinessSourceHealth(readiness: MarketdataReadinessSummary): string {
    return readiness.sourceHealth ?? readiness.sourceHealthStatus ?? 'UNKNOWN';
}

function readinessUpdatedAt(readiness: MarketdataReadinessSummary): string | null {
    return readiness.updatedAt ?? readiness.generatedAt ?? null;
}

function MarketDataStatusBadge({status}: {status?: string | null}) {
    return <Tag color={readinessStatusColor(status)}>{status ?? 'UNKNOWN'}</Tag>;
}

function MarketDataOriginBadge({origin}: {origin?: string | null}) {
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
    {title: '数据源', dataIndex: 'sourceCode', key: 'sourceCode', width: 180, render: (value: string) => <MetricText>{value}</MetricText>},
    {title: '数据来源', dataIndex: 'dataOrigin', key: 'dataOrigin', width: 150, render: (value: string) => <MarketDataOriginBadge origin={value} />},
    {title: '交易所', dataIndex: 'exchange', key: 'exchange', width: 120},
    {title: '交易对', dataIndex: 'symbol', key: 'symbol', width: 140},
    {title: '周期', dataIndex: 'timeframe', key: 'timeframe', width: 100},
    {title: '源状态', dataIndex: 'sourceStatus', key: 'sourceStatus', width: 130, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {title: '健康状态', dataIndex: 'sourceHealth', key: 'sourceHealth', width: 130, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {title: '新鲜度', dataIndex: 'freshnessStatus', key: 'freshnessStatus', width: 130, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {title: '缺口', dataIndex: 'gapStatus', key: 'gapStatus', width: 120, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {title: '错误类别', dataIndex: 'errorCategory', key: 'errorCategory', width: 170, render: (value: string) => <MarketDataStatusBadge status={value} />},
    {title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 190, render: (value: string) => <MetricText>{value}</MetricText>},
    {title: '原因', dataIndex: 'reason', key: 'reason', width: 360, ellipsis: true},
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
                : STABLE_FACT_PENDING_TEXT,
            dataOrigin: 'UNKNOWN',
            exchange: submittedQuery?.exchangeCode ?? STABLE_FACT_PENDING_TEXT,
            symbol: submittedQuery?.symbol ?? STABLE_FACT_PENDING_TEXT,
            timeframe: submittedQuery?.interval ?? STABLE_FACT_PENDING_TEXT,
            sourceStatus: 'UNKNOWN',
            sourceHealth: 'UNKNOWN',
            freshnessStatus: submittedQuery ? 'UNKNOWN' : 'NO_DATA',
            gapStatus: 'UNKNOWN',
            errorCategory: 'UNKNOWN',
            updatedAt: STABLE_FACT_PENDING_TEXT,
            reason: submittedQuery
                ? '等待 /api/marketdata/readiness 返回本地 DB 诊断事实；页面不会发起外部交易所请求。'
                : '提交查询后展示 readiness 只读诊断。',
        }];
    }

    return [{
        key: readiness.sourceCode ?? `${readiness.exchangeCode}:${readiness.symbol}:${readiness.interval}`,
        sourceCode: readiness.sourceCode ?? STABLE_FACT_PENDING_TEXT,
        dataOrigin: readiness.dataOrigin ?? 'UNKNOWN',
        exchange: readiness.exchangeCode ?? readiness.exchange ?? STABLE_FACT_PENDING_TEXT,
        symbol: readiness.symbol ?? STABLE_FACT_PENDING_TEXT,
        timeframe: readiness.interval ?? readiness.timeframe ?? STABLE_FACT_PENDING_TEXT,
        sourceStatus: readiness.sourceStatus ?? 'UNKNOWN',
        sourceHealth: readinessSourceHealth(readiness),
        freshnessStatus: readiness.freshnessStatus ?? 'UNKNOWN',
        gapStatus: readiness.gapStatus ?? 'UNKNOWN',
        errorCategory: readiness.errorCategory ?? 'UNKNOWN',
        updatedAt: dateText(readinessUpdatedAt(readiness)),
        reason: readiness.disabledReason
            ?? readiness.degradedReason
            ?? readiness.sourceHealthReason
            ?? STABLE_FACT_PENDING_TEXT,
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
    return (
        <Alert
            type="warning"
            showIcon
            message="行情数据质量只读诊断"
            description={(
                <Space direction="vertical" size={2}>
                    <span>本页仅展示行情数据质量诊断结果。</span>
                    <span>数据质量正常不代表可以交易。</span>
                    <span>Public marketdata readiness 不等于 trading authorization。</span>
                    <span>LIVE 当前禁用，private trading / permission probe / real provider 未实现。</span>
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
        return STABLE_FACT_PENDING_TEXT;
    }
    if (metric.status === 'AVAILABLE' && metric.value != null) {
        return String(metric.value);
    }
    return metric.status;
}

function overviewCountText(value?: number | null): string {
    return value == null ? STABLE_FACT_PENDING_TEXT : String(value);
}

function ScopeValue({value}: {value?: string | null}) {
    return <MetricText>{value && value.trim() ? value : STABLE_FACT_PENDING_TEXT}</MetricText>;
}

function ReadinessQualityTags({readiness}: {readiness: MarketdataReadinessSummary}) {
    const entries = Object.entries(readiness.qualityStatusSummary?.statuses ?? {});

    if (entries.length === 0) {
        return <Tag>qualityStatus unavailable</Tag>;
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
    return <Typography.Text style={{fontFamily: 'var(--nq-font-mono)'}}>{children}</Typography.Text>;
}

function MetricTile({label, value, detail}: {label: string; value: ReactNode; detail?: ReactNode}) {
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
        title: 'Issue code',
        dataIndex: 'code',
        key: 'code',
        width: 190,
        render: (value: string) => <MetricText>{value}</MetricText>,
    },
    {
        title: 'Severity',
        dataIndex: 'severity',
        key: 'severity',
        width: 130,
        render: (value: string) => <Tag color={issueSeverityColor(value)}>{value}</Tag>,
    },
    {
        title: 'Count',
        dataIndex: 'count',
        key: 'count',
        width: 100,
        render: (value: number) => <MetricText>{value}</MetricText>,
    },
    {
        title: 'Message',
        dataIndex: 'message',
        key: 'message',
    },
];

function QualityMetricTile({label, metric}: {label: string; metric?: MarketdataQualityMetric | null}) {
    return (
        <MetricTile
            label={label}
            value={<MetricText>{metricValueText(metric)}</MetricText>}
            detail={(
                <Space direction="vertical" size={2}>
                    <Tag color={metricStatusColor(metric?.status)}>{metric?.status ?? 'UNKNOWN'}</Tag>
                    <span>{metric?.reason ?? '缺少稳定本地事实时不推断为 0。'}</span>
                </Space>
            )}
        />
    );
}

/**
 * MarketdataQualityCenterPanel 消费 GateP Batch 2 只读 overview API。
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
    const errorText = error ? formatApiError(error as AppApiError) : null;

    return (
        <Card className="page-section" bordered={false} title="Data Quality Center">
            <div data-testid="marketdata-data-quality-center" style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                <Alert
                    type="warning"
                    showIcon
                    message="Data Quality diagnostic only"
                    description="Data Quality Center 只表示数据质量诊断；数据质量通过不等于 trading authorization，不代表 LIVE 可用、private trading 可用、权限探活通过或 real provider 可用。"
                />
                {!submittedQuery ? (
                    <Alert
                        type="info"
                        showIcon
                        message="等待查询条件"
                        description="提交查询后只读调用 GET /api/marketdata/quality/overview。当前不会触发采集、refresh-quality、permission probe 或真实交易所外联。"
                    />
                ) : null}
                {errorText ? (
                    <Alert
                        type="error"
                        showIcon
                        message="Data Quality Center unavailable"
                        description={`overview API failed: ${errorText}; 页面不会用 bars fallback 伪造 overview 通过态。`}
                    />
                ) : null}
                {unavailable ? (
                    <Alert
                        type="warning"
                        showIcon
                        message="Data Quality Center payload incomplete"
                        description="后端响应缺少 overview 必需字段；页面保持 fail-closed，不把缺失字段推断为 0 或 READY。"
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
                        {key: 'scopeFrom', label: 'scope.from', children: <MetricText>{overview?.scope.from ? formatDateTime(overview.scope.from) : (submittedQuery ? formatDateTime(submittedQuery.startTime) : STABLE_FACT_PENDING_TEXT)}</MetricText>},
                        {key: 'scopeTo', label: 'scope.to', children: <MetricText>{overview?.scope.to ? formatDateTime(overview.scope.to) : (submittedQuery ? formatDateTime(submittedQuery.endTime) : STABLE_FACT_PENDING_TEXT)}</MetricText>},
                        {key: 'generatedAt', label: 'generatedAt', children: <MetricText>{overview ? formatDateTime(overview.generatedAt) : STABLE_FACT_PENDING_TEXT}</MetricText>},
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
                        value={<MetricText>{overview?.totalBars ?? (loading || fetching ? 'LOADING' : STABLE_FACT_PENDING_TEXT)}</MetricText>}
                        detail="本地 bars 聚合计数；0 只在后端明确返回时显示。"
                    />
                    <MetricTile
                        label="expectedBars"
                        value={<MetricText>{overviewCountText(overview?.expectedBars)}</MetricText>}
                        detail="null 表示暂无稳定 expected 事实。"
                    />
                    <MetricTile
                        label="gapCount"
                        value={<MetricText>{overviewCountText(overview?.gapCount)}</MetricText>}
                        detail="null 不等于无缺口；缺少事实时保持未知。"
                    />
                    <QualityMetricTile label="duplicateCount" metric={overview?.duplicateCount} />
                    <QualityMetricTile label="outOfOrderCount" metric={overview?.outOfOrderCount} />
                    <QualityMetricTile label="staleCount" metric={overview?.staleCount} />
                    <MetricTile
                        label="latestBarTime"
                        value={<MetricText>{overview ? dateText(overview.latestBarTime) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                        detail={overview?.latestBarTime ?? 'NO_DATA / UNKNOWN 时保持空态。'}
                    />
                    <MetricTile
                        label="earliestBarTime"
                        value={<MetricText>{overview ? dateText(overview.earliestBarTime) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                        detail={overview?.earliestBarTime ?? 'NO_DATA / UNKNOWN 时保持空态。'}
                    />
                    <MetricTile
                        label="lastSuccessAt"
                        value={<MetricText>{overview ? dateText(overview.lastSuccessAt) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                        detail={overview?.lastSuccessAt ?? '暂无 ingestion success 事实。'}
                    />
                    <MetricTile
                        label="lastFailureAt"
                        value={<MetricText>{overview ? dateText(overview.lastFailureAt) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                        detail={overview?.lastFailureAt ?? '暂无 ingestion failure 事实。'}
                    />
                    <MetricTile
                        label="lastIngestionRunId"
                        value={<MetricText>{overview?.lastIngestionRunId ?? STABLE_FACT_PENDING_TEXT}</MetricText>}
                        detail="仅显示 run id，不显示 raw request、headers 或 credential。"
                    />
                    <MetricTile
                        label="sourceHealth"
                        value={<MarketDataStatusBadge status={overview?.sourceHealth ?? (submittedQuery ? 'UNKNOWN' : 'NO_DATA')} />}
                        detail="sourceHealth 是数据源健康诊断，不代表 provider 交易权限。"
                    />
                    <MetricTile
                        label="freshnessStatus"
                        value={<MarketDataStatusBadge status={overview?.freshnessStatus ?? (submittedQuery ? 'UNKNOWN' : 'NO_DATA')} />}
                        detail="NO_DATA / UNKNOWN 均为显式非通过态。"
                    />
                    <MetricTile
                        label="qualityStatus"
                        value={<MarketDataStatusBadge status={overview?.qualityStatus ?? (submittedQuery ? 'UNKNOWN' : 'NO_DATA')} />}
                        detail="INCOMPLETE / INVALID / GAP_DETECTED 不会被隐藏。"
                    />
                </div>

                <Descriptions
                    title="dataOriginSummary"
                    size="small"
                    column={{xs: 1, sm: 2, md: 3}}
                    items={[
                        {key: 'requestedDataOrigin', label: 'requestedDataOrigin', children: <ScopeValue value={overview?.dataOriginSummary.requestedDataOrigin} />},
                        {key: 'effectiveDataOrigin', label: 'effectiveDataOrigin', children: <MarketDataOriginBadge origin={overview?.dataOriginSummary.effectiveDataOrigin ?? 'UNKNOWN'} />},
                        {key: 'localDbBars', label: 'localDbBars', children: <MetricText>{overview?.dataOriginSummary.localDbBars ?? STABLE_FACT_PENDING_TEXT}</MetricText>},
                        {key: 'fixtureBars', label: 'fixtureBars', children: <MetricText>{overview?.dataOriginSummary.fixtureBars ?? STABLE_FACT_PENDING_TEXT}</MetricText>},
                        {key: 'unknownOriginBars', label: 'unknownOriginBars', children: <MetricText>{overview?.dataOriginSummary.unknownOriginBars ?? STABLE_FACT_PENDING_TEXT}</MetricText>},
                        {key: 'supportLevel', label: 'supportLevel', children: <MetricText>{overview?.dataOriginSummary.supportLevel ?? STABLE_FACT_PENDING_TEXT}</MetricText>},
                    ]}
                />
                <Typography.Text type="secondary">
                    `effectiveDataOrigin=LOCAL_DB` 表示当前只读聚合本地事实；请求或历史 decision 中出现 PUBLIC_OUTBOUND 不代表 public outbound runtime provider 已启用。
                </Typography.Text>

                <Descriptions
                    title="datasetCoverageSummary"
                    size="small"
                    column={{xs: 1, sm: 2, md: 4}}
                    items={[
                        {key: 'datasetCount', label: 'datasetCount', children: <MetricText>{overview?.datasetCoverageSummary.datasetCount ?? STABLE_FACT_PENDING_TEXT}</MetricText>},
                        {key: 'coverageExpected', label: 'expectedBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.expectedBars)}</MetricText>},
                        {key: 'actualBars', label: 'actualBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.actualBars)}</MetricText>},
                        {key: 'missingBars', label: 'missingBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.missingBars)}</MetricText>},
                        {key: 'duplicateBars', label: 'duplicateBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.duplicateBars)}</MetricText>},
                        {key: 'invalidBars', label: 'invalidBars', children: <MetricText>{overviewCountText(overview?.datasetCoverageSummary.invalidBars)}</MetricText>},
                        {key: 'latestDatasetId', label: 'latestDatasetId', children: <ScopeValue value={overview?.datasetCoverageSummary.latestDatasetId} />},
                        {key: 'latestCoverageAt', label: 'latestCoverageAt', children: <MetricText>{overview ? dateText(overview.datasetCoverageSummary.latestCoverageAt) : STABLE_FACT_PENDING_TEXT}</MetricText>},
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
                    locale={{emptyText: '暂无 topIssues；这只表示后端未返回问题项，不代表交易授权。'}}
                />
            </div>
        </Card>
    );
}

const jobColumns = (
    onRunOnce: (jobId: string) => void,
    pendingJobId: string | null,
): ColumnsType<MarketdataIngestionJob> => [
    {title: 'Job ID', dataIndex: 'jobId', key: 'jobId', width: 260, ellipsis: true},
    {title: 'Exchange', dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: 'Market', dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 130},
    {title: 'Interval', dataIndex: 'interval', key: 'interval', width: 100},
    {title: 'Status', dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag>},
    {title: 'Start', dataIndex: 'startTime', key: 'startTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'End', dataIndex: 'endTime', key: 'endTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Updated', dataIndex: 'updatedAt', key: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value)},
    {
        title: 'Action',
        key: 'action',
        fixed: 'right',
        width: 130,
        render: (_, record) => (
            <Button size="small" loading={pendingJobId === record.jobId} onClick={() => onRunOnce(record.jobId)}>
                Run once
            </Button>
        ),
    },
];

const runColumns: ColumnsType<MarketdataIngestionRun> = [
    {title: 'Run ID', dataIndex: 'runId', key: 'runId', width: 260, ellipsis: true},
    {title: 'Status', dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : 'blue'}>{value}</Tag>},
    {title: 'Fetched', dataIndex: 'fetchedBars', key: 'fetchedBars', width: 100},
    {title: 'Inserted', dataIndex: 'insertedBars', key: 'insertedBars', width: 100},
    {title: 'Updated', dataIndex: 'updatedBars', key: 'updatedBars', width: 100},
    {title: 'Skipped', dataIndex: 'skippedBars', key: 'skippedBars', width: 100},
    {title: 'Started', dataIndex: 'startedAt', key: 'startedAt', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'Finished', dataIndex: 'finishedAt', key: 'finishedAt', width: 180, render: (value?: string | null) => value ? formatDateTime(value) : '-'},
    {title: 'Error', dataIndex: 'errorMessage', key: 'errorMessage', width: 280, ellipsis: true, render: (value?: string | null) => value || '-'},
];

const datasetColumns = (
    onRefreshQuality: (datasetId: string) => void,
    pendingDatasetId: string | null,
): ColumnsType<MarketdataDataset> => [
    {title: 'Dataset ID', dataIndex: 'datasetId', key: 'datasetId', width: 260, ellipsis: true},
    {title: 'Name', dataIndex: 'datasetName', key: 'datasetName', width: 180},
    {title: 'Exchange', dataIndex: 'exchangeCode', key: 'exchangeCode', width: 120},
    {title: 'Market', dataIndex: 'marketType', key: 'marketType', width: 100},
    {title: 'Symbol', dataIndex: 'symbol', key: 'symbol', width: 130},
    {title: 'Interval', dataIndex: 'interval', key: 'interval', width: 100},
    {title: 'Status', dataIndex: 'status', key: 'status', width: 120, render: (value: string) => <Tag color={value === 'READY' ? 'green' : value === 'INVALID' ? 'red' : 'blue'}>{value}</Tag>},
    {title: 'Quality', dataIndex: 'qualityStatus', key: 'qualityStatus', width: 140, render: (value: string) => <Tag color={value === 'OK' ? 'green' : value === 'GAP_DETECTED' ? 'orange' : 'red'}>{value}</Tag>},
    {title: 'Bars', dataIndex: 'barCount', key: 'barCount', width: 100},
    {title: 'Gaps', dataIndex: 'gapCount', key: 'gapCount', width: 100},
    {title: 'Start', dataIndex: 'startTime', key: 'startTime', width: 180, render: (value: string) => formatDateTime(value)},
    {title: 'End', dataIndex: 'endTime', key: 'endTime', width: 180, render: (value: string) => formatDateTime(value)},
    {
        title: 'Action',
        key: 'action',
        fixed: 'right',
        width: 150,
        render: (_, record) => (
            <Button
                size="small"
                loading={pendingDatasetId === record.datasetId}
                onClick={() => onRefreshQuality(record.datasetId)}
            >
                Refresh quality
            </Button>
        ),
    },
];

export function MarketdataPage() {
    const [form] = Form.useForm<MarketdataBarsFormValues>();
    const [jobForm] = Form.useForm<CreateMarketdataIngestionJobFormValues>();
    const [datasetForm] = Form.useForm<CreateMarketdataDatasetFormValues>();
    const [searchParams] = useSearchParams();
    const [messageApi, contextHolder] = message.useMessage();
    const queryClient = useQueryClient();
    const contextExchangeCode = useAccountContextStore((state) => state.exchangeCode);
    const [submittedQuery, setSubmittedQuery] = useState<MarketdataBarsQuery | null>(null);
    const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
    const [pendingJobId, setPendingJobId] = useState<string | null>(null);
    const [pendingDatasetId, setPendingDatasetId] = useState<string | null>(null);

    // Chart foundation(B0.4) 使用 additive v2 CSS vars；页级注入不改全局 AppProviders。
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
            messageApi.success('Marketdata ingestion job created');
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.ingestionJobs()});
        },
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
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
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
        onSettled: () => setPendingJobId(null),
    });
    const createDatasetMutation = useMutation({
        mutationFn: marketdataApi.createDataset,
        onSuccess: async (dataset) => {
            messageApi.success(`Dataset created: ${dataset.qualityStatus}`);
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.datasets()});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.qualityOverviewAll()});
        },
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
    });
    const refreshDatasetMutation = useMutation({
        mutationFn: marketdataApi.refreshDatasetQuality,
        onMutate: (datasetId) => setPendingDatasetId(datasetId),
        onSuccess: async (dataset) => {
            messageApi.info(`Dataset quality: ${dataset.qualityStatus}`);
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.datasets()});
            await queryClient.invalidateQueries({queryKey: marketdataQueryKeys.qualityOverviewAll()});
        },
        onError: (error) => messageApi.error(formatApiError(error as AppApiError)),
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
        [bars, barsQuality, barsQuery.error, barsQuery.isLoading, submittedQuery],
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
        [bars, barsFreshness, barsQuality, barsQuery.error, barsQuery.isLoading, submittedQuery],
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
        [backendReadiness, chartError, dataQualityReadiness, readinessLoading, submittedQuery],
    );
    const chartEmptyText = submittedQuery
        ? '当前查询没有返回 OHLCV bars'
        : '提交查询后展示 K 线主图';
    const chartSourceLabel = submittedQuery
        ? `${submittedQuery.exchangeCode} ${submittedQuery.symbol} ${submittedQuery.interval}`
        : 'Marketdata bars';

    return (
        <Space direction="vertical" size={16} style={{display: 'flex'}}>
            {contextHolder}
            <Card className="page-card" bordered={false}>
                <PageHero
                    title="Marketdata"
                    description="SPOT 历史 OHLCV 查询、接入任务和 Dataset 管理入口，固定使用当前 freeze 可验收的交易所、交易对和周期范围。"
                    badge="Marketdata"
                />
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="查询条件"
                extra={<Button type="primary" onClick={() => form.submit()}>查询</Button>}
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
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime" rules={[{required: true, message: '请选择开始时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime" rules={[{required: true, message: '请选择结束时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                <Typography.Text type="secondary">默认交易所来自当前账户上下文：{contextExchangeCode ?? '未选择'}</Typography.Text>
                {hasRuntimeDeepLink ? (
                    <Alert
                        data-testid="marketdata-runtime-deep-link"
                        type="info"
                        showIcon
                        style={{marginTop: 12}}
                        message="Runtime readiness context applied"
                        description="已从 /runtime/readiness 安全预填 exchangeCode / marketType / symbol / interval。页面不会自动触发采集、ingestion run-once 或任何写端点；点击查询后仅调用既有只读 bars/readiness API。"
                    />
                ) : null}
            </Card>
            <Card className="page-section" bordered={false} title="K 线 readiness 视图">
                <div data-testid="marketdata-kline-readiness-view" style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                    <Descriptions
                        size="small"
                        column={{xs: 1, sm: 2, md: 3}}
                        items={[
                            {key: 'exchange', label: 'Exchange', children: <MetricText>{submittedQuery?.exchangeCode ?? '-'}</MetricText>},
                            {key: 'symbol', label: 'Instrument', children: <MetricText>{submittedQuery?.symbol ?? '-'}</MetricText>},
                            {key: 'interval', label: 'Timeframe', children: <MetricText>{submittedQuery?.interval ?? '-'}</MetricText>},
                            {key: 'barCount', label: 'Bar count', children: <MetricText>{bars.length}</MetricText>},
                            {key: 'lastBar', label: 'Last bar time', children: <MetricText>{lastBar ? formatDateTime(lastBar.closeTime ?? lastBar.openTime) : '-'}</MetricText>},
                            {key: 'quality', label: 'Data quality', children: <QualityTags quality={barsQuality}/>},
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
                            <Tag color="orange">gap / qualityStatus: {barsQuality.gapCount}</Tag>
                        ) : null}
                        {!barsQuality.hasQualityStatus && bars.length > 0 ? (
                            <Tag>qualityStatus missing: non-blocking</Tag>
                        ) : null}
                    </Space>
                    {barsFreshness.state === 'stale' ? (
                        <Alert
                            type="warning"
                            showIcon
                            message="Marketdata bars stale"
                            description="最后一根 K 线未覆盖查询结束时间；本视图只展示已有历史 bars，不做实时刷新或 WebSocket 补齐。"
                        />
                    ) : null}
                    {barsQuality.gapCount > 0 ? (
                        <Alert
                            type="warning"
                            showIcon
                            message="Marketdata bars quality degraded"
                            description="后端返回了非 OK qualityStatus；图表继续展示已有 bars，gap 修复仍由 MarketData ingestion / dataset quality 流程处理。"
                        />
                    ) : null}
                    {!barsQuality.hasQualityStatus && bars.length > 0 ? (
                        <Alert
                            type="info"
                            showIcon
                            message="qualityStatus unavailable"
                            description="当前 bars payload 未携带 qualityStatus；这是非阻断提示，不会伪造 gap 状态。"
                        />
                    ) : null}
                    <div
                        style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
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
                            title="OHLCV K-line"
                            emptyText={chartEmptyText}
                            height={320}
                        />
                        <NqVolumeChart
                            bars={chartBars}
                            loading={barsQuery.isLoading}
                            error={chartError}
                            sourceLabel={chartSourceLabel}
                            title="Volume"
                            emptyText={submittedQuery ? '当前查询没有返回成交量 bars' : '提交查询后展示成交量'}
                            height={180}
                        />
                    </div>
                    <Typography.Text type="secondary">
                        本视图复用现有 /api/marketdata/bars 与 marketdataApi.listBars()；不接 WebSocket、不接真实交易所私有流、不做买卖点或指标系统。
                    </Typography.Text>
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
            <Card className="page-section" bordered={false} title="Data Quality / Readiness">
                <div data-testid="marketdata-quality-readiness-view" style={{display: 'flex', flexDirection: 'column', gap: 16}}>
                    <MarketDataQualityNotice />
                    <Descriptions
                        size="small"
                        column={{xs: 1, sm: 2, md: 3}}
                        items={[
                            {key: 'queryExchange', label: 'Exchange', children: <MetricText>{submittedQuery?.exchangeCode ?? '-'}</MetricText>},
                            {key: 'queryInstrument', label: 'Instrument / Symbol', children: <MetricText>{submittedQuery?.symbol ?? '-'}</MetricText>},
                            {key: 'queryInterval', label: 'Interval / timeframe', children: <MetricText>{submittedQuery?.interval ?? '-'}</MetricText>},
                            {key: 'queryStart', label: 'Query start', children: <MetricText>{submittedQuery ? formatDateTime(submittedQuery.startTime) : '-'}</MetricText>},
                            {key: 'queryEnd', label: 'Query end', children: <MetricText>{submittedQuery ? formatDateTime(submittedQuery.endTime) : '-'}</MetricText>},
                            {
                                key: 'readinessStatus',
                                label: 'Readiness status',
                                children: (
                                    <Tag color={readinessStatusColor(backendReadiness?.status ?? dataQualityReadiness.status)}>
                                        {backendReadiness?.status ?? dataQualityReadiness.title}
                                    </Tag>
                                ),
                            },
                            {
                                key: 'sourceHealthStatus',
                                label: 'Source health status',
                                children: (
                                    <MarketDataStatusBadge status={backendReadiness ? readinessSourceHealth(backendReadiness) : (readinessLoading ? 'LOADING' : 'UNAVAILABLE')} />
                                ),
                            },
                            {
                                key: 'sourceStatus',
                                label: 'Source status',
                                children: (
                                    <MarketDataStatusBadge status={backendReadiness?.sourceStatus ?? (readinessLoading ? 'LOADING' : 'UNKNOWN')} />
                                ),
                            },
                            {
                                key: 'dataOrigin',
                                label: 'Data origin',
                                children: (
                                    <MarketDataOriginBadge origin={backendReadiness?.dataOrigin ?? 'UNKNOWN'} />
                                ),
                            },
                            {
                                key: 'gapStatus',
                                label: 'Gap status',
                                children: (
                                    <MarketDataStatusBadge status={backendReadiness?.gapStatus ?? 'UNKNOWN'} />
                                ),
                            },
                            {
                                key: 'backendSupportLevel',
                                label: 'Backend support',
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
                            label="Bars loaded"
                            value={<MetricText>{backendReadiness?.barCount ?? bars.length}</MetricText>}
                            detail={backendReadiness ? 'from /api/marketdata/readiness' : (submittedQuery ? 'from /api/marketdata/bars fallback' : 'pending query')}
                        />
                        <MetricTile
                            label="First bar time"
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.firstBarTime) : (firstBar ? formatDateTime(firstBar.openTime) : '-')}</MetricText>}
                            detail={backendReadiness ? (backendReadiness.firstBarTime ?? 'no data') : (firstBar ? firstBar.openTime : 'no data')}
                        />
                        <MetricTile
                            label="Last bar time"
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastBarTime) : (lastBar ? formatDateTime(lastBar.closeTime ?? lastBar.openTime) : '-')}</MetricText>}
                            detail={backendReadiness ? (backendReadiness.lastBarTime ?? 'no data') : (lastBar ? (lastBar.closeTime ?? lastBar.openTime) : 'no data')}
                        />
                        <MetricTile
                            label="Latest close"
                            value={<MetricText>{lastBar ? formatNumber(lastBar.closePrice, 8) : '-'}</MetricText>}
                            detail={lastBar ? 'last returned bar' : 'no data'}
                        />
                        <MetricTile
                            label="Latest volume"
                            value={<MetricText>{lastBar ? formatNumber(lastBar.volume, 8) : '-'}</MetricText>}
                            detail={lastBar ? 'last returned bar' : 'no data'}
                        />
                        <MetricTile
                            label="Freshness"
                            value={(
                                <DataFreshness
                                    source={backendReadiness ? 'backend readiness' : 'bars'}
                                    state={backendReadiness ? readinessFreshnessState(backendReadiness.freshnessStatus) : barsFreshness.state}
                                    detail={backendReadiness?.freshnessStatus ?? barsFreshness.detail}
                                    inline
                                />
                            )}
                            detail={backendReadiness ? 'from /api/marketdata/readiness' : (barsFreshness.stale ? 'stale by query interval estimate' : 'front-end estimate')}
                        />
                        <MetricTile
                            label="Quality status"
                            value={backendReadiness ? <ReadinessQualityTags readiness={backendReadiness}/> : <QualityTags quality={barsQuality}/>}
                            detail={backendReadiness ? `ok=${backendReadiness.qualityStatusSummary.okCount}, gap=${backendReadiness.qualityStatusSummary.gapSignalCount}, invalid=${backendReadiness.qualityStatusSummary.invalidCount}` : (barsQuality.hasQualityStatus ? 'aggregated from bars payload' : 'unavailable / readiness unavailable')}
                        />
                        <MetricTile
                            label="Gap count"
                            value={<MetricText>{backendReadiness ? countText(backendReadiness.gapCount) : (barsQuality.gapDetectionUnavailable ? '-' : barsQuality.gapCount)}</MetricText>}
                            detail={backendReadiness ? `expected=${countText(backendReadiness.expectedBarCount)}` : (barsQuality.gapDetectionUnavailable ? 'gap detection unavailable' : `quality=${barsQuality.qualityGapCount}, sequence=${barsQuality.sequenceGapCount ?? '-'}`)}
                        />
                        <MetricTile
                            label="Gap status"
                            value={<MarketDataStatusBadge status={backendReadiness?.gapStatus ?? 'UNKNOWN'} />}
                            detail={backendReadiness ? `missingFrom=${dateText(backendReadiness.missingFrom)}, missingTo=${dateText(backendReadiness.missingTo)}` : STABLE_FACT_PENDING_TEXT}
                        />
                        <MetricTile
                            label="Unknown quality count"
                            value={<MetricText>{backendReadiness?.unknownQualityCount ?? barsQuality.unknownQualityCount}</MetricText>}
                            detail={backendReadiness ? 'from backend qualityStatusSummary' : (barsQuality.unknownQualityCount > 0 ? 'qualityStatus missing on returned bars' : 'none')}
                        />
                        <MetricTile
                            label="Source health"
                            value={(
                                <MarketDataStatusBadge status={backendReadiness ? readinessSourceHealth(backendReadiness) : (readinessLoading ? 'LOADING' : 'UNAVAILABLE')} />
                            )}
                            detail={backendReadiness?.sourceHealthReason ?? (readinessLoading ? 'loading /api/marketdata/readiness' : dataQualityReadiness.sourceHealthDetail)}
                        />
                        <MetricTile
                            label="Source status"
                            value={<MarketDataStatusBadge status={backendReadiness?.sourceStatus ?? 'UNKNOWN'} />}
                            detail="sourceStatus 只表示数据源诊断状态，不代表 provider 或交易授权。"
                        />
                        <MetricTile
                            label="Data origin"
                            value={<MarketDataOriginBadge origin={backendReadiness?.dataOrigin ?? 'UNKNOWN'} />}
                            detail="LOCAL_DB / FIXTURE / FAKE_SERVER / PUBLIC_CANDIDATE 均为诊断来源，不代表真实外联已执行。"
                        />
                        <MetricTile
                            label="Error category"
                            value={<MarketDataStatusBadge status={backendReadiness?.errorCategory ?? 'UNKNOWN'} />}
                            detail={backendReadiness ? optionalText(backendReadiness.degradedReason ?? backendReadiness.disabledReason) : STABLE_FACT_PENDING_TEXT}
                        />
                        <MetricTile
                            label="Error rate"
                            value={<MetricText>{backendReadiness ? percentText(backendReadiness.errorRate) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="null 表示暂无稳定事实，不显示为 0%。"
                        />
                        <MetricTile
                            label="Latency"
                            value={<MetricText>{backendReadiness ? numberText(backendReadiness.latencyMs, ' ms') : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="null 表示暂无稳定事实，不硬编码延迟阈值。"
                        />
                        <MetricTile
                            label="Missing from"
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.missingFrom) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="缺口起点；null 不等于无缺口。"
                        />
                        <MetricTile
                            label="Missing to"
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.missingTo) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="缺口终点；null 不等于无缺口。"
                        />
                        <MetricTile
                            label="Last observed"
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastObservedAt) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="最近观测到的本地行情或 ingestion 事实。"
                        />
                        <MetricTile
                            label="Updated at"
                            value={<MetricText>{backendReadiness ? dateText(readinessUpdatedAt(backendReadiness)) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail={backendReadiness ? '优先使用 updatedAt，兼容 generatedAt。' : STABLE_FACT_PENDING_TEXT}
                        />
                        <MetricTile
                            label="Stale threshold"
                            value={<MetricText>{backendReadiness ? numberText(backendReadiness.staleAfterSeconds, ' s') : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="过期阈值来自后端只读事实；前端不推导业务阈值。"
                        />
                        <MetricTile
                            label="Degraded reason"
                            value={<MetricText>{backendReadiness ? optionalText(backendReadiness.degradedReason) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="仅显示脱敏诊断原因。"
                        />
                        <MetricTile
                            label="Disabled reason"
                            value={<MetricText>{backendReadiness ? optionalText(backendReadiness.disabledReason) : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="仅显示脱敏禁用原因。"
                        />
                        <MetricTile
                            label="Trace / Request"
                            value={<MetricText>{backendReadiness ? `${optionalText(backendReadiness.traceId)} / ${optionalText(backendReadiness.requestId)}` : STABLE_FACT_PENDING_TEXT}</MetricText>}
                            detail="traceId/requestId 可为空；不得显示 credential、header 或 raw payload。"
                        />
                        <MetricTile
                            label="Backend support"
                            value={<MetricText>{backendReadiness?.backendSupportLevel ?? (submittedQuery ? 'UNAVAILABLE' : '-')}</MetricText>}
                            detail={backendReadiness ? `generated ${formatDateTime(backendReadiness.generatedAt)}` : 'readiness API fallback'}
                        />
                        <MetricTile
                            label="Last success"
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastSuccessAt) : '-'}</MetricText>}
                            detail={backendReadiness?.lastSuccessAt ?? STABLE_FACT_PENDING_TEXT}
                        />
                        <MetricTile
                            label="Last failure"
                            value={<MetricText>{backendReadiness ? dateText(backendReadiness.lastFailureAt) : '-'}</MetricText>}
                            detail={backendReadiness?.lastFailureAt ?? STABLE_FACT_PENDING_TEXT}
                        />
                    </div>
                    <Space size={8} wrap>
                        <Tag color={readinessStatusColor(backendReadiness?.status ?? dataQualityReadiness.status)}>
                            {backendReadiness?.status ?? dataQualityReadiness.status}
                        </Tag>
                        {backendReadiness ? (
                            <>
                                <Tag color={readinessStatusColor(backendReadiness.freshnessStatus)}>
                                    freshness: {backendReadiness.freshnessStatus}
                                </Tag>
                                <Tag color={readinessStatusColor(readinessSourceHealth(backendReadiness))}>
                                    source health: {readinessSourceHealth(backendReadiness)}
                                </Tag>
                                <Tag color={readinessStatusColor(backendReadiness.gapStatus)}>
                                    gap: {backendReadiness.gapStatus ?? 'UNKNOWN'}
                                </Tag>
                                <Tag color={readinessStatusColor(backendReadiness.sourceStatus)}>
                                    source status: {backendReadiness.sourceStatus ?? 'UNKNOWN'}
                                </Tag>
                                <Tag>data origin: {backendReadiness.dataOrigin ?? 'UNKNOWN'}</Tag>
                                <Tag>backend support: {backendReadiness.backendSupportLevel}</Tag>
                            </>
                        ) : (
                            <Tag>source health: {readinessLoading ? 'LOADING' : 'UNAVAILABLE'}</Tag>
                        )}
                        {barsQuality.gapDetectionUnavailable ? (
                            <Tag>gap detection unavailable</Tag>
                        ) : null}
                    </Space>
                    {chartError ? (
                        <Alert
                            type="error"
                            showIcon
                            message="Data quality unavailable"
                            description="bars query failed; this view does not infer data readiness from a failed response."
                        />
                    ) : null}
                    {!chartError && submittedQuery && bars.length === 0 ? (
                        <Alert
                            type="info"
                            showIcon
                            message="No bars returned"
                            description="当前查询窗口没有返回 bars；freshness、gap 和 qualityStatus 只能显示 no data / unavailable。"
                        />
                    ) : null}
                    {barsQuality.gapDetectionUnavailable ? (
                        <Alert
                            type="info"
                            showIcon
                            message="Gap detection unavailable"
                            description="当前 bars payload 无 qualityStatus，且周期或时间字段不足以稳定推断序列缺口；页面不会伪造 gap=0 或 source health=OK。"
                        />
                    ) : null}
                    {readinessUnavailable ? (
                        <Alert
                            type="warning"
                            showIcon
                            message="MarketData source health unavailable"
                            description={readinessError
                                ? `readiness API failed: ${readinessError}; using bars-derived fallback only.`
                                : 'readiness API did not return a usable summary; using bars-derived fallback only.'}
                        />
                    ) : null}
                    {backendReadiness ? (
                        <Alert
                            type={backendReadiness.status === 'FRESH' ? 'success' : backendReadiness.status === 'ERROR' ? 'error' : 'warning'}
                            showIcon
                            message={`MarketData readiness: ${backendReadiness.status}`}
                            description={`${backendReadiness.sourceHealthReason} Backend support: ${backendReadiness.backendSupportLevel}. 本结论只表示行情数据诊断，不代表交易授权。`}
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
            <Card className="page-section" bordered={false} title="Bars 结果">
                {barsQuery.error ? (
                    <Alert type="error" showIcon message="Marketdata bars 查询失败" description={formatApiError(barsQuery.error as AppApiError)} />
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
                title="接入任务"
                extra={<Button type="primary" loading={createJobMutation.isPending} onClick={() => jobForm.submit()}>创建任务</Button>}
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
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime" rules={[{required: true, message: '请选择开始时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime" rules={[{required: true, message: '请选择结束时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                {jobsQuery.error ? (
                    <Alert type="error" showIcon message="Marketdata ingestion jobs 查询失败" description={formatApiError(jobsQuery.error as AppApiError)} />
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
            <Card className="page-section" bordered={false} title="运行结果">
                {selectedJobId ? (
                    runsQuery.error ? (
                        <Alert type="error" showIcon message="Marketdata ingestion runs 查询失败" description={formatApiError(runsQuery.error as AppApiError)} />
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
                    <Alert type="info" showIcon message="请选择或创建一个接入任务查看运行结果" />
                )}
            </Card>
            <Card
                className="page-section"
                bordered={false}
                title="Datasets"
                extra={(
                    <Button
                        type="primary"
                        loading={createDatasetMutation.isPending}
                        onClick={() => datasetForm.submit()}
                    >
                        创建 Dataset
                    </Button>
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
                        <Form.Item label="Dataset Name" name="datasetName">
                            <Input style={{width: 260}} />
                        </Form.Item>
                        <Form.Item label="交易所" name="exchangeCode">
                            <Select style={{width: 140}} options={EXCHANGE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="市场" name="marketType">
                            <Select style={{width: 120}} options={MARKET_TYPE_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="交易对" name="symbol">
                            <Select showSearch style={{width: 160}} options={SYMBOL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="周期" name="interval">
                            <Select style={{width: 120}} options={INTERVAL_OPTIONS} />
                        </Form.Item>
                        <Form.Item label="开始时间" name="startTime" rules={[{required: true, message: '请选择开始时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                        <Form.Item label="结束时间" name="endTime" rules={[{required: true, message: '请选择结束时间'}]}>
                            <DatePicker showTime style={{width: 220}} />
                        </Form.Item>
                    </Space>
                </Form>
                {datasetsQuery.error ? (
                    <Alert type="error" showIcon message="Marketdata datasets 查询失败" description={formatApiError(datasetsQuery.error as AppApiError)} />
                ) : (
                    <Table
                        rowKey="datasetId"
                        columns={datasetColumns((datasetId) => refreshDatasetMutation.mutate(datasetId), pendingDatasetId)}
                        dataSource={datasetsQuery.data ?? []}
                        loading={datasetsQuery.isLoading || datasetsQuery.isFetching}
                        pagination={{pageSize: 10, showSizeChanger: false}}
                        scroll={{x: 1900}}
                        locale={{emptyText: '暂无 marketdata dataset，可先创建数据集。'}}
                    />
                )}
            </Card>
        </Space>
    );
}

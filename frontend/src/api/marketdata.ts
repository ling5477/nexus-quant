import {apiClient} from '@/api/client';

import {parseExactJson} from './exact-json';
import type {
    DatasetOperationRequest,
    DatasetPage,
    DatasetPageRequest,
    MarketdataBar,
    MarketdataBarsQuery,
    MarketdataDataset,
    MarketdataQualityOverview,
    MarketdataQualityOverviewQuery,
    MarketdataReadinessQuery,
    MarketdataReadinessSummary,
} from '@/types/marketdata';

const barDecimalFields = new Set([
    'openPrice',
    'highPrice',
    'lowPrice',
    'closePrice',
    'volume',
    'quoteVolume',
]);

/** 拒绝错误窗口和不完整身份，防止把截断目录或别页结果当成可选数据集。 */
export function validateDatasetPage(value: unknown, request: DatasetPageRequest): DatasetPage {
    if (!value || typeof value !== 'object') throw new Error('Invalid dataset page response');
    const page = value as Partial<DatasetPage>;
    if (
        !Array.isArray(page.items) ||
        !Number.isSafeInteger(page.total) ||
        page.total! < 0 ||
        page.limit !== request.limit ||
        page.offset !== request.offset ||
        page.items.length !== Math.min(request.limit, Math.max(0, page.total! - request.offset))
    ) {
        throw new Error('Invalid dataset page window');
    }
    const ids = new Set<string>();
    for (const row of page.items) {
        if (
            !row ||
            typeof row.datasetId !== 'string' ||
            !row.datasetId.trim() ||
            (request.datasetId && row.datasetId !== request.datasetId) ||
            typeof row.datasetName !== 'string' ||
            !row.datasetName.trim() ||
            ![
                'exchangeCode',
                'marketType',
                'symbol',
                'interval',
                'status',
                'qualityStatus',
                'source',
            ].every((key) => typeof row[key as keyof MarketdataDataset] === 'string') ||
            !Number.isFinite(Date.parse(row.startTime)) ||
            !Number.isFinite(Date.parse(row.endTime)) ||
            !Number.isFinite(Date.parse(row.updatedAt)) ||
            ids.has(row.datasetId)
        ) {
            throw new Error('Invalid dataset page item');
        }
        ids.add(row.datasetId);
    }
    return page as DatasetPage;
}

export const marketdataApi = {
    async listBars(query: MarketdataBarsQuery): Promise<MarketdataBar[]> {
        const {data} = await apiClient.get<MarketdataBar[]>('/marketdata/bars', {
            params: query,
            transformResponse: [(raw: string) => parseExactJson(raw, barDecimalFields)],
        });
        return data;
    },
    async getReadiness(query: MarketdataReadinessQuery): Promise<MarketdataReadinessSummary> {
        const {data} = await apiClient.get<MarketdataReadinessSummary>('/marketdata/readiness', {
            params: query,
        });
        return data;
    },
    async getQualityOverview(
        query: MarketdataQualityOverviewQuery,
    ): Promise<MarketdataQualityOverview> {
        const {data} = await apiClient.get<MarketdataQualityOverview>(
            '/marketdata/quality/overview',
            {
                params: query,
            },
        );
        return data;
    },
    async listDatasets(): Promise<MarketdataDataset[]> {
        const {data} = await apiClient.get<MarketdataDataset[]>('/marketdata/datasets');
        return data;
    },
    async pageDatasets(request: DatasetPageRequest, signal?: AbortSignal): Promise<DatasetPage> {
        const {data} = await apiClient.get<unknown>('/marketdata/datasets/page', {
            params: request,
            signal,
        });
        return validateDatasetPage(data, request);
    },
    async getDataset(datasetId: string, signal?: AbortSignal): Promise<MarketdataDataset> {
        const {data} = await apiClient.get<MarketdataDataset>(
            `/marketdata/datasets/${encodeURIComponent(datasetId)}`,
            {signal},
        );
        if (!data || data.datasetId !== datasetId)
            throw new Error('Invalid dataset detail identity');
        return data;
    },
    /** 稳定编号先由恢复协议保存；此层只调用正式入口，不自动重试写入。 */
    async submitDatasetOperation(request: DatasetOperationRequest): Promise<unknown> {
        const datasetPath = `/marketdata/datasets/${encodeURIComponent(request.datasetId)}`;
        const path =
            request.kind === 'CREATE'
                ? `${datasetPath}/creation`
                : `${datasetPath}/quality-refreshes/${encodeURIComponent(request.operationId)}`;
        const {data} = await apiClient.post<unknown>(path, request.input);
        return data;
    },
    async getDatasetOperation(operationId: string, signal?: AbortSignal): Promise<unknown> {
        const {data} = await apiClient.get<unknown>(
            `/marketdata/dataset-operations/${encodeURIComponent(operationId)}`,
            {signal},
        );
        return data;
    },
};

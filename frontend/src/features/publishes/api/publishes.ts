import {apiClient} from '@/api/client';
import {validateResultPage} from '@/api/result-page';
import type {
    BacktestPublishDetailItem,
    BacktestPublishListItem,
    BacktestPublishRequest,
    PublishPage,
    PublishPageRequest,
} from '@/features/publishes/types/publishes';

export function validatePublishPage(value: unknown, request: PublishPageRequest): PublishPage {
    return validateResultPage<BacktestPublishListItem>(value, request, (value) => {
        if (!value || typeof value !== 'object') return null;
        const row = value as Partial<BacktestPublishListItem>;
        return typeof row.publishRecordId === 'string' &&
            Boolean(row.publishRecordId.trim()) &&
            typeof row.backtestRunId === 'string' &&
            Boolean(row.backtestRunId.trim()) &&
            ['SUCCEEDED', 'FAILED'].includes(row.publishStatus ?? '')
            ? row.publishRecordId
            : null;
    });
}

interface PublishListRequest {
    researchConfigId?: string;
    backtestConfigId?: string;
    strategyVersionId?: string;
}

export const publishesApi = {
    async page(request: PublishPageRequest, signal?: AbortSignal): Promise<PublishPage> {
        const {data} = await apiClient.get<unknown>('/publishes/page', {params: request, signal});
        return validatePublishPage(data, request);
    },
    async list(request: PublishListRequest): Promise<BacktestPublishListItem[]> {
        const {data} = await apiClient.get<BacktestPublishListItem[]>('/publishes', {
            params: {
                strategyVersionId: request.strategyVersionId,
            },
        });
        return data;
    },
    async detail(publishId: string): Promise<BacktestPublishDetailItem> {
        const {data} = await apiClient.get<BacktestPublishDetailItem>(`/publishes/${publishId}`);
        return data;
    },
    /** 按原运行读取已经保存的发布结果；恢复入口不能再次创建发布或扫描全部发布列表。 */
    async forRun(runId: string, signal?: AbortSignal): Promise<BacktestPublishDetailItem> {
        const {data} = await apiClient.get<BacktestPublishDetailItem>(
            `/backtest-runs/${encodeURIComponent(runId)}/publish`,
            {signal},
        );
        return data;
    },
    async publish(
        runId: string,
        request?: BacktestPublishRequest,
    ): Promise<BacktestPublishDetailItem> {
        const {data} = await apiClient.post<BacktestPublishDetailItem>('/publishes', request, {
            params: {backtestRunId: runId},
        });
        return data;
    },
};

import {createRecoverably} from '@/features/research/creation-operations';
import {apiClient} from '@/api/client';
import type {
    ResearchConfigCreateRequest,
    ResearchConfigListItem,
    ResearchConfigPage,
    ResearchPageRequest,
} from '@/features/research/types/research';

/** 拒绝错误窗口和损坏对象，避免将别页数据或不完整身份放进当前表格。 */
export function validateResearchPage(
    value: unknown,
    request: ResearchPageRequest,
): ResearchConfigPage {
    if (!value || typeof value !== 'object') throw new Error('Invalid research page response');
    const page = value as Partial<ResearchConfigPage>;
    if (
        !Array.isArray(page.items) ||
        !Number.isSafeInteger(page.total) ||
        page.total! < 0 ||
        page.limit !== request.limit ||
        page.offset !== request.offset ||
        page.items.length !== Math.min(request.limit, Math.max(0, page.total! - request.offset))
    ) {
        throw new Error('Invalid research page window');
    }
    const ids = new Set<string>();
    for (const row of page.items) {
        if (
            !row ||
            typeof row.researchConfigId !== 'string' ||
            !row.researchConfigId.trim() ||
            typeof row.sourceStrategyId !== 'string' ||
            !row.sourceStrategyId.trim() ||
            typeof row.name !== 'string' ||
            !Number.isFinite(Date.parse(row.createdAt)) ||
            ids.has(row.researchConfigId)
        )
            throw new Error('Invalid research page item');
        ids.add(row.researchConfigId);
    }
    return page as ResearchConfigPage;
}

export const researchApi = {
    async page(request: ResearchPageRequest, signal?: AbortSignal): Promise<ResearchConfigPage> {
        const {data} = await apiClient.get<unknown>('/research-configs/page', {
            params: request,
            signal,
        });
        return validateResearchPage(data, request);
    },
    async list(sourceStrategyId?: string): Promise<ResearchConfigListItem[]> {
        const {data} = await apiClient.get<ResearchConfigListItem[]>('/research-configs', {
            params: sourceStrategyId ? {sourceStrategyId} : undefined,
        });
        return data;
    },
    async detail(configId: string, signal?: AbortSignal): Promise<ResearchConfigListItem> {
        const {data} = await apiClient.get<ResearchConfigListItem>(
            `/research-configs/${encodeURIComponent(configId)}`,
            {signal},
        );
        return data;
    },
    async create(request: ResearchConfigCreateRequest): Promise<ResearchConfigListItem> {
        return createRecoverably('RESEARCH', request, (receipt) =>
            researchApi.detail(receipt.objectId),
        );
    },
    /** 复用既有归档合同；操作者、首次归档时间及历史保留规则由后端裁决。 */
    async archive(configId: string, archiveReason?: string): Promise<ResearchConfigListItem> {
        const {data} = await apiClient.post<ResearchConfigListItem>(
            `/research-configs/${encodeURIComponent(configId)}/archive`,
            {archiveReason},
        );
        return data;
    },
};

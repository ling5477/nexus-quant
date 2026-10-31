import {createRecoverably} from '@/features/research/creation-operations';
import {apiClient} from '@/api/client';
import {validateResultPage} from '@/api/result-page';
import type {
    StrategyDefinitionListItem,
    StrategyDefinitionCreateRequest,
    StrategyStatusUpdateRequest,
    StrategyVersionCreateRequest,
    StrategyVersionItem,
    StrategyPageRequest,
    StrategyDefinitionPage,
    StrategyVersionListItem,
    StrategyVersionPageRequest,
    StrategyVersionPage,
} from '@/features/strategies/types/strategies';

/** 父策略及版本身份必须与请求一致，避免将另一策略的元信息或快照用于绑定。 */
function versionIdentity(value: unknown, code: string, id?: string): string | null {
    if (!value || typeof value !== 'object') return null;
    const row = value as Record<string, unknown>;
    if (
        typeof row.strategyVersionId !== 'string' ||
        !row.strategyVersionId.trim() ||
        row.strategyCode !== code ||
        (id?.trim() && row.strategyVersionId !== id.trim()) ||
        !Number.isInteger(row.version) ||
        Number(row.version) < 1 ||
        typeof row.versionName !== 'string' ||
        !['DRAFT', 'ACTIVE', 'ARCHIVED'].includes(String(row.status)) ||
        typeof row.checksum !== 'string' ||
        typeof row.createdBy !== 'string' ||
        typeof row.createdAt !== 'string' ||
        !Number.isFinite(Date.parse(row.createdAt)) ||
        typeof row.updatedAt !== 'string' ||
        !Number.isFinite(Date.parse(row.updatedAt))
    )
        return null;
    return row.strategyVersionId;
}

export const strategiesApi = {
    async page(
        request: StrategyPageRequest,
        signal?: AbortSignal,
    ): Promise<StrategyDefinitionPage> {
        const {data} = await apiClient.get<unknown>('/strategies/page', {
            params: {...request, enabled: request.enabled === 'all' ? undefined : request.enabled},
            signal,
        });
        // 错误窗口和残缺身份必须进入失败态；精确关联不能错误取用另一条策略。
        return validateResultPage<StrategyDefinitionListItem>(data, request, (value) => {
            if (!value || typeof value !== 'object') return null;
            const row = value as Record<string, unknown>;
            if (
                typeof row.strategyId !== 'string' ||
                !row.strategyId.trim() ||
                typeof row.strategyCode !== 'string' ||
                !row.strategyCode.trim() ||
                typeof row.strategyName !== 'string' ||
                typeof row.enabled !== 'boolean' ||
                typeof row.createdAt !== 'string' ||
                !Number.isFinite(Date.parse(row.createdAt)) ||
                (request.strategyId?.trim() && row.strategyId !== request.strategyId.trim())
            )
                return null;
            return row.strategyId;
        });
    },
    async create(request: StrategyDefinitionCreateRequest): Promise<StrategyDefinitionListItem> {
        return createRecoverably('STRATEGY', request, (receipt) =>
            strategiesApi.detail(receipt.lookupKey),
        );
    },
    async list(): Promise<StrategyDefinitionListItem[]> {
        const {data} = await apiClient.get<StrategyDefinitionListItem[]>('/strategies');
        return data;
    },
    async detail(strategyCode: string): Promise<StrategyDefinitionListItem> {
        const {data} = await apiClient.get<StrategyDefinitionListItem>(
            `/strategies/${strategyCode}`,
        );
        return data;
    },
    async updateStatus(
        strategyCode: string,
        request: StrategyStatusUpdateRequest,
    ): Promise<StrategyDefinitionListItem> {
        const {data} = await apiClient.patch<StrategyDefinitionListItem>(
            `/strategies/${strategyCode}/status`,
            request,
        );
        return data;
    },
    async listVersions(strategyCode: string): Promise<StrategyVersionItem[]> {
        const {data} = await apiClient.get<StrategyVersionItem[]>(
            `/strategies/${strategyCode}/versions`,
        );
        return data;
    },
    async pageVersions(
        strategyCode: string,
        request: StrategyVersionPageRequest,
        signal?: AbortSignal,
    ): Promise<StrategyVersionPage> {
        const {data} = await apiClient.get<unknown>(
            `/strategies/${encodeURIComponent(strategyCode)}/versions/page`,
            {params: request, signal},
        );
        return validateResultPage<StrategyVersionListItem>(data, request, (row) =>
            versionIdentity(row, strategyCode, request.strategyVersionId),
        );
    },
    async createVersion(
        strategyCode: string,
        request: StrategyVersionCreateRequest,
    ): Promise<StrategyVersionItem> {
        return createRecoverably(
            'VERSION',
            request,
            (receipt) => strategiesApi.versionDetail(receipt.parentKey, receipt.objectId),
            strategyCode,
        );
    },
    async versionDetail(
        strategyCode: string,
        versionId: string,
        signal?: AbortSignal,
    ): Promise<StrategyVersionItem> {
        const {data} = await apiClient.get<StrategyVersionItem>(
            `/strategies/${encodeURIComponent(strategyCode)}/versions/${encodeURIComponent(versionId)}`,
            {signal},
        );
        if (
            !versionIdentity(data, strategyCode, versionId) ||
            typeof data.paramSnapshotJson !== 'string' ||
            typeof data.configSnapshotJson !== 'string' ||
            typeof data.sourceSnapshotJson !== 'string'
        )
            throw new Error('Invalid strategy version detail');
        return data;
    },
};

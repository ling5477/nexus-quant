import {apiClient} from '@/api/client';
import {originalResult, type OriginalResultReceipt} from '@/features/research-result-recovery';
import {parseExactJson} from '@/api/exact-json';
import type {
    InstrumentCatalogItem,
    InstrumentCatalogSyncResponse,
} from '@/features/instruments/types/instruments';

export const instrumentsApi = {
    async list(exchangeCode?: string, signal?: AbortSignal): Promise<InstrumentCatalogItem[]> {
        const {data} = await apiClient.get<InstrumentCatalogItem[]>('/instruments', {
            params: exchangeCode ? {exchangeCode} : undefined,
            signal,
            transformResponse: [
                (raw: string) =>
                    parseExactJson(raw, new Set(['tickSize', 'stepSize', 'minQuantity'])),
            ],
        });
        return data;
    },

    async syncRequestResult(
        requestId: string,
    ): Promise<OriginalResultReceipt<InstrumentCatalogSyncResponse>> {
        const {data} = await apiClient.get<OriginalResultReceipt<InstrumentCatalogSyncResponse>>(
            `/instruments/sync-requests/${encodeURIComponent(requestId)}`,
        );
        return data;
    },
    async sync(
        exchangeCode: string | undefined,
        requestId: string,
    ): Promise<InstrumentCatalogSyncResponse> {
        const target = exchangeCode ?? 'ALL';
        const {data} = await apiClient.post<OriginalResultReceipt<InstrumentCatalogSyncResponse>>(
            `/instruments/sync-requests/${encodeURIComponent(requestId)}`,
            null,
            {params: {target}},
        );
        return originalResult(data, requestId, target);
    },
};

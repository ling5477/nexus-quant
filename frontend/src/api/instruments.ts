import {apiClient} from '@/api/client';
import type {InstrumentCatalogItem, InstrumentCatalogSyncResponse} from '@/types/instruments';

export const instrumentsApi = {
    async list(exchangeCode?: string): Promise<InstrumentCatalogItem[]> {
        const {data} = await apiClient.get<InstrumentCatalogItem[]>('/instruments', {
            params: exchangeCode ? {exchangeCode} : undefined,
        });
        return data;
    },

    async sync(exchangeCode?: string): Promise<InstrumentCatalogSyncResponse> {
        const {data} = await apiClient.post<InstrumentCatalogSyncResponse>('/instruments/sync', null, {
            params: exchangeCode ? {exchangeCode} : undefined,
        });
        return data;
    },
};

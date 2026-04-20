import {apiClient} from '@/api/client';
import type {MarketdataBar, MarketdataBarsQuery} from '@/types/marketdata';

export const marketdataApi = {
    async listBars(query: MarketdataBarsQuery): Promise<MarketdataBar[]> {
        const {data} = await apiClient.get<MarketdataBar[]>('/marketdata/bars', {
            params: query,
        });
        return data;
    },
};

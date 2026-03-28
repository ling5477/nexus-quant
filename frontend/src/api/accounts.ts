import {apiClient} from '@/api/client';
import type {ExchangeAccountSummary} from '@/types/accounts';

export const accountsApi = {
    async list(): Promise<ExchangeAccountSummary[]> {
        const {data} = await apiClient.get<ExchangeAccountSummary[]>('/exchange-accounts');
        return data;
    },
};

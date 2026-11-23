import {apiClient} from '@/api/client';
import {accountCommandsApi} from './commands';
import type {
    CreateExchangeAccountRequest,
    ExchangeAccountDetail,
    ExchangeAccountSummary,
    UpdateExchangeAccountRequest,
} from '@/features/accounts/types/accounts';

export const accountsApi = {
    async list(): Promise<ExchangeAccountSummary[]> {
        const {data} = await apiClient.get<ExchangeAccountSummary[]>('/exchange-accounts');
        return data;
    },

    async detail(accountId: number, signal?: AbortSignal): Promise<ExchangeAccountDetail> {
        const {data} = await apiClient.get<ExchangeAccountDetail>(
            `/exchange-accounts/${accountId}`,
            {signal},
        );
        return data;
    },

    async create(payload: CreateExchangeAccountRequest): Promise<ExchangeAccountSummary> {
        return accountCommandsApi.submit({type: 'CREATE', ...payload});
    },

    async update(
        accountId: number,
        payload: UpdateExchangeAccountRequest,
    ): Promise<ExchangeAccountSummary> {
        return accountCommandsApi.submit({type: 'UPDATE', accountId, ...payload});
    },

    async enable(accountId: number): Promise<ExchangeAccountSummary> {
        return accountCommandsApi.submit({type: 'ENABLE', accountId});
    },

    async disable(accountId: number): Promise<ExchangeAccountSummary> {
        return accountCommandsApi.submit({type: 'DISABLE', accountId});
    },

    async setDefault(accountId: number): Promise<ExchangeAccountSummary> {
        return accountCommandsApi.submit({type: 'SET_DEFAULT', accountId});
    },
};

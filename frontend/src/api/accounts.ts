import {apiClient} from '@/api/client';
import type {
    CreateExchangeAccountRequest,
    ExchangeAccountActiveCredentialResponse,
    ExchangeAccountCredentialSummary,
    ExchangeAccountCredentialUpsertRequest,
    ExchangeAccountDetail,
    ExchangeAccountSummary,
    UpdateExchangeAccountRequest,
} from '@/types/accounts';

export const accountsApi = {
    async list(): Promise<ExchangeAccountSummary[]> {
        const {data} = await apiClient.get<ExchangeAccountSummary[]>('/exchange-accounts');
        return data;
    },

    async detail(accountId: number): Promise<ExchangeAccountDetail> {
        const {data} = await apiClient.get<ExchangeAccountDetail>(`/exchange-accounts/${accountId}`);
        return data;
    },

    async create(payload: CreateExchangeAccountRequest): Promise<ExchangeAccountSummary> {
        const {data} = await apiClient.post<ExchangeAccountSummary>('/exchange-accounts', payload);
        return data;
    },

    async update(accountId: number, payload: UpdateExchangeAccountRequest): Promise<ExchangeAccountSummary> {
        const {data} = await apiClient.patch<ExchangeAccountSummary>(`/exchange-accounts/${accountId}`, payload);
        return data;
    },

    async enable(accountId: number): Promise<ExchangeAccountSummary> {
        const {data} = await apiClient.post<ExchangeAccountSummary>(`/exchange-accounts/${accountId}/enable`);
        return data;
    },

    async disable(accountId: number): Promise<ExchangeAccountSummary> {
        const {data} = await apiClient.post<ExchangeAccountSummary>(`/exchange-accounts/${accountId}/disable`);
        return data;
    },

    async setDefault(accountId: number): Promise<ExchangeAccountSummary> {
        const {data} = await apiClient.post<ExchangeAccountSummary>(`/exchange-accounts/${accountId}/set-default`);
        return data;
    },

    async getActiveCredential(accountId: number): Promise<ExchangeAccountActiveCredentialResponse> {
        const {data} = await apiClient.get<ExchangeAccountActiveCredentialResponse>(`/exchange-accounts/${accountId}/credentials/active`);
        return data;
    },

    async upsertCredential(accountId: number, payload: ExchangeAccountCredentialUpsertRequest): Promise<ExchangeAccountCredentialSummary> {
        const {data} = await apiClient.post<ExchangeAccountCredentialSummary>(`/exchange-accounts/${accountId}/credentials`, payload);
        return data;
    },

    async verifyCredential(accountId: number): Promise<ExchangeAccountCredentialSummary> {
        const {data} = await apiClient.post<ExchangeAccountCredentialSummary>(`/exchange-accounts/${accountId}/credentials/verify`);
        return data;
    },
};

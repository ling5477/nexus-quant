import {apiClient} from '@/api/client';
import type {ExchangeAccountSummary} from '@/features/accounts/types/accounts';

export interface DemoRegistrationInput {
    registrationId: string;
    username: string;
    email: string;
    password: string;
    confirmPassword: string;
}

export interface DemoRegistrationResult {
    status: string;
    tenantId: string | null;
    exchangeAccountId: number | null;
    accountId: number | null;
    reservedPaperRunId: string | null;
    boundPaperRunId: string | null;
    initialCapital: string | null;
    currency: string | null;
}

export const demoRegistrationKeys = {
    result: (userId: number) => ['demo-registration', userId] as const,
};

export const demoRegistrationApi = {
    async simAccount(): Promise<ExchangeAccountSummary[]> {
        const {data} = await apiClient.get<ExchangeAccountSummary>('/demo/sim-account');
        if (!data || data.exchangeCode !== 'PAPER' || data.tradeEnv !== 'SIM'
            || !Number.isSafeInteger(data.exchangeAccountId) || data.exchangeAccountId <= 0
            || !Number.isSafeInteger(data.legacyAccountId) || data.legacyAccountId! <= 0)
            throw new Error('Invalid initialized simulation account.');
        return [data];
    },
    async register(input: DemoRegistrationInput): Promise<void> {
        const {data} = await apiClient.post<{status: string}>('/auth/register', input);
        if (data?.status !== 'ACCEPTED') throw new Error('Registration acknowledgement unavailable.');
    },
    async verify(token: string): Promise<{status: string}> {
        const {data} = await apiClient.post<{status: string}>('/auth/verify-email', {token});
        return data;
    },
    async resend(email: string): Promise<void> {
        const {data} = await apiClient.post<{status: string}>('/auth/resend-verification', {email});
        if (data?.status !== 'ACCEPTED') throw new Error('Verification email acknowledgement unavailable.');
    },
    async result(signal?: AbortSignal): Promise<DemoRegistrationResult> {
        const {data} = await apiClient.get<DemoRegistrationResult>('/demo/registration-result', {signal});
        return data;
    },
};

import {create} from 'zustand';

import type {ExchangeAccountSummary} from '@/types/accounts';

type BootstrapStatus = 'idle' | 'loading' | 'ready';

interface AccountContextState {
    selectedExchangeAccountId: number | null;
    legacyAccountId: number | null;
    exchangeCode: string | null;
    tradeEnv: string | null;
    accountAlias: string | null;
    bootstrapStatus: BootstrapStatus;
    setSelectedAccount: (account: ExchangeAccountSummary | null) => void;
    setBootstrapStatus: (status: BootstrapStatus) => void;
    clearAccountContext: () => void;
}

export const useAccountContextStore = create<AccountContextState>((set) => ({
    selectedExchangeAccountId: null,
    legacyAccountId: null,
    exchangeCode: null,
    tradeEnv: null,
    accountAlias: null,
    bootstrapStatus: 'idle',
    setSelectedAccount: (account) => set({
        selectedExchangeAccountId: account?.exchangeAccountId ?? null,
        legacyAccountId: account?.legacyAccountId ?? null,
        exchangeCode: account?.exchangeCode ?? null,
        tradeEnv: account?.tradeEnv ?? null,
        accountAlias: account?.accountAlias ?? null,
    }),
    setBootstrapStatus: (bootstrapStatus) => set({bootstrapStatus}),
    clearAccountContext: () => set({
        selectedExchangeAccountId: null,
        legacyAccountId: null,
        exchangeCode: null,
        tradeEnv: null,
        accountAlias: null,
        bootstrapStatus: 'idle',
    }),
}));

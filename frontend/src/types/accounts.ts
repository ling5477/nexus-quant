export interface ExchangeAccountSummary {
    exchangeAccountId: number;
    legacyAccountId: number | null;
    exchangeCode: string;
    tradeEnv: string;
    accountAlias: string;
    externalAccountRef: string | null;
    isDefault: boolean;
    status: string;
}

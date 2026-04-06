export type ExchangeCredentialType = 'OKX_API_V5' | 'BINANCE_HMAC' | 'BINANCE_ED25519';
export type CredentialVerificationStatus = 'PENDING' | 'VERIFIED' | 'FAILED' | 'REVOKED';

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

export interface ExchangeAccountDetail extends ExchangeAccountSummary {}

export interface CreateExchangeAccountRequest {
    exchangeCode: string;
    tradeEnv: string;
    accountAlias: string;
    externalAccountRef?: string | null;
}

export interface UpdateExchangeAccountRequest {
    accountAlias: string;
    externalAccountRef?: string | null;
}

export interface ExchangeAccountCredentialSummary {
    credentialId: number;
    exchangeAccountId: number;
    credentialType: ExchangeCredentialType;
    maskedAccessKey: string;
    verificationStatus: CredentialVerificationStatus;
    isActive: boolean;
    rotatedFromCredentialId: number | null;
    lastVerifiedAt: string | null;
    lastVerificationError: string | null;
    updatedAt: string;
}

export interface ExchangeAccountActiveCredentialResponse {
    exchangeAccountId: number;
    activeCredential: ExchangeAccountCredentialSummary | null;
}

export interface ExchangeAccountCredentialUpsertRequest {
    credentialType: ExchangeCredentialType;
    apiKey: string;
    secretKey?: string | null;
    passphrase?: string | null;
    privateKeyPem?: string | null;
}

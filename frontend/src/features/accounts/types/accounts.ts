export type ExchangeCredentialType = 'OKX_API_V5' | 'BINANCE_HMAC' | 'BINANCE_ED25519';
type CredentialVerificationStatus = 'PENDING' | 'VERIFIED' | 'FAILED' | 'REVOKED';

export interface ExchangeAccountSummary {
    exchangeAccountId: number;
    /**
     * legacyAccountId 仅用于旧账户身份的兼容展示，不能再作为前端正式主上下文。
     */
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
    credentialStatus: 'ACTIVE' | 'DISABLED' | 'ROTATED' | 'REVOKED' | 'EXPIRED';
    verificationStatus: CredentialVerificationStatus;
    isActive: boolean;
    rotatedFromCredentialId: number | null;
    revokedAt: string | null;
    rotatedAt: string | null;
    lastVerifiedAt: string | null;
    lastVerificationError: string | null;
    updatedAt: string;
    permissionProbeStatus: string;
    permissionScope: string | null;
    withdrawEnabled: boolean;
    ipAllowlistProbeStatus: string;
    failedAuthCount: number;
    lastPermissionProbeAt: string | null;
    lastPermissionProbeError: string | null;
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

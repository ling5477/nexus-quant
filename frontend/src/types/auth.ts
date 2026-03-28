export interface LoginRequest {
    username: string;
    password: string;
}

export interface LoginResponse {
    accessToken: string;
    tokenType: string;
    expiresIn: number;
    expiresAt: string;
    username: string;
    roles: string[];
}

export interface CurrentUser {
    userId: number;
    username: string;
    roles: string[];
    authenticated: boolean;
    defaultExchangeAccountId: number | null;
    defaultExchangeCode: string | null;
    defaultTradeEnv: string | null;
    defaultAccountAlias: string | null;
}
